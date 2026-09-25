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
inertial release, bounded whole-canvas pinch zoom and small/medium/large density
are implemented without relayout during a gesture. Only near-visible cards are
composed. Focus and playback presentation are independent;
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
  is the current item. The tap rule as it stood at that point was: clicking the
  currently playing focused cover opens the full player, clicking any other cover
  starts it. Superseded — see "Focus-wall gesture policy" below; only the focused
  cover answers a single tap now.

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

### Codex review after the Claude implementation

Codex reviewed the committed implementation and found three transport-edge
issues that are fixed in the current working tree:

- The grid rendered a Pause icon for the current playing item but still invoked
  `onPlay`, replacing/restarting the queue. `LibraryGrid` now receives the shared
  `onTogglePlayPause` callback and uses it for the current item, matching both
  list layouts and the focus wall.
- `PlaybackService.onTaskRemoved()` stopped the service whenever the queue was
  paused. The agreed default is to retain the service and queue after a task
  swipe, whether playing or paused, so it now stops only for an empty session.
  `PlaybackServicePolicyTest` covers empty and populated queues.
- `POST_NOTIFICATIONS` was declared but never requested. On Android 13+ the first
  explicit Play action now requests it once per saved Activity state; playback
  proceeds whether the user grants or denies it.

The formal Gradle XML reports produced in Claude's ordinary terminal were read
back independently and contain 54 tests, 0 failures, 0 errors and 0 skipped.
Codex's restricted command environment still fails before project evaluation
with `java.io.IOException: Unable to establish loopback connection`, even with
`--no-daemon`; therefore the new review changes and the two added policy tests
still need these commands from Android Studio or a normal terminal:

```powershell
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
```

No APK was built or installed during this Codex review.

### Focus-wall V2 in the working tree

The owner's four current visual priorities supersede further Room integration for
this slice. Codex inspected Folia Major's open-source lattice design for concepts,
then implemented independent geometry rather than copying its AGPL templates or
source:

- `FocusWallLayoutEngine` now groups ten sorted items into a 12 x 10 logical
  block. Three original exact-cover templates, stable template selection and
  deterministic horizontal/vertical reflection produce large and small square,
  portrait and landscape cards without random state.
- Default card gutters are 2 dp. Card size now comes from the mosaic itself; the
  former centre-distance scale no longer opens large gaps between cards.
- Cards below 116 dp wide or 112 dp high switch to a compact treatment (smaller
  type/glyph, no secondary line) so the smallest gears do not overflow.
- Focusing an item switches only its block to a centred 6 x 6 hero template. The
  other nine identities re-pack into the surrounding frame, and Compose animates
  their centre and width/height transition over 320 ms. Other blocks do not move.
- Drag release still selected/snapped the nearest card, and tapping an unfocused
  card still focused it without playing, as `PRODUCT_SPEC.md` then required. Both
  were removed on the owner's next request — see "Focus-wall gesture policy".
- `CoverAccentLoader` decodes at most a thumbnail-sized version of the focused
  cover on an IO dispatcher, samples a quantised colour histogram and caches the
  result. The 2.2 dp border uses that colour at 76% alpha; failed/no artwork falls
  back to the existing deterministic placeholder accent. Non-focused cards incur
  no palette decode.
- Pure tests now cover exact deterministic geometry, mixed aspect families,
  non-overlap, local-only focus reflow, large-scene viewport bounds and colour
  sampling/transparent-pixel rejection.

The owner subsequently requested automated verification. The combined focus-wall,
playback-review and Room working tree now has **63/63 passing unit tests**, lint
reports **0 errors and 12 warnings** (seven future-surface strings plus existing
version/manifest/icon notices), and `assembleDebug` succeeds. No device was started
and no APK was installed. The Room work and focus-wall V2 are still uncommitted in
the same working tree and must not be discarded.

The APK is at
`D:\c\Documents\ChatGPT\.asmrplayer-build\app\outputs\apk\debug\app-debug.apk`.

