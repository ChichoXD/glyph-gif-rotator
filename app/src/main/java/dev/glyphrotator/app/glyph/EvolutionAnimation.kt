package dev.glyphrotator.app.glyph

import android.graphics.Bitmap

/**
 * La evolución vista en la Matrix, en cuatro versiones distintas para poder elegir.
 *
 * El problema de fondo es que 25x25 en blanco y negro no da para efectos de color ni para
 * detalle fino: lo único que se lee de verdad es **la silueta** y **el brillo**. Por eso las
 * cuatro variantes juegan con esas dos cosas y no con otra, y todas terminan igual —destello
 * blanco y el nuevo apareciendo—, que es lo que hace entender que ha cambiado de forma.
 *
 * Cada variante devuelve también dónde van los golpes de vibración, calculados sobre los
 * tiempos reales de sus propios frames: es la única forma de que el tacto siga cuadrando si
 * luego se toca alguna duración.
 */
object EvolutionAnimation {

    enum class Style {
        /** Parpadeo entre las dos siluetas, cada vez más rápido. La clásica de los juegos. */
        BLINK,

        /** Un disco de luz que se abre, lo borra todo, y se cierra dejando al nuevo. */
        RAYS,

        /** Un barrido de arriba abajo que va sustituyendo una forma por la otra. */
        WIPE,

        /** Latidos que crecen y aceleran hasta reventar en blanco. */
        PULSE,
    }

    data class Sequence(
        val animation: GifAnimation,
        val beats: List<CatchHaptics.Beat>,
    )

    fun build(from: Bitmap, to: Bitmap, matrixSize: Int, style: Style): Sequence {
        val before = levelsOf(from, matrixSize)
        val after = levelsOf(to, matrixSize)
        val builder = Builder(matrixSize)

        when (style) {
            Style.BLINK -> blink(builder, before, after)
            Style.RAYS -> rays(builder, before, after)
            Style.WIPE -> wipe(builder, before, after)
            Style.PULSE -> pulse(builder, before, after)
        }
        return builder.build()
    }

    // =====================================================================================
    // Las cuatro variantes
    // =====================================================================================

    /**
     * Las dos siluetas alternándose cada vez más rápido.
     *
     * Es la de toda la vida, y aquí funciona especialmente bien: al no haber color, dos
     * siluetas alternas es exactamente lo que se ve en el juego original. La aceleración es lo
     * que crea la tensión — empieza pausado y termina siendo un parpadeo.
     */
    private fun blink(builder: Builder, from: IntArray, to: IntArray) {
        builder.add(from, INTRO_MS)

        val silFrom = silhouette(from)
        val silTo = silhouette(to)
        var duration = BLINK_START_MS
        var showingNew = true

        repeat(BLINK_SWAPS) {
            // El tacto entra solo en la parte rápida: en los primeros cambios, tan separados,
            // se sentiría como golpes sueltos en vez de como algo que se acelera.
            if (duration <= BLINK_HAPTIC_FROM_MS) builder.beat()
            builder.add(if (showingNew) silTo else silFrom, duration)
            showingNew = !showingNew
            duration = (duration * BLINK_ACCELERATION).toLong().coerceAtLeast(BLINK_MIN_MS)
        }
        finish(builder, to)
    }

    /**
     * Un disco de luz que se abre desde el centro hasta comerse la pantalla, y se cierra
     * dejando ya al nuevo.
     *
     * La gracia está en que la forma vieja no se apaga: **la tapa la luz**. Y al cerrarse, lo
     * que va quedando detrás ya es la otra silueta, así que el cambio ocurre escondido dentro
     * del blanco y no se ve el corte.
     */
    private fun rays(builder: Builder, from: IntArray, to: IntArray) {
        builder.add(from, INTRO_MS)

        val silFrom = silhouette(from)
        val silTo = silhouette(to)
        val maxRadius = builder.size * DISC_MAX_RATIO
        var duration = RAY_START_MS

        for (step in 1..RAY_STEPS) {
            if (step % RAY_HAPTIC_EVERY == 0) builder.beat()
            builder.add(withDisc(silFrom, builder.size, maxRadius * step / RAY_STEPS), duration)
            duration = (duration * RAY_ACCELERATION).toLong().coerceAtLeast(RAY_MIN_MS)
        }

        builder.beat(strong = true)
        builder.add(solid(builder.size, MAX_LEVEL), FLASH_HOLD_MS)

        for (step in RAY_STEPS downTo 0) {
            builder.add(withDisc(silTo, builder.size, maxRadius * step / RAY_STEPS), RAY_CLOSE_MS)
        }
        reveal(builder, to)
    }

