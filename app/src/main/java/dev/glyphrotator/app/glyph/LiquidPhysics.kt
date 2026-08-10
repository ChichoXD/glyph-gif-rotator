package dev.glyphrotator.app.glyph

import kotlin.math.abs
import kotlin.math.sin

/**
 * Física de inclinación del líquido (una única fuente de verdad), usada tanto por
 * [LiquidBatteryPlayer] (app principal) como por `ClockBatteryToyService` (botón
 * físico), para que no se desincronicen entre sí como pasó cuando cada uno tenía su
 * propia copia de esta lógica.
 */
object LiquidPhysics {

    fun step(heights: FloatArray, pct: Int, tiltX: Float, tiltY: Float, size: Int) {
        // Centro real de píxeles: con 25 columnas (0..24) el centro es 12, no 12.5. Usar
        // size/2f recortaba una columna por la izquierda pero no por la derecha.
        val center = (size - 1) / 2f
        val maxOffset = center
        val topMargin = size * 0.12f
        val baseLevel = size - topMargin - (pct / 100f) * (size - topMargin)
        val time = System.currentTimeMillis()

        for (x in 0 until size) {
            if (abs(x - center) > maxOffset) {
                heights[x] = size.toFloat()
                continue
            }

            var tiltEffect = 0f
            if (tiltY < -5f) {
                tiltEffect += (tiltY + 5f) * 2f
            } else if (tiltY > 5f) {
                tiltEffect -= (tiltY - 5f) * 1f
            }
            tiltEffect += (x - center) * (-tiltX) * 0.5f

            val waveOffset = sin(x * 0.4f + time * 0.004f) * 0.8f
            val targetHeight = baseLevel + tiltEffect + waveOffset

            heights[x] += (targetHeight - heights[x]) * SMOOTHING
            heights[x] = heights[x].coerceIn(0f, size.toFloat())
        }
    }

    private const val SMOOTHING = 0.12f
}
