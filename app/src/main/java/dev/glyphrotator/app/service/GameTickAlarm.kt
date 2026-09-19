package dev.glyphrotator.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * El despertador del juego, y la pieza que faltaba para que el juego existiera de noche.
 *
 * ## Qué estaba roto
 *
 * Las apariciones, la incubación y el entrenamiento se comprobaban desde un bucle de corrutina
 * dentro del servicio: `while (isActive) { delay(60_000); ... }`. Eso funciona con la pantalla
 * encendida y **no funciona en absoluto** con el móvil en reposo, que es justo cuando el juego
 * tiene que ocurrir.
 *
 * El motivo es que un servicio en primer plano mantiene vivo el **proceso**, no la **CPU**.
 * Cuando la pantalla se apaga y el teléfono entra en suspensión, el temporizador que hay detrás
 * de `delay()` deja de correr: no es que se retrase, es que el reloj se para hasta que algo
 * despierte al aparato. El bucle se congelaba en el primer `delay()` y no volvía en toda la
 * noche. `minutesSinceLastSpawn` no subía nunca, así que no llegaba a tirarse el dado ni una
 * vez: daba igual esperar veinte minutos que una hora que ocho.
 *
 * Se notaba como "el juego no hace nada", y de rebote hacía parecer roto el modo demo — que sí
 * multiplicaba la probabilidad, pero de una tirada que no se llegaba a hacer.
 *
 * ## Por qué una alarma y no un wake lock
 *
 * Un `PARTIAL_WAKE_LOCK` mantendría la CPU despierta toda la noche para hacer un cálculo de
 * microsegundos cada minuto: se comería la batería que el juego premia ahorrar. La alarma
 * despierta al teléfono el instante justo y lo deja volver a dormir.
 *
 * `setAndAllowWhileIdle` y no `setExact*`: atraviesa el modo Doze sin pedir el permiso
 * `SCHEDULE_EXACT_ALARM` —que en Android 13+ hay que conceder a mano y Google restringe—, a
 * cambio de que la hora sea aproximada. Da igual: quien la recibe cuenta **cuánto tiempo ha
 * pasado de verdad** en vez de suponer que fue un minuto, así que una alarma tarde no pierde
 * nada. Ver `GlyphRotationService.stepWildSpawn`.
 *
 * En Doze el sistema estrangula estas alarmas a una cada nueve minutos salvo que la app esté
 * exenta de la optimización de batería — que es exactamente para lo que la app ya pide
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Sin esa exención el juego sigue funcionando, solo que
 * a saltos más largos, y por eso el recuento por tiempo transcurrido no es un adorno.
 */
object GameTickAlarm {

    /** Cada cuánto se despierta al teléfono con la pantalla apagada. */
    const val INTERVAL_MS = 60_000L

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        val at = System.currentTimeMillis() + INTERVAL_MS
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
            .onFailure { Log.e(TAG, "No se pudo programar el tick del juego", it) }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(context)) }
    }

    /**
     * `getForegroundService` y no `getService`: desde Android 12 arrancar un servicio desde
     * segundo plano lanza excepción. El nuestro ya está en primer plano, pero el intent llega
     * con el móvil dormido y este es el camino que el sistema acepta sin discutir.
     */
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, GlyphRotationService::class.java)
            .setAction(GlyphRotationService.ACTION_GAME_TICK)
        return PendingIntent.getForegroundService(
            context.applicationContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val REQUEST_CODE = 7301
    private const val TAG = "GameTickAlarm"
}
