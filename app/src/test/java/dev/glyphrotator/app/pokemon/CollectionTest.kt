package dev.glyphrotator.app.pokemon

import dev.glyphrotator.app.pokemon.spawn.SpawnConditions
import dev.glyphrotator.app.pokemon.spawn.SpawnTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que hace posible completar la colección entera —cada especie en cada estado de su
 * evolución— sin que el equipo acabe siendo una pila de copias.
 */
class DuplicatePenaltyTest {

    @Test
    fun `sin tener ninguno el peso es el normal`() {
        assertEquals(1f, SpawnTable.duplicatePenalty(0), 0.0001f)
    }

    @Test
    fun `los repetidos bajan, pero sin desaparecer`() {
        // La caída es suave a propósito: quien va a por la colección completa tiene que poder
        // seguir encontrándolos.
        val uno = SpawnTable.duplicatePenalty(1)
        val dos = SpawnTable.duplicatePenalty(2)
        assertTrue("con uno debería quedar por encima de la mitad, fue $uno", uno > 0.5f)
        assertTrue(dos < uno)
        assertTrue("con dos todavía debería salir, fue $dos", dos > 0.2f)
    }

    @Test
    fun `pasado el tope deja de aparecer`() {
        // Es lo que evita que el equipo se llene de copias del mismo.
        assertEquals(0f, SpawnTable.duplicatePenalty(SpawnTable.MAX_DUPLICATES), 0.0001f)
        assertEquals(0f, SpawnTable.duplicatePenalty(99), 0.0001f)
    }

    @Test
    fun `tener uno reduce su peso en la tabla real`() {
        val base = SpawnConditions(minutesScreenOff = 90, pokedexCount = 10)
        val conUno = base.copy(ownedBySpecies = mapOf(25 to 1))
        val pikachu = PokemonRegistry[25]!!

        assertTrue(SpawnTable.weightOf(pikachu, conUno) < SpawnTable.weightOf(pikachu, base))
    }

    @Test
    fun `con el tope alcanzado ese ya no sale, pero los demas si`() {
        val conditions = SpawnConditions(
            minutesScreenOff = 90,
            pokedexCount = 10,
            ownedBySpecies = mapOf(25 to SpawnTable.MAX_DUPLICATES),
        )
        assertEquals(0f, SpawnTable.weightOf(PokemonRegistry[25]!!, conditions), 0.0001f)
        assertTrue(SpawnTable.weightOf(PokemonRegistry[1]!!, conditions) > 0f)
    }

    @Test
    fun `tener a Charmander no afecta a Charmeleon`() {
        // Son especies distintas: el tope es por especie, que es lo que permite tener la
        // cadena entera a la vez.
        val conditions = SpawnConditions(
            minutesScreenOff = 90,
            pokedexCount = 10,
            ownedBySpecies = mapOf(4 to SpawnTable.MAX_DUPLICATES),
        )
        assertEquals(0f, SpawnTable.weightOf(PokemonRegistry[4]!!, conditions), 0.0001f)
        assertTrue(SpawnTable.weightOf(PokemonRegistry[5]!!, conditions) > 0f)
    }
}

/**
 * El bloqueo de evolución: sube de nivel pero no cambia de forma.
 */
class EvolutionLockTest {

    private fun juegoCon(especie: Int, nivel: Int, bloqueado: Boolean): Pair<PokemonGame, FakePokemonStore> {
        val store = FakePokemonStore()
        val caught = store.addCaught(especie, nivel)
        store.updateCaught(caught.copy(isEvolutionLocked = bloqueado))
        store.setTrainingPartner(caught.uid)
        return PokemonGame(store) to store
    }

    @Test
    fun `sin bloqueo evoluciona al llegar al nivel`() {
        val (game, store) = juegoCon(especie = 4, nivel = 15, bloqueado = false)
        game.applyTraining(minutesWithScreenOff = 120)
        assertEquals("debería haber pasado a Charmeleon", 5, store.getCaught().single().speciesId)
    }

    @Test
    fun `con bloqueo sube de nivel pero sigue siendo el mismo`() {
        // Es lo que permite conservar a Charmander mientras tienes también a sus evoluciones.
        val (game, store) = juegoCon(especie = 4, nivel = 15, bloqueado = true)
        game.applyTraining(minutesWithScreenOff = 480)

        val resultado = store.getCaught().single()
        assertEquals(4, resultado.speciesId)
        assertTrue("debería haber subido de nivel igualmente", resultado.level > 15)
    }

    @Test
    fun `el bloqueo sobrevive a subir de nivel`() {
        val (game, store) = juegoCon(especie = 4, nivel = 15, bloqueado = true)
        game.applyTraining(minutesWithScreenOff = 480)
        assertTrue(store.getCaught().single().isEvolutionLocked)
    }
}

/**
 * Saber a qué nivel evoluciona es lo que permite decidir cuándo parar.
 */
class EvolutionLevelHintTest {

    @Test
    fun `dice el nivel al que le toca`() {
        assertEquals(16, EvolutionResolver.evolutionLevelOf(4))  // Charmander -> Charmeleon
        assertEquals(36, EvolutionResolver.evolutionLevelOf(5))  // Charmeleon -> Charizard
        assertEquals(7, EvolutionResolver.evolutionLevelOf(10))  // Caterpie -> Metapod
    }

    @Test
    fun `null si no evoluciona por nivel`() {
        assertNull(EvolutionResolver.evolutionLevelOf(6))    // Charizard, forma final
        assertNull(EvolutionResolver.evolutionLevelOf(25))   // Pikachu evoluciona con piedra
    }
}
