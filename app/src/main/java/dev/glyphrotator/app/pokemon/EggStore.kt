package dev.glyphrotator.app.pokemon

import android.content.Context

/**
 * Los huevos que tienes y cuánto lleva incubado el primero.
 *
 * Se guarda en disco por lo mismo que las apariciones: todo esto pasa con la pantalla apagada y
 * Android puede matar el servicio en medio. Un huevo que se perdiera al reiniciarse el proceso
 * serían diez capturas tiradas, y la promesa era justo la contraria — **no se pierden**.
 *
 * Solo incuba el primero de la cola. Incubarlos todos a la vez convertiría una noche larga en
 * cinco Pokémon de golpe y el huevo dejaría de significar nada.
 */
class EggStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Cuántos huevos tienes esperando. */
    val count: Int
        get() = prefs.getInt(KEY_COUNT, 0)

    /** Capturas hechas desde el último huevo, de 0 a [EggRules.CAPTURES_PER_EGG]. */
    val capturesTowardNext: Int
        get() = prefs.getInt(KEY_CAPTURES, 0)

    /** Minutos de pantalla apagada que lleva incubado el primero de la cola. */
    val incubatedMinutes: Int
        get() = prefs.getInt(KEY_INCUBATED, 0)

    val progress: Float
        get() = EggRules.progress(incubatedMinutes)

    val hasEgg: Boolean
        get() = count > 0

    /**
     * Apunta una captura y devuelve true si con ella te has ganado un huevo.
     *
     * El contador se guarda aparte del total de capturas para que soltar Pokémon o cualquier
     * cosa que toque la lista no descuadre la cuenta hacia el siguiente.
     */
    fun registerCapture(): Boolean {
        val captures = capturesTowardNext + 1
        if (captures < DemoTuning.capturesPerEgg) {
            prefs.edit().putInt(KEY_CAPTURES, captures).apply()
            return false
        }
        prefs.edit()
            .putInt(KEY_CAPTURES, 0)
            .putInt(KEY_COUNT, count + 1)
            .apply()
        return true
    }

    /**
     * Suma [minutes] de incubación al primero de la cola y dice si ya está listo.
     *
     * Sin huevos no cuenta nada: el tiempo en reposo no se guarda "por adelantado" para el
     * siguiente, o el primer huevo que consiguieras nacería en el acto.
     */
    fun incubate(minutes: Int = 1): Boolean {
        if (!hasEgg || minutes <= 0) return false
        val incubated = incubatedMinutes + minutes
        prefs.edit().putInt(KEY_INCUBATED, incubated).apply()
        return EggRules.isReady(incubated)
    }

    /** Quita el huevo que se acaba de abrir y pone el siguiente a incubar desde cero. */
    fun consumeHatched() {
        if (!hasEgg) return
        prefs.edit()
            .putInt(KEY_COUNT, count - 1)
            .putInt(KEY_INCUBATED, 0)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "glyph_pokemon_eggs"
        const val KEY_COUNT = "count"
        const val KEY_CAPTURES = "captures_toward_next"
        const val KEY_INCUBATED = "incubated_minutes"
    }
}
