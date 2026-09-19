package dev.glyphrotator.app.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EggRulesTest {

    @Test
    fun `no esta listo hasta cumplir la incubacion completa`() {
        assertFalse(EggRules.isReady(EggRules.INCUBATION_MINUTES - 1))
        assertTrue(EggRules.isReady(EggRules.INCUBATION_MINUTES))
    }

    @Test
    fun `el progreso va de cero a uno y no se pasa`() {
        assertEquals(0f, EggRules.progress(0), 0.001f)
        assertEquals(0.5f, EggRules.progress(EggRules.INCUBATION_MINUTES / 2), 0.01f)
        assertEquals(1f, EggRules.progress(EggRules.INCUBATION_MINUTES * 10), 0.001f)
    }

    /**
     * Lo que hace útil al huevo: que sirva para completar la Pokédex y no para repetir lo que
     * ya tienes. Si esto se rompiera, el huevo pasaría a ser una captura más.
     */
    @Test
    fun `prioriza las especies que faltan en la pokedex`() {
        val bases = PokemonRegistry.all.filter { it.evolutionRequirement == null }
        val pokedex = bases.dropLast(1).map { it.id }.toSet()

        val candidates = EggRules.hatchCandidates(PokemonRegistry.all, pokedex)

        assertEquals(listOf(bases.last().id), candidates.map { it.id })
    }

    @Test
    fun `con la pokedex completa sigue pudiendo salir algo`() {
        val pokedex = PokemonRegistry.all.map { it.id }.toSet()

        val candidates = EggRules.hatchCandidates(PokemonRegistry.all, pokedex)

        assertTrue(candidates.isNotEmpty())
    }

    /** De un huevo no sale un Charizard: solo formas que no vienen de evolucionar. */
    @Test
    fun `solo salen formas base`() {
        val candidates = EggRules.hatchCandidates(PokemonRegistry.all, emptySet())

        assertTrue(candidates.all { it.evolutionRequirement == null })
    }
}
