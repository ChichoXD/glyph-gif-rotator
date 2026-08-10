package dev.glyphrotator.app.glyph

import android.graphics.Bitmap

/**
 * Un GIF ya decodificado a frames individuales, cada uno como bitmap cuadrado
 * (listo para `GlyphMatrixObject.setImageSource`) junto con su duración original en ms.
 */
data class GifAnimation(
    val frames: List<Bitmap>,
    val frameDurationsMs: List<Long>
) {
    init {
        require(frames.size == frameDurationsMs.size) {
            "frames y frameDurationsMs deben tener el mismo tamaño"
        }
    }

    val isEmpty: Boolean get() = frames.isEmpty()
}
