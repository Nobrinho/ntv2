package com.ntv2.app.feature.profiles.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.profiles.Profile
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons

internal val ProfileText = com.ntv2.app.core.ui.BrandColors.TextPrimary

/** Cores do avatar (protótipo 62): pares do degradê 135°. */
internal val ProfilePalette = listOf(
    com.ntv2.app.core.ui.BrandColors.Accent to com.ntv2.app.core.ui.BrandColors.Cyan,
    com.ntv2.app.core.ui.BrandColors.Rose to com.ntv2.app.core.ui.BrandColors.Warning,
    com.ntv2.app.core.ui.BrandColors.Violet to com.ntv2.app.core.ui.BrandColors.Accent,
    com.ntv2.app.core.ui.BrandColors.Success to com.ntv2.app.core.ui.BrandColors.Cyan,
    com.ntv2.app.core.ui.BrandColors.Warning to com.ntv2.app.core.ui.BrandColors.Error,
    com.ntv2.app.core.ui.BrandColors.TextPrimary to com.ntv2.app.core.ui.BrandColors.TextMuted
)

/** Símbolos do avatar (protótipo 62). "type" = inicial do nome. */
internal val ProfileSymbols: List<Pair<String, ImageVector?>> = listOf(
    "type" to null,
    "clapperboard" to NbrIcons.Clapperboard,
    "tv" to NbrIcons.Tv,
    "sparkles" to NbrIcons.Sparkles,
    "crown" to NbrIcons.Crown,
    "heart" to NbrIcons.Heart,
    "star" to NbrIcons.Star,
    "sun" to NbrIcons.Sun,
    "gauge" to NbrIcons.Gauge,
    "users" to NbrIcons.Users,
    "layers" to NbrIcons.Layers,
    "flag" to NbrIcons.Flag
)

internal fun profileBrush(color: Int): Brush {
    val (a, b) = ProfilePalette[color.coerceIn(0, ProfilePalette.lastIndex)]
    return Brush.linearGradient(listOf(a, b))
}

/** Avatar: quadrado arredondado (raio 28% do lado) com degradê e inicial ou símbolo escuro. */
@Composable
internal fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val symbol = ProfileSymbols.firstOrNull { it.first == profile.symbol }?.second
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(profileBrush(profile.color)),
        contentAlignment = Alignment.Center
    ) {
        if (symbol != null) {
            Icon(symbol, contentDescription = null, tint = com.ntv2.app.core.ui.BrandColors.OnCta, modifier = Modifier.size(size * 0.46f))
        } else {
            Text(
                profile.initial,
                color = com.ntv2.app.core.ui.BrandColors.OnCta,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.ExtraBold,
                style = NbrType.Body
            )
        }
    }
}

/** Botão em pílula do protótipo (52 dp). */
@Composable
internal fun ProfileButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null
) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        destructive && primary -> BrandColors.Error
        destructive -> BrandColors.Error.copy(alpha = 0.12f)
        primary -> BrandColors.Accent
        else -> BrandColors.Fill
    }
    val content = when {
        destructive && primary -> Color.White
        destructive -> BrandColors.Error
        primary -> BrandColors.OnCta
        else -> ProfileText
    }
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.scale(1.03f) else Modifier)
            .clip(BrandShapes.Pill)
            .clickable(onClick = onClick)
            .background(fill)
            .border(
                if (focused) 2.dp else if (primary && !destructive) 0.dp else 1.dp,
                if (focused) (if (primary) Color.White else BrandColors.Accent)
                else if (destructive) BrandColors.Error.copy(alpha = 0.4f) else BrandColors.BorderSubtle,
                BrandShapes.Pill
            )
            .height(52.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(label, style = NbrType.Button, color = content)
    }
}

/** Topo das subtelas: voltar + título 22 bold + ação opcional em accent à direita. */
@Composable
internal fun ProfileTopBar(title: String, onBack: () -> Unit, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileIconButton(NbrIcons.ChevronLeft, "Voltar", onBack)
        Text(title, style = NbrType.PageTitle, color = ProfileText, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            var focused by remember { mutableStateOf(false) }
            Text(
                action,
                style = NbrType.Button,
                color = BrandColors.Accent,
                modifier = Modifier
                    .clip(BrandShapes.Pill)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onAction)
                    .background(if (focused) BrandColors.Accent.copy(alpha = 0.15f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
internal fun ProfileIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) BrandColors.Accent else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = ProfileText, modifier = Modifier.size(24.dp))
    }
}

/** Rótulo de grupo em maiúsculas (11 bold, espaçado, muted). */
@Composable
internal fun ProfileSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = NbrType.Overline, color = BrandColors.TextMuted, modifier = modifier)
}

/** Grupo em cartão surface raio 16 com divisórias. */
@Composable
internal fun ProfileGroup(items: List<@Composable () -> Unit>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BrandShapes.Lg)
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.BorderSubtle, BrandShapes.Lg)
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(BrandColors.BorderSubtle))
            item()
        }
    }
}

/** Item de grupo: ícone em quadrado 34 dp, título 14 semibold, subtítulo 11, valor/chave à direita. */
@Composable
internal fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(BrandShapes.Sm).background(BrandColors.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NbrType.BodyStrong, color = ProfileText)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = NbrType.Caption.copy(fontSize = 11.sp), color = BrandColors.TextMuted)
            }
        }
        trailing()
    }
}

@Composable
internal fun ProfileValue(text: String, muted: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = NbrType.Chip, color = if (muted) BrandColors.TextMuted else BrandColors.Accent)
        Icon(NbrIcons.ChevronRight, contentDescription = null, tint = BrandColors.TextMuted, modifier = Modifier.size(16.dp))
    }
}

/** Chave 46×28 do protótipo. */
@Composable
internal fun ProfileSwitch(checked: Boolean) {
    Box(
        Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(BrandShapes.Pill)
            .background(if (checked) BrandColors.Accent else Color.White.copy(alpha = 0.15f))
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (checked) com.ntv2.app.core.ui.BrandColors.OnCta else ProfileText))
    }
}

/** Selo "Infantil" violeta. */
@Composable
internal fun KidsBadge() {
    Text(
        "Infantil",
        style = NbrType.Overline.copy(letterSpacing = 0.sp),
        color = BrandColors.Violet,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BrandColors.Violet.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
