package com.ntv2.app.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.BuildConfig
import com.ntv2.app.core.ui.BrandBackdrop
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.CardLoadingPlaceholder
import com.ntv2.app.core.ui.CardLoadingStyle
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.MainBottomNav
import com.ntv2.app.core.ui.MainTab
import com.ntv2.app.core.ui.NavRail
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.core.ui.reserveMainBottomNav
import com.ntv2.app.core.ui.trapFocus
import com.ntv2.app.feature.media.data.index.SearchIndexStatus
import com.ntv2.app.feature.update.presentation.UpdateStage
import com.ntv2.app.feature.update.presentation.UpdateUiState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    showCovers: Boolean,
    animationsEnabled: Boolean,
    castPhotos: Boolean,
    nativeBlurGlow: Boolean = true,
    minDurationMinutes: Int,
    cardLoadingStyle: CardLoadingStyle = CardLoadingStyle.DEFAULT,
    onToggleCovers: (Boolean) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onToggleCastPhotos: (Boolean) -> Unit,
    trailerPreview: Boolean = true,
    onToggleTrailerPreview: (Boolean) -> Unit = {},
    onToggleNativeBlurGlow: (Boolean) -> Unit = {},
    onChangeMinDuration: (Int) -> Unit,
    onChangeCardLoadingStyle: (CardLoadingStyle) -> Unit = {},
    onManageChannels: () -> Unit,
    onOpenListedChannels: () -> Unit,
    onLogout: () -> Unit,
    onOpenLibrary: () -> Unit,
    updateState: UpdateUiState = UpdateUiState(),
    onCheckForUpdates: () -> Unit = {},
    onStartUpdateDownload: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onReplaceInstalledApp: () -> Unit = {},
    onCancelUpdateDownload: () -> Unit = {},
    indexStatus: SearchIndexStatus? = null,
    onRefreshIndex: () -> Unit = {},
    // Rail: Busca abre a busca da Biblioteca; Atualizar recarrega a Biblioteca.
    onSearch: () -> Unit = onOpenLibrary,
    onRefresh: () -> Unit = onOpenLibrary,
    onShowIntro: () -> Unit = {},
    runConnectionTest: (() -> kotlinx.coroutines.flow.Flow<com.ntv2.app.core.network.ConnectionReport>)? = null,
    // Vincular aparelhos (null = backend desligado: o item não aparece).
    deviceLink: com.ntv2.app.core.backend.link.DeviceLinkRepository? = null,
    // NBR Play Premium por Pix (null = backend desligado: o item não aparece).
    premium: com.ntv2.app.core.backend.premium.PremiumRepository? = null
) {
    var showPremium by remember { mutableStateOf(false) }
    val premiumStatus = premium?.status?.collectAsState()?.value
    LaunchedEffect(premium) { runCatching { premium?.refresh() } }
    var showConnectionTest by remember { mutableStateOf(false) }
    var showDeviceLink by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showCardLoadingPicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    // Celular (protótipo 28): Interface e Sobre ficam atrás do botão "Interface e sobre".
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    val adaptive = rememberAdaptiveLayoutInfo()
    // Um FocusRequester por item; o último focado é lembrado (inclusive ao voltar de outra tela
    // ou fechar um modal), em vez de sempre voltar ao primeiro.
    val itemFocus = remember { List(SETTINGS_ITEM_COUNT) { FocusRequester() } }
    var lastFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    fun itemMod(index: Int) = Modifier
        .focusRequester(itemFocus[index])
        .onFocusChanged { if (it.isFocused) lastFocusIndex = index }
    val anyModal = showPrivacy || confirmLogout || showCardLoadingPicker || showConnectionTest ||
        showDeviceLink || showPremium || showDurationPicker
    // Também ao fechar algo aberto por cima do app inteiro (ex.: "Ver introdução novamente"), que
    // avisa pelo sinal de restauração de foco.
    val restoreSignal = com.ntv2.app.core.ui.LocalFocusRestoreSignal.current
    LaunchedEffect(anyModal, restoreSignal) {
        if (adaptive.useTvLayout && !anyModal) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { itemFocus[lastFocusIndex].requestFocus() }
        }
    }

    FocusGlideScope(Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BrandColors.Background)) {
        BrandBackdrop(com.ntv2.app.core.ui.BrandBackdropKind.Minimal)
        val useTvLayout = adaptive.useTvLayout && maxWidth >= 720.dp
        val tv = useTvLayout

        // ---- Itens (os mesmos no celular e na TV; muda só a arrumação) ----
        val premiumActive = premiumStatus?.premium == true
        // Perfis de acesso (MOCK local): abre a troca rápida, de onde se gerencia.
        val profileActions = com.ntv2.app.core.profiles.LocalProfileActions.current
        val itProfiles: @Composable () -> Unit = {
            SettingsItem(
                tv, NbrIcons.Users, "Perfis",
                profileActions.current?.let { "Atual: ${it.name}" + if (it.kids) " · infantil" else "" } ?: "Trocar ou gerenciar perfis",
                onClick = profileActions.openSwitcher
            )
        }
        val itConnect: (@Composable () -> Unit)? = if (deviceLink != null) { {
            SettingsItem(tv, NbrIcons.Link, "Conectar aparelhos",
                "Compartilhar Minha lista e o progresso entre TV e celular",
                modifier = itemMod(13), onClick = { showDeviceLink = true })
        } } else null
        // Só com o Premium ligado no servidor (ou para quem já é Premium).
        val itPremium: (@Composable () -> Unit)? =
            if (premium != null && (premiumStatus?.enforced == true || premiumActive)) { {
                SettingsItem(
                    tv, NbrIcons.Crown, "NBR Play Premium",
                    subtitle = if (premiumActive) {
                        "Ativo" + (premiumStatus?.premiumUntilMs?.let { " até ${formatDate(it)}" } ?: "") + " · renovar"
                    } else "Conectar TV e celular com a mesma Minha lista · Pix",
                    tone = SettingsTone.Violet,
                    modifier = itemMod(14),
                    onClick = { showPremium = true },
                    trailing = { if (premiumActive) SettingsValue(tv, "Ativo", BrandColors.Success) else SettingsChevron(tv) }
                )
            } } else null
        val itChannels: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.Library, "Seleção de canais", "Gerenciar os canais disponíveis",
                modifier = itemMod(5), onClick = onManageChannels)
        }
        val itConnection: (@Composable () -> Unit)? = if (runConnectionTest != null) { {
            SettingsItem(tv, NbrIcons.Activity, "Teste de conexão", "Medir internet, Telegram e velocidade neste aparelho",
                modifier = itemMod(10), onClick = { showConnectionTest = true })
        } } else null
        val itDuration: @Composable () -> Unit = {
            DurationItem(tv, minDurationMinutes, onChangeMinDuration, itemMod(3), onOpenPicker = { showDurationPicker = true })
        }
        val itIndex: (@Composable () -> Unit)? = indexStatus?.let { status -> {
            SettingsItem(
                tv, NbrIcons.Database, "Índice de filmes e séries", indexSubtitle(status),
                modifier = itemMod(11),
                onClick = { if (!status.refreshing) onRefreshIndex() },
                trailing = {
                    when {
                        status.refreshing -> CircularProgressIndicator(
                            color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(if (tv) 14.dp else 16.dp)
                        )
                        status.failed -> SettingsValue(tv, "Falha", BrandColors.Error)
                        status.downloadedAtMillis != 0L -> SettingsValue(tv, "Ativo", BrandColors.Success)
                        else -> SettingsChevron(tv)
                    }
                }
            )
        } }
        val itCovers: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.Image, "Capas", "Exibir capas dos conteúdos na interface",
                modifier = itemMod(0), onClick = { onToggleCovers(!showCovers) },
                trailing = { SettingsSwitch(tv, showCovers) })
        }
        val itCast: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.Users, "Fotos do elenco", "Mostrar rostos do elenco nos detalhes (senão, só nomes)",
                modifier = itemMod(2), onClick = { onToggleCastPhotos(!castPhotos) },
                trailing = { SettingsSwitch(tv, castPhotos) })
        }
        val itTrailer: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.Clapperboard, "Prévia de trailer", "Toca o trailer sem som no fundo dos detalhes",
                modifier = itemMod(15), onClick = { onToggleTrailerPreview(!trailerPreview) },
                trailing = { SettingsSwitch(tv, trailerPreview) })
        }
        val itAnimations: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.Sparkles, "Animações",
                "Animações e transições (luz pulsante ao carregar, controles do player e rolagem do título)",
                modifier = itemMod(1), onClick = { onToggleAnimations(!animationsEnabled) },
                trailing = { SettingsSwitch(tv, animationsEnabled) })
        }
        val itGlow: @Composable () -> Unit = {
            SettingsItem(
                tv, NbrIcons.Sun, "Iluminação da capa: desfoque nativo",
                if (android.os.Build.VERSION.SDK_INT >= 31) "ON = desfoque do Android 12+ · OFF = versão compatível"
                else "Este aparelho (Android ${android.os.Build.VERSION.RELEASE}) usa sempre a versão compatível",
                modifier = itemMod(8), onClick = { onToggleNativeBlurGlow(!nativeBlurGlow) },
                trailing = { SettingsSwitch(tv, nativeBlurGlow) }
            )
        }
        val itCardPreview: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.SquareDashed, "Prévia do card", "Enquanto a capa carrega: ${cardLoadingStyle.label}",
                modifier = itemMod(4), onClick = { showCardLoadingPicker = true })
        }
        val itUpdates: @Composable () -> Unit = {
            SettingsItem(
                tv, NbrIcons.Download, "Atualizações", updateSubtitle(updateState),
                modifier = itemMod(9),
                onClick = {
                    when (updateState.stage) {
                        UpdateStage.AVAILABLE -> onStartUpdateDownload()
                        UpdateStage.READY_TO_INSTALL, UpdateStage.PERMISSION_REQUIRED -> onInstallUpdate()
                        UpdateStage.DOWNLOADING, UpdateStage.WAITING_FOR_DOWNLOAD -> onCancelUpdateDownload()
                        UpdateStage.SIGNATURE_MISMATCH -> onReplaceInstalledApp()
                        UpdateStage.VERIFYING, UpdateStage.CHECKING -> Unit
                        else -> onCheckForUpdates()
                    }
                },
                trailing = {
                    val newVersion = updateState.update?.versionName
                    if ((updateState.stage == UpdateStage.AVAILABLE || updateState.stage == UpdateStage.READY_TO_INSTALL) &&
                        !newVersion.isNullOrEmpty()
                    ) SettingsValue(tv, newVersion, BrandColors.Accent) else SettingsChevron(tv)
                }
            )
        }
        val itIntro: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.RotateCcw, "Ver introdução novamente", "Rever a apresentação do app",
                modifier = itemMod(12), onClick = onShowIntro)
        }
        val itPrivacy: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.ShieldCheck, "Política de privacidade", "Como o app trata seus dados",
                modifier = itemMod(6), onClick = { showPrivacy = true })
        }
        val itLogout: @Composable () -> Unit = {
            SettingsItem(tv, NbrIcons.LogOut, "Sair da conta", "Encerrar a sessão neste dispositivo",
                modifier = itemMod(7), tone = SettingsTone.Error, titleColor = BrandColors.Error,
                onClick = { confirmLogout = true })
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Rail (Busca/Canais/Atualizar voltam à Biblioteca; Config = atual).
            if (useTvLayout) {
                NavRail(
                    settingsActive = true,
                    onHome = onOpenLibrary,
                    onSearch = onSearch,
                    onChannels = onOpenListedChannels,
                    onRefresh = onRefresh,
                    onSettings = {}
                )
            }

            if (useTvLayout) {
                // TV 13/13b: título 32/800 e duas colunas iguais (gap 24), cartões soltos com gap 7.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = adaptive.tvSafeHorizontal, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Configurações", style = NbrType.ScreenTitle, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = SettingsText)
                    Row(
                        modifier = Modifier.fillMaxWidth().focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SettingsSectionLabel("Conta e app")
                            SettingsGroup(true, listOfNotNull(itProfiles, itConnect, itPremium, itChannels, itPrivacy, itLogout))
                            SettingsSectionLabel("Conteúdo", Modifier.padding(top = 10.dp))
                            SettingsGroup(true, listOfNotNull(itDuration, itIndex, itConnection))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SettingsSectionLabel("Interface")
                            SettingsGroup(true, listOf(itCovers, itAnimations, itCast, itTrailer, itGlow, itCardPreview))
                            SettingsSectionLabel("Sobre", Modifier.padding(top = 10.dp))
                            SettingsGroup(true, listOf(itUpdates, itIntro))
                        }
                    }
                }
            } else {
                // Celular 28/28b: padding 20, gap 12, grupos em cartão.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Respeita a barra de status (o título ficava por baixo da hora) e a tab bar.
                        .statusBarsPadding()
                        .reserveMainBottomNav()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Configurações", style = NbrType.ScreenTitle, color = SettingsText)
                    SettingsSectionLabel("Conta e app")
                    SettingsGroup(false, listOfNotNull(itProfiles, itConnect, itPremium, itChannels, itConnection))
                    SettingsSectionLabel("Conteúdo")
                    SettingsGroup(false, listOfNotNull(itDuration, itIndex))
                    SettingsMoreButton("Interface e sobre", expanded = moreExpanded) { moreExpanded = !moreExpanded }
                    if (moreExpanded) {
                        SettingsSectionLabel("Interface")
                        SettingsGroup(false, listOf(itCovers, itCast, itTrailer, itAnimations, itGlow, itCardPreview))
                        SettingsSectionLabel("Sobre")
                        SettingsGroup(false, listOf(itUpdates, itIntro, itPrivacy, itLogout))
                        Text(
                            "NBR Play ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = NbrType.Caption, fontSize = 11.sp, color = BrandColors.TextMuted,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (!useTvLayout) {
            MainBottomNav(
                selected = MainTab.Settings,
                onLibrary = onOpenLibrary,
                onChannels = onOpenListedChannels,
                onSettings = {},
                onSearch = onSearch,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showDurationPicker) {
            DurationPickerOverlay(
                tv = useTvLayout,
                current = minDurationMinutes,
                onApply = { onChangeMinDuration(it); showDurationPicker = false },
                onDismiss = { showDurationPicker = false }
            )
        }

        if (showCardLoadingPicker) {
            CardLoadingPickerOverlay(
                tv = useTvLayout,
                current = cardLoadingStyle,
                animationsEnabled = animationsEnabled,
                onSelect = onChangeCardLoadingStyle,
                onDismiss = { showCardLoadingPicker = false }
            )
        }

        if (showPrivacy) {
            PrivacyPolicyOverlay(onClose = { showPrivacy = false })
        }

        if (showDeviceLink && deviceLink != null) {
            DeviceLinkOverlay(
                repository = deviceLink,
                isTv = adaptive.useTvLayout,
                onClose = { showDeviceLink = false },
                onOpenPremium = premium?.let { { showDeviceLink = false; showPremium = true } }
            )
        }

        if (showPremium && premium != null) {
            PremiumOverlay(repository = premium, isTv = adaptive.useTvLayout, onClose = { showPremium = false })
        }

        if (showConnectionTest && runConnectionTest != null) {
            ConnectionTestOverlay(runTest = runConnectionTest, onClose = { showConnectionTest = false })
        }

        if (confirmLogout) {
            ConfirmDialog(
                title = "Sair da conta?",
                message = "Você será desconectado deste dispositivo.",
                icon = NbrIcons.LogOut,
                confirmLabel = "Sair",
                confirmIcon = NbrIcons.LogOut,
                cancelLabel = "Cancelar",
                cancelIcon = NbrIcons.X,
                destructive = true,
                onConfirm = { confirmLogout = false; onLogout() },
                onDismiss = { confirmLogout = false }
            )
        }
    }
    }
}

