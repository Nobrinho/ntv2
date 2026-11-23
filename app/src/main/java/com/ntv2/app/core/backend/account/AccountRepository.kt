package com.ntv2.app.core.backend.account

import com.ntv2.app.core.backend.BackendException
import com.ntv2.app.core.backend.SupabaseClient
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Resultado do "Apagar meus dados" (contagens do servidor). */
data class DeleteMyDataResult(
    val leftAccount: Boolean,
    val profiles: Int,
    val favorites: Int,
    val history: Int,
    val progress: Int
)

/** Uma denúncia enviada por esta conta, com a resposta do administrador. */
data class MyReport(
    val id: Long,
    val title: String,
    val reason: String,
    val status: String,
    val adminNote: String?,
    val createdAtIso: String?,
    val resolvedAtIso: String?
)

/**
 * "Minha conta": identificador da conta, bloqueio, denúncias enviadas e "Apagar meus dados" (LGPD).
 * Contrato em ntv2-backend/CONTRATO.md (`current_account`, `delete_my_data`, `my_reports`).
 */
class AccountRepository(
    private val client: SupabaseClient,
    /** Limpa o que é pessoal neste aparelho (banco local, perfis) depois do apagamento no servidor. */
    private val wipeLocal: suspend () -> Unit
) {
    /** 403 BLOCKED: o administrador bloqueou este aparelho. */
    val blocked: StateFlow<Boolean> get() = client.blocked

    /** Conta deste aparelho (aparelhos conectados compartilham uma); null sem rede/sessão. */
    suspend fun accountId(): String? = runCatching {
        client.rpc("current_account", "{}").trim().trim('"').ifBlank { null }
    }.getOrNull()

    /** Apaga no servidor e, só depois do 2xx, neste aparelho. Falha (sem rede, bloqueado) propaga. */
    suspend fun deleteMyData(): DeleteMyDataResult {
        val o = JSONObject(client.rpc("delete_my_data", "{}"))
        wipeLocal()
        return DeleteMyDataResult(
            leftAccount = o.optBoolean("left_account", false),
            profiles = o.optInt("profiles"),
            favorites = o.optInt("favorites"),
            history = o.optInt("history"),
            progress = o.optInt("progress")
        )
    }

    /** Denúncias da conta, mais recentes primeiro. Backend sem a função (404): lista vazia. */
    suspend fun myReports(): List<MyReport> {
        val body = try {
            client.rpc("my_reports", "{}")
        } catch (e: BackendException) {
            if (e.code == 404) return emptyList() else throw e
        }
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MyReport(
                id = o.optLong("id"),
                title = o.str("title") ?: o.str("media_id") ?: "Título",
                reason = o.str("reason").orEmpty(),
                status = o.str("status") ?: "open",
                adminNote = o.str("admin_note"),
                createdAtIso = o.str("created_at"),
                resolvedAtIso = o.str("resolved_at")
            )
        }
    }

    private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)
}
