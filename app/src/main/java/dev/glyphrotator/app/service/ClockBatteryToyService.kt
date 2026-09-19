package dev.glyphrotator.app.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.launch
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import dev.glyphrotator.app.glyph.GlyphMatrixConnection
import dev.glyphrotator.app.glyph.GlyphTextMetrics
import dev.glyphrotator.app.glyph.LiquidBatteryFrameRenderer
import dev.glyphrotator.app.glyph.LiquidPhysics
import dev.glyphrotator.app.data.AppPreferences
import dev.glyphrotator.app.achievements.AchievementRewards
import dev.glyphrotator.app.achievements.AchievementStore
import dev.glyphrotator.app.pokemon.EggStore
import dev.glyphrotator.app.pokemon.ItemDropStore
import dev.glyphrotator.app.pokemon.ItemDropTable
import dev.glyphrotator.app.pokemon.SpriteLibrary
import dev.glyphrotator.app.pokemon.SpriteRenderModes
import dev.glyphrotator.app.pokemon.PokemonRepository
import dev.glyphrotator.app.pokemon.spawn.WildSpawnStore
import java.util.Calendar

/**
 * Glyph Toy real (registrado en el carrusel del botón físico trasero), independiente del
 * [GlyphRotationService]. Muestra un reloj tenue por defecto (mismo brillo bajo que el reloj
 * en reposo de la app, para que sea un único estado consistente en vez de dos distintos); al
 * mantener pulsado el botón (`GlyphToy.EVENT_CHANGE`) cambia unos segundos a la batería con
 * simulación de líquido (misma física de inclinación que
 * [dev.glyphrotator.app.glyph.LiquidBatteryPlayer], vía [LiquidPhysics], para que no se
 * desincronicen). Al ser un toy aparte del de glyph-catch, el botón nunca interfiere con la
 * captura de Pokémon: cada toy solo recibe los eventos mientras es el que está activo en el
 * carrusel.
 */
class ClockBatteryToyService : Service(), SensorEventListener {

    /** El manager de la conexión compartida; null mientras no esté lista. */
    private val glyphMatrixManager: GlyphMatrixManager?
        get() = GlyphMatrixConnection.requireManager()
    private var sensorManager: SensorManager? = null
    private var sensorRegistered = false
    private var thread: HandlerThread? = null
    private var tickHandler: Handler? = null
    private var tickRunnable: Runnable? = null
    private var revertToClockRunnable: Runnable? = null
    // Los escribe onLongPress (hilo principal) y los lee el ticker (HandlerThread propio),
    // así que van @Volatile para que el ticker vea el cambio al instante.
    @Volatile private var showingBattery = false
    @Volatile private var batteryViewStartTime = 0L

    /**
     * El compañero, dibujado por **este** servicio y no por el de rotación.
     *
     * Antes `tryShowPartner()` llamaba a `GlyphRotationService.preview()`, que pinta con
     * `setAppMatrixFrame` — el canal de app. Pero mientras nuestro toy es el elegido en el
     * carrusel del botón, la Matrix la manda el canal de toy (`setMatrixFrame`): lo que se
     * mandaba por el otro canal no llegaba a verse nunca. Se activaba la opción, se pulsaba el
     * botón y no pasaba nada.
     *
     * La batería sí funcionaba porque siempre se dibujó aquí. Ahora el compañero también.
     */
    @Volatile private var showingPartner = false
    @Volatile private var partnerFrames: List<android.graphics.Bitmap> = emptyList()
    @Volatile private var partnerFrameIndex = 0

