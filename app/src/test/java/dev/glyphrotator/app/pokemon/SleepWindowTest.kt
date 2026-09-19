package dev.glyphrotator.app.pokemon

import dev.glyphrotator.app.pokemon.spawn.SleepWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La franja de noche, que es donde se esconden los fallos: todo esto cruza la medianoche.
 */
class SleepWindowTest {

    private val bedtime = 23 * 60 + 30 // 23:30

    @Test
    fun `acostarse a la hora cuenta`() {
        assertTrue(SleepWindow.startsAtNight(23 * 60 + 30, bedtime))
    }

    @Test
    fun `acostarse un poco antes tambien cuenta`() {
        assertTrue(SleepWindow.startsAtNight(22 * 60 + 45, bedtime))
    }

    @Test
    fun `de madrugada sigue contando`() {
        assertTrue(SleepWindow.startsAtNight(1 * 60, bedtime))
        assertTrue(SleepWindow.startsAtNight(4 * 60 + 59, bedtime))
    }

    @Test
    fun `una siesta de tarde no cuenta`() {
        assertFalse(SleepWindow.startsAtNight(16 * 60, bedtime))
        assertFalse(SleepWindow.startsAtNight(9 * 60, bedtime))
    }

    @Test
    fun `pasadas las cinco ya no es dormir`() {
        assertFalse(SleepWindow.startsAtNight(5 * 60 + 30, bedtime))
    }

    /**
     * El caso que motivó sacar esto a funciones aparte: las 00:30 con la hora puesta a las 23:30
     * son **una hora tarde**, no veintitrés horas antes.
     */
    @Test
    fun `pasada la medianoche el retraso se cuenta bien`() {
        assertEquals(60, SleepWindow.minutesLate(30, bedtime))
        assertEquals(150, SleepWindow.minutesLate(2 * 60, bedtime))
    }

    @Test
    fun `llegar a tiempo o antes no penaliza`() {
        assertEquals(0, SleepWindow.minutesLate(23 * 60 + 30, bedtime))
        assertEquals(0, SleepWindow.minutesLate(22 * 60, bedtime))
    }

    @Test
    fun `con hora de acostarse temprana la ventana no cruza medianoche`() {
        val earlyBedtime = 2 * 60 // 02:00, un horario raro pero válido

        assertTrue(SleepWindow.startsAtNight(2 * 60, earlyBedtime))
        assertTrue(SleepWindow.startsAtNight(1 * 60 + 15, earlyBedtime))
        assertFalse(SleepWindow.startsAtNight(20 * 60, earlyBedtime))
    }

    @Test
    fun `el retraso nunca sale negativo ni absurdo`() {
        for (minute in 0 until SleepWindow.DAY_MINUTES) {
            val late = SleepWindow.minutesLate(minute, bedtime)
            assertTrue("minuto $minute dio $late", late in 0..(SleepWindow.DAY_MINUTES / 2))
        }
    }

    // ---- Cuánto puede durar una noche -------------------------------------------------

    @Test
    fun `una noche normal cuenta`() {
        assertTrue(SleepWindow.isPlausibleSleep(7 * 60))
        assertTrue(SleepWindow.isPlausibleSleep(SleepWindow.MIN_SLEEP_MINUTES))
        assertTrue(SleepWindow.isPlausibleSleep(SleepWindow.MAX_SLEEP_MINUTES))
    }

    @Test
    fun `una siesta no es una noche`() {
        assertFalse(SleepWindow.isPlausibleSleep(90))
        assertFalse(SleepWindow.isPlausibleSleep(SleepWindow.MIN_SLEEP_MINUTES - 1))
    }

    /**
     * El lado que faltaba y por el que entraba basura.
     *
     * El tramo se mide desde una marca que vive en disco para sobrevivir a que maten el servicio, y
     * `ACTION_SCREEN_ON` solo llega con el servicio vivo. Un fin de semana con el móvil en un cajón
     * daba sesenta y tantas horas seguidas, que entraban como una noche: la pantalla decía "Anoche
     * 66 h 00 min" y la media de la ventana se quedaba contaminada.
     */
    @Test
    fun `un fin de semana con el movil apagado no es una noche`() {
        assertFalse("66 horas no es dormir", SleepWindow.isPlausibleSleep(66 * 60))
        assertFalse(SleepWindow.isPlausibleSleep(SleepWindow.MAX_SLEEP_MINUTES + 1))
        assertFalse(SleepWindow.isPlausibleSleep(3 * 24 * 60))
    }