**Correction, and a warning about this path.** This section previously recorded that
file as 74,159,084 bytes with SHA-256 `0EEB0BD5...`. That record had gone stale --
the artifact was replaced and no build in the session could be tied to it. The file
at this path is a single mutable output that every `assembleDebug` overwrites, so a
byte count and hash written here is only true until the next build. The authoritative
fingerprint for whatever is currently on disk belongs next to the change that built
it, not in this section. Debug APKs here are also not byte-reproducible, so treat any
hash as a fingerprint of one build, not of the source tree. For the current build,
see the verification under "Focus-wall gesture policy" below.

On the first requested verification, KSP rejected the old ASCII build directory
because it lived on `C:` while this checkout lives on `D:`. The init script now
defaults to the repository's ASCII-named parent directory
(`D:\c\Documents\ChatGPT\.asmrplayer-build` here), keeping generated sources on
the same drive while still avoiding the UTF-8/GBK test-worker path problem.
The Codex terminal also needed `TEMP` and `TMP` set to `C:\jtmp`; otherwise JDK 17
failed while opening the local selector used by Gradle's daemon connection. This
environment override is process-local and is not an application change.

Agent continuity is now explicit: the owner starts Claude manually, Codex does not
invoke it automatically, and both `AGENTS.md` and `CLAUDE.md` require a resuming
agent to inspect this handoff, the task board, Git status/log and the current diff
before editing. Keeping these two docs current is the handoff mechanism.

### Alternate Folia-style wall in the working tree

The owner approved the current focus-wall V2 but requested that it remain intact
while a second layout more closely follows the Folia v0.7.4 demonstration. The
new `FoliaWall` entry is immediately to the right of `FocusWall` in the layout
switcher; the original engine and its fixed centred-bento behavior are unchanged.

- `FoliaWallLayoutEngine` uses independently-authored 12 x 8 exact-cover blocks
  with twelve items, two base gears and deterministic block reflection.
- Focusing no longer applies one universal top/side/bottom frame. The selected
  item's original slot chooses the nearest hero quadrant; two original focus
  gears plus horizontal/vertical reflection yield eight stable arrangements.
  **Superseded** by "Folia focus grows in place, and a wall tools button" below:
  those eight arrangements were all block corners, and the hero now takes its
  position from the card's own slot instead.
- Remaining identities are assigned to the globally nearest open slots with a
  small aspect-ratio penalty. Only the selected block changes, so distant blocks
  stay fixed and motion remains understandable.
- Both wall modes reuse the same renderer, artwork-derived translucent border,
  gestures, focused-card controls and playback behavior. Each keeps a separate
  in-session pan/zoom state while the user switches between them.
- `FoliaWallLayoutTest` covers determinism, mixed shapes, non-overlap, distinct
  per-slot focus gears, local-only reflow and a bounded 40,000-item visibility
  query. `LibraryLayoutModeTest` locks the new mode immediately after the old one.
- The owner supplied `F:\41646818644-1-16.mp4` (11,973,324 bytes). This sandbox
  confirmed the file but could not decode it: FFmpeg is absent, the package
  download failed TLS validation and browser automation correctly rejected the
  local `file://` URL. Final motion/visual comparison therefore remains a phone
  review item rather than a claimed automated video comparison.

The narrow Folia/layout-mode run passed. The required full verification also
passes: **68/68 unit tests**, followed by `lintDebug` with **0 errors and 12
existing warnings**. Commands used on this host were:

```powershell
$env:TEMP='C:\jtmp'; $env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
```

### Room schema draft in the working tree

After the transport review commit, Codex started the next task without yet
connecting the database to Media3:

- `gradle/libs.versions.toml` and `app/build.gradle.kts` select stable Room 2.8.5,
  the Room Gradle plugin and KSP 2.3.6. AGP 9 built-in Kotlin is retained; kapt is
  not introduced. KSP exported the checked schema as
  `app/schemas/com.example.asmrplayer.core.database.AsmrDatabase/1.json`.
