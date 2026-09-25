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

An audio that has entered the local playing state opens a Poweramp-inspired dark
full-player surface when its focused card is clicked again. It has large artwork,
on-demand real duration/artist/album metadata, precise seek UI, configurable-style
10-second jumps, previous/next navigation, and queue/bookmark/chapter bottom
panels. Artwork opens a dedicated immersive subtitle/artwork layer. Timeline and
bookmark math are pure and tested; real decoding is now provided by Media3 (see
below), while durable queue/bookmark persistence still belongs to the pending
Room task.

The UI now uses scanned folders and supported audio instead of fixture data. The
player reads a small metadata subset on demand for its current item, but the app
does not yet index complete embedded metadata or embedded artwork, persist a Room
index, perform a recursive full-library scan, or play media.

Direct raster artwork is now resolved for the current folder and for the first 48
naturally sorted child folders, then loaded through Coil 3.6.3 so decoding and
memory caching stay display-bounded. Audio inside a folder inherits that folder's
first direct image for now. Embedded audio artwork and recursive child-folder
fallback are still pending, so the full product artwork precedence is not yet
complete.

## Completed slice: folder snapshots, Media3 playback, card transport, layouts

All four "Now" items in `docs/TASKS.md` are implemented in the working tree.

### 1. Bounded per-folder snapshot cache

New `core/storage/FolderSnapshotCache.kt`:

- `FolderSnapshot` holds the folder key/title, the scanned item list, the
  supported-item count and truncation flag, plus the last focused item id and the
  focus-wall viewport (`canvasZoom`, `panX`, `panY`, `wallDensityOrdinal`).
- `FolderSnapshotCache` is an access-ordered `LinkedHashMap` with a `capacity`
  (default `12`, `require(capacity > 0)`) that evicts the least recently used
  folder. `update` is a no-op returning `false` when the folder is not resident, so
  a late viewport callback cannot resurrect an evicted folder.

`feature/library/LibraryViewModel.kt`:

- `loadFolder(folderUri, forceRefresh)` gained a restore path: when the snapshot is
  resident it rebuilds `LibraryUiState` from the snapshot with `isRestored = true`
  and returns **without touching the SAF scanner**.
- Entering a *different* folder never inherits the previous viewport: a `folderKey`
  change emits a fresh state, so pan/zoom/focus start clean per folder.
- `onViewportChange` writes only to the cache and deliberately emits no state, so
  panning does not recompose the library on every frame. `onFocusedItemChange` and
  `onWallDensityChange` write to both the cache and the UI state because they are
  discrete.
- `refresh()` invalidates the folder and rescans; `selectRoot` clears the folder
  stack and the whole snapshot cache.

### 2. Genuine playback through AndroidX Media3

- `core/media/PlaybackService.kt`: a `MediaSessionService` that builds an
  `ExoPlayer` (`AudioAttributes.DEFAULT`, `setHandleAudioBecomingNoisy(true)`) and
  a `MediaSession`. `onTaskRemoved` stops the service only when playback is not
  pending and the queue is empty, so an active session survives a task swipe.
- `core/media/PlaybackConnection.kt`: a lifecycle-safe `MediaController` holder.
  `connect()` uses a `SessionToken` from `ComponentName(context, PlaybackService::class.java)`
  and `MediaController.Builder(...).buildAsync()` with a Guava listener on
  `MoreExecutors.directExecutor()`, guarding against a stale future after
  reconnect. `rememberPlaybackConnection()` connects on `ON_START` and disconnects
  on `ON_STOP`/dispose. `state` is a `StateFlow<PlaybackState>` published from
  `Player.Listener.onEvents`; position advances from a 500 ms ticker that only runs
  while `playWhenReady`. Commands issued before the controller is ready are queued
  as a single pending action and replayed on connect.
- `core/media/PlaybackState.kt`: an immutable transport snapshot (`isConnected`,
  `mediaId`, `title`, `isPlaying`, `playWhenReady`, `isBuffering`, `positionMs`,
  `durationMs`, `hasNext`, `hasPrevious`, `hasQueue`, `errorMessage`) with pure
  `hasDuration`, `progress`, `isCurrent(id)` and `positionForFraction(fraction)`
  helpers, plus `toggleCommandFor(state)`.
