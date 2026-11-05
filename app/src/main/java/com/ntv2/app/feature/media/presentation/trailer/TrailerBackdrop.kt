package com.ntv2.app.feature.media.presentation.trailer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
internal class TrailerBridge(
    private val onPlaying: () -> Unit,
    private val onError: (String?) -> Unit,
    private val onEnded: () -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var active = true

    @JavascriptInterface
    fun playing() {
        Log.i(TRAILER_LOG, "player: tocando")
        main.post { if (active) onPlaying() }
    }

    @JavascriptInterface
    fun error(code: String?) {
        Log.w(TRAILER_LOG, "player: erro $code")
        main.post { if (active) onError(code) }
    }

    /** Só diagnóstico: o IFrame API carregou e o player foi criado. */
    @JavascriptInterface
    fun ready() {
        Log.i(TRAILER_LOG, "player: pronto")
    }

    /** Só diagnóstico: -1 sem iniciar, 0 fim, 1 tocando, 2 pausado, 3 carregando, 5 na fila. */
    @JavascriptInterface
    fun state(code: String?) {
        Log.i(TRAILER_LOG, "player: estado $code")
    }

    @JavascriptInterface
    fun ended() {
        Log.i(TRAILER_LOG, "player: fim do vídeo")
        main.post { if (active) onEnded() }
    }

    fun release() {
        active = false
        main.removeCallbacksAndMessages(null)
    }
}

/** Guarda o WebView criado pelo AndroidView, para a pausa/retomada do ciclo de vida. */
private class WebViewHolder {
    var view: WebView? = null

    /** O processo de renderização morreu: o WebView não pode mais receber chamadas (só destroy). */
    var rendererGone = false

    /** WebView vivo e utilizável (criado e com o renderizador de pé). */
    val usable: WebView? get() = if (rendererGone) null else view
}

/**
 * Cliente que segura o WebView na página do trailer: navegação de página inteira (ex.: "Assistir no
 * YouTube") é bloqueada, em vez de abrir o navegador. Navegação dentro do iframe do player
 * (redirecionamentos, consentimento, verificação de idade) segue normal.
 * Se o renderizador morrer (comum com vídeo no Fire TV de pouca RAM), devolve `true` para o app
 * não ser encerrado junto e conta como falha (fica a imagem).
 */
internal class TrailerWebViewClient(private val onRendererGone: () -> Unit) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.isForMainFrame == true

    // Só é chamado para o frame principal: sempre bloqueia.
    @Deprecated("API < 24")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        Log.i(TRAILER_LOG, "página: início ${url?.take(60)}")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        Log.i(TRAILER_LOG, "página: carregada ${url?.take(60)}")
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        Log.w(TRAILER_LOG, "página: erro ${error?.errorCode} ${error?.description} em ${request?.url?.toString()?.take(80)}")
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        onRendererGone()
        return true
    }
}

/**
 * Prévia do trailer (player oficial do YouTube, mudo) para o fundo dos detalhes. Fica invisível até o
 * vídeo realmente tocar; erro ou nada de `playing` em 8 s chama [onFailed] com o motivo (quem chama tira o
 * componente da tela e a imagem de fundo continua). Nunca recebe foco do D-pad e o WebView é
 * destruído quando sai da composição.
 */
