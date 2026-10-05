package com.ntv2.app.feature.media.domain

/**
 * Altura do vídeo deduzida do texto de qualidade da legenda do canal ("2160p, BluRay, Remux, HDR", "4K", "1080p").
 * Filme em partes é postado como documento, sem as dimensões do vídeo da mensagem; sem isto ele ficaria sem o selo
 * de resolução e sem o aviso de pouca RAM, que dependem da altura.
 */
object QualityText {
    private val NUMBER_P = Regex("(?<![0-9])(\\d{3,4})\\s*p(?![a-z])", RegexOption.IGNORE_CASE)
    private val UHD = Regex("(?<![a-z0-9])(4k|uhd)(?![a-z0-9])", RegexOption.IGNORE_CASE)
    private val FULL_HD = Regex("(?<![a-z0-9])(fhd|full\\s*hd)(?![a-z0-9])", RegexOption.IGNORE_CASE)

    /** Altura em pixels (2160, 1080, 720…), ou 0 se o texto não diz. */
    fun heightOf(quality: String?): Int {
        val text = quality?.trim().orEmpty()
        if (text.isEmpty()) return 0
        NUMBER_P.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 240..4320 }?.let { return it }
        return when {
            UHD.containsMatchIn(text) -> 2160
            FULL_HD.containsMatchIn(text) -> 1080
            else -> 0
        }
    }
}
