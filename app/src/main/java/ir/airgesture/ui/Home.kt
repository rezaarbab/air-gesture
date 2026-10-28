@file:OptIn(ExperimentalMaterial3Api::class)

package ir.airgesture.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.airgesture.Air
import ir.airgesture.Prefs
import ir.airgesture.inject.AirInputService
import ir.airgesture.model.SenseMode
import ir.airgesture.svc.AirService

@Composable
fun Home() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val snap by Air.state.collectAsState()
    var mode by remember { mutableStateOf(prefs.mode) }
    var gain by remember { mutableStateOf(prefs.pointerGain) }
    var smooth by remember { mutableStateOf(prefs.pointerSmooth) }
    var dwell by remember { mutableStateOf(prefs.dwellMs) }
    var dwellOn by remember { mutableStateOf(prefs.dwellEnabled) }
    var needsTarget by remember { mutableStateOf(prefs.dwellNeedsTarget) }
    var blink by remember { mutableStateOf(prefs.blinkEnabled) }
    var skeleton by remember { mutableStateOf(prefs.showSkeleton) }
    var stride by remember { mutableStateOf(prefs.handStride) }
    var speed by remember { mutableStateOf(prefs.swipeSpeed) }
    var invert by remember { mutableStateOf(prefs.invert) }
    var agility by remember { mutableStateOf(prefs.proxAgility) }
    var rot by remember { mutableStateOf(prefs.rotation) }
    var gpu by remember { mutableStateOf(prefs.gpuDelegate) }
    var fmt by remember { mutableStateOf(prefs.frameFormat) }

    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Air.note(if (granted) "perm: camera granted" else "perm: camera denied, pointer and hand modes cannot run")
        if (granted && snap.running) AirService.reload(ctx)
    }
    val accOk = AirInputService.ready || accessibilityEnabled(ctx)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 26.dp),
    ) {
        SectionCard("وضعیت", "هر خط یک علت مشخص را می‌گوید") {
            StatusLine("سرویس", snap.running, if (snap.running) mode.title else "خاموش است")
            StatusLine(
                "مجوز دسترسی",
                accOk,
                if (AirInputService.ready) "سرویس به سیستم وصل است"
                else if (accOk) "روشن است، هنوز وصل نشده"
                else "در تنظیمات دسترسی «اشاره» را روشن کن",
            )
            StatusLine("دوربین جلو", granted(ctx, Manifest.permission.CAMERA), "برای نشانگر و دست لازم است")
            StatusLine("پنجرهٔ شناور", canOverlay(ctx), "نشانگر، حلقهٔ آب و اسکلت دست")
            StatusLine("استثنای باتری", ignoringBattery(ctx), "رام‌ها سرویس دارای دوربین را می‌بندند")
        }

        SectionCard("حالت کار") {
            Segmented(
                listOf(SenseMode.POINTER.title, SenseMode.GESTURE.title, SenseMode.PROX.title),
                listOf(SenseMode.POINTER, SenseMode.GESTURE, SenseMode.PROX).indexOf(mode),
            ) { i ->
                mode = listOf(SenseMode.POINTER, SenseMode.GESTURE, SenseMode.PROX)[i]
                prefs.mode = mode
                apply(prefs, snap.running, ctx)
            }
            Hint(
                when (mode) {
                    SenseMode.POINTER -> "سر نشانگر را می‌برد؛ پینچ کلیک، پینچ نگه‌داشته + حرکت = کشیدن، دو پینچ = تنظیم نقطهٔ مرجع."
                    SenseMode.GESTURE -> "فقط دست: بالا/پایین، پینچ، دو پینچ، کف باز ثابت."
                    SenseMode.PROX -> "بدون دوربین: یک موج، دو موج، پوشاندن طولانی."
                },
            )
        }

        if (mode == SenseMode.POINTER) {
            SectionCard("نشانگر", "سر تو سرعت است، نه موقعیت") {
                Text("سرعت", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                Segmented(listOf("آرام", "متوسط", "تند", "خیلی تند"), GainSpeeds.indexOf(gain)) { i ->
                    gain = GainSpeeds[i]
                    prefs.pointerGain = gain
                    apply(prefs, snap.running, ctx)
                }
                Text("نرمی", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Segmented(listOf("زیاد", "متوسط", "کم"), Smoothing.indexOfFirst { it.first == smooth }) { i ->
                    smooth = Smoothing[i].first
                    prefs.pointerSmooth = smooth
                    apply(prefs, snap.running, ctx)
                }
                Hint("نرمی زیاد لرزش را می‌گیرد و کمی تاخیر اضافه می‌کند. اگر نشانگر «می‌لرزد»، زیاد را انتخاب کن.")
            }

            SectionCard("کلیک با توقف") {
                ToggleLine("حلقهٔ آب روشن باشد", "اگر روی چیزی بایستی، بعد از مدتی کلیک می‌شود", dwellOn) {
                    dwellOn = it
                    prefs.dwellEnabled = it
                    apply(prefs, snap.running, ctx)
                }
                if (dwellOn) {
                    Spacer(Modifier.size(8.dp))
                    Segmented(listOf("۵۰۰", "۹۰۰", "۱۴۰۰", "۲۰۰۰"), DwellTimes.indexOf(dwell)) { i ->
                        dwell = DwellTimes[i]
                        prefs.dwellMs = dwell
                        apply(prefs, snap.running, ctx)
                    }
                    ToggleLine(
                        "فقط روی دکمهٔ واقعی",
                        "اگر هیچ کنترلی زیر نشانگر نباشد، کلیک نمی‌کند",
                        needsTarget,
                    ) {
                        needsTarget = it
                        prefs.dwellNeedsTarget = it
                        apply(prefs, snap.running, ctx)
                    }
                }
                ToggleLine("پلک هم کلیک باشد", "از blendshape پلکِ مدل چهره", blink) {
                    blink = it
                    prefs.blinkEnabled = it
                    apply(prefs, snap.running, ctx)
                }
                Text("دقت مدل دست", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Segmented(listOf("هر فریم", "یکی در میون", "یکی در سه"), Strides.indexOf(stride)) { i ->
                    stride = Strides[i]
                    prefs.handStride = stride
                    apply(prefs, snap.running, ctx)
                }
                Hint("هر فریم کلیک سریع‌تر، ولی فریم‌рейت پایین‌تر.")
            }
        }

        if (mode == SenseMode.GESTURE) {
            SectionCard("حساسیت دست") {
                Segmented(listOf("آرام", "متوسط", "تند"), SlowSpeeds.indexOf(speed)) { i ->
                    speed = SlowSpeeds[i]
                    prefs.swipeSpeed = speed
                    apply(prefs, snap.running, ctx)
                }
                ToggleLine("عکسی جهت عمودی", "اگر بالا رفتن دست، پایین رفتن را می‌داد", invert) {
                    invert = it
                    prefs.invert = it
                    apply(prefs, snap.running, ctx)
                }
            }
        }

        if (mode == SenseMode.PROX) {
            SectionCard("حساسیت سنسور") {
                Segmented(listOf("تیز", "متوسط", "آرام"), Agility.indexOfFirst { it.first == agility }) { i ->
                    agility = Agility[i].first
                    prefs.proxAgility = agility
                    apply(prefs, snap.running, ctx)
                }
                Hint("موج چقدر کوتاه باشد تا شمرده شود.")
            }
        }

        if (mode != SenseMode.PROX) {
            SectionCard("دوربین و مدل", "اگر هیچ چیز شناسایی نمی‌شود، اول چرخش را عوض کن") {
                Text("چرخش تصویر", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                Segmented(listOf("خودکار", "0", "90", "180", "270"), RotOptions.indexOf(rot)) { i ->
                    rot = RotOptions[i]
                    prefs.rotation = rot
                    apply(prefs, snap.running, ctx)
                }
                Hint("خودکار = (۳۶۰ منهای زاویهٔ سنسور). اگر دست یا چهره هیچ‌وقت پیدا نشد، یکی از ۹۰/۲۷۰ را امتحان کن: مدل کف دست به جهت‌گیری حساس است.")
                Text("فرمت فریم", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Segmented(listOf("JPEG (اثبات‌شده)", "YUV (سریع‌تر)"), if (fmt == Prefs.FMT_YUV) 1 else 0) { i ->
                    fmt = if (i == 1) Prefs.FMT_YUV else Prefs.FMT_JPEG
                    prefs.frameFormat = fmt
                    apply(prefs, snap.running, ctx)
                }
                Hint(
                    "JPEG همان مسیری است که در گوشی تو فریم می‌داد. اگر با YUV در تب تشخیص خط «NO FRAME DELIVERED» " +
                        "دیدی، یعنی HAL این گوشی YUV را روی این سطح استریم نمی‌کند؛ برگردان روی JPEG. " +
                        "اگر «found in 0 of 20» دیدی، فریم می‌آید و مشکل جهت/نور است، نه دوربین.",
                )
                ToggleLine("delegate روی GPU", "سریع‌تر؛ اگر رام رد کند خودش به CPU برمی‌گردد", gpu) {
                    gpu = it
                    prefs.gpuDelegate = it
                    apply(prefs, snap.running, ctx)
                }
                ToggleLine("اسکلت دست روی صفحه", "انیمیشن همان چیزی که مدل می‌بیند", skeleton) {
                    skeleton = it
                    prefs.showSkeleton = it
                    apply(prefs, snap.running, ctx)
                }
            }

            SectionCard("همین حالا") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DwellRing(if (mode == SenseMode.POINTER) snap.dwell else 0f)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        KeyLine("framerate", "${snap.fps}", snap.fps > 12)
                        KeyLine("چهره/دست", snap.probe)
                        KeyLine("نشانگر", "${num(snap.cursorX)} , ${num(snap.cursorY)}")
                        KeyLine("قاب", snap.frameInfo)
                    }
                }
            }
        }

        SectionCard("اجرا") {
            Button(
                onClick = {
                    if (snap.running) {
                        prefs.enabled = false
                        AirService.stop(ctx)
                    } else {
                        prefs.enabled = true
                        if (!accOk) openAccessibility(ctx)
                        if (!granted(ctx, Manifest.permission.CAMERA)) camLauncher.launch(Manifest.permission.CAMERA)
                        AirService.start(ctx)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) { Text(if (snap.running) "خاموش کردن اشاره" else "روشن کردن اشاره") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openAccessibility(ctx) }, modifier = Modifier.weight(1f)) { Text("دسترسی") }
                OutlinedButton(onClick = { camLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) { Text("دوربین") }
                OutlinedButton(onClick = { if (!canOverlay(ctx)) openOverlay(ctx) }, modifier = Modifier.weight(1f)) { Text("پنجره") }
            }
            OutlinedButton(
                onClick = { openBattery(ctx) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("استثنای باتری") }
            Text(
                "آخرین: ${snap.lastGesture} → ${snap.lastAction}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private val GainSpeeds = listOf(1.6f, 2.4f, 3.4f, 4.6f)
private val Smoothing = listOf(0.18f to "زیاد", 0.35f to "متوسط", 0.7f to "کم")
private val DwellTimes = listOf(500L, 900L, 1400L, 2000L)
private val Strides = listOf(1, 2, 3)
private val SlowSpeeds = listOf(0.65f, 0.9f, 1.3f)
private val Agility = listOf(0.7f to "تیز", 1.0f to "متوسط", 1.4f to "آرام")
private val RotOptions = listOf(-1, 0, 90, 180, 270)


private fun apply(prefs: Prefs, running: Boolean, ctx: Context) {
    if (running) AirService.reload(ctx)
}

private fun num(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)

private fun granted(ctx: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun canOverlay(ctx: Context): Boolean = try {
    Settings.canDrawOverlays(ctx)
} catch (e: Exception) {
    false
}

private fun ignoringBattery(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return try {
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) {
        false
    }
}

private fun openAccessibility(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openOverlay(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        Air.note("perm: no overlay settings screen on this ROM")
    }
}

private fun openBattery(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Being listed in enabled_accessibility_services is the whole test. The old check also demanded
 * the master accessibility_enabled flag, which some ROMs leave at 0 while running services -
 * that is the "I gave every permission and it still says I did not" bug, and the running service
 * itself is the better evidence anyway.
 */
fun accessibilityEnabled(ctx: Context): Boolean {
    if (AirInputService.ready) return true
    val expected = ComponentName(ctx, AirInputService::class.java).flattenToShortString()
    val list = Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") ?: return false
    return list.split(":").any { it.equals(expected, ignoreCase = true) }
}
