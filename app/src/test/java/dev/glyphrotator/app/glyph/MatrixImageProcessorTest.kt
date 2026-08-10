package dev.glyphrotator.app.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixImageProcessorTest {

    // =====================================================================================
    // Reducción por áreas
    // =====================================================================================

    @Test
    fun `reducir a la mitad promedia cada bloque`() {
        // 2x2 -> 1x1: la media de 0, 1, 0 y 1 es 0.5
        val source = floatArrayOf(0f, 1f, 0f, 1f)
        val result = MatrixImageProcessor.downscaleByArea(source, sourceSize = 2, targetSize = 1)
        assertEquals(1, result.size)
        assertEquals(0.5f, result[0], 0.001f)
    }

    @Test
    fun `si el tamano ya coincide no cambia nada`() {
        val source = floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f)
        val result = MatrixImageProcessor.downscaleByArea(source, 2, 2)
        assertEquals(source.toList(), result.toList())
    }

    /**
     * Con "vecino más cercano" una línea fina puede caer entre dos píxeles de destino y
     * desaparecer del todo. Promediando siempre deja rastro.
     */
    @Test
    fun `una linea fina no desaparece al reducir`() {
        val size = 8
        val source = FloatArray(size * size)
        for (x in 0 until size) source[3 * size + x] = 1f // una fila encendida

        val result = MatrixImageProcessor.downscaleByArea(source, size, 4)

        assertTrue("La línea se perdió por completo", result.any { it > 0f })
    }

    @Test
    fun `una imagen uniforme sigue uniforme`() {
        val source = FloatArray(16) { 0.7f }
        val result = MatrixImageProcessor.downscaleByArea(source, 4, 2)
        result.forEach { assertEquals(0.7f, it, 0.001f) }
    }

    @Test
    fun `el resultado tiene el tamano pedido`() {
        val source = FloatArray(56 * 56) { 0.5f }
        val result = MatrixImageProcessor.downscaleByArea(source, 56, 25)
        assertEquals(25 * 25, result.size)
    }

    // =====================================================================================
    // Contraste
    // =====================================================================================

    /**
     * Regresión del motivo de todo esto: tras promediar, un sprite se queda en un rango
     * estrecho de grises y en la Matrix se ve plano y sin detalle.
     */
    @Test
    fun `un rango estrecho de grises se estira a blanco y negro`() {
        val source = floatArrayOf(0.40f, 0.45f, 0.50f, 0.55f)
        val result = MatrixImageProcessor.stretchContrast(source)

        assertEquals(0f, result.min(), 0.001f)
        assertEquals(1f, result.max(), 0.001f)
    }

    @Test
    fun `el orden relativo de los pixeles se respeta`() {
        val source = floatArrayOf(0.3f, 0.5f, 0.4f)
        val result = MatrixImageProcessor.stretchContrast(source)
        assertTrue(result[0] < result[2])
        assertTrue(result[2] < result[1])
    }

    @Test
    fun `una imagen casi plana se deja en paz para no amplificar ruido`() {
        val source = floatArrayOf(0.50f, 0.51f, 0.50f, 0.51f)
        val result = MatrixImageProcessor.stretchContrast(source)
        assertEquals(source.toList(), result.toList())
    }

    @Test
    fun `el realce separa claros de oscuros`() {
        val source = floatArrayOf(0.4f, 0.6f)
        val result = MatrixImageProcessor.sharpen(source)
        assertTrue("El oscuro debe oscurecerse", result[0] < source[0])
        assertTrue("El claro debe aclararse", result[1] > source[1])
    }

    @Test
    fun `el realce nunca se sale de rango`() {
        val source = floatArrayOf(0f, 0.5f, 1f)
        MatrixImageProcessor.sharpen(source, strength = 5f).forEach {
            assertTrue("Valor fuera de 0..1: $it", it in 0f..1f)
        }
    }

    // =====================================================================================
    // Cadena completa
    // =====================================================================================

    @Test
    fun `un sprite de 56 acaba en 25 con contraste completo`() {
        val size = 56
        val source = FloatArray(size * size) { index ->
            // Un patrón de damero, como el pixel art
            if (((index / size) / 4 + (index % size) / 4) % 2 == 0) 0.45f else 0.55f
        }

        val result = MatrixImageProcessor.prepare(source, size, 25)

        assertEquals(25 * 25, result.size)
        assertTrue("Debería haber recuperado contraste", result.max() - result.min() > 0.5f)
    }

    @Test
    fun `la luminancia respeta el brillo percibido`() {
        val white = MatrixImageProcessor.luminance(0xFFFFFFFF.toInt())
        val black = MatrixImageProcessor.luminance(0xFF000000.toInt())
        val green = MatrixImageProcessor.luminance(0xFF00FF00.toInt())
        val blue = MatrixImageProcessor.luminance(0xFF0000FF.toInt())

        assertEquals(1f, white, 0.01f)
        assertEquals(0f, black, 0.01f)
        assertTrue("El verde se percibe más claro que el azul", green > blue)
    }

    @Test
    fun `lo transparente cuenta como apagado`() {
        assertEquals(0f, MatrixImageProcessor.luminance(0x00FFFFFF), 0.001f)
    }

    @Test
    fun `el gris generado es realmente gris`() {
        val argb = MatrixImageProcessor.toGrayArgb(0.5f)
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        assertEquals(r, g)
        assertEquals(g, b)
        assertEquals(128, r)
    }
}

