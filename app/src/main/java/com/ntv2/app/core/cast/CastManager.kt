package com.ntv2.app.core.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "NtvCast"

sealed interface CastStatus {
    /** Sem Google Play Services (ex.: Fire TV) ou Cast indisponível: o botão não aparece. */
    data object Unsupported : CastStatus
    data object NoDevices : CastStatus
    data object DevicesAvailable : CastStatus
    data object Connecting : CastStatus
    data class Connected(val deviceName: String) : CastStatus
}

data class RemotePlayback(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val ended: Boolean = false,
    val error: String? = null
)

/**
 * Ponte com o Chromecast (Google Cast SDK). Só funciona com Google Play Services; em aparelhos sem
 * ele (Fire TV) fica [CastStatus.Unsupported] e o resto do app ignora o Cast.
 */
class CastManager(context: Context) {
    private val appContext = context.applicationContext

    private val castContext: CastContext? by lazy {
        val gms = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        if (gms != ConnectionResult.SUCCESS) return@lazy null
        runCatching { CastContext.getSharedInstance(appContext) }
            .onFailure { Log.w(TAG, "Cast indisponível", it) }
            .getOrNull()
    }

    private val _status = MutableStateFlow<CastStatus>(CastStatus.Unsupported)
    val status: StateFlow<CastStatus> = _status.asStateFlow()

    private val _remote = MutableStateFlow(RemotePlayback())
    val remote: StateFlow<RemotePlayback> = _remote.asStateFlow()

    private var started = false

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishRemote()
    }
    private val progressListener = RemoteMediaClient.ProgressListener { progress, duration ->
        _remote.update { it.copy(positionMs = progress, durationMs = duration) }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) { _status.value = CastStatus.Connecting }
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = detach()
        override fun onSessionEnded(session: CastSession, error: Int) = detach()
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = detach()
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    /** Liga a escuta de aparelhos/sessões (chamar na thread principal). */
    fun start() {
        if (started) return
        val ctx = castContext ?: return
        started = true
        ctx.addCastStateListener { state -> onCastState(state) }
        ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        onCastState(ctx.castState)
        ctx.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { attach(it) }
    }

    private fun onCastState(state: Int) {
        if (_status.value is CastStatus.Connected && state == CastState.CONNECTED) return
        _status.value = when (state) {
            CastState.NO_DEVICES_AVAILABLE -> CastStatus.NoDevices
            CastState.NOT_CONNECTED -> CastStatus.DevicesAvailable
            CastState.CONNECTING -> CastStatus.Connecting
            CastState.CONNECTED -> castContext?.sessionManager?.currentCastSession
                ?.castDevice?.friendlyName?.let { CastStatus.Connected(it) } ?: CastStatus.Connecting
            else -> CastStatus.NoDevices
        }
    }

    private fun attach(session: CastSession) {
        _status.value = CastStatus.Connected(session.castDevice?.friendlyName ?: "Chromecast")
        session.remoteMediaClient?.let { client ->
            client.registerCallback(remoteCallback)
            client.addProgressListener(progressListener, 1_000L)
        }
        publishRemote()
    }

    private fun detach() {
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.let {
            it.unregisterCallback(remoteCallback)
            it.removeProgressListener(progressListener)
        }
        _remote.value = RemotePlayback()
        onCastState(castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE)
    }

    private fun publishRemote() {
        val client = client() ?: return
        val status = client.mediaStatus
        _remote.update {
            it.copy(
                isPlaying = client.isPlaying,
                isBuffering = client.isBuffering || client.isLoadingNextItem,
                positionMs = client.approximateStreamPosition,
                durationMs = client.streamDuration.takeIf { d -> d > 0L } ?: it.durationMs,
                ended = status?.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                    status.idleReason == MediaStatus.IDLE_REASON_FINISHED,
                error = if (status?.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                    status.idleReason == MediaStatus.IDLE_REASON_ERROR
                ) "O Chromecast não conseguiu tocar este vídeo (formato ou áudio não suportado)." else null
            )
        }
    }

    private fun client(): RemoteMediaClient? =
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    /** Lista de aparelhos (desconectado) ou controle do aparelho atual (conectado). */
    fun showDevicePicker(activityContext: Context) {
        val ctx = castContext ?: return
        val selector = ctx.mergedSelector ?: return
        if (_status.value is CastStatus.Connected) {
            MediaRouteControllerDialog(themed(activityContext)).show()
        } else {
            MediaRouteChooserDialog(themed(activityContext)).apply { routeSelector = selector }.show()
        }
    }

    // Os diálogos do MediaRouter exigem tema AppCompat (o app usa tema próprio).
    private fun themed(context: Context): Context =
        android.view.ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)

    fun load(url: String, title: String, posterUrl: String?, mimeType: String, startPositionMs: Long, durationMs: Long) {
        val client = client() ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            posterUrl?.takeIf { it.startsWith("http") }?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .apply { if (durationMs > 0L) setStreamDuration(durationMs) }
            .build()
        client.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .setCurrentTime(startPositionMs)
                .build()
        )
    }

    fun play() { client()?.play() }
    fun pause() { client()?.pause() }
    fun seekTo(positionMs: Long) {
        client()?.seek(com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition(positionMs).build())
    }

    /** Encerra a sessão (o Chromecast volta à tela inicial). */
    fun endSession() {
        castContext?.sessionManager?.endCurrentSession(true)
    }
}
