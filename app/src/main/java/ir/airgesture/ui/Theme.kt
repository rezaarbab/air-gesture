@file:OptIn(ExperimentalMaterial3Api::class)

package ir.airgesture.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api

private val Ink = Color(0xFF0B1013)
private val Panel = Color(0xFF141C21)
private val Accent = Color(0xFF3DDC97)
private val Warn = Color(0xFFFF6F6F)

@Composable
fun AirTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Accent,
        onPrimary = Ink,
        surface = Panel,
        onSurface = Color(0xFFE7F0F3),
        background = Ink,
        onBackground = Color(0xFFE7F0F3),
        error = Warn,
        surfaceVariant = Color(0xFF1C262C),
        onSurfaceVariant = Color(0xFF9FB2BA),
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
