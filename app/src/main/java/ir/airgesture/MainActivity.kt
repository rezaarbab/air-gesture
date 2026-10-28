package ir.airgesture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.airgesture.model.SenseMode
import ir.airgesture.ui.AirTheme
import ir.airgesture.ui.Diag
import ir.airgesture.ui.Guide
import ir.airgesture.ui.Home
import ir.airgesture.ui.Mapping
import ir.airgesture.ui.Segmented
import ir.airgesture.ui.coralError
import ir.airgesture.ui.deepGreen
import ir.airgesture.ui.amberWarn

class MainActivity : ComponentActivity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContent {
            AirTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Root()
                    }
                }
            }
        }
    }
}

private val TABS = listOf("خانه", "نگاشت", "تشخیص", "راهنما")

@Composable
fun Root() {
    var tab by remember { mutableStateOf(0) }
    val snap by Air.state.collectAsState()
    val prefs = remember { Prefs(LocalContext.current) }
    Column(Modifier.fillMaxSize()) {
        Header(snap, prefs.mode)
        Segmented(TABS, tab) { tab = it }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                0 -> Home()
                1 -> Mapping()
                2 -> Diag()
                else -> Guide()
            }
        }
    }
}

@Composable
private fun Header(snap: Air.Snap, mode: SenseMode) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("اشاره", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            val (text, color) = when {
                !snap.running -> "خاموش" to coralError
                !snap.accReady -> "مجوز دسترسی لازم است" to amberWarn
                !snap.sourceUp -> "حسگر بالا نیامده" to amberWarn
                !snap.tracking && mode == SenseMode.POINTER -> "چهره پیدا نیست" to amberWarn
                else -> "آماده — ${mode.title}" to deepGreen
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
                    Spacer(Modifier.size(8.dp))
                    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
        Text(
            if (snap.running) "سر = نشانگر · پینچ = کلیک · پینچ نگه‌داشته = کشیدن" else "روشنش کن و دو پینچ بزن تا نقطهٔ مرجع تنظیم شود",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
