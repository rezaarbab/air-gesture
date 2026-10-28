package ir.airgesture.model

/**
 * What a gesture is turned into. Named after the finger motion that gets synthesized, so
 * DRAG_UP always means "a stroke that starts low on the screen and ends high" regardless of
 * which way the content happens to travel.
 */
enum class AirAction(val title: String) {
    NONE("هیچ"),
    DRAG_UP("انگشت به بالا (آهسته)"),
    DRAG_DOWN("انگشت به پایین (آهسته)"),
    FLICK_UP("انگشت به بالا (کنده‌شدن)"),
    FLICK_DOWN("انگشت به پایین (کنده‌شدن)"),
    DOUBLE_TAP_CENTER("دو ضربهٔ پشت هم در وسط صفحه"),
    TAP_CENTER("یک ضربه در وسط صفحه"),
    LONG_PRESS_CENTER("نگه‌داشتن در وسط صفحه"),
    BACK("برگشت"),
    HOME("خانه"),
    RECENTS("برنامه‌های اخیر");

    companion object {
        fun named(value: String): AirAction = entries.firstOrNull { it.name == value } ?: NONE

        /** The shipped mapping: hand up pages the feed forward, two pinches like a post. */
        fun defaultFor(g: AirGesture): AirAction = when (g) {
            AirGesture.SWIPE_UP -> FLICK_UP
            AirGesture.SWIPE_DOWN -> FLICK_DOWN
            AirGesture.PINCH -> TAP_CENTER
            AirGesture.DOUBLE_PINCH -> DOUBLE_TAP_CENTER
            AirGesture.PALM_HOLD -> NONE
            AirGesture.WAVE -> FLICK_UP
            AirGesture.DOUBLE_WAVE -> DOUBLE_TAP_CENTER
            AirGesture.COVER_LONG -> BACK
        }
    }
}
