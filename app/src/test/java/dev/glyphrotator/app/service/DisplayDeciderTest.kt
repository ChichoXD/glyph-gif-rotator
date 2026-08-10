package dev.glyphrotator.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayDeciderTest {

    private fun decide(inputs: DisplayInputs) = DisplayDecider.decide(inputs)

    // =====================================================================================
    // El caso que falló de verdad: teléfono en uso pero la app mostrando el reloj
    // =====================================================================================

    /**
     * Regresión: el sondeo de estado hacía `continue` si creía que la pantalla estaba
     * apagada, así que si ese flag se desincronizaba (servicio reiniciado con la pantalla
     * apagada, broadcast perdido) la app se quedaba en el reloj para siempre aunque el
     * teléfono llevara horas desbloqueado.
     */
    @Test
    fun `telefono desbloqueado manda carrusel, no reloj`() {
        val inputs = DisplayInputs(screenUnlocked = true, clockEnabled = true)
        assertEquals(DisplayMode.CAROUSEL, decide(inputs))
    }

    @Test
    fun `bloqueado con reloj activado muestra el reloj`() {
        val inputs = DisplayInputs(screenUnlocked = false, clockEnabled = true)
        assertEquals(DisplayMode.CLOCK, decide(inputs))
    }

    @Test
    fun `bloqueado sin reloj deja la matrix apagada`() {
        val inputs = DisplayInputs(screenUnlocked = false, clockEnabled = false)
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    // =====================================================================================
    // Prioridades
    // =====================================================================================

    /**
     * Regresión: el toy pinta con setMatrixFrame y el servicio con setAppMatrixFrame. Con
     * los dos a la vez se veían 2 fps del toy compitiendo con 7 fps del carrusel, y el
     * líquido del botón físico quedaba tapado.
     */
    @Test
    fun `mientras el toy muestra el liquido, el servicio no pinta nada`() {
        val inputs = DisplayInputs(
            ownToyActive = true,
            musicPlaying = true,
            screenUnlocked = true,
            clockEnabled = true
        )
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    /**
     * El toy solo reclama la Matrix mientras dura el líquido. Si la reclamara por estar
     * simplemente seleccionado en el botón, dejarlo elegido apagaría el carrusel para
     * siempre.
     */
    @Test
    fun `al acabar el liquido el servicio recupera el mando`() {
        val inputs = DisplayInputs(ownToyActive = false, screenUnlocked = true)
        assertEquals(DisplayMode.CAROUSEL, decide(inputs))
    }

    @Test
    fun `otra app de glyph en primer plano gana a todo lo demas`() {
        val inputs = DisplayInputs(
            externalGlyphAppActive = true,
            musicPlaying = true,
            plugFlashActive = true,
            screenUnlocked = true,
            clockEnabled = true
        )
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    @Test
    fun `bateria critica apaga aunque haya musica o carga`() {
        val inputs = DisplayInputs(criticalBattery = true, musicPlaying = true, plugFlashActive = true)
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    @Test
    fun `el destello de carga gana a la musica`() {
        val inputs = DisplayInputs(plugFlashActive = true, musicPlaying = true, screenUnlocked = true)
        assertEquals(DisplayMode.BATTERY_LIQUID, decide(inputs))
    }

    @Test
    fun `la musica gana al carrusel aunque el telefono este en uso`() {
        val inputs = DisplayInputs(musicPlaying = true, screenUnlocked = true)
        assertEquals(DisplayMode.VINYL_SPINNING, decide(inputs))
    }

    @Test
    fun `la musica gana al reloj con la pantalla apagada`() {
        val inputs = DisplayInputs(musicPlaying = true, screenUnlocked = false, clockEnabled = true)
        assertEquals(DisplayMode.VINYL_SPINNING, decide(inputs))
    }

    // =====================================================================================
    // Música
    // =====================================================================================

    @Test
    fun `reproduciendo gira el disco y en pausa se congela`() {
        assertEquals(DisplayMode.VINYL_SPINNING, decide(DisplayInputs(musicPlaying = true)))
        assertEquals(DisplayMode.VINYL_FROZEN, decide(DisplayInputs(musicPaused = true)))
    }

    @Test
    fun `si sonara y estuviera en pausa a la vez, manda reproduciendo`() {
        val inputs = DisplayInputs(musicPlaying = true, musicPaused = true)
        assertEquals(DisplayMode.VINYL_SPINNING, decide(inputs))
    }

    @Test
    fun `sin musica y en uso, vuelve el carrusel`() {
        val inputs = DisplayInputs(musicPlaying = false, musicPaused = false, screenUnlocked = true)
        assertEquals(DisplayMode.CAROUSEL, decide(inputs))
    }

    // =====================================================================================
    // Animaciones puntuales
    // =====================================================================================

    @Test
    fun `la prueba de captura no se interrumpe por musica ni por el reloj`() {
        val inputs = DisplayInputs(
            oneShotActive = true,
            musicPlaying = true,
            screenUnlocked = false,
            clockEnabled = true
        )
        assertEquals(DisplayMode.CAROUSEL, decide(inputs))
    }

    @Test
    fun `el destello de bluetooth se ve con la pantalla apagada`() {
        val inputs = DisplayInputs(bluetoothFlashActive = true, screenUnlocked = false, clockEnabled = true)
        assertEquals(DisplayMode.CAROUSEL, decide(inputs))
    }

    @Test
    fun `pero la bateria critica corta hasta las animaciones puntuales`() {
        val inputs = DisplayInputs(criticalBattery = true, oneShotActive = true, bluetoothFlashActive = true)
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    // =====================================================================================
    // Estado por defecto
    // =====================================================================================

    @Test
    fun `sin nada activo y bloqueado, la matrix queda apagada`() {
        assertEquals(DisplayMode.OFF, decide(DisplayInputs()))
    }

    @Test
    fun `con una notificacion recien llegada le dejamos la matrix al sistema`() {
        val inputs = DisplayInputs(notificationFlashActive = true, screenUnlocked = true)
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    @Test
    fun `la notificacion tambien se impone a la musica y a la carga`() {
        // Si no, la animación del sistema y el vinilo (o el líquido) se pintan a la vez.
        assertEquals(
            DisplayMode.OFF,
            decide(DisplayInputs(notificationFlashActive = true, musicPlaying = true))
        )
        assertEquals(
            DisplayMode.OFF,
            decide(DisplayInputs(notificationFlashActive = true, plugFlashActive = true))
        )
    }

    @Test
    fun `cuando pasa la notificacion se vuelve al carrusel`() {
        val durante = DisplayInputs(notificationFlashActive = true, screenUnlocked = true)
        assertEquals(DisplayMode.OFF, decide(durante))
        assertEquals(DisplayMode.CAROUSEL, decide(durante.copy(notificationFlashActive = false)))
    }

    @Test
    fun `con la rotacion apagada no se enciende nada por su cuenta`() {
        // El servicio puede seguir vivo tras una vista previa, y sus sondeos periodicos
        // volvian a sacar el carrusel como si el interruptor no existiera.
        val apagada = DisplayInputs(rotationEnabled = false, screenUnlocked = true)
        assertEquals(DisplayMode.OFF, decide(apagada))
        assertEquals(DisplayMode.OFF, decide(apagada.copy(musicPlaying = true)))
        assertEquals(DisplayMode.OFF, decide(apagada.copy(plugFlashActive = true)))
        assertEquals(DisplayMode.OFF, decide(apagada.copy(screenUnlocked = false, clockEnabled = true)))
    }

    @Test
    fun `una vista previa se ve aunque la rotacion este apagada`() {
        // Pedir ver un diseno concreto es una orden explicita del usuario.
        assertEquals(
            DisplayMode.CAROUSEL,
            decide(DisplayInputs(rotationEnabled = false, bluetoothFlashActive = true))
        )
        assertEquals(
            DisplayMode.CAROUSEL,
            decide(DisplayInputs(rotationEnabled = false, oneShotActive = true))
        )
    }

    @Test
    fun `con el vinilo apagado la musica no cambia nada`() {
        val sinVinilo = DisplayInputs(vinylEnabled = false, musicPlaying = true)
        assertEquals(DisplayMode.CAROUSEL, decide(sinVinilo.copy(screenUnlocked = true)))
        assertEquals(DisplayMode.CLOCK, decide(sinVinilo.copy(clockEnabled = true)))
        assertEquals(DisplayMode.OFF, decide(sinVinilo))
        assertEquals(DisplayMode.OFF, decide(sinVinilo.copy(musicPlaying = false, musicPaused = true)))
    }

    @Test
    fun `la decision es estable - las mismas entradas dan siempre lo mismo`() {
        val inputs = DisplayInputs(musicPaused = true, screenUnlocked = true, clockEnabled = true)
        val first = decide(inputs)
        repeat(50) { assertEquals(first, decide(inputs)) }
    }
}
