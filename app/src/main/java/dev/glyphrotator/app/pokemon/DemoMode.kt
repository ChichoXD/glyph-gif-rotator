package dev.glyphrotator.app.pokemon

import android.content.Context

/**
 * El modo demo: el juego entero acelerado para poder probarlo en un día.
 *
 * El ritmo normal está pensado para semanas. Una aparición tarda hasta una hora de pantalla
 * apagada, un huevo son diez capturas y tres horas de incubación, y un nivel son cien minutos
 * de entrenamiento. Eso está bien para jugar, pero es imposible de **probar**: para saber si
 * los multiplicadores de agua, sueño y hábitos están bien equilibrados harían falta semanas de
 * cumplir metas y apuntar resultados.
 *
 * En demo, cada rueda gira mucho más deprisa. Los factores están elegidos para que una tarde
 * dé aproximadamente lo que da una semana normal.
 *
 * **Lo que el demo NO toca** son los multiplicadores de la vida real —sueño, agua, hábitos,
 * racha—, y es a propósito. Si también se inflaran, no se estaría probando el equilibrio: se
 * estaría probando otro juego. Lo que se acelera es cada cuánto pasan las cosas, no cuánto
 * premian.
 */
class DemoMode(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    companion object {
        const val PREFS_NAME = "glyph_demo"
        private const val KEY_ENABLED = "enabled"

        /**
         * Cuánto se multiplica la probabilidad de que aparezca un salvaje.
         *
         * Con ×12, el primer escalón pasa de un 4 % a un 48 % por comprobación: en vez de
         * esperar cuarto de hora larga, sale uno casi seguro en los primeros minutos.
         */
        const val SPAWN_MULTIPLIER = 12.0

        /** La franja de madrugada también se abre, o de noche no se podría probar nada. */
        const val BEDTIME_MULTIPLIER = 20.0

        /** Experiencia por minuto de pantalla apagada. Un nivel pasa de 100 minutos a 5. */
        const val EXP_MULTIPLIER = 20

        /** Capturas para que caiga un huevo: de diez a dos. */
        const val CAPTURES_PER_EGG = 2

        /** Minutos de incubación: de tres horas a diez minutos. */
        const val INCUBATION_MINUTES = 10

        /** Lectura rápida sin tener que construir el objeto. */
        fun isOn(context: Context): Boolean = DemoMode(context).isEnabled
    }
}
