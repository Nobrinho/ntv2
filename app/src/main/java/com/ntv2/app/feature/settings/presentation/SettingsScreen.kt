package com.ntv2.app.feature.settings.presentation

import com.ntv2.app.core.ui.reserveMainBottomNav
import com.ntv2.app.core.ui.BrandBackdrop
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.focusProperties
import com.ntv2.app.core.ui.trapFocus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.MainBottomNav
import com.ntv2.app.core.ui.MainTab
import com.ntv2.app.core.ui.NavRail
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.CloudDownload
import com.ntv2.app.feature.media.data.index.SearchIndexStatus
import com.ntv2.app.core.ui.CardLoadingPlaceholder
import com.ntv2.app.core.ui.CardLoadingStyle
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.ntv2.app.BuildConfig
import com.ntv2.app.feature.update.presentation.UpdateStage
import com.ntv2.app.feature.update.presentation.UpdateUiState

private val BRAND = com.ntv2.app.core.ui.BrandColors.Accent

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
    deviceLink: com.ntv2.app.core.backend.link.DeviceLinkRepository? = null
) {
    var showConnectionTest by remember { mutableStateOf(false) }
    var showDeviceLink by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showCardLoadingPicker by remember { mutableStateOf(false) }
    val adaptive = rememberAdaptiveLayoutInfo()
    // Um FocusRequester por item; o último focado é lembrado (inclusive ao voltar de outra tela
    // ou fechar um modal), em vez de sempre voltar ao primeiro.
    val itemFocus = remember { List(SETTINGS_ITEM_COUNT) { FocusRequester() } }
    var lastFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    fun itemMod(index: Int) = Modifier
        .focusRequester(itemFocus[index])
        .onFocusChanged { if (it.isFocused) lastFocusIndex = index }
    LaunchedEffect(showPrivacy, confirmLogout, showCardLoadingPicker, showConnectionTest, showDeviceLink) {
        if (adaptive.useTvLayout && !showPrivacy && !confirmLogout && !showCardLoadingPicker && !showConnectionTest && !showDeviceLink) {
            runCatching { itemFocus[lastFocusIndex].requestFocus() }
        }
    }

    FocusGlideScope(Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(com.ntv2.app.core.ui.BrandColors.Background)) {
        BrandBackdrop(com.ntv2.app.core.ui.BrandBackdropKind.Minimal)
        val useTvLayout = adaptive.useTvLayout && maxWidth >= 720.dp
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Celular: respeita a barra de status (o título "Configurações" ficava por baixo da hora).
                    .then(if (useTvLayout) Modifier else Modifier.statusBarsPadding().reserveMainBottomNav())
                    .padding(
                        horizontal = if (useTvLayout) adaptive.tvSafeHorizontal else 16.dp,
                        vertical = if (useTvLayout) adaptive.tvSafeVertical else 14.dp
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Configurações", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.size(8.dp))

                Column(
                    modifier = Modifier.focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingsSectionLabel("Interface")
                    ToggleCard(
                        icon = Icons.Filled.Image,
                        title = "Capas",
                        subtitle = "Exibir capas dos conteúdos na interface",
                        value = showCovers,
                        modifier = itemMod(0),
                        onToggle = { onToggleCovers(!showCovers) }
                    )
                    ToggleCard(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Animações",
                        subtitle = "Animações e transições (luz pulsante ao carregar, controles do player e rolagem do título)",
                        value = animationsEnabled,
                        modifier = itemMod(1),
                        onToggle = { onToggleAnimations(!animationsEnabled) }
                    )
                    ToggleCard(
                        icon = Icons.Filled.BlurOn,
                        title = "Iluminação da capa: desfoque nativo",
                        subtitle = if (android.os.Build.VERSION.SDK_INT >= 31) {
                            "ON = desfoque do Android 12+ · OFF = versão compatível"
                        } else {
                            "Este aparelho (Android ${android.os.Build.VERSION.RELEASE}) usa sempre a versão compatível"
                        },
                        value = nativeBlurGlow,
                        modifier = itemMod(8),
                        onToggle = { onToggleNativeBlurGlow(!nativeBlurGlow) }
                    )
                    ToggleCard(
                        icon = Icons.Filled.Group,
                        title = "Fotos do elenco",
                        subtitle = "Mostrar rostos do elenco nos detalhes (senão, só nomes)",
                        value = castPhotos,
                        modifier = itemMod(2),
                        onToggle = { onToggleCastPhotos(!castPhotos) }
                    )
                    NavCard(
                        icon = Icons.Filled.Animation,
                        title = "Animação dos cards",
                        subtitle = "Enquanto a capa carrega: ${cardLoadingStyle.label}",
                        destructive = false,
                        modifier = itemMod(4),
                        onClick = { showCardLoadingPicker = true }
                    )
                    SettingsSectionLabel("Conteúdo", topGap = true)
                    DurationCard(
                        modifier = itemMod(3),
                        value = minDurationMinutes,
                        onChange = onChangeMinDuration
                    )
                    NavCard(
                        icon = Icons.Filled.Tv,
                        title = "Seleção de canais",
                        subtitle = "Gerenciar os canais disponíveis",
                        destructive = false,
                        modifier = itemMod(5),
                        onClick = onManageChannels
                    )
                    if (indexStatus != null) {
                        NavCard(
                            icon = Icons.Filled.CloudDownload,
                            title = "Índice de filmes e séries",
                            subtitle = indexSubtitle(indexStatus),
                            destructive = false,
                            modifier = itemMod(11),
                            onClick = { if (!indexStatus.refreshing) onRefreshIndex() }
                        )
                    }
                    SettingsSectionLabel("Conta e app", topGap = true)
                    if (deviceLink != null) {
                        NavCard(
                            icon = Icons.Filled.Devices,
                            title = "Conectar aparelhos",
                            subtitle = "Compartilhar Minha lista e o progresso entre TV e celular",
                            destructive = false,
                            modifier = itemMod(13),
                            onClick = { showDeviceLink = true }
                        )
                    }
                    if (runConnectionTest != null) {
                        NavCard(
                            icon = Icons.Filled.NetworkCheck,
                            title = "Teste de conexão",
                            subtitle = "Medir internet, Telegram e velocidade neste aparelho",
                            destructive = false,
                            modifier = itemMod(10),
                            onClick = { showConnectionTest = true }
                        )
                    }
                    NavCard(
                        icon = Icons.Filled.SystemUpdate,
                        title = "Atualizações",
                        subtitle = updateSubtitle(updateState),
                        destructive = false,
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
                        }
                    )
                    NavCard(
                        icon = Icons.Filled.Shield,
                        title = "Política de privacidade",
                        subtitle = "Como o app trata seus dados",
                        destructive = false,
                        modifier = itemMod(6),
                        onClick = { showPrivacy = true }
                    )
                    NavCard(
                        icon = Icons.Filled.School,
                        title = "Ver introdução novamente",
                        subtitle = "Rever a apresentação do app",
                        destructive = false,
                        modifier = itemMod(12),
                        onClick = onShowIntro
                    )
                    Spacer(Modifier.size(8.dp))
                    NavCard(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Sair da conta",
                        subtitle = "Encerrar a sessão neste dispositivo",
                        destructive = true,
                        modifier = itemMod(7),
                        onClick = { confirmLogout = true }
                    )
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

        if (showCardLoadingPicker) {
        CardLoadingPickerOverlay(
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
            DeviceLinkOverlay(repository = deviceLink, isTv = adaptive.useTvLayout, onClose = { showDeviceLink = false })
        }

        if (showConnectionTest && runConnectionTest != null) {
            ConnectionTestOverlay(runTest = runConnectionTest, onClose = { showConnectionTest = false })
        }

        if (confirmLogout) {
            ConfirmDialog(
                title = "Sair da conta?",
                message = "Você será desconectado deste dispositivo.",
                icon = Icons.AutoMirrored.Filled.Logout,
                confirmLabel = "Sair",
                confirmIcon = Icons.AutoMirrored.Filled.Logout,
                cancelLabel = "Cancelar",
                cancelIcon = Icons.Filled.Close,
                destructive = true,
                onConfirm = { confirmLogout = false; onLogout() },
                onDismiss = { confirmLogout = false }
            )
        }
    }
    }
}

@Composable
private fun ToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit
) {
    SettingCardShell(icon = icon, title = title, subtitle = subtitle, modifier = modifier, onClick = onToggle) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (value) BRAND else com.ntv2.app.core.ui.BrandColors.Border)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                if (value) "ON" else "OFF",
                color = if (value) Color.Black else Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private const val SETTINGS_ITEM_COUNT = 14

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

