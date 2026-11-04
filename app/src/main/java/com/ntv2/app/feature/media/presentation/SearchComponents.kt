package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons

// Peças da busca do protótipo (12/13 Busca no celular, TV 08 Busca, 54 Busca sem resultado).

/** Título da TV: 32/800 (64/800 no protótipo de 1920). */
internal val TvSearchTitleStyle = NbrType.ScreenTitle.copy(fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold)

/**
 * Campo de busca do celular (12/13): pílula de 52, fundo surface, contorno 2 accent em foco
 * (sutil fora dele), lupa 20 accent, texto 15/500, placeholder 14/400 muted e "X" 18 para limpar.
 */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var focused by remember { mutableStateOf(false) }
    val shape = BrandShapes.Pill
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = NbrType.BodyLarge.copy(fontWeight = FontWeight.Medium, color = TEXT_PRIMARY),
        cursorBrush = SolidColor(BrandColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(BrandColors.Surface)
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) BrandColors.Accent else BrandColors.BorderSubtle,
                        shape
                    )
                    .padding(start = 18.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(NbrIcons.Search, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = NbrType.Body,
                            color = BrandColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(NbrIcons.X, contentDescription = "Limpar", tint = BrandColors.TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    )
}

/**
 * Chip de filtro da busca: pílula de 32 (26 na TV), fill + contorno sutil, 13/500 text-2.
 * Ativo: accent com texto on-accent e "X" quando [removable].
 */
@Composable
internal fun SearchChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    tv: Boolean = false,
    removable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = BrandShapes.Pill
    val fg = if (active) BrandColors.OnCta else BrandColors.TextSecondary
    Row(
        modifier = modifier
            .height(if (tv) 26.dp else 32.dp)
            .clip(shape)
            .background(if (active) BrandColors.Accent else BrandColors.Fill)
            .then(if (active) Modifier else Modifier.border(1.dp, BrandColors.BorderSubtle, shape))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (tv) 12.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = if (tv) NbrType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            else NbrType.Secondary.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
        if (removable) {
            Icon(NbrIcons.X, contentDescription = "Remover filtro", tint = fg, modifier = Modifier.size(if (tv) 12.dp else 14.dp))
        }
    }
}

/** Chip selecionável dos painéis de filtro (celular e TV); na TV ganha anel de foco. */
@Composable
internal fun SearchFilterOptionChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    tv: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = BrandShapes.Pill
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { val s = if (focused) 1.05f else 1f; scaleX = s; scaleY = s }
            .height(if (tv) 32.dp else 36.dp)
            .clip(shape)
            .background(if (selected) BrandColors.Accent else BrandColors.Fill)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused && selected -> Color.White
                    focused -> BrandColors.Accent
                    selected -> BrandColors.Accent
                    else -> BrandColors.BorderSubtle
                },
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = NbrType.Chip,
            color = if (selected) BrandColors.OnCta else if (focused) TEXT_PRIMARY else BrandColors.TextSecondary,
            maxLines = 1
        )
    }
}

/**
 * Resultado da busca do celular (13): cartão surface com contorno sutil, raio 14, padding 10;
 * capa 56×80 raio 8; título 15/600 + meta 12/400 muted; selo de qualidade à direita.
 */
@Composable
internal fun SearchResultRow(
    title: String,
    meta: String,
    badge: String?,
    cover: String?,
    cardLoadingStyle: com.ntv2.app.core.ui.CardLoadingStyle,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BrandColors.SurfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                com.ntv2.app.core.ui.CoverWithLoading(
                    cover = cover,
                    contentDescription = title,
                    style = cardLoadingStyle,
                    animationsEnabled = animationsEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(NbrIcons.Clapperboard, contentDescription = null, tint = BrandColors.TextMuted, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = NbrType.ItemTitle, color = TEXT_PRIMARY, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) {
                Text(meta, style = NbrType.Caption, color = BrandColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (badge != null) QualityTag(badge)
    }
}

/** Selo de qualidade (13): padding 2/8, raio 6, fill, 11/700 text-2. */
@Composable
internal fun QualityTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NbrType.Overline.copy(letterSpacing = 0.sp),
        color = BrandColors.TextSecondary,
        maxLines = 1,
        modifier = modifier
            .clip(BrandShapes.Xs)
            .background(BrandColors.Fill)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Linha "Pesquisando…" com o anel accent. */
@Composable
internal fun SearchingIndicator(modifier: Modifier = Modifier, tv: Boolean = false) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(color = BrandColors.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(if (tv) 20.dp else 24.dp))
        Text("Pesquisando…", style = if (tv) NbrType.BodyStrong else NbrType.ItemTitle, color = TEXT_PRIMARY)
    }
}

