#!/usr/bin/env python3
"""MWM Chess online rooms: the server behind chess.mwmai.no.

Standard library only. Caddy proxies /api/* to this process. Rooms live in
memory, so restarting the service ends running games.

Game flow: one player creates a room and gets a 4-letter code, a friend joins
with the code, and the two play. The server keeps the move list, checks that
each move comes from the side to move and in the right order, and relays it by
long-poll. It does not know the chess rules: both apps check every move with
their own engine, so a move one app accepts is also legal on the other.

Moves travel as UCI text ("e2e4", "e7e8q"). Every API reply is HTTP 200, with
{"ok": false, "error": ...} on failure, because players on one home Wi-Fi share
a public IP and the VPS caddy-4xx fail2ban jail bans an IP after 30 4xx
responses in 5 minutes.
"""

from __future__ import annotations

import json
import logging
import os
import re
import secrets
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = os.environ.get("CHESS_HOST", "127.0.0.1")
PORT = int(os.environ.get("CHESS_PORT", "8796"))

CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"  # no I or O, they read as 1 and 0
CODE_LEN = 4
COLORS = ("white", "black")
MOVE_RE = re.compile(r"^[a-h][1-8][a-h][1-8][qrbn]?$")
MAX_MOVES = 1200
POLL_HOLD_S = 25.0
ONLINE_GRACE_S = 12.0  # a player counts as online this long after their last poll
ROOM_IDLE_S = 6 * 3600
MAX_ROOMS = 500
CREATES_PER_IP_HOUR = 30
BODY_MAX = 1024
TICK_S = 1.0

log = logging.getLogger("mwm-chess")

clock = time.monotonic  # tests swap this for a fake clock

COND = threading.Condition()
ROOMS: dict[str, Room] = {}
CREATES: dict[str, list[float]] = {}


@dataclass
class Player:
    token: str
    last_seen: float
    polls: int = 0  # open long-polls
    online: bool = True  # last online state broadcast to the room
    rematch: bool = False
    left: bool = False  # left the room for good


@dataclass
class Room:
    code: str
    created: float
    touched: float
    seats: dict[str, Player] = field(default_factory=dict)  # "white"/"black" -> Player
    moves: list[str] = field(default_factory=list)
    result: dict | None = None  # {"reason": "resign", "winner": "white"}
    game: int = 1  # bumps on every rematch
    version: int = 1

    def color_of(self, token: str) -> str | None:
        for color, p in self.seats.items():
            if secrets.compare_digest(p.token, token):
                return color
        return None


class ApiError(Exception):
    """A user-facing failure, returned as {"ok": false, "error": code}."""


def now() -> float:
    return clock()


def other(color: str) -> str:
    return "black" if color == "white" else "white"


def bump(room: Room) -> None:
    room.version += 1
    room.touched = now()
    COND.notify_all()


def is_online(p: Player, t: float) -> bool:
    return not p.left and (p.polls > 0 or t - p.last_seen < ONLINE_GRACE_S)


def clean_code(raw) -> str:
    return re.sub(r"[^A-Z]", "", str(raw or "").upper())[:CODE_LEN]


def new_code() -> str:
    for _ in range(200):
        code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LEN))
        if code not in ROOMS:
            return code
    raise ApiError("server_busy")


def to_move(room: Room) -> str:
    return "white" if len(room.moves) % 2 == 0 else "black"


def _room(code) -> Room:
    room = ROOMS.get(clean_code(code))
    if room is None:
        raise ApiError("no_game")
    return room


def _seat(room: Room, token) -> str:
    color = room.color_of(str(token or ""))
    if color is None:
        raise ApiError("not_in_game")
    return color


