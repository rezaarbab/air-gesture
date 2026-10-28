@file:OptIn(ExperimentalMaterial3Api::class)

package ir.airgesture.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ir.airgesture.Air
import ir.airgesture.model.Flavor

/** What actually happened, in the order it happened. This is the screen a bug report needs. */
@Composable
fun Diag() {
    val ctx = LocalContext.current
    val snap by Air.state.collectAsState()
    val lines by Air.lines.collectAsState()
    val clip = LocalClipboardManager.current
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Panel("شمارش‌ها") {
            Line("بیلد", "${Flavor.id} $version")
            Line("سرویس", if (snap.running) "روشن" else "خاموش")
            Line("منبع بالا آمده", if (snap.sourceUp) "بله" else "نه")
            Line("دست دیده می‌شود", if (snap.handVisible) "بله" else "نه")
            Line("فریم بر ثانیه", "${snap.fps}")
            Line("فریم‌های خوانده‌شده", "${snap.frames}")
            Line("اشاره‌های ثبت‌شده", "${snap.gestures}")
            Line("آخرین اشاره", "${snap.lastGesture} → ${snap.lastAction}")
            OutlinedButton(
                onClick = {
                    clip.setText(AnnotatedString(lines.joinToString("\n") { "${Air.stamp(it.t)} ${it.text}" }))
                    Air.note("diag: log copied to clipboard")
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("کپی لاگ") }
        }
        Panel("رویدادها") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (lines.isEmpty()) {
                    Text("چیزی اینجا نیست، یعنی سرویس اصلاً اجرا نشده است.", color = MaterialTheme.colorScheme.error)
                }
                lines.reversed().forEach { l ->
                    Text(
                        "${Air.stamp(l.t)}  ${l.text}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Text("$k: $v", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
}
