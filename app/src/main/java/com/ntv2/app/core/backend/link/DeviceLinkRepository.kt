package com.ntv2.app.core.backend.link

import com.ntv2.app.core.backend.BackendException
import com.ntv2.app.core.backend.BackendSession
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.sync.SyncTime
import org.json.JSONArray
import org.json.JSONObject

/** Código mostrado na tela (normalmente da TV) para outro aparelho confirmar. */
data class LinkCode(val code: String, val expiresAtMs: Long)

enum class LinkStatus { PENDING, LINKED, EXPIRED, NOT_FOUND }

data class LinkStatusResult(val status: LinkStatus, val deviceName: String? = null)

enum class ClaimResult { LINKED, ALREADY_LINKED, INVALID, EXPIRED, OWN_CODE, PREMIUM_REQUIRED, RATE_LIMITED, FAILED }

data class LinkedDevice(val name: String?, val joinedAtMs: Long?, val thisDevice: Boolean)

/**
 * Vínculo de aparelhos por código (RPCs link_* do backend; contrato em ntv2-backend/CONTRATO.md).
 * A TV mostra o código, o celular confirma, e os dois passam a compartilhar Minha lista, progresso
 * e histórico. Depois de entrar ou sair de uma conta, [onAccountChanged] dispara a sincronização,
 * que sobe e baixa tudo de novo (a conta mudou).
 */
class DeviceLinkRepository(
    private val client: SupabaseClient,
    private val session: BackendSession,
    private val deviceName: () -> String,
    private val onAccountChanged: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis
) {
    val enabled: Boolean get() = client.config.enabled

    suspend fun createCode(): LinkCode {
        session.userId()
        val json = JSONObject(client.rpc("link_create", JSONObject().put("device_name", deviceName()).toString()))
        // Prazo contado pelo relógio deste aparelho a partir de agora: o expires_at do servidor não
        // serve para a contagem se o relógio local estiver errado (TV dias atrasada mostrava "2436:00").
        // Quem decide a validade de verdade é o servidor.
        return LinkCode(code = json.getString("code"), expiresAtMs = now() + CODE_TTL_MS)
    }

    suspend fun status(code: String): LinkStatusResult {
        val json = JSONObject(client.rpc("link_status", JSONObject().put("code", code).toString()))
        val status = when (json.optString("status")) {
            "pending" -> LinkStatus.PENDING
            "linked" -> LinkStatus.LINKED
            "expired" -> LinkStatus.EXPIRED
            else -> LinkStatus.NOT_FOUND
        }
        if (status == LinkStatus.LINKED) onAccountChanged()
        return LinkStatusResult(status, json.optString("device_name").takeIf { it.isNotBlank() && it != "null" })
    }

    suspend fun claim(code: String): ClaimResult {
        val normalized = normalizeCode(code)
        if (normalized.length != CODE_LENGTH) return ClaimResult.INVALID
        val result = try {
            session.userId()
            val body = JSONObject().put("code", normalized).put("device_name", deviceName()).toString()
            when (JSONObject(client.rpc("link_claim", body)).optString("status")) {
                "linked" -> ClaimResult.LINKED
                "already_linked" -> ClaimResult.ALREADY_LINKED
                "expired" -> ClaimResult.EXPIRED
                "own_code" -> ClaimResult.OWN_CODE
                "premium_required" -> ClaimResult.PREMIUM_REQUIRED
                else -> ClaimResult.INVALID
            }
        } catch (e: BackendException) {
            if (e.isRateLimited) ClaimResult.RATE_LIMITED else ClaimResult.FAILED
        } catch (e: Exception) {
            ClaimResult.FAILED
        }
        if (result == ClaimResult.LINKED || result == ClaimResult.ALREADY_LINKED) onAccountChanged()
        return result
    }

    /** Sai da conta compartilhada; este aparelho fica com uma cópia dos dados. */
    suspend fun leave(): Boolean {
        val status = JSONObject(client.rpc("link_leave", "{}")).optString("status")
        onAccountChanged()
        return status == "left" || status == "not_linked"
    }

    suspend fun devices(): List<LinkedDevice> {
        session.userId()
        val array = JSONArray(client.rpc("account_devices", "{}"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            LinkedDevice(
                name = o.optString("device_name").takeIf { it.isNotBlank() && it != "null" },
                joinedAtMs = SyncTime.toEpochMs(o.optString("joined_at")),
                thisDevice = o.optBoolean("this_device")
            )
        }
    }

    companion object {
        const val CODE_LENGTH = 6
        /** Validade do código no servidor (link_create): 10 minutos. */
        const val CODE_TTL_MS = 10 * 60_000L
        /** Mesmo alfabeto do servidor: sem 0/O e 1/I/L, para ler na TV sem confusão. */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        /** Maiúsculas, só letras/números do alfabeto (espaço, traço e caracteres fora dele são ignorados). */
        fun normalizeCode(input: String): String =
            input.uppercase().filter { it in ALPHABET }.take(CODE_LENGTH)
    }
}
