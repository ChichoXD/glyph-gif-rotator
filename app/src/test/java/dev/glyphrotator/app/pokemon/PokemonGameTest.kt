package dev.glyphrotator.app.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PokemonGameTest {

    private lateinit var store: FakePokemonStore
    private lateinit var game: PokemonGame

    @Before
    fun setUp() {
        store = FakePokemonStore()
        game = PokemonGame(store)
    }

    // =====================================================================================
    // Entrenamiento
    // =====================================================================================

    @Test
    fun `sin companero de entrenamiento no pasa nada`() {
        store.addCaught(speciesId = 25)
        assertEquals(PokemonGame.Outcome.NothingHappened, game.applyTraining(60))
    }

    @Test
    fun `el companero gana experiencia con la pantalla apagada`() {
        val pikachu = store.addCaught(speciesId = 25, level = 5)
        store.setTrainingPartner(pikachu.uid)

        game.applyTraining(minutesWithScreenOff = 10)

        val updated = store.getCaught().first()
        assertEquals(5, updated.level)
        assertEquals(10 * TrainingRules.EXP_PER_MINUTE, updated.exp)
    }

    @Test
    fun `entrenar lo suficiente sube de nivel`() {
        val pikachu = store.addCaught(speciesId = 25, level = 5)
        store.setTrainingPartner(pikachu.uid)

        val outcome = game.applyTraining(minutesWithScreenOff = 100)

        assertTrue(outcome is PokemonGame.Outcome.LeveledUp)
        assertTrue(store.getCaught().first().level > 5)
    }

    @Test
    fun `entrenar hasta el nivel de evolucion evoluciona solo`() {
        // Bulbasaur evoluciona a Ivysaur al nivel 16.
        val bulbasaur = store.addCaught(speciesId = 1, level = 15)
        store.setTrainingPartner(bulbasaur.uid)

        val outcome = game.applyTraining(minutesWithScreenOff = 200)

        assertTrue(outcome is PokemonGame.Outcome.Evolved)
        val evolved = outcome as PokemonGame.Outcome.Evolved
        assertEquals("Bulbasaur", evolved.from.name)
        assertEquals("Ivysaur", evolved.to.name)
        assertEquals(2, store.getCaught().first().speciesId)
        assertTrue(store.getPokedex().contains(2))
    }

    @Test
    fun `un pokemon al maximo ya no gana nada`() {
        val pokemon = store.addCaught(speciesId = 25, level = LevelCalculator.MAX_LEVEL)
        store.setTrainingPartner(pokemon.uid)
        assertEquals(PokemonGame.Outcome.NothingHappened, game.applyTraining(500))
    }

    // =====================================================================================
    // Piedras de evolución
    // =====================================================================================

    @Test
    fun `la piedra correcta evoluciona y consume el objeto`() {
        val pikachu = store.addCaught(speciesId = 25, level = 20)
        store.addItem(PokemonItem.THUNDER_STONE)

        val outcome = game.useItem(PokemonItem.THUNDER_STONE, pikachu.uid)

        assertTrue(outcome is PokemonGame.Outcome.Evolved)
        assertEquals(26, store.getCaught().first().speciesId)
        assertEquals(0, store.getQuantity(PokemonItem.THUNDER_STONE))
        assertTrue(store.getPokedex().contains(26))
    }

    @Test
    fun `la piedra equivocada no hace nada ni se gasta`() {
        val pikachu = store.addCaught(speciesId = 25, level = 20)
        store.addItem(PokemonItem.WATER_STONE)

        val outcome = game.useItem(PokemonItem.WATER_STONE, pikachu.uid)

        assertTrue(outcome is PokemonGame.Outcome.Failed)
        assertEquals(25, store.getCaught().first().speciesId)
        assertEquals(1, store.getQuantity(PokemonItem.WATER_STONE))
    }

    @Test
    fun `sin el objeto en el inventario no se puede usar`() {
        val pikachu = store.addCaught(speciesId = 25, level = 20)
        val outcome = game.useItem(PokemonItem.THUNDER_STONE, pikachu.uid)
        assertTrue(outcome is PokemonGame.Outcome.Failed)
        assertEquals(25, store.getCaught().first().speciesId)
    }

    @Test
    fun `cada piedra lleva a un eeveelution distinto`() {
        val eevee = store.addCaught(speciesId = 133, level = 20)
        store.addItem(PokemonItem.FIRE_STONE)

        game.useItem(PokemonItem.FIRE_STONE, eevee.uid)

        assertEquals(136, store.getCaught().first().speciesId) // Flareon
    }

    // =====================================================================================
    // Caramelo raro y cable unión
    // =====================================================================================

    @Test
    fun `el caramelo raro sube exactamente un nivel`() {
        val pokemon = store.addCaught(speciesId = 25, level = 10)
        store.addItem(PokemonItem.RARE_CANDY)

        val outcome = game.useItem(PokemonItem.RARE_CANDY, pokemon.uid)

        assertTrue(outcome is PokemonGame.Outcome.LeveledUp)
        assertEquals(11, store.getCaught().first().level)
        assertEquals(0, store.getQuantity(PokemonItem.RARE_CANDY))
    }

    @Test
    fun `el caramelo raro puede desencadenar una evolucion por nivel`() {
        val bulbasaur = store.addCaught(speciesId = 1, level = 15)
        store.addItem(PokemonItem.RARE_CANDY)

        val outcome = game.useItem(PokemonItem.RARE_CANDY, bulbasaur.uid)

        assertTrue(outcome is PokemonGame.Outcome.Evolved)
        assertEquals(2, store.getCaught().first().speciesId)
    }

    @Test
    fun `el cable union resuelve las evoluciones por intercambio`() {
        val kadabra = store.addCaught(speciesId = 64, level = 30)
        store.addItem(PokemonItem.LINKING_CORD)

        val outcome = game.useItem(PokemonItem.LINKING_CORD, kadabra.uid)

        assertTrue(outcome is PokemonGame.Outcome.Evolved)
        assertEquals(65, store.getCaught().first().speciesId) // Alakazam
    }

    @Test
    fun `el cable union no sirve en un pokemon que no evoluciona asi`() {
        val pikachu = store.addCaught(speciesId = 25, level = 30)
        store.addItem(PokemonItem.LINKING_CORD)

        val outcome = game.useItem(PokemonItem.LINKING_CORD, pikachu.uid)

        assertTrue(outcome is PokemonGame.Outcome.Failed)
        assertEquals(1, store.getQuantity(PokemonItem.LINKING_CORD))
    }

    // =====================================================================================
    // Estado general
    // =====================================================================================

    @Test
    fun `usar un objeto sobre un pokemon inexistente falla sin romper nada`() {
        store.addItem(PokemonItem.RARE_CANDY)
        val outcome = game.useItem(PokemonItem.RARE_CANDY, "no-existe")
        assertTrue(outcome is PokemonGame.Outcome.Failed)
        assertEquals(1, store.getQuantity(PokemonItem.RARE_CANDY))
    }

    @Test
    fun `solo hay un companero de entrenamiento a la vez`() {
        val first = store.addCaught(speciesId = 25)
        val second = store.addCaught(speciesId = 1)

        store.setTrainingPartner(first.uid)
        store.setTrainingPartner(second.uid)

        assertEquals(second.uid, store.getTrainingPartner()?.uid)
        assertEquals(1, store.getCaught().count { it.isTrainingPartner })
    }

    @Test
    fun `capturar registra la especie en la pokedex`() {
        store.addCaught(speciesId = 25)
        store.addCaught(speciesId = 25)

        assertEquals(setOf(25), store.getPokedex())
        assertEquals(2, store.getCaught().size)
    }

    @Test
    fun `el progreso de pokedex cuenta especies unicas sobre el total`() {
        store.addCaught(speciesId = 1)
        store.addCaught(speciesId = 4)

        val (seen, total) = game.pokedexProgress()
        assertEquals(2, seen)
        assertEquals(151, total)
    }

    @Test
    fun `solo se ofrecen los objetos que harian algo`() {
        val pikachu = store.addCaught(speciesId = 25, level = 20)
        store.addItem(PokemonItem.THUNDER_STONE)
        store.addItem(PokemonItem.WATER_STONE)
        store.addItem(PokemonItem.RARE_CANDY)

        val usable = game.usableItemsFor(store.getCaught().first { it.uid == pikachu.uid })

        assertTrue(usable.contains(PokemonItem.THUNDER_STONE))
        assertTrue(usable.contains(PokemonItem.RARE_CANDY))
        assertFalse(usable.contains(PokemonItem.WATER_STONE))
    }
}
