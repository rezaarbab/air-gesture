import ir.airgesture.decode.BlinkCfg
import ir.airgesture.decode.BlinkDecoder
import ir.airgesture.decode.DwellCfg
import ir.airgesture.decode.HandCfg
import ir.airgesture.decode.HandDecoder
import ir.airgesture.decode.HandSample
import ir.airgesture.decode.PointerCfg
import ir.airgesture.decode.ProxCfg
import ir.airgesture.decode.ProxDecoder
import ir.airgesture.model.AirGesture
import ir.airgesture.pointer.DwellTracker
import ir.airgesture.pointer.Move
import ir.airgesture.pointer.PointerFilter
import kotlin.math.abs

/**
 * Plain JVM assertions for the Android-free logic - no device, no Gradle. Run with tools/run-tests.sh
 * (locally or in CI before the APK is built). Each case names the behaviour it expects, so a red
 * line is already a diagnosis.
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

private fun check(name: String, ok: Boolean, detail: String = "") {
    if (ok) {
        passes += 1
        println("ok   $name${if (detail.isEmpty()) "" else " ($detail)"}")
    } else {
        fails += 1
        println("FAIL $name${if (detail.isEmpty()) "" else " ($detail)"}")
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
    handDecoderCases()
    proxDecoderCases()
    pointerFilterCases()
    dwellCases()
    blinkCases()
    println("passes=$passes fails=$fails")
    if (fails > 0) throw IllegalStateException("$fails assertions failed")
}

private fun handDecoderCases() {
    val cfg = HandCfg()
    println("== hand decoder ==")
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
        expect("a slow creep is not a swipe", swipe(d, 0.70f, 0.66f, stepMs = 33), emptyList())
    }
    run {
        val d = HandDecoder(cfg)
        val out = ArrayList<AirGesture>()
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
}

private fun proxDecoderCases() {
    val pc = ProxCfg()
    println("== proximity decoder ==")
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
}

private fun pointerFilterCases() {
    val pc = PointerCfg()
    println("== pointer filter (gain ${pc.gainX}, deadzone ${pc.deadzone}, clamp ${pc.clampSpeed}) ==")

    fun feed(d: PointerFilter, t0: Long, x: Float, y: Float, n: Int): Move {
        var last = Move(0f, 0f, 0f, false)
        for (i in 0 until n) last = d.feed(t0 + i * 33L, x, y, true)
        return last
    }

    run {
        val d = PointerFilter(pc)
        val first = d.feed(1000, 0.5f, 0.5f, true)
        check("the first sample only sets neutral", first.dx == 0f && first.dy == 0f, "dx=${first.dx}")
    }
    run {
        val d = PointerFilter(pc)
        d.feed(1000, 0.5f, 0.5f, true)
        val m = feed(d, 1100, 0.6f, 0.5f, 6)
        check("head right of neutral pushes +x", m.dx > 0.1f, "dx=${m.dx}")
        check("a level head does not drift vertically", abs(m.dy) < 0.001f, "dy=${m.dy}")
    }
    run {
        val d = PointerFilter(pc)
        d.feed(1000, 0.5f, 0.5f, true)
        val m = feed(d, 1100, 0.515f, 0.5f, 20)
        check("inside the deadzone nothing moves", m.dx == 0f, "dx=${m.dx}")
    }
    run {
        val d = PointerFilter(pc)
        d.feed(1000, 0.5f, 0.5f, true)
        val m = feed(d, 1100, 0.98f, 0.5f, 40)
        check("speed is clamped, not infinite", abs(m.dx) <= pc.clampSpeed + 0.001f, "dx=${m.dx}")
    }
    run {
        val d = PointerFilter(pc)
        d.feed(1000, 0.5f, 0.5f, true)
        feed(d, 1100, 0.6f, 0.5f, 6)
        val lost = d.feed(1300, 0.6f, 0.5f, false)
        check("losing the face freezes the cursor", lost.dx == 0f && lost.dy == 0f && !lost.valid)
        val back = d.feed(1400, 0.6f, 0.5f, true)
        check("smoothing ramps from rest instead of jumping to full speed", back.dx in 0f..0.15f, "dx=${back.dx}")
    }
    run {
        val d = PointerFilter(pc)
        feed(d, 1000, 0.62f, 0.5f, 10)
        d.recenter(0.62f, 0.5f)
        val m = d.feed(2000, 0.62f, 0.5f, true)
        check("recenter makes the current pose neutral", m.dx == 0f && m.dy == 0f)
    }
    run {
        val d = PointerFilter(pc.copy(invertY = true))
        d.feed(1000, 0.5f, 0.5f, true)
        val m = feed(d, 1100, 0.5f, 0.6f, 8)
        check("invertY sends a lowered head upward", m.dy < 0f, "dy=${m.dy}")
    }
}

private fun dwellCases() {
    val dc = DwellCfg()
    println("== dwell (hold ${dc.holdMs}ms, tolerance ${dc.moveTolerance}) ==")

    run {
        val t = DwellTracker(dc)
        val a = t.tick(1000, 0.5f, 0.5f, "btn")
        val b = t.tick(1450, 0.5f, 0.5f, "btn")
        val c = t.tick(1900, 0.5f, 0.5f, "btn")
        check("nothing fired at the start", a.progress == 0f && !a.fired)
        check("half full after half the wait", abs(b.progress - 0.5f) < 0.02f, "p=${b.progress}")
        check("fires exactly at holdMs", c.fired && c.progress == 1f, "p=${c.progress}")
        val d = t.tick(1950, 0.5f, 0.5f, "btn")
        check("a second tick does not double-click", !d.fired && d.progress == 0f)
    }
    run {
        val t = DwellTracker(dc)
        t.tick(1000, 0.5f, 0.5f, "btn")
        t.tick(1400, 0.5f, 0.5f, "btn")
        val moved = t.tick(1450, 0.55f, 0.5f, "btn")
        check("drifting past tolerance cancels the fill", moved.progress == 0f, "p=${moved.progress}")
    }
    run {
        val t = DwellTracker(dc)
        t.tick(1000, 0.5f, 0.5f, "a")
        val swapped = t.tick(1400, 0.5f, 0.5f, "b")
        check("sliding to another control restarts the fill", swapped.progress == 0f)
    }
    run {
        val t = DwellTracker(dc.copy(enabled = false))
        val r = t.tick(1000, 0.5f, 0.5f, "a")
        check("disabled dwell never fills", r.progress == 0f && !r.running)
    }
    run {
        val t = DwellTracker(dc.copy(needsTarget = true))
        val r = t.tick(1000, 0.5f, 0.5f, null)
        check("needsTarget with nothing under it does not click", r.progress == 0f && !r.running)
    }
    run {
        val t = DwellTracker(dc)
        var fired = 0
        var lastProgress = 0f
        var tt = 1000L
        while (tt < 4000L) {
            val r = t.tick(tt, 0.5f, 0.5f, "a")
            if (r.fired) fired += 1
            lastProgress = r.progress
            tt += 33L
        }
        check("one dwell clicks once, not repeatedly", fired == 1, "fired=$fired")
        check("standing still keeps the ring full", lastProgress == 1f, "p=$lastProgress")
    }
}

private fun blinkCases() {
    val bc = BlinkCfg()
    println("== blink (close ${bc.close}, open ${bc.open}, ${bc.minMs}..${bc.maxMs}ms) ==")
    fun seq(d: BlinkDecoder, closedMs: Long): List<AirGesture> {
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, 0.05f, true))
        out.addAll(d.feed(1033, 0.9f, true))
        out.addAll(d.feed(1033 + closedMs, 0.05f, true))
        return out
    }
    run {
        val d = BlinkDecoder(bc)
        expect("a normal blink is a gesture", seq(d, 120), listOf(AirGesture.BLINK))
    }
    run {
        val d = BlinkDecoder(bc)
        expect("a 30ms flicker is not a blink", seq(d, 30), emptyList())
    }
    run {
        val d = BlinkDecoder(bc)
        expect("a long squint is not a blink", seq(d, 900), emptyList())
    }
    run {
        val d = BlinkDecoder(bc)
        val out = ArrayList<AirGesture>()
        out.addAll(seq(d, 120))
        d.setCfg(bc)
        out.addAll(d.feed(1300, 0.9f, true))
        out.addAll(d.feed(1420, 0.05f, true))
        expect("the cooldown swallows the second blink", out, listOf(AirGesture.BLINK))
    }
    run {
        val d = BlinkDecoder(bc)
        val out = ArrayList<AirGesture>()
        out.addAll(d.feed(1000, 0.9f, true))
        out.addAll(d.feed(1100, 0.9f, false))
        out.addAll(d.feed(1200, 0.05f, true))
        expect("face lost mid-blink is not a blink", out, emptyList())
    }
}
