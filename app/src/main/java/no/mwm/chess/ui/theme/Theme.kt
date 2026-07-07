package no.mwm.chess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * The app commits to a single dark "royal wood" look in both system themes, so the
 * felt table and gilt board always read the same. Screens paint [Design.background]
 * themselves; this scheme just tints any stray Material text.
 */
private val Scheme = darkColorScheme(
    primary = Design.gold,
    onPrimary = Color(0xFF201203),
    secondary = Design.goldText,
    background = Design.bgLow,
    onBackground = Design.cream,
    surface = Design.panelHi,
    onSurface = Design.cream,
    surfaceVariant = Design.panelLow,
    onSurfaceVariant = Design.muted,
    outline = Design.gold,
)

@Composable
fun ChessTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val cinzel = TextStyle(fontFamily = Cinzel)
    val typography = Typography(
        displayLarge = base.displayLarge.merge(cinzel),
        headlineMedium = base.headlineMedium.merge(cinzel),
        titleLarge = base.titleLarge.merge(cinzel),
        titleMedium = base.titleMedium.merge(cinzel),
        bodyLarge = base.bodyLarge.merge(cinzel),
        bodyMedium = base.bodyMedium.merge(cinzel),
        labelLarge = base.labelLarge.merge(cinzel),
    )
    MaterialTheme(colorScheme = Scheme, typography = typography, content = content)
}
