package dev.glyphrotator.app.pokemon

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El modo demo acelera el juego para poder enseñarlo sin esperar tres horas a que salga un huevo.
 *
 * La regla que hay que proteger está escrita en MEMORIA.md y no la guardaba nada: **con el demo
 * apagado, los números tienen que ser exactamente los de la vida real**. [DemoTuning] es estado
 * global mutable —a propósito, para que las reglas del juego sigan siendo objetos puros sin
 * `Context`—, y ese tipo de estado es justo el que se queda pegado sin que nadie se entere.
 *
 * El fallo que esto coge no es que el demo no funcione: es el contrario, que un valor de demo se
 * quede aplicado en una partida normal. Eso no se ve —el juego sigue funcionando, solo que
 * regalando experiencia— y no lo detecta ningún otro test, porque el resto comprueban las reglas
 * puras con sus constantes y nunca miran por qué camino llega el número.
 */
class DemoTuningTest {

    /**
     * Estado global: si un test lo deja encendido, contamina a los que vengan detrás y encima el
     * orden de ejecución de JUnit no está garantizado. Se apaga siempre.
     */
    @After
    fun apagarDemo() {
        DemoTuning.setForTesting(false)
    }

    @Test
    fun `con el demo apagado, los valores son los de la vida real`() {
        DemoTuning.setForTesting(false)

        assertEquals("el multiplicador de apariciones", 1.0, DemoTuning.spawnMultiplier, 0.0)
        assertEquals("el tope de madrugada", 1.0, DemoTuning.bedtimeMultiplier, 0.0)
        assertEquals(
            "la experiencia por minuto",
            TrainingRules.EXP_PER_MINUTE,
            DemoTuning.expPerMinute
        )
        assertEquals(
            "las capturas por huevo",
            EggRules.CAPTURES_PER_EGG,
            DemoTuning.capturesPerEgg
        )
        assertEquals(
            "los minutos de incubación",
            EggRules.INCUBATION_MINUTES,
            DemoTuning.incubationMinutes
        )
    }

    /**
     * Y al revés: que encenderlo haga algo. Un demo que por un cero mal puesto devolviera los
     * mismos números sería peor que no tenerlo — parecería que el juego va lento de verdad.
     *
     * Se comprueba el sentido, no el valor exacto: los números concretos se pueden reequilibrar,
     * pero "el demo acelera" no es negociable.
     */
    @Test
    fun `con el demo encendido, todo va mas rapido`() {
        DemoTuning.setForTesting(true)

        assertTrue(
            "las apariciones deberían subir, dio ${DemoTuning.spawnMultiplier}",
            DemoTuning.spawnMultiplier > 1.0
        )
        assertTrue(
            "la madrugada debería subir, dio ${DemoTuning.bedtimeMultiplier}",
            DemoTuning.bedtimeMultiplier > 1.0
        )
        assertTrue(
            "la experiencia debería subir, dio ${DemoTuning.expPerMinute}",
            DemoTuning.expPerMinute > TrainingRules.EXP_PER_MINUTE
        )
        assertTrue(
            "deberían hacer falta menos capturas, dio ${DemoTuning.capturesPerEgg}",
            DemoTuning.capturesPerEgg < EggRules.CAPTURES_PER_EGG
        )
        assertTrue(
            "el huevo debería tardar menos, dio ${DemoTuning.incubationMinutes}",
            DemoTuning.incubationMinutes < EggRules.INCUBATION_MINUTES
        )
    }

    /**
     * La vuelta atrás. Es el caso real: enseñas el juego con el demo, lo apagas, y a partir de ahí
     * la partida tiene que valer. Si algo se quedara pegado, se quedaría para siempre.
     */
    @Test
    fun `apagar el demo devuelve los valores de la vida real`() {
        DemoTuning.setForTesting(true)
        DemoTuning.setForTesting(false)

        assertEquals(1.0, DemoTuning.spawnMultiplier, 0.0)
        assertEquals(1.0, DemoTuning.bedtimeMultiplier, 0.0)
        assertEquals(TrainingRules.EXP_PER_MINUTE, DemoTuning.expPerMinute)
        assertEquals(EggRules.CAPTURES_PER_EGG, DemoTuning.capturesPerEgg)
        assertEquals(EggRules.INCUBATION_MINUTES, DemoTuning.incubationMinutes)
    }

    /**
     * La pantalla de Equilibrio enseña los dos ritmos enfrentados y saca los números de aquí. Si
     * la diferencia fuera cosmética, esa pantalla estaría mintiendo: por eso se pide que el salto
     * sea grande de verdad, no un 10 %.
     */
    @Test
    fun `la diferencia entre los dos ritmos es grande, no cosmetica`() {
        DemoTuning.setForTesting(false)
        val expReal = DemoTuning.expPerMinute
        val incubacionReal = DemoTuning.incubationMinutes

        DemoTuning.setForTesting(true)

        assertTrue(
            "la experiencia del demo debería multiplicar por varias veces, dio " +
                "${DemoTuning.expPerMinute} contra $expReal",
            DemoTuning.expPerMinute >= expReal * 5
        )
        assertTrue(
            "el huevo del demo debería tardar una fracción, dio " +
                "${DemoTuning.incubationMinutes} contra $incubacionReal",
            DemoTuning.incubationMinutes <= incubacionReal / 5
        )
    }
}
