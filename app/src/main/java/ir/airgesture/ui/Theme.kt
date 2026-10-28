package ir.airgesture.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Light theme only, on purpose: this app is used while looking at another app, and a white paper
 * base with one green accent stays readable under bright light where the dark UI turned into a
 * mirror.
 */
private val Paper = Color(0xFFF5F7F8)
private val Card = Color(0xFFFFFFFF)
private val Ink = Color(0xFF12212A)
private val InkSoft = Color(0xFF5C6F7C)
private val Leaf = Color(0xFF12A56C)
private val LeafDeep = Color(0xFF0B7A4E)
private val Sky = Color(0xFF2E7CF6)
private val Amber = Color(0xFFB8860B)
private val Coral = Color(0xFFE0553B)
private val Line = Color(0xFFE2E8EC)

@Composable
fun AirTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = LeafDeep,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9F5E8),
        onPrimaryContainer = LeafDeep,
        secondary = Sky,
        onSecondary = Color.White,
        background = Paper,
        onBackground = Ink,
        surface = Card,
        onSurface = Ink,
        surfaceVariant = Color(0xFFEFF3F5),
        onSurfaceVariant = InkSoft,
        error = Coral,
        onError = Color.White,
        outline = Line,
    )
    val type = Typography().let { base ->
        base.copy(
            headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = Ink),
            bodyLarge = base.bodyLarge.copy(color = Ink),
            bodyMedium = base.bodyMedium.copy(color = Ink),
            bodySmall = base.bodySmall.copy(color = InkSoft),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

val leafGreen: Color get() = Leaf
val deepGreen: Color get() = LeafDeep
val skyBlue: Color get() = Sky
val amberWarn: Color get() = Amber
val coralError: Color get() = Coral
val hairline: Color get() = Line
