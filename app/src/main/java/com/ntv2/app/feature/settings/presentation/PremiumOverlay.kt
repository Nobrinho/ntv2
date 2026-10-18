package com.ntv2.app.feature.settings.presentation

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.backend.premium.PaymentState
import com.ntv2.app.core.backend.premium.PixCharge
import com.ntv2.app.core.backend.premium.PixResult
import com.ntv2.app.core.backend.premium.PremiumPlan
import com.ntv2.app.core.backend.premium.PremiumRepository
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NBR Play Premium: status, escolha do plano e pagamento por Pix. Na TV o QR Code fica grande para
 * pagar pelo app do banco no celular; no celular também dá para copiar o código "copia e cola".
 * A confirmação chega sozinha (o app pergunta ao servidor a cada poucos segundos).
 */
@Composable
internal fun PremiumOverlay(
    repository: PremiumRepository,
    isTv: Boolean,
    onClose: () -> Unit
) {
    var charge by remember { mutableStateOf<PixCharge?>(null) }
    BackHandler(enabled = true) { if (charge != null) charge = null else onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onClose)
            .trapFocus(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.Border, RoundedCornerShape(8.dp))
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(28.dp))
                Text("NBR Play Premium", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val current = charge
            if (current == null) {
                PlanChooser(repository, onCharge = { charge = it }, onClose = onClose)
            } else {
                PixPayment(repository, current, isTv, onBack = { charge = null }, onDone = onClose)
            }
        }
    }
}

