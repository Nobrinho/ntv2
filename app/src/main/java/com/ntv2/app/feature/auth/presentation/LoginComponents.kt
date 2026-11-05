package com.ntv2.app.feature.auth.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.R
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons

/*
 * Componentes das telas de entrada (protótipo 05–08 do celular e TV 05). Ver docs/PROTOTIPO-ESPEC.md.
 * Celular 1 px = 1 dp; TV com os valores do protótipo divididos por 2.
 */

internal val LoginText = com.ntv2.app.core.ui.BrandColors.TextPrimary

private val LoginTitle = NbrType.ScreenTitle // 28/700
private val LoginTitleTv = NbrType.ScreenTitle.copy(fontSize = 32.sp, lineHeight = (32 * 1.21f).sp)
private val LoginSubtitle = NbrType.BodyLarge // 15/400
private val LoginField = NbrType.BodyLarge.copy(fontSize = 17.sp, lineHeight = (17 * 1.21f).sp)

/** Topo de entrada: linha de 44 com `ChevronLeft` 22 · logo 56×44 · espaço 22. Sem [onBack], o chevron some. */
@Composable
internal fun LoginTopBar(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(if (focused) Modifier.border(2.dp, BrandColors.Accent, CircleShape) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(NbrIcons.ChevronLeft, contentDescription = "Voltar", tint = LoginText, modifier = Modifier.size(22.dp))
            }
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Image(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = "NBR Play",
            modifier = Modifier.size(width = 56.dp, height = 44.dp)
        )
        Spacer(Modifier.size(32.dp))
    }
}

/** Bloco de título: 28/700 (32/800 na TV) + texto 15/400 text-2, gap 8. */
@Composable
internal fun LoginTitleBlock(title: String, subtitle: String?, tv: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = if (tv) LoginTitleTv else LoginTitle, color = LoginText)
        if (subtitle != null) Text(subtitle, style = LoginSubtitle, color = BrandColors.TextSecondary)
    }
}

/** Rótulo de método ("Método 1 · QR Code (recomendado)"): 14/700 na cor dada. */
@Composable
internal fun LoginMethodLabel(text: String, color: Color = BrandColors.Accent, modifier: Modifier = Modifier) {
    Text(text, style = NbrType.Body.copy(fontWeight = FontWeight.Bold), color = color, modifier = modifier)
}

/** Cartão branco do QR (220/raio 20/QR 170 no celular; 240/raio 16/QR 190 na TV); spinner enquanto gera. */
@Composable
internal fun LoginQrCard(qr: ImageBitmap?, size: Dp, radius: Dp, qrSize: Dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(radius)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (qr != null) {
            Image(qr, contentDescription = "QR Code", modifier = Modifier.size(qrSize))
        } else {
            CircularProgressIndicator(color = BrandColors.Background)
        }
    }
}

/** Estado do QR: ponto warning + "Aguardando conexão…" (ou "Gerando QR…" em muted). */
@Composable
internal fun LoginQrStatus(ready: Boolean, tv: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (tv) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ready) {
            Box(Modifier.size(if (tv) 7.dp else 8.dp).clip(CircleShape).background(BrandColors.Warning))
        }
        Text(
            if (ready) "Aguardando conexão…" else "Gerando QR…",
            style = if (tv) NbrType.Caption else NbrType.Secondary,
            color = if (ready) BrandColors.Warning else BrandColors.TextMuted
        )
    }
}

/** Passo numerado: número em círculo 24 fill (12/700 accent; 12/800 na TV) + texto 14/400 text-2. */
@Composable
internal fun LoginStepLine(number: String, text: String, tv: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (tv) 10.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(BrandColors.Fill),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = NbrType.Caption.copy(fontWeight = FontWeight.Bold), color = BrandColors.Accent)
        }
        Text(text, style = NbrType.Body, color = BrandColors.TextSecondary)
    }
}

/** Caixa de passos do celular: padding 16, gap 12, raio 16, surface, contorno border-subtle. */
@Composable
internal fun LoginStepsBox(steps: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BrandShapes.Lg)
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.BorderSubtle, BrandShapes.Lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        steps.forEachIndexed { i, s -> LoginStepLine("${i + 1}", s) }
    }
}

/**
 * Campo de entrada do protótipo: altura 56, padding 0/16, gap 10, raio 14, surface;
 * contorno 1 border-subtle → 2 accent em foco → 2 error em erro. Prefixo 17/600 muted.
 */
