package ir.airgesture.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import ir.airgesture.Air
import ir.airgesture.model.AirAction

/**
 * The only thing in this app that touches another app. The framework feeds the strokes through
 * the same input path a finger uses, so the app on screen has no way to tell the difference -
 * which is also why the gesture dies silently on secure windows and permission dialogs.
 *
 * The second job here is knowing *what* the pointer is standing on, so the dwell ring can wrap a
 * real control instead of floating in the dark. That needs the window's node tree, which only
 * exists for apps that expose one: native views yes, a Canvas game or a Flutter surface mostly no.
 */
class AirInputService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Step>()
    private var busy = false

    /** What the pointer is over, in pixels. */
    data class Target(
        val cx: Float,
        val cy: Float,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val key: String,
        val actionable: Boolean,
        val label: String,
    )

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

    private fun submit(a: AirAction, px: Float, py: Float) {
        handler.post {
            when (a) {
                AirAction.NONE, AirAction.RECENTER -> Unit
                AirAction.BACK -> global(GLOBAL_ACTION_BACK, "back")
                AirAction.HOME -> global(GLOBAL_ACTION_HOME, "home")
                AirAction.RECENTS -> global(GLOBAL_ACTION_RECENTS, "recents")
                else -> {
                    val steps = Synth.steps(a, screenW, screenH, px, py)
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

    private fun submitDrag(d: Drag) {
        handler.post {
            val s = Synth.dragStroke(d)
            if (s == null) {
                Air.note("drag: path too short to synthesize (${d.points.size} points)")
            } else {
                queue.clear()
                queue.add(s)
                pump()
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

    /**
     * Deepest visible node containing the point. Actionable wins when there is one, otherwise the
     * plain container is returned so the ring still hugs something. Walking stops at 4000 nodes
     * and 30 levels: a huge or cyclic tree must cost seconds at most, not the whole service.
     */
    fun targetAt(fx: Float, fy: Float): Target? {
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Air.note("tree: rootInActiveWindow threw ${e.javaClass.simpleName}")
            return null
        } ?: return null

        val px = fx * screenW
        val py = fy * screenH
        val bounds = Rect()
        var any: AccessibilityNodeInfo? = null
        var anyDepth = -1
        var act: AccessibilityNodeInfo? = null
        var actDepth = -1
        var seen = 0

        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && seen < 4000) {
            val (node, depth) = stack.removeFirst()
            seen += 1
            if (depth > 30) continue
            node.getBoundsInScreen(bounds)
            if (bounds.left < bounds.right && bounds.contains(px.toInt(), py.toInt()) &&
                node.isVisibleToUser
            ) {
                if (depth > anyDepth) {
                    anyDepth = depth
                    any = node
                }
                if ((node.isClickable || node.isLongClickable || node.isCheckable) && depth > actDepth) {
                    actDepth = depth
                    act = node
                }
            }
            for (i in 0 until node.childCount) {
                val c = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (c != null) stack.addLast(c to depth + 1)
            }
        }
        val pick = act ?: any ?: return null
        val r = Rect()
        pick.getBoundsInScreen(r)
        val cls = pick.className?.toString().orEmpty().substringAfterLast('.')
        val text = (pick.text?.toString().orEmpty() + " " + pick.contentDescription?.toString().orEmpty()).trim()
        return Target(
            cx = r.centerX().toFloat(),
            cy = r.centerY().toFloat(),
            left = r.left,
            top = r.top,
            right = r.right,
            bottom = r.bottom,
            key = "$cls@${r.left},${r.top},${r.right},${r.bottom}",
            actionable = act != null,
            label = if (text.length > 28) text.substring(0, 28) else text,
        )
    }

    private val screenW: Int get() = resources.displayMetrics.widthPixels
    private val screenH: Int get() = resources.displayMetrics.heightPixels

    companion object {
        @Volatile
        private var holder: AirInputService? = null

        val ready: Boolean get() = holder != null

        fun fire(a: AirAction, px: Float, py: Float) {
            val svc = holder
            if (svc == null) {
                Air.note("inject: skipped ${a.name}, accessibility service is off")
                return
            }
            svc.submit(a, px, py)
        }

        fun drag(d: Drag) {
            val svc = holder
            if (svc == null) {
                Air.note("drag: skipped, accessibility service is off")
                return
            }
            svc.submitDrag(d)
        }

        /** Null means either no service, or the app in front publishes no node tree. */
        fun targetAt(fx: Float, fy: Float): Target? = holder?.targetAt(fx, fy)

        fun screen(): Pair<Int, Int> {
            val svc = holder ?: return 0 to 0
            return svc.screenW to svc.screenH
        }
    }
}
