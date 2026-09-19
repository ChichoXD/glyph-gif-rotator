package dev.glyphrotator.app.habits

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * Cuánto usas el teléfono: minutos de pantalla encendida y desbloqueos, por día.
 *
 * No hace falta ningún permiso ni el "Acceso a uso" de Android: el servicio ya sabe cuándo se
 * enciende y se apaga la pantalla porque lo necesita para el juego. Esto solo apunta lo que ya
 * estaba pasando por delante.
 *
 * Se guarda un valor por día en su propia clave, igual que el agua. Es lo más tonto que se puede
 * hacer y también lo más robusto: no hay nada que serializar, cada día se escribe suelto y los
 * viejos caducan solos al dejar de leerse.
 *
 * El tramo abierto —desde que encendiste la pantalla hasta ahora— va **a disco**, no en memoria.
 * Es la misma lección que costó una noche entera de sueño perdida: lo que tiene que sobrevivir a
 * que Android mate el servicio no puede vivir en un campo.
 */
class UsageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Cuándo se encendió la pantalla, o 0 si está apagada. */
    var screenOnSinceMillis: Long
        get() = prefs.getLong(KEY_ON_SINCE, 0L)
        set(value) = prefs.edit().putLong(KEY_ON_SINCE, value).apply()

    /** Minutos de pantalla de hoy, contando el rato que lleve encendida ahora mismo. */
    fun minutesToday(nowMillis: Long = System.currentTimeMillis()): Int {
        val today = HabitRules.today()
        val closed = prefs.getInt(minutesKey(today), 0)
        return capToDay(today, closed + openMinutesWithinToday(nowMillis), nowMillis)
    }

    /**
     * Del tramo abierto, **solo la parte que cae dentro de hoy**.
     *
     * Antes se sumaba entero: un tramo empezado ayer por la tarde metía sus horas de ayer en el
     * contador de hoy, y encima al cerrarse se apuntaban a ayer —[closeScreenOnPeriod] atribuye al
     * día en que empezó—, así que las mismas horas se contaban dos veces en dos días distintos.
     */
    private fun openMinutesWithinToday(nowMillis: Long): Int =
        UsageWindow.openMinutesWithinToday(screenOnSinceMillis, nowMillis)

    fun unlocksToday(): Int = prefs.getInt(unlocksKey(HabitRules.today()), 0)

    /**
     * Minutos de cada uno de los últimos [days] días, del más antiguo al de hoy.
     *
     * Se recorta al leer, no solo al escribir: en disco ya hay días inflados por el fallo que se
     * arregló aquí —se vio uno con 1032 minutos, 17 h, antes de comer—. Recortando en la lectura
     * esos días viejos se enderezan solos sin necesidad de migrar nada.
     */
    fun minutesHistory(days: Int = HISTORY_DAYS, nowMillis: Long = System.currentTimeMillis()): List<Int> {
        val today = HabitRules.today()
        return (days - 1 downTo 0).map { offset ->
            val day = today - offset
            if (offset == 0) {
                minutesToday(nowMillis)
            } else {
                capToDay(day, prefs.getInt(minutesKey(day), 0), nowMillis)
            }
        }
    }

    fun unlocksHistory(days: Int = HISTORY_DAYS): List<Int> {
        val today = HabitRules.today()
        return (days - 1 downTo 0).map { offset -> prefs.getInt(unlocksKey(today - offset), 0) }
    }

    fun averageMinutes(days: Int): Int {
        val history = minutesHistory(days)
        return if (history.isEmpty()) 0 else history.sum() / history.size
    }

    /**
     * Cierra el tramo de pantalla encendida y lo suma al día.
     *
     * El tramo se apunta **en el día en que empezó**, aunque termine pasada la medianoche. Es lo
     * que hace que "ayer usé el móvil tres horas" signifique algo: partirlo en dos daría dos días
     * con hora y media cada uno y ninguno sería verdad.
     */
    fun closeScreenOnPeriod(nowMillis: Long = System.currentTimeMillis()) {
        val since = screenOnSinceMillis
        if (since <= 0L) return
        screenOnSinceMillis = 0L

        // Una marca del futuro solo puede venir de un cambio de hora del sistema. No hay forma de
        // saber cuánto duró ese tramo, y adivinar es lo que metió las 17 h: se tira.
        if (since > nowMillis) return

        val minutes = ((nowMillis - since) / 60_000L).toInt()
        if (minutes <= 0) return

        val day = HabitRules.today(
            Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()).toLocalDate()
        )
        val key = minutesKey(day)
        val total = prefs.getInt(key, 0) + minutes
        prefs.edit().putInt(key, capToDay(day, total, nowMillis)).apply()
    }

    /**
     * Nadie puede haber mirado la pantalla más minutos de los que lleva teniendo el día.
     *
     * Es la regla que faltaba y la que se rompió de verdad: se encontró un día con **1032 minutos
     * —17 h 12— guardados a las 11:16 de la mañana**, cuando solo habían pasado 671 minutos desde
     * medianoche. Un número imposible, no solo alto.
     *
     * De dónde salía: `ACTION_SCREEN_ON` y `ACTION_SCREEN_OFF` **solo se pueden recibir con el
     * servicio vivo** —Android no deja declararlas en el manifest—. La marca de apertura sí vive en
     * disco, a propósito, para que sobreviva a que maten el servicio (fallo 12). Juntando las dos
     * cosas: si el servicio muere con la pantalla encendida y la pantalla se apaga mientras está
     * muerto, nadie cierra el tramo; cuando el servicio revive y por fin llega un
     * `ACTION_SCREEN_OFF`, se apunta como pantalla encendida **todo el rato que estuvo muerto**,
     * que es justo el rato en el que no se estaba mirando nada.
     *
     * El recorte no convierte el dato en exacto —eso pediría un latido periódico que confirmara
     * que la pantalla sigue encendida, y es otro trabajo—: lo convierte en **posible**. Un día
     * completo se queda en 1440; el de hoy, en lo que lleve transcurrido. Es la diferencia entre
     * una gráfica que exagera y una que miente.
     */
    private fun capToDay(day: Int, minutes: Int, nowMillis: Long): Int =
        UsageWindow.capToDay(day, minutes, nowMillis)

    fun registerUnlock() {
        val key = unlocksKey(HabitRules.today())
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun minutesKey(day: Int) = "min_$day"
    private fun unlocksKey(day: Int) = "unlock_$day"

    companion object {
        const val HISTORY_DAYS = HabitRules.HISTORY_DAYS

        private const val PREFS_NAME = "glyph_usage"
        private const val KEY_ON_SINCE = "on_since"
    }
}
