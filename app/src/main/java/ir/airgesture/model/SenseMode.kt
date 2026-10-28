package ir.airgesture.model

/** What the camera or sensor is being used for. */
enum class SenseMode(val title: String) {
    /** Head drives a pointer, pinch clicks, dwell clicking is optional. */
    POINTER("نشانگر با سر"),

    /** Index fingertip drives the pointer like a mouse finger; pinch clicks. */
    HAND_POINTER("نشانگر با انگشت"),

    /** Hand only: swipes and pinches as discrete gestures. */
    GESTURE("حرکت دست"),

    /** Proximity sensor only. */
    PROX("سنسور مجاورت");

    /** Pointer-like modes need the cursor machinery. */
    val isPointer: Boolean get() = this == POINTER || this == HAND_POINTER
}
