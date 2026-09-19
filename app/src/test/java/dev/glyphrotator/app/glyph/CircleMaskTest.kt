package dev.glyphrotator.app.glyph

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Verifica, de forma independiente, la fórmula del círculo de LEDs que sacó la sesión del
 * emulador del SDK de Nothing.
 *
 * Contexto: `GlyphLedLayout.ROW_WIDTHS` es una tabla escrita a mano contando diseños exportados
 * de Glyph Museum — solo vale para el Phone (3). Para el (4a) Pro no había equivalente, y este
 * proyecto lo dio por irresoluble ("`circleSpanAtRow` es código muerto en el SDK, no expone el
 * radio; deducir uno que cuadre con 137 sería inventarlo").
 *
 * Resultó que la misma fórmula está **viva** en `GlyphMatrixUtils.generateMatrixProgress()`, y
 * ahí sí se ve con qué parámetros se llama:
 *
 *     cx = cy = R = (size - 1) / 2
 *     Rtol2   = (R + 0.5)^2
 *
 * Si eso es cierto, aplicarla a 25 tiene que reproducir `ROW_WIDTHS` **fila a fila** — una tabla
 * obtenida por un camino completamente distinto. Ese es el contraste que convierte la fórmula en
 * creíble, y es justo lo que comprueba este test.
 */
class CircleMaskTest {

    /** La fórmula del SDK, tal cual. */
    private fun anchosPorFila(size: Int): IntArray {
        val r = (size - 1) / 2.0
        val rtol2 = (r + 0.5) * (r + 0.5)
        return IntArray(size) { y ->
            val dy = y - r
            val dy2 = dy * dy
            if (dy2 > rtol2) {
                0
            } else {
                val maxDx = sqrt(max(0.0, rtol2 - dy2))
                val xLeft = ceil(r - maxDx).toInt().coerceAtLeast(0)
                val xRight = floor(r + maxDx).toInt().coerceAtMost(size - 1)
                if (xRight < xLeft) 0 else xRight - xLeft + 1
            }
        }
    }

    /** La tabla escrita a mano que ya vive en el proyecto, contando diseños de Glyph Museum. */
    private val rowWidthsPhone3 = intArrayOf(
        7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25,
        25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7,
    )

    @Test
    fun `la formula del SDK reproduce la tabla del Phone 3 fila a fila`() {
        // Es la prueba de fuego: dos caminos independientes —contar diseños exportados y aplicar
        // la fórmula del SDK— tienen que dar exactamente lo mismo. Si no coinciden, la fórmula
        // no es la que usa el hardware y no hay que usarla para el 4a Pro.
        assertArrayEquals(rowWidthsPhone3, anchosPorFila(25))
    }

    @Test
    fun `y da los 489 LEDs conocidos del Phone 3`() {
        assertEquals(489, anchosPorFila(25).sum())
    }

    @Test
    fun `en 13x13 da los 137 LEDs del 4a Pro`() {
        // El número no se ajustó a mano: sale de aplicar la misma fórmula con el mismo radio.
        // Que coincida con el recuento publicado del 4a Pro es lo que la respalda.
        assertEquals(137, anchosPorFila(13).sum())
    }

    @Test
    fun `el circulo es simetrico arriba-abajo`() {
        for (size in listOf(13, 25)) {
            val anchos = anchosPorFila(size)
            for (y in 0 until size) {
                assertEquals(
                    "fila $y contra ${size - 1 - y} en $size×$size",
                    anchos[y],
                    anchos[size - 1 - y]
                )
            }
        }
    }

    @Test
    fun `ninguna fila se sale de la matriz`() {
        for (size in listOf(9, 13, 17, 25, 33)) {
            for (ancho in anchosPorFila(size)) {
                assertEquals(true, ancho in 0..size)
            }
        }
    }
}
