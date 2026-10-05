package com.ntv2.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.ntv2.app.R

/** Geometria do brand kit v2.1: raios baixos. Pílula só para LIVE, contadores e presença. */
object BrandShapes {
    val Xs = RoundedCornerShape(2.dp)
    val Sm = RoundedCornerShape(4.dp) // inputs, chips, navegação
    val Md = RoundedCornerShape(6.dp) // botões, cards de mídia
    val Lg = RoundedCornerShape(8.dp) // diálogos e superfícies grandes
}

/** Inter embarcada (kit v2.1). */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun TextStyle.inter(weight: FontWeight? = null) =
    copy(fontFamily = InterFamily, fontWeight = weight ?: fontWeight)

private fun brandTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.inter(FontWeight.Bold),
        displayMedium = b.displayMedium.inter(FontWeight.Bold),
        displaySmall = b.displaySmall.inter(FontWeight.Bold),
        headlineLarge = b.headlineLarge.inter(FontWeight.Bold),
        headlineMedium = b.headlineMedium.inter(FontWeight.Bold),
        headlineSmall = b.headlineSmall.inter(FontWeight.SemiBold),
        titleLarge = b.titleLarge.inter(FontWeight.SemiBold),
        titleMedium = b.titleMedium.inter(FontWeight.SemiBold),
        titleSmall = b.titleSmall.inter(FontWeight.SemiBold),
        bodyLarge = b.bodyLarge.inter(),
        bodyMedium = b.bodyMedium.inter(),
        bodySmall = b.bodySmall.inter(),
        labelLarge = b.labelLarge.inter(FontWeight.Medium),
        labelMedium = b.labelMedium.inter(FontWeight.Medium),
        labelSmall = b.labelSmall.inter(FontWeight.Medium)
    )
}

/** Tema do NBR Play: aplica o brand kit nos dois sistemas Material (TV e Material 3 padrão). */
@Composable
fun NbrTheme(content: @Composable () -> Unit) {
    val typography = remember { brandTypography() }
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = BrandColors.Accent,
            onPrimary = BrandColors.OnCta,
            primaryContainer = BrandColors.Accent,
            onPrimaryContainer = BrandColors.OnCta,
            secondary = BrandColors.Violet,
            tertiary = BrandColors.Cyan,
            background = BrandColors.Background,
            surface = BrandColors.Surface,
            surfaceVariant = BrandColors.SurfaceAlt,
            onSurface = Color(0xFFF7F9FC),
            onSurfaceVariant = BrandColors.TextSecondary,
            outline = Color(0xFF252C37),
            error = BrandColors.Error
        )
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = BrandColors.Accent,
                onPrimary = BrandColors.OnCta,
                primaryContainer = BrandColors.Accent,
                onPrimaryContainer = BrandColors.OnCta,
                secondary = BrandColors.Violet,
                tertiary = BrandColors.Cyan,
                background = BrandColors.Background,
                surface = BrandColors.Surface,
                surfaceVariant = BrandColors.SurfaceAlt,
                onSurfaceVariant = BrandColors.TextSecondary,
                error = BrandColors.Error,
                border = BrandColors.SurfaceAlt
            ),
            typography = typography,
            content = content
        )
    }
}

enum class BrandButtonStyle { Primary, Secondary, Destructive, Premium }

private val LocalBrandLabelStyle = staticCompositionLocalOf { TextStyle.Default }

/**
 * Botão do kit: raio 6dp; foco (D-pad) = escala + contorno + glow; toque = clique normal.
 * Primary = azul NBR com texto escuro; Secondary = ghost; Destructive = vermelho; Premium = violeta.
 * Dentro dele use [BrandButtonLabel] para o texto.
 */
@Composable
fun BrandButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BrandButtonStyle = BrandButtonStyle.Primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fill: Color
    val fg: Color
    val line: Color
    when (style) {
        BrandButtonStyle.Primary -> { fill = BrandColors.Accent; fg = BrandColors.OnCta; line = BrandColors.Accent }
        BrandButtonStyle.Secondary -> { fill = Color(0x14FFFFFF); fg = Color(0xFFF7F9FC); line = Color(0x33FFFFFF) }
        BrandButtonStyle.Destructive -> { fill = Color(0x1FFF626D); fg = BrandColors.Error; line = BrandColors.Error }
        BrandButtonStyle.Premium -> { fill = BrandColors.Violet; fg = BrandColors.OnCta; line = BrandColors.Violet }
    }
    val glow = if (style == BrandButtonStyle.Premium) BrandColors.Violet else BrandColors.Accent
    val a = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.scale(1.03f).shadow(14.dp, BrandShapes.Md, ambientColor = glow, spotColor = glow)
                else Modifier
            )
            .clip(BrandShapes.Md)
            .background(fill.copy(alpha = fill.alpha * a))
            .border(
                BorderStroke(
                    if (focused) 2.dp else 1.dp,
                    if (focused) glow else line.copy(alpha = line.alpha * a)
                ),
                BrandShapes.Md
            )
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(PaddingValues(horizontal = 18.dp, vertical = 10.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            LocalBrandLabelStyle provides MaterialTheme.typography.titleSmall.copy(color = fg.copy(alpha = a))
        ) { content() }
    }
}

/** Rótulo para dentro de [BrandButton]. */
@Composable
fun BrandButtonLabel(text: String) {
    Text(text, style = LocalBrandLabelStyle.current)
}

/** Campo de texto do kit: raio 4dp, fundo Surface, borda cinza → azul NBR no foco, vermelho em erro. */
@Composable
fun BrandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> BrandColors.Error
        focused -> BrandColors.Accent
        else -> Color(0x4DB6BFCC)
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(BrandShapes.Sm)
            .background(BrandColors.Surface)
            .border(if (focused) 2.dp else 1.dp, borderColor, BrandShapes.Sm),
        singleLine = true,
        textStyle = textStyle.copy(color = Color(0xFFF7F9FC)),
        cursorBrush = SolidColor(BrandColors.Accent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (prefix != null) Text(prefix, style = textStyle, color = BrandColors.TextSecondary)
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = Color(0xFF6B7585))
                    }
                    inner()
                }
            }
        }
    )
}
