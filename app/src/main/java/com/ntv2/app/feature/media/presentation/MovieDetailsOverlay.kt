package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ntv2.app.core.ui.trapFocus
import com.ntv2.app.core.ui.tmdbAtWidth
import com.ntv2.app.core.ui.imageTiming
import com.ntv2.app.core.ui.fadeInUpStaggered
import com.ntv2.app.core.ui.slideInFromRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.domain.MovieDetails
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding

// Tela de detalhes do filme (Continuar/Recomeçar/Voltar).

internal val BRAND_GREEN = Color(0xFF2BEE34)

// Tela de Detalhes (estilo Netflix/Prime): responsiva (TV/paisagem lado a lado, celular/retrato
// empilhado). O conteúdo aparece imediatamente; imagens secundárias carregam sem bloquear a tela.
@Composable
internal fun MovieDetailsOverlay(
    media: MediaCardUi,
    details: MovieDetails?,
    showCastPhotos: Boolean,
    lowRamPlaybackWarnings: Boolean,
    animationsEnabled: Boolean,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    playLoading: Boolean = false,
    playFailed: Boolean = false,
    onRestart: () -> Unit = {},
    isTv: Boolean = true
) {
    BackHandler(enabled = true) { onDismiss() }
    val playFocus = remember { FocusRequester() }

    // O fundo é o banner do filme (nunca o pôster): só cai para a capa do card se não houver banner.
    val backdrop = details?.backdropPath?.let { tmdbAtWidth(it, "w780") }
        ?: media.posterPath ?: media.thumbnailPath
    // Entrada nº 4 (slide da direita): a arte de fundo entra quando termina de carregar, em vez de
    // simplesmente aparecer de um quadro para o outro.
    var backdropLoaded by remember(backdrop) { mutableStateOf(false) }
    val backdropTiming = imageTiming("fundo", backdrop)
    // Registra o que a tela TEM no instante em que abre: se o endereço já existe aqui e a imagem só
    // aparece 30 s depois, o atraso está no carregamento; se vier "sem imagem", está nos metadados.
    LaunchedEffect(media.mediaId, backdrop, details) {
        android.util.Log.i(
            "NtvImg",
            "detalhes ${media.mediaId}: fundo=${backdrop ?: "sem imagem"} " +
                "elenco=${details?.cast?.count { it.photoUrl != null } ?: 0} fotos"
        )
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    // trapFocus: a grade continua composta por trás — o foco não pode escapar para ela.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF050505)).trapFocus()) {
        val portrait = maxHeight > maxWidth
        val compactLandscape = !portrait && maxHeight < 520.dp
        if (portrait) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    if (backdrop != null) {
                        AsyncImage(
                            model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                            onState = { backdropTiming(it); if (it.isDone()) backdropLoaded = true },
                            modifier = Modifier.fillMaxSize()
                                .slideInFromRight(backdropLoaded, animationsEnabled)
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color(0x00050505), 0.7f to Color(0x99050505), 1f to Color(0xFF050505))
                        )
                    )
                }
                DetailsInfo(
                    media, details, showCastPhotos, lowRamPlaybackWarnings, animationsEnabled,
                    playFocus, onPlay, onDismiss,
                    actionsFirst = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    playLoading = playLoading,
                    playFailed = playFailed,
                    onRestart = onRestart,
                    showBackButton = isTv,
                    fillActions = !isTv
                )
            }
        } else {
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                    onState = { backdropTiming(it); if (it.isDone()) backdropLoaded = true },
                    modifier = Modifier.fillMaxSize()
                        .slideInFromRight(backdropLoaded, animationsEnabled)
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF2050505), 0.45f to Color(0xB3050505), 0.8f to Color(0x00050505))
            ))
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x00050505), 0.55f to Color(0x66050505), 1f to Color(0xF2050505))
            ))
            DetailsInfo(
                media, details, showCastPhotos, lowRamPlaybackWarnings, animationsEnabled,
                playFocus, onPlay, onDismiss,
                actionsFirst = compactLandscape,
                modifier = Modifier.fillMaxWidth(0.62f).align(Alignment.CenterStart)
                    // Rolável sempre: ao focar os botões, a coluna rola até eles (nunca ficam cortados).
                    .verticalScroll(rememberScrollState())
                    .padding(start = 48.dp, end = 24.dp, top = 40.dp, bottom = 40.dp),
                playLoading = playLoading,
                playFailed = playFailed,
                onRestart = onRestart,
                showBackButton = isTv
            )
        }
        // Celular: fechar pelo X no canto superior esquerdo (mesmo padrão do player), em vez do
        // botão "Voltar" na linha de ações — que não cabia ao lado de Continuar e Recomeçar.
        if (!isTv) {
            DetailsCloseButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(12.dp)
            )
        }
    }
}

@Composable
internal fun DetailsCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x80000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

internal fun coil.compose.AsyncImagePainter.State.isDone(): Boolean =
    this is coil.compose.AsyncImagePainter.State.Success || this is coil.compose.AsyncImagePainter.State.Error

