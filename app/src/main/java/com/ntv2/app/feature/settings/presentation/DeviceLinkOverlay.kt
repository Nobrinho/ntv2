package com.ntv2.app.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.backend.link.ClaimResult
import com.ntv2.app.core.backend.link.DeviceLinkRepository
import com.ntv2.app.core.backend.link.LinkCode
import com.ntv2.app.core.backend.link.LinkStatus
import com.ntv2.app.core.backend.link.LinkedDevice
import com.ntv2.app.core.backend.sync.SyncOutcome
import com.ntv2.app.core.backend.sync.SyncRunner
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ntv2.app.core.ui.icons.NbrIcons

private enum class LinkMode { MENU, SHOW_CODE, ENTER_CODE }

/**
 * Conectar aparelhos: a TV mostra um código, o celular digita, e os dois passam a compartilhar
 * Minha lista, progresso e histórico. Na TV o caminho principal é mostrar; no celular, digitar.
 */
@Composable
internal fun DeviceLinkOverlay(
    repository: DeviceLinkRepository,
    isTv: Boolean,
    onClose: () -> Unit,
    // TV com controle remoto: o código é digitado num teclado próprio, sem abrir o teclado do sistema.
    useKeypad: Boolean = false,
    // Estado do sync e "Sincronizar agora" (null = sem backend).
    syncRunner: SyncRunner? = null,
    // Conectar aparelhos é Premium: abre a tela de assinatura (null = sem pagamentos no app).
    onOpenPremium: (() -> Unit)? = null
) {
    var mode by remember { mutableStateOf(LinkMode.MENU) }
    val back = { if (mode == LinkMode.MENU) onClose() else mode = LinkMode.MENU }
    BackHandler(enabled = true) { back() }

    val body: @Composable ColumnScope.() -> Unit = {
        when (mode) {
            LinkMode.MENU -> LinkMenu(
                repository = repository,
                isTv = isTv,
                syncRunner = syncRunner,
                onShowCode = { mode = LinkMode.SHOW_CODE },
                onEnterCode = { mode = LinkMode.ENTER_CODE },
                onClose = onClose
            )
            LinkMode.SHOW_CODE -> ShowCode(repository, isTv, onDone = { mode = LinkMode.MENU })
            LinkMode.ENTER_CODE -> EnterCode(repository, isTv, useKeypad, onDone = { mode = LinkMode.MENU }, onOpenPremium = onOpenPremium)
        }
    }
    if (isTv) {
        // TV 14: tela cheia, padding 48/60; título 32/800 à esquerda, cartão do código à direita.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandColors.Background)
                .clickable(enabled = false) {}
                .trapFocus()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 60.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            Text("Conectar aparelhos", color = SettingsText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                style = NbrType.ScreenTitle)
            Column(Modifier.widthIn(max = 840.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
        }
    } else {
        // Celular 29/30: subtela com padding 24 e gap 18.
        SettingsPhonePage(title = "Conectar aparelhos", onBack = back, horizontalPadding = 24.dp, gap = 18.dp, content = body)
    }
}

@Composable
private fun LinkMenu(
    repository: DeviceLinkRepository,
    isTv: Boolean,
    syncRunner: SyncRunner?,
    onShowCode: () -> Unit,
    onEnterCode: () -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<LinkedDevice>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val leaveFocus = remember { FocusRequester() }

    LaunchedEffect(reload) {
        loadFailed = false
        devices = runCatching { repository.devices() }.onFailure { loadFailed = true }.getOrNull()
    }
    // Também depois de carregar a lista (a coluna cresce e o foco não pode se perder).
    LaunchedEffect(devices) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    // "Desconectar" vira "Confirmar desconexão" (e volta): o botão novo recebe o foco, senão ele cai fora do diálogo.
    var leaveTouched by remember { mutableStateOf(false) }
    LaunchedEffect(confirmLeave) {
        if (!leaveTouched) return@LaunchedEffect
        withFrameNanos { }
        runCatching { leaveFocus.requestFocus() }
    }

    val linked = (devices?.size ?: 0) >= 2
    Text(
        if (linked) "Estes aparelhos compartilham Minha lista, o progresso e o histórico:"
        else "Compartilhe Minha lista, o progresso e o histórico entre a TV e o celular. " +
            "Um aparelho mostra o código e o outro digita.",
        color = BrandColors.TextSecondary,
        style = if (isTv) NbrType.Body else NbrType.Body.copy(lineHeight = 20.sp),
        fontSize = if (isTv) 14.sp else (if (linked) 13.sp else 14.sp)
    )
    when {
        devices == null && !loadFailed -> CircularProgressIndicator(
            color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)
        )
        loadFailed -> SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning,
            "Não foi possível carregar os aparelhos. Verifique a internet.", tv = isTv)
        linked -> devices!!.forEach { DeviceRow(it, isTv) }
    }

    val show: @Composable (Modifier, Boolean) -> Unit = { m, primary ->
        TestButton(icon = NbrIcons.QrCode, label = "Mostrar código", primary = primary, modifier = m, onClick = onShowCode)
    }
    val enter: @Composable (Modifier, Boolean) -> Unit = { m, primary ->
        TestButton(icon = NbrIcons.Keyboard, label = "Digitar código", primary = primary, modifier = m, onClick = onEnterCode)
    }
    // TV: o normal é mostrar (digitar com o controle é chato); celular: digitar o código da TV.
    if (isTv) {
        show(bw(isTv).focusRequester(firstFocus), true)
        enter(bw(isTv), false)
    } else {
        enter(bw(isTv).focusRequester(firstFocus), true)
        show(bw(isTv), false)
    }
    if (linked && syncRunner != null) SyncNow(syncRunner, isTv)
    if (linked) {
        if (!confirmLeave) {
            TestButton(icon = NbrIcons.Unlink, label = "Desconectar este aparelho",
                modifier = bw(isTv).focusRequester(leaveFocus),
                onClick = { leaveTouched = true; confirmLeave = true })
        } else {
            SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning,
                "Este aparelho sai da conta e fica com uma cópia dos dados. Os outros continuam conectados.", tv = isTv)
            TestButton(
                icon = NbrIcons.Unlink,
                label = if (leaving) "Desconectando…" else "Confirmar desconexão",
                enabled = !leaving,
                style = BrandButtonStyle.Destructive,
                modifier = bw(isTv).focusRequester(leaveFocus),
                onClick = {
                    leaving = true
                    scope.launch {
                        runCatching { repository.leave() }
                        leaving = false
                        confirmLeave = false
                        reload++
                    }
                }
            )
        }
    }
    TestButton(icon = NbrIcons.X, label = "Fechar", modifier = bw(isTv), onClick = onClose)
}

