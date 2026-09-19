package dev.glyphrotator.app.pokemon.spawn

import dev.glyphrotator.app.pokemon.PokemonRegistry
import dev.glyphrotator.app.pokemon.PokemonType
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpawnChanceTest {

    @Test
    fun `sin nada en la pokedex el primero sale pronto`() {
        // Esperar una hora para ver si el juego existe siquiera sería demasiado.
        val chance = SpawnChance.of(SpawnConditions(pokedexCount = 0))
        assertEquals(SpawnChance.FIRST_CATCH_CHANCE, chance, 0.0001)
    }

    @Test
    fun `la probabilidad sube con los minutos en reposo`() {
        fun tras(minutos: Int) =
            SpawnChance.of(SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = minutos))

        assertEquals(0.0, tras(0), 0.0001)
        assertEquals(0.0, tras(14), 0.0001)
        assertEquals(0.04, tras(15), 0.0001)
        assertEquals(0.08, tras(30), 0.0001)
        assertEquals(0.20, tras(45), 0.0001)
        assertEquals(1.00, tras(60), 0.0001)
        assertEquals(1.00, tras(600), 0.0001)
    }

    @Test
    fun `de madrugada se corta casi del todo`() {
        // Ocho horas de sueño con la probabilidad normal llenarían la cola de capturas y al
        // despertar no quedaría nada por hacer.
        val durmiendo = SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = 240, isBedtime = true)
        assertEquals(SpawnChance.BEDTIME_MAX_CHANCE, SpawnChance.of(durmiendo), 0.0001)
    }

    @Test
    fun `con la bateria en las ultimas no aparece nada`() {
        // La Matrix ni se enciende, así que la aparición se desperdiciaría.
        val sinBateria = SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = 120, batteryPercent = 5)
        assertEquals(0.0, SpawnChance.of(sinBateria), 0.0001)
    }

    @Test
    fun `pero cargando si aparece aunque quede poca`() {
        val cargando = SpawnConditions(
            pokedexCount = 5,
            minutesSinceLastSpawn = 120,
            batteryPercent = 5,
            isCharging = true,
        )
        assertTrue(SpawnChance.of(cargando) > 0.0)
    }

    // -------------------------------------------------------------------------------------
    // Modo demo. El fallo: los multiplicadores se aplicaban por fuera, después del tope de
    // madrugada, así que de noche —que es cuando se prueba el juego— el demo no hacía nada.
    // -------------------------------------------------------------------------------------

    @Test
    fun `el demo acelera las apariciones de dia`() {
        val despierto = SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = 15)

        val normal = SpawnChance.of(despierto)
        val demo = SpawnChance.of(despierto, spawnMultiplier = 12.0)

        assertEquals(0.04, normal, 0.0001)
        assertEquals(0.48, demo, 0.0001)
    }

    @Test
    fun `el demo tambien abre la franja de madrugada`() {
        // Esta es la que fallaba. Con el tope aplicado por fuera, de madrugada el demo daba
        // 0,5 % × 12 = 6 %: ni el 0,5 % normal ni el 10 % que promete la pantalla de Equilibrio.
        val durmiendo = SpawnConditions(
            pokedexCount = 5,
            minutesSinceLastSpawn = 240,
            isBedtime = true,
        )

        val normal = SpawnChance.of(durmiendo)
        val demo = SpawnChance.of(durmiendo, spawnMultiplier = 12.0, bedtimeMultiplier = 20.0)

        assertEquals(SpawnChance.BEDTIME_MAX_CHANCE, normal, 0.0001)
        assertEquals(SpawnChance.BEDTIME_MAX_CHANCE * 20.0, demo, 0.0001)
        assertTrue("de madrugada el demo tiene que notarse", demo > normal * 10)
    }

    @Test
    fun `el tope de madrugada sigue siendo un tope en demo`() {
        // Multiplicar por doce no puede saltarse el tope: si lo hiciera, una noche entera
        // volvería a llenar la cola de capturas, que es justo lo que el tope evita.
        val durmiendo = SpawnConditions(
            pokedexCount = 5,
            minutesSinceLastSpawn = 600,
            isBedtime = true,
        )
        val demo = SpawnChance.of(durmiendo, spawnMultiplier = 12.0, bedtimeMultiplier = 20.0)

        assertEquals(SpawnChance.BEDTIME_MAX_CHANCE * 20.0, demo, 0.0001)
        assertTrue("nunca puede pasar de 1", demo <= 1.0)
    }

    // -------------------------------------------------------------------------------------
    // Probabilidad compuesta. Hace falta porque con el móvil dormido la ronda la trae una
    // alarma del sistema que puede llegar tarde: si cada despertar valiera una sola tirada, el
    // ritmo del juego lo decidiría Android.
    // -------------------------------------------------------------------------------------

    @Test
    fun `un minuto compuesto es el minuto de siempre`() {
        assertEquals(0.04, SpawnChance.overMinutes(0.04, 1), 0.000001)
    }

    @Test
    fun `mas minutos es mas probable, pero nunca pasa de uno`() {
        val p = 0.04
        val uno = SpawnChance.overMinutes(p, 1)
        val diez = SpawnChance.overMinutes(p, 10)
        val mil = SpawnChance.overMinutes(p, 1000)

        assertTrue("diez minutos tienen que dar más que uno", diez > uno)
        assertTrue("y menos que la certeza", diez < 1.0)
        assertTrue("ni con mil minutos se pasa de 1", mil <= 1.0)
    }

    @Test
    fun `diez minutos al cuatro por ciento salen a un treinta y tres por ciento`() {
        // 1 - 0,96^10 = 0,3352. El número exacto importa: es lo que hace que diez minutos
        // entregados de golpe valgan lo mismo que diez minutos entregados de uno en uno.
        assertEquals(0.3352, SpawnChance.overMinutes(0.04, 10), 0.0001)
    }

    @Test
    fun `sin tiempo o sin probabilidad no pasa nada`() {
        assertEquals(0.0, SpawnChance.overMinutes(0.04, 0), 0.0)
        assertEquals(0.0, SpawnChance.overMinutes(0.04, -5), 0.0)
        assertEquals(0.0, SpawnChance.overMinutes(0.0, 60), 0.0)
    }

    @Test
    fun `lo que ya era seguro sigue siendo seguro`() {
        assertEquals(1.0, SpawnChance.overMinutes(1.0, 1), 0.0)
        assertEquals(1.0, SpawnChance.overMinutes(1.0, 30), 0.0)
    }

    @Test
    fun `sin multiplicadores el resultado no cambia`() {
        // El juego normal tiene que dar exactamente lo de siempre: los parámetros por defecto
        // valen 1 y no deben mover ni un decimal.
        for (minutos in listOf(0, 15, 30, 45, 60, 600)) {
            val c = SpawnConditions(pokedexCount = 5, minutesSinceLastSpawn = minutos)
            assertEquals(
                "a los $minutos minutos",
                SpawnChance.of(c),
                SpawnChance.of(c, spawnMultiplier = 1.0, bedtimeMultiplier = 1.0),
                0.0
            )
        }
    }
}

