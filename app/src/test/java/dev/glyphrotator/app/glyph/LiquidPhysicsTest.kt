package dev.glyphrotator.app.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SIZE = 25

class LiquidPhysicsTest {

    private fun newHeights() = FloatArray(SIZE) { SIZE.toFloat() }

    /** Deja correr la simulación hasta que se estabiliza (el suavizado es progresivo). */
    private fun settle(heights: FloatArray, pct: Int, tiltX: Float = 0f, tiltY: Float = 0f) {
        repeat(200) { LiquidPhysics.step(heights, pct, tiltX, tiltY, SIZE) }
    }

    /** Solo las columnas dentro del círculo; las de fuera se fuerzan a "vacío". */
    private fun innerColumns(heights: FloatArray): List<Float> {
        val center = SIZE / 2f
        return heights.filterIndexed { x, _ -> kotlin.math.abs(x - center) <= center - 1f }
    }

    @Test
    fun `las alturas nunca se salen de la matriz`() {
        val heights = newHeights()
        settle(heights, pct = 50, tiltX = 9.8f, tiltY = -9.8f)
        heights.forEach { assertTrue("Altura fuera de rango: $it", it in 0f..SIZE.toFloat()) }
    }

    @Test
    fun `con mas bateria el liquido sube mas`() {
        val low = newHeights().also { settle(it, pct = 10) }
        val high = newHeights().also { settle(it, pct = 90) }

        val lowAverage = innerColumns(low).average()
        val highAverage = innerColumns(high).average()

        // Menos altura = más arriba en pantalla (el eje Y crece hacia abajo).
        assertTrue("Con 90% debería llegar más arriba que con 10%", highAverage < lowAverage)
    }

    @Test
    fun `la bateria vacia deja el liquido en el fondo`() {
        val heights = newHeights()
        settle(heights, pct = 0)
        innerColumns(heights).forEach {
            assertTrue("Con 0% el líquido debe quedar abajo, estaba en $it", it >= SIZE * 0.8f)
        }
    }

    /**
     * Regresión: el centro se calculaba como `size / 2f` (12.5 en una matriz de 25), lo que
     * dejaba la simulación medio píxel descentrada y trataba distinto a la primera y la
     * última columna. El centro real de píxeles es 12.
     */
    @Test
    fun `el liquido es simetrico entre el borde izquierdo y el derecho`() {
        val heights = newHeights()
        settle(heights, pct = 50)
        assertEquals(heights[0], heights[SIZE - 1], 0.001f)
        assertEquals(heights[3], heights[SIZE - 4], 0.001f)
    }

    @Test
    fun `inclinar el telefono desnivela el liquido`() {
        val flat = newHeights().also { settle(it, pct = 50) }
        val tilted = newHeights().also { settle(it, pct = 50, tiltX = 6f) }

        val flatSpread = innerColumns(flat).let { it.max() - it.min() }
        val tiltedSpread = innerColumns(tilted).let { it.max() - it.min() }

        assertTrue("Inclinado debe haber más desnivel que en horizontal", tiltedSpread > flatSpread)
    }

    @Test
    fun `boca abajo el liquido se va al otro lado`() {
        val upright = newHeights().also { settle(it, pct = 50, tiltY = 0f) }
        val upsideDown = newHeights().also { settle(it, pct = 50, tiltY = -9.8f) }

        assertTrue(
            "Boca abajo el líquido debería desplazarse respecto a la posición normal",
            innerColumns(upsideDown).average() != innerColumns(upright).average()
        )
    }

    @Test
    fun `el movimiento es progresivo, no un salto instantaneo`() {
        val heights = newHeights()
        LiquidPhysics.step(heights, pct = 100, tiltX = 0f, tiltY = 0f, size = SIZE)
        // Tras un solo paso todavía no puede haber llegado arriba del todo.
        assertTrue(innerColumns(heights).average() > SIZE * 0.5f)
    }
}
