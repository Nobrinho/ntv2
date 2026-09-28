@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.exoplayer

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Escolha do decodificador de vídeo. Por padrão usa a ordem do sistema (hardware primeiro). Alguns
 * arquivos H.264 derrubam o decodificador de hardware da MediaTek do Fire TV (OMX.MTK.VIDEO.DECODER.AVC:
 * congela e depois "DECODE ERROR FATAL", sempre nos mesmos pontos do vídeo), enquanto o do celular
 * tolera. Na primeira falha o coordinator liga [preferSoftware] e prepara de novo: o decodificador de
 * software (c2.android / OMX.google) passa à frente, com o de hardware ainda como reserva.
 */
class VideoDecoderPolicy {
    @Volatile
    var preferSoftware: Boolean = false

    val codecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        if (!preferSoftware || !MimeTypes.isVideo(mimeType)) {
            infos
        } else {
            val (software, hardware) = infos.partition { it.softwareOnly }
            software + hardware
        }
    }

    companion object {
        /** Acima disso o software não dá conta no Fire TV (4K): só recria o de hardware. */
        const val MAX_SOFTWARE_VIDEO_HEIGHT = 1088

        /**
         * Falha do decodificador: troca para software já na 1ª vez — recriar o de hardware só adiava
         * o erro (no mesmo arquivo ele voltava a falhar minutos depois). Vídeo acima de 1080p fica
         * no hardware. [videoHeight] 0 = ainda desconhecida (trata como até 1080p).
         */
        fun shouldSwitchToSoftware(alreadySoftware: Boolean, videoHeight: Int): Boolean =
            !alreadySoftware && videoHeight <= MAX_SOFTWARE_VIDEO_HEIGHT
    }
}

/** Lembra quais vídeos precisaram do decodificador de software, para já abrirem com ele. */
interface SoftwareDecoderMemory {
    fun needsSoftware(mediaId: String): Boolean
    fun remember(mediaId: String)
}

class SharedPrefsSoftwareDecoderMemory(context: Context) : SoftwareDecoderMemory {
    private val prefs = context.applicationContext.getSharedPreferences("video_decoder", Context.MODE_PRIVATE)

    @Synchronized
    override fun needsSoftware(mediaId: String): Boolean = mediaId in load()

    @Synchronized
    override fun remember(mediaId: String) {
        val updated = appendRecent(load(), mediaId, MAX_ENTRIES)
        prefs.edit().putString(KEY, updated.joinToString("\n")).apply()
    }

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    companion object {
        private const val KEY = "software_media_ids"
        private const val MAX_ENTRIES = 300

        /** Lista em ordem de uso, sem repetidos, mantendo só os [max] mais recentes. */
        internal fun appendRecent(current: List<String>, id: String, max: Int): List<String> =
            (current.filter { it != id } + id).takeLast(max)
    }
}