class SpawnTableTest {

    private fun conditions(block: SpawnConditions.() -> SpawnConditions) =
        SpawnConditions(minutesScreenOff = 90, pokedexCount = 10).block()

    private fun pesoDe(id: Int, conditions: SpawnConditions) =
        SpawnTable.weightOf(PokemonRegistry[id]!!, conditions)

    @Test
    fun `con lluvia los de agua pesan mucho mas`() {
        val base = conditions { copy(weather = Weather.CLEAR) }
        val lluvia = conditions { copy(weather = Weather.RAIN) }
        // Squirtle (7), tipo agua.
        assertTrue(pesoDe(7, lluvia) > pesoDe(7, base) * 3)
    }

    @Test
    fun `con tormenta mandan los electricos`() {
        val tormenta = conditions { copy(weather = Weather.THUNDERSTORM) }
        val despejado = conditions { copy(weather = Weather.CLEAR) }
        // Pikachu (25), eléctrico.
        assertTrue(pesoDe(25, tormenta) > pesoDe(25, despejado) * 4)
    }

    @Test
    fun `en Halloween los fantasmas se disparan`() {
        val normal = conditions { copy(hourOfDay = 22) }
        val halloween = conditions { copy(hourOfDay = 22, isHalloween = true) }
        // Gastly (92), fantasma.
        assertTrue(pesoDe(92, halloween) > pesoDe(92, normal) * 8)
    }

    @Test
    fun `de noche los normales se retiran`() {
        val dia = conditions { copy(hourOfDay = 12) }
        val noche = conditions { copy(hourOfDay = 23) }
        // Rattata (19), normal.
        assertTrue(pesoDe(19, noche) < pesoDe(19, dia))
    }

