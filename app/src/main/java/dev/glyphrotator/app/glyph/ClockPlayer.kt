package dev.glyphrotator.app.glyph

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * A diferencia de [GlyphGifPlayer] (frames fijos precalculados), el reloj se
 * regenera cada medio segundo para que el parpadeo de los dos puntos esté al día.
 * Reloj de 12h con AM/PM, sin barra de batería.
 */
class ClockPlayer(
    private val scope: CoroutineScope,
    private val controller: GlyphMatrixController
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val calendar = Calendar.getInstance()
                val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
                val amPm = if (hour24 < 12) "AM" else "PM"
                val showColon = calendar.get(Calendar.SECOND) % 2 == 0

                // La X se calcula siempre con ":" para que no salte al parpadear el separador.
                val timeX = GlyphTextMetrics.centeredX("%02d:%02d".format(hour12, minute), controller.matrixSize)
                val timeText = "%02d%s%02d".format(hour12, if (showColon) ":" else " ", minute)

                controller.showClock(timeText, timeX, amPm)
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TICK_MS = 500L
    }
}
