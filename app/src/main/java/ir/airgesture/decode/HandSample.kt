package ir.airgesture.decode

/**
 * One frame of hand geometry, already reduced to what the gestures actually need.
 * x/y are normalized to the frame (y grows downward, like the image), pinchRatio and span are
 * divided by the hand's own size so they do not drift when the hand moves closer to the lens.
 */
data class HandSample(
    val tMs: Long,
    val present: Boolean,
    val wristX: Float,
    val wristY: Float,
    val pinchRatio: Float,
    val span: Float,
)
