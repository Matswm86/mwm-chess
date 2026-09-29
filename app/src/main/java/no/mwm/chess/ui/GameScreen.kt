package no.mwm.chess.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwm.chess.engine.Color
import no.mwm.chess.engine.StatusType
import no.mwm.chess.engine.PieceType
import no.mwm.chess.engine.ai.Difficulty
import no.mwm.chess.ui.theme.Cinzel
import no.mwm.chess.ui.theme.Design

@Composable
fun GameScreen(vm: ChessViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    // Leaving a running online game hands the win to the friend, so ask first.
    val goHome: () -> Unit = {
        if (vm.mode == GameMode.ONLINE && vm.onlinePhase == OnlinePhase.PLAYING && !vm.isGameOver) {
            confirmLeave = true
        } else {
            vm.backToMenu()
        }
    }

    // The phone's back button closes the open panel first, then returns to the main menu.
    BackHandler {
        when {
            showSettings -> showSettings = false
            confirmLeave -> confirmLeave = false
            vm.pendingPromotion != null -> vm.cancelPromotion()
            else -> goHome()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Design.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(vm, onHome = goHome, onSettings = { showSettings = true })
            TurnStrip(vm)
            vm.onlineError?.let { OnlineNotice(it) { vm.clearOnlineError() } }
            Board3DView(vm, Modifier.weight(1f).fillMaxWidth())
            ActionBar(vm)
        }

        if (showSettings) SettingsPopover(vm, onDismiss = { showSettings = false })
        if (vm.pendingPromotion != null) PromotionSheet(vm)
        if (vm.isGameOver) {
            GameOverOverlay(vm)
        } else if (vm.mode == GameMode.ONLINE && vm.codeCardOpen) {
            CodeCard(vm)
        }
        if (confirmLeave) {
            LeaveDialog(
                onStay = { confirmLeave = false },
                onLeave = { confirmLeave = false; vm.backToMenu() },
            )
        }
    }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(vm: ChessViewModel, onHome: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Design.topBar)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(glyph = "⌂", size = 46.dp, onClick = onHome)
        Spacer(Modifier.width(10.dp))
        KingAvatar(thinking = vm.thinking || vm.reconnecting)
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (vm.mode == GameMode.ONLINE) {
                Text(
                    "ONLINE",
                    fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    letterSpacing = 3.sp, color = Design.creamBright,
                )
                Text(
                    "GAME ${vm.onlineCode ?: ""}",
                    fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    letterSpacing = 4.sp, color = Design.goldText,
                )
            } else {
                Text(
                    "LEVEL ${vm.difficulty.ordinal + 1}",
                    fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    letterSpacing = 3.sp, color = Design.creamBright,
                )
                Text(
                    vm.difficulty.label.uppercase(),
                    fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    letterSpacing = 4.sp, color = Design.goldText,
                )
            }
        }
        CircleButton(glyph = "⚙", size = 46.dp, onClick = onSettings)
    }
}

@Composable
private fun KingAvatar(thinking: Boolean) {
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Design.goldRim)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(androidx.compose.ui.graphics.Color(0xFF43325A), androidx.compose.ui.graphics.Color(0xFF1B1130)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("♚", fontSize = 24.sp, color = Design.cream)
        }
        if (thinking) {
            CircularProgressIndicator(
                Modifier.fillMaxSize(),
                color = Design.goldLight,
                strokeWidth = 2.5.dp,
            )
        }
    }
}

// ---------------------------------------------------------------- turn strip

@Composable
private fun TurnStrip(vm: ChessViewModel) {
    val (text, color) = turnLabel(vm)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            letterSpacing = 1.5.sp, color = color,
        )
        val adv = materialLead(vm)
        if (adv != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                adv,
                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                color = Design.good,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(androidx.compose.ui.graphics.Color(0x991C3C32))
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- action bar

@Composable
private fun ActionBar(vm: ChessViewModel) {
    if (vm.mode == GameMode.ONLINE) {
        OnlineActionBar(vm)
        return
    }
    val canUndo = vm.canUndo && !vm.thinking && !vm.isGameOver
    val canHint = !vm.hinting && !vm.thinking && !vm.isGameOver &&
        (vm.mode != GameMode.VS_AI || vm.board.sideToMove == vm.humanColor)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF123331), androidx.compose.ui.graphics.Color(0xFF0A1F1D))))
            .padding(top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionColumn("↶", "UNDO", enabled = canUndo) { vm.undo() }
        ActionColumn(if (vm.hinting) "⋯" else "✦", "HINT", enabled = canHint) { vm.requestHint() }
        ActionColumn("↻", "RESTART", enabled = true) {
            val choice = if (vm.humanColor == Color.WHITE) ColorChoice.WHITE else ColorChoice.BLACK
            vm.startGame(vm.mode, vm.difficulty, choice)
        }
        ActionColumn("⚑", "RESIGN", enabled = !vm.isGameOver) { vm.resign() }
    }
}

