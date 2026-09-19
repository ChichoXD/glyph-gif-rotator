package dev.glyphrotator.app.glyph

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * La vibración de la captura, al compás de lo que se ve en la Matrix.
 *
 * La Matrix es pequeña y está en la parte de atrás del teléfono: mirándola no siempre se
 * distingue una sacudida de la siguiente. El tacto lo resuelve — cada sacudida se nota en la
 * mano aunque no estés mirando, que es justo como funciona en los juegos.
 *
 * Se emite un patrón único en vez de un pulso por sacudida: así el ritmo lo marca el propio
 * vibrador y no se desincroniza si un frame tarda de más en pintarse.
 */
object CatchHaptics {

    private fun vibrator(context: Context): Vibrator? =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
            ?.takeIf { it.hasVibrator() }

    /**
     * El golpe seco de la bola al cerrarse sobre el Pokémon.
     *
     * Va justo cuando el círculo termina de cerrarse, que es el momento en que el Pokémon
     * desaparece dentro.
     */
    fun ballClosed(context: Context) {
        val vibrator = vibrator(context) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(CLOSE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Las tres sacudidas y el clic final, en un solo patrón.
     *
     * Los tiempos son los mismos que usa [PokeballCatchAnimation.buildWobbleFrames]: un
     * pulso corto en el punto máximo de cada balanceo, las mismas pausas entre medias, y un
     * pulso más largo al final para el clic de confirmación.
     */
    fun wobbleSequence(context: Context, wobbles: Int = PokeballCatchAnimation.WOBBLE_COUNT) {
        val vibrator = vibrator(context) ?: return

        // Los instantes se calculan a partir de las constantes de la animación, no a mano:
        // repetirlos aquí hacía que cualquier ajuste de los tiempos descuadrara el tacto sin
        // que nada avisara, que es justo lo que pasaba.
        val stepMs = PokeballCatchAnimation.WOBBLE_STEP_MS
        val pathMs = PokeballCatchAnimation.WOBBLE_STEPS * stepMs
        val cycleMs = pathMs + PokeballCatchAnimation.WOBBLE_PAUSE_MS

        // El primer balanceo empieza cuando la bola ya cayó y se asentó.
        val wobbleStart = PokeballCatchAnimation.DROP_DURATION_MS + PokeballCatchAnimation.WOBBLE_SETTLE_MS

        val moments = ArrayList<Pair<Long, Boolean>>() // instante, y si es el clic final
        for (wobble in 0 until wobbles) {
            val start = wobbleStart + wobble * cycleMs
            // Un golpe en cada extremo del recorrido: justo donde la bola se ilumina.
            for (peak in PokeballCatchAnimation.WOBBLE_PEAK_STEPS) {
                moments += (start + (peak + 1) * stepMs) to false
            }
        }
        val clickAt = wobbleStart + wobbles * cycleMs
        moments += clickAt to true

        // Los destellos de "capturado": un pulso por destello, al mismo compás.
        var flashAt = clickAt + PokeballCatchAnimation.WOBBLE_CLICK_MS
        repeat(PokeballCatchAnimation.CAUGHT_FLASHES) {
            flashAt += PokeballCatchAnimation.CAUGHT_FLASH_MS
            moments += flashAt to false
            flashAt += PokeballCatchAnimation.CAUGHT_FLASH_MS
        }

        // El patrón alterna espera/vibración, así que se convierten los instantes absolutos
        // en huecos entre pulsos.
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        var elapsed = 0L
        for ((moment, isClick) in moments) {
            val wait = (moment - elapsed).coerceAtLeast(0L)
            val duration = if (isClick) CLICK_MS else TAP_MS
            timings += wait
            amplitudes += 0
            timings += duration
            amplitudes += if (isClick) CLICK_AMPLITUDE else TAP_AMPLITUDE
            elapsed = moment + duration
        }

        vibrator.vibrate(
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), NO_REPEAT)
        )
    }

