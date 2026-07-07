package no.mwm.chess.engine

/** Standard Algebraic Notation for a move, given the position BEFORE it is made
 *  and whether it delivers check / checkmate. */
object Notation {

    fun san(before: Board, move: Move, causesCheck: Boolean, causesMate: Boolean): String {
        val suffix = if (causesMate) "#" else if (causesCheck) "+" else ""

        if (move.flag == MoveFlag.CASTLE_KING) return "O-O$suffix"
        if (move.flag == MoveFlag.CASTLE_QUEEN) return "O-O-O$suffix"

        val piece = before.squares[move.from]!!
        val isCapture = before.squares[move.to] != null || move.flag == MoveFlag.EN_PASSANT
        val dest = squareName(move.to)

        if (piece.type == PieceType.PAWN) {
            val body = if (isCapture) "${fileChar(move.from)}x$dest" else dest
            val promo = move.promotion?.let { "=${pieceLetter(it)}" } ?: ""
            return "$body$promo$suffix"
        }

        val disamb = disambiguation(before, move, piece)
        val capture = if (isCapture) "x" else ""
        return "${pieceLetter(piece.type)}$disamb$capture$dest$suffix"
    }

    private fun disambiguation(before: Board, move: Move, piece: Piece): String {
        val rivals = MoveGen.legalMoves(before).filter {
            it.to == move.to && it.from != move.from &&
                before.squares[it.from]?.type == piece.type &&
                before.squares[it.from]?.color == piece.color
        }
        if (rivals.isEmpty()) return ""
        val sameFile = rivals.any { fileOf(it.from) == fileOf(move.from) }
        val sameRank = rivals.any { rankOf(it.from) == rankOf(move.from) }
        return when {
            !sameFile -> fileChar(move.from).toString()
            !sameRank -> (rankOf(move.from) + 1).toString()
            else -> "${fileChar(move.from)}${rankOf(move.from) + 1}"
        }
    }

    private fun squareName(sq: Int): String = "${fileChar(sq)}${rankOf(sq) + 1}"
    private fun fileChar(sq: Int): Char = 'a' + fileOf(sq)

    private fun pieceLetter(t: PieceType): String = when (t) {
        PieceType.KING -> "K"
        PieceType.QUEEN -> "Q"
        PieceType.ROOK -> "R"
        PieceType.BISHOP -> "B"
        PieceType.KNIGHT -> "N"
        PieceType.PAWN -> ""
    }
}
