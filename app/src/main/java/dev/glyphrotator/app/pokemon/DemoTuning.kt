package dev.glyphrotator.app.pokemon

import android.content.Context

/**
 * Los valores del juego que el modo demo acelera, en un solo sitio.
 *
 * Es estado global mutable, que normalmente sería mala idea. Aquí se hace así por una razón
 * concreta: las reglas del juego —[EggRules], [LevelCalculator], `SpawnChance`— son objetos
 * puros sin Android dentro, y eso es lo que permite comprobarlas en los tests. Pasarles un
 * `Context` para que consulten una preferencia rompería justo lo que las hace comprobables.
 *
 * Con esto, las reglas siguen siendo puras y leen un número; quien decide ese número es la app
 * al arrancar. Y los tests pueden ponerlo a mano para probar los dos modos.
 *
 * **Se lee, no se guarda.** La verdad vive en [DemoMode]; esto es solo la copia en memoria para
 * no ir a disco en cada comprobación de aparición, que ocurre cada minuto.
 */
object DemoTuning {

    @Volatile
    var isDemo: Boolean = false
        private set

    /** Se llama al arrancar el servicio y al cambiar el interruptor. */
    fun refresh(context: Context) {
        isDemo = DemoMode(context).isEnabled
    }

    /** Para los tests, que no tienen Context. */
    fun setForTesting(enabled: Boolean) {
        isDemo = enabled
    }

    /** Cuánto se multiplica la probabilidad de que salga un salvaje. */
    val spawnMultiplier: Double
        get() = if (isDemo) DemoMode.SPAWN_MULTIPLIER else 1.0

    /** Lo mismo para el tope de la franja de madrugada. */
    val bedtimeMultiplier: Double
        get() = if (isDemo) DemoMode.BEDTIME_MULTIPLIER else 1.0

    /** Experiencia por minuto de pantalla apagada. */
    val expPerMinute: Int
        get() = if (isDemo) {
            TrainingRules.EXP_PER_MINUTE * DemoMode.EXP_MULTIPLIER
        } else {
            TrainingRules.EXP_PER_MINUTE
        }

    /** Capturas para que caiga un huevo. */
    val capturesPerEgg: Int
        get() = if (isDemo) DemoMode.CAPTURES_PER_EGG else EggRules.CAPTURES_PER_EGG

    /** Minutos de reposo que tarda un huevo en abrirse. */
    val incubationMinutes: Int
        get() = if (isDemo) DemoMode.INCUBATION_MINUTES else EggRules.INCUBATION_MINUTES
}
