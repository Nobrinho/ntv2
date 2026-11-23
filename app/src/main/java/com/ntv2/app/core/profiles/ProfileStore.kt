package com.ntv2.app.core.profiles

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Perfil de acesso (protótipo 43-45 e 60-66): "Quem está assistindo?".
 *
 * Cada perfil tem a própria Minha lista, histórico e "continuar assistindo" (as tabelas do Room e do
 * backend são por `profileId`). Os perfis sincronizam entre os aparelhos da conta (`account_profiles`,
 * ver PersonalizationSyncer). O PIN só existe como hash ([PinHash]).
 */
data class Profile(
    val id: String,
    val name: String,
    /** Índice em [ProfilePalette]. */
    val color: Int,
    /** Símbolo do avatar ([ProfileSymbols]); null = inicial do nome. */
    val symbol: String?,
    val kids: Boolean,
    /** Classificação máxima do perfil infantil (0 = Livre). */
    val maxRating: Int,
    /** Hash do PIN de 4 dígitos ([PinHash]); null = sem PIN. */
    val pinHash: String?,
    val autoNext: Boolean,
    val audio: String,
    val subtitles: String,
    /** Ordem na grade de perfis. */
    val sort: Int = 0,
    /** Última alteração (epoch ms): last-write-wins na sincronização. 0 = perfil de exemplo, nunca enviado. */
    val updatedAt: Long = 0L
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
    val hasPin: Boolean get() = !pinHash.isNullOrEmpty()

    /** Novo PIN (o hash é gerado aqui) ou null para tirar o PIN. */
    fun withPin(pin: String?): Profile = copy(pinHash = pin?.takeIf { it.isNotEmpty() }?.let { PinHash.hash(it) })

    fun checkPin(pin: String): Boolean = PinHash.verify(pin, pinHash)

    companion object {
        fun new(name: String = "", color: Int = 0) = Profile(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            color = color,
            symbol = null,
            kids = false,
            maxRating = 12,
            pinHash = null,
            autoNext = true,
            audio = "Português",
            subtitles = "Desligadas"
        )
    }
}

object ProfileRules {
    const val MAX_PROFILES = 5
    const val MAX_NAME = 20
    val RATINGS = listOf(0, 10, 12, 14, 16)
    val AUDIO = listOf("Português", "Original")
    val SUBTITLES = listOf("Desligadas", "Português", "Inglês")
    fun ratingLabel(r: Int) = if (r == 0) "Livre" else "$r anos"
}

/** Um perfil como viaja na sincronização: vivo ([profile] != null) ou removido (tombstone). */
data class ProfileRecord(
    val id: String,
    val updatedAt: Long,
    val profile: Profile?
) {
    val deleted: Boolean get() = profile == null
}

