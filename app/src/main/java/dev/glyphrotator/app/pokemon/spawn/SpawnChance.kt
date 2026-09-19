package dev.glyphrotator.app.pokemon.spawn

/**
 * Cada cuánto aparece un Pokémon.
 *
 * La idea del juego es premiar **no** usar el teléfono, así que la probabilidad sube con los
 * minutos de pantalla apagada y se reinicia en cuanto aparece uno. Con la pantalla encendida
 * no aparece nada: si apareciera mientras usas el móvil, el incentivo desaparece.
 */
object SpawnChance {

    /**
     * Probabilidad **final** (0..1) de que aparezca uno en esta comprobación.
     *
     * Los tramos son escalones y no una curva continua a propósito: así se puede explicar en
     * una frase —"a la hora seguro que sale uno"— y el jugador entiende qué gana esperando.
     *
     * Los dos multiplicadores del modo demo entran **aquí dentro** y no fuera, y esa es la
     * corrección: el de madrugada es un **tope**, y un tope aplicado antes de multiplicar no
     * topa nada. Antes el servicio hacía `of(...) * spawnMultiplier` por fuera, así que de
     * madrugada el resultado salía del tope normal (0,5 %) multiplicado por doce — ni el 0,5 %
     * de siempre ni el 10 % que promete la pantalla de Equilibrio, sino un tercer número que no
     * aparecía en ningún sitio. `BEDTIME_MULTIPLIER` existía, estaba documentado y se pintaba en
     * pantalla, pero **no lo leía nadie**.
     *
     * @param spawnMultiplier acelera el ritmo general (demo: ×12).
     * @param bedtimeMultiplier abre el tope de la franja de madrugada (demo: ×20), que es la
     *   franja en la que de verdad se prueba el juego —de noche, con el móvil quieto—.
     */
    fun of(
        conditions: SpawnConditions,
        spawnMultiplier: Double = 1.0,
        bedtimeMultiplier: Double = 1.0,
    ): Double {
        // Con el teléfono en la mano no aparece nada.
        if (conditions.batteryPercent <= CRITICAL_BATTERY_PERCENT && !conditions.isCharging) {
            // Con la batería en las últimas no se enciende la Matrix, así que tampoco tiene
            // sentido gastar una aparición que el jugador no llegaría a ver.
            return 0.0
        }

        // El primero de todos sale rápido: sin nada en la Pokédex, esperar una hora para ver
        // si el juego existe siquiera es demasiado.
        if (conditions.pokedexCount == 0) {
            return (FIRST_CATCH_CHANCE * spawnMultiplier).coerceAtMost(1.0)
        }

        val base = THRESHOLDS.lastOrNull { conditions.minutesSinceLastSpawn >= it.first }?.second ?: 0.0

        // De madrugada se corta casi del todo: si no, ocho horas de sueño llenarían la cola
        // de capturas y al despertar no quedaría nada por hacer.
        if (conditions.isBedtime) {
            val cap = (BEDTIME_MAX_CHANCE * bedtimeMultiplier).coerceAtMost(1.0)
            return (base * spawnMultiplier).coerceAtMost(cap)
        }

        // Haber dormido bien se cobra despierto, que es cuando se disfruta.
        return (base * spawnMultiplier * SleepSchedule.frequencyMultiplier(conditions.sleepQuality))
            .coerceAtMost(1.0)
    }

    /**
     * La misma probabilidad, pero para un hueco de varios minutos de golpe.
     *
     * Hace falta porque el juego ya no se comprueba con un temporizador que corre solo: con el
     * móvil dormido la CPU está parada, así que el aviso lo da una alarma del sistema que puede
     * llegar tarde —y en modo Doze llega mucho más tarde—. Ver [dev.glyphrotator.app.service.GameTickAlarm].
     *
     * Si en vez de esto se tirara **una sola vez** por cada despertar, el ritmo del juego
     * dependería de cuándo el sistema decide despertar al teléfono: la misma hora de reposo daría
     * ocho tiradas o una según le apeteciera a Android ese día. Con la probabilidad compuesta,
     * diez minutos valen diez minutos aunque lleguen en un solo aviso.
     *
     * La fórmula es la de "al menos una vez": el complemento de fallar todas.
     */
    fun overMinutes(perMinute: Double, minutes: Int): Double {
        if (minutes <= 0) return 0.0
        if (perMinute <= 0.0) return 0.0
        if (perMinute >= 1.0) return 1.0
        return 1.0 - Math.pow(1.0 - perMinute, minutes.toDouble())
    }

    /** Minutos acumulados -> probabilidad. */
    private val THRESHOLDS = listOf(
        15 to 0.04,
        30 to 0.08,
        45 to 0.20,
        60 to 1.00,
    )

    const val FIRST_CATCH_CHANCE = 0.20
    const val BEDTIME_MAX_CHANCE = 0.005
    const val CRITICAL_BATTERY_PERCENT = 10
}
