package dev.glyphrotator.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import dev.glyphrotator.app.R
import dev.glyphrotator.app.habits.HabitRules
import kotlin.math.ceil

/**
 * El calendario de los últimos meses, dibujado con puntos.
 *
 * Una columna por semana y una fila por día, como los mapas de actividad que se ven por ahí,
 * pero con **puntos en vez de cuadrados**: la trama de puntos es el idioma de Nothing y es lo
 * que hace que esto y la pantalla de atrás del teléfono parezcan la misma cosa.
 *
 * Los días sin nada se dibujan igual, muy tenues. Es lo mismo que en los iconos: se ve la
 * rejilla entera, como una pantalla de LEDs donde solo algunos están encendidos. Sin los
 * apagados no se entendería cuánto hueco has dejado, que es justo lo que hay que ver.
 */
class DotCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Intensidad de cada día, de 0 a 1, del más antiguo al de hoy. */
    private var intensities: List<Float> = emptyList()

    /** Día de la semana del último dato: 1 lunes, 7 domingo. */
    private var lastWeekday: Int = 7

    /** Cuando está encendido, el último punto se marca en rojo: es "hoy". */
    private var markToday: Boolean = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
    }

    /**
     * Suelo de altura, en dp.
     *
     * Casi nunca manda —el alto normal sale del ancho, proporcional, y por eso el resto de la
     * vista es independiente de la densidad—, pero cuando manda tenía que estar en dp igual: 60
     * píxeles crudos son 20 dp en el Phone (3) y el triple en una pantalla de densidad 1.
     */
    private val minHeightPx = (MIN_HEIGHT_DP * resources.displayMetrics.density).toInt()

    // Blanco cálido para lo cumplido: el mismo valor que un LED encendido en la Matrix.
    private val litColor = context.getColor(R.color.glyph_led_on)
    private val dimColor = context.getColor(R.color.glyph_led_off)
    private val todayColor = context.getColor(R.color.glyph_red)

    fun setData(intensities: List<Float>, lastWeekday: Int, markToday: Boolean = true) {
        this.intensities = intensities
        this.lastWeekday = lastWeekday.coerceIn(1, DAYS_PER_WEEK)
        this.markToday = markToday
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // El alto sale del ancho: siete filas de puntos cuadrados, sin dejar huecos raros. El
        // hueco de las etiquetas se descuenta primero, o los puntos se saldrían por la derecha.
        val usable = width - labelWidth()
        val cell = if (weeks() == 0) 0f else usable.toFloat() / weeks()
        val height = ceil(cell * DAYS_PER_WEEK).toInt().coerceAtLeast(minHeightPx)
        setMeasuredDimension(width, height)
    }

    /** El hueco de la izquierda para las iniciales de los días. */
    private fun labelWidth(): Float = labelPaint.measureText("M") * LABEL_GUTTER

    private fun weeks(): Int {
        if (intensities.isEmpty()) return 0
        // Los días que sobran antes del primer lunes ocupan su propia columna igualmente.
        val leading = (DAYS_PER_WEEK - ((intensities.size - lastWeekday) % DAYS_PER_WEEK)) % DAYS_PER_WEEK
        return ceil((intensities.size + leading).toFloat() / DAYS_PER_WEEK).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val total = intensities.size
        if (total == 0) return

        val columns = weeks()
        val gutter = labelWidth()
        val cell = (width - gutter) / columns
        val radius = cell * DOT_RATIO / 2f

        // Las iniciales de los días, en columna a la izquierda. Sin esto la rejilla se lee como
        // una textura bonita y no como un calendario: no hay forma de saber que la primera fila
        // es el lunes ni de situar un hueco en la semana.
        labelPaint.textSize = cell * LABEL_TEXT_RATIO
        for (row in 0 until DAYS_PER_WEEK) {
            labelPaint.color = dimColor
            canvas.drawText(
                WEEKDAY_INITIALS[row],
                0f,
                cell * (row + 0.5f) + labelPaint.textSize / 3f,
                labelPaint
            )
        }

        // Se recorre hacia atrás desde hoy: así el último punto cae siempre en su día de la
        // semana real y el calendario queda alineado sin tener que saber en qué día empezó.
        for (index in total - 1 downTo 0) {
            val fromEnd = total - 1 - index
            val weekday = ((lastWeekday - 1 - fromEnd) % DAYS_PER_WEEK + DAYS_PER_WEEK) % DAYS_PER_WEEK
            val column = columns - 1 - ((fromEnd + (DAYS_PER_WEEK - lastWeekday)) / DAYS_PER_WEEK)
            if (column < 0) continue

            val intensity = intensities[index].coerceIn(0f, 1f)
            paint.color = when {
                fromEnd == 0 && markToday -> todayColor
                intensity <= 0f -> dimColor
                else -> blendToWhite(intensity)
            }

            val centerX = gutter + cell * (column + 0.5f)
            val centerY = cell * (weekday + 0.5f)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, radius, paint)

            // El día redondo lleva un anillo alrededor. A este tamaño una gota o una marca
            // dentro del punto no se leería —son unos pocos píxeles—, y el anillo destaca el día
            // sin romper la trama: sigue siendo el mismo punto, solo que señalado.
            if (intensity >= 1f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = radius * RING_WIDTH_RATIO
                canvas.drawCircle(centerX, centerY, radius * RING_RADIUS_RATIO, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    /**
     * Del gris de la rejilla al blanco, según la intensidad.
     *
     * Un salto seco entre "hecho" y "no hecho" perdería el matiz de un día en que bebiste la
     * mitad del objetivo, que no es lo mismo que no beber nada.
     */
    private fun blendToWhite(intensity: Float): Int {
        val from = dimColor
        val to = litColor
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * intensity).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    companion object {
        const val DAYS_PER_WEEK = 7

        /**
         * Las iniciales, empezando en lunes.
         *
         * En lunes y no en domingo porque es como se piensa la semana aquí y como se cuentan los
         * objetivos: "tres veces por semana" empieza el lunes. Con la semana arrancando en
         * domingo, el fin de semana quedaría partido entre dos columnas.
         *
         * La X del miércoles evita que las dos "M" se confundan.
         */
        private val WEEKDAY_INITIALS = arrayOf("L", "M", "X", "J", "V", "S", "D")

        /** El día de la semana de hoy, para alinear el calendario. */
        fun todayWeekday(): Int = HabitRules.daysElapsedInWeek(HabitRules.today())

        /**
         * Cuántos días pedir para que salgan [weeks] columnas y **todas las filas empiecen en la
         * misma**.
         *
         * Con un número redondo de días —28, 63— la ventana casi nunca cae en lunes, así que sobran
         * días sueltos al principio que se llevan una columna propia. El efecto se veía raro y con
         * razón: un martes 22 de agosto, la fila del domingo dibujaba en las columnas 0 a 3 y las
         * otras seis filas de la 1 a la 4, así que **la fila del domingo salía corrida** y su primer
         * punto se metía debajo de la letra.
         *
         * Contando desde hoy hacia atrás hasta el lunes de la semana [weeks]-ésima, la primera
         * columna sale completa y ninguna fila se descuelga. Las filas de los días que esta semana
         * todavía no han llegado se quedan una columna cortas por la derecha, que es lo correcto:
         * el domingo que viene aún no ha pasado.
         */
        fun daysForWeeks(weeks: Int): Int =
            todayWeekday() + (weeks - 1).coerceAtLeast(0) * DAYS_PER_WEEK

        private const val DOT_RATIO = 0.68f
        private const val MIN_HEIGHT_DP = 20f

        /** Anchos de la columna de iniciales, en múltiplos del ancho de una letra. */
        private const val LABEL_GUTTER = 2.2f
        private const val LABEL_TEXT_RATIO = 0.62f

        /** El anillo del día redondo: justo fuera del punto y fino. */
        private const val RING_RADIUS_RATIO = 1.55f
        private const val RING_WIDTH_RATIO = 0.3f
    }
}
