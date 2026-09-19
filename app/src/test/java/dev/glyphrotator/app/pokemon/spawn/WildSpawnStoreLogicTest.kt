package dev.glyphrotator.app.pokemon.spawn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La caducidad del salvaje, aparte de Android para poder probar el paso del tiempo sin
 * esperarlo de verdad.
 *
 * Es la regla que evita que uno que apareció el lunes siga ahí el viernes: si no caducara,
 * dejar el teléfono quieto dejaría de significar nada.
 */
class WildSpawnExpiryTest {

    private val lifetimeMs = 12L * 60 * 60 * 1000

    /** Réplica de la regla de [WildSpawnStore.expireIfStale], sin SharedPreferences. */
    private fun hasExpired(appearedAtMillis: Long, nowMillis: Long): Boolean {
        if (appearedAtMillis <= 0L) return false
        return nowMillis - appearedAtMillis >= lifetimeMs
    }

    @Test
    fun `recien aparecido no caduca`() {
        assertFalse(hasExpired(appearedAtMillis = 1_000L, nowMillis = 1_000L))
    }

    @Test
    fun `aguanta una noche entera de sueño`() {
        // Ocho horas: hay que encontrarlo al despertar, no que se haya ido.
        val ochoHoras = 8L * 60 * 60 * 1000
        assertFalse(hasExpired(appearedAtMillis = 0L + 1, nowMillis = ochoHoras))
    }

    @Test
    fun `pasadas doce horas se va`() {
        assertTrue(hasExpired(appearedAtMillis = 1L, nowMillis = 1L + lifetimeMs))
    }

    @Test
    fun `sin ninguno esperando no hay nada que caducar`() {
        assertFalse(hasExpired(appearedAtMillis = 0L, nowMillis = Long.MAX_VALUE))
    }
}

/**
 * El acumulador de minutos, que es lo que hace subir la probabilidad.
 */
class SpawnAccumulationTest {

    @Test
    fun `la probabilidad crece hasta ser segura en una hora`() {
        // Recorrido completo de una hora en reposo, minuto a minuto.
        var previous = 0.0
        for (minute in 0..60) {
            val chance = SpawnChance.of(
                SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = minute)
            )
            assertTrue("bajó en el minuto $minute", chance >= previous)
            previous = chance
        }
        assertEquals(1.0, previous, 0.0001)
    }

    @Test
    fun `tras aparecer uno el contador vuelve a empezar`() {
        // El reinicio es lo que reparte las apariciones en el tiempo en vez de amontonarlas.
        val justAfter = SpawnChance.of(SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = 0))
        assertEquals(0.0, justAfter, 0.0001)
    }
}
