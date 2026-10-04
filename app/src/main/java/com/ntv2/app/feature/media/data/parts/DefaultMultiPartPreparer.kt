package com.ntv2.app.feature.media.data.parts

import com.ntv2.app.core.multipart.MultiPartRegistry
import com.ntv2.app.core.multipart.PartRef
import com.ntv2.app.core.telegram.media.TdlibMediaGateway
import com.ntv2.app.core.telegram.media.TelegramVideoParts
import com.ntv2.app.feature.media.domain.MultiPartPrepareResult
import com.ntv2.app.feature.media.domain.MultiPartPreparer

class DefaultMultiPartPreparer(
    private val gateway: TdlibMediaGateway,
    private val registry: MultiPartRegistry
) : MultiPartPreparer {

    override suspend fun prepare(channelId: Long, messageId: Long): MultiPartPrepareResult {
        val found = runCatching { gateway.resolveVideoParts(channelId, messageId) }.getOrNull()
            ?: return MultiPartPrepareResult.Failed
        return register(found, registry)
    }

    internal companion object {
        /** Registra as partes se o conjunto está completo e com tamanhos conhecidos. */
        fun register(found: TelegramVideoParts, registry: MultiPartRegistry): MultiPartPrepareResult {
            if (!found.isComplete) {
                return MultiPartPrepareResult.Incomplete(found.missing, found.total)
            }
            val parts = found.parts.sortedBy { it.index }.map { PartRef(it.fileId, it.sizeBytes) }
            // Sem o tamanho de cada parte não há como mapear a posição global: não toca.
            if (parts.any { it.sizeBytes <= 0L } || parts.map { it.fileId }.toSet().size != parts.size) {
                return MultiPartPrepareResult.Failed
            }
            registry.register(parts)
            return MultiPartPrepareResult.Ready
        }
    }
}