@Composable
private fun DurationCard(
    modifier: Modifier = Modifier,
    value: Int,
    onChange: (Int) -> Unit
) {
    val idx = DURATION_STEPS.indexOfFirst { it >= value }.let { if (it < 0) DURATION_STEPS.lastIndex else it }
    StepperSettingCard(
        modifier = modifier,
        icon = Icons.Filled.Timer,
        title = "Duração mínima",
        subtitle = "Oculta vídeos mais curtos que o tempo escolhido.",
        valueText = if (value <= 0) "Off" else "$value min",
        canDecrease = idx > 0,
        canIncrease = idx < DURATION_STEPS.lastIndex,
        onDecrease = { if (idx > 0) onChange(DURATION_STEPS[idx - 1]) },
        onIncrease = { if (idx < DURATION_STEPS.lastIndex) onChange(DURATION_STEPS[idx + 1]) }
    )
}

@Composable
private fun StepperSettingCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .glideTarget(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    // No mínimo, ← não é consumido: o foco pode sair para o rail.
                    Key.DirectionLeft -> if (canDecrease) { onDecrease(); true } else false
                    Key.DirectionRight -> { if (canIncrease) onIncrease(); true }
                    else -> false
                }
            }
            .focusable()
            .background(if (focused) com.ntv2.app.core.ui.BrandColors.BorderSubtle else com.ntv2.app.core.ui.BrandColors.Fill)
            .border(
                width = if (focused && !gliding) 2.dp else 1.dp,
                color = if (focused && !gliding) Color.White else com.ntv2.app.core.ui.BrandColors.Border,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        val compact = maxWidth < 560.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StepperSettingText(icon, title, subtitle, Modifier.fillMaxWidth())
                StepperControl(
                    valueText = valueText,
                    focused = focused,
                    canDecrease = canDecrease,
                    canIncrease = canIncrease,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepperSettingText(icon, title, subtitle, Modifier.weight(1f))
                StepperControl(
                    valueText = valueText,
                    focused = focused,
                    canDecrease = canDecrease,
                    canIncrease = canIncrease,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    modifier = Modifier.width(224.dp)
                )
            }
        }
    }
}

