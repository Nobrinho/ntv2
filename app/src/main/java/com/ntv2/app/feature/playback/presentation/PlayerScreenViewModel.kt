package com.ntv2.app.feature.playback.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ntv2.app.core.player.PlaybackSnapshot
import com.ntv2.app.core.player.PlaybackState
import com.ntv2.app.core.player.controller.PlaybackController
import com.ntv2.app.core.player.controller.PlaybackPrepareRequest
import com.ntv2.app.core.player.controller.PlaybackPrepareResult
import com.ntv2.app.core.player.recovery.StallAction
import com.ntv2.app.core.player.recovery.StallPolicy
import com.ntv2.app.core.player.source.MediaAvailability
import com.ntv2.app.core.storage.StorageBudget
import com.ntv2.app.feature.media.domain.MediaDetailsCache
import com.ntv2.app.feature.media.domain.MovieDetails
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerScreenUiState(
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val title: String = "",
    val channelName: String = "",
    val durationSeconds: Int = 0,
    val fileName: String? = null,
    val thumbnailPath: String? = null,
    val isPlaceholderMode: Boolean = true,
    val statusMessage: String = "reprodução ainda não inicializada",
    /** Detalhes ricos (pôster/sinopse/metadados) do filme, quando disponíveis. */
    val details: MovieDetails? = null,
    /** Progresso do download enquanto o vídeo não começa (null = não exibir). */
    val downloadProgress: DownloadProgress? = null,
    /** Aviso extra durante a espera (ex.: vídeo que precisa baixar mais antes de tocar). */
    val loadingHint: String? = null,
    /** Falha ao carregar: a tela mostra o motivo e os botões Tentar novamente / Voltar. */
    val loadError: PlayerLoadError? = null,
    /** Há Chromecast na rede (mostra o botão Transmitir). */
    val castAvailable: Boolean = false,
    /** Transmitindo agora: nome do aparelho (null = tocando aqui). */
    val castingTo: String? = null,
    /** Chromecast conectando / carregando o vídeo. */
    val castConnecting: Boolean = false,
    /** O vídeo terminou no Chromecast. */
    val castEnded: Boolean = false,
    /** Aviso sobre a transmissão (ex.: sem Wi-Fi, formato não suportado). */
    val castMessage: String? = null,
    /** Próximo episódio (série) já resolvido; preenchido ao terminar o atual, para autoplay. */
    val upNext: com.ntv2.app.feature.media.domain.UpNextEpisode? = null,
    /** Velocidade de rede (bytes/s) exibida no painel "Estado da rede". */
    val networkSpeedBytesPerSec: Long = 0L,
    /** false quando o TDLib está (re)conectando — o painel de rede mostra "Reconectando". */
    val connectionReady: Boolean = true
)

data class DownloadProgress(
    val downloadedBytes: Long,
    val expectedBytes: Long,
    val bytesPerSecond: Long,
    /** Filme dividido em partes: em que parte se está (null = arquivo único). */
    val parts: com.ntv2.app.core.multipart.PartsSnapshot? = null
)

data class PlayerLoadError(
    val title: String,
    val message: String,
    /** Detalhe técnico (ex.: erro do TDLib), exibido em letra menor. */
    val detail: String? = null
)

