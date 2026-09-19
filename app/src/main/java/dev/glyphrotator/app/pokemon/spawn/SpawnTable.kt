package dev.glyphrotator.app.pokemon.spawn

import dev.glyphrotator.app.pokemon.PokemonRegistry
import dev.glyphrotator.app.pokemon.PokemonSpecies
import dev.glyphrotator.app.pokemon.PokemonType
import kotlin.random.Random

/**
 * Qué Pokémon aparece, según lo que esté pasando en el mundo real.
 *
 * Funciona con pesos y no con listas cerradas por condición: cada especie parte de un peso
 * base según lo rara que sea, y las condiciones lo multiplican. Así llover no *sustituye* la
 * tabla por una de agua, sino que hace los de agua mucho más probables sin que deje de poder
 * salir cualquier otro — que es lo que hace que se sienta vivo en vez de programado.
 */
object SpawnTable {

    /**
     * Un Pokémon al azar con los pesos que tocan ahora mismo, o null si no hay ninguno
     * elegible (no debería pasar, pero el registro podría venir vacío en pruebas).
     */
    fun pick(
        conditions: SpawnConditions,
        random: Random = Random.Default,
        candidates: List<PokemonSpecies> = PokemonRegistry.all,
    ): PokemonSpecies? {
        val weights = candidates.map { weightOf(it, conditions) }
        val total = weights.sum()
        if (total <= 0f) return null

        var roll = random.nextFloat() * total
        for (index in candidates.indices) {
            roll -= weights[index]
            if (roll <= 0f) return candidates[index]
        }
        return candidates.lastOrNull()
    }

    /** El peso de una especie con estas condiciones. Público para poder verificar el reparto. */
    fun weightOf(species: PokemonSpecies, conditions: SpawnConditions): Float {
        var weight = rarityWeight(species)

        // El tiempo que hace. Son los multiplicadores que más se notan: con tormenta, los
        // eléctricos pasan de rareza a lo más habitual.
        when (conditions.weather) {
            Weather.RAIN -> {
                if (species.has(PokemonType.WATER)) weight *= 5f
                if (species.has(PokemonType.GRASS)) weight *= 3f
                if (species.has(PokemonType.FIRE)) weight *= 0.4f
            }
            Weather.THUNDERSTORM -> {
                if (species.has(PokemonType.ELECTRIC)) weight *= 5f
                if (species.has(PokemonType.FLYING)) weight *= 2f
            }
            Weather.SNOW -> {
                if (species.has(PokemonType.ICE)) weight *= 6f
                if (species.has(PokemonType.GRASS)) weight *= 0.5f
                if (species.has(PokemonType.FIRE)) weight *= 0.5f
            }
            Weather.CLOUDY -> {
                if (species.has(PokemonType.FLYING)) weight *= 1.5f
            }
            Weather.CLEAR -> {
                if (species.has(PokemonType.FIRE)) weight *= 1.5f
                if (species.has(PokemonType.GROUND)) weight *= 1.3f
            }
        }

        // De noche salen los que uno esperaría de noche, y se retiran los comunes de día.
        if (conditions.isNight) {
            if (species.has(PokemonType.GHOST)) weight *= 4f
            if (species.has(PokemonType.POISON)) weight *= 1.6f
            if (species.has(PokemonType.PSYCHIC)) weight *= 1.5f
            if (species.has(PokemonType.NORMAL)) weight *= 0.5f
        }
        if (conditions.isDay) {
            if (species.has(PokemonType.NORMAL)) weight *= 1.4f
            if (species.has(PokemonType.BUG)) weight *= 1.5f
        }

        // La estación mueve el fondo de la tabla sin llegar a mandar.
        when (conditions.season) {
            Season.WINTER -> {
                if (species.has(PokemonType.ICE)) weight *= 3f
                if (species.has(PokemonType.BUG)) weight *= 0.5f
            }
            Season.SPRING -> if (species.has(PokemonType.GRASS)) weight *= 2f
            Season.SUMMER -> {
                if (species.has(PokemonType.FIRE)) weight *= 2f
                if (species.has(PokemonType.WATER)) weight *= 1.5f
            }
            Season.AUTUMN -> if (species.has(PokemonType.GHOST)) weight *= 1.5f
        }

        // Fechas señaladas. Son el guiño que hace que merezca la pena tenerlo puesto todo el
        // año: en Halloween la Matrix se llena de fantasmas.
        if (conditions.isHalloween && species.has(PokemonType.GHOST)) weight *= 10f
        if (conditions.isChristmas && species.has(PokemonType.ICE)) weight *= 8f
        if (conditions.isFullMoon && species.has(PokemonType.PSYCHIC)) weight *= 4f

        // Cuanto más lleves sin mirar el teléfono, mejor sale la cosa: los raros solo
        // aparecen de verdad tras un buen rato en reposo.
        if (conditions.minutesScreenOff >= LONG_REST_MINUTES) {
            // Y haber dormido bien lo multiplica todavía más: es el premio que se nota,
            // porque cambia qué sale y no solo cuánto.
            val rested = SleepSchedule.rarityMultiplier(conditions.sleepQuality)
            weight *= when (rarity(species)) {
                Rarity.LEGENDARY -> 6f * rested
                Rarity.RARE -> 3f * rested
                Rarity.UNCOMMON -> 1.2f
                Rarity.COMMON -> 0.8f
            }
        } else if (rarity(species) == Rarity.LEGENDARY) {
            // Sin ese reposo, los legendarios directamente no salen.
            weight = 0f
        }

        return weight * duplicatePenalty(conditions.ownedBySpecies[species.id] ?: 0)
    }

