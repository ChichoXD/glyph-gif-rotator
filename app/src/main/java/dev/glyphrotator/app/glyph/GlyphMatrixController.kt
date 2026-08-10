package dev.glyphrotator.app.glyph

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

/**
 * Envuelve el ciclo de vida de [GlyphMatrixManager] para el Nothing Phone (3)
 * (`Glyph.DEVICE_23112`) y expone una API mínima para pintar bitmaps cuadrados
 * en la Glyph Matrix usando `setAppMatrixFrame`, tal y como recomienda el SDK
 * para control directo desde una app (a diferencia de `setMatrixFrame`, pensado
 * para Glyph Toys). Requiere Nothing OS 20250801 o posterior.
 */
class GlyphMatrixController(context: Context) {

    private val appContext = context.applicationContext

    /**
     * La conexión la lleva [GlyphMatrixConnection], compartida con el Glyph Toy del botón
     * físico. Antes cada uno montaba la suya sobre el mismo singleton del SDK, y el segundo
     * en llegar se quedaba esperando un callback que ya se había disparado: por eso al tener
     * el toy enlazado el servicio no volvía a pintar nada.
     */
    private val manager: GlyphMatrixManager?
        get() = GlyphMatrixConnection.requireManager()

    val isConnected: Boolean
        get() = GlyphMatrixConnection.isConnected

    /** Brillo (0-255) aplicado a cada frame siguiente. Ajustable en caliente según batería. */
    @Volatile
    var brightness: Int = 255

    /** Tamaño (en píxeles) del lado de la matriz cuadrada de este dispositivo (25 en Phone 3). */
    val matrixSize: Int
        get() = Common.getDeviceMatrixLength()

    /** Inicia la conexión con la Matrix. [onReady] se invoca una vez lista. */
    fun connect(onReady: () -> Unit) {
        GlyphMatrixConnection.connect(appContext, onReady)
    }

    /** Reintenta si la conexión no llegó a establecerse. */
    fun reconnect() {
        GlyphMatrixConnection.retry(appContext)
    }

    /** Pinta un bitmap cuadrado (ya recortado/escalado al tamaño de la matriz) en la capa superior. */
    fun showFrame(bitmap: Bitmap) {
        val gmm = manager ?: return
        if (!isConnected) return
        val glyphObject = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setScale(100)
            .setOrientation(0)
            .setPosition(0, 0)
            .setBrightness(brightness)
            .setReverse(false)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(glyphObject)
            .build(appContext)

        try {
            gmm.setAppMatrixFrame(frame)
        } catch (e: GlyphException) {
            Log.e(TAG, "setAppMatrixFrame falló", e)
        }
    }

    /**
     * Igual que [showFrame] pero en tres capas: [liquidBitmap] abajo (más tenue),
     * [contrastBitmap] en medio (una caja opaca para que el número no se mezcle con el
     * líquido) y [percentNumber] arriba usando el renderizador de texto nativo del SDK,
     * bien más brillante que el líquido para que se lea claro. El brillo aquí es fijo
     * (no usa [brightness]/la atenuación por batería baja): esta pantalla existe
     * precisamente para leer el % al cargar, así que debe verse nítida siempre, incluso
     * con poca batería.
     */
    fun showLiquidBattery(liquidBitmap: Bitmap, contrastBitmap: Bitmap, percentNumber: String) {
        val gmm = manager ?: return
        if (!isConnected) return

        val liquidObject = GlyphMatrixObject.Builder()
            .setImageSource(liquidBitmap)
            .setScale(100)
            .setPosition(0, 0)
            .setBrightness(LIQUID_BRIGHTNESS)
            .build()

        val contrastObject = GlyphMatrixObject.Builder()
            .setImageSource(contrastBitmap)
            .setScale(100)
            .setPosition(0, 0)
            .setBrightness(255)
            .build()

        val textObject = GlyphMatrixObject.Builder()
            .setText(percentNumber)
            .setPosition(GlyphTextMetrics.centeredX(percentNumber, matrixSize), 9)
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addLow(liquidObject)
            .addMid(contrastObject)
            .addTop(textObject)
            .build(appContext)

        try {
            gmm.setAppMatrixFrame(frame)
        } catch (e: GlyphException) {
            Log.e(TAG, "setAppMatrixFrame (líquido+texto) falló", e)
        }
    }

