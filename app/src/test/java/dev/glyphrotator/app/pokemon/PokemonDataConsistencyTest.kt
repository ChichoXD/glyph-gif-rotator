package dev.glyphrotator.app.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprobaciones de coherencia del catálogo. Las 151 entradas están escritas a mano, así
 * que estos tests protegen de erratas: ids duplicados, evoluciones que apuntan a especies
 * inexistentes, cadenas rotas, etc.
 */
class PokemonDataConsistencyTest {

    private val all = PokemonRegistry.all

    @Test
    fun `los ids van del 1 al 151 sin huecos ni repetidos`() {
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals((1..151).toList(), ids.sorted())
    }

    @Test
    fun `ninguna especie se queda sin nombre`() {
        all.forEach { assertTrue("Especie ${it.id} sin nombre", it.name.isNotBlank()) }
    }

    @Test
    fun `no hay nombres repetidos`() {
        val names = all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `todas las evoluciones apuntan a especies que existen`() {
        all.forEach { species ->
            species.evolvesTo.forEach { targetId ->
                assertNotNull(
                    "${species.name} evoluciona al id $targetId, que no existe",
                    PokemonRegistry[targetId]
                )
            }
        }
    }

    @Test
    fun `toda especie con requisito tiene quien evolucione hacia ella`() {
        all.filter { it.evolutionRequirement != null }.forEach { target ->
            val hasSource = all.any { it.evolvesTo.contains(target.id) }
            assertTrue("${target.name} tiene requisito pero nadie evoluciona a ella", hasSource)
        }
    }

    @Test
    fun `toda especie que es destino de evolucion declara su requisito`() {
        all.forEach { source ->
            source.evolvesTo.forEach { targetId ->
                val target = PokemonRegistry[targetId]!!
                assertNotNull(
                    "${target.name} es evolución de ${source.name} pero no dice cómo",
                    target.evolutionRequirement
                )
            }
        }
    }

    @Test
    fun `ninguna especie evoluciona a si misma`() {
        all.forEach { species ->
            assertTrue("${species.name} evoluciona a sí misma", !species.evolvesTo.contains(species.id))
        }
    }

    @Test
    fun `las evoluciones por nivel usan niveles alcanzables`() {
        all.mapNotNull { it.evolutionRequirement as? EvolutionRequirement.Level }.forEach {
            assertTrue("Nivel de evolución fuera de rango: ${it.level}", it.level in 2..LevelCalculator.MAX_LEVEL)
        }
    }

    @Test
    fun `las evoluciones por piedra usan piedras de verdad`() {
        all.mapNotNull { it.evolutionRequirement as? EvolutionRequirement.Stone }.forEach {
            assertTrue(
                "${it.item} no está en la lista de piedras de evolución",
                EvolutionResolver.evolutionStones.contains(it.item)
            )
        }
    }

    @Test
    fun `el segundo tipo nunca repite el primero`() {
        all.forEach { species ->
            if (species.type2 != null) {
                assertTrue("${species.name} tiene el mismo tipo dos veces", species.type1 != species.type2)
            }
        }
    }

    @Test
    fun `las cadenas de evolucion no tienen ciclos`() {
        all.forEach { start ->
            val visited = mutableSetOf(start.id)
            var frontier = start.evolvesTo.toList()
            while (frontier.isNotEmpty()) {
                frontier.forEach { id ->
                    assertTrue("Ciclo de evolución detectado en ${start.name}", visited.add(id))
                }
                frontier = frontier.flatMap { PokemonRegistry[it]?.evolvesTo.orEmpty() }
            }
        }
    }

    @Test
    fun `las familias conocidas estan completas`() {
        // Tres etapas: Bulbasaur -> Ivysaur -> Venusaur
        assertEquals(listOf(2), PokemonRegistry[1]!!.evolvesTo)
        assertEquals(listOf(3), PokemonRegistry[2]!!.evolvesTo)
        // Eevee se abre en tres ramas
        assertEquals(3, PokemonRegistry[133]!!.evolvesTo.size)
        // Los legendarios no evolucionan
        listOf(144, 145, 146, 150, 151).forEach {
            assertTrue("${PokemonRegistry[it]!!.name} no debería evolucionar", PokemonRegistry[it]!!.evolvesTo.isEmpty())
        }
    }
}
