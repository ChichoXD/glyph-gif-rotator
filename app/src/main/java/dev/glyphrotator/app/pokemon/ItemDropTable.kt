package dev.glyphrotator.app.pokemon

import kotlin.random.Random

/**
 * Qué objeto te llevas, según por dónde te lo has ganado. Sin nada de Android, para poder
 * comprobarlo en los tests — mismo motivo que [dev.glyphrotator.app.pokemon.spawn.SpawnTable].
 *
 * Tres fuentes, tres tablas, cada una más generosa que la anterior porque cada una cuesta más:
 *
 * - [Source.CAPTURE] — cada [CAPTURES_PER_ITEM] capturas reales. Es la más barata de conseguir,
 *   así que reparte sobre todo piedras: hacen falta varias veces (Eevee solo puede evolucionar
 *   una vez, pero las piedras no se comparten entre especies) y así no se cuelga la evolución
 *   de nadie por no haber salido la piedra que tocaba.
 * - [Source.PERFECT_DAY] — un día entero con sueño, agua y todos tus hábitos cumplidos. Cuesta
 *   un día de vida real, no un rato de juego, así que reparte parejo entre las cinco piedras, el
 *   cable de unión y el Caramelo Raro.
 * - [Source.ACHIEVEMENT] — solo los logros grandes (ver [dev.glyphrotator.app.service.GlyphRotationService]
 *   para la lista), que se consiguen una vez en toda la partida. Es la única que favorece de
 *   verdad el Caramelo Raro: un logro grande merece el objeto más directo, el que sube nivel sin
 *   depender de qué especie tengas.
 *
 * **La Caña Súper (`SUPER_ROD`) no sale nunca de aquí.** No evoluciona nada —
 * `EvolutionResolver.canUseItemOn` la deja fija en `false`— así que darla como premio sería
 * regalar un objeto que no hace nada, que es peor que no darlo: parecería un fallo, no un premio.
 */
object ItemDropTable {

    enum class Source { CAPTURE, PERFECT_DAY, ACHIEVEMENT }

    /** Cada cuántas capturas reales cae un objeto. Sin relación con [EggRules.CAPTURES_PER_EGG]: son premios distintos y no hace falta que coincidan. */
    const val CAPTURES_PER_ITEM = 5

    /** Un objeto al azar con los pesos de [source]. */
    fun roll(source: Source, random: Random = Random.Default): PokemonItem {
        val weights = weightsFor(source)
        val total = weights.values.sum()
        var draw = random.nextFloat() * total
        for ((item, weight) in weights) {
            draw -= weight
            if (draw <= 0f) return item
        }
        return weights.keys.last()
    }

    /** Público para poder verificar el reparto exacto de cada tabla en los tests. */
    fun weightsFor(source: Source): Map<PokemonItem, Float> = when (source) {
        Source.CAPTURE -> CAPTURE_WEIGHTS
        Source.PERFECT_DAY -> PERFECT_DAY_WEIGHTS
        Source.ACHIEVEMENT -> ACHIEVEMENT_WEIGHTS
    }

    private val STONES = listOf(
        PokemonItem.FIRE_STONE,
        PokemonItem.WATER_STONE,
        PokemonItem.THUNDER_STONE,
        PokemonItem.LEAF_STONE,
        PokemonItem.MOON_STONE,
    )

    private val CAPTURE_WEIGHTS: Map<PokemonItem, Float> = buildMap {
        STONES.forEach { put(it, 10f) }
        put(PokemonItem.LINKING_CORD, 6f)
        put(PokemonItem.RARE_CANDY, 3f)
    }

    private val PERFECT_DAY_WEIGHTS: Map<PokemonItem, Float> = buildMap {
        STONES.forEach { put(it, 8f) }
        put(PokemonItem.LINKING_CORD, 8f)
        put(PokemonItem.RARE_CANDY, 8f)
    }

    private val ACHIEVEMENT_WEIGHTS: Map<PokemonItem, Float> = buildMap {
        STONES.forEach { put(it, 4f) }
        put(PokemonItem.LINKING_CORD, 6f)
        put(PokemonItem.RARE_CANDY, 12f)
    }
}
