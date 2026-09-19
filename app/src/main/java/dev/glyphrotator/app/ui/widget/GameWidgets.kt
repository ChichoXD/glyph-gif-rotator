package dev.glyphrotator.app.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.glyphrotator.app.R
import dev.glyphrotator.app.data.AppPreferences
import dev.glyphrotator.app.glyph.DotIcons
import dev.glyphrotator.app.habits.DailyBonus
import dev.glyphrotator.app.habits.HabitStore
import dev.glyphrotator.app.habits.WaterStore
import dev.glyphrotator.app.pokemon.EggRules
import dev.glyphrotator.app.pokemon.EggStore
import dev.glyphrotator.app.pokemon.LevelCalculator
import dev.glyphrotator.app.pokemon.PokemonRegistry
import dev.glyphrotator.app.pokemon.PokemonRepository
import dev.glyphrotator.app.pokemon.spawn.SleepTracker

/**
 * Los widgets del juego, todos sobre el mismo molde ([StatWidgetProvider]).
 *
 * Están juntos en un archivo porque cada uno son diez líneas: lo único que hace cada clase es
 * decir qué texto va en cada hueco. Repartirlos en ocho archivos escondería lo que de verdad
 * importa, que es poder compararlos de un vistazo.
 */

/** Tu compañero: nivel y cuánto le falta para el siguiente. */
class PartnerWidget : StatWidgetProvider() {
    override fun defaultIcon() = DotIcons.Icon.POKEBALL

    /**
     * Aquí se ve **el Pokémon que entrena**, no una pokéball.
     *
     * Es el único widget cuyo dibujo tiene un sujeto concreto: los demás representan una idea
     * —agua, sueño, hábitos— y un icono lineal la dice mejor que cualquier foto. Este tiene un
     * nombre propio delante, y una pokéball genérica desperdiciaba el único sitio de la pantalla
     * de inicio donde tu compañero podía asomarse.
     *
     * Cae a la pokéball cuando no hay compañero elegido o cuando su sprite no se puede leer: el
     * hueco vacío se nota más que un icono de más.
     */
    override fun autoIcon(context: Context, sizePx: Int): android.graphics.Bitmap? {
        val partner = PokemonRepository(context).getTrainingPartner()
            ?: return super.autoIcon(context, sizePx)
        return WidgetSprite.firstFrame(
            context = context,
            speciesId = partner.speciesId,
            sizePx = sizePx,
            color = AppPreferences(context).widgetColor,
        ) ?: super.autoIcon(context, sizePx)
    }

    override fun stat(context: Context): Stat {
        val partner = PokemonRepository(context).getTrainingPartner()
            ?: return Stat(
                title = context.getString(R.string.widget_partner_title),
                value = context.getString(R.string.widget_no_partner),
                detail = context.getString(R.string.widget_no_partner_hint),
            )

        val progress = (LevelCalculator.progressFraction(partner.level, partner.exp) * 100).toInt()
        return Stat(
            title = context.getString(R.string.widget_partner_title),
            value = partner.displayName,
            detail = if (partner.isAtLevelCap) {
                context.getString(R.string.widget_partner_capped, partner.level)
            } else {
                context.getString(R.string.widget_partner_level, partner.level, progress)
            },
            progress = if (partner.isAtLevelCap) 100 else progress,
        )
    }
}

/** El huevo: cuántos tienes y cómo va el que incuba. */
class EggWidget : StatWidgetProvider() {
    override fun defaultIcon() = DotIcons.Icon.EGG

    override fun stat(context: Context): Stat {
        val eggs = EggStore(context)
        if (!eggs.hasEgg) {
            return Stat(
                title = context.getString(R.string.widget_egg_title),
                value = context.getString(R.string.widget_no_eggs),
                detail = context.getString(
                    R.string.widget_next_egg,
                    eggs.capturesTowardNext,
                    EggRules.CAPTURES_PER_EGG
                ),
                progress = eggs.capturesTowardNext * 100 / EggRules.CAPTURES_PER_EGG,
            )
        }
        val progress = (eggs.progress * 100).toInt()
        return Stat(
            title = context.getString(R.string.widget_egg_title),
            value = context.resources.getQuantityString(R.plurals.widget_eggs, eggs.count, eggs.count),
            detail = context.getString(R.string.widget_egg_progress, progress),
            progress = progress,
        )
    }
}

