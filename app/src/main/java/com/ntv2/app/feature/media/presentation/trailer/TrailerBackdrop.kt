package com.ntv2.app.feature.media.presentation.trailer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

private const val TRAILER_START_DELAY_MS = 2000L
private const val FADE_IN_MS = 600
private const val PLAYING_TIMEOUT_MS = 8000L
private const val TRAILER_SCALE = 1.2f

/** Economia de dados do sistema ligada para este app (o trailer não toca). */
fun isDataSaverOn(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return runCatching {
        cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }.getOrDefault(false)
}

/**
 * Ponte JS -> Kotlin. Os métodos chegam na thread do JavaBridge (não na principal): tudo é repassado
 * para a thread principal, e nada é entregue depois que o componente saiu da tela ([active] = false).
 */
private class TrailerBridge(
    private val onPlaying: () -> Unit,
    private val onError: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var active = true

    @JavascriptInterface
    fun playing() {
        main.post { if (active) onPlaying() }
    }

    @JavascriptInterface
    fun error(@Suppress("UNUSED_PARAMETER") code: String?) {
        main.post { if (active) onError() }
    }

    fun release() {
        active = false
        main.removeCallbacksAndMessages(null)
    }
}

/** Guarda o WebView criado pelo AndroidView, para a pausa/retomada do ciclo de vida. */
private class WebViewHolder {
    var view: WebView? = null
}

/**
 * Prévia do trailer (player oficial do YouTube, mudo) para o fundo dos detalhes. Fica invisível até o
 * vídeo realmente tocar; erro ou nada de `playing` em 8 s chama [onFailed] (quem chama tira o
 * componente da tela e a imagem de fundo continua). Nunca recebe foco do D-pad e o WebView é
 * destruído quando sai da composição.
 */
@Composable
fun TrailerBackdrop(
    videoId: String,
    animationsEnabled: Boolean,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Trocar de ID recria tudo (WebView, ponte e prazo), em vez de reaproveitar o player antigo.
    key(videoId) {
        TrailerBackdropContent(videoId, animationsEnabled, onFailed, modifier)
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun TrailerBackdropContent(
    videoId: String,
    animationsEnabled: Boolean,
    onFailed: (String) -> Unit,
    modifier: Modifier,
) {
    val currentOnFailed by rememberUpdatedState(onFailed)
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val bridge = remember {
        TrailerBridge(
            onPlaying = { playing = true },
            onError = { failed = true },
        )
    }
    val holder = remember { WebViewHolder() }

    // Uma falha só: avisa quem chama (que tira o componente da composição).
    LaunchedEffect(failed) {
        if (failed) currentOnFailed(videoId)
    }
    // O prazo começa junto com o carregamento (o WebView é criado nesta mesma composição) e é
    // cancelado quando `playing` chega (a chave muda e o efeito anterior é cancelado).
    LaunchedEffect(playing) {
        if (!playing) {
            delay(PLAYING_TIMEOUT_MS)
            failed = true
        }
    }

    // Pausa ao ir para o fundo e retoma ao voltar. As guardas evitam erro se o player não estiver pronto.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> holder.view?.evaluateJavascript(
                    "player && player.pauseVideo && player.pauseVideo()", null
                )
                Lifecycle.Event.ON_START -> holder.view?.evaluateJavascript(
                    "player && player.playVideo && player.playVideo()", null
                )
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose { bridge.release() }
    }

    val alpha by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(FADE_IN_MS) else snap(),
        label = "trailerAlpha",
    )

    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = { ctx ->
                val web = try {
                    WebView(ctx)
                } catch (_: Throwable) {
                    // Sem WebView disponível (provedor atualizando/ausente): conta como falha.
                    null
                }
                if (web == null) {
                    bridge.release()
                    failed = true
                    View(ctx)
                } else {
                    web.apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        isClickable = false
                        isLongClickable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setOnTouchListener { _, _ -> true }
                        setBackgroundColor(Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        addJavascriptInterface(bridge, "NtvTrailer")
                        loadDataWithBaseURL(TrailerHtml.BASE_URL, TrailerHtml.build(videoId), "text/html", "utf-8", null)
                    }.also { holder.view = it }
                }
            },
            onRelease = { view ->
                bridge.release()
                holder.view = null
                if (view is WebView) {
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.removeJavascriptInterface("NtvTrailer")
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false }
                .graphicsLayer {
                    scaleX = TRAILER_SCALE
                    scaleY = TRAILER_SCALE
                    this.alpha = alpha
                },
        )
    }
}

/**
 * Fundo dos detalhes com a prévia: decide se o trailer entra ([shouldPreviewTrailer]) e só cria o
 * WebView 2 s depois de a imagem de fundo ter carregado. Em falha, guarda o ID na sessão e tira o
 * trailer da composição (a imagem por baixo continua).
 *
 * [trailerPreviewEnabled] já combina a configuração local com a flag remota.
 */
@Composable
fun DetailsTrailerBackdrop(
    trailerUrl: String?,
    trailerPreviewEnabled: Boolean,
    showCovers: Boolean,
    imageLoaded: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val videoId = remember(trailerUrl) { YouTubeIds.videoId(trailerUrl) } ?: return
    val context = LocalContext.current
    val dataSaver = remember { isDataSaverOn(context) }
    var failedHere by remember(videoId) { mutableStateOf(false) }
    val allowed = !failedHere && shouldPreviewTrailer(
        videoId = videoId,
        userEnabled = trailerPreviewEnabled,
        remoteEnabled = true,
        showCovers = showCovers,
        dataSaver = dataSaver,
        failedIds = TrailerFailures.ids,
    )
    if (!allowed || !imageLoaded) return
    var ready by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId) {
        delay(TRAILER_START_DELAY_MS)
        ready = true
    }
    if (ready) {
        TrailerBackdrop(
            videoId = videoId,
            animationsEnabled = animationsEnabled,
            onFailed = { id ->
                TrailerFailures.add(id)
                failedHere = true
            },
            modifier = modifier,
        )
    }
}
