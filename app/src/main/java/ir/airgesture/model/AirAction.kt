package ir.airgesture.model

/**
 * What a gesture is turned into. Named after the finger motion that gets synthesized, so
 * DRAG_UP always means "a stroke that starts low on the screen and ends high" regardless of
 * which way the content happens to travel. Actions ending in _POINTER land wherever the
 * cursor currently is.
 */
enum class AirAction(val title: String) {
    NONE("هیچ"),
    DRAG_UP("انگشت به بالا (آهسته)"),
    DRAG_DOWN("انگشت به پایین (آهسته)"),
    FLICK_UP("انگشت به بالا (کنده‌شدن)"),
    FLICK_DOWN("انگشت به پایین (کنده‌شدن)"),
    TAP_CENTER("یک ضربه در وسط صفحه"),
    DOUBLE_TAP_CENTER("دو ضربهٔ پشت هم در وسط صفحه"),
    LONG_PRESS_CENTER("نگه‌داشتن در وسط صفحه"),
    TAP_POINTER("یک ضربه روی نشانگر"),
    DOUBLE_TAP_POINTER("دو ضربه روی نشانگر"),
    LONG_PRESS_POINTER("نگه‌داشتن روی نشانگر"),
    RECENTER("نقطهٔ مرجع نشانگر را تنظیم کن"),
    BACK("برگشت"),
    HOME("خانه"),
    RECENTS("برنامه‌های اخیر");

    companion object {
        fun named(value: String): AirAction = entries.firstOrNull { it.name == value } ?: NONE

        /** The shipped mapping: hand up pages the feed, a pinch clicks under the cursor. */
        fun defaultFor(g: AirGesture, m: SenseMode): AirAction {
            if (m.isPointer) {
                return when (g) {
                    AirGesture.PINCH -> TAP_POINTER
                    AirGesture.DOUBLE_PINCH -> RECENTER
                    AirGesture.BLINK -> NONE
                    else -> NONE
                }
            }
            return when (g) {
                AirGesture.SWIPE_UP -> FLICK_UP
                AirGesture.SWIPE_DOWN -> FLICK_DOWN
                AirGesture.PINCH -> TAP_CENTER
                AirGesture.DOUBLE_PINCH -> DOUBLE_TAP_CENTER
                AirGesture.PALM_HOLD -> NONE
                AirGesture.WAVE -> FLICK_UP
                AirGesture.DOUBLE_WAVE -> DOUBLE_TAP_CENTER
                AirGesture.COVER_LONG -> BACK
                else -> NONE
            }
        }
    }
}
