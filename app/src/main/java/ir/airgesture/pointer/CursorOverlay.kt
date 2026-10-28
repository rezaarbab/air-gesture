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
import kotlin.math.max
import kotlin.math.min

/**
 * Everything drawn over the other app: the cursor, the dwell "water", and the live skeleton of
 * whatever the model just recognized. One full-screen NOT_TOUCHABLE overlay, so it can sit on top
 * of a button without ever eating the touch that button needs.
 *
 * The skeleton panel is sized to what it contains (up to two hands plus at most two wrapped lines
 * of status), because a fixed box with a long Persian sentence drawn under it read as a banner
 * across the whole top of the screen.
 */
class CursorOverlay private constructor(
    private val wm: WindowManager,
    private val view: OverlayView,
) {

    private val main = Handler(Looper.getMainLooper())

    fun setPointer(x: Float, y: Float, progress: Float, target: AirInputService.Target?, on: Boolean) {
        main.post { view.setPointer(x, y, progress, target, on) }
    }

    fun setHands(skeletons: List<FloatArray>, pinchRatio: Float, closeAt: Float) {
        main.post { view.setHands(skeletons, pinchRatio, closeAt) }
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
            color = Color.argb(155, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val cardEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 18, 33, 42)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
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
        private var hands: List<FloatArray> = emptyList()
        private var pinch = 1f
        private var closeAt = 0.38f
        private var status = ""

        fun setPointer(px: Float, py: Float, p: Float, t: AirInputService.Target?, on: Boolean) {
            x = px; y = py; progress = p; target = t; pointerOn = on
            invalidate()
        }

        fun setHands(skeletons: List<FloatArray>, ratio: Float, close: Float) {
            hands = skeletons; pinch = ratio; closeAt = close
            invalidate()
        }

        fun setStatus(text: String) {
            if (text != status) {
                status = text
                invalidate()
            }
        }

        override fun onDraw(c: Canvas) {
            drawPanel(c)
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

        /** Panel width follows the hands inside it and the longest wrapped status line - nothing else. */
        private fun drawPanel(c: Canvas) {
            val handCount = max(1, hands.size)
            val cellW = 96f * d
            val cellH = 118f * d
            val pad = 10f * d
            val lines = statusLines()
            val textBlock = if (lines.isEmpty()) 0f else (lines.size * (label.textSize + 3f * d))
            val widest = lines.maxOfOrNull { label.measureText(it) } ?: 0f
            val bodyW = cellW * handCount + pad * (handCount - 1)
            val panelW = max(bodyW, widest) + pad * 2f
            val panelH = cellH + (if (lines.isEmpty()) 0f else pad + textBlock) + pad * 2f
            val left = width - panelW - 10f * d
            val panel = RectF(left, 10f * d, left + panelW, 10f * d + panelH)
            c.drawRoundRect(panel, 18f * d, 18f * d, card)
            c.drawRoundRect(panel, 18f * d, 18f * d, cardEdge)

            if (hands.isEmpty()) {
                val ghostBox = RectF(panel.left + pad, panel.top + pad, panel.right - pad, panel.top + pad + cellH)
                c.drawRoundRect(ghostBox, 14f * d, 14f * d, ghost)
                c.drawText("دست پیدا نیست", ghostBox.centerX(), ghostBox.centerY(), label)
            } else {
                hands.take(2).forEachIndexed { i, pts ->
                    val cell = RectF(
                        panel.left + pad + i * (cellW + pad),
                        panel.top + pad,
                        panel.left + pad + i * (cellW + pad) + cellW,
                        panel.top + pad + cellH,
                    )
                    drawHand(c, pts, cell)
                }
            }
            if (lines.isNotEmpty()) {
                val textTop = panel.top + pad + cellH + pad + label.textSize
                lines.forEachIndexed { i, line ->
                    c.drawText(line, panel.centerX(), textTop + i * (label.textSize + 3f * d), label)
                }
            }
        }

        private fun drawHand(c: Canvas, pts: FloatArray, cell: RectF) {
            if (pts.size < 24) return
            val inner = RectF(cell.left + 6f * d, cell.top + 6f * d, cell.right - 6f * d, cell.bottom - 6f * d)
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
            var i = 0
            while (i + 1 < pts.size) {
                val p = i / 2
                val radius = if (p in TIPS) 5.2f * d else 3.4f * d
                c.drawCircle(inner.left + (1f - pts[i]) * sx, inner.top + pts[i + 1] * sy, radius, if (p in TIPS) tip else joint)
                i += 2
            }
            // The pinching fingers light up, so a click is visible as it happens.
            if (pinch < closeAt && pts.size > 17) {
                c.drawCircle(inner.left + (1f - pts[8]) * sx, inner.top + pts[9] * sy, 9f * d, halo)
            }
        }

        /** At most two lines, split on spaces, so a long sentence never becomes a banner. */
        private fun statusLines(): List<String> {
            val text = status.trim()
            if (text.isEmpty()) return emptyList()
            val limit = (width * 0.55f)
            if (label.measureText(text) <= limit) return listOf(text)
            val words = text.split(" ")
            val lines = ArrayList<String>(2)
            val current = StringBuilder()
            for (w in words) {
                val trial = if (current.isEmpty()) w else "$current $w"
                if (label.measureText(trial) > limit && current.isNotEmpty()) {
                    lines.add(current.toString())
                    current.setLength(0)
                    current.append(w)
                    if (lines.size == 2) break
                } else {
                    current.setLength(0)
                    current.append(trial)
                }
            }
            if (lines.size < 2 && current.isNotEmpty()) lines.add(current.toString())
            return lines
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
