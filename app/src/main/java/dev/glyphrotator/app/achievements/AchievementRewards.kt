package dev.glyphrotator.app.achievements

import android.content.Context
import dev.glyphrotator.app.pokemon.ItemDropTable
import dev.glyphrotator.app.pokemon.PokemonRepository
import dev.glyphrotator.app.service.GlyphRotationService

/**
 * Qué pasa cuando se desbloquea un logro grande: un objeto.
 *
 * No todos los logros dan objeto — con 37 y algunos tan fáciles como "atrapa tu primer Pokémon",
 * dar premio en cada uno inundaría el inventario en los primeros cinco minutos y el objeto
 * dejaría de significar nada. Solo los que representan un esfuerzo real de verdad, uno por
 * partida: capturas grandes, la Pokédex completa, rachas largas, el equipo legendario.
 *
 * Aparte de [dev.glyphrotator.app.pokemon.PokemonGame] y de `AchievementStore` a propósito: dos
 * sitios distintos llaman a `checkNewlyUnlocked()` —la captura por botón y la ronda nocturna del
 * servicio— y los dos tienen que repartir el mismo premio de la misma forma. Puesto en un sitio
 * común, no hay que acordarse de mantenerlos iguales.
 */
object AchievementRewards {

    /**
     * Los logros que dan objeto al desbloquearse.
     *
     * Elegidos por lo que cuestan, no por su categoría: el mismo criterio en captura, Pokédex,
     * constancia, agua y hábitos. Los de "primera vez" (catch_1, dex_10, water_1, habit_first...)
     * quedan fuera — son de bienvenida, se consiguen sin proponérselo.
     */
    val MILESTONE_IDS: Set<String> = setOf(
        "catch_50", "catch_100", "catch_151",
        "dex_50", "dex_100", "dex_full",
        "starters_final", "birds", "mewtwo",
        "evo_10", "level_100",
        "streak_30", "streak_100", "perfect_week",
        "water_30", "habit_30",
        "egg_hatch_10",
    )

    /** Reparte un objeto por cada logro grande en [unlocked]. El resto de la lista no hace nada. */
    fun grant(context: Context, unlocked: List<Achievement>) {
        val repository by lazy { PokemonRepository(context) }
        for (achievement in unlocked) {
            if (achievement.id !in MILESTONE_IDS) continue
            val item = ItemDropTable.roll(ItemDropTable.Source.ACHIEVEMENT)
            repository.addItem(item)
            GlyphRotationService.itemReceivedAnimation(context, item, ItemDropTable.Source.ACHIEVEMENT)
        }
    }
}
