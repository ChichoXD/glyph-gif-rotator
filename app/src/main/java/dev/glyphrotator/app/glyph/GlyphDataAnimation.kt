package dev.glyphrotator.app.glyph

import android.content.Context

/**
 * Una animación exportada desde Glyph Museum: brillo por LED y duración de cada frame.
 *
 * El formato guarda **un valor por LED físico** (489 en el Phone 3, colocados en círculo), no
 * una cuadrícula de 25x25. Por eso se manda tal cual con `setAppMatrixFrame(int[])` en vez de
 * dibujarlo en un bitmap: convertirlo a cuadrícula y dejar que el sistema recorte perdería la
 * correspondencia exacta con lo que diseñó su autor.
 */
class GlyphDataAnimation(val frames: List<IntArray>, val durationsMs: List<Long>) {

    val totalDurationMs: Long get() = durationsMs.sum()

    companion object {

        /**
         * Lee el JSON de `assets`.
         *
         * Se parsea a mano por lo mismo que el clima: es una estructura conocida y fija, y
         * añadir una dependencia de JSON para dos campos no compensa.
         */
        fun fromAsset(context: Context, assetName: String): GlyphDataAnimation {
            val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
            return parse(json)
        }

        fun parse(json: String): GlyphDataAnimation {
            val frames = ArrayList<IntArray>()
            val durations = ArrayList<Long>()

            var at = 0
            while (true) {
                val durationAt = json.indexOf(FRAME_MARKER, at)
                if (durationAt < 0) break

                val comma = json.indexOf(',', durationAt + FRAME_MARKER.length)
                if (comma < 0) break
                val duration = json.substring(durationAt + FRAME_MARKER.length, comma)
                    .trim().toLongOrNull() ?: break

                val open = json.indexOf('[', comma)
                val close = json.indexOf(']', open)
                if (open < 0 || close < 0) break

                val values = json.substring(open + 1, close)
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }

                frames += values.toIntArray()
                durations += duration
                at = close
            }
            return GlyphDataAnimation(frames, durations)
        }

        private const val FRAME_MARKER = "{\"d\":"
    }
}