- `core/database/LibraryEntities.kt` defines indexed folder, audio, artwork and
  subtitle rows. Artwork stores references/cache identity and dimensions, never
  bitmap bytes. Missing media is retained through `isMissing`.
- `core/database/PlaybackEntities.kt` defines per-audio long-form progress, the
  singleton global restore point, durable queue order and time-point bookmarks.
- `LibraryDao` pages direct children with composite parent/sort indices and offers
  bounded-batch upserts. `PlaybackDao.replaceQueue` is transactional.
- `AsmrDatabase` is version 1 and intentionally has no destructive migration
  fallback.
- `shouldMarkPlaybackCompleted` codifies the 95%-or-last-30-seconds rule while
  preventing short fixture tracks from becoming complete immediately. Four pure
  tests cover the policy.

Room/KSP dependencies are now cached, the generated implementation compiles and
the schema export, tests, lint and APK build all succeed. The original percentage
test used a 100-second track, so its 94.999% sample also satisfied the independent
last-30-seconds rule; the fixture now uses a 1,000-second track and isolates the
95% boundary without changing production behavior. There are 63 passing test
methods in total.

```powershell
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
.\gradlew.bat -I tools\ascii-build-dir.init.gradle assembleDebug
```

- Inspect/commit the Room v1 schema draft with the focus-wall slice, or separate
  them deliberately. After that, connect
  `PlaybackService` to `PlaybackDao` for periodic progress and atomic queue
  persistence; the queue is still rebuilt from the folder listing on each play.
- Durable bookmarks: the full player's bookmark list is still local UI state only.
- Queue insertion. The focused card's "add to queue" affordance was removed
  because it was wired to an empty lambda; bringing it back needs a queue-insertion
  API on `PlaybackConnection` and the durable queue above, or an appended item is
  lost on the next play.
- Verify the first-play `POST_NOTIFICATIONS` request and the paused task-swipe
  behavior on the phone.
- Embedded artwork and recursive child-folder artwork fallback, so the documented
  artwork precedence is complete.
- Extend the bounded direct-folder scanner into a full-library scanner.

The earlier Claude implementation described before the "Immediate next work"
heading is committed on `master`, on top of Codex's `fbea40e` ("封面墙V1"):

- `1360e23` "Make SAF audio playable and finish the library surfaces" -- the slice
  itself, 28 files, +3133/-293.
- `461220e` "Record the commit and the missing Git identity in the docs" -- the doc
  correction, including this section.
- `053921a` "Correct the docs for the rewritten commits and shared author" -- first
  gave both agents one shared author.
- the tip commit reverses that again and gives each agent its own name.

That earlier baseline was clean apart from `bash.exe.stackdump`. The current
working tree intentionally also contains the later playback review, Room v1 and
focus-wall V2 described above; none of those newer changes is committed yet. The
crash dump remains unrelated and must not be staged.

### Focus-wall gesture policy: a single tap moves focus, dragging never does

The owner reported a product defect on both walls: "我一滑动就会聚焦在我停留的那个
封面" -- any drag re-focused whatever card the viewport happened to stop on. Two
coupled mechanisms caused it, and it was **not** a coding slip: the committed code
matched `PRODUCT_SPEC.md` and `DECISIONS.md` as written. Drag release called
`nearestPlacementToCenter` and re-focused that card, and
`LaunchedEffect(focusedItemId, placements)` then re-centred the canvas on it. So
every release reflowed a block and moved the viewport, which is exactly what made
the wall feel unscrollable. Fixing it therefore required changing the spec, not
just the code.

This went through two revisions. The first removed tap-to-focus entirely, leaving a
double tap as the only way to move focus and making a single tap act only on the
already-focused card. The owner then asked for the simpler rule -- "能正常拖动
（拖动时不改变聚焦封面），同时单击就能改变聚焦封面" -- and the final policy is:

