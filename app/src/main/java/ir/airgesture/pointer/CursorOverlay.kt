package ir.airgesture.pointer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import ir.airgesture.Air
import ir.airgesture.inject.AirInputService
import kotlin.math.min

/**
 * The cursor and its dwell ring, drawn over whatever app is open. Full-screen and NOT_TOUCHABLE:
 * it must be able to cover another app's button without ever eating the touch that button needs.
 *
 * "Water" is literal: when the pointer sits on a control, the control's own rectangle fills from
 * its bottom edge upward, and a ring around the cursor sweeps the same fraction.
 */
class CursorOverlay private constructor(
    private val wm: WindowManager,
    private val view: RingView,
) {

    fun update(x: Float, y: Float, progress: Float, target: AirInputService.Target?, on: Boolean) {
        view.set(x, y, progress, target, on)
    }

    fun detach() {
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            Air.note("cursor: removeView failed (${e.javaClass.simpleName})")
        }
    }

    private class RingView(ctx: Context) : View(ctx) {
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3DDC97")
            style = Paint.Style.FILL
        }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 61, 220, 151)
            style = Paint.Style.FILL
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3DDC97")
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 61, 220, 151)
            style = Paint.Style.FILL
        }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 61, 220, 151)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val d = resources.displayMetrics.density
        private var x = 0f
        private var y = 0f
        private var progress = 0f
        private var target: AirInputService.Target? = null
        private var on = false

        fun set(x: Float, y: Float, progress: Float, target: AirInputService.Target?, on: Boolean) {
            this.x = x
            this.y = y
            this.progress = progress
            this.target = target
            this.on = on
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            if (!on) return
            val t = target
            if (t != null && progress > 0f) {
                val box = RectF(t.left.toFloat(), t.top.toFloat(), t.right.toFloat(), t.bottom.toFloat())
                c.drawRoundRect(box, 14f, 14f, edge)
                val rise = box.height() * min(1f, progress)
                c.save()
                c.clipRect(box.left, box.bottom - rise, box.right, box.bottom)
                c.drawRoundRect(box, 14f, 14f, fill)
                c.restore()
            }
            val r = 13f * d
            c.drawCircle(x, y, r * 2.1f, halo)
            c.drawCircle(x, y, r, dot)
            if (progress > 0f) {
                val rr = r * 2.6f
                val box = RectF(x - rr, y - rr, x + rr, y + rr)
                c.drawArc(box, -90f, 360f * min(1f, progress), false, ring)
            }
        }
    }

    companion object {
        fun attach(ctx: Context): CursorOverlay? {
            if (!Settings.canDrawOverlays(ctx)) {
                Air.note("cursor: overlay not allowed, the pointer is invisible (dwell still works)")
                return null
            }
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
            val view = RingView(ctx)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            return try {
                wm.addView(view, params)
                CursorOverlay(wm, view)
            } catch (e: Exception) {
                Air.note("cursor: addView refused (${e.javaClass.simpleName}: ${e.message})")
                null
            }
        }
    }
}
