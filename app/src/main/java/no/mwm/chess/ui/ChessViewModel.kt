package no.mwm.chess.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import no.mwm.chess.online.OnlineClient
import no.mwm.chess.online.OnlineError
import no.mwm.chess.online.RoomState
import no.mwm.chess.online.Seat
import kotlin.random.Random

enum class GameMode { VS_AI, TWO_PLAYER, ONLINE }

/** Where an online game stands: waiting for the friend to join, or playing. */
enum class OnlinePhase { WAITING, PLAYING }
enum class ColorChoice { WHITE, BLACK, RANDOM }

class ChessViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = SearchEngine()
    private val sound = SoundManager(app)
    private val online = OnlineClient()

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

    // ---- online play (mode == ONLINE)
    var onlinePhase by mutableStateOf<OnlinePhase?>(null)
        private set
    var onlineCode by mutableStateOf<String?>(null)
        private set
    /** Plain-English problem to show the player, or null. */
    var onlineError by mutableStateOf<String?>(null)
        private set
    /** True while the menu waits for the server to create or join a game. */
    var connecting by mutableStateOf(false)
        private set
    /** True while the last attempt to reach the server failed and polling is retrying. */
    var reconnecting by mutableStateOf(false)
        private set
    var opponentOnline by mutableStateOf(false)
        private set
    var opponentWantsRematch by mutableStateOf(false)
        private set
    var youWantRematch by mutableStateOf(false)
        private set
    /** Winner of an online game that ended by resignation or by a player leaving. */
    var onlineWinner by mutableStateOf<Color?>(null)
        private set
    /** "resign" or "left" when [onlineWinner] is set. */
    var onlineEndReason by mutableStateOf<String?>(null)
        private set

    private var seat: Seat? = null
    private var pollJob: Job? = null
    private var roomVersion = 0
    private var roomGame = 0
    private var sending = false
    /** Moves on this phone's board in UCI text, the same list the server keeps. */
    private val localUci = ArrayList<String>()

    private var selectedMoves: List<Move> = emptyList()
    private val historyBoards = ArrayList<Board>()

    /** True once the game has ended by rule, by resignation, or by an online player leaving. */
    val isGameOver: Boolean get() = status.isOver || resigned || onlineWinner != null

    /** Whether there is a move to take back. */
    val canUndo: Boolean get() = historyBoards.isNotEmpty()

    fun startGame(mode: GameMode, difficulty: Difficulty, colorChoice: ColorChoice) {
        if (mode == GameMode.ONLINE) return
        leaveOnline()
        this.mode = mode
        this.difficulty = difficulty
        humanColor = when (colorChoice) {
            ColorChoice.WHITE -> Color.WHITE
            ColorChoice.BLACK -> Color.BLACK
            ColorChoice.RANDOM -> if (Random.nextBoolean()) Color.WHITE else Color.BLACK
        }
        resetBoard()
        flipped = mode == GameMode.VS_AI && humanColor == Color.BLACK
        inMenu = false
        maybeTriggerAI()
    }

    private fun resetBoard() {
        board = Board.initial()
        clearSelection()
        clearHint()
        lastMove = null
        moveSans = emptyList()
        historyBoards.clear()
        localUci.clear()
        thinking = false
        hinting = false
        resigned = false
        pendingPromotion = null
        refreshStatus()
    }

    // ------------------------------------------------------------ online play

    /** Open a new online game and wait for a friend to join with its code. */
    fun createOnlineGame(colorChoice: ColorChoice) {
        val color = when (colorChoice) {
            ColorChoice.WHITE -> "white"
            ColorChoice.BLACK -> "black"
            ColorChoice.RANDOM -> "random"
        }
        connectThen { online.create(color) }
    }

    /** Join a friend's online game by its 4-letter code. */
    fun joinOnlineGame(code: String) {
        val clean = code.uppercase().filter { it in 'A'..'Z' }
        if (clean.length != 4) {
            onlineError = "A game code is 4 letters."
            return
        }
        connectThen { online.join(clean) }
    }

    fun clearOnlineError() {
        onlineError = null
    }

    private fun connectThen(open: suspend () -> Seat) {
        if (connecting) return
        connecting = true
        onlineError = null
        viewModelScope.launch {
            try {
                enterOnline(open())
            } catch (e: OnlineError) {
                onlineError = OnlineClient.describe(e.code)
            } finally {
                connecting = false
            }
        }
    }

    private fun enterOnline(s: Seat) {
        seat = s
        mode = GameMode.ONLINE
        onlineCode = s.code
        humanColor = colorOf(s.color)
        resetBoard()
        flipped = humanColor == Color.BLACK
        roomVersion = 0
        roomGame = 1
        onlineWinner = null
        onlineEndReason = null
        opponentOnline = false
        opponentWantsRematch = false
        youWantRematch = false
        reconnecting = false
        onlinePhase = OnlinePhase.WAITING
        inMenu = false
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val s = seat ?: break
                try {
                    val st = online.state(s, roomVersion)
                    reconnecting = false
                    if (seat === s) applyRoom(st)
                } catch (e: OnlineError) {
                    if (e.code == "no_game" || e.code == "not_in_game") {
                        onlineError = OnlineClient.describe(e.code)
                        opponentOnline = false
                        break
                    }
                    reconnecting = true
                    delay(RETRY_MS)
                }
            }
        }
    }

    /** Bring this phone's board in line with the room the server reports. */
    private fun applyRoom(st: RoomState) {
        if (st.version < roomVersion) return
        roomVersion = st.version
        humanColor = colorOf(st.you)
        opponentOnline = st.opponentOnline
        opponentWantsRematch = st.opponentRematch
        youWantRematch = st.youRematch
        onlinePhase = if (st.opponentJoined) OnlinePhase.PLAYING else OnlinePhase.WAITING

        if (st.game != roomGame) {
            roomGame = st.game
            flipped = humanColor == Color.BLACK
            replay(st.moves)
        } else {
            val known = localUci.size
            when {
                st.moves == localUci -> {}
                st.moves.size > known && st.moves.subList(0, known) == localUci -> {
                    for (text in st.moves.subList(known, st.moves.size)) {
                        val mv = Notation.parseUci(board, text)
                        if (mv == null) {
                            replay(st.moves)
                            break
                        }
                        applyMove(mv)
                    }
                }
                // Our own move is still on its way to the server.
                sending && st.moves.size < known && localUci.subList(0, st.moves.size) == st.moves -> {}
                else -> replay(st.moves)
            }
        }
        onlineWinner = st.resultWinner?.let { colorOf(it) }
        onlineEndReason = st.resultReason
    }

    /** Rebuild the board from the first move, without sounds. */
    private fun replay(moves: List<String>) {
        resetBoard()
        for (text in moves) {
            val mv = Notation.parseUci(board, text)
            if (mv == null) {
                onlineError = "Your board and your friend's disagree at move ${localUci.size + 1}."
                return
            }
            applyMove(mv, quiet = true)
        }
    }

    private fun sendMove(move: Move) {
        val s = seat ?: return
        val ply = localUci.size - 1
        sending = true
        viewModelScope.launch {
            try {
                val st = online.move(s, ply, Notation.uci(move))
                sending = false
                applyRoom(st)
            } catch (e: OnlineError) {
                sending = false
                onlineError = if (e.code == "network") {
                    "Your move didn't reach the server. Check the board and play it again if it's gone."
                } else {
                    OnlineClient.describe(e.code)
                }
                resync(s)
            }
        }
    }

    /** Fetch the whole room at once and match the board to it. */
    private suspend fun resync(s: Seat) {
        try {
            val st = online.state(s, 0)
            roomVersion = 0
            if (seat === s) applyRoom(st)
        } catch (e: OnlineError) {
            reconnecting = true
        }
    }

    /** Ask for another game against the same friend; colours swap when both ask. */
    fun requestRematch() {
        val s = seat ?: return
        viewModelScope.launch {
            try {
                applyRoom(online.rematch(s))
            } catch (e: OnlineError) {
                onlineError = OnlineClient.describe(e.code)
            }
        }
    }

    /** Stop polling and tell the server we left, so the friend isn't left waiting. */
    private fun leaveOnline() {
        pollJob?.cancel()
        pollJob = null
        val s = seat ?: return
        seat = null
        onlinePhase = null
        onlineCode = null
        onlineWinner = null
        onlineEndReason = null
        sending = false
        viewModelScope.launch {
            try {
                online.leave(s)
            } catch (e: OnlineError) {
                // The room expires on its own after 6 hours idle.
            }
        }
    }

    private fun colorOf(text: String): Color = if (text == "black") Color.BLACK else Color.WHITE

    /** Human concedes the game. */
    fun resign() {
        if (isGameOver) return
        if (mode == GameMode.ONLINE) {
            val s = seat ?: return
            clearSelection()
            viewModelScope.launch {
                try {
                    applyRoom(online.resign(s))
                } catch (e: OnlineError) {
                    onlineError = OnlineClient.describe(e.code)
                }
            }
            return
        }
        thinking = false
        hinting = false
        clearSelection()
        clearHint()
        resigned = true
    }

    fun backToMenu() {
        thinking = false
        hinting = false
        onlineError = null
        leaveOnline()
        inMenu = true
    }

    fun onSquareTap(sq: Int) {
        if (inMenu || thinking || hinting || pendingPromotion != null || isGameOver) return
        if (mode == GameMode.VS_AI && board.sideToMove != humanColor) return
        if (mode == GameMode.ONLINE &&
            (onlinePhase != OnlinePhase.PLAYING || sending || board.sideToMove != humanColor)
        ) return
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
            else -> playerMove(matching.first())
        }
    }

    fun choosePromotion(type: PieceType) {
        val pp = pendingPromotion ?: return
        pendingPromotion = null
        playerMove(Move(pp.first, pp.second, promotion = type))
    }

    fun cancelPromotion() {
        pendingPromotion = null
        clearSelection()
    }

    fun requestHint() {
        if (mode == GameMode.ONLINE) return
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
        if (mode == GameMode.ONLINE) return
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
        repeat(popped.coerceAtMost(localUci.size)) { localUci.removeAt(localUci.lastIndex) }
        lastMove = null
        clearHint()
        clearSelection()
        refreshStatus()
    }

    fun flipBoard() {
        flipped = !flipped
    }

    /** Change the computer's strength mid-game; takes effect on its next move. */
    fun changeDifficulty(d: Difficulty) {
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

    /** A move the player on this phone made; online games also send it to the server. */
    private fun playerMove(move: Move) {
        applyMove(move)
        if (mode == GameMode.ONLINE) sendMove(move)
    }

    private fun applyMove(move: Move, quiet: Boolean = false) {
        clearHint()
        val before = board
        val capturing = before.squares[move.to] != null || move.flag == MoveFlag.EN_PASSANT
        val castling = move.flag == MoveFlag.CASTLE_KING || move.flag == MoveFlag.CASTLE_QUEEN
        val promoting = move.promotion != null

        historyBoards.add(before)
        localUci.add(Notation.uci(move))
        val next = before.clone()
        next.makeMove(move)
        board = next
        lastMove = move
        clearSelection()
        refreshStatus()

        val mate = status.type == StatusType.CHECKMATE
        val check = status.type == StatusType.CHECK
        moveSans = moveSans + Notation.san(before, move, check, mate)

        if (!quiet) playSound(promoting, castling, capturing)
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

    private companion object {
        const val RETRY_MS = 2_000L
    }

    override fun onCleared() {
        super.onCleared()
        sound.release()
    }
}
