package no.mwm.chess.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A restrained, warm-neutral palette that frames the board without competing
// with it. Green accent nods to the classic tournament board.
private val Accent = Color(0xFF6E9553)
private val AccentDark = Color(0xFF88B06A)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF10160C),
    secondary = Color(0xFFCBB994),
    background = Color(0xFF15181C),
    onBackground = Color(0xFFE7E3DA),
    surface = Color(0xFF1E2228),
    onSurface = Color(0xFFE7E3DA),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFBEC6CE),
    outline = Color(0xFF3A424B),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF7A6A4A),
    background = Color(0xFFF3EFE6),
    onBackground = Color(0xFF20242A),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF20242A),
    surfaceVariant = Color(0xFFE7E1D4),
    onSurfaceVariant = Color(0xFF52585F),
    outline = Color(0xFFCBC3B2),
)

@Composable
fun ChessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