@Composable
private fun OnlineActionBar(vm: ChessViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF123331), androidx.compose.ui.graphics.Color(0xFF0A1F1D))))
            .padding(top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionColumn("⇅", "FLIP", enabled = true) { vm.flipBoard() }
        ActionColumn("⌂", "LEAVE", enabled = true) { vm.backToMenu() }
        ActionColumn("⚑", "RESIGN", enabled = !vm.isGameOver && vm.onlinePhase == OnlinePhase.PLAYING) { vm.resign() }
    }
}

/** A problem with the online game, shown under the turn line; tap to hide it. */
@Composable
private fun OnlineNotice(text: String, onDismiss: () -> Unit) {
    Text(
        text,
        fontFamily = Cinzel, fontSize = 12.sp, color = Design.danger, textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismiss() }
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

/**
 * The game code in big letters. It stays on screen until the player taps Continue, also after
 * the friend has joined, so there is time to read and share it.
 */
@Composable
private fun CodeCard(vm: ChessViewModel) {
    val context = LocalContext.current
    val code = vm.onlineCode ?: ""
    val waiting = vm.onlinePhase == OnlinePhase.WAITING
    val side = if (vm.humanColor == Color.WHITE) "White" else "Black"
    Box(
        Modifier.fillMaxSize().background(Design.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Design.panel)
                .border(1.5.dp, Design.gold, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (vm.joinedByCode) "YOU JOINED GAME" else "YOUR GAME CODE",
                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp,
                color = Design.goldText,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                code,
                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 10.sp,
                color = Design.creamBright,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    waiting ->
                        "Your friend opens MWM Chess, picks \"Play a friend online\" and types this code. " +
                            "You play $side."
                    vm.joinedByCode -> "You are in. You play $side."
                    else -> "Your friend has joined. You play $side."
                },
                fontFamily = Cinzel, fontSize = 12.sp, color = Design.muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (waiting) {
                CircularProgressIndicator(Modifier.size(26.dp), color = Design.goldLight, strokeWidth = 2.5.dp)
                Spacer(Modifier.height(16.dp))
                CardButton("SHARE CODE") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Play chess with me! Open MWM Chess, pick \"Play a friend online\" and enter the code $code",
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share game code"))
                }
                Spacer(Modifier.height(10.dp))
            }
            CardButton("CONTINUE") { vm.dismissCodeCard() }
            if (waiting) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Cancel",
                    fontFamily = Cinzel, fontSize = 13.sp, color = Design.muted,
                    modifier = Modifier.clickable { vm.backToMenu() }.padding(6.dp),
                )
            }
        }
    }
}

/** Asks before leaving a running online game, because leaving gives the friend the win. */
@Composable
private fun LeaveDialog(onStay: () -> Unit, onLeave: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Design.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(290.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Design.panel)
                .border(1.5.dp, Design.gold, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LEAVE THE GAME?",
                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp,
                color = Design.creamBright,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Your friend wins if you leave now.",
                fontFamily = Cinzel, fontSize = 12.sp, color = Design.muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            CardButton("KEEP PLAYING", onClick = onStay)
            Spacer(Modifier.height(10.dp))
            Text(
                "Leave to main menu",
                fontFamily = Cinzel, fontSize = 13.sp, color = Design.muted,
                modifier = Modifier.clickable { onLeave() }.padding(6.dp),
            )
        }
    }
}

/** The wide gold button used on the in-game cards. */
@Composable
private fun CardButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF9A6730), androidx.compose.ui.graphics.Color(0xFF5A3A19))))
            .border(1.5.dp, Design.gold, RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp,
            color = Design.creamBright,
        )
    }
}

@Composable
private fun ActionColumn(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircleButton(glyph = glyph, size = 60.dp, enabled = enabled, onClick = onClick)
        Spacer(Modifier.height(7.dp))
        Text(
            label, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
            letterSpacing = 1.5.sp, color = Design.muted,
        )
    }
}