    /**
     * La vibración de una animación traída de fuera, a partir de sus propias duraciones.
     *
     * No se pueden reutilizar los tiempos de la nuestra: los suyos son otros y quedaría
     * desincronizada. Los golpes se ponen en los **cambios de ritmo** —cuando un frame corto
     * sigue a uno largo— porque ahí es donde la bola arranca un movimiento, que es justo lo
     * que se quiere notar en la mano.
     */
    fun forDataAnimation(context: Context, durationsMs: List<Long>) {
        val vibrator = vibrator(context) ?: return
        if (durationsMs.isEmpty()) return

        val moments = ArrayList<Long>()
        var elapsed = 0L
        for (index in durationsMs.indices) {
            val duration = durationsMs[index]
            val previous = durationsMs.getOrNull(index - 1) ?: Long.MAX_VALUE
            // Un frame corto tras uno largo marca el inicio de un movimiento.
            if (duration <= FAST_FRAME_MS && previous > FAST_FRAME_MS) moments += elapsed
            elapsed += duration
        }
        if (moments.isEmpty()) return

        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        var cursor = 0L
        for (moment in moments) {
            timings += (moment - cursor).coerceAtLeast(0L)
            amplitudes += 0
            timings += TAP_MS
            amplitudes += TAP_AMPLITUDE
            cursor = moment + TAP_MS
        }
        // El remate al final, para confirmar que se ha quedado dentro.
        timings += (elapsed - cursor).coerceAtLeast(0L)
        amplitudes += 0
        timings += CLICK_MS
        amplitudes += CLICK_AMPLITUDE

        vibrator.vibrate(
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), NO_REPEAT)
        )
    }

    /**
     * Vibra en los instantes indicados, y remata al final.
     *
     * Recibe los momentos ya calculados en vez de deducirlos: quien monta la animación sabe
     * dónde están los golpes, y así el tacto no depende de adivinarlos por las duraciones.
     */
    fun atMoments(context: Context, momentsMs: List<Long>, totalMs: Long) {
        val vibrator = vibrator(context) ?: return
        if (momentsMs.isEmpty()) return

        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        var cursor = 0L
        for (moment in momentsMs) {
            timings += (moment - cursor).coerceAtLeast(0L)
            amplitudes += 0
            timings += TAP_MS
            amplitudes += TAP_AMPLITUDE
            cursor = moment + TAP_MS
        }
        // El remate: confirma que se ha quedado dentro.
        timings += (totalMs - CLICK_MS - cursor).coerceAtLeast(0L)
        amplitudes += 0
        timings += CLICK_MS
        amplitudes += CLICK_AMPLITUDE

        vibrator.vibrate(
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), NO_REPEAT)
        )
    }

    /** Un golpe de vibración en un instante concreto de una animación. */
    data class Beat(val atMs: Long, val strong: Boolean = false)

    /**
     * Emite un patrón ya calculado por quien montó la animación.
     *
     * Es la versión general de [atMoments]: aquí cada golpe decide su propia fuerza, que hace
     * falta cuando la animación tiene un momento culminante en medio y no al final —la
     * evolución, sin ir más lejos, revienta en blanco por la mitad.
     */
    fun playPattern(context: Context, beats: List<Beat>) {
        val vibrator = vibrator(context) ?: return
        if (beats.isEmpty()) return

        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        var cursor = 0L
        for (beat in beats.sortedBy { it.atMs }) {
            val duration = if (beat.strong) CLICK_MS else TAP_MS
            timings += (beat.atMs - cursor).coerceAtLeast(0L)
            amplitudes += 0
            timings += duration
            amplitudes += if (beat.strong) CLICK_AMPLITUDE else TAP_AMPLITUDE
            cursor = beat.atMs + duration
        }

        vibrator.vibrate(
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), NO_REPEAT)
        )
    }

    /** Por debajo de esto, un frame se considera parte de un movimiento rápido. */
    private const val FAST_FRAME_MS = 130L

    fun cancel(context: Context) {
        vibrator(context)?.cancel()
    }

    private const val NO_REPEAT = -1

    private const val CLOSE_MS = 45L

    /** Cada toque cae en el extremo del balanceo, donde la bola se ilumina. */
    private const val TAP_MS = 35L
    private const val TAP_AMPLITUDE = 140

    private const val CLICK_MS = 90L
    private const val CLICK_AMPLITUDE = 255
}