private const val SETTINGS_ITEM_COUNT = 16

private val INSTALLED_VERSION_LABEL: String =
    "Versão ${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"})"

private fun updateSubtitle(state: UpdateUiState): String = when (state.stage) {
    UpdateStage.IDLE -> "$INSTALLED_VERSION_LABEL · Verificar atualizações"
    UpdateStage.CHECKING -> "Verificando…"
    UpdateStage.UP_TO_DATE -> "$INSTALLED_VERSION_LABEL · Você está atualizado"
    UpdateStage.AVAILABLE -> "Nova versão ${state.update?.versionName.orEmpty()} disponível"
    UpdateStage.WAITING_FOR_DOWNLOAD -> "Aguardando conexão · selecione para cancelar"
    UpdateStage.DOWNLOADING -> {
        val percent = state.progress?.times(100)?.toInt()
        if (percent != null) "Baixando $percent% · selecione para cancelar" else "Baixando atualização…"
    }
    UpdateStage.VERIFYING -> "Verificando segurança do APK…"
    UpdateStage.READY_TO_INSTALL -> "Versão ${state.update?.versionName.orEmpty()} pronta para instalar"
    UpdateStage.PERMISSION_REQUIRED -> "Permita esta fonte e selecione para instalar"
    UpdateStage.SIGNATURE_MISMATCH, UpdateStage.UNSUPPORTED, UpdateStage.ERROR -> state.message ?: "Não foi possível verificar"
}

