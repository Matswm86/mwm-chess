# ♟ MWM Chess (Android)

A clean, beginner-friendly chess game for Android. Free, open source, and made
for people who are still learning the game: pick up a piece and it shows you
exactly where it can move. Play the computer at four strengths or a friend on
the same phone. No ads, no tracking, no network, everything runs on-device.

![MWM Chess — the board, showing legal moves for the picked-up knight](docs/screenshot.png)

*The Green theme: the knight on f3 is selected, so its legal moves are marked
with dots and the capturable pawn on e5 gets a ring; the last move (…a6) stays
highlighted, with the move list and controls below. (Preview rendered from the
app's real piece set and board colours.)*

## 📲 Download

**[⬇ Latest APK (release)](https://github.com/Matswm86/mwm-chess/releases/latest/download/mwm-chess.apk)**

Sideload it: open the APK on your phone and allow *install from unknown
sources* when prompted. Android 8.0+ (minSdk 26).

Alternatively, every push builds a fresh debug APK in CI: *Actions → Build APK →
artifact `mwm-chess-debug`* (requires a GitHub login to download artifacts).

## Features

- **Full, correct chess rules** — legal moves only, castling, en passant, pawn
  promotion (you pick the piece), check, checkmate, stalemate, plus draws by the
  50-move rule, threefold repetition, and insufficient material.
- **Play the computer** at four strengths: **Easy → Medium → Hard → Expert.**
  Easy sometimes plays a loose move so beginners can win; Expert searches deep
  and doesn't. Or play **two-player pass-and-play** on one device.
- **See every legal move** — tap a piece: empty squares it can reach get a dot,
  pieces it can take get a ring.
- **A hint button** — asks the engine for the best move for your side and
  highlights it in blue.
- **Beginner touches** — last-move highlight, a red glow when your king is in
  check, a captured-piece tray with a material-lead badge, a move-history strip
  in algebraic notation, coordinate (a–h / 1–8) toggle, undo, and board flip.
- **Sound effects** for moves, captures, castling, promotion, check and
  game-over, with a one-tap mute.
- **Handsome, no-nonsense look** — the classic **Cburnett** vector piece set
  (the pieces Lichess and Wikipedia use) on authentic tournament board colours,
  four themes (Green, Wood, Blue, Slate), and light/dark mode.

## Screens

- **Menu** — choose mode (vs computer / two players), difficulty, and which
  colour you play.
- **Game** — the board, both players' captured material with a lead badge, the
  current status ("White to move", "Checkmate…"), the move list, and the
  Hint / Undo / Flip / coordinates / sound controls.

## The engine

A pure-Kotlin engine: **negamax + alpha-beta** with **quiescence search** and
**iterative deepening**, ordered by MVV-LVA, with a material + piece-square-table
evaluation. Difficulty scales the search depth and time budget (Easy also has a
small blunder chance); the AI runs off the UI thread so the board never freezes.

## Tech

Kotlin + Jetpack Compose (Material 3), single Activity, `minSdk 26`. Rules and
move generation live in `app/src/main/java/no/mwm/chess/engine/`, the search in
`…/engine/ai/`, and the Compose UI in `…/ui/`. Pieces are Android vector
drawables; the board and highlights are drawn with plain Compose `Canvas`. No
game or chart libraries.

## Build locally

APKs are normally built in the cloud by **GitHub Actions** (see
`.github/workflows/android-build.yml`), so you don't need Android Studio. To
build on your own machine you need JDK 17 and the Android SDK:

```bash
gradle wrapper --gradle-version 8.7   # first time only, generates ./gradlew
./gradlew assembleDebug               # APK at app/build/outputs/apk/debug/
```

## License

**GPL-3.0-or-later** — see [`LICENSE`](LICENSE). The Cburnett piece art is used
under GPLv2+ with attribution to Colin M.L. Burnett — see [`NOTICE.md`](NOTICE.md).
Free and open source, made for learning rather than profit.
