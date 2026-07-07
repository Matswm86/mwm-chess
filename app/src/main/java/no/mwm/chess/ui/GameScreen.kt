package no.mwm.chess.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwm.chess.engine.Board
import no.mwm.chess.engine.Color
import no.mwm.chess.engine.Piece
import no.mwm.chess.engine.PieceType
import no.mwm.chess.engine.StatusType

@Composable
fun GameScreen(vm: ChessViewModel) {
    val topColor = if (vm.flipped) Color.WHITE else Color.BLACK
    val bottomColor = topColor.opposite

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(vm)

        PlayerBar(vm, side = topColor, isTop = true)
        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            BoardView(vm, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(6.dp))
        PlayerBar(vm, side = bottomColor, isTop = false)

        Spacer(Modifier.height(10.dp))
        StatusLine(vm)
        Spacer(Modifier.height(10.dp))
        Controls(vm)
    }

    vm.pendingPromotion?.let { PromotionDialog(vm) }
    if (vm.status.isOver) GameOverDialog(vm)
}

@Composable
private fun TopBar(vm: ChessViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { vm.backToMenu() }) { Text("‹ Menu") }
        Text(
            "MWM Chess",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = { vm.cycleBoardTheme() }) {
            Text(BoardThemes.all[vm.boardThemeIndex].name)
        }
    }
}

@Composable
private fun PlayerBar(vm: ChessViewModel, side: Color, isTop: Boolean) {
    val label = playerLabel(vm, side)
    val captured = missingPieces(vm.board, side.opposite) // enemies this side removed
    val advantage = materialAdvantage(vm.board, side)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(10.dp))
        CapturedTray(captured)
        if (advantage > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "+$advantage",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (vm.thinking && vm.mode == GameMode.VS_AI && side != vm.humanColor && isTop) {
            Text(
                "thinking…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CapturedTray(pieces: List<Piece>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
        pieces.forEach { p ->
            Image(
                painter = painterResource(pieceDrawable(p)),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(vm: ChessViewModel) {
    val s = vm.status
    val text = when (s.type) {
        StatusType.CHECKMATE -> "Checkmate — ${winnerName(vm, s.winner)} wins"
        StatusType.STALEMATE -> "Stalemate — draw"
        StatusType.DRAW_50 -> "Draw — 50-move rule"
        StatusType.DRAW_MATERIAL -> "Draw — not enough material"
        StatusType.DRAW_REPETITION -> "Draw — threefold repetition"
        StatusType.CHECK -> "${sideName(vm.board.sideToMove)} to move — check!"
        StatusType.ONGOING -> "${sideName(vm.board.sideToMove)} to move"
    }
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = if (s.type == StatusType.CHECK || s.type == StatusType.CHECKMATE)
            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun Controls(vm: ChessViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { vm.undo() }, modifier = Modifier.weight(1f)) { Text("Undo") }
        OutlinedButton(onClick = { vm.flipBoard() }, modifier = Modifier.weight(1f)) { Text("Flip") }
        OutlinedButton(onClick = { vm.toggleCoordinates() }, modifier = Modifier.weight(1f)) {
            Text(if (vm.showCoordinates) "Hide a–h" else "Show a–h")
        }
    }
}

@Composable
private fun PromotionDialog(vm: ChessViewModel) {
    val color = vm.board.sideToMove
    AlertDialog(
        onDismissRequest = { vm.cancelPromotion() },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.cancelPromotion() }) { Text("Cancel") } },
        title = { Text("Promote to") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT,
                ).forEach { type ->
                    Image(
                        painter = painterResource(pieceDrawable(Piece(type, color))),
                        contentDescription = type.name,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.choosePromotion(type) }
                            .padding(4.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun GameOverDialog(vm: ChessViewModel) {
    val s = vm.status
    val title = when (s.type) {
        StatusType.CHECKMATE -> "Checkmate"
        else -> "Game over"
    }
    val body = when (s.type) {
        StatusType.CHECKMATE -> "${winnerName(vm, s.winner)} wins by checkmate."
        StatusType.STALEMATE -> "Stalemate. It's a draw."
        StatusType.DRAW_50 -> "Draw by the 50-move rule."
        StatusType.DRAW_MATERIAL -> "Draw — neither side has enough to mate."
        StatusType.DRAW_REPETITION -> "Draw by threefold repetition."
        else -> ""
    }
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            TextButton(onClick = {
                val choice = if (vm.humanColor == Color.WHITE) ColorChoice.WHITE else ColorChoice.BLACK
                vm.startGame(vm.mode, vm.difficulty, choice)
            }) { Text("Rematch") }
        },
        dismissButton = { TextButton(onClick = { vm.backToMenu() }) { Text("Menu") } },
        title = { Text(title) },
        text = { Text(body) },
    )
}

// ---- helpers ----

private fun playerLabel(vm: ChessViewModel, side: Color): String {
    if (vm.mode == GameMode.TWO_PLAYER) return sideName(side)
    return if (side == vm.humanColor) "You (${sideName(side)})"
    else "Computer · ${vm.difficulty.label}"
}

private fun sideName(c: Color): String = if (c == Color.WHITE) "White" else "Black"

private fun winnerName(vm: ChessViewModel, winner: Color?): String {
    winner ?: return "Nobody"
    if (vm.mode == GameMode.TWO_PLAYER) return sideName(winner)
    return if (winner == vm.humanColor) "You" else "The computer"
}

private val PIECE_VALUE = mapOf(
    PieceType.PAWN to 1, PieceType.KNIGHT to 3, PieceType.BISHOP to 3,
    PieceType.ROOK to 5, PieceType.QUEEN to 9,
)
private val START_COUNT = mapOf(
    PieceType.PAWN to 8, PieceType.KNIGHT to 2, PieceType.BISHOP to 2,
    PieceType.ROOK to 2, PieceType.QUEEN to 1,
)

/** Pieces of [color] that have left the board, biggest first. */
private fun missingPieces(board: Board, color: Color): List<Piece> {
    val counts = HashMap<PieceType, Int>()
    for (sq in 0..63) {
        val p = board.squares[sq] ?: continue
        if (p.color == color) counts[p.type] = (counts[p.type] ?: 0) + 1
    }
    val out = ArrayList<Piece>()
    for ((type, start) in START_COUNT) {
        val have = counts[type] ?: 0
        repeat((start - have).coerceAtLeast(0)) { out.add(Piece(type, color)) }
    }
    return out.sortedByDescending { PIECE_VALUE[it.type] ?: 0 }
}

/** Point lead of [side] over its opponent (captured-material difference). */
private fun materialAdvantage(board: Board, side: Color): Int {
    fun points(color: Color): Int =
        missingPieces(board, color).sumOf { PIECE_VALUE[it.type] ?: 0 }
    // side's advantage = enemy material captured − own material lost
    return points(side.opposite) - points(side)
}
