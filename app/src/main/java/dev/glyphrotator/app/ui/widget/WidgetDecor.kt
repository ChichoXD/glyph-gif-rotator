package dev.glyphrotator.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import dev.glyphrotator.app.glyph.DotIcons
import dev.glyphrotator.app.ui.MinimalIcons

/**
 * Con qué está decorado cada widget.
 *
 * Se guarda **por widget colocado**, no por tipo: la gracia es poder tener dos widgets de agua
 * en el escritorio, uno con una gota y otro con una imagen tuya. La clave es el id que Android
 * le da a cada uno al colocarlo.
 *
 * Lo importado se guarda como URI y se convierte a puntos **al pintar**, no al elegirlo. Así el
 * mismo dibujo se adapta si algún día cambia el tamaño o los colores, y no hay que guardar
 * imágenes dentro de las preferencias.
 */
object WidgetDecor {

    /** Lo que decora un widget: un icono de los que vienen, una imagen tuya, o nada. */
    sealed class Decoration {
        data object None : Decoration()
        data class Builtin(val icon: DotIcons.Icon) : Decoration()
        data class Imported(val uri: Uri) : Decoration()
    }

    fun get(context: Context, appWidgetId: Int): Decoration {
        val raw = prefs(context).getString(key(appWidgetId), null) ?: return Decoration.None
        return when {
            raw.startsWith(PREFIX_ICON) -> {
                val name = raw.removePrefix(PREFIX_ICON)
                runCatching { DotIcons.Icon.valueOf(name) }
                    .map { Decoration.Builtin(it) }
                    // Un icono que ya no existe (renombrado entre versiones) no debe dejar el
                    // widget en blanco para siempre: se cae a "sin decoración".
                    .getOrElse { Decoration.None }
            }
            raw.startsWith(PREFIX_URI) -> Decoration.Imported(Uri.parse(raw.removePrefix(PREFIX_URI)))
            else -> Decoration.None
        }
    }

    fun setIcon(context: Context, appWidgetId: Int, icon: DotIcons.Icon) {
        prefs(context).edit().putString(key(appWidgetId), PREFIX_ICON + icon.name).apply()
    }

    fun setImported(context: Context, appWidgetId: Int, uri: Uri) {
        prefs(context).edit().putString(key(appWidgetId), PREFIX_URI + uri).apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(key(appWidgetId)).apply()
    }

    /**
     * El dibujo ya listo para meter en el widget, o null si no lleva decoración.
     *
     * Una imagen que no se pueda abrir —se borró, se revocó el permiso— devuelve null en vez de
     * reventar: el widget se queda sin icono, que es molesto pero no rompe el escritorio.
     */
    fun bitmap(context: Context, appWidgetId: Int, sizePx: Int): Bitmap? {
        return when (val decoration = get(context, appWidgetId)) {
            is Decoration.None -> null
            is Decoration.Builtin -> MinimalIcons.bitmap(context, decoration.icon, sizePx)
            is Decoration.Imported -> runCatching {
                val source = decodeSmall(context, decoration.uri) ?: return@runCatching null
                DotIcons.render(DotIcons.fromBitmap(source), sizePx)
            }.onFailure {
                Log.w(TAG, "No se pudo leer la imagen del widget $appWidgetId", it)
            }.getOrNull()
        }
    }

    /**
     * Lee la imagen ya reducida, sin cargarla entera en memoria.
     *
     * Importa de verdad: el destino son 12x12 puntos, pero una foto del carrete son 12 millones
     * de píxeles, y decodificarla entera son ~48 MB **en el proceso del escritorio**, no en el
     * de la app. Con varios widgets decorados con fotos, eso tumba el lanzador. Se mide primero
     * y se decodifica saltando píxeles, que es exactamente lo que hace falta cuando el
     * resultado va a ser una rejilla diminuta.
     */
    private fun decodeSmall(context: Context, uri: Uri): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= DECODE_TARGET_PX &&
            bounds.outHeight / (sample * 2) >= DECODE_TARGET_PX
        ) {
            sample *= 2
        }

        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri).use {
            android.graphics.BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"

    private const val TAG = "WidgetDecor"
    private const val PREFS_NAME = "glyph_widget_decor"
    private const val PREFIX_ICON = "icon:"
    private const val PREFIX_URI = "uri:"

    /** El lado del dibujo en píxeles. Suficiente para que los puntos salgan redondos. */
    const val ICON_SIZE_PX = 144

    /** A partir de este tamaño ya sobra resolución para sacar 12x12 puntos. */
    private const val DECODE_TARGET_PX = 64
}
