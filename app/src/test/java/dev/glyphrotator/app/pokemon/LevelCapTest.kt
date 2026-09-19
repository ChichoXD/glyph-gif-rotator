package dev.glyphrotator.app.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El tope de nivel que elige el usuario.
 *
 * Es lo que permite conservar una forma concreta: poniendo el tope justo debajo del nivel de
 * evolución, ese Pokémon se queda como está por mucho que lo entrenes.
 */
class LevelCapTest {

    private fun juegoCon(especie: Int, nivel: Int, tope: Int?): Pair<PokemonGame, FakePokemonStore> {
        val store = FakePokemonStore()
        val caught = store.addCaught(especie, nivel)
        store.updateCaught(caught.copy(levelCap = tope))
        store.setTrainingPartner(caught.uid)
        return PokemonGame(store) to store
    }

    @Test
    fun `sin tope sube todo lo que dé la experiencia`() {
        val (game, store) = juegoCon(especie = 4, nivel = 5, tope = null)
        game.applyTraining(minutesWithScreenOff = 480)
        assertTrue(store.getCaught().single().level > 10)
    }

    @Test
    fun `con tope se queda justo en el nivel elegido`() {
        val (game, store) = juegoCon(especie = 4, nivel = 5, tope = 15)
        game.applyTraining(minutesWithScreenOff = 480)
        assertEquals(15, store.getCaught().single().level)
    }

    @Test
    fun `el tope corta en seco, sin experiencia sobrante`() {
        // Si quedara experiencia acumulada, al subir el tope pasaría de nivel al instante y
        // se saltaría la evolución que querías ver.
        val (game, store) = juegoCon(especie = 4, nivel = 5, tope = 15)
        game.applyTraining(minutesWithScreenOff = 480)
        assertEquals(0, store.getCaught().single().exp)
    }

    @Test
    fun `en el tope deja de ganar experiencia`() {
        val (game, store) = juegoCon(especie = 4, nivel = 15, tope = 15)
        game.applyTraining(minutesWithScreenOff = 480)

        val resultado = store.getCaught().single()
        assertEquals(15, resultado.level)
        assertEquals(0, resultado.exp)
    }

    @Test
    fun `un tope por debajo de la evolucion conserva la forma`() {
        // Charmander evoluciona al 16: con el tope en 15 se queda Charmander para siempre.
        val (game, store) = juegoCon(especie = 4, nivel = 5, tope = 15)
        game.applyTraining(minutesWithScreenOff = 960)
        assertEquals("debería seguir siendo Charmander", 4, store.getCaught().single().speciesId)
    }

    @Test
    fun `un tope por encima deja que evolucione`() {
        val (game, store) = juegoCon(especie = 4, nivel = 5, tope = 20)
        game.applyTraining(minutesWithScreenOff = 960)

        val resultado = store.getCaught().single()
        assertEquals("debería haber evolucionado a Charmeleon", 5, resultado.speciesId)
        assertEquals(20, resultado.level)
    }

    @Test
    fun `el tope se conserva al entrenar`() {
        val (game, store) = juegoCon(especie = 4, nivel = 5, tope = 15)
        game.applyTraining(minutesWithScreenOff = 480)
        assertEquals(15, store.getCaught().single().levelCap)
    }

    @Test
    fun `isAtLevelCap solo es cierto al llegar`() {
        val store = FakePokemonStore()
        val base = store.addCaught(4, 10).copy(levelCap = 15)
        assertTrue(!base.isAtLevelCap)
        assertTrue(base.copy(level = 15).isAtLevelCap)
        assertTrue(base.copy(level = 16).isAtLevelCap)
        assertTrue(!base.copy(levelCap = null, level = 100).isAtLevelCap)
    }
}
