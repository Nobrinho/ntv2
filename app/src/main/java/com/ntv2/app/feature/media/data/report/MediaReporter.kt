package com.ntv2.app.feature.media.data.report

import android.os.Build
import com.ntv2.app.BuildConfig
import com.ntv2.app.core.telegram.media.TdlibMediaGateway

/** Destinatário dos reportes de mídia (conta do administrador no Telegram). */
const val MEDIA_REPORT_RECIPIENT = "Eminobre"

data class MediaReport(
    val reasonLabel: String,
    val title: String,
    val year: Int?,
    val channelName: String,
    val mediaId: String
)

/** Envia o reporte como mensagem privada, pela própria conta logada (sem token de bot no APK). */
class MediaReporter(private val gateway: TdlibMediaGateway) {

    suspend fun send(report: MediaReport): Boolean =
        runCatching { gateway.sendDirectMessage(MEDIA_REPORT_RECIPIENT, format(report)) }
            .onFailure { android.util.Log.w("NtvReport", "falha ao enviar reporte", it) }
            .getOrDefault(false)

    internal fun format(report: MediaReport): String {
        val title = report.year?.let { "${report.title} ($it)" } ?: report.title
        // mediaId = "<chatId>_<messageId>": o messageId ajuda a achar o post no canal.
        val messageId = report.mediaId.substringAfterLast('_')
        return buildString {
            appendLine("⚑ Reporte: ${report.reasonLabel}")
            appendLine("🎬 $title")
            appendLine("📺 Canal: ${report.channelName} · msg $messageId")
            append("📱 ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · app ${BuildConfig.VERSION_NAME}")
        }
    }
}