    @Test
    fun `los legendarios no salen sin un buen rato de reposo`() {
        // Es lo que da sentido al juego: el premio gordo exige dejar el teléfono quieto.
        val pocoRato = SpawnConditions(minutesScreenOff = 20, pokedexCount = 10)
        val muchoRato = SpawnConditions(minutesScreenOff = 120, pokedexCount = 10)
        assertEquals(0f, pesoDe(150, pocoRato), 0.0001f) // Mewtwo
        assertTrue(pesoDe(150, muchoRato) > 0f)
    }

    @Test
    fun `una forma base es mas comun que su forma final`() {
        val ahora = conditions { this }
        assertTrue(pesoDe(1, ahora) > pesoDe(3, ahora)) // Bulbasaur vs Venusaur
    }

    @Test
    fun `siempre sale alguno y es de los 151`() {
        val random = Random(1234)
        repeat(200) {
            val elegido = SpawnTable.pick(conditions { this }, random)
            assertNotNull(elegido)
            assertTrue(elegido!!.id in 1..151)
        }
    }

    @Test
    fun `sin candidatos no revienta`() {
        assertNull(SpawnTable.pick(conditions { this }, Random(1), candidates = emptyList()))
    }

    @Test
    fun `con lluvia salen mas de agua que sin ella`() {
        // La comprobación que de verdad importa: no que el peso suba, sino que se note al
        // repartir de verdad muchas veces.
        fun proporcionDeAgua(weather: Weather): Double {
            val random = Random(99)
            val cond = conditions { copy(weather = weather) }
            val agua = (1..600).count {
                val elegido = SpawnTable.pick(cond, random)!!
                elegido.type1 == PokemonType.WATER || elegido.type2 == PokemonType.WATER
            }
            return agua / 600.0
        }
        assertTrue(proporcionDeAgua(Weather.RAIN) > proporcionDeAgua(Weather.CLEAR) * 2)
    }
}

class SpawnConditionsTest {

    private fun calendarOf(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day)
        }

    @Test
    fun `halloween cubre la semana antes del 31 de octubre`() {
        assertTrue(SpawnConditions.isHalloween(calendarOf(2026, Calendar.OCTOBER, 31)))
        assertTrue(SpawnConditions.isHalloween(calendarOf(2026, Calendar.OCTOBER, 25)))
        assertTrue(!SpawnConditions.isHalloween(calendarOf(2026, Calendar.OCTOBER, 24)))
        assertTrue(!SpawnConditions.isHalloween(calendarOf(2026, Calendar.NOVEMBER, 1)))
    }

    @Test
    fun `navidad va del 20 de diciembre al 2 de enero`() {
        assertTrue(SpawnConditions.isChristmas(calendarOf(2026, Calendar.DECEMBER, 25)))
        assertTrue(SpawnConditions.isChristmas(calendarOf(2027, Calendar.JANUARY, 1)))
        assertTrue(!SpawnConditions.isChristmas(calendarOf(2027, Calendar.JANUARY, 3)))
        assertTrue(!SpawnConditions.isChristmas(calendarOf(2026, Calendar.DECEMBER, 19)))
    }

    @Test
    fun `las estaciones caen donde deben`() {
        assertEquals(Season.WINTER, SpawnConditions.seasonOf(calendarOf(2026, Calendar.JANUARY, 15)))
        assertEquals(Season.SPRING, SpawnConditions.seasonOf(calendarOf(2026, Calendar.APRIL, 15)))
        assertEquals(Season.SUMMER, SpawnConditions.seasonOf(calendarOf(2026, Calendar.JULY, 15)))
        assertEquals(Season.AUTUMN, SpawnConditions.seasonOf(calendarOf(2026, Calendar.OCTOBER, 15)))
    }

    @Test
    fun `la noche va de las ocho a las seis`() {
        assertTrue(SpawnConditions(hourOfDay = 20).isNight)
        assertTrue(SpawnConditions(hourOfDay = 3).isNight)
        assertTrue(!SpawnConditions(hourOfDay = 19).isNight)
        assertTrue(!SpawnConditions(hourOfDay = 6).isNight)
    }

    @Test
    fun `la luna llena aparece una vez por ciclo`() {
        // Se cuenta cuántas noches de luna llena hay en cuatro meses: deben ser unas cuatro,
        // una por ciclo sinódico.
        val start = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val nights = (0 until 120).count { SpawnConditions.isFullMoon(start + it * day) }
        assertTrue("salieron $nights noches de luna llena", nights in 3..6)
    }
}
