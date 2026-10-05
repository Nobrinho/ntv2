@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.io

import android.net.Uri
import android.os.SystemClock
import android.os.StatFs
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ntv2.app.core.multipart.PartsLookup
import com.ntv2.app.core.player.config.StreamProfile
import com.ntv2.app.core.player.telegram.PartialFileAccessor
import com.ntv2.app.core.storage.LowStorageException
import com.ntv2.app.core.storage.StorageBudget
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

class GrowingFileDataSourceFactory(
    private val partialFileAccessor: PartialFileAccessor,
    private val stallTimeoutMs: Long,
    private val readAheadBytes: Long,
    /** Chamado quando o download adiante é suspenso por falta de espaço (dispara limpeza de caches). */
    private val onLowStorage: () -> Unit = {},
    /** Janela deslizante no disco; null desativa (o arquivo cresce até o tamanho do vídeo). */
    private val diskWindow: DiskWindowPolicy? = null,
    /** false enquanto o TDLib reconecta: esse tempo não conta como download travado. */
    private val isNetworkReady: () -> Boolean = { true },
    /** Filmes divididos em partes: quando presente, `tgfile://multi/<id>` toca as partes como um arquivo só. */
    private val partsLookup: PartsLookup? = null,
    /** Recebe a parte que o player está lendo (a tela mostra "Parte 5/12"). */
    private val partsPlaybackState: com.ntv2.app.core.multipart.MultiPartPlaybackState? = null
) : DataSource.Factory {

    // Dimensionamento por vídeo (bitrate), definido pelo coordinator ao preparar.
    private val profiles = ConcurrentHashMap<Int, StreamProfile>()

    fun setProfile(fileId: Int, profile: StreamProfile) {
        profiles[fileId] = profile
    }

    fun clearProfile(fileId: Int) {
        profiles.remove(fileId)
    }

    override fun createDataSource(): DataSource {
        val single = newSingleFileDataSource()
        val lookup = partsLookup ?: return single
        return MultiPartRoutingDataSource(
            single = single,
            multi = MultiPartDataSource(
                partsLookup = lookup,
                accessor = partialFileAccessor,
                openPart = ::newSingleFileDataSource,
                prefetchAheadBytes = { firstFileId -> (profiles[firstFileId]?.aheadWindowBytes ?: readAheadBytes) },
                playbackState = partsPlaybackState
            )
        )
    }

    // Cada parte de um filme dividido é lida por um GrowingFileDataSource comum (mesma janela,
    // mesma liberação de disco, mesmos reabrires); o composto só decide qual parte abrir.
    private fun newSingleFileDataSource(): DataSource = GrowingFileDataSource(
        partialFileAccessor, stallTimeoutMs, readAheadBytes, onLowStorage, diskWindow,
        isNetworkReady, profiles::get, partsLookup?.let { it::sizeOfPart } ?: { null }
    )
}

private const val NUDGE_INTERVAL_MS = 1_000L
private const val COVERAGE_POLL_MS = 75L
// De quantos em quantos bytes lidos reavaliamos o que liberar do disco.
private const val EVICT_CHECK_STEP_BYTES = 8L * 1024L * 1024L
private const val TAG = "NtvDiskWindow"
private const val MB = 1024L * 1024L
private const val STALL_LOG_AFTER_MS = 6_000L
// Validade da checagem de espaço livre (StatFs é chamada de sistema; antes rodava a cada read()).
private const val FREE_SPACE_CACHE_MS = 2_000L
// Rede fora do ar por mais que isso: desiste mesmo assim (a tela mostra o erro com "Tentar de novo").
private const val MAX_NETWORK_WAIT_MS = 120_000L

