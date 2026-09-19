package dev.glyphrotator.app.glyph

/**
 * Estima el ancho del texto de la fuente bitmap por defecto del SDK (5px de alto,
 * ':' = 1px, '1' = 3px, el resto = 4px, con 1px de espacio entre caracteres) para poder
 * centrarlo horizontalmente con `setPosition`, ya que el SDK no ofrece alineación
 * automática.
 */
object GlyphTextMetrics {

    fun centeredX(text: String, matrixSize: Int): Int =
        ((matrixSize - width(text)) / 2).coerceAtLeast(0)

    /**
     * Cuánto ocupa un texto, en píxeles de ancho.
     *
     * Existe porque `centeredX` **no sabe decir que algo no cabe**: cuando el texto es más ancho
     * que la matriz devuelve 0 por el `coerceAtLeast`, y el SDK pinta lo que quepa cortando el
     * resto sin avisar. Con una matriz de 25 eso nunca se notó porque todo entraba; con la de 13
     * del Phone (4a) Pro, la hora completa ("12:34" mide 20) se corta por la mitad.
     *
     * Quien vaya a dibujar texto en la Matrix debería preguntar esto primero.
     */
    fun width(text: String): Int {
        if (text.isEmpty()) return 0
        var width = 0
        text.forEachIndexed { index, c ->
            width += charWidth(c)
            if (index < text.length - 1) width += 1
        }
        return width
    }

    /** Si un texto entra entero en una matriz de ese lado. */
    fun fitsIn(text: String, matrixSize: Int): Boolean = width(text) <= matrixSize

    /** Alto de la fuente bitmap del SDK. */
    const val FONT_HEIGHT = 5

    /**
     * La fila donde empezar a dibujar para que una línea de texto quede centrada en vertical.
     *
     * Las alturas estaban escritas a mano —el reloj en la 6, el AM/PM en la 15, la batería en la
     * 9— y funcionaban porque solo existía una matriz. En la de 13 del Phone (4a) Pro, la 15 está
     * **fuera de la pantalla** y la 9 deja el número cortado por abajo.
     *
     * Y estaban escritas **dos veces**: una en el toy del botón y otra en el canal de app, que
     * dibujan el mismo reloj y la misma batería por caminos distintos. Por eso viven aquí ahora.
     */
    fun centeredY(matrixSize: Int): Int =
        ((matrixSize - FONT_HEIGHT) / 2).coerceAtLeast(0)

    /**
     * Las dos filas de un texto en dos líneas (con una fila de hueco), centradas en vertical.
     *
     * Devuelve `null` si no caben: quien llame decide qué hacer entonces, en vez de dibujar algo
     * que se sale.
     */
    fun twoLineRows(matrixSize: Int): Pair<Int, Int>? {
        val total = FONT_HEIGHT * 2 + 1
        if (total > matrixSize) return null
        val first = (matrixSize - total) / 2
        return first to (first + FONT_HEIGHT + 1)
    }

    private fun charWidth(c: Char): Int = when (c) {
        ':', ' ' -> 1
        '1' -> 3
        else -> 4
    }
}
