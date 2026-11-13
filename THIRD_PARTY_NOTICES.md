# Third-party notices

Wise4Chess is licensed under the GNU General Public License v3.0 or later (see `LICENSE`).
This file lists third-party code, data and artwork that ships with the app. Every addition
must be recorded here together with its source and license.

## Libraries

| Component | Source | License |
|---|---|---|
| AndroidX (Core, Activity, Lifecycle, Navigation, Compose, Material 3) | <https://developer.android.com/jetpack/androidx> | Apache-2.0 |
| Kotlin standard library, kotlinx.serialization | <https://github.com/JetBrains/kotlin>, <https://github.com/Kotlin/kotlinx.serialization> | Apache-2.0 |

## Artwork

| Component | Source | License |
|---|---|---|
| Chess pieces "cburnett" by Colin M.L. Burnett (`core/ui/src/main/res/drawable/piece_*.xml`, converted from the SVG files in `tools/pieces/cburnett`) | <https://github.com/lichess-org/lila/tree/master/public/piece/cburnett> | GPL-2.0-or-later |
| Material Symbols icons (`core/ui/src/main/res/drawable/ic_*.xml`) | <https://github.com/google/material-design-icons> | Apache-2.0 |

The app icon and the sound effects (synthesised by `tools/sounds/generate.py`) are original
work for this project.

## Engines and data

| Component | Source | License |
|---|---|---|
| Stockfish 19 chess engine, built from the `third_party/stockfish` submodule and shipped as `libstockfish_lite.so` | <https://github.com/official-stockfish/Stockfish> | GPL-3.0-or-later |
| "Simple mirrored piece square features" patch by sscg13, which lets Stockfish use a small network (`engines/stockfish/patches/lite`) | <https://github.com/lichess-org/stockfish-web/tree/main/patches/sf_19_smallnet> | GPL-3.0-or-later |
| CuckooChess chess engine by Peter Österlund, with the UCI control code from DroidFish (`engines/cuckoo`, see its README) | <https://github.com/peterosterlund2/droidfish> | GPL-3.0-or-later |
| Evaluation network `nn-61e7af4bb97d.nnue` (`engines/stockfish/nets`), embedded in the engine binary | <https://tests.stockfishchess.org/nns> | CC0-1.0 |

## Development tools (not shipped in the app)

| Component | Source | License |
|---|---|---|
| JUnit 4 | <https://junit.org/junit4/> | EPL-1.0 |
| Robolectric | <https://github.com/robolectric/robolectric> | MIT |
| Roborazzi | <https://github.com/takahirom/roborazzi> | Apache-2.0 |
| python-chess (generates reference data for the tests) | <https://github.com/niklasf/python-chess> | GPL-3.0-or-later |
