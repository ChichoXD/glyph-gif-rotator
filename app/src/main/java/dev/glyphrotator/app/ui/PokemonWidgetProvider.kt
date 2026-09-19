package dev.glyphrotator.app.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.glyphrotator.app.R
import dev.glyphrotator.app.pokemon.EggRules
import dev.glyphrotator.app.pokemon.EggStore
import dev.glyphrotator.app.pokemon.LevelCalculator
import dev.glyphrotator.app.pokemon.PokemonRepository

/**
 * El widget del escritorio: tu compañero y tu huevo, sin abrir nada.
 *
 * Tiene sentido justo porque el juego pasa con la pantalla apagada: el progreso ocurre cuando
 * no estás mirando, y sin esto la única forma de saber cómo va era abrir la app a preguntar.
 *
 * Se refresca por dos vías: el periodo del sistema (media hora, que es el mínimo real) como red
 * de seguridad, y un empujón desde el servicio en cuanto pasa algo que se nota —una captura,
 * una eclosión, un nivel—. Solo con el periodo, un huevo recién abierto tardaría media hora en
 * aparecer y parecería que no funciona.
 */
class PokemonWidgetProvider : AppWidgetProvider() {

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
        val views = RemoteViews(context.packageName, R.layout.widget_pokemon)
        val repository = PokemonRepository(context)
        val eggs = EggStore(context)

        val partner = repository.getTrainingPartner()
        if (partner == null) {
            views.setTextViewText(R.id.widgetPartnerName, context.getString(R.string.widget_no_partner))
            views.setTextViewText(R.id.widgetPartnerDetail, context.getString(R.string.widget_no_partner_hint))
            views.setImageViewBitmap(R.id.widgetPartnerProgress, dev.glyphrotator.app.ui.widget.WidgetSegments.render(context, 0f))
        } else {
            views.setTextViewText(R.id.widgetPartnerName, partner.displayName)
            val progress = (LevelCalculator.progressFraction(partner.level, partner.exp) * 100).toInt()
            views.setTextViewText(
                R.id.widgetPartnerDetail,
                if (partner.isAtLevelCap) {
                    context.getString(R.string.widget_partner_capped, partner.level)
                } else {
                    context.getString(R.string.widget_partner_level, partner.level, progress)
                }
            )
            // En rojo: es el que esta ganando experiencia ahora mismo.
            views.setImageViewBitmap(
                R.id.widgetPartnerProgress,
                dev.glyphrotator.app.ui.widget.WidgetSegments.render(context, progress / 100f, active = true)
            )
        }

        if (eggs.hasEgg) {
            val progress = (eggs.progress * 100).toInt()
            views.setTextViewText(
                R.id.widgetEggTitle,
                context.resources.getQuantityString(R.plurals.widget_eggs, eggs.count, eggs.count)
            )
            views.setTextViewText(
                R.id.widgetEggDetail,
                context.getString(R.string.widget_egg_progress, progress)
            )
            views.setImageViewBitmap(
                R.id.widgetEggProgress,
                dev.glyphrotator.app.ui.widget.WidgetSegments.render(context, progress / 100f)
            )
        } else {
            views.setTextViewText(R.id.widgetEggTitle, context.getString(R.string.widget_no_eggs))
            views.setTextViewText(
                R.id.widgetEggDetail,
                context.getString(
                    R.string.widget_next_egg,
                    eggs.capturesTowardNext,
                    EggRules.CAPTURES_PER_EGG
                )
            )
            views.setImageViewBitmap(
                R.id.widgetEggProgress,
                dev.glyphrotator.app.ui.widget.WidgetSegments.render(
                    context,
                    eggs.capturesTowardNext.toFloat() / EggRules.CAPTURES_PER_EGG
                )
            )
        }

        // Tocarlo abre el juego, que es lo que uno espera de un widget que enseña progreso.
        val open = Intent(context, PokemonActivity::class.java)
        views.setOnClickPendingIntent(
            R.id.widgetRoot,
            PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }

    companion object {

        /**
         * Repinta todos los widgets colocados.
         *
         * Se llama desde el servicio cuando el estado cambia de verdad. Es barato: si no hay
         * ninguno puesto, la lista viene vacía y no se hace nada.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, PokemonWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val provider = PokemonWidgetProvider()
            provider.onUpdate(context, manager, ids)
        }
    }
}
