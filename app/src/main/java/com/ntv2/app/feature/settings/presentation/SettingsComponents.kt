package com.ntv2.app.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.trapFocus

/*
 * Peças das Configurações do protótipo (telas 28-40 do celular e TV 13-18).
 * Celular: grupos em cartão (surface, raio 16, borda sutil, divisórias); TV: cartões individuais.
 */

/** Branco do texto do protótipo (#F5F5F7). */
internal val SettingsText = com.ntv2.app.core.ui.BrandColors.TextPrimary

/** Rótulo de grupo (CONTA E APP): 11/700, espaçamento 1, maiúsculas, muted. */
@Composable
internal fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = NbrType.Overline, color = BrandColors.TextMuted, modifier = modifier)
}

/**
 * Grupo de itens. Celular: um cartão com divisória de 1 dp no topo de cada item a partir do 2º.
 * TV: cartões soltos com 7 dp entre eles.
 */
@Composable
internal fun SettingsGroup(
    tv: Boolean,
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    borderColor: Color = BrandColors.BorderSubtle
) {
    if (items.isEmpty()) return
    if (tv) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items.forEach { it() }
        }
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .clip(BrandShapes.Lg)
                .background(BrandColors.Surface)
                .border(1.dp, borderColor, BrandShapes.Lg)
        ) {
            items.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(BrandColors.BorderSubtle))
                item()
            }
        }
    }
}

/** Tom do ícone do item: azul (padrão), violeta (Premium) ou vermelho (Sair). */
internal enum class SettingsTone(val color: Color) {
    Accent(BrandColors.Accent), Violet(BrandColors.Violet), Error(BrandColors.Error)
}

/**
 * Item de configuração (2.11 celular / 2.12 TV). [trailing] fica à direita (chevron, valor ou chave).
 * Na TV o anel de foco é o deslizante da tela ([glideTarget]); sem ele, contorno azul.
 */
@Composable
internal fun SettingsItem(
    tv: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    tone: SettingsTone = SettingsTone.Accent,
    titleColor: Color = SettingsText,
    focusable: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = { SettingsChevron(tv) }
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    if (tv) {
        val shape = RoundedCornerShape(10.dp)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .glideTarget(10.dp)
                .clip(shape)
                .onFocusChanged { focused = it.isFocused }
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .background(if (focused) BrandColors.SurfaceAlt else BrandColors.Surface)
                .border(
                    if (focused && !gliding) 2.dp else 1.dp,
                    if (focused && !gliding) BrandColors.Accent else BrandColors.BorderSubtle,
                    shape
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    style = NbrType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, color = BrandColors.TextMuted, fontSize = 10.sp, style = NbrType.Caption,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .background(if (focused) BrandColors.Fill else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(BrandShapes.Sm).background(tone.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = titleColor, style = NbrType.BodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, color = BrandColors.TextMuted, style = NbrType.Caption.copy(fontSize = 11.sp, lineHeight = 14.85.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
    }
}

@Composable
internal fun SettingsChevron(tv: Boolean) {
    if (tv) return
    Icon(NbrIcons.ChevronRight, contentDescription = null, tint = BrandColors.TextMuted, modifier = Modifier.size(16.dp))
}

/** Valor à direita: celular 13/600 + chevron 16 (gap 4); TV 12/700 sem chevron. */
@Composable
internal fun SettingsValue(tv: Boolean, text: String, color: Color = BrandColors.TextSecondary) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = color,
            fontSize = if (tv) 12.sp else 13.sp,
            fontWeight = if (tv) FontWeight.Bold else FontWeight.SemiBold,
            style = NbrType.Chip,
            maxLines = 1
        )
        SettingsChevron(tv)
    }
}

/** Chave do protótipo: celular 44×26 (bolinha 20), TV 36×20 (bolinha 16). */
@Composable
internal fun SettingsSwitch(tv: Boolean, checked: Boolean) {
    val w = if (tv) 36.dp else 44.dp
    val h = if (tv) 20.dp else 26.dp
    val pad = if (tv) 2.dp else 3.dp
    val knob = if (tv) 16.dp else 20.dp
    val shape = RoundedCornerShape(h / 2)
    Box(
        Modifier
            .size(w, h)
            .clip(shape)
            .background(if (checked) BrandColors.Accent else BrandColors.Fill)
            .then(if (checked) Modifier else Modifier.border(1.dp, BrandColors.Border, shape))
            .padding(pad),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(knob).clip(CircleShape).background(if (checked) Color.White else BrandColors.TextMuted))
    }
}

