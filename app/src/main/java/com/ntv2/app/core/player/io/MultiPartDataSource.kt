@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.io

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.ntv2.app.core.multipart.MultiPartCursor
import com.ntv2.app.core.multipart.MultiPartPlaybackState
import com.ntv2.app.core.multipart.MultiPartReader
import com.ntv2.app.core.multipart.MultiPartUris
import com.ntv2.app.core.multipart.PartRef
import com.ntv2.app.core.multipart.PartUnavailableException
import com.ntv2.app.core.multipart.PartStream
import com.ntv2.app.core.multipart.PartsLookup
import com.ntv2.app.core.multipart.VirtualFileMap
import com.ntv2.app.core.player.telegram.PartialFileAccessor
import kotlinx.coroutines.runBlocking
import java.io.IOException

private const val TAG = "NtvParts"
private const val MB = 1024L * 1024L

/**
 * Toca um filme dividido em partes (`tgfile://multi/<fileId da parte 1>`) como UM arquivo só: a
 * posição que o ExoPlayer pede é global, e o [MultiPartReader] a traduz em (parte, offset) para um
 * data source comum de arquivo parcial (mesma janela, liberação de disco e reabertura de sempre).
 * O container é um só (as partes são um corte de bytes), então seek, índice no fim e tudo mais
 * seguem como num arquivo único — o player nem sabe que há partes.
 *
 * Aqui só fica a cola com Android/TDLib; a lógica de emenda está em [MultiPartReader] (testada).
 */
internal class MultiPartDataSource(
    private val partsLookup: PartsLookup,
    private val accessor: PartialFileAccessor,
    private val openPart: () -> DataSource,
    private val prefetchAheadBytes: (firstFileId: Int) -> Long,
    private val playbackState: MultiPartPlaybackState? = null
) : BaseDataSource(false) {

    private var dataSpec: DataSpec? = null
    private var reader: MultiPartReader? = null

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val firstFileId = MultiPartUris.firstFileIdOf(dataSpec.uri.toString())
            ?: throw IOException("URI inválida para filme em partes: ${dataSpec.uri}")
        val parts = partsLookup.partsOf(firstFileId)
            ?: throw IOException("Partes do filme não registradas (fileId=$firstFileId)")
        val map = VirtualFileMap(parts.map { it.sizeBytes })
        if (dataSpec.position >= map.totalSize) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        val unbounded = dataSpec.length == C.LENGTH_UNSET.toLong()
        val prefetchAhead = prefetchAheadBytes(firstFileId)
        val opened = MultiPartReader(
            map = map,
            startPosition = dataSpec.position,
            length = if (unbounded) MultiPartCursor.UNSET else dataSpec.length,
            prefetchAheadBytes = prefetchAhead,
            openPart = { part, offset, partLength ->
                playbackState?.set(firstFileId, part)
                Log.i(
                    TAG,
                    "lendo parte ${part + 1}/${parts.size} a partir de ${offset / MB}MB " +
                        "(posição global ${(map.partStart(part) + offset) / MB}MB de ${map.totalSize / MB}MB)"
                )
                openPartStream(dataSpec, parts[part], offset, partLength, partNumber = part + 1, total = parts.size)
            },
            prefetchPart = { part ->
                Log.i(TAG, "pré-baixando o começo da parte ${part + 1}/${parts.size} (emenda perto)")
                accessor.prefetchHead(parts[part].fileId, prefetchAhead)
            },
            discardPart = { part ->
                Log.i(TAG, "descartando a parte ${part + 1}/${parts.size} já assistida")
                accessor.discardFile(parts[part].fileId)
            }
        )
        reader = opened
        transferStarted(dataSpec)
        // Faixa aberta: não anuncia tamanho (como o arquivo único). Faixa fixa: o que falta dela.
        return if (unbounded) C.LENGTH_UNSET.toLong() else opened.remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val active = reader ?: return C.RESULT_END_OF_INPUT
        val read = active.read(buffer, offset, length)
        if (read > 0) bytesTransferred(read)
        return if (read == MultiPartReader.END) C.RESULT_END_OF_INPUT else read
    }

    /** Abre [part] no data source de arquivo parcial, já com o arquivo registrado no TDLib. */
    private fun openPartStream(
        spec: DataSpec,
        part: PartRef,
        offset: Long,
        partLength: Long,
        partNumber: Int,
        total: Int
    ): PartStream {
        // Partes 2 em diante ainda não foram abertas no TDLib (ou foram descartadas na emenda).
        try {
            runBlocking { accessor.ensureOpen(part.fileId) }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw PartUnavailableException(partNumber, total, "não abriu no Telegram (${error.message})", error)
        }
        val partSpec = spec.buildUpon()
            .setUri(Uri.parse("tgfile://video/${part.fileId}"))
            .setPosition(offset)
            .setLength(if (partLength == MultiPartCursor.UNSET) C.LENGTH_UNSET.toLong() else partLength)
            .build()
        val source = openPart()
        try {
            source.open(partSpec)
        } catch (error: Throwable) {
            runCatching { source.close() }
            throw error
        }
        return object : PartStream {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                source.read(buffer, offset, length)

            override fun close() = source.close()
        }
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            reader?.close()
        } finally {
            if (dataSpec != null) transferEnded()
            dataSpec = null
            reader = null
        }
    }
}

/** Escolhe a fonte pelo esquema: `tgfile://multi/...` é filme dividido; o resto é arquivo único. */
internal class MultiPartRoutingDataSource(
    private val single: DataSource,
    private val multi: DataSource
) : DataSource {

    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        single.addTransferListener(transferListener)
        multi.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = if (MultiPartUris.firstFileIdOf(dataSpec.uri.toString()) != null) multi else single
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = active ?: return C.RESULT_END_OF_INPUT
        return source.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
