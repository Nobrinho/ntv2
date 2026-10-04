package com.ntv2.app.core.multipart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MultiPartRegistryTest {

    private val parts = listOf(PartRef(11, 1_900), PartRef(12, 1_900), PartRef(13, 700))

    @Test
    fun `filme e identificado pelo fileId da parte 1`() {
        val registry = MultiPartRegistry()
        registry.register(parts)
        assertEquals(parts, registry.partsOf(11))
        assertNull(registry.partsOf(12))
        assertNull(registry.partsOf(99))
    }

    @Test
    fun `cada parte informa o proprio tamanho`() {
        val registry = MultiPartRegistry()
        registry.register(parts)
        assertEquals(1_900L, registry.sizeOfPart(12))
        assertEquals(700L, registry.sizeOfPart(13))
        assertNull(registry.sizeOfPart(99))
    }

    @Test
    fun `esquecer remove o filme e os tamanhos das partes`() {
        val registry = MultiPartRegistry()
        registry.register(parts)
        registry.forget(11)
        assertNull(registry.partsOf(11))
        assertNull(registry.sizeOfPart(12))
    }

    @Test
    fun `registrar de novo substitui sem deixar tamanhos velhos`() {
        val registry = MultiPartRegistry()
        registry.register(parts)
        registry.register(listOf(PartRef(11, 500), PartRef(20, 500)))
        assertEquals(2, registry.partsOf(11)!!.size)
        assertNull(registry.sizeOfPart(12))
        assertEquals(500L, registry.sizeOfPart(20))
    }

    @Test
    fun `rejeita partes invalidas`() {
        val registry = MultiPartRegistry()
        assertThrows(IllegalArgumentException::class.java) { registry.register(listOf(PartRef(1, 10))) }
        assertThrows(IllegalArgumentException::class.java) { registry.register(listOf(PartRef(1, 10), PartRef(2, 0))) }
        assertThrows(IllegalArgumentException::class.java) { registry.register(listOf(PartRef(1, 10), PartRef(1, 10))) }
    }

    @Test
    fun `allFileIds devolve as partes ou so o proprio arquivo`() {
        val registry = MultiPartRegistry()
        registry.register(parts)
        val lookup: PartsLookup = registry
        assertEquals(listOf(11, 12, 13), lookup.allFileIds(11))
        assertEquals(listOf(99), lookup.allFileIds(99))
        val none: PartsLookup? = null
        assertEquals(listOf(5), none.allFileIds(5))
    }

    @Test
    fun `uri do filme dividido vai e volta`() {
        val uri = MultiPartUris.forFirstFileId(42)
        assertEquals("tgfile://multi/42", uri)
        assertEquals(42, MultiPartUris.firstFileIdOf(uri))
        assertNull(MultiPartUris.firstFileIdOf("tgfile://video/42"))
        assertNull(MultiPartUris.firstFileIdOf("tgfile://multi/abc"))
    }
}
