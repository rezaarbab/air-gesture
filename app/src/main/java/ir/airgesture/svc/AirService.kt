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
import android.os.IBinder
import android.os.Looper
import ir.airgesture.Air
import ir.airgesture.Prefs
import ir.airgesture.R
import ir.airgesture.decode.HandDecoder
import ir.airgesture.decode.HandSample
import ir.airgesture.decode.ProxDecoder
import ir.airgesture.inject.AirInputService
import ir.airgesture.hud.Hud
import ir.airgesture.model.AirAction
import ir.airgesture.model.AirGesture
import ir.airgesture.model.Flavor
import ir.airgesture.model.SenseMode
import ir.airgesture.sense.CameraHandSource

/**
 * Reads the chosen sensor, decodes it into discrete gestures, and hands each one to the
 * accessibility service. Stays foreground so the camera and the sensor keep running while
 * another app is on screen.
 */
class AirService : Service(), SensorEventListener {

    private lateinit var prefs: Prefs
    private var handDecoder: HandDecoder? = null
    private var proxDecoder: ProxDecoder? = null
    private var camSource: CameraHandSource? = null
    private var sensorMgr: SensorManager? = null
    private var prox: Sensor? = null
    private val tick = Handler(Looper.getMainLooper())
    private var hud: Hud? = null

    private var lastNear = false
    private var windowStartMs = 0L
    private var windowFrames = 0

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

    private fun begin() {
        // A reload lands on a live service: without tearing the old source down the proximity
        // listener and the HUD would be registered twice.
        end()
        Air.update { it.copy(running = true) }
        val wanted = prefs.mode
        val mode = if (wanted == SenseMode.HAND && !Flavor.hasHand) {
            Air.note("mode: this build has no camera tracker, falling back to proximity")
            SenseMode.PROX
        } else {
            wanted
        }
        hud = Hud.attach(this)
        if (mode == SenseMode.HAND) startHand() else startProx()
        Air.note("svc: running, mode=$mode")
    }

    private fun end() {
        tick.removeCallbacks(runnable)
        proxDecoder?.reset()
        handDecoder?.reset()
        proxDecoder = null
        handDecoder = null
        sensorMgr?.unregisterListener(this)
        sensorMgr = null
        prox = null
        camSource?.stop()
        camSource = null
        hud?.detach()
        hud = null
        Air.update { it.copy(sourceUp = false, handVisible = false, fps = 0) }
    }

    private fun startHand() {
        handDecoder = HandDecoder(prefs.handCfg())
        val src = CameraHandSource(applicationContext) { s -> onSample(s) }
        camSource = src
        if (!src.start()) {
            Air.note("hand: camera pipeline refused to start (permission or no free camera)")
            Air.update { it.copy(sourceUp = false) }
            startProx()
        } else {
            Air.update { it.copy(sourceUp = true) }
        }
    }

    private fun startProx() {
        proxDecoder = ProxDecoder(prefs.proxCfg())
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
        mgr.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
        windowStartMs = System.currentTimeMillis()
        tick.postDelayed(runnable, TICK_MS)
        Air.update { it.copy(sourceUp = true) }
    }

    /** The sensor only speaks on change, so a tick is needed to time a cover and flush a half-wave. */
    private val runnable = object : Runnable {
        override fun run() {
            val d = proxDecoder
            if (d != null) handle(d.feed(System.currentTimeMillis(), lastNear))
            tick.postDelayed(this, TICK_MS)
        }
    }

    private fun onSample(s: HandSample) {
        windowFrames += 1
        val now = s.tMs
        if (windowStartMs == 0L) windowStartMs = now
        if (now - windowStartMs >= 1000L) {
            val fps = (windowFrames * 1000L / (now - windowStartMs)).toInt()
            val live = if (handDecoder != null) fps else 0
            Air.update { it.copy(fps = live, frames = it.frames + windowFrames, handVisible = s.present) }
            windowStartMs = now
            windowFrames = 0
        }
        val d = handDecoder ?: return
        handle(d.feed(s))
    }

    private fun handle(events: List<AirGesture>) {
        if (events.isEmpty()) return
        val p = prefs
        for (g in events) {
            val a: AirAction = p.actionFor(g)
            Air.update { it.copy(lastGesture = g.name, lastAction = a.name, gestures = it.gestures + 1) }
            Air.note("gesture ${g.name} -> ${a.name}")
            if (a != AirAction.NONE) AirInputService.fire(a)
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val s = prox ?: return
        if (e.sensor != s) return
        lastNear = e.values[0] < s.maximumRange
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        end()
        prefs.enabled = false
        Air.update { it.copy(running = false) }
        Air.note("svc: stopped")
        super.onDestroy()
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
        val mode = if (prefs.mode == SenseMode.HAND && Flavor.hasHand) "دوربین" else "سنسور مجاورت"
        return Notification.Builder(this, CH)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("کنترل با اشاره فعال است - $mode")
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
            val c = NotificationChannel(CH, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(c)
        }
    }

    companion object {
        private const val CH = "air-run"
        private const val NID = 41001
        private const val TICK_MS = 90L
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
