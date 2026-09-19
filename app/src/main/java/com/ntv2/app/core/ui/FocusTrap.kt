package com.ntv2.app.core.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Prende o foco do D-pad dentro deste grupo: nenhuma direção consegue levar o foco para fora
 * (evita focar itens invisíveis por trás de overlays/modais).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.trapFocus(): Modifier =
    this.focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()

/** Ao entrar neste grupo pelo D-pad, o foco vai sempre para [target] (se houver). */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.focusEnterTo(target: () -> FocusRequester?): Modifier =
    this.focusProperties { enter = { target() ?: FocusRequester.Default } }
