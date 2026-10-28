package ir.airgesture.decode

/** Tunables. Frame units are normalized 0..1; pointer output is screen fractions per second. */
data class HandCfg(
    val minSpeed: Float = 0.9f,
    val minTravel: Float = 0.14f,
    val windowMs: Long = 230,
    val pinchClose: Float = 0.38f,
    val pinchOpen: Float = 0.62f,
    val doubleWindowMs: Long = 520,
    val cooldownMs: Long = 420,
    val stillSpeed: Float = 0.22f,
    val palmHoldMs: Long = 700,
    val invert: Boolean = false,
)

data class ProxCfg(
    val maxPulseMs: Long = 420,
    val doubleWindowMs: Long = 560,
    val coverMs: Long = 900,
    val cooldownMs: Long = 300,
    val debounceMs: Long = 30,
)

/**
 * Velocity control, not position control: the head offset from a stored neutral point becomes a
 * cursor speed, so the pointer stops when the head returns to center. That is why a recenter
 * gesture exists, and why this survives a slouching user better than mapping the nose to a pixel.
 */
data class PointerCfg(
    val gainX: Float = 2.4f,
    val gainY: Float = 2.4f,
    val smooth: Float = 0.35f,
    val deadzone: Float = 0.035f,
    val clampSpeed: Float = 1.6f,
    val invertY: Boolean = true,
)

data class BlinkCfg(
    val close: Float = 0.55f,
    val open: Float = 0.25f,
    val minMs: Long = 60,
    val maxMs: Long = 450,
    val cooldownMs: Long = 700,
)

data class DwellCfg(
    val enabled: Boolean = true,
    val holdMs: Long = 900,
    /** Cursor travel, in screen fractions, that counts as "moved away" and cancels the fill. */
    val moveTolerance: Float = 0.02f,
    val needsTarget: Boolean = false,
    /** After a click the fill stays disarmed until the pointer actually moves. */
    val rearmMs: Long = 220,
)
