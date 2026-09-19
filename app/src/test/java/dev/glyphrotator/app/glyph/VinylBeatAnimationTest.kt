package dev.glyphrotator.app.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El vinilo generado por fórmula, que es el que se usa en matrices que no son la del Phone (3).
 *
 * Se prueba la geometría, no el dibujo: `Bitmap` no existe en los tests unitarios de este
 * proyecto. Por eso [VinylBeatAnimation.pixelValue] está separada del bucle que pinta.
 *
 * Hace falta porque el arte original son 8 frames dibujados a mano para una rejilla de 25×25:
 * en la matriz de 13×13 del Phone (4a) Pro no encajan, y no es algo que se arregle cambiando
 * un número.
 */
class VinylBeatAnimationTest {

    private val phone4aPro = 13
    private val phone3 = 25

    private fun frame(frameIndex: Int, size: Int): List<Int> =
        (0 until size).flatMap { row ->
            (0 until size).map { col -> VinylBeatAnimation.pixelValue(row, col, frameIndex, size) }
        }

    @Test
    fun `las esquinas quedan apagadas porque no hay LED ahi`() {
        // La matriz es un círculo dentro de un cuadrado: las cuatro esquinas no existen
        // físicamente. Encenderlas sería mandar luz a un sitio donde no hay diodo.
        val size = phone4aPro
        assertEquals(0, VinylBeatAnimation.pixelValue(0, 0, 0, size))
        assertEquals(0, VinylBeatAnimation.pixelValue(0, size - 1, 0, size))
        assertEquals(0, VinylBeatAnimation.pixelValue(size - 1, 0, 0, size))
        assertEquals(0, VinylBeatAnimation.pixelValue(size - 1, size - 1, 0, size))
    }

    @Test
    fun `el centro es la etiqueta y va a tope de brillo`() {
        val center = (phone4aPro - 1) / 2
        assertEquals(255, VinylBeatAnimation.pixelValue(center, center, 0, phone4aPro))
    }

    @Test
    fun `el disco esta centrado, no corrido medio pixel`() {
        // Con lado impar el centro cae en un píxel exacto. Si se usara size/2.0 en vez de
        // (size-1)/2.0, el disco quedaría descentrado — y a 13 píxeles eso se ve.
        val center = (phone4aPro - 1) / 2
        val izquierda = VinylBeatAnimation.pixelValue(center, 0, 0, phone4aPro)
        val derecha = VinylBeatAnimation.pixelValue(center, phone4aPro - 1, 0, phone4aPro)
        val arriba = VinylBeatAnimation.pixelValue(0, center, 0, phone4aPro)
        val abajo = VinylBeatAnimation.pixelValue(phone4aPro - 1, center, 0, phone4aPro)

        assertEquals("los cuatro extremos del diámetro deben tratarse igual", izquierda, derecha)
        assertEquals(arriba, abajo)
        assertTrue("y estar encendidos: son el borde del disco", izquierda > 0)
    }

    @Test
    fun `el destello gira, no son ocho frames iguales`() {
        // Es lo único que distingue "suena música" de "hay un círculo pintado". Si los frames
        // salieran idénticos, la animación existiría pero no se notaría.
        val primero = frame(0, phone4aPro)
        val distintos = (1 until 8).count { frame(it, phone4aPro) != primero }
        assertTrue("al menos algún frame tiene que diferir del primero, difieren $distintos", distintos > 0)
    }

    @Test
    fun `media vuelta cambia el dibujo`() {
        // El frame 4 de 8 es media vuelta: el destello tiene que estar en el lado contrario.
        assertNotEquals(frame(0, phone4aPro), frame(4, phone4aPro))
    }

    @Test
    fun `funciona igual en cualquier tamano de matriz`() {
        // No debe haber nada atado a 13 ni a 25: si Nothing saca un tercer modelo, esto sigue.
        for (size in listOf(9, phone4aPro, 17, phone3, 33)) {
            val encendidos = frame(0, size).count { it > 0 }
            assertTrue("en $size×$size algo tiene que encenderse", encendidos > 0)
            assertTrue("pero no el cuadrado entero", encendidos < size * size)
        }
    }

    @Test
    fun `el brillo nunca se sale del rango`() {
        for (size in listOf(phone4aPro, phone3)) {
            for (f in 0 until 8) {
                for (value in frame(f, size)) {
                    assertTrue("brillo fuera de rango: $value", value in 0..255)
                }
            }
        }
    }
}
