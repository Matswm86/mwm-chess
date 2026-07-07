package no.mwm.chess.ui

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import com.google.android.filament.Engine
import com.google.android.filament.LightManager
import io.github.sceneview.Scene
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import no.mwm.chess.engine.Color
import no.mwm.chess.engine.Piece
import no.mwm.chess.engine.PieceType
import no.mwm.chess.engine.fileOf
import no.mwm.chess.engine.rankOf
import no.mwm.chess.engine.squareOf
import kotlin.math.abs

/**
 * Board geometry, in the native units of chess_set.glb (produced by the splitter).
 * Files a..h run along +x; ranks 1..8 run along z from White's side (+z) to Black's (-z).
 * The playing surface is at y = [surfaceY]; every piece .glb is centred at the origin
 * with its base resting on that surface, so a piece placed at y = 0 stands correctly.
 */
object BoardGeo {
    val fileX = floatArrayOf(-7.0711f, -5.064f, -3.057f, -1.0499f, 0.9572f, 2.9643f, 4.9713f, 6.9784f)
    val rankZ = floatArrayOf(6.9633f, 4.9672f, 2.9711f, 0.975f, -1.0211f, -3.0172f, -5.0133f, -7.0093f)
    const val surfaceY = 0.06f
    private const val HALF = 1.0f

    fun position(sq: Int): Position = Position(fileX[fileOf(sq)], 0f, rankZ[rankOf(sq)])

    fun nearestFile(x: Float): Int? = nearest(fileX, x)
    fun nearestRank(z: Float): Int? = nearest(rankZ, z)

    private fun nearest(arr: FloatArray, v: Float): Int? {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in arr.indices) {
            val d = abs(arr[i] - v)
            if (d < bestD) { bestD = d; best = i }
        }
        return if (best >= 0 && bestD <= HALF) best else null
    }

    /** A slot in a captured-piece line-up just beyond the board frame. */
    fun capturedSlot(index: Int, nearSide: Boolean): Position {
        val step = 1.15f
        val startX = -7.5f
        val z = if (nearSide) 10.7f else -10.7f
        return Position(startX + index * step, 0f, z)
    }
}

/** Flat discs laid on the board to signal state. Material colour is fixed per kind. */
private enum class Marker(val color: ComposeColor, val radius: Float) {
    SELECT(ComposeColor(0xFFF5CF67), 0.92f),
    MOVE(ComposeColor(0xFFE6C86A), 0.30f),
    CAPTURE(ComposeColor(0xFFCE4A3C), 0.94f),
    CHECK(ComposeColor(0xFFD8493D), 0.94f),
}

/**
 * Owns every Filament node for the board. All piece and marker instances are created
 * once and added to the scene up-front; each [sync] only toggles visibility and moves
 * transforms (Filament reads those every frame), so the scene-graph list never churns.
 */
