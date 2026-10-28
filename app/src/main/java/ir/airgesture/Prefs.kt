package ir.airgesture

import android.content.Context
import android.content.SharedPreferences
import ir.airgesture.decode.BlinkCfg
import ir.airgesture.decode.DwellCfg
import ir.airgesture.decode.HandCfg
import ir.airgesture.decode.PointerCfg
import ir.airgesture.decode.ProxCfg
import ir.airgesture.model.AirAction
import ir.airgesture.model.AirGesture
import ir.airgesture.model.SenseMode

/**
 * One SharedPreferences file for the whole app. The mapping is keyed by mode as well as gesture,
 * because a pinch should click under the cursor in pointer mode and under nothing at all in
 * gesture mode; a broken value degrades to the default instead of wiping the rest.
 */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, false)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    var mode: SenseMode
        get() = SenseMode.entries.firstOrNull { it.name == sp.getString(K_MODE, null) } ?: SenseMode.POINTER
        set(v) = sp.edit().putString(K_MODE, v.name).apply()

    /** Needed hand speed in screen-heights per second (gesture mode). */
    var swipeSpeed: Float
        get() = sp.getFloat(K_SPEED, 0.9f)
        set(v) = sp.edit().putFloat(K_SPEED, v.coerceIn(0.35f, 2.5f)).apply()

    var invert: Boolean
        get() = sp.getBoolean(K_INVERT, false)
        set(v) = sp.edit().putBoolean(K_INVERT, v).apply()

    var proxAgility: Float
        get() = sp.getFloat(K_PROX, 1.0f)
        set(v) = sp.edit().putFloat(K_PROX, v.coerceIn(0.5f, 2.0f)).apply()

    /** Pointer: head offset is multiplied by this to become cursor speed (fractions of screen/s). */
    var pointerGain: Float
        get() = sp.getFloat(K_PGAIN, 2.4f)
        set(v) = sp.edit().putFloat(K_PGAIN, v.coerceIn(0.8f, 6.0f)).apply()

    /** 0.05 = heavily smoothed (steady, laggy), 1 = raw (jittery, instant). */
    var pointerSmooth: Float
        get() = sp.getFloat(K_PSMOOTH, 0.35f)
        set(v) = sp.edit().putFloat(K_PSMOOTH, v.coerceIn(0.05f, 1.0f)).apply()

    var dwellEnabled: Boolean
        get() = sp.getBoolean(K_DWELL, true)
        set(v) = sp.edit().putBoolean(K_DWELL, v).apply()

    var dwellMs: Long
        get() = sp.getLong(K_DWELL_MS, 900L)
        set(v) = sp.edit().putLong(K_DWELL_MS, v.coerceIn(400L, 2500L)).apply()

    /** Only dwell when the pointer is standing on something the screen tree says is clickable. */
    var dwellNeedsTarget: Boolean
        get() = sp.getBoolean(K_DWELL_TGT, false)
        set(v) = sp.edit().putBoolean(K_DWELL_TGT, v).apply()

    var blinkEnabled: Boolean
        get() = sp.getBoolean(K_BLINK, true)
        set(v) = sp.edit().putBoolean(K_BLINK, v).apply()

    /** Run the hand model once per N frames in pointer mode; 1 = every frame. */
    var handStride: Int
        get() = sp.getInt(K_STRIDE, 2)
        set(v) = sp.edit().putInt(K_STRIDE, v.coerceIn(1, 6)).apply()

    var gpuDelegate: Boolean
        get() = sp.getBoolean(K_GPU, true)
        set(v) = sp.edit().putBoolean(K_GPU, v).apply()

    /**
     * Degrees the frame is rotated before inference. -1 = derive it from the sensor
     * (front camera needs (360 - sensorOrientation), which is not the same number); the override
     * exists because hand and face detectors are orientation-sensitive and I cannot test on his
     * phone from here.
     */
    var rotation: Int
        get() = sp.getInt(K_ROT, -1)
        set(v) = sp.edit().putInt(K_ROT, v).apply()

    /** Draw the recognized hand as a live skeleton instead of only a status line. */
    var showSkeleton: Boolean
        get() = sp.getBoolean(K_SKEL, true)
        set(v) = sp.edit().putBoolean(K_SKEL, v).apply()

    fun actionFor(g: AirGesture, m: SenseMode): AirAction {
        val key = K_MAP + m.name + "." + g.name
        val fallback = AirAction.defaultFor(g, m).name
        return AirAction.named(sp.getString(key, fallback) ?: fallback)
    }

    fun setAction(g: AirGesture, m: SenseMode, a: AirAction) {
        sp.edit().putString(K_MAP + m.name + "." + g.name, a.name).apply()
    }

    fun handCfg(): HandCfg = HandCfg(minSpeed = swipeSpeed, invert = invert)

    fun proxCfg(): ProxCfg {
        val k = proxAgility
        return ProxCfg(
            maxPulseMs = (420 * k).toLong(),
            doubleWindowMs = (560 * k).toLong(),
            coverMs = (900 * k).toLong(),
        )
    }

    fun pointerCfg(): PointerCfg = PointerCfg(
        gainX = pointerGain,
        gainY = pointerGain,
        smooth = pointerSmooth,
        invertY = false,
    )

    fun dwellCfg(): DwellCfg = DwellCfg(
        enabled = dwellEnabled,
        holdMs = dwellMs,
        needsTarget = dwellNeedsTarget,
    )

    fun blinkCfg(): BlinkCfg = BlinkCfg()

    companion object {
        private const val FILE = "air"
        private const val K_ENABLED = "enabled"
        private const val K_MODE = "mode"
        private const val K_SPEED = "speed"
        private const val K_INVERT = "invert"
        private const val K_PROX = "prox"
        private const val K_PGAIN = "pgain"
        private const val K_PSMOOTH = "psmooth"
        private const val K_DWELL = "dwell"
        private const val K_DWELL_MS = "dwell_ms"
        private const val K_DWELL_TGT = "dwell_target"
        private const val K_BLINK = "blink"
        private const val K_STRIDE = "stride"
        private const val K_GPU = "gpu"
        private const val K_ROT = "rot"
        private const val K_SKEL = "skeleton"
        private const val K_MAP = "map."
    }
}
