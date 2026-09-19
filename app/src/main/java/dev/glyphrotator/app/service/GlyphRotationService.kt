package dev.glyphrotator.app.service

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nothing.ketchum.Common
import dev.glyphrotator.app.R
import dev.glyphrotator.app.achievements.AchievementRewards
import dev.glyphrotator.app.achievements.AchievementStore
import dev.glyphrotator.app.data.GifRepository
import dev.glyphrotator.app.glyph.CatchHaptics
import dev.glyphrotator.app.glyph.ClockPlayer
import dev.glyphrotator.app.glyph.EggAnimation
import dev.glyphrotator.app.habits.DailyBonus
import dev.glyphrotator.app.habits.HabitRules
import dev.glyphrotator.app.habits.HabitStore
import dev.glyphrotator.app.habits.UsageStore
import dev.glyphrotator.app.habits.WaterStore
import dev.glyphrotator.app.ui.widget.StatWidgetProvider
import dev.glyphrotator.app.glyph.EvolutionAnimation
import dev.glyphrotator.app.glyph.GifAnimation
import dev.glyphrotator.app.glyph.GlyphDataAnimation
import dev.glyphrotator.app.glyph.GlyphGifPlayer
import dev.glyphrotator.app.glyph.GlyphLedLayout
import dev.glyphrotator.app.glyph.MatrixImageProcessor
import dev.glyphrotator.app.glyph.GlyphMatrixController
import dev.glyphrotator.app.glyph.LiquidBatteryPlayer
import dev.glyphrotator.app.glyph.MediaFrameDecoder
import dev.glyphrotator.app.glyph.PokeballCatchAnimation
import dev.glyphrotator.app.glyph.PokeballSequence
import dev.glyphrotator.app.pokemon.DemoTuning
import dev.glyphrotator.app.pokemon.EggRules
import dev.glyphrotator.app.pokemon.EggStore
import dev.glyphrotator.app.pokemon.ItemDropStore
import dev.glyphrotator.app.pokemon.ItemDropTable
import dev.glyphrotator.app.pokemon.PokemonGame
import dev.glyphrotator.app.pokemon.PokemonItem
import dev.glyphrotator.app.pokemon.PokemonRegistry
import dev.glyphrotator.app.pokemon.SpriteLibrary
import dev.glyphrotator.app.pokemon.TrainingRules
import dev.glyphrotator.app.pokemon.SpriteRenderModes
import dev.glyphrotator.app.pokemon.spawn.SleepTracker
import dev.glyphrotator.app.pokemon.spawn.SpawnChance
import dev.glyphrotator.app.pokemon.spawn.SpawnConditions
import dev.glyphrotator.app.pokemon.spawn.SpawnTable
import dev.glyphrotator.app.pokemon.spawn.Weather
import dev.glyphrotator.app.pokemon.spawn.WeatherProvider
import dev.glyphrotator.app.pokemon.spawn.WildSpawnStore
import dev.glyphrotator.app.pokemon.PokemonRepository
import dev.glyphrotator.app.glyph.VinylBeatAnimation
import dev.glyphrotator.app.ui.MainActivity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class MusicState { NONE, PLAYING, PAUSED }

/**
 * Servicio en primer plano que mantiene viva la Glyph Matrix con un GIF/imagen en bucle
 * y lo cambia al azar (sin repetir el anterior) cada vez que se enciende la pantalla.
 * Se apaga con la pantalla (salvo estados "ambiente": música o carga), se atenúa/
 * desactiva según batería, muestra un disco de vinilo mientras suena música (girando o
 * quieto en pausa), una simulación de líquido con el % mientras carga, y un reloj en
 * reposo cuando la pantalla está apagada y no hay música ni carga. Se registra como
 * Foreground Service (tipo `specialUse`) para resistir la gestión de batería de Android.
 */
class GlyphRotationService : Service() {

    private var repository: GifRepository? = null
    private var controller: GlyphMatrixController? = null
    private var player: GlyphGifPlayer? = null
    private var clockPlayer: ClockPlayer? = null
    private var liquidBatteryPlayer: LiquidBatteryPlayer? = null
    private var receiverRegistered = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var legacyMusicPollJob: Job? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var currentGifId: String? = null
    // Concurrente a propósito: la precarga escribe desde varias corrutinas a la vez y la
    // rotación lee desde otra, así que un HashMap normal podría corromperse.
    private val decodedAnimationCache = ConcurrentHashMap<String, GifAnimation>()
    // `by lazy` y no en el constructor: el tamaño se pregunta al SDK, y este campo se crea antes
    // de que `controller` exista. Para cuando la música suene de verdad, ya está conectado.
    private val vinylSpinAnimation by lazy {
        VinylBeatAnimation.buildSpinAnimation(controller?.matrixSize ?: 25)
    }
    // Estos flags los tocan tanto los BroadcastReceiver (hilo principal) como las corrutinas
    // de fondo (sondeos, temporizadores), así que van @Volatile para que un hilo vea siempre
    // el último valor escrito por el otro.
    @Volatile private var isScreenOn = false
    @Volatile private var isUnlocked = false
    @Volatile private var isCriticalBattery = false
    @Volatile private var isCharging = false

    /**
     * Si hay cable puesto. Distinto de [isCharging], que mira el estado de carga y oscila solo.
     *
     * Arranca en `null` a propósito: "todavía no lo sé" no es lo mismo que "no hay cable". Con
     * false de inicio, el primer aviso de batería con el cable ya puesto se leía como un cambio
     * —de no enchufado a enchufado— y lanzaba el líquido veinte segundos cada vez que el
     * servicio arrancaba. Nadie había enchufado nada.
     */
    @Volatile private var isPlugged: Boolean? = null
    @Volatile private var musicState = MusicState.NONE
    @Volatile private var lastRawMusicState = MusicState.NONE
    @Volatile private var isShowingVinyl = false
    @Volatile private var isVinylStatic = false
    @Volatile private var isShowingClock = false
    @Volatile private var isShowingWild = false
    @Volatile private var isShowingCharging = false
    @Volatile private var isPlugFlashActive = false
    @Volatile private var isExternalGlyphAppActive = false
    @Volatile private var isBluetoothFlashActive = false
    @Volatile private var isCatchTestActive = false
    /**
     * Cuándo se apagó la pantalla y cuánto se ha pagado ya de ese tramo.
     *
     * Van a disco a través de [SleepTracker], no en campos del servicio. Estaban aquí en memoria
     * y por eso no se registraba ninguna noche ni se cobraba el entrenamiento largo: Android
     * mata el servicio en algún momento de la madrugada y al revivir no quedaba ni rastro del
     * tramo, así que al encender la pantalla no había nada que apuntar.
     */
    private var screenOffSinceMillis: Long
        get() = sleepTracker?.screenOffSinceMillis ?: 0L
        set(value) { sleepTracker?.screenOffSinceMillis = value }

    private var trainedMinutesThisPeriod: Int
        get() = sleepTracker?.trainedMinutes ?: 0
        set(value) { sleepTracker?.trainedMinutes = value }


    private var lastKnownForegroundPackage: String? = null
    private var lastForegroundQueryEnd = 0L
    private var pauseGraceJob: Job? = null
    private var plugFlashJob: Job? = null
    private var bluetoothFlashJob: Job? = null
    private var notificationFlashJob: Job? = null
    private var catchTestJob: Job? = null

    /**
     * Cuenta qué animación puntual es la más reciente: captura, huevo, eclosión o evolución
     * comparten el mismo hueco ([catchTestJob], [isCatchTestActive]), y de ahí salía un fallo de
     * verdad — reportado en el móvil, no encontrado leyendo código.
     *
     * `catchTestJob?.cancel()` es cooperativo: no para la corrutina en el acto, solo la marca
     * para que se corte en su próximo `delay()`. Si una segunda animación empezaba mientras la
     * primera aún no había llegado a ese punto, la `finally` de la **primera** corría *después*
     * de que la segunda ya estuviera pintando — y esa `finally` hacía `isCatchTestActive = false`
     * seguido de `updateDisplay()`. Con la bandera puesta a false de mentira, el decisor dejaba
     * de proteger a la segunda animación y `updateDisplay()` podía arrancar el carrusel **encima
     * de ella**: las dos pintando la Matrix a la vez.
     *
     * Es el mismo problema que ya resolvió [dev.glyphrotator.app.pokemon.CryPlayer] con su propio
     * contador de generación para los cries en vuelo, aplicado aquí a la Matrix: cada disparo se
     * lleva un número al nacer, y solo puede apagar la bandera y volver al carrusel el que sigue
     * siendo el más reciente cuando termina. El que fue cancelado no toca nada al morir tarde.
     */
    @Volatile private var animationGeneration = 0

    /**
     * Se llama al principio de cada disparo puntual (captura, huevo, eclosión, evolución).
     * Cancela lo que hubiera y devuelve el número que le toca a esta animación.
     */
    private fun startAnimationGeneration(): Int {
        catchTestJob?.cancel()
        isCatchTestActive = true
        return ++animationGeneration
    }

    /**
     * Cierre de un disparo puntual: solo actúa si [myGeneration] sigue siendo el más reciente.
     * Si ya lo sustituyó otro, no toca la bandera ni llama a `updateDisplay()` — eso ya es
     * responsabilidad del que le sustituyó, y hacerlo aquí sería pisarle la Matrix.
     */
    private fun finishAnimationGeneration(myGeneration: Int) {
        if (myGeneration != animationGeneration) return
        isCatchTestActive = false
        updateDisplay(forceNewGif = true)
    }
    private var pokemonGame: PokemonGame? = null
    private var pokemonRepository: PokemonRepository? = null
    private var wildSpawnStore: WildSpawnStore? = null
    private var eggStore: EggStore? = null
    private var sleepTracker: SleepTracker? = null
    private var waterStore: WaterStore? = null
    private var habitStore: HabitStore? = null
    private var usageStore: UsageStore? = null
    private var achievements: AchievementStore? = null
    private var weatherProvider: WeatherProvider? = null

