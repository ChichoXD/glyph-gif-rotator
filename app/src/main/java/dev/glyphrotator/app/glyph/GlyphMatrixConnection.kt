package dev.glyphrotator.app.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * La conexión con la Glyph Matrix, una sola para toda la app.
 *
 * `GlyphMatrixManager` es un singleton del proceso y su `init()` solo desemboca en
 * `onServiceConnected` una vez. En esta app hay **dos** cosas que quieren pintar —el servicio
 * de rotación por el canal de app y el Glyph Toy del botón físico por el canal de toy— y cada
 * una llamaba a `getInstance()` e `init()` por su cuenta. El resultado: la que llegaba
 * segunda se quedaba esperando un callback que ya se había disparado, y no volvía a pintar
 * nunca. Se notaba como "solo sale el reloj del toy" y "la vista previa no hace nada".
 *
 * Aquí se monta una única vez y quien llegue después con la conexión ya lista recibe el aviso
 * al momento.
 */
object GlyphMatrixConnection {

    @Volatile
    private var manager: GlyphMatrixManager? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    /** A quién avisar en cuanto la conexión esté lista. Se vacía al entregarlos. */
    private val waiting = mutableListOf<() -> Unit>()

    private var initStarted = false

    /**
     * El código del móvil en el que corremos.
     *
     * El SDK trae uno por modelo y el registro solo funciona con el correcto. Se pregunta en
     * vez de suponerlo: es lo único que separa que la Matrix pinte de que se quede muerta.
     */
    private fun currentDeviceCode(): String = when {
        Common.is25111p() -> Glyph.DEVICE_25111p
        else -> Glyph.DEVICE_23112
    }

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            val gmm = manager ?: return
            // El registro va con el código del móvil que toque, no con el del Phone (3) a pelo.
            //
            // Con `DEVICE_23112` clavado, en un Phone (4a) Pro el registro devuelve false y la
            // Matrix no vuelve a pintar nunca. Lo peor es cómo se manifiesta: la app abre, deja
            // añadir GIFs y responde a todo, porque nada de eso depende de la conexión. Solo la
            // pantalla de atrás se queda muerta, sin ningún error visible.
            val deviceCode = currentDeviceCode()
            val registered = try {
                gmm.register(deviceCode)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo al registrar $deviceCode", e)
                false
            }
            isConnected = registered
            if (!registered) {
                Log.e(TAG, "El registro en la Glyph Matrix devolvió false")
                return
            }
            notifyWaiting()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            isConnected = false
            // Se permite volver a montarla: el servicio del sistema puede reaparecer.
            synchronized(this@GlyphMatrixConnection) { initStarted = false }
        }
    }

    /**
     * Pide la conexión y avisa por [onReady] cuando esté lista. Si ya lo está, avisa al
     * momento, que es justo lo que faltaba: sin esto, el segundo en llegar no se enteraba.
     */
    fun connect(context: Context, onReady: () -> Unit) {
        if (isConnected) {
            onReady()
            return
        }

        val startNow: Boolean
        synchronized(this) {
            waiting += onReady
            startNow = !initStarted
            if (startNow) initStarted = true
        }
        if (!startNow) return

        val gmm = GlyphMatrixManager.getInstance(context.applicationContext)
        if (gmm == null) {
            Log.e(TAG, "getInstance() devolvió null: SDK no disponible en este dispositivo")
            synchronized(this) { initStarted = false }
            return
        }
        manager = gmm
        gmm.init(callback)
    }

    /**
     * Vuelve a intentarlo si la conexión no llegó a establecerse.
     *
     * `init()` no siempre acaba en `onServiceConnected` —si otra app tiene cogida la Matrix,
     * por ejemplo— y no avisa de nada. Sin reintentar, quien esperaba se queda ciego.
     */
    fun retry(context: Context) {
        if (isConnected) return
        synchronized(this) { initStarted = false }
        val gmm = GlyphMatrixManager.getInstance(context.applicationContext) ?: return
        manager = gmm
        synchronized(this) { initStarted = true }
        Log.i(TAG, "reintentando la conexión con la Matrix")
        gmm.init(callback)
    }

    /** El manager ya registrado, o null si todavía no hay conexión. */
    fun requireManager(): GlyphMatrixManager? = if (isConnected) manager else null

    /**
     * Apaga la Matrix sin desmontar la conexión.
     *
     * No se llama a `unInit()` a propósito: desmonta el singleton, y como el proceso sigue
     * vivo, el siguiente `init()` ya no vuelve a conectar. Dejar la conexión montada no
     * cuesta nada y es lo que permite volver a pintar después.
     */
    fun turnOff() {
        val gmm = manager ?: return
        runCatching { gmm.turnOff() }
    }

    private fun notifyWaiting() {
        val pending: List<() -> Unit>
        synchronized(this) {
            pending = waiting.toList()
            waiting.clear()
        }
        pending.forEach { it() }
    }

    private const val TAG = "GlyphMatrixConnection"
}