private fun indexSubtitle(status: SearchIndexStatus): String {
    if (status.refreshing) return "Baixando índice atualizado…"
    if (status.downloadedAtMillis == 0L) {
        return if (status.failed) "Falha ao baixar · selecione para tentar de novo"
        else "Ainda não baixado · selecione para baixar"
    }
    // Data em que o bot atualizou o índice; sem ela, a do download neste aparelho.
    val date = formatIndexDate(status.generatedAtMillis ?: status.downloadedAtMillis)
    val counts = "${status.movieCount} filmes · ${status.seriesCount} séries"
    return if (status.failed) "Falha ao baixar · atual de $date · selecione para tentar de novo"
    else "Atualizado em $date · $counts · selecione para baixar"
}

private fun formatIndexDate(millis: Long): String =
    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(millis))

private val DURATION_STEPS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

private fun durationLabel(minutes: Int): String = if (minutes <= 0) "Off" else "$minutes min"

/**
 * Duração mínima. Celular (28): valor "Off"/"10 min" + chevron, toque abre o sheet 38.
 * TV (13b): "◀  10 min  ▶" em azul; ← / → ajustam direto (OK também abre o seletor).
 */
@Composable
private fun DurationItem(
    tv: Boolean,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier,
    onOpenPicker: () -> Unit
) {
    val idx = DURATION_STEPS.indexOfFirst { it >= value }.let { if (it < 0) DURATION_STEPS.lastIndex else it }
    val canDecrease = idx > 0
    val canIncrease = idx < DURATION_STEPS.lastIndex
    SettingsItem(
        tv = tv,
        icon = NbrIcons.Timer,
        title = "Duração mínima",
        subtitle = "Oculta vídeos mais curtos que o tempo escolhido.",
        modifier = modifier.onKeyEvent { e ->
            if (!tv || e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                // No mínimo, ← não é consumido: o foco pode sair para o rail.
                Key.DirectionLeft -> if (canDecrease) { onChange(DURATION_STEPS[idx - 1]); true } else false
                Key.DirectionRight -> { if (canIncrease) onChange(DURATION_STEPS[idx + 1]); true }
                else -> false
            }
        },
        onClick = onOpenPicker,
        trailing = {
            if (tv) SettingsValue(true, "◀  ${durationLabel(value)}  ▶", BrandColors.Accent)
            else SettingsValue(false, durationLabel(value), if (value <= 0) BrandColors.TextSecondary else BrandColors.Accent)
        }
    )
}

