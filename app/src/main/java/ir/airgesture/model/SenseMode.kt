package ir.airgesture.model

/** What the camera/sensor is being used for. */
enum class SenseMode(val title: String) {
    /** Face drives a pointer, hand (pinch) clicks, dwell clicks are optional. */
    POINTER("نشانگر با سر/چهره"),

    /** Hand only: swipes and pinches as discrete gestures. */
    GESTURE("حرکت دست"),

    /** Proximity sensor only. */
    PROX("سنسور مجاورت");
}