/** Aparelho conectado (30): surface, raio 14, padding 14, ícone 22 azul; este aparelho com borda azul. */
@Composable
private fun DeviceRow(device: LinkedDevice, tv: Boolean) {
    val name = device.name ?: "Aparelho"
    val isTvDevice = name.startsWith("TV", ignoreCase = true) || name.contains("Fire", ignoreCase = true)
    val shape = RoundedCornerShape(if (tv) 10.dp else 14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Surface)
            .border(1.dp, if (device.thisDevice) BrandColors.Accent else BrandColors.BorderSubtle, shape)
            .padding(if (tv) 10.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isTvDevice) NbrIcons.Tv else NbrIcons.Smartphone, contentDescription = null, tint = BrandColors.Accent,
            modifier = Modifier.size(if (tv) 18.dp else 22.dp))
        Text(
            if (device.thisDevice) "$name · este aparelho" else name,
            color = SettingsText, style = NbrType.BodyStrong, fontSize = if (tv) 13.sp else 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShowCode(repository: DeviceLinkRepository, tv: Boolean, onDone: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf<LinkCode?>(null) }
    var status by remember { mutableStateOf(LinkStatus.PENDING) }
    var linkedTo by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val actionFocus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        code = null
        failed = false
        status = LinkStatus.PENDING
        val created = runCatching { repository.createCode() }.getOrNull()
        if (created == null) {
            failed = true
            return@LaunchedEffect
        }
        code = created
        // Pergunta ao servidor a cada 3 s se o outro aparelho já digitou; o relógio da tela anda junto.
        while (status == LinkStatus.PENDING) {
            delay(POLL_MS)
            now = System.currentTimeMillis()
            val result = runCatching { repository.status(created.code) }.getOrNull() ?: continue
            status = result.status
            linkedTo = result.deviceName
            if (now > created.expiresAtMs && status == LinkStatus.PENDING) status = LinkStatus.EXPIRED
        }
    }
    // O botão de ação muda (Cancelar só existe depois que o código chega): pede o foco de novo a cada
    // troca, senão o foco cai fora do diálogo (ia para o menu lateral na TV).
    LaunchedEffect(status, failed, code) {
        withFrameNanos { }
        runCatching { actionFocus.requestFocus() }
    }

    when {
        failed -> {
            SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning, "Não foi possível gerar o código. Verifique a internet.", tv = tv)
            TestButton(icon = NbrIcons.RefreshCw, label = "Tentar de novo", primary = true,
                modifier = bw(tv).focusRequester(actionFocus), onClick = { attempt++ })
        }
        status == LinkStatus.LINKED -> {
            ResultLine(NbrIcons.CircleCheck, BrandColors.Success,
                "Conectado${linkedTo?.let { " a $it" } ?: ""}. Minha lista e o progresso já estão sendo sincronizados.", tv)
            TestButton(icon = NbrIcons.CircleCheck, label = "OK", primary = true,
                modifier = bw(tv).focusRequester(actionFocus), onClick = onDone)
        }
        status == LinkStatus.EXPIRED || status == LinkStatus.NOT_FOUND -> {
            SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning, "O código expirou.", tv = tv)
            TestButton(icon = NbrIcons.RefreshCw, label = "Gerar outro código", primary = true,
                modifier = bw(tv).focusRequester(actionFocus), onClick = { attempt++ })
        }
        code == null -> CircularProgressIndicator(color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        else -> {
            val c = code!!
            val left = ((c.expiresAtMs - now) / 1000).coerceAtLeast(0)
            val expires = "Aguardando confirmação · expira em %d:%02d".format(left / 60, left % 60)
            val cancel: @Composable () -> Unit = {
                TestButton(icon = NbrIcons.X, label = "Cancelar", modifier = bw(tv).focusRequester(actionFocus), onClick = onDone)
            }
            if (tv) {
                // TV 14: passos numerados à esquerda, cartão "Seu código" à direita.
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        LinkStep(1, "No outro aparelho, abra o NBR Play › Configurações")
                        LinkStep(2, "Toque em Conectar aparelhos › Digitar código")
                        LinkStep(3, "Digite o código que aparece nesta tela")
                        Box(Modifier.padding(top = 5.dp)) { cancel() }
                    }
                    CodeCard(c.code, expires, tv = true, modifier = Modifier.width(320.dp))
                }
            } else {
                Text("No outro aparelho, abra Configurações › Conectar aparelhos › Digitar código e informe:",
                    color = BrandColors.TextSecondary, style = NbrType.Secondary)
                CodeCard(c.code, expires, tv = false)
                cancel()
            }
        }
    }
}