@Composable
fun TrailerBackdrop(
    videoId: String,
    animationsEnabled: Boolean,
    onFailed: (String, TrailerFailure) -> Unit,
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
    onFailed: (String, TrailerFailure) -> Unit,
    modifier: Modifier,
) {
    val currentOnFailed by rememberUpdatedState(onFailed)
    var playing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<TrailerFailure?>(null) }

    val bridge = remember {
        TrailerBridge(
            onPlaying = { playing = true },
            onError = { failure = TrailerFailure.PLAYER_ERROR },
        )
    }
    val holder = remember { WebViewHolder() }

    // Uma falha só: avisa quem chama (que tira o componente da composição).
    LaunchedEffect(failure) {
        failure?.let { currentOnFailed(videoId, it) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var started by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val timeout = remember { PlayingTimeout(PLAYING_TIMEOUT_MS) }
    // O prazo começa junto com o carregamento (o WebView é criado nesta mesma composição), só corre
    // com a tela em primeiro plano (para em ON_STOP e continua do ponto em ON_START) e é cancelado
    // quando `playing` chega.
    LaunchedEffect(playing, started) {
        if (playing || !started) return@LaunchedEffect
        val t0 = SystemClock.uptimeMillis()
        try {
            delay(timeout.remainingMs)
            Log.w(TRAILER_LOG, "player: sem 'playing' no prazo; fica a imagem (tenta de novo na próxima abertura)")
            failure = TrailerFailure.TIMEOUT
        } finally {
            timeout.consume(SystemClock.uptimeMillis() - t0)
        }
    }

    // Pausa ao ir para o fundo e retoma ao voltar. onPause/onResume do WebView suspendem a mídia mesmo
    // com o player ainda carregando; o JS (com guardas) cuida do player já pronto.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    started = false
                    holder.usable?.let { web ->
                        web.evaluateJavascript("player && player.pauseVideo && player.pauseVideo()", null)
                        web.onPause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    started = true
                    holder.usable?.let { web ->
                        web.onResume()
                        web.evaluateJavascript("player && player.playVideo && player.playVideo()", null)
                    }
                }
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
                } catch (t: Throwable) {
                    // Sem WebView disponível (provedor atualizando/ausente): conta como falha.
                    Log.w(TRAILER_LOG, "WebView indisponível: $t")
                    null
                }
                if (web == null) {
                    bridge.release()
                    failure = TrailerFailure.NO_WEBVIEW
                    View(ctx)
                } else {
                    web.apply {
                        // Altura fixa (MATCH_PARENT): com o WRAP_CONTENT padrão do AndroidView o WebView mede a
                        // altura pelo conteúdo, o iframe do player ficava com 0 px e o vídeo tocava invisível.
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
                        // Chamado na thread principal: marca o renderizador como morto (nada mais é
                        // chamado no WebView além de destroy) e segue o caminho de falha.
                        webViewClient = TrailerWebViewClient(onRendererGone = {
                            Log.w(TRAILER_LOG, "renderizador do WebView caiu; fica a imagem")
                            holder.rendererGone = true
                            bridge.release()
                            failure = TrailerFailure.RENDERER_GONE
                        })
                        addJavascriptInterface(bridge, "NtvTrailer")
                        Log.i(TRAILER_LOG, "WebView criado para $videoId")
                        loadDataWithBaseURL(TrailerHtml.BASE_URL, TrailerHtml.build(videoId), "text/html", "utf-8", null)
                        // Criado com a tela já em segundo plano: começa suspenso até o ON_START.
                        if (!started) onPause()
                    }.also { holder.view = it }
                }
            },
            onRelease = { view ->
                bridge.release()
                val rendererAlive = !holder.rendererGone
                holder.view = null
                if (view is WebView) {
                    // Com o renderizador morto, só destroy() é seguro.
                    if (rendererAlive) {
                        view.stopLoading()
                        view.loadUrl("about:blank")
                        view.removeJavascriptInterface("NtvTrailer")
                    }
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
 * WebView 2 s depois de a imagem de fundo ter carregado. Em falha, tira o trailer da composição (a
 * imagem por baixo continua); só falha que bloqueia ([TrailerFailure.blocksSession]) guarda o ID na
 * sessão, demora para começar volta a tentar na próxima abertura dos detalhes.
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
    val videoId = remember(trailerUrl) { YouTubeIds.videoId(trailerUrl) }
    LaunchedEffect(trailerUrl) {
        if (videoId == null) Log.i(TRAILER_LOG, "sem prévia: link ausente ou não reconhecido ($trailerUrl)")
    }
    if (videoId == null) return
    val context = LocalContext.current
    val dataSaver = remember { isDataSaverOn(context) }
    var failedHere by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId, trailerPreviewEnabled, showCovers, imageLoaded, dataSaver, failedHere) {
        Log.i(
            TRAILER_LOG,
            "decisão $videoId: ligada=$trailerPreviewEnabled capas=$showCovers imagem=$imageLoaded " +
                "economia=$dataSaver falhou=${failedHere || videoId in TrailerFailures.ids}"
        )
    }
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
            onFailed = { id, reason ->
                // Fecha esta tentativa; só o que se repetiria igual bloqueia até reabrir o app.
                reason.record(id)
                failedHere = true
            },
            modifier = modifier,
        )
    }
}

internal const val TRAILER_LOG = "NtvTrailer"
