package ir.airgesture.decode

import ir.airgesture.model.AirGesture

/**
 * The proximity sensor reports one bit, so the only signals are "when did the cover start" and
 * "when did it end". A feed on every change plus a tick is enough to time both.
 */
class ProxDecoder(private var cfg: ProxCfg) {

    private var near = false
    private var nearSince = -1L
    private var lastEnd = -1L
    private var pendingSingle = -1L
    private var lastFire = -1L
    private var lastChange = -1L
    private var coverFired = false

    fun setCfg(next: ProxCfg) {
        cfg = next
    }

    fun feed(t: Long, isNear: Boolean): List<AirGesture> {
        val out = ArrayList<AirGesture>(1)
        val c = cfg
        val changed = isNear != near
        if (changed && lastChange >= 0 && t - lastChange < c.debounceMs) return out

        if (changed) {
            lastChange = t
            near = isNear
            if (isNear) {
                nearSince = t
                coverFired = false
            } else {
                val dur = t - nearSince
                nearSince = -1
                val free = lastFire < 0 || t - lastFire >= c.cooldownMs
                if (dur in 0..c.maxPulseMs && !coverFired && free) {
                    if (lastEnd >= 0 && t - lastEnd <= c.doubleWindowMs) {
                        out.add(AirGesture.DOUBLE_WAVE)
                        lastEnd = -1
                        pendingSingle = -1
                        lastFire = t
                    } else {
                        lastEnd = t
                        pendingSingle = t + c.doubleWindowMs
                    }
                } else {
                    // An ambiguous break invalidates the first half of a would-be double wave.
                    lastEnd = -1
                }
            }
        }

        if (near && nearSince >= 0 && !coverFired && t - nearSince >= c.coverMs &&
            (lastFire < 0 || t - lastFire >= c.cooldownMs)
        ) {
            out.add(AirGesture.COVER_LONG)
            coverFired = true
            lastFire = t
        }

        if (pendingSingle >= 0 && t >= pendingSingle) {
            pendingSingle = -1
            if (lastFire < 0 || t - lastFire >= c.cooldownMs) {
                out.add(AirGesture.WAVE)
                lastFire = t
            }
        }
        return out
    }

    fun reset() {
        near = false
        nearSince = -1
        lastEnd = -1
        pendingSingle = -1
        lastFire = -1
        lastChange = -1
        coverFired = false
    }
}
