package ir.airgesture.model

enum class Source { HAND, PROX }

/** A recognised movement, before anybody decides what it means. */
enum class AirGesture(val title: String, val source: Source) {
    SWIPE_UP("دست به بالا", Source.HAND),
    SWIPE_DOWN("دست به پایین", Source.HAND),
    PINCH("یک پینچ", Source.HAND),
    DOUBLE_PINCH("دو پینچ پشت هم", Source.HAND),
    PALM_HOLD("کف باز ثابت", Source.HAND),
    WAVE("یک موج", Source.PROX),
    DOUBLE_WAVE("دو موج پشت هم", Source.PROX),
    COVER_LONG("پوشاندن طولانی", Source.PROX);

    companion object {
        fun named(value: String): AirGesture? = entries.firstOrNull { it.name == value }
    }
}
