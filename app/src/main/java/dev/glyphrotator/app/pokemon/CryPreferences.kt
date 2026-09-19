package dev.glyphrotator.app.pokemon

import android.content.Context

/**
 * Recuerda con qué variante de sonido quiere el usuario oír los cries.
 *
 * Se guarda el `name` del enum y no su posición: así reordenar o añadir variantes en
 * [CrySoundSet] no le cambia la elección a quien ya tenía una guardada.
 */
class CryPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var soundSet: CrySoundSet
        get() {
            val stored = prefs.getString(KEY_SOUND_SET, null) ?: return DEFAULT
            return runCatching { CrySoundSet.valueOf(stored) }.getOrDefault(DEFAULT)
        }
        set(value) {
            prefs.edit().putString(KEY_SOUND_SET, value.name).apply()
        }

    private companion object {
        const val PREFS_NAME = "glyph_pokemon_cry_prefs"
        const val KEY_SOUND_SET = "cry_sound_set"
        val DEFAULT = CrySoundSet.LEGACY
    }
}