/** Passo numerado da TV 14: círculo 24 (fill) + texto 13 text-2, gap 10. */
@Composable
private fun LinkStep(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(BrandColors.Fill), contentAlignment = Alignment.Center) {
            Text("$n", color = SettingsText, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = NbrType.Caption)
        }
        Text(text, color = BrandColors.TextSecondary, fontSize = 13.sp, style = NbrType.Secondary)
    }
}

/** Cartão "Seu código" (29 / TV 14): surface, borda azul, raio 20; código 44/800 azul (TV 55). */
@Composable
private fun CodeCard(code: String, expires: String, tv: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.Accent, shape)
            .padding(if (tv) 28.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Seu código", color = BrandColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = NbrType.Caption)
        Text(
            code.chunked(3).joinToString("-"),
            color = BrandColors.Accent,
            style = NbrType.Hero,
            fontSize = if (tv) 55.sp else 44.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = if (tv) 2.sp else 4.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(expires, color = BrandColors.Warning, style = NbrType.Caption, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EnterCode(
    repository: DeviceLinkRepository,
    tv: Boolean,
    useKeypad: Boolean,
    onDone: () -> Unit,
    onOpenPremium: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ClaimResult?>(null) }
    val fieldFocus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Com o teclado próprio (TV) nenhum campo de texto recebe foco: o teclado do sistema nunca abre.
    LaunchedEffect(Unit) { if (!useKeypad) runCatching { fieldFocus.requestFocus() } }
    // Saiu desta tela (Voltar, conectou, fechou): o teclado do sistema não pode ficar aberto por cima.
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    fun submit() {
        if (sending || text.length != DeviceLinkRepository.CODE_LENGTH) return
        keyboard?.hide()
        sending = true
        scope.launch {
            result = repository.claim(text)
            sending = false
        }
    }

    val okFocus = remember { FocusRequester() }
    LaunchedEffect(result) {
        if (result == ClaimResult.LINKED || result == ClaimResult.ALREADY_LINKED) {
            withFrameNanos { }
            runCatching { okFocus.requestFocus() }
        }
    }
    if (result == ClaimResult.LINKED || result == ClaimResult.ALREADY_LINKED) {
        ResultLine(NbrIcons.CircleCheck, BrandColors.Success,
            if (result == ClaimResult.LINKED) "Aparelhos conectados. Minha lista e o progresso já estão sendo sincronizados."
            else "Estes aparelhos já estavam conectados.", tv)
        TestButton(icon = NbrIcons.CircleCheck, label = "OK", primary = true,
            modifier = bw(tv).focusRequester(okFocus), onClick = onDone)
        return
    }

    Text("Digite o código que aparece na outra tela:", color = BrandColors.TextSecondary, style = NbrType.Secondary)
    if (useKeypad) {
        CodeKeypad(
            code = text,
            enabled = !sending,
            onChange = { next ->
                text = next
                result = null
                // Sexto caractere: conecta sozinho (o botão "Conectar" continua valendo).
                if (next.length == DeviceLinkRepository.CODE_LENGTH) submit()
            }
        )
    } else BasicTextField(
        value = text,
        onValueChange = { text = DeviceLinkRepository.normalizeCode(it); result = null },
        singleLine = true,
        textStyle = NbrType.Section.copy(color = SettingsText, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
        cursorBrush = SolidColor(BrandColors.Accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        ),
        // O onDone próprio substitui o fechar padrão: fecha o teclado sempre, mesmo com código incompleto.
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); submit() }),
        modifier = bw(tv)
            .focusRequester(fieldFocus)
            .onFocusChanged { fieldFocused = it.isFocused }
            .clip(BrandShapes.Field)
            .background(BrandColors.Surface)
            .border(if (fieldFocused) 1.5.dp else 1.dp, if (fieldFocused) BrandColors.Accent else BrandColors.Border, BrandShapes.Field)
            .padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = if (tv) 44.dp else 56.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text("Digitar código", color = BrandColors.TextMuted, style = NbrType.BodyLarge)
                inner()
            }
        }
    )
    result?.let { r ->
        val msg = when (r) {
            ClaimResult.INVALID -> "Código não encontrado. Confira as letras e números."
            ClaimResult.EXPIRED -> "Esse código expirou. Gere outro no aparelho que mostra o código."
            ClaimResult.OWN_CODE -> "Esse código é deste aparelho. Digite-o no outro aparelho."
            ClaimResult.PREMIUM_REQUIRED -> "Conectar aparelhos é um recurso do NBR Play Premium. Basta um dos dois aparelhos ter o Premium."
            ClaimResult.RATE_LIMITED -> "Muitas tentativas. Aguarde um minuto e tente de novo."
            else -> "Não foi possível conectar. Verifique a internet."
        }
        SettingsNotice(NbrIcons.TriangleAlert, BrandColors.Warning, msg, tv = tv)
        if (r == ClaimResult.PREMIUM_REQUIRED && onOpenPremium != null) {
            TestButton(icon = NbrIcons.Link, label = "Conhecer o Premium", style = BrandButtonStyle.Premium,
                modifier = bw(tv), onClick = onOpenPremium)
        }
    }
    TestButton(
        icon = NbrIcons.Link,
        label = if (sending) "Conectando…" else "Conectar",
        enabled = !sending && text.length == DeviceLinkRepository.CODE_LENGTH,
        primary = true,
        modifier = bw(tv),
        onClick = { submit() }
    )
    TestButton(icon = NbrIcons.X, label = "Voltar", modifier = bw(tv), onClick = onDone)
}

