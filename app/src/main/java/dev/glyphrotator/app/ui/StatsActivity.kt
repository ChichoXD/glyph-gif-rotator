package dev.glyphrotator.app.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dev.glyphrotator.app.R
import dev.glyphrotator.app.glyph.DotIcons
import dev.glyphrotator.app.habits.HabitRules
import dev.glyphrotator.app.habits.HabitStore
import dev.glyphrotator.app.habits.WaterStore
import dev.glyphrotator.app.pokemon.spawn.SleepTracker

/**
 * El registro, una página por cosa y deslizando de lado.
 *
 * Antes salían todos los calendarios apilados en una columna larga. Con cinco hábitos eso son
 * siete calendarios seguidos, y ninguno se mira: se convierten en textura. Uno por página deja
 * dar a cada uno el ancho entero, que es lo que hace que los puntos se vean de verdad.
 *
 * Se construye a mano porque el número de páginas depende de cuántos hábitos tengas, y eso no se
 * sabe hasta abrir la pantalla.
 */
class StatsActivity : AppCompatActivity() {

    // Medidas desde dimens.xml. Antes eran píxeles crudos: solo salían del tamaño correcto en
    // la densidad del Phone (3).
    private val iconPx by lazy { dimen(R.dimen.dot_icon_stat) }
    private val tabIconPx by lazy { dimen(R.dimen.dot_icon_small) }
    private val paddingPx by lazy { dimen(R.dimen.screen_padding) }
    private val gapPx by lazy { dimen(R.dimen.space_s) }

    private data class Page(
        val tab: String,
        val icon: DotIcons.Icon,
        val view: android.view.View,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val water = WaterStore(this)
        val habits = HabitStore(this)
        val sleep = SleepTracker(this)
        val today = HabitRules.today()

        val pages = buildList {
            add(
                Page(
                    tab = getString(R.string.stats_water_header),
                    icon = DotIcons.Icon.DROP,
                    view = page(
                        icon = DotIcons.Icon.DROP,
                        name = getString(R.string.stats_water_header),
                        summary = getString(
                            R.string.stats_water_summary,
                            water.todayMl,
                            water.goalMl,
                            water.streak,
                            water.bestStreak,
                            water.daysMetIn(WEEK),
                            water.averageMl(MONTH),
                        ),
                        intensities = water.historyMl(HISTORY_DAYS).map {
                            if (water.goalMl <= 0) 0f else it.toFloat() / water.goalMl
                        },
                    )
                )
            )

            for (habit in habits.all()) {
                add(
                    Page(
                        tab = habit.name.take(TAB_CHARS),
                        icon = DotIcons.guess(habit.name),
                        view = page(
                            icon = DotIcons.guess(habit.name),
                            name = habit.name,
                            summary = if (habit.isDaily) {
                                getString(R.string.stats_habit_daily, habit.name, habit.streak())
                            } else {
                                getString(
                                    R.string.stats_habit_weekly,
                                    habit.name,
                                    habit.timesPerWeek,
                                    habit.doneThisWeek(),
                                    habit.streak()
                                )
                            },
                            // Cumplido o no: aquí no hay medias tintas que representar.
                            intensities = (HISTORY_DAYS - 1 downTo 0).map { offset ->
                                if ((today - offset) in habit.history) 1f else 0f
                            },
                        )
                    )
                )
            }

            add(
                Page(
                    tab = getString(R.string.stats_sleep_header),
                    icon = DotIcons.Icon.SLEEP_ZZZ,
                    view = page(
                        icon = DotIcons.Icon.SLEEP_ZZZ,
                        name = getString(R.string.stats_sleep_header),
                        summary = if (sleep.nightsRecorded == 0) {
                            getString(R.string.stats_sleep_empty)
                        } else {
                            getString(
                                R.string.stats_sleep_summary,
                                sleep.lastNightMinutes / 60,
                                sleep.lastNightMinutes % 60,
                                (sleep.currentQuality * 100).toInt(),
                                sleep.nightsRecorded,
                                (sleep.averageQuality * 100).toInt(),
                                (sleep.bestQuality * 100).toInt(),
                            )
                        },
                        // Solo hay dato de anoche, así que el calendario del sueño se queda para
                        // cuando haya historial: pintar una fila de ceros engañaría.
                        intensities = emptyList(),
                    )
                )
            )
        }

        setContentView(buildRoot(pages))
    }

