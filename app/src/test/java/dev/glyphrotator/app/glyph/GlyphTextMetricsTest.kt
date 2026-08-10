package dev.glyphrotator.app.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MATRIX = 25

class GlyphTextMetricsTest {

    @Test
    fun `el texto vacio se queda en el centro`() {
        assertEquals(MATRIX / 2, GlyphTextMetrics.centeredX("", MATRIX))
    }

    @Test
    fun `nunca devuelve una posicion negativa aunque el texto no quepa`() {
        assertTrue(GlyphTextMetrics.centeredX("1234567890", MATRIX) >= 0)
    }

    /**
     * Regresión: el espacio en blanco del parpadeo se medía como 4px (igual que un dígito)
     * en vez de 1px como los dos puntos, así que la hora se recentraba y "saltaba" de lado
     * a lado cada segundo.
     */
    @Test
    fun `el espacio del parpadeo mide lo mismo que los dos puntos`() {
        val withColon = GlyphTextMetrics.centeredX("12:34", MATRIX)
        val withBlank = GlyphTextMetrics.centeredX("12 34", MATRIX)
        assertEquals(withColon, withBlank)
    }

    @Test
    fun `el uno ocupa menos que el resto de digitos`() {
        val narrow = GlyphTextMetrics.centeredX("11:11", MATRIX)
        val wide = GlyphTextMetrics.centeredX("88:88", MATRIX)
        assertTrue("El texto estrecho debe empezar más a la derecha", narrow > wide)
    }

    @Test
    fun `un texto mas largo empieza mas a la izquierda`() {
        val short = GlyphTextMetrics.centeredX("5", MATRIX)
        val long = GlyphTextMetrics.centeredX("100", MATRIX)
        assertTrue(long < short)
    }

    @Test
    fun `el porcentaje de bateria queda centrado de verdad`() {
        // "88" = 4+1+4 = 9px de ancho -> (25-9)/2 = 8
        assertEquals(8, GlyphTextMetrics.centeredX("88", MATRIX))
    }
}
