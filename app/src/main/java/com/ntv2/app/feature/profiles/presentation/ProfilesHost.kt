package com.ntv2.app.feature.profiles.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.profiles.Profile
import com.ntv2.app.core.profiles.ProfileRules
import com.ntv2.app.core.profiles.ProfileStore
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo

/** Telas do fluxo de perfis (protótipo 43, 44, 60-66; TV 19, 22-24). */
sealed interface ProfileScreen {
    /** "Quem está assistindo?" (abertura do app). */
    data object Picker : ProfileScreen
    data object Manage : ProfileScreen
    /** Troca rápida (sheet no celular, painel lateral na TV). */
    data object Switcher : ProfileScreen
    data class Edit(val draft: Profile, val isNew: Boolean) : ProfileScreen
    data class Avatar(val draft: Profile, val isNew: Boolean) : ProfileScreen
    data class PinCreate(val draft: Profile, val isNew: Boolean, val first: String? = null) : ProfileScreen
    data class PinEnter(val owner: Profile, val purpose: PinPurpose) : ProfileScreen
    data class Delete(val draft: Profile) : ProfileScreen
}

sealed interface PinPurpose {
    /** Entrar num perfil com PIN. */
    data class Enter(val targetId: String) : PinPurpose
    /** Sair do perfil infantil com PIN, indo para [targetId]. */
    data class ExitKids(val targetId: String) : PinPurpose
    /** Confirmar o PIN atual para removê-lo na edição. */
    data class Remove(val draft: Profile, val isNew: Boolean) : PinPurpose
}

/**
 * Hospeda o fluxo de perfis por cima do app. [screen] null = fechado.
 * MOCK: dados locais ([ProfileStore]); ver a nota na classe.
 */
