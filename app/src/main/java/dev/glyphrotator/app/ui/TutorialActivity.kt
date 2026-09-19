package dev.glyphrotator.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import dev.glyphrotator.app.R

/**
 * El tutorial: una idea por página, se pasa deslizando.
 *
 * Una sola pantalla larga con todo escrito no se lee. Partido en páginas, cada una con un
 * símbolo grande, un título y tres líneas, se lee entero casi sin querer — y el que ya sabe
 * cómo va le da a saltar y se acabó.
 *
 * El armazón vale para los dos tutoriales; el contenido está en [Tutorial].
 */
class TutorialActivity : AppCompatActivity() {

    private val paddingPx by lazy { dimen(R.dimen.screen_padding) }
    private val gapPx by lazy { dimen(R.dimen.space_s) }

    private lateinit var pages: List<Tutorial.Page>
    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var next: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val which = runCatching {
            Tutorial.Which.valueOf(intent.getStringExtra(EXTRA_WHICH).orEmpty())
        }.getOrDefault(Tutorial.Which.ROTATION)
        pages = Tutorial.pagesFor(which)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.glyph_black))
            fitsSystemWindows = true
        }

        root.addView(
            TextView(this).apply {
                text = getString(R.string.tutorial_eyebrow)
                textSize = 11f
                isAllCaps = true
                letterSpacing = 0.25f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(paddingPx, paddingPx, paddingPx, 0)
            }
        )

        pager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
            adapter = StaticPagerAdapter(pages.map { page(it) })
        }
        root.addView(pager)

        dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(paddingPx, 0, paddingPx, gapPx * 2)
        }
        root.addView(dots)

        val botones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(paddingPx, 0, paddingPx, paddingPx * 2)
        }
        val skip = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.tutorial_skip)
            // Saltar salía en rojo porque el estilo de Material tira de colorPrimary. Aquí el
            // rojo significa "esto es lo que hay que tocar", y saltar es justo lo contrario.
            setTextColor(getColor(R.color.glyph_grey))
            strokeColor = android.content.res.ColorStateList.valueOf(
                getColor(R.color.glyph_divider)
            )
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { weight = 1f; marginEnd = gapPx }
            setOnClickListener { finish() }
        }
        next = MaterialButton(this).apply {
            text = getString(R.string.tutorial_next)
            setTextColor(getColor(R.color.glyph_white))
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { weight = 1f; marginStart = gapPx }
            setOnClickListener {
                if (pager.currentItem >= pages.lastIndex) finish()
                else pager.currentItem = pager.currentItem + 1
            }
        }
        botones.addView(skip)
        botones.addView(next)
        root.addView(botones)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = refreshDots(position)
        })
        refreshDots(0)

        setContentView(root)
    }

    /**
     * Los puntos de abajo: encendido el de la página en la que estás.
     *
     * Puntos y no una barra de progreso porque es el idioma de la casa, y porque con cinco o
     * seis páginas se cuentan de un vistazo: se ve cuánto queda sin leer un número.
     */
    private fun refreshDots(current: Int) {
        dots.removeAllViews()
        val lado = dimen(R.dimen.space_m)
        val largo = dimen(R.dimen.space_xxl)
        for (i in pages.indices) {
            val actual = i == current
            dots.addView(
                android.view.View(this).apply {
                    setBackgroundResource(R.drawable.tutorial_dot)
                    isActivated = actual
                    // El de la página actual se estira. Un punto un poco más claro que los
                    // demás no se ve a un metro; un segmento sí, y además dice hacia dónde vas.
                    layoutParams = LinearLayout.LayoutParams(
                        if (actual) largo else lado,
                        lado
                    ).apply {
                        marginStart = lado / 2
                        marginEnd = lado / 2
                    }
                }
            )
        }
        next.text = getString(
            if (current >= pages.lastIndex) R.string.tutorial_done else R.string.tutorial_next
        )
    }

    private fun page(datos: Tutorial.Page): android.view.View {
        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        columna.addView(
            ImageView(this).apply {
                setImageResource(datos.icon)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    getColor(R.color.glyph_white)
                )
                layoutParams = LinearLayout.LayoutParams(ICON_DP.dp(), ICON_DP.dp())
            }
        )
        columna.addView(
            DotTextView(this).apply {
                text = getString(datos.title)
                cellSizeDp = 6f
                setPadding(0, gapPx * 4, 0, 0)
            }
        )
        columna.addView(
            TextView(this).apply {
                text = getString(datos.body)
                textSize = 14f
                typeface = android.graphics.Typeface.MONOSPACE
                setLineSpacing(0f, 1.6f)
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, gapPx * 3, 0, 0)
            }
        )
        return columna
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_WHICH = "which"
        private const val ICON_DP = 56

        fun open(context: Context, which: Tutorial.Which) {
            context.startActivity(
                Intent(context, TutorialActivity::class.java)
                    .putExtra(EXTRA_WHICH, which.name)
            )
        }

        /** Se abre solo la primera vez que se entra en esa pantalla. */
        fun openIfFirstRun(context: Context, which: Tutorial.Which) {
            if (Tutorial.claimFirstRun(context, which)) open(context, which)
        }
    }
}
