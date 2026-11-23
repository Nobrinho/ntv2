package com.ntv2.app.core.backend.account

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountRepositoryTest {

    private fun repo(respond: (String) -> BackendResponse, wiped: MutableList<Boolean>): AccountRepository {
        val transport = HttpTransport { req -> respond(req.url.substringAfter("/rest/v1/")) }
        val client = SupabaseClient(BackendConfig("http://local", "anon"), transport, maxAttempts = 1)
        client.tokenProvider = { "t1" }
        return AccountRepository(client) { wiped += true }
    }

    @Test
    fun `apagar limpa o aparelho so depois do servidor confirmar`() = runTest {
        val wiped = mutableListOf<Boolean>()
        val ok = repo({ BackendResponse(200, """{"left_account":true,"profiles":2,"favorites":5,"history":3,"progress":1}""") }, wiped)
        val r = ok.deleteMyData()
        assertTrue(r.leftAccount)
        assertEquals(5, r.favorites)
        assertEquals(1, wiped.size)

        val failing = repo({ BackendResponse(500, "erro") }, wiped)
        try {
            failing.deleteMyData()
            fail("devia propagar a falha")
        } catch (_: Exception) {
        }
        assertEquals("falha no servidor não apaga nada local", 1, wiped.size)
    }

    @Test
    fun `denuncias com status e resposta, e backend antigo sem a funcao`() = runTest {
        val wiped = mutableListOf<Boolean>()
        val r = repo({
            BackendResponse(200, """[{"id":7,"title":"Matrix","reason":"bad_quality","status":"resolved",
                "admin_note":"Trocamos o arquivo","created_at":"2026-11-20T10:00:00Z","resolved_at":"2026-11-21T10:00:00Z"}]""")
        }, wiped).myReports().single()
        assertEquals("Matrix", r.title)
        assertEquals("resolved", r.status)
        assertEquals("Trocamos o arquivo", r.adminNote)

        assertTrue(repo({ BackendResponse(404, """{"code":"PGRST202"}""") }, wiped).myReports().isEmpty())
    }

    @Test
    fun `403 BLOCKED marca o aparelho como bloqueado e um 2xx desmarca`() = runTest {
        var blocked = true
        val repo = repo({
            if (blocked) BackendResponse(403, """{"code":"BLOCKED"}""") else BackendResponse(200, "[]")
        }, mutableListOf())
        assertEquals(null, repo.accountId())
        assertTrue(repo.blocked.value)
        // A rota bloqueada fica em pausa (10 min) no cliente; outra chamada que dá certo desmarca.
        blocked = false
        assertTrue(repo.myReports().isEmpty())
        assertFalse(repo.blocked.value)
    }
}
