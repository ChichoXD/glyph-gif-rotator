package dev.glyphrotator.app.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Dibuja solo el líquido, a partir de una altura por columna ya calculada (ver
 * [LiquidBatteryPlayer], que lleva la física de inclinación). El porcentaje NO se dibuja
 * aquí: se pinta aparte con el renderizador de texto nativo del SDK
 * (`GlyphMatrixController.showLiquidBattery`), porque intentar dibujar texto con Canvas
 * a 25px y mezclarlo en el mismo bitmap que el líquido salía como ruido de píxeles.
 */
object LiquidBatteryFrameRenderer {

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val contrastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    fun renderLiquid(liquidHeights: FloatArray, batteryPct: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val pct = batteryPct.coerceIn(0, 100)
        // Centro real de píxeles (12 en una matriz de 25), no size/2f: si no, el círculo
        // queda desplazado medio píxel y se come una columna de un solo lado.
        val center = (size - 1) / 2f
        val radius = size / 2f

        liquidPaint.color = when {
            pct <= 15 -> Color.rgb(220, 90, 90)
            pct <= 40 -> Color.rgb(220, 180, 90)
            else -> Color.rgb(80, 190, 230)
        }

        for (x in 0 until size) {
            val liquidTop = liquidHeights[x]
            val dx = x - center
            for (y in 0 until size) {
                if (y < liquidTop) continue
                val dy = y - center
                if (dx * dx + dy * dy <= radius * radius) {
                    bitmap.setPixel(x, y, liquidPaint.color)
                }
            }
        }

        return bitmap
    }

    /** Caja opaca del tamaño del texto "XX%", para que no se mezcle con el líquido de detrás. */
    fun renderContrastBox(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val textWidth = size * 0.4f
        val textHeight = size * 0.26f
        canvas.drawRoundRect(
            RectF(center - textWidth / 2, center - textHeight / 2, center + textWidth / 2, center + textHeight / 2),
            size * 0.05f,
            size * 0.05f,
            contrastPaint
        )
        return bitmap
    }
}
