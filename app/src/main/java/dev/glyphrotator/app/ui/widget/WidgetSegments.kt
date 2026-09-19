package dev.glyphrotator.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import dev.glyphrotator.app.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * La barra de segmentos de los widgets, dibujada como bitmap.
 *
 * En la app esto es [dev.glyphrotator.app.ui.SegmentBarView], pero un widget no puede inflar una
 * vista propia: `RemoteViews` solo admite un puñado de clases del sistema. La salida es la
 * misma —diez segmentos encendidos o apagados, el idioma de la Matrix— y se mete en un
 * `ImageView` normal, que sí está permitido.
 *
 * Se dibuja a un tamaño fijo en píxeles y se deja que el `ImageView` la estire a lo ancho. Como
 * son rectángulos planos sin detalle, el estirado no se nota, y evita tener que saber de
 * antemano lo ancho que el usuario ha hecho el widget.
 */
object WidgetSegments {

    /**
     * @param fraction cuánto va cumplido, de 0 a 1
     * @param active si el estado es "está pasando ahora": entonces se enciende en rojo. Igual
     *   que en la app, el rojo aquí significa algo y no se reparte por decorar.
     */
    fun render(context: Context, fraction: Float, active: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(ANCHO_PX, ALTO_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val encendido = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(
                context,
                if (active) R.color.glyph_red else R.color.glyph_led_on
            )
        }
        // Apagado con `glyph_led_off`, no con el color del divisor: un divisor es una linea que
        // separa, y esto es un diodo que no esta encendido. Mismo par que SegmentBarView en la app.
        val apagado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.glyph_led_off)
        }

        val anchoSegmento = (ANCHO_PX - HUECO_PX * (SEGMENTOS - 1)).toFloat() / SEGMENTOS
        // Igual que en la app: nada encendido con la barra a cero, pero el primer punto de
        // progreso ya enciende el primer segmento.
        val encendidos = if (fraction <= 0f) {
            0
        } else {
            min(SEGMENTOS, max(1, ceil(fraction.coerceIn(0f, 1f) * SEGMENTOS).toInt()))
        }

        val rect = RectF()
        val radio = ALTO_PX / 2f
        for (i in 0 until SEGMENTOS) {
            val izquierda = i * (anchoSegmento + HUECO_PX)
            rect.set(izquierda, 0f, izquierda + anchoSegmento, ALTO_PX.toFloat())
            canvas.drawRoundRect(rect, radio, radio, if (i < encendidos) encendido else apagado)
        }
        return bitmap
    }

    private const val SEGMENTOS = 10
    private const val ANCHO_PX = 400
    private const val ALTO_PX = 12
    private const val HUECO_PX = 6
}
