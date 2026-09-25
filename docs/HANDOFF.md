# Handoff

## Current state

The repository began empty. Product discovery is complete and captured in the
documents under `docs/`. Android Studio and an SDK are installed locally, including
API 37 and Build Tools 36, but this terminal did not initially expose SDK tools in
`PATH`.

## Active implementation

The Kotlin/Compose application scaffold and the first complete focus-cover wall are
present. The library now selects exactly one SAF root, persists its URI grant with
Preferences DataStore, scans only the current folder's direct children, and allows
folder entry, parent navigation and manual refresh. Scans retain at most the first
500 naturally sorted display items while still counting all matching direct
children, so this first slice stays memory-bounded.

The wall uses deterministic world coordinates and a custom `SubcomposeLayout`.
Both orientations are predictable, screen-filling two-dimensional mosaics;
portrait prioritizes vertical exploration but never collapses into a single
column. Cards scale continuously by distance to the viewport centre,
so the centre cover is dominant and surrounding covers recede. Dragging,
release-to-snap, directional flings, bounded whole-canvas pinch zoom and
small/medium/large density are implemented without relayout during a gesture. Only
near-visible cards are composed. Focus and playback presentation are independent;
focused folders expose explicit Play and Enter actions, and focused audio exposes
Play/Pause and queue affordances. Back clears focus before navigating upward.

An audio that has entered the local playing state opens a dedicated full-player
surface when its focused card is clicked again. This is currently a UI handoff
surface; real decoding, progress and durable playback still belong to the pending
Media3 service task.

The UI now uses scanned folders and supported audio instead of fixture data. It
does not yet parse embedded metadata or embedded artwork, persist a Room index, perform a
recursive full-library scan, or play media.

Direct raster artwork is now resolved for the current folder and for the first 48
naturally sorted child folders, then loaded through Coil 3.6.3 so decoding and
memory caching stay display-bounded. Audio inside a folder inherits that folder's
first direct image for now. Embedded audio artwork and recursive child-folder
fallback are still pending, so the full product artwork precedence is not yet
complete.

## Local environment

- Android SDK: `F:\AndroidSDK`
- Android Studio: `F:\Android`
- System JDK: Microsoft OpenJDK 17
- Android Studio runtime: JetBrains Runtime 21

## Known repository issue

Git reports dubious ownership because the repository owner SID differs from the
execution user. Use a command-scoped override when inspecting Git:

```powershell
git -c safe.directory='D:/c/Documents/ChatGPT/安卓音乐播放器' status
```

Do not change the user's global Git configuration without explicit approval.

## Verification blocker

The Gradle 9.6.1 Wrapper JAR was extracted from the official distribution and its
SHA-256 matched Gradle's published checksum. `gradlew.bat --version` succeeds.

Any task that configures a build fails in this controlled execution environment
before project evaluation with:

```text
java.io.IOException: Unable to establish loopback connection
```

Both `--no-daemon` and `GRADLE_DAEMON_BIND_ADDRESS=127.0.0.1` were tried. Run these
commands from Android Studio or an ordinary local terminal:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

For the focus-wall change, the affected Compose/Kotlin sources were compiled with
the resolved Kotlin 2.4.10 compiler and Compose compiler plugin. All 17 pure unit
tests pass under JUnit 4.13.2, including the balanced two-dimensional portrait
ring, exact hero-centre, 40,000-item bounded-visibility and centre-depth ordering
tests. A locally signed validation APK was installed on `emulator-5554`; the
portrait mosaic was visually checked with multiple rows and columns filling the
viewport, a dominant hero at the exact canvas centre, and a side card successfully
snapping into that same centre. Folder entry, playback styling, full-player
navigation, back handling, density changes and landscape rendering were also
exercised without an app crash.

Gradle now has a 2 GiB heap and 1 GiB metaspace in `gradle.properties`, replacing
the defaults that caused an Android Studio out-of-memory build failure. Both
mandatory commands were retried after the two-dimensional portrait correction,
but still stop before project evaluation with the same loopback exception. They
remain:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

Local, Git-ignored test media is under `local-test-media/ASMR_Test`. It now contains
17 OGG fixture tracks, eight generated PNG covers, nested folders, an empty folder,
root-level audio, and matching LRC/VTT fixtures. The audio is copied from three
CC0/public-domain originals; source and license links are recorded in
`local-test-media/SOURCES.md`.

## Claude collaboration status

Claude Code 2.1.281 is configured by cc switch to use OpenCode Go with
`deepseek-v4-flash`. Direct non-interactive invocation works:

```powershell
claude -p --verbose "<task>"
```

Claude Code prints an `unrecognized_model` warning because the third-party model is
not described by its local model catalog, but the request completes successfully.
Do not override it with `--model sonnet`: the current Sonnet tier mapping points to
the older `deepseek-flash` identifier. Allow several minutes for repository review
tasks before treating the process as hung.

A read-only review of the initial scaffold completed successfully. It found no
definite Kotlin/Compose source compilation errors and independently confirmed that
the remaining requirement is a real Gradle build. Some version/JVM warnings from
the review were static false positives already answered by current Android docs.
