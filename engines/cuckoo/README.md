# CuckooChess

A chess engine written in Java by Peter Österlund, known for a strength setting that goes all
the way down to beginner level.

The files under `src/main/java/chess`, `src/main/java/org/petero` and `src/main/resources` are
copied unchanged from DroidFish (GPL-3.0-or-later):

- repository: <https://github.com/peterosterlund2/droidfish>
- commit: `06948d542f5b354624a8d88fc5d6f3019669e539` (2024-02-25, version 1.90)
- `CuckooChessEngine/src/main/java/chess` → `src/main/java/chess`
- `CuckooChessEngine/src/main/resources` → `src/main/resources`
- `DroidFishApp/.../engine/cuckoochess/{DroidEngineControl,SearchParams}.java` and
  `DroidFishApp/.../engine/LocalPipe.java` → `src/main/java/org/petero/droidfish/engine`

Keeping them unmodified makes it easy to pick up upstream changes. The opening book is not
included. Our own code is the Kotlin adapter in `src/main/kotlin`, whose command handling
follows DroidFish's `CuckooChessEngine.java`.