- `core/media/PlaybackQueue.kt`: `buildFolderQueue(folderItems, selected)` converts
  the current folder listing into a deterministic queue — the visible audio
  children in existing display order, starting at the selected entry. Child folders
  and audio without a document URI are excluded. A selected audio outside the
  listing plays alone rather than silently starting a different track. Returns
  `null` when nothing is playable.
- `AndroidManifest.xml`: declares `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS` and registers
  `.core.media.PlaybackService` with `android:exported="false"` and
  `foregroundServiceType="mediaPlayback"`. **No storage permission was added** —
  all media access remains through the persisted SAF document grant.

### 3. One playback state behind every surface

`ui/PlayerDetail.kt` and the focused audio card both read the same
`PlaybackState`; neither simulates progress locally.

- The full player derives `positionMs`/`durationMs`/`isPlaying` from the shared
  state when `playbackState.isCurrent(item.id)`, publishes play/pause,
  previous/next and seek, commits a slider scrub on `onValueChangeFinished`, and
  highlights the queue row matching `currentMediaId`. When the opened item is not
  the current one it says so (`player_not_current_item`) rather than showing fake
  zeros, and surfaces `playbackState.errorMessage` via `player_error`.
- `feature/focuswall/FocusCoverWall.kt` gained a `CompactSeekBar`
  (local `scrubFraction`, committed through `onSeekTo(playbackState.positionForFraction(fraction))`)
  and an explicit play/pause action on the focused card, shown only when that card
  is the current item. The existing tap rule is preserved: clicking the currently
  playing focused cover opens the full player, clicking any other cover starts it.

### 4. Library layout modes

`feature/library/LibraryLayoutMode.kt` defines `DetailedList`, `CompactList`,
`Grid` and `FocusWall` with a `@StringRes` label, `next()` for cycling and a
clamping `fromOrdinal`. `feature/library/LibraryLayouts.kt` renders the three new
modes and the shared `LibraryLayoutSwitcher`; all new labels live in
`res/values/strings.xml`.

Switching layouts rescans nothing: `LibraryContent` in `ui/AsmrPlayerApp.kt` hoists
`FocusWallViewportState` **above** the `when (layoutMode)` block, keyed on
`state.folderKey`, and the selected layout ordinal is kept in `rememberSaveable`.
A `snapshotFlow` inside `LibraryContent` persists zoom/pan back through
`onViewportChange`, so leaving and returning to the focus wall restores the same
viewport.

`ui/theme/Artwork.kt` now hosts the single shared `artworkBrush(seed)` palette,
replacing three private duplicates.

## Immediate next work

- Room schema and persistence for the queue and playback positions. The queue is
  currently rebuilt from the folder listing on each play and is not durable.
- Durable bookmarks: the full player's bookmark list is still local UI state only.
- Queue insertion. The focused card's "add to queue" affordance was removed
  because it was wired to an empty lambda; bringing it back needs a queue-insertion
  API on `PlaybackConnection` and the durable queue above, or an appended item is
  lost on the next play.
- Request `POST_NOTIFICATIONS` at runtime before relying on the playback
  notification on API 33+.
- Embedded artwork and recursive child-folder artwork fallback, so the documented
  artwork precedence is complete.
- Extend the bounded direct-folder scanner into a full-library scanner.