@Composable
fun ProfilesHost(store: ProfileStore, screen: ProfileScreen?, onNavigate: (ProfileScreen?) -> Unit) {
    if (screen == null) return
    val profiles by store.profiles.collectAsState()
    val activeId by store.activeId.collectAsState()
    val tv = rememberAdaptiveLayoutInfo().useTvLayout

    fun activate(id: String) { store.setActive(id); onNavigate(null) }

    /** Regras de PIN ao trocar de perfil (sair do infantil e entrar em perfil protegido). */
    fun requestSwitch(target: Profile, fromPicker: Boolean) {
        val active = store.active()
        when {
            !fromPicker && target.id != active.id && active.kids && active.hasPin ->
                onNavigate(ProfileScreen.PinEnter(active, PinPurpose.ExitKids(target.id)))
            target.hasPin && (fromPicker || target.id != active.id) ->
                onNavigate(ProfileScreen.PinEnter(target, PinPurpose.Enter(target.id)))
            else -> activate(target.id)
        }
    }

    fun back() {
        when (screen) {
            ProfileScreen.Picker -> Unit // escolha obrigatória na abertura
            ProfileScreen.Manage, ProfileScreen.Switcher -> onNavigate(null)
            is ProfileScreen.Edit -> onNavigate(ProfileScreen.Manage)
            is ProfileScreen.Avatar -> onNavigate(ProfileScreen.Edit(screen.draft, screen.isNew))
            is ProfileScreen.PinCreate -> onNavigate(ProfileScreen.Edit(screen.draft, screen.isNew))
            is ProfileScreen.Delete -> onNavigate(ProfileScreen.Edit(screen.draft, false))
            is ProfileScreen.PinEnter -> when (val p = screen.purpose) {
                is PinPurpose.Remove -> onNavigate(ProfileScreen.Edit(p.draft, p.isNew))
                else -> onNavigate(ProfileScreen.Picker)
            }
        }
    }
    BackHandler { back() }

    if (screen is ProfileScreen.Switcher) {
        SwitcherPanel(
            profiles = profiles, activeId = activeId, tv = tv,
            onPick = { requestSwitch(it, fromPicker = false) },
            onAdd = { onNavigate(ProfileScreen.Edit(Profile.new(color = profiles.size % ProfilePalette.size), true)) },
            onManage = { onNavigate(ProfileScreen.Manage) },
            onDismiss = { onNavigate(null) }
        )
        return
    }
    if (screen is ProfileScreen.Delete) {
        Box(Modifier.fillMaxSize().background(BrandColors.Background))
        ConfirmDialog(
            title = "Excluir o perfil ${screen.draft.name}?",
            message = "A lista, o histórico e o “Continuar assistindo” deste perfil serão apagados deste aparelho.",
            icon = NbrIcons.Trash2,
            confirmLabel = "Excluir perfil",
            confirmIcon = NbrIcons.Trash2,
            cancelLabel = "Cancelar",
            cancelIcon = NbrIcons.X,
            onConfirm = { store.delete(screen.draft.id); onNavigate(ProfileScreen.Manage) },
            onDismiss = { back() },
            destructive = true
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BrandColors.Background)
            .clickable(enabled = false) {} // bloqueia toques no app por baixo
    ) {
        AnimatedContent(
            targetState = screen,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "perfis"
        ) { s ->
            when (s) {
                ProfileScreen.Picker -> ProfileGrid(
                    title = "Quem está assistindo?", subtitle = null, profiles = profiles, editing = false, tv = tv,
                    onPick = { requestSwitch(it, fromPicker = true) },
                    onAdd = { onNavigate(ProfileScreen.Edit(Profile.new(color = profiles.size % ProfilePalette.size), true)) },
                    footer = {
                        ProfileOutlineButton("Gerenciar perfis", NbrIcons.Pencil) { onNavigate(ProfileScreen.Manage) }
                        Text(
                            "Cada perfil tem a própria lista, histórico e “Continuar assistindo”.",
                            style = NbrType.Caption, color = BrandColors.TextMuted, textAlign = TextAlign.Center
                        )
                    }
                )
                ProfileScreen.Manage -> ProfileGrid(
                    title = "Gerenciar perfis",
                    subtitle = "Toque num perfil para editar nome, avatar, PIN e preferências.",
                    profiles = profiles, editing = true, tv = tv,
                    onPick = { onNavigate(ProfileScreen.Edit(it, false)) },
                    onAdd = { onNavigate(ProfileScreen.Edit(Profile.new(color = profiles.size % ProfilePalette.size), true)) },
                    footer = {
                        Text(
                            "Até ${ProfileRules.MAX_PROFILES} perfis por conta · ${profiles.size} em uso",
                            style = NbrType.Caption, color = BrandColors.TextMuted
                        )
                        ProfileButton("Concluído", onClick = { onNavigate(null) }, modifier = Modifier.widthIn(max = 360.dp))
                    }
                )
                is ProfileScreen.Edit -> EditProfile(
                    draft = s.draft, isNew = s.isNew, canDelete = !s.isNew && profiles.size > 1, tv = tv,
                    onChange = { onNavigate(s.copy(draft = it)) },
                    onBack = { back() },
                    onSave = {
                        val name = s.draft.name.trim().ifEmpty { "Perfil ${profiles.size + 1}" }
                        store.save(s.draft.copy(name = name))
                        onNavigate(ProfileScreen.Manage)
                    },
                    onAvatar = { onNavigate(ProfileScreen.Avatar(s.draft, s.isNew)) },
                    onPin = {
                        if (s.draft.hasPin) onNavigate(ProfileScreen.PinEnter(s.draft, PinPurpose.Remove(s.draft, s.isNew)))
                        else onNavigate(ProfileScreen.PinCreate(s.draft, s.isNew))
                    },
                    onDelete = { onNavigate(ProfileScreen.Delete(s.draft)) }
                )
                is ProfileScreen.Avatar -> AvatarPicker(
                    draft = s.draft, tv = tv,
                    onBack = { back() },
                    onUse = { onNavigate(ProfileScreen.Edit(it, s.isNew)) }
                )
                is ProfileScreen.PinCreate -> PinPad(
                    avatar = null,
                    title = if (s.first == null) "Crie um PIN de 4 dígitos" else "Confirme o PIN",
                    subtitle = "Pedimos o PIN para entrar neste perfil. No perfil infantil, ele também é pedido para sair.",
                    step = if (s.first == null) "1 de 2" else "2 de 2",
                    tv = tv,
                    onBack = { back() },
                    onComplete = { pin ->
                        when {
                            s.first == null -> { onNavigate(s.copy(first = pin)); null }
                            s.first == pin -> { onNavigate(ProfileScreen.Edit(s.draft.copy(pin = pin), s.isNew)); null }
                            else -> { onNavigate(s.copy(first = null)); "Os PINs não conferem. Tente de novo." }
                        }
                    }
                )
                is ProfileScreen.PinEnter -> PinPad(
                    avatar = s.owner,
                    title = when (s.purpose) {
                        is PinPurpose.ExitKids -> "PIN para sair do perfil infantil"
                        is PinPurpose.Remove -> "Digite o PIN atual"
                        is PinPurpose.Enter -> "Digite o PIN de ${s.owner.name}"
                    },
                    subtitle = if (s.purpose is PinPurpose.Remove) "Para remover o PIN deste perfil." else "Este perfil está protegido.",
                    step = null,
                    tv = tv,
                    showForgot = true,
                    onBack = { back() },
                    onComplete = { pin ->
                        if (pin != s.owner.pin) "PIN incorreto. Tente de novo." else {
                            when (val p = s.purpose) {
                                is PinPurpose.Enter -> activate(p.targetId)
                                is PinPurpose.ExitKids -> {
                                    val target = store.byId(p.targetId)
                                    if (target != null && target.hasPin) onNavigate(ProfileScreen.PinEnter(target, PinPurpose.Enter(target.id)))
                                    else activate(p.targetId)
                                }
                                is PinPurpose.Remove -> onNavigate(ProfileScreen.Edit(p.draft.copy(pin = null), p.isNew))
                            }
                            null
                        }
                    }
                )
                else -> Unit
            }
        }
    }
}

