package ir.airgesture.decode

/** Tunables. Everything is in frame units per second, so a 0.9 threshold means "nine tenths of the screen height in one second". */
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
