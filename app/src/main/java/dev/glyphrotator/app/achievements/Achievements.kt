package dev.glyphrotator.app.achievements

import androidx.annotation.StringRes
import dev.glyphrotator.app.R
import dev.glyphrotator.app.glyph.DotIcons

/**
 * Los logros, definidos como **funciones del estado**, no como contadores propios.
 *
 * Es la diferencia importante: un contador aparte ("llevas 37 capturas") se descuadra en cuanto
 * algo falla —un guardado a medias, un borrado, una migración— y una vez descuadrado no hay forma
 * de saber cuál era el bueno. Calculando el progreso del estado real, el logro no puede mentir:
 * si tienes 37 Pokémon, el progreso es 37, hoy y dentro de un año.
 *
 * Cada logro solo dice **cuánto llevas** y **cuánto hace falta**. Lo de guardar cuáles ya están
 * desbloqueados es de [AchievementStore], y solo para no volver a avisar de lo mismo.
 */
data class Achievement(
    val id: String,
    /**
     * Nombre y pista como **referencia a recurso**, no como texto.
     *
     * Estaban escritos a pelo en español aquí dentro, y por eso la pantalla de Logros salía en
     * español entera en el build en inglés: al no pasar por `res/`, el sistema de idiomas de
     * Android no los veía y no había forma de traducirlos. Que sean `Int` no rompe la pureza de
     * esta clase — los tests siguen sin necesitar `Context`, porque nadie compara textos.
     */
    @StringRes val nameRes: Int,
    @StringRes val hintRes: Int,
    val icon: DotIcons.Icon,
    val target: Int,
    val progressOf: (GameSnapshot) -> Int,
) {
    fun progress(snapshot: GameSnapshot): Int = progressOf(snapshot).coerceIn(0, target)
    fun isUnlocked(snapshot: GameSnapshot): Boolean = progressOf(snapshot) >= target
    fun fraction(snapshot: GameSnapshot): Float = progress(snapshot).toFloat() / target
}

/**
 * Una foto del estado del juego y de los seguimientos.
 *
 * Se pasa ya calculada en vez de dejar que cada logro lea las preferencias por su cuenta: son
 * treinta y pico logros y cada uno abriría los mismos archivos, treinta veces, cada vez que se
 * abre la pantalla.
 */
data class GameSnapshot(
    val caughtCount: Int = 0,
    val pokedexCount: Int = 0,
    val speciesTotal: Int = 151,
    val distinctTypes: Int = 0,
    val evolutionsDone: Int = 0,
    val highestLevel: Int = 0,
    val cappedCount: Int = 0,
    val eggsHatched: Int = 0,
    val eggsHeld: Int = 0,
    val startersOwned: Int = 0,
    val startersFinal: Int = 0,
    val legendaryBirds: Int = 0,
    val hasMewtwo: Boolean = false,
    val bestStreak: Int = 0,
    val perfectDays: Int = 0,
    val waterDaysMet: Int = 0,
    val nightsRecorded: Int = 0,
    val perfectNights: Int = 0,
    val habitsCreated: Int = 0,
    val bestHabitStreak: Int = 0,
    val caughtAtNight: Int = 0,
    val caughtOnLowBattery: Int = 0,
    val releasedCount: Int = 0,
)

object AchievementCatalog {

