package dev.glyphrotator.app.habits

import dev.glyphrotator.app.pokemon.spawn.SleepSchedule

/**
 * Lo que tu vida real le da al juego.
 *
 * Tres cosas suman: dormir, beber agua y cumplir los hábitos que te hayas puesto. Las tres se
 * cobran **al día siguiente**, no en el momento: si el premio fuera inmediato, lo óptimo sería
 * beberse el objetivo de un trago a las nueve de la mañana. Cobrándolo mañana, lo que premia es
 * el día cumplido.
 *
 * Se **multiplican entre sí** en vez de sumarse. Sumando, con tener una sola cosa a tope daba
 * casi igual descuidar el resto; multiplicando, cada una vale poco por su cuenta y mucho junto
 * a las otras, que es la idea.
 */
object DailyBonus {

    /**
     * Cuánto se multiplica la experiencia del entrenamiento.
     *
     * [habitsKept] de [habitsTotal] cumplidos ayer; sin hábitos apuntados, esa parte no penaliza
     * (se queda en ×1) porque no tendría sentido castigar por no usar una función.
     */
    fun trainingMultiplier(
        sleepQuality: Float,
        waterGoalMet: Boolean,
        habitsKept: Int,
        habitsTotal: Int,
        streakDays: Int = 0,
    ): Float = sleepFactor(sleepQuality) *
        waterFactor(waterGoalMet) *
        habitFactor(habitsKept, habitsTotal) *
        streakFactor(streakDays)

    /**
     * Si estas tres cosas juntas cuentan como un día perfecto: sueño de calidad máxima, el
     * objetivo de agua cumplido, y todos los hábitos que te hayas puesto.
     *
     * **Todo o nada**, a diferencia de [trainingMultiplier]: el multiplicador premia por
     * fracciones porque castigar de golpe invita a abandonar, pero esto es un logro —
     * `perfect_day`— y un logro que se pudiera conseguir "casi" no sería un logro.
     *
     * Sin hábitos apuntados sigue pudiendo ser perfecto: `total <= 0` no penaliza, por la misma
     * razón que en [habitFactor] — no se juzga por una función que no usas.
     */
    fun isPerfectDay(
        sleepQuality: Float,
        waterGoalMet: Boolean,
        habitsKept: Int,
        habitsTotal: Int,
    ): Boolean = sleepQuality >= 1f && waterGoalMet && habitsKept >= habitsTotal

    /**
     * El premio por mantenerlo: crece con la racha más larga que tengas viva.
     *
     * Sin esto, treinta días seguidos valían exactamente lo mismo que el primero, y la racha era
     * un número decorativo. Que crezca poco a poco —y no de golpe al llegar a una cifra— es lo
     * que hace que romperla duela: no pierdes un premio concreto, pierdes todo lo acumulado.
     *
     * Se toma **la más larga**, no la suma ni la media: premia sostener algo de verdad en vez de
     * repartirse entre muchas cosas a medias.
     */
    fun streakFactor(streakDays: Int): Float {
        if (streakDays <= 0) return 1f
        val progress = (streakDays.toFloat() / STREAK_DAYS_FOR_MAX).coerceIn(0f, 1f)
        return 1f + progress * (MAX_STREAK_FACTOR - 1f)
    }

    /**
     * Dormir mal **frena**, dormir bien acelera.
     *
     * Antes el suelo era 1: incumplir no penalizaba, solo dejaba de premiar, y eso convertía
     * todo el sistema en un adorno —subías igual sin hacer nada, solo un poco más lento—. Con el
     * suelo por debajo de 1 hay una diferencia real entre cuidarte y no hacerlo.
     */
    fun sleepFactor(sleepQuality: Float): Float =
        MIN_SLEEP_FACTOR + sleepQuality.coerceIn(0f, 1f) * (MAX_SLEEP_FACTOR - MIN_SLEEP_FACTOR)

    fun waterFactor(goalMet: Boolean): Float = if (goalMet) MAX_WATER_FACTOR else MIN_WATER_FACTOR

    /**
     * Los hábitos: proporcional a cuántos cumpliste, no todo o nada.
     *
     * Con "todo o nada" un mal día borraría el esfuerzo de los otros cuatro hábitos, y eso
     * invita a abandonar en vez de a seguir.
     */
    fun habitFactor(kept: Int, total: Int): Float {
        // Sin hábitos apuntados no se juzga nada: ni premio ni castigo. Solo se te mide por lo
        // que tú mismo te has puesto.
        if (total <= 0) return 1f
        val ratio = (kept.toFloat() / total).coerceIn(0f, 1f)
        return MIN_HABIT_FACTOR + ratio * (MAX_HABIT_FACTOR - MIN_HABIT_FACTOR)
    }

    /** El techo con todo cumplido y el suelo sin nada, para poder enseñarlos en la app. */
    val MAX_MULTIPLIER: Float
        get() = MAX_SLEEP_FACTOR * MAX_WATER_FACTOR * MAX_HABIT_FACTOR * MAX_STREAK_FACTOR

    val MIN_MULTIPLIER: Float
        get() = MIN_SLEEP_FACTOR * MIN_WATER_FACTOR * MIN_HABIT_FACTOR

    /**
     * Los topes, elegidos para que la diferencia se note de verdad.
     *
     * Cumpliéndolo todo sale ×2,9; sin hacer nada, ×0,21. Son catorce veces de diferencia: con
     * la curva de 300 EXP por nivel, una noche de ocho horas cumpliendo da doce niveles y sin
     * cumplir da menos de uno. Ahí está la obligación que pedías —no es que subas más despacio,
     * es que **no subes**— sin llegar a bloquear del todo a quien tenga un mal día.
     *
     * El sueño se mueve menos que los otros dos porque ya reparte por su cuenta en
     * [SleepSchedule], donde decide cuántos Pokémon aparecen y cómo de raros. Si aquí pesara lo
     * mismo, dormir sería lo único que importase.
     */
    private const val MIN_SLEEP_FACTOR = 0.6f
    private const val MAX_SLEEP_FACTOR = 1.4f

    private const val MIN_WATER_FACTOR = 0.7f
    private const val MAX_WATER_FACTOR = 1.25f

    private const val MIN_HABIT_FACTOR = 0.5f
    private const val MAX_HABIT_FACTOR = 1.7f

    /** Un mes seguido es donde la racha llega a su tope: medio multiplicador extra. */
    private const val STREAK_DAYS_FOR_MAX = 30
    private const val MAX_STREAK_FACTOR = 1.5f
}