// Download sem nenhum byte novo por esse tempo = parado: reinicia no TDLib (até MAX_RESTARTS vezes).
private const val STALL_RESTART_MS = 12_000L
// Depois de esgotar as reinicializações, esse tempo parado vira erro.
private const val STALL_FAIL_MS = 20_000L
private const val MAX_RESTARTS = 2
// Parado há esse tempo: antes de reiniciar, pede ao TDLib para refazer as conexões (no Fire TV os
// sockets morrem em silêncio com o Wi‑Fi em economia e o TDLib segue "conectado").
private const val STALL_NETWORK_REFRESH_MS = 6_000L
// Tocando sem travar por esse tempo: zera as reinicializações (o limite não vale pelo filme inteiro).
private const val HEALTHY_RESET_MS = 60_000L
// Nenhum byte recebido ainda: o 1º arquivo de um canal pode estar num servidor (DC) do Telegram ao
// qual o app ainda não se conectou nesta sessão — o handshake demora, sobretudo no Fire TV. Cancelar
// o download nesse meio‑tempo recomeçava o handshake e o vídeo nunca iniciava; espera mais.
private const val FIRST_BYTE_RESTART_MS = 30_000L
private const val FIRST_BYTE_FAIL_MS = 50_000L
// Tocando há esse tempo sem o 1º quadro, mas baixando: avisa que o vídeo precisa de mais dados.
private const val SLOW_START_HINT_MS = 20_000L

sealed interface PlayerScreenAction {
    data class Prepare(
        val mediaId: String,
        val fileId: Int,
        val title: String,
        val channelName: String,
        val durationSeconds: Int,
        val fileName: String?,
        val thumbnailPath: String?
    ) : PlayerScreenAction

    data object Play : PlayerScreenAction
    data object Pause : PlayerScreenAction
    data class SeekBy(val deltaMs: Long) : PlayerScreenAction
    data class SeekTo(val positionMs: Long) : PlayerScreenAction
    data class SelectAudio(val id: String) : PlayerScreenAction
    /** id null desliga a legenda. */
    data class SelectSubtitle(val id: String?) : PlayerScreenAction
    data object OnAppStop : PlayerScreenAction
    data object OnAppResume : PlayerScreenAction
    data object Release : PlayerScreenAction
    /** "Tentar novamente" da tela de erro: retoma o download/preparo de onde parou. */
    data object RetryLoad : PlayerScreenAction
}

