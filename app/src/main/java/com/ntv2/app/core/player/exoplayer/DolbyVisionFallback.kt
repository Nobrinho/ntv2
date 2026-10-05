@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.exoplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput

/**
 * Dolby Vision perfil 7 (`dvhe.07`, remux de UHD Blu-ray) sem decodificador DV no aparelho.
 *
 * O media3 só troca a trilha DV por HEVC comum nos perfis 4 e 8; o 7 fica como `video/dolby-vision`
 * sem decodificador e a trilha vira NO_UNSUPPORTED_TYPE: toca o áudio e nenhuma imagem. Mas o perfil 7
 * carrega uma camada base HEVC (HDR10) tocável por qualquer decodificador HEVC; a camada de realce
 * (NAL 63) é ignorada por eles. Quando nenhum decodificador DV aceita a trilha, reescrevemos o formato
 * como HEVC para o resto do pipeline. Aparelhos com DV real continuam usando o decodificador DV.
 */
object DolbyVisionFallback {
    /** Perfil DV lido de `dvhe.07.06` / `dvh1.07.06`; null se não for um codec DV. */
    fun dolbyVisionProfile(codecs: String?): Int? {
        if (codecs == null) return null
        val parts = codecs.trim().split('.')
        if (parts.size < 2) return null
        if (parts[0] != "dvhe" && parts[0] != "dvh1") return null
        return parts[1].toIntOrNull()
    }

    /** O formato é DV perfil 7 (camada base HEVC + realce)? */
    fun isProfile7(format: Format): Boolean =
        format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION && dolbyVisionProfile(format.codecs) == 7

    /** Formato como HEVC (camada base). Só altera DV perfil 7; qualquer outro volta igual. */
    fun baseLayerFormat(format: Format): Format {
        if (!isProfile7(format)) return format
        return format.buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            // "dvhe.07.06" não descreve o HEVC; sem string de codec não há checagem de perfil/nível aqui
            // (o decodificador recusa na configuração e o fallback de decodificador segue adiante).
            .setCodecs(null)
            .build()
    }

    /** Algum decodificador de DV do aparelho aceita este formato? Falha na consulta = não. */
    fun deviceDecodesDolbyVision(format: Format): Boolean = runCatching {
        MediaCodecUtil.getDecoderInfos(MimeTypes.VIDEO_DOLBY_VISION, false, false)
            .any { it.isFormatSupported(format) }
    }.getOrDefault(false)
}

/** Envolve um [ExtractorsFactory] para que as trilhas DV perfil 7 sem suporte saiam como HEVC. */
class DolbyVisionFallbackExtractorsFactory(
    private val delegate: ExtractorsFactory,
    private val supportsDolbyVision: (Format) -> Boolean = DolbyVisionFallback::deviceDecodesDolbyVision
) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> =
        delegate.createExtractors().map { FallbackExtractor(it, supportsDolbyVision) }.toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        delegate.createExtractors(uri, responseHeaders)
            .map { FallbackExtractor(it, supportsDolbyVision) }.toTypedArray()
}

internal class FallbackExtractor(
    private val delegate: Extractor,
    private val supportsDolbyVision: (Format) -> Boolean
) : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun init(output: ExtractorOutput) {
        delegate.init(FallbackExtractorOutput(output, supportsDolbyVision))
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = delegate.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
}

internal class FallbackExtractorOutput(
    private val delegate: ExtractorOutput,
    private val supportsDolbyVision: (Format) -> Boolean
) : ExtractorOutput {
    override fun track(id: Int, type: Int): TrackOutput {
        val track = delegate.track(id, type)
        return if (type == C.TRACK_TYPE_VIDEO) FallbackTrackOutput(track, supportsDolbyVision) else track
    }

    override fun endTracks() = delegate.endTracks()

    override fun seekMap(seekMap: SeekMap) = delegate.seekMap(seekMap)
}

internal class FallbackTrackOutput(
    delegate: TrackOutput,
    private val supportsDolbyVision: (Format) -> Boolean
) : ForwardingTrackOutput(delegate) {
    override fun format(format: Format) {
        val base = if (DolbyVisionFallback.isProfile7(format) && !supportsDolbyVision(format)) {
            // runCatching: o Log não existe nos testes de JVM.
            runCatching {
                android.util.Log.i("NtvPlayer", "Dolby Vision ${format.codecs} sem decodificador: tocando a camada base HEVC")
            }
            DolbyVisionFallback.baseLayerFormat(format)
        } else {
            format
        }
        super.format(base)
    }
}
