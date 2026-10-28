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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var stride by remember { mutableStateOf(prefs.handStride) }
    var speed by remember { mutableStateOf(prefs.swipeSpeed) }
    var invert by remember { mutableStateOf(prefs.invert) }
    var agility by remember { mutableStateOf(prefs.proxAgility) }

    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Air.note(if (granted) "perm: camera granted" else "perm: camera denied, pointer and hand modes cannot run")
        if (granted && snap.running) AirService.reload(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Panel("وضعیت") {
            StatusRow("سرویس", snap.running, if (snap.running) mode.title else "خاموش")
            StatusRow(
                "مجوز دسترسی (تزریق لمس)",
                AirInputService.ready || accessibilityEnabled(ctx),
                if (AirInputService.ready) "سرویس به سیستم وصل است"
                else if (accessibilityEnabled(ctx)) "روشن است، هنوز وصل نشده"
                else "در تنظیمات دسترسی «اشاره» را روشن کن",
            )
            StatusRow("دوربین جلو", granted(ctx, Manifest.permission.CAMERA), "برای نشانگر و حرکت دست لازم است")
            StatusRow("پنجرهٔ شناور", canOverlay(ctx), "نشانگر، حلقهٔ آب و هایل‌بال")
            StatusRow("استثنای باتری", ignoringBattery(ctx), "رام‌ها سرویس دارای دوربین را می‌بندند")
        }

        Panel("حالت کار") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SenseMode.entries.forEach { m ->
                    Choice(m.shortTitle(), mode == m && snap.running) {
                        mode = m
                        prefs.mode = m
                        apply(prefs, snap.running, ctx)
                    }
                }
            }
            Hint(
                when (mode) {
                    SenseMode.POINTER -> "سر نشانگر را می‌برد، پینچ کلیک است، نگه‌داشتن پینچ و حرکت = کشیدن، دو پینچ = تنظیم نقطهٔ مرجع."
                    SenseMode.GESTURE -> "فقط دست: بالا/پایین، پینچ، دو پینچ، کف باز ثابت."
                    SenseMode.PROX -> "فقط سنسور مجاورت: یک موج، دو موج، پوشاندن طولانی. بدون دوربین."
                },
            )
        }

        if (mode == SenseMode.POINTER) {
            Panel("نشانگر") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GainSpeeds.forEach { g ->
                        Choice(pct(g), gain == g) {
                            gain = g
                            prefs.pointerGain = g
                            apply(prefs, snap.running, ctx)
                        }
                    }
                }
                Hint("سرعت نشانگر نسبت به فاصلهٔ سر از نقطهٔ مرجع.")
                Text("نرمی حرکت", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Smoothing.forEach { s ->
                        Choice(s.second, smooth == s.first) {
                            smooth = s.first
                            prefs.pointerSmooth = s.first
                            apply(prefs, snap.running, ctx)
                        }
                    }
                }
                Hint("نرمی بیشتر لرزش را می‌گیرد و کمی تاخیر اضافه می‌کند.")
                ToggleRow("کلیک با توقف روی چیزها (dwell)", dwellOn) {
                    dwellOn = it
                    prefs.dwellEnabled = it
                    apply(prefs, snap.running, ctx)
                }
                if (dwellOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DwellTimes.forEach { d ->
                            Choice("${d}ms", dwell == d) {
                                dwell = d
                                prefs.dwellMs = d
                                apply(prefs, snap.running, ctx)
                            }
                        }
                    }
                    ToggleRow("فقط روی دکمه‌های واقعی توقف کن", needsTarget) {
                        needsTarget = it
                        prefs.dwellNeedsTarget = it
                        apply(prefs, snap.running, ctx)
                    }
                }
                ToggleRow("پلک هم کلیک باشد", blink) {
                    blink = it
                    prefs.blinkEnabled = it
                    apply(prefs, snap.running, ctx)
                }
                Text("دقت مدل دست (فریم یکی در میون = روان‌تر)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Strides.forEach { n ->
                        Choice("هر $n", stride == n) {
                            stride = n
                            prefs.handStride = n
                            apply(prefs, snap.running, ctx)
                        }
                    }
                }
            }
        }

        if (mode == SenseMode.GESTURE) {
            Panel("حساسیت دست") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SlowSpeeds.forEach { s ->
                        Choice(label(s), speed == s) {
                            speed = s
                            prefs.swipeSpeed = s
                            apply(prefs, snap.running, ctx)
                        }
                    }
                }
                Hint("سرعت لازم دست برای یک اشاره؛ «آرام» اشاره‌های آهسته را هم می‌شمارد ولی خطا بیشتر می‌شود.")
                ToggleRow("عکسی کردن جهت عمودی", invert) {
                    invert = it
                    prefs.invert = it
                    apply(prefs, snap.running, ctx)
                }
            }
        }

        if (mode == SenseMode.PROX) {
            Panel("حساسیت سنسور") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Agility.forEach { a ->
                        Choice(a.second, agility == a.first) {
                            agility = a.first
                            prefs.proxAgility = a.first
                            apply(prefs, snap.running, ctx)
                        }
                    }
                }
                Hint("موج چقدر کوتاه باشد تا شمرده شود.")
            }
        }

        Panel("اجرا") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    if (snap.running) {
                        prefs.enabled = false
                        AirService.stop(ctx)
                    } else {
                        prefs.enabled = true
                        if (!accessibilityEnabled(ctx)) openAccessibility(ctx)
                        if (!granted(ctx, Manifest.permission.CAMERA)) camLauncher.launch(Manifest.permission.CAMERA)
                        AirService.start(ctx)
                    }
                }) { Text(if (snap.running) "خاموش کردن" else "روشن کردن اشاره") }
                OutlinedButton(onClick = { openAccessibility(ctx) }) { Text("تنظیمات دسترسی") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = { if (!canOverlay(ctx)) openOverlay(ctx) }) { Text("مجوز پنجره") }
                OutlinedButton(onClick = { camLauncher.launch(Manifest.permission.CAMERA) }) { Text("مجوز دوربین") }
                OutlinedButton(onClick = { openBattery(ctx) }) { Text("استثنای باتری") }
            }
            Text(
                "نشانگر ${pct(snap.cursorX)} , ${pct(snap.cursorY)} · حلقه ${pct(snap.dwell)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "آخرین: ${snap.lastGesture} → ${snap.lastAction}",
                style = MaterialTheme.typography.bodyMedium,
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

private fun SenseMode.shortTitle(): String = when (this) {
    SenseMode.POINTER -> "نشانگر"
    SenseMode.GESTURE -> "دست"
    SenseMode.PROX -> "سنسور"
}

private fun label(s: Float) = when (s) {
    0.65f -> "آرام"
    0.9f -> "متوسط"
    else -> "تند"
}

private fun pct(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)

private fun apply(prefs: Prefs, running: Boolean, ctx: Context) {
    if (running) AirService.reload(ctx)
}

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

/** Reads the system list, so the answer is right even before our service object exists. */
fun accessibilityEnabled(ctx: Context): Boolean {
    val expected = ComponentName(ctx, AirInputService::class.java).flattenToShortString()
    val list = Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") ?: return false
    val on = list.split(":").any { it.equals(expected, ignoreCase = true) }
    val master = Settings.Secure.getInt(ctx.contentResolver, "accessibility_enabled", 0)
    return on && master == 1
}
