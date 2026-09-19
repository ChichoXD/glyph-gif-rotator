package dev.glyphrotator.app.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBonusTest {

    /**
     * Lo que hace que el sistema **obligue** en vez de solo premiar: incumplir frena de verdad.
     * Con la curva de 300 EXP, ×0,21 convierte una noche entera en menos de un nivel.
     */
    @Test
    fun `incumplirlo todo frena en seco`() {
        val multiplier = DailyBonus.trainingMultiplier(
            sleepQuality = 0f,
            waterGoalMet = false,
            habitsKept = 0,
            habitsTotal = 3,
        )
        assertTrue("debería penalizar, dio $multiplier", multiplier < 0.3f)
        assertEquals(DailyBonus.MIN_MULTIPLIER, multiplier, 0.001f)
    }

    @Test
    fun `cumplirlo todo acelera de verdad`() {
        val multiplier = DailyBonus.trainingMultiplier(1f, true, 3, 3)

        assertTrue("debería acelerar, dio $multiplier", multiplier > 2.5f)
        // La diferencia entre cuidarse y no tiene que ser enorme, no cosmética.
        assertTrue(multiplier / DailyBonus.MIN_MULTIPLIER > 10f)
    }

    /**
     * Lo importante del diseño: no tener hábitos apuntados **no penaliza**. Si penalizara,
     * estrenar la app sería empezar con un castigo por no usar una función.
     */
    @Test
    fun `no tener habitos apuntados no resta`() {
        assertEquals(1f, DailyBonus.habitFactor(kept = 0, total = 0), 0.001f)
    }

    @Test
    fun `los habitos suman en proporcion a los cumplidos`() {
        val none = DailyBonus.habitFactor(0, 4)
        val half = DailyBonus.habitFactor(2, 4)
        val all = DailyBonus.habitFactor(4, 4)

        // Apuntarte cuatro y no cumplir ninguno penaliza: es lo que da sentido a apuntarlos.
        assertTrue("no cumplir nada debería frenar, dio $none", none < 1f)
        assertTrue(half > none)
        assertTrue(all > half)
        // A medias tiene que caer justo en medio: es lo que hace que un mal día no borre el
        // esfuerzo de los otros hábitos.
        assertEquals((none + all) / 2f, half, 0.001f)
    }

    @Test
    fun `las tres cosas se multiplican entre si`() {
        val quality = 1f
        val streak = 60 // pasado el techo, para llegar al máximo
        val expected = DailyBonus.sleepFactor(quality) *
            DailyBonus.waterFactor(true) *
            DailyBonus.habitFactor(3, 3) *
            DailyBonus.streakFactor(streak)

        val multiplier = DailyBonus.trainingMultiplier(quality, true, 3, 3, streak)

        assertEquals(expected, multiplier, 0.001f)
        assertEquals(DailyBonus.MAX_MULTIPLIER, multiplier, 0.001f)
    }

    /** Estrenar la app sin hábitos apuntados no puede salir penalizado. */
    @Test
    fun `sin habitos apuntados solo cuentan sueno y agua`() {
        val multiplier = DailyBonus.trainingMultiplier(1f, true, 0, 0)
        val expected = DailyBonus.sleepFactor(1f) * DailyBonus.waterFactor(true)

        assertEquals(expected, multiplier, 0.001f)
    }

    /**
     * Cuidarse en las tres cosas tiene que valer más que volcarse en una sola: es toda la
     * gracia de que se multipliquen en vez de sumarse.
     */
    @Test
    fun `repartirse en las tres rinde mas que volcarse en una`() {
        val onlySleep = DailyBonus.trainingMultiplier(1f, false, 0, 3)
        val spread = DailyBonus.trainingMultiplier(0.5f, true, 2, 3)

        assertTrue(spread > onlySleep)
    }

    /**
     * Sin esto la racha era un número decorativo: treinta días seguidos valían lo mismo que el
     * primero. Que crezca poco a poco es lo que hace que romperla duela.
     */
    @Test
    fun `la racha suma y crece poco a poco`() {
        val none = DailyBonus.streakFactor(0)
        val week = DailyBonus.streakFactor(7)
        val month = DailyBonus.streakFactor(30)

        assertEquals(1f, none, 0.001f)
        assertTrue(week > none)
        assertTrue(month > week)
    }

    @Test
    fun `la racha tiene techo`() {
        assertEquals(DailyBonus.streakFactor(30), DailyBonus.streakFactor(365), 0.001f)
    }

    @Test
    fun `el agua es todo o nada`() {
        assertTrue(DailyBonus.waterFactor(false) < 1f)
        assertTrue(DailyBonus.waterFactor(true) > 1f)
    }

    // ---- Día perfecto: sale de aquí el objeto por hábitos --------------------------------

    @Test
    fun `las tres cosas cumplidas es un dia perfecto`() {
        assertTrue(
            DailyBonus.isPerfectDay(
                sleepQuality = 1f, waterGoalMet = true, habitsKept = 3, habitsTotal = 3
            )
        )
    }

    /**
     * A diferencia del multiplicador de experiencia, aquí no hay término medio: 99 % de calidad
     * de sueño no es un día perfecto, es casi uno. Un logro que se pudiera conseguir "casi" no
     * sería un logro.
     */
    @Test
    fun `casi perfecto no es perfecto`() {
        assertFalse(
            DailyBonus.isPerfectDay(
                sleepQuality = 0.99f, waterGoalMet = true, habitsKept = 3, habitsTotal = 3
            )
        )
        assertFalse(
            DailyBonus.isPerfectDay(
                sleepQuality = 1f, waterGoalMet = false, habitsKept = 3, habitsTotal = 3
            )
        )
        assertFalse(
            DailyBonus.isPerfectDay(
                sleepQuality = 1f, waterGoalMet = true, habitsKept = 2, habitsTotal = 3
            )
        )
    }

    /** Sin hábitos apuntados no se juzga esa parte, igual que en `habitFactor`. */
    @Test
    fun `sin habitos apuntados el dia puede ser perfecto igual`() {
        assertTrue(
            DailyBonus.isPerfectDay(
                sleepQuality = 1f, waterGoalMet = true, habitsKept = 0, habitsTotal = 0
            )
        )
    }
}