/** Coluna central (protótipo: celular 390 com margens; TV centralizada). */
@Composable
private fun ProfilePage(
    tv: Boolean,
    scroll: Boolean = false,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (tv) 720.dp else 480.dp)
                .fillMaxWidth()
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = if (tv) 32.dp else 20.dp, vertical = if (tv) 28.dp else 8.dp),
            verticalArrangement = if (centered) Arrangement.spacedBy(20.dp, Alignment.CenterVertically) else Arrangement.spacedBy(16.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            content = content
        )
    }
}

/** 43 Quem está assistindo / 60 Gerenciar perfis / TV 19. */
@Composable
private fun ProfileGrid(
    title: String,
    subtitle: String?,
    profiles: List<Profile>,
    editing: Boolean,
    tv: Boolean,
    onPick: (Profile) -> Unit,
    onAdd: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    ProfilePage(tv = tv, centered = true) {
        Text(title, style = NbrType.PageTitle.copy(fontSize = if (tv) 28.sp else 26.sp), color = ProfileText, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = NbrType.Secondary, color = BrandColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        val cells: List<Profile?> = profiles + if (profiles.size < ProfileRules.MAX_PROFILES) listOf(null) else emptyList()
        val perRow = if (tv) 5 else 2
        val size = if (tv) 110.dp else 112.dp
        cells.chunked(perRow).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                row.forEachIndexed { c, p ->
                    val mod = if (r == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier
                    if (p == null) AddCell(size, mod, onAdd) else ProfileCell(p, size, editing, mod) { onPick(p) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        footer()
    }
}

@Composable
private fun ProfileCell(p: Profile, size: androidx.compose.ui.unit.Dp, editing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(size + 24.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .scale(if (focused) 1.06f else 1f)
                .border(if (focused) 3.dp else 0.dp, if (focused) BrandColors.Accent else Color.Transparent, RoundedCornerShape(size * 0.28f + 4.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            ProfileAvatar(p, size)
            if (editing) {
                Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color(0x8C090B0F)))
                Box(Modifier.size(40.dp).clip(CircleShape).background(ProfileText), contentAlignment = Alignment.Center) {
                    Icon(NbrIcons.Pencil, contentDescription = "Editar", tint = com.ntv2.app.core.ui.BrandColors.OnCta, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = NbrType.ItemTitle, color = if (focused) ProfileText else BrandColors.TextSecondary, maxLines = 1)
            if (p.hasPin) Icon(NbrIcons.Lock, contentDescription = "Com PIN", tint = BrandColors.TextMuted, modifier = Modifier.size(13.dp))
        }
        if (p.kids) KidsBadge()
    }
}

@Composable
private fun AddCell(size: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.width(size + 24.dp).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(BrandColors.Fill)
                .border(2.dp, if (focused) BrandColors.Accent else BrandColors.Border, RoundedCornerShape(size * 0.28f)),
            contentAlignment = Alignment.Center
        ) { Icon(NbrIcons.Plus, contentDescription = null, tint = BrandColors.TextSecondary, modifier = Modifier.size(36.dp)) }
        Text("Adicionar", style = NbrType.ItemTitle, color = BrandColors.TextSecondary)
    }
}

@Composable
private fun ProfileOutlineButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(BrandShapes.Pill)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Accent.copy(alpha = 0.15f) else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) BrandColors.Accent else BrandColors.Border, BrandShapes.Pill)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandColors.TextSecondary, modifier = Modifier.size(16.dp))
        Text(label, style = NbrType.BodyStrong, color = BrandColors.TextSecondary)
    }
}