- A single tap on a card that does **not** hold focus moves focus to it: the card
  re-packs its block around the hero slot and the canvas centres on it. Nothing is
  played or entered yet.
- A single tap on the card that **already** holds focus activates it: audio starts,
  the currently playing item opens the full player, a folder is entered.
- **No double-tap gesture is bound to the cards.** A quick double tap therefore
  reads as "focus, then activate" for free.
- Drag **release is inertial and free**: no snapping to the nearest card, no
  re-centring, no focus change. Panning never moves focus.

Worth noting for whoever reads this next: `strings.xml` still carries an orphaned,
unused `demo_hint` reading "点击卡片使其聚焦，再次点击执行操作" -- tap a card to focus
it, tap again to act. That is the rule above, word for word, left behind from the
fixture-data prototype. So this is the wall's originally designed interaction
restored, not a new one invented to patch the defect.

Implementation, all in `feature/focuswall`:

- `FocusWallInteraction.kt` holds one pure function,
  `resolveCardTap(focusedItemId, tappedItemId): FocusWallIntent`, returning
  `Focus` when the tapped card is not the focused one and `Activate` when it is.
  Both sealed-interface branches are genuinely reachable, so no dead branch is
  forced on the caller.
- `FocusCoverWall.kt` binds cards with plain `clickable(onClick)`, **not**
  `combinedClickable`. That is deliberate: binding `onDoubleClick` makes Compose
  hold every single tap back for the double-tap timeout (~300 ms) before delivering
  it, which is felt as lag on the wall's primary gesture. "Double tap to open" does
  not need the handler -- it is the same rule applied twice. This build is
  therefore also more responsive on a single tap than the double-tap revision that
  preceded it.
- `settlePan(...)` runs on release: `Animatable.animateDecay` with
  `exponentialDecay(frictionMultiplier = 1.35f)`. `nearestPlacementToCenter` and
  `directionalNeighbor` were deleted; with the release path gone they had no
  remaining caller.
- Because release no longer snaps, the pan became unbounded and a hard fling could
  throw the wall out of reach with nothing left to grab. `FocusWallLayout.kt` now
  exports `wallWorldBounds()`, `FocusWallWorldBounds`, `FocusWallPan` and
  `clampWallPan()`. **This bound is Claude's own addition, not something the owner
  asked for** -- it is a safety rail that keeps the viewport *centre* inside the
  wall's extent, and panning is completely free within it. The owner has not yet
  accepted or rejected it.
- `focus_wall_hint` now reads "拖动浏览 · 双指缩放 · 单击聚焦 · 再单击已聚焦封面执行操作".

Both wall modes share this code path: `FocusWallStyle.Bento` and
`FocusWallStyle.Folia` differ only in the layout engine, so the single gesture
change covers the Bento wall, focus-wall V2 and the Folia wall at once.

**A first action is a single tap again.** `focusedItemId` starts as null for a
folder with no cached snapshot, but `resolveCardTap` returns `Focus` whenever the
tapped card is not the focused one -- including when nothing is focused at all --
so entering a folder and tapping a cover focuses it, exactly as before the defect
was reported. The mid-round double-tap revision had made that tap a no-op, which
would have cost two gestures to play anything; the owner's second request removed
that cost. Playing an item is still two taps when it was not already focused (one
to focus, one to activate), but the second tap is now the same gesture, not a
different one.

`PRODUCT_SPEC.md` and `DECISIONS.md` (D-004) were updated to state the new policy,
since the old text was what the removed behaviour had been written to satisfy.

Verification of this change:

```powershell
$env:TEMP='C:\jtmp'; $env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest --rerun lintDebug assembleDebug
```

