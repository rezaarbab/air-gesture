package ir.airgesture.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Handler
import android.os.Looper
import ir.airgesture.Air
import ir.airgesture.model.AirAction

/**
 * The only thing in this app that touches another app. The framework feeds the strokes through
 * the same input path a finger uses, so the app on screen has no way to tell the difference -
 * which is also why the gesture dies silently on secure windows and permission dialogs.
 */
class AirInputService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Step>()
    private var busy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        holder = this
        Air.update { it.copy(accReady = true) }
        Air.note("acc: connected, gestures enabled")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (holder === this) {
            holder = null
            Air.update { it.copy(accReady = false) }
            Air.note("acc: unbound (switched off or killed)")
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (holder === this) {
            holder = null
            Air.update { it.copy(accReady = false) }
        }
        super.onDestroy()
    }

    /** Queue one action and start pumping. Safe from any thread. */
    private fun submit(a: AirAction, w: Int, h: Int) {
        handler.post {
            when (a) {
                AirAction.NONE -> Unit
                AirAction.BACK -> global(GLOBAL_ACTION_BACK, "back")
                AirAction.HOME -> global(GLOBAL_ACTION_HOME, "home")
                AirAction.RECENTS -> global(GLOBAL_ACTION_RECENTS, "recents")
                else -> {
                    val steps = Synth.steps(a, w, h)
                    if (steps.isEmpty()) {
                        Air.note("inject: no stroke built for ${a.name}")
                    } else {
                        queue.clear()
                        queue.addAll(steps)
                        pump()
                    }
                }
            }
        }
    }

    private fun global(what: Int, name: String) {
        val ok = performGlobalAction(what)
        Air.note(if (ok) "inject: global $name" else "inject: global $name refused by the system")
    }

    private fun pump() {
        if (busy) return
        val s = queue.removeFirstOrNull() ?: return
        busy = true
        handler.postDelayed({ dispatch(s.desc) }, if (s.delayMs > 0) s.delayMs else 0L)
    }

    private fun dispatch(desc: GestureDescription) {
        val ok = dispatchGesture(desc, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                busy = false
                pump()
            }

            override fun onCancelled(g: GestureDescription?) {
                busy = false
                // The window refused it: secure flag, a system dialog, or the keyboard holding focus.
                Air.note("inject: cancelled by the window in front")
                pump()
            }
        }, handler)
        if (!ok) {
            busy = false
            Air.note("inject: dispatchGesture refused to start (service not active?)")
            handler.postDelayed({ pump() }, 150L)
        }
    }

    companion object {
        @Volatile
        private var holder: AirInputService? = null

        val ready: Boolean get() = holder != null

        fun fire(a: AirAction) {
            val svc = holder
            if (svc == null) {
                Air.note("inject: skipped ${a.name}, accessibility service is off")
                return
            }
            val dm = svc.resources.displayMetrics
            svc.submit(a, dm.widthPixels, dm.heightPixels)
        }
    }
}
