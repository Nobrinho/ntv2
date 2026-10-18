package com.ntv2.app.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LinkMode { MENU, SHOW_CODE, ENTER_CODE }

/**
 * Conectar aparelhos: a TV mostra um código, o celular digita, e os dois passam a compartilhar
 * Minha lista, progresso e histórico. Na TV o caminho principal é mostrar; no celular, digitar.
 */
@Composable
internal fun DeviceLinkOverlay(
    repository: DeviceLinkRepository,
    isTv: Boolean,
    onClose: () -> Unit
) {
    var mode by remember { mutableStateOf(LinkMode.MENU) }
    BackHandler(enabled = true) { if (mode == LinkMode.MENU) onClose() else mode = LinkMode.MENU }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onClose)
            .trapFocus(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.Border, RoundedCornerShape(8.dp))
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Conectar aparelhos",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            when (mode) {
                LinkMode.MENU -> LinkMenu(
                    repository = repository,
                    isTv = isTv,
                    onShowCode = { mode = LinkMode.SHOW_CODE },
                    onEnterCode = { mode = LinkMode.ENTER_CODE },
                    onClose = onClose
                )
                LinkMode.SHOW_CODE -> ShowCode(repository, onDone = { mode = LinkMode.MENU })
                LinkMode.ENTER_CODE -> EnterCode(repository, onDone = { mode = LinkMode.MENU })
            }
        }
    }
}