The first attempt failed to compile: `cancelAnimation()` is a member of
`AnimationScope`, not of the `Animatable` receiver that `Animatable.animateDecay`
hands its block, so `settlePan` now cancels the job it owns instead. The rerun
passes **76/76 unit tests, 0 failures, 0 errors, 0 skipped** (read back from the
JUnit XML under `.asmrplayer-build/app/test-results/testDebugUnitTest/`, not from
the Gradle console) and `lintDebug` reports **0 errors and 12 warnings** -- the same
twelve the previous slice recorded, so this change added none. The count is 76
rather than the double-tap revision's 77 because the two rewritten
`FocusWallInteractionTest` cases collapsed into one "focus, then activate" case.

The six new pure tests from the earlier revision still stand: world bounds
enclosing every placement in all four quadrants, absent bounds, the no-op without
bounds, the rail stopping the centre exactly at the wall's extent, the bound
scaling with zoom, and a degenerate zero zoom staying finite.

The APK carrying this policy is 74,175,884 bytes, SHA-256
`FDDB98B95FCD901ACAC46119AE763E6FAE4407ABD96EDBC347B4BC16B354AA47`, built
2026-09-25 21:17. **It has not been installed.** The owner installs APKs himself.

**Install history, so the two builds are not confused.** The build installed with
`adb install -r` at 2026-09-25 21:11:04 (device checksum
`7a5a5295e9564f04cbbc65461e88ebd8`) carried the **double-tap** revision, and is
therefore already superseded by the build above. It replaced the 20:16 build, which
predated any of this work and is what the owner was seeing when reporting the
defect; `firstInstallTime` stayed at 12:52:55, so app data including the persisted
SAF root survived, and no media was read, written or deleted.

Note for the next agent: the owner installs APKs himself and does not want `adb`
driven on his phone without asking, even for a read-only query like `dumpsys`. That
21:11 install was authorised in the moment ("装到手机上看看") and was already
complete when he said he would rather do it himself; do not treat it as standing
permission.

**What is still unverified.** The app has not been driven by hand on any of these
policies. The gesture rule, the pan bound and the tap-to-focus recentring are all
awaiting a manual pass on the device; a checksum only proves which build is
running, not that the behaviour is right.

### Folia focus grows in place, and a wall tools button

Two independent requests from the owner, done in one slice.

**1. The focused Folia cover no longer jumps to a corner.** This was not a
transient bug but the direct consequence of the design: `FoliaFocusTemplates`
held two written templates with the hero pinned at `FoliaSlot(0, 0, 6, 6)` and
`focusedSlotsByItem` mirrored them with `focusOnRight`/`focusOnBottom` derived
from the original slot's centre, so the hero could only ever land on one of eight
mirror combinations -- all of them block corners.

The replacement keeps the focus *shape* fixed (a 6 x 6 hero; the shape is the
configured focus shape and was never the variable) and instead derives the hero's
*position* from the card's own slot:

- `foliaFocusCover(heroX, heroY, variant)` builds an exact cover of the 12 x 8
  block: the hero, plus four complement regions -- top strip, bottom strip, left
  band and right band, where the bands span only the hero's six rows -- each
  allocated a share of the remaining eleven slots by capped largest-remainder
  (`allocateFoliaPieces`) and cut into integer cells by recursive guillotine
  halving (`splitFoliaRegion`). `variant` keeps the determinism and the
  per-block variety that `stableFoliaBucket(seed, 2)` already provided.
- `heroX`/`heroY` centre that shape on the card's original centre and clamp it
  into the block.
- The greedy nearest-neighbour assignment of neighbours to slots
  (`movementCost`) is untouched -- that is the "the shapes around it move" half,
  and it already worked.

Corner cards still focus in their corner, and the corner case regenerates the
same structure the hand-written template had, so there is no visual regression
there. The corner geometry was checked by hand before implementing: `heroX=0,
heroY=0` yields a right band of six 3 x 2 cells and a bottom strip of five, which
is what the old template produced.

Test changes in `FoliaWallLayoutTest.kt`:

