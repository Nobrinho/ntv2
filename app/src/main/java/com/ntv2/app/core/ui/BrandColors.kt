package com.ntv2.app.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Paleta do NBR Play (brand kit, tema escuro). Fonte única das cores de marca do app: não repita
 * hex de marca nas telas.
 */
object BrandColors {
    /** Foco, seleção, progresso e destaques (tokens "accent"/"focus" do kit). */
    val Accent = Color(0xFFB8C8E0)
    /** Botão de ação principal: branco com conteúdo escuro. */
    val Cta = Color(0xFFFFFFFF)
    val OnCta = Color(0xFF0B0C0F)
    val Background = Color(0xFF0B0C0F)
    val Surface = Color(0xFF16181D)
    val SurfaceAlt = Color(0xFF22252C)
    val TextSecondary = Color(0xFFB7BBC4)
    val Success = Color(0xFF70D6A0)
}
