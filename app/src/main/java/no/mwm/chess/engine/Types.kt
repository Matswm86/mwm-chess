package no.mwm.chess.engine

/** Board squares are indexed 0..63 with index = rank * 8 + file.
 *  file 0 = a-file, rank 0 = White's first rank (rank 1). */

enum class Color {
    WHITE, BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

data class Piece(val type: PieceType, val color: Color)

enum class MoveFlag { NORMAL, DOUBLE_PUSH, EN_PASSANT, CASTLE_KING, CASTLE_QUEEN }

/** A move. [promotion] is non-null only for pawn promotions. */
data class Move(
    val from: Int,
    val to: Int,
    val promotion: PieceType? = null,
    val flag: MoveFlag = MoveFlag.NORMAL,
)

fun fileOf(sq: Int): Int = sq and 7
fun rankOf(sq: Int): Int = sq shr 3
fun squareOf(file: Int, rank: Int): Int = rank * 8 + file
fun onBoard(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7