private class Chess3DController(
    private val engine: Engine,
    private val modelLoader: ModelLoader,
    private val materialLoader: MaterialLoader,
    private val nodes: SnapshotStateList<Node>,
) {
    private val pieceInstances = LinkedHashMap<String, List<ModelNode>>()
    private val markerInstances = LinkedHashMap<Marker, List<CylinderNode>>()
    private val cursor = HashMap<String, Int>()
    private val markerCursor = HashMap<Marker, Int>()
    var ready = false
        private set

    // generous instance budgets (cover promotions); pawns can never exceed 8 per side.
    private val counts = mapOf("p" to 8, "n" to 10, "b" to 10, "r" to 10, "q" to 10, "k" to 1)
    private val markerCounts = mapOf(Marker.MOVE to 32, Marker.CAPTURE to 12, Marker.SELECT to 2, Marker.CHECK to 2)

    fun load() {
        nodes.add(ModelNode(modelLoader.createModelInstance("models/board.glb")).apply {
            position = Position(0f, 0f, 0f)
        })
        for (c in listOf("w", "b")) for ((t, n) in counts) {
            val list = modelLoader.createInstancedModel("models/$c$t.glb", n)
                .map { ModelNode(it).apply { isVisible = false } }
            list.forEach { nodes.add(it) }
            pieceInstances[c + t] = list
        }
        for ((kind, n) in markerCounts) {
            val list = (0 until n).map {
                CylinderNode(
                    engine = engine,
                    radius = kind.radius,
                    height = 0.06f,
                    materialInstance = materialLoader.createColorInstance(
                        color = kind.color, metallic = 0f, roughness = 0.55f, reflectance = 0.2f,
                    ),
                ).apply { isVisible = false }
            }
            list.forEach { nodes.add(it) }
            markerInstances[kind] = list
        }
        ready = true
    }

    private fun keyOf(p: Piece): String {
        val c = if (p.color == Color.WHITE) "w" else "b"
        val t = when (p.type) {
            PieceType.PAWN -> "p"; PieceType.KNIGHT -> "n"; PieceType.BISHOP -> "b"
            PieceType.ROOK -> "r"; PieceType.QUEEN -> "q"; PieceType.KING -> "k"
        }
        return c + t
    }

    private fun nextPiece(key: String): ModelNode? {
        val list = pieceInstances[key] ?: return null
        val i = cursor.getOrDefault(key, 0)
        if (i >= list.size) return null
        cursor[key] = i + 1
        return list[i]
    }

    private fun nextMarker(kind: Marker): CylinderNode? {
        val list = markerInstances[kind] ?: return null
        val i = markerCursor.getOrDefault(kind, 0)
        if (i >= list.size) return null
        markerCursor[kind] = i + 1
        return list[i]
    }

    fun placeCamera(camera: CameraNode, flipped: Boolean) {
        val z = if (flipped) -17.5f else 17.5f
        camera.position = Position(0f, 18.5f, z)
        camera.lookAt(Position(0f, 0f, if (flipped) 0.5f else -0.5f))
    }

    fun sync(vm: ChessViewModel) {
        if (!ready) return
        pieceInstances.values.forEach { l -> l.forEach { it.isVisible = false } }
        markerInstances.values.forEach { l -> l.forEach { it.isVisible = false } }
        cursor.clear()
        markerCursor.clear()

        // ----- state discs (drawn under pieces) -----
        vm.checkSquare?.let { placeMarker(Marker.CHECK, it) }
        vm.selected?.let { placeMarker(Marker.SELECT, it) }
        for (t in vm.legalTargets) {
            placeMarker(if (vm.board.squares[t] != null) Marker.CAPTURE else Marker.MOVE, t)
        }

        // ----- pieces on the board -----
        for (sq in 0..63) {
            val p = vm.board.squares[sq] ?: continue
            val node = nextPiece(keyOf(p)) ?: continue
            node.scale = Scale(1f, 1f, 1f)
            node.position = BoardGeo.position(sq)
            node.isVisible = true
        }

        // ----- captured pieces, lined up off-board -----
        lineUp(captured(vm, Color.BLACK), nearSide = true)  // Black pieces taken -> White's side (near)
        lineUp(captured(vm, Color.WHITE), nearSide = false) // White pieces taken -> Black's side (far)
    }

    private fun placeMarker(kind: Marker, sq: Int) {
        val d = nextMarker(kind) ?: return
        val p = BoardGeo.position(sq)
        d.position = Position(p.x, 0.1f, p.z)
        d.isVisible = true
    }

    private fun lineUp(pieces: List<Piece>, nearSide: Boolean) {
        pieces.forEachIndexed { i, p ->
            val node = nextPiece(keyOf(p)) ?: return@forEachIndexed
            node.scale = Scale(0.55f, 0.55f, 0.55f)
            node.position = BoardGeo.capturedSlot(i, nearSide)
            node.isVisible = true
        }
    }

    /**
     * Pieces of [color] that have been captured, most valuable first. Promotions are
     * netted out: a pawn that promoted is on the board as another piece, not captured,
     * so its lost-pawn slot is cancelled by the surplus promoted piece.
     */
    private fun captured(vm: ChessViewModel, color: Color): List<Piece> {
        val start = mapOf(
            PieceType.QUEEN to 1, PieceType.ROOK to 2, PieceType.BISHOP to 2,
            PieceType.KNIGHT to 2, PieceType.PAWN to 8,
        )
        val have = HashMap<PieceType, Int>()
        for (sq in 0..63) {
            val p = vm.board.squares[sq] ?: continue
            if (p.color == color) have[p.type] = (have[p.type] ?: 0) + 1
        }
        var promotions = 0
        for (t in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
            promotions += ((have[t] ?: 0) - (start[t] ?: 0)).coerceAtLeast(0)
        }
        val out = ArrayList<Piece>()
        for (t in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
            repeat(((start[t] ?: 0) - (have[t] ?: 0)).coerceAtLeast(0)) { out.add(Piece(t, color)) }
        }
        val lostPawns = ((8 - (have[PieceType.PAWN] ?: 0)) - promotions).coerceAtLeast(0)
        repeat(lostPawns) { out.add(Piece(PieceType.PAWN, color)) }
        return out
    }

    fun handleTap(camera: CameraNode, x: Float, y: Float, vm: ChessViewModel) {
        val ray = camera.screenPointToRay(x, y)
        val o = ray.origin
        val d = ray.direction
        if (abs(d.y) < 1e-5f) return
        val t = (BoardGeo.surfaceY - o.y) / d.y
        if (t <= 0f) return
        val wx = o.x + d.x * t
        val wz = o.z + d.z * t
        val file = BoardGeo.nearestFile(wx) ?: return
        val rank = BoardGeo.nearestRank(wz) ?: return
        vm.onSquareTap(squareOf(file, rank))
    }
}

