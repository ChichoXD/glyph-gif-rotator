package dev.glyphrotator.app.pokemon

/**
 * Progresión de nivel por experiencia. Curva plana: cada nivel cuesta lo mismo
 * ([EXP_PER_LEVEL]), igual que en glyph-catch, para que entrenar sea predecible.
 */
object LevelCalculator {

    const val MAX_LEVEL = 100
    const val EXP_PER_LEVEL = 300

    data class Result(val level: Int, val exp: Int, val leveledUp: Boolean)

    /**
     * Suma [gainedExp] a un Pokémon que está en [currentLevel]/[currentExp] y devuelve su
     * nuevo estado, o `null` si no cambia nada (ya está al máximo o no gana nada).
     */
    fun applyExp(currentLevel: Int, currentExp: Int, gainedExp: Int): Result? {
        if (gainedExp <= 0) return null
        if (currentLevel >= MAX_LEVEL) return null

        val totalExp = (currentLevel - 1) * EXP_PER_LEVEL + currentExp + gainedExp
        val maxTotalExp = (MAX_LEVEL - 1) * EXP_PER_LEVEL

        if (totalExp >= maxTotalExp) {
            return Result(MAX_LEVEL, 0, leveledUp = currentLevel < MAX_LEVEL)
        }

        val newLevel = totalExp / EXP_PER_LEVEL + 1
        val newExp = totalExp % EXP_PER_LEVEL
        return Result(newLevel, newExp, leveledUp = newLevel > currentLevel)
    }

    /** Fracción 0..1 de progreso dentro del nivel actual, para barras de progreso. */
    fun progressFraction(level: Int, exp: Int): Float =
        if (level >= MAX_LEVEL) 1f else (exp.toFloat() / EXP_PER_LEVEL).coerceIn(0f, 1f)

    fun expNeeded(level: Int): Int = if (level >= MAX_LEVEL) 0 else EXP_PER_LEVEL
}

/**
 * Reglas de entrenamiento: el Pokémon marcado como compañero gana experiencia mientras la
 * pantalla está apagada (no por pasos ni por capturas), con un extra cada cierto rato.
 */
object TrainingRules {

    const val EXP_PER_MINUTE = 3
    const val BONUS_INTERVAL_MINUTES = 20
    const val BONUS_AMOUNT = 90

    /**
     * Lo máximo que puede pagar **un solo tramo**: un día entero de pantalla apagada.
     *
     * No es equilibrio, es un tope de cordura, y sale del mismo sitio que los fallos 26 y 27. El
     * tramo se mide desde una marca que vive en disco para sobrevivir a que maten el servicio, pero
     * `ACTION_SCREEN_ON` **solo llega con el servicio vivo**: si estuvo muerto un fin de semana, al
     * revivir se cobran las 72 horas de golpe como si el teléfono hubiera estado en reposo, aunque
     * lo hubieras usado toda la mañana.
     *
     * Los números: una noche de 8 h son 3600 EXP, doce niveles, que es lo diseñado. Sin tope, un
     * fin de semana de servicio muerto daban 32 400 EXP — **108 niveles**, de 1 a 100 de una vez—.
     * Con el tope se queda en 10 800, treinta y seis niveles, que sigue siendo generoso para
     * alguien que de verdad no ha tocado el móvil en todo un día.
     *
     * Se recorta aquí dentro y no en quien llama para que las dos rutas de cobro —la de minuto a
     * minuto y la que salda el resto al encender— vean el mismo techo y no se descuadren.
     */
    const val MAX_PERIOD_MINUTES = 24 * 60

    /** Experiencia ganada por [minutesWithScreenOff] minutos seguidos de pantalla apagada. */
    fun expForScreenOffMinutes(minutesWithScreenOff: Int): Int {
        if (minutesWithScreenOff <= 0) return 0
        val minutes = minutesWithScreenOff.coerceAtMost(MAX_PERIOD_MINUTES)
        val base = minutes * DemoTuning.expPerMinute
        val bonuses = minutes / BONUS_INTERVAL_MINUTES
        return base + bonuses * BONUS_AMOUNT
    }

    /**
     * Lo que falta por cobrar entre el minuto [fromMinutes] y el [toMinutes] del mismo rato
     * con la pantalla apagada.
     *
     * Es lo que permite pagar el entrenamiento poco a poco sin falsear las cuentas: como se
     * calcula siempre sobre el total acumulado, el extra de los 20 minutos cae en su sitio
     * exacto y da igual cada cuánto se vaya cobrando. Ir sumando minuto a minuto por separado,
     * en cambio, no lo daría nunca.
     */
    fun expBetween(fromMinutes: Int, toMinutes: Int): Int =
        (expForScreenOffMinutes(toMinutes) - expForScreenOffMinutes(fromMinutes)).coerceAtLeast(0)
}
