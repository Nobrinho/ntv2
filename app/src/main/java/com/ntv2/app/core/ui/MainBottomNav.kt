package com.ntv2.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/** Altura da barra, sem a área das teclas de navegação. Alvo de toque acima dos 48dp. */
private val BarHeight = 68.dp

/**
 * Reserva o espaço da barra (altura + divisória + teclas de navegação) para o conteúdo da tela
 * não ficar atrás dela, sem número fixo em cada tela.
 */
fun Modifier.reserveMainBottomNav(): Modifier =
    this.padding(bottom = BarHeight + 1.dp).navigationBarsPadding()

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
    onSearch: (() -> Unit)? = null,
    // "Meu" (histórico/personalização): só aparece quando a tela fornece a ação.
    onMyStuff: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().background(BrandColors.Background.copy(alpha = 0.95f))) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BrandColors.SurfaceAlt))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(BarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomNavItem(
                icon = Icons.Filled.Movie,
                label = "Biblioteca",
                selected = selected == MainTab.Library,
                modifier = Modifier.weight(1f),
                onClick = onLibrary
            )
            if (onSearch != null) {
                MainBottomNavItem(
                    icon = Icons.Filled.Search,
                    label = "Busca",
                    selected = false,
                    modifier = Modifier.weight(1f),
                    onClick = onSearch
                )
            }
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
}

@Composable
private fun MainBottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val content = if (selected) BrandColors.Accent else BrandColors.TextSecondary
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Indicador do item ativo: faixa curta colada no topo da barra.
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(BrandColors.Accent)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            Text(
                label,
                color = content,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
