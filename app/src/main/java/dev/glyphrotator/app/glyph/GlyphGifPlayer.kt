package dev.glyphrotator.app.glyph

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reproduce un [GifAnimation] en bucle sobre la Glyph Matrix respetando el timing
 * original de cada frame, hasta que se llame a [stop] o se reemplace con [play].
 */
class GlyphGifPlayer(
    private val scope: CoroutineScope,
    private val controller: GlyphMatrixController
) {
    private var job: Job? = null

    /** Índice del último frame pintado, para poder retomar una animación por donde iba. */
    @Volatile
    var currentIndex: Int = 0
        private set

    fun play(animation: GifAnimation, startIndex: Int = 0) {
        job?.cancel()
        if (animation.isEmpty) return
        job = scope.launch {
            var index = startIndex % animation.frames.size
            while (isActive) {
                currentIndex = index
                controller.showFrame(animation.frames[index])
                delay(animation.frameDurationsMs[index])
                index = (index + 1) % animation.frames.size
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
