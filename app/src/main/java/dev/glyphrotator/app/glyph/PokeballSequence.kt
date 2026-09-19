package dev.glyphrotator.app.glyph

/**
 * Reordena la animación de la pokeball para que cuente la captura en el orden que toca.
 *
 * La original va al revés de lo que necesitamos: empieza con la bola ya en el suelo
 * balanceándose, **después** salta, y termina quieta. Aquí se reaprovechan sus mismos frames
 * en otro orden: primero la caída, luego tres balanceos y al final quieta inclinada.
 *
 * Los índices salen de medir la altura media de los LEDs encendidos en cada frame (cuanto más
 * bajo el número, más arriba está la bola), así que no son arbitrarios: describen dónde está
 * la bola en cada uno.
 */
object PokeballSequence {

    /**
     * La caída, desde cerca del centro hasta el impacto.
     *
     * Empieza a media altura y no arriba del todo para **enlazar con la parte anterior**: el
     * círculo que se cierra sobre el Pokémon termina en el centro de la Matrix, así que la
     * bola tiene que aparecer ahí y caer, no salir de la nada por arriba.
     *
     * Alturas medidas de los frames (cuanto menor, más arriba): el 8 está a 116, el 7 a 153,
     * el 10 a 309, el 6 a 351 y el 11 a 407, ya aplastada contra el suelo. El centro de la
     * Matrix cae sobre 244, y ninguno está justo ahí; el 7 es el más alto que sigue leyéndose
     * como caída sin arrancar desde el borde, así que el 8 se descarta.
     */
    private val FALL = intArrayOf(7, 10, 6, 11)

    /** El rebote tras el impacto: sube un poco (355) y se asienta (368). */
    private val BOUNCE = intArrayOf(12, 13)

    /**
     * Un balanceo completo: inclinada a un lado, centro, al otro lado, centro.
     *
     * Son los cuatro frames que la original repite al principio, y sus duraciones ya marcan
     * el ritmo bueno: 100 ms en los extremos y 170 ms en el centro.
     */
    private val WOBBLE = intArrayOf(1, 2, 3, 4)

    /** El frame final: quieta e inclinada, que es como termina la original. */
    private const val STATIC_TILTED = 3

    /** Cuánto dura la caída de cada frame. Más corto que el original: cae, no flota. */
    private const val FALL_FRAME_MS = 55L

    private const val IMPACT_MS = 120L
    private const val SETTLE_MS = 260L
    private const val FINAL_HOLD_MS = 1200L

    /** La bola quieta y centrada entre sacudida y sacudida. */
    private const val WOBBLE_REST_FRAME = 2
    private const val WOBBLE_PAUSE_MS = 420L

    /**
     * El brillo del frame final. Por encima de 1 oscurece, pero poco: gris, no apagado.
     */
    private const val STATIC_GAMMA = 1.35f

    /**
     * Brillo mínimo de un LED encendido, sobre 1.
     *
     * A 1, cualquier LED encendido va a 255: blanco puro, sin medias tintas.
     *
     * Se llegó aquí después de probar 0,55 y 0,62 y seguir viéndose gris. El original usa
     * muchos tonos entre 83 y 125, y en la Matrix esos se leen apagados hagas lo que hagas con
     * la curva. Se pierde el sombreado interior, pero a 25x25 lo que se reconoce es la silueta,
     * no el degradado.
     */
    private const val LIT_FLOOR = 1f

    /** El frame final va sin tanto suelo: ahí sí se busca el gris. */
    private const val STATIC_FLOOR = 0.55f

    /**
     * La frenada hacia el frame final: pasos cada vez más largos.
     *
     * Es lo que convierte el corte en un asentamiento. El brillo baja al mismo ritmo, así que
     * la bola se apaga mientras se para en vez de dar un salto de intensidad.
     */
    private val SETTLE_STEPS = longArrayOf(90L, 140L, 220L)

