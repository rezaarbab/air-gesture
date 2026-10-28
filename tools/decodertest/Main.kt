import ir.airgesture.decode.HandCfg
import ir.airgesture.decode.HandDecoder
import ir.airgesture.decode.HandSample
import ir.airgesture.decode.ProxCfg
import ir.airgesture.decode.ProxDecoder
import ir.airgesture.model.AirGesture

/**
 * Plain JVM assertions for the two decoders - no device, no Gradle. Run with tools/run-tests.sh.
 * Each case names the gesture it expects, so a red line is already a diagnosis.
 */

private var fails = 0
private var passes = 0

private fun expect(name: String, got: List<AirGesture>, want: List<AirGesture>) {
    if (got == want) {
        passes += 1
        println("ok   $name -> $got")
    } else {
        fails += 1
        println("FAIL $name -> got $got want $want")
    }
}

private fun hand(t: Long, y: Float, pinch: Float = 0.9f, present: Boolean = true) =
    HandSample(t, present, 0.5f, y, pinch, 0.25f)

private fun swipe(
    d: HandDecoder,
    from: Float,
    to: Float,
    start: Long = 1000,
    stepMs: Long = 33,
): List<AirGesture> {
    val out = ArrayList<AirGesture>()
    val steps = 8
    for (i in 0..steps) {
        val f = i.toFloat() / steps
        val y = from + (to - from) * f
        out.addAll(d.feed(hand(start + i * stepMs, y)))
    }
    return out
}

fun main() {
    val cfg = HandCfg()
    println("== hand decoder (cfg $cfg) ==")

    run {
        val d = HandDecoder(cfg)
        expect("swipe up (y 0.70 -> 0.42)", swipe(d, 0.70f, 0.42f), listOf(AirGesture.SWIPE_UP))
    }
    run {
        val d = HandDecoder(cfg)
        expect("swipe down (y 0.42 -> 0.70)", swipe(d, 0.42f, 0.70f), listOf(AirGesture.SWIPE_DOWN))
    }
    run {
        val d = HandDecoder(cfg.copy(invert = true))
        expect("invert flips up to down", swipe(d, 0.70f, 0.42f), listOf(AirGesture.SWIPE_DOWN))
    }
    run {
        val d = HandDecoder(cfg)
        val slow = swipe(d, 0.70f, 0.66f, stepMs = 33)
        expect("a 4cm creep is not a swipe", slow, emptyList())
    }
    run {
        val d = HandDecoder(cfg)
        val out = ArrayList<AirGesture>()
        // Close, open, then hold past the double window: one PINCH, no phantom double.
        out.addAll(d.feed(hand(1000, 0.5f, 0.9f)))
        out.addAll(d.feed(hand(1033, 0.5f, 0.20f)))
        out.addAll(d.feed(hand(1066, 0.5f, 0.9f)))
        out.addAll(d.feed(hand(1700, 0.5f, 0.9f)))
        expect("single pinch after the window", out, listOf(AirGesture.PINCH))
    }
    run {
        val d = HandDecoder(cfg)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(hand(1000, 0.5f, 0.9f)))
        out.addAll(d.feed(hand(1033, 0.5f, 0.20f)))
        out.addAll(d.feed(hand(1066, 0.5f, 0.9f)))
        out.addAll(d.feed(hand(1200, 0.5f, 0.20f)))
        out.addAll(d.feed(hand(1233, 0.5f, 0.9f)))
        out.addAll(d.feed(hand(1900, 0.5f, 0.9f)))
        expect("two pinches are DOUBLE_PINCH only", out, listOf(AirGesture.DOUBLE_PINCH))
    }
    run {
        val d = HandDecoder(cfg)
        val out = ArrayList<AirGesture>()
        for (i in 0..40) out.addAll(d.feed(hand(1000L + i * 33L, 0.5f, 0.9f)))
        expect("still open palm fires once", out, listOf(AirGesture.PALM_HOLD))
    }
    run {
        val d = HandDecoder(cfg)
        val out = ArrayList<AirGesture>()
        out.addAll(swipe(d, 0.70f, 0.42f))
        d.feed(hand(5000, 0.5f, 0.9f, present = false))
        out.addAll(swipe(d, 0.70f, 0.42f, start = 6000))
        expect("hand lost then a new swipe still works", out, listOf(AirGesture.SWIPE_UP, AirGesture.SWIPE_UP))
    }
    run {
        val d = HandDecoder(cfg)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(hand(1000, 0.5f, 0.9f)))
        out.addAll(d.feed(hand(1033, 0.5f, 0.20f)))
        d.reset()
        out.addAll(d.feed(hand(1400, 0.5f, 0.9f)))
        expect("reset drops a half-pressed pinch", out, emptyList())
    }

    println("== proximity decoder (cfg ${ProxCfg()}) ==")
    val pc = ProxCfg()

    run {
        val d = ProxDecoder(pc)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, true))
        out.addAll(d.feed(1150, false))
        out.addAll(d.feed(1800, false))
        expect("one short wave", out, listOf(AirGesture.WAVE))
    }
    run {
        val d = ProxDecoder(pc)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, true))
        out.addAll(d.feed(1150, false))
        out.addAll(d.feed(1300, true))
        out.addAll(d.feed(1450, false))
        out.addAll(d.feed(2100, false))
        expect("two waves are DOUBLE_WAVE only", out, listOf(AirGesture.DOUBLE_WAVE))
    }
    run {
        val d = ProxDecoder(pc)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, true))
        for (i in 1..20) out.addAll(d.feed(1000L + i * 100L, true))
        out.addAll(d.feed(3100, false))
        expect("a long cover is COVER_LONG, not a wave", out, listOf(AirGesture.COVER_LONG))
    }
    run {
        val d = ProxDecoder(pc)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, true))
        out.addAll(d.feed(1090, false))
        out.addAll(d.feed(1120, true))
        out.addAll(d.feed(1210, false))
        out.addAll(d.feed(1900, false))
        expect("second wave inside cooldown is dropped", out, listOf(AirGesture.DOUBLE_WAVE))
    }
    run {
        val d = ProxDecoder(pc)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, true))
        out.addAll(d.feed(1020, false))
        out.addAll(d.feed(1030, true))
        out.addAll(d.feed(1900, true))
        out.addAll(d.feed(2100, false))
        expect("a 10ms glitch extends the same cover", out, listOf(AirGesture.COVER_LONG))
    }
    run {
        val d = ProxDecoder(pc)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, true))
        out.addAll(d.feed(1700, false))
        out.addAll(d.feed(2600, false))
        expect("an in-between cover says nothing", out, emptyList())
    }

    println("passes=$passes fails=$fails")
    if (fails > 0) throw IllegalStateException("$fails decoder assertions failed")
}
