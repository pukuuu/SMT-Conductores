package cl.smt.conductores.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SmtDarkColors = darkColorScheme(
    primary = Color(0xFF00C853),
    onPrimary = Color.White,

    secondary = Color(0xFF22C55E),
    onSecondary = Color.White,

    background = Color(0xFF020617),
    onBackground = Color(0xFFF8FAFC),

    surface = Color(0xFF0B1120),
    onSurface = Color(0xFFF8FAFC),

    surfaceVariant = Color(0xFF111827),
    onSurfaceVariant = Color(0xFFF8FAFC),

    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun SMTConductoresTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SmtDarkColors,
        content = content
    )
}