class ContentBoundsTest {

    private fun argb(alpha: Int, gray: Int) = (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray

    @Test
    fun `encuentra el dibujo ignorando el margen transparente`() {
        val size = 10
        val pixels = IntArray(size * size) { argb(0, 0) }
        // Un cuadrado opaco de 3x3 en (4,4)
        for (y in 4..6) for (x in 4..6) pixels[y * size + x] = argb(255, 200)

        val bounds = MatrixImageProcessor.findContentBounds(pixels, size, size)!!
        assertEquals(4, bounds.left)
        assertEquals(4, bounds.top)
        assertEquals(6, bounds.right)
        assertEquals(6, bounds.bottom)
        assertEquals(3, bounds.width)
        assertEquals(3, bounds.height)
    }

    @Test
    fun `una imagen totalmente vacia no tiene contenido`() {
        val pixels = IntArray(25) { argb(0, 0) }
        assertEquals(null, MatrixImageProcessor.findContentBounds(pixels, 5, 5))
    }

    @Test
    fun `una imagen llena ocupa todo el recuadro`() {
        val pixels = IntArray(16) { argb(255, 255) }
        val bounds = MatrixImageProcessor.findContentBounds(pixels, 4, 4)!!
        assertEquals(0, bounds.left)
        assertEquals(3, bounds.right)
        assertEquals(4, bounds.width)
    }

    @Test
    fun `los pixeles casi transparentes no cuentan como contenido`() {
        val size = 4
        val pixels = IntArray(size * size) { argb(8, 255) } // alfa muy bajo
        assertEquals(null, MatrixImageProcessor.findContentBounds(pixels, size, size))
    }

    @Test
    fun `el negro opaco sobre transparente tampoco cuenta`() {
        // Un sprite recortado deja negro puro donde no hay dibujo: no debe ensanchar el recuadro.
        val size = 5
        val pixels = IntArray(size * size) { argb(255, 0) }
        pixels[2 * size + 2] = argb(255, 255)

        val bounds = MatrixImageProcessor.findContentBounds(pixels, size, size)!!
        assertEquals(2, bounds.left)
        assertEquals(2, bounds.right)
    }

    @Test
    fun `en modo contorno el borde negro del sprite entra en el recuadro`() {
        // El contorno de un sprite es negro y opaco: con el criterio de brillo se quedaría
        // fuera y el dibujo llegaría recortado por los bordes.
        val size = 5
        val pixels = IntArray(size * size) { argb(0, 0) }
        pixels[1 * size + 1] = argb(255, 0)
        pixels[3 * size + 3] = argb(255, 0)

        val bounds = MatrixImageProcessor.findContentBounds(
            pixels, size, size, MatrixImageProcessor.RenderMode.OUTLINE
        )!!
        assertEquals(1, bounds.left)
        assertEquals(1, bounds.top)
        assertEquals(3, bounds.right)
        assertEquals(3, bounds.bottom)
    }
}

class OutlineTest {

    private fun argb(alpha: Int, gray: Int) = (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray

    /** Cuadrado opaco de 4x4 centrado en un lienzo de 8x8 transparente. */
    private fun cuadrado(gray: Int): IntArray {
        val pixels = IntArray(64) { argb(0, 0) }
        for (y in 2..5) for (x in 2..5) pixels[y * 8 + x] = argb(255, gray)
        return pixels
    }

    @Test
    fun `lo transparente queda apagado`() {
        val salida = MatrixImageProcessor.outline(cuadrado(200), 8)
        assertEquals(0f, salida[0], 0.0001f)
        assertEquals(0f, salida[7], 0.0001f)
    }

    @Test
    fun `el borde brilla mas que el interior`() {
        val salida = MatrixImageProcessor.outline(cuadrado(200), 8)
        val borde = salida[2 * 8 + 2]
        val interior = salida[3 * 8 + 3]
        assertTrue("el borde debería destacar sobre el relleno", borde > interior)
    }

    @Test
    fun `el interior se queda encendido tenue, no apagado`() {
        // Solo con líneas el dibujo flota y no se distingue masa de hueco.
        val interior = MatrixImageProcessor.outline(cuadrado(200), 8)[3 * 8 + 3]
        assertTrue(interior > 0f)
        assertTrue(interior < 0.5f)
    }

    @Test
    fun `una figura negra da el mismo contorno que una clara`() {
        // Este es el fallo que rompía a Gyarados: por brillo, lo oscuro no encendía nada.
        val oscura = MatrixImageProcessor.outline(cuadrado(0), 8)
        val clara = MatrixImageProcessor.outline(cuadrado(255), 8)
        for (i in oscura.indices) assertEquals(oscura[i], clara[i], 0.0001f)
    }

    @Test
    fun `un relleno uniforme no enciende el interior a tope`() {
        // Es lo que convertía a Psyduck y Ninetales en manchas sólidas.
        val salida = MatrixImageProcessor.outline(cuadrado(255), 8)
        val encendidosATope = salida.count { it > 0.9f }
        val bordes = 12 // el perímetro de un 4x4
        assertTrue("no debería encenderse más que el perímetro", encendidosATope <= bordes)
    }
}
