package com.ntv2.app.feature.media.data.index

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchIndexImageTest {

    private val base = "https://image.tmdb.org/t/p/w500"

    @Test
    fun `caminho TMDB recebe o host e URL completa passa inteira`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.png",
            JSONObject("""{"logo_url":"/abc.png"}""").tmdbImage("logo_url", base)
        )
        assertEquals(
            "https://cdn.exemplo/duna.png",
            JSONObject("""{"logo_url":"https://cdn.exemplo/duna.png"}""").tmdbImage("logo_url", base)
        )
    }

    @Test
    fun `ausente, vazio e os literais null-None viram nulo`() {
        assertNull(JSONObject("{}").tmdbImage("logo_url", base))
        assertNull(JSONObject("""{"logo_url":""}""").tmdbImage("logo_url", base))
        assertNull(JSONObject("""{"logo_url":"null"}""").tmdbImage("logo_url", base))
        assertNull(JSONObject("""{"logo_url":"None"}""").tmdbImage("logo_url", base))
    }
}
