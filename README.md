# JOKESONG

An offline, folder-first Android audio player in active development. Music, long-form
audio and ASMR share one library and playback session; branding is not genre-specific.

The product specification is in [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md), and
the architecture baseline is in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Current implementation

- Kotlin and Jetpack Compose
- SAF folder browsing and Media3 playback, with focus separate from now-playing state
- Portrait/landscape cover walls plus compact/list/grid layouts
- Bounded pinch zoom and inertial scrolling; dragging never changes the focused cover
- Folder/audio type markers and focused actions
- Deterministic random discovery sampler with tests
- Chinese-first resources and four-destination navigation shell
- Ordinary timed lyrics, immersive visualizers and optional desktop lyrics
- Direct, validated single-track tag/embedded-art editing on supported local files
- Initial collectible-card studio/viewer; see HANDOFF for limitations and device status

Current browsing is bounded and folder-first, not a finished recursive 40,000-track
index. Read [`docs/HANDOFF.md`](docs/HANDOFF.md) for actual verification and remaining
acceptance; a compiled native test is not a device test pass.

## Open in Android Studio

1. Open this repository as a project.
2. Allow Gradle sync to use the SDK recorded in `local.properties`.
3. Run the `app` configuration on a modern Android device or emulator.

Command-line verification:

```powershell
.\gradlew.bat -I tools/ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools/ascii-build-dir.init.gradle lintDebug
.\gradlew.bat -I tools/ascii-build-dir.init.gradle assembleDebug
```

On this host the current APK is under the sibling
`../.asmrplayer-build/app/outputs/apk/debug/`, not the stale `app/build` directory.
Keep the existing application ID/signing key so owner-installed updates retain data.

## Updates and releases

- [`CHANGELOG.md`](CHANGELOG.md): user-facing changes and known limits.
- [`docs/RELEASING.md`](docs/RELEASING.md): tested source + Release APK delivery.
- [`AGENTS.md`](AGENTS.md), [`CLAUDE.md`](CLAUDE.md): shared agent rules.

Each completed code update must sync code, documentation and a tested APK. Signing
secrets, private media, local settings and tool/auth caches never belong in Git.

Public source: https://github.com/liuchengzhiaa/JOKESONG

Phone downloads: https://github.com/liuchengzhiaa/JOKESONG/releases/latest

Direct latest APK (no GitHub login):
https://github.com/liuchengzhiaa/JOKESONG/releases/latest/download/JOKESONG.apk

After reviewing/committing a completed slice, `./tools/publish-release.ps1` verifies,
builds, pushes and publishes a new version without overwriting existing releases.
`./tools/publish-release.ps1 -PlanOnly` prints the local plan without network/writes.
