package dev.glyphrotator.app.glyph

import kotlin.math.roundToInt

/**
 * Prepara imágenes para la Glyph Matrix, que es de 25x25 y monocroma.
 *
 * Reducir un sprite a ese tamaño con el escalado normal de Android (interpolación
 * bilineal) difumina los bordes y el dibujo se vuelve una mancha gris. Aquí se hace en dos
 * pasos: se promedia por áreas (así ningún píxel del original se pierde, a diferencia del
 * "vecino más cercano") y después se estira el contraste para recuperar los bordes duros.
 *
 * Trabaja sobre arrays de luminancia para poder probarse sin Android.
 */
object MatrixImageProcessor {

    /**
     * Reduce [source] (cuadrada, de lado [sourceSize]) a un lado de [targetSize] promediando
     * el área que cae dentro de cada píxel de destino.
     */
    fun downscaleByArea(source: FloatArray, sourceSize: Int, targetSize: Int): FloatArray {
        require(source.size == sourceSize * sourceSize) { "El tamaño no cuadra con los datos" }
        if (sourceSize == targetSize) return source.copyOf()

        val result = FloatArray(targetSize * targetSize)
        val ratio = sourceSize.toFloat() / targetSize

        for (ty in 0 until targetSize) {
            val startY = (ty * ratio).toInt()
            val endY = ((ty + 1) * ratio).toInt().coerceAtMost(sourceSize).coerceAtLeast(startY + 1)
            for (tx in 0 until targetSize) {
                val startX = (tx * ratio).toInt()
                val endX = ((tx + 1) * ratio).toInt().coerceAtMost(sourceSize).coerceAtLeast(startX + 1)

                var sum = 0f
                var count = 0
                for (sy in startY until endY) {
                    for (sx in startX until endX) {
                        sum += source[sy * sourceSize + sx]
                        count++
                    }
                }
                result[ty * targetSize + tx] = if (count > 0) sum / count else 0f
            }
        }
        return result
    }

    /**
     * Estira el contraste para que el píxel más oscuro quede a 0 y el más claro a 1. Es lo
     * que devuelve la nitidez tras promediar: si no, un sprite reducido se queda en un rango
     * estrecho de grises y en la Matrix se ve plano.
     *
     * Si la imagen es casi plana (sin apenas diferencia entre claro y oscuro) se deja igual,
     * porque estirarla solo amplificaría ruido.
     */
    fun stretchContrast(pixels: FloatArray, minimumRange: Float = 0.05f): FloatArray {
        if (pixels.isEmpty()) return pixels

        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (value in pixels) {
            if (value < min) min = value
            if (value > max) max = value
        }
        return stretchContrast(pixels, min, max, minimumRange)
    }

    /**
     * Igual, pero con un rango dado desde fuera. En un GIF hay que medir el rango sobre la
     * animación entera: si cada frame usa el suyo, el brillo salta de un frame a otro y la
     * animación parece vibrar.
     */
    fun stretchContrast(
        pixels: FloatArray,
        min: Float,
        max: Float,
        minimumRange: Float = 0.05f,
    ): FloatArray {
        if (pixels.isEmpty()) return pixels
        val range = max - min
        if (range < minimumRange) return pixels.copyOf()
        return FloatArray(pixels.size) { ((pixels[it] - min) / range).coerceIn(0f, 1f) }
    }

    /**
     * Realza el contraste alrededor del punto medio: acerca los grises claros al blanco y
     * los oscuros al negro, sin llegar a binarizar (la Matrix admite niveles intermedios y
     * perderlos del todo haría el dibujo aún más pobre).
     */
    fun sharpen(pixels: FloatArray, strength: Float = 1.6f): FloatArray = FloatArray(pixels.size) {
        val centered = (pixels[it] - 0.5f) * strength + 0.5f
        centered.coerceIn(0f, 1f)
    }