class PlayerScreenViewModel(
    private val playbackController: PlaybackController,
    private val mediaDetailsCache: MediaDetailsCache? = null,
    /** false enquanto o TDLib (re)conecta: esse tempo não conta como download parado. */
    private val networkReady: StateFlow<Boolean> = MutableStateFlow(true),
    private val castManager: com.ntv2.app.core.cast.CastManager? = null,
    private val streamServer: com.ntv2.app.core.cast.LocalStreamServer? = null,
    private val progressStore: com.ntv2.app.core.player.progress.PlaybackProgressStore? = null,
    private val upNextQueue: com.ntv2.app.feature.media.domain.UpNextQueue? = null,
    /** Filmes divididos em partes: o Cast serve o filme todo, não só a parte 1. */
    private val partsLookup: com.ntv2.app.core.multipart.PartsLookup? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerScreenUiState())
    val uiState: StateFlow<PlayerScreenUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    val player: Player? get() = playbackController.player
    private var prepareJob: Job? = null
    private var watchdogJob: Job? = null
    private var lastRequest: PlaybackPrepareRequest? = null
    private var preparingFileId: Int = 0
    @Volatile
    private var networkPanelVisible: Boolean = false

    /** Painel "Estado da rede" aberto/fechado: só então a velocidade é publicada a cada segundo. */
    fun onNetworkPanelVisible(visible: Boolean) {
        networkPanelVisible = visible
    }

    // Transmitindo: o player local fica parado e o estado exibido vem do Chromecast.
    private val casting = MutableStateFlow(false)
    private var castMediaId: String? = null
    private var castFileName: String? = null
    private var lastCastSaveAt = 0L

    init {
        val remote = castManager?.remote ?: MutableStateFlow(com.ntv2.app.core.cast.RemotePlayback())
        observeJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(playbackController.snapshot, remote, casting) { snap, r, isCasting ->
                if (!isCasting) snap else snap.copy(
                    state = when {
                        r.isBuffering -> PlaybackState.Buffering
                        r.isPlaying -> PlaybackState.Ready
                        else -> PlaybackState.Paused
                    },
                    isPlaying = r.isPlaying,
                    currentPositionMs = r.positionMs
                )
            }.collect { snapshot ->
                // Ao terminar o vídeo, consulta a fila "próximo episódio" (série) para autoplay.
                val next = if (snapshot.state == PlaybackState.Ended && !casting.value) {
                    lastRequest?.mediaId?.let { upNextQueue?.nextAfter(it) }
                } else null
                _uiState.update { it.copy(snapshot = snapshot, upNext = next) }
            }
        }
        // Amostra contínua para o painel "Estado da rede": velocidade de download (suavizada) e o
        // estado da conexão com o Telegram. Roda enquanto o player existe; custo desprezível.
        viewModelScope.launch {
            val speed = SpeedMeter()
            while (true) {
                val snap = playbackController.snapshot.value
                val bps = speed.sample(snap.progressBytes, now())
                val ready = networkReady.value
                // A velocidade só é exibida no painel de rede: fora dele não recompõe a tela a cada
                // segundo (pesava no Fire TV durante a reprodução).
                if (networkPanelVisible || uiState.value.connectionReady != ready) {
                    _uiState.update {
                        it.copy(
                            networkSpeedBytesPerSec = if (networkPanelVisible) bps else it.networkSpeedBytesPerSec,
                            connectionReady = ready
                        )
                    }
                }
                delay(1_000L)
            }
        }
        castManager?.let { manager ->
            // Sem Google Play Services (Fire TV) o start não faz nada e o status fica "Unsupported".
            manager.start()
            viewModelScope.launch { manager.status.collect { status -> onCastStatus(status) } }
            viewModelScope.launch {
                remote.collect { r ->
                    if (!casting.value) return@collect
                    if (r.positionMs > 0L || r.isPlaying) _uiState.update { it.copy(castConnecting = false) }
                    if (r.ended) _uiState.update { it.copy(castEnded = true) }
                    r.error?.let { msg -> _uiState.update { it.copy(castMessage = msg) } }
                    saveCastProgress(r)
                }
            }
        }
    }

    private fun saveCastProgress(r: com.ntv2.app.core.cast.RemotePlayback) {
        val mediaId = castMediaId ?: return
        val t = now()
        if (t - lastCastSaveAt < 5_000L || r.positionMs <= 0L) return
        lastCastSaveAt = t
        val duration = r.durationMs.takeIf { it > 0L } ?: uiState.value.durationSeconds * 1_000L
        viewModelScope.launch { runCatching { progressStore?.onProgress(mediaId, r.positionMs, duration) } }
    }

    private fun onCastStatus(status: com.ntv2.app.core.cast.CastStatus) {
        val connected = status is com.ntv2.app.core.cast.CastStatus.Connected
        val connecting = status is com.ntv2.app.core.cast.CastStatus.Connecting
        val available = status !is com.ntv2.app.core.cast.CastStatus.Unsupported &&
            status !is com.ntv2.app.core.cast.CastStatus.NoDevices
        _uiState.update { it.copy(castAvailable = available, castConnecting = connecting || (it.castConnecting && casting.value)) }
        when {
            connected && !casting.value -> startCasting((status as com.ntv2.app.core.cast.CastStatus.Connected).deviceName)
            !connected && !connecting && casting.value -> stopCasting()
        }
    }

    /** Abre a lista de Chromecasts (ou o controle do conectado). Precisa do contexto da Activity. */
    fun openCastPicker(activityContext: android.content.Context) {
        castManager?.showDevicePicker(activityContext)
    }

    /** Parar de transmitir (botão da tela): encerra a sessão; a volta ao local vem pelo status. */
    fun stopCastingByUser() {
        castManager?.endSession()
    }

    fun dismissCastMessage() {
        _uiState.update { it.copy(castMessage = null) }
    }

    private fun startCasting(deviceName: String) {
        val request = lastRequest ?: return
        val server = streamServer ?: return
        val manager = castManager ?: return
        if (uiState.value.isPlaceholderMode) {
            _uiState.update { it.copy(castMessage = "Aguarde o vídeo começar para transmitir.") }
            return
        }
        // Filme dividido: a URI e o tamanho são do filme todo (o snapshot só tem os da parte 1).
        val parts = partsLookup?.partsOf(request.fileId)
        val totalBytes = parts?.sumOf { it.sizeBytes }
            ?: playbackController.snapshot.value.expectedBytes?.takeIf { it > 0L }
            ?: run {
                _uiState.update { it.copy(castMessage = "Não foi possível transmitir: tamanho do vídeo desconhecido.") }
                return
            }
        val sourceUri = if (parts != null) {
            com.ntv2.app.core.multipart.MultiPartUris.forFirstFileId(request.fileId)
        } else {
            "tgfile://video/${request.fileId}"
        }
        val position = playbackController.player?.currentPosition ?: uiState.value.snapshot.currentPositionMs
        val mime = mimeTypeFor(castFileName)
        val url = server.serve(request.fileId, sourceUri, totalBytes, mime) ?: run {
            _uiState.update { it.copy(castMessage = "Conecte o celular ao mesmo Wi-Fi do Chromecast para transmitir.") }
            return
        }
        watchdogJob?.cancel()
        playbackController.suspendForCast()
        castMediaId = request.mediaId
        casting.value = true
        _uiState.update {
            it.copy(castingTo = deviceName, castConnecting = true, castEnded = false, castMessage = null, loadError = null)
        }
        manager.load(
            url = url,
            title = uiState.value.title,
            posterUrl = uiState.value.thumbnailPath,
            mimeType = mime,
            startPositionMs = position,
            durationMs = request.durationSeconds * 1_000L
        )
    }

    private fun stopCasting() {
        val position = castManager?.remote?.value?.positionMs ?: 0L
        casting.value = false
        castMediaId = null
        streamServer?.stop()
        _uiState.update { it.copy(castingTo = null, castConnecting = false) }
        // Voltou para o aparelho: continua daqui de onde o Chromecast parou.
        playbackController.resumeLocalAt(position)
        startWatchdog()
    }

    private fun mimeTypeFor(fileName: String?): String = when (fileName?.substringAfterLast('.', "")?.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> "video/mp4"
    }

    fun onAction(action: PlayerScreenAction) {
        when (action) {
            is PlayerScreenAction.Prepare -> {
                preparingFileId = action.fileId
                castFileName = com.ntv2.app.core.multipart.PartName.displayName(action.fileName)
                val details = mediaDetailsCache?.get(action.mediaId)
                _uiState.update {
                    it.copy(
                        title = details?.title ?: action.title,
                        channelName = action.channelName,
                        durationSeconds = action.durationSeconds,
                        // Filme dividido: sem o sufixo `.partNNofMM` (aparece no painel e serve para deduzir o ano).
                        fileName = com.ntv2.app.core.multipart.PartName.displayName(action.fileName),
                        thumbnailPath = details?.posterPath ?: action.thumbnailPath,
                        statusMessage = "preparando reprodução…",
                        details = details
                    )
                }
                startPrepareLoop(
                    PlaybackPrepareRequest(
                        mediaId = action.mediaId,
                        fileId = action.fileId,
                        title = action.title,
                        durationSeconds = action.durationSeconds
                    )
                )
            }

            PlayerScreenAction.Play -> when {
                casting.value -> castManager?.play()
                !uiState.value.isPlaceholderMode -> playbackController.play()
            }
            PlayerScreenAction.Pause -> when {
                casting.value -> castManager?.pause()
                !uiState.value.isPlaceholderMode -> playbackController.pause()
            }
            is PlayerScreenAction.SeekBy -> {
                if (casting.value) {
                    val current = castManager?.remote?.value?.positionMs ?: 0L
                    castManager?.seekTo((current + action.deltaMs).coerceAtLeast(0L))
                    return
                }
                if (uiState.value.isPlaceholderMode) return
                // Usa a posição REAL do player (o snapshot só atualiza em mudanças de estado).
                val current = playbackController.player?.currentPosition
                    ?: uiState.value.snapshot.currentPositionMs
                playbackController.seekTo((current + action.deltaMs).coerceAtLeast(0L))
            }
            is PlayerScreenAction.SeekTo -> {
                if (casting.value) {
                    castManager?.seekTo(action.positionMs.coerceAtLeast(0L))
                    return
                }
                if (uiState.value.isPlaceholderMode) return
                playbackController.seekTo(action.positionMs.coerceAtLeast(0L))
            }

            is PlayerScreenAction.SelectAudio -> if (!uiState.value.isPlaceholderMode) {
                playbackController.selectAudioTrack(action.id)
            }
            is PlayerScreenAction.SelectSubtitle -> if (!uiState.value.isPlaceholderMode) {
                playbackController.selectTextTrack(action.id)
            }

            // Transmitindo, sair do app não pausa: o vídeo segue no Chromecast.
            PlayerScreenAction.OnAppStop -> if (!uiState.value.isPlaceholderMode && !casting.value) playbackController.onAppStop()
            PlayerScreenAction.OnAppResume -> if (!uiState.value.isPlaceholderMode && !casting.value) playbackController.onAppResume()
            PlayerScreenAction.Release -> playbackController.release()
            PlayerScreenAction.RetryLoad -> {
                val request = lastRequest ?: return
                _uiState.update { it.copy(loadError = null, loadingHint = null) }
                if (uiState.value.isPlaceholderMode) {
                    startPrepareLoop(request)
                } else {
                    playbackController.retry()
                    startWatchdog()
                }
            }
        }
    }

    override fun onCleared() {
        // Saiu do player transmitindo: guarda a posição do Chromecast e encerra a transmissão.
        if (casting.value) {
            val mediaId = castMediaId
            val r = castManager?.remote?.value
            if (mediaId != null && r != null && r.positionMs > 0L) {
                val duration = r.durationMs.takeIf { it > 0L } ?: uiState.value.durationSeconds * 1_000L
                // Fora da Main e sem bloquear: o viewModelScope já foi cancelado aqui, e runBlocking
                // na Main (duas gravações no Room) era risco de ANR.
                val store = progressStore
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { store?.onProgress(mediaId, r.positionMs, duration) }
                }
            }
            casting.value = false
            streamServer?.stop()
            castManager?.endSession()
        }
        observeJob?.cancel()
        prepareJob?.cancel()
        watchdogJob?.cancel()
        playbackController.release()
        // Garante o cancelamento do download mesmo se a reprodução nunca iniciou (evita downloads
        // órfãos que acumulam e travam os próximos vídeos).
        playbackController.discardMedia(preparingFileId)
        super.onCleared()
    }

    /**
     * Prepara a reprodução; enquanto o arquivo ainda baixa os bytes iniciais, reexecuta a cada 1s.
     * Não há mais limite fixo de tempo: só desiste quando o download PARA de avançar. Download
     * parado é reiniciado no TDLib algumas vezes antes de virar erro (com o motivo real).
     */
    private fun startPrepareLoop(request: PlaybackPrepareRequest) {
        lastRequest = request
        prepareJob?.cancel()
        watchdogJob?.cancel()
        _uiState.update { it.copy(loadError = null, loadingHint = null, downloadProgress = null) }
        prepareJob = viewModelScope.launch {
            val stall = StallPolicy(nowMs = now(), initialBytes = -1L, maxRestarts = MAX_RESTARTS)
            val speed = SpeedMeter()
            var detail: String? = null
            while (true) {
                when (val result = playbackController.prepare(request)) {
                    PlaybackPrepareResult.Started -> {
                        _uiState.update {
                            it.copy(isPlaceholderMode = false, statusMessage = "reproduzindo", downloadProgress = null)
                        }
                        startWatchdog()
                        return@launch
                    }

                    is PlaybackPrepareResult.MissingSource -> {
                        val availability = result.availability
                        result.detail?.let { detail = it }
                        val t = now()
                        when (availability) {
                            is MediaAvailability.Downloading -> {
                                val progress = DownloadProgress(
                                    downloadedBytes = availability.downloadedBytes,
                                    expectedBytes = availability.expectedBytes,
                                    bytesPerSecond = speed.sample(availability.downloadedBytes, t)
                                )
                                _uiState.update {
                                    it.copy(
                                        isPlaceholderMode = true,
                                        statusMessage = "baixando o início do vídeo…",
                                        downloadProgress = progress
                                    )
                                }
                            }
                            MediaAvailability.TdlibFileUnavailable,
                            MediaAvailability.LocalFileMissing -> _uiState.update {
                                it.copy(isPlaceholderMode = true, statusMessage = "preparando reprodução…")
                            }
                            else -> {
                                fail(
                                    PlayerLoadError(
                                        title = "Vídeo inválido",
                                        message = "Os dados deste vídeo estão incompletos. Volte e abra de novo."
                                    )
                                )
                                return@launch
                            }
                        }

                        if (!networkReady.value) {
                            _uiState.update { it.copy(statusMessage = "conectando ao Telegram…") }
                        }
                        val downloaded = (availability as? MediaAvailability.Downloading)?.downloadedBytes ?: stall.lastBytes
                        // Nenhum byte ainda: o 1º contato com o servidor do canal pode demorar (limites maiores).
                        val neverStarted = maxOf(downloaded, stall.lastBytes) <= 0L
                        val storage = playbackController.storageBlockingDownload()
                        val action = stall.tick(
                            nowMs = t,
                            waiting = true,
                            downloadedBytes = downloaded,
                            networkReady = networkReady.value,
                            storageBlocked = storage != null,
                            restartAfterMs = if (neverStarted) FIRST_BYTE_RESTART_MS else STALL_RESTART_MS,
                            failAfterMs = if (neverStarted) FIRST_BYTE_FAIL_MS else STALL_FAIL_MS,
                            // Sem reconectar antes: no início reconectar recomeçava o contato com o servidor.
                            refreshAfterMs = null
                        )
                        when (action) {
                            is StallAction.Restart -> {
                                playbackController.refreshNetwork()
                                playbackController.restartDownload(request.fileId)
                                _uiState.update {
                                    it.copy(statusMessage = "download parado — reconectando (${action.attempt} de $MAX_RESTARTS)…")
                                }
                            }
                            is StallAction.FailStalled -> {
                                fail(
                                    if (availability is MediaAvailability.TdlibFileUnavailable && neverStarted) {
                                        PlayerLoadError(
                                            title = "Arquivo indisponível no Telegram",
                                            message = "O Telegram não liberou este arquivo. A postagem pode ter sido " +
                                                "removida ou o canal pode ter restrições.",
                                            detail = detail
                                        )
                                    } else {
                                        stalledError(action.lastBytes, detail)
                                    }
                                )
                                return@launch
                            }
                            StallAction.FailLowStorage -> {
                                fail(storageError(storage))
                                return@launch
                            }
                            StallAction.RefreshNetwork, StallAction.None -> Unit
                        }
                        delay(1_000L)
                    }

                    is PlaybackPrepareResult.InsufficientStorage -> {
                        fail(lowStorageError(result.freeBytes, result.requiredBytes))
                        return@launch
                    }

                    is PlaybackPrepareResult.Failed -> {
                        _uiState.update {
                            it.copy(
                                isPlaceholderMode = true,
                                statusMessage = result.message,
                                snapshot = it.snapshot.copy(
                                    state = PlaybackState.Error(
                                        message = result.message,
                                        recoverable = true
                                    )
                                )
                            )
                        }
                        fail(
                            PlayerLoadError(
                                title = "Não foi possível abrir o vídeo",
                                message = "O player não conseguiu iniciar este arquivo.",
                                detail = result.message
                            )
                        )
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * Depois que o player iniciou: vigia a espera pelo 1º quadro / buffer. Download parado →
     * reinicia a reprodução (até MAX_RESTARTS) e depois vira erro; download andando mas demorando
     * → aviso de que o vídeo precisa baixar mais; erro do ExoPlayer → tela de erro.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            val startedAt = now()
            val stall = StallPolicy(
                nowMs = startedAt,
                initialBytes = playbackController.snapshot.value.downloadedBytes,
                maxRestarts = MAX_RESTARTS,
                healthyResetMs = HEALTHY_RESET_MS
            )
            var everPlayed = false
            val speed = SpeedMeter()
            while (true) {
                delay(1_000L)
                val snap = playbackController.snapshot.value
                val t = now()
                val state = snap.state
                if (state is PlaybackState.Error && state.lowStorage) {
                    fail(lowStorageError(freeBytes = null, requiredBytes = null))
                    return@launch
                }
                if (state is PlaybackState.Error && state.partUnavailable != null) {
                    val part = state.partUnavailable
                    fail(
                        PlayerLoadError(
                            title = "Parte ${part.part} de ${part.total} indisponível",
                            message = "Essa parte do filme não está no canal (ou ainda não terminou de ser enviada). " +
                                "Avise quem postou o filme.",
                            detail = state.message
                        )
                    )
                    return@launch
                }
                if (state is PlaybackState.Error && state.unsupportedVideo != null) {
                    val what = state.unsupportedVideo.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
                    fail(
                        PlayerLoadError(
                            title = "Vídeo não suportado neste aparelho",
                            message = "O decodificador deste aparelho não reproduz este vídeo$what. " +
                                "Procure outra versão do arquivo, em outro formato ou resolução menor.",
                            detail = state.message
                        )
                    )
                    return@launch
                }
                if (state is PlaybackState.Error) {
                    fail(
                        PlayerLoadError(
                            title = "Erro na reprodução",
                            message = "O vídeo não pôde ser reproduzido neste aparelho ou o arquivo está corrompido.",
                            detail = state.message
                        )
                    )
                    return@launch
                }
                if (state == PlaybackState.Ready || state == PlaybackState.Paused || state == PlaybackState.Ended) {
                    everPlayed = everPlayed || state != PlaybackState.Paused || snap.currentPositionMs > 0L
                }
                val complete = snap.expectedBytes?.let { it > 0L && snap.downloadedBytes >= it } == true
                val waiting = (state == PlaybackState.Buffering || state == PlaybackState.Preparing) && !complete
                // Só consulta o espaço (StatFs) quando está esperando bytes.
                val storage = if (waiting) playbackController.storageBlockingDownload() else null
                val action = stall.tick(
                    nowMs = t,
                    waiting = waiting,
                    // Progresso da parte em leitura (monotônico): num filme dividido o total agregado seria
                    // mascarado pela pré-carga da parte seguinte.
                    downloadedBytes = snap.stallBytes,
                    networkReady = networkReady.value,
                    storageBlocked = storage != null,
                    restartAfterMs = STALL_RESTART_MS,
                    failAfterMs = STALL_FAIL_MS,
                    refreshAfterMs = STALL_NETWORK_REFRESH_MS
                )
                if (!waiting) {
                    if (uiState.value.downloadProgress != null || uiState.value.loadingHint != null) {
                        _uiState.update { it.copy(downloadProgress = null, loadingHint = null) }
                    }
                    continue
                }
                val hint = if (!everPlayed && t - startedAt >= SLOW_START_HINT_MS) {
                    PartsTexts.indexHint(snap.parts)
                } else null
                _uiState.update {
                    it.copy(
                        downloadProgress = DownloadProgress(
                            downloadedBytes = snap.downloadedBytes,
                            expectedBytes = snap.expectedBytes ?: 0L,
                            bytesPerSecond = speed.sample(snap.progressBytes, t),
                            parts = snap.parts
                        ),
                        loadingHint = hint
                    )
                }
                when (action) {
                    StallAction.RefreshNetwork -> playbackController.refreshNetwork()
                    is StallAction.Restart -> playbackController.retry()
                    is StallAction.FailStalled -> {
                        fail(stalledError(action.lastBytes, detail = null))
                        return@launch
                    }
                    StallAction.FailLowStorage -> {
                        fail(storageError(storage))
                        return@launch
                    }
                    StallAction.None -> Unit
                }
            }
        }
    }

    private fun stalledError(lastBytes: Long, detail: String?) = PlayerLoadError(
        title = "Download parado",
        message = "O Telegram parou de enviar este vídeo" +
            (if (lastBytes > 0L) " (${formatBytes(lastBytes)} recebidos)" else "") +
            ". Tente novamente em instantes.",
        detail = detail
    )

    /** Parado por falta de espaço (o player deixa de baixar de propósito): não é rede. */
    private fun storageError(storage: com.ntv2.app.core.storage.StorageSnapshot?) = lowStorageError(
        freeBytes = storage?.freeBytes,
        requiredBytes = storage?.let { StorageBudget.downloadFloor(it.totalBytes) }
    )

    private fun lowStorageError(freeBytes: Long?, requiredBytes: Long?): PlayerLoadError {
        val detail = if (freeBytes != null && requiredBytes != null) {
            "Livre: ${formatBytes(freeBytes)} · necessário: ${formatBytes(requiredBytes)}"
        } else null
        return PlayerLoadError(
            title = "Pouco espaço no aparelho",
            message = "O app já limpou o próprio cache, mas o armazenamento continua quase cheio. " +
                "Libere espaço em Configurações › Aplicativos e tente novamente.",
            detail = detail
        )
    }

    private fun fail(error: PlayerLoadError) {
        _uiState.update { it.copy(loadError = error, downloadProgress = null, loadingHint = null) }
    }

    private fun now(): Long = android.os.SystemClock.elapsedRealtime()

    /** Velocidade de download suavizada (média móvel) a partir de amostras de bytes acumulados. */
    private class SpeedMeter {
        private var lastBytes = -1L
        private var lastAt = 0L
        private var speed = 0L
        fun sample(bytes: Long, at: Long): Long {
            if (lastBytes < 0L) {
                lastBytes = bytes
                lastAt = at
                return speed
            }
            val dt = at - lastAt
            if (dt >= 900L) {
                val instant = (bytes - lastBytes).coerceAtLeast(0L) * 1000L / dt
                speed = if (speed == 0L) instant else (speed * 2 + instant) / 3
                lastBytes = bytes
                lastAt = at
            }
            return speed
        }
    }
}

class PlayerScreenViewModelFactory(
    private val playbackController: PlaybackController,
    private val mediaDetailsCache: MediaDetailsCache? = null,
    private val networkReady: StateFlow<Boolean> = MutableStateFlow(true),
    private val castManager: com.ntv2.app.core.cast.CastManager? = null,
    private val streamServer: com.ntv2.app.core.cast.LocalStreamServer? = null,
    private val progressStore: com.ntv2.app.core.player.progress.PlaybackProgressStore? = null,
    private val upNextQueue: com.ntv2.app.feature.media.domain.UpNextQueue? = null,
    private val partsLookup: com.ntv2.app.core.multipart.PartsLookup? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerScreenViewModel::class.java)) {
            return PlayerScreenViewModel(playbackController, mediaDetailsCache, networkReady, castManager, streamServer, progressStore, upNextQueue, partsLookup) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/** "12,3 MB", "1,4 GB", "850 KB". */
internal fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val br = java.util.Locale("pt", "BR")
    return when {
        bytes >= gb -> String.format(br, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(br, "%.1f MB", bytes / mb)
        else -> String.format(br, "%.0f KB", bytes / kb)
    }
}
