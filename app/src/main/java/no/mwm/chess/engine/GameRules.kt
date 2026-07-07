package no.mwm.chess.engine

enum class StatusType {
    ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW_50, DRAW_MATERIAL, DRAW_REPETITION
}

data class GameStatus(val type: StatusType, val winner: Color? = null) {
    val isOver: Boolean
        get() = type == StatusType.CHECKMATE || type == StatusType.STALEMATE ||
            type == StatusType.DRAW_50 || type == StatusType.DRAW_MATERIAL ||
            type == StatusType.DRAW_REPETITION
}

object GameRules {

    fun status(b: Board, repetitionCount: Int = 0): GameStatus {
        val legal = MoveGen.legalMoves(b)
        val inCheck = MoveGen.isInCheck(b, b.sideToMove)
        if (legal.isEmpty()) {
            return if (inCheck) GameStatus(StatusType.CHECKMATE, b.sideToMove.opposite)
            else GameStatus(StatusType.STALEMATE)
        }
        if (repetitionCount >= 3) return GameStatus(StatusType.DRAW_REPETITION)
        if (b.halfmoveClock >= 100) return GameStatus(StatusType.DRAW_50)
        if (insufficientMaterial(b)) return GameStatus(StatusType.DRAW_MATERIAL)
        return if (inCheck) GameStatus(StatusType.CHECK) else GameStatus(StatusType.ONGOING)
    }

    /** True for K vs K, K+minor vs K, and K+B vs K+B (basic dead positions). */
    private fun insufficientMaterial(b: Board): Boolean {
        var minors = 0
        for (sq in 0..63) {
            val p = b.squares[sq] ?: continue
            when (p.type) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.BISHOP, PieceType.KNIGHT -> minors++
                PieceType.KING -> {}
            }
        }
        return minors <= 1
    }
}
