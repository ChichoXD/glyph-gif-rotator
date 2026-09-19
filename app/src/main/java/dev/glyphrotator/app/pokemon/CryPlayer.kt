package dev.glyphrotator.app.pokemon

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * Reproduce el cry de un Pokémon, en la variante de sonido que haya elegido el usuario.
 *
 * Usa [SoundPool] y no `MediaPlayer`: los cries son cortos y se tocan seguidos, y MediaPlayer
 * necesita preparar y soltar un reproductor entero por sonido, con lo que el primer toque
 * llega tarde y dos toques seguidos se pisan.
 *
 * Los 453 audios no se precargan —serían casi 6 MB en memoria para oír tres o cuatro—: cada
 * cry se carga la primera vez que se pide y se guarda en una caché pequeña; al llenarse, se
 * descarga el que lleve más tiempo sin sonar.
 *
 * No es seguro para usar desde varios hilos: está pensado para la pantalla de Pokémon, que
 * vive entera en el hilo principal.
 */
class CryPlayer(context: Context, initialSet: CrySoundSet = CrySoundSet.LEGACY) {

    private val appContext = context.applicationContext

    private val pool = SoundPool.Builder()
        // Dos a la vez: lo justo para que tocar dos fichas seguidas no corte la primera de
        // golpe, sin llegar a que se solapen media docena y suene a ruido.
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // USAGE_MEDIA y no ASSISTANCE_SONIFICATION: los cries salen por el canal
                // multimedia, que es el que mueven los botones de volumen del teléfono. Con
                // el de sonificación iban por el canal del sistema y no había forma de
                // subirlos ni bajarlos.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    var soundSet: CrySoundSet = initialSet
        private set

    /** Cries ya cargados, del menos usado al más reciente (caché LRU). */
    private val loaded = LinkedHashMap<Int, Int>(CACHE_SIZE, 0.75f, true)

    /** Cargas pedidas y aún sin terminar: id de SoundPool -> si hay que sonarlo al llegar. */
    private val pending = mutableMapOf<Int, Boolean>()

    /**
     * Marca de la variante vigente. Al cambiar de variante sube, y las cargas pedidas con una
     * marca vieja se tiran al llegar: si no, un cry pedido justo antes del cambio terminaría
     * de cargarse después y sonaría la variante anterior.
     */
    private var generation = 0
    private val requestGeneration = mutableMapOf<Int, Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val shouldPlay = pending.remove(sampleId)
            val bornIn = requestGeneration.remove(sampleId)
            when {
                status != 0 -> {
                    Log.w(TAG, "No se pudo cargar el cry (sampleId=$sampleId, status=$status)")
                    forget(sampleId)
                }
                // Llegó tarde, después de cambiar de variante: se descarta en vez de sonar.
                bornIn != generation -> {
                    forget(sampleId)
                    pool.unload(sampleId)
                }
                shouldPlay == true -> playLoaded(sampleId)
            }
        }
    }

    /**
     * Suena el cry de [dexNumber] (1..151). Si aún no está cargado, se pide la carga y suena
     * en cuanto esté: [SoundPool.load] es asíncrono y usar su id antes de tiempo no reproduce
     * nada.
     */
    fun play(dexNumber: Int) {
        val resources = cryResourcesFor(soundSet)
        val index = dexNumber - 1
        if (index !in resources.indices) {
            Log.w(TAG, "Fuera de la Pokédex: $dexNumber")
            return
        }

        val resourceId = resources[index]
        val alreadyLoaded = loaded[resourceId]
        if (alreadyLoaded != null) {
            playLoaded(alreadyLoaded)
            return
        }

        val sampleId = pool.load(appContext, resourceId, 1)
        if (sampleId == 0) {
            Log.w(TAG, "SoundPool no aceptó la carga de $dexNumber")
            return
        }
        loaded[resourceId] = sampleId
        pending[sampleId] = true
        requestGeneration[sampleId] = generation
        trimCache()
    }

    /**
     * Cambia la variante de sonido. Vacía lo cargado de la anterior, porque los recursos son
     * distintos y mantenerlos solo ocuparía memoria de sonidos que ya no se van a pedir.
     */
    fun setSoundSet(set: CrySoundSet) {
        if (set == soundSet) return
        soundSet = set
        generation++
        // Las cargas en curso ya no valen: al terminar verán una marca vieja y se tirarán.
        pending.clear()
        for (sampleId in loaded.values) pool.unload(sampleId)
        loaded.clear()
    }

    fun release() {
        pending.clear()
        requestGeneration.clear()
        loaded.clear()
        pool.release()
    }

    private fun playLoaded(sampleId: Int) {
        pool.play(sampleId, VOLUME, VOLUME, 1, 0, 1f)
    }

    /** Quita de la caché el que lleve más tiempo sin sonar cuando se pasa del tamaño. */
    private fun trimCache() {
        while (loaded.size > CACHE_SIZE) {
            val oldest = loaded.entries.iterator()
            if (!oldest.hasNext()) return
            val entry = oldest.next()
            oldest.remove()
            // Si aún se está cargando, se deja marcado para que no suene al llegar.
            pending.remove(entry.value)
            requestGeneration.remove(entry.value)
            pool.unload(entry.value)
        }
    }

    private fun forget(sampleId: Int) {
        val key = loaded.entries.firstOrNull { it.value == sampleId }?.key ?: return
        loaded.remove(key)
    }

    private companion object {
        const val TAG = "CryPlayer"
        const val MAX_STREAMS = 2

        /** Los audios ya vienen igualados de volumen, así que se tocan tal cual. */
        const val VOLUME = 1f

        /**
         * Suficiente para moverse por el equipo tocando fichas sin recargar, y muy lejos de
         * tener los 151 de una variante en memoria.
         */
        const val CACHE_SIZE = 24
    }
}