/**
 * Seletor da duração mínima. Celular (38): sheet com opções de 62×48 (raio 14) e "Aplicar".
 * TV: painel lateral à direita (como o TV 18), cada opção aplica ao ser escolhida.
 */
@Composable
private fun DurationPickerOverlay(
    tv: Boolean,
    current: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }
    var selected by remember { mutableIntStateOf(DURATION_STEPS.firstOrNull { it >= current } ?: DURATION_STEPS.last()) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    if (tv) {
        Box(
            Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(BrandColors.Surface)
                    .border(1.dp, BrandColors.BorderSubtle)
                    .clickable(enabled = false) {}
                    .trapFocus()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Duração mínima", style = NbrType.Section, color = SettingsText)
                Text("Oculta vídeos mais curtos que o tempo escolhido.", style = NbrType.Caption, color = BrandColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                DURATION_STEPS.forEach { step ->
                    TvOptionRow(
                        label = durationLabel(step),
                        selected = step == selected,
                        modifier = if (step == selected) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { onApply(step) }
                    )
                }
            }
        }
    } else {
        Box(
            Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(sheetShape)
                    .background(BrandColors.Surface)
                    .border(1.dp, BrandColors.BorderSubtle, sheetShape)
                    .clickable(enabled = false) {}
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(BrandColors.Border))
                }
                Text("Duração mínima", style = NbrType.SheetTitle, fontSize = 20.sp, color = SettingsText)
                Text("Oculta vídeos mais curtos que o tempo escolhido.", style = NbrType.Secondary, color = BrandColors.TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DURATION_STEPS) { step ->
                        val on = step == selected
                        Box(
                            Modifier
                                .size(62.dp, 48.dp)
                                .clip(BrandShapes.Field)
                                .clickable { selected = step }
                                .background(if (on) BrandColors.Accent else BrandColors.SurfaceAlt)
                                .then(if (on) Modifier else Modifier.border(1.dp, BrandColors.BorderSubtle, BrandShapes.Field))
                                .then(if (on) Modifier.focusRequester(firstFocus) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                durationLabel(step), style = NbrType.Chip, fontWeight = FontWeight.Bold,
                                color = if (on) BrandColors.OnCta else BrandColors.TextSecondary, maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                BrandButton(onClick = { onApply(selected) }, modifier = Modifier.fillMaxWidth()) {
                    BrandButtonLabel("Aplicar")
                }
            }
        }
    }
}

