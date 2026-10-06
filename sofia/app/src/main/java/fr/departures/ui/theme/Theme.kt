package fr.departures.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun c(i: Int) = Color(i)

@Composable
fun DeparturesTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = palette(dark)
    val scheme = if (dark) darkColorScheme(
        primary = c(p.accent), onPrimary = c(p.bg),
        background = c(p.bg), onBackground = c(p.ink),
        surface = c(p.bg), onSurface = c(p.ink),
        surfaceVariant = c(p.surface), onSurfaceVariant = c(p.ink2),
        surfaceContainer = c(p.surface), surfaceContainerLow = c(p.surface), surfaceContainerHigh = c(p.surface),
        outline = c(p.ink3), outlineVariant = c(p.rule),
        error = c(p.warn), tertiary = c(p.now),
    ) else lightColorScheme(
        primary = c(p.accent), onPrimary = Color.White,
        background = c(p.bg), onBackground = c(p.ink),
        surface = c(p.bg), onSurface = c(p.ink),
        surfaceVariant = c(p.surface), onSurfaceVariant = c(p.ink2),
        surfaceContainer = c(p.surface), surfaceContainerLow = c(p.surface), surfaceContainerHigh = c(p.surface),
        outline = c(p.ink3), outlineVariant = c(p.rule),
        error = c(p.warn), tertiary = c(p.now),
    )
    val base = Typography()
    val typography = base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
