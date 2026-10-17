package com.ntv2.app.feature.onboarding.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ntv2.app.R
import com.ntv2.app.core.ui.AdaptiveLayoutInfo
import com.ntv2.app.core.ui.BrandBackdropKind
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.LocalAnimationsEnabled
import com.ntv2.app.core.ui.brandBackdrop
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val body: String,
    val accent: Color,
    /** null = página de boas-vindas (mostra o logo no hero). */
    val icon: ImageVector?
)

private fun onboardingPages(isTv: Boolean) = listOf(
    OnboardingPage(
        title = "Bem-vindo ao NBR Play",
        body = "Seus filmes e séries em um só lugar, no celular e na TV.",
        accent = BrandColors.Accent,
        icon = null
    ),
    OnboardingPage(
        title = "Seus canais viram catálogo",
        body = "Escolha seus canais do Telegram e o NBR Play organiza os vídeos com capas, sinopse e elenco.",
        accent = BrandColors.Violet,
        icon = Icons.Filled.VideoLibrary
    ),
    OnboardingPage(
        title = "Continue de onde parou",
        body = "Progresso, favoritos e histórico salvos para você voltar ao que estava assistindo.",
        accent = BrandColors.Rose,
        icon = Icons.Filled.History
    ),
    OnboardingPage(
        title = "Entre com o seu Telegram",
        body = if (isTv) {
            "No próximo passo, aponte a câmera do celular para o QR Code ou entre com o seu número. " +
                "Sua conta fica só neste aparelho."
        } else {
            "No próximo passo, entre com o seu número do Telegram. Sua conta fica só neste aparelho."
        },
        accent = BrandColors.Cyan,
        icon = Icons.Filled.Lock
    )
)