/** Acrescentar e apagar no código do teclado próprio (mesmas regras do campo de texto). */
internal object CodeKeypadLogic {
    /** Linhas do teclado: o alfabeto do servidor, 8 teclas por linha. */
    val ROWS: List<String> = DeviceLinkRepository.ALPHABET.chunked(8)

    fun append(code: String, key: Char): String = DeviceLinkRepository.normalizeCode(code + key)

    fun backspace(code: String): String = code.dropLast(1)
}

/**
 * Teclado do código para o controle remoto: as letras e números válidos numa grade, sem o teclado do
 * sistema (na TV ele abria por cima e não fechava mais). O código aparece em 6 caixas acima das teclas.
 */
@Composable
private fun CodeKeypad(code: String, enabled: Boolean, onChange: (String) -> Unit) {
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstKey.requestFocus() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until DeviceLinkRepository.CODE_LENGTH) {
            val current = i == code.length
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .size(width = 40.dp, height = 48.dp)
                    .clip(shape)
                    .background(BrandColors.Surface)
                    .border(if (current) 2.dp else 1.dp, if (current) BrandColors.Accent else BrandColors.Border, shape),
                contentAlignment = Alignment.Center
            ) {
                Text(code.getOrNull(i)?.toString() ?: "", color = SettingsText, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, style = NbrType.Section)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodeKeypadLogic.ROWS.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { c, key ->
                    KeypadKey(
                        label = key.toString(),
                        enabled = enabled,
                        modifier = if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier,
                        onClick = { onChange(CodeKeypadLogic.append(code, key)) }
                    )
                }
                if (r == CodeKeypadLogic.ROWS.lastIndex) {
                    KeypadKey(label = "Apagar", enabled = enabled && code.isNotEmpty(), wide = true,
                        onClick = { onChange(CodeKeypadLogic.backspace(code)) })
                }
            }
        }
    }
}

