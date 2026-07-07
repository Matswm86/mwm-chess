package no.mwm.chess.engine

/** Record needed to undo a move made with [Board.makeMove]. */
class Undo(
    val move: Move,
    val captured: Piece?,
    val capturedSquare: Int,
    val wk: Boolean,
    val wq: Boolean,
    val bk: Boolean,
    val bq: Boolean,
    val ep: Int?,
    val half: Int,
    val full: Int,
)

/** Mutable chess position. Cheap to [clone]; supports make/unmake for search. */
class Board(
    val squares: Array<Piece?>,
    var sideToMove: Color,
    var whiteKingSide: Boolean,
    var whiteQueenSide: Boolean,
    var blackKingSide: Boolean,
    var blackQueenSide: Boolean,
    var enPassant: Int?,
    var halfmoveClock: Int,
    var fullmove: Int,
) {
    fun clone(): Board = Board(
        squares.copyOf(), sideToMove,
        whiteKingSide, whiteQueenSide, blackKingSide, blackQueenSide,
        enPassant, halfmoveClock, fullmove,
    )

    fun pieceAt(sq: Int): Piece? = squares[sq]

    fun kingSquare(color: Color): Int {
        for (i in 0..63) {
            val p = squares[i]
            if (p != null && p.type == PieceType.KING && p.color == color) return i
        }
        return -1
    }

    private fun clearCastleOnSquare(sq: Int) {
        when (sq) {
            0 -> whiteQueenSide = false
            7 -> whiteKingSide = false
            56 -> blackQueenSide = false
            63 -> blackKingSide = false
        }
    }

    fun makeMove(m: Move): Undo {
        val piece = squares[m.from]!!
        val color = piece.color

        var capturedSquare = m.to
        var captured = squares[m.to]
        if (m.flag == MoveFlag.EN_PASSANT) {
            capturedSquare = if (color == Color.WHITE) m.to - 8 else m.to + 8
            captured = squares[capturedSquare]
        }

        val undo = Undo(
            m, captured, capturedSquare,
            whiteKingSide, whiteQueenSide, blackKingSide, blackQueenSide,
            enPassant, halfmoveClock, fullmove,
        )

        if (m.flag == MoveFlag.EN_PASSANT) squares[capturedSquare] = null

        squares[m.to] = if (m.promotion != null) Piece(m.promotion, color) else piece
        squares[m.from] = null

        when (m.flag) {
            MoveFlag.CASTLE_KING -> if (color == Color.WHITE) {
                squares[5] = squares[7]; squares[7] = null
            } else {
                squares[61] = squares[63]; squares[63] = null
            }
            MoveFlag.CASTLE_QUEEN -> if (color == Color.WHITE) {
                squares[3] = squares[0]; squares[0] = null
            } else {
                squares[59] = squares[56]; squares[56] = null
            }
            else -> {}
        }

        halfmoveClock =
            if (piece.type == PieceType.PAWN || captured != null) 0 else halfmoveClock + 1

        enPassant = if (m.flag == MoveFlag.DOUBLE_PUSH) {
            if (color == Color.WHITE) m.from + 8 else m.from - 8
        } else {
            null
        }

        if (piece.type == PieceType.KING) {
            if (color == Color.WHITE) {
                whiteKingSide = false; whiteQueenSide = false
            } else {
                blackKingSide = false; blackQueenSide = false
            }
        }
        clearCastleOnSquare(m.from)
        clearCastleOnSquare(m.to)

        if (color == Color.BLACK) fullmove++
        sideToMove = color.opposite
        return undo
    }

    fun unmakeMove(u: Undo) {
        val m = u.move
        val color = sideToMove.opposite // the side that made the move

        val movedPiece = squares[m.to]!!
        val originalPiece =
            if (m.promotion != null) Piece(PieceType.PAWN, color) else movedPiece
        squares[m.from] = originalPiece
        squares[m.to] = null

        when (m.flag) {
            MoveFlag.CASTLE_KING -> if (color == Color.WHITE) {
                squares[7] = squares[5]; squares[5] = null
            } else {
                squares[63] = squares[61]; squares[61] = null
            }
            MoveFlag.CASTLE_QUEEN -> if (color == Color.WHITE) {
                squares[0] = squares[3]; squares[3] = null
            } else {
                squares[56] = squares[59]; squares[59] = null
            }
            else -> {}
        }

        if (u.captured != null) squares[u.capturedSquare] = u.captured

        whiteKingSide = u.wk
        whiteQueenSide = u.wq
        blackKingSide = u.bk
        blackQueenSide = u.bq
        enPassant = u.ep
        halfmoveClock = u.half
        fullmove = u.full
        sideToMove = color
    }

    /** Stable string used for threefold-repetition detection. */
    fun repetitionKey(): String {
        val sb = StringBuilder(80)
        for (i in 0..63) {
            val p = squares[i]
            sb.append(if (p == null) '.' else pieceChar(p))
        }
        sb.append(if (sideToMove == Color.WHITE) 'w' else 'b')
        sb.append(if (whiteKingSide) 'K' else '-')
        sb.append(if (whiteQueenSide) 'Q' else '-')
        sb.append(if (blackKingSide) 'k' else '-')
        sb.append(if (blackQueenSide) 'q' else '-')
        sb.append(enPassant?.toString() ?: "-")
        return sb.toString()
    }

    private fun pieceChar(p: Piece): Char {
        val c = when (p.type) {
            PieceType.PAWN -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK -> 'r'
            PieceType.QUEEN -> 'q'
            PieceType.KING -> 'k'
        }
        return if (p.color == Color.WHITE) c.uppercaseChar() else c
    }

    companion object {
        fun initial(): Board {
            val sq = arrayOfNulls<Piece>(64)
            val back = arrayOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK,
            )
            for (f in 0..7) {
                sq[f] = Piece(back[f], Color.WHITE)
                sq[8 + f] = Piece(PieceType.PAWN, Color.WHITE)
                sq[48 + f] = Piece(PieceType.PAWN, Color.BLACK)
                sq[56 + f] = Piece(back[f], Color.BLACK)
            }
            return Board(sq, Color.WHITE, true, true, true, true, null, 0, 1)
        }
    }
}
