package dev.glyphrotator.app.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpriteLibraryTest {

    private fun dex(name: String) = SpriteLibrary.dexNumberFrom(name)

    @Test
    fun `lee el numero con ceros delante`() {
        assertEquals(130, dex("0130.gif"))
        assertEquals(1, dex("0001.gif"))
        assertEquals(151, dex("0151.gif"))
    }

    @Test
    fun `lee el numero sin ceros delante`() {
        assertEquals(6, dex("6.gif"))
    }

    @Test
    fun `admite que el nombre siga despues del numero`() {
        assertEquals(130, dex("130-gyarados.gif"))
        assertEquals(25, dex("025_pikachu_animado.gif"))
    }

    @Test
    fun `descarta lo que no empieza por numero`() {
        assertNull(dex("gyarados.gif"))
        assertNull(dex("glyph_animation_globe.gif"))
        assertNull(dex(".gif"))
    }

    @Test
    fun `descarta numeros fuera de la primera generacion`() {
        // Si no, un archivo suelto como "2024-captura.gif" se colaría como si fuera un sprite.
        assertNull(dex("0.gif"))
        assertNull(dex("152.gif"))
        assertNull(dex("2024-captura.gif"))
    }
}