    /**
     * Todos los logros, agrupados por lo que premian.
     *
     * El orden importa: dentro de cada grupo van de más fácil a más difícil, para que la lista
     * se lea como una escalera y no como un montón. Los ids son fijos —se guardan en disco— así
     * que renombrar uno dejaría huérfano el desbloqueo de quien ya lo tuviera.
     */
    val all: List<Achievement> = buildList {
        // ---- Captura -----------------------------------------------------------------
        add(catch("catch_1", R.string.ach_catch_1_name, R.string.ach_catch_1_hint, 1))
        add(catch("catch_10", R.string.ach_catch_10_name, R.string.ach_catch_10_hint, 10))
        add(catch("catch_50", R.string.ach_catch_50_name, R.string.ach_catch_50_hint, 50))
        add(catch("catch_100", R.string.ach_catch_100_name, R.string.ach_catch_100_hint, 100))
        add(catch("catch_151", R.string.ach_catch_151_name, R.string.ach_catch_151_hint, 151))

        add(
            Achievement(
                "types_all", R.string.ach_types_all_name, R.string.ach_types_all_hint,
                DotIcons.Icon.STAR, 10
            ) { it.distinctTypes }
        )
        add(
            Achievement(
                "catch_night", R.string.ach_catch_night_name, R.string.ach_catch_night_hint,
                DotIcons.Icon.SLEEP_ZZZ, 5
            ) { it.caughtAtNight }
        )
        add(
            Achievement(
                "catch_low_battery",
                R.string.ach_catch_low_battery_name, R.string.ach_catch_low_battery_hint,
                DotIcons.Icon.FLAME, 1
            ) { it.caughtOnLowBattery }
        )
        add(
            Achievement(
                "released", R.string.ach_released_name, R.string.ach_released_hint,
                DotIcons.Icon.LEAF, 1
            ) { it.releasedCount }
        )

        // ---- Pokédex -----------------------------------------------------------------
        add(dex("dex_10", R.string.ach_dex_10_name, R.string.ach_dex_10_hint, 10))
        add(dex("dex_50", R.string.ach_dex_50_name, R.string.ach_dex_50_hint, 50))
        add(dex("dex_100", R.string.ach_dex_100_name, R.string.ach_dex_100_hint, 100))
        add(
            Achievement(
                "dex_full", R.string.ach_dex_full_name, R.string.ach_dex_full_hint,
                DotIcons.Icon.POKEDEX, 151
            ) { it.pokedexCount }
        )
        add(
            Achievement(
                "starters", R.string.ach_starters_name, R.string.ach_starters_hint,
                DotIcons.Icon.FLAME, 3
            ) { it.startersOwned }
        )
        add(
            Achievement(
                "starters_final",
                R.string.ach_starters_final_name, R.string.ach_starters_final_hint,
                DotIcons.Icon.FLAME, 3
            ) { it.startersFinal }
        )
        add(
            Achievement(
                "birds", R.string.ach_birds_name, R.string.ach_birds_hint,
                DotIcons.Icon.STAR, 3
            ) { it.legendaryBirds }
        )
        add(
            Achievement(
                "mewtwo", R.string.ach_mewtwo_name, R.string.ach_mewtwo_hint,
                DotIcons.Icon.STAR, 1
            ) { if (it.hasMewtwo) 1 else 0 }
        )

        // ---- Evolución y entrenamiento -----------------------------------------------
        add(evolution("evo_1", R.string.ach_evo_1_name, R.string.ach_evo_1_hint, 1))
        add(evolution("evo_10", R.string.ach_evo_10_name, R.string.ach_evo_10_hint, 10))
        add(level("level_25", R.string.ach_level_25_name, R.string.ach_level_25_hint, 25))
        add(level("level_50", R.string.ach_level_50_name, R.string.ach_level_50_hint, 50))
        add(level("level_100", R.string.ach_level_100_name, R.string.ach_level_100_hint, 100))
        add(
            Achievement(
                "capped", R.string.ach_capped_name, R.string.ach_capped_hint,
                DotIcons.Icon.CLOCK, 3
            ) { it.cappedCount }
        )

        // ---- Huevos ------------------------------------------------------------------
        add(
            Achievement(
                "egg_first", R.string.ach_egg_first_name, R.string.ach_egg_first_hint,
                DotIcons.Icon.EGG, 1
            ) { it.eggsHeld + it.eggsHatched }
        )
        add(
            Achievement(
                "egg_hatch_1", R.string.ach_egg_hatch_1_name, R.string.ach_egg_hatch_1_hint,
                DotIcons.Icon.EGG, 1
            ) { it.eggsHatched }
        )
        add(
            Achievement(
                "egg_hatch_10", R.string.ach_egg_hatch_10_name, R.string.ach_egg_hatch_10_hint,
                DotIcons.Icon.EGG, 10
            ) { it.eggsHatched }
        )

        // ---- Constancia --------------------------------------------------------------
        add(streak("streak_7", R.string.ach_streak_7_name, R.string.ach_streak_7_hint, 7))
        add(streak("streak_30", R.string.ach_streak_30_name, R.string.ach_streak_30_hint, 30))
        add(streak("streak_100", R.string.ach_streak_100_name, R.string.ach_streak_100_hint, 100))
        add(
            Achievement(
                "perfect_day", R.string.ach_perfect_day_name, R.string.ach_perfect_day_hint,
                DotIcons.Icon.HEART, 1
            ) { it.perfectDays }
        )
        add(
            Achievement(
                "perfect_week", R.string.ach_perfect_week_name, R.string.ach_perfect_week_hint,
                DotIcons.Icon.HEART, 7
            ) { it.perfectDays }
        )

        // ---- Agua, sueño y hábitos ---------------------------------------------------
        add(
            Achievement(
                "water_1", R.string.ach_water_1_name, R.string.ach_water_1_hint,
                DotIcons.Icon.DROP, 1
            ) { it.waterDaysMet }
        )
        add(
            Achievement(
                "water_30", R.string.ach_water_30_name, R.string.ach_water_30_hint,
                DotIcons.Icon.DROP, 30
            ) { it.waterDaysMet }
        )
        add(
            Achievement(
                "sleep_1", R.string.ach_sleep_1_name, R.string.ach_sleep_1_hint,
                DotIcons.Icon.SLEEP_ZZZ, 1
            ) { it.nightsRecorded }
        )
        add(
            Achievement(
                "sleep_perfect", R.string.ach_sleep_perfect_name, R.string.ach_sleep_perfect_hint,
                DotIcons.Icon.SLEEP_ZZZ, 1
            ) { it.perfectNights }
        )
        add(
            Achievement(
                "habit_first", R.string.ach_habit_first_name, R.string.ach_habit_first_hint,
                DotIcons.Icon.BOOK, 1
            ) { it.habitsCreated }
        )
        add(
            Achievement(
                "habit_30", R.string.ach_habit_30_name, R.string.ach_habit_30_hint,
                DotIcons.Icon.DUMBBELL, 30
            ) { it.bestHabitStreak }
        )
    }

    private fun catch(id: String, @StringRes name: Int, @StringRes hint: Int, target: Int) =
        Achievement(id, name, hint, DotIcons.Icon.POKEBALL, target) { it.caughtCount }

    private fun dex(id: String, @StringRes name: Int, @StringRes hint: Int, target: Int) =
        Achievement(id, name, hint, DotIcons.Icon.POKEDEX, target) { it.pokedexCount }

    private fun evolution(id: String, @StringRes name: Int, @StringRes hint: Int, target: Int) =
        Achievement(id, name, hint, DotIcons.Icon.STAR, target) { it.evolutionsDone }

    private fun level(id: String, @StringRes name: Int, @StringRes hint: Int, target: Int) =
        Achievement(id, name, hint, DotIcons.Icon.RARE_CANDY, target) { it.highestLevel }

    private fun streak(id: String, @StringRes name: Int, @StringRes hint: Int, target: Int) =
        Achievement(id, name, hint, DotIcons.Icon.FLAME, target) { it.bestStreak }

    /** Los que ya están conseguidos con ese estado. */
    fun unlocked(snapshot: GameSnapshot): List<Achievement> = all.filter { it.isUnlocked(snapshot) }
}
