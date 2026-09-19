package dev.glyphrotator.app.pokemon.spawn

import java.util.Calendar

/** El tiempo que hace, de lo que depende qué tipos aparecen más. */
enum class Weather { CLEAR, CLOUDY, RAIN, THUNDERSTORM, SNOW }

enum class Season { WINTER, SPRING, SUMMER, AUTUMN }

/**
 * Todo lo que el mundo real aporta a la aparición de un Pokémon.
 *
 * Es un dato plano a propósito: así las reglas se pueden probar con cualquier combinación
 * —Halloween a las tres de la mañana nevando con el 4% de batería— sin tocar el teléfono ni
 * esperar a que llegue la fecha.
 */
data class SpawnConditions(
    /** Minutos seguidos con la pantalla apagada. */
    val minutesScreenOff: Int = 0,
    /** Minutos acumulados desde la última aparición. */
    val minutesSinceLastSpawn: Int = 0,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    /** Hora del día, 0..23. */
    val hourOfDay: Int = 12,
    val season: Season = Season.SPRING,
    val weather: Weather = Weather.CLEAR,
    val isHalloween: Boolean = false,
    val isChristmas: Boolean = false,
    val isFullMoon: Boolean = false,
    /** Cuántas especies distintas se han visto ya. */
    val pokedexCount: Int = 0,
    /** Dentro de la franja de sueño del usuario. */
    val isBedtime: Boolean = false,
    /** Lo bien que se durmió anoche, de 0 a 1. Sube la frecuencia y la calidad. */
    val sleepQuality: Float = 0f,
    /**
     * Cuántos tienes ya de cada especie.
     *
     * Sirve para que lo repetido salga menos sin dejar de salir: la idea es poder completar
     * la colección entera —cada especie en cada estado— sin que el equipo se llene de copias.
     */
    val ownedBySpecies: Map<Int, Int> = emptyMap(),
) {
    /** De noche de 20:00 a 06:00, igual que en el original. */
    val isNight: Boolean get() = hourOfDay < 6 || hourOfDay >= 20

    val isDay: Boolean get() = hourOfDay in 6..17

    companion object {

        /** Halloween: la semana antes del 31 de octubre, contando el propio día. */
        fun isHalloween(calendar: Calendar): Boolean {
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            return month == Calendar.OCTOBER && day in 25..31
        }

        /** Navidad: del 20 de diciembre al 2 de enero. */
        fun isChristmas(calendar: Calendar): Boolean {
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            return (month == Calendar.DECEMBER && day >= 20) || (month == Calendar.JANUARY && day <= 2)
        }

        fun seasonOf(calendar: Calendar): Season = when (calendar.get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Season.WINTER
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Season.SPRING
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Season.SUMMER
            else -> Season.AUTUMN
        }

        /**
         * Luna llena, con el método de los ciclos sinódicos.
         *
         * No hace falta precisión astronómica: basta con acertar la noche, así que se cuenta
         * desde una luna llena conocida y se mira si el ciclo está lo bastante cerca del medio.
         */
        fun isFullMoon(timeMillis: Long): Boolean {
            val daysSinceKnownFullMoon = (timeMillis - KNOWN_FULL_MOON_MILLIS) / DAY_MILLIS.toDouble()
            val phase = ((daysSinceKnownFullMoon % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS
            return phase < FULL_MOON_WINDOW_DAYS || phase > SYNODIC_DAYS - FULL_MOON_WINDOW_DAYS
        }

        /** 6 de enero de 2023, 23:08 UTC: luna llena verificable. */
        private const val KNOWN_FULL_MOON_MILLIS = 1_673_046_480_000L
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val SYNODIC_DAYS = 29.530588853
        private const val FULL_MOON_WINDOW_DAYS = 0.6
    }
}
