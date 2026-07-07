package no.mwm.chess.ui

import androidx.compose.ui.graphics.Color

/** A board colour scheme. Light/dark are the two square colours. */
data class BoardTheme(
    val name: String,
    val light: Color,
    val dark: Color,
)

/** Shared, board-independent highlight colours. Tuned to read on both the
 *  light and the dark squares of every theme (chess squares are never near
 *  black, so a translucent dark marker is always visible). */
object Highlights {
    // Move/highlight tints below are the values lichess ships (verified from
    // lila's board CSS), which are tuned to read on both light and dark squares.
    val selected = Color(0x999BC700)      // strong yellow-green wash on the picked-up piece
    val lastMove = Color(0x699BC700)      // rgba(155,199,0,0.41) — previous move
    val moveDot = Color(0x8014551E)       // rgba(20,85,30,0.5) — empty legal square
    val captureRing = Color(0x59145500)   // rgba(20,85,0,~0.35) — capturable piece
    val checkInner = Color(0xE6E53935)     // red glow centre behind a checked king
    val checkOuter = Color(0x00E53935)     // fades to transparent
    val coordOnLight = Color(0x99000000)
    val coordOnDark = Color(0x99FFFFFF)
}

object BoardThemes {
    val all = listOf(
        BoardTheme("Green", Color(0xFFEEEED2), Color(0xFF769656)),   // chess.com classic green
        BoardTheme("Wood", Color(0xFFF0D9B5), Color(0xFFB58863)),    // lichess brown
        BoardTheme("Blue", Color(0xFFDEE3E6), Color(0xFF8CA2AD)),    // cool slate
        BoardTheme("Slate", Color(0xFFCFD8DC), Color(0xFF546E7A)),   // graphite
    )
}
