package com.ntv2.app.feature.settings.presentation

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
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Shield
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.ConfirmDialog
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
import com.ntv2.app.core.ui.CardLoadingPlaceholder
import com.ntv2.app.core.ui.CardLoadingStyle
import androidx.compose.ui.input.key.onPreviewKeyEvent

private val BRAND = Color(0xFF2BEE34)

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
    // Rail: Busca abre a busca da Biblioteca; Atualizar recarrega a Biblioteca.
    onSearch: () -> Unit = onOpenLibrary,
    onRefresh: () -> Unit = onOpenLibrary
) {
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
    LaunchedEffect(showPrivacy, confirmLogout, showCardLoadingPicker) {
        if (adaptive.useTvLayout && !showPrivacy && !confirmLogout && !showCardLoadingPicker) {
            runCatching { itemFocus[lastFocusIndex].requestFocus() }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {
        val useTvLayout = adaptive.useTvLayout && maxWidth >= 720.dp
        Row(modifier = Modifier.fillMaxSize()) {
            // Rail (Busca/Canais/Atualizar voltam à Biblioteca; Config = atual).
            if (useTvLayout) {
                NavRail(
                    settingsActive = true,
                    onSearch = onSearch,
                    onChannels = onOpenListedChannels,
                    onRefresh = onRefresh,
                    onSettings = {}
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (useTvLayout) Modifier else Modifier.padding(bottom = 76.dp))
                    .padding(
                        horizontal = if (useTvLayout) 40.dp else 16.dp,
                        vertical = if (useTvLayout) 28.dp else 14.dp
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
                    DurationCard(
                        modifier = itemMod(3),
                        value = minDurationMinutes,
                        onChange = onChangeMinDuration
                    )
                    NavCard(
                        icon = Icons.Filled.Animation,
                        title = "Animação dos cards",
                        subtitle = "Enquanto a capa carrega: ${cardLoadingStyle.label}",
                        destructive = false,
                        modifier = itemMod(4),
                        onClick = { showCardLoadingPicker = true }
                    )
                    NavCard(
                        icon = Icons.Filled.Tv,
                        title = "Seleção de canais",
                        subtitle = "Gerenciar os canais disponíveis",
                        destructive = false,
                        modifier = itemMod(5),
                        onClick = onManageChannels
                    )
                    NavCard(
                        icon = Icons.Filled.Shield,
                        title = "Política de privacidade",
                        subtitle = "Como o app trata seus dados",
                        destructive = false,
                        modifier = itemMod(6),
                        onClick = { showPrivacy = true }
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
                .clip(RoundedCornerShape(20.dp))
                .background(if (value) BRAND else Color(0x33FFFFFF))
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

private const val SETTINGS_ITEM_COUNT = 9

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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
            .background(if (focused) Color(0x22FFFFFF) else Color(0x11FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp)
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
            Text(subtitle, color = Color(0xFFB0B0B0), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x16000000))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
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
            .clip(RoundedCornerShape(20.dp))
            // Só toque: no D-pad quem ajusta é o card (← / →); o botão não recebe foco.
            .focusProperties { canFocus = false }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (enabled) Color(0x2BFFFFFF) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else Color(0x44FFFFFF), style = MaterialTheme.typography.titleMedium)
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
        titleColor = if (destructive) Color(0xFFFF6B6B) else Color.White,
        onClick = onClick
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFB0B0B0),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x22FFFFFF) else Color(0x11FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BRAND, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xFFB0B0B0), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
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
                    color = Color(0xFFCFCFCF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                if (compact) "Deslize para rolar · toque no X para fechar" else "Use ↑ / ↓ para rolar · ↑ no topo vai ao X · Voltar para fechar",
                color = Color(0xFF9A9A9A),
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
            .clip(RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClose)
            .background(if (focused) Color.White else Color(0x1FFFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Fechar",
            tint = if (focused) Color.Black else Color(0xFFCFCFCF),
            modifier = Modifier.size(22.dp)
        )
    }
}

private const val PRIVACY_POLICY_TEXT =
    "Última atualização: 12/09/2026\n\n" +
    "O Nbr PLAY é um aplicativo cliente de mídia para TV que exibe e reproduz vídeos dos " +
    "canais do Telegram escolhidos pelo próprio usuário. O aplicativo não hospeda, não " +
    "distribui e não disponibiliza conteúdo: ele apenas organiza e reproduz o que já existe " +
    "nos canais aos quais a sua conta do Telegram tem acesso.\n\n" +
    "1. Dados que tratamos\n" +
    "Não possuímos servidores próprios e não coletamos, armazenamos ou compartilhamos seus " +
    "dados pessoais conosco. O aplicativo se conecta diretamente ao Telegram usando a API " +
    "oficial do Telegram (TDLib). A autenticação (por QR Code ou telefone) é feita entre o " +
    "seu dispositivo e o Telegram.\n\n" +
    "2. Armazenamento no dispositivo\n" +
    "Ficam salvos apenas localmente no seu aparelho: a sessão de login do Telegram (para " +
    "manter você conectado), os canais que você selecionou, suas preferências (capas, " +
    "animações, filtros, máximo de cards) e o progresso de reprodução. Esses dados não são " +
    "enviados para nós nem para terceiros e podem ser apagados ao sair da conta ou desinstalar " +
    "o aplicativo.\n\n" +
    "3. Permissões\n" +
    "O aplicativo usa apenas a permissão de Internet, necessária para se comunicar com o " +
    "Telegram e reproduzir os vídeos.\n\n" +
    "4. Terceiros\n" +
    "O uso do Telegram está sujeito à Política de Privacidade e aos Termos do próprio Telegram. " +
    "Não utilizamos publicidade nem ferramentas de análise/rastreamento.\n\n" +
    "5. Conteúdo\n" +
    "Todo o conteúdo exibido pertence aos canais e usuários do Telegram. O Nbr PLAY não é " +
    "responsável pelo conteúdo publicado nesses canais e não realiza qualquer distribuição " +
    "de mídia.\n\n" +
    "6. Crianças\n" +
    "O aplicativo não é direcionado a crianças e não coleta intencionalmente dados de menores.\n\n" +
    "7. Alterações\n" +
    "Esta política pode ser atualizada; a data no topo indica a versão vigente.\n\n" +
    "8. Contato\n" +
    "Dúvidas sobre esta política: nbrplay@outlook.com."

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
            .background(Color(0xF2050505))
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
                color = Color(0xFFB0B0B0),
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
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x22FFFFFF) else Color(0x11FFFFFF))
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White
                    selected -> BRAND
                    else -> Color(0x33FFFFFF)
                },
                shape = RoundedCornerShape(12.dp)
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
            color = Color(0xFF9A9A9A),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 3
        )
    }
}
