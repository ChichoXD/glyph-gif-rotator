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
import dev.glyphrotator.app.data.GifRepository
import dev.glyphrotator.app.glyph.ClockPlayer
import dev.glyphrotator.app.glyph.GifAnimation
import dev.glyphrotator.app.glyph.GlyphGifPlayer
import dev.glyphrotator.app.glyph.GlyphLedLayout
import dev.glyphrotator.app.glyph.MatrixImageProcessor
import dev.glyphrotator.app.glyph.GlyphMatrixController
import dev.glyphrotator.app.glyph.LiquidBatteryPlayer
import dev.glyphrotator.app.glyph.MediaFrameDecoder
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
    private val vinylSpinAnimation by lazy { VinylBeatAnimation.buildSpinAnimation() }
    // Estos flags los tocan tanto los BroadcastReceiver (hilo principal) como las corrutinas
    // de fondo (sondeos, temporizadores), así que van @Volatile para que un hilo vea siempre
    // el último valor escrito por el otro.
    @Volatile private var isScreenOn = false
    @Volatile private var isUnlocked = false
    @Volatile private var isCriticalBattery = false
    @Volatile private var isCharging = false
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


    private var lastKnownForegroundPackage: String? = null
    private var lastForegroundQueryEnd = 0L
    private var pauseGraceJob: Job? = null
    private var plugFlashJob: Job? = null
    private var bluetoothFlashJob: Job? = null
    private var notificationFlashJob: Job? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    isUnlocked = !isKeyguardLocked()
                    refreshBatteryBrightness()
                    updateDisplay(forceNewGif = true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    isUnlocked = false
                    updateDisplay()
                }
                Intent.ACTION_USER_PRESENT -> {
                    isUnlocked = true
                    updateDisplay(forceNewGif = true)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val wasCritical = isCriticalBattery
                    val wasCharging = isCharging
                    refreshBatteryBrightness()
                    if (isCharging != wasCharging) triggerPlugFlash(justPlugged = isCharging)
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

        // startForeground() debe llamarse cuanto antes: un servicio arrancado con
        // startForegroundService() que no lo hace a tiempo provoca un crash del sistema,
        // incluso si luego decidimos pararlo por ser un dispositivo no compatible.
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Common.is23112()) {
            Log.e(TAG, "Este dispositivo no es un Nothing Phone (3): deteniendo el servicio")
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

    /**
     * Resuelve desde la app al salvaje que esté esperando: o se atrapa, o se deja ir.
     *
     * Existe como salida de emergencia. La captura de verdad es la pulsación larga del botón
     * físico, pero eso depende de que nuestro toy sea el que está seleccionado en los ajustes de
     * Glyph; si no lo es, el botón no nos llega, y entonces se ve un Pokémon en la Matrix que no
     * hay forma de atrapar ni de quitar. Con esto siempre queda una salida.
     */

    /**
     * Comprueba si se ha desbloqueado algún logro y lo apunta.
     *
     * Sin animación en la Matrix a propósito: los logros caen justo cuando acaba de pasar otra
     * cosa —una captura, una evolución— y una segunda animación encima taparía la que estabas
     * mirando. Se apuntan y se ven en su pantalla.
     */

    /**
     * El multiplicador de experiencia que dejó el día de ayer.
     *
     * Se recalcula en cada ronda en vez de guardarse: son tres lecturas de preferencias una vez
     * por minuto, y así no hay que acordarse de invalidar nada cuando cambian los hábitos o el
     * objetivo de agua.
     */

    /**
     * Repinta los widgets que dependen del estado del juego.
     *
     * El periodo del sistema es de media hora como mínimo, así que sin este empujón una captura
     * o una eclosión tardarían eso en aparecer en el escritorio y parecería que están rotos.
     */

    /**
     * Incuba el huevo mientras el teléfono está en reposo, y lo abre cuando toca.
     *
     * Se incuba con el mismo criterio que se entrena: pantalla apagada. Así las dos cosas que
     * progresan solas premian lo mismo —dejar el teléfono quieto— en vez de tirar cada una por
     * su lado.
     */

    /** Lo que el mundo real aporta ahora mismo a la aparición. */

    /**
     * Enseña al Pokémon salvaje en la Matrix, en bucle y tenue.
     *
     * Tenue a propósito: aparece con la pantalla apagada y puede pasar horas ahí, así que va
     * al mismo brillo bajo que el reloj en reposo en vez de a tope toda la noche.
     */

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

        isScreenOn = actuallyScreenOn
        isUnlocked = actuallyUnlocked
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
            wildSpawnWaiting = false,
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
        clockPlayer?.stop()
        liquidBatteryPlayer?.stop()

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
        clockPlayer?.stop()
        liquidBatteryPlayer?.stop()

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
     * Captura de un Pokémon concreto: se le ve moverse, la bola se cierra encima hasta que
     * desaparece dentro, y después la pokeball se agita.
     *
     * Si no hay sprite de esa especie se cae a la versión de siempre (círculo a secas), que
     * es lo que había antes de que existieran los sprites.
     */

    /**
     * Enseña el huevo en una de sus tres fases, en bucle, para poder compararlas.
     *
     * Comparte el hueco de la captura porque, igual que ella, es una animación que manda
     * mientras dura: si el carrusel se colara por encima no se podría juzgar nada.
     */

    /**
     * La eclosión: el huevo sacudiéndose, el destello al romperse y el recién nacido.
     *
     * Ocurre con la pantalla apagada, que es cuando se incuba, así que la Matrix es lo único
     * encendido y se ve entera. La vibración va acelerando con las sacudidas para que se note
     * en la mano aunque tengas el móvil boca abajo.
     */
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


    /**
     * Primero el Pokémon suelto, y después el mismo Pokémon visto por la abertura de la bola
     * mientras se cierra. Sigue animándose durante el cierre: si se congelara, parecería que
     * lo que se traga la bola es una foto.
     */


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
     * Congela el disco tal cual está en ese instante: parar el reproductor basta, porque
     * la Glyph Matrix conserva el último frame pintado hasta que se envíe uno nuevo.
     */
    private fun showVinylStatic() {
        val gifPlayer = player ?: return
        gifPlayer.stop()
        isShowingVinyl = true
        isVinylStatic = true
    }

    /** Reloj en reposo (12h + AM/PM), mientras la pantalla está apagada y no hay música/carga. */
    private fun startClockStandby() {
        val clock = clockPlayer ?: return
        player?.stop()
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
        private const val EXTRA_WILD_CATCH = "wild_catch"
        private const val EXTRA_EGG_STAGE = "egg_stage"

        /** Cuánto se enseña el huevo al probarlo: lo justo para ver un par de ciclos. */

        /** La eclosión: sacudidas, destellos de la cáscara y el recién nacido. */
        private const val EXTRA_EVOLUTION_STYLE = "evolution_style"
        private const val EXTRA_EVOLUTION_FROM = "evolution_from"
        private const val EXTRA_EVOLUTION_TO = "evolution_to"

        /** Cuánto se ve al recién evolucionado moviéndose antes de volver a lo de siempre. */

        /**
         * La variante que se usa cuando evoluciona de verdad, no en la prueba.
         *
         * Provisional: está aquí fija a la espera de que se elija una de las cuatro probándolas
         * desde la app. Cuando esté decidida, esto pasa a ser un ajuste guardado.
         */
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

        /** Cada cuánto se comprueba si aparece un Pokémon salvaje. */
        private const val WILD_SPAWN_POLL_MS = 60_000L

        /** El mismo bucle mientras usas el teléfono, donde no tiene nada que hacer. */
        private const val IDLE_STEP_POLL_MS = 5L * 60_000L

        /** Franja de sueño: de madrugada las apariciones se cortan casi del todo. */
        private val BEDTIME_HOURS = 1..7

        /** Nivel con el que llega un Pokémon salvaje. */
        private const val WILD_MIN_LEVEL = 3
        private const val WILD_MAX_LEVEL = 18

        /** La animación de la pokeball, exportada de Glyph Museum. */
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
}
}
