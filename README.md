# Wise4Chess

An offline chess app for Android: play, analyse and study in one place. Free and open source,
no ads, no accounts, no tracking.

> **Status:** early development. You can play against Stockfish or CuckooChess at a chosen strength or
> against a friend on the same device, and set up positions. Analysis, puzzles and openings
> are still to come.

## Goals

- **Play** against engines of any strength and style, or a friend on the same device.
- **Analyse** with a live multi-line engine view and a move-by-move game review.
- **Puzzles** matched to your rating.
- **Openings** with variations and spaced repetition, then continue the game against a bot.
- **Game library** with PGN import and export.

Everything runs on the device. Engine code ships inside the app; large neural networks are
optional downloads.

## Building

Requires JDK 21 (Gradle fetches it automatically), the Android SDK (platform 37) and, for the
chess engine, the Android NDK 30.0.16248370 plus `make` and `git` on the build machine.

Stockfish is a git submodule, so clone with `--recurse-submodules` or run
`git submodule update --init` afterwards.

```sh
./gradlew :app:assembleDebug                                 # debug APK
./gradlew :core:chess:test :core:engine:test :engines:cuckoo:test testDebugUnitTest   # unit tests
```

## Project layout

| Module | Contents |
|---|---|
| `:app` | Entry point, navigation, screens |
| `:core:chess` | Chess rules and notation in pure Kotlin, no Android dependencies |
| `:core:engine` | UCI engine client in pure Kotlin |
| `:core:ui` | Theme, the chess board and other shared composables |
| `:engines:stockfish` | Builds Stockfish for Android and launches it |
| `:engines:cuckoo` | CuckooChess, a Java engine for the weaker levels |

Reference data for the chess core's tests is generated with
[python-chess](https://github.com/niklasf/python-chess) by `tools/chess-fixtures/generate.py`.

## License

[GNU General Public License v3.0 or later](LICENSE). Third-party components are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
