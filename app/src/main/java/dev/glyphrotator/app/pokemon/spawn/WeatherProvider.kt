package dev.glyphrotator.app.pokemon.spawn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL

/**
 * El tiempo que hace donde estás, para las apariciones.
 *
 * Guarda el último valor y solo vuelve a preguntar cada [REFRESH_INTERVAL_MS]: el tiempo no
 * cambia de un minuto a otro, y las apariciones se comprueban cada minuto. Consultarlo cada
 * vez sería gastar batería y datos para nada.
 *
 * Si no hay permiso de ubicación, o no hay red, se queda en despejado — que es el caso neutro
 * y deja el juego funcionando igual, solo que sin los multiplicadores del clima.
 */
class WeatherProvider(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cached: Weather = Weather.CLEAR

    @Volatile
    private var lastFetchMillis = 0L

    /** Lo último que se sabe. No bloquea: quien lo llama no espera a la red. */
    val current: Weather get() = cached

    /** Si el usuario fijó unas coordenadas a mano, en vez de usar la ubicación del sistema. */
    var manualLatitude: Double
        get() = prefs.getFloat(KEY_LATITUDE, Float.NaN).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LATITUDE, value.toFloat()).apply()

    var manualLongitude: Double
        get() = prefs.getFloat(KEY_LONGITUDE, Float.NaN).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LONGITUDE, value.toFloat()).apply()

    /**
     * Consulta el tiempo si toca. Hay que llamarlo desde un hilo de fondo: hace red.
     *
     * Devuelve true si se actualizó, para poder distinguir "no tocaba" de "falló".
     */
    fun refreshIfDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (nowMillis - lastFetchMillis < REFRESH_INTERVAL_MS && lastFetchMillis > 0L) return false

        val coordinates = resolveCoordinates() ?: return false
        // Se marca aunque falle: si no hay red, reintentar cada minuto no arregla nada y sí
        // gasta batería.
        lastFetchMillis = nowMillis

        val weather = fetch(coordinates.first, coordinates.second) ?: return false
        cached = weather
        return true
    }

    private fun fetch(latitude: Double, longitude: Double): Weather? = runCatching {
        val connection = (URL(WeatherCodes.currentWeatherUrl(latitude, longitude)).openConnection()
            as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            WeatherCodes.parseWeatherCode(body)?.let(WeatherCodes::toWeather)
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "No se pudo consultar el tiempo", it) }.getOrNull()

    /**
     * Dónde estamos: primero lo que haya fijado el usuario, y si no la última posición
     * conocida del sistema.
     *
     * Se usa la última conocida y no una lectura nueva a propósito: pedir una posición fresca
     * enciende el GPS, y para saber si llueve sobra con estar en la ciudad correcta.
     */
    private fun resolveCoordinates(): Pair<Double, Double>? {
        val manualLat = manualLatitude
        val manualLon = manualLongitude
        if (!manualLat.isNaN() && !manualLon.isNaN()) return manualLat to manualLon

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val manager = appContext.getSystemService(LocationManager::class.java) ?: return null
        val location = runCatching {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return null

        return location.latitude to location.longitude
    }

    private companion object {
        const val TAG = "WeatherProvider"
        const val PREFS_NAME = "glyph_weather"
        const val KEY_LATITUDE = "manual_lat"
        const val KEY_LONGITUDE = "manual_lon"

        /** Media hora: el tiempo no cambia más rápido que eso a efectos del juego. */
        const val REFRESH_INTERVAL_MS = 30L * 60 * 1000
        const val TIMEOUT_MS = 8_000
    }
}
