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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.ntv2.app.R

/** Geometria do protótipo (nbrplay.pen) e do brand kit (tokens radius 8/12/20/pill). Ver docs/PROTOTIPO-ESPEC.md. */
object BrandShapes {
    val Xs = RoundedCornerShape(6.dp)   // selos (FHD, L, 12), badges
    val Sm = RoundedCornerShape(10.dp)  // miniaturas, ícone de item, botões pequenos
    val Md = RoundedCornerShape(12.dp)  // cards de pôster, capas
    val Field = RoundedCornerShape(14.dp) // campos de texto, opções de sheet
    val Lg = RoundedCornerShape(16.dp)  // grupos de configuração, cards de lista
    val Xl = RoundedCornerShape(24.dp)  // diálogos
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Pill = RoundedCornerShape(percent = 50) // botões, chips, tab bar, busca
}

/**
 * Escala de texto do protótipo, em sp. Os números valem para celular e TV: a TV do protótipo foi
 * desenhada em 1920×1080 px e o Fire TV tem 960×540 dp, então os valores de lá divididos por 2 caem
 * nesta mesma escala. Altura de linha = a natural da Inter (1,21) e sem padding da fonte, como no pen.dev.
 */
object NbrType {
    private fun s(size: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = InterFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.21f).sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val Hero = s(30, FontWeight.Bold)            // título sobre a capa (detalhe de filme/série, coleção)
    val ScreenTitle = s(28, FontWeight.Bold)     // título das telas das abas (Biblioteca, Meu, Configurações)
    val PageTitle = s(22, FontWeight.Bold)       // título de subtela, ao lado do voltar
    val SheetTitle = s(19, FontWeight.Bold)      // título de sheet e diálogo
    val Section = s(17, FontWeight.Bold)         // cabeçalho de fileira/seção
    val ItemTitle = s(15, FontWeight.SemiBold)   // título de item e de opção
    val Button = s(15, FontWeight.Bold)          // rótulo de botão
    val BodyLarge = s(15, FontWeight.Normal)
    val Body = s(14, FontWeight.Normal)
    val BodyStrong = s(14, FontWeight.SemiBold)
    val Secondary = s(13, FontWeight.Normal)
    val Chip = s(13, FontWeight.SemiBold)
    val Caption = s(12, FontWeight.Normal)
    val Overline = s(11, FontWeight.Bold, 1f)    // rótulos em maiúsculas (CONTA E APP)
    val Tab = s(10, FontWeight.Medium)           // rótulo da tab bar
    val Paragraph = Body.copy(lineHeight = 20.sp) // sinopse e textos longos (protótipo usa 1,4)
}

/** Inter embarcada (kit v2.1). */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Papéis do Material apontando para a escala do protótipo (vale para o Material da TV e o Material 3). */
private object BrandRoles {
    private val base = NbrType.Body
    val displayLarge = base.copy(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
    val displayMedium = base.copy(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold)
    val displaySmall = NbrType.Hero
    val headlineLarge = NbrType.ScreenTitle
    val headlineMedium = NbrType.PageTitle
    val headlineSmall = NbrType.SheetTitle
    val titleLarge = NbrType.Section
    val titleMedium = NbrType.ItemTitle
    val titleSmall = NbrType.BodyStrong
    val bodyLarge = NbrType.BodyLarge
    val bodyMedium = NbrType.Body
    val bodySmall = NbrType.Caption
    val labelLarge = NbrType.Chip
    val labelMedium = base.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
    val labelSmall = base.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
}

private fun brandTypography() = with(BrandRoles) {
    Typography(
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall
    )
}

private fun brandM3Typography() = with(BrandRoles) {
    androidx.compose.material3.Typography(
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall
    )
}

/** Tema do NBR Play: aplica o brand kit nos dois sistemas Material (TV e Material 3 padrão). */
@Composable
fun NbrTheme(content: @Composable () -> Unit) {
    val typography = remember { brandTypography() }
    val m3Typography = remember { brandM3Typography() }
    androidx.compose.material3.MaterialTheme(
        typography = m3Typography,
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
 * Botão do protótipo: pílula, 52 dp de altura no celular (40 dp na TV), rótulo 15 bold.
 * Foco (D-pad) = escala + contorno + glow; toque = clique normal.
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
        BrandButtonStyle.Secondary -> { fill = com.ntv2.app.core.ui.BrandColors.Fill; fg = Color(0xFFF7F9FC); line = com.ntv2.app.core.ui.BrandColors.Border }
        BrandButtonStyle.Destructive -> { fill = Color(0x1FFF626D); fg = BrandColors.Error; line = BrandColors.Error }
        BrandButtonStyle.Premium -> { fill = BrandColors.Violet; fg = BrandColors.OnCta; line = BrandColors.Violet }
    }
    val glow = if (style == BrandButtonStyle.Premium) BrandColors.Violet else BrandColors.Accent
    val a = if (enabled) 1f else 0.4f
    val tv = rememberAdaptiveLayoutInfo().useTvLayout
    val shape = BrandShapes.Pill
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    Modifier.scale(1.03f).shadow(14.dp, shape, ambientColor = glow, spotColor = glow)
                } else if (focused) Modifier.scale(1.03f)
                else Modifier
            )
            .clip(shape)
            .background(fill.copy(alpha = fill.alpha * a))
            .border(
                BorderStroke(
                    if (focused) 2.dp else 1.dp,
                    if (focused) glow else line.copy(alpha = line.alpha * a)
                ),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = if (tv) 40.dp else 52.dp)
            .padding(PaddingValues(horizontal = 22.dp, vertical = 8.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            LocalBrandLabelStyle provides NbrType.Button.copy(color = fg.copy(alpha = a))
        ) { content() }
    }
}

/** Rótulo para dentro de [BrandButton]. */
@Composable
fun BrandButtonLabel(text: String) {
    Text(text, style = LocalBrandLabelStyle.current)
}

/** Campo de texto do protótipo: raio 14dp, 50 dp, fundo Surface, borda sutil → azul NBR (1,5 dp) no foco, vermelho em erro. */
@Composable
fun BrandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    isError: Boolean = false,
    textStyle: TextStyle = NbrType.BodyLarge,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> BrandColors.Error
        focused -> BrandColors.Accent
        else -> BrandColors.BorderSubtle
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(BrandShapes.Field)
            .background(BrandColors.Surface)
            .border(if (focused || isError) 1.5.dp else 1.dp, borderColor, BrandShapes.Field),
        singleLine = true,
        textStyle = textStyle.copy(color = Color(0xFFF7F9FC)),
        cursorBrush = SolidColor(BrandColors.Accent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                modifier = Modifier.heightIn(min = 50.dp).padding(horizontal = 14.dp),
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
