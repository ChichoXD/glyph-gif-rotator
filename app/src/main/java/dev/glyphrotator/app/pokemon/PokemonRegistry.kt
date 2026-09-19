package dev.glyphrotator.app.pokemon

import dev.glyphrotator.app.pokemon.PokemonType.BUG
import dev.glyphrotator.app.pokemon.PokemonType.DRAGON
import dev.glyphrotator.app.pokemon.PokemonType.ELECTRIC
import dev.glyphrotator.app.pokemon.PokemonType.FAIRY
import dev.glyphrotator.app.pokemon.PokemonType.FIGHTING
import dev.glyphrotator.app.pokemon.PokemonType.FIRE
import dev.glyphrotator.app.pokemon.PokemonType.FLYING
import dev.glyphrotator.app.pokemon.PokemonType.GHOST
import dev.glyphrotator.app.pokemon.PokemonType.GRASS
import dev.glyphrotator.app.pokemon.PokemonType.GROUND
import dev.glyphrotator.app.pokemon.PokemonType.ICE
import dev.glyphrotator.app.pokemon.PokemonType.NORMAL
import dev.glyphrotator.app.pokemon.PokemonType.POISON
import dev.glyphrotator.app.pokemon.PokemonType.PSYCHIC
import dev.glyphrotator.app.pokemon.PokemonType.ROCK
import dev.glyphrotator.app.pokemon.PokemonType.STEEL
import dev.glyphrotator.app.pokemon.PokemonType.WATER

/**
 * Catálogo de especies (primera generación completa, 151 entradas) con sus tipos y cadenas
 * de evolución. Solo datos: los sprites no están aquí (ver [PokemonSpecies]).
 */
object PokemonRegistry {

    private val byId = LinkedHashMap<Int, PokemonSpecies>()

    val all: List<PokemonSpecies> get() = byId.values.toList()

    operator fun get(id: Int): PokemonSpecies? = byId[id]

    fun byName(name: String): PokemonSpecies? =
        byId.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun add(
        id: Int,
        name: String,
        type1: PokemonType,
        type2: PokemonType? = null,
        fromId: Int? = null,
        requirement: EvolutionRequirement? = null
    ): PokemonSpecies {
        val species = PokemonSpecies(id, name, type1, type2, evolutionRequirement = requirement)
        byId[id] = species
        if (fromId != null) byId[fromId]?.evolvesTo?.add(id)
        return species
    }

    private fun lvl(level: Int) = EvolutionRequirement.Level(level)
    private fun stone(item: PokemonItem) = EvolutionRequirement.Stone(item)
    private val trade = EvolutionRequirement.Trade

