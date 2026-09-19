package dev.glyphrotator.app.glyph

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Si el texto que dibuja el toy **cabe** en la matriz de cada móvil.
 *
 * `GlyphTextMetrics` sabe centrar, pero no sabe decir que algo no entra: cuando el texto es más
 * ancho que la matriz, `centeredX` devuelve 0 por el `coerceAtLeast(0)` y el SDK pinta lo que
 * quepa, cortando el resto sin avisar. En una matriz de 25 nunca se notó porque todo cabía.
 *
 * En el Phone (4a) Pro la matriz es de 13, y esto deja de ser teórico: ahí el reloj es lo que se
 * ve casi todo el tiempo, porque ese móvil no tiene botón Glyph — solo avisos de pantalla
 * ambiente.
 */
class GlyphTextFitTest {

    private val phone3 = 25
    private val phone4aPro = 13

    private fun anchoDe(text: String): Int = GlyphTextMetrics.width(text)

    @Test
    fun `la hora completa cabe en el Phone 3`() {
        assertTrue(
            "\"12:34\" mide ${anchoDe("12:34")} y la matriz son $phone3",
            anchoDe("12:34") <= phone3
        )
        assertTrue(anchoDe("00:00") <= phone3)
    }

    /**
     * Deja constancia del problema: la hora en una sola línea **no cabe** en 13.
     *
     * No es un test que haya que "arreglar" cambiando el número — es la razón de que el reloj del
     * 4a Pro tenga que dibujarse en dos líneas.
     */
    @Test
    fun `la hora completa NO cabe en el 4a Pro`() {
        val ancho = anchoDe("12:34")
        assertTrue(
            "\"12:34\" mide $ancho: no entra en una matriz de $phone4aPro",
            ancho > phone4aPro
        )
    }

    @Test
    fun `pero la hora y los minutos por separado si caben`() {
        // Es la salida: dos líneas, horas arriba y minutos abajo, como cualquier reloj LED pequeño.
        for (texto in listOf("12", "34", "00", "59", "11")) {
            assertTrue(
                "\"$texto\" mide ${anchoDe(texto)} y no entra en $phone4aPro",
                anchoDe(texto) <= phone4aPro
            )
        }
    }

    @Test
    fun `el AM PM cabe en los dos`() {
        assertTrue(anchoDe("AM") <= phone4aPro)
        assertTrue(anchoDe("PM") <= phone4aPro)
    }

    @Test
    fun `el porcentaje de bateria cabe en los dos`() {
        // El caso ancho es el 100 %, que es justo el que más ocupa.
        for (texto in listOf("0", "56", "100")) {
            assertTrue(
                "\"$texto\" mide ${anchoDe(texto)} y no entra en $phone4aPro",
                anchoDe(texto) <= phone4aPro
            )
        }
    }
    // ---------------------------------------------------------------------------------
    // Alturas. Estaban clavadas (6, 9, 15) y en una matriz de 13 la 15 cae fuera de la
    // pantalla y la 9 deja el texto cortado por abajo.
    // ---------------------------------------------------------------------------------

    @Test
    fun `una linea centrada cabe entera en los dos`() {
        for (size in listOf(phone4aPro, phone3)) {
            val y = GlyphTextMetrics.centeredY(size)
            assertTrue("empieza dentro", y >= 0)
            assertTrue(
                "acaba dentro: y=$y + fuente en una matriz de $size",
                y + GlyphTextMetrics.FONT_HEIGHT <= size
            )
        }
    }

    @Test
    fun `las dos lineas del reloj caben en los dos`() {
        for (size in listOf(phone4aPro, phone3)) {
            val filas = GlyphTextMetrics.twoLineRows(size)
            assertTrue("en $size deberian caber dos lineas", filas != null)
            val (arriba, abajo) = filas!!
            assertTrue("la de arriba empieza dentro", arriba >= 0)
            assertTrue("no se pisan", abajo >= arriba + GlyphTextMetrics.FONT_HEIGHT)
            assertTrue(
                "la de abajo acaba dentro en una matriz de $size",
                abajo + GlyphTextMetrics.FONT_HEIGHT <= size
            )
        }
    }

    @Test
    fun `en una matriz diminuta se avisa en vez de dibujar fuera`() {
        // 8 filas no dan para dos lineas de 5. Devolver null deja que quien llame decida,
        // en vez de pintar algo que se sale.
        assertTrue(GlyphTextMetrics.twoLineRows(8) == null)
    }

}
