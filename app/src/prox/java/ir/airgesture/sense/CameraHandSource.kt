package ir.airgesture.sense

import android.content.Context
import ir.airgesture.Air
import ir.airgesture.decode.HandSample

/**
 * The proximity build has no camera pipeline and no model to run, so this exists only to keep
 * the shared code compiling - and to say so out loud if somebody selects hand mode anyway.
 */
class CameraHandSource(private val ctx: Context, private val onSample: (HandSample) -> Unit) {

    fun start(): Boolean {
        Air.note("hand: this APK is the proximity-only build")
        return false
    }

    fun stop() = Unit
}
