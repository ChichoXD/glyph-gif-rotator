package dev.glyphrotator.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import dev.glyphrotator.app.pokemon.SpriteLibrary

/**
 * El primer fotograma del sprite de una especie, a tamaño de icono de widget.
 *
 * Aparte de [CollectionWidgetProvider] a propósito: aquel necesita **varios** fotogramas para
 * animarlos en un `ViewFlipper`, y monta un `Movie` para poder pedir el frame de un instante
 * concreto. Aquí hace falta uno solo y quieto, que cabe en un `ImageView` de 44 dp. Compartir el
 * camino largo obligaría a decodificar el GIF entero para tirar todo menos el primero.
 *
 * Se reduce al decodificar, con `inSampleSize`, y no después: los bitmaps de un widget viven en el
 * proceso del escritorio, donde hay unos pocos megas **para todos los widgets juntos**. Decodificar
 * a tamaño completo para escalar luego es justo lo que tumba el lanzador.
 */
object WidgetSprite {

    /** Devuelve null si no hay sprite guardado o si el archivo no se deja leer. */
    fun firstFrame(context: Context, speciesId: Int, sizePx: Int, color: Boolean): Bitmap? {
        val uri = SpriteLibrary(context).uriFor(speciesId) ?: return null
        val bitmap = decode(context, uri, sizePx) ?: return null
        return if (color) bitmap else grayscale(bitmap)
    }

    private fun decode(context: Context, uri: Uri, sizePx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        var sample = 1
        while (sizePx > 0 && bounds.outWidth / (sample * 2) >= sizePx) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.onFailure { Log.w(TAG, "No se pudo leer el sprite de $uri", it) }.getOrNull()

    /**
     * A blanco y negro para quien prefiera el mismo idioma que la Matrix.
     *
     * Es la preferencia `widgetColor`, la misma que respeta la pestaña de Colección: si el usuario
     * la ha apagado, un compañero a todo color aquí rompería la coherencia de la pantalla de
     * inicio entera.
     */
    private fun grayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private const val TAG = "WidgetSprite"
}
