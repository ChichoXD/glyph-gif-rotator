package dev.glyphrotator.app.glyph

import org.junit.Test

/**
 * Imprime el vinilo generado en texto, para poder **verlo** sin un móvil delante.
 *
 * No es una comprobación: no falla nunca. Existe porque no hay ningún emulador de la Glyph
 * Matrix —el kit oficial de Nothing exige hardware real— y sin esto la única forma de saber si
 * el disco se lee como un disco a trece píxeles era instalarlo en un 4a Pro y mirarlo.
 *
 * Se ejecuta con:
 * `./gradlew testDebugUnitTest --tests "*VinylPreviewTest*" -i`
 */
class VinylPreviewTest {

    /** Cada nivel de brillo a un carácter, del más apagado al más encendido. */
    private fun glyph(value: Int): String = when {
        value == 0 -> "  "
        value < 100 -> " ."
        value < 200 -> " +"
        value < 240 -> " o"
        else -> " #"
    }

    private fun dibuja(size: Int, frameIndex: Int) {
        println("--- ${size}x$size, frame $frameIndex ---")
        for (row in 0 until size) {
            val linea = (0 until size).joinToString("") { col ->
                glyph(VinylBeatAnimation.pixelValue(row, col, frameIndex, size))
            }
            println(linea)
        }
        println()
    }

    @Test
    fun `dibuja el vinilo del 4a Pro`() {
        println()
        println("=== VINILO GENERADO — Nothing Phone (4a) Pro, matriz de 13x13 ===")
        println("    '#' etiqueta/borde   'o' borde   '+' destello que gira   '.' disco")
        println()
        // Media vuelta: con estos cuatro se ve si el destello se mueve de verdad.
        for (frame in listOf(0, 2, 4, 6)) dibuja(13, frame)
    }

    @Test
    fun `dibuja el mismo vinilo a 25 para comparar`() {
        println()
        println("=== EL MISMO ALGORITMO A 25x25, solo para comparar ===")
        println("(el Phone (3) NO usa esto: usa su arte original dibujado a mano)")
        println()
        dibuja(25, 0)
    }
}
