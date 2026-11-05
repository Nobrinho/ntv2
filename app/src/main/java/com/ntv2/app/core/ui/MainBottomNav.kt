package com.ntv2.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.icons.NbrIcons

/** Altura da pílula flutuante do protótipo (tab bar), sem a área das teclas de navegação. */
private val BarHeight = 60.dp
/** Distância da pílula ao fundo (acima da barra de navegação) e às laterais. */
private val BarBottomGap = 12.dp
private val BarSideGap = 16.dp

/**
 * Reserva o espaço da barra (pílula + margem + teclas de navegação) para o conteúdo da tela
 * não ficar atrás dela, sem número fixo em cada tela.
 */
fun Modifier.reserveMainBottomNav(): Modifier =
    this.padding(bottom = BarHeight + BarBottomGap).navigationBarsPadding()

/**
 * Mesma reserva de [reserveMainBottomNav], em dp, para usar como padding de conteúdo de listas:
 * o conteúdo rola por trás da pílula translúcida (como no protótipo) e o último item ainda fica visível.
 */
@Composable
fun mainBottomNavReserve(): Dp =
    BarHeight + BarBottomGap + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

enum class MainTab {
    Library,
    MyStuff,
    Channels,
    Settings
}

/** Tab bar do protótipo: pílula flutuante de 60 dp, fundo #151A22EE, borda sutil, sombra, padding 6. */
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = BarSideGap, end = BarSideGap, bottom = BarBottomGap)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                // Sombra do protótipo: #00000066, y 6, blur 20.
                .shadow(14.dp, BrandShapes.Pill, ambientColor = Color(0x66000000), spotColor = Color(0x66000000))
                .clip(BrandShapes.Pill)
                .background(Color(0xEE151A22))
                .border(1.dp, BrandColors.BorderSubtle, BrandShapes.Pill)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomNavItem(NbrIcons.Clapperboard, "Biblioteca", selected == MainTab.Library, Modifier.weight(1f), onLibrary)
            if (onSearch != null) {
                MainBottomNavItem(NbrIcons.Search, "Busca", false, Modifier.weight(1f), onSearch)
            }
            if (onMyStuff != null) {
                MainBottomNavItem(NbrIcons.Heart, "Meu", selected == MainTab.MyStuff, Modifier.weight(1f), onMyStuff)
            }
            MainBottomNavItem(NbrIcons.Library, "Canais", selected == MainTab.Channels, Modifier.weight(1f), onChannels)
            MainBottomNavItem(NbrIcons.Settings, "Config", selected == MainTab.Settings, Modifier.weight(1f), onSettings)
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
    val content = if (selected) BrandColors.Accent else BrandColors.TextMuted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(BrandShapes.Pill)
            .background(if (selected) Color(0x2266D9FF) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = if (selected) NbrType.Tab.copy(fontWeight = FontWeight.SemiBold) else NbrType.Tab, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
