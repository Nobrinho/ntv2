@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.exoplayer

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Escolha do decodificador de vídeo. Por padrão usa a ordem do sistema (hardware primeiro). Alguns
 * arquivos H.264 derrubam o decodificador de hardware da MediaTek do Fire TV (OMX.MTK.VIDEO.DECODER.AVC:
 * congela e depois "DECODE ERROR FATAL", sempre nos mesmos pontos do vídeo). Com [preferSoftware] o
 * decodificador de software (c2.android / OMX.google) passa à frente, com o de hardware como reserva.
 *
 * O software é usado só em JANELAS em volta desses pontos (ver [DecoderWindows]): medido no AFTKM, o
 * software de H.264 1080p ocupa 2–2,7 dos 4 núcleos e perde ~60% dos quadros em cenas pesadas — no
 * filme inteiro trocava dois travamentos por minutos de imagem engasgando.
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

        /** [videoHeight] 0 = ainda desconhecida (trata como até 1080p). */
        fun softwareCapable(videoHeight: Int): Boolean = videoHeight <= MAX_SOFTWARE_VIDEO_HEIGHT
    }
}

/**
 * Janelas de software em volta dos pontos onde o hardware falhou (lógica pura). Para cada ponto P:
 * software de P − [PRE_SWITCH_MS] (troca um pouco antes de chegar lá) até P + [AFTER_MS] (passa o
 * trecho e volta ao hardware). Janelas curtas: a falha é num quadro específico e, medido no AFTKM,
 * o hardware volta bem logo depois (0% de perda), enquanto o software perde ~40% dos quadros. Se o
 * hardware falhar de novo logo após, a nova falha vira outro ponto e as janelas se juntam.
 * Pontos próximos ([MERGE_GAP_MS]) viram uma janela só — cada troca custa ~1,5 s de carregamento.
 */
object DecoderWindows {
    const val PRE_SWITCH_MS = 4_000L
    const val AFTER_MS = 8_000L
    const val MERGE_GAP_MS = 20_000L
    /** Pontos a menos disso de um já conhecido são o mesmo ponto. */
    const val SAME_POINT_MS = 10_000L

    /** Fim da janela de software que contém [positionMs], ou null (usar hardware). */
    fun softwareUntil(positionMs: Long, badPositionsMs: List<Long>): Long? {
        var end: Long? = null
        for (bad in badPositionsMs.sorted()) {
            val start = bad - PRE_SWITCH_MS
            val stop = bad + AFTER_MS
            val current = end
            if (current == null) {
                if (positionMs in start..stop) end = stop
            } else if (start <= current + MERGE_GAP_MS) {
                end = maxOf(current, stop)
            } else {
                break
            }
        }
        return end
    }

    /** Acrescenta [positionMs] se não houver um ponto conhecido a menos de [SAME_POINT_MS]. */
    fun addPoint(points: List<Long>, positionMs: Long, max: Int = 20): List<Long> {
        if (points.any { kotlin.math.abs(it - positionMs) < SAME_POINT_MS }) return points
        return (points + positionMs).sorted().takeLast(max)
    }
}

/** Lembra em que posições o decodificador de hardware falhou em cada vídeo. */
interface DecoderTroubleMemory {
    fun badPositions(mediaId: String): List<Long>
    fun remember(mediaId: String, positionMs: Long)
}

class SharedPrefsDecoderTroubleMemory(context: Context) : DecoderTroubleMemory {
    private val prefs = context.applicationContext.getSharedPreferences("video_decoder", Context.MODE_PRIVATE)

    init {
        // Versão anterior guardava só o id (software no filme inteiro); sem as posições não serve.
        prefs.edit().remove("software_media_ids").apply()
    }

    @Synchronized
    override fun badPositions(mediaId: String): List<Long> = load()[mediaId].orEmpty()

    @Synchronized
    override fun remember(mediaId: String, positionMs: Long) {
        val all = load()
        val updated = DecoderWindows.addPoint(all[mediaId].orEmpty(), positionMs)
        prefs.edit().putString(KEY, encode(all, mediaId, updated, MAX_MEDIA)).apply()
    }

    private fun load(): LinkedHashMap<String, List<Long>> = decode(prefs.getString(KEY, null))

    companion object {
        private const val KEY = "hw_bad_positions"
        private const val MAX_MEDIA = 300

        /** Uma linha por vídeo: "mediaId|p1,p2", do mais antigo ao mais recente. */
        internal fun decode(raw: String?): LinkedHashMap<String, List<Long>> {
            val map = LinkedHashMap<String, List<Long>>()
            raw?.split('\n')?.forEach { line ->
                val id = line.substringBeforeLast('|', "")
                if (id.isEmpty()) return@forEach
                val points = line.substringAfterLast('|').split(',').mapNotNull { it.toLongOrNull() }
                if (points.isNotEmpty()) map[id] = points
            }
            return map
        }

        /** Grava [points] de [mediaId] como o mais recente, mantendo só [max] vídeos. */
        internal fun encode(
            all: LinkedHashMap<String, List<Long>>,
            mediaId: String,
            points: List<Long>,
            max: Int
        ): String {
            all.remove(mediaId)
            all[mediaId] = points
            return all.entries.toList().takeLast(max)
                .joinToString("\n") { (id, p) -> "$id|${p.joinToString(",")}" }
        }
    }
}
