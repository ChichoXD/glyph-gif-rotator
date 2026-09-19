package dev.glyphrotator.app.pokemon.spawn

/**
 * Cómo has dormido, y qué te da eso al día siguiente.
 *
 * La idea es que el juego premie descansar de verdad, no solo dejar el teléfono tirado: si
 * cumples tu horario y duermes las horas, al día siguiente aparecen **más** Pokémon y **mejores**.
 * Durante la noche las apariciones siguen recortadas —para no despertarte con la cola llena—,
 * así que el premio se cobra despierto, que es cuando puedes disfrutarlo.
 */
object SleepSchedule {

    /**
     * Calidad del sueño de anoche, de 0 a 1.
     *
     * Mezcla dos cosas, porque dormir bien no es solo dormir mucho:
     *  - **cuánto** dormiste respecto al objetivo,
     *  - **cuándo**: empezar a tu hora cuenta, trasnochar penaliza.
     *
     * [minutesAsleep] son minutos seguidos de pantalla apagada dentro de tu franja, y
     * [minutesLateToBed] lo que te pasaste de tu hora de acostarte (0 si llegaste a tiempo).
     */
    fun quality(
        minutesAsleep: Int,
        minutesLateToBed: Int = 0,
        goalMinutes: Int = DEFAULT_GOAL_MINUTES,
    ): Float {
        if (minutesAsleep <= 0 || goalMinutes <= 0) return 0f

        // Dormir de más no suma: el objetivo es el objetivo.
        val amount = (minutesAsleep.toFloat() / goalMinutes).coerceIn(0f, 1f)

        // La puntualidad se pierde poco a poco, no de golpe: media hora tarde no debería
        // borrar una noche entera de sueño.
        val punctuality = (1f - minutesLateToBed.toFloat() / LATE_TOLERANCE_MINUTES).coerceIn(0f, 1f)

        return (amount * AMOUNT_WEIGHT + punctuality * PUNCTUALITY_WEIGHT).coerceIn(0f, 1f)
    }

    /**
     * Cuánto se multiplica la probabilidad de que aparezca alguno, con esa calidad.
     *
     * Se queda en x2 a propósito: más convertiría una buena noche en una avalancha y el resto
     * de días parecerían rotos en comparación.
     */
    fun frequencyMultiplier(quality: Float): Double =
        1.0 + (quality.coerceIn(0f, 1f) * (MAX_FREQUENCY_MULTIPLIER - 1.0))

    /**
     * Cuánto se multiplican los pesos de los raros y legendarios.
     *
     * Aquí sí se abre más la mano: es el premio que de verdad se nota, porque cambia **qué**
     * sale y no solo cuánto.
     */
    fun rarityMultiplier(quality: Float): Float =
        1f + (quality.coerceIn(0f, 1f) * (MAX_RARITY_MULTIPLIER - 1f))

    /** Siete horas y media, el objetivo por defecto. Ajustable por el usuario. */
    const val DEFAULT_GOAL_MINUTES = 450

    /** Hora de acostarse por defecto, en minutos desde medianoche (23:30). */
    const val DEFAULT_BEDTIME_MINUTES = 23 * 60 + 30

    /**
     * Cuánto puedes retrasarte antes de perder toda la puntualidad.
     *
     * Públicos porque la pantalla de sueño enseña el desglose —cuánto viene de las horas y
     * cuánto de la puntualidad— y tiene que salir exactamente la misma nota que se calcula
     * aquí. Repitiendo los números allí, cualquier ajuste dejaría el desglose mintiendo.
     */
    const val LATE_TOLERANCE_MINUTES = 120f

    const val AMOUNT_WEIGHT = 0.7f
    const val PUNCTUALITY_WEIGHT = 0.3f

    private const val MAX_FREQUENCY_MULTIPLIER = 2.0
    private const val MAX_RARITY_MULTIPLIER = 4f
}
