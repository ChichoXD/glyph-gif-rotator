package dev.glyphrotator.app.glyph

import android.graphics.Bitmap
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * El huevo en la Matrix, con tres animaciones según lo que le quede para abrirse.
 *
 * Se dibuja por código y no con GIFs importados por dos razones: a 25x25 un óvalo dibujado
 * sale nítido y uno reducido sale blando, y así el estado se puede leer de un vistazo sin
 * depender de que haya tres archivos en su sitio.
 *
 * La idea de las tres fases es que el movimiento cuente el progreso: casi quieto al principio,
 * respirando a media eclosión, y sacudiéndose con grietas al final.
 */
object EggAnimation {

    /** En qué punto está el huevo. */
    enum class Stage {
        /** Recién puesto: apenas respira. */
        FRESH,

        /** A media eclosión: respira claramente y se balancea. */
        WARM,

        /** A punto: se sacude y le salen grietas. */
        HATCHING;

        companion object {
            /** La fase que toca con [progress] de 0 a 1. */
            fun of(progress: Float): Stage = when {
                progress < 0.4f -> FRESH
                progress < 0.8f -> WARM
                else -> HATCHING
            }
        }
    }

    /**
     * La animación completa de esa fase, en bucle.
     *
     * Todas duran lo mismo y tienen el mismo número de frames: lo que cambia es la amplitud
     * del movimiento y las grietas, así que pasar de una fase a la siguiente no da un salto
     * brusco de ritmo.
     */
    fun build(matrixSize: Int, stage: Stage): GifAnimation {
        val frames = ArrayList<Bitmap>(FRAME_COUNT)
        val durations = ArrayList<Long>(FRAME_COUNT)

        for (index in 0 until FRAME_COUNT) {
            val phase = index.toFloat() / FRAME_COUNT * (2 * Math.PI).toFloat()
            frames += eggBitmap(matrixSize, stage, phase)
            durations += FRAME_MS
        }
        return GifAnimation(frames, durations)
    }

