package dev.glyphrotator.app.glyph

/**
 * Estima el ancho del texto de la fuente bitmap por defecto del SDK (5px de alto,
 * ':' = 1px, '1' = 3px, el resto = 4px, con 1px de espacio entre caracteres) para poder
 * centrarlo horizontalmente con `setPosition`, ya que el SDK no ofrece alineación
 * automática.
 */
object GlyphTextMetrics {

    fun centeredX(text: String, matrixSize: Int): Int {
        if (text.isEmpty()) return matrixSize / 2
        var width = 0
        text.forEachIndexed { index, c ->
            width += charWidth(c)
            if (index < text.length - 1) width += 1
        }
        return ((matrixSize - width) / 2).coerceAtLeast(0)
    }

    private fun charWidth(c: Char): Int = when (c) {
        ':', ' ' -> 1
        '1' -> 3
        else -> 4
    }
}
