package dev.glyphrotator.app.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonRegistryTest {

    @Test
    fun `contiene las 151 especies de la primera generacion`() {
        assertEquals(151, PokemonRegistry.all.size)
        assertEquals("Bulbasaur", PokemonRegistry[1]?.name)
        assertEquals("Mew", PokemonRegistry[151]?.name)
    }

    @Test
    fun `los tipos dobles se guardan bien`() {
        val charizard = PokemonRegistry[6]!!
        assertEquals(PokemonType.FIRE, charizard.type1)
        assertEquals(PokemonType.FLYING, charizard.type2)

        val pikachu = PokemonRegistry[25]!!
        assertEquals(PokemonType.ELECTRIC, pikachu.type1)
        assertNull(pikachu.type2)
    }

    @Test
    fun `las cadenas de evolucion apuntan a la especie correcta`() {
        assertEquals(listOf(2), PokemonRegistry[1]?.evolvesTo)
        assertEquals(listOf(3), PokemonRegistry[2]?.evolvesTo)
        assertTrue(PokemonRegistry[3]?.evolvesTo.isNullOrEmpty())
    }

    @Test
    fun `eevee tiene tres evoluciones por piedra`() {
        val eevee = PokemonRegistry[133]!!
        assertEquals(listOf(134, 135, 136), eevee.evolvesTo)
    }

    @Test
    fun `se puede buscar por nombre sin distinguir mayusculas`() {
        assertEquals(25, PokemonRegistry.byName("pikachu")?.id)
        assertNull(PokemonRegistry.byName("Mewthree"))
    }
}

class LevelCalculatorTest {

    @Test
    fun `ganar experiencia insuficiente no sube de nivel`() {
        val result = LevelCalculator.applyExp(currentLevel = 5, currentExp = 0, gainedExp = 100)!!
        assertEquals(5, result.level)
        assertEquals(100, result.exp)
        assertFalse(result.leveledUp)
    }

    @Test
    fun `completar la barra sube exactamente un nivel`() {
        val result = LevelCalculator.applyExp(5, 0, LevelCalculator.EXP_PER_LEVEL)!!
        assertEquals(6, result.level)
        assertEquals(0, result.exp)
        assertTrue(result.leveledUp)
    }

    @Test
    fun `una sola tanda de experiencia puede subir varios niveles`() {
        val result = LevelCalculator.applyExp(1, 0, LevelCalculator.EXP_PER_LEVEL * 3 + 50)!!
        assertEquals(4, result.level)
        assertEquals(50, result.exp)
        assertTrue(result.leveledUp)
    }

    @Test
    fun `no se puede pasar del nivel maximo`() {
        val result = LevelCalculator.applyExp(99, 0, LevelCalculator.EXP_PER_LEVEL * 50)!!
        assertEquals(LevelCalculator.MAX_LEVEL, result.level)
        assertEquals(0, result.exp)
    }

    @Test
    fun `estando al maximo ya no pasa nada`() {
        assertNull(LevelCalculator.applyExp(LevelCalculator.MAX_LEVEL, 0, 5000))
    }

    @Test
    fun `sin experiencia ganada no hay cambios`() {
        assertNull(LevelCalculator.applyExp(10, 40, 0))
    }
}

class TrainingRulesTest {

    @Test
    fun `sin tiempo de pantalla apagada no se gana nada`() {
        assertEquals(0, TrainingRules.expForScreenOffMinutes(0))
    }

    @Test
    fun `antes del primer bonus solo cuenta la experiencia por minuto`() {
        assertEquals(10 * TrainingRules.EXP_PER_MINUTE, TrainingRules.expForScreenOffMinutes(10))
    }

    @Test
    fun `al llegar al intervalo se suma el bonus`() {
        val minutes = TrainingRules.BONUS_INTERVAL_MINUTES
        val expected = minutes * TrainingRules.EXP_PER_MINUTE + TrainingRules.BONUS_AMOUNT
        assertEquals(expected, TrainingRules.expForScreenOffMinutes(minutes))
    }

    @Test
    fun `los bonus se acumulan por cada intervalo completo`() {
        val minutes = TrainingRules.BONUS_INTERVAL_MINUTES * 3
        val expected = minutes * TrainingRules.EXP_PER_MINUTE + TrainingRules.BONUS_AMOUNT * 3
        assertEquals(expected, TrainingRules.expForScreenOffMinutes(minutes))
    }

    /**
     * Lo importante de cobrar a plazos: que dé exactamente igual que cobrarlo de una vez.
     *
     * Si no cuadrara, entrenar de noche rendiría distinto según cada cuánto pasara el servicio
     * a repartir, que es justo el tipo de fallo que nadie notaría hasta llevar semanas jugando.
     */
    @Test
    fun `cobrar minuto a minuto da lo mismo que cobrarlo todo junto`() {
        val total = 8 * 60
        var accumulated = 0
        for (minute in 1..total) accumulated += TrainingRules.expBetween(minute - 1, minute)

        assertEquals(TrainingRules.expForScreenOffMinutes(total), accumulated)
    }

    @Test
    fun `un tramo ya cobrado no vuelve a pagarse`() {
        assertEquals(0, TrainingRules.expBetween(60, 60))
        assertEquals(0, TrainingRules.expBetween(60, 30))
    }

