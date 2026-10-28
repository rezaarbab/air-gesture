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
import ir.airgesture.model.Flavor
import ir.airgesture.model.SenseMode
import ir.airgesture.svc.AirService

/** Every recognised movement on the left, what it should do on the right. */
@Composable
fun Mapping() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val prox = prefs.mode == SenseMode.PROX || !Flavor.hasHand
    val gestures = remember(prox) {
        AirGesture.entries.filter { if (prox) it.source == ir.airgesture.model.Source.PROX else it.source == ir.airgesture.model.Source.HAND }
    }
    var picked by remember { mutableStateOf<AirGesture?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Panel("نگاشت اشاره‌ها") {
            gestures.forEach { g ->
                Row2(
                    left = g.title,
                    right = prefs.actionFor(g).title,
                    onClick = { picked = g },
                )
            }
            Hint(
                if (prox) "سنسور مجاورت جهت را نمی‌فهمد، پس فقط تعداد و طول پنهان‌شدن دست معنی دارد."
                else "دست را ۳۰ تا ۶۰ سانتی‌متری جلوی دوربین نگه دار؛ نور کم تشخیص را افت می‌دهد.",
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
                                prefs.setAction(g, a)
                                picked = null
                                if (prefs.enabled) AirService.reload(ctx)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                a.title,
                                color = if (a == prefs.actionFor(g)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun Row2(left: String, right: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(left, style = MaterialTheme.typography.bodyLarge)
        Text(right, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
