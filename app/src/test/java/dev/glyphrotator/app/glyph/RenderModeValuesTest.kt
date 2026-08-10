package dev.glyphrotator.app.glyph

import dev.glyphrotator.app.glyph.MatrixImageProcessor.RenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo importante aquí es que elegir un modo para un sprite no se contagie a los demás: cada
 * modo hace lo suyo y [RenderMode.LUMINANCE] sigue dando exactamente lo de siempre.
 */
class RenderModeValuesTest {

    private fun argb(alpha: Int, gray: Int) = (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray

    private fun cuadrado(gray: Int): IntArray {
        val pixels = IntArray(64) { argb(0, 0) }
        for (y in 2..5) for (x in 2..5) pixels[y * 8 + x] = argb(255, gray)
        return pixels
    }

    @Test
    fun `el modo brillo sigue siendo la luminancia de siempre`() {
        val square = cuadrado(180)
        val salida = MatrixImageProcessor.values(square, 8, RenderMode.LUMINANCE)
        for (i in square.indices) {
            assertEquals(MatrixImageProcessor.luminance(square[i]), salida[i], 0.0001f)
        }
    }

    @Test
    fun `una figura negra solo se ve fuera del modo brillo`() {
        val negra = cuadrado(0)
        val interior = 3 * 8 + 3

        assertEquals(0f, MatrixImageProcessor.values(negra, 8, RenderMode.LUMINANCE)[interior], 0.0001f)
        assertTrue(MatrixImageProcessor.values(negra, 8, RenderMode.SILHOUETTE)[interior] > 0f)
        assertTrue(MatrixImageProcessor.values(negra, 8, RenderMode.OUTLINE)[interior] > 0f)
    }

    @Test
    fun `en silueta el relleno se enciende y en contorno no`() {
        // Es justo la diferencia entre que un Pokémon claro salga como mancha o con perfil.
        val clara = cuadrado(255)
        val interior = 3 * 8 + 3
        val borde = 2 * 8 + 2

        val silueta = MatrixImageProcessor.values(clara, 8, RenderMode.SILHOUETTE)
        assertEquals(silueta[borde], silueta[interior], 0.0001f)

        val contorno = MatrixImageProcessor.values(clara, 8, RenderMode.OUTLINE)
        assertTrue(contorno[borde] > contorno[interior])
    }

    @Test
    fun `ningun modo enciende lo transparente`() {
        for (mode in RenderMode.entries) {
            val salida = MatrixImageProcessor.values(cuadrado(255), 8, mode)
            assertEquals("modo $mode", 0f, salida[0], 0.0001f)
        }
    }
}