All of the above is now committed on `master` as `ebf121f` ("Make SAF audio
playable and finish the library surfaces"), on top of Codex's `fbea40e` ("封面墙V1").
The working tree is clean apart from an untracked `bash.exe.stackdump`, which is a
shell crash dump and deliberately not committed.

### Git identity is not configured

`user.name` and `user.email` are set neither locally nor globally, so a bare
`git commit` fails. Codex's commit is authored `Codex <codex@local>`; `ebf121f` is
authored `Claude <claude@local>` the same way, passed per command:

```powershell
git -c user.name='Claude' -c user.email='claude@local' commit ...
```

Set a real identity in the repository if these placeholder authors are unwanted --
but do not change the user's global Git configuration without explicit approval.

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

## Verification: the recorded Gradle blocker is gone

Earlier handoffs recorded that every Gradle task failed before project evaluation
with `java.io.IOException: Unable to establish loopback connection`. **That no
longer reproduces, and the recorded diagnosis was wrong.** `./gradlew.bat`
configures and runs normally: `assembleDebug`, `lintDebug` and
`testDebugUnitTest` all execute.

### The real failure, and its fix

`testDebugUnitTest` did fail, but later and for a different reason: compilation
succeeded, then all 12 test classes failed to load with `ClassNotFoundException`.

Root cause, established by experiment rather than inference:

- Gradle 9.6.1 writes the test worker's classpath into a Java `@argfile` encoded
  as **UTF-8**.
- The `java` launcher decodes `@argfile` using **`sun.jnu.encoding`**, which is
  **GBK (codepage 936)** on this Simplified-Chinese Windows install.
- This repository's path contains non-ASCII characters (`...\安卓音乐播放器\...`),
  so every classpath entry was decoded as mojibake and no directory resolved.
- A direct probe confirmed the mechanism: the identical classpath in an argfile
  written as UTF-8 produced `ClassNotFoundException`, while the same content
  written as GBK loaded the class. `JAVA_TOOL_OPTIONS` and `JDK_JAVA_OPTIONS`
  cannot help, because the launcher parses the argfile before any option applies.

`tools/ascii-build-dir.init.gradle` redirects `layout.buildDirectory` to an ASCII
path, which removes every non-ASCII entry from the worker classpath (dependency
JARs already live under the ASCII `~/.gradle`). Run the mandatory commands as:

```powershell
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
```

Results with that init script:

- `testDebugUnitTest` -- BUILD SUCCESSFUL, **48 of 48 tests pass, 0 failures,
  0 errors** across 11 test classes. This is the first time these tests have run
  under Gradle; every earlier "48 of 48" claim came from hand-invoking
  `JUnitCore` against a hand-compiled classpath, not from the real task.
- `lintDebug` -- BUILD SUCCESSFUL, **0 errors**, 12 warnings (9 `UnusedResources`,
  1 `DataExtractionRules`, 1 `MissingApplicationIcon`, 1 `UseKtx`).
- `assembleDebug` -- BUILD SUCCESSFUL; the installable APK lands at
  `<asciiBuildDir>/app/outputs/apk/debug/app-debug.apk`.

The init script is a workaround for the launcher, not a product change: sources,
resources and the APK are unaffected. It becomes unnecessary once the repository
sits on an ASCII path or the machine enables the Windows UTF-8 system locale.

Local, Git-ignored test media is under `local-test-media/ASMR_Test`. It contains
17 OGG fixture tracks, eight generated PNG covers, nested folders, an empty
folder, root-level audio, and matching LRC/VTT fixtures. The audio is copied from
three CC0/public-domain originals; source and license links are recorded in
`local-test-media/SOURCES.md`. Fixture durations run from 2.57 s to 18.9 s, which
matters when exercising transport behaviour: a 2.57 s track advances to the next
queue entry before a screenshot can be taken.

## Device verification

Verified on a real Redmi (Android 15 / API 35 / arm64-v8a) against the user's own
SAF-selected library, and on `emulator-5554` (API 36 / x86_64) against the
generated fixtures. All media access is read-only and goes through the persisted
SAF document grant; no storage permission is requested and nothing was modified or
deleted on either device.

**Which build each device actually ran.** The two devices did not run the same
APK, and that matters when reading the list below. The phone's install was
`lastUpdateTime` 14:38:41; the two defects were fixed in source at 15:02:48
(`FocusCoverWall.kt`), 15:03:18 (`LibraryLayouts.kt`) and 15:05:48
(`strings.xml`). So the phone ran the **pre-fix** build for the whole of that
round -- it is what *found* the defects -- and the fixed build went only to
`emulator-5554`, which was updated at 15:06:51 from the 15:06:44 APK. Anything
below describing behaviour after the fixes was observed on the emulator, not the
phone.

That gap is now closed: the current APK (73,352,596 bytes, built 15:06:44) was
installed over the phone's existing build at 15:25:09 with `adb install -r`, which
preserves app data, so the phone's persisted SAF root is unchanged. A UI dump of
that build on the phone shows the focused folder card exposing exactly one action
-- the *enter* button at `[638,1670][972,1840]` -- and no play or queue
affordance, confirming both fixes are present in what the phone now runs.

Confirmed working:

- **Folder snapshot cache.** Navigating up restores the folder from the in-memory
  snapshot with the `library_restored_hint` notice and no rescan, restoring both
  the focused card and the wall density that folder was left with. Entering a
  *different* folder starts clean (density back to its default), so per-folder
  isolation holds.
- **Media3 playback.** A real `AudioTrack` at 44.1 kHz in `state:started`; the
  session reports `PLAYING`, the queue is built from the folder listing starting at
  the selected track, tracks auto-advance, and real duration is reported.
- **Focused-card seek bar.** The compact bar renders live position and duration
  from the shared state, and a real drag moved the player from 9007 ms to
  17578 ms on an 18903 ms track. The canvas gesture loop yields to it correctly --
  the interaction the previous handoff flagged as the likeliest regression.
- **Four layouts.** Detailed, compact, grid and focus wall all render the same
  sorted listing with real artwork; switching rescans nothing.
- **Focus wall geometry.** The focused hero's centre measured (610, 1553) against
  a wall viewport centre of (610, 1553.5), so D-004's exact-centre rule holds.
- **Full player.** Opens from the focused current card, follows the session across
  a track change, and its timeline drag (5577 ms to 8554 ms) and pause act on the
  real player. Interaction note: the first tap on a card only focuses it; a second
  tap on the card body opens the player.

### Defects found on device and fixed

1. **Play on a folder card was a silent no-op.** The focused folder card offered a
   play button, but `buildFolderQueue` excludes child folders, so `playFromFolder`
   returned without acting or saying anything. Reproduced on both devices. Folders
   now expose only the explicit *enter* action on the focus wall, and carry no
   trailing affordance at all in the list and grid layouts -- an intermediate
   arrow was tried there first and removed again, see below.
2. **The focused card's "add to queue" button was wired to an empty lambda**
   (`IconButton(onClick = {})`) -- an affordance that could never respond. It has
   been removed rather than left as a placeholder, and its now-unused string
   resource was dropped with it. Making it real needs a queue-insertion API on
   `PlaybackConnection` *and* a durable queue: the queue is currently rebuilt from
   the live folder listing on every play, so an appended item would not survive the
   next play.

### Presentation changes after the first device look

Two things read badly on the phone and were removed at the owner's request:

1. **The folder affordance glyph in the list and grid layouts.** These layouts
   briefly showed an `ArrowForward` on every folder row and in each folder cover's
   bottom-right corner. It was visual noise repeated on every single row, so it is
   gone: a folder now carries no trailing action in either layout, and the row or
   cell itself is what opens it. Audio is unaffected and keeps its play/pause
   button. The focus wall's focused folder card lost the arrow inside its *enter*
   button first, and then the button itself, so a focused folder now shows nothing
   but its own title and counts.

   The card action row was decoration on top of an action the cover already
   performs, and `resolveCardTap` proves it: a tap on the focused folder cover
   resolves to `FocusWallIntent.Activate`, and `onActivate` is exactly what the
   button called (`onEnterFolder = onActivate`), so entering a folder never needed
   the button. With it gone, `FocusCoverCard` and `FocusWallCanvas` lost their
   `onEnterFolder` parameter and `R.string.enter_folder` was deleted. The action
   row is now gated on `isFocused && item.type == LibraryItemType.Audio`, so only
   audio renders one.

   `R.string.open_folder` ("进入文件夹") was already unused before this change and
   has since been deleted too, taking `UnusedResources` from 8 to 7. The seven
   that remain are `all_audio`, `recent`, `favorites`, `sort`, `layout`,
   `demo_hint` and `local_audio` -- all strings for surfaces that do not exist
   yet, so they are placeholders rather than dead weight.
2. **The `library_restored_hint` notice** ("已从内存快照恢复（未重新扫描）…") no
   longer renders, and its string resource was deleted. Restoring from the
   in-memory snapshot is an implementation detail; the folder should simply look
   the way it was left. `LibraryUiState.isRestored` is kept, because
   `FocusWallViewportState` still uses it to skip the restore animation -- only the
   announcement is gone. The error and truncation notices still appear; the notice
   row now triggers on `isTruncated || hasError` alone.

Verified on `emulator-5554` against the fixtures: the grid and detailed list render
folders with no glyph, the root audio row still shows its play button, the focused
folder card on the wall shows only its title and counts, and the screenshot taken
after entering a folder and pressing Back is byte-identical to the one taken before
entering -- the restored folder renders exactly as a fresh scan would, with no
notice row. Entering was re-tested *after* the button was removed, since the cover
tap is now the only way in: tapping the focused 双耳录音室 cover navigated into it
and listed 01_左耳轻语 / 02_右耳轻语, and focusing 01_左耳轻语 there still renders
the circular play button. Each resulting APK was installed on the phone, confirmed
by matching MD5 (`82efa4c27bb30f472f3dbbe2e6dccb8b` for the final one).

### Folder summary line

A folder's secondary line used to be a fixed `%1$d 个子文件夹 · %2$d 条音频`, so a
folder holding only audio still spent half the line on "0 个子文件夹". It is now
built from the counts that are actually present, via a single shared helper rather
than the two private duplicates that existed before:

- `feature/FolderSummary.kt` holds `folderSummaryParts(childFolderCount, audioCount)`
  -- a pure, unit-tested function returning which halves to show, in display order
  -- and `folderSummaryLine(...)`, the `@Composable` renderer. It sits at the
  `feature` package root because the library layouts and the focus wall both need
  it and neither should own it.
- `R.string.folder_contents` was replaced by `folder_subfolder_count` and
  `folder_audio_count`; the separator " · " is applied by the helper.
- **An entirely empty folder is a deliberate exception**: with no non-zero count to
  carry the line it still renders "0 个子文件夹 · 0 条音频". Going blank there would
  read as a failure to load rather than as an empty folder, which matters because
  folders holding only images are common in a real library. This is the one case the
  new rule does not change, and it is covered by a test.

`app/src/test/.../feature/FolderSummaryTest.kt` covers all four quadrants, including
the empty and negative-count cases. Verified on `emulator-5554`: 双耳录音室 and the
other audio-only fixture folders now read just "2 条音频", while 环境声 (one
subfolder, one audio) still reads "1 个子文件夹 · 1 条音频".

### Remaining verification and risks

- **Media3 session lifecycle.** Foreground-service promotion, the media
  notification, audio-focus ducking and behaviour across process death have still
  not been exercised. `POST_NOTIFICATIONS` is declared but never requested at
  runtime, so on API 33+ the playback notification will not appear until a runtime
  request is added.
- **`android:exported="false"`** deliberately keeps external controllers (Android
  Auto, assistant, Bluetooth transport controls) out. Revisit when that becomes a
  product decision.
- **Queue and position are not durable.** `buildFolderQueue` rebuilds from the live
  folder listing on every play, so a queue does not survive process death and is
  not yet persisted in Room; the full player's bookmarks remain local UI state.
- **Restore path assumes a warm cache.** Back navigation restores instantly from
  `FolderSnapshotCache`, but the cache is memory-only with a capacity of 12, so a
  deep folder stack or a process restart falls back to a real rescan. That is the
  intended bounded behaviour, not a defect.
- **SAF URIs may go stale.** A persisted document grant can be revoked between
  sessions; playback failure currently surfaces only through
  `playbackState.errorMessage`.
- **Artwork precedence is incomplete.** Direct raster artwork resolves for the
  current folder and the first 48 naturally sorted child folders; folders that hold
  only subfolders fall back to the generated gradient placeholder. Recursive
  child-folder artwork fallback is still pending.

Gradle has a 2 GiB heap and 1 GiB metaspace in `gradle.properties`, replacing the
defaults that caused an Android Studio out-of-memory build failure.

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
