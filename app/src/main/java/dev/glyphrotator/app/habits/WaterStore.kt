package dev.glyphrotator.app.habits

import android.content.Context


/**
 * El agua del día: cuánto llevas, cuánto te propusiste y cuánto suma cada toque.
 *
 * El día se identifica por su fecha y **se comprueba al leer**, no con una alarma a medianoche:
 * las alarmas se pierden si el sistema mata la app, y entonces el contador amanecería con el
 * agua de ayer. Así, la primera lectura del día nuevo ya devuelve cero sola.
 *
 * La recompensa se cobra **al día siguiente** a propósito. Si el bonus se aplicara al momento,
 * lo suyo sería beberse el objetivo de golpe por la mañana; cobrándolo mañana, lo que premia es
 * haber cumplido el día entero.
 */
class WaterStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Cuánto quieres beber al día, en ml. */
    var goalMl: Int
        get() = prefs.getInt(KEY_GOAL, DEFAULT_GOAL_ML)
        set(value) = prefs.edit().putInt(KEY_GOAL, value.coerceIn(MIN_GOAL_ML, MAX_GOAL_ML)).apply()

    /** Cuánto suma cada toque del widget, en ml. Un vaso, una botella, lo que uses. */
    var portionMl: Int
        get() = prefs.getInt(KEY_PORTION, DEFAULT_PORTION_ML)
        set(value) = prefs.edit().putInt(KEY_PORTION, value.coerceIn(MIN_PORTION_ML, MAX_PORTION_ML)).apply()

    /** Lo que llevas hoy. Si ha cambiado el día, es cero. */
    val todayMl: Int
        get() = if (prefs.getInt(KEY_DAY, 0) == today()) prefs.getInt(KEY_TODAY_ML, 0) else 0

    val progress: Float
        get() = (todayMl.toFloat() / goalMl).coerceIn(0f, 1f)

    val goalMetToday: Boolean
        get() = todayMl >= goalMl

    /** Si ayer se cumplió: es lo que decide el bonus de hoy. */
    val goalMetYesterday: Boolean
        get() = prefs.getInt(KEY_LAST_MET_DAY, 0) == yesterday()

    val streak: Int get() = if (streakIsAlive()) prefs.getInt(KEY_STREAK, 0) else 0
    val bestStreak: Int get() = prefs.getInt(KEY_BEST_STREAK, 0)
    val daysMet: Int get() = prefs.getInt(KEY_DAYS_MET, 0)

    /**
     * Cuánto se bebió cada uno de los últimos [days] días, del más antiguo al de hoy.
     *
     * Se guarda un valor por día en su propia clave. Es la forma más tonta de hacerlo y también
     * la más robusta: no hay que serializar nada, cada día se escribe una vez y los viejos
     * caducan solos al no volver a leerse.
     */
    fun historyMl(days: Int = HISTORY_DAYS): List<Int> {
        val today = today()
        return (days - 1 downTo 0).map { offset -> prefs.getInt(dayKey(today - offset), 0) }
    }

    /** Días con el objetivo cumplido en los últimos [days]. */
    fun daysMetIn(days: Int): Int = historyMl(days).count { it >= goalMl }

    /** La media diaria de los últimos [days], contando también los días a cero. */
    fun averageMl(days: Int): Int {
        val history = historyMl(days)
        return if (history.isEmpty()) 0 else history.sum() / history.size
    }

    /** Suma una ración y devuelve el total del día. */
    fun addPortion(): Int = add(portionMl)

    fun add(amountMl: Int): Int {
        if (amountMl <= 0) return todayMl

        val wasMet = goalMetToday
        val total = (todayMl + amountMl).coerceAtMost(MAX_DAILY_ML)

        val editor = prefs.edit()
            .putInt(KEY_DAY, today())
            .putInt(KEY_TODAY_ML, total)
            // Y en su día concreto, para el historial y las medias.
            .putInt(dayKey(today()), total)

        // El día se apunta como cumplido una sola vez, justo al cruzar el objetivo: seguir
        // bebiendo después no debería inflar la racha ni las estadísticas.
        if (!wasMet && total >= goalMl) {
            val continued = prefs.getInt(KEY_LAST_MET_DAY, 0) == yesterday()
            val streak = if (continued) prefs.getInt(KEY_STREAK, 0) + 1 else 1
            editor.putInt(KEY_LAST_MET_DAY, today())
                .putInt(KEY_STREAK, streak)
                .putInt(KEY_DAYS_MET, daysMet + 1)
            if (streak > bestStreak) editor.putInt(KEY_BEST_STREAK, streak)
        }

        editor.apply()
        return total
    }

    /**
     * Quita una ración, para cuando has dado un toque de más.
     *
     * No deshace el "día cumplido" aunque bajes del objetivo: cruzarlo ya pasó, y quitarle la
     * racha a alguien por corregir un error le enseñaría a no corregirlo.
     */
    fun removePortion(): Int {
        val total = (todayMl - portionMl).coerceAtLeast(0)
        prefs.edit()
            .putInt(KEY_DAY, today())
            .putInt(KEY_TODAY_ML, total)
            .putInt(dayKey(today()), total)
            .apply()
        return total
    }

    /** Para corregir un toque de más. */
    fun resetToday() {
        prefs.edit()
            .putInt(KEY_DAY, today())
            .putInt(KEY_TODAY_ML, 0)
            .putInt(dayKey(today()), 0)
            .apply()
    }

    private fun dayKey(day: Int) = "ml_$day"

    /** La racha sigue viva si se cumplió hoy o ayer; si no, ya se rompió. */
    private fun streakIsAlive(): Boolean {
        val lastMet = prefs.getInt(KEY_LAST_MET_DAY, 0)
        return lastMet == today() || lastMet == yesterday()
    }

    // El día como número de días desde 1970, igual que en los hábitos: así restar días funciona
    // sin casos raros en fin de año, que con "año * 1000 + día" se rompían.
    private fun today(): Int = HabitRules.today()
    private fun yesterday(): Int = HabitRules.today() - 1

    companion object {
        const val DEFAULT_GOAL_ML = 2000
        const val DEFAULT_PORTION_ML = 250

        private const val PREFS_NAME = "glyph_water"
        private const val KEY_GOAL = "goal_ml"
        private const val KEY_PORTION = "portion_ml"
        private const val KEY_DAY = "day_id"
        private const val KEY_TODAY_ML = "today_ml"
        private const val KEY_LAST_MET_DAY = "last_met_day"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_DAYS_MET = "days_met"

        private const val MIN_GOAL_ML = 250
        private const val MAX_GOAL_ML = 8000
        private const val MIN_PORTION_ML = 50
        private const val MAX_PORTION_ML = 2000

        /** Tope de seguridad por si alguien se queda pulsando el widget. */
        private const val MAX_DAILY_ML = 20000

        /** Cuántos días atrás se guardan, igual que en los hábitos. */
        const val HISTORY_DAYS = HabitRules.HISTORY_DAYS
    }
}