    @Test
    fun `un tramo absurdo o negativo no cuela`() {
        assertFalse(SleepWindow.isPlausibleSleep(0))
        assertFalse(SleepWindow.isPlausibleSleep(-120))
        assertFalse(SleepWindow.isPlausibleSleep(Int.MAX_VALUE))
    }

    // -------------------------------------------------------------------------------------
    // Coser la noche. El contador medía "el tramo seguido más largo", así que cualquier
    // vistazo al móvil de madrugada la partía en dos y se tiraba el trozo menor.
    // -------------------------------------------------------------------------------------

    private val minuto = 60_000L

    @Test
    fun `mirar el movil un momento no rompe la noche`() {
        // Te despiertas a las 4:00, miras la hora dos minutos, y sigues durmiendo.
        val despertar = 4 * 60 * minuto
        val volverADormir = despertar + 2 * minuto

        assertTrue(SleepWindow.mergesIntoSameNight(despertar, volverADormir))
    }

    @Test
    fun `ir al bano tampoco`() {
        val despertar = 4 * 60 * minuto
        assertTrue(SleepWindow.mergesIntoSameNight(despertar, despertar + 10 * minuto))
        assertTrue(SleepWindow.mergesIntoSameNight(despertar, despertar + 60 * minuto))
    }

    @Test
    fun `pero levantarse de verdad abre una noche nueva`() {
        // Dos horas despierto ya no es un despertar: es que te has levantado.
        val despertar = 6 * 60 * minuto
        assertFalse(SleepWindow.mergesIntoSameNight(despertar, despertar + 120 * minuto))
        assertFalse(
            SleepWindow.mergesIntoSameNight(
                despertar,
                despertar + (SleepWindow.MAX_WAKE_GAP_MINUTES + 1) * minuto
            )
        )
    }

    @Test
    fun `justo en el limite todavia cuenta como la misma noche`() {
        val despertar = 4 * 60 * minuto
        assertTrue(
            SleepWindow.mergesIntoSameNight(
                despertar,
                despertar + SleepWindow.MAX_WAKE_GAP_MINUTES * minuto
            )
        )
    }

    @Test
    fun `sin noche anterior no hay nada que coser`() {
        assertFalse(SleepWindow.mergesIntoSameNight(0L, 4 * 60 * minuto))
        assertFalse(SleepWindow.mergesIntoSameNight(-1L, 4 * 60 * minuto))
    }

    @Test
    fun `un tramo que empieza antes de que acabe el anterior no cuela`() {
        // No debería pasar, pero si dos relojes se cruzan no se puede coser hacia atrás.
        val fin = 4 * 60 * minuto
        assertFalse(SleepWindow.mergesIntoSameNight(fin, fin - 10 * minuto))
    }

    /**
     * El caso real que contó el usuario, en números.
     *
     * Acostarse a las 23:30, mirar el móvil a las 4:00 y levantarse a las 8:00. Antes se
     * apuntaban 4 h 30 min —el tramo más largo de los dos— en vez de las 8 h 30 min que
     * realmente durmió. Y ese número alimenta el multiplicador de entrenamiento del compañero,
     * así que dormir bien salía mal pagado por haber mirado el reloj.
     */
    @Test
    fun `la noche partida se vuelve a juntar entera`() {
        val acostarse = 23 * 60 * minuto + 30 * minuto      // 23:30
        val despertarse = acostarse + 270 * minuto          // 04:00, 4 h 30 min después
        val volverADormir = despertarse + 3 * minuto        // 04:03
        val levantarse = volverADormir + 237 * minuto       // 08:00

        assertTrue(
            "el vistazo de las 4:00 tiene que coserse",
            SleepWindow.mergesIntoSameNight(despertarse, volverADormir)
        )

        val trozoMasLargo = 270
        val nocheEntera = ((levantarse - acostarse) / minuto).toInt()

        assertEquals("la noche entera son 8 h 30 min", 510, nocheEntera)
        assertTrue("y es bastante más que el trozo mayor", nocheEntera > trozoMasLargo)
        assertTrue("y sigue siendo una noche creíble", SleepWindow.isPlausibleSleep(nocheEntera))
    }
}
