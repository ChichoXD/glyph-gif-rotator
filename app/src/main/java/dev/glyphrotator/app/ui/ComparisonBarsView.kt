package dev.glyphrotator.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import dev.glyphrotator.app.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Los desbloqueos de esta semana contra los de la semana pasada, en columnas de LEDs.
 *
 * La comparación es el dato. Un número suelto —"hoy 47 desbloqueos"— no dice nada: ¿es mucho? La
 * respuesta solo aparece al lado del mismo día de la semana anterior. Por eso la serie vieja se
 * pinta **detrás y en apagado** en vez de al lado: puesta al lado, el ojo tiene que emparejar dos
 * columnas y contar; puesta detrás, la diferencia se ve como lo que sobresale.
 *
 * Segmentos y no barra continua por lo mismo que en [SegmentBarView]: es el idioma de la Matrix de
 * atrás. Una barra lisa es de cualquier app.
 *
 * **Hoy en rojo**, el único de la vista. La cifra va encima de cada columna porque una tira de
 * LEDs se lee bien para comparar pero mal para saber el número exacto, y aquí hacen falta las dos
 * cosas.
 *
 * Medidas en dp: el fallo 24 otra vez.
 */
class ComparisonBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var current: List<Float> = emptyList()
    private var previous: List<Float> = emptyList()
    private var labels: List<String> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val viewHeightPx = dp(HEIGHT_DP)
    private val valueSpacePx = dp(VALUE_SPACE_DP)
    private val labelSpacePx = dp(LABEL_SPACE_DP)
    private val valueTextPx = dp(VALUE_TEXT_DP)
    private val labelTextPx = dp(LABEL_TEXT_DP)
    private val labelBaselinePx = dp(LABEL_BASELINE_DP)
    private val segmentGapPx = dp(SEGMENT_GAP_DP)

    private val litColor = ContextCompat.getColor(context, R.color.glyph_led_on)
    private val dimColor = ContextCompat.getColor(context, R.color.glyph_led_off)
    private val accentColor = ContextCompat.getColor(context, R.color.glyph_red)
    private val greyColor = ContextCompat.getColor(context, R.color.glyph_grey)

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dimColor }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        color = greyColor
    }

    private val rect = RectF()

    /**
     * [current] y [previous] del más antiguo al de hoy, en la misma unidad y del mismo largo.
     *
     * Si [previous] viene vacía se pinta solo la serie de esta semana: la primera semana de uso no
     * tiene con qué compararse, y dibujar una fila de ceros detrás diría "la semana pasada no
     * tocaste el móvil", que es mentira — es que no había móvil que medir.
     */
    fun setData(
        current: List<Float>,
        previous: List<Float> = emptyList(),
        labels: List<String> = emptyList(),
    ) {
        this.current = current
        this.previous = previous
        this.labels = labels
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), viewHeightPx.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (current.isEmpty()) return

        val columnTop = valueSpacePx
        val columnBottom = height - labelSpacePx
        val columnHeight = columnBottom - columnTop
        if (columnHeight <= 0f) return

        // La escala la marcan las dos series juntas. Si cada una se escalara a su propio pico, una
        // semana de 20 y otra de 200 saldrían igual de altas y la comparación diría lo contrario
        // de lo que pasó.
        val top = max(
            current.maxOrNull() ?: 0f,
            previous.maxOrNull() ?: 0f
        ).coerceAtLeast(1f)

        val slot = width.toFloat() / current.size
        val ghostWidth = slot * GHOST_RATIO
        val frontWidth = slot * FRONT_RATIO

        val segmentHeight = (columnHeight - segmentGapPx * (SEGMENTS - 1)) / SEGMENTS
        if (segmentHeight <= 0f) return

        valuePaint.textSize = valueTextPx
        labelPaint.textSize = labelTextPx

        for (index in current.indices) {
            val centerX = slot * (index + 0.5f)
            val isToday = index == current.lastIndex

            // Detrás: la semana pasada, apagada y más ancha.
            previous.getOrNull(index)?.let { old ->
                drawColumn(
                    canvas = canvas,
                    centerX = centerX,
                    columnWidth = ghostWidth,
                    columnBottom = columnBottom,
                    segmentHeight = segmentHeight,
                    lit = litSegments(old, top),
                    paint = ghostPaint,
                    drawUnlit = false,
                )
            }

            // Delante: esta semana. Los apagados se pintan también, que es lo que hace que se lea
            // como una tira de LEDs y no como una barra recortada.
            segmentPaint.color = if (isToday) accentColor else litColor
            drawColumn(
                canvas = canvas,
                centerX = centerX,
                columnWidth = frontWidth,
                columnBottom = columnBottom,
                segmentHeight = segmentHeight,
                lit = litSegments(current[index], top),
                paint = segmentPaint,
                drawUnlit = true,
            )

            valuePaint.color = if (isToday) accentColor else greyColor
            canvas.drawText(
                current[index].toInt().toString(),
                centerX,
                valueSpacePx - dp(VALUE_BASELINE_DP),
                valuePaint
            )

            labels.getOrNull(index)?.let {
                labelPaint.color = if (isToday) accentColor else greyColor
                canvas.drawText(it, centerX, height - labelBaselinePx, labelPaint)
            }
        }
    }

    private fun drawColumn(
        canvas: Canvas,
        centerX: Float,
        columnWidth: Float,
        columnBottom: Float,
        segmentHeight: Float,
        lit: Int,
        paint: Paint,
        drawUnlit: Boolean,
    ) {
        val left = centerX - columnWidth / 2
        val right = centerX + columnWidth / 2
        val radius = columnWidth / 2

        for (i in 0 until SEGMENTS) {
            val isLit = i < lit
            if (!isLit && !drawUnlit) continue

            val bottom = columnBottom - i * (segmentHeight + segmentGapPx)
            rect.set(left, bottom - segmentHeight, right, bottom)
            canvas.drawRoundRect(
                rect,
                radius,
                radius,
                if (isLit) paint else ghostPaint
            )
        }
    }

    /**
     * Cuántos segmentos se encienden. Redondeo hacia arriba salvo el cero: igual que en
     * [SegmentBarView], "casi nada" y "nada" no son lo mismo y aquí un cero es un dato.
     */
    private fun litSegments(value: Float, top: Float): Int {
        if (value <= 0f) return 0
        return min(SEGMENTS, max(1, ceil(value / top * SEGMENTS).toInt()))
    }

    private companion object {
        const val HEIGHT_DP = 148f

        /** Hueco de arriba para la cifra, y de abajo para la inicial del día. */
        const val VALUE_SPACE_DP = 20f
        const val LABEL_SPACE_DP = 16f

        const val VALUE_TEXT_DP = 10f
        const val VALUE_BASELINE_DP = 6f
        const val LABEL_TEXT_DP = 9f
        const val LABEL_BASELINE_DP = 3f

        const val SEGMENT_GAP_DP = 2.5f

        /** Ocho: los suficientes para comparar de un vistazo sin que cada uno sea una raya. */
        const val SEGMENTS = 8

        /** La sombra de la semana pasada, más ancha, para que asome por los dos lados. */
        const val GHOST_RATIO = 0.66f
        const val FRONT_RATIO = 0.38f
    }
}
