package dev.glyphrotator.app.pokemon.spawn

import dev.glyphrotator.app.pokemon.PokemonRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepScheduleTest {

    @Test
    fun `sin dormir la calidad es cero`() {
        assertEquals(0f, SleepSchedule.quality(minutesAsleep = 0), 0.0001f)
    }

    @Test
    fun `cumplir el objetivo a la hora da la calidad maxima`() {
        val q = SleepSchedule.quality(minutesAsleep = 450, minutesLateToBed = 0)
        assertEquals(1f, q, 0.0001f)
    }

    @Test
    fun `dormir de mas no suma`() {
        // El objetivo es el objetivo: doce horas no valen más que las siete y media.
        val justo = SleepSchedule.quality(minutesAsleep = 450)
        val pasado = SleepSchedule.quality(minutesAsleep = 720)
        assertEquals(justo, pasado, 0.0001f)
    }

    @Test
    fun `dormir poco baja la calidad aunque llegues puntual`() {
        val corto = SleepSchedule.quality(minutesAsleep = 225, minutesLateToBed = 0)
        assertTrue("debería quedarse a media tabla, fue $corto", corto in 0.5f..0.7f)
    }

    @Test
    fun `trasnochar penaliza poco a poco, no de golpe`() {
        // Media hora tarde no puede borrar una noche entera de sueño.
        val puntual = SleepSchedule.quality(minutesAsleep = 450, minutesLateToBed = 0)
        val algoTarde = SleepSchedule.quality(minutesAsleep = 450, minutesLateToBed = 30)
        val muyTarde = SleepSchedule.quality(minutesAsleep = 450, minutesLateToBed = 180)

        assertTrue(algoTarde < puntual)
        assertTrue(algoTarde > 0.85f)
        assertTrue(muyTarde < algoTarde)
    }

    @Test
    fun `la frecuencia se dobla como mucho`() {
        assertEquals(1.0, SleepSchedule.frequencyMultiplier(0f), 0.0001)
        assertEquals(2.0, SleepSchedule.frequencyMultiplier(1f), 0.0001)
        // Aunque llegara un valor fuera de rango, no se dispara.
        assertEquals(2.0, SleepSchedule.frequencyMultiplier(5f), 0.0001)
    }

    @Test
    fun `haber dormido bien sube la probabilidad de que salga alguno`() {
        val base = SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = 15)
        val descansado = base.copy(sleepQuality = 1f)
        assertTrue(SpawnChance.of(descansado) > SpawnChance.of(base))
    }

    @Test
    fun `pero de madrugada sigue cortado pese a la calidad`() {
        // El premio se cobra despierto; si no, la cola se llenaría igual durante la noche.
        val durmiendo = SpawnConditions(
            pokedexCount = 5,
            minutesSinceLastSpawn = 240,
            isBedtime = true,
            sleepQuality = 1f,
        )
        assertEquals(SpawnChance.BEDTIME_MAX_CHANCE, SpawnChance.of(durmiendo), 0.0001)
    }

    @Test
    fun `haber dormido bien mejora lo que sale, no solo cuanto`() {
        val descansado = SpawnConditions(minutesScreenOff = 120, pokedexCount = 10, sleepQuality = 1f)
        val hechoPolvo = descansado.copy(sleepQuality = 0f)
        val mewtwo = PokemonRegistry[150]!!

        assertTrue(
            SpawnTable.weightOf(mewtwo, descansado) > SpawnTable.weightOf(mewtwo, hechoPolvo) * 3
        )
    }

    @Test
    fun `sin el reposo largo, dormir bien no saca legendarios`() {
        // El descanso de la noche no sustituye a dejar el teléfono quieto ahora.
        val pocoReposo = SpawnConditions(minutesScreenOff = 10, pokedexCount = 10, sleepQuality = 1f)
        assertEquals(0f, SpawnTable.weightOf(PokemonRegistry[150]!!, pocoReposo), 0.0001f)
    }
}