/** 44 Novo perfil / 61 Editar perfil / TV 23. */
@Composable
private fun EditProfile(
    draft: Profile,
    isNew: Boolean,
    canDelete: Boolean,
    tv: Boolean,
    onChange: (Profile) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAvatar: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    ProfilePage(tv = tv, scroll = true) {
        ProfileTopBar(if (isNew) "Novo perfil" else "Editar perfil", onBack, "Salvar", onSave)
        Column(Modifier.fillMaxWidth().clickable(onClick = onAvatar), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                ProfileAvatar(draft, 96.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(start = 0.dp).size(34.dp).clip(CircleShape)
                        .background(BrandColors.Background).padding(3.dp).clip(CircleShape).background(BrandColors.Accent),
                    contentAlignment = Alignment.Center
                ) { Icon(NbrIcons.Pencil, contentDescription = null, tint = BrandColors.OnCta, modifier = Modifier.size(15.dp)) }
            }
            Text("Trocar avatar", style = NbrType.Chip, color = BrandColors.Accent)
        }
        ProfileSectionLabel("Nome")
        NameField(draft.name) { onChange(draft.copy(name = it.take(ProfileRules.MAX_NAME))) }
        ProfileSectionLabel("Acesso")
        val access = buildList<@Composable () -> Unit> {
            add { ProfileRow(NbrIcons.Lock, "PIN do perfil", "Pedido ao entrar neste perfil", onPin) { ProfileValue(if (draft.hasPin) "Ativado" else "Desligado", muted = !draft.hasPin) } }
            add { ProfileRow(NbrIcons.ShieldCheck, "Perfil infantil", "Só títulos até a classificação escolhida", { onChange(draft.copy(kids = !draft.kids)) }) { ProfileSwitch(draft.kids) } }
            if (draft.kids) add {
                ProfileRow(NbrIcons.ShieldCheck, "Classificação máxima", "Títulos acima disso ficam ocultos", {
                    val r = ProfileRules.RATINGS; onChange(draft.copy(maxRating = r[(r.indexOf(draft.maxRating) + 1).mod(r.size)]))
                }) { ProfileValue(ProfileRules.ratingLabel(draft.maxRating)) }
            }
        }
        ProfileGroup(access)
        ProfileSectionLabel("Reprodução")
        val playback = buildList<@Composable () -> Unit> {
            add { ProfileRow(NbrIcons.SkipForward, "Próximo episódio automático", null, { onChange(draft.copy(autoNext = !draft.autoNext)) }) { ProfileSwitch(draft.autoNext) } }
            add { ProfileRow(NbrIcons.AudioLines, "Áudio preferido", null, {
                val l = ProfileRules.AUDIO; onChange(draft.copy(audio = l[(l.indexOf(draft.audio) + 1).mod(l.size)]))
            }) { ProfileValue(draft.audio) } }
            add { ProfileRow(NbrIcons.Captions, "Legendas", null, {
                val l = ProfileRules.SUBTITLES; onChange(draft.copy(subtitles = l[(l.indexOf(draft.subtitles) + 1).mod(l.size)]))
            }) { ProfileValue(draft.subtitles, muted = draft.subtitles == "Desligadas") } }
        }
        ProfileGroup(playback)
        Spacer(Modifier.height(8.dp))
        if (isNew) ProfileButton("Salvar perfil", onSave)
        if (canDelete) ProfileButton("Excluir perfil", onDelete, primary = false, destructive = true, icon = NbrIcons.Trash2)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(BrandShapes.Field)
            .background(BrandColors.Surface)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) BrandColors.Accent else BrandColors.BorderSubtle, BrandShapes.Field)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Nome do perfil", style = NbrType.BodyLarge.copy(fontSize = 16.sp), color = BrandColors.TextMuted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = NbrType.BodyLarge.copy(fontSize = 16.sp, color = ProfileText),
                cursorBrush = SolidColor(BrandColors.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
        Text("${value.length}/${ProfileRules.MAX_NAME}", style = NbrType.Caption, color = BrandColors.TextMuted)
    }
}

