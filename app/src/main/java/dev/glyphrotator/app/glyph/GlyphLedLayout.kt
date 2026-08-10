package dev.glyphrotator.app.glyph

import android.graphics.Bitmap

/**
 * Dónde cae cada uno de los 489 LEDs de la Matrix dentro de la cuadrícula de 25x25.
 *
 * Los LEDs están en círculo, así que cada fila tiene una anchura distinta y van numerados
 * seguidos de arriba abajo. Estas anchuras no son inventadas: son las únicas que suman
 * exactamente 489 para un círculo de radio 12,40 en una rejilla de 25, y coinciden con el
 * recuento de los diseños exportados desde Glyph Museum.
 *
 * Hace falta para **pintar esos diseños por el mismo camino que el resto de la app**. Mandarlos
 * con `setAppMatrixFrame(int[])` funciona, pero esa ruta no pasa por `GlyphMatrixObject`, que es
 * donde se aplica el brillo: los mismos valores salían notablemente más apagados que todo lo
 * demás, y por mucho que se subieran los números seguían viéndose grises porque el techo lo
 * ponía la ruta, no el valor.
 */
object GlyphLedLayout {

    /** LEDs de cada fila, de arriba abajo. Suman 489. */
    private val ROW_WIDTHS = intArrayOf(
        7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25,
        25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7,
    )

    const val LED_COUNT = 489
    const val MATRIX_SIZE = 25

    /**
     * Convierte un frame en crudo (un valor por LED) a un bitmap cuadrado en gris.
     *
     * Lo que cae fuera del círculo queda negro: son posiciones que no existen físicamente.
     */
    fun toBitmap(leds: IntArray, size: Int = MATRIX_SIZE): Bitmap {
        val pixels = IntArray(size * size)
        var index = 0

        for (y in 0 until size) {
            val width = ROW_WIDTHS.getOrNull(y) ?: 0
            val startX = (size - width) / 2
            for (offset in 0 until width) {
                val value = leds.getOrNull(index) ?: 0
                index++
                val level = value.coerceIn(0, 255)
                val x = startX + offset
                if (x in 0 until size) {
                    pixels[y * size + x] = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
                }
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
