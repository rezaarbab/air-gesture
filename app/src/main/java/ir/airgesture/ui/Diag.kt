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
import ir.airgesture.Prefs

/** What actually happened, in the order it happened. This is the screen a bug report needs. */
@Composable
fun Diag() {
    val ctx = LocalContext.current
    val snap by Air.state.collectAsState()
    val lines by Air.lines.collectAsState()
    val clip = LocalClipboardManager.current
    val mode = remember { Prefs(ctx).mode }
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
            .padding(bottom = 26.dp),
    ) {
        SectionCard("شمارش‌ها", "اگر جایی صفر است، آن مسیر اجرا نشده") {
            KeyLine("نسخه", version)
            KeyLine("حالت", mode.title)
            KeyLine("سرویس", if (snap.running) "روشن" else "خاموش")
            KeyLine("دسترسی", if (snap.accReady) "وصل" else "وصل نیست", snap.accReady)
            KeyLine("منبع", if (snap.sourceUp) "بالا آمده" else "بالا نیامده", snap.sourceUp)
            KeyLine("قاب / چرخش", if (snap.frameInfo.isEmpty()) "—" else snap.frameInfo)
            KeyLine("چهره", if (snap.faceVisible) "دیده می‌شود" else "نیست", snap.faceVisible)
            KeyLine("دست", if (snap.handVisible) "دیده می‌شود" else "نیست", snap.handVisible)
            KeyLine("framerate", "${snap.fps}", snap.fps > 12)
            KeyLine("مدل‌ها", "face ${ms(snap.faceMs)} · hand ${ms(snap.handMs)} ms")
            KeyLine("دیدن/فریم", if (snap.probe.isEmpty()) "—" else snap.probe, snap.probe.contains("/"))
            KeyLine("نشانگر", "${num(snap.cursorX)} , ${num(snap.cursorY)}  (حرکت کرد: ${if (snap.moved) "بله" else "نه"})")
            KeyLine("حلقه", "${(snap.dwell * 100).toInt()}%  «${snap.target}»")
            KeyLine("فریم‌ها", "${snap.frames}")
            KeyLine("اشاره‌ها", "${snap.gestures}")
            OutlinedButton(
                onClick = {
                    clip.setText(AnnotatedString(lines.joinToString("\n") { "${Air.stamp(it.t)} ${it.text}" }))
                    Air.note("diag: log copied to clipboard")
                },
                modifier = Modifier.padding(top = 10.dp),
            ) { Text("کپی لاگ") }
        }

        SectionCard("رویدادها") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (lines.isEmpty()) {
                    Text("چیزی اینجا نیست، یعنی سرویس اصلاً اجرا نشده است.", color = coralError)
                }
                lines.reversed().forEach { l ->
                    Text(
                        "${Air.stamp(l.t)}  ${l.text}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun ms(v: Float): String = String.format(java.util.Locale.US, "%.1f", v)

private fun num(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)
