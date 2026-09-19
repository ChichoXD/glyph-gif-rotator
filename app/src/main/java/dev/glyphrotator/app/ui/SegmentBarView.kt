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
 * Una barra de progreso hecha de segmentos sueltos, como la fila de un display.
 *
 * Sustituye a la `LinearProgressIndicator` de Material en la ficha de un Pokémon. La razón no es
 * el adorno: una barra continua es de cualquier app, y esta pantalla tiene que parecer un
 * aparato. Los segmentos son el mismo idioma que la Matrix de atrás —puntos encendidos y
 * apagados— y hacen que la experiencia se lea de un vistazo sin tener que estimar una longitud.
 *
 * ```
 * EXP 000 / 300
 * ▮ ▮ ▮ ▯ ▯ ▯ ▯ ▯ ▯ ▯
 * ```
 *
 * El segmento en curso **se enciende entero**, no a medias: un aparato no muestra medio LED.
 * Con 10 segmentos cada uno vale un 10 %, y se prefiere redondear hacia arriba para que el
 * primer punto de experiencia ya encienda el primero — que no se note vacío nada más capturar.
 */
class SegmentBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Cuánto va cumplido, de 0 a 1. */
    var fraction: Float = 0f
        set(value) {
            val nuevo = value.coerceIn(0f, 1f)
            if (nuevo == field) return
            field = nuevo
            invalidate()
        }

    /**
     * Si esta barra es la del compañero que está entrenando.
     *
     * Es el único sitio de la pantalla donde se enciende el rojo, y por eso significa algo:
     * marca dónde está entrando la experiencia ahora mismo. Ver DISENO.md, "el rojo es un
     * estado".
     */
    var isActive: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            invalidate()
        }

    private val encendido = Paint(Paint.ANTI_ALIAS_FLAG)

    // El apagado se pinta, no se omite: ver la fila entera con solo algunos encendidos es lo que
    // separa "una barra de progreso" de "una tira de LEDs". Por eso `glyph_led_off` y no el color
    // del divisor, que era lo que había: un divisor es una línea, esto es un diodo sin encender.
    private val apagado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.glyph_led_off)
    }

    private val segmento = RectF()

    private val colorActivo = ContextCompat.getColor(context, R.color.glyph_red)
    private val colorNormal = ContextCompat.getColor(context, R.color.glyph_led_on)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val alto = resolveSize(
            (ALTO_DP * resources.displayMetrics.density).toInt(),
            heightMeasureSpec
        )
        setMeasuredDimension(ancho, alto)
    }

    override fun onDraw(canvas: Canvas) {
        val ancho = width.toFloat()
        val alto = height.toFloat()
        if (ancho <= 0f || alto <= 0f) return

        val hueco = HUECO_DP * resources.displayMetrics.density
        val anchoSegmento = (ancho - hueco * (SEGMENTOS - 1)) / SEGMENTOS
        if (anchoSegmento <= 0f) return

        // Redondeo hacia arriba, pero sin encender nada con la barra a cero: "casi nada" y
        // "nada" no son lo mismo, y la diferencia importa al capturar.
        val encendidos = if (fraction <= 0f) {
            0
        } else {
            min(SEGMENTOS, max(1, ceil(fraction * SEGMENTOS).toInt()))
        }

        encendido.color = if (isActive) colorActivo else colorNormal
        val radio = alto / 2f

        for (i in 0 until SEGMENTOS) {
            val izquierda = i * (anchoSegmento + hueco)
            segmento.set(izquierda, 0f, izquierda + anchoSegmento, alto)
            canvas.drawRoundRect(segmento, radio, radio, if (i < encendidos) encendido else apagado)
        }
    }

    private companion object {
        /** Diez, para que cada uno sea un 10 % redondo y se cuenten de un vistazo. */
        const val SEGMENTOS = 10
        const val HUECO_DP = 3f
        const val ALTO_DP = 4f
    }
}
