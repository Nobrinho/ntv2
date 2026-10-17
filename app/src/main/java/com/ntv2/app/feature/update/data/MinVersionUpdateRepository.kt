package com.ntv2.app.feature.update.data

import com.ntv2.app.BuildConfig
import com.ntv2.app.feature.update.domain.UpdateAvailability
import com.ntv2.app.feature.update.domain.UpdateRepository

/**
 * Aplica o `min_version_code` da config remota: se a versão instalada estiver abaixo dele, a
 * atualização oferecida pelo manifesto vira obrigatória. Sem config (backend desligado) o mínimo
 * é 0 e nada muda.
 */
class MinVersionUpdateRepository(
    private val delegate: UpdateRepository,
    private val minVersionCode: suspend () -> Long,
    private val installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
) : UpdateRepository {
    override suspend fun check(): UpdateAvailability {
        val availability = delegate.check()
        if (availability !is UpdateAvailability.Available || availability.mandatory) return availability
        val minimum = runCatching { minVersionCode() }.getOrDefault(0L)
        return if (installedVersionCode < minimum) availability.copy(mandatory = true) else availability
    }
}
