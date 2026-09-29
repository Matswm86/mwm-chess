"""Tests for the MWM Chess room server. Run: python3 -m unittest test_server -q"""

from __future__ import annotations

import json
import threading
import unittest
import urllib.request
from http.server import ThreadingHTTPServer

import server as S


class FakeClock:
    def __init__(self) -> None:
        self.t = 1000.0

    def __call__(self) -> float:
        return self.t


class RoomTests(unittest.TestCase):
    def setUp(self) -> None:
        self.clock = FakeClock()
        S.clock = self.clock
        S.ROOMS.clear()
        S.CREATES.clear()
        S.POLL_HOLD_S = 0.0  # state calls return at once unless a test needs the wait

    def pair(self, color: str = "white"):
        host = S.api_create({"color": color}, "1.1.1.1")
        guest = S.api_join({"code": host["code"].lower()}, "2.2.2.2")
        return host, guest

    def move(self, who: dict, ply: int, mv: str) -> dict:
        return S.api_move({"code": who["code"], "token": who["token"], "ply": ply, "move": mv}, "x")

    def test_create_and_join_gives_opposite_colours(self):
        host, guest = self.pair("black")
        self.assertEqual(host["you"], "black")
        self.assertEqual(guest["you"], "white")
        self.assertEqual(len(host["code"]), 4)
        self.assertNotRegex(host["code"], "[IO]")

    def test_random_colour_is_one_of_two(self):
        host = S.api_create({}, "1.1.1.1")
        self.assertIn(host["you"], S.COLORS)

    def test_third_player_is_refused(self):
        host, _ = self.pair()
        with self.assertRaisesRegex(S.ApiError, "game_full"):
            S.api_join({"code": host["code"]}, "3.3.3.3")

    def test_unknown_code(self):
        with self.assertRaisesRegex(S.ApiError, "no_game"):
            S.api_join({"code": "ZZZZ"}, "1.1.1.1")

    def test_moves_alternate_and_relay(self):
        host, guest = self.pair("white")
        self.move(host, 0, "e2e4")
        with self.assertRaisesRegex(S.ApiError, "not_your_turn"):
            self.move(host, 1, "d2d4")
        self.move(guest, 1, "e7e5")
        state = S.api_state(host["code"], host["token"], 0)
        self.assertEqual(state["moves"], ["e2e4", "e7e5"])
        self.assertTrue(state["opponent"]["joined"])

    def test_stale_ply_is_out_of_sync(self):
        host, guest = self.pair("white")
        self.move(host, 0, "e2e4")
        with self.assertRaisesRegex(S.ApiError, "out_of_sync"):
            self.move(guest, 0, "e7e5")

    def test_move_before_opponent_joins(self):
        host = S.api_create({"color": "white"}, "1.1.1.1")
        with self.assertRaisesRegex(S.ApiError, "no_opponent"):
            self.move(host, 0, "e2e4")

    def test_bad_move_text_and_bad_token(self):
        host, _ = self.pair("white")
        with self.assertRaisesRegex(S.ApiError, "bad_move"):
            self.move(host, 0, "e2e9")
        with self.assertRaisesRegex(S.ApiError, "not_in_game"):
            S.api_move({"code": host["code"], "token": "nope", "ply": 0, "move": "e2e4"}, "x")
        with self.assertRaisesRegex(S.ApiError, "bad_request"):
            S.api_move(
                {"code": host["code"], "token": host["token"], "ply": "x", "move": "e2e4"}, "x"
            )

    def test_promotion_move_text_is_accepted(self):
        self.assertTrue(S.MOVE_RE.match("e7e8q"))
        self.assertFalse(S.MOVE_RE.match("e7e8k"))

    def test_resign_ends_game_and_blocks_moves(self):
        host, guest = self.pair("white")
        state = S.api_resign({"code": host["code"], "token": host["token"]}, "x")
        self.assertEqual(state["result"], {"reason": "resign", "winner": "black"})
        with self.assertRaisesRegex(S.ApiError, "game_over"):
            self.move(host, 0, "e2e4")

    def test_rematch_needs_both_and_swaps_colours(self):
        host, guest = self.pair("white")
        self.move(host, 0, "e2e4")
        S.api_resign({"code": guest["code"], "token": guest["token"]}, "x")
        first = S.api_rematch({"code": host["code"], "token": host["token"]}, "x")
        self.assertEqual(first["game"], 1)
        self.assertTrue(first["rematch"])
        second = S.api_rematch({"code": guest["code"], "token": guest["token"]}, "x")
        self.assertEqual(second["game"], 2)
        self.assertEqual(second["you"], "white")
        self.assertEqual(second["moves"], [])
        self.assertIsNone(second["result"])
        host_view = S.api_state(host["code"], host["token"], 0)
        self.assertEqual(host_view["you"], "black")
        self.assertFalse(host_view["rematch"])

    def test_leave_hands_the_win_and_shows_offline(self):
        host, guest = self.pair("white")
        S.api_leave({"code": guest["code"], "token": guest["token"]}, "x")
        state = S.api_state(host["code"], host["token"], 0)
        self.assertEqual(state["result"], {"reason": "left", "winner": "white"})
        self.assertFalse(state["opponent"]["online"])

    def test_opponent_goes_offline_after_grace(self):
        host, guest = self.pair("white")
        self.clock.t += S.ONLINE_GRACE_S + 1
        S.api_state(host["code"], host["token"], 0)  # host polls, guest does not
        v_before = S.ROOMS[host["code"]].version
        S.tick()
        state = S.api_state(host["code"], host["token"], 0)
        self.assertFalse(state["opponent"]["online"])
        self.assertGreater(state["v"], v_before)

    def test_idle_room_expires(self):
        host, _ = self.pair()
        self.clock.t += S.ROOM_IDLE_S + 1
        S.tick()
        self.assertNotIn(host["code"], S.ROOMS)

    def test_create_rate_limit_per_ip(self):
        for _ in range(S.CREATES_PER_IP_HOUR):
            S.api_create({}, "9.9.9.9")
        with self.assertRaisesRegex(S.ApiError, "too_many_games"):
            S.api_create({}, "9.9.9.9")
        S.api_create({}, "8.8.8.8")
        self.clock.t += 3601
        S.api_create({}, "9.9.9.9")

    def test_long_poll_wakes_on_move(self):
        S.clock = __import__("time").monotonic
        S.POLL_HOLD_S = 5.0
        host, guest = self.pair("white")
        v = S.ROOMS[host["code"]].version
        out: dict = {}

        def wait() -> None:
            out["state"] = S.api_state(guest["code"], guest["token"], v)

        th = threading.Thread(target=wait)
        th.start()
        self.move(host, 0, "e2e4")
        th.join(3.0)
        self.assertFalse(th.is_alive(), "long-poll did not wake on the move")
        self.assertEqual(out["state"]["moves"], ["e2e4"])


