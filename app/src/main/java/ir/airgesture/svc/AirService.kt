package ir.airgesture.svc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import ir.airgesture.Air
import ir.airgesture.Prefs
import ir.airgesture.R
import ir.airgesture.decode.BlinkDecoder
import ir.airgesture.decode.FaceSample
import ir.airgesture.decode.HandDecoder
import ir.airgesture.decode.HandSample
import ir.airgesture.decode.ProxDecoder
import ir.airgesture.hud.Hud
import ir.airgesture.inject.AirInputService
import ir.airgesture.inject.Drag
import ir.airgesture.model.AirAction
import ir.airgesture.model.AirGesture
import ir.airgesture.model.SenseMode
import ir.airgesture.pointer.CursorOverlay
import ir.airgesture.pointer.DwellTracker
import ir.airgesture.pointer.Move
import ir.airgesture.pointer.PointerFilter
import ir.airgesture.sense.CameraSource
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Reads the camera or the sensor, decides what the movement meant, and hands it to the
 * accessibility service.
 *
 * Pointer mode runs two clocks: the camera thread produces samples as fast as the sensor allows,
 * and a 30 Hz loop on the main thread integrates head velocity into a cursor. That split is what
 * makes the cursor glide at 30 fps even when the model only manages 18.
 */
class AirService : Service(), SensorEventListener {

    private lateinit var prefs: Prefs
    private var mode = SenseMode.POINTER

    private var handDecoder: HandDecoder? = null
    private var proxDecoder: ProxDecoder? = null
    private var blinkDecoder: BlinkDecoder? = null
    private var filter: PointerFilter? = null
    private var dwell: DwellTracker? = null

    private var camSource: CameraSource? = null
    private var sensorMgr: SensorManager? = null
    private var prox: Sensor? = null

    private val main = Handler(Looper.getMainLooper())
    private var treeThread: HandlerThread? = null
    private var treeHandler: Handler? = null

    private var overlay: CursorOverlay? = null
    private var hud: Hud? = null

    private var curX = 0.5f
    private var curY = 0.5f
    @Volatile
    private var lastMove = Move(0f, 0f, 0f, false)
    private var lastIntegrateT = 0L
    private var lastNoseX = -1f
    private var lastNoseY = -1f
    private var cachedTarget: AirInputService.Target? = null
    private var lastTreeAsk = 0L
    private var movedOnce = false

    private var pinchHeld = false
    private var pinchSince = -1L
    private var pinchClose = 0.38f
    private val dragPath = ArrayList<FloatArray>(80)
    private var suppressPinchBefore = 0L

