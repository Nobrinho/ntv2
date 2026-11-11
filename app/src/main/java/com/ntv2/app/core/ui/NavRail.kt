package com.ntv2.app.core.ui


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.R
import com.ntv2.app.core.ui.icons.NbrIcons

private val BRAND = com.ntv2.app.core.ui.BrandColors.Accent

/** Largura do menu lateral do protótipo (TV 07: 128 px em 1920 → 64 dp). */
private val RailWidth = 64.dp
/** Bloco do item (80 px → 40 dp, raio 24 px → 12 dp) e ícone (36 px → 18 dp). */
private val RailTile = 40.dp
private val RailTileShape = RoundedCornerShape(12.dp)

/** Coluna base do rail (logo NBR Play no topo + slot de itens), compartilhada entre telas. */
@Composable
fun RailColumn(
    // Ao entrar no rail pelo D-pad (← da grade), o foco vai para este item em vez do mais próximo.
    enterFocus: (() -> FocusRequester?)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(RailWidth)
                // Menu lateral do protótipo: fundo Surface com divisória sutil à direita.
                .background(com.ntv2.app.core.ui.BrandColors.Surface)
                .then(if (enterFocus != null) Modifier.focusEnterTo(enterFocus) else Modifier)
                .focusGroup()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo fixo no topo; os itens ficam centralizados na altura que sobra.
            Image(
                painter = painterResource(R.drawable.ic_brand_logo_small),
                contentDescription = "NBR Play",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 36.dp, height = 28.dp)
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    // Rolável só como salvaguarda em telas muito baixas; nos tamanhos normais tudo cabe.
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(com.ntv2.app.core.ui.BrandColors.BorderSubtle)
        )
    }
}

/**
 * Item de rail do protótipo: bloco de 40 dp com o ícone, destaque azul translúcido no item ativo.
 * O protótipo não tem rótulo; o app mantém o rótulo pequeno sob o bloco para não perder a informação.
 *
 * [primary] = ação principal (call-to-action): quando habilitado fica com fundo azul sólido e
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
    val gliding = glideActive()
    val active = focused || highlighted
    val focusScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused && enabled && interactive) BRAND_FOCUS_SCALE else 1f,
        animationSpec = androidx.compose.animation.core.tween(FOCUS_ANIM_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "rail-focus-scale"
    )
    val cta = primary && enabled // botão de ação em destaque
    val onCta = com.ntv2.app.core.ui.BrandColors.OnCta // conteúdo escuro sobre o CTA
    val tint = when {
        !enabled -> com.ntv2.app.core.ui.BrandColors.BorderStrong
        cta -> onCta
        active -> BRAND
        else -> com.ntv2.app.core.ui.BrandColors.TextMuted
    }
    val background = when {
        cta -> com.ntv2.app.core.ui.BrandColors.Cta
        focused -> Color(0x3366D9FF)
        highlighted -> Color(0x2666D9FF)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(RailTile)
                .glideTarget(12.dp, scale = BRAND_FOCUS_SCALE)
                .onFocusChanged { focused = it.isFocused }
                .then(if (enabled && interactive) Modifier.clickable(onClick = onClick) else Modifier)
                .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
                .clip(RailTileShape)
                .background(background)
                // Foco sobre o CTA: borda azul para não "sumir" o realce de foco.
                .then(
                    if (cta && focused && !gliding) Modifier.border(2.dp, com.ntv2.app.core.ui.BrandColors.Accent, RailTileShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(
            label,
            color = when {
                !enabled -> com.ntv2.app.core.ui.BrandColors.BorderStrong
                active -> BRAND
                else -> com.ntv2.app.core.ui.BrandColors.TextMuted
            },
            style = NbrType.Tab,
            maxLines = 1
        )
    }
}

/** Rail de navegação (Início/Busca/Minha lista/Canais/Config, como no protótipo, + Limpar/Atualizar do app). */
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
    onSettings: () -> Unit,
    // "Minha lista" (com o Histórico em outra aba): só aparece quando a tela fornece a ação (Biblioteca).
    onMyList: (() -> Unit)? = null
) {
    RailColumn(enterFocus = firstItemFocus?.let { f -> { f } }) {
        RailButton(icon = NbrIcons.House, label = "Início", highlighted = homeActive, onClick = onHome)
        RailButton(
            icon = NbrIcons.Search,
            label = "Busca",
            highlighted = searchActive,
            modifier = if (firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier,
            onClick = onSearch
        )
        // Só aparece com filtro de texto ativo: limpa sem precisar entrar na busca.
        if (showClearFilter) {
            RailButton(NbrIcons.FunnelX, "Limpar", onClick = onClearFilter)
        }
        // Ordem do protótipo: Início, Busca, Minha lista, Canais, Config (Atualizar é do app).
        if (onMyList != null) {
            RailButton(NbrIcons.Heart, "Minha lista", onClick = onMyList)
        }
        RailButton(
            NbrIcons.Library,
            "Canais",
            modifier = if (channelsFocus != null) Modifier.focusRequester(channelsFocus) else Modifier,
            onClick = onChannels
        )
        RailButton(NbrIcons.RefreshCw, "Atualizar", onClick = onRefresh)
        RailButton(
            NbrIcons.Settings,
            "Config",
            highlighted = settingsActive,
            interactive = !settingsActive,
            modifier = if (settingsFocus != null) Modifier.focusRequester(settingsFocus) else Modifier,
            onClick = onSettings
        )
    }
}
