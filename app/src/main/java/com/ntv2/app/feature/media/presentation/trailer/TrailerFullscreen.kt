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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
 * Trailer em tela cheia, com som (botão "Trailer" dos detalhes). Sem controles do YouTube: só o
 * vídeo (e a legenda, quando houver).
 *
 * Se a prévia do fundo já está tocando o mesmo vídeo ([handle] com `handoff`), assume aquele WebView:
 * o vídeo continua de onde estava e só o som sobe para 100% — sem carregar de novo. Senão, cria um.
 *
 * D-pad: OK pausa/continua (no fim, volta para os detalhes), ← → voltam/avançam 10 s, Voltar fecha. O WebView nunca
 * recebe foco: as teclas chegam a esta tela e viram chamadas JS. Toque (celular) pausa/continua.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun TrailerFullscreen(
    videoId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    handle: TrailerHandle? = null,
) {
    BackHandler(enabled = true) { onClose() }
    // Assume o WebView da prévia só se ela estiver tocando este mesmo vídeo.
    val takeOver = remember { handle != null && handle.handoff && handle.canHandOff(videoId) }
    var state by remember(videoId) {
        mutableStateOf<FullscreenState>(if (takeOver) FullscreenState.Playing else FullscreenState.Loading)
    }
    var showHint by remember(videoId) { mutableStateOf(takeOver) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var rendererGone by remember { mutableStateOf(false) }

    val onPlaying = {
        if (state != FullscreenState.Playing) showHint = true
        state = FullscreenState.Playing
    }
    val onError = { code: String? -> state = FullscreenState.Failed(trailerErrorMessage(code)) }
    // Fim do trailer: volta direto para os detalhes (sem tela de "Fim do trailer").
    val closeNow by androidx.compose.runtime.rememberUpdatedState(onClose)
    val onEnded = {
        state = FullscreenState.Ended
        closeNow()
    }
    val bridge = remember(videoId) {
        val inherited = if (takeOver) handle?.bridge else null
        inherited?.also { it.retarget(onPlaying, onError, onEnded) }
            ?: TrailerBridge(onPlaying = onPlaying, onError = onError, onEnded = onEnded)
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

    // No lugar: o WebView continua na prévia (não sai da janela, o vídeo não para); só liga o som.
    // Ao fechar, pausa — a prévia sai e destrói o WebView.
    if (takeOver) {
        DisposableEffect(Unit) {
            val view = handle?.web
            web = view
            Log.i(TRAILER_LOG, "tela cheia: usando a prévia de $videoId no lugar (sem recarregar)")
            view?.evaluateJavascript("ntvSound(100); if (player && player.playVideo) player.playVideo();", null)
            onDispose {
                view?.evaluateJavascript("if (player && player.pauseVideo) player.pauseVideo();", null)
                web = null
            }
        }
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
            .background(if (takeOver) Color.Transparent else Color.Black)
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
        if (!takeOver) AndroidView(
            factory = { ctx ->
                val inherited: WebView? = null
                if (inherited != null) {
                    // Mesmo WebView da prévia: tira do fundo dos detalhes, traz para cá e liga o som.
                    Log.i(TRAILER_LOG, "tela cheia: assumindo a prévia de $videoId (sem recarregar)")
                    (inherited.parent as? ViewGroup)?.removeView(inherited)
                    inherited.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    inherited.setBackgroundColor(AndroidColor.BLACK)
                    // Tirar o WebView da janela pausa a mídia: depois de anexar aqui, retoma de onde parou
                    // (de novo após um instante, se o primeiro pedido chegar antes da superfície estar pronta).
                    val resume = "ntvSound(100); ntvFit(); if (player && player.playVideo) player.playVideo();"
                    inherited.post { inherited.evaluateJavascript(resume, null) }
                    inherited.postDelayed({ inherited.evaluateJavascript(resume, null) }, 400)
                    // `handoff` fica ligado até fechar: a prévia, se sair depois, não destrói este WebView.
                    handle?.let { it.web = null; it.bridge = null; it.playing = false }
                    web = inherited
                    return@AndroidView inherited
                }
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
                        addJavascriptInterface(bridge, "NtvTrailer")
                        Log.i(TRAILER_LOG, "tela cheia: WebView criado para $videoId")
                        loadDataWithBaseURL(TrailerHtml.BASE_URL, TrailerHtml.build(videoId, volume = 100, captions = true), "text/html", "utf-8", null)
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
        // Degradê em cima/embaixo: trailers com barras embutidas não mostram faixa preta seca.
        if (!takeOver) Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black, 0.08f to Color.Transparent,
                    0.92f to Color.Transparent, 1f to Color.Black
                )
            )
        )

        when (val s = state) {
            FullscreenState.Loading -> CircularProgressIndicator(
                color = BrandColors.Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
            // Fundo opaco: cobre o player, para a grade de recomendados do YouTube nunca aparecer.
            // Só no quadro até a tela fechar: cobre o player (a grade de recomendados nunca aparece).
            FullscreenState.Ended -> Box(Modifier.fillMaxSize().background(Color.Black))
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
