package ir.airgesture.hud

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import ir.airgesture.Air
import ir.airgesture.model.AirGesture
import ir.airgesture.svc.AirService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A pill over whatever app is open: it says which gesture was just read and stops the run when
 * tapped. Optional - with no overlay permission the app still works - but without it a missed
 * gesture is indistinguishable from a broken one.
 */
class Hud private constructor(
    private val ctx: Context,
    private val wm: WindowManager,
    private val pill: TextView,
) {

    private val job: Job

    init {
        val scope = CoroutineScope(Dispatchers.Main)
        pill.setOnClickListener {
            Air.note("hud: tapped, stopping")
            AirService.stop(ctx)
        }
        job = scope.launch {
            Air.state.collect { snap -> render(snap) }
        }
    }

    private fun render(snap: Air.Snap) {
        val head = when {
            !snap.running -> "اشاره خاموش"
            !snap.accReady -> "مجوز دسترسی فعال نیست"
            !snap.sourceUp -> "حسگر بالا نیامد"
            else -> "اشاره آماده"
        }
        val g = AirGesture.named(snap.lastGesture)
        pill.text = if (g == null) head else "$head · ${g.title}"
    }

    fun detach() {
        job.cancel()
        try {
            wm.removeView(pill)
        } catch (e: Exception) {
            Air.note("hud: removeView failed (${e.javaClass.simpleName})")
        }
    }

    companion object {
        fun attach(ctx: Context): Hud? {
            if (!Settings.canDrawOverlays(ctx)) {
                Air.note("hud: overlay not allowed, running without the pill")
                return null
            }
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
            val pill = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(185, 10, 16, 20))
                setPadding(30, 16, 30, 16)
                textSize = 13f
                maxLines = 1
                gravity = Gravity.CENTER
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 20
            }
            return try {
                wm.addView(pill, params)
                Hud(ctx, wm, pill)
            } catch (e: Exception) {
                Air.note("hud: addView refused (${e.javaClass.simpleName}: ${e.message})")
                null
            }
        }
    }
}