    init {
        add(1, "Bulbasaur", GRASS, POISON)
        add(2, "Ivysaur", GRASS, POISON, fromId = 1, requirement = lvl(16))
        add(3, "Venusaur", GRASS, POISON, fromId = 2, requirement = lvl(32))
        add(4, "Charmander", FIRE)
        add(5, "Charmeleon", FIRE, fromId = 4, requirement = lvl(16))
        add(6, "Charizard", FIRE, FLYING, fromId = 5, requirement = lvl(36))
        add(7, "Squirtle", WATER)
        add(8, "Wartortle", WATER, fromId = 7, requirement = lvl(16))
        add(9, "Blastoise", WATER, fromId = 8, requirement = lvl(36))
        add(10, "Caterpie", BUG)
        add(11, "Metapod", BUG, fromId = 10, requirement = lvl(7))
        add(12, "Butterfree", BUG, FLYING, fromId = 11, requirement = lvl(10))
        add(13, "Weedle", BUG, POISON)
        add(14, "Kakuna", BUG, POISON, fromId = 13, requirement = lvl(7))
        add(15, "Beedrill", BUG, POISON, fromId = 14, requirement = lvl(10))
        add(16, "Pidgey", NORMAL, FLYING)
        add(17, "Pidgeotto", NORMAL, FLYING, fromId = 16, requirement = lvl(18))
        add(18, "Pidgeot", NORMAL, FLYING, fromId = 17, requirement = lvl(36))
        add(19, "Rattata", NORMAL)
        add(20, "Raticate", NORMAL, fromId = 19, requirement = lvl(20))
        add(21, "Spearow", NORMAL, FLYING)
        add(22, "Fearow", NORMAL, FLYING, fromId = 21, requirement = lvl(20))
        add(23, "Ekans", POISON)
        add(24, "Arbok", POISON, fromId = 23, requirement = lvl(22))
        add(25, "Pikachu", ELECTRIC)
        add(26, "Raichu", ELECTRIC, fromId = 25, requirement = stone(PokemonItem.THUNDER_STONE))
        add(27, "Sandshrew", GROUND)
        add(28, "Sandslash", GROUND, fromId = 27, requirement = lvl(22))
        add(29, "Nidoran♀", POISON)
        add(30, "Nidorina", POISON, fromId = 29, requirement = lvl(16))
        add(31, "Nidoqueen", POISON, GROUND, fromId = 30, requirement = stone(PokemonItem.MOON_STONE))
        add(32, "Nidoran♂", POISON)
        add(33, "Nidorino", POISON, fromId = 32, requirement = lvl(16))
        add(34, "Nidoking", POISON, GROUND, fromId = 33, requirement = stone(PokemonItem.MOON_STONE))
        add(35, "Clefairy", FAIRY)
        add(36, "Clefable", FAIRY, fromId = 35, requirement = stone(PokemonItem.MOON_STONE))
        add(37, "Vulpix", FIRE)
        add(38, "Ninetales", FIRE, fromId = 37, requirement = stone(PokemonItem.FIRE_STONE))
        add(39, "Jigglypuff", NORMAL, FAIRY)
        add(40, "Wigglytuff", NORMAL, FAIRY, fromId = 39, requirement = stone(PokemonItem.MOON_STONE))
        add(41, "Zubat", POISON, FLYING)
        add(42, "Golbat", POISON, FLYING, fromId = 41, requirement = lvl(22))
        add(43, "Oddish", GRASS, POISON)
        add(44, "Gloom", GRASS, POISON, fromId = 43, requirement = lvl(21))
        add(45, "Vileplume", GRASS, POISON, fromId = 44, requirement = stone(PokemonItem.LEAF_STONE))
        add(46, "Paras", BUG, GRASS)
        add(47, "Parasect", BUG, GRASS, fromId = 46, requirement = lvl(24))
        add(48, "Venonat", BUG, POISON)
        add(49, "Venomoth", BUG, POISON, fromId = 48, requirement = lvl(31))
        add(50, "Diglett", GROUND)
        add(51, "Dugtrio", GROUND, fromId = 50, requirement = lvl(26))
        add(52, "Meowth", NORMAL)
        add(53, "Persian", NORMAL, fromId = 52, requirement = lvl(28))
        add(54, "Psyduck", WATER)
        add(55, "Golduck", WATER, fromId = 54, requirement = lvl(33))
        add(56, "Mankey", FIGHTING)
        add(57, "Primeape", FIGHTING, fromId = 56, requirement = lvl(28))
        add(58, "Growlithe", FIRE)
        add(59, "Arcanine", FIRE, fromId = 58, requirement = stone(PokemonItem.FIRE_STONE))
        add(60, "Poliwag", WATER)
        add(61, "Poliwhirl", WATER, fromId = 60, requirement = lvl(25))
        add(62, "Poliwrath", WATER, FIGHTING, fromId = 61, requirement = stone(PokemonItem.WATER_STONE))
        add(63, "Abra", PSYCHIC)
        add(64, "Kadabra", PSYCHIC, fromId = 63, requirement = lvl(16))
        add(65, "Alakazam", PSYCHIC, fromId = 64, requirement = trade)
        add(66, "Machop", FIGHTING)
        add(67, "Machoke", FIGHTING, fromId = 66, requirement = lvl(28))
        add(68, "Machamp", FIGHTING, fromId = 67, requirement = trade)
        add(69, "Bellsprout", GRASS, POISON)
        add(70, "Weepinbell", GRASS, POISON, fromId = 69, requirement = lvl(21))
        add(71, "Victreebel", GRASS, POISON, fromId = 70, requirement = stone(PokemonItem.LEAF_STONE))
        add(72, "Tentacool", WATER, POISON)
        add(73, "Tentacruel", WATER, POISON, fromId = 72, requirement = lvl(30))
        add(74, "Geodude", ROCK, GROUND)
        add(75, "Graveler", ROCK, GROUND, fromId = 74, requirement = lvl(25))
        add(76, "Golem", ROCK, GROUND, fromId = 75, requirement = trade)
        add(77, "Ponyta", FIRE)
        add(78, "Rapidash", FIRE, fromId = 77, requirement = lvl(40))
        add(79, "Slowpoke", WATER, PSYCHIC)
        add(80, "Slowbro", WATER, PSYCHIC, fromId = 79, requirement = lvl(37))
        add(81, "Magnemite", ELECTRIC, STEEL)
        add(82, "Magneton", ELECTRIC, STEEL, fromId = 81, requirement = lvl(30))
        add(83, "Farfetch'd", NORMAL, FLYING)
        add(84, "Doduo", NORMAL, FLYING)
        add(85, "Dodrio", NORMAL, FLYING, fromId = 84, requirement = lvl(31))
        add(86, "Seel", WATER)
        add(87, "Dewgong", WATER, ICE, fromId = 86, requirement = lvl(34))
        add(88, "Grimer", POISON)
        add(89, "Muk", POISON, fromId = 88, requirement = lvl(38))
        add(90, "Shellder", WATER)
        add(91, "Cloyster", WATER, ICE, fromId = 90, requirement = stone(PokemonItem.WATER_STONE))
        add(92, "Gastly", GHOST, POISON)
        add(93, "Haunter", GHOST, POISON, fromId = 92, requirement = lvl(25))
        add(94, "Gengar", GHOST, POISON, fromId = 93, requirement = trade)
        add(95, "Onix", ROCK, GROUND)
        add(96, "Drowzee", PSYCHIC)
        add(97, "Hypno", PSYCHIC, fromId = 96, requirement = lvl(26))
        add(98, "Krabby", WATER)
        add(99, "Kingler", WATER, fromId = 98, requirement = lvl(28))
        add(100, "Voltorb", ELECTRIC)
        add(101, "Electrode", ELECTRIC, fromId = 100, requirement = lvl(30))
        add(102, "Exeggcute", GRASS, PSYCHIC)
        add(103, "Exeggutor", GRASS, PSYCHIC, fromId = 102, requirement = stone(PokemonItem.LEAF_STONE))
        add(104, "Cubone", GROUND)
        add(105, "Marowak", GROUND, fromId = 104, requirement = lvl(28))
        add(106, "Hitmonlee", FIGHTING)
        add(107, "Hitmonchan", FIGHTING)
        add(108, "Lickitung", NORMAL)
        add(109, "Koffing", POISON)
        add(110, "Weezing", POISON, fromId = 109, requirement = lvl(35))
        add(111, "Rhyhorn", GROUND, ROCK)
        add(112, "Rhydon", GROUND, ROCK, fromId = 111, requirement = lvl(42))
        add(113, "Chansey", NORMAL)
        add(114, "Tangela", GRASS)
        add(115, "Kangaskhan", NORMAL)
        add(116, "Horsea", WATER)
        add(117, "Seadra", WATER, fromId = 116, requirement = lvl(32))
        add(118, "Goldeen", WATER)
        add(119, "Seaking", WATER, fromId = 118, requirement = lvl(33))
        add(120, "Staryu", WATER)
        add(121, "Starmie", WATER, PSYCHIC, fromId = 120, requirement = stone(PokemonItem.WATER_STONE))
        add(122, "Mr. Mime", PSYCHIC, FAIRY)
        add(123, "Scyther", BUG, FLYING)
        add(124, "Jynx", ICE, PSYCHIC)
        add(125, "Electabuzz", ELECTRIC)
        add(126, "Magmar", FIRE)
        add(127, "Pinsir", BUG)
        add(128, "Tauros", NORMAL)
        add(129, "Magikarp", WATER)
        add(130, "Gyarados", WATER, FLYING, fromId = 129, requirement = lvl(20))
        add(131, "Lapras", WATER, ICE)
        add(132, "Ditto", NORMAL)
        add(133, "Eevee", NORMAL)
        add(134, "Vaporeon", WATER, fromId = 133, requirement = stone(PokemonItem.WATER_STONE))
        add(135, "Jolteon", ELECTRIC, fromId = 133, requirement = stone(PokemonItem.THUNDER_STONE))
        add(136, "Flareon", FIRE, fromId = 133, requirement = stone(PokemonItem.FIRE_STONE))
        add(137, "Porygon", NORMAL)
        add(138, "Omanyte", ROCK, WATER)
        add(139, "Omastar", ROCK, WATER, fromId = 138, requirement = lvl(40))
        add(140, "Kabuto", ROCK, WATER)
        add(141, "Kabutops", ROCK, WATER, fromId = 140, requirement = lvl(40))
        add(142, "Aerodactyl", ROCK, FLYING)
        add(143, "Snorlax", NORMAL)
        add(144, "Articuno", ICE, FLYING)
        add(145, "Zapdos", ELECTRIC, FLYING)
        add(146, "Moltres", FIRE, FLYING)
        add(147, "Dratini", DRAGON)
        add(148, "Dragonair", DRAGON, fromId = 147, requirement = lvl(30))
        add(149, "Dragonite", DRAGON, FLYING, fromId = 148, requirement = lvl(55))
        add(150, "Mewtwo", PSYCHIC)
        add(151, "Mew", PSYCHIC)
    }
}
