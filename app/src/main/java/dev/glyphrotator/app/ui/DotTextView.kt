package dev.glyphrotator.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import dev.glyphrotator.app.R
import dev.glyphrotator.app.glyph.DotFont

/**
 * Un título escrito con puntos, como el display de la Matrix.
 *
 * Es el elemento que le da cara a la app. Un título en monoespaciada es correcto y es lo que
 * tiene cualquier aplicación; escrito en puntos se reconoce al instante como parte de un aparato
 * con una pantalla de LEDs detrás. Se usa **solo en los títulos de pantalla**: en cuanto se
 * reparte por todos lados deja de llamar la atención y se vuelve ilegible en textos largos.
 *
 * Por defecto **solo se pintan los puntos encendidos**. Los iconos sí llevan la rejilla apagada
 * de fondo —la necesitan para leerse a tamaño pequeño—, pero en un título de diecisiete letras
 * esa trama se junta y lo que se ve es un rectángulo gris con algo dentro. Se puede activar con
 * [showGrid] donde tenga sentido.
 */
class DotTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var text: String = ""
        set(value) {
            if (value == field) return
            field = value
            rejilla = DotFont.grid(value, SEPARACION)
            requestLayout()
            invalidate()
        }

    /** El lado de cada celda. Manda sobre el tamaño de la vista, como una fuente. */
    var cellSizeDp: Float = 5f
        set(value) {
            if (value == field) return
            field = value
            requestLayout()
            invalidate()
        }

    /** Enciende los puntos en rojo. Para un título que además es un estado. */
    var isActive: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            invalidate()
        }

    /**
     * Si se dibujan también los puntos apagados.
     *
     * En los iconos sí, porque un dibujo pequeño necesita la rejilla para entenderse. En un
     * título **no**: con 17 letras la trama de apagados se junta y el título entero se lee como
     * un rectángulo gris con algo dentro. Solo encendidos, y las letras respiran.
     */
    var showGrid: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            invalidate()
        }

    private var rejilla: Array<BooleanArray> = DotFont.grid("", SEPARACION)

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorEncendido = ContextCompat.getColor(context, R.color.glyph_white)
    private val colorActivo = ContextCompat.getColor(context, R.color.glyph_red)
    private val colorApagado = APAGADO

    /**
     * El lado de celda que de verdad se usa.
     *
     * Se pide el tamaño deseado, pero si el texto no cabe de ancho se encoge hasta que quepa en
     * vez de salirse. Un título recortado por la derecha es peor que uno un poco más pequeño, y
     * los nombres largos —"GLYPH GIF ROTATOR" son 102 columnas de puntos— no caben a tamaño
     * completo en un móvil.
     */
    private var celdaPx: Float = 0f

    private fun celdaDeseada(): Float = cellSizeDp * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val columnas = rejilla.firstOrNull()?.size ?: 0
        val horizontal = paddingLeft + paddingRight

        var celda = celdaDeseada()
        val modo = MeasureSpec.getMode(widthMeasureSpec)
        val disponible = MeasureSpec.getSize(widthMeasureSpec) - horizontal
        if (columnas > 0 && modo != MeasureSpec.UNSPECIFIED && disponible > 0) {
            celda = minOf(celda, disponible.toFloat() / columnas)
        }
        celdaPx = celda

        val ancho = (columnas * celda).toInt() + horizontal
        val alto = (DotFont.HEIGHT * celda).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(ancho, widthMeasureSpec),
            resolveSize(alto, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val columnas = rejilla.firstOrNull()?.size ?: return
        if (columnas == 0) return

        val celda = if (celdaPx > 0f) celdaPx else celdaDeseada()
        // El punto no llena su celda: el hueco es lo que hace la trama.
        val radio = celda * PROPORCION_PUNTO / 2f
        val encendido = if (isActive) colorActivo else colorEncendido

        for (y in 0 until DotFont.HEIGHT) {
            for (x in 0 until columnas) {
                val vivo = rejilla[y][x]
                if (!vivo && !showGrid) continue
                pincel.color = if (vivo) encendido else colorApagado
                canvas.drawCircle(
                    paddingLeft + celda * (x + 0.5f),
                    paddingTop + celda * (y + 0.5f),
                    radio,
                    pincel
                )
            }
        }
    }

    private companion object {
        /** Blanco al 13 %: se intuye la rejilla sin competir con las letras. */
        const val APAGADO = 0x22FFFFFF
        const val PROPORCION_PUNTO = 0.72f

        /** Dos columnas entre letras: a una sola, las palabras largas se pegan y no se leen. */
        const val SEPARACION = 2
    }
}
