package com.ntv2.app.feature.media.data.report

import android.os.Build
import com.ntv2.app.BuildConfig
import com.ntv2.app.core.backend.BackendException
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.backendWarn
import com.ntv2.app.core.backend.config.RemoteConfig
import com.ntv2.app.core.backend.telemetry.Telemetry
import com.ntv2.app.core.backend.telemetry.TelemetryEvents
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MediaReport(
    /** Código estável do motivo (vai para o backend: `reason`). */
    val reason: String,
    /** Texto do motivo na tela (vai na DM do Telegram). */
    val reasonLabel: String,
    val title: String,
    val year: Int?,
    val channelName: String,
    val mediaId: String,
    /** Texto livre opcional (motivo "Outro"). */
    val note: String? = null
)

/** Resultado do envio, mostrado ao usuário. */
enum class ReportOutcome {
    /** Registrado (no backend ou pela DM). */
    Sent,
    /** Mesmo reporte nas últimas 24 h. */
    Duplicate,
    /** Limite diário de reportes atingido. */
    RateLimited,
    /** Nada funcionou agora: ficou na fila e será reenviado na próxima abertura. */
    Failed
}

/** Envia o texto do reporte por DM do Telegram para @[recipient]. true = confirmado. */
fun interface DirectMessageSender {
    suspend fun send(recipient: String, text: String): Boolean
}

/** Fila de reportes que não saíram (reenviada na próxima abertura do app). */
interface PendingReportStore {
    suspend fun load(): List<MediaReport>
    suspend fun save(reports: List<MediaReport>)
}

/**
 * Envia reportes de mídia: pela RPC `submit_report` quando o backend está ligado e, se ela falhar
 * (ou o backend estiver desligado), pela DM do Telegram da própria conta logada (sem token de bot
 * no APK). Se nenhum dos dois sair, o reporte vai para a fila e [flushPending] tenta de novo.
 */
class MediaReporter(
    private val client: SupabaseClient?,
    private val directMessage: DirectMessageSender,
    private val pending: PendingReportStore,
    private val config: () -> RemoteConfig = { RemoteConfig() },
    private val telemetry: Telemetry = Telemetry.NoOp,
    private val deviceInfo: () -> String = ::defaultDeviceInfo,
    private val appVersion: String = BuildConfig.VERSION_NAME
) {
    private val queueMutex = Mutex()

    suspend fun send(report: MediaReport): ReportOutcome {
        val outcome = deliver(report)
        if (outcome == null) {
            enqueue(report)
            return ReportOutcome.Failed
        }
        if (outcome == ReportOutcome.Sent) telemetry.log(TelemetryEvents.REPORT_SENT, mapOf("reason" to report.reason))
        return outcome
    }

    /** Reenvia a fila; o que falhar de novo continua nela. Retorna quantos saíram da fila. */
    suspend fun flushPending(): Int = queueMutex.withLock {
        val queued = runCatching { pending.load() }.getOrDefault(emptyList())
        if (queued.isEmpty()) return@withLock 0
        val remaining = queued.filter { report ->
            val outcome = deliver(report)
            if (outcome == ReportOutcome.Sent) {
                telemetry.log(TelemetryEvents.REPORT_SENT, mapOf("reason" to report.reason))
            }
            // Duplicado/limite: o servidor já tem ou recusa; não adianta insistir.
            outcome == null
        }
        runCatching { pending.save(remaining) }
        queued.size - remaining.size
    }

    /** null = não saiu por nenhum caminho (vai para a fila). */
    private suspend fun deliver(report: MediaReport): ReportOutcome? {
        val backend = client
        if (backend != null && backend.config.enabled) {
            val viaBackend = runCatching { submit(backend, report) }
                .onFailure { backendWarn(TAG, "submit_report falhou, tentando DM: ${it.message}", it) }
                .getOrNull()
            if (viaBackend != null) return viaBackend
        }
        val dm = runCatching { directMessage.send(config().reportRecipient, format(report)) }
            .onFailure { backendWarn(TAG, "falha ao enviar reporte por DM", it) }
            .getOrDefault(false)
        return if (dm) ReportOutcome.Sent else null
    }

    private suspend fun submit(client: SupabaseClient, report: MediaReport): ReportOutcome {
        val p = JSONObject()
            .put("media_id", report.mediaId)
            .put("title", report.title)
            .put("year", report.year ?: JSONObject.NULL)
            .put("channel_name", report.channelName)
            .put("reason", report.reason)
            .put("note", report.note?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("device", deviceInfo())
            .put("app_version", appVersion)
        val body = client.rpc("submit_report", JSONObject().put("p", p).toString())
        return when (val status = JSONObject(body).optString("status")) {
            "created" -> ReportOutcome.Sent
            "duplicate" -> ReportOutcome.Duplicate
            "rate_limited" -> ReportOutcome.RateLimited
            else -> throw BackendException(200, "status desconhecido: $status")
        }
    }

    private suspend fun enqueue(report: MediaReport) = queueMutex.withLock {
        runCatching {
            val queued = pending.load()
            // Mesmo reporte já na fila não entra de novo.
            if (queued.none { it.mediaId == report.mediaId && it.reason == report.reason }) {
                pending.save((queued + report).takeLast(MAX_QUEUED))
            }
        }.onFailure { backendWarn(TAG, "falha ao guardar reporte na fila", it) }
    }

    internal fun format(report: MediaReport): String {
        val title = report.year?.let { "${report.title} ($it)" } ?: report.title
        // mediaId = "<chatId>_<messageId>": o messageId ajuda a achar o post no canal.
        val messageId = report.mediaId.substringAfterLast('_')
        return buildString {
            appendLine("⚑ Reporte: ${report.reasonLabel}")
            report.note?.takeIf { it.isNotBlank() }?.let { appendLine("💬 ${it.trim()}") }
            appendLine("🎬 $title")
            appendLine("📺 Canal: ${report.channelName} · msg $messageId")
            append("📱 ${deviceInfo()} · app $appVersion ${if (BuildConfig.DEBUG) "debug" else "release"}")
        }
    }

    private companion object {
        const val TAG = "NtvReport"
        const val MAX_QUEUED = 50
    }
}

private fun defaultDeviceInfo(): String =
    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

/** Fila em JSON num arquivo do app (poucos itens; não precisa de banco). */
class FilePendingReportStore(private val file: File) : PendingReportStore {
    override suspend fun load(): List<MediaReport> {
        if (!file.exists()) return emptyList()
        val array = runCatching { JSONArray(file.readText()) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            MediaReport(
                reason = o.optString("reason"),
                reasonLabel = o.optString("reason_label"),
                title = o.optString("title"),
                year = if (o.has("year") && !o.isNull("year")) o.optInt("year") else null,
                channelName = o.optString("channel_name"),
                mediaId = o.optString("media_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                note = if (o.isNull("note")) null else o.optString("note")
            )
        }
    }

    override suspend fun save(reports: List<MediaReport>) {
        if (reports.isEmpty()) {
            file.delete()
            return
        }
        val array = JSONArray()
        reports.forEach { r ->
            array.put(
                JSONObject()
                    .put("reason", r.reason)
                    .put("reason_label", r.reasonLabel)
                    .put("title", r.title)
                    .put("year", r.year ?: JSONObject.NULL)
                    .put("channel_name", r.channelName)
                    .put("media_id", r.mediaId)
                    .put("note", r.note ?: JSONObject.NULL)
            )
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