@Composable
private fun LinkMenu(
    repository: DeviceLinkRepository,
    isTv: Boolean,
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
        color = BrandColors.TextMuted,
        style = MaterialTheme.typography.bodyMedium
    )
    when {
        devices == null && !loadFailed -> CircularProgressIndicator(
            color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)
        )
        loadFailed -> Text("Não foi possível carregar os aparelhos. Verifique a internet.", color = BrandColors.Warning,
            style = MaterialTheme.typography.bodyMedium)
        linked -> devices!!.forEach { DeviceRow(it) }
    }

    val show: @Composable (Modifier, Boolean) -> Unit = { m, primary ->
        TestButton(icon = Icons.Filled.QrCode2, label = "Mostrar código", primary = primary, modifier = m, onClick = onShowCode)
    }
    val enter: @Composable (Modifier, Boolean) -> Unit = { m, primary ->
        TestButton(icon = Icons.Filled.Keyboard, label = "Digitar código", primary = primary, modifier = m, onClick = onEnterCode)
    }
    // TV: o normal é mostrar (digitar com o controle é chato); celular: digitar o código da TV.
    if (isTv) {
        show(Modifier.fillMaxWidth().focusRequester(firstFocus), true)
        enter(Modifier.fillMaxWidth(), false)
    } else {
        enter(Modifier.fillMaxWidth().focusRequester(firstFocus), true)
        show(Modifier.fillMaxWidth(), false)
    }
    if (linked) {
        if (!confirmLeave) {
            TestButton(icon = Icons.Filled.LinkOff, label = "Desconectar este aparelho",
                modifier = Modifier.fillMaxWidth().focusRequester(leaveFocus),
                onClick = { leaveTouched = true; confirmLeave = true })
        } else {
            Text("Este aparelho sai da conta e fica com uma cópia dos dados. Os outros continuam conectados.",
                color = BrandColors.Warning, style = MaterialTheme.typography.bodyMedium)
            TestButton(
                icon = Icons.Filled.LinkOff,
                label = if (leaving) "Desconectando…" else "Confirmar desconexão",
                enabled = !leaving,
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(leaveFocus),
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
    TestButton(icon = Icons.Filled.Close, label = "Fechar", modifier = Modifier.fillMaxWidth(), onClick = onClose)
}

@Composable
private fun DeviceRow(device: LinkedDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BrandColors.SurfaceAlt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Devices, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(20.dp))
        Text(device.name ?: "Aparelho", color = Color.White, style = MaterialTheme.typography.titleSmall,
            maxLines = 1, modifier = Modifier.weight(1f))
        if (device.thisDevice) {
            Text("este aparelho", color = BrandColors.Accent, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ShowCode(repository: DeviceLinkRepository, onDone: () -> Unit) {
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
            Text("Não foi possível gerar o código. Verifique a internet.", color = BrandColors.Warning,
                style = MaterialTheme.typography.bodyMedium)
            TestButton(icon = Icons.Filled.Refresh, label = "Tentar de novo", primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(actionFocus), onClick = { attempt++ })
        }
        status == LinkStatus.LINKED -> {
            ResultLine(Icons.Filled.CheckCircle, BrandColors.Accent,
                "Conectado${linkedTo?.let { " a $it" } ?: ""}. Minha lista e o progresso já estão sendo sincronizados.")
            TestButton(icon = Icons.Filled.CheckCircle, label = "OK", primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(actionFocus), onClick = onDone)
        }
        status == LinkStatus.EXPIRED || status == LinkStatus.NOT_FOUND -> {
            Text("O código expirou.", color = BrandColors.Warning, style = MaterialTheme.typography.bodyMedium)
            TestButton(icon = Icons.Filled.Refresh, label = "Gerar outro código", primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(actionFocus), onClick = { attempt++ })
        }
        code == null -> CircularProgressIndicator(color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        else -> {
            val c = code!!
            Text("No outro aparelho, abra Configurações › Conectar aparelhos › Digitar código e informe:",
                color = BrandColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
            Text(
                c.code.chunked(3).joinToString(" "),
                color = Color.White,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandColors.SurfaceAlt)
                    .padding(vertical = 18.dp)
            )
            val left = ((c.expiresAtMs - now) / 1000).coerceAtLeast(0)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text("Aguardando confirmação · expira em %d:%02d".format(left / 60, left % 60),
                    color = BrandColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            TestButton(icon = Icons.Filled.Close, label = "Cancelar", modifier = Modifier.fillMaxWidth().focusRequester(actionFocus),
                onClick = onDone)
        }
    }
}

@Composable
private fun EnterCode(repository: DeviceLinkRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ClaimResult?>(null) }
    val fieldFocus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    fun submit() {
        if (sending || text.length != DeviceLinkRepository.CODE_LENGTH) return
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
        ResultLine(Icons.Filled.CheckCircle, BrandColors.Accent,
            if (result == ClaimResult.LINKED) "Aparelhos conectados. Minha lista e o progresso já estão sendo sincronizados."
            else "Estes aparelhos já estavam conectados.")
        TestButton(icon = Icons.Filled.CheckCircle, label = "OK", primary = true,
            modifier = Modifier.fillMaxWidth().focusRequester(okFocus), onClick = onDone)
        return
    }

    Text("Digite o código que aparece na outra tela:", color = BrandColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    BasicTextField(
        value = text,
        onValueChange = { text = DeviceLinkRepository.normalizeCode(it); result = null },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 36.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 8.sp, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(BrandColors.Accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(fieldFocus)
            .onFocusChanged { fieldFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(BrandColors.SurfaceAlt)
            .border(if (fieldFocused) 3.dp else 1.dp, if (fieldFocused) BrandColors.Accent else BrandColors.Border, RoundedCornerShape(8.dp))
            .padding(vertical = 16.dp)
    )
    result?.let { r ->
        val msg = when (r) {
            ClaimResult.INVALID -> "Código não encontrado. Confira as letras e números."
            ClaimResult.EXPIRED -> "Esse código expirou. Gere outro no aparelho que mostra o código."
            ClaimResult.OWN_CODE -> "Esse código é deste aparelho. Digite-o no outro aparelho."
            ClaimResult.RATE_LIMITED -> "Muitas tentativas. Aguarde um minuto e tente de novo."
            else -> "Não foi possível conectar. Verifique a internet."
        }
        Text(msg, color = BrandColors.Warning, style = MaterialTheme.typography.bodyMedium)
    }
    TestButton(
        icon = Icons.Filled.Devices,
        label = if (sending) "Conectando…" else "Conectar",
        enabled = !sending && text.length == DeviceLinkRepository.CODE_LENGTH,
        primary = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = { submit() }
    )
    TestButton(icon = Icons.Filled.Close, label = "Voltar", modifier = Modifier.fillMaxWidth(), onClick = onDone)
}

@Composable
private fun ResultLine(icon: ImageVector, color: Color, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val POLL_MS = 3_000L
