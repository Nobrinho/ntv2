package com.ntv2.app.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.ntv2.app.core.backend.account.AccountRepository
import com.ntv2.app.core.backend.account.MyReport
import com.ntv2.app.core.backend.link.DeviceLinkRepository
import com.ntv2.app.core.backend.link.LinkedDevice
import com.ntv2.app.core.backend.premium.PremiumRepository
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.launch

/**
 * "Minha conta": identificador para suporte, aviso de bloqueio, aparelhos conectados, Premium, as
 * denúncias enviadas (com a resposta do administrador) e "Apagar meus dados" (LGPD).
 */
@Composable
internal fun AccountOverlay(
    account: AccountRepository,
    deviceLink: DeviceLinkRepository?,
    premium: PremiumRepository?,
    isTv: Boolean,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    val body: @Composable ColumnScope.() -> Unit = {
        AccountBody(account, deviceLink, premium, isTv, onClose)
    }
    if (isTv) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandColors.Background)
                .clickable(enabled = false) {}
                .trapFocus()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 60.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Minha conta", color = SettingsText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                style = NbrType.ScreenTitle)
            Column(Modifier.widthIn(max = 840.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
        }
    } else {
        SettingsPhonePage(title = "Minha conta", onBack = onClose, horizontalPadding = 24.dp, gap = 16.dp, content = body)
    }
}

@Composable
private fun ColumnScope.AccountBody(
    account: AccountRepository,
    deviceLink: DeviceLinkRepository?,
    premium: PremiumRepository?,
    isTv: Boolean,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val blocked by account.blocked.collectAsStateWithLifecycle()
    val premiumStatus = premium?.status?.collectAsStateWithLifecycle()?.value
    var accountId by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<LinkedDevice>?>(null) }
    var reports by remember { mutableStateOf<List<MyReport>?>(null) }
    var reportsFailed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val firstFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }

    LaunchedEffect(reload) {
        accountId = account.accountId()
        devices = deviceLink?.let { runCatching { it.devices() }.getOrNull() }
        reportsFailed = false
        reports = runCatching { account.myReports() }.onFailure { reportsFailed = true }.getOrNull()
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    LaunchedEffect(confirmDelete) {
        if (!confirmDelete) return@LaunchedEffect
        withFrameNanos { }
        runCatching { deleteFocus.requestFocus() }
    }

    if (blocked) {
        SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Error,
            "Este aparelho foi bloqueado pelo administrador: busca, sincronização e denúncias estão pausadas. " +
                "Se achar que é um engano, escreva para nbrplay@outlook.com com o identificador abaixo.", tv = isTv)
    }

    AccountSection("Identificador da conta", isTv)
    Text(
        accountId?.let { "${it.take(8)} · informe este código ao pedir ajuda" } ?: "Indisponível sem internet",
        color = BrandColors.TextSecondary, style = NbrType.Body, fontSize = if (isTv) 14.sp else 13.sp
    )

    if (premium != null) {
        AccountSection("NBR Play Premium", isTv)
        Text(
            when {
                premiumStatus?.premium == true ->
                    "Ativo" + (premiumStatus.premiumUntilMs?.let { " até ${formatDate(it)}" } ?: "")
                else -> "Plano gratuito"
            },
            color = if (premiumStatus?.premium == true) BrandColors.Success else BrandColors.TextSecondary,
            style = NbrType.Body, fontSize = if (isTv) 14.sp else 13.sp
        )
    }

    if (deviceLink != null) {
        AccountSection("Aparelhos desta conta", isTv)
        when (val list = devices) {
            null -> Text("Carregando…", color = BrandColors.TextMuted, style = NbrType.Body, fontSize = 13.sp)
            else -> if (list.size >= 2) list.forEach { DeviceRow(it, isTv) }
            else Text("Só este aparelho. Conecte TV e celular em Configurações > Conectar aparelhos.",
                color = BrandColors.TextSecondary, style = NbrType.Body, fontSize = 13.sp)
        }
    }

    AccountSection("Minhas denúncias", isTv)
    when {
        reports == null && !reportsFailed -> CircularProgressIndicator(
            color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        reportsFailed -> SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning,
            "Não foi possível carregar as denúncias. Verifique a internet.", tv = isTv)
        reports.isNullOrEmpty() -> Text("Nenhuma denúncia enviada. Use o botão de bandeira nos detalhes de um título.",
            color = BrandColors.TextSecondary, style = NbrType.Body, fontSize = 13.sp)
        else -> reports!!.take(20).forEach { ReportRow(it, isTv) }
    }

    AccountSection("Meus dados", isTv)
    Text(
        "Apaga deste aparelho e do servidor: perfis, Minha lista, histórico, progresso e estatísticas de uso. " +
            "Se o aparelho estiver conectado a outros, ele sai da conta antes; os outros continuam com os dados deles. " +
            "O Premium continua.",
        color = BrandColors.TextSecondary, style = NbrType.Body, fontSize = if (isTv) 14.sp else 13.sp
    )
    deleteMessage?.let { (ok, text) ->
        SettingsNotice(if (ok) NbrIcons.ShieldCheck else NbrIcons.TriangleAlert,
            if (ok) BrandColors.Success else BrandColors.Warning, text, tv = isTv)
    }
    if (!confirmDelete) {
        TestButton(icon = NbrIcons.Trash2, label = "Apagar meus dados", style = BrandButtonStyle.Destructive,
            modifier = bw(isTv), onClick = { confirmDelete = true; deleteMessage = null })
    } else {
        SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning, "Não tem como desfazer. Apagar mesmo?", tv = isTv)
        TestButton(
            icon = NbrIcons.Trash2,
            label = if (deleting) "Apagando…" else "Confirmar: apagar tudo",
            enabled = !deleting,
            style = BrandButtonStyle.Destructive,
            modifier = bw(isTv).focusRequester(deleteFocus),
            onClick = {
                deleting = true
                scope.launch {
                    deleteMessage = runCatching { account.deleteMyData() }.fold(
                        onSuccess = { true to "Pronto: seus dados foram apagados deste aparelho e do servidor." },
                        onFailure = { false to "Não foi possível apagar agora. Verifique a internet e tente de novo." }
                    )
                    deleting = false
                    confirmDelete = false
                    reload++
                }
            }
        )
        TestButton(icon = NbrIcons.X, label = "Cancelar", modifier = bw(isTv), onClick = { confirmDelete = false })
    }
    // Foco inicial no Fechar (não no botão destrutivo).
    TestButton(icon = NbrIcons.X, label = "Fechar", modifier = bw(isTv).focusRequester(firstFocus), onClick = onClose)
}