    private var lastNear = false
    private var statsStartT = 0L
    private var statsFrames = 0
    private var lastNearT = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        channel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Air.note("svc: stop requested from the notification")
            prefs.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started()) {
            Air.note("svc: not started, check the error above")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RELOAD) Air.note("svc: reload requested, applying new settings")
        begin()
        return START_STICKY
    }

    private fun started(): Boolean = try {
        startForeground(NID, buildNotification())
        true
    } catch (e: Exception) {
        // Android 14 refuses a camera-typed service that was not started from the foreground.
        Air.note("svc: startForeground refused (${e.javaClass.simpleName}: ${e.message})")
        false
    }

    /** JPEG is the path proven to deliver frames on the test phone; YUV is selectable to A/B it. */
    private fun jpeg(): Boolean = prefs.frameFormat != Prefs.FMT_YUV

    private fun begin() {
        // A reload lands on a live service: without tearing the old source down the cursor and
        // the proximity listener would be registered twice.
        end()
        Air.update { it.copy(running = true) }
        mode = prefs.mode
        pinchClose = prefs.handCfg().pinchClose
        when (mode) {
            SenseMode.POINTER -> startPointer()
            SenseMode.GESTURE -> startGesture()
            SenseMode.PROX -> startProx()
        }
        Air.note("svc: running, mode=${mode.name}")
    }

    private fun end() {
        tick.removeCallbacks(runnable)
        main.removeCallbacks(loop)
        proxDecoder?.reset()
        handDecoder?.reset()
        blinkDecoder?.reset()
        proxDecoder = null
        handDecoder = null
        blinkDecoder = null
        filter = null
        dwell = null
        sensorMgr?.unregisterListener(this)
        sensorMgr = null
        prox = null
        camSource?.stop()
        camSource = null
        treeThread?.quitSafely()
        treeThread = null
        treeHandler = null
        overlay?.detach()
        overlay = null
        hud?.detach()
        hud = null
        cachedTarget = null
        lastIntegrateT = 0L
        statsStartT = 0L
        statsFrames = 0
        movedOnce = false
        dragPath.clear()
        pinchHeld = false
        suppressPinchBefore = 0L
        Air.update {
            it.copy(
                sourceUp = false,
                handVisible = false,
                faceVisible = false,
                fps = 0,
                dwell = 0f,
                tracking = false,
                moved = false,
                probe = "",
            )
        }
    }

    // ---- pointer mode ----

    private fun startPointer() {
        filter = PointerFilter(prefs.pointerCfg())
        dwell = DwellTracker(prefs.dwellCfg())
        blinkDecoder = BlinkDecoder(prefs.blinkCfg())
        handDecoder = HandDecoder(prefs.handCfg())
        attachDrawers()
        startTreeThread()
        val src = CameraSource(applicationContext, ::onFace, ::onHand)
        camSource = src
        if (!src.start(true, true, prefs.handStride, prefs.gpuDelegate, prefs.rotation, jpeg())) {
            Air.note("pointer: camera unavailable, falling back to proximity")
            // The cursor must not stay armed on a frozen position and click by itself.
            main.removeCallbacks(loop)
            detachDrawers()
            dwell = null
            filter = null
            blinkDecoder = null
            handDecoder = null
            treeThread?.quitSafely()
            treeThread = null
            treeHandler = null
            camSource = null
            startProx()
            return
        }
        main.post(loop)
        Air.update { it.copy(sourceUp = true, frameInfo = src.lastProbe) }
    }

    private fun startGesture() {
        handDecoder = HandDecoder(prefs.handCfg())
        attachDrawers()
        val src = CameraSource(applicationContext, ::onFace, ::onHand)
        camSource = src
        if (!src.start(false, true, 1, prefs.gpuDelegate, prefs.rotation, jpeg())) {
            Air.note("gesture: camera unavailable, falling back to proximity")
            detachDrawers()
            handDecoder = null
            camSource = null
            startProx()
            return
        }
        Air.update { it.copy(sourceUp = true, frameInfo = src.lastProbe) }
    }

    /** The overlay draws the skeleton and the cursor; the pill is only the fallback when it cannot. */
    private fun attachDrawers() {
        overlay = CursorOverlay.attach(this)
        if (overlay == null) hud = Hud.attach(this)
    }

    private fun detachDrawers() {
        overlay?.detach()
        overlay = null
        hud?.detach()
        hud = null
    }

    private fun startTreeThread() {
        val t = HandlerThread("air-tree")
        t.start()
        treeThread = t
        treeHandler = Handler(t.looper)
    }

    private val loop = object : Runnable {
        override fun run() {
            integrate()
            main.postDelayed(this, LOOP_MS)
        }
    }

    private fun integrate() {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastIntegrateT == 0L) LOOP_MS else (now - lastIntegrateT)
        lastIntegrateT = now
        val mv = lastMove
        if (mv.valid && dt > 0) {
            val k = dt.toFloat() / 1000f
            val nx = (curX - mv.dx * k).coerceIn(0f, 1f)
            val ny = (curY + mv.dy * k).coerceIn(0f, 1f)
            if (!movedOnce && hypot((nx - curX).toDouble(), (ny - curY).toDouble()) > 0.0006) {
                movedOnce = true
            }
            curX = nx
            curY = ny
        }
        val d = dwell ?: return
        // The cursor always follows the head; only the dwell waits for a first real movement,
        // otherwise the app would click the screen center one second after start with no input.
        if (!movedOnce) {
            overlay?.setPointer(curX * screenW(), curY * screenH(), 0f, cachedTarget, true)
            Air.update { it.copy(cursorX = curX, cursorY = curY, dwell = 0f) }
            return
        }
        // The tree is only worth reading once a fill is visibly under way (or when the user
        // insists the pointer must sit on a real control).
        val previous = Air.state.value.dwell
        if ((previous > 0.02f || prefs.dwellNeedsTarget) && now - lastTreeAsk > TREE_MS) {
            lastTreeAsk = now
            askTree()
        }
        val tick = d.tick(now, curX, curY, cachedTarget?.key)
        val t = cachedTarget
        overlay?.setPointer(curX * screenW(), curY * screenH(), tick.progress, t, true)
        Air.update { it.copy(cursorX = curX, cursorY = curY, dwell = tick.progress, moved = true, target = t?.label.orEmpty()) }
        if (tick.fired) dwellClick(t)
    }

    private fun askTree() {
        val h = treeHandler ?: return
        val fx = curX
        val fy = curY
        h.post {
            val found = AirInputService.targetAt(fx, fy)
            main.post { cachedTarget = found }
        }
    }

    /**
     * Snap to the control's own center only when that node is actually actionable. A plain
     * container can be the whole screen, and clicking its middle is the "it clicked something I
     * never aimed at" bug.
     */
    private fun dwellClick(t: AirInputService.Target?) {
        val w = screenW()
        val h = screenH()
        val snap = t != null && t.actionable
        val px = if (snap) t!!.cx else curX * w
        val py = if (snap) t!!.cy else curY * h
        val name = when {
            t == null -> "nothing"
            snap -> "clickable ${t.label}"
            else -> "container (tapped at the pointer, not at the container center)"
        }
        Air.note("dwell: fired on $name")
        AirInputService.fire(AirAction.TAP_POINTER, px, py)
    }

    private fun onFace(s: FaceSample) {
        countFrame(s.tMs)
        val tracking = s.present
        Air.update { it.copy(faceVisible = tracking, tracking = tracking) }
        if (!tracking) {
            // Losing the face freezes the cursor and cancels any fill in progress: a stale
            // position must not turn into a click.
            lastMove = Move(0f, 0f, 0f, false)
            dwell?.reset()
            overlay?.setStatus("چهره پیدا نیست")
            return
        }
        lastNoseX = s.noseX
        lastNoseY = s.noseY
        lastMove = filter?.feed(s.tMs, s.noseX, s.noseY, true) ?: lastMove
        overlay?.setStatus(if (movedOnce) "نشانگر آماده" else "سر را تکان بده تا نشانگر راه بیفتد")
        val b = blinkDecoder ?: return
        if (prefs.blinkEnabled) handle(b.feed(s.tMs, s.blink, true), s.tMs)
    }

    private fun onHand(s: HandSample) {
        if (mode == SenseMode.GESTURE) countFrame(s.tMs)
        if (prefs.showSkeleton) overlay?.setHand(if (s.present) s.pts else null, s.pinchRatio, pinchClose)
        val d = handDecoder ?: return
        if (mode == SenseMode.POINTER) trackPinchDrag(s)
        handle(d.feed(s), s.tMs)
        Air.update { it.copy(handVisible = s.present) }
    }

    /**
     * Holding a pinch and moving the head is a drag: the pointer path is recorded while the
     * fingers stay closed and replayed as one stroke on release. The decoder still reports the
     * release as a pinch, so that event is swallowed when it became a drag instead.
     */
    private fun trackPinchDrag(s: HandSample) {
        if (s.present && s.pinchRatio < pinchClose) {
            if (!pinchHeld) {
                pinchHeld = true
                pinchSince = s.tMs
                dragPath.clear()
            }
            if (dragPath.size < 80) {
                dragPath.add(floatArrayOf(curX * screenW(), curY * screenH()))
            }
        } else if (pinchHeld) {
            pinchHeld = false
            val held = s.tMs - pinchSince
            if (held > DRAG_MIN_MS && dragPath.size > 3 && pathSpread() > DRAG_MIN_SPREAD) {
                val drag = Drag(dragPath.toList(), held.coerceAtMost(3000L))
                Air.note("drag: ${drag.points.size} points over ${held}ms")
                AirInputService.drag(drag)
                suppressPinchBefore = s.tMs + DRAG_SUPPRESS_MS
            }
            dragPath.clear()
        }
    }

    private fun pathSpread(): Float {
        if (dragPath.size < 2) return 0f
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in dragPath) {
            if (p[0] < minX) minX = p[0]
            if (p[0] > maxX) maxX = p[0]
            if (p[1] < minY) minY = p[1]
            if (p[1] > maxY) maxY = p[1]
        }
        val w = screenW().coerceAtLeast(1).toFloat()
        val h = screenH().coerceAtLeast(1).toFloat()
        val fx = (maxX - minX) / w
        val fy = (maxY - minY) / h
        return sqrt((fx * fx + fy * fy).toDouble()).toFloat()
    }

    /** One second window: fps, and how many of those frames the model actually saw something in. */
    private fun countFrame(t: Long) {
        statsFrames += 1
        if (statsStartT == 0L) {
            statsStartT = t
            val src = camSource
            if (src != null) Air.update { it.copy(frameInfo = src.lastProbe) }
            return
        }
        if (t - statsStartT < 1000L) return
        val fps = (statsFrames * 1000L / (t - statsStartT)).toInt()
        val src = camSource
        val probeText = if (src == null) "" else {
            if (mode == SenseMode.GESTURE) "hand ${src.handSeen}/${src.handCount}"
            else "face ${src.faceSeen}/${src.faceCount} · hand ${src.handSeen}/${src.handCount}"
        }
        Air.update {
            it.copy(
                fps = fps,
                frames = it.frames + statsFrames,
                faceMs = src?.faceAvgMs ?: 0f,
                handMs = src?.handAvgMs ?: 0f,
                probe = probeText,
            )
        }
        statsStartT = t
        statsFrames = 0
    }

    // ---- proximity mode ----

    private fun startProx() {
        proxDecoder = ProxDecoder(prefs.proxCfg())
        hud = Hud.attach(this)
        val mgr = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (mgr == null) {
            Air.note("prox: SensorManager unavailable")
            return
        }
        sensorMgr = mgr
        val s = mgr.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (s == null) {
            Air.note("prox: this phone reports no proximity sensor")
            Air.update { it.copy(sourceUp = false) }
            return
        }
        prox = s
        lastNear = false
        lastNearT = 0L
        mgr.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
        tick.postDelayed(runnable, TICK_MS)
        Air.update { it.copy(sourceUp = true) }
    }

    /** The proximity sensor only speaks on change, so a tick times a cover and flushes a half-wave. */
    private val runnable = object : Runnable {
        override fun run() {
            val d = proxDecoder
            val t = System.currentTimeMillis()
            if (d != null) handle(d.feed(t, lastNear), t)
            tick.postDelayed(this, TICK_MS)
        }
    }

    private val tick = Handler(Looper.getMainLooper())

    private fun handle(events: List<AirGesture>, now: Long) {
        if (events.isEmpty()) return
        for (g in events) {
            if (g == AirGesture.PINCH && now < suppressPinchBefore) {
                suppressPinchBefore = 0L
                Air.note("gesture PINCH swallowed, that release became a drag")
                continue
            }
            val a = prefs.actionFor(g, mode)
            Air.update { it.copy(lastGesture = g.name, lastAction = a.name, gestures = it.gestures + 1) }
            Air.note("gesture ${g.name} -> ${a.name}")
            when (a) {
                AirAction.RECENTER -> {
                    if (lastNoseX >= 0f) {
                        filter?.recenter(lastNoseX, lastNoseY)
                        movedOnce = false
                        curX = 0.5f
                        curY = 0.5f
                        dwell?.reset()
                        Air.note("pointer: neutral set, cursor back to center until the head moves")
                    } else {
                        Air.note("pointer: no face in view, cannot set neutral")
                    }
                }
                else -> AirInputService.fire(a, curX * screenW(), curY * screenH())
            }
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val s = prox ?: return
        if (e.sensor != s) return
        lastNear = e.values[0] < s.maximumRange
        lastNearT = System.currentTimeMillis()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        end()
        prefs.enabled = false
        Air.update { it.copy(running = false) }
        Air.note("svc: stopped")
        super.onDestroy()
    }

    private var metricsW = 0
    private var metricsH = 0

    private fun screenW(): Int {
        if (metricsW > 0) return metricsW
        val dm = resources.displayMetrics
        metricsW = dm.widthPixels
        metricsH = dm.heightPixels
        return metricsW
    }

    private fun screenH(): Int {
        if (metricsH > 0) return metricsH
        val dm = resources.displayMetrics
        metricsW = dm.widthPixels
        metricsH = dm.heightPixels
        return metricsH
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, ir.airgesture.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AirService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CH)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("اشاره فعال - ${prefs.mode.title}")
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
            .build()
    }

    private fun channel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CH = "air-run"
        private const val NID = 41001
        private const val TICK_MS = 90L
        private const val LOOP_MS = 33L
        private const val TREE_MS = 140L
        private const val DRAG_MIN_MS = 350L
        private const val DRAG_MIN_SPREAD = 0.05f
        private const val DRAG_SUPPRESS_MS = 800L
        const val ACTION_STOP = "ir.airgesture.STOP"
        private const val ACTION_RELOAD = "ir.airgesture.RELOAD"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, AirService::class.java))
        }

        /** Same service, new settings: used after a mapping or sensitivity change. */
        fun reload(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, AirService::class.java).setAction(ACTION_RELOAD))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AirService::class.java).setAction(ACTION_STOP))
        }
    }
}
