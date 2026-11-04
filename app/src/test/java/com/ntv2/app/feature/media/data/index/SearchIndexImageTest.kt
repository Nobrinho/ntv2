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

    private val repo = SearchIndexRepository(indexUrl = "http://invalido.localhost/index.json", client = okhttp3.OkHttpClient())

    @Test
    fun `serie do index json mantem trailer_url e None vira nulo`() {
        val com = repo.parseOneSeries(JSONObject("""{"tmdb_id":1,"title":"A","trailer_url":"https://youtu.be/abcdefghijk"}"""))
        val sem = repo.parseOneSeries(JSONObject("""{"tmdb_id":2,"title":"B","trailer_url":"None"}"""))
        assertEquals("https://youtu.be/abcdefghijk", com.trailerUrl)
        assertNull(sem.trailerUrl)
    }

    @Test
    fun `filme do index json mantem trailer_url e None ou ausente vira nulo`() {
        val com = repo.parseMovie(JSONObject("""{"tmdb_id":603,"title":"Matrix","video_message_id":5,"trailer_url":"https://www.youtube.com/watch?v=abcdefghijk"}"""))!!
        val none = repo.parseMovie(JSONObject("""{"tmdb_id":604,"title":"B","video_message_id":6,"trailer_url":"None"}"""))!!
        val ausente = repo.parseMovie(JSONObject("""{"tmdb_id":605,"title":"C","video_message_id":7}"""))!!
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", com.trailerUrl)
        assertNull(none.trailerUrl)
        assertNull(ausente.trailerUrl)
    }
}
