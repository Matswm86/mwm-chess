package no.mwm.chess.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import no.mwm.chess.R

/**
 * The "royal wood & gold" visual language from the new design (Chess.dc.html):
 * a dark-green felt table, a carved-wood + gilt board frame, cream ivory type in
 * Cinzel. All chrome colours live here so screens stay declarative.
 */
object Design {
    // Table / background
    val bgTop = Color(0xFF22423F)
    val bgMid = Color(0xFF12312E)
    val bgLow = Color(0xFF0A1917)
    val bgDeep = Color(0xFF060F0E)

    // Gold / brass
    val gold = Color(0xFFC79A4A)
    val goldLight = Color(0xFFF4D178)
    val goldDark = Color(0xFFA9772F)
    val goldText = Color(0xFFE6BD62)

    // Wood (top bar, panels, buttons)
    val woodHi = Color(0xFF835A2B)
    val woodMid = Color(0xFF5C3C1A)
    val woodLow = Color(0xFF492E13)
    val panelHi = Color(0xFF3A2614)
    val panelLow = Color(0xFF291A0C)
    val buttonHi = Color(0xFF8F5F2C)
    val buttonLow = Color(0xFF4A2F14)

    // Ivory / parchment text
    val cream = Color(0xFFF0E2C0)
    val creamBright = Color(0xFFF7E7B8)
    val muted = Color(0xFFD9C48D)

    // Semantic accents
    val good = Color(0xFF8FD8A2)
    val danger = Color(0xFFEF8A7E)

    val scrim = Color(0xCC060C0B)

    /** The felt-table radial glow used as the whole-screen backdrop. */
    val background: Brush = Brush.radialGradient(
        colors = listOf(bgTop, bgMid, bgLow, bgDeep),
        // pushed above the top edge like the design's 50% -5% radial origin
        center = androidx.compose.ui.geometry.Offset(0.5f, -0.05f),
        radius = 1600f,
    )

    val topBar: Brush = Brush.verticalGradient(listOf(woodHi, woodMid, woodLow))
    val panel: Brush = Brush.verticalGradient(listOf(panelHi, panelLow))
    val button: Brush = Brush.verticalGradient(listOf(buttonHi, buttonLow))
    val goldRim: Brush = Brush.linearGradient(listOf(goldLight, goldDark))

    val panelShape: Shape = RoundedCornerShape(16.dp)
    val buttonShape: Shape = RoundedCornerShape(14.dp)
}

/** Cinzel (OFL) is a variable font; expose the weights the design uses. */
@OptIn(ExperimentalTextApi::class)
val Cinzel = FontFamily(
    Font(R.font.cinzel_variable, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cinzel_variable, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cinzel_variable, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cinzel_variable, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
