package com.ntv2.app.core.profiles

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Perfil de acesso (protótipo 43-45 e 60-66).
 *
 * MOCK: os perfis vivem só neste aparelho (SharedPreferences) e ainda não separam Minha lista,
 * histórico nem filtram o catálogo pela classificação. Servem para validar o fluxo e o visual antes
 * do backend (tabela de perfis no Supabase).
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
    /** PIN de 4 dígitos; null = sem PIN. */
    val pin: String?,
    val autoNext: Boolean,
    val audio: String,
    val subtitles: String
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
    val hasPin: Boolean get() = !pin.isNullOrEmpty()

    companion object {
        fun new(name: String = "", color: Int = 0) = Profile(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            color = color,
            symbol = null,
            kids = false,
            maxRating = 12,
            pin = null,
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

class ProfileStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nbr_profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()
    private val _activeId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> _profiles.value.any { it.id == id } }
            ?: _profiles.value.first().id
    )
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    fun active(): Profile = _profiles.value.firstOrNull { it.id == _activeId.value } ?: _profiles.value.first()
    fun byId(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun setActive(id: String) {
        if (byId(id) == null) return
        _activeId.value = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Cria ou atualiza (pelo id). */
    fun save(profile: Profile) {
        val list = _profiles.value
        val updated = if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it }
        else (list + profile).take(ProfileRules.MAX_PROFILES)
        persist(updated)
    }

    /** Não apaga o último perfil; se apagar o ativo, ativa o primeiro. */
    fun delete(id: String) {
        val list = _profiles.value
        if (list.size <= 1) return
        persist(list.filterNot { it.id == id })
        if (_activeId.value == id) setActive(_profiles.value.first().id)
    }

    private fun persist(list: List<Profile>) {
        _profiles.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("color", p.color)
                put("symbol", p.symbol ?: ""); put("kids", p.kids); put("maxRating", p.maxRating)
                put("pin", p.pin ?: ""); put("autoNext", p.autoNext)
                put("audio", p.audio); put("subtitles", p.subtitles)
            })
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun load(): List<Profile> {
        val raw = prefs.getString(KEY_LIST, null)
        val parsed = runCatching {
            val arr = JSONArray(raw ?: return@runCatching emptyList<Profile>())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Profile(
                    id = o.getString("id"),
                    name = o.optString("name", "Perfil"),
                    color = o.optInt("color", 0),
                    symbol = o.optString("symbol").ifEmpty { null },
                    kids = o.optBoolean("kids", false),
                    maxRating = o.optInt("maxRating", 12),
                    pin = o.optString("pin").ifEmpty { null },
                    autoNext = o.optBoolean("autoNext", true),
                    audio = o.optString("audio", "Português"),
                    subtitles = o.optString("subtitles", "Desligadas")
                )
            }
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { seed() }
    }

    /** Perfis de demonstração da primeira abertura. */
    private fun seed(): List<Profile> = listOf(
        Profile.new("Você", color = 0).copy(id = "p_main"),
        Profile.new("Kids", color = 2).copy(id = "p_kids", symbol = "sparkles", kids = true, maxRating = 12, autoNext = false)
    ).also { persistSeed(it) }

    private fun persistSeed(list: List<Profile>) {
        // Grava na próxima mudança; aqui só garante que o seed exista ao reabrir.
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("color", p.color); put("symbol", p.symbol ?: "")
                put("kids", p.kids); put("maxRating", p.maxRating); put("pin", ""); put("autoNext", p.autoNext)
                put("audio", p.audio); put("subtitles", p.subtitles)
            })
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    companion object {
        private const val KEY_LIST = "profiles"
        private const val KEY_ACTIVE = "active"

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
