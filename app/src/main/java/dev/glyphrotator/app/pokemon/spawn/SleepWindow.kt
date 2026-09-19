package dev.glyphrotator.app.pokemon.spawn

/**
 * La aritmética de la franja de noche, sin nada de Android.
 *
 * Está separada de [SleepTracker] porque es justo donde se esconden los fallos: todo esto cruza
 * la medianoche, y "las 00:30 son más tarde que las 23:30" es verdad para una persona y mentira
 * para un número. Dentro del almacén no había forma de probarlo; aquí sí.
 */
object SleepWindow {

    const val DAY_MINUTES = 24 * 60

    /** Hasta qué hora se puede empezar a dormir y seguir contando: las 5 de la mañana. */
    const val LATEST_START_MINUTES = 5 * 60

    /** Margen para acostarse antes de la hora sin que deje de contar. */
    const val EARLY_TOLERANCE_MINUTES = 60

    /** Por debajo de tres horas es una siesta, no una noche. */
    const val MIN_SLEEP_MINUTES = 180

    /**
     * Y por encima de dieciséis, no es dormir: es el teléfono apagado.
     *
     * Faltaba este lado. El tramo de pantalla apagada se mide desde una marca que vive **en disco**
     * para sobrevivir a que maten el servicio, y `ACTION_SCREEN_ON` solo llega con el servicio
     * vivo. Un fin de semana con el móvil en un cajón daba un tramo de sesenta y tantas horas que
     * entraba como una noche: "Anoche 66 h 00 min", y la media de la ventana contaminada.
     *
     * Es el mismo par de decisiones que provocó el fallo 26 en el tiempo de pantalla, aquí por el
     * otro lado: allí inflaba lo que miras, aquí lo que duermes.
     */
    const val MAX_SLEEP_MINUTES = 16 * 60

    /**
     * Si un tramo dura lo que puede durar una noche.
     *
     * Se **descarta**, no se recorta. Recortar un tramo de tres días a dieciséis horas inventaría
     * una noche que nadie durmió; devolver false deja esa noche sin dato, que es la verdad.
     */
    fun isPlausibleSleep(minutes: Int): Boolean =
        minutes in MIN_SLEEP_MINUTES..MAX_SLEEP_MINUTES

    /**
     * Cuánto puedes estar despierto a media noche sin que cuente como haberte levantado.
     *
     * Hora y media: mirar la hora, ir al baño o contestar un mensaje caben de sobra; desayunar y
     * volver a la cama, no.
     */
    const val MAX_WAKE_GAP_MINUTES = 90

    /**
     * Si un tramo nuevo de pantalla apagada es **la misma noche** que el anterior.
     *
     * Aquí estaba el fallo del contador de sueño, y no era de aritmética sino de definición: una
     * noche se medía como "el tramo seguido más largo de pantalla apagada", así que **cualquier**
     * vistazo al móvil de madrugada la partía en dos y solo sobrevivía el trozo mayor. Acostarse
     * a las 23:30, mirar la hora a las 4:00 y levantarse a las 8:00 se apuntaba como cuatro horas
     * de sueño, no como ocho y media. Y el juego usa ese número para el multiplicador de
     * entrenamiento, así que dormir bien salía mal pagado por haber mirado el reloj.
     *
     * Una noche es el **rato entre que te acuestas y te levantas**, no el trozo más largo. Los
     * despertares cortos se cosen; los largos abren una noche nueva.
     */
    fun mergesIntoSameNight(previousEndMillis: Long, newStartMillis: Long): Boolean {
        if (previousEndMillis <= 0L || newStartMillis < previousEndMillis) return false
        val gapMinutes = (newStartMillis - previousEndMillis) / 60_000L
        return gapMinutes <= MAX_WAKE_GAP_MINUTES
    }

    /**
     * Si un tramo que empieza a esa hora cuenta como irse a dormir.
     *
     * La ventana va desde una hora antes de tu hora de acostarte hasta las 5 de la mañana. Sin
     * el margen previo, acostarse pronto un día contaría como no haber dormido.
     */
    fun startsAtNight(minuteOfDay: Int, bedtimeMinutes: Int): Boolean {
        val from = (bedtimeMinutes - EARLY_TOLERANCE_MINUTES + DAY_MINUTES) % DAY_MINUTES
        return if (from <= LATEST_START_MINUTES) {
            // Hora de acostarse muy temprana: la ventana no cruza la medianoche.
            minuteOfDay in from..LATEST_START_MINUTES
        } else {
            // El caso normal: empieza por la noche y termina de madrugada.
            minuteOfDay >= from || minuteOfDay <= LATEST_START_MINUTES
        }
    }

    /**
     * Cuánto te pasaste de tu hora, teniendo en cuenta el cambio de día.
     *
     * Acostarse a las 00:30 con la hora puesta a las 23:30 es **una hora tarde**, no veintitrés
     * horas antes. Por eso la diferencia se normaliza al medio día más cercano en vez de
     * restarse a pelo.
     */
    fun minutesLate(startMinuteOfDay: Int, bedtimeMinutes: Int): Int {
        val difference = startMinuteOfDay - bedtimeMinutes
        val normalized = when {
            difference < -DAY_MINUTES / 2 -> difference + DAY_MINUTES
            difference > DAY_MINUTES / 2 -> difference - DAY_MINUTES
            else -> difference
        }
        return normalized.coerceAtLeast(0)
    }
}
