package no.mwm.chess.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One room seat: the room code, this phone's secret token, and our colour. */
data class Seat(val code: String, val token: String, val color: String)

/** A room as the server sees it; [moves] are UCI strings from move 1. */
data class RoomState(
    val version: Int,
    val you: String,
    val game: Int,
    val moves: List<String>,
    val resultReason: String?,
    val resultWinner: String?,
    val opponentJoined: Boolean,
    val opponentOnline: Boolean,
    val opponentRematch: Boolean,
    val youRematch: Boolean,
)

/** A failure from the room server ("no_game", "game_full", ...) or "network" when it could not be reached. */
class OnlineError(val code: String) : Exception(code)

/**
 * Talks to the room server at chess.mwmai.no. Every call runs on the IO
 * dispatcher and throws [OnlineError] on failure. The server answers HTTP 200
 * for errors too, with {"ok": false, "error": "..."}.
 */
class OnlineClient(private val base: String = BASE_URL) {

    suspend fun create(color: String): Seat = seat(post("/api/create", JSONObject().put("color", color)))

    suspend fun join(code: String): Seat = seat(post("/api/join", JSONObject().put("code", code)))

    suspend fun move(seat: Seat, ply: Int, uci: String): RoomState =
        room(post("/api/move", auth(seat).put("ply", ply).put("move", uci)))

    suspend fun resign(seat: Seat): RoomState = room(post("/api/resign", auth(seat)))

    suspend fun rematch(seat: Seat): RoomState = room(post("/api/rematch", auth(seat)))

    suspend fun leave(seat: Seat) {
        post("/api/leave", auth(seat))
    }

    /** Waits up to ~25 s on the server for anything newer than [since], then returns the room. */
    suspend fun state(seat: Seat, since: Int): RoomState {
        val q = "g=${enc(seat.code)}&p=${enc(seat.token)}&v=$since"
        return room(request("GET", "/api/state?$q", null, POLL_READ_TIMEOUT_MS))
    }

    private fun auth(seat: Seat) = JSONObject().put("code", seat.code).put("token", seat.token)

    private suspend fun post(path: String, body: JSONObject): JSONObject =
        request("POST", path, body.toString(), READ_TIMEOUT_MS)

    private suspend fun request(method: String, path: String, body: String?, readTimeout: Int): JSONObject =
        withContext(Dispatchers.IO) {
            val json = try {
                val conn = URL(base + path).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method
                    conn.connectTimeout = CONNECT_TIMEOUT_MS
                    conn.readTimeout = readTimeout
                    conn.setRequestProperty("User-Agent", "mwm-chess-android")
                    conn.setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.outputStream.use { it.write(body.toByteArray()) }
                    }
                    if (conn.responseCode != 200) throw OnlineError("network")
                    JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                throw OnlineError("network")
            } catch (e: org.json.JSONException) {
                throw OnlineError("network")
            }
            if (!json.optBoolean("ok", false)) throw OnlineError(json.optString("error", "server_error"))
            json
        }

    private fun seat(j: JSONObject) = Seat(j.getString("code"), j.getString("token"), j.getString("you"))

    private fun room(j: JSONObject): RoomState {
        val moves = j.getJSONArray("moves")
        val result = j.optJSONObject("result")
        val opp = j.getJSONObject("opponent")
        return RoomState(
            version = j.getInt("v"),
            you = j.getString("you"),
            game = j.getInt("game"),
            moves = List(moves.length()) { moves.getString(it) },
            resultReason = result?.optString("reason"),
            resultWinner = result?.optString("winner"),
            opponentJoined = opp.getBoolean("joined"),
            opponentOnline = opp.getBoolean("online"),
            opponentRematch = opp.getBoolean("rematch"),
            youRematch = j.getBoolean("rematch"),
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val BASE_URL = "https://chess.mwmai.no"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val POLL_READ_TIMEOUT_MS = 40_000

        /** Plain-English text for an [OnlineError] code. */
        fun describe(code: String): String = when (code) {
            "no_game" -> "No game with that code. Check the letters and try again."
            "game_full" -> "That game already has two players."
            "network" -> "Can't reach the game server. Check your internet connection."
            "too_many_games" -> "Too many new games from this network. Try again in a while."
            "server_busy" -> "The game server is busy. Try again in a minute."
            "not_in_game" -> "This game has ended on the server."
            "out_of_sync" -> "The board got out of step with your opponent's; reloading it."
            else -> "Something went wrong ($code)."
        }
    }
}
