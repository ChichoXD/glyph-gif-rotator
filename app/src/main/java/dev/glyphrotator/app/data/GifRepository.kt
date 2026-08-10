package dev.glyphrotator.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.UUID

/**
 * Persiste la lista de GIFs elegidos por el usuario (como permisos URI persistentes de
 * Storage Access Framework) y el estado de la rotación, en SharedPreferences.
 */
class GifRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isRotationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Incluye un diseño de reloj (con batería) como una opción más de la rotación. */
    var isClockEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOCK_ENABLED, value).apply()

    /** El disco de vinilo mientras suena música. Activado por defecto, como estaba antes. */
    var isVinylEnabled: Boolean
        get() = prefs.getBoolean(KEY_VINYL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VINYL_ENABLED, value).apply()

    /** Por debajo de este % de batería se atenúa el brillo de la Matrix. */
    var dimBrightnessThresholdPct: Int
        get() = prefs.getInt(KEY_DIM_THRESHOLD, DEFAULT_DIM_THRESHOLD_PCT)
        set(value) = prefs.edit().putInt(KEY_DIM_THRESHOLD, value.coerceIn(0, 100)).apply()

    /** Por debajo de este % de batería se apaga la Matrix por completo. */
    var criticalBatteryThresholdPct: Int
        get() = prefs.getInt(KEY_CRITICAL_THRESHOLD, DEFAULT_CRITICAL_THRESHOLD_PCT)
        set(value) = prefs.edit().putInt(KEY_CRITICAL_THRESHOLD, value.coerceIn(0, 100)).apply()

    /** GIF/imagen que se muestra unos segundos al conectarse un dispositivo Bluetooth. */
    val bluetoothGifUri: Uri?
        get() = prefs.getString(KEY_BLUETOOTH_GIF_URI, null)?.let { Uri.parse(it) }

    val bluetoothGifName: String?
        get() = prefs.getString(KEY_BLUETOOTH_GIF_NAME, null)

    fun setBluetoothGif(uri: Uri, displayName: String) {
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo persistir el permiso de lectura para $uri", e)
        }
        clearBluetoothGif()
        prefs.edit()
            .putString(KEY_BLUETOOTH_GIF_URI, uri.toString())
            .putString(KEY_BLUETOOTH_GIF_NAME, displayName)
            .apply()
    }

    fun clearBluetoothGif() {
        val previous = bluetoothGifUri
        if (previous != null) {
            try {
                appContext.contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "No se pudo liberar el permiso de lectura para $previous", e)
            }
        }
        prefs.edit().remove(KEY_BLUETOOTH_GIF_URI).remove(KEY_BLUETOOTH_GIF_NAME).apply()
    }

    fun getAll(): List<GifItem> =
        currentIds().mapNotNull { id ->
            val uriString = prefs.getString(uriKey(id), null) ?: return@mapNotNull null
            val name = prefs.getString(nameKey(id), uriString) ?: uriString
            GifItem(id, Uri.parse(uriString), name)
        }

    fun addGif(uri: Uri, displayName: String): GifItem {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo persistir el permiso de lectura para $uri", e)
        }

        val id = UUID.randomUUID().toString()
        val ids = currentIds() + id
        prefs.edit()
            .putString(KEY_IDS, ids.joinToString(SEPARATOR))
            .putString(uriKey(id), uri.toString())
            .putString(nameKey(id), displayName)
            .apply()
        return GifItem(id, uri, displayName)
    }

    fun removeGif(id: String) {
        val item = getAll().find { it.id == id }
        if (item != null) {
            try {
                appContext.contentResolver.releasePersistableUriPermission(
                    item.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "No se pudo liberar el permiso de lectura para ${item.uri}", e)
            }
        }

        val ids = currentIds() - id
        val editor = prefs.edit()
            .putString(KEY_IDS, ids.joinToString(SEPARATOR))
            .remove(uriKey(id))
            .remove(nameKey(id))
        if (prefs.getString(KEY_LAST_GIF_ID, null) == id) {
            editor.remove(KEY_LAST_GIF_ID)
        }
        editor.apply()
    }

    fun setLastGifId(id: String) {
        prefs.edit().putString(KEY_LAST_GIF_ID, id).apply()
    }

    fun getLastGifId(): String? = prefs.getString(KEY_LAST_GIF_ID, null)

    private fun currentIds(): List<String> =
        (prefs.getString(KEY_IDS, "") ?: "").split(SEPARATOR).filter { it.isNotBlank() }

    private fun uriKey(id: String) = "uri_$id"
    private fun nameKey(id: String) = "name_$id"

    private companion object {
        const val TAG = "GifRepository"
        const val PREFS_NAME = "glyph_rotator_prefs"
        const val KEY_IDS = "gif_ids"
        const val KEY_ENABLED = "rotation_enabled"
        const val KEY_LAST_GIF_ID = "last_gif_id"
        const val KEY_CLOCK_ENABLED = "clock_enabled"
        const val KEY_VINYL_ENABLED = "vinyl_enabled"
        const val KEY_DIM_THRESHOLD = "battery_dim_threshold_pct"
        const val KEY_CRITICAL_THRESHOLD = "battery_critical_threshold_pct"
        const val DEFAULT_DIM_THRESHOLD_PCT = 40
        const val DEFAULT_CRITICAL_THRESHOLD_PCT = 15
        const val KEY_BLUETOOTH_GIF_URI = "bluetooth_gif_uri"
        const val KEY_BLUETOOTH_GIF_NAME = "bluetooth_gif_name"
        const val SEPARATOR = ","
    }
}
