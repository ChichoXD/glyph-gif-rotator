package dev.glyphrotator.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Existe, sobre todo, como componente habilitado en "Acceso a notificaciones" para que
 * [android.media.session.MediaSessionManager] pueda darnos las sesiones multimedia activas
 * (necesario para distinguir reproduciendo de en pausa, algo que
 * [android.media.AudioManager.isMusicActive] no puede hacer).
 *
 * Además avisa a [NotificationFlash] de cada notificación nueva, para que el carrusel se
 * aparte mientras el sistema pinta su animación en la Matrix y no se solapen.
 */
class MusicSessionListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (isSilent(notification)) return
        NotificationFlash.trigger()
    }

    /**
     * Las notificaciones permanentes (nuestro propio aviso de servicio activo, el reproductor
     * de música, las descargas...) y las de grupo no encienden la Matrix, así que apartarse
     * por ellas solo dejaría el carrusel apagado sin motivo.
     */
    private fun isSilent(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return true
        if (sbn.isOngoing) return true
        val flags = sbn.notification.flags
        return flags and Notification.FLAG_GROUP_SUMMARY != 0
    }
}