@Composable
fun Board3DView(vm: ChessViewModel, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val sceneNodes = rememberNodes()
    val cameraNode = rememberCameraNode(engine) {
        near = 0.5f
        far = 300f
        position = Position(0f, 18.5f, 17.5f)
        lookAt(Position(0f, 0f, -0.5f))
    }
    val keyLight = rememberMainLightNode(engine)
    // fill lights so no face of a piece renders fully black (no IBL is bundled).
    val fillLeft = remember(engine) {
        LightNode(engine, LightManager.Type.DIRECTIONAL) {
            color(1.0f, 0.96f, 0.88f); intensity(45_000f); direction(0.6f, -0.5f, 0.6f); castShadows(false)
        }
    }
    val fillRight = remember(engine) {
        LightNode(engine, LightManager.Type.DIRECTIONAL) {
            color(0.9f, 0.95f, 1.0f); intensity(35_000f); direction(-0.6f, -0.4f, -0.5f); castShadows(false)
        }
    }
    val controller = remember(engine) {
        Chess3DController(engine, modelLoader, materialLoader, sceneNodes)
    }

    LaunchedEffect(controller) {
        if (!controller.ready) {
            controller.load()
            sceneNodes.add(fillLeft)
            sceneNodes.add(fillRight)
        }
        controller.placeCamera(cameraNode, vm.flipped)
        controller.sync(vm)
    }
    LaunchedEffect(vm.flipped) { controller.placeCamera(cameraNode, vm.flipped) }
    LaunchedEffect(vm.board, vm.selected, vm.legalTargets, vm.lastMove, vm.checkSquare) {
        controller.sync(vm)
    }

    Box(modifier) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            cameraNode = cameraNode,
            cameraManipulator = null, // fixed 3/4 view so taps map cleanly to squares
            childNodes = sceneNodes,
            mainLightNode = keyLight,
            isOpaque = false, // let the felt-table gradient behind the Scene show through
            onTouchEvent = { e, _ ->
                if (e.action == MotionEvent.ACTION_UP) controller.handleTap(cameraNode, e.x, e.y, vm)
                true
            },
        )
    }
}
