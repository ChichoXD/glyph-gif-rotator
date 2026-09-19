package dev.glyphrotator.app.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.exp
import kotlin.math.ln

/**
 * Secuencia de "captura": un círculo blanco se cierra rápido sobre la Matrix (misma curva
 * de aceleración que usa glyph-catch en su AnimationCoordinator, reimplementada aquí sin
 * copiar su código) y después se reproduce el GIF real de la pokeball (aportado por el
 * usuario, decodificado con timing real vía [MediaFrameDecoder]).
 */
object PokeballCatchAnimation {

    /** Un paso del cierre: qué diámetro dibujar y cuánto dejarlo en pantalla. */
    data class ShrinkStep(val diameter: Int, val durationMs: Long)

    /**
     * Calcula los pasos del cierre (diámetro y duración). Separado del dibujo para poder
     * verificar la curva sin depender de Android.
     */
    fun buildShrinkSteps(matrixSize: Int): List<ShrinkStep> {
        val totalDelta = matrixSize - MIN_CIRCLE_DIAMETER
        if (totalDelta <= 0) return emptyList()

        val steps = ArrayList<ShrinkStep>(totalDelta)
        var previousPortion = 0.0
        for (step in 1..totalDelta) {
            val progress = step / totalDelta.toDouble()
            val timePortion = easeOutStrongInverse(progress)
            val deltaPortion = (timePortion - previousPortion).coerceAtLeast(0.0)
            val duration = (deltaPortion * SHRINK_DURATION_MS).toLong().coerceAtLeast(MIN_FRAME_DURATION_MS)
            previousPortion = timePortion

            steps += ShrinkStep(
                diameter = (matrixSize - step).coerceAtLeast(MIN_CIRCLE_DIAMETER),
                durationMs = duration
            )
        }
        return steps
    }

    /** Frames del círculo cerrándose, ya con sus duraciones (antes de los frames del GIF). */
    fun buildShrinkFrames(matrixSize: Int): GifAnimation {
        val steps = buildShrinkSteps(matrixSize)
        return GifAnimation(
            steps.map { circleBitmap(matrixSize, it.diameter) },
            steps.map { it.durationMs }
        )
    }

