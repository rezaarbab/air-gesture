package ir.airgesture.model

/** A recognised movement, before anybody decides what it means. */
enum class AirGesture(val title: String, val modes: Set<SenseMode>) {
    SWIPE_UP("دست به بالا", setOf(SenseMode.GESTURE)),
    SWIPE_DOWN("دست به پایین", setOf(SenseMode.GESTURE)),
    PINCH("یک پینچ", setOf(SenseMode.POINTER, SenseMode.HAND_POINTER, SenseMode.GESTURE)),
    DOUBLE_PINCH("دو پینچ پشت هم", setOf(SenseMode.POINTER, SenseMode.HAND_POINTER, SenseMode.GESTURE)),
    PALM_HOLD("کف باز ثابت", setOf(SenseMode.GESTURE)),
    BLINK("پلک", setOf(SenseMode.POINTER, SenseMode.HAND_POINTER)),
    WAVE("یک موج", setOf(SenseMode.PROX)),
    DOUBLE_WAVE("دو موج پشت هم", setOf(SenseMode.PROX)),
    COVER_LONG("پوشاندن طولانی", setOf(SenseMode.PROX));

    companion object {
        fun named(value: String): AirGesture? = entries.firstOrNull { it.name == value }
        fun forMode(m: SenseMode): List<AirGesture> = entries.filter { it.modes.contains(m) }
    }
}