    /**
     * Cuánto se penaliza tener ya alguno de esa especie.
     *
     * La caída es suave a propósito: quien quiera la colección completa —cada especie en
     * cada estado de su evolución— tiene que poder seguir encontrándolos. Lo que se corta es
     * el goteo infinito de copias, no la posibilidad.
     *
     * Pasado [MAX_DUPLICATES] deja de aparecer del todo, para que el equipo no se llene.
     */
    fun duplicatePenalty(owned: Int): Float = when {
        owned <= 0 -> 1f
        owned >= MAX_DUPLICATES -> 0f
        else -> 1f / (1f + owned * DUPLICATE_FALLOFF)
    }

    private fun PokemonSpecies.has(type: PokemonType) = type1 == type || type2 == type

    private fun rarityWeight(species: PokemonSpecies) = when (rarity(species)) {
        Rarity.COMMON -> 100f
        Rarity.UNCOMMON -> 40f
        Rarity.RARE -> 8f
        Rarity.LEGENDARY -> 1f
    }

    /**
     * La rareza sale de la cadena evolutiva, que es la señal que ya tenemos: una forma base
     * es común, una intermedia es menos frecuente, y una forma final —que no evoluciona a
     * nada más— es rara.
     */
    fun rarity(species: PokemonSpecies): Rarity = when {
        species.id in LEGENDARY_IDS -> Rarity.LEGENDARY
        species.evolutionRequirement != null && species.evolvesTo.isEmpty() -> Rarity.RARE
        species.evolutionRequirement != null -> Rarity.UNCOMMON
        else -> Rarity.COMMON
    }

    enum class Rarity { COMMON, UNCOMMON, RARE, LEGENDARY }

    /** Los pájaros legendarios, Mewtwo y Mew. */
    private val LEGENDARY_IDS = setOf(144, 145, 146, 150, 151)

    /** A partir de aquí el reposo se considera largo y se abre la mano con los raros. */
    const val LONG_REST_MINUTES = 60

    /** Cuántos se pueden tener de la misma especie antes de que deje de aparecer. */
    const val MAX_DUPLICATES = 3

    /** Cuánto baja el peso por cada copia que ya tengas. Suave: 1 -> 0,56 -> 0,38. */
    private const val DUPLICATE_FALLOFF = 0.8f
}
