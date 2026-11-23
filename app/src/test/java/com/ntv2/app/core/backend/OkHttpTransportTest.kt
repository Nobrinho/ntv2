package com.ntv2.app.core.backend

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OkHttpTransportTest {

    private fun BackendRequest.bodyText(): String? =
        toOkHttpRequest().body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }

    @Test
    fun `GET vai sem corpo e com os cabeçalhos`() {
        val req = BackendRequest(
            method = "GET",
            url = "https://exemplo.supabase.co/rest/v1/app_config",
            headers = mapOf("apikey" to "k", "Content-Type" to "application/json"),
            body = null
        ).toOkHttpRequest()

        assertEquals("GET", req.method)
        assertNull(req.body)
        assertEquals("k", req.header("apikey"))
    }

    @Test
    fun `POST leva o corpo em UTF-8 com o tipo do cabeçalho`() {
        val request = BackendRequest(
            method = "POST",
            url = "https://exemplo.supabase.co/rest/v1/rpc/f",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"título":"ação"}"""
        )
        val req = request.toOkHttpRequest()

        assertEquals("POST", req.method)
        assertEquals("application", req.body?.contentType()?.type)
        assertEquals("json", req.body?.contentType()?.subtype)
        assertEquals("""{"título":"ação"}""", request.bodyText())
    }

    @Test
    fun `POST sem corpo recebe corpo vazio`() {
        val request = BackendRequest("POST", "https://exemplo.supabase.co/auth/v1/logout", emptyMap(), null)

        assertNotNull(request.toOkHttpRequest().body)
        assertEquals("", request.bodyText())
    }
}
