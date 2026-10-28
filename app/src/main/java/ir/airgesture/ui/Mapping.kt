@file:OptIn(ExperimentalMaterial3Api::class)

package ir.airgesture.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.airgesture.Prefs
import ir.airgesture.model.AirAction
import ir.airgesture.model.AirGesture
import ir.airgesture.model.SenseMode
import ir.airgesture.svc.AirService

/** Every movement the current mode can produce, and what it should do. */
@Composable
fun Mapping() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val mode = prefs.mode
    val gestures = remember(mode) { AirGesture.forMode(mode) }
    var picked by remember { mutableStateOf<AirGesture?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Panel("نگاشت — ${mode.title}") {
            gestures.forEach { g ->
                val a = prefs.actionFor(g, mode)
                val def = AirAction.defaultFor(g, mode)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { picked = g }
                        .padding(vertical = 9.dp),
                ) {
                    Text(g.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (a == def) "${a.title} (پیش‌فرض)" else a.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Hint(
                when (mode) {
                    SenseMode.POINTER -> "در حالت نشانگر، «روی نشانگر» یعنی جایی که الان ایستاده؛ اگر حلقه روی دکمه‌ای نشسته باشد، کلیک همان‌جا می‌خورد."
                    SenseMode.GESTURE -> "دست را ۳۰ تا ۶۰ سانتی‌متری جلوی دوربین نگه دار؛ نور کم تشخیص را افت می‌دهد."
                    SenseMode.PROX -> "سنسور جهت را نمی‌فهمد، پس فقط تعداد و طول پنهان‌شدن دست معنی دارد."
                },
            )
        }
    }

    val g = picked
    if (g != null) {
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(g.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AirAction.entries.forEach { a ->
                        TextButton(
                            onClick = {
                                prefs.setAction(g, mode, a)
                                picked = null
                                if (prefs.enabled) AirService.reload(ctx)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                a.title,
                                color = if (a == prefs.actionFor(g, mode)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picked = null }) { Text("بستن") } },
        )
    }
}