    /**
     * Monta la secuencia a partir de la animación original.
     *
     * [brightnessGamma] aclara con una curva: la original se queda corta en la Matrix, sobre
     * todo en los grises intermedios que usa para el sombreado.
     */
    fun build(
        source: GlyphDataAnimation,
        wobbles: Int = 3,
        /** Exponente de la curva de brillo: por debajo de 1 aclara. */
        brightnessGamma: Float = 0.35f,
    ): GlyphDataAnimation {
        if (source.frames.isEmpty()) return source

        val frames = ArrayList<IntArray>()
        val durations = ArrayList<Long>()

        fun add(index: Int, durationMs: Long) {
            val frame = source.frames.getOrNull(index) ?: return
            frames += brighten(frame, brightnessGamma)
            durations += durationMs
        }

        // 1. La caída, acelerando: los últimos tramos duran menos.
        for ((step, index) in FALL.withIndex()) {
            val isImpact = step == FALL.lastIndex
            add(index, if (isImpact) IMPACT_MS else FALL_FRAME_MS)
        }

        // 2. El rebote y el asentamiento antes de empezar a moverse.
        for (index in BOUNCE) add(index, SETTLE_MS)

        // 3. Los balanceos, con las duraciones que ya traía la original, y una pausa con la
        // bola quieta entre uno y otro: encadenados sin respiro se leen como un temblor, no
        // como tres sacudidas que se puedan contar.
        repeat(wobbles) { wobble ->
            for (index in WOBBLE) add(index, source.durationsMs.getOrNull(index) ?: 120L)
            if (wobble < wobbles - 1) add(WOBBLE_REST_FRAME, WOBBLE_PAUSE_MS)
        }

        // 4. La transición: el último balanceo acaba centrado y el frame final está inclinado,
        // así que pasar de uno a otro de golpe da un tirón. Se encadena frenando —cada paso
        // dura más que el anterior— y bajando el brillo poco a poco hasta el gris final.
        source.frames.getOrNull(STATIC_TILTED)?.let { tilted ->
            for (step in SETTLE_STEPS.indices) {
                val progress = (step + 1f) / (SETTLE_STEPS.size + 1)
                val floor = LIT_FLOOR + (STATIC_FLOOR - LIT_FLOOR) * progress
                val gamma = brightnessGamma + (STATIC_GAMMA - brightnessGamma) * progress
                frames += brighten(tilted, gamma, floor)
                durations += SETTLE_STEPS[step]
            }

            // Y ya quieta, sostenida y más apagada: se queda en pantalla más de un segundo y
            // a brillo pleno cansa la vista, además de que bajar el brillo es lo que hace
            // leer "ya está, esto ya no se mueve".
            frames += brighten(tilted, STATIC_GAMMA, STATIC_FLOOR)
            durations += FINAL_HOLD_MS
        }

        return GlyphDataAnimation(frames, durations)
    }

    /**
     * Sube el brillo con una curva, no multiplicando.
     *
     * Multiplicar aplasta: todo lo que pasa de 160 se va a 255 y el sombreado se convierte en
     * una mancha blanca. Con una curva de gamma los tonos bajos y medios suben mucho —que es
     * donde se nota el "más blanco"— y los altos casi no se mueven, así que **no se pierde el
     * detalle**: lo que ya era claro sigue distinguiéndose de lo que era blanco del todo.
     *
     * Lo apagado se queda apagado: elevar el cero sigue siendo cero.
     */
    private fun brighten(frame: IntArray, gamma: Float, floor: Float = LIT_FLOOR): IntArray =
        IntArray(frame.size) {
            val value = frame[it]
            if (value <= 0) {
                0
            } else {
                // La curva sola no bastaba: dejaba los medios sobre 160, que en la Matrix se
                // lee gris. Con el suelo, todo lo encendido arranca ya alto y la curva solo
                // reparte el tramo que queda, así que sale blanco conservando los matices.
                val curved = Math.pow((value / 255f).toDouble(), gamma.toDouble()).toFloat()
                val lifted = floor + (1f - floor) * curved
                (255f * lifted).toInt().coerceIn(1, 255)
            }
        }

    /**
     * Los instantes donde la bola arranca un movimiento, para la vibración.
     *
     * Se calculan sobre la secuencia ya montada: el impacto de la caída y cada extremo de
     * cada balanceo. Así el tacto va con lo que se ve aunque cambien las duraciones.
     */
    fun hapticMoments(wobbles: Int = 3, source: GlyphDataAnimation? = null): List<Long> {
        val moments = ArrayList<Long>()
        var elapsed = 0L

        for ((step, _) in FALL.withIndex()) {
            val isImpact = step == FALL.lastIndex
            if (isImpact) moments += elapsed
            elapsed += if (isImpact) IMPACT_MS else FALL_FRAME_MS
        }
        elapsed += SETTLE_MS * BOUNCE.size

        repeat(wobbles) { wobble ->
            for ((position, index) in WOBBLE.withIndex()) {
                // Dos golpes por sacudida, uno en cada extremo del balanceo.
                if (position == 0 || position == 2) moments += elapsed
                elapsed += source?.durationsMs?.getOrNull(index) ?: 120L
            }
            // La misma pausa que en la animación, o el tacto se adelantaría a la imagen.
            if (wobble < wobbles - 1) elapsed += WOBBLE_PAUSE_MS
        }
        return moments
    }
}
