package dev.glyphrotator.app.pokemon.spawn

import android.content.Context
import java.util.Calendar

/**
 * Lleva la cuenta de cómo dormiste, para que [SleepSchedule] tenga algo que puntuar.
 *
 * Hasta ahora la calidad del sueño entraba en las apariciones como un 0 fijo: los
 * multiplicadores existían pero no se activaban jamás. Esto es lo que faltaba — medir.
 *
 * No hace falta ningún sensor: **el propio teléfono ya lo dice**. Un tramo largo de pantalla
 * apagada de madrugada es dormir, y a qué hora empezó ese tramo es a qué hora te acostaste. Es
 * menos exacto que una pulsera, pero no pide permisos, no gasta batería y no se puede hacer
 * trampa sin dejar el móvil quieto de verdad, que es justo lo que el juego quiere premiar.
 */
class SleepTracker(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Hora de acostarse, en minutos desde medianoche. Ajustable. */
    var bedtimeMinutes: Int
        get() = prefs.getInt(KEY_BEDTIME, SleepSchedule.DEFAULT_BEDTIME_MINUTES)
        set(value) = prefs.edit().putInt(KEY_BEDTIME, value.coerceIn(0, 24 * 60 - 1)).apply()

    /** Cuánto quieres dormir, en minutos. */
    var goalMinutes: Int
        get() = prefs.getInt(KEY_GOAL, SleepSchedule.DEFAULT_GOAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_GOAL, value.coerceAtLeast(60)).apply()

    /**
     * Cuándo se apagó la pantalla, en disco.
     *
     * Estaba en memoria, dentro del servicio, y por eso **no se registró ni una sola noche**:
     * Android mata el servicio en algún momento de la madrugada, al revivir el campo valía cero
     * y al encender la pantalla no había ningún tramo que apuntar. Se perdía la noche entera, y
     * con ella el entrenamiento del compañero.
     *
     * Es la misma lección que ya estaba aprendida con las apariciones salvajes: lo que tiene que
     * sobrevivir a la noche no puede vivir en memoria.
     */
    var screenOffSinceMillis: Long
        get() = prefs.getLong(KEY_SCREEN_OFF_SINCE, 0L)
        set(value) = prefs.edit().putLong(KEY_SCREEN_OFF_SINCE, value).apply()

    /** Minutos de ese tramo que ya se le han pagado al compañero. */
    var trainedMinutes: Int
        get() = prefs.getInt(KEY_TRAINED_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_TRAINED_MINUTES, value).apply()

    /** Minutos que dormiste la última noche registrada. */
    val lastNightMinutes: Int get() = prefs.getInt(KEY_LAST_MINUTES, 0)

    /** Cuánto te pasaste de tu hora al acostarte, en minutos. */
    val lastNightLateMinutes: Int get() = prefs.getInt(KEY_LAST_LATE, 0)

    /** Noches registradas y suma de calidades, para la media. */
    val nightsRecorded: Int get() = prefs.getInt(KEY_NIGHTS, 0)

    /**
     * Minutos dormidos cada una de las últimas [days] noches, de la más antigua a anoche.
     *
     * Antes solo se guardaba la última noche y el resto se perdía, así que no había forma de
     * pintar una gráfica ni de decir "esta semana has dormido menos". Ahora cada noche va a su
     * propia clave, igual que el agua y el uso del teléfono.
     */
    fun minutesHistory(days: Int = HISTORY_NIGHTS): List<Int> {
        val today = dev.glyphrotator.app.habits.HabitRules.today()
        return (days - 1 downTo 0).map { offset -> prefs.getInt(nightKey(today - offset), 0) }
    }

    fun averageMinutes(days: Int): Int {
        val nights = minutesHistory(days).filter { it > 0 }
        return if (nights.isEmpty()) 0 else nights.sum() / nights.size
    }

    private fun nightKey(day: Int) = "night_$day"
    val bestQuality: Float get() = prefs.getFloat(KEY_BEST, 0f)
    val averageQuality: Float
        get() = if (nightsRecorded == 0) 0f else prefs.getFloat(KEY_QUALITY_SUM, 0f) / nightsRecorded

    /** La calidad de la última noche, que es la que está premiando ahora mismo. */
    val currentQuality: Float
        get() = SleepSchedule.quality(lastNightMinutes, lastNightLateMinutes, goalMinutes)

    /**
     * Apunta un tramo de pantalla apagada, y devuelve true si contaba como haber dormido.
     *
     * Solo cuentan los tramos **largos que pisan la franja de noche**: dejar el móvil en la
     * mesa toda la tarde no es dormir, y una noche no se puede sumar a trozos porque entonces
     * levantarse cinco veces daría lo mismo que dormir del tirón.
     *
     * De cada noche se queda el tramo más largo, no la suma, por lo mismo.
     */
    fun recordScreenOffPeriod(
        startMillis: Long,
        endMillis: Long,
        calendar: Calendar = Calendar.getInstance(),
    ): Boolean {
        // La noche se identifica por el día en que **termina**: acostarse a las 23:30 del lunes
        // y a la 1:00 del martes son la misma noche, y contarlas por separado daría dos.
        calendar.timeInMillis = endMillis
        val nightId = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)

        calendar.timeInMillis = startMillis
        val startMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        // ¿Este tramo continúa la noche que ya estaba en marcha, o empieza una nueva?
        //
        // Continuarla es lo que arregla el contador: un vistazo al móvil de madrugada enciende la
        // pantalla, cierra el tramo y abría una noche nueva, y como solo sobrevivía el tramo más
        // largo, la mitad de la noche se tiraba. Ver SleepWindow.mergesIntoSameNight.
        val spanId = prefs.getInt(KEY_NIGHT_SPAN_ID, 0)
        val spanStart = prefs.getLong(KEY_NIGHT_START, 0L)
        val spanEnd = prefs.getLong(KEY_NIGHT_END, 0L)
        val continues = spanId == nightId &&
            spanStart in 1 until startMillis &&
            SleepWindow.mergesIntoSameNight(spanEnd, startMillis)

        // Una noche nueva sí tiene que empezar dentro de la franja; una que continúa, no —a las
        // 6:00 ya no se puede "empezar a dormir", pero sí seguir durmiendo.
        if (!continues && !startsAtNight(startMinuteOfDay)) return false

        val effectiveStart = if (continues) spanStart else startMillis

        // El tramo se recuerda **aunque todavía no dé para una noche**. Esa es la otra mitad del
        // arreglo: si te acuestas a las 23:30 y te despiertas a las 2:00, ese trozo solo son dos
        // horas y media —no llega a noche— pero es cuando te acostaste. Sin guardarlo, el tramo
        // de las 2:00 a las 8:00 empezaría de cero y volvería a faltar la primera parte.
        prefs.edit()
            .putInt(KEY_NIGHT_SPAN_ID, nightId)
            .putLong(KEY_NIGHT_START, effectiveStart)
            .putLong(KEY_NIGHT_END, endMillis)
            .apply()

        val minutes = ((endMillis - effectiveStart) / 60_000L).toInt()
        // Los dos lados: menos de tres horas es una siesta, más de dieciséis es el móvil apagado.
        // El techo faltaba y por eso entraban fines de semana enteros como una noche. Ver
        // SleepWindow.isPlausibleSleep y el fallo 26, que es este mismo por el otro lado.
        if (!SleepWindow.isPlausibleSleep(minutes)) return false

        val sameNight = prefs.getInt(KEY_LAST_NIGHT_ID, 0) == nightId
        if (sameNight && minutes <= lastNightMinutes) return false

        // La hora de acostarse es la del principio de la noche entera, no la del último tramo:
        // si no, despertarse a las 4:00 contaría como haberse acostado a las 4:00 y la
        // puntualidad se hundiría por dormir del tirón hasta esa hora.
        calendar.timeInMillis = effectiveStart
        val bedMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val late = minutesLate(bedMinuteOfDay)
        val quality = SleepSchedule.quality(minutes, late, goalMinutes)

        val editor = prefs.edit()
            .putInt(KEY_LAST_NIGHT_ID, nightId)
            .putInt(KEY_LAST_MINUTES, minutes)
            .putInt(KEY_LAST_LATE, late)
            // Y en su noche concreta, para la gráfica. La noche se apunta en el día en que
            // **termina**, que es como se cuenta al despertar: "anoche dormí siete horas".
            .putInt(
                nightKey(
                    dev.glyphrotator.app.habits.HabitRules.today(
                        java.time.Instant.ofEpochMilli(endMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    )
                ),
                minutes
            )

        if (sameNight) {
            // Se corrige la noche en curso: se cambia lo que ya se había apuntado por lo nuevo,
            // en vez de contarla dos veces.
            val previous = SleepSchedule.quality(lastNightMinutes, lastNightLateMinutes, goalMinutes)
            editor.putFloat(KEY_QUALITY_SUM, prefs.getFloat(KEY_QUALITY_SUM, 0f) - previous + quality)
        } else {
            editor.putInt(KEY_NIGHTS, nightsRecorded + 1)
            editor.putFloat(KEY_QUALITY_SUM, prefs.getFloat(KEY_QUALITY_SUM, 0f) + quality)
        }
        if (quality > bestQuality) editor.putFloat(KEY_BEST, quality)

        editor.apply()
        return true
    }

    private fun startsAtNight(minuteOfDay: Int): Boolean =
        SleepWindow.startsAtNight(minuteOfDay, bedtimeMinutes)

    private fun minutesLate(startMinuteOfDay: Int): Int =
        SleepWindow.minutesLate(startMinuteOfDay, bedtimeMinutes)

    private companion object {
        const val PREFS_NAME = "glyph_sleep"
        const val KEY_BEDTIME = "bedtime_minutes"
        const val KEY_GOAL = "goal_minutes"
        const val KEY_LAST_MINUTES = "last_minutes"
        const val KEY_LAST_LATE = "last_late"
        const val KEY_LAST_NIGHT_ID = "last_night_id"
        const val KEY_NIGHTS = "nights"
        const val KEY_QUALITY_SUM = "quality_sum"
        const val KEY_BEST = "best_quality"
        const val KEY_SCREEN_OFF_SINCE = "screen_off_since"
        const val KEY_TRAINED_MINUTES = "trained_minutes"

        /** La noche que se está cosiendo ahora mismo: cuál es, cuándo empezó y por dónde va. */
        const val KEY_NIGHT_SPAN_ID = "night_span_id"
        const val KEY_NIGHT_START = "night_span_start"
        const val KEY_NIGHT_END = "night_span_end"

        /** Menos de tres horas seguidas no es dormir, es una siesta o el móvil olvidado. */
        /** Los dos límites viven en [SleepWindow], que es donde se pueden probar. */
        const val MIN_SLEEP_MINUTES = SleepWindow.MIN_SLEEP_MINUTES

        /** Noches que se guardan para la gráfica. */
        const val HISTORY_NIGHTS = 30
    }
}
