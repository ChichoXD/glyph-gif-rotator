package dev.glyphrotator.app.service

/** Qué debe estar mostrando la Glyph Matrix en un momento dado. */
enum class DisplayMode {
    /** Nada: Matrix apagada. */
    OFF,

    /** Disco de vinilo girando (suena música). */
    VINYL_SPINNING,

    /** Disco de vinilo congelado donde estaba (música en pausa). */
    VINYL_FROZEN,

    /** Batería con simulación de líquido. */
    BATTERY_LIQUID,

    /** Reloj tenue en reposo. */
    CLOCK,

    /** Rotación de GIFs/imágenes del usuario. */
    CAROUSEL,

    /** Un Pokémon salvaje esperando a que enciendas la pantalla. */
}

/** Estado del teléfono y de la app que determina qué mostrar. */
data class DisplayInputs(
    /** Nuestro propio toy está activo en el botón físico y manda él. */
    val ownToyActive: Boolean = false,
    val externalGlyphAppActive: Boolean = false,
    /** Acaba de entrar una notificación y el sistema está pintando su animación. */
    val notificationFlashActive: Boolean = false,
    val criticalBattery: Boolean = false,
    /** Una animación puntual en curso: manda sobre el carrusel mientras dura. */
    val oneShotActive: Boolean = false,
    val bluetoothFlashActive: Boolean = false,
    val plugFlashActive: Boolean = false,
    val musicPlaying: Boolean = false,
    val musicPaused: Boolean = false,
    val screenUnlocked: Boolean = false,
    val clockEnabled: Boolean = false,
    /** Hay un Pokémon salvaje esperando. */
    /** El disco de vinilo mientras suena música. */
    val vinylEnabled: Boolean = true,
    /**
     * El interruptor principal de la app. Por defecto true para que las pruebas se centren en
     * lo que miden; el servicio siempre pasa el valor real.
     */
    val rotationEnabled: Boolean = true
)

/**
 * Única fuente de verdad de qué se muestra en la Matrix. Está aparte del servicio y sin
 * dependencias de Android a propósito: aquí es donde vivía el fallo que dejaba la app
 * pegada en el reloj con el teléfono desbloqueado, así que conviene poder probar todas las
 * combinaciones sin dispositivo.
 */
object DisplayDecider {

    fun decide(inputs: DisplayInputs): DisplayMode = when {
        // Nuestro toy dibuja por otro canal del SDK (setMatrixFrame): si pintáramos los dos
        // a la vez competiríamos con él y taparíamos lo que muestra el botón físico.
        inputs.ownToyActive -> DisplayMode.OFF

        // La app de otro autor manda: le dejamos la Matrix libre.
        inputs.externalGlyphAppActive -> DisplayMode.OFF

        // Con una notificación recién llegada manda la animación del sistema. Va por encima
        // de música y carga: si no, las dos imágenes se pintan a la vez y se solapan.
        inputs.notificationFlashActive -> DisplayMode.OFF

        // Con la batería crítica no encendemos nada.
        inputs.criticalBattery -> DisplayMode.OFF

        // Animaciones puntuales que el usuario acaba de disparar. Van por encima del
        // interruptor principal: pedir ver un diseño concreto es una orden explícita.
        inputs.oneShotActive -> DisplayMode.CAROUSEL
        inputs.bluetoothFlashActive -> DisplayMode.CAROUSEL

        // Con la rotación apagada no se enciende nada por su cuenta. El servicio puede seguir
        // vivo un rato tras una vista previa, y sin esto sus sondeos periódicos volvían a
        // sacar el carrusel como si el interruptor no existiera.
        !inputs.rotationEnabled -> DisplayMode.OFF

        // El salvaje manda: se va si no lo recoges, y la música o la carga siguen ahí después.

        // Música y carga se ven también con la pantalla apagada.
        inputs.plugFlashActive -> DisplayMode.BATTERY_LIQUID
        // Con el vinilo desactivado la música no cambia nada: sigue mandando lo que tocara
        // (carrusel si estás usando el teléfono, reloj o nada si está en reposo).
        inputs.musicPlaying && inputs.vinylEnabled -> DisplayMode.VINYL_SPINNING
        inputs.musicPaused && inputs.vinylEnabled -> DisplayMode.VINYL_FROZEN

        // En reposo: si hay un Pokémon esperando manda él; si no, el reloj si lo quiere el
        // usuario. El salvaje va delante porque es lo que hay que ver antes de que se vaya.
        !inputs.screenUnlocked -> when {
            inputs.clockEnabled -> DisplayMode.CLOCK
            else -> DisplayMode.OFF
        }

        // Teléfono en uso: sus GIFs.
        else -> DisplayMode.CAROUSEL
    }
}