    /**
     * Una línea de luz que baja y va dejando la forma nueva por donde pasa.
     *
     * Es la más legible de las cuatro porque el cambio se ve ocurrir, franja a franja, en vez
     * de esconderse tras un destello. A cambio es la menos "mágica".
     */
    private fun wipe(builder: Builder, from: IntArray, to: IntArray) {
        builder.add(from, INTRO_MS)

        val size = builder.size
        for (row in 0 until size) {
            val frame = IntArray(size * size) { index ->
                val y = index / size
                when {
                    y == row -> MAX_LEVEL              // la línea que barre, encendida
                    y < row -> to[index]               // por donde ya pasó: la forma nueva
                    else -> from[index]                // lo que queda por barrer: la vieja
                }
            }
            if (row == 0 || row == size / 2) builder.beat()
            builder.add(frame, WIPE_ROW_MS)
        }
        finish(builder, to)
    }

    /**
     * Latidos que crecen y aceleran hasta reventar.
     *
     * Cada latido son dos golpes seguidos, como un corazón, y la silueta se hincha un poco en
     * cada uno. Es la que mejor se nota **sin mirar**: la vibración cuenta la historia entera
     * aunque tengas el móvil boca abajo.
     */
    private fun pulse(builder: Builder, from: IntArray, to: IntArray) {
        builder.add(from, INTRO_MS)

        val sil = silhouette(from)
        var stepMs = PULSE_START_MS

        repeat(PULSE_BEATS) { beat ->
            // Cada latido se hincha un poco más que el anterior: es lo que hace sentir que
            // aquello va a estallar en vez de quedarse repitiéndose igual.
            val peak = PULSE_MIN_ZOOM + (PULSE_MAX_ZOOM - PULSE_MIN_ZOOM) * (beat + 1f) / PULSE_BEATS

            builder.beat()
            builder.add(zoom(sil, builder.size, peak), stepMs)
            builder.add(dimmed(sil, PULSE_LOW_LEVEL), stepMs)

            builder.beat()
            builder.add(zoom(sil, builder.size, peak * PULSE_SECOND_RATIO), stepMs)
            builder.add(dimmed(sil, PULSE_REST_LEVEL), (stepMs * PULSE_REST_RATIO).toLong())

            stepMs = (stepMs * PULSE_ACCELERATION).toLong().coerceAtLeast(PULSE_MIN_MS)
        }
        finish(builder, to)
    }

    // =====================================================================================
    // El final, común a todas
    // =====================================================================================

    /**
     * El destello y el nuevo apareciendo.
     *
     * Va aparte porque es lo que hace que las cuatro se lean como "la misma cosa contada de
     * otra forma": el momento del cambio siempre es el mismo, un fogonazo con su golpe.
     */
    private fun finish(builder: Builder, to: IntArray) {
        builder.beat(strong = true)
        builder.add(solid(builder.size, MAX_LEVEL), FLASH_MS)
        builder.add(solid(builder.size, 0), FLASH_GAP_MS)
        builder.add(solid(builder.size, MAX_LEVEL), FLASH_HOLD_MS)
        reveal(builder, to)
    }

    /** El nuevo saliendo del blanco poco a poco, en vez de aparecer de golpe. */
    private fun reveal(builder: Builder, to: IntArray) {
        val white = solid(builder.size, MAX_LEVEL)
        for (step in 1..REVEAL_STEPS) {
            builder.add(blend(white, to, step.toFloat() / REVEAL_STEPS), REVEAL_STEP_MS)
        }
        builder.add(to, REVEAL_HOLD_MS)
    }

    // =====================================================================================
    // Utilidades de píxeles
    // =====================================================================================

    /**
     * Solo la forma, sin sombreado: todo lo encendido al mismo nivel.
     *
     * En la Matrix el degradado interior de un sprite pequeño no se distingue, y durante un
     * parpadeo rápido menos aún. Aplanarlo hace que las dos formas se comparen limpias.
     */
    private fun silhouette(levels: IntArray, level: Int = MAX_LEVEL): IntArray =
        IntArray(levels.size) { if (levels[it] > SILHOUETTE_THRESHOLD) level else 0 }

    private fun dimmed(levels: IntArray, factor: Float): IntArray =
        IntArray(levels.size) { (levels[it] * factor).toInt().coerceIn(0, MAX_LEVEL) }

    private fun solid(size: Int, level: Int) = IntArray(size * size) { level }

    private fun blend(a: IntArray, b: IntArray, t: Float): IntArray {
        val amount = t.coerceIn(0f, 1f)
        return IntArray(a.size) { (a[it] + (b[it] - a[it]) * amount).toInt().coerceIn(0, MAX_LEVEL) }
    }

