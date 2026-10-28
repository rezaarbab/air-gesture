package ir.airgesture.pointer

import ir.airgesture.decode.PointerCfg
import kotlin.math.abs
import kotlin.math.hypot

/** Screen fractions per second to add to the cursor, plus whether the head was actually seen. */
data class Move(val dx: Float, val dy: Float, val speed: Float, val valid: Boolean)

/**
 * Head offset in, cursor velocity out. Pure and JVM-tested, because "the pointer drifted away
 * after ten seconds" is a calibration bug you cannot debug on a phone.
 */
class PointerFilter(private var cfg: PointerCfg) {

    private var centerX = -1f
    private var centerY = -1f
    private var smoothX = 0f
    private var smoothY = 0f
    private var lastT = -1L

    fun setCfg(next: PointerCfg) {
        cfg = next
    }

    /** The current head position becomes neutral. Called on start and on the recenter gesture. */
    fun recenter(x: Float, y: Float) {
        centerX = x
        centerY = y
        smoothX = 0f
        smoothY = 0f
        lastT = -1L
    }

    val hasCenter: Boolean get() = centerX >= 0f

    fun feed(t: Long, x: Float, y: Float, present: Boolean): Move {
        if (!present) {
            // Losing the face must freeze the cursor, not fling it to wherever the last guess was.
            smoothX = 0f
            smoothY = 0f
            return Move(0f, 0f, 0f, false)
        }
        if (centerX < 0f) recenter(x, y)
        val dt = if (lastT < 0) 0L else t - lastT
        lastT = t
        if (dt <= 0L) return Move(0f, 0f, 0f, true)

        val c = cfg
        val ox = x - centerX
        var oy = y - centerY
        if (c.invertY) oy = -oy
        val a = c.smooth.coerceIn(0.02f, 1f)
        smoothX += (ox - smoothX) * a
        smoothY += (oy - smoothY) * a

        if (abs(smoothX) < c.deadzone) smoothX = 0f
        if (abs(smoothY) < c.deadzone) smoothY = 0f

        val vx = (smoothX * c.gainX).coerceIn(-c.clampSpeed, c.clampSpeed)
        val vy = (smoothY * c.gainY).coerceIn(-c.clampSpeed, c.clampSpeed)
        return Move(vx, vy, hypot(vx.toDouble(), vy.toDouble()).toFloat(), true)
    }

    fun reset() {
        centerX = -1f
        centerY = -1f
        smoothX = 0f
        smoothY = 0f
        lastT = -1L
    }
}
