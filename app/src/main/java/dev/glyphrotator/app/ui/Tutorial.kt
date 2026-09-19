package dev.glyphrotator.app.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.glyphrotator.app.R

/**
 * Los dos tutoriales de la app y la memoria de cuáles ya se han visto.
 *
 * Se abren solos **la primera vez** y luego solo si se piden. Un tutorial que reaparece cada vez
 * deja de ser ayuda y pasa a ser un obstáculo; uno que no se puede volver a abrir obliga a
 * acordarse de todo a la primera. Por eso hay además un botón pequeño en cada pantalla.
 *
 * El contenido vive aquí y no dentro de la pantalla que lo enseña: así las dos usan el mismo
 * armazón y añadir un tercero es añadir una lista.
 */
object Tutorial {

    /** Una página: un símbolo grande, un título y una explicación. */
    data class Page(
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val body: Int,
    )

    enum class Which(val key: String) {
        ROTATION("rotation"),
        POKEMON("pokemon"),
    }

    fun pagesFor(which: Which): List<Page> = when (which) {
        Which.ROTATION -> listOf(
            Page(
                R.drawable.ic_min_glyph,
                R.string.tut_rot_1_title,
                R.string.tut_rot_1_body,
            ),
            Page(
                R.drawable.ic_min_pokedex,
                R.string.tut_rot_2_title,
                R.string.tut_rot_2_body,
            ),
            Page(
                R.drawable.ic_min_scan,
                R.string.tut_rot_3_title,
                R.string.tut_rot_3_body,
            ),
            Page(
                R.drawable.ic_min_clock,
                R.string.tut_rot_4_title,
                R.string.tut_rot_4_body,
            ),
            Page(
                R.drawable.ic_min_warning,
                R.string.tut_rot_5_title,
                R.string.tut_rot_5_body,
            ),
        )

        Which.POKEMON -> listOf(
            Page(
                R.drawable.ic_min_pokeball,
                R.string.tut_pok_1_title,
                R.string.tut_pok_1_body,
            ),
            Page(
                R.drawable.ic_min_capture,
                R.string.tut_pok_2_title,
                R.string.tut_pok_2_body,
            ),
            Page(
                R.drawable.ic_min_training,
                R.string.tut_pok_3_title,
                R.string.tut_pok_3_body,
            ),
            Page(
                R.drawable.ic_min_water,
                R.string.tut_pok_4_title,
                R.string.tut_pok_4_body,
            ),
            Page(
                R.drawable.ic_min_egg,
                R.string.tut_pok_5_title,
                R.string.tut_pok_5_body,
            ),
            Page(
                R.drawable.ic_min_stats,
                R.string.tut_pok_6_title,
                R.string.tut_pok_6_body,
            ),
        )
    }

    /**
     * Si toca enseñarlo solo. Devuelve true **una vez** y lo apunta: llamarlo dos veces no abre
     * el tutorial dos veces, que es justo lo que pasaría si la pantalla se recrea al girar o al
     * volver de otra.
     */
    fun claimFirstRun(context: Context, which: Which): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + which.key
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    /** Para poder volver a ver el de bienvenida desde cero si hiciera falta. */
    fun forget(context: Context, which: Which) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX + which.key, false)
            .apply()
    }

    private const val PREFS_NAME = "glyph_tutorial"
    private const val KEY_PREFIX = "seen_"
}
