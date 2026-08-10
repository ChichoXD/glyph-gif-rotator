package dev.glyphrotator.app.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import dev.glyphrotator.app.glyph.GlyphMatrixConnection
import dev.glyphrotator.app.glyph.GlyphTextMetrics
import dev.glyphrotator.app.glyph.LiquidBatteryFrameRenderer
import dev.glyphrotator.app.glyph.LiquidPhysics
import java.util.Calendar

/**
 * Glyph Toy real (registrado en el carrusel del botón físico trasero), independiente del
 * [GlyphRotationService]. Muestra un reloj tenue por defecto (mismo brillo bajo que el reloj
 * en reposo de la app, para que sea un único estado consistente en vez de dos distintos); al
 * mantener pulsado el botón (`GlyphToy.EVENT_CHANGE`) cambia unos segundos a la batería con
 * simulación de líquido (misma física de inclinación que
 * [dev.glyphrotator.app.glyph.LiquidBatteryPlayer], vía [LiquidPhysics], para que no se
 * desincronicen). Al ser un toy aparte del de glyph-catch, el botón nunca interfiere con la
 * captura de Pokémon: cada toy solo recibe los eventos mientras es el que está activo en el
 * carrusel.
 */
class ClockBatteryToyService : Service(), SensorEventListener {

    /** El manager de la conexión compartida; null mientras no esté lista. */
    private val glyphMatrixManager: GlyphMatrixManager?
        get() = GlyphMatrixConnection.requireManager()
    private var sensorManager: SensorManager? = null
    private var sensorRegistered = false
    private var thread: HandlerThread? = null
    private var tickHandler: Handler? = null
    private var tickRunnable: Runnable? = null
    private var revertToClockRunnable: Runnable? = null
    // Los escribe onLongPress (hilo principal) y los lee el ticker (HandlerThread propio),
    // así que van @Volatile para que el ticker vea el cambio al instante.
    @Volatile private var showingBattery = false
    @Volatile private var batteryViewStartTime = 0L
    private val liquidHeights = FloatArray(MATRIX_SIZE) { MATRIX_SIZE.toFloat() }
    private val contrastBitmap by lazy { LiquidBatteryFrameRenderer.renderContrastBox(MATRIX_SIZE) }

    @Volatile private var tiltX = 0f
    @Volatile private var tiltY = 0f

    override fun onBind(intent: Intent?): IBinder {
        // Por la conexión compartida, no montando la suya: el toy y el servicio de rotación
        // usan el mismo singleton del SDK, y el que llegaba segundo se quedaba sin callback.
        GlyphMatrixConnection.connect(applicationContext) { startTicking() }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        val handlerThread = HandlerThread("ClockBatteryToyTicker").apply { start() }
        thread = handlerThread
        tickHandler = Handler(handlerThread.looper)

        return serviceMessenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        GlyphToyPresence.setActive(false)
        stopTicking()
        unregisterSensor()
        thread?.quitSafely()
        thread = null
        tickHandler = null
        // Sin `unInit()`: la conexión es compartida con el servicio de rotación, y desmontarla
        // aquí lo dejaba ciego —no volvía a pintar hasta reiniciar la app—. Basta con dejar de
        // dibujar; quien siga usándola la mantiene viva.
        return false
    }