/**
 * Introdução da primeira abertura (antes do login). Carrossel com hero: phone = hero em cima e texto
 * embaixo com swipe; TV/tela larga = duas colunas, navegação por D-pad (←/→ trocam de página).
 * [onFinished] é chamado em "Começar" e em "Pular".
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val adaptive = rememberAdaptiveLayoutInfo()
    val pages = remember(adaptive.isTv) { onboardingPages(adaptive.isTv) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val animations = LocalAnimationsEnabled.current
    // targetPage: toques rápidos no D-pad avançam várias páginas mesmo durante a animação.
    val page = pagerState.targetPage
    val isLast = page == pages.lastIndex

    fun goTo(target: Int) {
        scope.launch {
            if (animations) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
        }
    }
    val next = { if (isLast) onFinished() else goTo(page + 1) }

    // Voltar recua uma página; na primeira, deixa o Voltar seguir para o app (confirmação de saída).
    BackHandler(enabled = page > 0) { goTo(page - 1) }

    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(adaptive.useTvLayout) {
        if (!adaptive.useTvLayout) return@LaunchedEffect
        // A tela de trás (Login) também pede foco ao montar: reafirma depois que ela assentar.
        repeat(3) {
            runCatching { primaryFocus.requestFocus() }
            kotlinx.coroutines.delay(200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColors.Background)
            .brandBackdrop(BrandBackdropKind.Glow)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { if (!isLast) goTo(page + 1); !isLast }
                    Key.DirectionLeft -> { if (page > 0) goTo(page - 1); page > 0 }
                    else -> false
                }
            }
    ) {
        if (adaptive.useTvLayout || !adaptive.isPortrait) {
            WideOnboarding(
                adaptive = adaptive,
                pages = pages,
                page = page,
                pagerState = pagerState,
                primaryFocus = primaryFocus,
                onNext = next,
                onSkip = onFinished,
                onDot = ::goTo
            )
        } else {
            PhoneOnboarding(
                pages = pages,
                page = page,
                pagerState = pagerState,
                onNext = next,
                onSkip = onFinished,
                onDot = ::goTo
            )
        }
    }
}

@Composable
private fun PhoneOnboarding(
    pages: List<OnboardingPage>,
    page: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDot: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (page < pages.lastIndex) SkipButton(onSkip)
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            val item = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { OnboardingHero(item, size = 260.dp) }
                Column(
                    modifier = Modifier.weight(0.85f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PageTexts(item, align = TextAlign.Center)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PageDots(count = pages.size, current = page, accent = pages[page].accent, onDot = onDot)
            BrandButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                PrimaryLabel(isLast = page == pages.lastIndex)
            }
        }
    }
}

@Composable
private fun WideOnboarding(
    adaptive: AdaptiveLayoutInfo,
    pages: List<OnboardingPage>,
    page: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    primaryFocus: FocusRequester,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDot: (Int) -> Unit
) {
    val animations = LocalAnimationsEnabled.current
    val heroSize = (adaptive.heightDp * 0.55f).coerceIn(220f, 420f).dp
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (adaptive.isTv) {
                    Modifier.padding(horizontal = adaptive.tvSafeHorizontal, vertical = adaptive.tvSafeVertical)
                } else {
                    Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 16.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hero: no toque continua deslizável; na TV a troca vem do D-pad/botões.
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !adaptive.isTv,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) { index ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OnboardingHero(pages[index], size = heroSize)
            }
        }
        Spacer(Modifier.width(48.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            PageDots(count = pages.size, current = page, accent = pages[page].accent, onDot = onDot)
            // Altura fixa do texto: pontos e botões não "pulam" entre páginas de textos diferentes.
            AnimatedContent(
                targetState = page,
                modifier = Modifier.height(200.dp),
                contentAlignment = Alignment.TopStart,
                transitionSpec = {
                    if (animations) fadeIn(tween(260)) togetherWith fadeOut(tween(180))
                    else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                },
                label = "onboardingText"
            ) { index ->
                Column { PageTexts(pages[index], align = TextAlign.Start, large = true) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandButton(onClick = onNext, modifier = Modifier.focusRequester(primaryFocus)) {
                    PrimaryLabel(isLast = page == pages.lastIndex)
                }
                if (page < pages.lastIndex) {
                    BrandButton(onClick = onSkip, style = BrandButtonStyle.Secondary) {
                        BrandButtonLabel("Pular")
                    }
                }
            }
            if (adaptive.isTv) {
                Text(
                    "Use ◀ ▶ no controle para navegar",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun PrimaryLabel(isLast: Boolean) {
    BrandButtonLabel(if (isLast) "Começar" else "Avançar")
    if (!isLast) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SkipButton(onSkip: () -> Unit) {
    Text(
        "Pular",
        style = MaterialTheme.typography.titleSmall,
        color = BrandColors.TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSkip)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun PageTexts(page: OnboardingPage, align: TextAlign, large: Boolean = false) {
    Text(
        page.title,
        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = align,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(14.dp))
    Text(
        page.body,
        style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        color = BrandColors.TextSecondary,
        textAlign = align,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PageDots(count: Int, current: Int, accent: Color, onDot: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(if (selected) accent else BrandColors.BorderStrong, label = "dotColor")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .background(color, CircleShape)
                    .clickable(onClick = { onDot(i) })
            )
        }
    }
}

/** Arte do hero desenhada em código: halo radial na cor da página, anéis e o ícone num disco. */
@Composable
private fun OnboardingHero(page: OnboardingPage, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(page.accent.copy(alpha = 0.38f), page.accent.copy(alpha = 0.08f), Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r
            )
            drawCircle(color = page.accent.copy(alpha = 0.22f), radius = r * 0.78f, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(color = page.accent.copy(alpha = 0.12f), radius = r * 0.96f, style = Stroke(width = 1.dp.toPx()))
            // Pontos de "órbita" para dar profundidade.
            listOf(0.2f to 0.78f, 0.62f to 0.96f, 1.15f to 0.78f).forEachIndexed { i, (angle, ring) ->
                val a = (angle + i) * Math.PI.toFloat()
                drawCircle(
                    color = page.accent.copy(alpha = 0.7f),
                    radius = (3 + i).dp.toPx(),
                    center = Offset(center.x + kotlin.math.cos(a) * r * ring, center.y + kotlin.math.sin(a) * r * ring)
                )
            }
        }
        val disc = size * 0.46f
        Box(
            Modifier
                .size(disc)
                .background(
                    Brush.linearGradient(listOf(BrandColors.SurfaceAlt, BrandColors.Surface)),
                    CircleShape
                )
                .border(1.5.dp, page.accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (page.icon != null) {
                Icon(page.icon, contentDescription = null, tint = page.accent, modifier = Modifier.size(disc * 0.42f))
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_brand_logo),
                    contentDescription = "NBR Play",
                    modifier = Modifier.size(disc * 0.7f)
                )
            }
        }
    }
}
