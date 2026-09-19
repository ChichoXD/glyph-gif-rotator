package dev.glyphrotator.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import dev.glyphrotator.app.R
import dev.glyphrotator.app.glyph.DotIcons
import dev.glyphrotator.app.ui.MinimalIcons
import dev.glyphrotator.app.ui.PokemonActivity

/**
 * La base de todos los widgets de dato: título, un número grande, una línea de detalle y barra.
 *
 * Cada widget concreto solo dice **qué** enseñar; el cómo —elegir la versión grande o la
 * pequeña según el tamaño que le hayas dado, pintar, abrir la app al tocar— vive aquí una sola
 * vez. Es lo que permite tener ocho widgets sin ocho copias del mismo código.
 */
abstract class StatWidgetProvider : AppWidgetProvider() {

    /** Lo que enseña un widget: [progress] va de 0 a 100, o -1 para no pintar barra. */
    data class Stat(
        val title: String,
        val value: String,
        val detail: String,
        val progress: Int = -1,
    )

    protected abstract fun stat(context: Context): Stat

    /**
     * El dibujo que lleva de fábrica, cuando el widget no es de los que se decoran.
     *
     * Hay widgets donde elegir icono no significa nada: la Pokédex es la Pokédex. Esos no
     * declaran pantalla de configuración —así no te preguntan nada al colocarlos— y ponen aquí
     * su dibujo.
     */
    protected open fun defaultIcon(): DotIcons.Icon? = null

    /**
     * El dibujo cuando el usuario **no** ha elegido decoración a mano.
     *
     * Por defecto, el icono lineal de [defaultIcon]. Existe como punto aparte porque el widget de
     * compañero lo pisa con el sprite del Pokémon que entrena, y eso no es un icono fijo: cambia
     * con la partida. La decoración elegida a mano sigue mandando sobre esto — quien se ha
     * molestado en elegir un dibujo no quiere que se lo pisemos.
     */
    protected open fun autoIcon(context: Context, sizePx: Int): android.graphics.Bitmap? =
        defaultIcon()?.let { MinimalIcons.bitmap(context, it, sizePx) }

    /**
     * Qué pasa al tocarlo. Por defecto abre el juego, que es lo que se espera de un widget que
     * enseña progreso; los que hacen algo (el agua suma, la colección cambia de Pokémon) lo
     * cambian.
     */
    protected open fun clickIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, PokemonActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, render(context, appWidgetManager, id))
        }
    }

    /** Al redimensionarlo se vuelve a pintar: puede tocar cambiar de versión. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, render(context, appWidgetManager, appWidgetId))
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ): RemoteViews {
        val height = manager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val compact = height in 1 until COMPACT_HEIGHT_DP

        val layout = if (compact) R.layout.widget_stat_compact else R.layout.widget_stat
        val stat = stat(context)

        return RemoteViews(context.packageName, layout).apply {
            // La decoración es de este widget concreto, no del tipo: dos widgets de agua pueden
            // llevar dibujos distintos.
            val icon = WidgetDecor.bitmap(context, appWidgetId, WidgetDecor.ICON_SIZE_PX)
                ?: autoIcon(context, WidgetDecor.ICON_SIZE_PX)
            if (icon != null) {
                setImageViewBitmap(R.id.statIcon, icon)
                setViewVisibility(R.id.statIcon, android.view.View.VISIBLE)
            } else {
                // Oculto y no invisible: en un widget pequeño, un hueco vacío se nota mucho.
                setViewVisibility(R.id.statIcon, android.view.View.GONE)
            }

            setTextViewText(R.id.statTitle, stat.title)
            setTextViewText(R.id.statValue, stat.value)
            setTextViewText(R.id.statDetail, stat.detail)
            if (stat.progress >= 0) {
                // Segmentos dibujados a mano en vez de la barra del sistema: el mismo idioma que
                // la Matrix. Va como bitmap porque RemoteViews no deja inflar una vista propia.
                setViewVisibility(R.id.statProgress, android.view.View.VISIBLE)
                setImageViewBitmap(
                    R.id.statProgress,
                    WidgetSegments.render(context, stat.progress.coerceIn(0, 100) / 100f)
                )
            } else {
                setViewVisibility(R.id.statProgress, android.view.View.GONE)
            }
            setOnClickPendingIntent(R.id.statRoot, clickIntent(context))
        }
    }

    companion object {
        /** Por debajo de este alto no cabe el detalle sin que quede apretado. */
        private const val COMPACT_HEIGHT_DP = 110

        /**
         * Repinta todos los widgets de una clase.
         *
         * Se llama cuando el estado cambia de verdad —una captura, un nivel, un trago de agua—
         * porque el periodo del sistema es de media hora como mínimo y sin esto un cambio
         * tardaría eso en verse, que se lee como que el widget está roto.
         */
        fun refresh(context: Context, provider: Class<out AppWidgetProvider>) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, provider).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