def snapshot(room: Room, color: str) -> dict:
    t = now()
    opp = room.seats.get(other(color))
    me = room.seats[color]
    return {
        "ok": True,
        "v": room.version,
        "code": room.code,
        "you": color,
        "game": room.game,
        "moves": list(room.moves),
        "result": room.result,
        "opponent": {
            "joined": opp is not None,
            "online": opp is not None and is_online(opp, t),
            "rematch": opp is not None and opp.rematch,
        },
        "rematch": me.rematch,
    }


# ---------------------------------------------------------------- actions


def api_create(data: dict, ip: str) -> dict:
    pick = str(data.get("color") or "random").lower()
    if pick not in ("white", "black", "random"):
        raise ApiError("bad_request")
    t = now()
    with COND:
        recent = [c for c in CREATES.get(ip, []) if t - c < 3600]
        if len(recent) >= CREATES_PER_IP_HOUR:
            raise ApiError("too_many_games")
        if len(ROOMS) >= MAX_ROOMS:
            raise ApiError("server_busy")
        recent.append(t)
        CREATES[ip] = recent
        color = pick if pick != "random" else secrets.choice(COLORS)
        room = Room(code=new_code(), created=t, touched=t)
        room.seats[color] = Player(token=secrets.token_urlsafe(18), last_seen=t)
        ROOMS[room.code] = room
        log.info("room %s created", room.code)
        return {"ok": True, "code": room.code, "token": room.seats[color].token, "you": color}


def api_join(data: dict, ip: str) -> dict:
    with COND:
        room = _room(data.get("code"))
        free = [c for c in COLORS if c not in room.seats]
        if not free:
            raise ApiError("game_full")
        color = free[0]
        room.seats[color] = Player(token=secrets.token_urlsafe(18), last_seen=now())
        bump(room)
        log.info("room %s joined", room.code)
        return {"ok": True, "code": room.code, "token": room.seats[color].token, "you": color}


def api_move(data: dict, ip: str) -> dict:
    move = str(data.get("move") or "")
    try:
        ply = int(data.get("ply"))
    except (TypeError, ValueError):
        raise ApiError("bad_request") from None
    if not MOVE_RE.match(move):
        raise ApiError("bad_move")
    with COND:
        room = _room(data.get("code"))
        color = _seat(room, data.get("token"))
        if room.result is not None:
            raise ApiError("game_over")
        if len(room.seats) < 2:
            raise ApiError("no_opponent")
        if ply != len(room.moves):
            raise ApiError("out_of_sync")
        if to_move(room) != color:
            raise ApiError("not_your_turn")
        if len(room.moves) >= MAX_MOVES:
            raise ApiError("game_over")
        room.moves.append(move)
        bump(room)
        return snapshot(room, color)


def api_resign(data: dict, ip: str) -> dict:
    with COND:
        room = _room(data.get("code"))
        color = _seat(room, data.get("token"))
        if room.result is None:
            room.result = {"reason": "resign", "winner": other(color)}
            bump(room)
        return snapshot(room, color)


def api_rematch(data: dict, ip: str) -> dict:
    """Ask for a new game in the same room; it starts when both players ask. Colours swap."""
    with COND:
        room = _room(data.get("code"))
        color = _seat(room, data.get("token"))
        room.seats[color].rematch = True
        if len(room.seats) == 2 and all(p.rematch for p in room.seats.values()):
            room.seats = {other(c): p for c, p in room.seats.items()}
            for p in room.seats.values():
                p.rematch = False
            room.moves = []
            room.result = None
            room.game += 1
            color = other(color)
        bump(room)
        return snapshot(room, color)


def api_leave(data: dict, ip: str) -> dict:
    """The player left the room for good; the opponent wins if the game was still on."""
    with COND:
        room = _room(data.get("code"))
        color = _seat(room, data.get("token"))
        if room.result is None and len(room.seats) == 2:
            room.result = {"reason": "left", "winner": other(color)}
        room.seats[color].left = True
        room.seats[color].rematch = False
        bump(room)
        return {"ok": True}


