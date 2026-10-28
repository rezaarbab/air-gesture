package ir.airgesture.pointer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import ir.airgesture.Air
import ir.airgesture.inject.AirInputService
import kotlin.math.min

/**
 * Everything drawn over the other app: the cursor, the dwell "water", and the live skeleton of
 * whatever the model just recognized. It is one full-screen NOT_TOUCHABLE overlay so it can cover
 * a button without ever eating the touch that button needs.
 *
 * The skeleton is the answer to "the app says nothing": seeing your hand drawn means detection is
 * alive and only the threshold is wrong, seeing the dashed placeholder means the model never found
 * a hand at all. Those two need different fixes and the screen now says which one it is.
 */
class CursorOverlay private constructor(
    private val wm: WindowManager,
    private val view: OverlayView,
) {

    private val main = Handler(Looper.getMainLooper())

    fun setPointer(x: Float, y: Float, progress: Float, target: AirInputService.Target?, on: Boolean) {
        main.post { view.setPointer(x, y, progress, target, on) }
    }

    fun setHand(pts: FloatArray?, pinchRatio: Float, closeAt: Float) {
        main.post { view.setHand(pts, pinchRatio, closeAt) }
    }

    fun setStatus(text: String) {
        main.post { view.setStatus(text) }
    }

    fun detach() {
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            Air.note("cursor: removeView failed (${e.javaClass.simpleName})")
        }
    }

    private class OverlayView(ctx: Context) : View(ctx) {
        private val d = resources.displayMetrics.density
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12A56C")
            style = Paint.Style.FILL
        }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 18, 165, 108)
            style = Paint.Style.FILL
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12A56C")
            style = Paint.Style.STROKE
            strokeWidth = 8f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 18, 165, 108)
            style = Paint.Style.FILL
        }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 18, 165, 108)
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
        }
        private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F7A52")
            style = Paint.Style.STROKE
            strokeWidth = 5f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12A56C")
            style = Paint.Style.FILL
        }
        private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFB300")
            style = Paint.Style.FILL
        }
        private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 120, 130, 140)
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(9f * d, 9f * d), 0f)
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A2A33")
            textSize = 12f * d
            textAlign = Paint.Align.CENTER
        }

        private var x = 0f
        private var y = 0f
        private var progress = 0f
        private var target: AirInputService.Target? = null
        private var pointerOn = false
        private var hand: FloatArray? = null
        private var pinch = 1f
        private var closeAt = 0.38f
        private var status = ""

        fun setPointer(px: Float, py: Float, p: Float, t: AirInputService.Target?, on: Boolean) {
            x = px; y = py; progress = p; target = t; pointerOn = on
            invalidate()
        }

        fun setHand(pts: FloatArray?, ratio: Float, close: Float) {
            hand = pts; pinch = ratio; closeAt = close
            invalidate()
        }

        fun setStatus(text: String) {
            if (text != status) {
                status = text
                invalidate()
            }
        }

        override fun onDraw(c: Canvas) {
            drawSkeleton(c)
            if (!pointerOn) return
            val t = target
            if (t != null && progress > 0f) {
                val box = RectF(t.left.toFloat(), t.top.toFloat(), t.right.toFloat(), t.bottom.toFloat())
                c.drawRoundRect(box, 12f * d, 12f * d, edge)
                val rise = box.height() * min(1f, progress)
                c.save()
                c.clipRect(box.left, box.bottom - rise, box.right, box.bottom)
                c.drawRoundRect(box, 12f * d, 12f * d, fill)
                c.restore()
            }
            val r = 11f * d
            c.drawCircle(x, y, r * 2.1f, halo)
            c.drawCircle(x, y, r, dot)
            if (progress > 0f) {
                val rr = r * 2.7f
                c.drawArc(RectF(x - rr, y - rr, x + rr, y + rr), -90f, 360f * min(1f, progress), false, ring)
            }
        }

        /** A framed box in the top corner with the 21 points mapped into it, mirrored like a selfie. */
        private fun drawSkeleton(c: Canvas) {
            val boxW = 108f * d
            val boxH = 132f * d
            val left = width - boxW - 10f * d
            val top = 12f * d
            val box = RectF(left, top, left + boxW, top + boxH)
            c.drawRoundRect(box, 18f * d, 18f * d, card)
            val pts = hand
            if (pts == null || pts.size < 24) {
                c.drawRoundRect(
                    RectF(box.left + 14f * d, box.top + 20f * d, box.right - 14f * d, box.bottom - 26f * d),
                    14f * d, 14f * d, ghost,
                )
                c.drawText("دست پیدا نیست", box.centerX(), box.centerY(), label)
            } else {
                val pad = 12f * d
                val inner = RectF(box.left + pad, box.top + pad, box.right - pad, box.bottom - 24f * d)
                val sx = inner.width()
                val sy = inner.height()
                var bi = 0
                while (bi + 1 < HAND_BONES.size) {
                    val a = HAND_BONES[bi] * 2
                    val b = HAND_BONES[bi + 1] * 2
                    if (a + 1 < pts.size && b + 1 < pts.size) {
                        c.drawLine(
                            inner.left + (1f - pts[a]) * sx, inner.top + pts[a + 1] * sy,
                            inner.left + (1f - pts[b]) * sx, inner.top + pts[b + 1] * sy, bone,
                        )
                    }
                    bi += 2
                }
                val pinching = pinch < closeAt
                var i = 0
                while (i + 1 < pts.size) {
                    val p = i / 2
                    val paint = when {
                        p in TIPS && pinching && p in PINCH_TIPS -> tip
                        p in TIPS -> tip
                        else -> joint
                    }
                    val radius = if (p in TIPS) 5.2f * d else 3.4f * d
                    c.drawCircle(inner.left + (1f - pts[i]) * sx, inner.top + pts[i + 1] * sy, radius, paint)
                    i += 2
                }
            }
            if (status.isNotEmpty()) {
                c.drawText(status, box.centerX(), box.bottom + 16f * d, label)
            }
        }
    }

    companion object {
        /** MediaPipe hand connections, flattened as pairs of landmark indices. */
        private val HAND_BONES = intArrayOf(
            0, 1, 1, 2, 2, 3, 3, 4,
            0, 5, 5, 6, 6, 7, 7, 8,
            5, 9, 9, 10, 10, 11, 11, 12,
            9, 13, 13, 14, 14, 15, 15, 16,
            13, 17, 17, 18, 18, 19, 19, 20, 0, 17,
        )

        private val TIPS = setOf(4, 8, 12, 16, 20)
        private val PINCH_TIPS = setOf(4, 8)

        fun attach(ctx: Context): CursorOverlay? {
            if (!Settings.canDrawOverlays(ctx)) {
                Air.note("cursor: overlay not allowed, nothing is drawn (dwell and clicks still work)")
                return null
            }
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
            val view = OverlayView(ctx)
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
