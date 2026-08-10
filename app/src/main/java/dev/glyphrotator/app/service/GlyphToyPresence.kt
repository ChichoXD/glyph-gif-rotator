package dev.glyphrotator.app.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Marca si nuestro Glyph Toy está **dibujando activamente** (la batería con líquido tras
 * mantener pulsado el botón físico).
 *
 * El toy dibuja con `setMatrixFrame` y el servicio de rotación con `setAppMatrixFrame`:
 * son canales distintos del SDK, así que si ambos pintan a la vez compiten por la Matrix y
 * lo que muestra el botón queda pisado por el carrusel.
 *
 * Ojo: se marca solo mientras dura el líquido, NO mientras el toy está simplemente
 * seleccionado en el carrusel. Si se marcara todo el tiempo que está enlazado, dejar el toy
 * elegido en el botón apagaría el carrusel de GIFs para siempre.
 *
 * Ambos viven en el mismo proceso, así que basta con una bandera compartida.
 */
object GlyphToyPresence {

    private val active = AtomicBoolean(false)

    /** Se avisa al servicio para que reaccione en cuanto cambia la presencia del toy. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    val isActive: Boolean get() = active.get()

    fun setActive(value: Boolean) {
        if (active.getAndSet(value) != value) {
            onChanged?.invoke()
        }
    }
}
