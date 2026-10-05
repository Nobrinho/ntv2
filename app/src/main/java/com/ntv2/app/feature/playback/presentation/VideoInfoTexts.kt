package com.ntv2.app.feature.playback.presentation

import com.ntv2.app.core.player.MediaTracksInfo
import java.util.Locale

/**
 * Textos do formato REAL do vídeo (lido pelo ExoPlayer ao abrir) para o painel de informações. Puros, para
 * testar na JVM. Filme dividido em partes e arquivo único mostram a mesma coisa: o container é um só.
 */
internal object VideoInfoTexts {

    /** "2160p (3840x2160)"; a resolução pela LARGURA (filme em tela larga tem altura menor); null se desconhecida. */
    fun resolution(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val rotulo = when {
            width >= 3000 -> 2160
            width >= 2400 -> 1440
            width >= 1800 -> 1080
            width >= 1200 -> 720
            else -> if (height >= 480) 480 else height
        }
        return "${rotulo}p (${width}x$height)"
    }

    private fun videoCodec(mime: String?): String? = when (mime) {
        null -> null
        "video/hevc" -> "HEVC"
        "video/avc" -> "H.264"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/mpeg2" -> "MPEG-2"
        "video/dolby-vision" -> "Dolby Vision"
        else -> mime.substringAfter('/')
    }

    /** "23,976" / "24" (vírgula, sem zeros sobrando). */
    fun frameRate(fps: Float): String? {
        if (fps <= 0f) return null
        return String.format(Locale("pt", "BR"), "%.3f", fps).trimEnd('0').trimEnd(',') + " fps"
    }

    /** "2160p (3840x2160) · HEVC 10-bit · HDR10 · Dolby Vision 7 (camada base) · 23,976 fps"; null sem vídeo. */
    fun videoLine(tracks: MediaTracksInfo): String? {
        val codec = videoCodec(tracks.videoMimeType)?.let { c ->
            if (tracks.videoBitDepth > 8) "$c ${tracks.videoBitDepth}-bit" else c
        }
        val partes = listOfNotNull(
            resolution(tracks.videoWidth, tracks.videoHeight),
            codec,
            tracks.videoHdr,
            tracks.dolbyVisionBaseLayerProfile?.let { "Dolby Vision $it (camada base)" },
            frameRate(tracks.videoFrameRate)
        )
        return partes.joinToString(" · ").ifEmpty { null }
    }

    private fun audioCodec(mime: String?): String? = when (mime) {
        null -> null
        "audio/ac3" -> "AC3"
        "audio/eac3" -> "EAC3"
        "audio/eac3-joc" -> "EAC3 Atmos"
        "audio/true-hd" -> "TrueHD"
        "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr" -> "DTS"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/flac" -> "FLAC"
        "audio/mpeg" -> "MP3"
        else -> mime.substringAfter('/').uppercase(Locale.ROOT)
    }

    private fun channels(count: Int): String? =
        when (count) {
            0 -> null
            1 -> "1.0"
            2 -> "2.0"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${count}ch"
        }

    /** "AC3 5.1 · Português (BR)": a faixa de áudio em uso; null se ainda não há. */
    fun audioLine(tracks: MediaTracksInfo): String? {
        val formato = listOfNotNull(audioCodec(tracks.audioMimeType), channels(tracks.audioChannels)).joinToString(" ")
        val faixa = tracks.audios.firstOrNull { it.isSelected }?.label
        return listOfNotNull(formato.ifEmpty { null }, faixa).joinToString(" · ").ifEmpty { null }
    }
}
