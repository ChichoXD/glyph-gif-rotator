package dev.glyphrotator.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.glyphrotator.app.R
import dev.glyphrotator.app.data.AppPreferences
import dev.glyphrotator.app.data.GifRepository
import dev.glyphrotator.app.databinding.ActivityPokemonBinding
import dev.glyphrotator.app.pokemon.CaughtPokemon
import dev.glyphrotator.app.pokemon.CryPlayer
import dev.glyphrotator.app.pokemon.EvolutionResolver
import dev.glyphrotator.app.pokemon.CryPreferences
import dev.glyphrotator.app.pokemon.CrySoundSet
import dev.glyphrotator.app.pokemon.PokemonGame
import dev.glyphrotator.app.pokemon.PokemonItem
import dev.glyphrotator.app.pokemon.PokemonRegistry
import dev.glyphrotator.app.pokemon.EggRules
import dev.glyphrotator.app.pokemon.EggStore
import dev.glyphrotator.app.pokemon.PokemonRepository
import dev.glyphrotator.app.glyph.DotIcons
import dev.glyphrotator.app.habits.DailyBonus
import dev.glyphrotator.app.habits.HabitRules
import dev.glyphrotator.app.habits.HabitStore
import dev.glyphrotator.app.habits.WaterStore
import dev.glyphrotator.app.pokemon.spawn.SleepSchedule
import dev.glyphrotator.app.pokemon.spawn.WildSpawnStore
import dev.glyphrotator.app.ui.widget.BonusWidget
import dev.glyphrotator.app.ui.widget.HabitsWidget
import dev.glyphrotator.app.ui.widget.StatWidgetProvider
import dev.glyphrotator.app.pokemon.spawn.SleepTracker
import dev.glyphrotator.app.pokemon.SpriteLibrary
import dev.glyphrotator.app.pokemon.SpriteRenderModes
import dev.glyphrotator.app.pokemon.TrainingRules
import dev.glyphrotator.app.service.GlyphRotationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla del juego: equipo capturado, compañero de entrenamiento, inventario y uso de
 * objetos. Sin sprites: cada Pokémon se muestra por número de Pokédex, nombre y tipos.
 */
class PokemonActivity : AppCompatActivity() {

    // Medidas desde dimens.xml. Estaban escritas como píxeles sueltos dentro de cada
    // setPadding, así que ni escalaban ni había dos diálogos con el mismo margen.
    private val buttonIconPx by lazy { dimen(R.dimen.dot_icon_small) }
    private val dialogPaddingPx by lazy { dimen(R.dimen.space_l) }
    private val headerPaddingPx by lazy { dimen(R.dimen.screen_padding) }
    private val gapPx by lazy { dimen(R.dimen.space_s) }
    private val gapMediumPx by lazy { dimen(R.dimen.space_m) }
    private val gapTinyPx by lazy { dimen(R.dimen.space_xxs) }

    private lateinit var binding: ActivityPokemonBinding
    private lateinit var repository: PokemonRepository
    private lateinit var game: PokemonGame
    private lateinit var adapter: PokemonAdapter
    private lateinit var library: SpriteLibrary
    private lateinit var cryPlayer: CryPlayer
    private lateinit var cryPreferences: CryPreferences
    private lateinit var eggs: EggStore
    private lateinit var sleep: SleepTracker
    private lateinit var water: WaterStore
    private lateinit var habits: HabitStore

    private companion object {
        /** El cry de muestra al cambiar de variante: es el que cualquiera reconoce al vuelo. */
        const val PIKACHU = 25

        /** Lo que se queda el Pokémon en la Matrix al tocar su ficha. */
        const val SHOW_ON_MATRIX_MS = 8_000L

        /** Margen para que el servicio guarde antes de releer el estado. */
        const val WILD_RESOLVE_REFRESH_MS = 700L

        /** Ventanas de las estadísticas: la semana y el mes. */
        const val WEEK_DAYS = 7
        const val MONTH_DAYS = 30

    }

