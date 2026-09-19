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

    /**
     * LEDs de cada fila, de arriba abajo, para una matriz de lado [size].
     *
     * Esto era una tabla fija de 25 números escrita a mano contando diseños exportados de Glyph
     * Museum, y por eso el Phone (4a) Pro se quedaba fuera: su círculo tiene 137 LEDs en 13×13 y
     * no había tabla equivalente. Se dio por irresoluble más de una vez —"el SDK no publica
     * ninguna máscara, deducir un radio que cuadre con 137 sería inventarlo"—.
     *
     * Resultó que el SDK **sí** trae la fórmula, viva, en `GlyphMatrixUtils.generateMatrixProgress()`:
     *
     *     cx = cy = R = (size - 1) / 2      Rtol2 = (R + 0.5)^2
     *     por fila:  maxDx = sqrt(Rtol2 - dy^2),  de ceil(cx - maxDx) a floor(cx + maxDx)
     *
     * Aplicada a 25 reproduce **exactamente** la tabla que estaba escrita a mano, fila a fila —dos
     * caminos independientes dando los mismos 25 números—, y a 13 da 137. Ver `CircleMaskTest`.
     *
     * Lo que sigue **NO CONFIRMADO** es que el hardware ponga los diodos justo ahí: el SDK nunca
     * declara una máscara explícita. Para el Phone (3) hay el contraste de Glyph Museum; para el
     * 4a Pro, ninguno hasta que haya un móvil delante. Es una hipótesis muy bien apoyada, no un
     * hecho verificado.
     */
    fun rowWidths(size: Int): IntArray {
        val r = (size - 1) / 2.0
        val rtol2 = (r + 0.5) * (r + 0.5)
        return IntArray(size) { y ->
            val dy2 = (y - r) * (y - r)
            if (dy2 > rtol2) {
                0
            } else {
                val maxDx = kotlin.math.sqrt(kotlin.math.max(0.0, rtol2 - dy2))
                val xLeft = kotlin.math.ceil(r - maxDx).toInt().coerceAtLeast(0)
                val xRight = kotlin.math.floor(r + maxDx).toInt().coerceAtMost(size - 1)
                if (xRight < xLeft) 0 else xRight - xLeft + 1
            }
        }
    }

    /** Cuántos LEDs hay de verdad dentro del círculo de una matriz de ese lado. */
    fun ledCount(size: Int): Int = rowWidths(size).sum()

    /** El Phone (3), que es donde nació esto. Se conservan por compatibilidad. */
    const val LED_COUNT = 489
    const val MATRIX_SIZE = 25

    /**
     * Convierte un frame en crudo (un valor por LED) a un bitmap cuadrado en gris.
     *
     * Lo que cae fuera del círculo queda negro: son posiciones que no existen físicamente.
     */
    fun toBitmap(leds: IntArray, size: Int = MATRIX_SIZE): Bitmap {
        val pixels = IntArray(size * size)
        val rowWidths = rowWidths(size)
        var index = 0

        for (y in 0 until size) {
            val width = rowWidths.getOrNull(y) ?: 0
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