    /**
     * Reloj de 12h con AM/PM (dos líneas de texto nativo del SDK, sin barra de batería).
     * [timeX] se calcula siempre a partir de la variante con ":" (aunque en ese instante
     * se esté mostrando el espacio en blanco del parpadeo), para que la hora no se recentre
     * y "salte" de lado a lado cada vez que el separador aparece/desaparece.
     */
    fun showClock(timeText: String, timeX: Int, amPmText: String) {
        val gmm = manager ?: return
        if (!isConnected) return

        val timeObject = GlyphMatrixObject.Builder()
            .setText(timeText)
            .setPosition(timeX, 6)
            .setBrightness(brightness)
            .build()

        val amPmObject = GlyphMatrixObject.Builder()
            .setText(amPmText)
            .setPosition(GlyphTextMetrics.centeredX(amPmText, matrixSize), 15)
            .setBrightness((brightness * 0.8f).toInt().coerceIn(0, 255))
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(timeObject)
            .addMid(amPmObject)
            .build(appContext)

        try {
            gmm.setAppMatrixFrame(frame)
        } catch (e: GlyphException) {
            Log.e(TAG, "setAppMatrixFrame (reloj) falló", e)
        }
    }

    /**
     * Manda un frame en negro.
     *
     * La Matrix conserva encendido lo último que se le pintó: cerrar la sesión no la apaga,
     * solo deja de mandarle cosas, así que sin esto el último dibujo se queda ahí fijo y
     * parece que la app sigue funcionando cuando ya está parada.
     */
    private fun paintBlank() {
        val gmm = manager ?: return
        if (!isConnected) return
        val blank = Bitmap.createBitmap(matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
        blank.eraseColor(android.graphics.Color.BLACK)
        val glyphObject = GlyphMatrixObject.Builder()
            .setImageSource(blank)
            .setScale(100)
            .setOrientation(0)
            .setPosition(0, 0)
            .setBrightness(0)
            .setReverse(false)
            .build()
        try {
            gmm.setAppMatrixFrame(
                GlyphMatrixFrame.Builder().addTop(glyphObject).build(appContext)
            )
        } catch (e: GlyphException) {
            Log.w(TAG, "No se pudo pintar el frame en negro", e)
        }
    }

    /**
     * Manda un frame en crudo: un valor de brillo por LED físico.
     *
     * La Matrix tiene 489 LEDs colocados en círculo, no una cuadrícula de 625. Las animaciones
     * exportadas desde Glyph Museum vienen en ese formato, así que se envían tal cual en vez
     * de dibujarlas en un bitmap y dejar que el sistema recorte.
     */
    fun showRawFrame(leds: IntArray) {
        val gmm = manager ?: return
        if (!isConnected) return
        try {
            gmm.setAppMatrixFrame(leds)
        } catch (e: GlyphException) {
            Log.e(TAG, "setAppMatrixFrame(int[]) falló", e)
        }
    }

    /** Apaga el dibujo actual sin cerrar la conexión (para pantalla apagada o batería crítica). */
    fun clear() {
        val gmm = manager ?: return
        paintBlank()
        try {
            gmm.closeAppMatrix()
        } catch (e: GlyphException) {
            Log.w(TAG, "closeAppMatrix falló al limpiar", e)
        }
    }

    /** Libera la conexión con el servicio de Glyph Matrix. Llamar siempre desde onDestroy. */
    fun release() {
        val gmm = manager ?: return
        // En negro antes de cerrar: una vez cerrada la sesión ya no se le puede mandar nada,
        // y lo que hubiera en pantalla se quedaría encendido para siempre.
        paintBlank()
        try {
            gmm.closeAppMatrix()
        } catch (e: GlyphException) {
            Log.w(TAG, "closeAppMatrix falló (puede ser normal si ya estaba cerrada)", e)
        }
        gmm.turnOff()
        // Sin `unInit()` a propósito: desmonta el singleton del SDK, y como el proceso de la
        // app sigue vivo, el siguiente arranque del servicio reutilizaba ese mismo objeto ya
        // desmontado y su `init()` no volvía a conectar nunca. La Matrix quedaba muerta hasta
        // cerrar la app del todo. Se deja la conexión montada: la pantalla ya está apagada.
    }

    private companion object {
        const val TAG = "GlyphMatrixController"
        const val LIQUID_BRIGHTNESS = 130

        /** Compartidos por todo el proceso: ver el comentario de [manager]. */
        @Volatile
        var sharedManager: GlyphMatrixManager? = null

        @Volatile
        var sharedConnected: Boolean = false
    }
}
