package dev.glyphrotator.app.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MATRIX = 25

class PokeballCatchAnimationTest {

    private val steps = PokeballCatchAnimation.buildShrinkSteps(MATRIX)

    @Test
    fun `el circulo se cierra hasta el tamaño de la pokeball`() {
        // No se cierra a un punto: acaba en el diámetro de la bola que entra después, para
        // que el círculo se convierta en ella en vez de desaparecer y reaparecer.
        assertEquals(MATRIX - 1, steps.first().diameter)
        assertEquals(11, steps.last().diameter)
    }

    @Test
    fun `el diametro solo decrece, nunca rebota`() {
        steps.zipWithNext().forEach { (previous, next) ->
            assertTrue(
                "El diámetro subió de ${previous.diameter} a ${next.diameter}",
                next.diameter < previous.diameter
            )
        }
    }

    @Test
    fun `ningun frame es instantaneo`() {
        steps.forEach { assertTrue("Duración inválida: ${it.durationMs}", it.durationMs > 0) }
    }

    /**
     * Curva ease-out: el círculo colapsa de golpe y frena al final, para que el cierre se
     * "asiente" en vez de cortarse en seco.
     */
    @Test
    fun `el cierre arranca rapido y frena al final`() {
        val firstHalf = steps.take(steps.size / 2).sumOf { it.durationMs }
        val secondHalf = steps.drop(steps.size / 2).sumOf { it.durationMs }
        assertTrue(
            "La segunda mitad debería durar más que la primera ($firstHalf vs $secondHalf)",
            secondHalf > firstHalf
        )
    }

    @Test
    fun `el cierre completo dura un instante, no segundos`() {
        val total = steps.sumOf { it.durationMs }
        assertTrue("El cierre duró ${total}ms, demasiado", total in 100..600)
    }

    @Test
    fun `una matriz diminuta no rompe nada`() {
        assertTrue(PokeballCatchAnimation.buildShrinkSteps(5).isEmpty())
        assertTrue(PokeballCatchAnimation.buildShrinkSteps(0).isEmpty())
    }

    @Test
    fun `hay suficientes pasos para que se vea fluido`() {
        // Uno por píxel de diámetro recortado, del 24 hasta el tamaño de la pokeball.
        assertEquals(MATRIX - 11, steps.size)
        assertTrue("Muy pocos pasos para una animación fluida", steps.size >= 12)
    }
}
