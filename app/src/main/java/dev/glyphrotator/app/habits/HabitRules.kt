package dev.glyphrotator.app.habits

import java.time.LocalDate

/**
 * Las cuentas de los hábitos, sin nada de Android para poder probarlas.
 *
 * Lo importante que resuelve aquí: **no todos los hábitos son diarios**. "No morderme las uñas"
 * es de todos los días; "ir al gym" son tres veces por semana y castigarte los otros cuatro días
 * sería absurdo, además de empujar a sobreentrenar. Así que un hábito semanal no se juzga por si
 * lo hiciste hoy, sino por si **vas al ritmo** que te propusiste.
 */
object HabitRules {

    /** Un hábito diario es el caso de siete veces por semana. */
    const val DAILY = 7

    /** El día de hoy como número de días desde 1970: sirve para restar sin líos de calendario. */
    fun today(date: LocalDate = LocalDate.now()): Int = date.toEpochDay().toInt()

    /**
     * El lunes de la semana de [day], como número de día.
     *
     * La semana empieza en lunes porque es como se piensa "tres veces por semana"; con la semana
     * empezando en domingo, el fin de semana quedaría partido entre dos semanas distintas.
     */
    fun weekStart(day: Int): Int {
        val date = LocalDate.ofEpochDay(day.toLong())
        return day - (date.dayOfWeek.value - 1)
    }

    /** Cuántos días de la semana han pasado ya, contando hoy: 1 el lunes, 7 el domingo. */
    fun daysElapsedInWeek(day: Int): Int = day - weekStart(day) + 1

    /** Cuántas veces se cumplió esta semana, mirando el historial. */
    fun doneThisWeek(history: Set<Int>, day: Int = today()): Int {
        val start = weekStart(day)
        return history.count { it in start..day }
    }

    /**
     * Cuántas veces **debería** llevar ya, para ir al ritmo.
     *
     * Se reparte a lo largo de la semana en vez de exigirlo todo el lunes o perdonarlo hasta el
     * domingo. Con tres a la semana: el miércoles tocaría llevar una, el viernes dos, el domingo
     * las tres.
     */
    fun expectedByNow(timesPerWeek: Int, day: Int = today()): Int {
        if (timesPerWeek <= 0) return 0
        return timesPerWeek * daysElapsedInWeek(day) / DAILY
    }

    /**
     * Si vas al ritmo que te propusiste. Es lo que decide si el hábito suma o resta hoy.
     *
     * Un hábito diario se resuelve solo con esto: siete por semana significa que cada día que
     * pasa sube lo esperado en uno, así que saltarte un día te deja por debajo al instante.
     */
    fun onPace(history: Set<Int>, timesPerWeek: Int, day: Int = today()): Boolean =
        doneThisWeek(history, day) >= expectedByNow(timesPerWeek, day)

    /**
     * La racha, en días o en semanas según el tipo.
     *
     * Un hábito diario cuenta días seguidos. Uno semanal cuenta **semanas cumplidas seguidas**:
     * decir "llevas 40 días de racha yendo al gym" cuando vas tres veces por semana no
     * significaría nada.
     */
    fun currentStreak(history: Set<Int>, timesPerWeek: Int, day: Int = today()): Int {
        if (history.isEmpty()) return 0

        if (timesPerWeek >= DAILY) {
            var streak = 0
            // Se permite empezar en ayer: si hoy aún no lo has marcado, la racha sigue viva.
            var cursor = if (day in history) day else day - 1
            while (cursor in history) {
                streak++
                cursor--
            }
            return streak
        }

        var streak = 0
        var week = weekStart(day)
        // La semana en curso solo cuenta si ya está cumplida; si no, se empieza por la anterior.
        if (doneThisWeek(history, day) >= timesPerWeek) streak++
        week -= DAILY
        while (true) {
            val done = history.count { it in week..(week + DAILY - 1) }
            if (done < timesPerWeek) break
            streak++
            week -= DAILY
        }
        return streak
    }

    /** Cuántas veces se cumplió en los últimos [days] días. */
    fun doneInLast(history: Set<Int>, days: Int, day: Int = today()): Int =
        history.count { it > day - days && it <= day }

    /**
     * El historial recortado a lo que se usa.
     *
     * Se guardan noventa días: suficiente para rachas, para la media del mes y para pintar unas
     * semanas atrás, y poco como para que quepa de sobra en unas preferencias.
     */
    const val HISTORY_DAYS = 90

    fun trimmed(history: Set<Int>, day: Int = today()): Set<Int> =
        history.filterTo(HashSet()) { it > day - HISTORY_DAYS }
}
