package dev.glyphrotator.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.glyphrotator.app.data.GifRepository

/**
 * Reinicia el servicio de rotación tras un reinicio del teléfono (o una actualización
 * de la app) si el usuario había dejado la rotación activada y todavía hay GIFs en la lista.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val repository = GifRepository(context)
        // Se comprueba el reloj además de la lista porque la app deja activar la rotación
        // solo con el reloj, sin ningún GIF: mirando solo la lista, ese montaje no volvía
        // después de reiniciar y parecía que se hubiera desactivado solo.
        val hasSomethingToShow = repository.getAll().isNotEmpty() || repository.isClockEnabled
        if (repository.isRotationEnabled && hasSomethingToShow) {
            GlyphRotationService.start(context)
        }
    }
}