@Composable
private fun CircleButton(glyph: String, size: androidx.compose.ui.unit.Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xFF8F5F2C), androidx.compose.ui.graphics.Color(0xFF4A2F14)),
                ),
            )
            .border(2.5.dp, Design.gold, CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontFamily = Cinzel,
            fontSize = (size.value * 0.42f).sp,
            color = if (enabled) Design.creamBright else Design.muted.copy(alpha = 0.4f),
        )
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsPopover(vm: ChessViewModel, onDismiss: () -> Unit) {
    val scrimSource = remember { MutableInteractionSource() }
    val panelSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Design.scrim)
            .clickable(interactionSource = scrimSource, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 66.dp, end = 14.dp)
                .width(220.dp)
                .clip(Design.panelShape)
                .background(Design.panel)
                .border(1.5.dp, Design.gold, Design.panelShape)
                // swallow taps inside the panel
                .clickable(interactionSource = panelSource, indication = null) {},
        ) {
            if (vm.mode != GameMode.ONLINE) {
                PanelHeader("DIFFICULTY")
                Difficulty.entries.forEach { d ->
                    SettingRow(
                        label = d.label,
                        active = d == vm.difficulty,
                        onClick = { vm.changeDifficulty(d) },
                    )
                }
            }
            PanelHeader("BOARD")
            SettingRow("Flip board", active = false) { vm.flipBoard() }
            SettingRow(if (vm.soundOn) "Sound: on" else "Sound: off", active = vm.soundOn) { vm.toggleSound() }
            SettingRow("Main menu", active = false) { onDismiss(); vm.backToMenu() }
        }
    }
}