private class GrowingFileDataSource(
    private val partialFileAccessor: PartialFileAccessor,
    private val stallTimeoutMs: Long,
    private val defaultReadAheadBytes: Long,
    private val onLowStorage: () -> Unit,
    private val defaultDiskWindow: DiskWindowPolicy?,
    private val isNetworkReady: () -> Boolean,
    private val profileFor: (Int) -> StreamProfile?,
    /** Tamanho exato do arquivo quando se sabe de antemão (parte de filme dividido); null = só o TDLib sabe. */
    private val knownSizeFor: (Int) -> Long? = { null }
) : BaseDataSource(false) {

    private var dataSpec: DataSpec? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var fileId: Int = -1
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    // Valores efetivos deste open (perfil do vídeo, ou os padrões).
    private var readAheadBytes: Long = defaultReadAheadBytes
    private var diskWindow: DiskWindowPolicy? = defaultDiskWindow
    // Base do download deste open (posição do dataSpec). O download é sempre estendido de forma
    // CONTÍGUA a partir daqui (só aumentando o tamanho) — nunca movendo o offset à frente, o que
    // criaria um buraco entre a fronteira baixada e a nova posição e travaria a leitura.
    private var downloadBaseOffset: Long = 0L
    // Até onde já pedimos download; mantém uma janela à frente do ponto de leitura em vez de
    // baixar o arquivo inteiro de uma vez (isso enchia o disco e o TDLib abortava por ENOSPC).
    private var lastRequestedEnd: Long = 0L
    // Cache do prefixo baixado consultado ao TDLib (evita consultar a cada leitura).
    private var cacheBase: Long = -1L
    private var cachePrefix: Long = 0L
    // Posição da última avaliação da janela deslizante.
    private var lastEvictCheck: Long = 0L
    // Cache da checagem de espaço livre.
    private var freeSpaceOk: Boolean = true
    private var freeSpaceCheckedAt: Long = -FREE_SPACE_CACHE_MS
    // Leitura curta: o TDLib diz que há bytes, mas o arquivo no disco ainda é menor (logo após
    // recriar a cópia). Antes isso virava "fim do vídeo".
    private var shortReadSince: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        fileId = parseFileId(dataSpec.uri)
        if (fileId < 0) {
            throw IOException("URI inválida para playback: ${dataSpec.uri}")
        }
        applyProfile(profileFor(fileId))

        readPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            C.LENGTH_UNSET.toLong()
        } else {
            dataSpec.length
        }

        // Seek para trás, num trecho já liberado do disco: baixa de novo a partir daqui.
        if (isEvicted(readPosition)) resetLocalCopyAt(readPosition)

        // Parte de filme dividido recém-aberta (emenda ou pulo): o arquivo pode ainda não existir.
        if (knownSizeFor(fileId) != null) awaitLocalFile()
        val filePath = partialFileAccessor.resolvePath(fileId)
            ?: throw IOException("Arquivo local ainda não disponível para fileId=$fileId")
        val file = File(filePath)
        if (!file.exists()) {
            throw IOException("Arquivo não encontrado: $filePath")
        }
        randomAccessFile = RandomAccessFile(file, "r")
        randomAccessFile?.seek(readPosition)
        transferStarted(dataSpec)

        // C2: baixa a faixa exigida por ESTE open (posição de seek ou índice no fim do arquivo),
        // mas LIMITADA a uma janela — não o arquivo inteiro (evita encher o disco). Leituras de
        // tamanho fixo (ex.: índice do MKV) pedem exatamente o solicitado.
        downloadBaseOffset = readPosition
        lastEvictCheck = readPosition
        cacheBase = -1L
        cachePrefix = 0L
        shortReadSince = 0L
        val requestLen = if (bytesRemaining == C.LENGTH_UNSET.toLong()) readAheadBytes else bytesRemaining
        partialFileAccessor.requestRange(fileId, readPosition, requestLen, priority = 32)
        lastRequestedEnd = readPosition + requestLen

        return bytesRemaining
    }

    private fun applyProfile(profile: StreamProfile?) {
        if (profile == null) {
            readAheadBytes = defaultReadAheadBytes
            diskWindow = defaultDiskWindow
            return
        }
        readAheadBytes = profile.aheadWindowBytes
        diskWindow = if (profile.diskWindowEnabled) {
            defaultDiskWindow?.copy(keepBehindBytes = profile.keepBehindBytes)
        } else {
            null
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (randomAccessFile == null) return C.RESULT_END_OF_INPUT
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        while (true) {
            // Fim da parte (tamanho exato conhecido): não espera o TDLib marcar o arquivo completo,
            // o que nunca acontece numa cópia recriada só a partir do meio.
            knownSizeFor(fileId)?.let { size -> if (readPosition >= size) return C.RESULT_END_OF_INPUT }
            // Leitura avançou (a partir do início fixado) para dentro do trecho liberado: reabre
            // sobre uma cópia nova, baixada a partir da posição atual.
            if (isEvicted(readPosition)) reopenAfterReset(readPosition)
            val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
            // Legibilidade por POSIÇÃO: pergunta ao TDLib quantos bytes contíguos há a partir de
            // readPosition (reconhece frente E fim já no disco). Antes usávamos o prefixo relativo
            // ao offset único, que "esquecia" a frente após buscar o índice no fim (MKV) → travava.
            val readable = readablePrefixFrom(readPosition)
            val prefix = diskWindow?.clampReadable(
                readPosition,
                readable,
                partialFileAccessor.evictedEnd(fileId)
            ) ?: readable
            when (
                val plan = PartialReadPlanner.plan(
                    readableBytes = prefix,
                    bytesRemaining = bytesRemaining,
                    requestedLength = length,
                    isComplete = partialFileAccessor.isComplete(fileId)
                )
            ) {
                is PartialReadPlanner.Plan.Read -> {
                    val read = raf.read(buffer, offset, plan.maxBytes)
                    if (read <= 0) {
                        if (partialFileAccessor.isComplete(fileId)) return C.RESULT_END_OF_INPUT
                        waitShortRead()
                        continue
                    }
                    shortReadSince = 0L
                    readPosition += read
                    if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                        bytesRemaining -= read
                    }
                    bytesTransferred(read)
                    // Renova a janela antes de consumir os últimos bytes disponíveis. Antes este
                    // pedido só acontecia no estado Wait, quando o player já tinha parado.
                    maybeRequestAhead()
                    maybeEvictBehind()
                    return read
                }

                PartialReadPlanner.Plan.EndOfInput -> return C.RESULT_END_OF_INPUT

                PartialReadPlanner.Plan.Wait -> {
                    maybeRequestAhead()
                    val covered = runBlocking { awaitCoverageOrStall(readPosition) }
                    if (!covered) {
                        // Parou porque o guarda de disco suspendeu o download: erro específico, para a
                        // tela explicar o motivo em vez de um "Timeout" genérico.
                        if (!hasEnoughFreeSpace(force = true)) {
                            throw LowStorageException("Pouco espaço livre no aparelho para continuar o vídeo")
                        }
                        throw IOException("Timeout aguardando bytes do arquivo parcial")
                    }
                }
            }
        }
    }

    /** Espera (até o limite de travamento) o TDLib criar o arquivo local de uma parte recém-aberta. */
    private fun awaitLocalFile() {
        val deadline = SystemClock.elapsedRealtime() + stallTimeoutMs
        while (true) {
            val path = partialFileAccessor.resolvePath(fileId)
            if (!path.isNullOrEmpty() && File(path).exists()) return
            if (SystemClock.elapsedRealtime() > deadline) {
                throw IOException("Timeout aguardando o arquivo local da parte (fileId=$fileId)")
            }
            Thread.sleep(COVERAGE_POLL_MS)
        }
    }

    /** O TDLib reportou bytes que o arquivo físico ainda não tem: consulta de novo em instantes. */
    private fun waitShortRead() {
        val now = SystemClock.elapsedRealtime()
        if (shortReadSince == 0L) shortReadSince = now
        if (now - shortReadSince > stallTimeoutMs) {
            throw IOException("Arquivo local menor que o baixado (fileId=$fileId pos=${readPosition / MB}MB)")
        }
        cacheBase = -1L
        cachePrefix = 0L
        Thread.sleep(COVERAGE_POLL_MS)
    }

    /**
     * Bytes contíguos disponíveis a partir de [pos], com cache: só consulta o TDLib quando a
     * posição sai da região já conhecida (evita uma consulta por leitura).
     */
    private fun readablePrefixFrom(pos: Long): Long {
        if (cacheBase in 0..pos && pos < cacheBase + cachePrefix) {
            return cacheBase + cachePrefix - pos
        }
        val prefix = runBlocking { partialFileAccessor.downloadedPrefixFrom(fileId, pos) }
        cacheBase = pos
        cachePrefix = prefix
        return prefix
    }

    /**
     * Aguarda até que [position] esteja coberta pela região contígua baixada, OU o download
     * conclua. Retorna false apenas se o download ficar TRAVADO (nenhum byte novo) por mais que
     * [stallTimeoutMs] com a rede pronta. Usa progresso real de download (downloadedBytes, qualquer
     * mudança — cai para 0 quando a cópia local é recriada) para não abortar um seek lento mas em
     * andamento — ex.: buscar o índice do MKV lá no fim do arquivo.
     */
    private suspend fun awaitCoverageOrStall(position: Long): Boolean {
        var lastDownloaded = partialFileAccessor.downloadedBytes(fileId)
        var lastProgressAt = SystemClock.elapsedRealtime()
        var networkDownSince = 0L
        var lastNudgeAt = 0L
        var stallLogged = false
        while (true) {
            if (partialFileAccessor.isComplete(fileId)) return true
            if (partialFileAccessor.downloadedPrefixFrom(fileId, position) > 0L) return true

            val now = SystemClock.elapsedRealtime()
            // Re-solicita periodicamente a faixa necessária: sem isso o TDLib conclui a janela
            // anterior e fica OCIOSO (o pedido do índice no fim do MKV se perdia → stall/erro →
            // ExoPlayer re-tentava → flip-flop frente/fim, minutos de buffering ou falha).
            // Respeita o guarda de disco (não re-solicita se o espaço livre estiver baixo).
            if (now - lastNudgeAt > NUDGE_INTERVAL_MS && hasEnoughFreeSpace()) {
                val len = (position - downloadBaseOffset + readAheadBytes).coerceAtLeast(readAheadBytes)
                partialFileAccessor.requestRange(fileId, downloadBaseOffset, len, priority = 32)
                lastNudgeAt = now
            }

            delay(COVERAGE_POLL_MS)

            val t = SystemClock.elapsedRealtime()
            val downloaded = partialFileAccessor.downloadedBytes(fileId)
            if (downloaded != lastDownloaded) {
                lastDownloaded = downloaded
                lastProgressAt = t
                stallLogged = false
            }
            // TDLib reconectando: não conta como travado (até um limite).
            if (!isNetworkReady()) {
                if (networkDownSince == 0L) networkDownSince = t
                if (t - networkDownSince < MAX_NETWORK_WAIT_MS) lastProgressAt = t
            } else {
                networkDownSince = 0L
            }
            // Diagnóstico: download parado há um tempo — registra o que o TDLib reporta (o TDLib
            // não loga nada), para descobrir por que o trecho pedido não chega.
            if (!stallLogged && t - lastProgressAt > STALL_LOG_AFTER_MS) {
                stallLogged = true
                Log.i(
                    "NtvDownload",
                    "parado fileId=$fileId pos=${position / MB}MB base=${downloadBaseOffset / MB}MB " +
                        "baixado=${downloaded / MB}MB " +
                        "total=${(partialFileAccessor.expectedBytes(fileId) ?: 0L) / MB}MB " +
                        "completo=${partialFileAccessor.isComplete(fileId)} espacoOk=${hasEnoughFreeSpace()} " +
                        "rede=${isNetworkReady()} liberadoAte=${partialFileAccessor.evictedEnd(fileId) / MB}MB"
                )
            }
            if (t - lastProgressAt > stallTimeoutMs) return false
        }
    }

    /**
     * Mantém ~[readAheadBytes] baixados à frente do ponto de leitura, estendendo a solicitação
     * conforme a reprodução avança — em vez de baixar tudo de uma vez. Só para leituras de
     * tamanho aberto (playback); leituras fixas (índice) não estendem.
     */
    private fun maybeRequestAhead() {
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) return
        val remainingAhead = lastRequestedEnd - readPosition
        // Checagem barata primeiro: só pensa em estender quando a janela está pela metade.
        if (remainingAhead > readAheadBytes / 2L) return
        if (partialFileAccessor.isComplete(fileId)) return
        // Guarda de disco: não estende o download se o espaço livre estiver baixo. Sem isso, um
        // filme grande enche o disco e o TDLib faz abort() (ENOSPC no binlog) → app fecha. Aqui
        // a reprodução degrada (para de baixar adiante) em vez de derrubar o app.
        if (!hasEnoughFreeSpace()) return
        val desiredEnd = readPosition + readAheadBytes
        // Estende o download CONTÍGUO a partir da base (aumenta o tamanho), sem mover o offset
        // à frente — assim não abre buracos que travariam a leitura.
        partialFileAccessor.requestRange(
            fileId,
            downloadBaseOffset,
            desiredEnd - downloadBaseOffset,
            priority = 32
        )
        lastRequestedEnd = desiredEnd
    }

    /**
     * Espaço livre suficiente para continuar baixando. O piso fica ACIMA do limiar em que o sistema
     * avisa "armazenamento baixo" (ver [StorageBudget]). Resultado guardado por
     * [FREE_SPACE_CACHE_MS]; [onLowStorage] só dispara na transição para "pouco espaço".
     */
    private fun hasEnoughFreeSpace(force: Boolean = false): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - freeSpaceCheckedAt < FREE_SPACE_CACHE_MS) return freeSpaceOk
        val parent = partialFileAccessor.resolvePath(fileId)?.let { File(it).parentFile } ?: return true
        val enough = runCatching {
            val stat = StatFs(parent.path)
            StorageBudget.canDownload(stat.availableBytes, stat.totalBytes)
        }.getOrDefault(true)
        if (!enough && freeSpaceOk) onLowStorage()
        freeSpaceOk = enough
        freeSpaceCheckedAt = now
        return enough
    }

    private fun isEvicted(position: Long): Boolean {
        val policy = diskWindow ?: return false
        return policy.isEvicted(position, partialFileAccessor.evictedEnd(fileId))
    }

    /**
     * Libera do disco o trecho já lido que ficou para trás (ver [DiskWindowPolicy]). Só na leitura
     * aberta da reprodução — leituras de tamanho fixo (índice) não contam.
     */
    private fun maybeEvictBehind() {
        val policy = diskWindow ?: return
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) return
        if (readPosition - lastEvictCheck < EVICT_CHECK_STEP_BYTES) return
        lastEvictCheck = readPosition
        val size = partialFileAccessor.expectedBytes(fileId)?.takeIf { it > 0L } ?: return
        val evictedEnd = partialFileAccessor.evictedEnd(fileId)
        val range = policy.evictionRange(evictedEnd, readPosition, downloadBaseOffset, size) ?: return
        // Em segundo plano (DiskEvictor): esta é a thread que carrega o vídeo e não pode esperar
        // o sistema de arquivos liberar os blocos.
        partialFileAccessor.scheduleEviction(fileId, range.first, range.last + 1)
    }

    /**
     * Apaga a cópia local e pede ao TDLib o trecho a partir de [position]; aguarda o arquivo novo
     * existir. Necessário porque o TDLib considera baixado o trecho que liberamos do disco.
     */
    private fun resetLocalCopyAt(position: Long) {
        Log.i(TAG, "rebaixando fileId=$fileId a partir de ${position / MB}MB (trecho liberado)")
        runBlocking {
            partialFileAccessor.resetLocalCopy(fileId)
            partialFileAccessor.requestRange(fileId, position, readAheadBytes, priority = 32)
            val deadline = SystemClock.elapsedRealtime() + stallTimeoutMs
            while (true) {
                val path = partialFileAccessor.resolvePath(fileId)
                if (!path.isNullOrEmpty() && File(path).exists()) break
                if (SystemClock.elapsedRealtime() > deadline) {
                    throw IOException("Timeout recriando o arquivo local de fileId=$fileId")
                }
                delay(100L)
            }
        }
    }

    private fun reopenAfterReset(position: Long) {
        resetLocalCopyAt(position)
        val path = partialFileAccessor.resolvePath(fileId)
            ?: throw IOException("Arquivo local indisponível após rebaixar fileId=$fileId")
        runCatching { randomAccessFile?.close() }
        randomAccessFile = RandomAccessFile(File(path), "r").also { it.seek(position) }
        downloadBaseOffset = position
        lastRequestedEnd = position + readAheadBytes
        lastEvictCheck = position
        cacheBase = -1L
        cachePrefix = 0L
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            randomAccessFile?.close()
        } finally {
            randomAccessFile = null
            dataSpec?.let { transferEnded() }
            dataSpec = null
            fileId = -1
            readPosition = 0L
            bytesRemaining = C.LENGTH_UNSET.toLong()
            downloadBaseOffset = 0L
            lastRequestedEnd = 0L
            freeSpaceCheckedAt = -FREE_SPACE_CACHE_MS
            freeSpaceOk = true
        }
    }

    private fun parseFileId(uri: Uri): Int {
        val last = uri.lastPathSegment ?: return -1
        return last.toIntOrNull() ?: -1
    }
}
