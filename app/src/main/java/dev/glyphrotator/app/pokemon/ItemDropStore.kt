package dev.glyphrotator.app.pokemon

import android.content.Context
import dev.glyphrotator.app.habits.HabitRules

/**
 * Cuenta lo que hace falta para saber cuándo toca un objeto: capturas desde el último y qué día
 * se comprobó por última vez si ayer fue un día perfecto.
 *
 * Mismo patrón que [EggStore] y por el mismo motivo: esto pasa con la pantalla apagada y Android
 * puede matar el servicio en medio, así que el contador vive en disco y no en un campo.
 */
class ItemDropStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Capturas hechas desde el último objeto, de 0 a [ItemDropTable.CAPTURES_PER_ITEM]. */
    val capturesTowardNext: Int
        get() = prefs.getInt(KEY_CAPTURES, 0)

    /**
     * Apunta una captura y dice si con ella toca objeto.
     *
     * Aparte del contador de [EggStore] a propósito: son dos premios distintos por la misma
     * captura, y que compartieran contador ataría sus ritmos sin motivo — cambiar cada cuánto
     * cae un huevo no debería mover cada cuánto cae un objeto.
     */
    fun registerCapture(): Boolean {
        val captures = capturesTowardNext + 1
        if (captures < ItemDropTable.CAPTURES_PER_ITEM) {
            prefs.edit().putInt(KEY_CAPTURES, captures).apply()
            return false
        }
        prefs.edit().putInt(KEY_CAPTURES, 0).apply()
        return true
    }

    /**
     * El último día (epoch day) en que se comprobó si el anterior fue perfecto. 0 si nunca.
     *
     * Se guarda para no comprobarlo dos veces el mismo día: el día perfecto premia una sola vez,
     * y sin esta marca cada ronda del sondeo (una vez por minuto) volvería a mirar lo mismo.
     */
    var lastPerfectDayCheck: Int
        get() = prefs.getInt(KEY_LAST_CHECK, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_CHECK, value).apply()

    /** Si hoy es un día distinto al último comprobado, para saber si toca mirar. */
    fun isNewDay(): Boolean = HabitRules.today() != lastPerfectDayCheck

    private companion object {
        const val PREFS_NAME = "glyph_pokemon_item_drops"
        const val KEY_CAPTURES = "captures_toward_next"
        const val KEY_LAST_CHECK = "last_perfect_day_check"
    }
}