@Composable
private fun PanelHeader(text: String) {
    Text(
        text,
        fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp,
        color = Design.goldText,
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingRow(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (active) androidx.compose.ui.graphics.Color(0x29C79A4A) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (active) Design.creamBright else Design.muted, modifier = Modifier.weight(1f),
        )
        if (active) Text("✓", color = Design.good, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------- promotion

@Composable
private fun PromotionSheet(vm: ChessViewModel) {
    Box(
        Modifier.fillMaxSize().background(Design.scrim),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Design.panel)
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "PROMOTE PAWN",
                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp,
                color = Design.goldText,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    PieceType.QUEEN to "♛", PieceType.ROOK to "♜",
                    PieceType.BISHOP to "♝", PieceType.KNIGHT to "♞",
                ).forEach { (type, glyph) ->
                    Box(
                        Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF5C3D1E), androidx.compose.ui.graphics.Color(0xFF3A2410))))
                            .border(2.dp, Design.gold, RoundedCornerShape(12.dp))
                            .clickable { vm.choosePromotion(type) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(glyph, fontSize = 34.sp, color = Design.creamBright)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- game over

@Composable
private fun GameOverOverlay(vm: ChessViewModel) {
    val outcome = gameOutcome(vm)
    val online = vm.mode == GameMode.ONLINE
    Box(
        Modifier.fillMaxSize().background(Design.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(290.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Design.panel)
                .border(1.5.dp, Design.gold, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(outcome.icon, fontSize = 40.sp, color = outcome.color)
            Spacer(Modifier.height(6.dp))
            Text(
                outcome.title,
                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 3.sp,
                color = outcome.color,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                outcome.subtitle,
                fontFamily = Cinzel, fontSize = 12.sp, letterSpacing = 1.sp, color = Design.muted,
                textAlign = TextAlign.Center,
            )
            if (online) {
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        vm.onlineEndReason == "left" -> "Your friend left the game."
                        vm.opponentWantsRematch -> "Your friend wants a rematch. Colours swap."
                        !vm.opponentOnline -> "Your friend is offline."
                        else -> "Rematch swaps colours."
                    },
                    fontFamily = Cinzel, fontSize = 12.sp, color = Design.goldText, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF9A6730), androidx.compose.ui.graphics.Color(0xFF5A3A19))))
                    .border(1.5.dp, Design.gold, RoundedCornerShape(11.dp))
                    .clickable(enabled = !(online && vm.youWantRematch)) {
                        if (online) {
                            vm.requestRematch()
                        } else {
                            val choice = if (vm.humanColor == Color.WHITE) ColorChoice.WHITE else ColorChoice.BLACK
                            vm.startGame(vm.mode, vm.difficulty, choice)
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        !online -> "NEW GAME"
                        vm.youWantRematch -> "WAITING FOR FRIEND…"
                        else -> "REMATCH"
                    },
                    fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp,
                    color = Design.creamBright,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Main menu",
                fontFamily = Cinzel, fontSize = 13.sp, color = Design.muted,
                modifier = Modifier.clickable { vm.backToMenu() }.padding(6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun turnLabel(vm: ChessViewModel): Pair<String, androidx.compose.ui.graphics.Color> {
    if (vm.isGameOver) return "Game over" to Design.muted
    if (vm.thinking) return "Opponent is thinking…" to Design.goldText
    val inCheck = vm.status.type == StatusType.CHECK
    if (vm.mode == GameMode.ONLINE) {
        return when {
            vm.onlinePhase != OnlinePhase.PLAYING -> "Waiting for your friend to join" to Design.muted
            vm.reconnecting -> "Reconnecting…" to Design.danger
            !vm.opponentOnline -> "Your friend is offline, waiting…" to Design.danger
            vm.board.sideToMove == vm.humanColor ->
                if (inCheck) "Check — your move" to Design.danger else "Your move" to Design.cream
            else -> "Friend's move…" to Design.muted
        }
    }
    return if (vm.mode == GameMode.VS_AI) {
        if (vm.board.sideToMove == vm.humanColor) {
            if (inCheck) "Check — your move" to Design.danger else "Your move" to Design.cream
        } else "Opponent to move" to Design.muted
    } else {
        val side = if (vm.board.sideToMove == Color.WHITE) "White" else "Black"
        if (inCheck) "$side — check" to Design.danger else "$side to move" to Design.cream
    }
}

private val VAL = mapOf(
    PieceType.PAWN to 1, PieceType.KNIGHT to 3, PieceType.BISHOP to 3,
    PieceType.ROOK to 5, PieceType.QUEEN to 9,
)
private val START = mapOf(
    PieceType.PAWN to 8, PieceType.KNIGHT to 2, PieceType.BISHOP to 2,
    PieceType.ROOK to 2, PieceType.QUEEN to 1,
)

private fun points(vm: ChessViewModel, color: Color): Int {
    val have = HashMap<PieceType, Int>()
    for (sq in 0..63) {
        val p = vm.board.squares[sq] ?: continue
        if (p.color == color) have[p.type] = (have[p.type] ?: 0) + 1
    }
    var lost = 0
    for ((t, n) in START) lost += (n - (have[t] ?: 0)).coerceAtLeast(0) * (VAL[t] ?: 0)
    return lost
}

/** Material lead of the human (VS_AI) or White (two-player), or null if even. */
private fun materialLead(vm: ChessViewModel): String? {
    val me = if (vm.mode == GameMode.TWO_PLAYER) Color.WHITE else vm.humanColor
    val lead = points(vm, me.opposite) - points(vm, me)
    return if (lead > 0) "+$lead" else null
}

private class Outcome(val title: String, val subtitle: String, val icon: String, val color: androidx.compose.ui.graphics.Color)

private fun gameOutcome(vm: ChessViewModel): Outcome {
    val s = vm.status
    if (vm.mode == GameMode.ONLINE) {
        val w = vm.onlineWinner
        if (w != null && !s.isOver) {
            val won = w == vm.humanColor
            return when {
                vm.onlineEndReason == "left" -> Outcome("VICTORY", "Your friend left the game", "♚", Design.good)
                won -> Outcome("VICTORY", "Your friend resigned", "♚", Design.good)
                else -> Outcome("DEFEAT", "You resigned the game", "♟", Design.danger)
            }
        }
        if (s.type == StatusType.CHECKMATE) {
            return if (s.winner == vm.humanColor) Outcome("VICTORY", "Checkmate — well played", "♚", Design.good)
            else Outcome("DEFEAT", "Checkmate", "♟", Design.danger)
        }
    }
    if (vm.resigned && !s.isOver) {
        return if (vm.mode == GameMode.VS_AI) {
            Outcome("DEFEAT", "You resigned the game", "♟", Design.danger)
        } else {
            val loser = if (vm.board.sideToMove == Color.WHITE) "White" else "Black"
            val winner = if (vm.board.sideToMove == Color.WHITE) "Black" else "White"
            Outcome("RESIGNED", "$loser resigns — $winner wins", "⚑", Design.goldText)
        }
    }
    val humanWon = s.winner != null && (vm.mode != GameMode.VS_AI || s.winner == vm.humanColor)
    return when (s.type) {
        StatusType.CHECKMATE ->
            if (vm.mode == GameMode.VS_AI) {
                if (humanWon) Outcome("VICTORY", "Checkmate — well played", "♚", Design.good)
                else Outcome("DEFEAT", "Checkmate", "♟", Design.danger)
            } else {
                val w = if (s.winner == Color.WHITE) "White" else "Black"
                Outcome("CHECKMATE", "$w wins", "♚", Design.goldText)
            }
        StatusType.STALEMATE -> Outcome("DRAW", "Stalemate — no legal moves", "½", Design.goldText)
        StatusType.DRAW_50 -> Outcome("DRAW", "50-move rule", "½", Design.goldText)
        StatusType.DRAW_MATERIAL -> Outcome("DRAW", "Not enough material", "½", Design.goldText)
        StatusType.DRAW_REPETITION -> Outcome("DRAW", "Threefold repetition", "½", Design.goldText)
        else -> Outcome("GAME OVER", "", "♚", Design.goldText)
    }
}
