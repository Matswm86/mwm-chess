package no.mwm.chess.engine.ai

import no.mwm.chess.engine.Board
import no.mwm.chess.engine.Color
import no.mwm.chess.engine.Eval
import no.mwm.chess.engine.Move
import no.mwm.chess.engine.MoveGen
import no.mwm.chess.engine.PieceType
import kotlin.random.Random

/** AI strength levels. Depth is the hard cap; [timeMs] bounds thinking on a
 *  phone; [blunderChance] injects a random legal move to make Easy beatable. */
enum class Difficulty(
    val label: String,
    val maxDepth: Int,
    val timeMs: Long,
    val blunderChance: Double,
) {
    EASY("Easy", 2, 500, 0.35),
    MEDIUM("Medium", 4, 1100, 0.06),
    HARD("Hard", 6, 2600, 0.0),
    EXPERT("Expert", 10, 5000, 0.0),
}

/** Negamax + alpha-beta + quiescence with iterative deepening and a time cap. */
class SearchEngine {

    private companion object {
        const val INF = 1_000_000
        const val MATE = 100_000
    }

    private val pieceVal = intArrayOf(100, 320, 330, 500, 900, 20000)

    private var stopTime = 0L
    private var aborted = false
    private var nodes = 0

    fun chooseMove(board: Board, difficulty: Difficulty): Move? {
        val legal = MoveGen.legalMoves(board)
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        if (difficulty.blunderChance > 0.0 && Random.nextDouble() < difficulty.blunderChance) {
            return legal.random()
        }
        return bestMove(board, legal, difficulty)
    }

    private fun bestMove(board: Board, rootMoves: List<Move>, difficulty: Difficulty): Move {
        stopTime = System.currentTimeMillis() + difficulty.timeMs
        nodes = 0
        var ordered = orderMoves(board, rootMoves)
        var best = ordered[0]

        var depth = 1
        while (depth <= difficulty.maxDepth) {
            aborted = false
            var alpha = -INF
            val beta = INF
            var localBest = ordered[0]
            var localBestScore = -INF
            val scored = ArrayList<Pair<Move, Int>>(ordered.size)

            for (m in ordered) {
                val u = board.makeMove(m)
                val score = -negamax(board, depth - 1, -beta, -alpha, 1)
                board.unmakeMove(u)
                if (aborted) break
                scored.add(m to score)
                if (score > localBestScore) {
                    localBestScore = score
                    localBest = m
                }
                if (score > alpha) alpha = score
            }

            if (!aborted) {
                best = localBest
                // Order the next iteration best-first for stronger pruning.
                scored.sortByDescending { it.second }
                ordered = scored.map { it.first }
            }
            if (System.currentTimeMillis() >= stopTime) break
            depth++
        }
        return best
    }

    private fun negamax(board: Board, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        if (aborted) return 0
        if ((nodes++ and 2047) == 0 && System.currentTimeMillis() >= stopTime) {
            aborted = true
            return 0
        }
        if (depth <= 0) return quiescence(board, alphaIn, beta)

        val me = board.sideToMove
        val moves = orderMoves(board, MoveGen.pseudoLegal(board))
        var alpha = alphaIn
        var best = -INF
        var legalCount = 0

        for (m in moves) {
            val u = board.makeMove(m)
            if (MoveGen.isInCheck(board, me)) {
                board.unmakeMove(u)
                continue
            }
            legalCount++
            val score = -negamax(board, depth - 1, -beta, -alpha, ply + 1)
            board.unmakeMove(u)
            if (aborted) return 0
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }

        if (legalCount == 0) {
            return if (MoveGen.isInCheck(board, me)) -MATE + ply else 0
        }
        return best
    }

    private fun quiescence(board: Board, alphaIn: Int, beta: Int): Int {
        if (aborted) return 0
        if ((nodes++ and 2047) == 0 && System.currentTimeMillis() >= stopTime) {
            aborted = true
            return 0
        }
        val standPat = Eval.evaluate(board)
        if (standPat >= beta) return beta
        var alpha = if (standPat > alphaIn) standPat else alphaIn

        val me = board.sideToMove
        val caps = MoveGen.pseudoLegal(board).filter { MoveGen.isCapture(board, it) }
        for (m in orderMoves(board, caps)) {
            val u = board.makeMove(m)
            if (MoveGen.isInCheck(board, me)) {
                board.unmakeMove(u)
                continue
            }
            val score = -quiescence(board, -beta, -alpha)
            board.unmakeMove(u)
            if (aborted) return 0
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    /** Captures first, ordered by MVV-LVA; promotions weighted high. */
    private fun orderMoves(board: Board, moves: List<Move>): List<Move> {
        if (moves.size <= 1) return moves
        return moves.sortedByDescending { m -> moveScore(board, m) }
    }

    private fun moveScore(board: Board, m: Move): Int {
        var score = 0
        val victim = board.squares[m.to]
        if (victim != null) {
            score += 10 * pieceVal[victim.type.ordinal] -
                pieceVal[board.squares[m.from]!!.type.ordinal]
        }
        if (m.promotion != null) {
            score += if (m.promotion == PieceType.QUEEN) 9000 else 3000
        }
        if (m.flag == no.mwm.chess.engine.MoveFlag.EN_PASSANT) score += 1000
        return score
    }

    // Kept for API symmetry / potential UI hints.
    fun sideToMoveName(board: Board): String =
        if (board.sideToMove == Color.WHITE) "White" else "Black"
}
