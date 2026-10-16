package com.ntv2.app.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Paleta do NBR Play (brand kit v2.1, tema escuro). Fonte única das cores de marca do app: não repita
 * hex de marca nas telas.
 */
object BrandColors {
    /** Foco, seleção, progresso e destaques (NBR Blue / Ice). */
    val Accent = Color(0xFF66D9FF)
    /** Botão de ação principal: azul NBR com conteúdo escuro. */
    val Cta = Accent
    val OnCta = Color(0xFF090B0F)
    val Background = Color(0xFF090B0F)
    val Surface = Color(0xFF151A22)
    val SurfaceAlt = Color(0xFF1C222C)
    val TextSecondary = Color(0xFFB6BFCC)
    /** Texto de apoio mais discreto: dicas, campos vazios, legendas de baixo contraste. */
    val TextMuted = Color(0xFF8E98A8)
    /** Contorno padrão de campos e painéis sobre o fundo escuro. */
    val Border = Color(0x33FFFFFF)
    /** Contorno discreto de itens em repouso. */
    val BorderSubtle = Color(0x22FFFFFF)
    /** Contorno de itens interativos em destaque leve (campos, teclas). */
    val BorderStrong = Color(0x44FFFFFF)
    /** Preenchimento translúcido de botões e itens em repouso sobre o fundo. */
    val Fill = Color(0x14FFFFFF)
    /** Texto e ícones de itens desabilitados. */
    val ContentDisabled = Color(0x66FFFFFF)
    val Cyan = Color(0xFF31F2E0)
    /** NBR Premium / informação especial. */
    val Violet = Color(0xFF9B7CFF)
    val Rose = Color(0xFFFF6FAE)
    val Warning = Color(0xFFFFBE55)
    val Success = Color(0xFF4EDB9A)
    val Error = Color(0xFFFF626D)
}
