package dev.glyphrotator.app.habits

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las cuentas de los hábitos. Se fija un lunes concreto para que las pruebas no dependan del día
 * en que se ejecuten, que si no fallarían solas los domingos.
 */
class HabitRulesTest {

    private val monday = HabitRules.today(LocalDate.of(2026, 8, 10))
    private val wednesday = monday + 2
    private val sunday = monday + 6

    @Test
    fun `la semana empieza en lunes`() {
        assertEquals(monday, HabitRules.weekStart(monday))
        assertEquals(monday, HabitRules.weekStart(wednesday))
        assertEquals(monday, HabitRules.weekStart(sunday))
        assertEquals(monday + 7, HabitRules.weekStart(sunday + 1))
    }

    @Test
    fun `los dias transcurridos van de uno a siete`() {
        assertEquals(1, HabitRules.daysElapsedInWeek(monday))
        assertEquals(3, HabitRules.daysElapsedInWeek(wednesday))
        assertEquals(7, HabitRules.daysElapsedInWeek(sunday))
    }

    /**
     * El reparto del ritmo, que es lo que evita exigirlo todo el lunes o perdonarlo hasta el
     * domingo. Con tres a la semana: el miércoles toca una, el domingo las tres.
     */
    @Test
    fun `lo esperado se reparte a lo largo de la semana`() {
        assertEquals(0, HabitRules.expectedByNow(3, monday))
        assertEquals(1, HabitRules.expectedByNow(3, wednesday))
        assertEquals(3, HabitRules.expectedByNow(3, sunday))
    }

    @Test
    fun `un habito de tres por semana no castiga los dias de descanso`() {
        // Fue el lunes y hoy es miércoles: va al día aunque hoy no lo haya hecho.
        val history = setOf(monday)

        assertTrue(HabitRules.onPace(history, timesPerWeek = 3, day = wednesday))
        assertFalse(HabitRules.onPace(history, timesPerWeek = 3, day = sunday))
    }

    @Test
    fun `un habito diario se cae en cuanto te saltas un dia`() {
        val history = setOf(monday, monday + 1)

        assertTrue(HabitRules.onPace(history, HabitRules.DAILY, day = monday + 1))
        assertFalse(HabitRules.onPace(history, HabitRules.DAILY, day = monday + 2))
    }

    @Test
    fun `la racha diaria cuenta dias seguidos`() {
        val history = setOf(monday, monday + 1, monday + 2)

        assertEquals(3, HabitRules.currentStreak(history, HabitRules.DAILY, day = monday + 2))
    }

    /** Si hoy aún no lo has marcado, la racha de ayer sigue viva: aún tienes el día por delante. */
    @Test
    fun `la racha diaria aguanta hasta el final del dia`() {
        val history = setOf(monday, monday + 1)

        assertEquals(2, HabitRules.currentStreak(history, HabitRules.DAILY, day = monday + 2))
    }

    /**
     * Una racha semanal cuenta **semanas cumplidas**, no días: decir "40 días de racha" yendo al
     * gym tres veces por semana no significaría nada.
     */
    @Test
    fun `la racha semanal cuenta semanas cumplidas`() {
        val previousWeek = monday - 7
        val history = setOf(
            previousWeek, previousWeek + 2, previousWeek + 4, // tres la semana pasada
            monday, monday + 2, monday + 4,                   // tres esta semana
        )

        assertEquals(2, HabitRules.currentStreak(history, timesPerWeek = 3, day = sunday))
    }

    @Test
    fun `sin historial no hay racha`() {
        assertEquals(0, HabitRules.currentStreak(emptySet(), HabitRules.DAILY, day = monday))
        assertEquals(0, HabitRules.currentStreak(emptySet(), 3, day = monday))
    }

    @Test
    fun `el historial viejo se recorta`() {
        val history = setOf(monday, monday - HabitRules.HISTORY_DAYS - 1)

        val trimmed = HabitRules.trimmed(history, monday)

        assertEquals(setOf(monday), trimmed)
    }

    @Test
    fun `cuenta lo hecho en los ultimos dias`() {
        val history = setOf(monday, monday - 5, monday - 40)

        assertEquals(2, HabitRules.doneInLast(history, days = 7, day = monday))
        assertEquals(3, HabitRules.doneInLast(history, days = 60, day = monday))
    }
}