    /**
     * Con qué diálogo abrirse, para poder llegar desde Ajustes.
     *
     * Se abre esta pantalla y se lanza el diálogo, en vez de duplicarlo en Ajustes: el mismo
     * diálogo en dos sitios acaba siendo dos diálogos distintos en cuanto uno se toca.
     */
    object Open {
        const val EXTRA = "abrir"
        const val WATER = "agua"
        const val SLEEP = "sueno"
        const val HABITS = "habitos"
    }

    /**
     * Elegir con qué sonido suenan los cries. Al cambiar se prueba con Pikachu, que es el que
     * todo el mundo reconoce: así se nota la diferencia entre variantes sin buscar una ficha.
     */
    private fun chooseCrySoundSet() {
        val options = CrySoundSet.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.cry_choose)
            .setSingleChoiceItems(
                options.map { getString(it.labelRes) }.toTypedArray(),
                options.indexOf(cryPlayer.soundSet),
            ) { dialog, which ->
                val chosen = options[which]
                cryPlayer.setSoundSet(chosen)
                cryPreferences.soundSet = chosen
                cryPlayer.play(PIKACHU)
                refreshCryButton()
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Tocar una ficha: el Pokémon sale en la Matrix y suena su cry a la vez.
     *
     * Las dos cosas juntas, porque por separado ninguna se entiende del todo: el sonido solo
     * no dice cuál es si no te sabes los cries, y el dibujo a 25x25 tampoco siempre.
     */
    private fun showPokemon(pokemon: CaughtPokemon) {
        val preferences = AppPreferences(this)
        if (preferences.cryOnTap) cryPlayer.play(pokemon.speciesId)
        if (!preferences.showOnMatrixOnTap) return

        val uri = library.uriFor(pokemon.speciesId) ?: run {
            // Sin aviso parecería que la Matrix está rota, cuando lo que falta es el sprite.
            toast(getString(R.string.toast_sprite_missing, pokemon.displayName))
            return
        }
        // Se enseña aunque la rotación esté apagada: ese interruptor es para el carrusel
        // automático, no para impedir mirar un Pokémon a propósito. El servicio se apaga solo
        // al terminar si la rotación seguía desactivada.
        GlyphRotationService.preview(
            this,
            uri,
            SHOW_ON_MATRIX_MS,
            SpriteRenderModes(this).modeFor(pokemon.speciesId),
        )
    }

    /**
     * Elegir hasta qué nivel sube este Pokémon.
     *
     * Las opciones se construyen alrededor de su nivel de evolución, que es la decisión real:
     * "justo antes" conserva esta forma para siempre, y las demás dejan margen para verlo
     * crecer. Sin evolución por nivel, se ofrecen tramos redondos.
     */
    private fun chooseLevelCap(stale: CaughtPokemon) {
        val pokemon = repository.getCaught().firstOrNull { it.uid == stale.uid } ?: return
        val evolutionLevel = EvolutionResolver.evolutionLevelOf(pokemon.speciesId)

        val levels = buildList {
            if (evolutionLevel != null && evolutionLevel > 1) add(evolutionLevel - 1)
            addAll(listOf(25, 50, 75, 100))
        }.filter { it >= pokemon.level }.distinct().sorted()

        val labels = buildList {
            add(getString(R.string.pokemon_level_cap_none))
            levels.forEach { add(getString(R.string.pokemon_level_cap_set, it)) }
        }.toTypedArray()

        val title = evolutionLevel?.let { getString(R.string.pokemon_evolves_at, it) }
            ?: getString(R.string.pokemon_level_cap_choose)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, index ->
                val cap = if (index == 0) null else levels[index - 1]
                repository.updateCaught(pokemon.copy(levelCap = cap))
                refresh()
            }
            .show()
    }

