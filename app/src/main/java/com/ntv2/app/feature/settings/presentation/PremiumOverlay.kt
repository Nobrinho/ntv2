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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.backend.premium.PaymentState
import com.ntv2.app.core.backend.premium.PixCharge
import com.ntv2.app.core.backend.premium.PixResult
import com.ntv2.app.core.backend.premium.PremiumPlan
import com.ntv2.app.core.backend.premium.PremiumRepository
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fundo das telas do Premium (31/33 e TV 15): violeta (ou verde na confirmação) embaixo, bg no topo. */
private val PremiumViolet = Color(0xFF2A1F5C)
private val PremiumGreen = Color(0xFF1C3A2E)

/**
 * NBR Play Premium: status, escolha do plano e pagamento por Pix. Na TV o QR Code fica grande para
 * pagar pelo app do banco no celular; no celular também dá para copiar o código "copia e cola".
 * A confirmação chega sozinha (o app pergunta ao servidor a cada poucos segundos).
 * Celular = subtelas 31 (planos), 32 (Pix) e 33 (confirmado); TV = TV 15 (planos + painel do Pix).
 */
@Composable
internal fun PremiumOverlay(
    repository: PremiumRepository,
    isTv: Boolean,
    onClose: () -> Unit
) {
    var charge by remember { mutableStateOf<PixCharge?>(null) }
    var approved by remember { mutableStateOf(false) }
    val back = { if (charge != null) charge = null else onClose() }
    BackHandler(enabled = true) { back() }

    val current = charge
    if (isTv) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BrandColors.Background, if (approved) PremiumGreen else PremiumViolet)))
                .clickable(enabled = false) {}
                .trapFocus()
                .padding(horizontal = 60.dp, vertical = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(NbrIcons.Crown, contentDescription = null, tint = BrandColors.Violet, modifier = Modifier.size(28.dp))
                        Text("NBR Play Premium", color = SettingsText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                            style = NbrType.ScreenTitle)
                    }
                    if (current == null) {
                        PlanChooser(repository, tv = true, onCharge = { charge = it }, onClose = onClose)
                    } else {
                        PremiumIntro(tv = true)
                    }
                }
                if (current != null) {
                    // Painel do Pix (TV 15): surface, raio 16, padding 20, 280 de largura.
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .width(280.dp)
                            .clip(shape)
                            .background(BrandColors.Surface)
                            .border(1.dp, BrandColors.BorderSubtle, shape)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PixPayment(repository, current, tv = true, onBack = { charge = null }, onDone = onClose,
                            onApproved = { approved = it })
                    }
                }
            }
        }
    } else if (current == null) {
        SettingsPhonePage(
            title = "NBR Play Premium",
            onBack = back,
            background = Brush.verticalGradient(listOf(BrandColors.Background, PremiumViolet)),
            horizontalPadding = 24.dp
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(BrandColors.Violet.copy(alpha = 0.2f))
                    .border(1.dp, BrandColors.Violet, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(NbrIcons.Crown, contentDescription = null, tint = BrandColors.Violet, modifier = Modifier.size(34.dp))
            }
            Text("Sua lista em todos os aparelhos", color = SettingsText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                style = NbrType.Hero, lineHeight = 30.sp)
            PlanChooser(repository, tv = false, onCharge = { charge = it }, onClose = onClose)
        }
    } else {
        PixPhonePage(repository, current, onBack = { charge = null }, onDone = onClose)
    }
}

@Composable
private fun PremiumIntro(tv: Boolean) {
    Text(
        "Com o Premium, a TV e o celular compartilham Minha lista, o progresso e o histórico " +
            "(Configurações › Conectar aparelhos). O Premium vale para todos os aparelhos conectados.",
        color = BrandColors.TextSecondary,
        style = NbrType.Body,
        fontSize = if (tv) 13.sp else 14.sp,
        lineHeight = if (tv) 19.sp else 20.sp
    )
}

