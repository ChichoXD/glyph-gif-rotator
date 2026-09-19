package dev.glyphrotator.app.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprueba qué pasa cuando un Pokémon sube varios niveles de golpe, que es lo normal aquí:
 * el entrenamiento se cobra al encender la pantalla, así que una noche entera de sueño puede
 * dar cientos de puntos de experiencia de una sentada.
 */
class EvolutionChainTest {

    private fun juegoCon(especie: Int, nivel: Int): Pair<PokemonGame, FakePokemonStore> {
        val store = FakePokemonStore()
        val caught = store.addCaught(especie, nivel)
        store.setTrainingPartner(caught.uid)
        return PokemonGame(store) to store
    }

    @Test
    fun `subir un nivel evoluciona si toca`() {
        // Caterpie evoluciona a Metapod al 7.
        val (game, store) = juegoCon(especie = 10, nivel = 6)
        game.applyTraining(minutesWithScreenOff = 100)
        assertEquals(11, store.getCaught().single().speciesId)
    }

    @Test
    fun `subir de golpe hasta pasada la segunda evolucion llega hasta Butterfree`() {
        // Caterpie(10) -> Metapod al 7 -> Butterfree al 10. Con una noche de pantalla apagada
        // se pasan los dos escalones de una vez, y debe acabar en Butterfree, no en Metapod.
        val (game, store) = juegoCon(especie = 10, nivel = 5)

        // 8 horas: 480 min * 3 EXP + 24 bonus * 90 = 3600 EXP = 12 niveles.
        game.applyTraining(minutesWithScreenOff = 480)

        val resultado = store.getCaught().single()
        assertTrue("debería haber pasado del nivel 10, está en ${resultado.level}", resultado.level >= 10)
        assertEquals(
            "se quedó en ${PokemonRegistry[resultado.speciesId]?.name}",
            12,
            resultado.speciesId
        )
    }

    @Test
    fun `la cadena no se salta escalones que aun no tocan`() {
        // Con nivel 8 le toca Metapod, pero Butterfree (nivel 10) todavía no.
        val (game, store) = juegoCon(especie = 10, nivel = 6)
        game.applyTraining(minutesWithScreenOff = 60) // 180 EXP, sube justo un nivel
        assertEquals(11, store.getCaught().single().speciesId)
    }

    @Test
    fun `evolucionar apunta todas las especies en la Pokedex, no solo la ultima`() {
        val (game, store) = juegoCon(especie = 10, nivel = 5)
        game.applyTraining(minutesWithScreenOff = 480)
        val pokedex = store.getPokedex()
        assertTrue("falta Caterpie", 10 in pokedex)
        assertTrue("falta Metapod", 11 in pokedex)
        assertTrue("falta Butterfree", 12 in pokedex)
    }
}
