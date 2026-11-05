package com.ntv2.app.feature.onboarding.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntv2.app.core.ui.AdaptiveLayoutInfo
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.LocalAnimationsEnabled
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val body: String,
    /** null = página de boas-vindas (mostra o logo no hero). */
    val icon: ImageVector?
)

private fun onboardingPages(isTv: Boolean) = listOf(
    OnboardingPage(
        title = "Bem-vindo ao NBR Play",
        body = "Seus filmes e séries em um só lugar, no celular e na TV.",
        icon = null
    ),
    OnboardingPage(
        title = "Seus canais viram catálogo",
        body = "Escolha seus canais do Telegram e o NBR Play organiza os vídeos com capas, sinopse e elenco.",
        icon = NbrIcons.Clapperboard
    ),
    OnboardingPage(
        title = "Continue de onde parou",
        body = "Progresso, favoritos e histórico salvos para você voltar ao que estava assistindo.",
        icon = NbrIcons.Play
    ),
    OnboardingPage(
        title = "Entre com o seu Telegram",
        body = if (isTv) {
            "No próximo passo, aponte a câmera do celular para o QR Code ou entre com o seu número. " +
                "Sua conta fica só neste aparelho."
        } else {
            "No próximo passo, entre com o seu número do Telegram. Sua conta fica só neste aparelho."
        },
        icon = NbrIcons.Send
    )
)

/**
 * Introdução da primeira abertura (antes do login), no visual do protótipo (02–04 / TV 02–04).
 * Celular: logo + "Pular" no topo, órbita e texto deslizáveis, pontos e botão no rodapé.
 * TV/tela larga: texto e ações à esquerda, órbita à direita; ←/→ do D-pad trocam de página.
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

/** Celular (02–04): padding 8/24/32/24, topo de 44, órbita 240/120/48, texto 28 + 16, pontos e botão de 52. */
@Composable
private fun PhoneOnboarding(
    pages: List<OnboardingPage>,
    page: Int,
    pagerState: PagerState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDot: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OnboardingLogo(width = 56.dp, height = 44.dp)
            if (page < pages.lastIndex) SkipText(onSkip)
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
                    .padding(horizontal = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { OnboardingOrbit(item.icon, orbit = 240.dp, core = 120.dp, iconSize = 48.dp) }
                PageTexts(item, tv = false)
            }
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Box(Modifier.padding(vertical = 24.dp)) {
                OnboardingDots(
                    count = pages.size, current = page, dot = 8.dp, activeWidth = 24.dp, gap = 6.dp, onDot = onDot
                )
            }
            BrandButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                PrimaryLabel(isLast = page == pages.lastIndex)
            }
        }
    }
}

/**
 * TV/tela larga (TV 02–04, protótipo ÷ 2): padding 48 e gap 48; à esquerda logo, mensagem e rodapé
 * (pontos, "Avançar" 180×36 + "Pular" 100×36, dica); à direita a órbita 280/130/55.
 */
@Composable
private fun WideOnboarding(
    adaptive: AdaptiveLayoutInfo,
    pages: List<OnboardingPage>,
    page: Int,
    pagerState: PagerState,
    primaryFocus: FocusRequester,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDot: (Int) -> Unit
) {
    val animations = LocalAnimationsEnabled.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(if (adaptive.isTv) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())
            .padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            OnboardingLogo(width = 60.dp, height = 48.dp)
            AnimatedContent(
                targetState = page,
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    if (animations) fadeIn(tween(260)) togetherWith fadeOut(tween(180))
                    else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                },
                label = "onboardingText"
            ) { index ->
                Column { PageTexts(pages[index], tv = true) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OnboardingDots(
                    count = pages.size, current = page, dot = 7.dp, activeWidth = 22.dp, gap = 5.dp, onDot = onDot
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandButton(
                        onClick = onNext,
                        modifier = Modifier
                            .focusRequester(primaryFocus)
                            .width(180.dp)
                            .height(36.dp)
                    ) {
                        PrimaryLabel(isLast = page == pages.lastIndex)
                    }
                    if (page < pages.lastIndex) {
                        BrandButton(
                            onClick = onSkip,
                            style = BrandButtonStyle.Secondary,
                            modifier = Modifier
                                .width(100.dp)
                                .height(36.dp)
                        ) {
                            BrandButtonLabel("Pular")
                        }
                    }
                }
                if (adaptive.isTv) {
                    Text("Use ◀ ▶ no controle para navegar", style = NbrType.Caption.copy(fontSize = 11.sp), color = BrandColors.TextMuted)
                }
            }
        }
        // Órbita: no toque continua deslizável; na TV a troca vem do D-pad/botões.
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !adaptive.isTv,
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
        ) { index ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OnboardingOrbit(pages[index].icon, orbit = 280.dp, core = 130.dp, iconSize = 55.dp)
            }
        }
    }
}

@Composable
private fun PrimaryLabel(isLast: Boolean) {
    BrandButtonLabel(if (isLast) "Começar" else "Avançar")
}

/** "Pular" do topo do celular: 14/600 muted. */
@Composable
private fun SkipText(onSkip: () -> Unit) {
    Text(
        "Pular",
        style = NbrType.BodyStrong,
        color = BrandColors.TextMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSkip)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

/** Título + descrição, gap 12. Celular 28/700 lh1.2 + 16/400 lh1.45; TV 32/800 lh1.1 + 14/400 lh1.45. */
@Composable
private fun PageTexts(page: OnboardingPage, tv: Boolean) {
    Text(
        page.title,
        style = if (tv) {
            NbrType.ScreenTitle.copy(fontSize = 32.sp, lineHeight = 35.2.sp, fontWeight = FontWeight.ExtraBold)
        } else {
            NbrType.ScreenTitle.copy(lineHeight = 33.6.sp)
        },
        color = OnboardingTextColor,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Text(
        page.body,
        style = if (tv) {
            NbrType.Body.copy(lineHeight = 20.3.sp)
        } else {
            NbrType.BodyLarge.copy(fontSize = 16.sp, lineHeight = 23.2.sp)
        },
        color = BrandColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
    )
}
