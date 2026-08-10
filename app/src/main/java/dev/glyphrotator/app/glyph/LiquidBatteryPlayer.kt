package dev.glyphrotator.app.glyph

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simulación de líquido con física de inclinación real (acelerómetro): ver
 * [LiquidPhysics] para el cálculo compartido con el toy del botón físico.
 */
class LiquidBatteryPlayer(
    private val scope: CoroutineScope,
    private val controller: GlyphMatrixController,
    context: Context
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var job: Job? = null
    private var sensorRegistered = false

    @Volatile private var tiltX = 0f
    @Volatile private var tiltY = 0f

    private lateinit var liquidHeights: FloatArray
    private var contrastBitmap: Bitmap? = null

    /** [instant]: si es true, el número/líquido arrancan ya en su nivel real, sin la rampa 0→%. */
    fun start(instant: Boolean = false, batteryPctProvider: () -> Int) {
        job?.cancel()
        registerSensor()

        job = scope.launch {
            val size = controller.matrixSize
            if (!::liquidHeights.isInitialized || liquidHeights.size != size) {
                liquidHeights = FloatArray(size) { size.toFloat() }
            }
            if (contrastBitmap == null) {
                contrastBitmap = LiquidBatteryFrameRenderer.renderContrastBox(size)
            }
            val animationStart = if (instant) {
                System.currentTimeMillis() - RISE_DURATION_MS
            } else {
                System.currentTimeMillis()
            }

            while (isActive) {
                val realPct = batteryPctProvider().coerceIn(0, 100)
                // El número sube de 0 al % real a la vez que el líquido, en vez de saltar de golpe.
                val progress = ((System.currentTimeMillis() - animationStart) / RISE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                val pct = (progress * realPct).toInt()

                LiquidPhysics.step(liquidHeights, pct, tiltX, tiltY, size)

                val liquidBitmap = LiquidBatteryFrameRenderer.renderLiquid(liquidHeights, realPct, size)
                controller.showLiquidBattery(liquidBitmap, contrastBitmap!!, "$pct")
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        unregisterSensor()
    }

    private fun registerSensor() {
        if (sensorRegistered) return
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorRegistered = true
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorManager?.unregisterListener(this)
        sensorRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val ax = event.values[0]
        val ay = event.values[1]
        tiltX = ALPHA * tiltX + (1 - ALPHA) * ax
        tiltY = ALPHA * tiltY + (1 - ALPHA) * ay
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val TICK_MS = 50L
        const val ALPHA = 0.8f
        const val RISE_DURATION_MS = 900L
    }
}
