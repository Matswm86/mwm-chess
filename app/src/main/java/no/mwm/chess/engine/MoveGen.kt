package no.mwm.chess.engine

/** Move generation, attack detection and legality filtering. */
object MoveGen {

    private val KNIGHT_D = arrayOf(
        intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
        intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2),
    )
    private val KING_D = arrayOf(
        intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
        intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
    )
    private val ROOK_D = arrayOf(
        intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
    )
    private val BISHOP_D = arrayOf(
        intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1),
    )

    fun isSquareAttacked(b: Board, sq: Int, by: Color): Boolean {
        val sqf = fileOf(sq)
        val sqr = rankOf(sq)
        val s = b.squares

        // Pawn attacks: a `by`-pawn sits one rank toward its own origin.
        val pRank = if (by == Color.WHITE) sqr - 1 else sqr + 1
        if (pRank in 0..7) {
            for (df in intArrayOf(-1, 1)) {
                val pf = sqf + df
                if (pf in 0..7) {
                    val p = s[pRank * 8 + pf]
                    if (p != null && p.color == by && p.type == PieceType.PAWN) return true
                }
            }
        }
        // Knight
        for (d in KNIGHT_D) {
            val f = sqf + d[0]; val r = sqr + d[1]
            if (onBoard(f, r)) {
                val p = s[r * 8 + f]
                if (p != null && p.color == by && p.type == PieceType.KNIGHT) return true
            }
        }
        // King
        for (d in KING_D) {
            val f = sqf + d[0]; val r = sqr + d[1]
            if (onBoard(f, r)) {
                val p = s[r * 8 + f]
                if (p != null && p.color == by && p.type == PieceType.KING) return true
            }
        }
        // Rook / Queen rays
        for (d in ROOK_D) {
            var f = sqf + d[0]; var r = sqr + d[1]
            while (onBoard(f, r)) {
                val p = s[r * 8 + f]
                if (p != null) {
                    if (p.color == by && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += d[0]; r += d[1]
            }
        }
        // Bishop / Queen rays
        for (d in BISHOP_D) {
            var f = sqf + d[0]; var r = sqr + d[1]
            while (onBoard(f, r)) {
                val p = s[r * 8 + f]
                if (p != null) {
                    if (p.color == by && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += d[0]; r += d[1]
            }
        }
        return false
    }

    fun isInCheck(b: Board, color: Color): Boolean {
        val ks = b.kingSquare(color)
        if (ks < 0) return false
        return isSquareAttacked(b, ks, color.opposite)
    }

    fun pseudoLegal(b: Board): MutableList<Move> {
        val moves = ArrayList<Move>(48)
        val me = b.sideToMove
        val s = b.squares
        for (sq in 0..63) {
            val p = s[sq] ?: continue
            if (p.color != me) continue
            when (p.type) {
                PieceType.PAWN -> pawnMoves(b, sq, me, moves)
                PieceType.KNIGHT -> stepMoves(b, sq, me, KNIGHT_D, moves)
                PieceType.KING -> {
                    stepMoves(b, sq, me, KING_D, moves)
                    castleMoves(b, sq, me, moves)
                }
                PieceType.BISHOP -> slideMoves(b, sq, me, BISHOP_D, moves)
                PieceType.ROOK -> slideMoves(b, sq, me, ROOK_D, moves)
                PieceType.QUEEN -> {
                    slideMoves(b, sq, me, ROOK_D, moves)
                    slideMoves(b, sq, me, BISHOP_D, moves)
                }
            }
        }
        return moves
    }

    private fun stepMoves(b: Board, sq: Int, me: Color, dirs: Array<IntArray>, out: MutableList<Move>) {
        val f = fileOf(sq); val r = rankOf(sq)
        for (d in dirs) {
            val nf = f + d[0]; val nr = r + d[1]
            if (!onBoard(nf, nr)) continue
            val target = nr * 8 + nf
            val occ = b.squares[target]
            if (occ == null || occ.color != me) out.add(Move(sq, target))
        }
    }

    private fun slideMoves(b: Board, sq: Int, me: Color, dirs: Array<IntArray>, out: MutableList<Move>) {
        val f = fileOf(sq); val r = rankOf(sq)
        for (d in dirs) {
            var nf = f + d[0]; var nr = r + d[1]
            while (onBoard(nf, nr)) {
                val target = nr * 8 + nf
                val occ = b.squares[target]
                if (occ == null) {
                    out.add(Move(sq, target))
                } else {
                    if (occ.color != me) out.add(Move(sq, target))
                    break
                }
                nf += d[0]; nr += d[1]
            }
        }
    }

    private fun pawnMoves(b: Board, sq: Int, me: Color, out: MutableList<Move>) {
        val f = fileOf(sq); val r = rankOf(sq)
        val dir = if (me == Color.WHITE) 1 else -1
        val startRank = if (me == Color.WHITE) 1 else 6
        val promoRank = if (me == Color.WHITE) 7 else 0
        val oneR = r + dir

        if (oneR in 0..7) {
            val one = oneR * 8 + f
            if (b.squares[one] == null) {
                if (oneR == promoRank) addPromotions(sq, one, out) else out.add(Move(sq, one))
                if (r == startRank) {
                    val two = (r + 2 * dir) * 8 + f
                    if (b.squares[two] == null) out.add(Move(sq, two, flag = MoveFlag.DOUBLE_PUSH))
                }
            }
            for (df in intArrayOf(-1, 1)) {
                val cf = f + df
                if (cf in 0..7) {
                    val target = oneR * 8 + cf
                    val occ = b.squares[target]
                    if (occ != null && occ.color != me) {
                        if (oneR == promoRank) addPromotions(sq, target, out)
                        else out.add(Move(sq, target))
                    } else if (occ == null && b.enPassant == target) {
                        out.add(Move(sq, target, flag = MoveFlag.EN_PASSANT))
                    }
                }
            }
        }
    }

    private fun addPromotions(from: Int, to: Int, out: MutableList<Move>) {
        out.add(Move(from, to, PieceType.QUEEN))
        out.add(Move(from, to, PieceType.ROOK))
        out.add(Move(from, to, PieceType.BISHOP))
        out.add(Move(from, to, PieceType.KNIGHT))
    }

    private fun castleMoves(b: Board, sq: Int, me: Color, out: MutableList<Move>) {
        val enemy = me.opposite
        if (me == Color.WHITE && sq == 4) {
            if (b.whiteKingSide && b.squares[5] == null && b.squares[6] == null &&
                rookAt(b, 7, Color.WHITE) &&
                !isSquareAttacked(b, 4, enemy) && !isSquareAttacked(b, 5, enemy) &&
                !isSquareAttacked(b, 6, enemy)
            ) out.add(Move(4, 6, flag = MoveFlag.CASTLE_KING))
            if (b.whiteQueenSide && b.squares[1] == null && b.squares[2] == null &&
                b.squares[3] == null && rookAt(b, 0, Color.WHITE) &&
                !isSquareAttacked(b, 4, enemy) && !isSquareAttacked(b, 3, enemy) &&
                !isSquareAttacked(b, 2, enemy)
            ) out.add(Move(4, 2, flag = MoveFlag.CASTLE_QUEEN))
        } else if (me == Color.BLACK && sq == 60) {
            if (b.blackKingSide && b.squares[61] == null && b.squares[62] == null &&
                rookAt(b, 63, Color.BLACK) &&
                !isSquareAttacked(b, 60, enemy) && !isSquareAttacked(b, 61, enemy) &&
                !isSquareAttacked(b, 62, enemy)
            ) out.add(Move(60, 62, flag = MoveFlag.CASTLE_KING))
            if (b.blackQueenSide && b.squares[57] == null && b.squares[58] == null &&
                b.squares[59] == null && rookAt(b, 56, Color.BLACK) &&
                !isSquareAttacked(b, 60, enemy) && !isSquareAttacked(b, 59, enemy) &&
                !isSquareAttacked(b, 58, enemy)
            ) out.add(Move(60, 58, flag = MoveFlag.CASTLE_QUEEN))
        }
    }

    private fun rookAt(b: Board, sq: Int, color: Color): Boolean {
        val p = b.squares[sq]
        return p != null && p.type == PieceType.ROOK && p.color == color
    }

    /** Fully legal moves for the side to move. */
    fun legalMoves(b: Board): List<Move> {
        val me = b.sideToMove
        val result = ArrayList<Move>(40)
        for (m in pseudoLegal(b)) {
            val u = b.makeMove(m)
            if (!isInCheck(b, me)) result.add(m)
            b.unmakeMove(u)
        }
        return result
    }

    fun legalMovesFrom(b: Board, from: Int): List<Move> =
        legalMoves(b).filter { it.from == from }

    fun isCapture(b: Board, m: Move): Boolean =
        b.squares[m.to] != null || m.flag == MoveFlag.EN_PASSANT
}
