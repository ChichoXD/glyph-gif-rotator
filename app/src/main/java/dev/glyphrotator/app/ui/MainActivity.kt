package dev.glyphrotator.app.ui

import android.Manifest
import dev.glyphrotator.app.BuildConfig
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
import androidx.activity.addCallback
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.nothing.ketchum.Common
import dev.glyphrotator.app.R
import dev.glyphrotator.app.data.AppPreferences
import dev.glyphrotator.app.data.GifItem
import dev.glyphrotator.app.pokemon.CryPreferences
import dev.glyphrotator.app.pokemon.CrySoundSet
import dev.glyphrotator.app.data.GifRepository
import dev.glyphrotator.app.databinding.ActivityMainBinding
import dev.glyphrotator.app.glyph.EggAnimation
import dev.glyphrotator.app.habits.UsageStore
import dev.glyphrotator.app.pokemon.spawn.SleepTracker
import dev.glyphrotator.app.glyph.EvolutionAnimation
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

    /**
     * El permiso de ubicación, que es lo único que le faltaba al clima para funcionar.
     *
     * Sin él [dev.glyphrotator.app.pokemon.spawn.WeatherProvider] no sabe dónde estás y se
     * queda en "despejado" para siempre: los multiplicadores de lluvia, tormenta y nieve
     * existen en el código pero no se activan nunca.
     */
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Toast.makeText(
                this,
                if (granted) R.string.toast_weather_enabled else R.string.toast_weather_denied,
                Toast.LENGTH_LONG
            ).show()
            refreshWeatherButton()
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

        // El título va en puntos, con la misma fuente que la Matrix de atrás.
        binding.textTitle.text = getString(R.string.main_title)

        // El tutorial se abre solo la primera vez; despues, solo si se pide.
        binding.buttonHelp.setOnClickListener {
            TutorialActivity.open(this, Tutorial.Which.ROTATION)
        }
        TutorialActivity.openIfFirstRun(this, Tutorial.Which.ROTATION)

        setupPager()
        setupPokedex()
        setupSettings()

        // El atrás cierra los ajustes antes de cerrar la app: si saliera del todo, la tuerca
        // sería un billete de ida. Se registra una sola vez aquí, no dentro de setupPager(), que
        // se vuelve a llamar cada vez que se activa o desactiva el mod de Pokémon.
        onBackPressedDispatcher.addCallback(this) {
            if (binding.settingsOverlay.isVisible) showSettings(false) else finish()
        }

        repository = GifRepository(this)
        adapter = GifAdapter(onRemove = ::onRemoveGif, onPreview = ::onPreviewGif)
        // En rejilla y no en lista: los diseños se distinguen por cómo se ven, no por cómo se
        // llaman, y en renglones había que leerlos uno a uno con una miniatura de 48 dp que no
        // servía para reconocer nada. Tres columnas y no cuatro porque debajo va el nombre: con
        // cuatro se queda en un par de sílabas cortadas.
        binding.recyclerGifs.layoutManager = GridLayoutManager(this, DESIGN_COLUMNS)
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

        setupBatteryThresholdInputs()
        setupBluetoothGifPicker()

        binding.buttonTestCatch.setOnClickListener {
            // Pikachu como muestra: así el botón de prueba enseña la animación completa (Pokémon
            // moviéndose + bola cerrándose encima) y no solo el círculo suelto.
            GlyphRotationService.catchAnimation(this, dexNumber = 25)
        }

        binding.buttonTestEgg.setOnClickListener { showEggStagePicker() }
        binding.buttonBalance.setOnClickListener {
            startActivity(android.content.Intent(this, BalanceActivity::class.java))
        }

        binding.buttonWeather.setOnClickListener {
            if (hasLocationPermission()) {
                Toast.makeText(this, R.string.toast_weather_enabled, Toast.LENGTH_SHORT).show()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        refreshWeatherButton()

        binding.buttonOpenPokemon.setOnClickListener {
            startActivity(Intent(this, PokemonActivity::class.java))
        }

        if (!Common.is23112() && !Common.is25111p()) {
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
    /**
     * La rejilla de la Pokédex.
     *
     * Se llena al arrancar y se refresca al volver a la pantalla: capturar pasa por la Matrix o
     * por la pantalla de Pokémon, así que cuando se vuelve aquí puede haber una especie más.
     */
    /**
     * Uno solo para toda la pantalla.
     *
     * `CryPlayer` monta un `SoundPool` por dentro, así que crear uno en cada toque dejaría
     * reproductores sueltos hasta que el recolector se acordara de ellos.
     */
    private val cryPlayer by lazy { dev.glyphrotator.app.pokemon.CryPlayer(this) }

    private val pokedexAdapter by lazy {
        PokedexAdapter(
            library = dev.glyphrotator.app.pokemon.SpriteLibrary(this),
            onSeen = { dexNumber -> cryPlayer.play(dexNumber) }
        )
    }

    private fun setupPokedex() {
        binding.pagePokedex.layoutManager =
            androidx.recyclerview.widget.GridLayoutManager(this, POKEDEX_COLUMNS)
        binding.pagePokedex.adapter = pokedexAdapter
        refreshPokedex()
    }

    private fun refreshPokedex() {
        pokedexAdapter.submitSeen(
            dev.glyphrotator.app.pokemon.PokemonRepository(this).getPokedex()
        )
    }

    private fun setupPager() {
        // Cada pestaña con su página, su nombre y su icono.
        //
        // El icono es lo que permite que ocho quepan arriba sin que la fila se lea como una tira
        // indistinguible: con solo texto había que ir descifrando etiqueta por etiqueta para
        // encontrar la que buscabas, y ese era todo el problema — nunca fue el número.
        val pokemonModEnabled = AppPreferences(this).pokemonModEnabled
        val allPages = listOf(
            Triple(binding.pageStatus, R.string.tab_status, R.drawable.ic_min_glyph),
            Triple(binding.pageModes, R.string.tab_modes, R.drawable.ic_min_visuals),
            Triple(binding.pageLimits, R.string.tab_limits, R.drawable.ic_min_battery),
            Triple(binding.pagePokedex, R.string.tab_pokedex, R.drawable.ic_min_pokedex),
            Triple(binding.pageGame, R.string.tab_game, R.drawable.ic_min_pokeball),
            Triple(binding.pageUsage, R.string.tab_usage, R.drawable.ic_min_stats),
            Triple(binding.pageDesigns, R.string.tab_designs, R.drawable.ic_min_designs),
        )
        // La Pokédex y el Juego solo existen con el mod activado: la app base es rotación, nada
        // más.
        val pokemonOnly = setOf(binding.pagePokedex, binding.pageGame)
        val pages = allPages.filter { (page, _, _) -> page !in pokemonOnly || pokemonModEnabled }

        // Esto se puede llamar más de una vez —al activar o desactivar el mod—, así que cada
        // página se suelta de donde esté ahora: la primera vez todas viven en `pageHolder`
        // (incluida la de Ajustes), las siguientes veces las de pestañas ya viven dentro del
        // carrusel viejo. Una vista con padre revienta al añadirla a un sitio nuevo.
        binding.pageHolder.removeAllViews()
        allPages.forEach { (page, _, _) ->
            (page.parent as? android.view.ViewGroup)?.removeView(page)
        }
        pages.forEach { (page, _, _) -> page.visibility = android.view.View.VISIBLE }

        // Los ajustes ya no son una pestaña: se quedan fuera del carrusel, en su propio hueco, y
        // se llega a ellos por la tuerca de la cabecera. También se suelta de donde esté antes de
        // añadirla: la segunda vez ya está dentro de `settingsOverlay`, no de `pageHolder`.
        (binding.pageSettings.parent as? android.view.ViewGroup)?.removeView(binding.pageSettings)
        binding.pageSettings.visibility = android.view.View.VISIBLE
        binding.settingsOverlay.addView(binding.pageSettings)

        binding.pager.adapter = StaticPagerAdapter(pages.map { it.first })
        // Páginas ligeras: mantenerlas vivas evita el parpadeo al deslizar y que se pierda el
        // sitio donde estabas dentro de cada una.
        binding.pager.offscreenPageLimit = pages.size

        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            val (_, label, icon) = pages[position]
            tab.customView = layoutInflater
                .inflate(R.layout.view_tab_icon, binding.tabs, false)
                .apply {
                    findViewById<android.widget.ImageView>(R.id.tabIcon).setImageResource(icon)
                    findViewById<android.widget.TextView>(R.id.tabLabel).setText(label)
                }
            // El nombre hace falta igual para TalkBack. La vista personalizada no es un `setText`,
            // así que sin esto la pestaña se anuncia como "pestaña 3 de 8" y nada más: quien la
            // escuche en vez de verla se queda sin saber a dónde va.
            tab.contentDescription = getString(label)
        }.attach()

        binding.buttonSettings.setOnClickListener { showSettings(true) }
    }

    /**
     * Cambia entre el carrusel de pestañas y la pantalla de ajustes.
     *
     * Se esconde también la fila de pestañas: dejarla puesta mientras se ven los ajustes diría que
     * sigues dentro del carrusel, y con ninguna encendida además no se sabría en cuál estás.
     */
    private fun showSettings(visible: Boolean) {
        binding.settingsOverlay.isVisible = visible
        binding.pager.isVisible = !visible
        binding.tabs.isVisible = !visible
    }

    /**
     * La pestaña de Ajustes: todo lo que se elige, junto.
     *
     * Antes estaba repartido —el sonido en la pantalla de Pokémon, el agua dentro del diálogo del
     * agua, el horario de sueño detrás de dos toques—. Cada cosa se cambiaba donde se usaba, que
     * está bien para tocarla al vuelo y fatal para saber qué se puede configurar.
     */
    private fun setupSettings() {
        val preferences = AppPreferences(this)

        setupVersionUnlock(preferences)

        binding.switchPokemonMod.isChecked = preferences.pokemonModEnabled
        applyPokemonModVisibility(preferences.pokemonModEnabled)
        binding.switchPokemonMod.setOnCheckedChangeListener { _, checked ->
            preferences.pokemonModEnabled = checked
            applyPokemonModVisibility(checked)
            // Las pestañas de Pokédex y Juego aparecen o desaparecen del carrusel al momento, sin
            // pedir que se reabra la app.
            setupPager()
        }

        binding.switchWidgetColor.isChecked = preferences.widgetColor
        binding.switchWidgetColor.setOnCheckedChangeListener { _, checked ->
            preferences.widgetColor = checked
            refreshCollectionWidgets()
        }

        binding.switchWidgetAnimated.isChecked = preferences.widgetAnimated
        binding.switchWidgetAnimated.setOnCheckedChangeListener { _, checked ->
            preferences.widgetAnimated = checked
            refreshCollectionWidgets()
        }

        binding.switchCryOnTap.isChecked = preferences.cryOnTap
        binding.switchCryOnTap.setOnCheckedChangeListener { _, checked ->
            preferences.cryOnTap = checked
        }

        binding.switchShowOnTap.isChecked = preferences.showOnMatrixOnTap
        binding.switchShowOnTap.setOnCheckedChangeListener { _, checked ->
            preferences.showOnMatrixOnTap = checked
        }

        binding.switchPartnerOnButton.isChecked = preferences.partnerOnGlyphButton
        binding.switchPartnerOnButton.setOnCheckedChangeListener { _, checked ->
            preferences.partnerOnGlyphButton = checked
        }

        binding.buttonReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        binding.buttonCrySet.setOnClickListener { chooseCrySoundSet() }
        binding.buttonSettingsWater.setOnClickListener { openPokemonScreen(PokemonActivity.Open.WATER) }
        binding.buttonSettingsSleep.setOnClickListener { openPokemonScreen(PokemonActivity.Open.SLEEP) }
        binding.buttonSettingsHabits.setOnClickListener { openPokemonScreen(PokemonActivity.Open.HABITS) }
    }

    /**
     * Oculta también la sección "Seguimiento" (agua, sueño, hábitos) cuando el mod está apagado.
     *
     * No es solo cosmético: esos trackers **no hacen nada por sí solos**. Su único efecto es
     * alimentar el multiplicador de entrenamiento del compañero — `DailyBonus.sleepFactor`,
     * `waterFactor`, `habitFactor`—, así que enseñárselos a quien no tiene el juego activado es
     * un menú de ajustes para una función que no existe.
     */
    private fun applyPokemonModVisibility(enabled: Boolean) {
        binding.groupPokemonModOptions.isVisible = enabled
        binding.headerSettingsHabits.isVisible = enabled
        binding.cardSettingsHabits.isVisible = enabled
    }

    /**
     * La versión al pie de Ajustes, y el gesto que desbloquea el modo Pokémon.
     *
     * Siete toques, como las opciones de desarrollador de Android — el mismo gesto que ya conoce
     * cualquiera que haya activado la depuración USB en su móvil.
     *
     * Se hace con un gesto y no con una clave escrita porque hace lo mismo con mucho menos: sin
     * campo de texto, sin validación, sin guardar contraseñas ni estados de error. Y no se
     * encuentra por accidente, que es todo lo que se pedía: el juego aún no está confirmado en
     * hardware y un betatester que tropiece con él reporta fallos de algo sin terminar.
     */
    private fun setupVersionUnlock(preferences: AppPreferences) {
        binding.textVersion.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        applyPokemonSectionVisibility(preferences.pokemonModUnlocked)
        if (preferences.pokemonModUnlocked) return

        var taps = 0
        binding.textVersion.setOnClickListener {
            taps++
            when {
                taps >= UNLOCK_TAPS -> {
                    preferences.pokemonModUnlocked = true
                    applyPokemonSectionVisibility(true)
                    Toast.makeText(this, R.string.toast_pokemon_unlocked, Toast.LENGTH_LONG).show()
                }
                // Solo se avisa cerca del final: antes de eso, un contador visible convertiría
                // un gesto escondido en un botón con instrucciones.
                taps >= UNLOCK_TAPS - HINT_FROM_REMAINING -> {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_pokemon_unlock_hint, UNLOCK_TAPS - taps),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /** Enseña o esconde la sección entera de Pokémon en Ajustes (cabecera, tarjeta y pista). */
    private fun applyPokemonSectionVisibility(unlocked: Boolean) {
        binding.headerSettingsPokemon.isVisible = unlocked
        binding.cardSettingsPokemon.isVisible = unlocked
        if (!unlocked) {
            binding.headerSettingsHabits.isVisible = false
            binding.cardSettingsHabits.isVisible = false
        }
    }

    private fun openPokemonScreen(what: String) {
        startActivity(
            Intent(this, PokemonActivity::class.java)
                .putExtra(PokemonActivity.Open.EXTRA, what)
        )
    }

    /** El widget de la colección es el único que depende del color y de la animación. */
    private fun refreshCollectionWidgets() {
        dev.glyphrotator.app.ui.widget.StatWidgetProvider.refresh(
            this,
            dev.glyphrotator.app.ui.widget.CollectionWidgetProvider::class.java
        )
    }

    /** La variante de sonido de los cries, sin salir de Ajustes. */
    private fun chooseCrySoundSet() {
        val preferences = CryPreferences(this)
        val options = CrySoundSet.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.cry_choose)
            .setSingleChoiceItems(
                options.map { getString(it.labelRes) }.toTypedArray(),
                options.indexOf(preferences.soundSet),
            ) { dialog, which ->
                preferences.soundSet = options[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** El botón cambia de texto y se apaga cuando ya no hay nada que pedir. */
    private fun refreshWeatherButton() {
        val granted = hasLocationPermission()
        binding.buttonWeather.setText(
            if (granted) R.string.button_weather_active else R.string.button_enable_weather
        )
        binding.buttonWeather.isEnabled = !granted
    }

    /**
     * La pestaña de uso: cuánto miras el teléfono y cuánto duermes.
     *
     * Aquí y no con los hábitos a propósito. Los hábitos son cosas que **decides** hacer; esto es
     * lo que haces sin decidirlo, y mezclarlos convertiría el registro en una lista de reproches.
     *
     * Se refresca en onResume porque el dato de hoy crece mientras miras la pantalla: si se
     * calculara solo al abrir la app, el número estaría viejo desde el primer segundo.
     */
    private fun refreshUsage() {
        val usage = UsageStore(this)
        val sleep = SleepTracker(this)

        val minutes = usage.minutesToday()
        binding.textUsageToday.text = getString(
            R.string.usage_today,
            minutes / 60,
            minutes % 60,
            usage.unlocksToday(),
            usage.averageMinutes(USAGE_DAYS) / 60,
            usage.averageMinutes(USAGE_DAYS) % 60,
        )

        val labels = weekdayLabels(USAGE_DAYS)

        // El tiempo de pantalla va en curva: la pregunta aquí es "¿voy a más o a menos?", no
        // "¿cuánto el martes?". Ver SplineChartView. Al tocar un día sale su dato en una burbuja,
        // que es lo que la curva sola no puede dar: la forma se lee de un vistazo, el número no.
        binding.chartUsageMinutes.labelFormatter = { minutes ->
            getString(R.string.usage_chart_value, minutes.toInt() / 60, minutes.toInt() % 60)
        }
        binding.chartUsageMinutes.setData(
            usage.minutesHistory(USAGE_DAYS).map { it.toFloat() },
            labels = labels,
        )

        // Los desbloqueos, contra los de la semana pasada. Se piden catorce días de golpe y se
        // parten por la mitad: los siete primeros son la semana vieja y los siete últimos, esta.
        val unlocks = usage.unlocksHistory(COMPARE_DAYS).map { it.toFloat() }
        binding.chartUsageUnlocks.setData(
            current = unlocks.takeLast(USAGE_DAYS),
            previous = unlocks.take(USAGE_DAYS),
            labels = labels,
        )

        // El sueño, en calendario y no en barras: de un vistazo importa qué noches se llegó al
        // objetivo y si hay racha, no cuántos minutos exactos hizo el jueves. La intensidad es
        // lo dormido sobre lo propuesto, así que una noche completa enciende el punto entero.
        val goal = sleep.goalMinutes
        // Los días se piden alineados a semanas enteras, no un número redondo: con 28 sueltos la
        // fila del domingo se descolgaba una columna a la izquierda. Ver DotCalendarView.daysForWeeks.
        binding.calendarSleep.setData(
            sleep.minutesHistory(DotCalendarView.daysForWeeks(SLEEP_CALENDAR_WEEKS)).map { minutes ->
                if (goal <= 0) 0f else (minutes.toFloat() / goal).coerceIn(0f, 1f)
            },
            DotCalendarView.todayWeekday(),
        )

        val average = sleep.averageMinutes(USAGE_DAYS)
        binding.textSleepSummary.text = if (sleep.nightsRecorded == 0) {
            getString(R.string.usage_sleep_empty)
        } else {
            getString(
                R.string.usage_sleep_summary,
                average / 60,
                average % 60,
                goal / 60,
                goal % 60,
            )
        }
    }

    private companion object {
        /** Toques en la versión para desbloquear el mod, como las opciones de desarrollador. */
        const val UNLOCK_TAPS = 7

        /** A cuántos toques del final se empieza a avisar. */
        const val HINT_FROM_REMAINING = 3

        /** Tres columnas: con cuatro el nombre no cabe y con dos se hace eterna. */
        const val POKEDEX_COLUMNS = 3

        /**
         * Cuatro columnas en la galería de diseños, como en la referencia.
         *
         * Caben cuatro porque los tiles van **sin nombre debajo**: si llevaran etiqueta habría que
         * bajar a tres para que el texto no se quedara en dos sílabas cortadas.
         */
        const val DESIGN_COLUMNS = 4

        /** La ventana de las gráficas: una semana, que es como se piensa el hábito diario. */
        const val USAGE_DAYS = 7

        /** Dos semanas: la de ahora y la de antes, que es contra lo que se compara. */
        const val COMPARE_DAYS = USAGE_DAYS * 2

        /**
         * Cuatro semanas de sueño. Con una no se ve ninguna racha —una semana mala y una buena son
         * la misma imagen— y con nueve, como en el registro, los puntos salen diminutos dentro de
         * una tarjeta.
         *
         * En semanas y no en días sueltos: los días se cuentan con `daysForWeeks`, que alinea la
         * ventana para que todas las filas empiecen en la misma columna.
         */
        const val SLEEP_CALENDAR_WEEKS = 4

        /** La pareja de muestra para probar la evolución. */
        const val EVOLUTION_DEMO_FROM = 4
        const val EVOLUTION_DEMO_TO = 5
    }

    /** Las iniciales de los últimos [days] días, terminando en hoy. */
    private fun weekdayLabels(days: Int): List<String> {
        val initials = arrayOf("L", "M", "X", "J", "V", "S", "D")
        val today = java.time.LocalDate.now()
        return (days - 1 downTo 0).map { offset ->
            initials[today.minusDays(offset.toLong()).dayOfWeek.value - 1]
        }
    }

    /** Las tres fases del huevo, para verlas seguidas y comparar el movimiento. */
    private fun showEggStagePicker() {
        val stages = listOf(
            EggAnimation.Stage.FRESH to R.string.egg_stage_fresh,
            EggAnimation.Stage.WARM to R.string.egg_stage_warm,
            EggAnimation.Stage.HATCHING to R.string.egg_stage_hatching,
        )
        val labels = stages.map { getString(it.second) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_egg_stage_title)
            .setItems(labels) { _, which ->
                val (stage, labelRes) = stages[which]
                GlyphRotationService.eggAnimation(this, stage)
                Toast.makeText(
                    this,
                    getString(R.string.toast_egg_playing, getString(labelRes)),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        refreshUsage()
        refreshBatteryOptimizationWarning()
        refreshPokedex()

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