@Composable
internal fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    textStyle: TextStyle = LoginField.copy(fontWeight = FontWeight.Medium),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var focused by remember { mutableStateOf(false) }
    val line = when {
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
            .border(if (focused || isError) 2.dp else 1.dp, line, BrandShapes.Field),
        singleLine = true,
        textStyle = textStyle.copy(color = LoginText),
        cursorBrush = SolidColor(BrandColors.Accent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        decorationBox = { inner ->
            Row(
                modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (prefix != null) {
                    Text(prefix, style = LoginField.copy(fontWeight = FontWeight.SemiBold), color = BrandColors.TextMuted)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = BrandColors.TextMuted.copy(alpha = 0.6f))
                    }
                    inner()
                }
            }
        }
    )
}

/** Estilo do campo de senha: "••••" 20/700. */
internal val LoginPasswordStyle: TextStyle = NbrType.BodyLarge.copy(fontSize = 20.sp, lineHeight = (20 * 1.21f).sp, fontWeight = FontWeight.Bold)

/**
 * Código em caixas: 52×64, gap 10, centralizadas, raio 14, surface, contorno 1 border;
 * a caixa atual (em foco) tem contorno 2 accent; dígito 26/700. Mínimo de 5 caixas.
 * O texto continua num campo único (mesma entrada de antes), só desenhado em caixas.
 */
@Composable
internal fun LoginCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var focused by remember { mutableStateOf(false) }
    val boxes = maxOf(5, value.length)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = NbrType.Hero.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = keyboardOptions,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.alpha(0f)) { inner() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    for (i in 0 until boxes) {
                        val current = focused && (i == value.length || (value.length >= boxes && i == boxes - 1))
                        val (w, c) = when {
                            isError -> 2.dp to BrandColors.Error
                            current -> 2.dp to BrandColors.Accent
                            else -> 1.dp to BrandColors.Border
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 64.dp)
                                .clip(BrandShapes.Field)
                                .background(BrandColors.Surface)
                                .border(w, c, BrandShapes.Field),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                value.getOrNull(i)?.toString() ?: "",
                                style = NbrType.Hero.copy(fontSize = 26.sp, lineHeight = (26 * 1.21f).sp),
                                color = LoginText
                            )
                        }
                    }
                }
            }
        }
    )
}

/** Link de texto 14/600 accent centralizado ("Reenviar código"); desabilitado fica muted. Foco D-pad = contorno. */
@Composable
internal fun LoginTextLink(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = BrandShapes.Pill
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, BrandColors.Accent, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = NbrType.BodyStrong,
            color = if (enabled) BrandColors.Accent else BrandColors.TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** Mensagem de erro: `CircleAlert` 16 error + 13/400 error, gap 8. */
@Composable
internal fun LoginError(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NbrIcons.CircleAlert, contentDescription = null, tint = BrandColors.Error, modifier = Modifier.size(16.dp))
        Text(message, style = NbrType.Secondary, color = BrandColors.Error)
    }
}

/** Separador "OU": linhas border-subtle + rótulo 12 muted. */
@Composable
internal fun LoginOrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(BrandColors.BorderSubtle))
        Text("OU", style = NbrType.Caption.copy(fontWeight = FontWeight.SemiBold), color = BrandColors.TextMuted)
        Box(Modifier.weight(1f).height(1.dp).background(BrandColors.BorderSubtle))
    }
}

/** Aviso de sessão encerrada: faixa warning@12%, contorno warning@33%, raio 14, `Info` 18 + 13/400. */
@Composable
internal fun LoginSessionEndedBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BrandShapes.Field)
            .background(BrandColors.Warning.copy(alpha = 0.12f))
            .border(1.dp, BrandColors.Warning.copy(alpha = 0.33f), BrandShapes.Field)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NbrIcons.Info, contentDescription = null, tint = BrandColors.Warning, modifier = Modifier.size(18.dp))
        Text(
            "Sua sessão do Telegram foi encerrada em outro dispositivo. Entre novamente para continuar.",
            style = NbrType.Secondary,
            color = LoginText
        )
    }
}

/** Login concluído: círculo 96 accent@12% com contorno accent@33% e `Check` 40 accent; título 22/700 + texto. */
@Composable
internal fun LoginSuccess(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(BrandColors.Accent.copy(alpha = 0.12f))
                .border(1.dp, BrandColors.Accent.copy(alpha = 0.33f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(NbrIcons.Check, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(40.dp))
        }
        Text("Login realizado", style = NbrType.PageTitle, color = LoginText, textAlign = TextAlign.Center)
        Text(
            "Preparando seu conteúdo…",
            style = NbrType.Body,
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
