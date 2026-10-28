package ir.airgesture.pointer

import ir.airgesture.decode.DwellCfg
import kotlin.math.abs

/**
 * The "fill with water" timer: stand still on something for holdMs and it becomes a click.
 * Progress is reported every tick so the overlay can draw it, and a fired click disarms the
 * fill until the pointer has been still again - otherwise one dwell would chain-fire.
 */
class DwellTracker(private var cfg: DwellCfg) {

    data class Tick(
        val progress: Float,
        val fired: Boolean,
        val target: String?,
        val running: Boolean,
    )

    private var startT = -1L
    private var anchorX = 0f
    private var anchorY = 0f
    private var target: String? = null
    private var armedAt = -1L
    private var fired = false

    fun setCfg(next: DwellCfg) {
        cfg = next
        if (!next.enabled) reset()
    }

    fun reset() {
        startT = -1L
        target = null
        fired = false
    }

    /** id is whatever the pointer is standing on (a node key), or null when nothing was found. */
    fun tick(t: Long, x: Float, y: Float, id: String?): Tick {
        val c = cfg
        if (!c.enabled || (c.needsTarget && id == null)) {
            reset()
            return Tick(0f, false, id, false)
        }
        val drifted = startT >= 0 &&
            (abs(x - anchorX) > c.moveTolerance || abs(y - anchorY) > c.moveTolerance)
        if (startT < 0 || drifted || id != target) {
            startT = t
            anchorX = x
            anchorY = y
            target = id
            fired = false
        }
        if (t < armedAt) return Tick(0f, false, target, false)

        val p = if (c.holdMs <= 0L) 1f else ((t - startT).toFloat() / c.holdMs.toFloat()).coerceIn(0f, 1f)
        if (p >= 1f && !fired) {
            fired = true
            armedAt = t + c.rearmMs
            return Tick(1f, true, target, true)
        }
        return Tick(p, false, target, true)
    }
}
