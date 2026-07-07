package no.mwm.chess.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.mwm.chess.engine.Board
import no.mwm.chess.engine.Color
import no.mwm.chess.engine.GameRules
import no.mwm.chess.engine.GameStatus
import no.mwm.chess.engine.Move
import no.mwm.chess.engine.MoveFlag
import no.mwm.chess.engine.MoveGen
import no.mwm.chess.engine.Notation
import no.mwm.chess.engine.PieceType
import no.mwm.chess.engine.StatusType
import no.mwm.chess.engine.ai.Difficulty
import no.mwm.chess.engine.ai.SearchEngine
import kotlin.random.Random

enum class GameMode { VS_AI, TWO_PLAYER }
enum class ColorChoice { WHITE, BLACK, RANDOM }

class ChessViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = SearchEngine()
    private val sound = SoundManager(app)

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
    var soundOn by mutableStateOf(true)
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
    var hinting by mutableStateOf(false)
        private set
    var pendingPromotion by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var checkSquare by mutableStateOf<Int?>(null)
        private set
    var hintFrom by mutableStateOf<Int?>(null)
        private set
    var hintTo by mutableStateOf<Int?>(null)
        private set
    var moveSans by mutableStateOf<List<String>>(emptyList())
        private set
    var resigned by mutableStateOf(false)
        private set

    private var selectedMoves: List<Move> = emptyList()
    private val historyBoards = ArrayList<Board>()

    /** True once the game has ended by rule or by resignation. */
    val isGameOver: Boolean get() = status.isOver || resigned

    /** Whether there is a move to take back. */
    val canUndo: Boolean get() = historyBoards.isNotEmpty()

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
        clearHint()
        lastMove = null
        moveSans = emptyList()
        historyBoards.clear()
        inMenu = false
        thinking = false
        hinting = false
        resigned = false
        refreshStatus()
        maybeTriggerAI()
    }

    /** Human concedes the game. */
    fun resign() {
        if (isGameOver) return
        thinking = false
        hinting = false
        clearSelection()
        clearHint()
        resigned = true
    }

    fun backToMenu() {
        thinking = false
        hinting = false
        inMenu = true
    }

    fun onSquareTap(sq: Int) {
        if (inMenu || thinking || hinting || pendingPromotion != null || isGameOver) return
        if (mode == GameMode.VS_AI && board.sideToMove != humanColor) return
        clearHint()

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

    fun requestHint() {
        if (inMenu || thinking || hinting || pendingPromotion != null || isGameOver) return
        if (mode == GameMode.VS_AI && board.sideToMove != humanColor) return
        hinting = true
        val snapshot = board.clone()
        viewModelScope.launch {
            val mv = withContext(Dispatchers.Default) { engine.chooseMove(snapshot, Difficulty.HARD) }
            hinting = false
            if (!inMenu && mv != null && !isGameOver &&
                (mode != GameMode.VS_AI || board.sideToMove == humanColor)
            ) {
                select(mv.from)
                hintFrom = mv.from
                hintTo = mv.to
            }
        }
    }

    fun undo() {
        if (thinking || hinting || inMenu) return
        if (resigned) { resigned = false; return }
        if (historyBoards.isEmpty()) return
        var popped = 0
        board = historyBoards.removeAt(historyBoards.lastIndex); popped++
        if (mode == GameMode.VS_AI && historyBoards.isNotEmpty() &&
            board.sideToMove != humanColor
        ) {
            board = historyBoards.removeAt(historyBoards.lastIndex); popped++
        }
        if (moveSans.size >= popped) moveSans = moveSans.dropLast(popped)
        lastMove = null
        clearHint()
        clearSelection()
        refreshStatus()
    }

    fun flipBoard() {
        flipped = !flipped
    }

    /** Change the computer's strength mid-game; takes effect on its next move. */
    fun setDifficulty(d: Difficulty) {
        difficulty = d
    }

    fun toggleSound() {
        soundOn = !soundOn
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

    private fun clearHint() {
        hintFrom = null
        hintTo = null
    }

    private fun applyMove(move: Move) {
        clearHint()
        val before = board
        val capturing = before.squares[move.to] != null || move.flag == MoveFlag.EN_PASSANT
        val castling = move.flag == MoveFlag.CASTLE_KING || move.flag == MoveFlag.CASTLE_QUEEN
        val promoting = move.promotion != null

        historyBoards.add(before)
        val next = before.clone()
        next.makeMove(move)
        board = next
        lastMove = move
        clearSelection()
        refreshStatus()

        val mate = status.type == StatusType.CHECKMATE
        val check = status.type == StatusType.CHECK
        moveSans = moveSans + Notation.san(before, move, check, mate)

        playSound(promoting, castling, capturing)
        maybeTriggerAI()
    }

    private fun playSound(promoting: Boolean, castling: Boolean, capturing: Boolean) {
        if (!soundOn) return
        val event = when {
            status.isOver -> SoundEvent.GAMEOVER
            status.type == StatusType.CHECK -> SoundEvent.CHECK
            promoting -> SoundEvent.PROMOTE
            castling -> SoundEvent.CASTLE
            capturing -> SoundEvent.CAPTURE
            else -> SoundEvent.MOVE
        }
        sound.play(event)
    }

    private fun maybeTriggerAI() {
        if (isGameOver) return
        if (mode != GameMode.VS_AI || board.sideToMove == humanColor) return
        thinking = true
        val snapshot = board.clone()
        val diff = difficulty
        viewModelScope.launch {
            val mv = withContext(Dispatchers.Default) { engine.chooseMove(snapshot, diff) }
            thinking = false
            if (!inMenu && mv != null && !isGameOver &&
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

    override fun onCleared() {
        super.onCleared()
        sound.release()
    }
}