@Composable
private fun AccountSection(title: String, isTv: Boolean) {
    Text(title, color = SettingsText, style = NbrType.Body, fontWeight = FontWeight.Bold,
        fontSize = if (isTv) 16.sp else 15.sp, modifier = Modifier.padding(top = 6.dp))
}

private val REPORT_REASONS = mapOf(
    "wrong_video" to "Vídeo errado", "not_dubbed" to "Não está dublado", "audio_problem" to "Problema no áudio",
    "subtitle_problem" to "Problema na legenda", "bad_quality" to "Qualidade ruim", "does_not_play" to "Não reproduz",
    "other" to "Outro problema"
)

@Composable
private fun ReportRow(report: MyReport, tv: Boolean) {
    val shape = RoundedCornerShape(if (tv) 10.dp else 14.dp)
    val (statusText, statusColor) = when (report.status) {
        "resolved" -> "Resolvida" to BrandColors.Success
        "ignored" -> "Analisada" to BrandColors.TextMuted
        else -> "Em análise" to BrandColors.Warning
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.BorderSubtle, shape)
            .padding(if (tv) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(report.title, color = SettingsText, style = NbrType.Body, fontWeight = FontWeight.SemiBold,
            fontSize = if (tv) 14.sp else 14.sp, maxLines = 1)
        Text("${REPORT_REASONS[report.reason] ?: report.reason} · $statusText", color = statusColor,
            style = NbrType.Body, fontSize = 12.sp)
        report.adminNote?.takeIf { it.isNotBlank() }?.let {
            Text("Resposta: $it", color = BrandColors.TextSecondary, style = NbrType.Body, fontSize = 12.sp)
        }
    }
}
