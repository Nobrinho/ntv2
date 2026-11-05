package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo

/** Cor do círculo dos estados vazios/erro do protótipo (11, 52, 53, 54). */
internal enum class StatusTone { Neutral, Warning, Error, Search }

/**
 * Círculo de 96 (raio 48) com ícone de 40 dos estados vazios/erro: neutro = surface-alt + ícone accent;
 * aviso/erro/busca = cor@12% + contorno cor@33% + ícone na cor.
 */
@Composable
internal fun StatusIconCircle(
    icon: ImageVector,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    iconSize: Dp = 40.dp
) {
    val color = when (tone) {
        StatusTone.Neutral, StatusTone.Search -> BrandColors.Accent
        StatusTone.Warning -> BrandColors.Warning
        StatusTone.Error -> BrandColors.Error
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (tone == StatusTone.Neutral) Modifier.background(BrandColors.SurfaceAlt)
                else Modifier.background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.33f), CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

/**
 * Estado sem grade (vazio ou erro) no padrão do protótipo: círculo com ícone, título 22/700, motivos
 * 14/400 text-2 e ações empilhadas, centralizados. O foco já nasce na ação principal para o D-pad
 * não ficar sem alvo.
 */
@Composable
internal fun LibraryStatusPanel(
    title: String,
    lines: List<String>,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryFocus: FocusRequester = remember { FocusRequester() },
    // ← sai do painel de volta ao rail (a busca automática de foco não alcançava o rail de forma confiável).
    onLeft: (() -> Unit)? = null,
    icon: ImageVector = NbrIcons.Library,
    tone: StatusTone = StatusTone.Neutral
) {
    val tv = rememberAdaptiveLayoutInfo().useTvLayout
    LaunchedEffect(Unit) { runCatching { primaryFocus.requestFocus() } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (onLeft != null && e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { onLeft(); true } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (tv) 440.dp else 342.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusIconCircle(icon = icon, tone = tone)
            Text(title, color = TEXT_PRIMARY, style = NbrType.PageTitle, textAlign = TextAlign.Center)
            if (lines.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    lines.forEach {
                        Text(
                            it,
                            color = BrandColors.TextSecondary,
                            style = NbrType.Body.copy(lineHeight = 20.sp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrandButton(onClick = onPrimary, modifier = Modifier.fillMaxWidth().focusRequester(primaryFocus)) {
                    BrandButtonLabel(primaryLabel)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    BrandButton(onClick = onSecondary, style = BrandButtonStyle.Secondary, modifier = Modifier.fillMaxWidth()) {
                        BrandButtonLabel(secondaryLabel)
                    }
                }
            }
        }
    }
}
