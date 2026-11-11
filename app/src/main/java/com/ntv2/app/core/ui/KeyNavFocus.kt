package com.ntv2.app.core.ui

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView

/**
 * Foco inicial "pendente" das telas por cima do app (detalhes, série, Meu, perfis).
 *
 * Aberta por toque, a janela está em "modo toque": o Android recusa o foco que a tela pede ao abrir e, na
 * 1ª tecla do D-pad, o foco ia para o que tinha foco antes — muitas vezes um item da tela escondida por
 * baixo. Forçar a saída do modo toque resolvia isso, mas deixava o anel de foco aparecendo durante o uso
 * por toque e travava a rolagem.
 *
 * Aqui o toque fica intacto: cada overlay registra o seu item inicial ([initialKeyFocus]) e, quando o modo
 * toque termina (o usuário apertou uma tecla), o overlay do topo recebe o foco se ainda não o tem.
 */
class InitialFocusStack {
    internal class Entry(val requester: FocusRequester) {
        var hasFocus = false
        /** Já recebeu o foco inicial pelo D-pad nesta abertura. */
        var delivered = false
    }

    internal val entries = mutableListOf<Entry>()

    /**
     * Chamado quando o modo toque termina. Na 1ª vez de cada overlay o foco vai sempre ao item inicial
     * (o Android, ao sair do modo toque, escolhe o 1º item da tela, ex.: "Voltar"); depois, só se o foco
     * estiver fora do overlay — para não perder a posição de quem alterna entre toque e controle.
     */
    internal fun onKeyNavigationStarted() {
        val top = entries.lastOrNull() ?: return
        if (!top.delivered || !top.hasFocus) {
            top.delivered = true
            runCatching { top.requester.requestFocus() }
        }
    }
}

val LocalInitialFocusStack = staticCompositionLocalOf<InitialFocusStack?> { null }

/** Cria a pilha e escuta o fim do modo toque. Uma vez, na raiz do app. */
@Composable
fun rememberInitialFocusStack(): InitialFocusStack {
    val stack = remember { InitialFocusStack() }
    val view = LocalView.current
    DisposableEffect(view, stack) {
        val listener = ViewTreeObserver.OnTouchModeChangeListener { inTouchMode ->
            // Um quadro depois: deixa o Android terminar de tratar a tecla que saiu do modo toque.
            if (!inTouchMode) view.post { stack.onKeyNavigationStarted() }
        }
        view.viewTreeObserver.addOnTouchModeChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnTouchModeChangeListener(listener) }
    }
    return stack
}

/**
 * Marca a raiz de um overlay: enquanto ele estiver na tela, a 1ª tecla do D-pad depois de usar o toque
 * leva o foco a [requester] (se o foco não estiver já dentro do overlay). [stack] padrão: o da raiz do app.
 */
@Composable
fun Modifier.initialKeyFocus(
    requester: FocusRequester,
    stack: InitialFocusStack? = LocalInitialFocusStack.current
): Modifier {
    if (stack == null) return this
    val entry = remember(requester) { InitialFocusStack.Entry(requester) }
    DisposableEffect(stack, entry) {
        stack.entries.add(entry)
        onDispose { stack.entries.remove(entry) }
    }
    return this.onFocusChanged { entry.hasFocus = it.hasFocus }
}
