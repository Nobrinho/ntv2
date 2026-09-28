@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.session

import android.net.Uri
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.ntv2.app.core.player.MediaTrackOption
import com.ntv2.app.core.player.MediaTracksInfo
import java.util.Locale
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ntv2.app.core.player.PlaybackCoordinator
import com.ntv2.app.core.player.PlaybackMedia
import com.ntv2.app.core.player.PlaybackSnapshot
import com.ntv2.app.core.player.PlaybackState
import com.ntv2.app.core.player.io.GrowingFileDataSourceFactory
import com.ntv2.app.core.player.progress.PlaybackProgressStore
import com.ntv2.app.core.player.telegram.TelegramPlaybackDataSource
import com.ntv2.app.core.player.config.StreamProfiles
import com.ntv2.app.core.player.exoplayer.ExoPlayerProvider
import com.ntv2.app.core.player.exoplayer.DecoderTroubleMemory
import com.ntv2.app.core.player.exoplayer.DecoderWindows
import com.ntv2.app.core.player.exoplayer.VideoDecoderPolicy
import com.ntv2.app.core.storage.LowStorageException
import com.ntv2.app.core.storage.StorageBudget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
private const val MB = 1024L * 1024L
// Vídeo congelado: tocando (áudio/relógio avançando) sem nenhum quadro novo por esse tempo.
private const val VIDEO_FREEZE_RECOVER_MS = 3_000L
// Limite de recuperações por mídia (evita laço se o arquivo realmente não decodifica).
private const val MAX_FREEZE_RECOVERIES = 5
private const val FIRE_TV_DROPPED_FRAME_RECOVERY_THRESHOLD = 24
private const val FIRE_TV_RECOVERY_COOLDOWN_MS = 30_000L
// Tocando sem problema por esse tempo: zera os contadores de recuperação. Antes o limite valia para
// o filme inteiro — depois de algumas quedas de rede num filme de 2 h, a próxima já virava erro.
private const val HEALTHY_RESET_MS = 60_000L
private const val FRAME_STATS_WINDOW_S = 10
// Erro de I/O (rede/arquivo) do ExoPlayer: reconecta e retoma da mesma posição, com espera crescente.
private const val MAX_IO_RECOVERIES = 3
private const val IO_RECOVERY_BACKOFF_MS = 2_000L
// Erro do decodificador de vídeo (ex.: MediaTek do Fire TV com "DECODE ERROR FATAL"): recria e retoma.
private const val MAX_DECODER_ERROR_RECOVERIES = 6
// Falha também no decodificador de software = trecho corrompido: pula esse tanto (× nº de falhas).
private const val DECODER_ERROR_SKIP_MS = 2_000L

