package com.ntv2.app.feature.media.presentation.trailer

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.delay

private const val FULLSCREEN_START_TIMEOUT_MS = 15_000L
private const val HINT_VISIBLE_MS = 4_000L
private const val SEEK_SECONDS = 10

/** Estado da tela cheia do trailer. */
private sealed interface FullscreenState {
    data object Loading : FullscreenState
    data object Playing : FullscreenState
    data object Ended : FullscreenState
    data class Failed(val message: String) : FullscreenState
}

/** Explica o código de erro do player do YouTube (onError) em português. */
internal fun trailerErrorMessage(code: String?): String = when (code) {
    "2" -> "Link do trailer inválido (erro 2)."
    "5" -> "O player do YouTube não conseguiu tocar neste aparelho (erro 5)."
    "100" -> "O vídeo foi removido ou é privado (erro 100)."
    "101", "150" -> "O dono do vídeo não permite tocar fora do YouTube (erro $code)."
    "152", "153" -> "O YouTube recusou a origem do player (erro $code)."
    null -> "O trailer não pôde ser tocado."
    else -> "O trailer não pôde ser tocado (erro $code)."
}

/**
 * Trailer em tela cheia, com som (botão "Trailer" dos detalhes). Usa o mesmo player oficial da
 * prévia, mas visível desde o início: mostra "Carregando", o vídeo, o fim ou o erro com o código.
 *
 * D-pad: OK pausa/continua (no fim, recomeça), ← → voltam/avançam 10 s, Voltar fecha. O WebView nunca
 * recebe foco: as teclas chegam a esta tela e viram chamadas JS. Toque (celular) pausa/continua.
 * Independe da configuração "Prévia de trailer" e da lista de falhas da sessão.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun TrailerFullscreen(
    videoId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onClose() }
    var state by remember(videoId) { mutableStateOf<FullscreenState>(FullscreenState.Loading) }
    var showHint by remember(videoId) { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var rendererGone by remember { mutableStateOf(false) }

    val bridge = remember(videoId) {
        TrailerBridge(
            onPlaying = {
                if (state != FullscreenState.Playing) showHint = true
                state = FullscreenState.Playing
            },
            onError = { code -> state = FullscreenState.Failed(trailerErrorMessage(code)) },
            onEnded = { state = FullscreenState.Ended },
        )
    }
    DisposableEffect(bridge) { onDispose { bridge.release() } }

    // Sem "tocando" em 15 s: mostra o motivo em vez de ficar carregando para sempre.
    LaunchedEffect(videoId) {
        delay(FULLSCREEN_START_TIMEOUT_MS)
        if (state == FullscreenState.Loading) {
            Log.w(TRAILER_LOG, "tela cheia: sem 'playing' em 15 s")
            state = FullscreenState.Failed("O trailer não começou em 15 s. Verifique a conexão e tente de novo.")
        }
    }
    // Diagnóstico: ainda carregando depois de 4 s → registra o que o JS da página tem.
    LaunchedEffect(videoId) {
        delay(4_000)
        if (state == FullscreenState.Loading) {
            web?.evaluateJavascript(
                "JSON.stringify({yt: typeof YT, loaded: (typeof YT !== 'undefined') ? YT.loaded : null, " +
                    "player: typeof player, bridge: typeof NtvTrailer, scripts: document.scripts.length, " +
                    "srcs: Array.prototype.map.call(document.scripts, function (x) { return x.src; }), " +
                    "iframe: (function (f) { return f ? {src: f.src, w: f.offsetWidth, h: f.offsetHeight} : null; })(document.querySelector('iframe'))})"
            ) { result -> Log.w(TRAILER_LOG, "tela cheia: ainda carregando; JS=$result") }
        }
    }
    // Diagnóstico: ao começar a tocar, registra o tamanho do iframe (tela preta = iframe sem tamanho
    // ou aparelho que não desenha o iframe).
    LaunchedEffect(state == FullscreenState.Playing) {
        if (state == FullscreenState.Playing) {
            web?.evaluateJavascript(
                "(function (f) { return (f ? f.offsetWidth + 'x' + f.offsetHeight + ' id=' + f.id + ' css=' + getComputedStyle(f).height : 'sem iframe') + " +
                    "' janela=' + window.innerWidth + 'x' + window.innerHeight + ' doc=' + document.documentElement.clientHeight + ' body=' + document.body.offsetHeight + ' inline=' + (f ? f.getAttribute('style') : '') + ' sheets=' + document.styleSheets.length; })" +
                    "(document.querySelector('iframe'))"
            ) { result -> Log.i(TRAILER_LOG, "tela cheia: iframe $result; página ${web?.width}x${web?.height}") }
        }
    }
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(HINT_VISIBLE_MS)
            showHint = false
        }
    }

    // App em segundo plano: pausa a mídia; na volta, o vídeo continua parado até o OK.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val view = web?.takeIf { !rendererGone } ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    view.evaluateJavascript("window.player && player.pauseVideo && player.pauseVideo()", null)
                    view.onPause()
                }
                Lifecycle.Event.ON_START -> view.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun js(code: String) {
        web?.takeIf { !rendererGone }?.evaluateJavascript(code, null)
    }

    fun primaryAction() {
        when (state) {
            FullscreenState.Ended -> {
                js("ntvRestart()")
                state = FullscreenState.Playing
            }
            FullscreenState.Playing -> js("ntvToggle()")
            else -> Unit
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black)
            .trapFocus()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    // Solta das teclas que tratamos também é consumida (não vaza para trás).
                    return@onPreviewKeyEvent event.key.nativeKeyCode != AndroidKeyEvent.KEYCODE_BACK
                }
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { primaryAction(); true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { js("ntvSeek(-$SEEK_SECONDS)"); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { js("ntvSeek($SEEK_SECONDS)"); true }
                    AndroidKeyEvent.KEYCODE_BACK -> false // BackHandler fecha
                    else -> true // ↑ ↓ e o resto: o foco não sai desta tela
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { primaryAction() },
            ),
    ) {
        AndroidView(
            factory = { ctx ->
                val view = try {
                    WebView(ctx)
                } catch (t: Throwable) {
                    Log.w(TRAILER_LOG, "tela cheia: WebView indisponível: $t")
                    null
                }
                if (view == null) {
                    state = FullscreenState.Failed("Este aparelho não tem o componente de vídeo da web (WebView).")
                    View(ctx)
                } else {
                    view.apply {
                        // Altura fixa (MATCH_PARENT): com o WRAP_CONTENT padrão do AndroidView o WebView mede a
                        // altura pelo conteúdo, o iframe do player ficava com 0 px e o vídeo tocava invisível.
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        isFocusable = false
                        isFocusableInTouchMode = false
                        isClickable = false
                        isLongClickable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setOnTouchListener { _, _ -> false }
                        setBackgroundColor(AndroidColor.BLACK)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        webViewClient = TrailerWebViewClient(onRendererGone = {
                            Log.w(TRAILER_LOG, "tela cheia: renderizador do WebView caiu")
                            rendererGone = true
                            bridge.release()
                            state = FullscreenState.Failed("O componente de vídeo parou neste aparelho.")
                        })
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                                Log.i(TRAILER_LOG, "tela cheia: console ${message?.messageLevel()} ${message?.message()?.take(160)}")
                                return true
                            }
                        }
                        addJavascriptInterface(bridge, "NtvTrailer")
                        Log.i(TRAILER_LOG, "tela cheia: WebView criado para $videoId")
                        loadDataWithBaseURL(
                            TrailerHtml.BASE_URL, TrailerHtml.buildFullscreen(videoId), "text/html", "utf-8", null
                        )
                    }.also { web = it }
                }
            },
            onRelease = { view ->
                bridge.release()
                web = null
                if (view is WebView) {
                    if (!rendererGone) {
                        view.stopLoading()
                        view.loadUrl("about:blank")
                        view.removeJavascriptInterface("NtvTrailer")
                    }
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        when (val s = state) {
            FullscreenState.Loading -> CircularProgressIndicator(
                color = BrandColors.Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
            FullscreenState.Ended -> FullscreenMessage(
                title = "Fim do trailer",
                body = "OK para ver de novo · Voltar para fechar",
                modifier = Modifier.align(Alignment.Center),
            )
            is FullscreenState.Failed -> FullscreenMessage(
                title = "Trailer indisponível",
                body = s.message + "\nVoltar para fechar",
                modifier = Modifier.align(Alignment.Center),
            )
            FullscreenState.Playing -> if (showHint) {
                Text(
                    "OK pausa · ◀ ▶ 10 s · Voltar fecha",
                    style = NbrType.Secondary,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xB3090B0F))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FullscreenMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BrandColors.Surface)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = NbrType.SheetTitle, color = Color.White)
        Text(body, style = NbrType.Body, color = BrandColors.TextSecondary, textAlign = TextAlign.Center)
    }
}