    /**
     * Cómo se convierte un dibujo en píxeles encendidos.
     *
     * [LUMINANCE] es lo de siempre y lo que usa el carrusel: cada píxel se enciende según su
     * brillo. Funciona con los diseños hechos a mano, que ya vienen pensados para la Matrix.
     *
     * [OUTLINE] es para los sprites de Pokémon, que son dibujos normales con mucho detalle
     * que a 25x25 no cabe. Ahí el brillo falla de dos maneras opuestas: un Pokémon oscuro
     * (Gyarados) casi no enciende y llega roto en trozos, y uno claro y uniforme (Psyduck,
     * Ninetales) enciende entero y llega como una mancha. Lo que sí identifica al dibujo a
     * ese tamaño es su perfil.
     *
     * [SILHOUETTE] va por transparencia: el dibujo entero encendido, el color solo modulando
     * dentro. Recupera las partes oscuras que el brillo apaga, a costa de perder el interior.
     *
     * [SILHOUETTE_DETAIL] es la silueta con las divisiones internas talladas en oscuro: el
     * dibujo se ve encendido y normal, y las separaciones (las colas de Ninetales) salen como
     * surcos apagados. Es lo contrario del contorno, que deja el cuerpo oscuro y por eso se
     * ve como un negativo.
     *
     * [SILHOUETTE_SOFT] es lo mismo pero apenas marcado, para cuando la silueta lisa sale
     * como una mancha blanca y las divisiones a tope tallan cosas que no tocan (la cara de
     * Bellsprout, donde los ojos quedaban como dos agujeros).
     *
     * Ninguno gana siempre, y por eso se elige por sprite en vez de imponer uno a todos.
     */
    enum class RenderMode { LUMINANCE, SILHOUETTE, SILHOUETTE_SOFT, SILHOUETTE_DETAIL, OUTLINE }

    /**
     * Valores del dibujo listos para reducir, según el modo. [square] es el recorte cuadrado
     * en ARGB, de lado [size], con 0 en lo que caiga fuera de la imagen.
     */
    fun values(square: IntArray, size: Int, mode: RenderMode): FloatArray = when (mode) {
        RenderMode.LUMINANCE -> FloatArray(square.size) { luminance(square[it]) }
        RenderMode.SILHOUETTE -> FloatArray(square.size) { silhouette(square[it]) }
        RenderMode.OUTLINE -> outline(square, size)
        RenderMode.SILHOUETTE_DETAIL -> silhouetteWithGrooves(square, size)
        RenderMode.SILHOUETTE_SOFT ->
            silhouetteWithGrooves(square, size, threshold = 0.45f, depth = 0.55f)
    }

