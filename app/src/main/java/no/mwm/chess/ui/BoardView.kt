package no.mwm.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwm.chess.engine.fileOf
import no.mwm.chess.engine.rankOf
import no.mwm.chess.engine.squareOf

@Composable
fun BoardView(vm: ChessViewModel, modifier: Modifier = Modifier) {
    val theme = BoardThemes.all[vm.boardThemeIndex]
    Column(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        for (row in 0..7) {
            Row(Modifier.weight(1f)) {
                for (col in 0..7) {
                    val sq = displaySquare(row, col, vm.flipped)
                    SquareCell(vm, theme, sq, row, col, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

private fun displaySquare(row: Int, col: Int, flipped: Boolean): Int {
    val rank = if (flipped) row else 7 - row
    val file = if (flipped) 7 - col else col
    return squareOf(file, rank)
}

@Composable
private fun SquareCell(
    vm: ChessViewModel,
    theme: BoardTheme,
    sq: Int,
    row: Int,
    col: Int,
    modifier: Modifier,
) {
    val file = fileOf(sq)
    val rank = rankOf(sq)
    val isLight = (file + rank) % 2 != 0
    val base = if (isLight) theme.light else theme.dark
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .background(base)
            .selectable(
                selected = vm.selected == sq,
                interactionSource = interaction,
                indication = null,
            ) { vm.onSquareTap(sq) },
    ) {
        val lm = vm.lastMove
        if (lm != null && (lm.from == sq || lm.to == sq)) {
            Box(Modifier.fillMaxSize().background(Highlights.lastMove))
        }
        if (vm.selected == sq) {
            Box(Modifier.fillMaxSize().background(Highlights.selected))
        }
        if (vm.checkSquare == sq) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Highlights.checkInner, Highlights.checkOuter),
                        radius = size.minDimension * 0.72f,
                    ),
                    radius = size.minDimension * 0.72f,
                )
            }
        }

        val piece = vm.board.squares[sq]
        if (piece != null) {
            Image(
                painter = painterResource(pieceDrawable(piece)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }

        if (sq in vm.legalTargets) {
            val occupied = piece != null
            Canvas(Modifier.fillMaxSize()) {
                if (occupied) {
                    drawCircle(
                        color = Highlights.captureRing,
                        radius = size.minDimension * 0.46f,
                        style = Stroke(width = size.minDimension * 0.085f),
                    )
                } else {
                    drawCircle(
                        color = Highlights.moveDot,
                        radius = size.minDimension * 0.16f,
                    )
                }
            }
        }

        if (vm.showCoordinates) {
            val coordColor = if (isLight) Highlights.coordOnLight else Highlights.coordOnDark
            if (col == 0) {
                Text(
                    text = (rank + 1).toString(),
                    color = coordColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp),
                )
            }
            if (row == 7) {
                Text(
                    text = ('a' + file).toString(),
                    color = coordColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 1.dp),
                )
            }
        }
    }
}