    // ---- El techo de un solo tramo -----------------------------------------------------

    /**
     * Un fin de semana con el servicio muerto no puede valer una partida entera.
     *
     * El tramo se mide desde una marca en disco que sobrevive a que maten el servicio, pero
     * `ACTION_SCREEN_ON` solo llega con el servicio vivo: al revivir se cobraban las 72 horas de
     * golpe como si el teléfono hubiera estado en reposo. Eran 32 400 EXP, **108 niveles**, de 1 a
     * 100 de una vez. Es el fallo 26 por tercera vez, ahora en la economía del juego.
     */
    @Test
    fun `un tramo larguisimo no paga mas de un dia`() {
        val unDia = TrainingRules.expForScreenOffMinutes(TrainingRules.MAX_PERIOD_MINUTES)

        assertEquals(unDia, TrainingRules.expForScreenOffMinutes(3 * 24 * 60))
        assertEquals(unDia, TrainingRules.expForScreenOffMinutes(Int.MAX_VALUE))
        assertTrue(
            "un fin de semana no puede dar 108 niveles, dio ${unDia / 300}",
            unDia / LevelCalculator.EXP_PER_LEVEL < 40
        )
    }

    /** Y por debajo del techo no cambia nada: una noche normal sigue pagando lo de siempre. */
    @Test
    fun `el techo no toca una noche normal`() {
        val ocho = 8 * 60
        assertEquals(
            ocho * TrainingRules.EXP_PER_MINUTE +
                (ocho / TrainingRules.BONUS_INTERVAL_MINUTES) * TrainingRules.BONUS_AMOUNT,
            TrainingRules.expForScreenOffMinutes(ocho)
        )
    }

    /** Y a plazos sigue cuadrando con el total, también pasado el techo. */
    @Test
    fun `cobrar a plazos cuadra tambien pasado el techo`() {
        val total = TrainingRules.MAX_PERIOD_MINUTES + 500
        var accumulated = 0
        for (minute in 1..total) accumulated += TrainingRules.expBetween(minute - 1, minute)

        assertEquals(TrainingRules.expForScreenOffMinutes(total), accumulated)
    }
}

class EvolutionResolverTest {

    @Test
    fun `evoluciona por nivel al alcanzar el requisito`() {
        assertNull(EvolutionResolver.levelEvolutionTarget(speciesId = 1, level = 15))
        assertEquals(2, EvolutionResolver.levelEvolutionTarget(speciesId = 1, level = 16)?.id)
        assertEquals(2, EvolutionResolver.levelEvolutionTarget(speciesId = 1, level = 40)?.id)
    }

    @Test
    fun `una especie final no evoluciona por nivel`() {
        assertNull(EvolutionResolver.levelEvolutionTarget(speciesId = 3, level = 100))
    }

    @Test
    fun `la piedra correcta evoluciona y la incorrecta no`() {
        assertEquals(
            26,
            EvolutionResolver.stoneEvolutionTarget(25, PokemonItem.THUNDER_STONE)?.id
        )
        assertNull(EvolutionResolver.stoneEvolutionTarget(25, PokemonItem.FIRE_STONE))
    }

    @Test
    fun `cada piedra lleva a un eeveelution distinto`() {
        assertEquals(134, EvolutionResolver.stoneEvolutionTarget(133, PokemonItem.WATER_STONE)?.id)
        assertEquals(135, EvolutionResolver.stoneEvolutionTarget(133, PokemonItem.THUNDER_STONE)?.id)
        assertEquals(136, EvolutionResolver.stoneEvolutionTarget(133, PokemonItem.FIRE_STONE)?.id)
        assertNull(EvolutionResolver.stoneEvolutionTarget(133, PokemonItem.LEAF_STONE))
    }

    @Test
    fun `las evoluciones por intercambio se detectan`() {
        assertEquals(65, EvolutionResolver.tradeEvolutionTarget(64)?.id)
        assertEquals(94, EvolutionResolver.tradeEvolutionTarget(93)?.id)
        assertNull(EvolutionResolver.tradeEvolutionTarget(25))
    }

    @Test
    fun `el caramelo raro solo sirve por debajo del nivel maximo`() {
        assertTrue(EvolutionResolver.canUseItemOn(PokemonItem.RARE_CANDY, 25, level = 50))
        assertFalse(
            EvolutionResolver.canUseItemOn(PokemonItem.RARE_CANDY, 25, LevelCalculator.MAX_LEVEL)
        )
    }

    @Test
    fun `el cable union solo sirve en evoluciones por intercambio`() {
        assertTrue(EvolutionResolver.canUseItemOn(PokemonItem.LINKING_CORD, 64, 30))
        assertFalse(EvolutionResolver.canUseItemOn(PokemonItem.LINKING_CORD, 25, 30))
    }

    @Test
    fun `la cana solo activa pesca, nunca se usa sobre un pokemon`() {
        assertFalse(EvolutionResolver.canUseItemOn(PokemonItem.SUPER_ROD, 129, 20))
    }

    @Test
    fun `hay cinco piedras de evolucion`() {
        assertEquals(5, EvolutionResolver.evolutionStones.size)
        assertNotNull(PokemonRegistry[38])
    }
}
