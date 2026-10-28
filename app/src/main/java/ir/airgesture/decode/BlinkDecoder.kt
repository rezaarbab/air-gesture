package ir.airgesture.decode

import ir.airgesture.model.AirGesture

/**
 * Blinks are counted from MediaPipe's own eyeBlink blendshape (0 open, 1 closed) rather than
 * from eye geometry, so glasses and a dark room change the score less. A blink is a close-then
 * -open edge whose closed stretch sits inside [minMs, maxMs]; a long squint is deliberately not
 * a click, because holding your eyes shut is how people think.
 */
class BlinkDecoder(private var cfg: BlinkCfg) {

    private var closed = false
    private var closedSince = -1L
    private var lastFire = -1L

    fun setCfg(next: BlinkCfg) {
        cfg = next
    }

    fun feed(t: Long, blink: Float, present: Boolean): List<AirGesture> {
        val c = cfg
        if (!present) {
            closed = false
            closedSince = -1L
            return emptyList()
        }
        if (!closed && blink > c.close) {
            closed = true
            closedSince = t
            return emptyList()
        }
        if (closed && blink < c.open) {
            closed = false
            val dur = t - closedSince
            closedSince = -1L
            if (dur in c.minMs..c.maxMs && (lastFire < 0 || t - lastFire >= c.cooldownMs)) {
                lastFire = t
                return listOf(AirGesture.BLINK)
            }
        }
        return emptyList()
    }

    fun reset() {
        closed = false
        closedSince = -1L
        lastFire = -1L
    }
}
