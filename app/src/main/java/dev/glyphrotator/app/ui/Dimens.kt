package dev.glyphrotator.app.ui

import android.content.Context
import androidx.annotation.DimenRes

/**
 * El valor de un `dimen` en píxeles.
 *
 * Las pantallas que se construyen a mano —logros, estadísticas, Pokémon— llaman a `setPadding`
 * y a `LayoutParams`, y las dos piden **píxeles**. Escribir el número a pelo es lo que había
 * antes y por eso no escalaba: 96 px son 32 dp en el Phone (3) y otra cosa en cualquier otro
 * móvil. Pasando por `dimens.xml` el número sale ya convertido a la densidad de quien lo mire.
 */
fun Context.dimen(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)