    /** Lo que cae dentro del disco se pone blanco; el resto se deja como estaba. */
    private fun withDisc(levels: IntArray, size: Int, radius: Float): IntArray {
        val center = (size - 1) / 2f
        return IntArray(levels.size) { index ->
            val dx = (index % size) - center
            val dy = (index / size) - center
            if (dx * dx + dy * dy <= radius * radius) MAX_LEVEL else levels[index]
        }
    }

    /**
     * Escala respecto al centro leyendo del origen, no creando un bitmap más grande.
     *
     * Se recorre el destino y se mira **de dónde vendría** cada píxel: así lo que se sale del
     * borde simplemente no se dibuja, sin recortes ni bitmaps intermedios.
     */
    private fun zoom(levels: IntArray, size: Int, factor: Float): IntArray {
        if (factor <= 1f) return levels
        val center = (size - 1) / 2f
        return IntArray(levels.size) { index ->
            val x = center + ((index % size) - center) / factor
            val y = center + ((index / size) - center) / factor
            val sx = Math.round(x)
            val sy = Math.round(y)
            if (sx in 0 until size && sy in 0 until size) levels[sy * size + sx] else 0
        }
    }

    /** El bitmap a niveles de gris planos. Basta un canal: los frames ya vienen en gris. */
    private fun levelsOf(source: Bitmap, size: Int): IntArray {
        val bitmap = if (source.width == size && source.height == size) {
            source
        } else {
            Bitmap.createScaledBitmap(source, size, size, true)
        }
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        return IntArray(pixels.size) { pixels[it] and 0xFF }
    }

    private fun bitmapOf(levels: IntArray, size: Int): Bitmap {
        val pixels = IntArray(levels.size) {
            val level = levels[it].coerceIn(0, MAX_LEVEL)
            (0xFF shl 24) or (level shl 16) or (level shl 8) or level
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * Va acumulando frames y apuntando en qué milisegundo cae cada golpe.
     *
     * Los golpes se marcan **antes** de añadir su frame: así el instante que se guarda es el
     * del comienzo de ese frame, que es cuando se ve el cambio.
     */
    private class Builder(val size: Int) {
        private val frames = ArrayList<Bitmap>()
        private val durations = ArrayList<Long>()
        private val beats = ArrayList<CatchHaptics.Beat>()
        private var elapsedMs = 0L

        fun add(levels: IntArray, durationMs: Long) {
            frames += bitmapOf(levels, size)
            durations += durationMs
            elapsedMs += durationMs
        }

        fun beat(strong: Boolean = false) {
            beats += CatchHaptics.Beat(elapsedMs, strong)
        }

        fun build() = Sequence(GifAnimation(frames, durations), beats)
    }

    private const val MAX_LEVEL = 255

    /** Por debajo de esto un píxel se considera fondo y no forma parte de la silueta. */
    private const val SILHOUETTE_THRESHOLD = 40

    /** Cuánto se ve al Pokémon tal cual antes de que empiece a pasar algo. */
    private const val INTRO_MS = 900L

    private const val BLINK_SWAPS = 13
    private const val BLINK_START_MS = 360L
    private const val BLINK_ACCELERATION = 0.74f
    private const val BLINK_MIN_MS = 55L
    private const val BLINK_HAPTIC_FROM_MS = 150L

    private const val DISC_MAX_RATIO = 0.78f
    private const val RAY_STEPS = 14
    private const val RAY_START_MS = 70L
    private const val RAY_ACCELERATION = 0.92f
    private const val RAY_MIN_MS = 32L
    private const val RAY_CLOSE_MS = 38L
    private const val RAY_HAPTIC_EVERY = 4

    private const val WIPE_ROW_MS = 34L

    private const val PULSE_BEATS = 4
    private const val PULSE_START_MS = 130L
    private const val PULSE_ACCELERATION = 0.78f
    private const val PULSE_MIN_MS = 55L
    private const val PULSE_MIN_ZOOM = 1.06f
    private const val PULSE_MAX_ZOOM = 1.28f

    /** El segundo golpe del latido es algo más flojo que el primero. */
    private const val PULSE_SECOND_RATIO = 0.92f
    private const val PULSE_LOW_LEVEL = 0.45f
    private const val PULSE_REST_LEVEL = 0.28f
    private const val PULSE_REST_RATIO = 1.6f

    private const val FLASH_MS = 90L
    private const val FLASH_GAP_MS = 70L
    private const val FLASH_HOLD_MS = 260L

    private const val REVEAL_STEPS = 5
    private const val REVEAL_STEP_MS = 60L
    private const val REVEAL_HOLD_MS = 1100L
}
