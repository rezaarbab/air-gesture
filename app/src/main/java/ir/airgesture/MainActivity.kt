package ir.airgesture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.airgesture.ui.AirTheme
import ir.airgesture.ui.Diag
import ir.airgesture.ui.Guide
import ir.airgesture.ui.Home
import ir.airgesture.ui.Mapping

class MainActivity : ComponentActivity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContent {
            AirTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root()
                }
            }
        }
    }
}

private enum class Tab(val label: String) { HOME("خانه"), MAP("نگاشت"), DIAG("تشخیص"), GUIDE("راهنما") }

@Composable
fun Root() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "اشاره — کنترل بدون لمس",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            Tab.entries.forEach { t ->
                TextButton(onClick = { tab = t }) {
                    Text(t.label, color = if (t == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when (tab) {
            Tab.HOME -> Home()
            Tab.MAP -> Mapping()
            Tab.DIAG -> Diag()
            Tab.GUIDE -> Guide()
        }
    }
}
