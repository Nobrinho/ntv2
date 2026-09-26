package com.ntv2.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val BRAND = Color(0xFF2BEE34)

enum class MainTab {
    Library,
    MyStuff,
    Channels,
    Settings
}

@Composable
fun MainBottomNav(
    selected: MainTab,
    onLibrary: () -> Unit,
    onChannels: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    // "Meu" (histórico/personalização): só aparece quando a tela fornece a ação.
    onMyStuff: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF20C0C0C))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainBottomNavItem(
            icon = Icons.Filled.Movie,
            label = "Biblioteca",
            selected = selected == MainTab.Library,
            modifier = Modifier.weight(1f),
            onClick = onLibrary
        )
        if (onMyStuff != null) {
            MainBottomNavItem(
                icon = Icons.Filled.Favorite,
                label = "Meu",
                selected = selected == MainTab.MyStuff,
                modifier = Modifier.weight(1f),
                onClick = onMyStuff
            )
        }
        MainBottomNavItem(
            icon = Icons.Filled.Subscriptions,
            label = "Canais",
            selected = selected == MainTab.Channels,
            modifier = Modifier.weight(1f),
            onClick = onChannels
        )
        MainBottomNavItem(
            icon = Icons.Filled.Settings,
            label = "Config",
            selected = selected == MainTab.Settings,
            modifier = Modifier.weight(1f),
            onClick = onSettings
        )
    }
}

@Composable
private fun MainBottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val content = if (selected) Color.Black else Color.White
    val background = if (selected) BRAND else Color(0x22FFFFFF)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .background(background)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(22.dp))
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(19.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
