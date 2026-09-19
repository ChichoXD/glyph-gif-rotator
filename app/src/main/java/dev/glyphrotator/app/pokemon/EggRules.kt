package dev.glyphrotator.app.pokemon

import kotlin.random.Random

/**
 * Las reglas de los huevos, sin nada de Android para poder comprobarlas en los tests.
 *
 * La idea: cada diez capturas te llevas un huevo, los huevos **se acumulan y no se pierden**, y
 * se incuban con el teléfono en reposo igual que se entrena al compañero. Al abrirse dan una
 * forma base —de las que no vienen de evolucionar—, priorizando las que te falten en la
 * Pokédex, así que el huevo es la vía para completar lo que no te sale por suerte.
 */
object EggRules {

    /** Capturas necesarias para conseguir un huevo. */
    const val CAPTURES_PER_EGG = 10

    /** Minutos de pantalla apagada que tarda en abrirse. Tres horas. */
    const val INCUBATION_MINUTES = 180

    /** Lo incubado que está, de 0 a 1. Es lo que decide qué fase se dibuja. */
    fun progress(incubatedMinutes: Int): Float =
        (incubatedMinutes.toFloat() / DemoTuning.incubationMinutes).coerceIn(0f, 1f)

    fun isReady(incubatedMinutes: Int): Boolean =
        incubatedMinutes >= DemoTuning.incubationMinutes

    /**
     * De qué especies puede salir.
     *
     * Solo formas base, como en los juegos: de un huevo no sale un Charizard. Y de esas, las
     * que aún no estén registradas — si ya las tienes todas, vuelve a valer cualquiera, porque
     * quedarse sin poder abrir un huevo sería peor que repetir.
     */
    fun hatchCandidates(all: List<PokemonSpecies>, pokedex: Set<Int>): List<PokemonSpecies> {
        val bases = all.filter { it.evolutionRequirement == null }
        val missing = bases.filter { it.id !in pokedex }
        return missing.ifEmpty { bases }
    }

    fun pickHatch(
        all: List<PokemonSpecies>,
        pokedex: Set<Int>,
        random: Random = Random.Default,
    ): PokemonSpecies? = hatchCandidates(all, pokedex).randomOrNull(random)

    /** Nivel con el que sale el recién nacido: bajo, es un bebé. */
    val HATCH_LEVELS = 1..5
}
