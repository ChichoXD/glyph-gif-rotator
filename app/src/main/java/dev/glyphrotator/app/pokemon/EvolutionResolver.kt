package dev.glyphrotator.app.pokemon

/**
 * Decide si un Pokémon capturado puede evolucionar y en qué se convierte. Cada especie
 * declara su propio requisito ([EvolutionRequirement]), así que no hay reglas genéricas:
 * se consulta la cadena real de [PokemonRegistry].
 */
object EvolutionResolver {

    /** Evolución por nivel alcanzada (p. ej. Ivysaur a nivel 32), o null. */
    fun levelEvolutionTarget(speciesId: Int, level: Int): PokemonSpecies? {
        val species = PokemonRegistry[speciesId] ?: return null
        return species.evolvesTo
            .mapNotNull { PokemonRegistry[it] }
            .firstOrNull { target ->
                val requirement = target.evolutionRequirement
                requirement is EvolutionRequirement.Level && level >= requirement.level
            }
    }

    /** Evolución al usar una piedra concreta (p. ej. Pikachu + Piedra Trueno), o null. */
    fun stoneEvolutionTarget(speciesId: Int, stone: PokemonItem): PokemonSpecies? {
        val species = PokemonRegistry[speciesId] ?: return null
        return species.evolvesTo
            .mapNotNull { PokemonRegistry[it] }
            .firstOrNull { target ->
                val requirement = target.evolutionRequirement
                requirement is EvolutionRequirement.Stone && requirement.item == stone
            }
    }

    /** Evolución por intercambio, resuelta con el Cable Unión, o null. */
    fun tradeEvolutionTarget(speciesId: Int): PokemonSpecies? {
        val species = PokemonRegistry[speciesId] ?: return null
        return species.evolvesTo
            .mapNotNull { PokemonRegistry[it] }
            .firstOrNull { it.evolutionRequirement is EvolutionRequirement.Trade }
    }

    /** Si este objeto sirve de algo sobre este Pokémon ahora mismo (para habilitar la UI). */
    fun canUseItemOn(item: PokemonItem, speciesId: Int, level: Int): Boolean = when (item) {
        PokemonItem.RARE_CANDY -> level < LevelCalculator.MAX_LEVEL
        PokemonItem.LINKING_CORD -> tradeEvolutionTarget(speciesId) != null
        PokemonItem.FIRE_STONE,
        PokemonItem.WATER_STONE,
        PokemonItem.THUNDER_STONE,
        PokemonItem.LEAF_STONE,
        PokemonItem.MOON_STONE -> stoneEvolutionTarget(speciesId, item) != null
        PokemonItem.SUPER_ROD -> false
    }

    /**
     * A qué nivel evolucionaría esta especie, o null si no evoluciona por nivel.
     *
     * Es lo que hay que enseñar al usuario para que sepa cuándo parar de subirlo si quiere
     * conservar esta forma en el equipo.
     */
    fun evolutionLevelOf(speciesId: Int): Int? {
        val species = PokemonRegistry[speciesId] ?: return null
        return species.evolvesTo
            .mapNotNull { PokemonRegistry[it] }
            .firstNotNullOfOrNull { (it.evolutionRequirement as? EvolutionRequirement.Level)?.level }
    }

    /** Todas las piedras de evolución (sin contar consumibles como el Caramelo Raro). */
    val evolutionStones: List<PokemonItem> = listOf(
        PokemonItem.FIRE_STONE,
        PokemonItem.WATER_STONE,
        PokemonItem.THUNDER_STONE,
        PokemonItem.LEAF_STONE,
        PokemonItem.MOON_STONE
    )
}
