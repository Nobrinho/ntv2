package com.ntv2.app.core.profiles

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStoreTest {

    private var clock = 1_000L
    private fun store(prefs: FakePrefs = FakePrefs()) = ProfileStore(prefs) { clock }

    @Test
    fun `PIN hash confere e nao guarda o PIN`() {
        val hash = PinHash.hash("1234")
        assertFalse(hash.contains("1234"))
        assertTrue(PinHash.verify("1234", hash))
        assertFalse(PinHash.verify("4321", hash))
        assertFalse(PinHash.verify("1234", null))
        assertFalse(PinHash.verify("1234", "lixo"))
        // Salt aleatório: o mesmo PIN gera hashes diferentes.
        assertTrue(hash != PinHash.hash("1234"))
    }

    @Test
    fun `primeira abertura cria os perfis de exemplo que nao sobem`() {
        val s = store()
        assertEquals(listOf(ProfileIds.MAIN, ProfileIds.KIDS), s.profiles.value.map { it.id })
        assertEquals(ProfileIds.MAIN, s.activeId.value)
        assertTrue(s.changedSince(0L).isEmpty())
    }

    @Test
    fun `PIN em texto das versoes anteriores vira hash e sobe uma vez`() {
        val prefs = FakePrefs()
        prefs.values["profiles"] = JSONArray().put(
            JSONObject().put("id", "p_main").put("name", "Você").put("pin", "4321")
        ).toString()
        val s = store(prefs)
        val p = s.byId("p_main")!!
        assertTrue(p.checkPin("4321"))
        assertFalse(prefs.values["profiles"].toString().contains("\"pin\":\"4321\""))
        assertEquals(1, s.changedSince(0L).size)
    }

    @Test
    fun `salvar carimba a hora e apagar vira tombstone`() {
        val s = store()
        var changes = 0
        val removed = mutableListOf<String>()
        s.onLocalChange = { changes++ }
        s.onProfileRemoved = { removed += it }

        clock = 5_000L
        s.save(Profile.new("Ana").copy(id = "ana"))
        assertEquals(5_000L, s.byId("ana")!!.updatedAt)

        clock = 6_000L
        s.setActive("ana")
        s.delete("ana")
        assertNull(s.byId("ana"))
        assertEquals(ProfileIds.MAIN, s.activeId.value)
        assertEquals(listOf("ana"), removed)
        assertEquals(2, changes)
        val tomb = s.changedSince(5_500L).single()
        assertEquals("ana", tomb.id)
        assertTrue(tomb.deleted)
    }

    @Test
    fun `remoto mais novo vence e o mais velho e ignorado`() {
        val s = store()
        clock = 5_000L
        s.save(s.byId(ProfileIds.MAIN)!!.copy(name = "Local"))

        val older = ProfileRecord(ProfileIds.MAIN, 4_000L, s.byId(ProfileIds.MAIN)!!.copy(name = "Velho"))
        assertFalse(s.applyRemote(older))
        assertEquals("Local", s.byId(ProfileIds.MAIN)!!.name)

        val newer = ProfileRecord(ProfileIds.MAIN, 9_000L, s.byId(ProfileIds.MAIN)!!.copy(name = "Novo"))
        assertTrue(s.applyRemote(newer))
        assertEquals("Novo", s.byId(ProfileIds.MAIN)!!.name)
        assertEquals(9_000L, s.byId(ProfileIds.MAIN)!!.updatedAt)
    }

    @Test
    fun `perfis da conta substituem os de exemplo e remocao remota apaga`() {
        val s = store()
        val removed = mutableListOf<String>()
        s.onProfileRemoved = { removed += it }
        // A conta já tem 4 perfis: com os 2 de exemplo passaria do limite de 5; o Kids de exemplo sai.
        listOf("a", "b", "c", "d").forEachIndexed { i, id ->
            s.applyRemote(ProfileRecord(id, 100L + i, Profile.new(id).copy(id = id, sort = i + 2)))
        }
        val ids = s.profiles.value.map { it.id }
        assertEquals(5, ids.size)
        assertTrue(ProfileIds.MAIN in ids)
        assertFalse(ProfileIds.KIDS in ids)

        assertTrue(s.applyRemote(ProfileRecord("a", 500L, null)))
        assertNull(s.byId("a"))
        assertEquals(listOf("a"), removed)
        // Recriar com data antiga não ressuscita.
        assertFalse(s.applyRemote(ProfileRecord("a", 400L, Profile.new("a").copy(id = "a"))))
    }

    @Test
    fun `o ultimo perfil nunca e removido`() {
        val s = store()
        s.delete(ProfileIds.KIDS)
        s.delete(ProfileIds.MAIN)
        assertNotNull(s.byId(ProfileIds.MAIN))
        assertFalse(s.applyRemote(ProfileRecord(ProfileIds.MAIN, 99_999L, null)))
        assertEquals(1, s.profiles.value.size)
    }
}
