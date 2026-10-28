package ir.airgesture.decode

/**
 * One frame of face geometry. x/y is the nose tip in frame units (y down); the blink score comes
 * from MediaPipe's blendshapes, so it is a learned eye-closure value rather than a geometry guess.
 */
data class FaceSample(
    val tMs: Long,
    val present: Boolean,
    val noseX: Float,
    val noseY: Float,
    val blink: Float,
)
