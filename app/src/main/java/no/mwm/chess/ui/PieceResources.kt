package no.mwm.chess.ui

import no.mwm.chess.R
import no.mwm.chess.engine.Color
import no.mwm.chess.engine.Piece
import no.mwm.chess.engine.PieceType

/** Maps a piece to its Cburnett vector drawable. */
fun pieceDrawable(p: Piece): Int = when (p.color) {
    Color.WHITE -> when (p.type) {
        PieceType.KING -> R.drawable.piece_wk
        PieceType.QUEEN -> R.drawable.piece_wq
        PieceType.ROOK -> R.drawable.piece_wr
        PieceType.BISHOP -> R.drawable.piece_wb
        PieceType.KNIGHT -> R.drawable.piece_wn
        PieceType.PAWN -> R.drawable.piece_wp
    }
    Color.BLACK -> when (p.type) {
        PieceType.KING -> R.drawable.piece_bk
        PieceType.QUEEN -> R.drawable.piece_bq
        PieceType.ROOK -> R.drawable.piece_br
        PieceType.BISHOP -> R.drawable.piece_bb
        PieceType.KNIGHT -> R.drawable.piece_bn
        PieceType.PAWN -> R.drawable.piece_bp
    }
}
