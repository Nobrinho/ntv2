package com.ntv2.app.feature.playback.presentation

import kotlin.math.abs

/** Dados do vídeo que decidem o modo da tela. */
internal data class DisplayVideo(val width: Int, val height: Int, val frameRate: Float, val mimeType: String?)

/** Modo de saída da tela (espelho de android.view.Display.Mode, para testar na JVM). */
internal data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/**
 * Escolhe o modo de saída da TV para o vídeo (lógica pura).
 *
 * - Vídeo acima de 1080p (4K) e a TV aceita 4K na cadência do vídeo: sai em 4K. Assim o Fire TV não
 *   precisa reduzir cada quadro 4K para 1080p — o passo mais pesado para o AFTKM (ex.: Tetris, VP9
 *   3840x1606 com perda de quadros com a saída em 1080p). É o "igualar resolução do conteúdo".
 * - [matchFrameRate] (VP9 no AFTKM): mantém a resolução atual e só casa a cadência (bloqueios do
 *   compositor com 23,976 fps em 59,94 Hz).
 * - Caso contrário, ou sem modo compatível: null (não mexe na tela).
 */
internal object DisplayModeChooser {
    private const val FPS_TOLERANCE = 0.15f
    private const val FULL_HD_HEIGHT = 1088

    fun choose(
        current: DisplayModeSpec,
        supported: List<DisplayModeSpec>,
        videoWidth: Int,
        videoHeight: Int,
        videoFrameRate: Float,
        matchFrameRate: Boolean
    ): DisplayModeSpec? {
        if (videoFrameRate <= 0f) return null
        val isUhdVideo = videoHeight > FULL_HD_HEIGHT || videoWidth > 1920
        if (isUhdVideo) {
            val uhd = supported
                .filter { it.height >= 2160 && it.width >= 3840 }
                .closestTo(videoFrameRate)
            if (uhd != null) return uhd.takeIf { it.id != current.id }
        }
        if (!matchFrameRate) return null
        return supported
            .filter { it.width == current.width && it.height == current.height }
            .closestTo(videoFrameRate)
            ?.takeIf { it.id != current.id }
    }

    private fun List<DisplayModeSpec>.closestTo(fps: Float): DisplayModeSpec? =
        minByOrNull { abs(it.refreshRate - fps) }?.takeIf { abs(it.refreshRate - fps) <= FPS_TOLERANCE }
}
