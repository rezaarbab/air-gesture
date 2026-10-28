package ir.airgesture.decode

/**
 * One frame of hand geometry, already reduced to what the gestures need.
 * x/y are normalized to the frame (y grows downward, like the image), pinchRatio and span are
 * divided by the hand's own size so they do not drift when the hand moves closer to the lens.
 * pts is the 21-point skeleton (x,y pairs, same frame units) purely for drawing it on screen.
 */
data class HandSample(
    val tMs: Long,
    val present: Boolean,
    val wristX: Float,
    val wristY: Float,
    val pinchRatio: Float,
    val span: Float,
    val pts: FloatArray = FloatArray(0),
)
