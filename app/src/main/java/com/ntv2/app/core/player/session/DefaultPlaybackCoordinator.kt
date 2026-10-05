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
import com.ntv2.app.core.multipart.PartsLookup
import com.ntv2.app.core.multipart.allFileIds
import com.ntv2.app.core.player.MediaTrackOption
import com.ntv2.app.core.player.MediaTracksInfo
import java.util.Locale
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.ntv2.app.core.player.exoplayer.DolbyVisionFallbackExtractorsFactory
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
import com.ntv2.app.core.player.recovery.PlayerRecoveryPolicy
import com.ntv2.app.core.player.recovery.RecoveryAction
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
// Quadros descartados em 1 s que contam como rajada (VP9 no AFTKM) e como "não saudável".
private const val DROP_BURST_FRAMES = 24
private const val FRAME_STATS_WINDOW_S = 10

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
    private val decoderTroubleMemory: DecoderTroubleMemory? = null,
    /** Filmes divididos em partes: o `fileId` da parte 1 identifica o filme (ver MultiPartRegistry). */
    private val partsLookup: PartsLookup? = null
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
    private var wasPlayingBeforeStop: Boolean = false
    // Recuperação de troca de áudio: se a faixa escolhida não puder ser decodificada, o player dá
    // erro; voltamos ao áudio padrão e retomamos da mesma posição em vez de travar.
    private var pendingAudioSwitch: Boolean = false
    private var positionBeforeAudioSwitch: Long = 0L
    private var ioRecoveryJob: Job? = null
    // Regras de recuperação (limites, janelas de software, pulos) — ver PlayerRecoveryPolicy.
    private val recovery = PlayerRecoveryPolicy()

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
            val isDecoderError = error.errorCode in
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED until PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
            val isIoError = error.errorCode in
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED until PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
            if (player != null && currentMedia != null) {
                val position = player.currentPosition
                val action = when {
                    isDecoderError -> recovery.onDecoderError(
                        positionMs = position,
                        durationMs = player.duration,
                        videoHeight = snapshotState.value.tracks.videoHeight,
                        usingSoftware = videoDecoderPolicy.preferSoftware
                    )
                    isIoError && lowStorage == null -> recovery.onIoError(position)
                    else -> RecoveryAction.GiveUp
                }
                if (applyRecovery(action, "erro ${error.errorCodeName} em ${position}ms")) return
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

        val newMedia = recovery.startMedia(media.mediaId) {
            runCatching { decoderTroubleMemory?.badPositions(media.mediaId) }.getOrNull().orEmpty()
        }
        if (newMedia && recovery.badPositions.isNotEmpty()) {
            android.util.Log.i(
                "NtvPlayer",
                "pontos de falha do hardware lembrados: ${recovery.badPositions.joinToString { "${it / 1000}s" }}"
            )
        }
        // Começa no software só se a posição inicial cair numa janela em volta de um ponto de falha
        // (altura ainda desconhecida aqui; os pontos só são gravados para vídeos até 1080p).
        videoDecoderPolicy.preferSoftware = recovery.softwareWanted(media.startPositionMs, videoHeight = 0)
        currentMedia = media
        val handle = playbackDataSource.open(media.fileId)
        // Filme dividido: o bitrate vem do tamanho TOTAL (a parte 1 é só uma fatia).
        val totalBytes = partsLookup?.partsOf(media.fileId)?.sumOf { it.sizeBytes } ?: handle.expectedBytes
        applyStreamProfile(media, totalBytes, handle.localPath)
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

        // DV perfil 7 sem decodificador DV no aparelho vira HEVC (camada base); senão tocava só o áudio.
        val mediaSource = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            DolbyVisionFallbackExtractorsFactory(DefaultExtractorsFactory())
        ).createMediaSource(mediaItem)

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
        // Cada parte de um filme dividido é lida como um arquivo: todas usam o perfil do filme.
        partsLookup.allFileIds(media.fileId).forEach { dataSourceFactory.setProfile(it, profile) }
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
        freezeJob = scope.launch(Dispatchers.Main) {
            var lastFrames = -1L
            var lastDroppedFrames = -1L
            var lastPositionMs = 0L
            var frozenForMs = 0L
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
                    recovery.onNotPlaying()
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
                recovery.onPlayingSecond(healthy = frozenForMs == 0L && newlyDropped < DROP_BURST_FRAMES)
                val videoHeight = snapshotState.value.tracks.videoHeight
                // Janelas de software: troca antes de chegar a um ponto onde o hardware falhou e volta
                // ao hardware depois de passar (ou ao sair da janela por um avanço/retrocesso).
                if (VideoDecoderPolicy.softwareCapable(videoHeight)) {
                    val wantSoftware = recovery.softwareWanted(positionMs, videoHeight)
                    if (wantSoftware != videoDecoderPolicy.preferSoftware) {
                        android.util.Log.i(
                            "NtvPlayer",
                            if (wantSoftware) {
                                val until = DecoderWindows.softwareUntil(positionMs, recovery.badPositions) ?: 0L
                                "entrando em trecho de falha em ${positionMs / 1000}s — decodificador de software " +
                                    "até ${until / 1000}s"
                            } else {
                                "trecho de falha passou em ${positionMs / 1000}s — voltando ao hardware"
                            }
                        )
                        applyRecovery(RecoveryAction.RestartDecoder(positionMs, useSoftware = wantSoftware), reason = null)
                        continue
                    }
                }
                val affectedFireTvVp9 = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) &&
                    Build.MODEL.equals("AFTKM", ignoreCase = true) &&
                    snapshotState.value.tracks.videoMimeType == MimeTypes.VIDEO_VP9
                if (affectedFireTvVp9 && newlyDropped >= DROP_BURST_FRAMES) {
                    val action = recovery.onDropBurst(
                        positionMs,
                        nowMs = android.os.SystemClock.elapsedRealtime(),
                        usingSoftware = videoDecoderPolicy.preferSoftware
                    )
                    if (applyRecovery(action, "VP9/AFTKM descartou $newlyDropped quadros em 1s em ${positionMs}ms")) continue
                }
                if (frozenForMs >= VIDEO_FREEZE_RECOVER_MS) {
                    val action = recovery.onFreeze(positionMs, videoHeight, usingSoftware = videoDecoderPolicy.preferSoftware)
                    if (applyRecovery(action, "vídeo congelado há ${frozenForMs}ms em ${positionMs}ms")) continue
                }
            }
        }
    }

    /**
     * Executa a decisão da [PlayerRecoveryPolicy]. Retorna true se tratou (false = [RecoveryAction.GiveUp],
     * o chamador mostra o erro; [RecoveryAction.None] conta como tratado). [reason] null = troca planejada
     * de decodificador (janela de software), sem log de falha. Na Main.
     */
    private fun applyRecovery(action: RecoveryAction, reason: String?): Boolean {
        when (action) {
            RecoveryAction.None -> return true
            RecoveryAction.GiveUp -> return false
            is RecoveryAction.RestartDecoder -> {
                action.newBadPositionMs?.let { position ->
                    currentMedia?.mediaId?.let { id -> runCatching { decoderTroubleMemory?.remember(id, position) } }
                }
                videoDecoderPolicy.preferSoftware = action.useSoftware
                if (reason != null) {
                    android.util.Log.w(
                        "NtvPlayer",
                        "falha do decodificador ($reason) — ${recovery.decoderIncidents} neste vídeo; " +
                            (if (action.skippedMs > 0L) "trecho corrompido, pulando ${action.skippedMs / 1000}s; " else "") +
                            (if (action.useSoftware) "decodificador de software neste trecho" else "recriando")
                    )
                    snapshotState.update { it.copy(state = PlaybackState.Buffering, isPlaying = false) }
                }
                restartDecoderAt(action.positionMs)
                return true
            }
            is RecoveryAction.Reopen -> {
                // Erro de rede/arquivo: reconecta o TDLib e reabre na mesma posição, com espera crescente.
                android.util.Log.w(
                    "NtvPlayer",
                    "$reason — reconectando e retomando em ${action.delayMs}ms " +
                        "(${recovery.ioErrorCount}/${PlayerRecoveryPolicy.MAX_IO_ERRORS})"
                )
                refreshNetwork()
                snapshotState.update { it.copy(state = PlaybackState.Buffering, isPlaying = false) }
                ioRecoveryJob?.cancel()
                ioRecoveryJob = scope.launch(Dispatchers.Main) {
                    kotlinx.coroutines.delay(action.delayMs)
                    retryAt(action.positionMs)
                }
                return true
            }
        }
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
        partsLookup.allFileIds(fileId).forEach { runCatching { playbackDataSource.close(it) } }
    }

    override fun discardMedia(fileId: Int) {
        if (fileId <= 0) return
        scope.launch { partsLookup.allFileIds(fileId).forEach { playbackDataSource.deleteFile(it) } }
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
        val videoSupport = mutableListOf<Boolean>()
        val audios = mutableListOf<MediaTrackOption>()
        val subtitles = mutableListOf<MediaTrackOption>()

        tracks.groups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val selected = group.isTrackSelected(trackIndex)
                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> {
                        videoSupport += group.isTrackSupported(trackIndex, true)
                        if (selected || videoHeight == 0) {
                            if (format.width > 0) videoWidth = format.width
                            if (format.height > 0) videoHeight = format.height
                            if (format.frameRate > 0f) videoFrameRate = format.frameRate
                            videoMimeType = format.sampleMimeType
                        }
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
            videoUnsupported = MediaTracksInfo.isVideoUnsupported(videoSupport),
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
            // Filme dividido: todas as partes (as já descartadas na emenda não custam nada).
            val fileIds = partsLookup.allFileIds(media.fileId)
            cleanupJob = scope.launch {
                // Ao sair da reprodução, remove o arquivo do TDLib para não acumular no
                // armazenamento (o Fire TV tem pouco espaço). Nas demais paradas, apenas fecha.
                fileIds.forEach { fileId ->
                    if (deleteFile) {
                        playbackDataSource.deleteFile(fileId)
                    } else {
                        playbackDataSource.close(fileId)
                    }
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