    /**
     * Un paso del cierre con el Pokémon dentro: se ve solo lo que cae dentro del círculo, y
     * el borde del círculo va encendido.
     *
     * Es lo que da sentido a la captura en una pantalla de 25x25: en vez de tapar al Pokémon
     * con una bola, la abertura se va cerrando sobre él, así que se le sigue viendo moverse
     * hasta que el círculo es tan pequeño que ya no queda nada. Cuando el cierre termina, el
     * Pokémon ha desaparecido de verdad, no está debajo.
     */
    fun captureStepBitmap(source: Bitmap, matrixSize: Int, diameter: Int): Bitmap {
        val result = Bitmap.createBitmap(matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
        val center = (matrixSize - 1) / 2f
        val radius = diameter / 2f

        val pixels = IntArray(matrixSize * matrixSize)
        source.getPixels(pixels, 0, matrixSize, 0, 0, matrixSize, matrixSize)

        val output = IntArray(matrixSize * matrixSize)
        for (y in 0 until matrixSize) {
            for (x in 0 until matrixSize) {
                val dx = x - center
                val dy = y - center
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                output[y * matrixSize + x] = when {
                    // El borde: un anillo encendido que es lo que se lee como "la bola".
                    distance > radius -> OFF_PIXEL
                    distance > radius - RIM_THICKNESS -> RIM_PIXEL
                    else -> pixels[y * matrixSize + x]
                }
            }
        }
        return Bitmap.createBitmap(output, matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
    }

    /**
     * Las tres sacudidas de la pokeball, como en los juegos.
     *
     * No es un temblor: cada sacudida es un balanceo lateral —se inclina a un lado, cruza al
     * otro y vuelve al centro— seguido de una pausa con la bola quieta. Al llegar al punto
     * máximo de cada balanceo la bola se ilumina, que es el "latido" que hace de clic. Al
     * final, un destello más largo: el Pokémon está capturado.
     */
    fun buildWobbleFrames(matrixSize: Int, wobbles: Int = WOBBLE_COUNT): GifAnimation {
        val frames = ArrayList<Bitmap>()
        val durations = ArrayList<Long>()

        fun add(tilt: Float, glow: Float, durationMs: Long) {
            frames += pokeballBitmap(matrixSize, tilt, glow)
            durations += durationMs
        }

        // La caída: la bola entra desde arriba y se posa. Acelera como caería de verdad —el
        // recorrido va con el cuadrado del tiempo— y remata con un rebote corto, que es lo
        // que la hace aterrizar en vez de aparecer de golpe.
        for (frame in 0 until DROP_FRAMES) {
            val progress = (frame + 1f) / DROP_FRAMES
            frames += pokeballBitmap(matrixSize, tilt = 0f, glow = 0f, riseAbove = (1f - progress * progress) * DROP_HEIGHT)
            durations += DROP_FRAME_MS
        }
        for (bounce in BOUNCE_HEIGHTS) {
            frames += pokeballBitmap(matrixSize, tilt = 0f, glow = 0f, riseAbove = bounce)
            durations += DROP_FRAME_MS
        }

        // Ya en el suelo, quieta un momento antes de empezar a moverse.
        add(0f, 0f, WOBBLE_SETTLE_MS)

        repeat(wobbles) {
            // Ida a un lado, cruce al otro y vuelta: el recorrido completo del balanceo.
            for (step in WOBBLE_PATH) {
                // El brillo sube con lo inclinada que esté: el latido cae solo en los extremos.
                add(step, kotlin.math.abs(step) / MAX_TILT, WOBBLE_STEP_MS)
            }
            add(0f, 0f, WOBBLE_PAUSE_MS)
        }

        // El clic: la bola se queda encendida del todo.
        add(0f, 1f, WOBBLE_CLICK_MS)

        // Y el remate de "capturado": tres destellos cortos. Sin esto la animación se
        // terminaba sin decir que había salido bien, y quedaba la duda de si se había escapado.
        repeat(CAUGHT_FLASHES) {
            add(0f, 0f, CAUGHT_FLASH_MS)
            add(0f, 1f, CAUGHT_FLASH_MS)
        }
        add(0f, 1f, CAUGHT_HOLD_MS)
        return GifAnimation(frames, durations)
    }

    /**
     * La pokeball dibujada a mano: mitad de arriba maciza, mitad de abajo solo el contorno,
     * y el botón en medio. En blanco y negro es lo que la hace reconocible — con las dos
     * mitades rellenas sería un círculo cualquiera.
     *
     * [tilt] inclina el dibujo en radianes y [glow] (0..1) sube el brillo del conjunto.
     */
    private fun pokeballBitmap(matrixSize: Int, tilt: Float, glow: Float, riseAbove: Float = 0f): Bitmap {
        val pixels = IntArray(matrixSize * matrixSize)
        // Pequeña y apoyada abajo, como en el juego original: una bola que llena la pantalla
        // se lee como un círculo cualquiera, mientras que una pequeña posada en el suelo se
        // reconoce al instante y deja sitio para que se note el balanceo.
        val center = (matrixSize - 1) / 2f
        val groundY = matrixSize - GROUND_MARGIN
        val radius = matrixSize * BALL_RADIUS_RATIO
        val ballCenterY = groundY - radius - riseAbove
        val sin = kotlin.math.sin(tilt.toDouble()).toFloat()
        val cos = kotlin.math.cos(tilt.toDouble()).toFloat()

        val body = (110 + 80 * glow).toInt().coerceIn(0, 255)
        val edge = (200 + 55 * glow).toInt().coerceIn(0, 255)

        // El suelo: una línea tenue bajo la bola para que se apoye en algo y no flote.
        for (x in 0 until matrixSize) {
            val fromCenter = kotlin.math.abs(x - center)
            if (fromCenter <= radius + 2f) {
                pixels[groundY * matrixSize + x] = grayPixel(GROUND_LEVEL)
            }
        }

        for (y in 0 until matrixSize) {
            for (x in 0 until matrixSize) {
                val dx = x - center
                val dy = y - ballCenterY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance > radius) continue

                // Coordenadas giradas: inclinar el dibujo en vez de mover cada píxel.
                val ry = -dx * sin + dy * cos
                val level = when {
                    distance > radius - 1.2f -> edge          // contorno exterior
                    kotlin.math.abs(ry) < 1.2f -> edge        // banda central
                    ry < 0f -> body                           // mitad de arriba, maciza
                    else -> 0                                 // mitad de abajo, hueca
                }
                // El botón central, y un brillo arriba a la izquierda que le da volumen: sin él
                // la mitad superior es una mancha plana.
                val withButton = when {
                    distance < BUTTON_RADIUS -> edge
                    ry < -radius * 0.45f && dx < 0 -> edge
                    else -> level
                }
                pixels[y * matrixSize + x] = grayPixel(withButton)
            }
        }
        return Bitmap.createBitmap(pixels, matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
    }

    private fun circleBitmap(matrixSize: Int, diameter: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val center = matrixSize / 2f
        canvas.drawCircle(center, center, diameter / 2f, paint)
        return bitmap
    }

    private fun easeOutStrongInverse(progress: Double): Double {
        val clamped = progress.coerceIn(0.0, 1.0)
        if (clamped <= 0.0) return 0.0
        if (clamped >= 1.0) return 1.0
        val k = SHRINK_CURVE_STRENGTH
        val base = 1 - clamped * (1 - exp(-k))
        return -ln(base) / k
    }

    private fun grayPixel(level: Int) =
        (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private const val DROP_FRAMES = 7
    private const val DROP_FRAME_MS = 45L

    /** Desde qué altura cae, en píxeles de la Matrix. */
    private const val DROP_HEIGHT = 11f

    /** El rebote al tocar el suelo: sube poco y vuelve. */
    private val BOUNCE_HEIGHTS = floatArrayOf(2.2f, 0.8f, 0f)

    /** Lo que ocupa la bola respecto al lado de la Matrix. Pequeña, como en el original. */
    private const val BALL_RADIUS_RATIO = 0.24f

    /** Filas que quedan por debajo del suelo. */
    private const val GROUND_MARGIN = 4

    private const val GROUND_LEVEL = 60
    private const val BUTTON_RADIUS = 1.6f

    /** Las tres sacudidas de siempre. */
    const val WOBBLE_COUNT = 3

    /** Cuánto se inclina la bola en el punto máximo del balanceo, en radianes (~17°). */
    private const val MAX_TILT = 0.30f

    /**
     * El recorrido de una sacudida: al centro, a un lado, cruza al otro y vuelve. Los pasos
     * intermedios son los que hacen que se lea como un balanceo y no como un salto.
     */
    private val WOBBLE_PATH = floatArrayOf(
        -MAX_TILT / 2, -MAX_TILT, -MAX_TILT / 2, 0f,
        MAX_TILT / 2, MAX_TILT, MAX_TILT / 2, 0f,
    )

    // Públicos porque [CatchHaptics] calcula el patrón de vibración a partir de ellos: si los
    // repitiera por su cuenta, cualquier ajuste aquí descuadraría el tacto sin avisar.
    const val WOBBLE_SETTLE_MS = 320L
    const val WOBBLE_STEP_MS = 55L
    const val WOBBLE_PAUSE_MS = 480L
    const val WOBBLE_CLICK_MS = 900L

    /** El remate: destellos que confirman que se ha quedado dentro. */
    const val CAUGHT_FLASHES = 3
    const val CAUGHT_FLASH_MS = 110L
    const val CAUGHT_HOLD_MS = 700L

    /** Cuántos pasos tiene el recorrido de una sacudida. */
    val WOBBLE_STEPS: Int get() = WOBBLE_PATH.size

    /** Índices del recorrido donde la bola llega al extremo: ahí cae el golpe. */
    val WOBBLE_PEAK_STEPS: List<Int>
        get() = WOBBLE_PATH.indices.filter { kotlin.math.abs(WOBBLE_PATH[it]) >= MAX_TILT - 0.001f }

    /** Lo que dura la caída antes de posarse. */
    val DROP_DURATION_MS: Long get() = DROP_FRAMES * DROP_FRAME_MS

    private const val OFF_PIXEL = 0xFF000000.toInt()
    private const val RIM_PIXEL = 0xFFFFFFFF.toInt()

    /** Grosor del anillo, en píxeles de la Matrix. Uno solo se pierde al cerrarse. */
    private const val RIM_THICKNESS = 1.2f

    /** Cuánto se ve al Pokémon antes de que empiece a cerrarse la bola. */
    const val POKEMON_INTRO_MS = 1400L

    /**
     * Hasta dónde se cierra el círculo sobre el Pokémon.
     *
     * Es el diámetro de la pokeball de la animación siguiente: así el círculo no se cierra a
     * un punto y luego aparece una bola de la nada, sino que **se convierte** en ella. El
     * enlace entre las dos partes deja de notarse.
     */
    private const val MIN_CIRCLE_DIAMETER = 11
    private const val SHRINK_DURATION_MS = 180.0
    private const val SHRINK_CURVE_STRENGTH = 14.0
    private const val MIN_FRAME_DURATION_MS = 8L

    const val CATCH_GIF_ASSET = "pokeball_catch.gif"
    const val POKEBALL_HOLD_MS = 1500L
}
