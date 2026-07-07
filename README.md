# ♚ MWM Chess

A clean, beginner-friendly chess game for Android. Free, open source, and made
for people who are still learning the game. Tap a piece and it shows you exactly
where it can move.

> Free / non-commercial / open source. Built for learning, not for profit.

## Features

- **Full, correct chess rules** — legal moves only, castling, en passant, pawn
  promotion (choose your piece), check, checkmate, stalemate, plus draws by
  the 50-move rule, threefold repetition, and insufficient material.
- **Play the computer** at four strengths: **Easy → Medium → Hard → Expert.**
  The engine uses negamax + alpha-beta with quiescence search and iterative
  deepening; Easy occasionally plays a loose move so beginners can win, Expert
  searches deep and does not.
- **Two-player pass-and-play** on one device.
- **See every legal move.** Pick up a piece and the squares it can go to are
  marked with a dot; pieces you can capture get a ring.
- **Beginner touches:** last-move highlight, red glow when your king is in
  check, captured-piece tray with a material lead badge, a coordinate (a–h /
  1–8) toggle, undo, board flip, and four board themes (Green, Wood, Blue,
  Slate).
- **Handsome, no-nonsense look:** the classic **Cburnett** vector piece set
  (the pieces Lichess and Wikipedia use) on authentic tournament board colours.
  Light and dark mode.

## Screens

- **Menu** — choose mode, difficulty, and which colour you play.
- **Game** — the board, both players' captured material, the current status,
  and Undo / Flip / coordinate controls.

## Build / install

Android APKs are built in the cloud by **GitHub Actions** — you don't need
Android Studio.

1. Push to `main` (or open the **Actions** tab and run **Build APK**).
2. When the run finishes, open it and download the **`mwm-chess-debug`**
   artifact. It contains `app-debug.apk`.
3. Copy the APK to your phone and open it. You may need to allow
   "install from unknown sources" for your file manager or browser.

### Building locally (optional)

Requires JDK 17 and the Android SDK.

```bash
gradle wrapper --gradle-version 8.7   # first time only, generates ./gradlew
./gradlew assembleDebug               # APK at app/build/outputs/apk/debug/
```

## Tech

- Kotlin + Jetpack Compose (Material 3), single Activity, `minSdk 26`.
- Pure-Kotlin chess engine in `app/src/main/java/no/mwm/chess/engine/`
  (rules + move generation) and `.../engine/ai/` (the search).
- No ads, no tracking, no network. Everything runs on-device.

## License

**GPL-3.0-or-later.** See [`LICENSE`](LICENSE). The Cburnett piece art is used
under GPLv2+ with attribution — see [`NOTICE.md`](NOTICE.md).
