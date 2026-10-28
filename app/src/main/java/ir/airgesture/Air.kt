package ir.airgesture

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/** The single place the running state and the diagnosis log live. */
object Air {

    private const val TAG = "AIR"
    private const val MAX_LINES = 240

    data class Line(val t: Long, val text: String)

    data class Snap(
        val running: Boolean = false,
        val accReady: Boolean = false,
        val sourceUp: Boolean = false,
        val handVisible: Boolean = false,
        val faceVisible: Boolean = false,
        val fps: Int = 0,
        val frames: Long = 0,
        val gestures: Long = 0,
        val lastGesture: String = "—",
        val lastAction: String = "—",
        val faceMs: Float = 0f,
        val handMs: Float = 0f,
        val cursorX: Float = 0.5f,
        val cursorY: Float = 0.5f,
        val dwell: Float = 0f,
        val target: String = "",
        val tracking: Boolean = false,
        val moved: Boolean = false,
        val probe: String = "",
        val frameInfo: String = "",
        val blinkPeak: Float = 0f,
        val hands: Int = 0,
    )

    val state = MutableStateFlow(Snap())
    val lines = MutableStateFlow<List<Line>>(emptyList())

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun update(f: (Snap) -> Snap) {
        state.value = f(state.value)
    }

    /** One line, one cause: whatever is logged here has to say which check failed. */
    fun note(text: String) {
        val now = System.currentTimeMillis()
        Log.i(TAG, text)
        val next = lines.value
        val appended = if (next.size >= MAX_LINES) next.drop(1) + Line(now, text) else next + Line(now, text)
        lines.value = appended
    }

    fun stamp(t: Long): String = fmt.format(Date(t))
}