    private fun buildRoot(pages: List<Page>): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.glyph_black))
            // Igual que en Logros: se monta a mano, así que hay que pedir el hueco de la barra
            // de estado a mano también.
            fitsSystemWindows = true
        }

        // Cabecera en tres niveles, la misma que Pokémon y Logros: entradilla, título grande y
        // leyenda en gris. Aquí el título iba a 22sp muy espaciado, con el mismo peso que las
        // pestañas de debajo.
        root.addView(
            TextView(this).apply {
                text = getString(R.string.stats_eyebrow)
                textSize = 11f
                isAllCaps = true
                letterSpacing = 0.25f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(paddingPx, paddingPx, paddingPx, 0)
            }
        )
        root.addView(
            DotTextView(this).apply {
                text = getString(R.string.stats_title)
                cellSizeDp = 7f
                setPadding(paddingPx, gapPx, paddingPx, 0)
            }
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.stats_legend)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setLineSpacing(0f, 1.5f)
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(paddingPx, gapPx + gapPx / 2, paddingPx, gapPx * 2)
            }
        )

        val tabs = TabLayout(this).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            setSelectedTabIndicatorColor(getColor(R.color.glyph_white))
            setTabTextColors(getColor(R.color.glyph_grey), getColor(R.color.glyph_white))
            // Que la primera pestaña no quede cortada contra el borde.
            clipToPadding = false
            setPadding(paddingPx - gapPx, 0, paddingPx - gapPx, 0)
        }
        val pager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
            adapter = StaticPagerAdapter(pages.map { it.view })
            offscreenPageLimit = 1
        }

        root.addView(tabs)
        root.addView(pager)
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = pages[position].tab
            // El icono también en la pestaña, del mismo juego de puntos que la página.
            tab.icon = android.graphics.drawable.BitmapDrawable(
                resources,
                MinimalIcons.bitmap(this@StatsActivity, pages[position].icon, tabIconPx)
            )
        }.attach()
        return root
    }

    /**
     * Una página: el icono grande, el nombre, el resumen y el calendario a todo lo ancho.
     *
     * El icono es de los de puntos y no un emoji del sistema. Un emoji al lado de un calendario
     * de puntos canta: son dos lenguajes distintos en la misma pantalla, y el de puntos es el de
     * la app entera y el de la propia Matrix.
     */
    private fun page(
        icon: DotIcons.Icon,
        name: String,
        summary: String,
        intensities: List<Float>,
    ): android.view.View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        column.addView(
            android.widget.ImageView(this).apply {
                setImageResource(MinimalIcons.resFor(icon))
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
            }
        )
        column.addView(
            TextView(this).apply {
                text = name
                textSize = 18f
                setTextColor(getColor(R.color.glyph_white))
                setPadding(0, gapPx / 2, 0, 0)
            }
        )
        column.addView(
            TextView(this).apply {
                text = summary
                textSize = 13f
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, gapPx / 2, 0, gapPx)
            }
        )

        if (intensities.isEmpty()) {
            column.addView(
                TextView(this).apply {
                    text = getString(R.string.stats_no_history)
                    textSize = 13f
                    setTextColor(getColor(R.color.glyph_grey))
                }
            )
        } else {
            column.addView(
                DotCalendarView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setData(intensities, DotCalendarView.todayWeekday())
                }
            )
        }
        return column
    }

    private companion object {
        /** Nueve semanas: con una página por cosa, los puntos salen grandes. */
        const val HISTORY_DAYS = 63
        const val WEEK = 7
        const val MONTH = 30
        const val TAB_CHARS = 10

    }
}
