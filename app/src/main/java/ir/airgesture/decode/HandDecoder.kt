package ir.airgesture.decode

import ir.airgesture.model.AirGesture
import kotlin.math.abs

/**
 * Landmark trajectory in, discrete gestures out. No Android in here on purpose: the whole class
 * runs on a JVM harness, so a threshold change can be proved before it reaches a phone.
 */
class HandDecoder(private var cfg: HandCfg) {

    private data class Pt(val t: Long, val y: Float)

    private val win = ArrayDeque<Pt>()
    private var lastFire = -1L
    private var closed = false
    private var lastRelease = -1L
    private var pendingSingle = -1L
    private var stillSince = -1L
    private var palmFired = false

    fun setCfg(next: HandCfg) {
        cfg = next
    }

    fun feed(s: HandSample): List<AirGesture> {
        val out = ArrayList<AirGesture>(2)
        if (!s.present) {
            reset()
            return out
        }
        val c = cfg
        win.addLast(Pt(s.tMs, s.wristY))
        while (win.isNotEmpty() && s.tMs - win.first().t > c.windowMs) win.removeFirst()

        if (pendingSingle >= 0 && s.tMs >= pendingSingle) {
            out.add(AirGesture.PINCH)
            pendingSingle = -1
            lastFire = s.tMs
        }

        // Pinch: press on the way in, and a release only becomes a gesture once the fingers open.
        val isClosed = s.pinchRatio < c.pinchClose
        if (isClosed) {
            closed = true
        } else if (closed && s.pinchRatio > c.pinchOpen) {
            closed = false
            if (lastRelease >= 0 && s.tMs - lastRelease <= c.doubleWindowMs) {
                out.add(AirGesture.DOUBLE_PINCH)
                lastRelease = -1
                pendingSingle = -1
                lastFire = s.tMs
            } else {
                lastRelease = s.tMs
                pendingSingle = s.tMs + c.doubleWindowMs
            }
        }

        var speed = 0f
        if (win.size >= 3) {
            val a = win.first()
            val b = win.last()
            val dt = b.t - a.t
            val dy = b.y - a.y
            if (dt > 0) {
                speed = abs(dy) * 1000f / dt
                if (abs(dy) >= c.minTravel && speed >= c.minSpeed &&
                    (lastFire < 0 || s.tMs - lastFire >= c.cooldownMs)
                ) {
                    // A front camera is mirrored, but the y axis of the frame is not: hand up is y down.
                    val up = dy < 0
                    out.add(if (up != c.invert) AirGesture.SWIPE_UP else AirGesture.SWIPE_DOWN)
                    lastFire = s.tMs
                    win.clear()
                }
            }
        }

        val still = speed < c.stillSpeed && s.pinchRatio > c.pinchOpen
        if (still) {
            if (stillSince < 0) stillSince = s.tMs
            else if (!palmFired && s.tMs - stillSince >= c.palmHoldMs) {
                out.add(AirGesture.PALM_HOLD)
                palmFired = true
                lastFire = s.tMs
                stillSince = s.tMs
            }
        } else {
            stillSince = -1
            palmFired = false
        }
        return out
    }

    /** Losing the hand must not leave a half-pressed pinch or a stale window behind. */
    fun reset() {
        win.clear()
        closed = false
        lastRelease = -1
        pendingSingle = -1
        stillSince = -1
        palmFired = false
    }
}
