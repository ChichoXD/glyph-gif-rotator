package dev.glyphrotator.app.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Los hábitos que el usuario se apunta, con su objetivo y su historial.
 *
 * Los escribe él: "ir al gym", "leer", "no morderme las uñas", lo que sea. El juego no opina
 * sobre cuáles son buenos ni malos, solo cuenta si vas al ritmo que **tú** te has puesto.
 *
 * Cada hábito guarda los días concretos en que se cumplió, no solo un contador. Es lo que
 * permite decir "dos de tres esta semana" o "18 de los últimos 30 días" en vez de un número
 * suelto sin contexto, y lo que hace que las rachas se puedan recalcular en vez de arrastrar un
 * valor que se descuadra en cuanto algo va mal.
 */
class HabitStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Habit(
        val id: String,
        val name: String,
        /** Cuántas veces por semana te lo propones. Siete es todos los días. */
        val timesPerWeek: Int = HabitRules.DAILY,
        /** Los días en que se cumplió, como días desde 1970. */
        val history: Set<Int> = emptySet(),
    ) {
        val isDaily: Boolean get() = timesPerWeek >= HabitRules.DAILY

        fun isDoneToday(day: Int = HabitRules.today()): Boolean = day in history

        fun doneThisWeek(day: Int = HabitRules.today()): Int =
            HabitRules.doneThisWeek(history, day)

        fun onPace(day: Int = HabitRules.today()): Boolean =
            HabitRules.onPace(history, timesPerWeek, day)

        fun streak(day: Int = HabitRules.today()): Int =
            HabitRules.currentStreak(history, timesPerWeek, day)

        fun doneInLast(days: Int, day: Int = HabitRules.today()): Int =
            HabitRules.doneInLast(history, days, day)

        val totalDays: Int get() = history.size
    }

    fun all(): List<Habit> {
        val raw = prefs.getString(KEY_HABITS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val days = obj.optJSONArray("days")
            Habit(
                id = obj.optString("id"),
                name = obj.optString("name"),
                timesPerWeek = obj.optInt("perWeek", HabitRules.DAILY),
                history = buildSet {
                    if (days != null) for (i in 0 until days.length()) add(days.optInt(i))
                },
            )
        }
    }

    fun add(name: String, timesPerWeek: Int = HabitRules.DAILY) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        save(
            all() + Habit(
                id = System.currentTimeMillis().toString(),
                name = clean,
                timesPerWeek = timesPerWeek.coerceIn(1, HabitRules.DAILY),
            )
        )
    }

    fun remove(id: String) = save(all().filterNot { it.id == id })

    fun setTimesPerWeek(id: String, timesPerWeek: Int) {
        save(
            all().map {
                if (it.id == id) it.copy(timesPerWeek = timesPerWeek.coerceIn(1, HabitRules.DAILY)) else it
            }
        )
    }

    /**
     * Marca o desmarca el día de hoy.
     *
     * Desmarcar borra el día del historial sin más. Como las rachas se calculan a partir del
     * historial en vez de guardarse aparte, no hay nada que deshacer a mano: la cuenta vuelve
     * sola a lo que sería sin ese día.
     */
    fun setDoneToday(id: String, done: Boolean, day: Int = HabitRules.today()) {
        save(
            all().map { habit ->
                if (habit.id != id) return@map habit
                val history = habit.history.toMutableSet()
                if (done) history.add(day) else history.remove(day)
                habit.copy(history = HabitRules.trimmed(history, day))
            }
        )
    }

    /** Cuántos van al ritmo hoy: es lo que alimenta el bonus del día siguiente. */
    fun onPaceCount(day: Int = HabitRules.today()): Int = all().count { it.onPace(day) }

    fun doneToday(day: Int = HabitRules.today()): Int = all().count { it.isDoneToday(day) }

    /** Los de ayer, que son los que ya dan premio o castigo hoy. */
    fun onPaceYesterday(): Int = onPaceCount(HabitRules.today() - 1)

    private fun save(habits: List<Habit>) {
        val array = JSONArray()
        for (habit in habits) {
            array.put(
                JSONObject().apply {
                    put("id", habit.id)
                    put("name", habit.name)
                    put("perWeek", habit.timesPerWeek)
                    put("days", JSONArray().apply { habit.history.sorted().forEach { put(it) } })
                }
            )
        }
        prefs.edit().putString(KEY_HABITS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "glyph_habits"
        const val KEY_HABITS = "habits"
    }
}