@Composable
internal fun DetailsInfo(
    media: MediaCardUi,
    details: MovieDetails?,
    showCastPhotos: Boolean,
    lowRamPlaybackWarnings: Boolean,
    animationsEnabled: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    actionsFirst: Boolean,
    modifier: Modifier = Modifier,
    playLoading: Boolean = false,
    playFailed: Boolean = false,
    onRestart: () -> Unit = {},
    showBackButton: Boolean = true,
    fillActions: Boolean = false
) {
    val title = details?.title ?: media.title
    val durationSecs = if ((details?.durationSeconds ?: 0) > 0) details!!.durationSeconds else media.durationSeconds
    val showPlaybackWarning = lowRamPlaybackWarnings && media.needsLowRamPlaybackWarning()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        details?.originalTitle?.takeIf { it.isNotBlank() && it != title }?.let {
            Text(it, color = Color(0xFFC9C9C9), style = MaterialTheme.typography.titleMedium)
        }
        val meta = buildList {
            details?.year?.let { add(it.toString()) }
            if (durationSecs > 0) add(durationLabel(durationSecs))
            details?.rating?.let { add("★ ${"%.1f".format(it)}") }
            details?.ageRating?.takeIf { it.isNotBlank() }?.let { add(it) }
            details?.quality?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (meta.isNotEmpty()) {
            Text(meta.joinToString("   •   "), color = Color(0xFFE6E6E6), style = MaterialTheme.typography.titleSmall)
        }
        if (showPlaybackWarning) {
            Text(
                text = "Este aparelho pode tocar apenas o som em vídeos 1080p. Prefira versão 720p quando disponível.",
                color = Color(0xFFFFD37A),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26FFC857))
                    .border(1.dp, Color(0x66FFC857), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
        if (actionsFirst) {
            DetailsActionRow(
                media, showPlaybackWarning, playFocus, onPlay, onDismiss, playLoading, playFailed, onRestart,
                showBackButton = showBackButton,
                fillWidth = fillActions
            )
        }
        details?.genres?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFFBDBDBD), style = MaterialTheme.typography.bodyMedium)
        }
        details?.synopsis?.takeIf { it.isNotBlank() }?.let { SynopsisText(it) }
        details?.director?.takeIf { it.isNotBlank() }?.let {
            Text("Diretor: $it", color = Color(0xFFB6B6B6), style = MaterialTheme.typography.bodySmall)
        }

        val cast = details?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            if (showCastPhotos) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Entrada nº 11 (stagger): os rostos sobem em sequência, um pouco depois do
                    // anterior, em vez de a fileira inteira piscar junto.
                    var castVisible by remember(cast) { mutableStateOf(false) }
                    LaunchedEffect(cast) { castVisible = true }
                    cast.take(8).forEachIndexed { index, c ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(76.dp)
                                .fadeInUpStaggered(castVisible, index, animationsEnabled)
                        ) {
                            if (c.photoUrl != null) {
                                val castTiming = imageTiming("elenco#$index", c.photoUrl)
                                AsyncImage(
                                    model = c.photoUrl, contentDescription = c.name, contentScale = ContentScale.Crop,
                                    onState = castTiming,
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colorForTitle(c.name))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colorForTitle(c.name)),
                                    contentAlignment = Alignment.Center
                                ) { Text(initialFor(c.name), color = Color.White, style = MaterialTheme.typography.titleMedium) }
                            }
                            Text(c.name, color = Color(0xFFDCDCDC), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                Text("Elenco: ${cast.joinToString(", ") { it.name }}", color = Color(0xFFB6B6B6), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!actionsFirst) {
            DetailsActionRow(
                media, showPlaybackWarning, playFocus, onPlay, onDismiss, playLoading, playFailed, onRestart,
                showBackButton = showBackButton,
                fillWidth = fillActions
            )
        }
    }
}

// Sinopse: 4 linhas; focável pelo D-pad e OK expande/recolhe o texto completo.
@Composable
internal fun SynopsisText(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { expanded = !expanded }
            .background(if (focused) Color(0x1FFFFFFF) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text,
            color = Color(0xFFDCDCDC),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (focused) {
            Text(
                if (expanded) "OK para recolher" else "OK para ler tudo",
                color = Color(0xFF9A9A9A),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun DetailsActionRow(
    media: MediaCardUi,
    showPlaybackWarning: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    playLoading: Boolean = false,
    playFailed: Boolean = false,
    onRestart: () -> Unit = {},
    showBackButton: Boolean = true,
    // Celular em pé: os botões dividem a largura toda (nunca cortam na lateral).
    fillWidth: Boolean = false
) {
    Row(
        modifier = Modifier.focusGroup().then(if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val share = if (fillWidth) Modifier.weight(1f) else Modifier
        DetailButton(
            icon = Icons.Filled.PlayArrow,
            label = when {
                playLoading -> "Abrindo…"
                playFailed -> "Falhou — tentar de novo"
                showPlaybackWarning -> "Tentar assistir"
                media.progress > 0f -> "Continuar"
                else -> "Assistir"
            },
            primary = true,
            modifier = share.focusRequester(playFocus),
            loading = playLoading,
            onClick = onPlay
        )
        // Vídeo parado no meio: "Recomeçar" zera o progresso e toca do início (útil também quando
        // o download a partir do ponto salvo não anda).
        if (media.progress > 0f && !playLoading) {
            DetailButton(icon = Icons.Filled.Replay, label = "Recomeçar", primary = false, onClick = onRestart, modifier = share)
        }
        if (showBackButton) {
            DetailButton(icon = Icons.Filled.Close, label = "Voltar", primary = false, onClick = onDismiss, modifier = share)
        }
    }
}

@Composable
internal fun DetailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        primary -> BRAND_GREEN
        focused -> Color(0x33FFFFFF)
        else -> Color(0x1FFFFFFF)
    }
    val content = if (primary) Color(0xFF0B0B0B) else Color.White
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            // Enquanto carrega, ignora novos toques (evita disparos duplicados).
            .clickable(enabled = !loading, onClick = onClick)
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(label, color = content, style = MaterialTheme.typography.titleMedium)
    }
}