    private val decodeScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        )

    /**
     * El lado de la matriz cuadrada de este dispositivo, en LEDs: 25 en el Phone (3), 13 en el
     * (4a) Pro. Antes era una constante clavada a 25 — funcionaba porque solo existía un
     * modelo. Se pregunta al SDK (`Common.getDeviceMatrixLength()`) en vez de suponerlo, igual
     * que ya hace [dev.glyphrotator.app.glyph.GlyphMatrixController.matrixSize] para el otro
     * canal de dibujo: es el dato oficial del dispositivo real, no una medida investigada a
     * mano que podría no coincidir.
     */
    private val matrixSize: Int by lazy { Common.getDeviceMatrixLength() }

    /** Avisos de ambiente recibidos; en móviles sin botón marcan cuándo toca la batería. */
    @Volatile private var ambientTicks = 0
    private val liquidHeights by lazy { FloatArray(matrixSize) { matrixSize.toFloat() } }
    private val contrastBitmap by lazy { LiquidBatteryFrameRenderer.renderContrastBox(matrixSize) }

    @Volatile private var tiltX = 0f
    @Volatile private var tiltY = 0f

    override fun onBind(intent: Intent?): IBinder {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        // El hilo del ticker se monta **antes** de pedir la conexión, y ese orden no es
        // cosmético: `GlyphMatrixConnection.connect()` llama a su callback **en el acto** si la
        // conexión ya estaba lista, que es el caso normal —el servicio de rotación arranca
        // antes—. Con el orden de antes, `startTicking()` se ejecutaba dentro de esa llamada,
        // encontraba `tickHandler` a null y se iba en silencio: el toy no volvía a pintar nunca.
        //
        // Se veía como que el botón "apagaba" la Matrix: al pulsarlo, `showBatteryFor()` sí
        // marcaba la Matrix como del toy —y el reloj de la app se apartaba, como debe— pero no
        // había ticker que dibujara la batería. Ocho segundos en negro y vuelta al reloj.
        val handlerThread = HandlerThread("ClockBatteryToyTicker").apply { start() }
        thread = handlerThread
        tickHandler = Handler(handlerThread.looper)

        // Por la conexión compartida, no montando la suya: el toy y el servicio de rotación
        // usan el mismo singleton del SDK, y el que llegaba segundo se quedaba sin callback.
        GlyphMatrixConnection.connect(applicationContext) { startTicking() }

        return serviceMessenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        GlyphToyPresence.setActive(false)
        showingBattery = false
        showingPartner = false
        partnerFrames = emptyList()
        stopTicking()
        unregisterSensor()

        // **Apagar antes de irse.** La Matrix conserva el último frame que se le mandó y no se
        // borra sola: si el toy desaparece mientras estaba enseñando la batería —porque el
        // sistema lo desengancha, porque cambias de toy, o porque Android mata el proceso—, ese
        // dibujo se queda ahí para siempre. Se veía como un porcentaje congelado con el líquido
        // plano, y el botón sin hacer nada porque ya no había nadie escuchando.
        //
        // Es la misma lección de siempre en este proyecto, y este camino era el único que
        // quedaba sin cubrir: quien deja de pintar, apaga.
        GlyphMatrixConnection.turnOff()

        thread?.quitSafely()
        thread = null
        tickHandler = null
        // Sin `unInit()`: la conexión es compartida con el servicio de rotación, y desmontarla
        // aquí lo dejaba ciego —no volvía a pintar hasta reiniciar la app—. Basta con dejar de
        // dibujar; quien siga usándola la mantiene viva.
        return false
    }

    private fun registerSensor() {
        if (sensorRegistered) return
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorRegistered = true
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorManager?.unregisterListener(this)
        sensorRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        tiltX = ALPHA * tiltX + (1 - ALPHA) * event.values[0]
        tiltY = ALPHA * tiltY + (1 - ALPHA) * event.values[1]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Idempotente: si ya había un ticker en marcha se sustituye, no se duplica. */
    private fun startTicking() {
        val handler = tickHandler ?: return
        tickRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                renderCurrentFrame()
                val rapido = showingBattery || showingPartner
                handler.postDelayed(this, if (rapido) BATTERY_TICK_MS else TICK_MS)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    private fun stopTicking() {
        val handler = tickHandler ?: return
        tickRunnable?.let { handler.removeCallbacks(it) }
        revertToClockRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        revertToClockRunnable = null
    }

    private fun renderCurrentFrame() {
        val gmm = glyphMatrixManager ?: return
        val pct = batteryInfo().first

        val frame = if (showingPartner) {
            val bitmap = partnerFrames.getOrNull(partnerFrameIndex % partnerFrames.size.coerceAtLeast(1))
            if (bitmap == null) {
                showingPartner = false
                return
            }
            partnerFrameIndex++
            val sprite = GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setScale(100)
                .setPosition(0, 0)
                .setBrightness(255)
                .build()
            GlyphMatrixFrame.Builder()
                .addTop(sprite)
                .build(applicationContext)
        } else if (showingBattery) {
            // El número sube de 0 al % real a la vez que el líquido, en vez de saltar de golpe.
            val progress = ((System.currentTimeMillis() - batteryViewStartTime) / RISE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            val rampedPct = (progress * pct).toInt()
            LiquidPhysics.step(liquidHeights, rampedPct, tiltX, tiltY, matrixSize)

            val liquidObject = GlyphMatrixObject.Builder()
                .setImageSource(LiquidBatteryFrameRenderer.renderLiquid(liquidHeights, pct, matrixSize))
                .setScale(100)
                .setPosition(0, 0)
                .setBrightness(130)
                .build()
            val contrastObject = GlyphMatrixObject.Builder()
                .setImageSource(contrastBitmap)
                .setScale(100)
                .setPosition(0, 0)
                .setBrightness(255)
                .build()
            val textObject = GlyphMatrixObject.Builder()
                .setText("$rampedPct")
                // Fila calculada, no clavada: con 9 fijo el número se cortaba por abajo en 13.
                .setPosition(
                    GlyphTextMetrics.centeredX("$rampedPct", matrixSize),
                    ((matrixSize * 9) / 25)
                        .coerceAtMost(matrixSize - GlyphTextMetrics.FONT_HEIGHT)
                        .coerceAtLeast(0),
                )
                .setBrightness(255)
                .build()
            GlyphMatrixFrame.Builder()
                .addLow(liquidObject)
                .addMid(contrastObject)
                .addTop(textObject)
                .build(applicationContext)
        } else {
            val calendar = Calendar.getInstance()
            val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
            val amPm = if (hour24 < 12) "AM" else "PM"
            val showColon = calendar.get(Calendar.SECOND) % 2 == 0

            val horaEntera = "%02d:%02d".format(hour12, minute)

            if (GlyphTextMetrics.fitsIn(horaEntera, matrixSize)) {
                // Phone (3): igual que siempre, sin tocar nada.
                //
                // La X se calcula siempre con ":" para que no salte al parpadear el separador.
                val timeX = GlyphTextMetrics.centeredX(horaEntera, matrixSize)
                val timeText = "%02d%s%02d".format(hour12, if (showColon) ":" else " ", minute)

                val timeObject = GlyphMatrixObject.Builder()
                    .setText(timeText)
                    .setPosition(timeX, WIDE_TIME_Y)
                    .setBrightness(DIM_CLOCK_BRIGHTNESS)
                    .build()
                val amPmObject = GlyphMatrixObject.Builder()
                    .setText(amPm)
                    .setPosition(GlyphTextMetrics.centeredX(amPm, matrixSize), WIDE_AMPM_Y)
                    .setBrightness(DIM_CLOCK_BRIGHTNESS)
                    .build()
                GlyphMatrixFrame.Builder()
                    .addTop(timeObject)
                    .addMid(amPmObject)
                    .build(applicationContext)
            } else {
                // Phone (4a) Pro: la hora entera mide 20 píxeles y la matriz son 13. En una sola
                // línea se cortaba por la mitad — y en ese móvil el reloj es lo que se ve casi
                // todo el tiempo, porque no tiene botón Glyph.
                //
                // Dos líneas, como cualquier reloj LED pequeño: horas arriba, minutos abajo. El
                // AM/PM se cae: una tercera línea son 17 píxeles de alto y no hay sitio.
                val horas = "%02d".format(hour12)
                val minutos = "%02d".format(minute)

                // Las dos filas salen de GlyphTextMetrics, que es donde viven ahora todas las
                // alturas — antes estaban escritas a mano aquí y otra vez en GlyphMatrixController.
                val filas = GlyphTextMetrics.twoLineRows(matrixSize)
                val yHoras = filas?.first ?: 0
                val yMinutos = filas?.second ?: FONT_HEIGHT

                val horasObject = GlyphMatrixObject.Builder()
                    .setText(horas)
                    .setPosition(GlyphTextMetrics.centeredX(horas, matrixSize), yHoras)
                    .setBrightness(DIM_CLOCK_BRIGHTNESS)
                    .build()
                val minutosObject = GlyphMatrixObject.Builder()
                    .setText(minutos)
                    .setPosition(GlyphTextMetrics.centeredX(minutos, matrixSize), yMinutos)
                    .setBrightness(DIM_CLOCK_BRIGHTNESS)
                    .build()
                // El punto que parpadea en el hueco entre las dos líneas. Es lo único que queda
                // del ':' original, y hace su mismo trabajo: decir que el reloj está vivo.
                val separador = GlyphMatrixObject.Builder()
                    .setText(if (showColon) ":" else " ")
                    .setPosition(GlyphTextMetrics.centeredX(":", matrixSize), yHoras + FONT_HEIGHT)
                    .setBrightness(DIM_CLOCK_BRIGHTNESS)
                    .build()

                GlyphMatrixFrame.Builder()
                    .addTop(horasObject)
                    .addMid(minutosObject)
                    .addLow(separador)
                    .build(applicationContext)
            }
        }

        try {
            gmm.setMatrixFrame(frame.render())
        } catch (e: Exception) {
            Log.e(TAG, "setMatrixFrame falló", e)
        }
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        val bm = getSystemService(BatteryManager::class.java) ?: return 100 to false
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return pct to charging
    }

    /**
     * La pulsación larga captura al Pokémon salvaje si hay uno esperando.
     *
     * Es el gesto central del juego: se captura **sin encender la pantalla**, que es justo lo
     * que el juego premia. Recogerlo al desbloquear el teléfono, como estaba antes, iba en
     * contra de la propia mecánica.
     *
     * Sin nadie esperando, el botón hace lo de siempre: la batería con líquido. El gesto
     * significa una cosa u otra según lo que haya en la Matrix, y eso se entiende solo.
     */
    /**
     * Captura al que estuviera esperando. Devuelve false si no había ninguno.
     *
     * La animación la pinta el servicio de rotación por el canal de app: el toy deja de
     * dibujar mientras dura, para no competir con ella por la Matrix.
     */
    private fun tryCatchWildSpawn(): Boolean {
        val store = WildSpawnStore(applicationContext)

        // Caducar primero. `pendingSpeciesId` solo lee la preferencia: no caduca solo, y quien lo
        // caduca es el servicio de rotación. Con la rotación apagada ese servicio no existe, así
        // que un salvaje de hace tres días seguía ahí y el botón lo capturaba igual.
        store.expireIfStale()
        val speciesId = store.pendingSpeciesId ?: return false

        // Y sin sprite no se ha podido enseñar en la Matrix —el decisor exige `hasSpriteFor` para
        // pintarlo—, así que el usuario no sabe que hay nada esperando. Capturarlo a ciegas sería
        // un botón que hace algo invisible: mejor dejar paso a la batería, que sí se ve.
        if (SpriteLibrary(applicationContext).uriFor(speciesId) == null) return false

        store.clear()
        PokemonRepository(applicationContext)
            .addCaught(speciesId, level = WILD_LEVELS.random())

        // Cada diez capturas cae un huevo. Se apunta aquí y no al abrir la app porque este es
        // el único sitio por el que pasa una captura de verdad.
        if (EggStore(applicationContext).registerCapture()) {
            Log.i(TAG, "Diez capturas: te has ganado un huevo")
        }

        // Cada cinco, un objeto. Ver ItemDropTable para por qué el número no coincide con el
        // del huevo — son dos premios sueltos y no hace falta que compartan ritmo.
        if (ItemDropStore(applicationContext).registerCapture()) {
            val item = ItemDropTable.roll(ItemDropTable.Source.CAPTURE)
            PokemonRepository(applicationContext).addItem(item)
            Log.i(TAG, "Objeto recibido: ${item.name}")
            GlyphRotationService.itemReceivedAnimation(
                applicationContext, item, ItemDropTable.Source.CAPTURE
            )
        }

        // Los logros que dependen de cuándo capturas: no se pueden deducir mirando el equipo
        // después, porque un Pokémon no recuerda a qué hora ni con qué batería lo atrapaste.
        val achievements = AchievementStore(applicationContext)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in NIGHT_HOURS) achievements.registerNightCatch()
        if (batteryInfo().first <= LOW_BATTERY_PERCENT) achievements.registerLowBatteryCatch()

        // Antes se descartaba lo que devuelve checkNewlyUnlocked(). Como marca los logros como
        // vistos nada más leerlos, los que se cumplen justo con una captura —de golpe la mayoría
        // de los 37: todos los de captura, Pokédex e iniciales— se apuntaban en silencio y nunca
        // se anunciaban ni daban su objeto: para cuando el sondeo nocturno del otro servicio
        // volvía a mirar, ya no había nada "nuevo" que ver. Usar la lista aquí es el arreglo.
        val fresh = achievements.checkNewlyUnlocked()
        for (achievement in fresh) Log.i(TAG, "Logro conseguido: ${achievement.id}")
        AchievementRewards.grant(applicationContext, fresh)

        GlyphRotationService.catchAnimation(applicationContext, speciesId)
        return true
    }

    /**
     * Vuelve al reloj y apaga **todo** lo que pudiera estar puesto.
     *
     * Hay dos cosas que secuestran la Matrix —la batería y el compañero— y antes cada una tenía
     * su propio `Runnable` de vuelta, guardado en la misma variable. El segundo en llegar
     * borraba el del primero, y la bandera del primero se quedaba puesta: el sprite congelado en
     * la pantalla y el carrusel apagado para siempre, porque `GlyphToyPresence` seguía activo.
     *
     * Es la cuarta vez que este proyecto tropieza con lo mismo. La regla: quien deje de pintar
     * tiene que apagar todas las banderas, no solo la suya.
     */
    private fun volverAlReloj() {
        showingBattery = false
        showingPartner = false
        partnerFrames = emptyList()
        unregisterSensor()
        GlyphToyPresence.setActive(false)
    }

    /**
     * Enseña a tu compañero de entrenamiento, si lo tienes activado en Ajustes.
     *
     * Va **después** de la captura y no antes: si hay un salvaje esperando, atenderlo es lo
     * urgente —se va solo con el tiempo—, mientras que a tu compañero lo puedes ver cuando
     * quieras. Con la opción apagada, el botón hace lo de siempre: la batería con líquido.
     */
    private fun tryShowPartner(): Boolean {
        if (!AppPreferences(applicationContext).partnerOnGlyphButton) return false
        val partner = PokemonRepository(applicationContext).getTrainingPartner() ?: return false
        val uri = SpriteLibrary(applicationContext).uriFor(partner.speciesId) ?: return false
        val handler = tickHandler ?: return false

        // Se apaga lo que hubiera antes de empezar, y se reclama la Matrix: el servicio de
        // rotación se aparta mientras dura, para que no pinten los dos a la vez.
        showingBattery = false
        GlyphToyPresence.setActive(true)

        // El temporizador de vuelta se arma **ya**, antes de descodificar. Antes se armaba solo si
        // la decodificación salía bien, y esa es la puerta por la que se cuela el peor fallo de
        // este archivo: si el GIF tardaba y el servicio moría a mitad, nadie soltaba nunca
        // `GlyphToyPresence` y el carrusel se quedaba apagado **hasta reiniciar**. Armado antes, la
        // Matrix se suelta pase lo que pase.
        scheduleRevert(handler, PARTNER_VIEW_MS)

        // Descodificar un GIF tarda: se hace fuera del hilo del botón y se enseña cuando esté.
        decodeScope.launch {
            val animation = runCatching {
                dev.glyphrotator.app.glyph.MediaFrameDecoder.decode(
                    applicationContext,
                    uri,
                    matrixSize,
                    SpriteRenderModes(applicationContext).modeFor(partner.speciesId),
                )
            }.getOrNull()

            if (animation == null || animation.frames.isEmpty()) {
                // Sin sprite legible, el botón cae a la batería en vez de no hacer nada. Una
                // pulsación que no responde se lee como que el botón está roto, y el usuario no
                // tiene forma de saber que el problema era un GIF ilegible.
                handler.post { showBatteryFor(BATTERY_VIEW_MS) }
                return@launch
            }

            partnerFrames = animation.frames
            partnerFrameIndex = 0
            showingPartner = true
            handler.post { renderCurrentFrame() }

            // Se rearma para que los segundos se cuenten desde que se ve de verdad, no desde que
            // se pulsó: descodificar puede haberse llevado la mitad del tiempo.
            scheduleRevert(handler, PARTNER_VIEW_MS)
        }
        return true
    }

    /** Programa la vuelta al reloj, pisando la que hubiera pendiente. */
    private fun scheduleRevert(handler: Handler, delayMs: Long) {
        revertToClockRunnable?.let { handler.removeCallbacks(it) }
        val revert = Runnable { volverAlReloj() }
        revertToClockRunnable = revert
        handler.postDelayed(revert, delayMs)
    }

    private fun onLongPress() {
        if (tryCatchWildSpawn()) return
        if (tryShowPartner()) return

        showBatteryFor(BATTERY_VIEW_MS)
    }

    /** Enseña la batería con líquido durante [durationMs] y vuelve al reloj. */
    private fun showBatteryFor(durationMs: Long) {
        val handler = tickHandler ?: return
        showingPartner = false
        partnerFrames = emptyList()
        showingBattery = true
        batteryViewStartTime = System.currentTimeMillis()
        for (i in liquidHeights.indices) liquidHeights[i] = matrixSize.toFloat()
        registerSensor()
        // Solo aquí reclamamos la Matrix: durante estos segundos el servicio de rotación se
        // aparta para que el líquido no compita con el carrusel.
        GlyphToyPresence.setActive(true)
        scheduleRevert(handler, durationMs)
    }

    /**
     * El aviso de "pinta el ambiente", que llega una vez por minuto.
     *
     * En el Phone (3) es solo eso: repintar el reloj. En el **Phone (4a) Pro** es lo único que
     * llega — la documentación de Nothing lo dice sin rodeos: *"DEVICE_25111p does not support
     * Glyph Touch and only features AOD toys"*. Ese móvil **no tiene botón Glyph**, así que la
     * pulsación larga no existe y la batería con líquido sería inalcanzable para siempre.
     *
     * Por eso allí se turna sola: la mayor parte del tiempo el reloj y, cada cierto rato, unos
     * segundos de batería. Sin botón, la única forma de enseñar dos cosas es el tiempo.
     */
    private fun onAmbientTick() {
        if (!isTouchless()) {
            tickHandler?.post { renderCurrentFrame() }
            return
        }

        ambientTicks++
        if (!showingBattery && ambientTicks % TOUCHLESS_BATTERY_EVERY_TICKS == 0) {
            showBatteryFor(TOUCHLESS_BATTERY_MS)
        }
        tickHandler?.post { renderCurrentFrame() }
    }

    /** Si el móvil no tiene botón Glyph y solo admite toys de pantalla ambiente. */
    private fun isTouchless(): Boolean = runCatching { Common.is25111p() }.getOrDefault(false)

    private val serviceHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                val event = msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                when (event) {
                    GlyphToy.EVENT_CHANGE -> onLongPress()
                    GlyphToy.EVENT_AOD -> onAmbientTick()
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }

    private val serviceMessenger = Messenger(serviceHandler)

    private companion object {
        const val TAG = "ClockBatteryToyService"

        /**
         * Cada cuántos avisos de ambiente se enseña la batería en un móvil sin botón Glyph.
         *
         * El Phone (4a) Pro no tiene botón: la documentación de Nothing dice que solo admite
         * toys de pantalla ambiente. Sin esto, su batería con líquido sería inalcanzable.
         * Cinco minutos deja que aparezca sola de vez en cuando sin convertir el reloj en algo
         * que parpadea.
         */
        const val TOUCHLESS_BATTERY_EVERY_TICKS = 5
        const val TOUCHLESS_BATTERY_MS = 12_000L

        /** Lo que se queda el compañero en la Matrix al pedirlo con el botón. */
        const val PARTNER_VIEW_MS = 8_000L

        /** Madrugada, para el logro de capturar de noche. */
        val NIGHT_HOURS = 0..5

        /** Batería a partir de la cual cuenta como "capturado a pilas". */
        const val LOW_BATTERY_PERCENT = 15

        /** Nivel con el que llega un Pokémon salvaje capturado con el botón. */
        val WILD_LEVELS = 3..18
        const val TICK_MS = 500L
        const val BATTERY_TICK_MS = 50L
        const val BATTERY_VIEW_MS = 8_000L
        const val RISE_DURATION_MS = 900L
        const val ALPHA = 0.8f
        const val DIM_CLOCK_BRIGHTNESS = 80

        /** Alto de la fuente bitmap del SDK, en píxeles. */
        const val FONT_HEIGHT = 5

        /** Las alturas de siempre, para la matriz donde la hora cabe entera (Phone 3). */
        const val WIDE_TIME_Y = 6
        const val WIDE_AMPM_Y = 15
    }
}
