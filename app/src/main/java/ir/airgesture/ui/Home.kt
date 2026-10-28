@file:OptIn(ExperimentalMaterial3Api::class)

package ir.airgesture.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.airgesture.Air
import ir.airgesture.Prefs
import ir.airgesture.inject.AirInputService
import ir.airgesture.model.Flavor
import ir.airgesture.model.SenseMode
import ir.airgesture.svc.AirService
import androidx.compose.ui.platform.LocalContext

@Composable
fun Home() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val snap by Air.state.collectAsState()
    var mode by remember { mutableStateOf(prefs.mode) }
    var invert by remember { mutableStateOf(prefs.invert) }
    var hud by remember { mutableStateOf(prefs.hud) }
    var speed by remember { mutableStateOf(prefs.swipeSpeed) }

    val camLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Air.note(if (granted) "perm: camera granted" else "perm: camera denied, hand mode cannot run")
        if (granted && snap.running) AirService.reload(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Panel("وضعیت") {
            StatusRow("سرویس", snap.running, if (snap.running) "در حال خواندن حرکت" else "خاموش")
            StatusRow(
                "مجوز دسترسی (تزریق لمس)",
                AirInputService.ready || accessibilityEnabled(ctx),
                if (AirInputService.ready) "AirInputService به سیستم وصل است"
                else if (accessibilityEnabled(ctx)) "روشن است، هنوز وصل نشده"
                else "در تنظیمات دسترسی «اشاره» را روشن کن",
            )
            if (Flavor.hasHand) {
                StatusRow(
                    "دوربین جلو",
                    granted(ctx, Manifest.permission.CAMERA),
                    if (granted(ctx, Manifest.permission.CAMERA)) "دسترسی باز است" else "برای حالت دست لازم است",
                )
            }
            StatusRow("پنجرهٔ شناور (HUD)", canOverlay(ctx), "نمایش آخرین اشاره روی صفحهٔ اپ دیگر")
            StatusRow("استثنای باتری", ignoringBattery(ctx), "بعضی رام‌ها سرویس دوربین را می‌کشند")
        }

        Panel("منبع تشخیص") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Choice("دوربین + دست", mode == SenseMode.HAND && Flavor.hasHand) {
                    if (!Flavor.hasHand) {
                        Air.note("mode: this APK has no hand tracker, pick proximity")
                    } else {
                        mode = SenseMode.HAND
                        prefs.mode = mode
                        apply(prefs, snap.running, ctx)
                    }
                }
                Choice("سنسور مجاورت", mode == SenseMode.PROX) {
                    mode = SenseMode.PROX
                    prefs.mode = mode
                    apply(prefs, snap.running, ctx)
                }
            }
            Hint(
                if (mode == SenseMode.HAND) {
                    "دوربین جلو دست را می‌بیند: بالا/پایین، پینچ، دو پینچ. دقیق‌تر، ولی باتری و چراغ دوربین."
                } else {
                    "سنسور مجاورت فقط «پوشاندن و برداشتن دست» را می‌فهمد: یک موج، دو موج، پوشاندن طولانی."
                },
            )
        }

        Panel("حساسیت") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlowSpeeds.forEach { s ->
                    Choice(label(s), speed == s) {
                        speed = s
                        prefs.swipeSpeed = s
                        apply(prefs, snap.running, ctx)
                    }
                }
            }
            Hint("سرعت لازم دست برای یک اشاره. «آرام‌تر» یعنی اشاره‌های آهسته هم شمرده می‌شوند ولی خطا هم بیشتر می‌شود.")
            ToggleRow("عکسی کردن جهت عمودی", invert) {
                invert = it
                prefs.invert = it
                apply(prefs, snap.running, ctx)
            }
            ToggleRow("نمایش هایل‌بال روی صفحه", hud) {
                hud = it
                prefs.hud = it
                apply(prefs, snap.running, ctx)
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
                        if (mode == SenseMode.HAND && !granted(ctx, Manifest.permission.CAMERA)) {
                            camLauncher.launch(Manifest.permission.CAMERA)
                        }
                        AirService.start(ctx)
                    }
                }) {
                    Text(if (snap.running) "خاموش کردن" else "روشن کردن اشاره")
                }
                OutlinedButton(onClick = { openAccessibility(ctx) }) { Text("تنظیمات دسترسی") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = { if (!canOverlay(ctx)) openOverlay(ctx) }) { Text("مجوز پنجره") }
                OutlinedButton(onClick = { if (Flavor.hasHand) camLauncher.launch(Manifest.permission.CAMERA) }) { Text("مجوز دوربین") }
                OutlinedButton(onClick = { openBattery(ctx) }) { Text("استثنای باتری") }
            }
            Text(
                "آخرین: ${snap.lastGesture} → ${snap.lastAction}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private val SlowSpeeds = listOf(0.65f, 0.9f, 1.3f)

private fun label(s: Float) = when (s) {
    0.65f -> "آرام"
    0.9f -> "متوسط"
    else -> "تند"
}

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