- **Deliberately replaced, not deleted:** `focus reflows only the selected block
  and avoids one fixed top strip` is now `focus reflows only the selected block`.
  The first half still holds and is still asserted; the "not one fixed top strip"
  half described the old design's intent and is now false by construction, so
  those assertions were removed rather than left to rot.
- **New:** `the focused hero lands as close to the tapped card as its shape
  allows` computes, for each of 24 items, the best reachable hero centre over all
  21 legal origins and asserts the implemented centre is within 0.05 units of it.
  This is a discriminating bound, not a tautology: for a mid-block slot like
  `(4, 0, 5, 4)` the old code was 2.69 units off while the best reachable
  distance is 1.118.
- **New:** `focused heroes are no longer confined to block corners`.
- **New:** `every focus shape remains an exact cover of its block` -- recovers the
  cell grid from the placement rectangles and asserts 96 covered cells with no
  overlap for every focused item. This is the main safety net for the generator.
- One comment written during this slice was corrected before landing: block 0
  yields seven distinct origins but only **three** interior ones (`(4,0)`,
  `(3,2)`, `(5,2)`), not four. The threshold follows the corrected count.

**2. A floating tools button at the wall's bottom-end corner.** `FocusCoverWall`
now wraps its canvas in a `Box` and adds `WallToolsOverlay`, which floats above
the wall so it keeps its size and position under zoom and pan. It opens a small
`Surface` panel anchored to the button -- deliberately not a `ModalBottomSheet`,
which would hide the wall the action is about to move -- with a full-wall
transparent scrim behind it so a tap meant to close the panel cannot also land on
a card. Tools are modelled as a `WallTool` row list so later additions are one
more entry.

Its first entry, "聚焦当前播放位置", moves focus to the playing track. Focus and
playback are independent states, so the wall needed an explicit way back.

- `LibraryViewModel.notePlaybackOrigin()` records `folderStack` at play time.
  That is the only moment that knows both the listing and the track, and it needs
  no scan and no document-URI-to-path map. `MainActivity.onPlayItem` calls it
  before `playFromFolder`.
- `revealPlayingItem(itemId)` focuses directly when the item is already listed;
  otherwise it restores the recorded folder chain, loads that folder, and parks
  the focus in `pendingFocusItemId` for `applyPendingFocus()` to apply once the
  listing is published -- the load is asynchronous and would otherwise overwrite a
  focus set immediately.
- The snapshot-restore path publishes `isRestored = false` for such a jump. A jump
  is not back navigation, and restoring the saved viewport would have swallowed
  the one animation that brings the camera to the track.
- `canRevealPlayingItem(...)` gates the entry, and the answer is computed in
  `LibraryContent` so both walls see the same one. It is disabled when nothing is
  playing, and when the track is in another folder but no origin was recorded --
  which is exactly the case of a queue that outlived the process. A queue that
  cannot be traced back shows a disabled entry rather than a tap that does
  nothing. `MainActivity` passes `onRevealPlayingItem` as a lambda, so the
  discarded `Boolean` return is safe.
- Two strings were added: `wall_tools` and `focus_playing_item`.

Verification:

```powershell
$env:TEMP='C:\jtmp'; $env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest --rerun lintDebug assembleDebug
```

**83/83 unit tests pass, 0 failures, 0 errors, 0 skipped**, read back from the
JUnit XML under `.asmrplayer-build/app/test-results/testDebugUnitTest/` rather
than from the Gradle console. `lintDebug` reports **0 errors and 12 warnings** --
the same twelve as the previous slice, and the seven `UnusedResources` warnings do
not include either new string, so both are wired up. `assembleDebug` succeeds.

The APK is 74,209,472 bytes, SHA-256
`f1d0ba4cdefe2ca7ee3a60c329c9183c4005aa4b3892e4ab276ef807fa597ea5`, built
2026-09-25 22:08. **It has not been installed**, and no `adb` command was run.
This file is overwritten in place by every `assembleDebug`, so that size and hash
identify only this build; the path
`.asmrplayer-build/app/outputs/apk/debug/app-debug.apk` is a mutable output, not an
archive.

