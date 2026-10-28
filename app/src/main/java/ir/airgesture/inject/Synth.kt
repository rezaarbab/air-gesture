package ir.airgesture.inject

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import ir.airgesture.model.AirAction

/** A stroke to dispatch, and how long to wait before it so a sequence still reads as one gesture. */
data class Step(val desc: GestureDescription, val delayMs: Long)

/** A recorded pointer path in screen pixels, oldest first - what a pinch-drag leaves behind. */
data class Drag(val points: List<FloatArray>, val durationMs: Long)

/**
 * Turns an action into the touch events a finger would have produced. Durations are the whole
 * trick: RecyclerView and ViewPager decide to fling from the velocity of the synthesized stream,
 * so a slow stroke scrolls a little and a fast one pages a whole screen.
 */
object Synth {

    private const val TAP_MS = 55L
    private const val HOLD_MS = 650L
    private const val DRAG_MS = 320L
    private const val FLICK_MS = 95L
    private const val DOUBLE_GAP_MS = 110L
    private const val MIN_PATH_POINTS = 2

    /** px/py are the cursor position in pixels; pointer-anchored actions land there. */
    fun steps(a: AirAction, w: Int, h: Int, px: Float, py: Float): List<Step> {
        val cx = w / 2f
        val top = h * 0.26f
        val bottom = h * 0.76f
        val mx = px.coerceIn(1f, w - 2f)
        val my = py.coerceIn(1f, h - 2f)
        return when (a) {
            AirAction.DRAG_UP -> listOf(Step(drag(cx, bottom, top, DRAG_MS), 0))
            AirAction.DRAG_DOWN -> listOf(Step(drag(cx, top, bottom, DRAG_MS), 0))
            AirAction.FLICK_UP -> listOf(Step(drag(cx, bottom, top, FLICK_MS), 0))
            AirAction.FLICK_DOWN -> listOf(Step(drag(cx, top, bottom, FLICK_MS), 0))
            AirAction.TAP_CENTER -> listOf(Step(tap(cx, h * 0.45f, TAP_MS), 0))
            AirAction.DOUBLE_TAP_CENTER -> listOf(
                Step(tap(cx, h * 0.45f, TAP_MS), 0),
                Step(tap(cx, h * 0.45f, TAP_MS), DOUBLE_GAP_MS),
            )
            AirAction.LONG_PRESS_CENTER -> listOf(Step(tap(cx, h * 0.45f, HOLD_MS), 0))
            AirAction.TAP_POINTER -> listOf(Step(tap(mx, my, TAP_MS), 0))
            AirAction.DOUBLE_TAP_POINTER -> listOf(
                Step(tap(mx, my, TAP_MS), 0),
                Step(tap(mx, my, TAP_MS), DOUBLE_GAP_MS),
            )
            AirAction.LONG_PRESS_POINTER -> listOf(Step(tap(mx, my, HOLD_MS), 0))
            else -> emptyList()
        }
    }

    /**
     * A real drag: the finger goes down at the first recorded point, follows the path the head
     * traced, and lifts at the end. One stroke, because two strokes would run at the same time.
     */
    fun dragStroke(d: Drag): Step? {
        if (d.points.size < MIN_PATH_POINTS) return null
        val p = Path()
        p.moveTo(d.points[0][0], d.points[0][1])
        for (i in 1 until d.points.size) p.lineTo(d.points[i][0], d.points[i][1])
        val dur = d.durationMs.coerceIn(120L, 3000L)
        return Step(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, dur))
            .build(), 0L)
    }

    private fun tap(x: Float, y: Float, dur: Long): GestureDescription {
        val p = Path()
        p.moveTo(x, y)
        // A zero-length stroke is rejected; a sub-pixel move keeps it a tap, not a drag.
        p.lineTo(x + 0.4f, y + 0.4f)
        return stroke(p, dur)
    }

    private fun drag(x: Float, from: Float, to: Float, dur: Long): GestureDescription {
        val p = Path()
        p.moveTo(x, from)
        p.lineTo(x, to)
        return stroke(p, dur)
    }

    private fun stroke(p: Path, dur: Long): GestureDescription =
        GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, dur))
            .build()
}