@Composable
private fun PlanChooser(repository: PremiumRepository, onCharge: (PixCharge) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val status by repository.status.collectAsState()
    var loadFailed by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { loadFailed = runCatching { repository.refresh() }.isFailure }
    LaunchedEffect(status, loadFailed) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    val s = status
    if (s?.premium == true) {
        val until = s.premiumUntilMs?.let { formatDate(it) }
        ResultBox(Icons.Filled.CheckCircle, BrandColors.Accent, "Premium ativo${until?.let { " até $it" } ?: ""}. Obrigado por apoiar o app!")
    }
    Text(
        "Com o Premium, a TV e o celular compartilham Minha lista, o progresso e o histórico " +
            "(Configurações › Conectar aparelhos). O Premium vale para todos os aparelhos conectados.",
        color = BrandColors.TextMuted,
        style = MaterialTheme.typography.bodyMedium
    )
    Benefit(Icons.Filled.Devices, "Conectar aparelhos e sincronizar Minha lista, progresso e histórico")
    Text(
        "Pagamento por Pix, sem renovação automática: quando estiver perto de vencer, é só pagar de novo " +
            "(os dias se somam).",
        color = BrandColors.TextMuted,
        style = MaterialTheme.typography.bodySmall
    )

    when {
        s == null && !loadFailed -> CircularProgressIndicator(color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        s == null || s.plans.isEmpty() -> {
            Text("Não foi possível carregar os planos. Verifique a internet.", color = BrandColors.Warning,
                style = MaterialTheme.typography.bodyMedium)
            TestButton(icon = Icons.Filled.Close, label = "Fechar", modifier = Modifier.fillMaxWidth().focusRequester(firstFocus), onClick = onClose)
            return
        }
        else -> s.plans.forEachIndexed { i, plan ->
            val busy = creating == plan.id
            TestButton(
                icon = Icons.Filled.QrCode2,
                label = if (busy) "Gerando Pix…" else planLabel(plan, s.plans),
                enabled = creating == null,
                primary = i == s.plans.lastIndex,
                modifier = Modifier.fillMaxWidth().then(if (i == s.plans.lastIndex) Modifier.focusRequester(firstFocus) else Modifier),
                onClick = {
                    creating = plan.id
                    error = null
                    scope.launch {
                        when (val r = repository.createPix(plan.id)) {
                            is PixResult.Created -> onCharge(r.charge)
                            PixResult.Unavailable -> error = "Pagamentos indisponíveis no momento. Tente mais tarde."
                            PixResult.TooMany -> error = "Muitas cobranças geradas. Aguarde alguns minutos."
                            PixResult.Failed -> error = "Não foi possível gerar o Pix. Verifique a internet."
                        }
                        creating = null
                    }
                }
            )
        }
    }
    error?.let { Text(it, color = BrandColors.Warning, style = MaterialTheme.typography.bodyMedium) }
    TestButton(icon = Icons.Filled.Close, label = "Fechar", modifier = Modifier.fillMaxWidth(), onClick = onClose)
}

@Composable
private fun PixPayment(
    repository: PremiumRepository,
    charge: PixCharge,
    isTv: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var state by remember(charge.paymentId) { mutableStateOf(PaymentState.PENDING) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var copied by remember { mutableStateOf(false) }
    var checkTick by remember { mutableIntStateOf(0) }
    val actionFocus = remember { FocusRequester() }
    val qr = remember(charge.qrCodeBase64) {
        runCatching {
            val bytes = Base64.decode(charge.qrCodeBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    // Confere a cada 5 s (o servidor consulta o Mercado Pago); para quando aprova ou vence.
    LaunchedEffect(charge.paymentId, checkTick) {
        while (state == PaymentState.PENDING) {
            delay(CHECK_MS)
            now = System.currentTimeMillis()
            if (now > charge.expiresAtMs) {
                state = PaymentState.EXPIRED
                break
            }
            state = repository.check(charge.paymentId)
        }
    }
    LaunchedEffect(state) {
        withFrameNanos { }
        runCatching { actionFocus.requestFocus() }
    }

    when (state) {
        PaymentState.APPROVED -> {
            val until = repository.status.value?.premiumUntilMs?.let { formatDate(it) }
            ResultBox(Icons.Filled.CheckCircle, BrandColors.Accent,
                "Pagamento confirmado! Premium ativo${until?.let { " até $it" } ?: ""}.")
            TestButton(icon = Icons.Filled.CheckCircle, label = "OK", primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(actionFocus), onClick = onDone)
        }
        PaymentState.EXPIRED, PaymentState.FAILED, PaymentState.NOT_FOUND -> {
            Text(
                if (state == PaymentState.EXPIRED) "O Pix expirou sem pagamento. Gere um novo código."
                else "O pagamento não foi concluído. Gere um novo código.",
                color = BrandColors.Warning, style = MaterialTheme.typography.bodyMedium
            )
            TestButton(icon = Icons.Filled.Refresh, label = "Escolher plano de novo", primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(actionFocus), onClick = onBack)
        }
        PaymentState.PENDING -> {
            Text("${charge.planName} · ${PremiumRepository.formatPrice(charge.amountCents)}",
                color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                if (isTv) "No celular, abra o app do seu banco › Pix › Pagar com QR Code e aponte para a tela."
                else "Abra o app do seu banco › Pix › Copia e cola (ou leia o QR Code em outro aparelho).",
                color = BrandColors.TextMuted, style = MaterialTheme.typography.bodyMedium
            )
            if (qr != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = qr,
                        contentDescription = "QR Code do Pix",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(if (isTv) 300.dp else 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(10.dp)
                    )
                }
            }
            if (!isTv) {
                TestButton(
                    icon = if (copied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                    label = if (copied) "Código copiado" else "Copiar código Pix",
                    primary = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(actionFocus),
                    onClick = { clipboard.setText(AnnotatedString(charge.qrCode)); copied = true }
                )
            }
            val left = ((charge.expiresAtMs - now) / 1000).coerceAtLeast(0)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text("Aguardando o pagamento · expira em %d:%02d".format(left / 60, left % 60),
                    color = BrandColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            TestButton(
                icon = Icons.Filled.Close, label = "Cancelar",
                modifier = Modifier.fillMaxWidth().then(if (isTv) Modifier.focusRequester(actionFocus) else Modifier),
                onClick = onBack
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BrandColors.SurfaceAlt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultBox(icon: ImageVector, color: Color, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Mensal · R$ 9,90 · 30 dias" / "Anual · R$ 89,90 · 365 dias · economize 24%". */
private fun planLabel(plan: PremiumPlan, all: List<PremiumPlan>): String {
    val base = "${plan.name} · ${PremiumRepository.formatPrice(plan.priceCents)}"
    val cheapestPerDay = all.minByOrNull { it.days }
    if (cheapestPerDay == null || cheapestPerDay.id == plan.id) return base
    val full = cheapestPerDay.priceCents.toDouble() / cheapestPerDay.days * plan.days
    val saving = ((1 - plan.priceCents / full) * 100).toInt()
    return if (saving >= 5) "$base · economize $saving%" else base
}

internal fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(ms))

private const val CHECK_MS = 5_000L
