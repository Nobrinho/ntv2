package com.ntv2.app.core.backend.sync

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Datas na borda do backend: Room guarda epoch ms; o Supabase fala ISO-8601 UTC (timestamptz). */
object SyncTime {
    /** epoch ms → "2026-10-17T12:34:56.789Z". */
    fun toIso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    /**
     * ISO → epoch ms. Aceita o que o PostgREST devolve ("…56.789123+00:00", microssegundos são
     * truncados para ms) e a forma com "Z". Null se não der para ler.
     */
    fun toEpochMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
        }
    }
}

/** Last-write-wins: o remoto só substitui o local se for estritamente mais novo (empate = fica o local). */
object LastWriteWins {
    fun remoteWins(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
        localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt
}
