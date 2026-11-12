# Wise4Chess

An offline chess app for Android: play, analyse and study in one place. Free and open source,
no ads, no accounts, no tracking.

> **Status:** early development. The chess core (rules, FEN, SAN, PGN, Chess960) is done;
> there is no playable board yet.

## Goals

- **Play** against engines of any strength and style, or a friend on the same device.
- **Analyse** with a live multi-line engine view and a move-by-move game review.
- **Puzzles** matched to your rating.
- **Openings** with variations and spaced repetition, then continue the game against a bot.
- **Game library** with PGN import and export.

Everything runs on the device. Engine code ships inside the app; large neural networks are
optional downloads.

## Building

Requires JDK 21 (Gradle fetches it automatically) and the Android SDK (platform 37).

```sh
./gradlew :app:assembleDebug                    # debug APK
./gradlew :core:chess:test testDebugUnitTest    # unit tests
```

## Project layout

| Module | Contents |
|---|---|
| `:app` | Entry point, navigation, screens |
| `:core:chess` | Chess rules and notation in pure Kotlin, no Android dependencies |
| `:core:ui` | Theme and shared composables |

Reference data for the chess core's tests is generated with
[python-chess](https://github.com/niklasf/python-chess) by `tools/chess-fixtures/generate.py`.

## License

[GNU General Public License v3.0 or later](LICENSE). Third-party components are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