    private fun registerSensor() {
        if (sensorRegistered) return
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorRegistered = true
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorManager?.unregisterListener(this)
        sensorRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        tiltX = ALPHA * tiltX + (1 - ALPHA) * event.values[0]
        tiltY = ALPHA * tiltY + (1 - ALPHA) * event.values[1]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startTicking() {
        val handler = tickHandler ?: return
        val runnable = object : Runnable {
            override fun run() {
                renderCurrentFrame()
                handler.postDelayed(this, if (showingBattery) BATTERY_TICK_MS else TICK_MS)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    private fun stopTicking() {
        val handler = tickHandler ?: return
        tickRunnable?.let { handler.removeCallbacks(it) }
        revertToClockRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        revertToClockRunnable = null
    }

    private fun renderCurrentFrame() {
        val gmm = glyphMatrixManager ?: return
        val pct = batteryInfo().first

        val frame = if (showingBattery) {
            // El número sube de 0 al % real a la vez que el líquido, en vez de saltar de golpe.
            val progress = ((System.currentTimeMillis() - batteryViewStartTime) / RISE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            val rampedPct = (progress * pct).toInt()
            LiquidPhysics.step(liquidHeights, rampedPct, tiltX, tiltY, MATRIX_SIZE)

            val liquidObject = GlyphMatrixObject.Builder()
                .setImageSource(LiquidBatteryFrameRenderer.renderLiquid(liquidHeights, pct, MATRIX_SIZE))
                .setScale(100)
                .setPosition(0, 0)
                .setBrightness(130)
                .build()
            val contrastObject = GlyphMatrixObject.Builder()
                .setImageSource(contrastBitmap)
                .setScale(100)
                .setPosition(0, 0)
                .setBrightness(255)
                .build()
            val textObject = GlyphMatrixObject.Builder()
                .setText("$rampedPct")
                .setPosition(GlyphTextMetrics.centeredX("$rampedPct", MATRIX_SIZE), 9)
                .setBrightness(255)
                .build()
            GlyphMatrixFrame.Builder()
                .addLow(liquidObject)
                .addMid(contrastObject)
                .addTop(textObject)
                .build(applicationContext)
        } else {
            val calendar = Calendar.getInstance()
            val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
            val amPm = if (hour24 < 12) "AM" else "PM"
            val showColon = calendar.get(Calendar.SECOND) % 2 == 0

            // En una Matrix pequeña "12:34" no entra de ancho, así que la hora se parte en dos
            // líneas y se deja el AM/PM fuera. Es la diferencia entre leer la hora y ver una
            // fila de píxeles cortada por la mitad.
            val narrow = MATRIX_SIZE < WIDE_MATRIX_SIZE
            val timeText = if (narrow) {
                "%02d\n%02d".format(hour12, minute)
            } else {
                "%02d%s%02d".format(hour12, if (showColon) ":" else " ", minute)
            }
            // La X se calcula siempre con ":" para que no salte al parpadear el separador.
            val timeX = if (narrow) {
                GlyphTextMetrics.centeredX("%02d".format(hour12), MATRIX_SIZE)
            } else {
                GlyphTextMetrics.centeredX("%02d:%02d".format(hour12, minute), MATRIX_SIZE)
            }

            val timeObject = GlyphMatrixObject.Builder()
                .setText(timeText)
                .setPosition(timeX, if (narrow) 1 else 6)
                .setBrightness(DIM_CLOCK_BRIGHTNESS)
                .build()
            // El AM/PM solo cabe en la grande. En la pequeña la hora ya ocupa las dos filas y
            // meterlo encima dejaría las tres cosas ilegibles en vez de dos legibles.
            val builder = GlyphMatrixFrame.Builder().addTop(timeObject)
            if (!narrow) {
                builder.addMid(
                    GlyphMatrixObject.Builder()
                        .setText(amPm)
                        .setPosition(GlyphTextMetrics.centeredX(amPm, MATRIX_SIZE), 15)
                        .setBrightness(DIM_CLOCK_BRIGHTNESS)
                        .build()
                )
            }
            builder.build(applicationContext)
        }

        try {
            gmm.setMatrixFrame(frame.render())
        } catch (e: Exception) {
            Log.e(TAG, "setMatrixFrame falló", e)
        }
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        val bm = getSystemService(BatteryManager::class.java) ?: return 100 to false
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return pct to charging
    }

    /**
     * La pulsación larga captura al Pokémon salvaje si hay uno esperando.
     *
     * Es el gesto central del juego: se captura **sin encender la pantalla**, que es justo lo
     * que el juego premia. Recogerlo al desbloquear el teléfono, como estaba antes, iba en
     * contra de la propia mecánica.
     *
     * Sin nadie esperando, el botón hace lo de siempre: la batería con líquido. El gesto
     * significa una cosa u otra según lo que haya en la Matrix, y eso se entiende solo.
     */
    /**
     * Captura al que estuviera esperando. Devuelve false si no había ninguno.
     *
     * La animación la pinta el servicio de rotación por el canal de app: el toy deja de
     * dibujar mientras dura, para no competir con ella por la Matrix.
     */

    /**
     * Enseña a tu compañero de entrenamiento, si lo tienes activado en Ajustes.
     *
     * Va **después** de la captura y no antes: si hay un salvaje esperando, atenderlo es lo
     * urgente —se va solo con el tiempo—, mientras que a tu compañero lo puedes ver cuando
     * quieras. Con la opción apagada, el botón hace lo de siempre: la batería con líquido.
     */

    private fun onLongPress() {

        val handler = tickHandler ?: return
        showingBattery = true
        batteryViewStartTime = System.currentTimeMillis()
        for (i in liquidHeights.indices) liquidHeights[i] = MATRIX_SIZE.toFloat()
        registerSensor()
        // Solo aquí reclamamos la Matrix: durante estos segundos el servicio de rotación se
        // aparta para que el líquido no compita con el carrusel.
        GlyphToyPresence.setActive(true)
        revertToClockRunnable?.let { handler.removeCallbacks(it) }
        val revert = Runnable {
            showingBattery = false
            unregisterSensor()
            GlyphToyPresence.setActive(false)
        }
        revertToClockRunnable = revert
        handler.postDelayed(revert, BATTERY_VIEW_MS)
    }

    private val serviceHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                val event = msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                when (event) {
                    GlyphToy.EVENT_CHANGE -> onLongPress()
                    GlyphToy.EVENT_AOD -> tickHandler?.post { renderCurrentFrame() }
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }

    private val serviceMessenger = Messenger(serviceHandler)

    private companion object {
        const val TAG = "ClockBatteryToyService"
        /**
         * El lado de la Matrix, preguntado al SDK.
         *
         * Fijo a 25 estaba bien mientras solo existía el Phone (3). El (4a) Pro trae una de 13,
         * así que con el valor clavado se dibujaría fuera de la pantalla.
         */
        /**
         * El lado de la Matrix, preguntado al SDK con red de seguridad.
         *
         * El `takeIf` no es paranoia: `liquidHeights` se crea al construir el servicio, y si en
         * ese instante el SDK todavía no está listo devolvería 0. Un `FloatArray(0)` no se
         * arregla solo después — el líquido de la batería se quedaría roto para siempre sin dar
         * ningún error. Cayendo a 25 se comporta como antes en el Phone (3), que es lo probado.
         */
        val MATRIX_SIZE: Int
            get() = Common.getDeviceMatrixLength().takeIf { it > 0 } ?: FALLBACK_MATRIX_SIZE

        private const val FALLBACK_MATRIX_SIZE = 25

        /** A partir de este lado cabe la hora en una línea con AM/PM debajo. */
        const val WIDE_MATRIX_SIZE = 20

        /** Lo que se queda el compañero en la Matrix al pedirlo con el botón. */
        const val PARTNER_VIEW_MS = 8_000L

        /** Madrugada, para el logro de capturar de noche. */
        val NIGHT_HOURS = 0..5

        /** Batería a partir de la cual cuenta como "capturado a pilas". */
        const val LOW_BATTERY_PERCENT = 15

        /** Nivel con el que llega un Pokémon salvaje capturado con el botón. */
        val WILD_LEVELS = 3..18
        const val TICK_MS = 500L
        const val BATTERY_TICK_MS = 50L
        const val BATTERY_VIEW_MS = 8_000L
        const val RISE_DURATION_MS = 900L
        const val ALPHA = 0.8f
        const val DIM_CLOCK_BRIGHTNESS = 80
    }
}
