package dev.glyphrotator.app.pokemon.spawn

import android.content.Context

/**
 * El Pokémon que ha aparecido y está esperando a ser capturado.
 *
 * Se guarda en disco y no en memoria porque aparece con la pantalla apagada y se captura al
 * encenderla, y entre esos dos momentos Android puede haber matado el servicio: si viviera
 * solo en memoria, lo normal sería perderlo justo antes de verlo.
 *
 * Solo hay hueco para uno. Acumular una cola llevaría a despertarse con quince capturas
 * pendientes, que es exactamente lo que el juego quiere evitar.
 */
class WildSpawnStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Número de Pokédex del que espera, o null si no hay ninguno. */
    val pendingSpeciesId: Int?
        get() = prefs.getInt(KEY_SPECIES, 0).takeIf { it > 0 }

    /** Cuándo apareció, para saber cuánto lleva esperando. */
    val appearedAtMillis: Long
        get() = prefs.getLong(KEY_APPEARED_AT, 0L)

    /** Minutos de pantalla apagada acumulados desde la última aparición. */
    var minutesSinceLastSpawn: Int
        get() = prefs.getInt(KEY_MINUTES_SINCE, 0)
        set(value) = prefs.edit().putInt(KEY_MINUTES_SINCE, value.coerceAtLeast(0)).apply()

    fun put(speciesId: Int, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt(KEY_SPECIES, speciesId)
            .putLong(KEY_APPEARED_AT, nowMillis)
            // El contador se reinicia al aparecer: los minutos vuelven a contar desde cero.
            .putInt(KEY_MINUTES_SINCE, 0)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SPECIES).remove(KEY_APPEARED_AT).apply()
    }

    /**
     * Se va si lleva demasiado sin que nadie lo recoja, como en el juego.
     *
     * Sin esto, uno que apareciera un lunes seguiría ahí el viernes y la espera dejaría de
     * significar nada.
     */
    fun expireIfStale(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val appearedAt = appearedAtMillis
        if (appearedAt <= 0L) return false
        if (nowMillis - appearedAt < LIFETIME_MS) return false
        clear()
        return true
    }

    private companion object {
        const val PREFS_NAME = "glyph_pokemon_wild_spawn"
        const val KEY_SPECIES = "species_id"
        const val KEY_APPEARED_AT = "appeared_at"
        const val KEY_MINUTES_SINCE = "minutes_since_last_spawn"

        /** Doce horas: suficiente para dormir de un tirón y encontrarlo al despertar. */
        const val LIFETIME_MS = 12L * 60 * 60 * 1000
    }
}