    /**
     * Pone el símbolo a la izquierda de cada botón.
     *
     * Como dibujo y no como emoji dentro del texto: el emoji lo pinta el sistema con su propio
     * estilo —redondo, a color y con su forma— y al lado de esta familia de líneas canta.
     *
     * Se nombra el dibujo directamente en vez de pasar por el vocabulario de formas de
     * `DotIcons.Icon`. Ese vocabulario está pensado para los logros y la decoración de widgets,
     * donde importa la forma; aquí importa **qué significa el botón**, y estadísticas son tres
     * barras, no un libro.
     */
    private fun decorateButtons() {
        fun decorate(
            button: com.google.android.material.button.MaterialButton,
            @androidx.annotation.DrawableRes icon: Int,
        ) {
            button.setIconResource(icon)
            button.iconTint = android.content.res.ColorStateList.valueOf(
                getColor(R.color.glyph_white)
            )
            button.iconSize = buttonIconPx
        }

        decorate(binding.buttonWater, R.drawable.ic_min_water)
        decorate(binding.buttonHabits, R.drawable.ic_min_habits)
        decorate(binding.buttonSleep, R.drawable.ic_min_sleep)
        decorate(binding.buttonStats, R.drawable.ic_min_stats)
    }

    private fun refreshCryButton() {
        binding.buttonCrySoundSet.text =
            getString(R.string.cry_button, getString(cryPlayer.soundSet.labelRes))
    }

    override fun onDestroy() {
        // El SoundPool retiene memoria de audio: sin soltarlo se queda viva al cerrar.
        cryPlayer.release()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPokemonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textPokemonTitle.text = getString(R.string.pokemon_title)

        binding.buttonHelpPokemon.setOnClickListener {
            TutorialActivity.open(this, Tutorial.Which.POKEMON)
        }
        TutorialActivity.openIfFirstRun(this, Tutorial.Which.POKEMON)

        repository = PokemonRepository(this)
        game = PokemonGame(repository)

        cryPreferences = CryPreferences(this)
        eggs = EggStore(this)
        sleep = SleepTracker(this)
        water = WaterStore(this)
        habits = HabitStore(this)
        binding.buttonSleep.setOnClickListener { showSleepReport() }
        binding.buttonWater.setOnClickListener { showWaterDialog() }
        binding.buttonStats.setOnClickListener { showStats() }
        binding.buttonAchievements.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }
        decorateButtons()

        // Abierta desde Ajustes: se va directa al diálogo que se pidió.
        when (intent?.getStringExtra(Open.EXTRA)) {
            Open.WATER -> showWaterDialog()
            Open.SLEEP -> showSleepSettings()
            Open.HABITS -> showHabitsDialog()
        }
        binding.buttonHabits.setOnClickListener { showHabitsDialog() }
        cryPlayer = CryPlayer(this, cryPreferences.soundSet)
        // Los botones de volumen mueven el canal multimedia mientras esta pantalla esté
        // delante, aunque no haya nada sonando en ese instante.
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        library = SpriteLibrary(this)

        adapter = PokemonAdapter(
            onTrain = ::onSetPartner,
            onUseItem = ::onUseItem,
            onCry = ::showPokemon,
            onLevelCap = ::chooseLevelCap,
            library = library,
        )
        binding.recyclerTeam.layoutManager = LinearLayoutManager(this)
        binding.recyclerTeam.adapter = adapter

        // Los modos ya decididos para los sprites problemáticos: se ponen solos la primera vez.
        SpriteRenderModes(this).applyPresetsOnce()

        binding.buttonCrySoundSet.setOnClickListener { chooseCrySoundSet() }
        binding.buttonCatchRandom.setOnClickListener { catchRandom() }
        binding.buttonGiveItems.setOnClickListener { giveTestItems() }
        binding.buttonImportSprites.setOnClickListener { pickSpriteFolder.launch(null) }