def api_state(code, token, since: int) -> dict:
    deadline = now() + POLL_HOLD_S
    with COND:
        room = _room(code)
        color = _seat(room, token)
        p = room.seats[color]
        p.last_seen = now()
        p.polls += 1
        try:
            while room.version <= since and ROOMS.get(room.code) is room:
                left = deadline - now()
                if left <= 0:
                    break
                COND.wait(left)
        finally:
            p.polls -= 1
            p.last_seen = now()
        if ROOMS.get(room.code) is not room:
            raise ApiError("no_game")
        color = _seat(room, token)  # a rematch may have swapped the seats
        return snapshot(room, color)


POST_ACTIONS = {
    "/api/create": api_create,
    "/api/join": api_join,
    "/api/move": api_move,
    "/api/resign": api_resign,
    "/api/rematch": api_rematch,
    "/api/leave": api_leave,
}


# ---------------------------------------------------------------- housekeeping


def tick() -> None:
    """Expire idle rooms and tell each room when a player drops or returns."""
    t = now()
    with COND:
        for code, room in list(ROOMS.items()):
            if t - room.touched > ROOM_IDLE_S:
                del ROOMS[code]
                COND.notify_all()
                log.info("room %s expired", code)
                continue
            changed = False
            for p in room.seats.values():
                online = is_online(p, t)
                if online != p.online:
                    p.online = online
                    changed = True
            if changed:
                room.version += 1
                COND.notify_all()
        for ip, stamps in list(CREATES.items()):
            if all(t - s >= 3600 for s in stamps):
                del CREATES[ip]


def ticker() -> None:
    while True:
        time.sleep(TICK_S)
        try:
            tick()
        except Exception:
            log.exception("tick failed")


# ---------------------------------------------------------------- HTTP


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "MWMChess"
    sys_version = ""

    def log_message(self, fmt, *args) -> None:  # Caddy keeps the access log
        pass

    def client_ip(self) -> str:
        forwarded = self.headers.get("X-Forwarded-For", "")
        return forwarded.split(",")[-1].strip() or self.client_address[0]

    def send_json(self, obj: dict) -> None:
        body = json.dumps(obj, separators=(",", ":")).encode("utf-8")
        try:
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            self.close_connection = True

    def do_GET(self) -> None:
        url = urlparse(self.path)
        qs = parse_qs(url.query)
        arg = lambda k: (qs.get(k) or [""])[0]  # noqa: E731
        try:
            if url.path == "/api/state":
                try:
                    since = int(arg("v") or 0)
                except ValueError:
                    since = 0
                self.send_json(api_state(arg("g"), arg("p"), since))
            elif url.path == "/api/health":
                with COND:
                    self.send_json({"ok": True, "rooms": len(ROOMS)})
            else:
                self.send_json({"ok": False, "error": "not_found"})
        except ApiError as e:
            self.send_json({"ok": False, "error": str(e)})
        except Exception:
            log.exception("GET %s failed", url.path)
            self.send_json({"ok": False, "error": "server_error"})

    def do_POST(self) -> None:
        url = urlparse(self.path)
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = -1
        if length < 0 or length > BODY_MAX:
            self.close_connection = True
            self.send_json({"ok": False, "error": "bad_request"})
            return
        raw = self.rfile.read(length) if length else b""
        action = POST_ACTIONS.get(url.path)
        try:
            data = json.loads(raw or b"{}")
        except ValueError:
            data = None
        if action is None or not isinstance(data, dict):
            self.send_json({"ok": False, "error": "bad_request"})
            return
        try:
            self.send_json(action(data, self.client_ip()))
        except ApiError as e:
            self.send_json({"ok": False, "error": str(e)})
        except Exception:
            log.exception("POST %s failed", url.path)
            self.send_json({"ok": False, "error": "server_error"})


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    threading.Thread(target=ticker, daemon=True).start()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.daemon_threads = True
    log.info("listening on %s:%d", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()