/** Rodapé da lista: anel enquanto carrega mais; "Fim da lista" no fim. */
@Composable
internal fun SearchListFooter(loadingMore: Boolean, hasMore: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        if (loadingMore) {
            CircularProgressIndicator(color = BrandColors.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        } else if (!hasMore) {
            Text("Fim da lista", style = NbrType.Caption, color = BrandColors.TextMuted)
        }
    }
}

/**
 * Busca sem resultado (54): círculo de busca (accent@12% + contorno accent@33%) com `SearchX`,
 * título 22/700 com o termo e dica 14/400 text-2. Sem o "Pedir este título" (função nova).
 */
@Composable
internal fun SearchEmptyPanel(query: String, modifier: Modifier = Modifier, tv: Boolean = false) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (tv) 16.dp else 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 16.dp)
    ) {
        StatusIconCircle(
            icon = NbrIcons.SearchX,
            tone = StatusTone.Search,
            size = if (tv) 64.dp else 96.dp,
            iconSize = if (tv) 28.dp else 40.dp
        )
        Text(
            if (query.isNotBlank()) "Nada para “${query.trim()}”" else "Nenhum resultado encontrado",
            style = if (tv) NbrType.Section else NbrType.PageTitle,
            color = TEXT_PRIMARY,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 342.dp)
        )
        Text(
            if (query.isNotBlank()) "Confira a grafia ou tente menos palavras." else "Tente tirar algum filtro.",
            style = if (tv) NbrType.Secondary else NbrType.Body.copy(lineHeight = 20.sp),
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 342.dp)
        )
    }
}

/**
 * Pôster da grade de resultados da TV (TV 08): capa 2:3 raio 10, anel accent de 2 + brilho no foco,
 * título 11/600 embaixo (até 2 linhas). Sem capa, mostra o título dentro do quadro.
 */
@Composable
internal fun TvSearchPoster(
    cover: String?,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer { val s = if (focused) com.ntv2.app.core.ui.BRAND_FOCUS_SCALE else 1f; scaleX = s; scaleY = s }
                .then(
                    if (focused && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        Modifier.shadow(12.dp, shape, ambientColor = BrandColors.Accent, spotColor = BrandColors.Accent)
                    } else Modifier
                )
                .clip(shape)
                .background(BrandColors.SurfaceAlt)
                .border(if (focused) 2.dp else 1.dp, if (focused) BrandColors.Accent else BrandColors.BorderSubtle, shape),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                com.ntv2.app.core.ui.CoverWithLoading(
                    cover = cover,
                    contentDescription = title,
                    style = com.ntv2.app.core.ui.CardLoadingStyle.DEFAULT,
                    animationsEnabled = true,
                    modifier = Modifier.fillMaxSize()
                )
                // Anel por cima da capa (a imagem cobriria a borda do fundo).
                Box(
                    Modifier.matchParentSize().border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) BrandColors.Accent else BrandColors.BorderSubtle,
                        shape
                    )
                )
            } else {
                Text(
                    title,
                    color = BrandColors.TextSecondary,
                    style = NbrType.Caption,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        Text(
            title,
            style = NbrType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (focused) TEXT_PRIMARY else BrandColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Tecla do teclado da TV (TV 08): 38 de altura, raio 8, fill + contorno sutil, 14/600 text;
 * foco = anel accent de 2 + brilho. [primary] = accent/on-accent (ação "Buscar").
 */
@Composable
internal fun TvKey(
    label: String,
    modifier: Modifier,
    primary: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    Modifier.shadow(10.dp, shape, ambientColor = BrandColors.Accent, spotColor = BrandColors.Accent)
                } else Modifier
            )
            .clip(shape)
            .background(if (primary) BrandColors.Accent else if (focused) BrandColors.SurfaceAlt else BrandColors.Fill)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused && primary -> Color.White
                    focused -> BrandColors.Accent
                    primary -> BrandColors.Accent
                    else -> BrandColors.BorderSubtle
                },
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (small) NbrType.Caption.copy(fontWeight = FontWeight.Bold) else NbrType.BodyStrong,
            color = if (primary) BrandColors.OnCta else TEXT_PRIMARY,
            maxLines = 1
        )
    }
}

/** Campo da TV (TV 08): 38 de altura, raio 19, surface, contorno border, lupa 15 accent, texto 14/500. */
@Composable
internal fun TvSearchField(text: String, placeholder: Boolean, modifier: Modifier = Modifier) {
    val shape = BrandShapes.Pill
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.Border, shape)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NbrIcons.Search, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(15.dp))
        Text(
            text,
            style = if (placeholder) NbrType.Secondary else NbrType.BodyStrong.copy(fontWeight = FontWeight.Medium),
            color = if (placeholder) BrandColors.TextMuted else TEXT_PRIMARY,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