class ProfileStore internal constructor(
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis
) : ActiveProfile, com.ntv2.app.core.backend.sync.ProfileSyncSource {
    private constructor(context: Context) :
        this(context.applicationContext.getSharedPreferences("nbr_profiles", Context.MODE_PRIVATE))

    private val lock = Any()

    /** Perfis removidos (id → quando), guardados para a remoção chegar aos outros aparelhos. */
    private var tombstones: Map<String, Long> = loadTombstones()
    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()
    private val _activeId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> _profiles.value.any { it.id == id } }
            ?: _profiles.value.first().id
    )
    override val activeId: StateFlow<String> = _activeId.asStateFlow()

    /** Avisa a sincronização (ligado pelo AppContainer; no-op sem backend). */
    @Volatile var onLocalChange: () -> Unit = {}

    /** Perfil apagado aqui ou em outro aparelho: o AppContainer remove a personalização dele. */
    @Volatile var onProfileRemoved: (String) -> Unit = {}

    fun active(): Profile = _profiles.value.firstOrNull { it.id == _activeId.value } ?: _profiles.value.first()
    fun byId(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun setActive(id: String) {
        if (byId(id) == null) return
        _activeId.value = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Cria ou atualiza (pelo id). */
    fun save(profile: Profile) {
        synchronized(lock) {
            val list = _profiles.value
            val stamped = profile.copy(updatedAt = maxOf(now(), profile.updatedAt + 1))
            val updated = if (list.any { it.id == profile.id }) {
                list.map { if (it.id == profile.id) stamped else it }
            } else {
                (list + stamped.copy(sort = (list.maxOfOrNull { it.sort } ?: -1) + 1)).take(ProfileRules.MAX_PROFILES)
            }
            tombstones = tombstones - profile.id
            persist(updated)
        }
        onLocalChange()
    }

    /** Não apaga o último perfil; se apagar o ativo, ativa o primeiro. */
    fun delete(id: String) {
        synchronized(lock) {
            val list = _profiles.value
            if (list.size <= 1 || list.none { it.id == id }) return
            tombstones = tombstones + (id to now())
            persist(list.filterNot { it.id == id })
        }
        ensureActiveExists()
        onProfileRemoved(id)
        onLocalChange()
    }

    // ---- Sincronização ----

    /** Perfis e remoções alterados depois de [sinceMs] (perfis de exemplo, com updatedAt 0, não sobem). */
    override fun changedSince(sinceMs: Long): List<ProfileRecord> = synchronized(lock) {
        _profiles.value.filter { it.updatedAt > sinceMs }.map { ProfileRecord(it.id, it.updatedAt, it) } +
            tombstones.filterValues { it > sinceMs }.map { (id, at) -> ProfileRecord(id, at, null) }
    }

    /** Aplica um perfil vindo do servidor se for mais novo (last-write-wins). Devolve se aplicou. */
    override fun applyRemote(record: ProfileRecord): Boolean {
        var removedLocal = false
        synchronized(lock) {
            val local = _profiles.value.firstOrNull { it.id == record.id }
            val localAt = local?.updatedAt ?: tombstones[record.id]
            if (localAt != null && localAt >= record.updatedAt) return false
            val remote = record.profile
            if (remote == null) {
                // Nunca fica sem perfil nenhum: o último perfil não é removido.
                if (local != null && _profiles.value.size <= 1) return false
                tombstones = tombstones + (record.id to record.updatedAt)
                persist(_profiles.value.filterNot { it.id == record.id })
                removedLocal = local != null
            } else {
                tombstones = tombstones - record.id
                val incoming = remote.copy(id = record.id, updatedAt = record.updatedAt)
                val list = _profiles.value
                var updated = if (local != null) list.map { if (it.id == record.id) incoming else it } else list + incoming
                // Perfis de exemplo deste aparelho (nunca alterados) dão lugar aos da conta.
                if (updated.size > ProfileRules.MAX_PROFILES) {
                    updated = updated.filterNot { it.updatedAt == 0L && it.id != ProfileIds.MAIN }
                }
                persist(updated.sortedBy { it.sort }.take(ProfileRules.MAX_PROFILES))
            }
        }
        ensureActiveExists()
        if (removedLocal) onProfileRemoved(record.id)
        return true
    }

    /** "Apagar meus dados": volta aos perfis de exemplo, sem tombstones. */
    fun resetToDefaults() {
        synchronized(lock) {
            tombstones = emptyMap()
            persist(defaults())
        }
        setActive(ProfileIds.MAIN)
    }

    private fun ensureActiveExists() {
        if (byId(_activeId.value) == null) setActive(_profiles.value.first().id)
    }

    private fun persist(list: List<Profile>) {
        _profiles.value = list
        writePrefs(list)
    }

    private fun writePrefs(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("color", p.color)
                put("symbol", p.symbol ?: ""); put("kids", p.kids); put("maxRating", p.maxRating)
                put("pinHash", p.pinHash ?: ""); put("autoNext", p.autoNext)
                put("audio", p.audio); put("subtitles", p.subtitles)
                put("sort", p.sort); put("updatedAt", p.updatedAt)
            })
        }
        val dead = JSONObject().apply { tombstones.forEach { (id, at) -> put(id, at) } }
        prefs.edit().putString(KEY_LIST, arr.toString()).putString(KEY_TOMBSTONES, dead.toString()).apply()
    }

    private fun loadTombstones(): Map<String, Long> = runCatching {
        val o = JSONObject(prefs.getString(KEY_TOMBSTONES, null) ?: "{}")
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())

    private fun loadProfiles(): List<Profile> {
        var migratedPin = false
        val parsed = runCatching {
            val arr = JSONArray(prefs.getString(KEY_LIST, null) ?: return@runCatching emptyList<Profile>())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                // Versões anteriores guardavam o PIN em texto ("pin"): vira hash na primeira leitura.
                val legacyPin = o.optString("pin").ifEmpty { null }
                val hash = o.optString("pinHash").ifEmpty { null }
                    ?: legacyPin?.let { migratedPin = true; PinHash.hash(it) }
                Profile(
                    id = o.getString("id"),
                    name = o.optString("name", "Perfil"),
                    color = o.optInt("color", 0),
                    symbol = o.optString("symbol").ifEmpty { null },
                    kids = o.optBoolean("kids", false),
                    maxRating = o.optInt("maxRating", 12),
                    pinHash = hash,
                    autoNext = o.optBoolean("autoNext", true),
                    audio = o.optString("audio", "Português"),
                    subtitles = o.optString("subtitles", "Desligadas"),
                    sort = o.optInt("sort", i),
                    // Perfis de antes da sincronização: os editados (com PIN) sobem uma vez; os de
                    // exemplo (updatedAt 0) não sobrescrevem os da conta.
                    updatedAt = o.optLong("updatedAt", if (legacyPin != null) now() else 0L)
                )
            }
        }.getOrDefault(emptyList())
        val list = parsed.ifEmpty { defaults() }
        if (parsed.isEmpty() || migratedPin) writePrefs(list)
        return list
    }

    /** Perfis de exemplo da primeira abertura (updatedAt 0: os da conta, se houver, prevalecem). */
    private fun defaults(): List<Profile> = listOf(
        Profile.new("Você", color = 0).copy(id = ProfileIds.MAIN, sort = 0),
        Profile.new("Kids", color = 2).copy(
            id = ProfileIds.KIDS, symbol = "sparkles", kids = true, maxRating = 12, autoNext = false, sort = 1
        )
    )

    companion object {
        private const val KEY_LIST = "profiles"
        private const val KEY_ACTIVE = "active"
        private const val KEY_TOMBSTONES = "tombstones"

        @Volatile private var instance: ProfileStore? = null
        fun get(context: Context): ProfileStore =
            instance ?: synchronized(this) { instance ?: ProfileStore(context).also { instance = it } }
    }
}

/** Ações de perfil disponíveis para qualquer tela (topo da Biblioteca, Configurações). */
data class ProfileActions(
    val current: Profile? = null,
    val openSwitcher: () -> Unit = {},
    val openManage: () -> Unit = {}
)

val LocalProfileActions = staticCompositionLocalOf { ProfileActions() }
