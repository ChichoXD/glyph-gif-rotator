package dev.glyphrotator.app.pokemon

import dev.glyphrotator.app.pokemon.spawn.SleepSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La nota del sueño y lo que reparte.
 *
 * Importa que el desglose que se enseña en pantalla cuadre con la nota: si los dos trozos
 * —horas y puntualidad— no sumaran lo mismo que devuelve [SleepSchedule.quality], la pantalla
 * estaría explicando un número distinto del que de verdad se aplica.
 */
class SleepQualityTest {

    @Test
    fun `una noche perfecta da la nota maxima`() {
        val quality = SleepSchedule.quality(
            minutesAsleep = SleepSchedule.DEFAULT_GOAL_MINUTES,
            minutesLateToBed = 0,
        )
        assertEquals(1f, quality, 0.001f)
    }

    @Test
    fun `sin dormir no hay premio`() {
        assertEquals(0f, SleepSchedule.quality(0, 0), 0.001f)
        assertEquals(1.0, SleepSchedule.frequencyMultiplier(0f), 0.001)
        assertEquals(1f, SleepSchedule.rarityMultiplier(0f), 0.001f)
    }

    @Test
    fun `el desglose suma exactamente la nota`() {
        val slept = 300
        val late = 45
        val goal = SleepSchedule.DEFAULT_GOAL_MINUTES

        val amount = (slept.toFloat() / goal).coerceIn(0f, 1f)
        val punctuality = (1f - late / SleepSchedule.LATE_TOLERANCE_MINUTES).coerceIn(0f, 1f)
        val breakdown = amount * SleepSchedule.AMOUNT_WEIGHT +
            punctuality * SleepSchedule.PUNCTUALITY_WEIGHT

        assertEquals(SleepSchedule.quality(slept, late, goal), breakdown, 0.001f)
    }

    @Test
    fun `dormir de mas no suma pero trasnochar resta`() {
        val goal = SleepSchedule.DEFAULT_GOAL_MINUTES
        val enough = SleepSchedule.quality(goal, 0, goal)
        val tooMuch = SleepSchedule.quality(goal * 2, 0, goal)
        val lateNight = SleepSchedule.quality(goal, 90, goal)

        assertEquals(enough, tooMuch, 0.001f)
        assertTrue(lateNight < enough)
    }

    @Test
    fun `con la nota maxima se duplica la frecuencia y los raros pesan cuatro veces`() {
        assertEquals(2.0, SleepSchedule.frequencyMultiplier(1f), 0.001)
        assertEquals(4f, SleepSchedule.rarityMultiplier(1f), 0.001f)
    }
}
