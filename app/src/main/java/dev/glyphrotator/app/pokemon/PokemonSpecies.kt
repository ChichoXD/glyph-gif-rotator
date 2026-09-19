package dev.glyphrotator.app.pokemon

/**
 * Modelo de datos de las especies (solo datos: número de Pokédex, nombre, tipos y cómo
 * evoluciona). No incluye sprites: las imágenes son propiedad de Nintendo/Game Freak y las
 * aporta el usuario por su cuenta; aquí solo se referencian por [id].
 *
 * La estructura (tipos, requisitos de evolución por especie) sigue el modelo de
 * equalparts/glyph-catch (MIT), reimplementado en nuestro propio paquete.
 */

enum class PokemonType {
    NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND,
    FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY
}

/** Objetos del inventario: piedras de evolución y consumibles. */
enum class PokemonItem {
    FIRE_STONE,
    WATER_STONE,
    THUNDER_STONE,
    LEAF_STONE,
    MOON_STONE,
    LINKING_CORD,
    RARE_CANDY,
    SUPER_ROD
}

/** Qué hace falta para que una especie evolucione a la siguiente. */
sealed class EvolutionRequirement {
    data class Level(val level: Int) : EvolutionRequirement()
    data class Stone(val item: PokemonItem) : EvolutionRequirement()
    /** Sin intercambio real entre dispositivos: se resuelve con [PokemonItem.LINKING_CORD]. */
    data object Trade : EvolutionRequirement()
}

data class PokemonSpecies(
    val id: Int,
    val name: String,
    val type1: PokemonType,
    val type2: PokemonType? = null,
    /** Ids de las especies a las que puede evolucionar. */
    val evolvesTo: MutableList<Int> = mutableListOf(),
    /** Qué hizo falta para llegar a ESTA especie (null si es una forma base). */
    val evolutionRequirement: EvolutionRequirement? = null
) {
    val displayName: String get() = name
}
