package com.ntv2.app.core.player

/**
 * Rótulo padronizado de resolução: SD, HD (720p), FHD (1080p) e 4K. 1440p entra em FHD.
 *
 * Filmes em tela larga têm altura menor que a do formato (1920x800 é 1080p; 1280x544 é 720p), por isso a
 * LARGURA vale mais que a altura quando é conhecida. Só com a altura, usa faixas que absorvem as alturas
 * comuns de tela larga (800, 804, 808, 544…). Valores minúsculos são dado inválido: sem rótulo.
 */
object VideoResolution {

    fun label(width: Int = 0, height: Int = 0): String? =
        if (width > 0) fromWidth(width) else fromHeight(height)

    private fun fromWidth(width: Int): String? = when {
        width >= 3000 -> "4K"
        width >= 1800 -> "FHD"
        width >= 1200 -> "HD"
        width >= 400 -> "SD"
        else -> null
    }

    private fun fromHeight(height: Int): String? = when {
        height >= 1500 -> "4K"
        height >= 780 -> "FHD"
        // 560..599 cobre o SD PAL (720x576); o resto até 779 é HD 720p, inclusive tela larga (544).
        height in 560..599 -> "SD"
        height >= 520 -> "HD"
        height >= 240 -> "SD"
        else -> null
    }
}
