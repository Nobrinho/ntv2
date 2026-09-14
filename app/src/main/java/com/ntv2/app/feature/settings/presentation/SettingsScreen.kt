package com.ntv2.app.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewModule
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
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.NavRail
import kotlinx.coroutines.launch

private val BRAND = Color(0xFF2BEE34)

@Composable
fun SettingsScreen(
    showCovers: Boolean,
    animationsEnabled: Boolean,
    castPhotos: Boolean,
    minDurationMinutes: Int,
    maxCards: Int,
    onToggleCovers: (Boolean) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onToggleCastPhotos: (Boolean) -> Unit,
    onChangeMinDuration: (Int) -> Unit,
    onChangeMaxCards: (Int) -> Unit,
    onManageChannels: () -> Unit,
    onLogout: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val firstItemFocus = remember { FocusRequester() }
    var confirmLogout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstItemFocus.requestFocus() } }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Rail (Busca/Canais/Atualizar voltam à Biblioteca; Config = atual).
            NavRail(
                settingsActive = true,
                onSearch = onOpenLibrary,
                onChannels = onOpenLibrary,
                onRefresh = onOpenLibrary,
                onSettings = {}
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 28.dp)
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
                        modifier = Modifier.focusRequester(firstItemFocus),
                        onToggle = { onToggleCovers(!showCovers) }
                    )
                    ToggleCard(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Animações",
                        subtitle = "Ativar animações e transições da interface",
                        value = animationsEnabled,
                        modifier = Modifier,
                        onToggle = { onToggleAnimations(!animationsEnabled) }
                    )
                    ToggleCard(
                        icon = Icons.Filled.Group,
                        title = "Fotos do elenco",
                        subtitle = "Mostrar rostos do elenco nos detalhes (senão, só nomes)",
                        value = castPhotos,
                        modifier = Modifier,
                        onToggle = { onToggleCastPhotos(!castPhotos) }
                    )
                    DurationCard(
                        value = minDurationMinutes,
                        onChange = onChangeMinDuration
                    )
                    MaxCardsCard(
                        value = maxCards,
                        onChange = onChangeMaxCards
                    )
                    NavCard(
                        icon = Icons.Filled.Tv,
                        title = "Seleção de canais",
                        subtitle = "Gerenciar os canais disponíveis",
                        destructive = false,
                        onClick = onManageChannels
                    )
                    NavCard(
                        icon = Icons.Filled.Shield,
                        title = "Política de privacidade",
                        subtitle = "Como o app trata seus dados",
                        destructive = false,
                        onClick = { showPrivacy = true }
                    )
                    Spacer(Modifier.size(8.dp))
                    NavCard(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Sair da conta",
                        subtitle = "Encerrar a sessão neste dispositivo",
                        destructive = true,
                        onClick = { confirmLogout = true }
                    )
                }
            }
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

private val DURATION_STEPS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
private fun DurationCard(
    value: Int,
    onChange: (Int) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val idx = DURATION_STEPS.indexOfFirst { it >= value }.let { if (it < 0) DURATION_STEPS.lastIndex else it }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> {
                        if (idx > 0) onChange(DURATION_STEPS[idx - 1]); true
                    }
                    Key.DirectionRight -> {
                        if (idx < DURATION_STEPS.lastIndex) onChange(DURATION_STEPS[idx + 1]); true
                    }
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = BRAND, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Filtro de duração mínima", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Ocultar vídeos mais curtos que este tempo (← / →)", color = Color(0xFFB0B0B0), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("◄", color = if (idx > 0) Color.White else Color(0x44FFFFFF), style = MaterialTheme.typography.titleMedium)
            Text(
                if (value <= 0) "Off" else "$value min",
                color = BRAND,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.Center
            )
            Text("►", color = if (idx < DURATION_STEPS.lastIndex) Color.White else Color(0x44FFFFFF), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val MAX_CARDS_STEPS = listOf(60, 90, 120, 150, 200, 250, 300)

@Composable
private fun MaxCardsCard(
    value: Int,
    onChange: (Int) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val idx = MAX_CARDS_STEPS.indexOfFirst { it >= value }.let { if (it < 0) MAX_CARDS_STEPS.lastIndex else it }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> {
                        if (idx > 0) onChange(MAX_CARDS_STEPS[idx - 1]); true
                    }
                    Key.DirectionRight -> {
                        if (idx < MAX_CARDS_STEPS.lastIndex) onChange(MAX_CARDS_STEPS[idx + 1]); true
                    }
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ViewModule, contentDescription = null, tint = BRAND, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Máximo de cards na grade", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Limita a memória ao carregar mais (← / →)", color = Color(0xFFB0B0B0), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("◄", color = if (idx > 0) Color.White else Color(0x44FFFFFF), style = MaterialTheme.typography.titleMedium)
            Text(
                "$value",
                color = BRAND,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.Center
            )
            Text("►", color = if (idx < MAX_CARDS_STEPS.lastIndex) Color.White else Color(0x44FFFFFF), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean,
    onClick: () -> Unit
) {
    SettingCardShell(
        icon = icon,
        title = title,
        subtitle = subtitle,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Política de Privacidade", style = MaterialTheme.typography.headlineMedium, color = Color.White)
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
                            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-320f) }; true }
                            else -> false
                        }
                    }
                    .verticalScroll(scroll)
                    .padding(24.dp)
            ) {
                Text(
                    PRIVACY_POLICY_TEXT,
                    color = Color(0xFFCFCFCF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Use ↑ / ↓ para rolar · Voltar para fechar",
                color = Color(0xFF9A9A9A),
                style = MaterialTheme.typography.bodySmall
            )
        }
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

