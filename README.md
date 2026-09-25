# ASMR Player

An offline, folder-first Android ASMR player in active development.

The product specification is in [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md), and
the architecture baseline is in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Current prototype

- Kotlin and Jetpack Compose
- Portrait focus-cover flow with focus separate from now-playing state
- Bounded pinch zoom and inertial scrolling; dragging never changes the focused cover
- Folder/audio type markers and focused actions
- Deterministic random discovery sampler with tests
- Chinese-first resources and four-destination navigation shell

The current screen uses generated fixture cards; it does not scan or play local
media yet.

## Open in Android Studio

1. Open this repository as a project.
2. Allow Gradle sync to use the SDK recorded in `local.properties`.
3. Run the `app` configuration on a modern Android device or emulator.

Command-line verification:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

The application ID and product name are placeholders until final naming is chosen.