class DefaultPlaybackCoordinator(
    private val playbackDataSource: TelegramPlaybackDataSource,
    private val exoPlayerProvider: ExoPlayerProvider,
    private val dataSourceFactory: GrowingFileDataSourceFactory,
    private val progressStore: PlaybackProgressStore,
    /** Buffer do ExoPlayer em RAM (entra no cálculo do que guardar atrás no disco). */
    private val ramBufferBytes: Long = 64L * 1024L * 1024L,
    /** Faz o TDLib descartar as conexões e reconectar (sockets mortos após queda de Wi‑Fi). */
    private val refreshNetwork: () -> Unit = {},
    private val videoDecoderPolicy: VideoDecoderPolicy = VideoDecoderPolicy(),
    /** Onde o hardware falhou em cada vídeo: software só em volta desses pontos (ver DecoderWindows). */
    private val decoderTroubleMemory: DecoderTroubleMemory? = null
) : PlaybackCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotState = MutableStateFlow(PlaybackSnapshot())
    override val snapshot: StateFlow<PlaybackSnapshot> = snapshotState

    override val player: Player?
        get() = exoPlayer

    private var exoPlayer: ExoPlayer? = null
    // Última lista de faixas do ExoPlayer, para reaplicar seleção de áudio/legenda.
    private var lastTracks: Tracks? = null
    private var currentMedia: PlaybackMedia? = null
    private var observeJob: Job? = null
    private var progressJob: Job? = null
    private var freezeJob: Job? = null
    // Fechamento/remoção do arquivo da sessão anterior (roda em segundo plano).
    private var cleanupJob: Job? = null
    private var freezeRecoveries = 0
    private var freezeRecoveriesMediaId: String? = null
    private var wasPlayingBeforeStop: Boolean = false
    // Recuperação de troca de áudio: se a faixa escolhida não puder ser decodificada, o player dá
    // erro; voltamos ao áudio padrão e retomamos da mesma posição em vez de travar.
    private var pendingAudioSwitch: Boolean = false
    private var positionBeforeAudioSwitch: Long = 0L
    private var lastDecoderRecoveryAt = 0L
    private var ioRecoveries = 0
    private var ioRecoveryJob: Job? = null
    private var currentMediaIdForCounters: String? = null
    private var decoderErrorRecoveries = 0
    // Falhas do decodificador neste vídeo (congelamentos + erros); não zera com o tempo.
    private var decoderIncidents = 0
    // Posições (ms) em que o hardware falhou neste vídeo (memória + desta sessão).
    private var badPositions: List<Long> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val mapped = when (playbackState) {
                Player.STATE_IDLE -> PlaybackState.Idle
                Player.STATE_BUFFERING -> PlaybackState.Buffering
                Player.STATE_READY -> if (exoPlayer?.isPlaying == true) PlaybackState.Ready else PlaybackState.Paused
                Player.STATE_ENDED -> PlaybackState.Ended
                else -> PlaybackState.Idle
            }
            snapshotState.update {
                it.copy(
                    state = mapped,
                    isPlaying = exoPlayer?.isPlaying == true,
                    currentPositionMs = exoPlayer?.currentPosition ?: it.currentPositionMs,
                    bufferedPositionMs = exoPlayer?.bufferedPosition ?: it.bufferedPositionMs
                )
            }
            if (playbackState == Player.STATE_READY) {
                // Troca de áudio concluída com sucesso: não precisa mais monitorar erro dela.
                pendingAudioSwitch = false
            }
            if (playbackState == Player.STATE_ENDED) {
                // Assistido até o fim: limpa o progresso para não retomar no finzinho.
                currentMedia?.let { media -> scope.launch { progressStore.clear(media.mediaId) } }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Sem isso isPlaying só mudava junto do estado (keepScreenOn/watchdog liam valor velho).
            snapshotState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            lastTracks = tracks
            snapshotState.update { it.copy(tracks = buildTracksInfo(tracks)) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = exoPlayer
            // Erro logo após trocar de áudio: a faixa escolhida não pôde ser decodificada neste
            // aparelho. Em vez de travar, volta ao áudio padrão (auto) e retoma da mesma posição.
            if (pendingAudioSwitch && player != null) {
                pendingAudioSwitch = false
                android.util.Log.w("NTV2Audio", "erro ao trocar áudio, revertendo p/ padrão: ${error.errorCodeName}")
                runCatching {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .build()
                    player.prepare()
                    player.seekTo(positionBeforeAudioSwitch)
                    player.playWhenReady = true
                }
                snapshotState.update {
                    it.copy(state = PlaybackState.Buffering, isPlaying = false)
                }
                return
            }
            val lowStorage = generateSequence<Throwable>(error) { it.cause }
                .firstOrNull { it is LowStorageException }
            // Decodificador de vídeo falhou (4xxx): recria na mesma posição; se já é recorrente
            // neste vídeo, troca para o decodificador de software.
            val isDecoderError = error.errorCode in
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED until PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
            if (isDecoderError && player != null && currentMedia != null &&
                decoderErrorRecoveries < MAX_DECODER_ERROR_RECOVERIES
            ) {
                decoderErrorRecoveries++
                val position = player.currentPosition
                // Já no software e falhou de novo: o trecho do arquivo está corrompido (nenhum
                // decodificador passa). Pula um pouco à frente em vez de tentar o mesmo ponto — cada
                // tentativa é uma parada. Pulo crescente enquanto as falhas se repetem.
                val alreadySoftware = videoDecoderPolicy.preferSoftware
                val skipMs = if (alreadySoftware) DECODER_ERROR_SKIP_MS * decoderErrorRecoveries else 0L
                val resumeAt = (position + skipMs).let { target ->
                    val duration = player.duration
                    if (duration > 0L) target.coerceAtMost(duration - 1_000L) else target
                }
                onDecoderIncident(
                    position,
                    "erro ${error.errorCodeName} em ${position}ms" +
                        if (skipMs > 0L) "; trecho corrompido, pulando ${skipMs / 1000}s" else ""
                )
                snapshotState.update { it.copy(state = PlaybackState.Buffering, isPlaying = false) }
                restartDecoderAt(resumeAt)
                return
            }
            // Erro de rede/arquivo (ex.: download parado): em vez de tela de erro, reconecta o TDLib
            // e retoma da mesma posição, algumas vezes, com espera crescente.
            val isIoError = error.errorCode in
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED until PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
            if (lowStorage == null && isIoError && player != null && currentMedia != null &&
                ioRecoveries < MAX_IO_RECOVERIES
            ) {
                ioRecoveries++
                val position = player.currentPosition
                val backoffMs = IO_RECOVERY_BACKOFF_MS * ioRecoveries
                android.util.Log.w(
                    "NtvPlayer",
                    "erro de I/O ${error.errorCodeName} em ${position}ms — reconectando e retomando em " +
                        "${backoffMs}ms ($ioRecoveries/$MAX_IO_RECOVERIES)"
                )
                refreshNetwork()
                snapshotState.update { it.copy(state = PlaybackState.Buffering, isPlaying = false) }
                ioRecoveryJob?.cancel()
                ioRecoveryJob = scope.launch(Dispatchers.Main) {
                    kotlinx.coroutines.delay(backoffMs)
                    retryAt(position)
                }
                return
            }
            snapshotState.update {
                it.copy(
                    state = PlaybackState.Error(
                        message = lowStorage?.message ?: error.localizedMessage ?: "Falha de reprodução",
                        recoverable = true,
                        lowStorage = lowStorage != null
                    ),
                    isPlaying = false
                )
            }
        }
    }

    override suspend fun prepare(media: PlaybackMedia) {
        stopInternal(closeSession = true)
        // Espera o fechamento da sessão anterior ANTES de reabrir: no retry o arquivo é o mesmo, e
        // um close que terminasse depois do open apagava o estado recém-aberto (caminho vazio →
        // "Arquivo não encontrado" → Source error). Vale também para sair e reabrir o mesmo vídeo.
        cleanupJob?.join()
        cleanupJob = null

        snapshotState.update {
            it.copy(
                state = PlaybackState.Preparing,
                isPlaying = false,
                activeMediaId = media.mediaId,
                currentPositionMs = media.startPositionMs
            )
        }

        if (currentMediaIdForCounters != media.mediaId) {
            currentMediaIdForCounters = media.mediaId
            ioRecoveries = 0
            decoderErrorRecoveries = 0
            decoderIncidents = 0
            badPositions = runCatching { decoderTroubleMemory?.badPositions(media.mediaId) }.getOrNull().orEmpty()
            if (badPositions.isNotEmpty()) {
                android.util.Log.i(
                    "NtvPlayer",
                    "pontos de falha do hardware lembrados: ${badPositions.joinToString { "${it / 1000}s" }}"
                )
            }
        }
        // Começa no software só se a posição inicial cair numa janela em volta de um ponto de falha.
        videoDecoderPolicy.preferSoftware = DecoderWindows.softwareUntil(media.startPositionMs, badPositions) != null
        currentMedia = media
        val handle = playbackDataSource.open(media.fileId)
        applyStreamProfile(media, handle.expectedBytes, handle.localPath)
        snapshotState.update {
            it.copy(
                downloadedBytes = handle.downloadedBytes,
                expectedBytes = handle.expectedBytes
            )
        }

        // Sem pedido extra do início aqui: open() já pede os primeiros MB, e o TDLib tem UM trecho
        // de download por arquivo — um segundo pedido em 0 puxava o download de volta ao começo
        // justo quando o player pede a posição de retomada.

        // Um único ExoPlayer reaproveitado entre preparos (retry, troca de episódio).
        val playerInstance = exoPlayer ?: exoPlayerProvider.create()
        exoPlayer = playerInstance
        playerInstance.clearMediaItems()
        playerInstance.removeListener(playerListener)
        playerInstance.addListener(playerListener)

        val mediaItem = MediaItem.Builder()
            .setMediaId(media.mediaId)
            .setUri(Uri.parse(media.sourceUri))
            .build()

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        playerInstance.setMediaSource(mediaSource)
        playerInstance.prepare()
        playerInstance.seekTo(media.startPositionMs)
        playerInstance.playWhenReady = true

        observeFileState(media)
        startProgressSaving(media)
        startFreezeWatch(media)
    }

    /** Dimensiona janela à frente / atrás no disco pelo bitrate deste vídeo (até 4K). */
    private fun applyStreamProfile(media: PlaybackMedia, expectedBytes: Long, localPath: String) {
        val (free, total) = runCatching {
            val dir = java.io.File(localPath).parentFile ?: return@runCatching null to 0L
            val stat = android.os.StatFs(dir.path)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault(null to 0L)
        val profile = StreamProfiles.forMedia(
            expectedBytes = expectedBytes,
            durationMs = media.durationMs,
            ramBufferBytes = ramBufferBytes,
            freeBytes = free,
            downloadFloor = StorageBudget.downloadFloor(total)
        )
        dataSourceFactory.setProfile(media.fileId, profile)
        android.util.Log.i(
            "NtvPlayer",
            "perfil fileId=${media.fileId} ~${profile.bytesPerSecond * 8 / 1_000_000}Mbps " +
                "adiante=${profile.aheadWindowBytes / MB}MB atrás=${profile.keepBehindBytes / MB}MB " +
                "janelaDisco=${profile.diskWindowEnabled}"
        )
    }

    private fun startProgressSaving(media: PlaybackMedia) {
        progressJob?.cancel()
        // Roda na Main (ExoPlayer só pode ser lido na sua thread) e salva periodicamente,
        // para não perder a posição se o app for encerrado.
        progressJob = scope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(PROGRESS_SAVE_INTERVAL_MS)
                persistCurrentProgress(media)
            }
        }
    }

    /**
     * Detecta vídeo congelado com áudio tocando: o decodificador de hardware pode morrer sem
     * reportar erro ao ExoPlayer (visto no MediaTek do Fire TV com PPS repetido a cada
     * quadro-chave: "Fail to parse PPS"). Sem quadros novos por [VIDEO_FREEZE_RECOVER_MS] enquanto
     * a posição avança → prepara de novo na mesma posição, o que recria o decodificador.
     */
    private fun startFreezeWatch(media: PlaybackMedia) {
        freezeJob?.cancel()
        if (freezeRecoveriesMediaId != media.mediaId) {
            freezeRecoveriesMediaId = media.mediaId
            freezeRecoveries = 0
        }
        freezeJob = scope.launch(Dispatchers.Main) {
            var lastFrames = -1L
            var lastDroppedFrames = -1L
            var lastPositionMs = 0L
            var frozenForMs = 0L
            var healthyForMs = 0L
            // Resumo de quadros a cada FRAME_STATS_WINDOW_S segundos tocando (diagnóstico de engasgo).
            var statsTicks = 0
            var statsRendered = 0L
            var statsDropped = 0L
            var statsSkipped = 0L
            var lastRendered = -1L
            var lastSkipped = -1L
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                val player = exoPlayer ?: continue
                val counters = player.videoDecoderCounters
                if (counters == null || !player.isPlaying || player.playbackState != Player.STATE_READY) {
                    lastFrames = -1L
                    lastDroppedFrames = -1L
                    frozenForMs = 0L
                    healthyForMs = 0L
                    lastRendered = -1L
                    lastSkipped = -1L
                    continue
                }
                counters.ensureUpdated()
                val frames = counters.renderedOutputBufferCount.toLong() +
                    counters.droppedBufferCount + counters.skippedOutputBufferCount
                val positionMs = player.currentPosition
                val droppedFrames = counters.droppedBufferCount.toLong()
                val newlyDropped = if (lastDroppedFrames >= 0L) droppedFrames - lastDroppedFrames else 0L
                lastDroppedFrames = droppedFrames
                val rendered = counters.renderedOutputBufferCount.toLong()
                val skipped = counters.skippedOutputBufferCount.toLong()
                if (lastRendered >= 0L) {
                    statsRendered += rendered - lastRendered
                    statsSkipped += skipped - lastSkipped
                    statsDropped += newlyDropped
                }
                lastRendered = rendered
                lastSkipped = skipped
                if (++statsTicks >= FRAME_STATS_WINDOW_S) {
                    if (statsDropped > 0L || statsSkipped > 0L) {
                        val total = (statsRendered + statsDropped + statsSkipped).coerceAtLeast(1L)
                        android.util.Log.w(
                            "NtvPlayer",
                            "quadros em ${FRAME_STATS_WINDOW_S}s: exibidos=$statsRendered descartados=$statsDropped " +
                                "pulados=$statsSkipped (${(statsDropped + statsSkipped) * 100 / total}% perdidos) " +
                                "pos=${player.currentPosition / 1000}s"
                        )
                    }
                    statsTicks = 0
                    statsRendered = 0L
                    statsDropped = 0L
                    statsSkipped = 0L
                }
                val clockAdvanced = positionMs - lastPositionMs >= 700L
                frozenForMs = if (lastFrames >= 0L && frames == lastFrames && clockAdvanced) frozenForMs + 1_000L else 0L
                lastFrames = frames
                lastPositionMs = positionMs
                healthyForMs = if (frozenForMs == 0L && newlyDropped < FIRE_TV_DROPPED_FRAME_RECOVERY_THRESHOLD) {
                    healthyForMs + 1_000L
                } else 0L
                // Janelas de software: troca antes de chegar a um ponto onde o hardware falhou e volta
                // ao hardware depois de passar (ou ao sair da janela por um avanço/retrocesso).
                if (VideoDecoderPolicy.softwareCapable(snapshotState.value.tracks.videoHeight)) {
                    val softwareUntil = DecoderWindows.softwareUntil(positionMs, badPositions)
                    if ((softwareUntil != null) != videoDecoderPolicy.preferSoftware) {
                        videoDecoderPolicy.preferSoftware = softwareUntil != null
                        android.util.Log.i(
                            "NtvPlayer",
                            if (softwareUntil != null) {
                                "entrando em trecho de falha em ${positionMs / 1000}s — decodificador de software " +
                                    "até ${softwareUntil / 1000}s"
                            } else {
                                "trecho de falha passou em ${positionMs / 1000}s — voltando ao hardware"
                            }
                        )
                        restartDecoderAt(positionMs)
                        continue
                    }
                }
                if (healthyForMs >= HEALTHY_RESET_MS &&
                    (freezeRecoveries > 0 || ioRecoveries > 0 || decoderErrorRecoveries > 0)
                ) {
                    freezeRecoveries = 0
                    ioRecoveries = 0
                    decoderErrorRecoveries = 0
                }
                val affectedFireTvVp9 = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) &&
                    Build.MODEL.equals("AFTKM", ignoreCase = true) &&
                    snapshotState.value.tracks.videoMimeType == MimeTypes.VIDEO_VP9
                val now = android.os.SystemClock.elapsedRealtime()
                if (affectedFireTvVp9 &&
                    newlyDropped >= FIRE_TV_DROPPED_FRAME_RECOVERY_THRESHOLD &&
                    now - lastDecoderRecoveryAt >= FIRE_TV_RECOVERY_COOLDOWN_MS &&
                    freezeRecoveries < MAX_FREEZE_RECOVERIES
                ) {
                    freezeRecoveries++
                    lastDecoderRecoveryAt = now
                    android.util.Log.w(
                        "NtvPlayer",
                        "VP9/AFTKM descartou $newlyDropped quadros em 1s na posição ${positionMs}ms — " +
                            "recriando decodificador ($freezeRecoveries/$MAX_FREEZE_RECOVERIES)"
                    )
                    retry()
                    return@launch
                }
                if (frozenForMs >= VIDEO_FREEZE_RECOVER_MS && freezeRecoveries < MAX_FREEZE_RECOVERIES) {
                    freezeRecoveries++
                    onDecoderIncident(positionMs, "congelamento em ${positionMs}ms")
                    android.util.Log.w(
                        "NtvPlayer",
                        "vídeo congelado há ${frozenForMs}ms em ${positionMs}ms — recriando decodificador " +
                            "($freezeRecoveries/$MAX_FREEZE_RECOVERIES)"
                    )
                    restartDecoderAt(positionMs)
                    continue
                }
            }
        }
    }

    /**
     * Conta uma falha do decodificador. Falha do HARDWARE (até 1080p): guarda a posição (nesta sessão e
     * na memória do vídeo) e liga o software para a janela em volta dela; nas próximas vezes o app já
     * troca antes de chegar lá. Falha já no software: só conta (o chamador pula o trecho).
     */
    private fun onDecoderIncident(positionMs: Long, reason: String) {
        decoderIncidents++
        val height = snapshotState.value.tracks.videoHeight
        if (!videoDecoderPolicy.preferSoftware && VideoDecoderPolicy.softwareCapable(height)) {
            badPositions = DecoderWindows.addPoint(badPositions, positionMs)
            currentMedia?.mediaId?.let { id -> runCatching { decoderTroubleMemory?.remember(id, positionMs) } }
            videoDecoderPolicy.preferSoftware = DecoderWindows.softwareUntil(positionMs, badPositions) != null
        }
        android.util.Log.w(
            "NtvPlayer",
            "falha do decodificador ($reason) — $decoderIncidents neste vídeo; " +
                (if (videoDecoderPolicy.preferSoftware) "decodificador de software neste trecho" else "recriando")
        )
    }

    /**
     * Recria o decodificador em [positionMs] sem reabrir a sessão do arquivo: stop/prepare solta e
     * reinicializa os decodificadores (o seletor escolhe hardware ou software de novo), mas o download
     * no TDLib segue — mais leve que [retryAt], que fecha e reabre tudo. Na Main.
     */
    private fun restartDecoderAt(positionMs: Long) {
        val player = exoPlayer ?: return
        player.stop()
        player.prepare()
        player.seekTo(positionMs.coerceAtLeast(0L))
        player.playWhenReady = true
    }

    private suspend fun persistCurrentProgress(media: PlaybackMedia) {
        val player = exoPlayer ?: return
        val positionMs = player.currentPosition
        val durationMs = player.duration.takeIf { it > 0L } ?: media.durationMs
        progressStore.onProgress(media.mediaId, positionMs, durationMs)
    }

    override fun play() {
        exoPlayer?.playWhenReady = true
        snapshotState.update { it.copy(state = PlaybackState.Ready, isPlaying = true) }
    }

    override fun pause() {
        exoPlayer?.playWhenReady = false
        snapshotState.update { it.copy(state = PlaybackState.Paused, isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        val playerInstance = exoPlayer ?: return
        playerInstance.seekTo(positionMs)
        snapshotState.update { it.copy(state = PlaybackState.Buffering) }
        // O ExoPlayer reabre a fonte na nova posição e a GrowingFileDataSource solicita o range
        // necessário. Não emitimos DownloadFile aqui para não competir pelo offset único do TDLib.
    }

    override fun retry() {
        val media = currentMedia ?: return
        val position = exoPlayer?.currentPosition ?: media.startPositionMs
        // Na Main: o prepare mexe no ExoPlayer, que só pode ser acessado na thread dele. Na scope de
        // IO o app fechava ("Player is accessed on the wrong thread") quando o watchdog reiniciava.
        scope.launch(Dispatchers.Main) {
            prepare(media.copy(startPositionMs = position))
        }
    }

    override fun retryAt(positionMs: Long) {
        val media = currentMedia ?: return
        scope.launch(Dispatchers.Main) {
            prepare(media.copy(startPositionMs = positionMs.coerceAtLeast(0L)))
        }
    }

    override fun stop() {
        stopInternal(closeSession = false)
    }

    override fun onAppStop() {
        wasPlayingBeforeStop = exoPlayer?.isPlaying == true
        pause()
    }

    override fun onAppResume() {
        if (wasPlayingBeforeStop) {
            play()
        }
    }

    override fun selectAudioTrack(id: String) {
        val player = exoPlayer ?: return
        val override = overrideFor(id) ?: return
        // Guarda a posição/estado para poder reverter se a faixa não decodificar (ver onPlayerError).
        positionBeforeAudioSwitch = player.currentPosition
        pendingAudioSwitch = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(override)
            .build()
    }

    override fun selectTextTrack(id: String?) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        if (id == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val override = overrideFor(id) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(override)
        }
        player.trackSelectionParameters = builder.build()
    }

    /** Constrói o override de seleção a partir do id "grupo:faixa" sobre a última lista de faixas. */
    private fun overrideFor(id: String): TrackSelectionOverride? {
        val (groupIndex, trackIndex) = parseTrackId(id) ?: return null
        val group = lastTracks?.groups?.getOrNull(groupIndex) ?: return null
        return TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
    }

    override suspend fun restartDownload(fileId: Int) {
        if (fileId <= 0) return
        runCatching { playbackDataSource.close(fileId) }
    }

    override fun discardMedia(fileId: Int) {
        if (fileId <= 0) return
        scope.launch { playbackDataSource.deleteFile(fileId) }
    }

    override fun release() {
        stopInternal(closeSession = true, deleteFile = true)
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        snapshotState.value = PlaybackSnapshot()
    }

    private fun observeFileState(media: PlaybackMedia) {
        observeJob?.cancel()
        observeJob = scope.launch {
            playbackDataSource.observe(media.fileId).collect { fileState ->
                snapshotState.update {
                    it.copy(
                        downloadedBytes = fileState.downloadedBytes,
                        expectedBytes = fileState.expectedBytes
                    )
                }
            }
        }
    }

    private fun buildTracksInfo(tracks: Tracks): MediaTracksInfo {
        var videoWidth = 0
        var videoHeight = 0
        var videoFrameRate = 0f
        var videoMimeType: String? = null
        val audios = mutableListOf<MediaTrackOption>()
        val subtitles = mutableListOf<MediaTrackOption>()

        tracks.groups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val selected = group.isTrackSelected(trackIndex)
                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> if (selected || videoHeight == 0) {
                        if (format.width > 0) videoWidth = format.width
                        if (format.height > 0) videoHeight = format.height
                        if (format.frameRate > 0f) videoFrameRate = format.frameRate
                        videoMimeType = format.sampleMimeType
                    }
                    // Sem filtro de suporte: lista todas as faixas de áudio do container
                    // (ex.: 2º áudio/dublagem), como a versão anterior fazia. A reprodução
                    // usa override manual e o ExoPlayer decodifica por software se preciso.
                    C.TRACK_TYPE_AUDIO -> audios += MediaTrackOption(
                        id = "$groupIndex:$trackIndex",
                        label = audioLabel(format, audios.size),
                        isSelected = selected
                    )
                    C.TRACK_TYPE_TEXT -> if (group.isTrackSupported(trackIndex, true)) {
                        subtitles += MediaTrackOption(
                            id = "$groupIndex:$trackIndex",
                            label = trackLabel(format, subtitles.size, "Legenda"),
                            isSelected = selected
                        )
                    }
                }
            }
        }
        return MediaTracksInfo(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            videoFrameRate = videoFrameRate,
            videoMimeType = videoMimeType,
            audios = audios,
            subtitles = subtitles
        )
    }

    private fun audioLabel(format: Format, index: Int): String {
        val base = trackLabel(format, index, "Áudio")
        val channels = when {
            format.channelCount >= 6 -> "5.1"
            format.channelCount == 2 -> "estéreo"
            format.channelCount == 1 -> "mono"
            else -> null
        }
        return if (channels != null) "$base · $channels" else base
    }

    private fun trackLabel(format: Format, index: Int, fallbackPrefix: String): String {
        format.label?.takeIf { it.isNotBlank() }?.let { return it }
        languageDisplay(format.language)?.let { return it }
        return "$fallbackPrefix ${index + 1}"
    }

    private fun languageDisplay(code: String?): String? {
        if (code.isNullOrBlank() || code == "und") return null
        val display = Locale(code).getDisplayLanguage(Locale("pt", "BR"))
        return display.takeIf { it.isNotBlank() && it != code }
            ?.replaceFirstChar { it.uppercase() }
            ?: code.uppercase()
    }

    private fun parseTrackId(id: String): Pair<Int, Int>? {
        val parts = id.split(":")
        if (parts.size != 2) return null
        val g = parts[0].toIntOrNull() ?: return null
        val t = parts[1].toIntOrNull() ?: return null
        return g to t
    }

    private fun stopInternal(closeSession: Boolean, deleteFile: Boolean = false) {
        ioRecoveryJob?.cancel()
        ioRecoveryJob = null
        observeJob?.cancel()
        observeJob = null
        progressJob?.cancel()
        progressJob = null
        freezeJob?.cancel()
        freezeJob = null

        val media = currentMedia
        // Captura a posição ANTES de parar o player (currentPosition zera após stop) e salva.
        if (media != null) {
            val positionMs = exoPlayer?.currentPosition ?: 0L
            val durationMs = exoPlayer?.duration?.takeIf { it > 0L } ?: media.durationMs
            scope.launch { progressStore.onProgress(media.mediaId, positionMs, durationMs) }
        }

        exoPlayer?.playWhenReady = false
        exoPlayer?.stop()

        if (closeSession && media != null) {
            val fileId = media.fileId
            cleanupJob = scope.launch {
                // Ao sair da reprodução, remove o arquivo do TDLib para não acumular no
                // armazenamento (o Fire TV tem pouco espaço). Nas demais paradas, apenas fecha.
                if (deleteFile) {
                    playbackDataSource.deleteFile(fileId)
                } else {
                    playbackDataSource.close(fileId)
                }
            }
            currentMedia = null
        }
        lastTracks = null

        snapshotState.update {
            it.copy(
                state = PlaybackState.Idle,
                isPlaying = false,
                tracks = MediaTracksInfo()
            )
        }
    }
}
