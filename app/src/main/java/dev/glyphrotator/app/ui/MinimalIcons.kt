package dev.glyphrotator.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import dev.glyphrotator.app.R
import dev.glyphrotator.app.glyph.DotIcons

/**
 * La familia de iconos de la app: líneas finas, geometría simple, un solo color.
 *
 * **Por qué se dejan los de puntos.** Los iconos de matriz eran coherentes con la Matrix, pero
 * puestos por toda la interfaz la convertían en un aparato de los noventa. Ahora la regla es
 * otra: los iconos son símbolos técnicos y quietos, y **la matriz de puntos queda reservada
 * para el Glyph** — los títulos de pantalla y lo que se pinta en la pantalla trasera. Un icono
 * informa; el Glyph reacciona.
 *
 * **Por qué dibujados aquí y no descargados.** Cualquier paquete de iconos trae su licencia que
 * revisar, y ninguno viene con este lenguaje concreto. Dibujarlos como vectores dentro del
 * proyecto sale más barato de mantener, garantiza que todos compartan grosor y lienzo, y evita
 * el problema de derechos por completo.
 *
 * [DotIcons.Icon] se conserva como **vocabulario**: es lo que ya usan los logros, los hábitos y
 * la decoración de los widgets para decir "esto es agua" o "esto es sueño". Lo que cambia es
 * cómo se dibuja ese concepto, no cómo se nombra.
 */
object MinimalIcons {

    /** El dibujo que le toca a cada concepto. */
    @DrawableRes
    fun resFor(icon: DotIcons.Icon): Int = when (icon) {
        DotIcons.Icon.DUMBBELL -> R.drawable.ic_min_training
        DotIcons.Icon.CIGARETTE -> R.drawable.ic_min_cigarette
        DotIcons.Icon.DROP -> R.drawable.ic_min_water
        DotIcons.Icon.MOON -> R.drawable.ic_min_sleep
        DotIcons.Icon.HEART -> R.drawable.ic_min_habits
        DotIcons.Icon.FLAME -> R.drawable.ic_min_flame
        DotIcons.Icon.STAR -> R.drawable.ic_min_achievement
        DotIcons.Icon.BOOK -> R.drawable.ic_min_stats
        DotIcons.Icon.CLOCK -> R.drawable.ic_min_clock
        DotIcons.Icon.POKEBALL -> R.drawable.ic_min_pokeball
        DotIcons.Icon.EGG -> R.drawable.ic_min_egg
        DotIcons.Icon.RARE_CANDY -> R.drawable.ic_min_item
        DotIcons.Icon.SLEEP_ZZZ -> R.drawable.ic_min_sleep
        DotIcons.Icon.POKEDEX -> R.drawable.ic_min_pokedex
        DotIcons.Icon.LEAF -> R.drawable.ic_min_leaf
    }

    /**
     * Cómo se ve un icono según su estado.
     *
     * Son **variantes del mismo símbolo**, no cinco dibujos distintos: cambia el color y la
     * opacidad, nunca la forma. Así el icono se reconoce igual esté como esté.
     */
    enum class Variant { DEFAULT, ACTIVE, DISABLED }

    @ColorInt
    fun tintFor(context: Context, variant: Variant): Int = when (variant) {
        Variant.DEFAULT -> ContextCompat.getColor(context, R.color.glyph_white)
        // El rojo solo aquí, y solo cuando algo está pasando de verdad.
        Variant.ACTIVE -> ContextCompat.getColor(context, R.color.glyph_red)
        Variant.DISABLED -> ContextCompat.getColor(context, R.color.glyph_grey)
    }

    fun alphaFor(variant: Variant): Float = if (variant == Variant.DISABLED) 0.4f else 1f

    /**
     * El icono como bitmap, para los widgets.
     *
     * `RemoteViews` no puede teñir un vector por su cuenta, así que se dibuja aquí ya del color
     * que toca y se manda hecho. En la app no hace falta: allí basta con un `ImageView`.
     */
    fun bitmap(
        context: Context,
        icon: DotIcons.Icon,
        sizePx: Int,
        variant: Variant = Variant.DEFAULT,
    ): Bitmap {
        val drawable = ContextCompat.getDrawable(context, resFor(icon))!!.mutate()
        DrawableCompat.setTint(drawable, tintFor(context, variant))
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