@Composable
private fun StepperSettingText(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BRAND, modifier = Modifier.size(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = com.ntv2.app.core.ui.BrandColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StepperControl(
    valueText: String,
    focused: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x16000000))
            .border(1.dp, com.ntv2.app.core.ui.BrandColors.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Com foco (D-pad): setas indicam que ← / → ajustam o valor.
        StepTouchButton(if (focused) "◀" else "-", canDecrease, onDecrease)
        Text(
            valueText,
            color = BRAND,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        StepTouchButton(if (focused) "▶" else "+", canIncrease, onIncrease)
    }
}

@Composable
private fun StepTouchButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            // Só toque: no D-pad quem ajusta é o card (← / →); o botão não recebe foco.
            .focusProperties { canFocus = false }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (enabled) Color(0x2BFFFFFF) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else com.ntv2.app.core.ui.BrandColors.BorderStrong, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SettingCardShell(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        titleColor = if (destructive) com.ntv2.app.core.ui.BrandColors.Error else Color.White,
        onClick = onClick
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = com.ntv2.app.core.ui.BrandColors.TextSecondary,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun SettingCardShell(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.White,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glideTarget(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) com.ntv2.app.core.ui.BrandColors.BorderSubtle else com.ntv2.app.core.ui.BrandColors.Fill)
            .border(
                width = if (focused && !gliding) 2.dp else 1.dp,
                color = if (focused && !gliding) Color.White else com.ntv2.app.core.ui.BrandColors.Border,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ação destrutiva (Sair da conta): ícone na mesma cor vermelha do título.
        Icon(icon, contentDescription = null, tint = if (titleColor == Color.White) BRAND else titleColor, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = com.ntv2.app.core.ui.BrandColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
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
            Icons.Filled.Close,
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
    "• Dados técnicos de conexão: o endereço IP é usado no momento de cada acesso para segurança e para limitar abusos (limite de requisições por minuto). Ele não é associado ao seu perfil e fica apenas nos registros técnicos do provedor por poucos dias.\n" +
    "\n" +
    "Se o servidor estiver fora do ar, o reporte de problema pode ser enviado como mensagem privada, pela sua própria conta do Telegram, ao administrador do aplicativo. Nesse caso o administrador vê o seu perfil do Telegram. Nada é enviado sem a sua ação.\n" +
    "\n" +
    "5. Para que usamos e com qual base legal\n" +
    "• Sincronizar Minha lista, histórico e progresso; conectar aparelhos; receber reportes: Execução do serviço que você usa (inciso V)\n" +
    "• Diagnóstico de falhas, melhoria do aplicativo, segurança e prevenção de abuso: Legítimo interesse (inciso IX), com dados mínimos e sem identificar você\n" +
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
    "\n" +
    "7. Com quem compartilhamos\n" +
    "Não compartilhamos dados com anunciantes. Usamos os seguintes serviços para o aplicativo funcionar:\n" +
    "\n" +
    "• Supabase (hospedagem do servidor e do banco de dados), com servidores nos Estados Unidos.\n" +
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
 * Escolha da animação que o card mostra enquanto a capa não chega. Cada opção traz uma prévia ao
 * vivo, no mesmo formato do card (2:3), para dar para comparar antes de escolher.
 */
@Composable
private fun CardLoadingPickerOverlay(
    current: CardLoadingStyle,
    animationsEnabled: Boolean,
    onSelect: (CardLoadingStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler(enabled = true) { onDismiss() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2090B0F))
            .onPreviewKeyEvent { e ->
                e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape) &&
                    run { onDismiss(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Animação dos cards", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                "Prévia de como o card aparece enquanto a capa carrega.",
                color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(CardLoadingStyle.entries, key = { it.name }) { style ->
                    CardLoadingOption(
                        style = style,
                        selected = style == current,
                        animationsEnabled = animationsEnabled,
                        modifier = if (style == current) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { onSelect(style); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardLoadingOption(
    style: CardLoadingStyle,
    selected: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Column(
        modifier = modifier
            .width(150.dp)
            .glideTarget(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) com.ntv2.app.core.ui.BrandColors.BorderSubtle else com.ntv2.app.core.ui.BrandColors.Fill)
            .border(
                width = if ((focused && !gliding) || selected) 2.dp else 1.dp,
                color = when {
                    focused && !gliding -> Color.White
                    selected -> BRAND
                    else -> com.ntv2.app.core.ui.BrandColors.Border
                },
                shape = RoundedCornerShape(6.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CardLoadingPlaceholder(
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
            animate = animationsEnabled,
            cover = null,
            preview = true
        )
        Text(
            if (selected) "${style.label} ·" else style.label,
            color = if (selected) BRAND else Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )
        Text(
            style.description,
            color = com.ntv2.app.core.ui.BrandColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 3
        )
    }
}

/** Rótulo de grupo da lista de configurações (não recebe foco). */
@Composable
private fun SettingsSectionLabel(text: String, topGap: Boolean = false) {
    Text(
        text.uppercase(),
        color = com.ntv2.app.core.ui.BrandColors.Accent,
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = if (topGap) 10.dp else 0.dp)
    )
}
