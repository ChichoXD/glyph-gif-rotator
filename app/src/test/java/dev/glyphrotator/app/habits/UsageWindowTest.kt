package dev.glyphrotator.app.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * La regla que faltaba: **nadie puede haber mirado la pantalla más minutos de los que lleva
 * teniendo el día**.
 *
 * Sale de un caso real, no de un supuesto. En el móvil se leyó esto en las preferencias:
 *
 * ```
 * <long name="on_since" value="0" />
 * <int  name="min_20686" value="1032" />
 * ```
 *
 * 1032 minutos son 17 h 12, guardados a las 11:16 de la mañana, cuando solo habían pasado 671
 * desde medianoche. Y con `on_since` a cero: no era un tramo abierto mal contado, era un número
 * imposible ya escrito en disco.
 *
 * La causa está explicada en [UsageStore.closeScreenOnPeriod]: `ACTION_SCREEN_ON/OFF` solo llegan
 * con el servicio vivo, pero la marca de apertura vive en disco para sobrevivir a que lo maten
 * (fallo 12). Si muere con la pantalla encendida y la pantalla se apaga mientras está muerto, al
 * revivir se apunta como pantalla encendida todo el rato que estuvo muerto.
 *
 * Se prueba con una zona fija para que no dependa de dónde se ejecute.
 */
class UsageWindowTest {

    private val zone = ZoneId.of("Europe/Madrid")

    /** Milisegundos de una fecha y hora concretas en esa zona. */
    private fun at(date: String, hour: Int, minute: Int): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun day(date: String): Int = LocalDate.parse(date).toEpochDay().toInt()

    @Test
    fun `el caso real del movil se queda en lo posible`() {
        val now = at("2026-08-22", 11, 16)
        val today = day("2026-08-22")

        val capped = UsageWindow.capToDay(today, 1032, now, zone)

        assertEquals("solo han pasado 676 minutos desde medianoche", 676, capped)
        assertTrue("no puede superar lo transcurrido", capped < 1032)
    }

    @Test
    fun `un dia pasado admite las 24 horas enteras`() {
        val now = at("2026-08-22", 11, 16)
        val ayer = day("2026-08-21")

        assertEquals(1440, UsageWindow.minutesElapsedIn(ayer, now, zone))
        // Un día entero pegado a la pantalla es raro, pero no imposible: no se recorta.
        assertEquals(1440, UsageWindow.capToDay(ayer, 1440, now, zone))
        assertEquals(900, UsageWindow.capToDay(ayer, 900, now, zone))
    }

    @Test
    fun `un dia futuro no admite ni un minuto`() {
        val now = at("2026-08-22", 11, 16)
        val manana = day("2026-08-23")

        assertEquals(0, UsageWindow.capToDay(manana, 500, now, zone))
    }

    @Test
    fun `a medianoche el dia todavia no tiene minutos`() {
        val now = at("2026-08-22", 0, 0)
        assertEquals(0, UsageWindow.minutesElapsedIn(day("2026-08-22"), now, zone))
    }

    /**
     * El otro medio fallo: el tramo abierto se sumaba entero al contador de hoy aunque hubiera
     * empezado ayer. Y como al cerrarse se apunta al día en que **empezó**, las mismas horas
     * acababan contadas dos veces, en dos días distintos.
     */
    @Test
    fun `del tramo abierto solo cuenta la parte de hoy`() {
        val ayerPorLaTarde = at("2026-08-21", 18, 0)
        val now = at("2026-08-22", 11, 16)

        val minutos = UsageWindow.openMinutesWithinToday(ayerPorLaTarde, now, zone)

        assertEquals("desde medianoche, no desde las 18:00 de ayer", 676, minutos)
    }

    @Test
    fun `un tramo abierto hoy cuenta desde donde empezo`() {
        val estaManana = at("2026-08-22", 9, 0)
        val now = at("2026-08-22", 11, 16)

        assertEquals(136, UsageWindow.openMinutesWithinToday(estaManana, now, zone))
    }

    /**
     * Una marca del futuro solo puede venir de que alguien haya movido la hora del sistema. No hay
     * forma de saber cuánto duró ese tramo: devolver 0 es lo honesto, inventar es lo que metió las
     * 17 h.
     */
    @Test
    fun `una marca del futuro no inventa minutos`() {
        val now = at("2026-08-22", 11, 16)
        val futuro = at("2026-08-22", 23, 0)

        assertEquals(0, UsageWindow.openMinutesWithinToday(futuro, now, zone))
    }

    @Test
    fun `sin tramo abierto no hay nada que contar`() {
        val now = at("2026-08-22", 11, 16)
        assertEquals(0, UsageWindow.openMinutesWithinToday(0L, now, zone))
    }

    @Test
    fun `el recorte nunca devuelve negativos`() {
        val now = at("2026-08-22", 11, 16)
        assertEquals(0, UsageWindow.capToDay(day("2026-08-22"), -50, now, zone))
    }
}
