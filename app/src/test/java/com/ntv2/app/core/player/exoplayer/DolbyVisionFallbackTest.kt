package com.ntv2.app.core.player.exoplayer

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyVisionFallbackTest {

    private fun dv(codecs: String?) = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
        .setCodecs(codecs)
        .setWidth(3840)
        .setHeight(2160)
        .setFrameRate(23.976f)
        .setInitializationData(listOf(byteArrayOf(1, 2, 3)))
        .build()

    @Test
    fun `le o perfil do codec dolby vision`() {
        assertEquals(7, DolbyVisionFallback.dolbyVisionProfile("dvhe.07.06"))
        assertEquals(8, DolbyVisionFallback.dolbyVisionProfile("dvh1.08.04"))
        assertEquals(5, DolbyVisionFallback.dolbyVisionProfile(" dvhe.05.01 "))
        assertNull(DolbyVisionFallback.dolbyVisionProfile("hvc1.2.4.L153.B0"))
        assertNull(DolbyVisionFallback.dolbyVisionProfile("dvhe"))
        assertNull(DolbyVisionFallback.dolbyVisionProfile("dvhe.xx.06"))
        assertNull(DolbyVisionFallback.dolbyVisionProfile(null))
    }

    @Test
    fun `perfil 7 vira hevc mantendo o resto do formato`() {
        val base = DolbyVisionFallback.baseLayerFormat(dv("dvhe.07.06"))
        assertEquals(MimeTypes.VIDEO_H265, base.sampleMimeType)
        assertNull(base.codecs)
        assertEquals(3840, base.width)
        assertEquals(2160, base.height)
        assertEquals(23.976f, base.frameRate, 0.001f)
        assertEquals(1, base.initializationData.size) // vps/sps/pps do hvcC continuam
    }

    @Test
    fun `outros perfis nao sao alterados`() {
        for (codecs in listOf("dvhe.08.06", "dvh1.08.04", "dvhe.05.06", "dvhe.04.06", null)) {
            val formato = dv(codecs)
            assertSame(codecs, formato as Any, DolbyVisionFallback.baseLayerFormat(formato))
        }
        val hevc = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setCodecs("dvhe.07.06").build()
        assertSame(hevc, DolbyVisionFallback.baseLayerFormat(hevc)) // já é hevc: nada a fazer
    }

    // ---- as trilhas que o extrator entrega

    private class TrilhaGravadora : TrackOutput {
        val formatos = mutableListOf<Format>()
        override fun format(format: Format) { formatos += format }
        override fun sampleData(input: androidx.media3.common.DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int) = 0
        override fun sampleData(data: androidx.media3.common.util.ParsableByteArray, length: Int, sampleDataPart: Int) = Unit
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) = Unit
    }

    private class SaidaGravadora : ExtractorOutput {
        val trilhas = mutableMapOf<Int, TrilhaGravadora>()
        var fim = false
        override fun track(id: Int, type: Int): TrackOutput = trilhas.getOrPut(id) { TrilhaGravadora() }
        override fun endTracks() { fim = true }
        override fun seekMap(seekMap: SeekMap) = Unit
    }

    @Test
    fun `saida do extrator reescreve so o video dv7 sem suporte`() {
        val saida = SaidaGravadora()
        val envolvida = FallbackExtractorOutput(saida) { false }
        envolvida.track(1, C.TRACK_TYPE_VIDEO).format(dv("dvhe.07.06"))
        val audio = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_TRUEHD).setCodecs("dvhe.07.06").build()
        envolvida.track(2, C.TRACK_TYPE_AUDIO).format(audio)
        envolvida.endTracks()

        assertEquals(MimeTypes.VIDEO_H265, saida.trilhas.getValue(1).formatos.single().sampleMimeType)
        assertSame(audio, saida.trilhas.getValue(2).formatos.single()) // áudio passa direto
        assertTrue(saida.fim)
    }

    @Test
    fun `aparelho com decodificador dv mantem o formato original`() {
        val saida = SaidaGravadora()
        val original = dv("dvhe.07.06")
        FallbackExtractorOutput(saida) { true }.track(1, C.TRACK_TYPE_VIDEO).format(original)
        assertSame(original, saida.trilhas.getValue(1).formatos.single())
    }

    @Test
    fun `perfil 8 passa como veio mesmo sem suporte`() {
        val saida = SaidaGravadora()
        val p8 = dv("dvhe.08.06")
        FallbackExtractorOutput(saida) { false }.track(1, C.TRACK_TYPE_VIDEO).format(p8)
        assertSame(p8, saida.trilhas.getValue(1).formatos.single()) // o media3 já cuida do 8
    }

    @Test
    fun `extrator envolvido entrega a saida reescrita ao delegado`() {
        val saida = SaidaGravadora()
        var recebida: ExtractorOutput? = null
        val delegado = object : Extractor {
            override fun sniff(input: ExtractorInput) = true
            override fun init(output: ExtractorOutput) { recebida = output }
            override fun read(input: ExtractorInput, seekPosition: PositionHolder) = Extractor.RESULT_END_OF_INPUT
            override fun seek(position: Long, timeUs: Long) = Unit
            override fun release() = Unit
        }
        val extrator = FallbackExtractor(delegado) { false }
        extrator.init(saida)
        recebida!!.track(1, C.TRACK_TYPE_VIDEO).format(dv("dvhe.07.06"))
        assertEquals(MimeTypes.VIDEO_H265, saida.trilhas.getValue(1).formatos.single().sampleMimeType)
    }

    @Test
    fun `fabrica envolve todos os extratores`() {
        val delegadoFabrica = androidx.media3.extractor.ExtractorsFactory {
            arrayOf<Extractor>(DummyExtractorOutputExtractor(), DummyExtractorOutputExtractor())
        }
        val extratores = DolbyVisionFallbackExtractorsFactory(delegadoFabrica) { false }.createExtractors()
        assertEquals(2, extratores.size)
        assertTrue(extratores.all { it is FallbackExtractor })
    }

    private class DummyExtractorOutputExtractor : Extractor {
        override fun sniff(input: ExtractorInput) = false
        override fun init(output: ExtractorOutput) = Unit
        override fun read(input: ExtractorInput, seekPosition: PositionHolder) = Extractor.RESULT_END_OF_INPUT
        override fun seek(position: Long, timeUs: Long) = Unit
        override fun release() = Unit
    }

    @Test
    fun `fallback registra o perfil dv cuja camada base esta tocando`() {
        DolbyVisionFallback.appliedProfile = null
        val saida = SaidaGravadora()
        FallbackExtractorOutput(saida) { true }.track(1, C.TRACK_TYPE_VIDEO).format(dv("dvhe.07.06"))
        assertNull(DolbyVisionFallback.appliedProfile)                    // aparelho com DV: não houve fallback
        FallbackExtractorOutput(saida) { false }.track(2, C.TRACK_TYPE_VIDEO).format(dv("dvhe.07.06"))
        assertEquals(7, DolbyVisionFallback.appliedProfile)
        DolbyVisionFallback.appliedProfile = null
    }
}
