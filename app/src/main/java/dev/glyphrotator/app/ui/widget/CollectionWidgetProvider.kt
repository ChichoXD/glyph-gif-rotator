package dev.glyphrotator.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import dev.glyphrotator.app.R
import dev.glyphrotator.app.data.AppPreferences
import dev.glyphrotator.app.pokemon.PokemonRepository
import dev.glyphrotator.app.pokemon.SpriteLibrary
import dev.glyphrotator.app.ui.PokemonActivity

/**
 * Uno de tus Pokémon al azar, **a color y grande**, con la pokéball en una esquina.
 *
 * Es el único widget que no usa el molde de puntos. En los demás el dibujo acompaña a un dato;
 * aquí el Pokémon es el contenido, y pasarlo a doce por doce en blanco y negro sería tirar justo
 * lo que hay que enseñar. El sprite se lee del mismo sitio que usa la Matrix, pero sin convertir:
 * tal cual, con sus colores.
 *
 * Cambia al tocarlo, que es la gracia: sirve para acordarte de lo que tienes guardado en vez de
 * mirar siempre al mismo.
 */
class CollectionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_collection)
        val team = PokemonRepository(context).getCaught()

        if (team.isEmpty()) {
            views.setTextViewText(
                R.id.collectionName,
                context.getString(R.string.widget_collection_empty)
            )
            views.setTextViewText(
                R.id.collectionDetail,
                context.getString(R.string.widget_collection_empty_hint)
            )
        } else {
            val pokemon = team.random()
            views.setTextViewText(R.id.collectionName, pokemon.displayName)
            views.setTextViewText(
                R.id.collectionDetail,
                context.getString(
                    R.string.widget_collection_detail,
                    pokemon.speciesId,
                    pokemon.level,
                    team.size
                )
            )
            showSprite(context, views, pokemon.speciesId)
        }

        views.setOnClickPendingIntent(R.id.collectionRoot, refreshIntent(context))
        views.setOnClickPendingIntent(
            R.id.collectionBall,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, PokemonActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }

    /**
     * Mete los frames del sprite en el ViewFlipper.
     *
     * Con la animación apagada se mete uno solo: el flipper con un único hijo no pasa nada, así
     * que la misma pieza sirve para las dos preferencias sin código aparte.
     */
    private fun showSprite(context: Context, views: RemoteViews, speciesId: Int) {
        val preferences = AppPreferences(context)
        val frames = spriteFrames(
            context = context,
            speciesId = speciesId,
            maxFrames = if (preferences.widgetAnimated) MAX_FRAMES else 1,
            color = preferences.widgetColor,
        )
        if (frames.isEmpty()) return

        // Se limpia antes: sin esto, cada refresco añadiría los frames encima de los anteriores
        // y el widget acabaría con cientos de hijos hasta reventar el límite de RemoteViews.
        views.removeAllViews(R.id.collectionFlipper)
        for (frame in frames) {
            val item = RemoteViews(context.packageName, R.layout.widget_frame)
            item.setImageViewBitmap(R.id.frameImage, frame)
            views.addView(R.id.collectionFlipper, item)
        }
    }

    /**
     * Los primeros frames del GIF, reducidos.
     *
     * Reducidos porque los bitmaps de un widget viven en el proceso del escritorio, y ahí hay
     * unos pocos megas para todos los widgets juntos: un sprite a tamaño completo por cada
     * fotograma lo tumbaría.
     *
     * Se usa [Movie] —sí, está obsoleto— porque es lo único del sistema que deja sacar un
     * fotograma **concreto** de un GIF. `AnimatedImageDrawable`, que es lo moderno, solo sabe
     * reproducirlo entero y no permite pedirle el frame de un instante dado.
     */
    private fun spriteFrames(
        context: Context,
        speciesId: Int,
        maxFrames: Int,
        color: Boolean,
    ): List<Bitmap> {
        val uri = SpriteLibrary(context).uriFor(speciesId) ?: return emptyList()

        val animated = runCatching {
            context.contentResolver.openInputStream(uri).use { android.graphics.Movie.decodeStream(it) }
        }.getOrNull()

        if (animated == null || animated.duration() <= 0 || maxFrames <= 1) {
            return listOfNotNull(firstFrame(context, uri)?.let { if (color) it else grayscale(it) })
        }

        val frames = ArrayList<Bitmap>(maxFrames)
        val scale = (SPRITE_TARGET_PX.toFloat() / maxOf(animated.width(), 1)).coerceAtMost(1f)
        val width = (animated.width() * scale).toInt().coerceAtLeast(1)
        val height = (animated.height() * scale).toInt().coerceAtLeast(1)

        for (index in 0 until maxFrames) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
            animated.setTime(animated.duration() * index / maxFrames)
            animated.draw(canvas, 0f, 0f)
            frames += if (color) bitmap else grayscale(bitmap)
        }
        return frames
    }

    private fun firstFrame(context: Context, uri: android.net.Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SPRITE_TARGET_PX) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.onFailure { Log.w(TAG, "No se pudo leer el sprite de $uri", it) }.getOrNull()

    /** A blanco y negro, para quien prefiera el mismo idioma que la Matrix. */
    private fun grayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply { setSaturation(0f) }
        )
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** Tocarlo trae otro en vez de abrir la app: es lo que uno espera al ver "al azar". */
    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CollectionWidgetProvider::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_IDS,
                AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, CollectionWidgetProvider::class.java))
            ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val TAG = "CollectionWidget"

        /** Lo suficiente para verse nítido sin cargar el escritorio de memoria. */
        const val SPRITE_TARGET_PX = 200

        /** Frames que se meten en el widget. Más no se nota y cada uno ocupa memoria. */
        const val MAX_FRAMES = 8
    }
}
