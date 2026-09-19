package dev.glyphrotator.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import dev.glyphrotator.app.R

/**
 * La curva del tiempo de pantalla: una línea suave con relleno que se desvanece.
 *
 * Por qué curva y no barras, teniendo ya [BarChartView]: son dos preguntas distintas. Las barras
 * contestan "¿cuánto el martes?" —cada día es una cosa suelta que se compara con la de al lado—.
 * La curva contesta "¿voy a más o a menos?", que es lo que de verdad se quiere saber del tiempo de
 * pantalla. Un tramo que sube tres días seguidos se ve como una cuesta; en barras hay que ir
 * midiendo alturas a ojo.
 *
 * El trazo lleva **halo**: la misma línea pintada debajo más gruesa y casi transparente. No es
 * adorno — sobre negro puro una línea de 2 dp se lee dura y recortada, y el halo es lo que la hace
 * parecer un trazo encendido en vez de una pegatina. Se hace con dos pasadas y no con
 * `BlurMaskFilter` a propósito: el desenfoque obliga a renderizar la vista por software, y aquí
 * cuesta más de lo que aporta.
 *
 * **Hoy va en rojo**, y es el único rojo de la gráfica: línea vertical de puntos y el vértice
 * relleno. Ver DISENO.md — el rojo marca estado, y "el día en el que estás" es exactamente eso.
 *
 * Todas las medidas en dp, convertidas al construir. Es la lección del fallo 24: [BarChartView]
 * llevaba píxeles crudos y se veía bien por casualidad en la densidad del Phone (3).
 */
class SplineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val chartHeightPx = dp(HEIGHT_DP)
    private val labelSpacePx = dp(LABEL_SPACE_DP)
    private val labelTextPx = dp(LABEL_TEXT_DP)
    private val labelBaselinePx = dp(LABEL_BASELINE_DP)
    private val sidePaddingPx = dp(SIDE_PADDING_DP)
    private val topPaddingPx = dp(TOP_PADDING_DP)

    private val litColor = ContextCompat.getColor(context, R.color.glyph_led_on)
    private val accentColor = ContextCompat.getColor(context, R.color.glyph_red)
    private val greyColor = ContextCompat.getColor(context, R.color.glyph_grey)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(LINE_STROKE_DP)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = litColor
    }

    /** El halo: la misma curva, más gruesa y casi transparente, pintada debajo. */
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HALO_STROKE_DP)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = withAlpha(litColor, HALO_ALPHA)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val todayLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(TODAY_STROKE_DP)
        pathEffect = DashPathEffect(floatArrayOf(dp(DASH_DP), dp(DASH_DP)), 0f)
        color = accentColor
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accentColor
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        color = greyColor
    }

    private val curvePath = Path()
    private val fillPath = Path()

    /** El día tocado, o null si no hay ninguno. */
    private var selectedIndex: Int? = null

    /** Dónde quedó cada punto en el último dibujado, para saber cuál se ha tocado. */
    private var pointXs: FloatArray = FloatArray(0)
    private var pointYs: FloatArray = FloatArray(0)

    /**
     * Cómo se escribe el valor en la etiqueta.
     *
     * Lo pone quien usa la vista porque solo él sabe qué son estos números: aquí son minutos de
     * pantalla y se leen mejor como "2 h 15 min", pero la misma curva podría estar contando otra
     * cosa. Por defecto, el número tal cual.
     */
    var labelFormatter: (Float) -> String = { it.toInt().toString() }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.glyph_card)
    }
    private val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(BUBBLE_STROKE_DP)
        color = ContextCompat.getColor(context, R.color.glyph_border)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        color = litColor
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = litColor
    }
    private val bubbleRect = android.graphics.RectF()

    init {
        // Sin esto la vista no recibe el toque y TalkBack no la anuncia como algo que se pueda usar.
        isClickable = true
    }

    /**
     * [values] del más antiguo al de hoy, y [labels] con la inicial de cada día.
     *
     * El último valor es siempre hoy: es lo que decide dónde va la marca roja.
     */
    fun setData(values: List<Float>, labels: List<String> = emptyList()) {
        this.values = values
        this.labels = labels
        // Los datos nuevos pueden tener otro tamaño: un índice guardado apuntaría a otro día.
        selectedIndex = null
        invalidate()
    }

    /**
     * Un toque selecciona el día más cercano; tocarlo otra vez lo suelta.
     *
     * Se busca por cercanía **en horizontal y no por el punto exacto**: los vértices son de 3,5 dp
     * y nadie acierta ahí con el dedo. Cada día se queda con la franja vertical que le toca, que es
     * como se lee la gráfica de todas formas.
     */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action != android.view.MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event)
        }
        if (pointXs.isEmpty()) return super.onTouchEvent(event)

        var nearest = 0
        for (i in pointXs.indices) {
            if (kotlin.math.abs(pointXs[i] - event.x) < kotlin.math.abs(pointXs[nearest] - event.x)) {
                nearest = i
            }
        }
        selectedIndex = if (selectedIndex == nearest) null else nearest
        invalidate()
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), chartHeightPx.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        // Con un solo punto no hay curva que dibujar: una línea necesita de dónde a dónde ir.
        if (values.size < 2) return

        val labelSpace = if (labels.isEmpty()) 0f else labelSpacePx
        val plotBottom = height - labelSpace
        val plotTop = topPaddingPx
        val plotHeight = plotBottom - plotTop
        if (plotHeight <= 0f) return

        // La escala la marca el pico. El mínimo de 1 evita dividir por cero la semana que no se
        // ha usado el teléfono ni un minuto — que es un dato válido, no un hueco.
        val top = maxOf(values.max(), 1f)

        val usableWidth = width - sidePaddingPx * 2
        val step = usableWidth / (values.size - 1)

        val xs = FloatArray(values.size) { sidePaddingPx + step * it }
        val ys = FloatArray(values.size) { index ->
            plotBottom - plotHeight * (values[index] / top).coerceIn(0f, 1f)
        }
        pointXs = xs
        pointYs = ys

        buildSpline(xs, ys)

        // Relleno primero, para que la línea quede encima y no la coma el degradado.
        fillPath.set(curvePath)
        fillPath.lineTo(xs.last(), plotBottom)
        fillPath.lineTo(xs.first(), plotBottom)
        fillPath.close()
        fillPaint.shader = LinearGradient(
            0f, plotTop, 0f, plotBottom,
            withAlpha(litColor, FILL_TOP_ALPHA),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        canvas.drawPath(curvePath, haloPaint)
        canvas.drawPath(curvePath, linePaint)

        // Hoy: la vertical de puntos hasta la curva y el vértice relleno.
        val todayX = xs.last()
        val todayY = ys.last()
        canvas.drawLine(todayX, todayY, todayX, plotBottom, todayLinePaint)
        canvas.drawCircle(todayX, todayY, dp(DOT_RADIUS_DP), dotPaint)

        if (labels.isNotEmpty()) {
            labelPaint.textSize = labelTextPx
            for (index in values.indices) {
                val label = labels.getOrNull(index) ?: continue
                // La inicial de hoy también en rojo: si la marca de arriba es roja y la etiqueta
                // de abajo gris, se leen como dos cosas distintas. Y la del día tocado, en blanco,
                // para que se sepa cuál se está mirando sin buscar la burbuja.
                labelPaint.color = when {
                    index == selectedIndex -> litColor
                    index == values.lastIndex -> accentColor
                    else -> greyColor
                }
                canvas.drawText(label, xs[index], height - labelBaselinePx, labelPaint)
            }
        }

        selectedIndex?.let { drawBubble(canvas, it, xs, ys, plotTop) }
    }

    /**
     * La burbuja con el dato del día tocado.
     *
     * Va **encima del punto** salvo que no quepa, en cuyo caso baja debajo: el pico es justo el
     * valor que más se quiere consultar y es el que menos sitio deja arriba. Y se empuja hacia
     * dentro por los lados para que en el primer y el último día no se corte contra el borde.
     */
    private fun drawBubble(canvas: Canvas, index: Int, xs: FloatArray, ys: FloatArray, plotTop: Float) {
        val value = values.getOrNull(index) ?: return
        val text = labelFormatter(value)
        bubbleTextPaint.textSize = dp(BUBBLE_TEXT_DP)

        val padH = dp(BUBBLE_PADDING_H_DP)
        val padV = dp(BUBBLE_PADDING_V_DP)
        val textWidth = bubbleTextPaint.measureText(text)
        val metrics = bubbleTextPaint.fontMetrics
        val bubbleW = textWidth + padH * 2
        val bubbleH = (metrics.descent - metrics.ascent) + padV * 2

        // El punto marcado, para que se vea cuál se ha tocado aunque la burbuja quede desplazada.
        markerPaint.color = if (index == values.lastIndex) accentColor else litColor
        canvas.drawCircle(xs[index], ys[index], dp(DOT_RADIUS_DP), markerPaint)

        val gap = dp(BUBBLE_GAP_DP)
        var topY = ys[index] - gap - bubbleH
        if (topY < 0f) topY = ys[index] + gap          // no cabe arriba: se pone debajo
        topY = topY.coerceIn(0f, height - bubbleH)

        val left = (xs[index] - bubbleW / 2).coerceIn(0f, width - bubbleW)
        bubbleRect.set(left, topY, left + bubbleW, topY + bubbleH)

        val radius = dp(BUBBLE_RADIUS_DP)
        canvas.drawRoundRect(bubbleRect, radius, radius, bubblePaint)
        canvas.drawRoundRect(bubbleRect, radius, radius, bubbleBorderPaint)
        canvas.drawText(
            text,
            bubbleRect.centerX(),
            bubbleRect.centerY() - (metrics.ascent + metrics.descent) / 2f,
            bubbleTextPaint
        )
    }

    /**
     * Catmull-Rom convertida a bézier cúbica, que es lo que sabe dibujar [Path].
     *
     * Catmull-Rom porque **pasa por todos los puntos**. Una bézier suelta con tiradores a ojo
     * suaviza más, pero se separa del dato: la curva enseñaría un martes que no es el martes que
     * hubo. En una gráfica de datos reales eso no vale.
     *
     * Los tiradores de los extremos se calculan repitiendo el punto de fuera, que es la forma
     * estándar de cerrar la curva sin que el primer y el último tramo salgan rectos de golpe.
     */
    private fun buildSpline(xs: FloatArray, ys: FloatArray) {
        curvePath.reset()
        curvePath.moveTo(xs[0], ys[0])

        for (i in 0 until xs.size - 1) {
            val p0x = xs[maxOf(i - 1, 0)]
            val p0y = ys[maxOf(i - 1, 0)]
            val p1x = xs[i]
            val p1y = ys[i]
            val p2x = xs[i + 1]
            val p2y = ys[i + 1]
            val p3x = xs[minOf(i + 2, xs.size - 1)]
            val p3y = ys[minOf(i + 2, ys.size - 1)]

            val c1x = p1x + (p2x - p0x) / TENSION
            val c1y = p1y + (p2y - p0y) / TENSION
            val c2x = p2x - (p3x - p1x) / TENSION
            val c2y = p2y - (p3y - p1y) / TENSION

            curvePath.cubicTo(c1x, c1y, c2x, c2y, p2x, p2y)
        }
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val HEIGHT_DP = 132f
        const val LABEL_SPACE_DP = 16f
        const val LABEL_TEXT_DP = 9f
        const val LABEL_BASELINE_DP = 3f

        /** Aire a los lados para que el vértice de hoy y su halo no se corten contra el borde. */
        const val SIDE_PADDING_DP = 10f

        /** Y arriba, para que el pico no toque el techo de la vista. */
        const val TOP_PADDING_DP = 12f

        const val LINE_STROKE_DP = 2f
        const val HALO_STROKE_DP = 7f
        const val TODAY_STROKE_DP = 1f
        const val DASH_DP = 2.5f
        const val DOT_RADIUS_DP = 3.5f

        /** Bastante bajo: el halo tiene que insinuarse, no dibujar una segunda línea. */
        const val HALO_ALPHA = 46

        /** El relleno arranca tenue y baja a nada: es contexto, no otro dato. */
        const val FILL_TOP_ALPHA = 64

        /**
         * El 6 es la Catmull-Rom estándar (tensión 0,5). Subirlo aplana la curva hacia rectas y
         * bajarlo la hace ondear de más, inventando subidas entre dos días que no existieron.
         */
        const val TENSION = 6f

        // La burbuja del día tocado.
        const val BUBBLE_TEXT_DP = 11f
        const val BUBBLE_PADDING_H_DP = 8f
        const val BUBBLE_PADDING_V_DP = 5f
        const val BUBBLE_RADIUS_DP = 6f
        const val BUBBLE_STROKE_DP = 1f

        /** Separación entre el punto y la burbuja, para que no la tape. */
        const val BUBBLE_GAP_DP = 8f
    }
}
