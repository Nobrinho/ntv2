package com.ntv2.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
fun RailColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(116.dp)
            .background(Color(0xFF0C0C0C))
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

/** Item de rail (ícone + rótulo), focável, com estados de destaque e desabilitado. */
@Composable
fun RailButton(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || highlighted
    val tint = when {
        !enabled -> Color(0x44FFFFFF)
        active -> BRAND
        else -> Color.White
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (focused) Color(0x332BEE34) else Color.Transparent)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Text(
            label,
            color = if (active) BRAND else if (enabled) Color(0xFFB0B0B0) else Color(0x44FFFFFF),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

/** Rail de navegação da Biblioteca (Busca/Canais/Atualizar/Config). */
@Composable
fun NavRail(
    firstItemFocus: FocusRequester? = null,
    searchActive: Boolean = false,
    settingsActive: Boolean = false,
    onSearch: () -> Unit,
    onChannels: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    RailColumn {
        RailButton(
            icon = Icons.Filled.Search,
            label = "Busca",
            highlighted = searchActive,
            modifier = if (firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier,
            onClick = onSearch
        )
        RailButton(Icons.Filled.Subscriptions, "Canais", onClick = onChannels)
        RailButton(Icons.Filled.Refresh, "Atualizar", onClick = onRefresh)
        RailButton(Icons.Filled.Settings, "Config", highlighted = settingsActive, onClick = onSettings)
    }
}
