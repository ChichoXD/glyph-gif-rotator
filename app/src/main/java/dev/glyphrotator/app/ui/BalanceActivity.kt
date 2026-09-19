package dev.glyphrotator.app.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import dev.glyphrotator.app.R
import dev.glyphrotator.app.habits.DailyBonus
import dev.glyphrotator.app.pokemon.DemoMode
import dev.glyphrotator.app.pokemon.DemoTuning
import dev.glyphrotator.app.pokemon.EggRules
import dev.glyphrotator.app.pokemon.LevelCalculator
import dev.glyphrotator.app.pokemon.TrainingRules
import dev.glyphrotator.app.pokemon.spawn.SpawnChance
import dev.glyphrotator.app.service.GlyphRotationService

/**
 * El cuadro de mandos del equilibrio: qué vale cada cosa, en normal y en demo.
 *
 * Existe porque el ritmo normal es imposible de valorar jugando. Un huevo son diez capturas y
 * tres horas; un nivel, cien minutos. Para decidir si los multiplicadores de agua, sueño y
 * hábitos premian lo justo harían falta semanas de apuntar resultados en un papel.
 *
 * Aquí están los dos ritmos uno al lado del otro, con una línea que explica **qué mueve cada
 * número**. Con eso se puede mirar una tarde de demo y traducirla a "esto en normal sería una
 * semana", que es lo que hace falta para decidir si algo está fácil o difícil.
 *
 * Los multiplicadores de la vida real aparecen pero **no cambian en demo**, a propósito: lo que
 * se acelera es cada cuánto pasan las cosas, no cuánto premian. Si también se inflaran, no se
 * estaría probando este juego sino otro.
 */
class BalanceActivity : AppCompatActivity() {

    private val paddingPx by lazy { dimen(R.dimen.screen_padding) }
    private val gapPx by lazy { dimen(R.dimen.space_s) }

    private data class Fila(
        val concepto: String,
        val normal: String,
        val demo: String,
        /** Qué mueve este número dentro del juego. */
        val explicacion: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val demo = DemoMode(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx * 3)
        }