/** Tecla 44x40 (a de "Apagar" é larga): fill, contorno azul e fundo azul@20% em foco. */
@Composable
private fun KeypadKey(label: String, enabled: Boolean, modifier: Modifier = Modifier, wide: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .then(if (wide) Modifier.widthIn(min = 94.dp) else Modifier.width(44.dp))
            .heightIn(min = 40.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) BrandColors.Accent.copy(alpha = 0.20f) else BrandColors.Fill)
            .border(if (focused) 2.dp else 1.dp, if (focused) BrandColors.Accent else BrandColors.Border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = SettingsText.copy(alpha = if (enabled) 1f else 0.4f), fontSize = if (wide) 13.sp else 16.sp,
            fontWeight = FontWeight.Bold, style = NbrType.BodyStrong, maxLines = 1)
    }
}

/** "Sincronizar agora": refaz a sincronização na hora e mostra o resultado e a última vez que deu certo. */
@Composable
private fun SyncNow(runner: SyncRunner, tv: Boolean) {
    val scope = rememberCoroutineScope()
    val status by runner.status.collectAsState()
    var outcome by remember { mutableStateOf<SyncOutcome?>(null) }
    // Relógio da linha "há N min" (anda sozinho enquanto a tela está aberta).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status.lastSuccessAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    TestButton(
        icon = NbrIcons.RefreshCw,
        label = if (status.running) "Sincronizando…" else "Sincronizar agora",
        enabled = !status.running,
        modifier = bw(tv),
        onClick = { scope.launch { outcome = runner.syncNow() } }
    )
    val last = SyncRunner.lastSyncText(status.lastSuccessAtMs, now)
    val line = listOfNotNull(outcome?.let(SyncRunner::message), last).joinToString(" ")
    if (line.isNotEmpty()) {
        Text(line, color = if (outcome != null && outcome !is SyncOutcome.Done) BrandColors.Warning else BrandColors.TextMuted,
            style = NbrType.Caption)
    }
}

/** Sucesso (30): faixa verde@10% com borda verde, raio 14, texto 13 text-2. */
@Composable
private fun ResultLine(icon: ImageVector, color: Color, text: String, tv: Boolean) {
    SettingsNotice(icon, color, text, tv = tv)
}

/** Largura dos botões: cheia no celular; na TV, 260 dp (botões lado a lado no protótipo). */
private fun bw(tv: Boolean): Modifier = if (tv) Modifier.width(260.dp) else Modifier.fillMaxWidth()

private const val POLL_MS = 3_000L