**Known trade-offs, accepted:**

- No global document-URI-to-folder map was built. A queue that outlived the process
  cannot be traced back, so the entry stays disabled in that case. This is
  deliberate; see `canRevealPlayingItem`.
- If the folder jumped back to has since been refreshed and the track is gone from
  it, the pending focus lands on nothing and the wall simply shows that folder. Rare,
  and accepted.
- The Bento wall was not touched. Its hero was already centred in its block, so it
  never had this problem, and the owner raised only Folia. It does get the tools
  button, since that is shared.

**Verified by hand by the owner on 2026-09-25**, on the phone: the Folia focus
shape now appears where the tapped cover already was with its neighbours reshaping
around it, and the wall tools button and its panel work. No defect was reported
from that pass. The pan bound was not part of it: the owner asked what it was for
and then accepted it as it is, so it is now a settled product decision recorded in
`docs/DECISIONS.md` D-004 rather than an open question. Note that the earlier
"Claude's own addition, not something the owner asked for" wording elsewhere in
this file is now history -- the acceptance is what stands.

**How this was committed, and why it is two commits.** The working tree had
accumulated several slices that had never been committed: Codex's Room schema
draft, focus-wall V2, the Folia wall and `CoverAccentLoader`, plus the gesture
policy above and now this slice. The owner asked for the record, then chose to
split it by agent so the one-author-per-agent rule survives.

The split is by file. Codex's commit `4d22e30` carries the Room/KSP wiring,
`core/database/`, `core/media/PlaybackCompletion.kt`, `core/artwork/`,
`ui/theme/Artwork.kt`, `FocusWallLayout.kt`, `FoliaWallLayout.kt`, their tests,
the `LibraryLayoutMode.FoliaWall` plumbing and `tools/ascii-build-dir.init.gradle`.
This commit carries `FocusCoverWall.kt`, `FocusWallInteraction.kt`,
`LibraryViewModel.kt`, `MainActivity.kt`, `ui/AsmrPlayerApp.kt`, `strings.xml`,
the README/AGENTS/CLAUDE notes and the docs.

Two consequences, recorded rather than papered over:

- **The file boundary is an approximation.** `FocusCoverWall.kt`,
  `FocusWallLayout.kt` and `ui/AsmrPlayerApp.kt` each hold both agents' work, and
  the interleaving is at line level, not hunk level: `ui/AsmrPlayerApp.kt` alone
  mixes the FoliaWall dispatch (Codex) with the reveal wiring (Claude) inside one
  hunk. Each file went to the agent whose work dominates it, so Codex's commit also
  contains the pan-bound helpers and the six pan-clamp tests that are Claude's, and
  this commit also contains the V2 renderer and the `FocusWallStyle` enum.
- **Codex's commit does not compile on its own.** `LibraryLayoutMode.FoliaWall`
  lands there while the exhaustive `when (layoutMode)` over that enum in
  `ui/AsmrPlayerApp.kt` lands here, because that file belongs to this commit. The
  dependency is genuinely circular -- every other assignment of the shared files
  breaks one of the two trees -- so no ordering fixes it; only hand-editing an
  intermediate state that never existed would, and that was not worth fabricating
  history. The tree at the tip of this commit is the verified one, and there is no
  remote, so the split remains local and reversible.

The commit that carries the code another agent wrote does not also carry the
`Co-Authored-By: Claude Code` trailer, so `git log` does not imply Claude co-wrote
it; `git log --author` separates the two agents as before.

### Wall artwork decodes at the size it is drawn