        root.addView(etiqueta(getString(R.string.balance_eyebrow), 0))
        root.addView(
            DotTextView(this).apply {
                text = getString(R.string.balance_title)
                cellSizeDp = 7f
                setPadding(0, gapPx, 0, 0)
            }
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.balance_intro)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setLineSpacing(0f, 1.5f)
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, gapPx * 2, 0, gapPx * 2)
            }
        )

        // El interruptor. Al cambiarlo hay que avisar al servicio: la comprobación de aparición
        // lee el valor en memoria, no la preferencia, para no ir a disco cada minuto.
        root.addView(
            MaterialSwitch(this).apply {
                text = getString(R.string.balance_demo_switch)
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 14f
                minHeight = dimen(R.dimen.space_xxl) * 2
                setTextColor(getColor(R.color.glyph_white))
                isChecked = demo.isEnabled
                setOnCheckedChangeListener { _, checked ->
                    demo.isEnabled = checked
                    DemoTuning.refresh(this@BalanceActivity)
                    GlyphRotationService.refresh(this@BalanceActivity)
                    recreate()
                }
            }
        )

        root.addView(
            TextView(this).apply {
                text = getString(
                    if (demo.isEnabled) R.string.balance_state_demo else R.string.balance_state_normal
                )
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                letterSpacing = 0.12f
                isAllCaps = true
                setTextColor(
                    getColor(if (demo.isEnabled) R.color.glyph_red else R.color.glyph_grey)
                )
                setPadding(0, gapPx, 0, gapPx * 3)
            }
        )

        for ((titulo, filas) in bloques()) {
            root.addView(etiqueta(titulo, gapPx * 2))
            for (fila in filas) root.addView(fila(fila))
        }

        // Sin esto, el interruptor pide el foco al colocarse y el scroll salta hasta él: la
        // pantalla se abría ya empezada, con el título metido debajo de la barra de estado.
        root.isFocusableInTouchMode = true

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.glyph_black))
                fitsSystemWindows = true
                addView(root)
            }
        )
    }

    /**
     * Las filas, agrupadas por lo que gobiernan.
     *
     * Los porcentajes de aparición se sacan de [SpawnChance] de verdad, no escritos a mano: si
     * mañana se toca la tabla de probabilidades, esta pantalla se entera sola. Una pantalla de
     * equilibrio que miente es peor que no tenerla.
     */
    private fun bloques(): List<Pair<String, List<Fila>>> {
        val m = DemoMode.SPAWN_MULTIPLIER
        fun pct(p: Double) = "%.1f %%".format(p * 100)
        fun pctDemo(p: Double) = "%.1f %%".format((p * m).coerceAtMost(1.0) * 100)

        return listOf(
            getString(R.string.balance_group_spawn) to listOf(
                Fila(
                    getString(R.string.balance_first_catch),
                    pct(SpawnChance.FIRST_CATCH_CHANCE),
                    pctDemo(SpawnChance.FIRST_CATCH_CHANCE),
                    getString(R.string.balance_first_catch_why),
                ),
                Fila(
                    getString(R.string.balance_after_15),
                    pct(0.04), pctDemo(0.04),
                    getString(R.string.balance_after_15_why),
                ),
                Fila(
                    getString(R.string.balance_after_30),
                    pct(0.08), pctDemo(0.08),
                    getString(R.string.balance_after_30_why),
                ),
                Fila(
                    getString(R.string.balance_after_45),
                    pct(0.20), pctDemo(0.20),
                    getString(R.string.balance_after_45_why),
                ),
                Fila(
                    getString(R.string.balance_after_60),
                    pct(1.0), pctDemo(1.0),
                    getString(R.string.balance_after_60_why),
                ),
                Fila(
                    getString(R.string.balance_bedtime),
                    pct(SpawnChance.BEDTIME_MAX_CHANCE),
                    pct(SpawnChance.BEDTIME_MAX_CHANCE * DemoMode.BEDTIME_MULTIPLIER),
                    getString(R.string.balance_bedtime_why),
                ),
            ),

            getString(R.string.balance_group_training) to listOf(
                Fila(
                    getString(R.string.balance_exp_minute),
                    "${TrainingRules.EXP_PER_MINUTE} EXP",
                    "${TrainingRules.EXP_PER_MINUTE * DemoMode.EXP_MULTIPLIER} EXP",
                    getString(R.string.balance_exp_minute_why),
                ),
                Fila(
                    getString(R.string.balance_level_time),
                    minutos(LevelCalculator.EXP_PER_LEVEL / TrainingRules.EXP_PER_MINUTE),
                    minutos(
                        LevelCalculator.EXP_PER_LEVEL /
                            (TrainingRules.EXP_PER_MINUTE * DemoMode.EXP_MULTIPLIER)
                    ),
                    getString(R.string.balance_level_time_why),
                ),
                Fila(
                    getString(R.string.balance_bonus),
                    getString(
                        R.string.balance_bonus_value,
                        TrainingRules.BONUS_AMOUNT,
                        TrainingRules.BONUS_INTERVAL_MINUTES
                    ),
                    getString(
                        R.string.balance_bonus_value,
                        TrainingRules.BONUS_AMOUNT,
                        TrainingRules.BONUS_INTERVAL_MINUTES
                    ),
                    getString(R.string.balance_bonus_why),
                ),
            ),

            getString(R.string.balance_group_eggs) to listOf(
                Fila(
                    getString(R.string.balance_captures_egg),
                    "${EggRules.CAPTURES_PER_EGG}",
                    "${DemoMode.CAPTURES_PER_EGG}",
                    getString(R.string.balance_captures_egg_why),
                ),
                Fila(
                    getString(R.string.balance_incubation),
                    minutos(EggRules.INCUBATION_MINUTES),
                    minutos(DemoMode.INCUBATION_MINUTES),
                    getString(R.string.balance_incubation_why),
                ),
            ),

            getString(R.string.balance_group_life) to listOf(
                Fila(
                    getString(R.string.balance_sleep),
                    rango(DailyBonus.sleepFactor(0f), DailyBonus.sleepFactor(1f)),
                    getString(R.string.balance_same),
                    getString(R.string.balance_sleep_why),
                ),
                Fila(
                    getString(R.string.balance_water),
                    rango(DailyBonus.waterFactor(false), DailyBonus.waterFactor(true)),
                    getString(R.string.balance_same),
                    getString(R.string.balance_water_why),
                ),
                Fila(
                    getString(R.string.balance_habits),
                    rango(DailyBonus.habitFactor(0, 3), DailyBonus.habitFactor(3, 3)),
                    getString(R.string.balance_same),
                    getString(R.string.balance_habits_why),
                ),
                Fila(
                    getString(R.string.balance_streak),
                    rango(DailyBonus.streakFactor(0), DailyBonus.streakFactor(30)),
                    getString(R.string.balance_same),
                    getString(R.string.balance_streak_why),
                ),
                Fila(
                    getString(R.string.balance_total),
                    rango(
                        DailyBonus.sleepFactor(0f) * DailyBonus.waterFactor(false) *
                            DailyBonus.habitFactor(0, 3) * DailyBonus.streakFactor(0),
                        DailyBonus.sleepFactor(1f) * DailyBonus.waterFactor(true) *
                            DailyBonus.habitFactor(3, 3) * DailyBonus.streakFactor(30),
                    ),
                    getString(R.string.balance_same),
                    getString(R.string.balance_total_why),
                ),
            ),
        )
    }

    private fun minutos(total: Int): String = when {
        total >= 60 -> getString(R.string.balance_hours, total / 60, total % 60)
        else -> getString(R.string.balance_minutes, total)
    }

    private fun rango(min: Float, max: Float): String = "×%.2f – ×%.2f".format(min, max)

    private fun etiqueta(texto: String, arriba: Int): ViewGroup =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, arriba, 0, 0)
            addView(
                TextView(this@BalanceActivity).apply {
                    text = texto
                    textSize = 11f
                    isAllCaps = true
                    letterSpacing = 0.25f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(getColor(R.color.glyph_grey))
                }
            )
            addView(
                android.view.View(this@BalanceActivity).apply {
                    setBackgroundColor(getColor(R.color.glyph_divider))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dimen(R.dimen.hairline)
                    ).apply { topMargin = gapPx }
                }
            )
        }

    /**
     * Una fila: concepto arriba, los dos valores enfrentados y la explicación debajo en gris.
     *
     * El valor de demo va en rojo cuando cambia respecto al normal, y en gris cuando es el
     * mismo. Así se ve de un vistazo qué acelera el modo demo y qué deja igual, sin tener que
     * comparar los números uno a uno.
     */
    private fun fila(datos: Fila): ViewGroup {
        val cambia = datos.normal != datos.demo

        val bloque = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, gapPx * 2, 0, gapPx)
        }

        bloque.addView(
            TextView(this).apply {
                text = datos.concepto
                textSize = 14f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(getColor(R.color.glyph_white))
            }
        )

        val valores = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, gapPx, 0, 0)
        }
        valores.addView(valor(getString(R.string.balance_normal), datos.normal, false))
        valores.addView(valor(getString(R.string.balance_demo), datos.demo, cambia))
        bloque.addView(valores)

        bloque.addView(
            TextView(this).apply {
                text = datos.explicacion
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setLineSpacing(0f, 1.5f)
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, gapPx, 0, 0)
            }
        )
        return bloque
    }

    private fun valor(etiqueta: String, texto: String, destacado: Boolean): ViewGroup =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { weight = 1f }
            addView(
                TextView(this@BalanceActivity).apply {
                    text = etiqueta
                    textSize = 10f
                    isAllCaps = true
                    letterSpacing = 0.2f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(getColor(R.color.glyph_grey))
                }
            )
            addView(
                TextView(this@BalanceActivity).apply {
                    text = texto
                    textSize = 16f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(
                        getColor(if (destacado) R.color.glyph_red else R.color.glyph_white)
                    )
                }
            )
        }
}