    /**
     * Un frame del huevo.
     *
     * El "respirar" es un cambio de anchura: el óvalo se ensancha y se estrecha, como cuando
     * algo respira dentro. Es más legible a 25x25 que mover el dibujo entero, porque a este
     * tamaño un desplazamiento de un píxel ya es un salto enorme.
     */
    private fun eggBitmap(matrixSize: Int, stage: Stage, phase: Float): Bitmap {
        val breathe = sin(phase)
        val wobble = when (stage) {
            Stage.FRESH -> 0f
            Stage.WARM -> sin(phase * 2) * WARM_WOBBLE
            Stage.HATCHING -> sin(phase * 5) * HATCHING_WOBBLE
        }
        val widthGain = breathe * when (stage) {
            Stage.FRESH -> FRESH_BREATH
            Stage.WARM -> WARM_BREATH
            Stage.HATCHING -> HATCHING_BREATH
        }

        val shape = Shape(
            centerX = (matrixSize - 1) / 2f + wobble,
            centerY = (matrixSize - 1) / 2f,
            radiusX = matrixSize * EGG_WIDTH_RATIO + widthGain,
            radiusY = matrixSize * EGG_HEIGHT_RATIO,
        )

        // La cáscara, maciza y blanca. Nada de relleno intermedio: en la Matrix los grises se
        // leen como apagados, y lo que hace reconocible al huevo es la silueta con sus manchas,
        // no el sombreado.
        val pixels = IntArray(matrixSize * matrixSize)
        for (y in 0 until matrixSize) {
            for (x in 0 until matrixSize) {
                if (shape.contains(x.toFloat(), y.toFloat())) {
                    pixels[y * matrixSize + x] = gray(SHELL_LEVEL)
                }
            }
        }

        carveSpots(pixels, matrixSize, shape)

        if (stage == Stage.HATCHING) {
            // Las grietas crecen a lo largo del ciclo: empiezan cortas y se van abriendo, así
            // que la fase final cuenta un proceso en vez de repetir siempre el mismo dibujo.
            val growth = ((sin(phase - HALF_PI) + 1f) / 2f).coerceIn(0f, 1f)
            carveCracks(pixels, matrixSize, shape, growth)
        }
        return Bitmap.createBitmap(pixels, matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
    }

    /**
     * El óvalo del huevo: más ancho abajo y estrechándose hacia arriba.
     *
     * Se agrupa aquí porque las manchas y las grietas tienen que colocarse **en coordenadas del
     * huevo** y no de la pantalla: así siguen al dibujo cuando respira o se balancea, en vez de
     * quedarse clavadas en su sitio mientras la cáscara se mueve por debajo.
     */
    private class Shape(
        val centerX: Float,
        val centerY: Float,
        val radiusX: Float,
        val radiusY: Float,
    ) {
        /** Cuánto se estrecha a la altura [v] (de -1 arriba a 1 abajo). */
        private fun taperAt(v: Float): Float = 1f - TAPER * (-v)

        fun contains(x: Float, y: Float): Boolean {
            val v = (y - centerY) / radiusY
            val u = (x - centerX) / radiusX / taperAt(v)
            return u * u + v * v <= 1f
        }

        /** De coordenadas del huevo (u, v) al píxel donde caen. */
        fun toPixelX(u: Float, v: Float): Float = centerX + u * taperAt(v) * radiusX
        fun toPixelY(v: Float): Float = centerY + v * radiusY
    }

    /**
     * Las manchas de la cáscara: lo que hace que se lea como **el** huevo y no como un óvalo.
     *
     * Van apagadas, no encendidas. En blanco y negro no hay forma de pintar "manchas de otro
     * color", así que se recortan de la cáscara: el hueco negro sobre el blanco es lo único que
     * a 25x25 se distingue a la primera.
     *
     * Son triángulos con la punta arriba, repartidos por la mitad de abajo, como en el sprite
     * de los juegos.
     */
    private fun carveSpots(pixels: IntArray, matrixSize: Int, shape: Shape) {
        for (spot in SPOTS) {
            val topY = shape.toPixelY(spot.v).roundToInt()
            val centerXAt = shape.toPixelX(spot.u, spot.v)

            for (row in 0 until spot.rows) {
                val y = topY + row
                if (y !in 0 until matrixSize) continue
                // Cada fila es dos píxeles más ancha que la de encima: 1, 3, 5...
                val halfWidth = row
                for (offset in -halfWidth..halfWidth) {
                    val x = (centerXAt + offset).roundToInt()
                    if (x !in 0 until matrixSize) continue
                    // Solo dentro de la cáscara: una mancha que se saliera rompería la silueta.
                    if (!shape.contains(x.toFloat(), y.toFloat())) continue
                    pixels[y * matrixSize + x] = gray(0)
                }
            }
        }
    }

    /**
     * Las grietas de la última fase: una fractura principal con ramas que se abren.
     *
     * Se apagan píxeles en vez de encenderlos porque el huevo ya está encendido: una grieta es
     * un hueco por donde no hay cáscara.
     *
     * Los recorridos van en coordenadas del huevo y se dibujan interpolando entre sus puntos,
     * así que salen continuos en vez de a saltos. [growth] (0 a 1) decide cuánto se ha abierto
     * cada una: las ramas arrancan más tarde que la principal, que es como se rompe algo de
     * verdad — primero la fractura, luego lo que se abre a partir de ella.
     */
    private fun carveCracks(pixels: IntArray, matrixSize: Int, shape: Shape, growth: Float) {
        for ((index, path) in CRACK_PATHS.withIndex()) {
            // Cada rama espera su turno; la principal (índice 0) empieza desde el principio.
            val start = index * CRACK_STAGGER
            val progress = ((growth - start) / (1f - start)).coerceIn(0f, 1f)
            if (progress <= 0f) continue

            val segments = path.size - 1
            val drawnLength = segments * progress

            for (segment in 0 until segments) {
                val from = path[segment]
                val to = path[segment + 1]
                // Lo que toca dibujar de este tramo: entero, a medias o nada.
                val portion = (drawnLength - segment).coerceIn(0f, 1f)
                if (portion <= 0f) break

                val steps = CRACK_STEPS_PER_SEGMENT
                for (step in 0..steps) {
                    val t = step.toFloat() / steps
                    if (t > portion) break
                    val u = from.first + (to.first - from.first) * t
                    val v = from.second + (to.second - from.second) * t

                    val x = shape.toPixelX(u, v).roundToInt()
                    val y = shape.toPixelY(v).roundToInt()
                    if (x !in 0 until matrixSize || y !in 0 until matrixSize) continue
                    if (!shape.contains(x.toFloat(), y.toFloat())) continue
                    pixels[y * matrixSize + x] = gray(0)
                }
            }
        }
    }

    private fun gray(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    /** Una mancha: dónde cae su punta, en coordenadas del huevo, y cuántas filas ocupa. */
    private class Spot(val u: Float, val v: Float, val rows: Int)

    /**
     * Las manchas, repartidas por la mitad de abajo y de tamaños distintos.
     *
     * Irregulares a propósito: puestas simétricas parecen un patrón impreso, y lo que se busca
     * es que parezcan manchas.
     */
    private val SPOTS = listOf(
        Spot(u = -0.45f, v = 0.05f, rows = 3),
        Spot(u = 0.30f, v = -0.05f, rows = 2),
        Spot(u = 0.00f, v = 0.42f, rows = 3),
        Spot(u = -0.55f, v = 0.60f, rows = 2),
        Spot(u = 0.52f, v = 0.48f, rows = 2),
    )

    /**
     * Los recorridos de las grietas en coordenadas del huevo.
     *
     * El primero es la fractura principal, que baja desde el hombro haciendo zigzag; los otros
     * salen de puntos de ese recorrido, para que se lean como ramas suyas y no como arañazos
     * sueltos.
     */
    private val CRACK_PATHS = listOf(
        listOf(
            0.10f to -0.88f,
            -0.05f to -0.62f,
            0.18f to -0.34f,
            -0.02f to -0.08f,
            0.20f to 0.20f,
            0.02f to 0.46f,
        ),
        listOf(-0.02f to -0.08f, -0.34f to 0.02f, -0.58f to -0.16f),
        listOf(0.20f to 0.20f, 0.52f to 0.30f),
        listOf(0.18f to -0.34f, 0.50f to -0.46f),
    )

    /** Cuánto tarda cada rama en empezar, respecto al total de la apertura. */
    private const val CRACK_STAGGER = 0.18f

    /** Puntos por tramo. Suficientes para que la línea salga continua a 25x25. */
    private const val CRACK_STEPS_PER_SEGMENT = 10

    private const val HALF_PI = (Math.PI / 2).toFloat()

    private const val FRAME_COUNT = 16
    private const val FRAME_MS = 90L

    private const val EGG_HEIGHT_RATIO = 0.38f
    private const val EGG_WIDTH_RATIO = 0.28f

    /** Cuánto se estrecha hacia arriba. */
    private const val TAPER = 0.22f

    private const val SHELL_LEVEL = 255

    private const val FRESH_BREATH = 0.15f
    private const val WARM_BREATH = 0.55f
    private const val HATCHING_BREATH = 0.9f

    private const val WARM_WOBBLE = 0.4f
    private const val HATCHING_WOBBLE = 1.1f
}
