package dev.glyphrotator.app.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.glyphrotator.app.R
import dev.glyphrotator.app.achievements.AchievementCatalog
import dev.glyphrotator.app.achievements.AchievementStore
import dev.glyphrotator.app.glyph.DotIcons

/**
 * La lista de logros, partida en conseguidos y pendientes, cada grupo con su etiqueta.
 *
 * Los pendientes se enseñan **con la pista y la barra**, no ocultos. Un logro secreto no motiva
 * nada: lo que hace volver es ver que te faltan tres capturas para el siguiente.
 *
 * Los conseguidos van primero porque es lo que uno abre a mirar; y aun así se ven todos, para
 * que se note cuánto queda por delante. El corte entre los dos grupos era antes implícito —una
 * lista corrida ordenada por conseguidos— y había que adivinarlo contando iconos apagados.
 *
 * Se monta a mano y no en XML porque son 37 filas iguales. Las medidas salen de dimens.xml y el
 * lenguaje visual, de DISENO.md.
 */
class AchievementsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = AchievementStore(this)
        val snapshot = store.snapshot()
        // Se comprueba al abrir además de en segundo plano: así, si algo se desbloqueó mientras
        // el servicio estaba muerto, aparece igual al mirar la lista.
        store.checkNewlyUnlocked(snapshot)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx * 2)
        }

        val unlocked = AchievementCatalog.all.count { it.isUnlocked(snapshot) }

        // Cabecera en tres niveles, la misma que la pantalla de Pokémon: entradilla, título
        // grande y cuenta en gris. Antes el título iba a 22sp y muy espaciado, con lo que se
        // leía como una etiqueta más de las que tiene debajo en vez de como el nombre.
        root.addView(sectionLabel(getString(R.string.achievements_eyebrow), 0))
        root.addView(
            DotTextView(this).apply {
                text = getString(R.string.achievements_title)
                cellSizeDp = 7f
                setPadding(0, gapPx, 0, 0)
            }
        )
        root.addView(
            TextView(this).apply {
                text = getString(
                    R.string.achievements_progress,
                    unlocked,
                    AchievementCatalog.all.size
                )
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, gapPx + gapPx / 2, 0, 0)
            }
        )

        // Dos grupos con su etiqueta, en vez de una lista corrida ordenada por conseguidos. El
        // corte ya estaba, pero había que adivinarlo contando iconos apagados. Dentro de cada
        // grupo manda el orden del catálogo, que va de más fácil a más difícil.
        val done = AchievementCatalog.all.filter { it.isUnlocked(snapshot) }
        val pending = AchievementCatalog.all.filterNot { it.isUnlocked(snapshot) }

        if (done.isNotEmpty()) {
            root.addView(sectionLabel(getString(R.string.achievements_group_done), gapPx * 3))
            for (achievement in done) root.addView(row(achievement, snapshot, store))
        }
        if (pending.isNotEmpty()) {
            root.addView(sectionLabel(getString(R.string.achievements_group_pending), gapPx * 3))
            for (achievement in pending) root.addView(row(achievement, snapshot, store))
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.glyph_black))
                // Sin esto la entradilla se metía debajo de la barra de estado: esta pantalla
                // se monta a mano y no hereda el fitsSystemWindows que sí tienen las de XML.
                fitsSystemWindows = true
                clipToPadding = false
                addView(root)
            }
        )
    }

    /**
     * La ventana de un logro: qué hay que hacer, cuánto llevas y cuándo lo conseguiste.
     *
     * En una ventana de pixel art y no en el diálogo de serie de Android porque este es el único
     * sitio de la app donde el contenido **es** el juego. Un cuadro gris de Material al lado de
     * un icono de puntos rompe la ilusión; el borde doble con esquinas rectas la sostiene.
     *
     * Se enseña la pista también en los ya conseguidos: sirve para acordarse de qué hiciste.
     */
    private fun showDetail(
        achievement: dev.glyphrotator.app.achievements.Achievement,
        snapshot: dev.glyphrotator.app.achievements.GameSnapshot,
        store: AchievementStore,
    ) {
        val done = achievement.isUnlocked(snapshot)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.pixel_window)
            setPadding(detailPaddingPx, detailPaddingPx, detailPaddingPx, detailPaddingPx)
        }

        content.addView(
            ImageView(this).apply {
                setImageResource(MinimalIcons.resFor(achievement.icon))
                imageTintList = android.content.res.ColorStateList.valueOf(
                    MinimalIcons.tintFor(
                        this@AchievementsActivity,
                        if (done) MinimalIcons.Variant.DEFAULT else MinimalIcons.Variant.DISABLED
                    )
                )
                layoutParams = LinearLayout.LayoutParams(detailIconPx, detailIconPx)
                    .apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
                alpha = if (done) 1f else LOCKED_ALPHA
            }
        )
        content.addView(
            TextView(this).apply {
                text = getString(achievement.nameRes)
                textSize = 18f
                typeface = android.graphics.Typeface.MONOSPACE
                letterSpacing = 0.06f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.glyph_white))
                setPadding(0, gapPx, 0, 0)
            }
        )
        content.addView(
            TextView(this).apply {
                text = getString(achievement.hintRes)
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, gapPx, 0, gapPx)
            }
        )

        // La barra separadora, del mismo grosor que el borde: no es un adorno, marca dónde
        // termina "qué hay que hacer" y empieza "cómo vas".
        content.addView(
            android.view.View(this).apply {
                setBackgroundColor(getColor(R.color.glyph_divider))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dividerPx
                )
            }
        )

        content.addView(
            TextView(this).apply {
                text = if (done) {
                    val day = store.unlockedOn(achievement.id)
                    if (day != null) {
                        getString(
                            R.string.achievement_unlocked_on,
                            java.time.LocalDate.ofEpochDay(day.toLong())
                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
                        )
                    } else {
                        getString(R.string.achievements_done)
                    }
                } else {
                    getString(
                        R.string.achievement_progress_detail,
                        achievement.progress(snapshot),
                        achievement.target,
                        (achievement.fraction(snapshot) * 100).toInt()
                    )
                }
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(getColor(if (done) R.color.glyph_white else R.color.glyph_grey))
                setPadding(0, gapPx, 0, 0)
            }
        )

        if (!done) {
            content.addView(
                SegmentBarView(this).apply {
                    fraction = achievement.fraction(snapshot)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = gapPx }
                }
            )
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(content)
            .create()
            .apply {
                // Sin el fondo del diálogo de serie: si no, se ve su recuadro gris asomando por
                // detrás del marco de píxeles y queda una ventana dentro de otra.
                window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
                show()
            }
    }

    /**
     * Etiqueta de sección: mayúsculas, monoespaciada y muy espaciada, con su hilo debajo.
     *
     * Es la misma que usan las pantallas de XML (`Nothing.SectionLabel`), reconstruida aquí
     * porque esta se monta a mano. El hilo no es adorno: marca dónde empieza cada bloque sin
     * gastar una línea de texto en decirlo.
     */
    private fun sectionLabel(text: String, topPadding: Int): ViewGroup =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, topPadding, 0, 0)
            addView(
                TextView(this@AchievementsActivity).apply {
                    this.text = text
                    textSize = 11f
                    isAllCaps = true
                    letterSpacing = 0.25f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(getColor(R.color.glyph_grey))
                }
            )
            addView(
                android.view.View(this@AchievementsActivity).apply {
                    setBackgroundColor(getColor(R.color.glyph_divider))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dividerPx
                    ).apply { topMargin = gapPx }
                }
            )
        }

    private fun row(
        achievement: dev.glyphrotator.app.achievements.Achievement,
        snapshot: dev.glyphrotator.app.achievements.GameSnapshot,
        store: AchievementStore,
    ): ViewGroup {
        val done = achievement.isUnlocked(snapshot)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, gapPx / 2, 0, gapPx / 2)
        }

        row.addView(
            ImageView(this).apply {
                setImageResource(MinimalIcons.resFor(achievement.icon))
                imageTintList = android.content.res.ColorStateList.valueOf(
                    MinimalIcons.tintFor(
                        this@AchievementsActivity,
                        if (done) MinimalIcons.Variant.DEFAULT else MinimalIcons.Variant.DISABLED
                    )
                )
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx).apply {
                    rightMargin = gapPx
                }
                // Los pendientes en gris: se ven, pero se nota de un vistazo cuáles faltan.
                alpha = if (done) 1f else LOCKED_ALPHA
            }
        )

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2).apply { weight = 1f }
        }
        column.addView(
            TextView(this).apply {
                text = getString(achievement.nameRes)
                textSize = 15f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(getColor(if (done) R.color.glyph_white else R.color.glyph_grey))
            }
        )
        column.addView(
            TextView(this).apply {
                text = if (done) {
                    getString(R.string.achievements_done)
                } else {
                    getString(
                        R.string.achievements_pending,
                        getString(achievement.hintRes),
                        achievement.progress(snapshot),
                        achievement.target
                    )
                }
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(getColor(R.color.glyph_grey))
            }
        )
        if (!done) {
            // Segmentos, no barra continua: el mismo idioma que la Matrix de atrás, y se lee
            // cuánto falta sin tener que estimar una longitud.
            column.addView(
                SegmentBarView(this).apply {
                    fraction = achievement.fraction(snapshot)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = gapPx / 2 }
                }
            )
        }
        row.addView(column)
        row.setOnClickListener { showDetail(achievement, snapshot, store) }
        return row
    }

    // Medidas, sacadas de dimens.xml en vez de escritas a pelo. Eran píxeles crudos, que solo
    // salían del tamaño correcto en la densidad del Phone (3).
    private val iconPx by lazy { dimen(R.dimen.dot_icon_row) }
    private val paddingPx by lazy { dimen(R.dimen.screen_padding) }
    private val gapPx by lazy { dimen(R.dimen.space_s) }
    private val barHeightPx by lazy { dimen(R.dimen.progress_bar_height) }
    private val detailPaddingPx by lazy { dimen(R.dimen.screen_padding) }
    private val detailIconPx by lazy { dimen(R.dimen.dot_icon_detail) }
    private val dividerPx by lazy { dimen(R.dimen.hairline) }

    private companion object {
        const val LOCKED_ALPHA = 0.28f
    }
}