/** 62 Escolher avatar. */
@Composable
private fun AvatarPicker(draft: Profile, tv: Boolean, onBack: () -> Unit, onUse: (Profile) -> Unit) {
    var current by remember { mutableStateOf(draft) }
    ProfilePage(tv = tv, scroll = true) {
        ProfileTopBar("Escolher avatar", onBack)
        Row(
            Modifier.fillMaxWidth().clip(BrandShapes.Lg).background(BrandColors.Surface).border(1.dp, BrandColors.BorderSubtle, BrandShapes.Lg).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(current, 56.dp)
            Text(current.name.ifBlank { "Novo perfil" }, style = NbrType.ItemTitle, color = ProfileText)
        }
        ProfileSectionLabel("Cor")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ProfilePalette.indices.forEach { i ->
                var focused by remember { mutableStateOf(false) }
                val sel = current.color == i
                Box(
                    Modifier
                        .size(44.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(CircleShape)
                        .clickable { current = current.copy(color = i) }
                        .background(profileBrush(i))
                        .border(if (sel || focused) 3.dp else 0.dp, if (focused) BrandColors.Accent else ProfileText, CircleShape)
                )
            }
        }
        ProfileSectionLabel("Símbolo")
        ProfileSymbols.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { (key, icon) ->
                    var focused by remember { mutableStateOf(false) }
                    val sel = (current.symbol ?: "type") == key
                    val shape = RoundedCornerShape(22.dp)
                    Box(
                        Modifier
                            .size(74.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .clip(shape)
                            .clickable { current = current.copy(symbol = if (key == "type") null else key) }
                            .background(if (sel) profileBrush(current.color) else androidx.compose.ui.graphics.SolidColor(BrandColors.Surface))
                            .border(if (sel || focused) 3.dp else 1.dp, if (focused) BrandColors.Accent else if (sel) ProfileText else BrandColors.BorderSubtle, shape),
                        contentAlignment = Alignment.Center
                    ) {
                        val tint = if (sel) com.ntv2.app.core.ui.BrandColors.OnCta else BrandColors.TextSecondary
                        if (icon == null) Text("Aa", style = NbrType.PageTitle, color = tint) else Icon(icon, contentDescription = key, tint = tint, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ProfileButton("Usar este avatar", { onUse(current) })
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 63 Digite o PIN / 64 Criar PIN / TV 22. [onComplete] recebe os 4 dígitos e devolve uma mensagem de
 * erro (limpa os pontos) ou null.
 */
@Composable
private fun PinPad(
    avatar: Profile?,
    title: String,
    subtitle: String,
    step: String?,
    tv: Boolean,
    showForgot: Boolean = false,
    onBack: () -> Unit,
    onComplete: (String) -> String?
) {
    var pin by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { firstKey.requestFocus() } }
    fun press(d: String) {
        if (pin.length >= 4) return
        error = null
        pin += d
        if (pin.length == 4) {
            val entered = pin
            val err = onComplete(entered)
            pin = ""
            error = err
        }
    }
    fun delete() { if (pin.isNotEmpty()) pin = pin.dropLast(1) }

    ProfilePage(tv = tv) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProfileIconButton(if (avatar != null) NbrIcons.X else NbrIcons.ChevronLeft, "Voltar", onBack)
            Spacer(Modifier.weight(1f))
            if (step != null) Text(step, style = NbrType.Caption, color = BrandColors.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(BrandColors.Fill).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = when (e.key) {
                        Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"
                        Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"
                        Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"
                        Key.Nine, Key.NumPad9 -> "9"; else -> null
                    }
                    when {
                        digit != null -> { press(digit); true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { delete(); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (avatar != null) ProfileAvatar(avatar, 72.dp)
            else Box(Modifier.size(72.dp).clip(CircleShape).background(BrandColors.Accent.copy(alpha = 0.12f)).border(1.dp, BrandColors.Accent.copy(alpha = 0.33f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(NbrIcons.Lock, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(30.dp))
            }
            Text(title, style = NbrType.SheetTitle.copy(fontSize = 20.sp), color = ProfileText, textAlign = TextAlign.Center)
            Text(subtitle, style = NbrType.Secondary.copy(lineHeight = 18.sp), color = BrandColors.TextSecondary, textAlign = TextAlign.Center)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(4) { i ->
                    val filled = i < pin.length
                    val color = if (error != null) BrandColors.Error else if (filled) BrandColors.Accent else BrandColors.TextMuted
                    Box(Modifier.size(16.dp).clip(CircleShape).background(if (filled) color else Color.Transparent).border(2.dp, color, CircleShape))
                }
            }
            if (error != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(NbrIcons.CircleAlert, contentDescription = null, tint = BrandColors.Error, modifier = Modifier.size(14.dp))
                Text(error!!, style = NbrType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = BrandColors.Error)
            }
            Spacer(Modifier.height(4.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del")
            if (tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (keys.filter { it.isNotEmpty() }).forEachIndexed { i, k ->
                        PinKey(k, if (i == 0) Modifier.focusRequester(firstKey) else Modifier, size = 48) { if (k == "del") delete() else press(k) }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { k ->
                                if (k.isEmpty()) Spacer(Modifier.width(76.dp))
                                else PinKey(k, if (k == "1") Modifier.focusRequester(firstKey) else Modifier, size = 0) { if (k == "del") delete() else press(k) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (showForgot) {
                Text(
                    "Esqueci o PIN", style = NbrType.BodyStrong, color = BrandColors.Accent,
                    modifier = Modifier.clip(BrandShapes.Pill).clickable { note = "Em breve: redefinir o PIN pelo celular do dono da conta." }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                if (note != null) Text(note!!, style = NbrType.Caption, color = BrandColors.TextMuted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Tecla do PIN: 76×64 em pílula no celular; círculo de [size] dp na TV. */
@Composable
private fun PinKey(key: String, modifier: Modifier, size: Int, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val isDel = key == "del"
    val shape = BrandShapes.Pill
    Box(
        modifier
            .then(if (size > 0) Modifier.size(size.dp) else Modifier.width(76.dp).height(64.dp))
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused && size > 0) 1.08f else 1f)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Accent else if (isDel) Color.Transparent else BrandColors.Surface)
            .border(if (focused) 2.dp else if (isDel) 0.dp else 1.dp, if (focused) Color.White else BrandColors.BorderSubtle, shape),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (focused) BrandColors.OnCta else if (isDel) BrandColors.TextSecondary else ProfileText
        if (isDel) Icon(NbrIcons.X, contentDescription = "Apagar", tint = tint, modifier = Modifier.size(22.dp))
        else Text(key, style = NbrType.PageTitle.copy(fontSize = if (size > 0) 20.sp else 26.sp), color = tint)
    }
}

/** 66 Trocar de perfil (sheet) / TV 24 (painel lateral). */
@Composable
private fun SwitcherPanel(
    profiles: List<Profile>,
    activeId: String,
    tv: Boolean,
    onPick: (Profile) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(onClick = onDismiss)) {
        val panel: Modifier = if (tv) {
            Modifier.align(Alignment.CenterEnd).width(320.dp).fillMaxSize().background(BrandColors.Surface).padding(horizontal = 28.dp, vertical = 48.dp)
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(BrandShapes.Sheet).background(BrandColors.Surface)
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp)
        }
        Column(panel.clickable(enabled = false) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!tv) Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(BrandShapes.Pill).background(Color(0x40FFFFFF)))
            }
            Text("Trocar de perfil", style = NbrType.SheetTitle.copy(fontSize = if (tv) 22.sp else 19.sp), color = ProfileText)
            Spacer(Modifier.height(6.dp))
            profiles.forEachIndexed { i, p ->
                val cur = p.id == activeId
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(BrandShapes.Lg)
                        .clickable { onPick(p) }
                        .background(if (cur || focused) BrandColors.Accent.copy(alpha = 0.08f) else Color.Transparent)
                        .border(if (cur || focused) 1.5.dp else 0.dp, if (focused) BrandColors.Accent else if (cur) BrandColors.Accent.copy(alpha = 0.7f) else Color.Transparent, BrandShapes.Lg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(p, 44.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.name, style = NbrType.ItemTitle, color = ProfileText)
                        Text(
                            when { cur -> "Assistindo agora"; p.kids -> "Infantil · até ${ProfileRules.ratingLabel(p.maxRating)}"; else -> "Perfil" },
                            style = NbrType.Caption, color = BrandColors.TextMuted
                        )
                    }
                    if (p.hasPin) Icon(NbrIcons.Lock, contentDescription = "Com PIN", tint = BrandColors.TextMuted, modifier = Modifier.size(16.dp))
                    if (cur) Icon(NbrIcons.CircleCheck, contentDescription = "Atual", tint = BrandColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(BrandColors.BorderSubtle))
            if (profiles.size < ProfileRules.MAX_PROFILES) SwitcherAction(NbrIcons.Plus, "Adicionar perfil", onAdd)
            SwitcherAction(NbrIcons.Pencil, "Gerenciar perfis", onManage)
        }
    }
}

@Composable
private fun SwitcherAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(BrandShapes.Lg)
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(BrandColors.Fill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = BrandColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        Text(label, style = NbrType.ItemTitle, color = ProfileText, modifier = Modifier.weight(1f))
        Icon(NbrIcons.ChevronRight, contentDescription = null, tint = BrandColors.TextMuted, modifier = Modifier.size(16.dp))
    }
}
