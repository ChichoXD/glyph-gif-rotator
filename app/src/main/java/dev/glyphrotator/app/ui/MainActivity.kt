package dev.glyphrotator.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.nothing.ketchum.Common
import dev.glyphrotator.app.R
import dev.glyphrotator.app.data.GifItem
import dev.glyphrotator.app.data.GifRepository
import dev.glyphrotator.app.databinding.ActivityMainBinding
import dev.glyphrotator.app.service.GlyphRotationService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: GifRepository
    private lateinit var adapter: GifAdapter

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                proceedEnablingRotation()
            } else {
                binding.switchRotation.isChecked = false
            }
        }


    private val openGifsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            handlePickedUris(uris)
        }

    private val openBluetoothGifLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                repository.setBluetoothGif(uri, queryDisplayName(uri) ?: uri.lastPathSegment ?: "GIF")
                refreshBluetoothGifLabel()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPager()

        repository = GifRepository(this)
        adapter = GifAdapter(onRemove = ::onRemoveGif, onPreview = ::onPreviewGif)
        binding.recyclerGifs.layoutManager = LinearLayoutManager(this)
        binding.recyclerGifs.adapter = adapter

        binding.buttonAddGif.setOnClickListener {
            openGifsLauncher.launch(arrayOf("image/gif", "image/png", "image/jpeg", "image/webp"))
        }

        refreshList()
        binding.switchRotation.isChecked = repository.isRotationEnabled
        binding.switchRotation.setOnCheckedChangeListener { _, isChecked -> onToggleRotation(isChecked) }

        binding.buttonTurnOff.setOnClickListener {
            binding.switchRotation.isChecked = false
        }

        binding.buttonRefresh.setOnClickListener {
            if (!repository.isRotationEnabled) {
                binding.switchRotation.isChecked = true
            } else {
                GlyphRotationService.refresh(this)
                Toast.makeText(this, R.string.toast_refreshed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonFixBattery.setOnClickListener {
            if (isIgnoringBatteryOptimizations()) {
                Toast.makeText(this, R.string.toast_battery_already_ok, Toast.LENGTH_SHORT).show()
                refreshBatteryOptimizationWarning()
            } else {
                requestBatteryOptimizationExemption()
            }
        }

        binding.switchClock.isChecked = repository.isClockEnabled
        binding.switchClock.setOnCheckedChangeListener { _, isChecked ->
            repository.isClockEnabled = isChecked
            // refresh() y no notifyListChanged(): esto último solo repasa la lista de GIFs y
            // no vuelve a decidir qué mostrar, así que apagar el reloj no lo quitaba de la
            // Matrix hasta el siguiente cambio de estado.
            GlyphRotationService.refresh(this)
        }

        binding.switchVinyl.isChecked = repository.isVinylEnabled
        binding.switchVinyl.setOnCheckedChangeListener { _, isChecked ->
            repository.isVinylEnabled = isChecked
            GlyphRotationService.refresh(this)
        }

        binding.buttonReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        // Lleva a la pantalla del sistema, no a un selector propio: así la elección la recuerda
        // Android, sobrevive a las actualizaciones y se ve igual que en el resto de apps.
        binding.buttonLanguage.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
            runCatching { startActivity(intent) }
                .onFailure {
                    // Algunas ROMs no exponen esa pantalla; se cae a los ajustes de la app.
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:$packageName"))
                        )
                    }
                }
        }

        setupBatteryThresholdInputs()
        setupBluetoothGifPicker()


        if (!Common.is23112()) {
            Toast.makeText(this, R.string.toast_unsupported_device, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Reparte las secciones en pestañas deslizables.
     *
     * Las páginas ya vienen infladas dentro de un contenedor oculto de la propia pantalla: se
     * sacan de ahí y se le entregan al carrusel. Hacerlo así, y no con fragmentos, es lo que
     * permite que todo el código que maneja los interruptores y los botones siga funcionando sin
     * tocar una línea — para él, las vistas son exactamente las mismas de antes.
     */
    private fun setupPager() {
        val pages = listOf(
            binding.pageStatus to R.string.tab_status,
            binding.pageModes to R.string.tab_modes,
            binding.pageLimits to R.string.tab_limits,
            binding.pageDesigns to R.string.tab_designs,
        )

        // Se sueltan del contenedor oculto: una vista solo puede tener un padre, y el suyo pasa
        // a ser el carrusel.
        binding.pageHolder.removeAllViews()
        pages.forEach { (page, _) -> page.visibility = android.view.View.VISIBLE }

        binding.pager.adapter = StaticPagerAdapter(pages.map { it.first })
        // Páginas ligeras: mantenerlas vivas evita el parpadeo al deslizar y que se pierda el
        // sitio donde estabas dentro de cada una.
        binding.pager.offscreenPageLimit = pages.size

        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.setText(pages[position].second)
        }.attach()
    }


    private companion object {
        /** La ventana de las gráficas: una semana, que es como se piensa el hábito diario. */

        /** La pareja de muestra para probar la evolución. */
        const val EVOLUTION_DEMO_FROM = 4
        const val EVOLUTION_DEMO_TO = 5
    }

    /** Las iniciales de los últimos [days] días, terminando en hoy. */

    /** Las tres fases del huevo, para verlas seguidas y comparar el movimiento. */

    override fun onResume() {
        super.onResume()
        refreshList()
        refreshBatteryOptimizationWarning()

        // Si estás viendo esta pantalla, el teléfono está encendido y desbloqueado: es el
        // momento perfecto para que el servicio recompruebe su estado y se recupere solo si
        // se había quedado pegado (por ejemplo, mostrando el reloj con el móvil en uso).
        if (repository.isRotationEnabled) {
            GlyphRotationService.refresh(this)
        }
    }

    private fun refreshList() {
        val items = repository.getAll()
        adapter.submitList(items)
        binding.textEmpty.isVisible = items.isEmpty()
        binding.recyclerGifs.isVisible = items.isNotEmpty()
    }

    private fun handlePickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            repository.addGif(uri, queryDisplayName(uri) ?: uri.lastPathSegment ?: "GIF")
        }
        refreshList()
        GlyphRotationService.notifyListChanged(this)
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Muestra ese diseño en la Matrix al instante, para poder comprobar cómo queda. */
    private fun onPreviewGif(item: GifItem) {
        if (!repository.isRotationEnabled) {
            Toast.makeText(this, R.string.toast_preview_needs_rotation, Toast.LENGTH_SHORT).show()
            return
        }
        GlyphRotationService.preview(this, item.uri)
        Toast.makeText(this, getString(R.string.toast_preview_showing, item.displayName), Toast.LENGTH_SHORT).show()
    }

    private fun onRemoveGif(item: GifItem) {
        repository.removeGif(item.id)
        refreshList()

        if (repository.getAll().isEmpty() && !repository.isClockEnabled && repository.isRotationEnabled) {
            repository.isRotationEnabled = false
            binding.switchRotation.isChecked = false
            GlyphRotationService.stop(this)
            Toast.makeText(this, R.string.toast_rotation_disabled_no_gifs, Toast.LENGTH_LONG).show()
        } else {
            GlyphRotationService.notifyListChanged(this)
        }
    }

    private fun onToggleRotation(enabled: Boolean) {
        if (!enabled) {
            repository.isRotationEnabled = false
            GlyphRotationService.stop(this)
            return
        }

        if (repository.getAll().isEmpty() && !repository.isClockEnabled) {
            Toast.makeText(this, R.string.toast_add_gif_first, Toast.LENGTH_LONG).show()
            binding.switchRotation.isChecked = false
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        proceedEnablingRotation()
    }

    private fun proceedEnablingRotation() {
        binding.switchRotation.isChecked = true
        repository.isRotationEnabled = true
        GlyphRotationService.start(this)
        maybeRequestBatteryOptimizationExemption()
        maybeRequestNotificationListenerAccess()
        maybeRequestUsageAccess()
    }

    /**
     * Necesario para saber si GlyphMuseum está en primer plano y dejarle la Matrix libre.
     * Tampoco se puede conceder por código: solo abrimos la pantalla del sistema.
     */
    private fun maybeRequestUsageAccess() {
        val appOps = getSystemService(android.app.AppOpsManager::class.java) ?: return
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode == android.app.AppOpsManager.MODE_ALLOWED) return
        Toast.makeText(this, R.string.toast_request_usage_access, Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // Algunos fabricantes/ROMs no exponen esta pantalla; no es crítico.
        }
    }

    /**
     * Necesario para que el servicio distinga música reproduciéndose de en pausa
     * (AudioManager.isMusicActive() no puede). Android no permite concederlo por código:
     * solo abrimos la pantalla del sistema, el usuario decide.
     */
    private fun maybeRequestNotificationListenerAccess() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (enabled) return
        Toast.makeText(this, R.string.toast_request_notification_access, Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // Algunos fabricantes/ROMs no exponen esta pantalla; no es crítico.
        }
    }

    private fun setupBatteryThresholdInputs() {
        binding.inputDimThreshold.setText(repository.dimBrightnessThresholdPct.toString())
        binding.inputCriticalThreshold.setText(repository.criticalBatteryThresholdPct.toString())

        val watcher = { save: () -> Unit ->
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = save()
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
        }

        binding.inputDimThreshold.addTextChangedListener(watcher {
            val value = binding.inputDimThreshold.text?.toString()?.toIntOrNull() ?: return@watcher
            if (value <= repository.criticalBatteryThresholdPct) {
                Toast.makeText(this, R.string.toast_invalid_battery_thresholds, Toast.LENGTH_SHORT).show()
                return@watcher
            }
            repository.dimBrightnessThresholdPct = value
        })

        binding.inputCriticalThreshold.addTextChangedListener(watcher {
            val value = binding.inputCriticalThreshold.text?.toString()?.toIntOrNull() ?: return@watcher
            if (value >= repository.dimBrightnessThresholdPct) {
                Toast.makeText(this, R.string.toast_invalid_battery_thresholds, Toast.LENGTH_SHORT).show()
                return@watcher
            }
            repository.criticalBatteryThresholdPct = value
        })
    }

    private fun setupBluetoothGifPicker() {
        refreshBluetoothGifLabel()
        binding.buttonPickBluetoothGif.setOnClickListener {
            openBluetoothGifLauncher.launch(arrayOf("image/gif", "image/png", "image/jpeg", "image/webp"))
        }
        binding.textBluetoothGifSelected.setOnClickListener {
            if (repository.bluetoothGifUri != null) {
                repository.clearBluetoothGif()
                refreshBluetoothGifLabel()
            }
        }
    }

    private fun refreshBluetoothGifLabel() {
        val name = repository.bluetoothGifName
        binding.textBluetoothGifSelected.text = if (name != null) {
            getString(R.string.label_bluetooth_gif_selected, name)
        } else {
            getString(R.string.label_bluetooth_gif_none)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: false

    private fun maybeRequestBatteryOptimizationExemption() {
        if (isIgnoringBatteryOptimizations()) return
        requestBatteryOptimizationExemption()
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (e: ActivityNotFoundException) {
            // Algunos fabricantes/ROMs no exponen esta pantalla; caemos a la de ajustes de la app.
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e2: ActivityNotFoundException) {
                // Sin pantalla a la que ir; el aviso de la app seguirá visible.
            }
        }
    }

    /**
     * Muestra el aviso solo si Android todavía puede matar el servicio. Es la causa más
     * habitual de que la rotación aparezca "muerta" después de horas sin tocar el teléfono.
     */
    private fun refreshBatteryOptimizationWarning() {
        // Se oculta la tarjeta entera, no solo el texto: si no, quedaría un recuadro vacío
        // con su borde rojo ocupando sitio cuando ya no hay nada que avisar.
        binding.cardBatteryWarning.isVisible = !isIgnoringBatteryOptimizations()
    }
}
