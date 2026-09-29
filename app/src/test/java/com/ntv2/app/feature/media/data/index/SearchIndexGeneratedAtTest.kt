package com.ntv2.app.feature.media.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchIndexGeneratedAtTest {

    @Test
    fun `le generated_at com fuso`() {
        // 2026-09-29T05:00:21Z
        assertEquals(1_790_658_021_000L, parseGeneratedAt("2026-09-29T05:00:21+00:00"))
        assertEquals(1_790_658_021_000L, parseGeneratedAt("2026-09-29T02:00:21-03:00"))
    }

    @Test
    fun `le generated_at com Z`() {
        assertEquals(1_790_658_021_000L, parseGeneratedAt("2026-09-29T05:00:21Z"))
    }

    @Test
    fun `vazio ou invalido vira null`() {
        assertNull(parseGeneratedAt(null))
        assertNull(parseGeneratedAt(""))
        assertNull(parseGeneratedAt("null"))
        assertNull(parseGeneratedAt("ontem"))
    }
}
