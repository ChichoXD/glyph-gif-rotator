package dev.glyphrotator.app.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable

/**
 * Decodifica un GIF animado o una imagen estática a una lista de bitmaps cuadrados +
 * duraciones. Para GIFs usa android-gif-drawable para leer cada frame y su timing
 * original; para el resto de imágenes (PNG/JPEG/WEBP...) genera una "animación" de un
 * único frame estático.
 *
 * El SDK de Glyph Matrix exige bitmaps 1:1 (`GlyphMatrixObject.setImageSource`), así que
 * cada frame se recorta al centro a un cuadrado y se escala al tamaño de la matriz
 * (25x25 en el Nothing Phone (3), vía `Common.getDeviceMatrixLength()`).
 */
object MediaFrameDecoder {

    suspend fun decode(
        context: Context,
        uri: Uri,
        targetSize: Int,
        mode: MatrixImageProcessor.RenderMode = MatrixImageProcessor.RenderMode.LUMINANCE,
    ): GifAnimation =
        withContext(Dispatchers.IO) {
            if (isGif(context, uri)) {
                decodeGif(targetSize, mode) { GifDrawable(context.contentResolver, uri) }
            } else {
                decodeStaticImage(context, uri, targetSize, mode)
            }
        }

    /**
     * Si el archivo es un GIF animado.
     *
     * No basta con el MIME: `ContentResolver.getType` solo sabe responder de las URI
     * `content://`, y para las `file://` —las de los sprites copiados dentro de la app—
     * devuelve null. Fiándose de él, un GIF se decodificaba como imagen fija y en la Matrix
     * salía congelado en su primer frame.
     */
    private fun isGif(context: Context, uri: Uri): Boolean {
        context.contentResolver.getType(uri)?.let { return it == "image/gif" }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(GIF_SIGNATURE.size)
                stream.read(header) == header.size && header.contentEquals(GIF_SIGNATURE)
            }
        }.getOrNull() ?: false
    }

    /** Decodifica un GIF empaquetado en `app/src/main/assets/`, sin depender del selector de archivos. */
    suspend fun decodeAsset(context: Context, assetFileName: String, targetSize: Int): GifAnimation =
        withContext(Dispatchers.IO) {
            decodeGif(targetSize, MatrixImageProcessor.RenderMode.LUMINANCE) {
                GifDrawable(context.assets, assetFileName)
            }
        }

    /**
     * El recorte al contenido y el estirado de contraste se calculan una sola vez para todo
     * el GIF, no frame a frame.
     *
     * Haciéndolo por frame la animación vibra: el recuadro del dibujo cambia de sitio en
     * cuanto el personaje mueve una pata, y el contraste se reajusta a los claros y oscuros
     * de ese frame concreto, así que el brillo salta entre frames. Con un recuadro y un
     * rango comunes, la animación se queda quieta y solo se mueve lo que se mueve de verdad.
     */
    private fun decodeGif(
        targetSize: Int,
        mode: MatrixImageProcessor.RenderMode,
        open: () -> GifDrawable,
    ): GifAnimation {
        val drawable = open()
        try {
            val frameCount = drawable.numberOfFrames
            val durations = ArrayList<Long>(frameCount)
            for (index in 0 until frameCount) {
                val duration = drawable.getFrameDuration(index).toLong()
                durations.add(if (duration <= 0L) DEFAULT_FRAME_DURATION_MS else duration)
            }

            // Primera pasada: el recuadro que cubre el dibujo en todos los frames a la vez.
            var union: MatrixImageProcessor.ContentBounds? = null
            var width = 0
            var height = 0
            for (index in 0 until frameCount) {
                val frame = drawable.seekToFrameAndGet(index)
                width = frame.width
                height = frame.height
                val pixels = IntArray(width * height)
                frame.getPixels(pixels, 0, width, 0, 0, width, height)
                val bounds = MatrixImageProcessor.findContentBounds(pixels, width, height, mode)
                union = MatrixImageProcessor.union(union, bounds)
            }
            val box = squareBox(union, width, height)

            // Segunda pasada: cada frame reducido con ese mismo recuadro, todavía sin tocar
            // el contraste (hace falta ver el GIF entero para saber su rango real).
            val reduced = ArrayList<FloatArray>(frameCount)
            for (index in 0 until frameCount) {
                val frame = drawable.seekToFrameAndGet(index)
                val pixels = IntArray(width * height)
                frame.getPixels(pixels, 0, width, 0, 0, width, height)
                val square = cropSquare(pixels, width, height, box)
                val values = MatrixImageProcessor.values(square, box.side, mode)
                reduced.add(MatrixImageProcessor.downscaleByArea(values, box.side, targetSize))
            }

            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            for (frame in reduced) {
                for (value in frame) {
                    if (value < min) min = value
                    if (value > max) max = value
                }
            }

            val frames = reduced.mapTo(ArrayList(frameCount)) { frame ->
                val finished = MatrixImageProcessor.sharpen(
                    MatrixImageProcessor.stretchContrast(frame, min, max)
                )
                val argb = IntArray(finished.size) { MatrixImageProcessor.toGrayArgb(finished[it]) }
                Bitmap.createBitmap(argb, targetSize, targetSize, Bitmap.Config.ARGB_8888)
            }
            return GifAnimation(frames, durations)
        } finally {
            drawable.recycle()
        }
    }

    /** Recuadro cuadrado (puede salirse de la imagen) sobre el que se recorta cada frame. */
    private data class SquareBox(val originX: Int, val originY: Int, val side: Int)

    private fun squareBox(
        bounds: MatrixImageProcessor.ContentBounds?,
        width: Int,
        height: Int,
    ): SquareBox = if (bounds != null) {
        val side = maxOf(bounds.width, bounds.height)
        SquareBox(bounds.left - (side - bounds.width) / 2, bounds.top - (side - bounds.height) / 2, side)
    } else {
        val side = minOf(width, height)
        SquareBox((width - side) / 2, (height - side) / 2, side)
    }

    /** El recorte en ARGB; lo que caiga fuera de la imagen queda transparente. */
    private fun cropSquare(pixels: IntArray, width: Int, height: Int, box: SquareBox): IntArray {
        val square = IntArray(box.side * box.side)
        for (y in 0 until box.side) {
            val sourceY = box.originY + y
            for (x in 0 until box.side) {
                val sourceX = box.originX + x
                square[y * box.side + x] = if (sourceX in 0 until width && sourceY in 0 until height) {
                    pixels[sourceY * width + sourceX]
                } else {
                    0
                }
            }
        }
        return square
    }

    private fun decodeStaticImage(
        context: Context,
        uri: Uri,
        targetSize: Int,
        mode: MatrixImageProcessor.RenderMode,
    ): GifAnimation {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("No se pudo decodificar la imagen $uri")
        val frame = squareCropAndScale(bitmap, targetSize, mode)
        return GifAnimation(listOf(frame), listOf(STATIC_FRAME_DURATION_MS))
    }

    /**
     * Recorta al centro en cuadrado y reduce al tamaño de la Matrix.
     *
     * No usa `createScaledBitmap`: su interpolación bilineal difumina los bordes y a 25x25
     * el dibujo se convierte en una mancha gris (muy visible con pixel art). En su lugar se
     * promedia por áreas y se recupera el contraste con [MatrixImageProcessor].
     */
    private fun squareCropAndScale(
        source: Bitmap,
        targetSize: Int,
        mode: MatrixImageProcessor.RenderMode,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val all = IntArray(width * height)
        source.getPixels(all, 0, width, 0, 0, width, height)

        // Los sprites suelen traer mucho margen transparente. Si se escalara el lienzo
        // entero, el dibujo llegaría a la Matrix más pequeño de lo necesario.
        val bounds = MatrixImageProcessor.findContentBounds(all, width, height, mode)
        val box = squareBox(bounds, width, height)
        val square = cropSquare(all, width, height, box)

        val values = MatrixImageProcessor.values(square, box.side, mode)
        val processed = MatrixImageProcessor.sharpen(
            MatrixImageProcessor.stretchContrast(
                MatrixImageProcessor.downscaleByArea(values, box.side, targetSize)
            )
        )
        val output = IntArray(processed.size) { MatrixImageProcessor.toGrayArgb(processed[it]) }
        return Bitmap.createBitmap(output, targetSize, targetSize, Bitmap.Config.ARGB_8888)
    }

    /** "GIF", los tres primeros bytes de cualquier GIF. */
    private val GIF_SIGNATURE = byteArrayOf(0x47, 0x49, 0x46)

    private const val DEFAULT_FRAME_DURATION_MS = 100L
    private const val STATIC_FRAME_DURATION_MS = 60_000L
}
