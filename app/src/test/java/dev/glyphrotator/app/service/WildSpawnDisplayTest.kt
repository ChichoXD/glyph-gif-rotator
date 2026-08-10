package dev.glyphrotator.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El Pokémon salvaje aparece con la pantalla apagada, así que compite por el mismo hueco que
 * el reloj en reposo. Aquí se fija quién gana en cada caso.
 */
class WildSpawnDisplayTest {

    private fun decide(inputs: DisplayInputs) = DisplayDecider.decide(inputs)

    @Test
    fun `en reposo el salvaje manda sobre el reloj`() {
        // Es lo que hay que ver antes de que se vaya; el reloj está siempre.
        val inputs = DisplayInputs(wildSpawnWaiting = true, clockEnabled = true, screenUnlocked = false)
        assertEquals(DisplayMode.WILD_POKEMON, decide(inputs))
    }

    @Test
    fun `sin reloj activado tambien se enseña`() {
        val inputs = DisplayInputs(wildSpawnWaiting = true, clockEnabled = false, screenUnlocked = false)
        assertEquals(DisplayMode.WILD_POKEMON, decide(inputs))
    }

    @Test
    fun `se sigue viendo con el telefono en uso`() {
        // Se captura con el botón físico, no al desbloquear: si desapareciera de la Matrix al
        // encender la pantalla, no habría forma de saber que hay uno esperando.
        val inputs = DisplayInputs(wildSpawnWaiting = true, screenUnlocked = true)
        assertEquals(DisplayMode.WILD_POKEMON, decide(inputs))
    }

    @Test
    fun `gana a la musica y a la carga`() {
        // El salvaje se va si no lo recoges; la música y la carga siguen ahí después.
        assertEquals(
            DisplayMode.WILD_POKEMON,
            decide(DisplayInputs(wildSpawnWaiting = true, musicPlaying = true))
        )
        assertEquals(
            DisplayMode.WILD_POKEMON,
            decide(DisplayInputs(wildSpawnWaiting = true, plugFlashActive = true))
        )
    }

    @Test
    fun `la bateria critica lo apaga todo, tambien al salvaje`() {
        val inputs = DisplayInputs(wildSpawnWaiting = true, criticalBattery = true)
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    @Test
    fun `con la rotacion apagada no se enseña`() {
        // El interruptor principal manda: si está apagado, la Matrix no se enciende sola.
        val inputs = DisplayInputs(wildSpawnWaiting = true, rotationEnabled = false)
        assertEquals(DisplayMode.OFF, decide(inputs))
    }

    @Test
    fun `una notificacion o GlyphMuseum le ceden el paso al sistema`() {
        assertEquals(
            DisplayMode.OFF,
            decide(DisplayInputs(wildSpawnWaiting = true, notificationFlashActive = true))
        )
        assertEquals(
            DisplayMode.OFF,
            decide(DisplayInputs(wildSpawnWaiting = true, externalGlyphAppActive = true))
        )
    }
}
