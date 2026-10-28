package ir.airgesture.decode

/**
 * One frame of hand geometry, reduced to what the gestures, the pointer and the drawing need.
 *
 * x/y is the wrist (normalized to the frame, y grows downward), pinchRatio and span are divided by
 * the hand's own length so they do not drift when the hand comes closer to the lens. indexX/indexY
 * is the index fingertip - in finger-pointer mode that is the thing that moves the cursor. pts is
 * the 21-point skeleton (x,y pairs) for drawing.
 */
data class HandSample(
    val tMs: Long,
    val present: Boolean,
    val wristX: Float,
    val wristY: Float,
    val pinchRatio: Float,
    val span: Float,
    val pts: FloatArray = FloatArray(0),
    val indexX: Float = 0f,
    val indexY: Float = 0f,
    val handIndex: Int = 0,
)
