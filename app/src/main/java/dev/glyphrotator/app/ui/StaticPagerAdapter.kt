package dev.glyphrotator.app.ui

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Un carrusel de páginas **ya construidas**, en vez de creadas al vuelo.
 *
 * Lo normal en un ViewPager2 es que cada página sea un fragmento que se crea y se destruye. Aquí
 * no interesa: las páginas son las secciones de siempre —los interruptores, los umbrales, la
 * lista de diseños— y todo el código que las maneja las busca una sola vez al arrancar. Si se
 * destruyeran al deslizar, esas referencias apuntarían a vistas muertas y medio ajuste dejaría
 * de responder.
 *
 * Así que las páginas se inflan con la pantalla, se sacan de su contenedor y se entregan aquí ya
 * hechas. Son cinco y viven lo que vive la pantalla, de modo que no hay nada que reciclar.
 */
class StaticPagerAdapter(private val pages: List<View>) :
    RecyclerView.Adapter<StaticPagerAdapter.PageHolder>() {

    class PageHolder(view: View) : RecyclerView.ViewHolder(view)

    /**
     * Cada posición es su propio tipo.
     *
     * Es lo que impide que RecyclerView dé por buena la página de otra posición: con un tipo
     * común reutilizaría la primera vista para todas y se verían cinco pestañas idénticas.
     */
    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val page = pages[viewType]
        // ViewPager2 exige que la página ocupe todo el hueco; si no, revienta al medirla.
        page.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return PageHolder(page).apply { setIsRecyclable(false) }
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) = Unit

    override fun getItemCount(): Int = pages.size
}