/** Opção de lista da TV (TV 18): raio 10, padding 10/12; selecionada = fundo accent@12% + check. */
@Composable
internal fun TvOptionRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (selected) BrandColors.Accent.copy(alpha = 0.12f) else BrandColors.SurfaceAlt)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) BrandColors.Accent else BrandColors.BorderSubtle,
                shape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = NbrType.Body,
                color = if (selected) BrandColors.Accent else SettingsText)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, style = NbrType.Caption, color = BrandColors.TextMuted)
        }
        if (selected) Icon(NbrIcons.Check, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PrivacyPolicyOverlay(onClose: () -> Unit) {
    BackHandler(enabled = true) { onClose() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val adaptive = rememberAdaptiveLayoutInfo()
    val compact = adaptive.usePhoneLayout || !adaptive.useTvLayout

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .padding(horizontal = if (compact) 12.dp else 40.dp, vertical = if (compact) 12.dp else 40.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.82f).widthIn(max = 760.dp))
                .fillMaxHeight()
                .trapFocus(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho: título + X discreto para fechar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Política de Privacidade",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                PrivacyCloseButton(onClose)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, com.ntv2.app.core.ui.BrandColors.Border, RoundedCornerShape(6.dp))
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(320f) }; true }
                            // No topo do texto, ↑ sobe para o botão X.
                            Key.DirectionUp -> if (scroll.value > 0) {
                                scope.launch { scroll.animateScrollBy(-320f) }; true
                            } else false
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    }
                    .verticalScroll(scroll)
                    .padding(horizontal = if (compact) 18.dp else 24.dp, vertical = 20.dp)
            ) {
                Text(
                    PRIVACY_POLICY_TEXT,
                    color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                if (compact) "Deslize para rolar · toque no X para fechar" else "Use ↑ / ↓ para rolar · ↑ no topo vai ao X · Voltar para fechar",
                color = com.ntv2.app.core.ui.BrandColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PrivacyCloseButton(onClose: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClose)
            .background(if (focused) Color.White else com.ntv2.app.core.ui.BrandColors.BorderSubtle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            NbrIcons.X,
            contentDescription = "Fechar",
            tint = if (focused) Color.Black else com.ntv2.app.core.ui.BrandColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

private const val PRIVACY_POLICY_TEXT =
    "Última atualização: 18/10/2026\n" +
    "\n" +
    "O NBR Play é um aplicativo cliente de mídia para TV e celular que exibe e reproduz vídeos dos canais do Telegram escolhidos pelo próprio usuário. O aplicativo não hospeda, não distribui e não disponibiliza conteúdo: ele apenas organiza e reproduz o que já existe nos canais aos quais a sua conta do Telegram tem acesso.\n" +
    "\n" +
    "Esta política explica quais dados o aplicativo trata, para quê, por quanto tempo e como você exerce os seus direitos, nos termos da Lei Geral de Proteção de Dados (Lei 13.709/2018 — LGPD).\n" +
    "\n" +
    "1. Quem trata os dados\n" +
    "O responsável (controlador) pelos dados descritos aqui é o NBR Play. Contato do encarregado: nbrplay@outlook.com.\n" +
    "\n" +
    "2. O que NÃO coletamos\n" +
    "Não coletamos o seu nome, telefone, e-mail, foto, contatos, localização, mensagens nem os dados da sua conta do Telegram. O login no Telegram (por QR Code ou telefone) acontece diretamente entre o seu aparelho e o Telegram, pela API oficial (TDLib); nós não recebemos nem guardamos essas informações.\n" +
    "\n" +
    "3. Dados que ficam só no seu aparelho\n" +
    "A sessão de login do Telegram, os canais selecionados, as preferências (capas, animações, filtros, quantidade de cards) e cópias locais de Minha lista, do histórico e do progresso. Esses dados são apagados ao sair da conta ou desinstalar o aplicativo.\n" +
    "\n" +
    "4. Dados enviados ao nosso servidor\n" +
    "O aplicativo usa um servidor próprio (hospedado no Supabase) para os recursos abaixo. Cada aparelho recebe um identificador anônimo aleatório, criado automaticamente, que não está ligado ao seu nome, telefone ou conta do Telegram.\n" +
    "\n" +
    "• Minha lista, histórico e progresso (para sincronizar entre aparelhos e não se perderem): identificação do vídeo (número da mensagem no canal), título, nome do canal, endereço da capa, ano, posição e duração assistidas e as datas de cada alteração.\n" +
    "• Conectar aparelhos (quando você usa a opção): nome do aparelho (tipo e modelo, por exemplo \"TV · AFTKM\"), data do vínculo e o código temporário de 6 caracteres.\n" +
    "• Reportar problema (só quando você usa a opção em um vídeo): motivo escolhido, o texto que você escrever, título, ano, canal, identificação do vídeo, modelo do aparelho, versão do Android e versão do aplicativo.\n" +
    "• Dados de uso e diagnóstico (para encontrar falhas e melhorar o aplicativo): abertura do aplicativo, vídeo aberto (identificação do vídeo), erros de reprodução (código do erro, decodificador e formato do vídeo), buscas que não tiveram resultado (o texto pesquisado) e reportes enviados, sempre com o modelo do aparelho e a versão do aplicativo.\n" +
    "• Pagamentos do NBR Play Premium (só se você assinar): plano escolhido, valor, situação do pagamento, número do pagamento no Mercado Pago, datas e a validade do Premium. O pagamento em si acontece no seu banco e no Mercado Pago: não recebemos nem guardamos seus dados bancários, CPF ou nome.\n" +
    "• Dados técnicos de conexão: o endereço IP é usado no momento de cada acesso para segurança e para limitar abusos (limite de requisições por minuto). Ele não é associado ao seu perfil e fica apenas nos registros técnicos do provedor por poucos dias.\n" +
    "\n" +
    "Se o servidor estiver fora do ar, o reporte de problema pode ser enviado como mensagem privada, pela sua própria conta do Telegram, ao administrador do aplicativo. Nesse caso o administrador vê o seu perfil do Telegram. Nada é enviado sem a sua ação.\n" +
    "\n" +
    "5. Para que usamos e com qual base legal\n" +
    "• Sincronizar Minha lista, histórico e progresso; conectar aparelhos; receber reportes: Execução do serviço que você usa (inciso V)\n" +
    "• Diagnóstico de falhas, melhoria do aplicativo, segurança e prevenção de abuso: Legítimo interesse (inciso IX), com dados mínimos e sem identificar você\n" +
    "• Cobrar e liberar o Premium: Execução do contrato de assinatura (inciso V)\n" +
    "• Guardar os registros de pagamento: Cumprimento de obrigação legal e fiscal (inciso II)\n" +
    "\n" +
    "Não usamos os dados para publicidade, não vendemos dados e não criamos perfis para fins comerciais.\n" +
    "\n" +
    "6. Por quanto tempo guardamos\n" +
    "• Identificador anônimo e os dados de sincronização: Enquanto o aplicativo estiver em uso; apagados automaticamente após 60 dias sem uso\n" +
    "• Itens removidos de Minha lista/histórico: Marcados como removidos e apagados de vez após 90 dias\n" +
    "• Dados de uso e diagnóstico: 90 dias\n" +
    "• Códigos de \"Conectar aparelhos\": Válidos por 10 minutos; apagados 1 dia depois de vencer\n" +
    "• Contadores de limite de requisições: Poucos minutos\n" +
    "• Reportes de problema: Até a análise; quando o identificador anônimo é apagado, o reporte perde o vínculo com ele\n" +
    "• Registros de pagamento e validade do Premium: Pelo prazo exigido pela legislação fiscal (em geral, 5 anos); contas com Premium ativo não são apagadas por inatividade\n" +
    "\n" +
    "7. Com quem compartilhamos\n" +
    "Não compartilhamos dados com anunciantes. Usamos os seguintes serviços para o aplicativo funcionar:\n" +
    "\n" +
    "• Supabase (hospedagem do servidor e do banco de dados), com servidores nos Estados Unidos.\n" +
    "• Mercado Pago (processa o pagamento por Pix do Premium), sujeito à Política de Privacidade do próprio Mercado Pago.\n" +
    "• Telegram (login e acesso aos canais), sujeito à Política de Privacidade e aos Termos do próprio Telegram.\n" +
    "• GitHub (download do índice de filmes e das atualizações do aplicativo) e The Movie Database — TMDB (imagens de capas e elenco): recebem apenas o endereço IP no momento do download.\n" +
    "• Google (gstatic.com) e Cloudflare (speed.cloudflare.com): só quando você usa o Teste de conexão, para medir latência e velocidade; o resultado fica no aparelho.\n" +
    "\n" +
    "8. Transferência internacional\n" +
    "O servidor fica nos Estados Unidos. A transferência é feita para a execução do serviço que você usa (LGPD, art. 33, inciso IX) e com um provedor que adota medidas de segurança compatíveis (conexão criptografada, controle de acesso e isolamento por usuário).\n" +
    "\n" +
    "9. Segurança\n" +
    "Toda comunicação com o servidor é criptografada (HTTPS). Cada aparelho só acessa os próprios dados (ou os da conta que você conectou), o servidor limita a quantidade de requisições e não guardamos senhas, números de telefone ou dados do Telegram.\n" +
    "\n" +
    "10. Seus direitos\n" +
    "Você pode pedir confirmação de tratamento, acesso, correção, anonimização, portabilidade ou eliminação dos dados, e revogar o consentimento quando ele for a base legal (LGPD, art. 18), pelo e-mail nbrplay@outlook.com. Como os dados no servidor estão ligados a um identificador anônimo, informe o modelo do aparelho e a data aproximada de uso para localizarmos os registros.\n" +
    "\n" +
    "Também é possível, sem pedir nada:\n" +
    "• Desconectar aparelhos em Configurações › Conectar aparelhos;\n" +
    "• Apagar os dados do aparelho saindo da conta ou desinstalando o aplicativo — os dados do servidor são apagados automaticamente após 60 dias sem uso.\n" +
    "\n" +
    "11. Permissões do aparelho\n" +
    "• Internet: para se comunicar com o Telegram, com o servidor e reproduzir os vídeos.\n" +
    "• Estado da rede: para detectar quedas e trocas de rede e reconectar mais rápido.\n" +
    "• Manter o aparelho ativo: para não interromper a reprodução e os downloads.\n" +
    "• Instalar e remover aplicativos: para instalar novas versões do NBR Play e, se necessário, trocar uma versão instalada — sempre com a sua confirmação.\n" +
    "\n" +
    "12. Conteúdo\n" +
    "Todo o conteúdo exibido pertence aos canais e usuários do Telegram. O NBR Play não é responsável pelo conteúdo publicado nesses canais e não realiza qualquer distribuição de mídia.\n" +
    "\n" +
    "13. Crianças\n" +
    "O aplicativo não é direcionado a crianças e não coleta intencionalmente dados de menores.\n" +
    "\n" +
    "14. Alterações\n" +
    "Esta política pode ser atualizada; a data no topo indica a versão vigente.\n" +
    "\n" +
    "15. Contato\n" +
    "Dúvidas ou pedidos sobre esta política: nbrplay@outlook.com."


/**
 * Prévia do card (40): como o card aparece enquanto a capa não chega. Cada opção traz uma prévia ao
 * vivo, no formato do card (2:3), para comparar antes de escolher. Celular = subtela; TV = painel central.
 */
@Composable
private fun CardLoadingPickerOverlay(
    tv: Boolean,
    current: CardLoadingStyle,
    animationsEnabled: Boolean,
    onSelect: (CardLoadingStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler(enabled = true) { onDismiss() }
    val options: @Composable () -> Unit = {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 14.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(CardLoadingStyle.entries, key = { it.name }) { style ->
                CardLoadingOption(
                    tv = tv,
                    style = style,
                    selected = style == current,
                    animationsEnabled = animationsEnabled,
                    modifier = if (style == current) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onSelect(style); onDismiss() }
                )
            }
        }
    }
    if (tv) {
        SettingsTvDialog(
            width = 640.dp,
            modifier = Modifier.onPreviewKeyEvent { e ->
                e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape) && run { onDismiss(); true }
            }
        ) {
            Text("Prévia do card", style = NbrType.PageTitle, fontWeight = FontWeight.ExtraBold, color = SettingsText)
            Text("Prévia de como o card aparece enquanto a capa carrega.", style = NbrType.Caption, color = BrandColors.TextSecondary)
            options()
        }
    } else {
        SettingsPhonePage(title = "Prévia do card", onBack = onDismiss, gap = 16.dp) {
            Text("Prévia de como o card aparece enquanto a capa carrega.", style = NbrType.Body, color = BrandColors.TextSecondary)
            options()
        }
    }
}

@Composable
private fun CardLoadingOption(
    tv: Boolean,
    style: CardLoadingStyle,
    selected: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = BrandShapes.Md
    Column(
        modifier = modifier
            .width(if (tv) 120.dp else 150.dp)
            .glideTarget(12.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CardLoadingPlaceholder(
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .border(
                    if ((focused && !gliding) || selected) 2.dp else 1.dp,
                    when {
                        focused && !gliding -> Color.White
                        selected -> BrandColors.Accent
                        else -> BrandColors.BorderSubtle
                    },
                    shape
                ),
            animate = animationsEnabled,
            cover = null,
            preview = true
        )
        Text(
            if (selected) "${style.label} ·" else style.label,
            color = if (selected) BrandColors.Accent else BrandColors.TextSecondary,
            style = NbrType.Caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            style.description,
            color = BrandColors.TextMuted,
            style = NbrType.Caption,
            fontSize = if (tv) 10.sp else 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 3
        )
    }
}