/** Botão "Interface e sobre" (Mais ajustes): 44 de altura, pílula, fill + borda sutil, chevron que gira. */
@Composable
internal fun SettingsMoreButton(label: String, expanded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(BrandShapes.Pill)
            .clickable(onClick = onClick)
            .background(BrandColors.Fill)
            .border(1.dp, BrandColors.BorderSubtle, BrandShapes.Pill),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NbrType.Chip, color = BrandColors.TextSecondary)
        Icon(
            NbrIcons.ChevronDown, contentDescription = null, tint = BrandColors.TextSecondary,
            modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f)
        )
    }
}

/** Cabeçalho de subtela do celular: chevron-left 22 + título 22/700, gap 10. */
@Composable
internal fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            NbrIcons.ChevronLeft, contentDescription = "Voltar", tint = SettingsText,
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBack).padding(5.dp)
        )
        Text(title, style = NbrType.PageTitle, color = SettingsText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Subtela do celular em tela cheia (37 Teste de conexão, 29 Conectar aparelhos, 31 Premium...).
 * [content] rola; [actions] (botões) ficam presos embaixo, acima da barra de navegação.
 */
@Composable
internal fun SettingsPhonePage(
    title: String?,
    onBack: () -> Unit,
    background: Brush = Brush.verticalGradient(listOf(BrandColors.Background, BrandColors.Background)),
    horizontalPadding: Dp = 20.dp,
    gap: Dp = 16.dp,
    centerContent: Boolean = false,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BrandColors.Background)
            .background(background)
            .clickable(enabled = false) {}
            .trapFocus()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding)
    ) {
        if (title != null) {
            Box(Modifier.padding(top = 4.dp, bottom = gap)) { SettingsPageHeader(title, onBack) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = if (centerContent) Arrangement.spacedBy(gap, Alignment.CenterVertically)
            else Arrangement.spacedBy(gap),
            horizontalAlignment = if (centerContent) Alignment.CenterHorizontally else Alignment.Start,
            content = content
        )
        if (actions != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = actions
            )
        }
    }
}

/** Cartão do protótipo: surface, raio 16 (ou [radius]), borda sutil (ou [borderColor]), padding 14. */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    background: Color = BrandColors.Surface,
    borderColor: Color = BrandColors.BorderSubtle,
    padding: Dp = 14.dp,
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}

/** Faixa de aviso (30 "Aparelhos conectados", Premium ativo): tom@10%, borda no tom, raio 14, padding 14. */
@Composable
internal fun SettingsNotice(icon: ImageVector, color: Color, text: String, modifier: Modifier = Modifier, tv: Boolean = false) {
    val shape = RoundedCornerShape(if (tv) 10.dp else 14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color, shape)
            .padding(if (tv) 10.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (tv) 16.dp else 20.dp))
        Text(text, color = BrandColors.TextSecondary, style = if (tv) NbrType.Caption else NbrType.Secondary)
    }
}

/** Ponto + texto de espera ("Aguardando o pagamento · expira em 9:41"), em warning. */
@Composable
internal fun SettingsWaitingLine(text: String, tv: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (tv) 5.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (tv) 6.dp else 8.dp).clip(CircleShape).background(BrandColors.Warning))
        Text(text, color = BrandColors.Warning, style = NbrType.Caption, fontSize = if (tv) 10.sp else 12.sp)
    }
}

/** Painel central da TV (TV 17): surface, raio 18, borda sutil, padding 28, gap 12. */
@Composable
internal fun SettingsTvDialog(
    modifier: Modifier = Modifier,
    width: Dp = 480.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = false) {}
            .trapFocus(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier
                .width(width)
                .clip(shape)
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.BorderSubtle, shape)
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
