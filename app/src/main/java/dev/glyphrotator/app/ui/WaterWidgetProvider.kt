package dev.glyphrotator.app.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.glyphrotator.app.R
import dev.glyphrotator.app.habits.DailyBonus
import dev.glyphrotator.app.habits.WaterStore
import dev.glyphrotator.app.ui.widget.BonusWidget
import dev.glyphrotator.app.ui.widget.StatWidgetProvider
import dev.glyphrotator.app.ui.widget.WidgetDecor

/**
 * El widget del agua: cada toque suma una ración.
 *
 * Es el único widget que **hace** algo en vez de solo enseñar, y por eso está aparte del molde
 * común. La idea es que apuntar lo que bebes no cueste nada: si hubiera que abrir la app, se
 * dejaría de apuntar al segundo día y la mecánica moriría sola.
 *
 * El toque va por broadcast a este mismo receptor. No abre nada ni enciende la pantalla más
 * allá de lo que ya estaba: sumas y sigues.
 */
class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, id))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ADD -> {
                WaterStore(context).addPortion()
                refresh(context)
                // El bonus del día cambia al cumplir el objetivo, así que su widget también.
                StatWidgetProvider.refresh(context, BonusWidget::class.java)
                return
            }
            ACTION_UNDO -> {
                WaterStore(context).removePortion()
                refresh(context)
                StatWidgetProvider.refresh(context, BonusWidget::class.java)
                return
            }
        }
        super.onReceive(context, intent)
    }

    private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
        val water = WaterStore(context)
        val views = RemoteViews(context.packageName, R.layout.widget_water)

        val icon = WidgetDecor.bitmap(context, appWidgetId, WidgetDecor.ICON_SIZE_PX)
        if (icon != null) {
            views.setImageViewBitmap(R.id.waterIcon, icon)
            views.setViewVisibility(R.id.waterIcon, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.waterIcon, android.view.View.GONE)
        }

        views.setTextViewText(
            R.id.waterAmount,
            context.getString(R.string.widget_water_amount, water.todayMl, water.goalMl)
        )
        views.setImageViewBitmap(
            R.id.waterProgress,
            dev.glyphrotator.app.ui.widget.WidgetSegments.render(context, water.progress)
        )
        views.setTextViewText(
            R.id.waterHint,
            if (water.goalMetToday) {
                context.getString(R.string.widget_water_done)
            } else {
                context.getString(R.string.widget_water_add, water.portionMl)
            }
        )
        views.setTextViewText(
            R.id.waterBonus,
            context.getString(
                R.string.widget_water_bonus,
                String.format("%.2f", DailyBonus.waterFactor(water.goalMetToday))
            )
        )

        // Todo el widget es el botón de sumar: desde el escritorio uno da un toque y sigue, no
        // apunta a un botón pequeño con el pulgar. Restar va aparte, en su esquina.
        views.setOnClickPendingIntent(R.id.waterRoot, broadcast(context, ACTION_ADD))
        views.setOnClickPendingIntent(R.id.waterUndo, broadcast(context, ACTION_UNDO))
        return views
    }

    /**
     * Cada acción con su propio código de petición.
     *
     * Con el mismo código, la segunda sobrescribiría a la primera —Android las considera la
     * misma petición— y restar acabaría sumando.
     */
    private fun broadcast(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, WaterWidgetProvider::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val ACTION_ADD = "dev.glyphrotator.app.action.WATER_ADD"
        private const val ACTION_UNDO = "dev.glyphrotator.app.action.WATER_UNDO"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WaterWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            WaterWidgetProvider().onUpdate(context, manager, ids)
        }
    }
}
