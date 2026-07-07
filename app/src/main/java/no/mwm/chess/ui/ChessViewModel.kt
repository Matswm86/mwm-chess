package no.mwm.chess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.mwm.chess.engine.Board
import no.mwm.chess.engine.Color
import no.mwm.chess.engine.GameRules
import no.mwm.chess.engine.GameStatus
import no.mwm.chess.engine.Move
import no.mwm.chess.engine.MoveGen
import no.mwm.chess.engine.PieceType
import no.mwm.chess.engine.StatusType
import no.mwm.chess.engine.ai.Difficulty
import no.mwm.chess.engine.ai.SearchEngine
import kotlin.random.Random

enum class GameMode { VS_AI, TWO_PLAYER }
enum class ColorChoice { WHITE, BLACK, RANDOM }

class ChessViewModel : ViewModel() {

    private val engine = SearchEngine()

    var inMenu by mutableStateOf(true)
        private set
    var board by mutableStateOf(Board.initial())
        private set
    var mode by mutableStateOf(GameMode.VS_AI)
        private set
    var difficulty by mutableStateOf(Difficulty.MEDIUM)
        private set
    var humanColor by mutableStateOf(Color.WHITE)
        private set
    var flipped by mutableStateOf(false)
        private set
    var showCoordinates by mutableStateOf(true)
        private set
    var boardThemeIndex by mutableStateOf(0)
        private set

    var selected by mutableStateOf<Int?>(null)
        private set
    var legalTargets by mutableStateOf<Set<Int>>(emptySet())
        private set
    var lastMove by mutableStateOf<Move?>(null)
        private set
    var status by mutableStateOf(GameStatus(StatusType.ONGOING))
        private set
    var thinking by mutableStateOf(false)
        private set
    var pendingPromotion by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var checkSquare by mutableStateOf<Int?>(null)
        private set

    private var selectedMoves: List<Move> = emptyList()
    private val historyBoards = ArrayList<Board>()

    fun startGame(mode: GameMode, difficulty: Difficulty, colorChoice: ColorChoice) {
        this.mode = mode
        this.difficulty = difficulty
        humanColor = when (colorChoice) {
            ColorChoice.WHITE -> Color.WHITE
            ColorChoice.BLACK -> Color.BLACK
            ColorChoice.RANDOM -> if (Random.nextBoolean()) Color.WHITE else Color.BLACK
        }
        board = Board.initial()
        flipped = mode == GameMode.VS_AI && humanColor == Color.BLACK
        clearSelection()
        lastMove = null
        historyBoards.clear()
        inMenu = false
        thinking = false
        refreshStatus()
        maybeTriggerAI()
    }

    fun backToMenu() {
        thinking = false
        inMenu = true
    }

    fun onSquareTap(sq: Int) {
        if (inMenu || thinking || pendingPromotion != null || status.isOver) return
        if (mode == GameMode.VS_AI && board.sideToMove != humanColor) return

        val piece = board.squares[sq]
        val sel = selected
        if (sel == null) {
            if (piece != null && piece.color == board.sideToMove) select(sq)
            return
        }
        if (sq == sel) {
            clearSelection()
            return
        }
        val matching = selectedMoves.filter { it.to == sq }
        when {
            matching.isEmpty() ->
                if (piece != null && piece.color == board.sideToMove) select(sq) else clearSelection()
            matching.any { it.promotion != null } -> pendingPromotion = sel to sq
            else -> applyMove(matching.first())
        }
    }

    fun choosePromotion(type: PieceType) {
        val pp = pendingPromotion ?: return
        pendingPromotion = null
        applyMove(Move(pp.first, pp.second, promotion = type))
    }

    fun cancelPromotion() {
        pendingPromotion = null
        clearSelection()
    }

    fun undo() {
        if (thinking || inMenu || historyBoards.isEmpty()) return
        board = historyBoards.removeAt(historyBoards.lastIndex)
        if (mode == GameMode.VS_AI && historyBoards.isNotEmpty() &&
            board.sideToMove != humanColor
        ) {
            board = historyBoards.removeAt(historyBoards.lastIndex)
        }
        lastMove = null
        clearSelection()
        refreshStatus()
    }

    fun flipBoard() {
        flipped = !flipped
    }

    fun toggleCoordinates() {
        showCoordinates = !showCoordinates
    }

    fun cycleBoardTheme() {
        boardThemeIndex = (boardThemeIndex + 1) % BoardThemes.all.size
    }

    private fun select(sq: Int) {
        selected = sq
        selectedMoves = MoveGen.legalMovesFrom(board, sq)
        legalTargets = selectedMoves.map { it.to }.toSet()
    }

    private fun clearSelection() {
        selected = null
        selectedMoves = emptyList()
        legalTargets = emptySet()
    }

    private fun applyMove(move: Move) {
        historyBoards.add(board)
        val next = board.clone()
        next.makeMove(move)
        board = next
        lastMove = move
        clearSelection()
        refreshStatus()
        maybeTriggerAI()
    }

    private fun maybeTriggerAI() {
        if (status.isOver) return
        if (mode != GameMode.VS_AI || board.sideToMove == humanColor) return
        thinking = true
        val snapshot = board.clone()
        val diff = difficulty
        viewModelScope.launch {
            val mv = withContext(Dispatchers.Default) { engine.chooseMove(snapshot, diff) }
            thinking = false
            if (!inMenu && mv != null && !status.isOver &&
                board.sideToMove != humanColor
            ) {
                applyMove(mv)
            }
        }
    }

    private fun refreshStatus() {
        val key = board.repetitionKey()
        val rep = historyBoards.count { it.repetitionKey() == key } + 1
        status = GameRules.status(board, rep)
        checkSquare = when (status.type) {
            StatusType.CHECK, StatusType.CHECKMATE -> board.kingSquare(board.sideToMove)
            else -> null
        }
    }
}