class HttpTests(unittest.TestCase):
    """One real round trip through the HTTP handler on a random local port."""

    def setUp(self) -> None:
        S.clock = __import__("time").monotonic
        S.ROOMS.clear()
        S.CREATES.clear()
        S.POLL_HOLD_S = 0.0
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), S.Handler)
        self.base = f"http://127.0.0.1:{self.httpd.server_address[1]}"
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def tearDown(self) -> None:
        self.httpd.shutdown()
        self.httpd.server_close()

    def call(self, path: str, body: dict | None = None) -> tuple[int, dict]:
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(
            self.base + path, data=data, method="POST" if body is not None else "GET"
        )
        with urllib.request.urlopen(req, timeout=5) as r:
            return r.status, json.loads(r.read())

    def test_round_trip(self):
        status, host = self.call("/api/create", {"color": "white"})
        self.assertEqual(status, 200)
        _, guest = self.call("/api/join", {"code": host["code"]})
        self.call(
            "/api/move", {"code": host["code"], "token": host["token"], "ply": 0, "move": "g1f3"}
        )
        _, state = self.call(f"/api/state?g={guest['code']}&p={guest['token']}&v=0")
        self.assertEqual(state["moves"], ["g1f3"])
        self.assertEqual(self.call("/api/health")[1]["rooms"], 1)

    def test_errors_are_http_200(self):
        status, body = self.call("/api/join", {"code": "QQQQ"})
        self.assertEqual((status, body), (200, {"ok": False, "error": "no_game"}))
        status, body = self.call("/api/nothing")
        self.assertEqual((status, body["error"]), (200, "not_found"))


if __name__ == "__main__":
    unittest.main()
