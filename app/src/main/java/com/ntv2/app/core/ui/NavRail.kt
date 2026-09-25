package com.ntv2.app.core.ui


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.R

private val BRAND = Color(0xFF2BEE34)

/** Coluna base do rail (logo Nbr PLAY no topo + slot de itens), compartilhada entre telas. */
@Composable
fun RailColumn(
    // Ao entrar no rail pelo D-pad (← da grade), o foco vai para este item em vez do mais próximo.
    enterFocus: (() -> FocusRequester?)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(116.dp)
            .background(Color(0xFF0C0C0C))
            .then(if (enterFocus != null) Modifier.focusEnterTo(enterFocus) else Modifier)
            .focusGroup()
            .padding(vertical = 24.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = "Nbr PLAY",
            colorFilter = ColorFilter.tint(BRAND),
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/**
 * Item de rail (ícone + rótulo), focável, com estados de destaque e desabilitado.
 *
 * [primary] = ação principal (call-to-action): quando habilitado fica com fundo verde sólido e
 * conteúdo escuro, deixando evidente que já pode ser clicado (ex.: "Continuar" após selecionar).
 */
@Composable
fun RailButton(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    primary: Boolean = false,
    // false = só indicador (ex.: tela atual): não recebe foco nem clique, mas mantém o destaque.
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || highlighted
    val cta = primary && enabled // botão de ação em destaque
    val onCta = Color(0xFF0E0E0E) // conteúdo escuro sobre o verde
    val tint = when {
        !enabled -> Color(0x44FFFFFF)
        cta -> onCta
        active -> BRAND
        else -> Color.White
    }
    val background = when {
        cta -> BRAND
        focused -> Color(0x332BEE34)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled && interactive) Modifier.clickable(onClick = onClick) else Modifier)
            .background(background)
            // Foco sobre o CTA verde: borda branca para não "sumir" o realce de foco.
            .then(
                if (cta && focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Text(
            label,
            color = when {
                !enabled -> Color(0x44FFFFFF)
                cta -> onCta
                active -> BRAND
                else -> Color(0xFFB0B0B0)
            },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

/** Rail de navegação (Início/Busca/Canais/Atualizar/Config). */
@Composable
fun NavRail(
    firstItemFocus: FocusRequester? = null,
    channelsFocus: FocusRequester? = null,
    settingsFocus: FocusRequester? = null,
    searchActive: Boolean = false,
    settingsActive: Boolean = false,
    // Na Biblioteca o Início fica destacado e leva ao topo da grade; nas outras telas, volta a ela.
    homeActive: Boolean = false,
    showClearFilter: Boolean = false,
    onHome: () -> Unit = {},
    onSearch: () -> Unit,
    onClearFilter: () -> Unit = {},
    onChannels: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    RailColumn(enterFocus = firstItemFocus?.let { f -> { f } }) {
        RailButton(icon = Icons.Filled.Home, label = "Início", highlighted = homeActive, onClick = onHome)
        RailButton(
            icon = Icons.Filled.Search,
            label = "Busca",
            highlighted = searchActive,
            modifier = if (firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier,
            onClick = onSearch
        )
        // Só aparece com filtro de texto ativo: limpa sem precisar entrar na busca.
        if (showClearFilter) {
            RailButton(Icons.Filled.FilterAltOff, "Limpar", onClick = onClearFilter)
        }
        RailButton(
            Icons.Filled.Subscriptions,
            "Canais",
            modifier = if (channelsFocus != null) Modifier.focusRequester(channelsFocus) else Modifier,
            onClick = onChannels
        )
        RailButton(Icons.Filled.Refresh, "Atualizar", onClick = onRefresh)
        RailButton(
            Icons.Filled.Settings,
            "Config",
            highlighted = settingsActive,
            interactive = !settingsActive,
            modifier = if (settingsFocus != null) Modifier.focusRequester(settingsFocus) else Modifier,
            onClick = onSettings
        )
    }
}
