package dev.glyphrotator.app.data

import android.content.Context

/**
 * Las preferencias de la app en un solo sitio.
 *
 * Estaban repartidas: unas en la pantalla de Pokémon, otras escondidas dentro de un diálogo, y
 * los ajustes de agua o sueño solo aparecían al pulsar el botón de esa cosa. Quien quería
 * cambiar algo tenía que acordarse de dónde estaba. Aquí se guardan juntas y la pantalla de
 * Ajustes las enseña todas de una vez.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Si los Pokémon del widget salen a color o en blanco y negro.
     *
     * En blanco y negro quedan en el mismo idioma que la Matrix y que los iconos de puntos; a
     * color se reconocen mejor de un vistazo. No hay una respuesta buena, así que se elige.
     */
    var widgetColor: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_WIDGET_COLOR, value).apply()

    /** Si el sprite del widget se anima o se queda en su primer frame. */
    var widgetAnimated: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_ANIMATED, true)
        set(value) = prefs.edit().putBoolean(KEY_WIDGET_ANIMATED, value).apply()

    /** Si al tocar una ficha suena el cry del Pokémon. */
    var cryOnTap: Boolean
        get() = prefs.getBoolean(KEY_CRY_ON_TAP, true)
        set(value) = prefs.edit().putBoolean(KEY_CRY_ON_TAP, value).apply()

    /** Si al tocar una ficha el Pokémon sale en la Matrix. */
    var showOnMatrixOnTap: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ON_TAP, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ON_TAP, value).apply()

    /**
     * Si la pulsación larga del botón Glyph enseña a tu compañero.
     *
     * Solo cuando no hay ningún salvaje esperando: capturar sigue teniendo prioridad, porque es
     * lo que se pierde si no lo atiendes.
     */
    var partnerOnGlyphButton: Boolean
        get() = prefs.getBoolean(KEY_PARTNER_ON_BUTTON, false)
        set(value) = prefs.edit().putBoolean(KEY_PARTNER_ON_BUTTON, value).apply()

    /**
     * El interruptor maestro del mod de Pokémon. Apagado por defecto.
     *
     * La app base es solo rotación de GIFs — nada de Pokémon debe verse ni ejecutarse hasta que
     * el usuario active esto a propósito. No es un ajuste más de la lista: todo lo demás de
     * Pokémon depende de él.
     */
    var pokemonModEnabled: Boolean
        get() = prefs.getBoolean(KEY_POKEMON_MOD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_POKEMON_MOD_ENABLED, value).apply()

    /**
     * Si el interruptor del mod de Pokémon **se enseña siquiera** en Ajustes.
     *
     * Es distinto de [pokemonModEnabled]: uno decide si el juego está encendido, este decide si
     * la opción existe a la vista. Apagado por defecto porque el juego todavía no está
     * confirmado en hardware, y un betatester que se lo encuentra por casualidad reporta fallos
     * de algo que aún no se da por terminado.
     *
     * Se desbloquea con siete toques en la versión, y no se vuelve a bloquear.
     */
    var pokemonModUnlocked: Boolean
        get() = prefs.getBoolean(KEY_POKEMON_MOD_UNLOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_POKEMON_MOD_UNLOCKED, value).apply()

    private companion object {
        const val PREFS_NAME = "glyph_app_prefs"
        const val KEY_WIDGET_COLOR = "widget_color"
        const val KEY_WIDGET_ANIMATED = "widget_animated"
        const val KEY_CRY_ON_TAP = "cry_on_tap"
        const val KEY_SHOW_ON_TAP = "show_on_tap"
        const val KEY_PARTNER_ON_BUTTON = "partner_on_button"
        const val KEY_POKEMON_MOD_ENABLED = "pokemon_mod_enabled"
        const val KEY_POKEMON_MOD_UNLOCKED = "pokemon_mod_unlocked"
    }
}