        refresh()
    }

    /**
     * Importa la carpeta de sprites. Se copian dentro de la app en vez de quedarnos con la
     * URI de la carpeta: el permiso del selector no sobrevive a un reinicio y con 151
     * archivos no compensa arriesgarse a perderlos todos.
     */
    private val pickSpriteFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { library.importFrom(treeUri) }
            Toast.makeText(
                this@PokemonActivity,
                getString(R.string.toast_sprites_imported, result.imported, result.skipped),
                Toast.LENGTH_LONG
            ).show()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val team = repository.getCaught().sortedBy { it.speciesId }
        adapter.submitList(team)
        binding.textTeamEmpty.isVisible = team.isEmpty()
        binding.recyclerTeam.isVisible = team.isNotEmpty()

        val (seen, total) = game.pokedexProgress()
        binding.textPokedexProgress.text = getString(R.string.pokemon_pokedex_progress, seen, total)
        binding.textSpritesState.text =
            getString(R.string.sprites_state_summary, library.importedCount(), total)
        refreshCryButton()

        val partner = repository.getTrainingPartner()
        binding.textTrainingPartner.text = if (partner == null) {
            getString(R.string.pokemon_no_partner)
        } else {
            getString(
                R.string.pokemon_partner_active,
                partner.displayName,
                partner.level,
                TrainingRules.EXP_PER_MINUTE
            )
        }

        binding.textEggs.text = if (!eggs.hasEgg) {
            getString(R.string.pokemon_eggs_none, EggRules.CAPTURES_PER_EGG, eggs.capturesTowardNext)
        } else {
            getString(
                R.string.pokemon_eggs_incubating,
                eggs.count,
                (eggs.progress * 100).toInt(),
                eggs.capturesTowardNext,
                EggRules.CAPTURES_PER_EGG,
            )
        }

        // Si hay un salvaje esperando, se ofrece resolverlo aquí. Es la salida cuando el botón
        // físico no responde porque el toy seleccionado en Glyph no es el nuestro.
        val pendingWild = WildSpawnStore(this).pendingSpeciesId
        val pendingSpecies = pendingWild?.let { PokemonRegistry[it] }
        binding.buttonWild.isVisible = pendingSpecies != null
        if (pendingSpecies != null) {
            binding.buttonWild.text = getString(R.string.wild_pending_button, pendingSpecies.name)
            binding.buttonWild.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.wild_pending_title, pendingSpecies.name))
                    .setMessage(R.string.wild_pending_message)
                    .setPositiveButton(R.string.wild_catch) { _, _ ->
                        GlyphRotationService.resolveWild(this, catchIt = true)
                        binding.buttonWild.postDelayed({ refresh() }, WILD_RESOLVE_REFRESH_MS)
                    }
                    .setNegativeButton(R.string.wild_release) { _, _ ->
                        GlyphRotationService.resolveWild(this, catchIt = false)
                        binding.buttonWild.postDelayed({ refresh() }, WILD_RESOLVE_REFRESH_MS)
                    }
                    .show()
            }
        }

        val inventory = repository.getInventory()
        showInventory(inventory)
    }

    /**
     * El agua de hoy: cuánto llevas, y los ajustes de cuánto quieres beber.
     *
     * Sumar desde aquí existe como respaldo; lo normal es hacerlo desde el widget, que es el
     * único sitio donde apuntar un vaso no cuesta nada. Una mecánica de registro diario que
     * obligue a abrir la app se abandona a la semana.
     */
    /**
     * El inventario, en dos columnas y con las cantidades alineadas.
     *
     * Cada celda es nombre a la izquierda y `×N` a la derecha, con el nombre quedándose el hueco
     * que sobra. Alinear las cifras es lo que permite comparar de un vistazo cuánto tienes de cada
     * cosa: con el `×3` pegado detrás de un nombre largo y otro detrás de uno corto, hay que ir
     * buscándolos.
     *
     * Se construye a mano en vez de con un `RecyclerView` porque son ocho objetos como mucho y
     * fijos: un adaptador aquí sería más código para hacer lo mismo.
     */
    private fun showInventory(inventory: Map<PokemonItem, Int>) {
        binding.textInventory.isVisible = inventory.isEmpty()
        binding.gridInventory.isVisible = inventory.isNotEmpty()
        binding.gridInventory.removeAllViews()
        if (inventory.isEmpty()) return

        val columns = binding.gridInventory.columnCount
        inventory.entries.forEachIndexed { index, (item, quantity) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = android.widget.GridLayout.spec(index % columns, 1f)
                    // El hueco entre columnas solo va en la de la izquierda: puesto en las dos,
                    // la tabla se despega de los márgenes de la pantalla y deja de alinear con el
                    // resto de secciones.
                    if (index % columns == 0) rightMargin = dimen(R.dimen.space_m)
                    topMargin = dimen(R.dimen.space_s)
                }
            }

            row.addView(
                TextView(this).apply {
                    text = itemName(item)
                    textSize = 13f
                    typeface = android.graphics.Typeface.MONOSPACE
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(getColor(R.color.glyph_white))
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
            )
            row.addView(
                TextView(this).apply {
                    text = getString(R.string.pokemon_item_quantity, quantity)
                    textSize = 13f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(getColor(R.color.glyph_grey))
                    setPadding(dimen(R.dimen.space_s), 0, 0, 0)
                }
            )
            binding.gridInventory.addView(row)
        }
    }

    private fun showWaterDialog() {
        val message = getString(
            R.string.water_summary,
            water.todayMl,
            water.goalMl,
            (water.progress * 100).toInt(),
            water.portionMl,
            String.format("%.2f", DailyBonus.waterFactor(true)),
            water.streak,
            water.bestStreak,
            water.daysMet,
        )

        showInfoDialog(
            titleRes = R.string.water_dialog_title,
            message = message,
            infoTitleRes = R.string.info_water_title,
            infoBodyRes = R.string.info_water_body,
        ) { builder ->
            builder
                .setPositiveButton(getString(R.string.water_add_one, water.portionMl)) { _, _ ->
                    water.addPortion()
                    afterWaterChange()
                }
                .setNeutralButton(R.string.water_settings) { _, _ -> showWaterSettings() }
                .setNegativeButton(getString(R.string.water_remove_one, water.portionMl)) { _, _ ->
                    water.removePortion()
                    afterWaterChange()
                }
        }
    }

    private fun afterWaterChange() {
        WaterWidgetProvider.refresh(this)
        StatWidgetProvider.refresh(this, BonusWidget::class.java)
        showWaterDialog()
    }

    /**
     * Un diálogo con un icono de información arriba a la derecha que explica el porqué.
     *
     * La explicación va escondida detrás del icono y no en el propio texto porque son cosas que
     * se leen una vez: metidas en el mensaje, estorbarían cada día para siempre.
     */
    private fun showInfoDialog(
        titleRes: Int,
        message: String,
        infoTitleRes: Int,
        infoBodyRes: Int,
        configure: (AlertDialog.Builder) -> Unit,
    ) {
        val header = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(headerPaddingPx, dialogPaddingPx, dialogPaddingPx, 0)
            addView(
                android.widget.TextView(this@PokemonActivity).apply {
                    text = getString(titleRes)
                    textSize = 20f
                    setTextColor(getColor(R.color.glyph_white))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, -2).apply { weight = 1f }
                }
            )
            addView(
                android.widget.TextView(this@PokemonActivity).apply {
                    text = getString(R.string.info_button)
                    textSize = 22f
                    setTextColor(getColor(R.color.glyph_grey))
                    setPadding(gapPx, 0, 0, 0)
                    setOnClickListener {
                        AlertDialog.Builder(this@PokemonActivity)
                            .setTitle(infoTitleRes)
                            .setMessage(infoBodyRes)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            )
        }

        AlertDialog.Builder(this)
            .setCustomTitle(header)
            .setMessage(message)
            .also(configure)
            .show()
    }

    private fun showWaterSettings() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dialogPaddingPx, gapPx, dialogPaddingPx, 0)
        }
        val goalInput = android.widget.EditText(this).apply {
            hint = getString(R.string.water_goal_prompt)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(water.goalMl.toString())
        }
        val portionInput = android.widget.EditText(this).apply {
            hint = getString(R.string.water_portion_prompt)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(water.portionMl.toString())
        }
        container.addView(goalInput)
        container.addView(portionInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.water_settings)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                goalInput.text.toString().toIntOrNull()?.let { water.goalMl = it }
                portionInput.text.toString().toIntOrNull()?.let { water.portionMl = it }
                WaterWidgetProvider.refresh(this)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Los hábitos: se marcan los de hoy y se ven las rachas.
     *
     * Los escribe el usuario. El juego no opina sobre cuáles son buenos: solo cuenta si hoy lo
     * cumpliste, y eso hace que tu compañero entrene más rápido mañana.
     */
    private fun showHabitsDialog() {
        val all = habits.all()
        if (all.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.habits_title)
                .setMessage(R.string.habits_empty)
                .setPositiveButton(R.string.habits_add) { _, _ -> showAddHabit() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        // Cada línea dice su objetivo y cómo va: un hábito de tres veces por semana necesita
        // enseñar "2 de 3 esta semana", no un sí o un no de hoy.
        val labels = all.map { habit ->
            if (habit.isDaily) {
                getString(R.string.habits_entry_daily, habit.name, habit.streak())
            } else {
                getString(
                    R.string.habits_entry_weekly,
                    habit.name,
                    habit.doneThisWeek(),
                    habit.timesPerWeek,
                    habit.streak()
                )
            }
        }.toTypedArray()
        val checked = all.map { it.isDoneToday() }.toBooleanArray()

        val onPace = habits.onPaceCount()
        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.habits_bonus,
                    onPace,
                    all.size,
                    String.format("%.2f", DailyBonus.habitFactor(onPace, all.size))
                )
            )
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                habits.setDoneToday(all[which].id, isChecked)
                StatWidgetProvider.refresh(this, HabitsWidget::class.java)
                StatWidgetProvider.refresh(this, BonusWidget::class.java)
            }
            .setPositiveButton(R.string.habits_add) { _, _ -> showAddHabit() }
            .setNeutralButton(R.string.habits_stats) { _, _ -> showStats() }
            .setNegativeButton(android.R.string.ok) { _, _ -> refresh() }
            .show()
    }

    /**
     * Alta de un hábito: el nombre y **cada cuánto**.
     *
     * La frecuencia se pregunta al crearlo y no después porque cambia lo que significa cumplirlo:
     * "ir al gym" tres veces por semana no puede castigarte los otros cuatro días, y sin
     * preguntarlo habría que suponer que todo es diario.
     */
    private fun showAddHabit() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dialogPaddingPx, gapMediumPx, dialogPaddingPx, 0)
        }
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.habits_new_prompt)
        }
        val frequencyLabel = android.widget.TextView(this).apply {
            setPadding(0, gapPx, 0, gapTinyPx)
            text = getString(R.string.habits_frequency_label)
        }
        // Un desplegable de 1 a 7: "veces por semana", donde 7 se enseña como "todos los días".
        val frequencies = (1..HabitRules.DAILY).map { times ->
            if (times == HabitRules.DAILY) {
                getString(R.string.habits_frequency_daily)
            } else {
                resources.getQuantityString(R.plurals.habits_frequency_weekly, times, times)
            }
        }
        val spinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@PokemonActivity,
                android.R.layout.simple_spinner_dropdown_item,
                frequencies
            )
            setSelection(HabitRules.DAILY - 1)
        }
        container.addView(input)
        container.addView(frequencyLabel)
        container.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle(R.string.habits_add)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                habits.add(input.text.toString(), timesPerWeek = spinner.selectedItemPosition + 1)
                StatWidgetProvider.refresh(this, HabitsWidget::class.java)
                showHabitsDialog()
            }
            .setNeutralButton(R.string.habits_delete_pick) { _, _ -> showDeleteHabit() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * El registro de todo: agua, hábitos y sueño, con sus rachas y sus medias.
     *
     * Junto y en un solo sitio a propósito. Son tres cosas que se alimentan del mismo esfuerzo y
     * dan un único multiplicador; verlas por separado no dejaría ver por dónde estás fallando.
     */
    /** El registro completo, en su propia pantalla con los calendarios de puntos. */
    private fun showStats() {
        startActivity(Intent(this, StatsActivity::class.java))
    }

    private fun showDeleteHabit() {
        val all = habits.all()
        if (all.isEmpty()) return
        val labels = all.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.habits_delete_pick)
            .setItems(labels) { _, which ->
                val habit = all[which]
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.habits_delete_title, habit.name))
                    .setMessage(R.string.habits_delete_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        habits.remove(habit.id)
                        StatWidgetProvider.refresh(this, HabitsWidget::class.java)
                        StatWidgetProvider.refresh(this, BonusWidget::class.java)
                        refresh()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .show()
    }

    /**
     * El desglose de la noche: qué dormiste, qué puntuó y qué te está dando hoy.
     *
     * Se enseñan los dos componentes por separado —horas y puntualidad— porque son cosas que se
     * arreglan distinto: dormir más, o acostarse antes. Un número único no diría cuál.
     */
    private fun showSleepReport() {
        val bedtime = formatMinuteOfDay(sleep.bedtimeMinutes)
        val goal = formatDuration(sleep.goalMinutes)

        val message = if (sleep.lastNightMinutes <= 0) {
            getString(R.string.sleep_no_data, bedtime, goal)
        } else {
            val slept = sleep.lastNightMinutes
            val late = sleep.lastNightLateMinutes
            val quality = sleep.currentQuality

            // Los mismos pesos que usa SleepSchedule, para que el desglose cuadre con la nota.
            val amount = (slept.toFloat() / sleep.goalMinutes).coerceIn(0f, 1f)
            val punctuality = (1f - late / SleepSchedule.LATE_TOLERANCE_MINUTES).coerceIn(0f, 1f)

            getString(
                R.string.sleep_summary,
                formatDuration(slept),
                goal,
                ((slept.toFloat() / sleep.goalMinutes) * 100).toInt(),
                if (late <= 0) getString(R.string.sleep_on_time)
                else getString(R.string.sleep_late, formatDuration(late)),
                (quality * 100).toInt(),
                (amount * SleepSchedule.AMOUNT_WEIGHT * 100).toInt(),
                (punctuality * SleepSchedule.PUNCTUALITY_WEIGHT * 100).toInt(),
                String.format("%.1f", SleepSchedule.frequencyMultiplier(quality)),
                String.format("%.1f", SleepSchedule.rarityMultiplier(quality)),
                sleep.nightsRecorded,
                (sleep.averageQuality * 100).toInt(),
                (sleep.bestQuality * 100).toInt(),
                bedtime,
                goal,
            )
        }

        showInfoDialog(
            titleRes = R.string.sleep_dialog_title,
            message = message,
            infoTitleRes = R.string.info_sleep_title,
            infoBodyRes = R.string.info_sleep_body,
        ) { builder ->
            builder
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.button_sleep_settings) { _, _ -> showSleepSettings() }
        }
    }

    /** Hora de acostarse y horas objetivo, que es lo que cambia la nota. */
    private fun showSleepSettings() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dialogPaddingPx, gapPx, dialogPaddingPx, 0)
        }
        val bedtimeInput = android.widget.EditText(this).apply {
            hint = getString(R.string.sleep_bedtime_prompt)
            setText(formatMinuteOfDay(sleep.bedtimeMinutes))
        }
        val goalInput = android.widget.EditText(this).apply {
            hint = getString(R.string.sleep_goal_prompt)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.1f", sleep.goalMinutes / 60f))
        }
        container.addView(bedtimeInput)
        container.addView(goalInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.button_sleep_settings)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                parseMinuteOfDay(bedtimeInput.text.toString())?.let { sleep.bedtimeMinutes = it }
                goalInput.text.toString().replace(',', '.').toFloatOrNull()
                    ?.let { sleep.goalMinutes = (it * 60).toInt() }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatMinuteOfDay(minutes: Int): String =
        String.format("%02d:%02d", minutes / 60, minutes % 60)

    private fun parseMinuteOfDay(text: String): Int? {
        val parts = text.trim().split(":", ".")
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    private fun formatDuration(minutes: Int): String =
        getString(R.string.sleep_hours_minutes, minutes / 60, minutes % 60)

    private fun onSetPartner(pokemon: CaughtPokemon) {
        repository.setTrainingPartner(pokemon.uid)
        refresh()
    }

    private fun onUseItem(stale: CaughtPokemon) {
        // El objeto que trae el adaptador puede haber quedado desfasado (nivel, especie tras
        // evolucionar), así que se relee del repositorio antes de decidir qué objetos sirven.
        val pokemon = repository.getCaught().firstOrNull { it.uid == stale.uid } ?: run {
            refresh()
            return
        }

        val usable = game.usableItemsFor(pokemon)
        if (usable.isEmpty()) {
            toast(getString(R.string.pokemon_no_items_usable, pokemon.displayName))
            return
        }

        val labels = usable.map { itemName(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pokemon_choose_item)
            .setItems(labels) { _, index ->
                when (val outcome = game.useItem(usable[index], pokemon.uid)) {
                    is PokemonGame.Outcome.Evolved ->
                        toast(getString(R.string.pokemon_evolved, outcome.from.name, outcome.to.name))
                    is PokemonGame.Outcome.LeveledUp ->
                        toast(getString(R.string.pokemon_leveled_up, outcome.pokemon.displayName, outcome.newLevel))
                    is PokemonGame.Outcome.Failed -> toast(
                        outcome.arg
                            ?.let { getString(outcome.reasonRes, it) }
                            ?: getString(outcome.reasonRes)
                    )
                    PokemonGame.Outcome.NothingHappened -> Unit
                }
                refresh()
            }
            .show()
    }

    private fun catchRandom() {
        val species = PokemonRegistry.all.random()
        repository.addCaught(species.id, level = (5..15).random())
        // La captura se ve en la Matrix con ese mismo Pokémon: se le ve moverse y la bola
        // se cierra encima hasta que desaparece dentro.
        GlyphRotationService.catchAnimation(this, species.id)
        cryPlayer.play(species.id)
        toast(getString(R.string.pokemon_caught, species.name))
        refresh()
    }

    private fun giveTestItems() {
        PokemonItem.entries.forEach { repository.addItem(it, amount = 3) }
        refresh()
    }

    private fun itemName(item: PokemonItem): String = getString(
        when (item) {
            PokemonItem.FIRE_STONE -> R.string.item_fire_stone
            PokemonItem.WATER_STONE -> R.string.item_water_stone
            PokemonItem.THUNDER_STONE -> R.string.item_thunder_stone
            PokemonItem.LEAF_STONE -> R.string.item_leaf_stone
            PokemonItem.MOON_STONE -> R.string.item_moon_stone
            PokemonItem.LINKING_CORD -> R.string.item_linking_cord
            PokemonItem.RARE_CANDY -> R.string.item_rare_candy
            PokemonItem.SUPER_ROD -> R.string.item_super_rod
        }
    )

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
