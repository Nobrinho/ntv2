package com.ntv2.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Prepara o pedido de foco inicial de uma tela/overlay quando há navegação por teclas.
 *
 * Aberta por toque (ex.: tablet com controle), a janela fica em "modo toque" e o Android recusa foco
 * programático: o `requestFocus()` falha calado e a 1ª tecla do D-pad vai para o que tinha foco antes —
 * muitas vezes um item da tela escondida por baixo do overlay. Com teclas disponíveis
 * ([AdaptiveLayoutInfo.hasKeyNavigation]) a função devolvida sai do modo toque antes do pedido; sem teclas
 * (celular só de toque) não faz nada, para não aparecer destaque de foco no toque.
 *
 * Uso: `val prepare = rememberKeyNavFocusPrep()` e, no efeito de foco inicial, `prepare(); req.requestFocus()`.
 */
@Composable
fun rememberKeyNavFocusPrep(): () -> Unit {
    val view = LocalView.current
    val keys = rememberAdaptiveLayoutInfo().hasKeyNavigation
    return remember(view, keys) {
        { if (keys && view.isInTouchMode) view.requestFocusFromTouch() }
    }
}