@Composable
private fun PlanChooser(repository: PremiumRepository, tv: Boolean, onCharge: (PixCharge) -> Unit, onClose: () -> Unit) {
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
        SettingsNotice(NbrIcons.CircleCheck, BrandColors.Success, "Premium ativo${until?.let { " até $it" } ?: ""}. Obrigado por apoiar o app!", tv = tv)
    }
    PremiumIntro(tv)
    // Não está no protótipo: o app lista o benefício; fica no estilo dos cartões.
    SettingsCard(radius = if (tv) 10.dp else 14.dp, padding = if (tv) 10.dp else 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NbrIcons.Link, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(if (tv) 16.dp else 20.dp))
            Text("Conectar aparelhos e sincronizar Minha lista, progresso e histórico", color = SettingsText,
                style = NbrType.Secondary, fontSize = if (tv) 12.sp else 13.sp)
        }
    }

    when {
        s == null && !loadFailed -> CircularProgressIndicator(color = BrandColors.Violet, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        s == null || s.plans.isEmpty() -> {
            SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning, "Não foi possível carregar os planos. Verifique a internet.", tv = tv)
            TestButton(icon = NbrIcons.X, label = "Fechar", modifier = pbw(tv).focusRequester(firstFocus), onClick = onClose)
            return
        }
        else -> s.plans.forEachIndexed { i, plan ->
            val featured = i == s.plans.lastIndex
            PlanCard(
                tv = tv,
                plan = plan,
                saving = planSaving(plan, s.plans),
                featured = featured,
                busy = creating == plan.id,
                enabled = creating == null,
                modifier = if (featured) Modifier.focusRequester(firstFocus) else Modifier,
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
    Text(
        "Pagamento por Pix, sem renovação automática: quando estiver perto de vencer, é só pagar de novo " +
            "(os dias se somam).",
        color = BrandColors.TextMuted,
        style = NbrType.Caption,
        fontSize = if (tv) 11.sp else 12.sp
    )
    error?.let { SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning, it, tv = tv) }
    TestButton(icon = NbrIcons.X, label = "Fechar", modifier = pbw(tv), onClick = onClose)
}

/**
 * Plano (31 / TV 15): surface, borda sutil, raio 18 (TV 12), padding 16 (TV 14). O destacado (o maior)
 * fica violeta: fundo violet@12%, borda violet, preço violet e selo verde "economize X%".
 * Tocar no plano gera o Pix (como antes).
 */
@Composable
private fun PlanCard(
    tv: Boolean,
    plan: PremiumPlan,
    saving: Int?,
    featured: Boolean,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = RoundedCornerShape(if (tv) 12.dp else 18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .glideTarget(if (tv) 12.dp else 18.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { if (enabled) onClick() }
            .background(if (featured) BrandColors.Violet.copy(alpha = 0.12f) else BrandColors.Surface)
            .border(
                if (focused && !gliding) 2.dp else 1.dp,
                when {
                    focused && !gliding -> if (featured) BrandColors.Violet else BrandColors.Accent
                    featured -> BrandColors.Violet
                    else -> BrandColors.BorderSubtle
                },
                shape
            )
            .alpha(if (enabled || busy) 1f else 0.5f)
            .padding(if (tv) 14.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(plan.name, color = SettingsText, fontSize = if (tv) 16.sp else 17.sp, fontWeight = FontWeight.Bold,
                    style = NbrType.Section)
                if (saving != null) {
                    Text(
                        "economize $saving%",
                        color = BrandColors.OnCta, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, style = NbrType.Caption,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BrandColors.Success)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text("${plan.days} dias", color = BrandColors.TextMuted, style = NbrType.Caption, fontSize = if (tv) 11.sp else 12.sp)
        }
        if (busy) {
            Text("Gerando Pix…", color = BrandColors.TextSecondary, style = NbrType.Chip)
        } else {
            Text(
                PremiumRepository.formatPrice(plan.priceCents),
                color = if (featured) BrandColors.Violet else SettingsText,
                fontSize = if (tv) 17.sp else 18.sp, fontWeight = FontWeight.ExtraBold, style = NbrType.Section
            )
        }
    }
}

/** Celular: subtela do Pix (32) ou da confirmação (33). */
@Composable
private fun PixPhonePage(repository: PremiumRepository, charge: PixCharge, onBack: () -> Unit, onDone: () -> Unit) {
    var approved by remember { mutableStateOf(false) }
    if (approved) {
        SettingsPhonePage(
            title = null,
            onBack = onDone,
            background = Brush.verticalGradient(listOf(BrandColors.Background, PremiumGreen)),
            horizontalPadding = 24.dp,
            centerContent = true
        ) {
            PixPayment(repository, charge, tv = false, onBack = onBack, onDone = onDone, onApproved = { approved = it })
        }
    } else {
        SettingsPhonePage(title = "Pagar com Pix", onBack = onBack, horizontalPadding = 24.dp, gap = 14.dp) {
            PixPayment(repository, charge, tv = false, onBack = onBack, onDone = onDone, onApproved = { approved = it })
        }
    }
}

@Composable
private fun PixPayment(
    repository: PremiumRepository,
    charge: PixCharge,
    tv: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onApproved: (Boolean) -> Unit
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
    val days = repository.status.value?.plans?.firstOrNull { it.name == charge.planName }?.days

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
        onApproved(state == PaymentState.APPROVED)
        withFrameNanos { }
        runCatching { actionFocus.requestFocus() }
    }

    when (state) {
        PaymentState.APPROVED -> {
            // 33: selo 112 (verde@15%, borda verde), título 26/800 e texto 14 text-2 centralizados.
            val until = repository.status.value?.premiumUntilMs?.let { formatDate(it) }
            val badge = if (tv) 64.dp else 112.dp
            Box(
                Modifier.size(badge).clip(CircleShape).background(BrandColors.Success.copy(alpha = 0.15f))
                    .border(1.dp, BrandColors.Success, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(NbrIcons.CircleCheck, contentDescription = null, tint = BrandColors.Success, modifier = Modifier.size(badge / 2))
            }
            Text("Pagamento confirmado", color = SettingsText, fontSize = if (tv) 18.sp else 26.sp, fontWeight = FontWeight.ExtraBold,
                style = NbrType.Hero, textAlign = TextAlign.Center)
            Text(
                "NBR Play Premium ativo${until?.let { " até $it" } ?: ""}. Obrigado por apoiar o app!",
                color = BrandColors.TextSecondary, style = NbrType.Body, fontSize = if (tv) 12.sp else 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(if (tv) 4.dp else 24.dp))
            TestButton(icon = NbrIcons.CircleCheck, label = "OK", primary = true,
                modifier = pbw(tv).focusRequester(actionFocus), onClick = onDone)
        }
        PaymentState.EXPIRED, PaymentState.FAILED, PaymentState.NOT_FOUND -> {
            SettingsNotice(
                NbrIcons.TriangleAlert, BrandColors.Warning,
                if (state == PaymentState.EXPIRED) "O Pix expirou sem pagamento. Gere um novo código."
                else "O pagamento não foi concluído. Gere um novo código.",
                tv = tv
            )
            TestButton(icon = NbrIcons.RefreshCw, label = "Escolher plano de novo", primary = true,
                modifier = pbw(tv).focusRequester(actionFocus), onClick = onBack)
        }
        PaymentState.PENDING -> {
            val left = ((charge.expiresAtMs - now) / 1000).coerceAtLeast(0)
            val waiting = "Aguardando o pagamento · expira em %d:%02d".format(left / 60, left % 60)
            val price = PremiumRepository.formatPrice(charge.amountCents)
            if (tv) {
                Text("Pagar com Pix · ${charge.planName} $price", color = SettingsText, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, style = NbrType.BodyStrong, textAlign = TextAlign.Center)
                if (qr != null) QrCard(qr, size = 190.dp, padding = 20.dp, radius = 12.dp)
                Text(
                    "No celular, abra o app do seu banco › Pix › Pagar com QR Code e aponte para a tela.",
                    color = BrandColors.TextSecondary, style = NbrType.Caption, fontSize = 11.sp, textAlign = TextAlign.Center
                )
                SettingsWaitingLine(waiting, tv = true)
                TestButton(icon = NbrIcons.X, label = "Cancelar", modifier = Modifier.fillMaxWidth().focusRequester(actionFocus),
                    onClick = onBack)
            } else {
                // 32: resumo, QR 200 (raio 18, padding 22), instrução, copia e cola, espera e botões.
                SettingsCard(radius = 14.dp, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${charge.planName} · $price", color = SettingsText, style = NbrType.BodyStrong,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (days != null) Text("$days dias", color = BrandColors.TextMuted, style = NbrType.Caption)
                    }
                }
                if (qr != null) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        QrCard(qr, size = 200.dp, padding = 22.dp, radius = 18.dp)
                        Text("QR Code do Pix", color = BrandColors.TextMuted, style = NbrType.Caption)
                    }
                }
                Text(
                    "Abra o app do seu banco › Pix › Copia e cola (ou leia o QR Code em outro aparelho).",
                    color = BrandColors.TextSecondary, style = NbrType.Secondary
                )
                val copyShape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(copyShape)
                        .clickable { clipboard.setText(AnnotatedString(charge.qrCode)); copied = true }
                        .background(BrandColors.SurfaceAlt)
                        .border(1.dp, BrandColors.BorderSubtle, copyShape)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(charge.qrCode, color = BrandColors.TextMuted, style = NbrType.Caption, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(if (copied) NbrIcons.CircleCheck else NbrIcons.Copy, contentDescription = null,
                        tint = if (copied) BrandColors.Success else BrandColors.Accent, modifier = Modifier.size(18.dp))
                }
                SettingsWaitingLine(waiting, tv = false, modifier = Modifier.fillMaxWidth())
                TestButton(
                    icon = if (copied) NbrIcons.CircleCheck else NbrIcons.Copy,
                    label = if (copied) "Código copiado" else "Copiar código Pix",
                    primary = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(actionFocus),
                    onClick = { clipboard.setText(AnnotatedString(charge.qrCode)); copied = true }
                )
                TestButton(icon = NbrIcons.X, label = "Cancelar", modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), onClick = onBack)
            }
        }
    }
}

@Composable
private fun QrCard(qr: androidx.compose.ui.graphics.ImageBitmap, size: androidx.compose.ui.unit.Dp, padding: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp) {
    Image(
        bitmap = qr,
        contentDescription = "QR Code do Pix",
        filterQuality = FilterQuality.None,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Color.White)
            .padding(padding)
    )
}

/** Largura dos botões: cheia no celular; na TV, 260 dp. */
private fun pbw(tv: Boolean): Modifier = if (tv) Modifier.width(260.dp) else Modifier.fillMaxWidth()

/** Economia do plano em relação ao mais curto (só a partir de 5%), para o selo "economize X%". */
private fun planSaving(plan: PremiumPlan, all: List<PremiumPlan>): Int? {
    val shortest = all.minByOrNull { it.days }
    if (shortest == null || shortest.id == plan.id) return null
    val full = shortest.priceCents.toDouble() / shortest.days * plan.days
    val saving = ((1 - plan.priceCents / full) * 100).toInt()
    return if (saving >= 5) saving else null
}

internal fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(ms))

private const val CHECK_MS = 5_000L

