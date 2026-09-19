package dev.glyphrotator.app.glyph

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El reparto de las tres fases del huevo, que es lo que se puede comprobar sin Android
 * (dibujar necesita Bitmap, que aquí no existe).
 */
class EggStageTest {

    @Test
    fun `recien puesto durante el primer tramo`() {
        assertEquals(EggAnimation.Stage.FRESH, EggAnimation.Stage.of(0f))
        assertEquals(EggAnimation.Stage.FRESH, EggAnimation.Stage.of(0.39f))
    }

    @Test
    fun `a media eclosion en el tramo central`() {
        assertEquals(EggAnimation.Stage.WARM, EggAnimation.Stage.of(0.4f))
        assertEquals(EggAnimation.Stage.WARM, EggAnimation.Stage.of(0.79f))
    }

    @Test
    fun `a punto en el ultimo tramo`() {
        assertEquals(EggAnimation.Stage.HATCHING, EggAnimation.Stage.of(0.8f))
        assertEquals(EggAnimation.Stage.HATCHING, EggAnimation.Stage.of(1f))
    }

    @Test
    fun `las fases van en orden segun avanza`() {
        // Nunca debe retroceder: el estado tiene que contar el progreso de forma fiable.
        var previous = EggAnimation.Stage.FRESH
        var step = 0f
        while (step <= 1f) {
            val stage = EggAnimation.Stage.of(step)
            assert(stage.ordinal >= previous.ordinal) { "retrocedió en $step" }
            previous = stage
            step += 0.01f
        }
        assertEquals(EggAnimation.Stage.HATCHING, previous)
    }

    @Test
    fun `un progreso fuera de rango no rompe nada`() {
        assertEquals(EggAnimation.Stage.FRESH, EggAnimation.Stage.of(-1f))
        assertEquals(EggAnimation.Stage.HATCHING, EggAnimation.Stage.of(5f))
    }
}