/**
 * La Pokédex: cuántas especies llevas registradas.
 *
 * Este no se decora ni pregunta nada al colocarlo: lleva la Pokédex dibujada y punto. Elegirle
 * una mancuerna no significaría nada, y que te lo preguntara sería una pantalla de más por medio.
 */
class PokedexWidget : StatWidgetProvider() {

    override fun defaultIcon(): DotIcons.Icon = DotIcons.Icon.POKEDEX

    override fun stat(context: Context): Stat {
        val repository = PokemonRepository(context)
        val seen = repository.getPokedex().size
        val total = PokemonRegistry.all.size
        val caught = repository.getCaught().size

        return Stat(
            title = context.getString(R.string.widget_pokedex_title),
            value = context.getString(R.string.widget_pokedex_value, seen, total),
            detail = context.getString(R.string.widget_pokedex_detail, caught),
            progress = if (total == 0) 0 else seen * 100 / total,
        )
    }
}


/** Cómo dormiste anoche y qué racha llevas. */
class SleepWidget : StatWidgetProvider() {
    override fun defaultIcon() = DotIcons.Icon.SLEEP_ZZZ

    override fun stat(context: Context): Stat {
        val sleep = SleepTracker(context)
        if (sleep.lastNightMinutes <= 0) {
            return Stat(
                title = context.getString(R.string.widget_sleep_title),
                value = context.getString(R.string.widget_sleep_none),
                detail = context.getString(R.string.widget_sleep_none_hint),
            )
        }
        val quality = (sleep.currentQuality * 100).toInt()
        return Stat(
            title = context.getString(R.string.widget_sleep_title),
            value = context.getString(
                R.string.widget_sleep_value,
                sleep.lastNightMinutes / 60,
                sleep.lastNightMinutes % 60
            ),
            detail = context.getString(R.string.widget_sleep_detail, quality, sleep.nightsRecorded),
            progress = quality,
        )
    }
}

/** Los hábitos cumplidos hoy. */
class HabitsWidget : StatWidgetProvider() {
    override fun stat(context: Context): Stat {
        val habits = HabitStore(context).all()
        if (habits.isEmpty()) {
            return Stat(
                title = context.getString(R.string.widget_habits_title),
                value = context.getString(R.string.widget_habits_empty),
                detail = context.getString(R.string.widget_habits_empty_hint),
            )
        }
        // Los que van **al ritmo**, no los marcados hoy. Con "gym 3 veces por semana", un día de
        // descanso el widget decía "0 de 3" y la barra vacía mientras el bonus estaba intacto:
        // enseñaba un fracaso que no existía. Aquí se mide lo mismo que se premia.
        val onPace = habits.count { it.onPace() }
        val best = habits.maxOf { it.streak() }
        return Stat(
            title = context.getString(R.string.widget_habits_title),
            value = context.getString(R.string.widget_habits_value, onPace, habits.size),
            detail = context.getString(R.string.widget_habits_detail, best),
            progress = onPace * 100 / habits.size,
        )
    }
}

/** El bonus que tendrá mañana tu entrenamiento, juntando las tres cosas. */
class BonusWidget : StatWidgetProvider() {
    override fun defaultIcon() = DotIcons.Icon.RARE_CANDY

    override fun stat(context: Context): Stat {
        val sleep = SleepTracker(context)
        val water = WaterStore(context)
        val habits = HabitStore(context)
        val all = habits.all()

        val multiplier = DailyBonus.trainingMultiplier(
            sleepQuality = sleep.currentQuality,
            waterGoalMet = water.goalMetToday,
            habitsKept = habits.onPaceCount(),
            habitsTotal = all.size,
        )
        val ceiling = DailyBonus.MAX_MULTIPLIER

        return Stat(
            title = context.getString(R.string.widget_bonus_title),
            value = context.getString(R.string.widget_bonus_value, String.format("%.2f", multiplier)),
            detail = context.getString(
                R.string.widget_bonus_detail,
                String.format("%.2f", ceiling)
            ),
            progress = ((multiplier - 1f) / (ceiling - 1f) * 100).toInt(),
        )
    }
}
