package dev.glyphrotator.app.service

/**
 * Marca los segundos siguientes a una notificación, durante los cuales le dejamos la Matrix
 * libre al sistema.
 *
 * Nothing (y los diseños vinculados de GlyphMuseum) pinta su propia animación cuando entra
 * una notificación. Si el carrusel sigue pintando a la vez, las dos imágenes se solapan y no
 * se entiende ninguna. Con esto nos apartamos hasta que la animación del sistema termina.
 *
 * El servicio de rotación y el listener de notificaciones viven en el mismo proceso, así que
 * basta una bandera compartida, igual que [GlyphToyPresence].
 */
object NotificationFlash {

    /**
     * Cuánto nos apartamos. La animación de notificación de Nothing dura unos pocos
     * segundos; con esto se cubre de sobra sin dejar la Matrix apagada un rato largo.
     */
    const val DURATION_MS = 6_000L

    /** Se avisa al servicio para que reaccione en cuanto entra una notificación. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    @Volatile
    private var activeUntilMillis = 0L

    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis < activeUntilMillis

    /** Milisegundos que quedan de cesión, o 0 si ya podemos volver a pintar. */
    fun remainingMs(nowMillis: Long = System.currentTimeMillis()): Long =
        (activeUntilMillis - nowMillis).coerceAtLeast(0L)

    fun trigger(nowMillis: Long = System.currentTimeMillis()) {
        val wasActive = isActive(nowMillis)
        // Una notificación durante otra alarga la cesión en vez de recortarla.
        activeUntilMillis = maxOf(activeUntilMillis, nowMillis + DURATION_MS)
        if (!wasActive) onChanged?.invoke()
    }

    /** Solo para las pruebas: deja la bandera como recién arrancada. */
    fun reset() {
        activeUntilMillis = 0L
    }
}
