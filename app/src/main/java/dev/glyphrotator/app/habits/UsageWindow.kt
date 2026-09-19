package dev.glyphrotator.app.habits

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * La aritmética de días del tiempo de pantalla, fuera del almacén para poder probarla.
 *
 * Es la misma decisión que se tomó con [dev.glyphrotator.app.pokemon.SleepWindow] cuando la cuenta
 * que cruza medianoche daba disparates: dentro de [UsageStore] no hay forma de comprobarla, porque
 * el almacén necesita un `Context` y unas preferencias de verdad. Aquí son funciones puras y se
 * prueban con nueve líneas.
 *
 * La regla que vive aquí es una sola, y es la que faltaba: **nadie puede haber mirado la pantalla
 * más minutos de los que lleva teniendo el día**. Se encontró un día con 1032 minutos —17 h 12—
 * guardados a las 11:16 de la mañana, cuando solo habían pasado 671 desde medianoche.
 */
object UsageWindow {

    const val MINUTES_PER_DAY = 24 * 60

    /** El día (epoch day) al que pertenece un instante, en la zona del teléfono. */
    fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay().toInt()

    fun startOfDayMillis(day: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(day.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Minutos vividos de ese día: el de hoy va por el reloj, los pasados son enteros. */
    fun minutesElapsedIn(day: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = dayOf(nowMillis, zone)
        return when {
            day > today -> 0
            day < today -> MINUTES_PER_DAY
            else -> ((nowMillis - startOfDayMillis(day, zone)) / 60_000L)
                .toInt()
                .coerceIn(0, MINUTES_PER_DAY)
        }
    }

    /** Recorta un total al máximo posible de ese día. Nunca negativo. */
    fun capToDay(day: Int, minutes: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        minutes.coerceIn(0, minutesElapsedIn(day, nowMillis, zone))

    /**
     * Del tramo abierto, solo la parte que cae dentro del día de [nowMillis].
     *
     * Sumarlo entero contaba las horas de ayer en el contador de hoy, y como al cerrarse se apuntan
     * al día en que **empezó**, las mismas horas acababan contadas dos veces en dos días distintos.
     *
     * Un `openSince` en el futuro solo puede venir de un cambio de hora del sistema: devuelve 0, que
     * es lo honesto cuando no se sabe.
     */
    fun openMinutesWithinToday(
        openSince: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        if (openSince <= 0L || openSince > nowMillis) return 0
        val from = maxOf(openSince, startOfDayMillis(dayOf(nowMillis, zone), zone))
        return ((nowMillis - from) / 60_000L).toInt().coerceAtLeast(0)
    }
}
