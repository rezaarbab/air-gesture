package ir.airgesture

import android.content.Context
import android.content.SharedPreferences
import ir.airgesture.decode.HandCfg
import ir.airgesture.decode.ProxCfg
import ir.airgesture.model.AirAction
import ir.airgesture.model.AirGesture
import ir.airgesture.model.SenseMode

/**
 * One SharedPreferences file for the whole app. Mapping is stored as one key per gesture so a
 * broken value degrades to the default instead of wiping the rest.
 */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, false)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    var mode: SenseMode
        get() = if (sp.getString(K_MODE, null) == SenseMode.PROX.name) SenseMode.PROX else SenseMode.HAND
        set(v) = sp.edit().putString(K_MODE, v.name).apply()

    var hud: Boolean
        get() = sp.getBoolean(K_HUD, true)
        set(v) = sp.edit().putBoolean(K_HUD, v).apply()

    /** Needed hand speed in screen-heights per second; the slider raises the floor, never lowers it. */
    var swipeSpeed: Float
        get() = sp.getFloat(K_SPEED, 0.9f)
        set(v) = sp.edit().putFloat(K_SPEED, v.coerceIn(0.35f, 2.5f)).apply()

    var invert: Boolean
        get() = sp.getBoolean(K_INVERT, false)
        set(v) = sp.edit().putBoolean(K_INVERT, v).apply()

    /** Fraction of the travel that the proximity pulse may last and still count as a wave. */
    var proxAgility: Float
        get() = sp.getFloat(K_PROX, 1.0f)
        set(v) = sp.edit().putFloat(K_PROX, v.coerceIn(0.5f, 2.0f)).apply()

    fun actionFor(g: AirGesture): AirAction =
        AirAction.named(sp.getString(K_MAP + g.name, null) ?: AirAction.defaultFor(g).name)

    fun setAction(g: AirGesture, a: AirAction) {
        sp.edit().putString(K_MAP + g.name, a.name).apply()
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

    companion object {
        private const val FILE = "air"
        private const val K_ENABLED = "enabled"
        private const val K_MODE = "mode"
        private const val K_HUD = "hud"
        private const val K_SPEED = "speed"
        private const val K_INVERT = "invert"
        private const val K_PROX = "prox"
        private const val K_MAP = "map."
    }
}
