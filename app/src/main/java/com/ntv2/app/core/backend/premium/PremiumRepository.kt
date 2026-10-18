package com.ntv2.app.core.backend.premium

import com.ntv2.app.core.backend.BackendException
import com.ntv2.app.core.backend.BackendSession
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.sync.SyncTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class PremiumPlan(val id: String, val name: String, val priceCents: Int, val days: Int)

data class PremiumStatus(
    /** Interruptor no servidor (app_config.premium.enabled): false = tudo liberado, Premium oculto. */
    val enforced: Boolean = true,
    val premium: Boolean,
    val premiumUntilMs: Long? = null,
    val plans: List<PremiumPlan> = emptyList()
)

/** Cobrança Pix gerada pelo Mercado Pago (QR Code + "copia e cola"). */
data class PixCharge(
    val paymentId: String,
    val qrCode: String,
    val qrCodeBase64: String,
    val amountCents: Int,
    val planName: String,
    /** Contado pelo relógio deste aparelho (o da TV pode estar errado); o MP é quem vence de verdade. */
    val expiresAtMs: Long
)

enum class PaymentState { PENDING, APPROVED, EXPIRED, FAILED, NOT_FOUND }

sealed interface PixResult {
    data class Created(val charge: PixCharge) : PixResult
    /** Pagamentos desligados no servidor (sem credenciais do Mercado Pago). */
    data object Unavailable : PixResult
    /** Muitas cobranças em pouco tempo. */
    data object TooMany : PixResult
    data object Failed : PixResult
}

/**
 * NBR Play Premium por Pix (Edge Function `pix` + RPCs premium_status/payment_status; contrato em
 * ntv2-backend/CONTRATO.md). O Premium é da conta: vale para todos os aparelhos conectados.
 */
class PremiumRepository(
    private val client: SupabaseClient,
    private val session: BackendSession,
    private val onPremiumChanged: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis
) {
    val enabled: Boolean get() = client.config.enabled

    private val _status = MutableStateFlow<PremiumStatus?>(null)
    /** Último status conhecido (null = ainda não consultado). */
    val status: StateFlow<PremiumStatus?> = _status.asStateFlow()
    private var fetchedAtMs = 0L

    suspend fun refresh(): PremiumStatus {
        session.userId()
        val parsed = parseStatus(JSONObject(client.rpc("premium_status", "{}")))
        val before = _status.value?.premium
        _status.value = parsed
        fetchedAtMs = now()
        if (before == false && parsed.premium) onPremiumChanged()
        return parsed
    }

    /** Para decisões em segundo plano (ex.: sincronizar): usa o cache por [maxAgeMs]; erro = não Premium. */
    suspend fun isPremium(maxAgeMs: Long = CACHE_MS): Boolean {
        val cached = _status.value
        if (cached != null && now() - fetchedAtMs < maxAgeMs) return cached.premium
        return runCatching { refresh().premium }.getOrDefault(cached?.premium ?: false)
    }

    /**
     * Pode sincronizar? Sim se o Premium não for exigido ou se a conta for Premium. Sem resposta do
     * servidor (offline, backend antigo) libera: o servidor é quem barra o que for pago.
     */
    suspend fun syncAllowed(maxAgeMs: Long = CACHE_MS): Boolean {
        val cached = _status.value
        val s = if (cached != null && now() - fetchedAtMs < maxAgeMs) cached
        else runCatching { refresh() }.getOrNull() ?: cached
        return s == null || !s.enforced || s.premium
    }

    suspend fun createPix(planId: String): PixResult = try {
        session.userId()
        val json = JSONObject(client.function("pix", JSONObject().put("action", "create").put("plan_id", planId).toString()))
        PixResult.Created(
            PixCharge(
                paymentId = json.getString("payment_id"),
                qrCode = json.getString("qr_code"),
                qrCodeBase64 = json.optString("qr_code_base64"),
                amountCents = json.getInt("amount_cents"),
                planName = json.optString("plan_name"),
                expiresAtMs = now() + PIX_TTL_MS
            )
        )
    } catch (e: BackendException) {
        when {
            e.code == 503 -> PixResult.Unavailable
            e.code == 400 && e.message.orEmpty().contains("too_many_payments") -> PixResult.TooMany
            e.isRateLimited -> PixResult.TooMany
            else -> PixResult.Failed
        }
    } catch (e: Exception) {
        PixResult.Failed
    }

    /** Pede ao servidor para conferir no Mercado Pago (não depende do webhook ter chegado). */
    suspend fun check(paymentId: String): PaymentState {
        val json = try {
            JSONObject(client.function("pix", JSONObject().put("action", "check").put("payment_id", paymentId).toString()))
        } catch (e: BackendException) {
            return if (e.code == 404) PaymentState.NOT_FOUND else PaymentState.PENDING
        }
        val state = when (json.optString("status")) {
            "approved" -> PaymentState.APPROVED
            "expired", "cancelled" -> PaymentState.EXPIRED
            "rejected", "failed", "refunded" -> PaymentState.FAILED
            "not_found" -> PaymentState.NOT_FOUND
            else -> PaymentState.PENDING
        }
        if (state == PaymentState.APPROVED) runCatching { refresh() }
        return state
    }

    internal fun parseStatus(json: JSONObject): PremiumStatus {
        val plans = json.optJSONArray("plans")
        return PremiumStatus(
            // Backend sem o interruptor (versão anterior) exigia sempre.
            enforced = json.optBoolean("enforced", true),
            premium = json.optBoolean("premium"),
            premiumUntilMs = json.optString("premium_until").takeIf { it.isNotBlank() && it != "null" }
                ?.let { SyncTime.toEpochMs(it) },
            plans = (0 until (plans?.length() ?: 0)).map { i ->
                val p = plans!!.getJSONObject(i)
                PremiumPlan(p.getString("id"), p.getString("name"), p.getInt("price_cents"), p.getInt("days"))
            }
        )
    }

    companion object {
        const val PIX_TTL_MS = 30 * 60_000L
        private const val CACHE_MS = 10 * 60_000L

        fun formatPrice(cents: Int): String = "R$ %d,%02d".format(cents / 100, cents % 100)
    }
}
