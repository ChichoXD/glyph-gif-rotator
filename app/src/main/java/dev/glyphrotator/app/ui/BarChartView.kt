package dev.glyphrotator.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import dev.glyphrotator.app.R

/**
 * Barras verticales para lo que se mide en cantidad, no en "sí o no".
 *
 * El calendario de puntos vale para "¿lo cumpliste?", pero no para "¿cuánto?": dormir cinco horas
 * y dormir ocho serían el mismo punto. Aquí la altura **es** el dato, que es lo que deja ver de
 * un vistazo que llevas tres días durmiendo poco.
 *
 * Con línea de objetivo, porque una barra sola no dice si está bien o mal: siete horas es mucho o
 * poco según lo que te hayas propuesto.
 *
 * **Las medidas van en dp y se convierten al construir.** Antes eran píxeles crudos y se veía bien
 * por casualidad: en el Phone (3), con densidad 3, los 320 px de alto salían 106 dp. En una
 * pantalla de otra densidad la gráfica salía del tamaño equivocado, y como se dibuja a mano no hay
 * forma de que Android lo corrija solo. Es el mismo fallo que se arregló en las pantallas de
 * código el 20 de agosto; esta vista se quedó fuera de aquella pasada.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var values: List<Float> = emptyList()
    private var target: Float = 0f
    private var labels: List<String> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val chartHeightPx = dp(HEIGHT_DP)
    private val labelSpacePx = dp(LABEL_SPACE_DP)
    private val labelTextPx = dp(LABEL_TEXT_DP)
    private val labelBaselinePx = dp(LABEL_BASELINE_DP)
    private val barRadiusPx = dp(BAR_RADIUS_DP)
    private val stubPx = dp(STUB_DP)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(TARGET_STROKE_DP)
        pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(dp(DASH_DP), dp(DASH_DP)),
            0f
        )
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }

    // Blanco cálido y gris apagado: el mismo par que un LED encendido y uno apagado en la Matrix.
    // Que coincidan no es casualidad — es lo que hace que la gráfica se lea como parte del mismo
    // aparato y no como un widget de otra app.
    private val litColor = context.getColor(R.color.glyph_led_on)
    private val dimColor = context.getColor(R.color.glyph_led_off)
    private val accentColor = context.getColor(R.color.glyph_red)
    private val greyColor = context.getColor(R.color.glyph_grey)

    /**
     * [values] y [target] en la misma unidad. Un valor a 0 se dibuja igual, como muñón: un hueco
     * en blanco se leería como "no hay dato", y aquí un cero es un dato.
     */
    fun setData(values: List<Float>, target: Float = 0f, labels: List<String> = emptyList()) {
        this.values = values
        this.target = target
        this.labels = labels
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), chartHeightPx.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return

        val labelSpace = if (labels.isEmpty()) 0f else labelSpacePx
        val chartHeight = height - labelSpace
        // La escala la marca el mayor entre el pico y el objetivo: si solo mirara los valores,
        // una semana durmiendo poco se vería igual de llena que una semana perfecta.
        val top = maxOf(values.max(), target, 1f)

        val slot = width.toFloat() / values.size
        val barWidth = slot * BAR_RATIO
        labelPaint.textSize = labelTextPx

        for ((index, value) in values.withIndex()) {
            val fraction = (value / top).coerceIn(0f, 1f)
            val barHeight = (chartHeight * fraction).coerceAtLeast(stubPx)
            val centerX = slot * (index + 0.5f)

            barPaint.color = when {
                index == values.lastIndex -> accentColor   // hoy
                target > 0f && value >= target -> litColor // objetivo cumplido
                else -> dimColor
            }

            canvas.drawRoundRect(
                RectF(
                    centerX - barWidth / 2,
                    chartHeight - barHeight,
                    centerX + barWidth / 2,
                    chartHeight
                ),
                barRadiusPx,
                barRadiusPx,
                barPaint
            )

            labels.getOrNull(index)?.let {
                labelPaint.color = greyColor
                canvas.drawText(it, centerX, height - labelBaselinePx, labelPaint)
            }
        }

        if (target > 0f) {
            val y = chartHeight * (1f - (target / top).coerceIn(0f, 1f))
            targetPaint.color = greyColor
            canvas.drawLine(0f, y, width.toFloat(), y, targetPaint)
        }
    }

    private companion object {
        // En dp. Los valores están elegidos para que en el Phone (3) —densidad 3— salga
        // exactamente lo mismo de antes, que es como estaba calibrado a ojo.
        const val HEIGHT_DP = 106.67f
        const val LABEL_SPACE_DP = 14.67f
        const val LABEL_TEXT_DP = 8.67f
        const val LABEL_BASELINE_DP = 2.67f
        const val BAR_RADIUS_DP = 2f
        const val TARGET_STROKE_DP = 0.67f
        const val DASH_DP = 2.67f
        const val BAR_RATIO = 0.62f

        /** Alto mínimo de una barra a cero, para que se vea que ese día existe. */
        const val STUB_DP = 1f
    }
}