    /** Cuándo corrió la última ronda del juego, para saber cuánto tiempo cuenta la siguiente. */
    private var lastGameStepMillis = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    isUnlocked = !isKeyguardLocked()
                    usageStore?.screenOnSinceMillis = System.currentTimeMillis()
                    awardTrainingForScreenOffPeriod()
                    refreshBatteryBrightness()
                    // Con la pantalla encendida la CPU ya está despierta: el bucle de corrutina
                    // se basta y la alarma solo gastaría batería.
                    GameTickAlarm.cancel(applicationContext)
                    updateDisplay(forceNewGif = true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    isUnlocked = false
                    screenOffSinceMillis = System.currentTimeMillis()
                    trainedMinutesThisPeriod = 0
                    // Se cierra el tramo de uso aquí, que es donde de verdad dejas el teléfono.
                    usageStore?.closeScreenOnPeriod()
                    // Y aquí arranca el juego de verdad: a partir de ahora la CPU se va a dormir
                    // y el bucle de corrutina deja de correr, así que quien mantiene vivo el
                    // juego es la alarma. Sin esto no aparecía un solo Pokémon en toda la noche.
                    lastGameStepMillis = System.currentTimeMillis()
                    GameTickAlarm.schedule(applicationContext)
                    updateDisplay()
                }
                Intent.ACTION_USER_PRESENT -> {
                    isUnlocked = true
                    usageStore?.registerUnlock()
                    updateDisplay(forceNewGif = true)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val wasCritical = isCriticalBattery
                    val wasCharging = isCharging
                    val wasPlugged = isPlugged
                    refreshBatteryBrightness()

                    // El destello se dispara al **enchufar o desenchufar el cable**, no al
                    // cambiar el estado de carga.
                    //
                    // `BATTERY_PROPERTY_STATUS` va y viene solo: con el móvil casi lleno, o en
                    // un puerto USB que da poca corriente, alterna entre CHARGING y
                    // NOT_CHARGING cada pocos segundos. Y `ACTION_BATTERY_CHANGED` llega
                    // constantemente. Resultado: el líquido se relanzaba una y otra vez y se
                    // quedaba en la Matrix de forma permanente mientras hubiera cable.
                    //
                    // `EXTRA_PLUGGED` dice si hay cable puesto, que es lo que de verdad se
                    // quiere celebrar, y ese no oscila.
                    val ahoraEnchufado = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    isPlugged = ahoraEnchufado
                    // Solo si ya sabíamos cómo estaba antes. La primera lectura solo apunta.
                    if (wasPlugged != null && ahoraEnchufado != wasPlugged) {
                        triggerPlugFlash(justPlugged = ahoraEnchufado)
                    }

                    if (isCriticalBattery != wasCritical || isCharging != wasCharging) updateDisplay()
                }
                "android.bluetooth.device.action.ACL_CONNECTED" -> triggerBluetoothFlash()
            }
        }
    }

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers -> onActiveSessionsChanged(controllers ?: emptyList()) }

    override fun onCreate() {
        super.onCreate()

        // El ritmo del juego se lee una vez aqui: la comprobacion de aparicion pasa cada
        // minuto y no vale ir a disco cada vez.
        dev.glyphrotator.app.pokemon.DemoTuning.refresh(this)

        // startForeground() debe llamarse cuanto antes: un servicio arrancado con
        // startForegroundService() que no lo hace a tiempo provoca un crash del sistema,
        // incluso si luego decidimos pararlo por ser un dispositivo no compatible.
        startForeground(NOTIFICATION_ID, buildNotification())

        // Los dos modelos que el SDK de Nothing sabe registrar. `GlyphMatrixConnection` ya
        // elige el código correcto para cada uno (`currentDeviceCode()`); aquí solo hace falta
        // no parar el servicio en el segundo. Cualquier otro modelo sigue sin soporte: el SDK
        // no tiene un tercer `DEVICE_*` al que registrarse.
        if (!Common.is23112() && !Common.is25111p()) {
            Log.e(TAG, "Este dispositivo no es un Nothing Phone (3) ni un (4a) Pro: deteniendo el servicio")
            stopSelf()
            return
        }

        val repo = GifRepository(applicationContext)
        val ctrl = GlyphMatrixController(applicationContext)
        repository = repo
        controller = ctrl
        player = GlyphGifPlayer(serviceScope, ctrl)
        clockPlayer = ClockPlayer(serviceScope, ctrl)
        liquidBatteryPlayer = LiquidBatteryPlayer(serviceScope, ctrl, applicationContext)
        val pokemonRepo = PokemonRepository(applicationContext)
        pokemonRepository = pokemonRepo
        pokemonGame = PokemonGame(pokemonRepo)
        wildSpawnStore = WildSpawnStore(applicationContext)
        eggStore = EggStore(applicationContext)
        sleepTracker = SleepTracker(applicationContext)
        waterStore = WaterStore(applicationContext)
        habitStore = HabitStore(applicationContext)
        usageStore = UsageStore(applicationContext)
        achievements = AchievementStore(applicationContext)
        weatherProvider = WeatherProvider(applicationContext)

        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction("android.bluetooth.device.action.ACL_CONNECTED")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true

        ctrl.connect {
            isScreenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
            isUnlocked = isScreenOn && !isKeyguardLocked()
            refreshBatteryBrightness()
            updateDisplay(forceNewGif = true)
            warmDecodedAnimationCache()
        }

        startMusicMonitoring()
        startUnlockPolling()
        startExternalGlyphAppPolling()
        startWildSpawnPolling()

        // En cuanto el toy del botón entra o sale, recalculamos: mientras él manda nos
        // apartamos, y al soltar el botón recuperamos lo que tocara.
        GlyphToyPresence.onChanged = { updateDisplay(forceNewGif = true) }

        // Al entrar una notificación nos apartamos, y hay que volver solos cuando pasa: no
        // llega ningún evento que avise de que la animación del sistema ha terminado.
        NotificationFlash.onChanged = {
            updateDisplay()
            notificationFlashJob?.cancel()
            notificationFlashJob = serviceScope.launch {
                delay(NotificationFlash.remainingMs())
                updateDisplay(forceNewGif = true)
            }
        }
    }

    /**
     * Respaldo de ACTION_USER_PRESENT: en algunos teléfonos (Smart Lock / desbloqueo por
     * confianza) ese evento nunca llega aunque el teléfono sí esté realmente desbloqueado.
     * Comprobamos el estado real cada 1.5s y corregimos [isUnlocked] si se desincronizó.
     */
    /**
     * Mira una vez por minuto, con la pantalla apagada, si aparece un Pokémon salvaje.
     *
     * Un minuto es el paso natural: la probabilidad se define por minutos acumulados, y
     * comprobarlo más a menudo solo gastaría batería sin cambiar el resultado.
     */
    private fun startWildSpawnPolling() {
        serviceScope.launch {
            while (isActive) {
                // Con la pantalla encendida las tres rondas no hacen nada: ni aparecen Pokémon,
                // ni se entrena, ni se incuba. Se espera mucho más para no despertar la CPU cada
                // minuto durante todo el rato que estés usando el teléfono.
                delay(if (isScreenOn) IDLE_STEP_POLL_MS else WILD_SPAWN_POLL_MS)
                runGameStep()
            }
        }
    }

    /**
     * Una ronda del juego. La llaman dos cosas y por dos motivos distintos.
     *
     * Con la pantalla encendida la trae el bucle de corrutina de [startWildSpawnPolling], que
     * corre sin problema porque la CPU está despierta de todas formas.
     *
     * Con la pantalla apagada la trae la alarma del sistema ([GameTickAlarm]), y **ese es el
     * arreglo**: el bucle de corrutina se congelaba en cuanto el teléfono entraba en suspensión,
     * así que en reposo —que es cuando el juego tiene que pasar— no se ejecutaba nunca. No
     * aparecía ni un Pokémon en toda la noche.
     */
    private fun runGameStep() {
        val now = System.currentTimeMillis()
        // Cuánto ha pasado **de verdad** desde la ronda anterior, no cuánto se pidió esperar.
        // Una alarma en Doze puede llegar diez minutos tarde, y esos diez minutos cuentan.
        val elapsedMinutes = if (lastGameStepMillis <= 0L) {
            1
        } else {
            (((now - lastGameStepMillis) / 60_000L).toInt()).coerceIn(1, MAX_CATCHUP_MINUTES)
        }
        lastGameStepMillis = now

        // El tiempo se refresca aquí porque esta ronda ya está en un hilo de fondo y corre al
        // ritmo justo; el proveedor decide solo si toca consultar o no.
        runCatching { weatherProvider?.refreshIfDue() }
            .onFailure { Log.w(TAG, "Fallo consultando el tiempo", it) }
        runCatching { stepWildSpawn(elapsedMinutes) }
            .onFailure { Log.e(TAG, "Fallo comprobando apariciones", it) }
        runCatching { stepTraining() }
            .onFailure { Log.e(TAG, "Fallo entrenando al compañero", it) }
        runCatching { stepEgg() }
            .onFailure { Log.e(TAG, "Fallo incubando el huevo", it) }
        runCatching { stepDailyRollover() }
            .onFailure { Log.e(TAG, "Fallo comprobando el día perfecto", it) }

        // La alarma no se repite sola: se rearma en cada ronda mientras siga habiendo reposo.
        if (!isScreenOn) GameTickAlarm.schedule(applicationContext)
    }

    /**
     * Una vez al día, mira si ayer fue un día perfecto y reparte su premio.
     *
     * Aquí y no dentro de `dailyBonus()`: ese se recalcula cada minuto para el multiplicador de
     * experiencia, que es una lectura sin efectos — recalcularla de más no rompe nada. Repartir
     * un objeto sí tiene efecto, así que hace falta que ocurra **una sola vez**, y por eso lleva
     * su propia marca de qué día se comprobó ya ([ItemDropStore.lastPerfectDayCheck]).
     *
     * Con las mismas tres fuentes que `dailyBonus()` — sueño, agua y hábitos, todos de **ayer**—
     * y no de hoy: si el premio fuera por el día en curso, dejar el móvil parado el resto del día
     * después de cumplir todo no cambiaría nada, pero comprobarlo a medio día sí podría fallar
     * por algo que aún te faltaba por hacer.
     *
     * De paso **arregla dos logros que nunca se podían conseguir**: `registerPerfectDay()` no lo
     * llamaba nadie en todo el proyecto, así que `perfect_day` y `perfect_week` — que dependen de
     * él— eran inalcanzables desde que se escribieron. No estaba roto por un cambio reciente:
     * nunca llegó a conectarse.
     */
    private fun stepDailyRollover() {
        val drops = ItemDropStore(applicationContext)
        if (!drops.isNewDay()) return

        val habits = habitStore
        val water = waterStore
        val sleep = sleepTracker
        if (habits != null && water != null && sleep != null) {
            val perfect = DailyBonus.isPerfectDay(
                sleepQuality = sleep.currentQuality,
                waterGoalMet = water.goalMetYesterday,
                habitsKept = habits.onPaceYesterday(),
                habitsTotal = habits.all().size,
            )

            if (perfect) {
                achievements?.registerPerfectDay()
                announceAchievements()

                val item = ItemDropTable.roll(ItemDropTable.Source.PERFECT_DAY)
                pokemonRepository?.addItem(item)
                Log.i(TAG, "Día perfecto: te llevas ${item.name}")
                itemReceived(item)
            }
        }

        drops.lastPerfectDayCheck = HabitRules.today()
    }

    private fun stepWildSpawn(elapsedMinutes: Int = 1) {
        val store = wildSpawnStore ?: return

        // Lo primero, y antes de mirar la pantalla: al que se le pasa el tiempo se va.
        //
        // Estaba después de la salida de arriba y sin mirar el resultado, y eso dejaba dos
        // formas de que un Pokémon se quedara clavado en la Matrix para siempre: caducaba
        // mientras usabas el teléfono y no se enteraba nadie, o caducaba con la pantalla apagada
        // y nadie volvía a pintar, así que su último frame se quedaba ahí congelado. La Matrix
        // conserva lo último que se le mandó: si no se repinta, no se borra solo.
        if (store.expireIfStale()) {
            Log.i(TAG, "El Pokémon salvaje se ha ido por tiempo")
            updateDisplay(forceNewGif = true)
        }

        // Con la pantalla encendida no aparece nada: el juego premia dejar el teléfono quieto.
        if (isScreenOn) {
            store.minutesSinceLastSpawn = 0
            return
        }

        // Ya hay uno esperando: no se acumulan, o se despertaría uno con quince pendientes.
        if (store.pendingSpeciesId != null) return

        store.minutesSinceLastSpawn += elapsedMinutes

        val conditions = currentSpawnConditions(store.minutesSinceLastSpawn)
        // Los dos multiplicadores del demo van dentro: el de madrugada es un tope, y multiplicar
        // por fuera después de topar deshacía el tope. Ver SpawnChance.of.
        val perMinute = SpawnChance.of(
            conditions,
            spawnMultiplier = DemoTuning.spawnMultiplier,
            bedtimeMultiplier = DemoTuning.bedtimeMultiplier,
        )
        // Y se tira por todos los minutos que hayan pasado, no por uno: con el móvil dormido
        // esta ronda la trae una alarma que puede llegar tarde, y si cada despertar valiera una
        // sola tirada el ritmo del juego lo decidiría Android, no el juego.
        val chance = SpawnChance.overMinutes(perMinute, elapsedMinutes)
        if (Math.random() >= chance) return

        val species = SpawnTable.pick(conditions) ?: return
        store.put(species.id)
        Log.i(TAG, "Ha aparecido ${species.name} tras ${conditions.minutesSinceLastSpawn} min en reposo")
        updateDisplay(forceNewGif = true)
    }

    /**
     * Paga el entrenamiento del compañero minuto a minuto, mientras la pantalla sigue apagada.
     *
     * Es lo que hace que la evolución **se pueda ver**. Antes toda la experiencia se entregaba
     * de golpe al encender la pantalla, así que el momento de evolucionar caía siempre con el
     * usuario mirando la pantalla de delante y la Matrix a la espalda: la animación se
     * reproducía para nadie. Cobrándolo aquí, el nivel sube con el teléfono en reposo y la
     * evolución ocurre justo cuando la Matrix es lo único encendido.
     *
     * Se cobra por diferencia sobre el total acumulado, no sumando un minuto cada vez, para
     * que el extra de los 20 minutos siga cayendo donde le toca.
     */
    private fun stepTraining() {
        val game = pokemonGame ?: return
        if (isScreenOn) return

        val since = screenOffSinceMillis
        if (since <= 0L) return

        val minutes = ((System.currentTimeMillis() - since) / 60_000L).toInt()
        if (minutes <= trainedMinutesThisPeriod) return

        // De madrugada gana experiencia igual, pero no cambia de forma: evolucionar a las 4 de
        // la mañana es perderse justo lo que se quería enseñar.
        val awake = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY) !in BEDTIME_HOURS

        // Lo que hiciste ayer decide a qué ritmo entrena hoy: dormir, beber agua y cumplir tus
        // hábitos se multiplican entre sí. De ayer y no de hoy a propósito — si el premio fuera
        // inmediato, lo óptimo sería beberse el objetivo de un trago por la mañana.
        val gained = (TrainingRules.expBetween(trainedMinutesThisPeriod, minutes) * dailyBonus()).toInt()
        trainedMinutesThisPeriod = minutes

        var outcome: PokemonGame.Outcome = game.awardExp(gained, allowEvolution = awake)
        if (awake && outcome !is PokemonGame.Outcome.Evolved) {
            // Y si quedó alguna pendiente de la noche, se cobra ahora.
            game.evolvePartnerIfDue()?.let { outcome = it }
        }

        // Solo cuando ha pasado algo, o de vez en cuando. Repintar en cada ronda serían ocho
        // difusiones por minuto toda la noche; la barra de progreso del compañero sí avanza
        // siempre, así que se refresca cada tanto para que no se quede congelada media hora.
        if (outcome !is PokemonGame.Outcome.NothingHappened ||
            minutes % WIDGET_REFRESH_EVERY_MINUTES == 0
        ) {
            refreshGameWidgets()
        }

        val evolved = outcome as? PokemonGame.Outcome.Evolved ?: return
        Log.i(TAG, "${evolved.from.name} evolucionó a ${evolved.to.name}")
        achievements?.registerEvolution()
        announceAchievements()
        triggerEvolution(EVOLUTION_STYLE, evolved.from.id, evolved.to.id)
    }

    /**
     * Resuelve desde la app al salvaje que esté esperando: o se atrapa, o se deja ir.
     *
     * Existe como salida de emergencia. La captura de verdad es la pulsación larga del botón
     * físico, pero eso depende de que nuestro toy sea el que está seleccionado en los ajustes de
     * Glyph; si no lo es, el botón no nos llega, y entonces se ve un Pokémon en la Matrix que no
     * hay forma de atrapar ni de quitar. Con esto siempre queda una salida.
     */
    private fun resolvePendingWild(catchIt: Boolean) {
        val store = wildSpawnStore ?: return
        val speciesId = store.pendingSpeciesId ?: return
        store.clear()

        if (catchIt) {
            pokemonRepository?.addCaught(speciesId, level = (WILD_MIN_LEVEL..WILD_MAX_LEVEL).random())
            if (eggStore?.registerCapture() == true) Log.i(TAG, "Diez capturas: huevo nuevo")
            refreshGameWidgets()
            triggerCatch(speciesId)
        } else {
            Log.i(TAG, "Has dejado ir al Pokémon salvaje")
            achievements?.registerRelease()
            updateDisplay(forceNewGif = true)
        }
    }

    /**
     * Comprueba si se ha desbloqueado algún logro y lo apunta.
     *
     * Sin animación en la Matrix a propósito: los logros caen justo cuando acaba de pasar otra
     * cosa —una captura, una evolución— y una segunda animación encima taparía la que estabas
     * mirando. Se apuntan y se ven en su pantalla.
     */
    private fun announceAchievements() {
        val store = achievements ?: return
        val fresh = runCatching { store.checkNewlyUnlocked() }.getOrNull().orEmpty()
        // Por el id y no por el nombre: el id es fijo y el nombre ahora es un recurso traducido.
        // Un registro que cambia de idioma según el móvil no se puede buscar ni comparar.
        for (achievement in fresh) Log.i(TAG, "Logro conseguido: ${achievement.id}")
        AchievementRewards.grant(applicationContext, fresh)
        if (fresh.isNotEmpty()) refreshGameWidgets()
    }

    /**
     * El multiplicador de experiencia que dejó el día de ayer.
     *
     * Se recalcula en cada ronda en vez de guardarse: son tres lecturas de preferencias una vez
     * por minuto, y así no hay que acordarse de invalidar nada cuando cambian los hábitos o el
     * objetivo de agua.
     */
    private fun dailyBonus(): Float {
        val habits = habitStore ?: return 1f
        return DailyBonus.trainingMultiplier(
            sleepQuality = sleepTracker?.currentQuality ?: 0f,
            waterGoalMet = waterStore?.goalMetYesterday == true,
            habitsKept = habits.onPaceYesterday(),
            habitsTotal = habits.all().size,
            // La racha viva más larga de todo lo que sigues: premia sostener algo de verdad.
            streakDays = maxOf(
                waterStore?.streak ?: 0,
                habits.all().maxOfOrNull { it.streak() } ?: 0,
            ),
        )
    }

    /**
     * Repinta los widgets que dependen del estado del juego.
     *
     * El periodo del sistema es de media hora como mínimo, así que sin este empujón una captura
     * o una eclosión tardarían eso en aparecer en el escritorio y parecería que están rotos.
     */
    private fun refreshGameWidgets() {
        for (widget in GAME_WIDGETS) {
            runCatching { StatWidgetProvider.refresh(applicationContext, widget) }
                .onFailure { Log.w(TAG, "No se pudo refrescar un widget", it) }
        }
    }

    /**
     * Incuba el huevo mientras el teléfono está en reposo, y lo abre cuando toca.
     *
     * Se incuba con el mismo criterio que se entrena: pantalla apagada. Así las dos cosas que
     * progresan solas premian lo mismo —dejar el teléfono quieto— en vez de tirar cada una por
     * su lado.
     */
    private fun stepEgg() {
        if (isScreenOn) return
        val eggs = eggStore ?: return
        val repository = pokemonRepository ?: return
        if (!eggs.incubate(1)) return

        val species = EggRules.pickHatch(PokemonRegistry.all, repository.getPokedex()) ?: return
        eggs.consumeHatched()
        repository.addCaught(species.id, level = EggRules.HATCH_LEVELS.random())
        Log.i(TAG, "Ha nacido ${species.name} del huevo")
        achievements?.registerHatch()
        announceAchievements()
        refreshGameWidgets()

        triggerHatch(species.id)
    }

    /** Lo que el mundo real aporta ahora mismo a la aparición. */
    private fun currentSpawnConditions(minutesSinceLastSpawn: Int): SpawnConditions {
        val calendar = java.util.Calendar.getInstance()
        val now = System.currentTimeMillis()
        val minutesOff = if (screenOffSinceMillis > 0) {
            ((now - screenOffSinceMillis) / 60_000L).toInt()
        } else {
            0
        }
        return SpawnConditions(
            minutesScreenOff = minutesOff,
            minutesSinceLastSpawn = minutesSinceLastSpawn,
            batteryPercent = currentBatteryPercent(),
            isCharging = isCharging,
            hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            season = SpawnConditions.seasonOf(calendar),
            weather = weatherProvider?.current ?: Weather.CLEAR,
            isHalloween = SpawnConditions.isHalloween(calendar),
            isChristmas = SpawnConditions.isChristmas(calendar),
            isFullMoon = SpawnConditions.isFullMoon(now),
            pokedexCount = pokemonRepository?.getPokedex()?.size ?: 0,
            isBedtime = calendar.get(java.util.Calendar.HOUR_OF_DAY) in BEDTIME_HOURS,
            // Lo que dormiste anoche: sube la frecuencia hasta ×2 y los pesos de los raros
            // hasta ×4. Antes iba siempre a 0 y no se notaba nunca.
            sleepQuality = sleepTracker?.currentQuality ?: 0f,
            ownedBySpecies = pokemonRepository?.getCaught()
                ?.groupingBy { it.speciesId }?.eachCount().orEmpty(),
        )
    }

    /**
     * Enseña al Pokémon salvaje en la Matrix, en bucle y tenue.
     *
     * Tenue a propósito: aparece con la pantalla apagada y puede pasar horas ahí, así que va
     * al mismo brillo bajo que el reloj en reposo en vez de a tope toda la noche.
     */
    private fun startWildPokemon() {
        val ctrl = controller ?: return
        val gifPlayer = player ?: return
        val speciesId = wildSpawnStore?.pendingSpeciesId ?: return
        val uri = SpriteLibrary(applicationContext).uriFor(speciesId) ?: return

        isShowingVinyl = false
        isShowingCharging = false
        isShowingClock = false
        isShowingWild = true
        // Los Pokémon van siempre a brillo completo: la atenuación por batería es para el
        // carrusel, que puede estar horas encendido. Atenuar un sprite oscuro, que ya cuesta
        // leer a 25x25, lo hace desaparecer.
        ctrl.brightness = FULL_BRIGHTNESS
        clockPlayer?.stop()
        liquidBatteryPlayer?.stop()

        serviceScope.launch {
            runCatching {
                if (!awaitMatrixReady(ctrl)) return@launch
                gifPlayer.play(
                    MediaFrameDecoder.decode(
                        applicationContext,
                        uri,
                        ctrl.matrixSize,
                        SpriteRenderModes(applicationContext).modeFor(speciesId),
                    )
                )
            }.onFailure { Log.e(TAG, "No se pudo enseñar el Pokémon salvaje", it) }
        }
    }

    private fun hasSpriteFor(speciesId: Int): Boolean =
        SpriteLibrary(applicationContext).hasSprite(speciesId)

    private fun currentBatteryPercent(): Int =
        getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: 100

    /**
     * Red de seguridad por si se pierde algún aviso de pantalla, a ritmo distinto según haga
     * falta.
     *
     * Con la pantalla encendida se mira a menudo porque el desbloqueo puede llegar sin evento
     * (Smart Lock). Con la pantalla apagada **no hay nada que se pueda perder**: para que el
     * estado cambie tiene que encenderse la pantalla, y eso siempre dispara un broadcast. Sondear
     * apagado cada segundo y medio eran unos cincuenta mil despertares al día para leer dos
     * veces lo mismo.
     */
    private fun startUnlockPolling() {
        serviceScope.launch {
            while (isActive) {
                delay(if (isScreenOn) UNLOCK_POLL_ACTIVE_MS else UNLOCK_POLL_IDLE_MS)
                syncScreenState()
            }
        }
    }

    /**
     * Relee del sistema si la pantalla está encendida y desbloqueada, y corrige el estado si
     * no coincide. Es la red de seguridad de todo el servicio: los broadcasts de pantalla se
     * pueden perder (servicio arrancado con la pantalla ya apagada, proceso reiniciado por el
     * sistema, Smart Lock que no dispara ACTION_USER_PRESENT...) y sin esto la app se quedaba
     * pegada mostrando el reloj aunque el teléfono llevara horas desbloqueado.
     */
    private fun syncScreenState(force: Boolean = false) {
        val actuallyScreenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: return
        val actuallyUnlocked = actuallyScreenOn && !isKeyguardLocked()

        if (!force && actuallyScreenOn == isScreenOn && actuallyUnlocked == isUnlocked) return

        if (actuallyScreenOn && !isScreenOn) {
            awardTrainingForScreenOffPeriod()
        }
        if (!actuallyScreenOn && isScreenOn) {
            screenOffSinceMillis = System.currentTimeMillis()
            trainedMinutesThisPeriod = 0
        }

        isScreenOn = actuallyScreenOn
        isUnlocked = actuallyUnlocked

        // El despertador se pone al día con el estado real, y este es el único sitio donde se
        // puede. Los dos caminos que no pasan por un broadcast de pantalla acaban aquí: el
        // servicio arrancando con el móvil ya en reposo, y Android reviviéndolo de madrugada
        // tras matarlo (START_STICKY, `intent == null`). Sin esto, en los dos casos el juego se
        // quedaba sin nadie que lo despertara justo la noche entera.
        if (actuallyScreenOn) {
            GameTickAlarm.cancel(applicationContext)
        } else {
            lastGameStepMillis = System.currentTimeMillis()
            GameTickAlarm.schedule(applicationContext)
        }

        refreshBatteryBrightness()
        updateDisplay(forceNewGif = true)
    }

    /**
     * Deja la Matrix libre mientras GlyphMuseum esté en primer plano, para poder probar sus
     * diseños sin que nuestra app la esté escribiendo también. Necesita el permiso "Acceso a
     * uso" (no se puede pedir en tiempo de ejecución, solo abrir la pantalla de Ajustes).
     */
    private fun startExternalGlyphAppPolling() {
        if (!hasUsageAccess()) return
        val usageStatsManager = getSystemService(UsageStatsManager::class.java) ?: return
        serviceScope.launch {
            while (isActive) {
                // Con la pantalla apagada no hay ninguna app en primer plano, así que preguntarlo
                // es tirar batería: cada consulta va contra el servicio de estadísticas de uso,
                // que no es barato, y así eran ochenta y seis mil al día para nada.
                if (!isScreenOn) {
                    if (isExternalGlyphAppActive) {
                        isExternalGlyphAppActive = false
                        updateDisplay(forceNewGif = true)
                    }
                    delay(EXTERNAL_APP_POLL_IDLE_MS)
                    continue
                }

                val foreground = currentForegroundPackage(usageStatsManager)
                val active = foreground == GLYPH_MUSEUM_PACKAGE
                if (active != isExternalGlyphAppActive) {
                    isExternalGlyphAppActive = active
                    updateDisplay(forceNewGif = true)
                }
                delay(EXTERNAL_APP_POLL_ACTIVE_MS)
            }
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Qué app está en primer plano ahora mismo.
     *
     * `queryEvents` solo devuelve *cambios*, no el estado actual: si la app lleva un rato
     * abierta sin tocar nada, la consulta viene vacía. Por eso se recuerda el último paquete
     * visto ([lastKnownForegroundPackage]) y solo se cambia cuando llega un evento nuevo; si
     * no, se mantiene el anterior en vez de perderlo. La ventana es amplia para poder
     * reconstruir el estado tras arrancar el servicio.
     */
    private fun currentForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val end = System.currentTimeMillis()
        val begin = lastForegroundQueryEnd.takeIf { it > 0L } ?: (end - FOREGROUND_LOOKBACK_MS)
        lastForegroundQueryEnd = end

        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> lastKnownForegroundPackage = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED ->
                    if (lastKnownForegroundPackage == event.packageName) lastKnownForegroundPackage = null
            }
        }
        return lastKnownForegroundPackage
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = repository ?: return START_STICKY
        val ctrl = controller ?: return START_STICKY

        // intent == null significa que Android reinició el servicio por su cuenta tras
        // matarlo (START_STICKY). En ese caso no llegó ningún broadcast de pantalla, así que
        // hay que releer el estado real en vez de arrancar con los valores por defecto.
        if (intent == null) {
            syncScreenState(force = true)
            warmDecodedAnimationCache()
            return START_STICKY
        }

        if (intent.action == ACTION_GAME_TICK) {
            // El despertador. Llega con el teléfono dormido, hace la ronda y se rearma solo.
            //
            // `syncScreenState()` primero porque el aviso puede llegar después de que la
            // pantalla se encendiera sin que nos enterásemos, y no queremos hacer una ronda de
            // reposo con el móvil en la mano. Además rearma o cancela la alarma según toque, así
            // que la cadena no se rompe ni siquiera si la ronda no llega a correr.
            syncScreenState()
            if (!isScreenOn) runGameStep()
            return START_STICKY
        }

        if (intent.action == ACTION_REFRESH) {
            // Fuerza una resincronización completa: si la app quedó pegada en un estado que
            // ya no corresponde, esto la devuelve a lo que toca ahora mismo.
            controller?.let { if (!it.isConnected) it.connect { syncScreenState(force = true) } }
            warmDecodedAnimationCache()
            syncScreenState(force = true)
        } else if (intent.action == ACTION_PREVIEW) {
            intent.getStringExtra(EXTRA_PREVIEW_URI)?.let {
                triggerPreview(
                    android.net.Uri.parse(it),
                    intent.getLongExtra(EXTRA_PREVIEW_MS, PREVIEW_MS),
                    intent.getStringExtra(EXTRA_PREVIEW_MODE)
                        ?.let { name ->
                            runCatching { MatrixImageProcessor.RenderMode.valueOf(name) }.getOrNull()
                        }
                        ?: MatrixImageProcessor.RenderMode.LUMINANCE
                )
            }
        } else if (intent.action == ACTION_TEST_CATCH) {
            triggerCatch(intent.getIntExtra(EXTRA_CATCH_DEX, 0))
        } else if (intent.action == ACTION_TEST_EVOLUTION) {
            triggerEvolution(
                intent.getStringExtra(EXTRA_EVOLUTION_STYLE)
                    ?.let { runCatching { EvolutionAnimation.Style.valueOf(it) }.getOrNull() }
                    ?: EvolutionAnimation.Style.BLINK,
                intent.getIntExtra(EXTRA_EVOLUTION_FROM, 0),
                intent.getIntExtra(EXTRA_EVOLUTION_TO, 0),
            )
        } else if (intent.action == ACTION_RESOLVE_WILD) {
            resolvePendingWild(intent.getBooleanExtra(EXTRA_WILD_CATCH, false))
        } else if (intent.action == ACTION_ITEM_RECEIVED) {
            intent.getStringExtra(EXTRA_ITEM_NAME)
                ?.let { runCatching { PokemonItem.valueOf(it) }.getOrNull() }
                ?.let { itemReceived(it) }
        } else if (intent.action == ACTION_TEST_EGG) {
            triggerEgg(
                intent.getStringExtra(EXTRA_EGG_STAGE)
                    ?.let { runCatching { EggAnimation.Stage.valueOf(it) }.getOrNull() }
                    ?: EggAnimation.Stage.FRESH
            )
        } else if (intent.action == ACTION_LIST_CHANGED) {
            val all = repo.getAll()
            decodedAnimationCache.keys.retainAll(all.map { it.id }.toSet())
            if (all.isEmpty()) {
                stopSelf()
            } else {
                warmDecodedAnimationCache()
                if (all.none { it.id == currentGifId } && ctrl.isConnected &&
                    isUnlocked && !isCriticalBattery && !isCharging && musicState == MusicState.NONE
                ) {
                    rotateToRandomDesign()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Si no lo soltamos, la referencia al servicio muerto se queda viva en el objeto
        // compartido y el toy seguiría llamando a un updateDisplay que ya no vale.
        GlyphToyPresence.onChanged = null
        NotificationFlash.onChanged = null
        if (receiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            receiverRegistered = false
        }
        legacyMusicPollJob?.cancel()
        pauseGraceJob?.cancel()
        plugFlashJob?.cancel()
        bluetoothFlashJob?.cancel()
        notificationFlashJob?.cancel()
        catchTestJob?.cancel()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (e: Exception) {
            // Puede fallar si nunca llegó a registrarse; no es crítico en el apagado.
        }
        controllerCallbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllerCallbacks.clear()
        takeOverMatrix()
        serviceScope.cancel()
        controller?.release()
        super.onDestroy()
    }

    // =========================================================================================
    // Detección de música: sesiones multimedia activas (play/pause reales) con fallback a
    // AudioManager.isMusicActive() si el usuario no concedió acceso a notificaciones.
    // =========================================================================================

    private fun startMusicMonitoring() {
        val componentName = ComponentName(this, MusicSessionListener::class.java)
        val hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (!hasNotificationAccess) {
            startLegacyMusicPoll()
            return
        }
        val msm = getSystemService(MediaSessionManager::class.java) ?: return startLegacyMusicPoll()
        mediaSessionManager = msm
        try {
            onActiveSessionsChanged(msm.getActiveSessions(componentName))
            msm.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin acceso a notificaciones todavía; usando detección básica", e)
            startLegacyMusicPoll()
        }
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>) {
        controllerCallbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllerCallbacks.clear()

        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    recomputeMusicState(controllers)
                }
                override fun onSessionDestroyed() {
                    recomputeMusicState(controllers.filter { it != controller })
                }
            }
            controller.registerCallback(callback)
            controllerCallbacks[controller] = callback
        }
        recomputeMusicState(controllers)
    }

    private fun recomputeMusicState(controllers: List<MediaController>) {
        val states = controllers.mapNotNull { it.playbackState?.state }
        val raw = when {
            states.any { it == PlaybackState.STATE_PLAYING } -> MusicState.PLAYING
            states.any { it == PlaybackState.STATE_PAUSED } -> MusicState.PAUSED
            else -> MusicState.NONE
        }
        onRawMusicStateChanged(raw)
    }

    /**
     * Sin acceso a notificaciones solo podemos saber si algo está sonando, no si está en pausa.
     *
     * El ritmo se va relajando mientras no suena nada: sin música, mirar cinco veces por segundo
     * durante horas es de lejos lo más caro que hacía la app. Con música sonando vuelve al
     * ritmo rápido de inmediato, que es cuando importa reaccionar.
     */
    private fun startLegacyMusicPoll() {
        legacyMusicPollJob = serviceScope.launch {
            val audioManager = getSystemService(AudioManager::class.java)
            var interval = MUSIC_POLL_ACTIVE_MS
            while (isActive) {
                val playing = audioManager?.isMusicActive ?: false
                onRawMusicStateChanged(if (playing) MusicState.PLAYING else MusicState.NONE)

                interval = if (playing) {
                    MUSIC_POLL_ACTIVE_MS
                } else {
                    (interval * 2).coerceAtMost(MUSIC_POLL_IDLE_MAX_MS)
                }
                delay(interval)
            }
        }
    }

    /**
     * Traduce la señal cruda de reproducción (que puede venir sin distinción de pausa, vía
     * el fallback) al estado real de la app: al dejar de sonar, se queda [MusicState.PAUSED]
     * (disco quieto) durante [PAUSE_GRACE_MS] antes de caer a [MusicState.NONE] (rotación
     * normal de GIFs). Si vuelve a sonar durante ese margen, retoma el giro al instante.
     */
    private fun onRawMusicStateChanged(raw: MusicState) {
        if (raw == lastRawMusicState) return
        val previousRaw = lastRawMusicState
        lastRawMusicState = raw

        if (raw == MusicState.PLAYING) {
            pauseGraceJob?.cancel()
            musicState = MusicState.PLAYING
            updateDisplay()
            return
        }

        if (previousRaw == MusicState.PLAYING || previousRaw == MusicState.PAUSED) {
            musicState = MusicState.PAUSED
            updateDisplay()
            pauseGraceJob?.cancel()
            pauseGraceJob = serviceScope.launch {
                delay(PAUSE_GRACE_MS)
                musicState = MusicState.NONE
                updateDisplay()
            }
        } else {
            musicState = MusicState.NONE
            updateDisplay()
        }
    }

    // =========================================================================================
    // Qué mostrar
    // =========================================================================================

    /**
     * Punto único de decisión de qué mostrar (o apagar). Prioridad: GlyphMuseum en primer
     * plano (le dejamos la Matrix libre) > batería crítica > destello al conectar un
     * dispositivo Bluetooth (funciona con pantalla apagada) > destello de 20s al enchufar/
     * desenchufar (el líquido dura solo esos 20s, no mientras siga cargando) > música (vinilo,
     * funciona con pantalla apagada) > no desbloqueado —pantalla apagada o en el lock screen—
     * (reloj en reposo, si está activado) > desbloqueado de verdad (rotación al azar de
     * GIFs/imágenes).
     */
    /**
     * Toma la Matrix para una animación puntual: para lo que hubiera y **olvida que lo había**.
     *
     * Lo segundo es lo importante. Antes solo se paraban los reproductores, y las banderas
     * `isShowing*` se quedaban a true. Al terminar la animación, el decisor decía "toca el
     * reloj", veía `isShowingClock = true` y no hacía nada — pero el reloj estaba parado, así
     * que la Matrix se quedaba congelada con el último frame de la animación para siempre.
     *
     * Es el mismo fallo que el Pokémon salvaje clavado en pantalla: la Matrix conserva lo último
     * que se le mandó, y si nadie repinta, no se borra sola.
     */
    private fun takeOverMatrix() {
        player?.stop()
        clockPlayer?.stop()
        liquidBatteryPlayer?.stop()
        isShowingClock = false
        isShowingVinyl = false
        isShowingCharging = false
        isShowingWild = false
    }

    private fun updateDisplay(forceNewGif: Boolean = false) {
        val ctrl = controller ?: return
        if (!ctrl.isConnected) return

        val inputs = DisplayInputs(
            ownToyActive = GlyphToyPresence.isActive,
            externalGlyphAppActive = isExternalGlyphAppActive,
            notificationFlashActive = NotificationFlash.isActive(),
            criticalBattery = isCriticalBattery,
            catchTestActive = isCatchTestActive,
            bluetoothFlashActive = isBluetoothFlashActive,
            plugFlashActive = isPlugFlashActive,
            musicPlaying = musicState == MusicState.PLAYING,
            musicPaused = musicState == MusicState.PAUSED,
            screenUnlocked = isUnlocked,
            clockEnabled = repository?.isClockEnabled == true,
            // Solo cuenta si además tenemos su sprite: sin él no habría nada que enseñar y la
            // Matrix se quedaría en negro tapando al reloj.
            wildSpawnWaiting = wildSpawnStore?.pendingSpeciesId?.let { hasSpriteFor(it) } == true,
            vinylEnabled = repository?.isVinylEnabled != false,
            rotationEnabled = repository?.isRotationEnabled != false
        )

        val mode = DisplayDecider.decide(inputs)
        when (mode) {
            DisplayMode.OFF -> stopAndClear()
            DisplayMode.BATTERY_LIQUID -> if (!isShowingCharging) startChargingLiquid()
            DisplayMode.VINYL_SPINNING -> if (!isShowingVinyl || isVinylStatic) startVinylSpin()
            DisplayMode.VINYL_FROZEN -> if (!isShowingVinyl || !isVinylStatic) showVinylStatic()
            DisplayMode.CLOCK -> if (!isShowingClock) startClockStandby()
            DisplayMode.WILD_POKEMON -> if (!isShowingWild) startWildPokemon()
            DisplayMode.CAROUSEL -> {
                // De vuelta al carrusel manda otra vez el brillo según la batería.
                refreshBatteryBrightness()
                // Las animaciones puntuales (prueba de captura, destello de Bluetooth) ya
                // están pintando ellas mismas: no las interrumpimos a media reproducción.
                if (isCatchTestActive || isBluetoothFlashActive) return
                if (isShowingVinyl || isShowingCharging || isShowingClock || forceNewGif) {
                    isShowingVinyl = false
                    isShowingCharging = false
                    isShowingClock = false
                    rotateToRandomDesign()
                }
            }
        }
    }

    /**
     * Muestra el GIF/imagen elegido para Bluetooth unos segundos al conectarse un dispositivo
     * (auriculares, etc.), con o sin pantalla encendida. Si no hay ninguno elegido, no hace nada.
     */
    private fun triggerBluetoothFlash() {
        val repo = repository ?: return
        val ctrl = controller ?: return
        val gifPlayer = player ?: return
        val uri = repo.bluetoothGifUri ?: return

        bluetoothFlashJob?.cancel()
        isBluetoothFlashActive = true
        // Por takeOverMatrix y no parando los reproductores a mano: así paraba el reloj pero
        // dejaba `isShowingClock` en true, y al acabar el destello con la pantalla apagada el
        // decisor decía "toca el reloj", veía la bandera puesta y no hacía nada. La Matrix se
        // quedaba con el último frame del Bluetooth para siempre. Es el fallo 14 otra vez.
        takeOverMatrix()

        serviceScope.launch {
            try {
                val animation = MediaFrameDecoder.decode(applicationContext, uri, ctrl.matrixSize)
                gifPlayer.play(animation)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo decodificar el GIF de Bluetooth $uri", e)
            }
        }

        bluetoothFlashJob = serviceScope.launch {
            delay(BLUETOOTH_FLASH_MS)
            isBluetoothFlashActive = false
            updateDisplay(forceNewGif = true)
        }
    }

    /**
     * Al encender la pantalla, entrega al compañero de entrenamiento la experiencia
     * correspondiente al rato que estuvo apagada (ver
     * [dev.glyphrotator.app.pokemon.TrainingRules]). Si sube de nivel puede evolucionar solo.
     */
    private fun awardTrainingForScreenOffPeriod() {
        val game = pokemonGame ?: return
        val since = screenOffSinceMillis
        if (since <= 0L) return
        screenOffSinceMillis = 0L

        // Si ese tramo fue una noche, aquí es donde se apunta: es el único momento en que se
        // conoce entero, con su principio y su final.
        sleepTracker?.let { tracker ->
            runCatching { tracker.recordScreenOffPeriod(since, System.currentTimeMillis()) }
                .onFailure { Log.e(TAG, "Fallo al apuntar el sueño", it) }
        }

        val minutes = ((System.currentTimeMillis() - since) / 60_000L).toInt()
        // Solo lo que quede por pagar: el grueso ya se fue entregando minuto a minuto en
        // [stepTraining]. Aquí se salda el resto —los minutos sueltos desde el último cobro, y
        // todo el periodo entero si el servicio no llegó a hacer ninguna ronda.
        val pending = TrainingRules.expBetween(trainedMinutesThisPeriod, minutes)
        trainedMinutesThisPeriod = 0
        if (pending <= 0) return

        serviceScope.launch {
            // Sin evolucionar: con la pantalla encendida la Matrix está detrás y no se vería.
            // Queda pendiente, y [stepTraining] la cobra en el próximo rato en reposo.
            runCatching { game.awardExp(pending, allowEvolution = false) }
                .onFailure { Log.e(TAG, "Fallo al aplicar el entrenamiento", it) }
        }
    }

    /**
     * Reproduce la secuencia de captura (círculo cerrándose + GIF real de la pokeball) una
     * vez, para poder probarla desde el botón de la app sin depender de que "salga" nada.
     */
    /**
     * Muestra un diseño concreto en la Matrix durante unos segundos, para poder verlo sin
     * esperar a que la rotación lo elija al azar. Reutiliza el mismo hueco que el destello
     * de Bluetooth, así que respeta las mismas prioridades (batería crítica, etc.).
     */
    private fun triggerPreview(
        uri: android.net.Uri,
        durationMs: Long = PREVIEW_MS,
        mode: MatrixImageProcessor.RenderMode = MatrixImageProcessor.RenderMode.LUMINANCE,
    ) {
        val ctrl = controller ?: return
        val gifPlayer = player ?: return

        bluetoothFlashJob?.cancel()
        isBluetoothFlashActive = true
        // Mismo motivo que en triggerBluetoothFlash: parar los reproductores sin apagar las
        // banderas dejaba la vista previa clavada en la Matrix al terminar.
        takeOverMatrix()

        bluetoothFlashJob = serviceScope.launch {
            try {
                // Con la rotación apagada el servicio no existía y acaba de arrancar: la
                // conexión con la Matrix tarda un momento en establecerse, y los frames que
                // se manden antes se descartan en silencio. De ahí que la vista previa
                // saliera tarde o no saliera.
                if (!awaitMatrixReady(ctrl)) {
                    warnMatrixBusy()
                    return@launch
                }
                gifPlayer.play(MediaFrameDecoder.decode(applicationContext, uri, ctrl.matrixSize, mode))
            } catch (e: Exception) {
                Log.e(TAG, "PREVIEW falló con $uri", e)
            }

            // El tiempo se cuenta desde que se ve algo, no desde que se pidió: si no, lo que
            // tarde en conectar y decodificar se lo come de los segundos en pantalla.
            delay(durationMs)
            isBluetoothFlashActive = false
            // Una vista previa con la rotación apagada es algo puntual: al acabar volvemos a
            // dejarlo todo como estaba, sin colar el carrusel por la puerta de atrás.
            if (repository?.isRotationEnabled == false) {
                stopAndClear()
                stopSelf()
            } else {
                updateDisplay(forceNewGif = true)
            }
        }
    }

    /**
     * Un objeto en la mochila, por cualquiera de las tres vías (captura, día perfecto, logro).
     *
     * **Sin animación todavía.** El usuario va a diseñar la suya —algo parecido a la captura,
     * pero con un objeto en vez de un Pokémon— y este es el único sitio por el que pasan las
     * tres, así que aquí es donde hay que engancharla: sustituir este cuerpo por el
     * `takeOverMatrix()` + dibujado que corresponda, siguiendo el mismo molde que [triggerCatch]
     * un poco más abajo. Hasta entonces, no toca la Matrix — el objeto ya se ha guardado en el
     * inventario en cuanto se llega aquí, así que no hacer nada aquí no pierde el premio, solo
     * no lo anuncia en pantalla.
     */
    private fun itemReceived(item: PokemonItem) {
        Log.i(TAG, "Objeto recibido: ${item.name}")
    }

    /**
     * Captura de un Pokémon concreto: se le ve moverse, la bola se cierra encima hasta que
     * desaparece dentro, y después la pokeball se agita.
     *
     * Si no hay sprite de esa especie se cae a la versión de siempre (círculo a secas), que
     * es lo que había antes de que existieran los sprites.
     */
    private fun triggerCatch(dexNumber: Int) {
        val ctrl = controller ?: return
        val myGeneration = startAnimationGeneration()
        // La captura también a brillo completo, por lo mismo.
        ctrl.brightness = FULL_BRIGHTNESS
        takeOverMatrix()

        catchTestJob = serviceScope.launch {
            try {
                if (!awaitMatrixReady(ctrl)) {
                    warnMatrixBusy()
                    return@launch
                }
                val sprite = loadSprite(dexNumber, ctrl.matrixSize)
                if (sprite != null) playCapture(ctrl, sprite) else playPlainShrink(ctrl)
                playPokeball(ctrl)
                delay(PokeballCatchAnimation.POKEBALL_HOLD_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en la animación de captura", e)
            } finally {
                CatchHaptics.cancel(applicationContext)
                finishAnimationGeneration(myGeneration)
            }
        }
    }

    /**
     * Enseña el huevo en una de sus tres fases, en bucle, para poder compararlas.
     *
     * Comparte el hueco de la captura porque, igual que ella, es una animación que manda
     * mientras dura: si el carrusel se colara por encima no se podría juzgar nada.
     */
    private fun triggerEgg(stage: EggAnimation.Stage) {
        val ctrl = controller ?: return
        val myGeneration = startAnimationGeneration()
        ctrl.brightness = FULL_BRIGHTNESS
        takeOverMatrix()

        catchTestJob = serviceScope.launch {
            try {
                if (!awaitMatrixReady(ctrl)) {
                    warnMatrixBusy()
                    return@launch
                }
                playEgg(ctrl, stage, EGG_PREVIEW_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en la animación del huevo", e)
            } finally {
                finishAnimationGeneration(myGeneration)
            }
        }
    }

    /**
     * La eclosión: el huevo sacudiéndose, el destello al romperse y el recién nacido.
     *
     * Ocurre con la pantalla apagada, que es cuando se incuba, así que la Matrix es lo único
     * encendido y se ve entera. La vibración va acelerando con las sacudidas para que se note
     * en la mano aunque tengas el móvil boca abajo.
     */
    private fun triggerHatch(speciesId: Int) {
        val ctrl = controller ?: return
        val myGeneration = startAnimationGeneration()
        ctrl.brightness = FULL_BRIGHTNESS
        takeOverMatrix()

        catchTestJob = serviceScope.launch {
            try {
                if (!awaitMatrixReady(ctrl)) {
                    warnMatrixBusy()
                    return@launch
                }
                CatchHaptics.playPattern(applicationContext, hatchBeats())
                playEgg(ctrl, EggAnimation.Stage.HATCHING, HATCH_SHAKE_MS)

                // El destello de la cáscara al romperse: el mismo recurso que usa la evolución
                // para tapar el cambio de una forma por otra.
                //
                // El tamaño se pregunta al dispositivo. Antes iba con las constantes del Phone (3)
                // —489 LEDs en una rejilla de 25— clavadas: en la matriz de 13 del (4a) Pro eso
                // construía un bitmap del tamaño equivocado con casi cuatro veces los valores que
                // hacen falta.
                val ledsEncendidos = GlyphLedLayout.ledCount(ctrl.matrixSize)
                repeat(HATCH_FLASHES) {
                    ctrl.showFrame(
                        GlyphLedLayout.toBitmap(IntArray(ledsEncendidos) { 255 }, ctrl.matrixSize)
                    )
                    delay(HATCH_FLASH_MS)
                    ctrl.showFrame(
                        GlyphLedLayout.toBitmap(IntArray(ledsEncendidos), ctrl.matrixSize)
                    )
                    delay(HATCH_FLASH_MS)
                }

                val sprite = loadSprite(speciesId, ctrl.matrixSize)
                if (sprite != null && !sprite.isEmpty) {
                    var elapsed = 0L
                    var frame = 0
                    while (elapsed < HATCH_REVEAL_MS) {
                        val index = frame % sprite.frames.size
                        ctrl.showFrame(sprite.frames[index])
                        val duration = sprite.frameDurationsMs[index]
                        delay(duration)
                        elapsed += duration
                        frame++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en la eclosión", e)
            } finally {
                CatchHaptics.cancel(applicationContext)
                finishAnimationGeneration(myGeneration)
            }
        }
    }

    /**
     * Los golpes de la eclosión: sacudidas cada vez más seguidas y un remate al romperse.
     *
     * Los tiempos se reparten sobre lo que dura la parte del huevo, así que si esa duración
     * cambia el tacto la sigue en vez de quedarse desfasado.
     */
    private fun hatchBeats(): List<CatchHaptics.Beat> {
        val beats = ArrayList<CatchHaptics.Beat>()
        var moment = 0L
        var gap = HATCH_FIRST_GAP_MS
        while (moment + gap < HATCH_SHAKE_MS) {
            moment += gap
            beats += CatchHaptics.Beat(moment)
            gap = (gap * HATCH_GAP_ACCELERATION).toLong().coerceAtLeast(HATCH_MIN_GAP_MS)
        }
        beats += CatchHaptics.Beat(HATCH_SHAKE_MS, strong = true)
        return beats
    }

    /** El huevo en bucle durante [durationMs]. */
    private suspend fun playEgg(
        ctrl: GlyphMatrixController,
        stage: EggAnimation.Stage,
        durationMs: Long,
    ) {
        val animation = EggAnimation.build(ctrl.matrixSize, stage)
        var elapsed = 0L
        var frame = 0
        while (elapsed < durationMs) {
            val index = frame % animation.frames.size
            ctrl.showFrame(animation.frames[index])
            val duration = animation.frameDurationsMs[index]
            delay(duration)
            elapsed += duration
            frame++
        }
    }

    /**
     * La evolución en la Matrix: se ve al de antes, ocurre el cambio y aparece el nuevo.
     *
     * Comparte el mismo hueco que la captura ([isCatchTestActive]) a propósito: son las dos
     * animaciones que mandan sobre todo lo demás mientras duran, así que ni el carrusel ni el
     * líquido de carga pueden colarse por encima.
     */
    private fun triggerEvolution(
        style: EvolutionAnimation.Style,
        fromDex: Int,
        toDex: Int,
    ) {
        val ctrl = controller ?: return
        val myGeneration = startAnimationGeneration()
        ctrl.brightness = FULL_BRIGHTNESS
        takeOverMatrix()

        catchTestJob = serviceScope.launch {
            try {
                if (!awaitMatrixReady(ctrl)) {
                    warnMatrixBusy()
                    return@launch
                }
                playEvolution(ctrl, style, fromDex, toDex)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en la animación de evolución", e)
            } finally {
                CatchHaptics.cancel(applicationContext)
                finishAnimationGeneration(myGeneration)
            }
        }
    }

    /**
     * Monta y reproduce la evolución de [fromDex] a [toDex].
     *
     * Sin sprite de alguna de las dos especies no hay nada que enseñar —la animación entera
     * consiste en comparar una forma con otra—, así que se avisa en vez de pintar un destello
     * suelto que no significaría nada.
     */
    private suspend fun playEvolution(
        ctrl: GlyphMatrixController,
        style: EvolutionAnimation.Style,
        fromDex: Int,
        toDex: Int,
    ) {
        val fromSprite = loadSprite(fromDex, ctrl.matrixSize)
        val toSprite = loadSprite(toDex, ctrl.matrixSize)
        if (fromSprite == null || toSprite == null || fromSprite.isEmpty || toSprite.isEmpty) {
            Log.w(TAG, "Sin sprites para la evolución $fromDex -> $toDex")
            return
        }

        val sequence = EvolutionAnimation.build(
            from = fromSprite.frames.first(),
            to = toSprite.frames.first(),
            matrixSize = ctrl.matrixSize,
            style = style,
        )

        // La vibración se lanza entera antes de empezar a pintar: es un patrón con sus propias
        // pausas, así que se marca el ritmo sola y no se descuadra si un frame tarda de más.
        CatchHaptics.playPattern(applicationContext, sequence.beats)

        val animation = sequence.animation
        for (index in animation.frames.indices) {
            ctrl.showFrame(animation.frames[index])
            delay(animation.frameDurationsMs[index])
        }

        // Y para terminar, el nuevo moviéndose: la animación acaba en una foto fija, y verlo
        // animado es lo que remata la idea de que ahora **es** ese Pokémon.
        var elapsed = 0L
        var frame = 0
        while (elapsed < EVOLUTION_OUTRO_MS) {
            val index = frame % toSprite.frames.size
            ctrl.showFrame(toSprite.frames[index])
            val duration = toSprite.frameDurationsMs[index]
            delay(duration)
            elapsed += duration
            frame++
        }
    }

    /**
     * Espera a que la Matrix esté conectada, hasta [timeoutMs].
     *
     * El SDK conecta de forma asíncrona, así que un servicio recién arrancado todavía no
     * puede pintar: lo que se le mande antes se pierde sin avisar.
     */
    private suspend fun awaitMatrixReady(
        ctrl: GlyphMatrixController,
        timeoutMs: Long = MATRIX_READY_TIMEOUT_MS,
    ): Boolean {
        var waited = 0L
        while (!ctrl.isConnected && waited < timeoutMs) {
            // Se reintenta cada segundo en vez de esperar de brazos cruzados: el primer
            // intento puede quedarse sin callback y entonces no llega nunca solo.
            if (waited > 0 && waited % MATRIX_RECONNECT_EVERY_MS == 0L) ctrl.reconnect()
            delay(MATRIX_READY_POLL_MS)
            waited += MATRIX_READY_POLL_MS
        }
        return ctrl.isConnected
    }

    /**
     * Avisa de que no se pudo tomar la Matrix.
     *
     * Pasa cuando otra app de Glyph la tiene cogida —GlyphMuseum, sin ir más lejos, incluso
     * desde segundo plano—: el SDK no da ningún error, simplemente no llama nunca al callback
     * de conexión. Sin este aviso el usuario solo ve que "no pasa nada" al tocar.
     */
    private fun warnMatrixBusy() {
        Log.w(TAG, "La Matrix no conectó: probablemente otra app de Glyph la tiene cogida")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                applicationContext,
                R.string.toast_matrix_busy,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private suspend fun loadSprite(dexNumber: Int, matrixSize: Int): GifAnimation? {
        val uri = SpriteLibrary(applicationContext).uriFor(dexNumber) ?: return null
        return runCatching {
            MediaFrameDecoder.decode(
                applicationContext,
                uri,
                matrixSize,
                SpriteRenderModes(applicationContext).modeFor(dexNumber)
            )
        }.getOrNull()
    }

    /**
     * Primero el Pokémon suelto, y después el mismo Pokémon visto por la abertura de la bola
     * mientras se cierra. Sigue animándose durante el cierre: si se congelara, parecería que
     * lo que se traga la bola es una foto.
     */
    private suspend fun playCapture(ctrl: GlyphMatrixController, sprite: GifAnimation) {
        var frame = 0
        var elapsed = 0L
        while (elapsed < PokeballCatchAnimation.POKEMON_INTRO_MS) {
            val index = frame % sprite.frames.size
            ctrl.showFrame(sprite.frames[index])
            val duration = sprite.frameDurationsMs[index]
            delay(duration)
            elapsed += duration
            frame++
        }

        for (step in PokeballCatchAnimation.buildShrinkSteps(ctrl.matrixSize)) {
            val index = frame % sprite.frames.size
            ctrl.showFrame(
                PokeballCatchAnimation.captureStepBitmap(
                    sprite.frames[index],
                    ctrl.matrixSize,
                    step.diameter
                )
            )
            delay(step.durationMs)
            frame++
        }
        // El golpe seco justo cuando el Pokémon desaparece dentro de la bola.
        CatchHaptics.ballClosed(applicationContext)
    }

    private suspend fun playPlainShrink(ctrl: GlyphMatrixController) {
        val shrink = PokeballCatchAnimation.buildShrinkFrames(ctrl.matrixSize)
        for (i in shrink.frames.indices) {
            ctrl.showFrame(shrink.frames[i])
            delay(shrink.frameDurationsMs[i])
        }
    }

    /**
     * Las tres sacudidas y el clic. Se dibujan aquí en vez de usar el GIF de la pokeball
     * porque así el balanceo y el destello de cada latido van al mismo compás que el cierre
     * anterior; con un GIF de duración fija no habría forma de sincronizarlos.
     */
    /**
     * La pokeball: la animación de Glyph Museum que trajo el usuario, tal cual la diseñó su
     * autor —caída, sacudidas y confirmación—, mandando el brillo de cada LED directamente.
     *
     * Si el archivo faltara o viniera roto se cae a la versión dibujada por código, para que
     * la captura nunca se quede sin animación.
     */
    private suspend fun playPokeball(ctrl: GlyphMatrixController) {
        // El asset de la pokeball es una exportación de Glyph Museum: **489 valores, uno por LED
        // físico del Phone (3)**, colocados en su círculo concreto. No se puede reescalar — en la
        // matriz de 13 del (4a) Pro, con 137 LEDs, se leerían los primeros 137 valores y saldría
        // ruido. Es el mismo caso que el arte del vinilo: contenido dibujado para una disposición
        // de LEDs, no algo con un tamaño que se pueda cambiar.
        //
        // Por suerte ya había salida: la versión dibujada por código se genera al tamaño que se le
        // pida. Aquí solo se decide entrar por ella.
        val data = if (ctrl.matrixSize == GlyphLedLayout.MATRIX_SIZE) {
            runCatching {
                GlyphDataAnimation.fromAsset(applicationContext, POKEBALL_ASSET)
            }.getOrNull()
        } else {
            null
        }

        if (data == null || data.frames.isEmpty()) {
            Log.w(TAG, "Sin la animación de la pokeball: se usa la dibujada por código")
            val wobble = PokeballCatchAnimation.buildWobbleFrames(ctrl.matrixSize)
            CatchHaptics.wobbleSequence(applicationContext)
            for (i in wobble.frames.indices) {
                ctrl.showFrame(wobble.frames[i])
                delay(wobble.frameDurationsMs[i])
            }
            return
        }

        // Reordenada: primero la caída, luego tres balanceos y al final quieta inclinada.
        // La original venía al revés —se balanceaba antes de saltar— y así no contaba la
        // captura, solo enseñaba una bola moviéndose.
        val sequence = PokeballSequence.build(data)

        // Los golpes van en el impacto y en cada extremo de cada balanceo, calculados sobre
        // la secuencia ya montada.
        CatchHaptics.atMoments(
            applicationContext,
            PokeballSequence.hapticMoments(source = data),
            sequence.totalDurationMs,
        )
        // Se pintan como bitmap y no con setAppMatrixFrame(int[]): esa ruta no pasa por
        // GlyphMatrixObject, que es donde se aplica el brillo, y por eso la pokeball salía
        // apagada al lado de la captura por mucho que se subieran los valores.
        for (i in sequence.frames.indices) {
            ctrl.showFrame(GlyphLedLayout.toBitmap(sequence.frames[i], ctrl.matrixSize))
            delay(sequence.durationsMs[i])
        }
    }

    /**
     * Fuerza el líquido de batería en pantalla 20s al detectar que se enchufó/desenchufó
     * el cable. Al enchufar, el número sube animado de 0 al % real (efecto "llenado");
     * al desenchufar, se muestra el líquido directamente en su nivel real, sin repetir
     * esa animación de inicio (no tendría sentido "vaciarlo" solo por desconectar).
     */
    private fun triggerPlugFlash(justPlugged: Boolean) {
        plugFlashJob?.cancel()
        isPlugFlashActive = true
        // Si hay una captura en marcha no se pinta encima: el decisor ya le da prioridad, pero
        // esta llamada se lo saltaba y el líquido aparecía sobre la pokeball. Se deja la
        // bandera puesta y al acabar la captura updateDisplay() lo saca si aún toca.
        if (!isCatchTestActive) startChargingLiquid(instant = !justPlugged)
        plugFlashJob = serviceScope.launch {
            delay(PLUG_FLASH_MS)
            isPlugFlashActive = false
            updateDisplay()
        }
    }

    /** Retoma el giro justo donde se quedó si ya estábamos mostrando el vinilo (pausado o no). */
    private fun startVinylSpin() {
        val gifPlayer = player ?: return
        val resumeFromCurrent = isShowingVinyl
        if (!resumeFromCurrent) {
            clockPlayer?.stop()
            liquidBatteryPlayer?.stop()
        }
        isShowingVinyl = true
        isVinylStatic = false
        isShowingClock = false
        isShowingCharging = false
        gifPlayer.play(vinylSpinAnimation, if (resumeFromCurrent) gifPlayer.currentIndex else 0)
    }

    /**
     * Congela el disco tal cual está en ese instante.
     *
     * Parar el reproductor basta **solo si el disco ya estaba puesto**, porque la Matrix conserva
     * el último frame pintado. Si veníamos del reloj no hay ningún disco pintado y lo que quedaría
     * clavado sería la hora, así que en ese caso hay que pintar un frame a mano.
     *
     * No sirve `play()` seguido de `stop()`: [GlyphGifPlayer.play] lanza una corrutina y el primer
     * `showFrame` no llega a ocurrir antes del `stop()`. Se pinta directamente por el controlador,
     * que es la misma llamada que haría el reproductor.
     *
     * Y se paran los otros dos reproductores, que era la otra mitad del fallo 25: el reloj es un
     * bucle con `delay`, así que seguía repintando la hora encima del disco congelado.
     */
    private fun showVinylStatic() {
        val gifPlayer = player ?: return
        val discoYaPuesto = isShowingVinyl

        gifPlayer.stop()
        clockPlayer?.stop()
        liquidBatteryPlayer?.stop()

        if (!discoYaPuesto && !vinylSpinAnimation.isEmpty) {
            controller?.showFrame(vinylSpinAnimation.frames[0])
        }

        isShowingVinyl = true
        isVinylStatic = true
        isShowingClock = false
        isShowingCharging = false
    }

    /**
     * Reloj en reposo (12h + AM/PM), mientras la pantalla está apagada y no hay música/carga.
     *
     * Para a los otros dos reproductores y apaga sus banderas, igual que hacen [startVinylSpin] y
     * [startChargingLiquid]. Era la única de las tres que no lo hacía, y esa asimetría dejaba
     * pasar el fallo 25: al volver la música, `startVinylSpin` leía `isShowingVinyl` —que seguía
     * en true desde antes del reloj— y creía estar retomando el disco, así que se saltaba el
     * `clockPlayer?.stop()`. El reloj es un bucle con `delay`, no un dibujo suelto: se quedaba
     * repintando por encima del vinilo cada tick.
     */
    private fun startClockStandby() {
        val clock = clockPlayer ?: return
        player?.stop()
        liquidBatteryPlayer?.stop()
        isShowingVinyl = false
        isVinylStatic = false
        isShowingCharging = false
        isShowingClock = true
        clock.start()
    }

    /** Simulación de líquido con el % mientras el teléfono está cargando. */
    private fun startChargingLiquid(instant: Boolean = false) {
        val liquid = liquidBatteryPlayer ?: return
        player?.stop()
        clockPlayer?.stop()
        isShowingVinyl = false
        isVinylStatic = false
        isShowingClock = false
        isShowingCharging = true
        liquid.start(instant) { currentBatteryInfo().first }
    }

    private fun stopAndClear() {
        takeOverMatrix()
        controller?.clear()
        isShowingVinyl = false
        isVinylStatic = false
        isShowingClock = false
        isShowingWild = false
        isShowingCharging = false
        bluetoothFlashJob?.cancel()
        isBluetoothFlashActive = false
        catchTestJob?.cancel()
        isCatchTestActive = false
    }

    private fun refreshBatteryBrightness() {
        val ctrl = controller ?: return
        val repo = repository ?: return
        val (pct, charging) = currentBatteryInfo()
        ctrl.brightness = if (pct < repo.dimBrightnessThresholdPct) DIMMED_BRIGHTNESS else FULL_BRIGHTNESS
        isCriticalBattery = pct in 0 until repo.criticalBatteryThresholdPct
        isCharging = charging
    }

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false

    private fun currentBatteryInfo(): Pair<Int, Boolean> {
        val bm = getSystemService(BatteryManager::class.java) ?: return 100 to false
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return pct to isCharging
    }

    /**
     * Elige al azar (sin repetir el anterior) entre los GIFs/imágenes de la lista. Los frames
     * decodificados se cachean en memoria ([decodedAnimationCache]): la primera vez que se
     * muestra un GIF tarda lo que tarde en decodificar, pero las siguientes veces es
     * instantáneo (nada de esperar al desbloquear el teléfono).
     */
    private fun rotateToRandomDesign() {
        val repo = repository ?: return
        val ctrl = controller ?: return
        val gifPlayer = player ?: return
        val all = repo.getAll()
        if (all.isEmpty()) return

        clockPlayer?.stop()
        liquidBatteryPlayer?.stop()

        val next = if (all.size == 1) {
            all.first()
        } else {
            all.filter { it.id != currentGifId }.randomOrNull() ?: all.random()
        }
        currentGifId = next.id
        repo.setLastGifId(next.id)

        val cached = decodedAnimationCache[next.id]
        if (cached != null) {
            Log.d(TAG, "rotateToRandomDesign: '${next.displayName}' YA estaba en caché, play() inmediato")
            gifPlayer.play(cached)
            return
        }

        Log.d(TAG, "rotateToRandomDesign: '${next.displayName}' NO estaba en caché, decodificando ahora (esto es lo que se nota como demora)")
        val decodeStart = System.currentTimeMillis()
        serviceScope.launch {
            try {
                val animation = MediaFrameDecoder.decode(
                    applicationContext,
                    next.uri,
                    ctrl.matrixSize
                )
                Log.d(TAG, "Decode de '${next.displayName}' tardó ${System.currentTimeMillis() - decodeStart}ms")
                decodedAnimationCache[next.id] = animation
                if (currentGifId == next.id) gifPlayer.play(animation)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo decodificar ${next.uri}", e)
            }
        }
    }

    /**
     * Precarga todos los GIFs/imágenes en paralelo (no uno a uno) para que estén listos cuanto
     * antes tras conectar o cambiar la lista, y así la rotación sea instantánea de verdad.
     */
    private fun warmDecodedAnimationCache() {
        val repo = repository ?: return
        val ctrl = controller ?: return
        val pending = repo.getAll().filterNot { decodedAnimationCache.containsKey(it.id) }
        if (pending.isEmpty()) return
        val startedAt = System.currentTimeMillis()
        pending.forEach { item ->
            serviceScope.launch {
                try {
                    decodedAnimationCache[item.id] = MediaFrameDecoder.decode(applicationContext, item.uri, ctrl.matrixSize)
                    Log.d(TAG, "Precargado ${item.displayName} en ${System.currentTimeMillis() - startedAt}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo precargar ${item.uri}", e)
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        run {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val contentIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val TAG = "GlyphRotationService"
        private const val CHANNEL_ID = "glyph_rotation_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_LIST_CHANGED = "dev.glyphrotator.app.action.LIST_CHANGED"
        private const val ACTION_TEST_CATCH = "dev.glyphrotator.app.action.TEST_CATCH"
        private const val ACTION_TEST_EVOLUTION = "dev.glyphrotator.app.action.TEST_EVOLUTION"
        private const val ACTION_TEST_EGG = "dev.glyphrotator.app.action.TEST_EGG"
        private const val ACTION_RESOLVE_WILD = "dev.glyphrotator.app.action.RESOLVE_WILD"
        private const val ACTION_ITEM_RECEIVED = "dev.glyphrotator.app.action.ITEM_RECEIVED"
        private const val EXTRA_ITEM_NAME = "item_name"
        private const val EXTRA_WILD_CATCH = "wild_catch"
        private const val EXTRA_EGG_STAGE = "egg_stage"

        /** Cuánto se enseña el huevo al probarlo: lo justo para ver un par de ciclos. */
        private const val EGG_PREVIEW_MS = 6000L

        /** La eclosión: sacudidas, destellos de la cáscara y el recién nacido. */
        private const val HATCH_SHAKE_MS = 4000L
        private const val HATCH_FLASHES = 3
        private const val HATCH_FLASH_MS = 90L
        private const val HATCH_REVEAL_MS = 3000L
        private const val HATCH_FIRST_GAP_MS = 700L
        private const val HATCH_GAP_ACCELERATION = 0.78f
        private const val HATCH_MIN_GAP_MS = 130L
        private const val EXTRA_EVOLUTION_STYLE = "evolution_style"
        private const val EXTRA_EVOLUTION_FROM = "evolution_from"
        private const val EXTRA_EVOLUTION_TO = "evolution_to"

        /** Cuánto se ve al recién evolucionado moviéndose antes de volver a lo de siempre. */
        private const val EVOLUTION_OUTRO_MS = 1800L

        /**
         * La variante que se usa cuando evoluciona de verdad, no en la prueba.
         *
         * Provisional: está aquí fija a la espera de que se elija una de las cuatro probándolas
         * desde la app. Cuando esté decidida, esto pasa a ser un ajuste guardado.
         */
        private val EVOLUTION_STYLE = EvolutionAnimation.Style.BLINK
        private const val ACTION_PREVIEW = "dev.glyphrotator.app.action.PREVIEW"
        private const val EXTRA_PREVIEW_URI = "preview_uri"
        private const val EXTRA_PREVIEW_MS = "preview_ms"
        private const val EXTRA_PREVIEW_MODE = "preview_mode"
        private const val EXTRA_CATCH_DEX = "catch_dex"

        /** Margen para que el SDK conecte antes de pintar; de sobra en la práctica. */
        private const val MATRIX_READY_TIMEOUT_MS = 8_000L
        private const val MATRIX_READY_POLL_MS = 50L

        /** Cada cuánto se reintenta la conexión mientras se espera. */
        private const val MATRIX_RECONNECT_EVERY_MS = 1_000L

        /** Cada cuántos minutos en reposo se repintan los widgets aunque no pase nada. */
        private const val WIDGET_REFRESH_EVERY_MINUTES = 10

        /** Los widgets que dependen del estado del juego y hay que repintar al cambiarlo. */
        private val GAME_WIDGETS = listOf(
            dev.glyphrotator.app.ui.widget.PartnerWidget::class.java,
            dev.glyphrotator.app.ui.widget.EggWidget::class.java,
            dev.glyphrotator.app.ui.widget.PokedexWidget::class.java,
            dev.glyphrotator.app.ui.widget.CollectionWidgetProvider::class.java,
            dev.glyphrotator.app.ui.widget.BonusWidget::class.java,
        )

        /** Cada cuánto se comprueba si aparece un Pokémon salvaje. */
        /**
         * Tope de minutos que puede valer una sola ronda.
         *
         * Con el móvil dormido las rondas las trae una alarma, y si el sistema estuvo horas sin
         * despertarla —Doze agresivo, móvil en un cajón, o el servicio muerto y revivido— el
         * salto puede ser enorme. Sin tope, esa única ronda tiraría el dado por seiscientos
         * minutos de golpe y sacaría un Pokémon garantizado por haber tenido el móvil apagado,
         * que es lo contrario de lo que el juego premia. Es la misma lección de los fallos 26 a
         * 28: todo lo que se mide por diferencia de relojes necesita un techo.
         */
        private const val MAX_CATCHUP_MINUTES = 30

        /** El intent del despertador. Público para [GameTickAlarm]. */
        const val ACTION_GAME_TICK = "dev.glyphrotator.app.action.GAME_TICK"

        private const val WILD_SPAWN_POLL_MS = 60_000L

        /** El mismo bucle mientras usas el teléfono, donde no tiene nada que hacer. */
        private const val IDLE_STEP_POLL_MS = 5L * 60_000L

        /** Franja de sueño: de madrugada las apariciones se cortan casi del todo. */
        private val BEDTIME_HOURS = 1..7

        /** Nivel con el que llega un Pokémon salvaje. */
        private const val WILD_MIN_LEVEL = 3
        private const val WILD_MAX_LEVEL = 18

        /** La animación de la pokeball, exportada de Glyph Museum. */
        private const val POKEBALL_ASSET = "pokeball_catch.json"
        private const val PREVIEW_MS = 15_000L
        private const val ACTION_REFRESH = "dev.glyphrotator.app.action.REFRESH"
        /**
         * El sondeo de música de respaldo: rápido mientras suena, y relajándose hasta cinco
         * segundos cuando no. Solo se usa sin acceso a notificaciones; con él, la música llega
         * por evento y no se sondea nada.
         */
        private const val MUSIC_POLL_ACTIVE_MS = 400L
        private const val MUSIC_POLL_IDLE_MAX_MS = 5_000L
        private const val PAUSE_GRACE_MS = 5000L
        private const val FULL_BRIGHTNESS = 255
        private const val DIMMED_BRIGHTNESS = 80
        private const val PLUG_FLASH_MS = 20_000L
        private const val BLUETOOTH_FLASH_MS = 12_000L
        /**
         * Sondeo de pantalla: atento mientras está encendida, muy relajado cuando no.
         *
         * Apagado se mantiene un sondeo lento en vez de ninguno porque el servicio puede
         * arrancar con la pantalla ya apagada y perderse el primer encendido; veinte segundos de
         * retraso en el peor caso son inofensivos, y son mil veces menos despertares.
         */
        private const val UNLOCK_POLL_ACTIVE_MS = 1500L
        private const val UNLOCK_POLL_IDLE_MS = 20_000L

        private const val EXTERNAL_APP_POLL_ACTIVE_MS = 1500L
        private const val EXTERNAL_APP_POLL_IDLE_MS = 30_000L
        private const val FOREGROUND_LOOKBACK_MS = 60_000L
        private const val GLYPH_MUSEUM_PACKAGE = "com.pauwma.glyphmuseum"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GlyphRotationService::class.java))
        }

        // Lint avisa de "instancia nueva" pensando en listeners SAM, pero aquí es el patrón
        // normal de Android: el Intent identifica el servicio por componente, no por identidad.
        @Suppress("ImplicitSamInstance")
        fun stop(context: Context) {
            context.stopService(Intent(context, GlyphRotationService::class.java))
        }

        /** Avisa al servicio (si está corriendo) de que la lista de GIFs cambió desde la UI. */
        fun notifyListChanged(context: Context) {
            val intent = Intent(context, GlyphRotationService::class.java).setAction(ACTION_LIST_CHANGED)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Lanza la animación de captura una vez, para probarla desde el botón de la app. */
        /**
         * Rearranca el servicio si hiciera falta y le hace releer el estado real. Se llama al
         * abrir la app: si estás mirando la pantalla, el teléfono está encendido y
         * desbloqueado, así que lo que toca es el carrusel.
         */
        fun refresh(context: Context) {
            val intent = Intent(context, GlyphRotationService::class.java).setAction(ACTION_REFRESH)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Muestra ese diseño en la Matrix ahora mismo. [durationMs] permite acortarlo para
         * el pase automático de sprites, donde 15 segundos por Pokémon serían casi 40
         * minutos para ver los 151.
         */
        fun preview(
            context: Context,
            uri: android.net.Uri,
            durationMs: Long = PREVIEW_MS,
            mode: MatrixImageProcessor.RenderMode = MatrixImageProcessor.RenderMode.LUMINANCE,
        ) {
            val intent = Intent(context, GlyphRotationService::class.java)
                .setAction(ACTION_PREVIEW)
                .putExtra(EXTRA_PREVIEW_URI, uri.toString())
                .putExtra(EXTRA_PREVIEW_MS, durationMs)
                .putExtra(EXTRA_PREVIEW_MODE, mode.name)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Reproduce la captura del Pokémon [dexNumber]: se le ve moverse, la bola se cierra
         * encima y desaparece dentro. Con 0 se usa la versión sin Pokémon, solo el círculo.
         */
        fun catchAnimation(context: Context, dexNumber: Int = 0) {
            val intent = Intent(context, GlyphRotationService::class.java)
                .setAction(ACTION_TEST_CATCH)
                .putExtra(EXTRA_CATCH_DEX, dexNumber)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Avisa de que [item] acaba de entrar en la mochila, desde fuera del servicio —captura
         * por el botón físico, o un logro grande recién desbloqueado—.
         *
         * [source] no se usa todavía: queda ahí para cuando la animación quiera distinguir de
         * dónde vino el objeto (un logro grande podría merecer un destello más largo que una
         * captura de cada día).
         */
        fun itemReceivedAnimation(
            context: Context,
            item: PokemonItem,
            @Suppress("UNUSED_PARAMETER") source: ItemDropTable.Source,
        ) {
            val intent = Intent(context, GlyphRotationService::class.java)
                .setAction(ACTION_ITEM_RECEIVED)
                .putExtra(EXTRA_ITEM_NAME, item.name)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Atrapa o deja ir al salvaje que esté esperando, desde la app. */
        fun resolveWild(context: Context, catchIt: Boolean) {
            val intent = Intent(context, GlyphRotationService::class.java)
                .setAction(ACTION_RESOLVE_WILD)
                .putExtra(EXTRA_WILD_CATCH, catchIt)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Enseña el huevo en la fase [stage] durante unos segundos. */
        fun eggAnimation(context: Context, stage: EggAnimation.Stage) {
            val intent = Intent(context, GlyphRotationService::class.java)
                .setAction(ACTION_TEST_EGG)
                .putExtra(EXTRA_EGG_STAGE, stage.name)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Reproduce la evolución de [fromDex] a [toDex] con la variante [style]. */
        fun evolutionAnimation(
            context: Context,
            style: EvolutionAnimation.Style,
            fromDex: Int,
            toDex: Int,
        ) {
            val intent = Intent(context, GlyphRotationService::class.java)
                .setAction(ACTION_TEST_EVOLUTION)
                .putExtra(EXTRA_EVOLUTION_STYLE, style.name)
                .putExtra(EXTRA_EVOLUTION_FROM, fromDex)
                .putExtra(EXTRA_EVOLUTION_TO, toDex)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