The owner asked why some covers go soft once the wall is zoomed. The cause was
structural, not a Coil defect. World units equal physical pixels at zoom 1
(`FocusCoverWall.kt`, `viewportWidth = maxWidth.toPx()`), so the engine's
placements are pixel sizes for an unmagnified canvas. Each card is composed and
measured at exactly that size (`Constraints.fixed(placement.width, ...)`) and is
then magnified by `graphicsLayer { scaleX = zoom * animatedWidth / placement.width }`.
`AsyncImage` sized its request from those layout constraints, so the bitmap was
decoded for the *unmagnified* card and every bit of zoom — up to 1.35 by the canvas
and another 1.16 by the large density preset — magnified a bitmap that was never
asked to be bigger. It stays soft because nothing ever re-decodes it.

Two new pure functions in `FocusWallLayout.kt` sit beside `focusWallDepthScale`:

- `artworkDecodeScale(zoom)` rounds up into one of three buckets
  (`1f`, `1.15f`, `MaximumCanvasZoom`). Bucketing matters: a pinch crosses a bucket
  a couple of times instead of re-decoding on every frame, and every bucket already
  used stays in Coil's memory cache.
- `artworkDecodeSize(widthPx, heightPx, zoom)` multiplies the layout size by that
  bucket and caps each side at `MaximumArtworkDecodePx` (1536).

`FocusCoverCard` now builds an explicit `ImageRequest` with that size and
`Precision.INEXACT`. Inexact is deliberate: it stops Coil upscaling a source that is
smaller than the request, so the card scales what the artwork actually has instead of
paying for an interpolated bitmap. The bucket is read in `BoxWithConstraints`'s
composition scope, not inside the canvas, because cards are placed by a layout pass;
a zoom read inside the canvas would never reach them.

**Recorded trade-offs.** The 320 ms focus transition still softens the cards that are
*shrinking* — the old hero slot is composed at its new, smaller size and scaled up
from it for the length of the animation. Keeping the previous decode size alive for
one transition would fix it at the cost of tracking that state per card; not worth it
now. And a cover whose source is smaller than the card cannot be rescued at all: the
request is inexact, so a low-resolution source stays soft however far the wall is
zoomed. Both are properties of the design, not defects to be chased later.

Verified: 87/87 unit tests (`ArtworkDecodeSizeTest` adds four: unmagnified decode,
never shrinking below the layout size, monotonic bucketing, and the cap), lint 0
errors with the same 12 warnings, `assembleDebug` produces a 74,209,472-byte debug
APK (SHA-256 `c245a454ff8806aba3a011f5265d402369fc6fbf998a428f7244afdfd8883817`).
Not installed and not driven by hand: the owner installs APKs himself, and sharpness
is a judgement only he can make on the phone.

### Git identity: one author per agent

`user.name` and `user.email` are unset both globally and in this repository, so a
bare `git commit` fails. That is deliberate. Two agents work in this repository, so
any repo-local default would silently stamp one agent's name on the other's commits;
a refusal is a bug report, a wrong author is not. Each commit names its own author
on the command line instead:

```powershell
git -c user.name='Codex'  -c user.email='codex@local'  commit ...
git -c user.name='Claude' -c user.email='claude@local' commit ...
```

`fbea40e` is Codex's and is untouched. The three commits that carry this slice are
Claude's, so `git log --author=Claude` and `--author=Codex` cleanly separate the two
agents. Both addresses are placeholders, not real mailboxes.

Those three were rewritten twice: once to move them off a per-command author onto a
shared one, then again to split that shared author apart. Their hashes therefore do
not match the ones first reported. No content changed either time -- every tree is
byte-identical to its pre-rewrite object, which is why the working tree stayed clean
across both rewrites.

The user's global Git configuration is still untouched and must not be changed
without explicit approval.

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
- **The reveal target is a folder, not an identity.** "Focus the playing item"
  follows the folder chain recorded at play time. If that folder has been refreshed
  since and no longer lists the track, the pending focus lands on nothing and the
  wall just shows the folder. A queue that outlived the process cannot be traced at
  all and leaves the entry disabled. Both are accepted trade-offs of not building a
  global document-URI-to-folder map; neither is verified on a device.

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