    /**
     * Silueta con las divisiones internas talladas en oscuro.
     *
     * Solo cuentan los saltos de color por encima de [threshold]: si se hiciera con
     * cualquier diferencia, el sombreado suave del dibujo carcomería la figura entera y no
     * quedaría nada reconocible. [depth] es cuánto se apaga el surco.
     */
    fun silhouetteWithGrooves(
        square: IntArray,
        size: Int,
        // Probado sobre los sprites: por encima de 0.25 apenas se marcan las divisiones, y
        // por debajo de 0.15 el sombreado suave empieza a comerse los bordes del dibujo.
        threshold: Float = 0.20f,
        depth: Float = 1f,
    ): FloatArray {
        val alpha = FloatArray(square.size) { ((square[it] ushr 24) and 0xFF) / 255f }
        val luma = FloatArray(square.size) { opaqueLuminance(square[it]) }
        val result = FloatArray(square.size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                var jump = 0f
                if (alpha[index] > 0.5f) {
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx !in 0 until size || ny !in 0 until size) continue
                            val neighbour = ny * size + nx
                            if (alpha[neighbour] <= 0.5f) continue
                            val difference = kotlin.math.abs(luma[index] - luma[neighbour])
                            if (difference > jump) jump = difference
                        }
                    }
                }
                val carve = (((jump - threshold) / (1f - threshold)).coerceIn(0f, 1f)) * depth
                result[index] = silhouette(square[index]) * (1f - carve)
            }
        }
        return result
    }

    /** Manda la silueta; el brillo del color solo modula dentro de ella. */
    fun silhouette(argb: Int, innerDetail: Float = 0.45f): Float {
        val alpha = ((argb ushr 24) and 0xFF) / 255f
        if (alpha == 0f) return 0f
        return alpha * ((1f - innerDetail) + innerDetail * opaqueLuminance(argb))
    }

    /**
     * Contorno del dibujo: el borde de la silueta y los cambios fuertes de color dentro,
     * sobre el cuerpo encendido a media luz ([bodyLevel]).
     *
     * El cuerpo tenue importa: solo con las líneas el dibujo queda flotando y no se entiende
     * qué es masa y qué es hueco.
     */
    fun outline(
        square: IntArray,
        size: Int,
        bodyLevel: Float = 0.30f,
        innerEdgeGain: Float = 1f,
    ): FloatArray {
        val alpha = FloatArray(square.size) { ((square[it] ushr 24) and 0xFF) / 255f }
        val luma = FloatArray(square.size) { opaqueLuminance(square[it]) }
        val result = FloatArray(square.size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                var strongest = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        val inside = nx in 0 until size && ny in 0 until size
                        val neighbour = if (inside) ny * size + nx else -1
                        val neighbourAlpha = if (inside) alpha[neighbour] else 0f
                        val alphaJump = kotlin.math.abs(alpha[index] - neighbourAlpha)
                        if (alphaJump > strongest) strongest = alphaJump
                        // El contraste interno solo cuenta entre dos píxeles del dibujo: si
                        // no, el salto contra el fondo transparente lo dominaría todo.
                        if (inside && alpha[index] > 0.5f && neighbourAlpha > 0.5f) {
                            val lumaJump =
                                (kotlin.math.abs(luma[index] - luma[neighbour]) * innerEdgeGain)
                                    .coerceAtMost(1f)
                            if (lumaJump > strongest) strongest = lumaJump
                        }
                    }
                }
                // Multiplicar por el alfa deja el contorno dentro de la figura. Si no, el
                // salto contra el fondo encendería también el píxel transparente de al lado
                // y el dibujo saldría engordado por todo el perímetro.
                result[index] = alpha[index] * maxOf(strongest, bodyLevel)
            }
        }
        return result
    }

    /** Cadena completa: promediar, estirar contraste y realzar. */
    fun prepare(source: FloatArray, sourceSize: Int, targetSize: Int): FloatArray =
        sharpen(stretchContrast(downscaleByArea(source, sourceSize, targetSize)))

    /** Recuadro que encierra el dibujo, ignorando el margen vacío alrededor. */
    data class ContentBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    private fun boundsWhereAlphaVisible(pixels: IntArray, width: Int, height: Int): ContentBounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((pixels[y * width + x] ushr 24) and 0xFF > ALPHA_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0 || bottom < 0) null else ContentBounds(left, top, right, bottom)
    }

    /**
     * Recuadro que cubre a los dos. Sirve para encuadrar un GIF con un único recorte: si
     * cada frame usa el suyo, el dibujo se recoloca en cuanto el personaje mueve algo y la
     * animación entera parece vibrar.
     */
    fun union(a: ContentBounds?, b: ContentBounds?): ContentBounds? {
        if (a == null) return b
        if (b == null) return a
        return ContentBounds(
            minOf(a.left, b.left),
            minOf(a.top, b.top),
            maxOf(a.right, b.right),
            maxOf(a.bottom, b.bottom),
        )
    }

    /**
     * Busca el recuadro que ocupa realmente el dibujo dentro de [pixels] (ARGB).
     *
     * Los sprites suelen venir con mucho margen transparente alrededor; si se escala el
     * lienzo entero, el dibujo llega a la Matrix más pequeño de lo necesario y pierde
     * detalle. Devuelve null si la imagen está vacía del todo.
     *
     * En [RenderMode.OUTLINE] el recuadro va por transparencia: el contorno de un sprite es
     * negro y el criterio de brillo lo dejaría fuera, recortando el dibujo por los bordes.
     */
    fun findContentBounds(
        pixels: IntArray,
        width: Int,
        height: Int,
        mode: RenderMode = RenderMode.LUMINANCE,
    ): ContentBounds? {
        if (mode == RenderMode.OUTLINE) {
            return boundsWhereAlphaVisible(pixels, width, height)
        }
        var left = width
        var top = height
        var right = -1
        var bottom = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                val alpha = (argb ushr 24) and 0xFF
                if (alpha > ALPHA_THRESHOLD && luminance(argb) > LUMINANCE_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }

        return if (right < 0 || bottom < 0) null else ContentBounds(left, top, right, bottom)
    }

    private const val ALPHA_THRESHOLD = 16
    private const val LUMINANCE_THRESHOLD = 0.04f

    /** Luminancia percibida (0..1) de un color ARGB. */
    fun luminance(argb: Int): Float {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha == 0) return 0f
        // Lo transparente cuenta como apagado, proporcionalmente al alfa.
        return opaqueLuminance(argb) * (alpha / 255f)
    }

    /** Luminancia del color ignorando la transparencia. */
    private fun opaqueLuminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // Pesos estándar de luminancia percibida (Rec. 601).
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }


    fun toGrayArgb(value: Float): Int {
        val level = (value.coerceIn(0f, 1f) * 255).roundToInt()
        return (0xFF shl 24) or (level shl 16) or (level shl 8) or level
    }
}
