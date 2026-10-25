# Architecture

## Desktop lyrics boundary (2026-10-11)

- `core/overlay/DesktopLyricsController` is owned/closed by PlaybackService, with
  its own main-thread coroutine scope and Player listener. It never starts another
  foreground service or another player. The Activity is not the timing owner.
- PlaybackConnection transports the scanner's existing per-item matched subtitle
  URI/format/version through MediaMetadata extras. Service uses TimedTextReader's
  bounded SAF read and the common parser off-main once per current item/version;
  clears old document immediately and cancels stale loads on transitions.
- Settings reuse the sole AppPreferences DataStore; independent DesktopLyricsSettings
  is clamped on load/write. No Activity visibility subscription: D-039 keeps the same
  system window/ticker inside and outside the app. MainActivity visibility hooks removed.
  LyricColorHsv is pure JVM and converts UI H/S/V controls to the existing opaque ARGB
  schema. Palette drafts update locally; release/preset/reset writes to DataStore.
- Settings UI remains Compose. Only the system overlay is a small native Canvas View:
  it has no Activity ViewTree owners and must not instantiate a separate Compose player.
  Holds at most four StaticLayouts/timelines, rebuilds on line/width changes, polls
  at 4Hz or 20Hz for active word sweep. No ticker while disabled;
  paused word sweep does not invalidate unchanged text. No blur/images in this window.
- WindowManager TYPE_APPLICATION_OVERLAY requires explicit SYSTEM_ALERT_WINDOW grant.
  Window alpha is capped at min(0.75, platform maximum on API31+) to respect locked
  touch-through; background alpha alone would not meet Android's rule. See
  https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events.
  Position is normalized, clamped to screen bars/cutouts, persisted only on drag end.
  Window metrics are cached until configuration changes rather than queried at 20Hz.
  Screen-off receiver immediately removes the window; keyguard/permission/queue are
  rechecked while backgrounded. Remove window, unregister receiver and cancel jobs
  before releasing the service's player.
- No text document or playback state is persisted in overlay settings. Missing,
  unreadable or untimed lyrics yield no window, rather than stale lyrics or fake times.

## Technology baseline

- Kotlin with Android Gradle Plugin 9.4 built-in Kotlin support
- Jetpack Compose with the stable Compose BOM
- AndroidX Media3 `ExoPlayer` and `MediaSessionService`
- Room for the indexed library and durable playback state
- Preferences DataStore for small user settings
- Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) for the single media root
- Coroutines and Flow for asynchronous work and observable state

Minimum Android version is initially API 29. Compile and target SDK are API 37.

## Planned modules

Start as a single application module while boundaries settle. Packages must follow
these intended seams so they can become Gradle modules later:

```text
app
  core/model          Stable domain types
  core/database       Room schema and migrations
  core/settings       Global appearance model and its pure reading helpers
  core/storage        SAF traversal and document operations
  core/media          Media3 player, queue and media session
  core/metadata       Parsing and transactional tag writes
  core/subtitles      LRC/SRT/VTT parsing and matching
  core/artwork        Artwork resolution, thumbnail cache and privacy aliases
  feature/library     Folder/audio browsing and scan UI
  feature/focuswall   Deterministic spatial layout and gestures
  feature/player      Player, queue, chapters and bookmarks
  feature/subtitles   Lyrics state, following policy and the reading surface
  feature/search      Indexed and pinyin search
  feature/playlists   Manual and system collections
  feature/settings    Root, appearance, privacy and backup
```

## State boundaries

Keep these identities separate:

- Canonical media identity: persistent document URI plus stable fingerprint.
- Canonical metadata: parsed file tags plus user edits written to the file.
- Indexed presentation: cached title, duration and artwork references in Room.
- Playback state: queue, shuffle order, current item, position and completion.
- Focus-wall state: selected item, viewport transform, density and random sample.
- Privacy presentation: a projection applied at the UI/media-session boundary only.

Never mutate canonical metadata to implement privacy presentation.

## Scale constraints

- Stream SAF traversal; do not materialize the entire document tree in memory.
- Commit scan results in bounded batches and expose partial results.
- Page large database queries.
- Store thumbnail cache keys and dimensions, never full bitmaps in Room.
- Decode artwork to the actual display bucket and cap memory/disk caches.
- Make focus-wall layout a pure deterministic function of ordered item keys and
  density; only compose visible/near-visible cards.
- A Media3 position tick may invalidate only the focused current wall card. Ordinary
  posters consume stable identity/presentation inputs and must not observe the live
  playback clock.
- Drag-frame work stays linear in the near-visible set: calculate screen geometry
  once per card, cache stable brushes, and animate only discrete presentation state.
  Do not start a tween whose target is the continuously changing pan/depth value.
- Do not hash complete multi-gigabyte files during routine scans. Fingerprints use
  URI, size, modified time and bounded content sampling when needed.

### Artwork-driven presentation

- Folder display follows D-049: AudioArtworkProvider `/folder` only calls the
  direct metadata reader and opens a direct cover. It never invokes the writer,
  Room/private migration, embedded extraction, recursion, journal recovery or the
  FolderCoverFiles lock. This removes v0.1.7's write-before-display/head-of-line wait.
- `FolderCoverBatch` is the pure, explicitly triggered root processor. It visits
  every descendant even under covered parents, pages 32 natural-order candidates
  from a per-run `CoverDirectoryCatalog` rather than rescanning SAF for each page,
  prefers direct embedded art, then successful child cover, and processes all
  siblings. D-054 adds ordinary-image fallback: the same scratch catalog keeps
  image rows; 32-item pages probe dimensions through an 8 MiB-capped header stream.
  Keep just one best `(URI,width,height)` per subtree; compare Long pixel areas and
  stable natural-name/URI ties. Propagate image fallback provenance separately
  from preferred disk/audio covers so early image-generated children cannot mask
  later sharper images or embedded-art sources. Fully validate/copy only the final
  chosen image through the existing protected writer. No changes to daily display.
  Old private manual images migrate ONLY in this operation, after disk
  cover. No old per-display recursive resolver remains; common types/budgets are
  in FolderCoverModels. Do not count an unpersisted source as generated artwork.
- LibraryViewModel owns a separate batch StateFlow/job, independent of library
  listing/position state. Progress is throttled to 200ms; UI offers confirmation,
  counts and cancellation. Navigation/rotation retain the job; process death does
  not. No new dependency/service/permission. Rerun is idempotent because saved
  covers win. Root changes cancel/join processing before releasing the old grant.
- During explicit work, only existing/recovery and actual install operations take
  FolderCoverFiles' dedicated lock; never hold it across the whole traversal. Daily
  folder reads do not acquire it at all. `LockedFolderCoverStore` explicitly implements
  every store method (no anonymous interface delegation); image/page/audio reads
  forward without holding the write lock. Regression tests exercise the production
  wrapper on both JVM and ART, not only the raw probe/catalog primitives.
  Track extraction retains its old bounded
  cache/monitor and inherited native-allocation limitation.
- `FolderCoverRepository.project` versions raw folder lookup IDs. Successful manual
  save and batch termination re-project snapshots without losing focus/viewport or
  scanning audio again. All folder layouts share captured-target long-press actions;
  audio chooser offers the existing real-tag editor and a separate embedded-art-to-
  parent-cover action. Verify the known local provider's direct-parent document ID,
  same tree and provider child relation. Force a fresh source extraction key for an
  explicit audio action, not a stale/missing image cache entry or inherited fallback.
- D-055 `SafFolderCoverStore.save` stages at most64 MiB to private disk in32KiB
  chunks. Shared `prepareCoverImage` keeps compliant <=8 MiB/2048px originals, or
  software-decodes at a <=2048px target and encodes opaque JPEG/transparent WebP.
  Quality92/84/72/60 and2048/1024/512px retries have a strict8 MiB encoded stream
  cap. No full-size bitmap or encoded ByteArrayOutputStream. A codec-only cancellable
  gate bounds concurrent bitmap work; never resolve content URIs/provider calls
  inside that gate. Caller opens/stages sources before entering it. Prepared temporary
  copies close/delete on success/error/cancel, original staging sources never mutate.
  Validate actual format/bounds and <=128px complete decode, then write/fsync/read-back-
  hash the prepared bytes into the unchanged sibling stage/journal transaction.
  Actual MIME determines the cover extension. Unknown/cloud providers refuse writes;
  local ExternalStorageProvider requires a persisted read/write tree grant and flags.
  Image writes do not broaden the audio-tag editor's stricter provider scope.
- `CoverInstallTransaction` records an AtomicFile journal before renaming an existing
  cover to a hidden UUID backup and promoting the validated stage. This is recoverable,
  not atomic. Next explicit batch/manual access finalises a verified promotion or restores the backup;
  conflicts/corruption retain the journal and copies rather than guessing/deleting.
  Recovery is explicit batch/manual-only now; display never enters the writer.
  Retain backups and interrupted hidden stages; exclude them from all artwork/scans.
  No automatic backup/orphan sweep. Manual replacement refuses multiple cover images.
  Removing an app assignment can no longer reset a real disk cover, so remove that
  action instead of deleting user images. Failed writes have explicit dialog text.
- The shared song/folder `coverImagePickerIntent` keeps its temporary read grant and
  mihon/local start hint; no broad storage permission or retained source-image grant.

- The focus wall's selected item is the authority for the ambient palette. Media3
  playback identity stays independent and does not recolour the wall by itself.
- `core/artwork` may decode one small focused-cover bitmap and return immutable
  colour/dimension facts through a bounded cache. It must not retain the bitmap or
  query the library to build a palette.
- The focus-wall ambient effect uses separate cheap drawing siblings around card
  composition: a backdrop and a lower-alpha poster wash. The wash is necessary
  because the opaque 2 dp mosaic hides nearly all of a backdrop by itself. Neither
  layer consumes pointer input. Colour transition frames must not re-run spatial
  layout; no full-screen artwork blur or permanent frame loop is part of the base
  effect.
- Folia is the sole wall renderer (D-053); remove the old Bento geometry/chrome,
  but retain the off-main immutable scene, spatial index, bounded decode and
  shared gestures. Layout values are explicit 0/1/2/4; legacy 3 also reads as Folia.
- Hoisted `FocusWallViewportState` reads density/zoom/ambient/focus shape/size from
  global Preferences, as do lights-out/follow/tint/motion. FolderSnapshot retains
  only navigation (focused item/pan), listing and subtitle matches. Restoring a
  cached listing must apply current global preferences, never cached appearance.
  Pinch publishes live zoom to the global in-memory value immediately and debounces
  disk persistence; pan frames do not recompose the library or restart that debounce.
  Preference reconciliation must not reset a pinch when another control changes.
- Lights-out, auto-focus, poster tint and reduced motion are global presentation
  settings in their own Preferences DataStore. Keeping them separate from the SAF
  root DataStore prevents an appearance edit from re-emitting the selected root
  and accidentally rebuilding navigation. Rendering consumes immutable values from
  `LibraryUiState`; no presentation preference is written into media or Room.
- Global appearance (accent palette, motion strength, UI scale, one-tap preset)
  lives in `core/settings` as an immutable model with pure helpers, and is applied
  by `ui/theme`. The theme maps the accent onto the Material colour scheme and
  scales `LocalDensity`, so every surface that reads the scheme follows it without
  being told which accent is active. Consumers must read the palette through the
  theme rather than keeping their own copy of the accent values; the settings
  swatches do exactly that, which is why the accent's two theme variants are
  internal rather than private.
- A continuous setting's position on its own control is a pure function of the
  value and that setting's range, in `core/settings`. Rows and pages both read it,
  so a level mark cannot disagree with the slider it belongs to.
- Timed-text parsing and playback timing remain outside visualizer scenes. Cover
  lyrics and full-screen scenes consume the same normalized model rather than
  parsing files independently.
- `core/subtitles` owns matching, the normalized model, the decoder and the bounded
  parse cache; it holds no UI type and reads no settings. Matching is keyed by the
  caller's own item handle, so the storage layer never re-keys a result by title.
- Lyrics state is separate from `LibraryUiState`, because browsing replaces the whole
  library state on every folder change and the playing track's lyrics must survive
  that. The surface loads on request -- current listing, then cache, then file -- and
  shows nothing rather than another track's lines while a load is in flight.
- The cover lyric layer is a bounded projection of that same state: at most three
  strings and one palette-derived shade. It is requested only for a focused Media3
  current item, owns no parser or clock, and disappears when either identity moves.
  Its current line may reuse the shared lyric-wipe renderer, but only this single
  focused/current subcomposition receives playback ticks or runs a sweep-front
  scheduler; ordinary wall cards remain independent of the Media3 position ticker.
  Full-reader following and cover-centred composition intentionally use different
  anchors because one is a document and the other is a poster scene.
- The immersive lyric scene is another bounded projection, not another player. Its
  pure feature model selects at most two lines on either side; the Compose layer
  consumes the existing `PlaybackState`, the
  cached focused-cover palette and the parsed document. Line changes animate only
  when the current line index changes. Disposing the surface cancels its one bounded
  palette request.
- **No frame loop outside the foreground scene.** The wall, ordinary cards and the
  cover lyric layer never run one: they read the Media3 position ticker or a line
  boundary, and every animation they start ends. The full-screen lyric scene is the
  one exception, and only under three conditions at once -- the scene is composed
  (so it is on screen), playback is running, and the global motion setting is above
  zero. Under those conditions two modes (`流光`, `时计`) advance their own clock
  with `withFrameNanos` from `ui/lyrics/LyricsSceneClock.kt`; leaving the scene,
  pausing, or setting motion to 0% ends the coroutine and the picture stops where it
  stands. Compose's own lifecycle is the visibility test -- the loop lives inside the
  mode's composable, so there is no separate visibility flag to get out of step.
  `回环` uses the same bounded rule only while its one line-boundary spring is
  settling; it has no free-running ambient clock. Every other mode keeps the older
  promise: motion amplitudes are pure functions that
  return zero at 0% motion, driven by a line boundary with a spring or tween that
  ends.
- `凝彩` is split at a pure scene-program boundary. `TemperaProgram` deterministically
  compiles the track/line identity and shared word timing into one bounded shot with
  no more than twelve expressive slices and absolute start/settle/end times. The
  Compose renderer is a consumer of that immutable program: artwork, geometry,
  expressive type and the stable readable lyric are separate planes. It may read the
  bounded scene position while composed, but never reparses lyrics, generates random
  values at draw time or owns playback. Future post effects must be capability-gated
  and measured before they join this runtime.
- A timed document opens the immersive scene only when the viewed item is the current
  Media3 item. Loading, missing, empty, untimed and non-current cases stay on the
  diagnostic reader, which preserves truthful failure states and full-document
  browsing rather than inventing a visualizer clock.
- `LyricsVisualizer` has **two shapes**, and the registry holds both. The ordinary
  shape draws the current line and is given a `ContentTransform` for the line change,
  which the stage applies with `AnimatedContent`. The second, `WindowedLyricsVisualizer`,
  is handed a `LyricRailWindow` -- a bounded slice of the document around the active
  line -- and lays every line in it out itself as one moving object, so the stage
  gives it no per-line transition and no compact height. `时计` and `回环` are
  windowed: the former is one lyric wheel and the latter is one depth-sorted tilted
  orbit, and neither can be assembled one line at a time. The registry's `when`
  still has no `else`, so a new mode has to pick a shape.
- While the timed immersive route is composed it owns the entire window. The outer
  `Scaffold` reserves no system-bar insets, the renderer draws edge-to-edge, and a
  scene-local `WindowInsetsControllerCompat` hides the bars with transient swipe
  reveal and restores them on disposal. Header and transport are palette-derived
  overlays: they never change renderer bounds, auto-hide while playback runs, and
  retain 48dp touch targets even though their visible glass is smaller.
- The full-screen lyric scene is a host over **two** registries. `LyricsVisualizer`
  owns the lines and the line-change transition; `LyricsBackground` owns the artwork
  treatment and the shade over it. Both are resolved by exhaustive `when`s with no
  `else`, so a new entry cannot compile until it is implemented, and
  `ui/lyrics/LyricsSceneHost.kt` is the only place that knows about both. Switching
  either one cannot alter the normalized document, Media3 clock or queue; the mode,
  the background and the word-glow timing policy are global presentation preferences
  and survive process recreation.
- The production foreground registry currently contains exactly four modes:
  `Luminous`, `Pendolo`, `Claddagh` and `Tempera`. Removed prototypes are not kept as
  dormant composables. Because early builds persisted raw enum ordinals, each
  surviving mode declares its legacy `storedValue` (3, 5, 6 and 7); removed/unknown
  values migrate to `Luminous`. Never persist the shortened enum's new ordinal.
- 凝彩's optional image library is a bounded list of persistable SAF document URIs
  in DataStore, not copied images and not a directory scan. The renderer chooses an
  image deterministically from the compiled shot id and falls back to the audio cover
  if the library is empty or unavailable. Its stage aspect is a rendering constraint
  inside the full-screen host, so a 4:4 or 4:3 canvas cannot move chrome or system
  insets.
- The split is enforced by the input types, not by convention: a visualizer receives
  `LyricsSceneInputs` (which includes the frame, the format and the glow policy),
  while a background receives `LyricsBackgroundInputs` (artwork, palette, line index
  and motion only). A background therefore has no access to lyric text and cannot
  take over typography. `lineIndex` is the only playback state it sees, which lets a
  boundary-driven background move on a line change without needing a frame loop.
- A mode may expose **parameters of its own** (`feature/subtitles/LyricModeTuning.kt`:
  three for `流光`, ten for `时计`). They are absolute positions with declared ranges,
  stored as integers per mode, and they are deliberately not part of the global motion
  setting: motion decides whether the scene moves at all and scales how far anything
  travels, while a mode's parameters decide the shape it moves in, so the two cannot
  fight. The scene header offers a settings sheet only where
  `LyricsVisualizerMode.hasTunings` is true, a drag previews locally and commits once
  on release, and a commit writes that mode's whole parameter set in a single storage
  edit.
- `LyricsVisualizerMode` and `LyricsVisualizerBackground` are the two persisted
  registries. Storage persists only validated ordinals and each enum keeps its
  rendering in `ui/lyrics`; entries may only be appended because the ordinal is a
  user setting. The background ordinal is nullable and `null` means *follow the mode*
  -- every mode declares a default background, and the three original modes' defaults
  are exactly the treatments they had before backgrounds were selectable, so an
  install that predates the setting renders identically.
- `LyricGlowTimeline` is a presentation projection outside the parser. It splits
  display text into Unicode grapheme clusters, prefers real enhanced-LRC word times,
  and in Smart mode estimates a bounded timeline from line timing. It carries an
  Exact/Estimated source marker and never mutates `TimedTextDocument`. It exposes a
  continuous sweep front instead of a per-grapheme on/off state, so the renderer
  paints one left-to-right colour wipe whose edge can rest inside a display
  character. A local front scheduler shared by the immersive scene and the
  focused-cover current line interpolates between Media3's position updates and
  holds still while the line is paused or global motion is off, so smooth lyric
  timing does not raise the app-wide Media3 position tick rate.
- The focus-wall tools use a translucent frosted-glass treatment with a highlight
  border and a bottom-end scale/fade reveal. It deliberately avoids live backdrop
  capture/blur in this base implementation: sampling and blurring the moving mosaic
  would reintroduce continuous GPU work on the interaction path. True backdrop blur
  remains capability-gated visual tuning after phone profiling.
- Privacy aliases are applied before artwork-derived presentation. Canonical
  artwork and metadata never change.

## Global now-playing entry and full-player presentation

`PlaybackState` carries the current Media3 title, artwork URI and document URI as
presentation metadata in addition to transport state. This lets the app shell render
the global record after navigating away from the folder that created the queue, or
after reconnecting to a restored session, without rescanning a directory. Media3 is
still authoritative; this metadata is not a second queue or player model.

`ui/GlobalVinylPlayer.kt` is a stateless route affordance. Rotation is held in an
`Animatable`, but the angle is consumed by a graphics-layer block so a playback frame
does not recompose the app shell. It runs only while visible, playing and above 0%
global motion. `AsmrPlayerApp` owns visibility, route transition and the bounded last
known `LibraryItem` presentation snapshot.

`ui/PlayerDetail.kt` owns one metadata read, timeline, panels and callback set and
renders one adaptive portrait/landscape player. There is no presentation registry:
the artwork region has no top chrome, and the lower control surface is the one home
for media information, transport and secondary panels. New player features belong in
that shared control layer. A Compose HorizontalPager owns cover and ordinary lyric pages; swiping beyond lyrics
opens the existing immersive route. The shell retains the section when that route closes.
`PlayerPresentation.kt` owns bounded intrinsic fit, left-aligned lyric following and
shared sizing controls. AppPreferences persists sizing percentages on slider release.
A tap on artwork has no lyric-navigation meaning.

`ui/FoliaSeekBar.kt` is the shared seek primitive for the focus wall, full player and
immersive lyric transport. It draws a one-pixel visual line and a tiny head, while the
whole composable height remains the touch target. Do not replace one surface with a
Material slider independently, or seeking will change shape and sensitivity between
otherwise continuous player surfaces.

`ui/GlobalVinylPlayer.kt` owns only bounded direct manipulation and rendering. It
stores no route or playback state. The shell supplies a normalized X/Y position from
`AppSettings`; drag frames stay local to Compose, then the settled edge/height is
written once through `LibraryViewModel`. `PendoloVisualizer` follows the same commit-
on-release rule for its wheel centre: drag preview is renderer-local, and the final
`PendoloTuning` is persisted by the existing host callback.

## Media service

The playback engine belongs to a `MediaSessionService`, not an Activity or screen
ViewModel. UI connects through a `MediaController`. The service owns queue state,
audio focus, noisy-output handling, notification publication and periodic position
persistence.

Privacy aliases must be applied before constructing `MediaItem.MediaMetadata` that
can escape to system or external controllers.

### App-specific state on the session

A Media3 session carries two extra channels beyond the player itself, and this app uses both
for the sleep timer, because its two directions have different shapes:

- **`SessionCommand` for asking.** A sleep timer has to survive the screen locking, so it
  cannot live in `PlaybackConnection` -- that disconnects on `ON_STOP` by design. The service
  owns the deadline; the UI asks it to arm or cancel with `sendCustomCommand`. Custom commands
  are *not* available to a controller by default: `MediaSession.Callback.onConnect` has to grant
  the command, and it must do so by adding to `availableSessionCommands` from the accepted
  result builder rather than by constructing a set, or the session loses every standard command
  and the UI looks like it has no controls.
- **Session extras for reading.** `MediaSession.setSessionExtras` is the only session state a
  controller can read on connect without a round trip, so it is what tells a UI that arrived
  late that a timer is already running. It is republished whenever the timer changes, and
  `MediaController.Listener.onExtrasChanged` is what makes a *paused* player's UI notice --
  the periodic position ticker only runs during playback.

Anything a `Player` already models belongs on the player instead, and repeat is the example:
`PlaybackState.repeatMode` is read straight back off `Player.repeatMode`, so there is no second
copy to go stale and nothing to republish.

### One lyric document per surface

The wall, the full player and the immersive scene all read the same parsed subtitle document,
requested once by `AsmrPlayerApp` from the item the player is on, falling back to the focused
wall item. The player page is a lyric surface too -- its banner and its two-line block are the
same document -- so there is no second parse and no way for two surfaces to disagree about which
line is being sung. State belonging to a track that is no longer playing is ignored rather than
displayed: `SubtitleDetailState.describes(itemId)` is the check.

## The card workshop

A card is three PNG layers, one JSON document, and one geometry file. The layers are
generated on the device from a cover the library already has
(`core/card/CardLayerFactory.kt`); the geometry is a constant; the JSON is written by
`CardConfig`. Nothing about a card is fetched.

### The viewer engine is vendored and hosted; Android owns its presentation

The reference viewer is a static Three.js page in `app/src/main/assets/card/` and runs
in a `WebView`. Its GLB, layer shader, finish uniforms, depth controls and export path
remain the reference engine. Reimplementing that scene in Compose would have been a
different product; fidelity was the requirement, so Three.js still renders the card.

The shell is deliberately three-mode, and one document serves all of them. A normal URL
retains the complete atelier page for desktop comparison; Android loads the same
document with `?embedded=1`, which adds an `app-embedded` class before first paint. That
mode makes the WebGL stage full-window, uses a transparent canvas over the app's dark
tinted background, hides desktop-only masthead/footer furniture, and turns the
title/material/depth controls into a frosted, collapsed overlay. The reliable back
action is Compose-native and sits above the WebView with safe-area padding.
Presentation changes therefore do not fork the render engine or card format.

`?bare=1` adds `app-bare` on top of that, and exists for the one surface where the card
is not a page about itself: the full player's artwork slot, where the card *is* the
cover. The save button, the artwork bar and the controls toggle are hidden outright
rather than collapsed, because a cover that offers to export itself is offering
something the owner did not ask it for. The class has to be added by the inline
bootstrap before the stylesheet link, exactly like `app-embedded`: a class applied after
first paint would show the save button over the artwork for a frame, which is the kind
of thing that reads as a rendering fault rather than as a loading order.
`CardViewerAssetContractTest.bareModeHidesTheShellChromeBeforeTheFirstFrame` asserts
both halves of that -- the ordering in the document and that the stylesheet actually
names the chrome it hides -- because the class existing and doing nothing looks
identical from the phone.

The slot's **shape** is Kotlin's business and not the page's. `resize()` fits the card to
whatever viewport it is given, so a square slot shows about 58% of a 2:3 card's width and
spends the rest on the page's own near-black background -- which is the black frame the
owner reported, and which no CSS change would fix without forking the vendored bundle. The
player instead gives the card a 2:3 box (`Modifier.artworkSized`), read from
`CardLayerGeometry.CanvasAspect` so the slot, the compose form's preview and the warehouse
tile all size against the canvas the layers were built on.

The bundle is nonetheless **patched**, and that is a maintenance fact rather than a
detail. `app.bundle.js` carries local edits in one region of its `app.js` section: the
embedded bootstrap and its viewport measurement from 31086, `resize()` at 31677, and the
gesture and collapsed-control state from 31814 to 31980. The file's own line endings are
a usable index to them -- the vendored original is CRLF and every line the adaptation
added or rewrote is LF, so nothing before the `// app.js` banner at 31085 is touched. An
edit that preserved CRLF would not show up that way, so `docs/HANDOFF.md`'s description
of the change remains the authority and the endings are only a cross-check. The reference
is MIT, so editing it is permitted; re-vendoring it is what needs those ranges read
first, because nothing else records them.

Do not size this embedded shell with `svh`, `dvh`, `vh` or percentage height alone.
The owner's System WebView exposes the correct `window.innerHeight` while resolving the
document's CSS viewport-height units and percentage chain to zero inside the edge-to-edge
Android view. `syncEmbeddedViewport()` therefore publishes measured pixel values as
`--embedded-viewer-height`, `--embedded-controls-height` and
`--embedded-depth-height` before `init()` constructs Three.js, and updates them from
both window and `visualViewport` resize events. CSS consumes those variables; `resize()`
then receives a non-zero stage. This is load-bearing compatibility code, not a styling
preference.

The same rule covers the safe area. The page draws its own glass actions, workbench and
depth panel, and those are the only controls in the app that Compose does not position.
CSS cannot discover where this device's status bar is: `env(safe-area-inset-*)` is a
browser convenience a WebView is not required to resolve, and Android System WebView
reports zero for it in an edge-to-edge app. `CardViewerScreen` therefore measures
`WindowInsets.safeDrawing` -- deliberately the same inset the Compose back control above
the WebView already uses, so the two rows of controls cannot drift apart -- and publishes
it as `--safe-top/right/bottom/left` on `document.documentElement`, re-publishing on every
page state because the properties do not survive a document. The stylesheet combines the
host property with `env()` through `max()`, so whichever source knows wins and neither
being available leaves the constant that was there before. The page is told where the
edges are; it is never asked.

The embedded gesture owns a continuous `-PI..PI` horizontal target rather than the
reference viewer's small tilt clamp. Pointer movement updates that target one-to-one;
crossing `PI/2` changes the reported face, and pointer release settles to 0 or `PI`.
The drag itself never triggers a layout pass or rebuilds textures. Vertical pitch keeps
a smaller bounded range. The control overlay is excluded from pointer input while
collapsed and carries matching accessibility expanded/hidden state.

The page is served by interception, and that is the only architecture available. The
app holds **no `INTERNET` permission**, so it cannot open a loopback socket for a local
server; and `file://` cannot work because the page loads its four binaries with
`fetch` and `TextureLoader`, which refuse a non-CORS scheme. `shouldInterceptRequest`
answers every request instead, from a virtual origin
(`https://appassets.androidplatform.net/cards/<id>/`) that never leaves the process.

Three properties of that host are load-bearing:

- **Routing is an allow-list of known relative paths, not a path join.** `..` is
  therefore *unmatched* rather than *rejected*, which is a structural difference: there
  is no traversal to sanitise. The one path segment treated as data is the card id, and
  it is validated separately.
- **One URL prefix per card.** The WebView's cache key differs per card, so one card's
  texture request can never be answered with another's image; everything also carries
  `Cache-Control: no-store`.
- **An intercepted request never returns null.** `shouldInterceptRequest` fires for the
  top-level document as well, and a null there means the real network -- which, with no
  permission, is a blank page with no explanation. Unknown paths get an explicit 404.

The viewer's save button clicks an anchor holding a `data:` URL that is never attached
to the DOM, so `CardSaveBridge` overrides `HTMLAnchorElement.prototype.click` on the
page rather than listening for clicks at the document, and moves the payload across the
Javascript bridge in bounded chunks.

### Gestures are injected, and they pre-empt the page's own

`CardGesture` is the second injected script. The page's own zoom is reachable only from
a `wheel` listener, and a WebView never delivers a wheel event, so a two-finger pinch
is translated into the wheel events the page already handles rather than into a CSS
transform -- the page's zoom divides the camera frustum and re-renders, so magnifying
costs no sharpness, and there is only ever one zoom implementation.

Every listener it installs is on `document` in the **capture** phase. That is not a
style choice: the page's own handlers are on `#stage`, and a listener on the same
element is queued behind the ones already registered there, so it could not stop them.
A capture listener on an ancestor runs first and can. The script therefore ends the
page's drag with a synthetic `pointerup` before it starts magnifying -- the page tracks
"a finger is down" in a single flag shared by all pointers, so without this the second
finger would keep turning the card -- and stops the events the page would have read.

`CardGesture.MinZoom`/`MaxZoom` duplicate the vendored bundle's wheel clamp, because a
Kotlin constant cannot read a JavaScript one. `CardGestureTest` asserts the two agree by
reading the clamp out of `app.bundle.js`, so a drift between them fails the build rather
than being discovered as a pinch that stops moving partway.

### Cards are files, not rows

`filesDir/cards/<cardId>/` holds `card-config.json`, `assets/{subject,background,text}.png`,
`preview.png` (the flat picture of the card face, written when it is built),
`card.png` (the capture the viewer's own save produces) and `meta.json`. A new Room
entity would have meant a schema version bump with no migration mechanism in the
repository, for an index that the filesystem already is. Keeping it on disk also makes
"deleting a card only removes what this app made" true by construction: `AGENTS.md`'s
rule that deletion means removing an index entry rather than user media holds without
any code being written for it.

`CardSummary.thumbnail` is `card.png` when it exists and `preview.png` otherwise, so a
card always has a face. The preview is a quarter of the card's pixels (512x768) drawn
from the same three layers the viewer loads, and it is deliberately not a substitute
for the capture: what it shows is the card lying still, with no foil, tilt or lighting.

`meta.json` holds four keys -- `mediaId`, `pinned`, `source`, `createdAtMillis` -- and
is the file a future version will add keys to, so `CardMeta.parse` is the one reader in
the package that falls back on every field rather than failing. `mediaId` is the
`LibraryItem.id` (the document URI's identity, not the title), which is what makes
`CardRepository.findForMedia` answer "does this song already have a card?" -- the
question the full player asks before showing a card in its cover slot.

The collection room is a view over that directory, and holds no state of its own. Its
shelves, its search and its sort are all recomputed from the listing on every read
(`CardShelves.shelve`), so there is no index that can disagree with the files -- a card
deleted by a future version, or by the owner through a file manager, simply stops
appearing. The one property the owner can change about a card after it is made is
pinning, and pinning is a key in `meta.json` rather than a list somewhere else, for the
same reason. Batch operations are the single-card operations applied in a loop through
the same repository methods, which keeps one implementation of "delete a card" and keeps
the delete confined to directories this app created.

`CardStudioScreen` owns three pages of its own (the room, the compose form, the viewer)
as one internal `rememberSaveable` enum, following `FocusCoverWall`'s `WallPanelPage`
rather than adding routes to the app's hand-written navigation. The app passes it one
nullable seed -- the item the studio was opened from -- captured when it is opened rather
than read live, so a session started from Settings (where nothing is playing, and where
the room is about the collection rather than about the current song) cannot be seeded by
whatever was last open in the player. Alongside the seed it passes `startAtCompose`, which
is the other half of "why was this opened": a song's page opens the form and Settings opens
the room (D-032). The two travel together because they are decided at the same moment, and
neither can be read later off the seed -- `startAtCompose` is true precisely when the seed
is a song that has no card yet, which is a fact the studio cannot see from the item alone.

`CardStudioScreen` reports a finished card back up through `onCardCreated` rather than
being read from. The player caches its own song's card per item, so a card made while that
page is still mounted is exactly the change it cannot otherwise see; the app answers by
bumping `cardRevision`, which is a key on the player's `LaunchedEffect`. Nothing else about
the card's state crosses the boundary -- the studio stays the only thing that knows what
the room contains.

The player's cover choice is held as a nullable `Boolean` keyed to the item, not as a
`Boolean` derived from the preference, so that "the owner has not said anything about this
song on this visit" is a value the UI can be in. Folding that with the song's `hasCard` and
with `cardCoverByDefault` is `showsCardCover` in `PlayerDetail.kt`: a pure top-level
function taking three plain values, deliberately not a `CardSummary?`, so the fold is a
unit test without a card on disk (`CardCoverChoiceTest`). Reading the preference into the
composable and letting it decide there would put the interesting case -- a choice
outranking the setting in both directions -- somewhere no test can reach it.

The one place a card leaves the app is `CardExporter`, which inserts its own entry into
`Pictures/白相/` through MediaStore. That is an app creating and then owning a row,
which needs no permission from API 29; the app never reads, enumerates or deletes from
the picture library, so there is nothing to grant and nothing to revoke.

## Transactional tag writes

Tag writing operates through a provider capability check and explicit job state.
The source document is copied or rewritten into a sibling temporary document,
re-opened and validated, then swapped only when the provider supports a safe
replacement sequence. A failed or interrupted job is recoverable on next launch.

No implementation may claim atomic replacement when the underlying document
provider cannot provide it. D-040 explicitly authorizes a **recoverable, non-atomic**
two-rename sequence for primary internal ExternalStorageProvider documents only.
This supersedes the former blanket strict-atomic/read-only clause, not the duty to
refuse unsupported providers. Ordinary SAF write/rename flags alone are insufficient.

Library long press uses the same MetadataEditorDialog/MetadataEditViewModel in
cover-only mode, capturing id/document/title/artwork independently of playback.
`metadataFieldsForSave` returns no text edits in that mode and AudioTagEditor skips
savePropertyMap for an empty field set. Expected original properties are still
verified after the cover mutation, along with normal payload/probe/journal checks.
No-op cover saves are disabled. Success uses the existing onMetadataSaved path to
patch bounded snapshots, retained player art and Media3 queue without rescan; only
a fully completed save closes this cover-only editor automatically. Recovery-pending
success keeps its recovery controls. Folder covers use D-048's separate real-cover
image repository; the ordinary full metadata form retains its original behavior.

`core/metadata/AudioTagEditor` serializes all edits/recovery with one process mutex.
`SafTagDocuments` validates persisted read/write grants, parent/name binding, provider
flags, regular seekable FD/size and space for one full sibling plus 64 MiB. It never
reconstructs a `/storage` path; native writable FDs are restricted to generated stage
documents. TagLib 1.0.6 owns duplicated detached FDs; original FDs stay read-only.
Native parsing/mutation runs on IO, with max 16 MiB container metadata, 16 pictures /
8 MiB total, selected cover sampled to 1024px and at most 4 MiB JPEG. Unknown tags,
unchanged multi-values and non-front art are retained and verified. MP4 has no front/
back picture types, so the first cover is the replaceable one.

`AudioPayload` is independent, JVM-testable and streaming (128 KiB / Long offsets):
MP3 audio region, FLAC STREAMINFO + audio, MP4 mdat, WAV fmt + data. `AudioProbe` uses
Android MediaExtractor to compare codec setup and first/last packets. Both must agree
before a source-affecting rename. Unsupported layouts fail closed. Whole-file hashes
also guard source changes and final install/recovery; no exclusive cross-app lock is
claimed. Do not concurrently edit the same file in another app.
The first writer refuses DRM/encrypted samples and containers with more than one
track; it does not claim to verify other tracks or DRM auxiliary offsets.

`MetadataTransaction` persists an AtomicFile journal in noBackupFilesDir **before**
create/rename/cleanup work, including both UUID artifact names. Commit pauses
cancellation before backup/install/verify. Recovery checks original/backup hashes,
rolls back uncommitted installs, cleans only verified owned artifacts, and refuses
third-party conflicts. Source audio is not permanently duplicated. Root UI offers
recovery even if the original filename is temporarily absent. The scanner hides
only strict generated artifact names. One bounded old-tag/art snapshot is retained
privately; it is marked committed with a digest but has no undo UI yet.

The Activity ViewModel holds one editor session/draft across rotation, not one VM per
track. Late/cancelled loads cannot retarget a new editor. Physical filename remains
unchanged. Successful save refreshes actual detail tags, artwork version and bounded
folder snapshots in place (focus/viewport retained); the affected current Media3
item is stopped/replaced/prepared at its prior position with queue/lyric extras kept.
An unrelated playing item is not stopped. Playback resumes manually after editing.
Native device tests and provider failure injection are separate from JVM transaction
tests; a compiled instrumentation APK is not evidence that device writes passed.

## Testing strategy

- Pure unit tests for natural sorting, folder-art resolution, subtitle matching,
  completion thresholds, shuffle persistence and privacy projections.
- **Unit tests run on the JVM; the app runs on Android's ICU. They do not agree about
  regex syntax** -- ICU rejects an unescaped `}` that `java.util.regex` reads as a
  literal, which is how a green 193-test suite shipped a `PatternSyntaxException` that
  crashed the app whenever a subtitle opened. Escape every literal brace and bracket
  in a pattern, and treat a device run as the only proof for regex behaviour.
- Room migration and query tests with large generated fixture sets.
- Instrumented SAF tests using controlled document providers.
- Media service tests for restore, audio focus and noisy-output behavior.
- Compose tests for focus-versus-playing state and navigation semantics.
- Macrobenchmarks for cold library launch, scrolling, thumbnail loading and focus
  wall gestures at representative scale.

### Player source-proportion cover and spatial field (2026-10-18)
CoverFacts retains rawPaletteArgbs alongside the unchanged balanced wall palette.
The same bounded thumbnail decode adds PlayerColourField: nine region colours plus
nine regional/global dark fractions, at most 4096 sampled pixels. Prefer chromatic
midtones to pale highlights; fall back to bright colour, neutral region average or
source-derived global colour. No original pixel/image array is retained in the LRU.

PlayerBackdrop receives the player's already loaded facts rather than initiating a
second read. A pure renderer spreads these anchors with smooth Gaussian weights,
retains dark coverage, maps brightness for white-text contrast and adds fixed tiny
dither. Generate one 96x144 opaque texture (~54 KiB) on Dispatchers.Default; display
with bilinear filtering and a motion-aware crossfade. No full-screen artwork blur,
per-frame sample, permanent ticker or wall-palette change. Hold the preceding field
while new facts load, but replace it with an explicit fallback after missing/failed
artwork rather than retaining another song's colours indefinitely.

PlayerArtwork's successful Coil decode publishes its displayed width/height ratio,
including orientation, to URI-keyed player state. Ignore <0.002 ratio jitter to avoid
resizing feedback. The sampled facts ratio is only a fallback, then 1:1 while no
valid dimensions exist. Keep ContentScale.Fit, existing adaptive size/percentage
bounds and the card branch's independent geometry. No implicit trimming of square
source images with baked-in padding. Phone reports of a square cover are not a
proven single root cause: this removes the code's palette-read dependency, with
actual failing media/device reproduction still pending.

### Lazy audio artwork (2026-10-11 follow-up)
SafDirectoryScanner previously assigned currentFolderArtwork to every audio. It now
builds a versioned private content URI per retained track, with optional strict-basename
sidecar/folder fallback. Directory enumeration does not call MediaMetadataRetriever.
AudioArtworkProvider (not exported, read-only) extracts embedded art on image request;
stages embedded bytes up to64 MiB privately and reuses D-055 preparation rather than
rejecting every source above8 MiB. Cache each validated/normalized result at <=8 MiB;
64 MiB/192 disk entries and192 in-process negative results. Version the cache key
with prepared-v2 (same bounded directory) to avoid stale raw/missing results; source
size/mtime and URI still isolate entries. All file writes/deletes are confined to the provider's own
cacheDir/track-artwork-v1, never SAF media. Native retriever returns an embedded byte
array before its size can be checked (a known API memory limitation). This change
bounds subsequent retained source/codec/output work, not that native allocation.
>64 MiB/unreadable sources still fall back; decodable large embedded art is sampled,
not fully pixel-decoded. Metadata-editor transaction/tag limits are unchanged. CoverFacts retains a separate
playerToneArgb derived from mid-tone chromatic coarse buckets; wall palette stays unchanged.

### Large-wall follow-up (2026-10-18)
- `FocusWallSceneRequest` captures immutable inputs on the UI thread. Scene geometry,
  ID map, world bounds and `FocusWallSpatialIndex` are constructed on Dispatchers.Default
  and published together. A cancelled producer cannot replace the newest request;
  same-folder reflow can retain its prior scene, but another folder's scene is never shown.
  Only FullCover uses decoded aspect as a layout input; palette arrival alone is not a reflow.
- The two-axis centre-bucket index replaces the per-frame y-strip scan. Query bounds
  still expand by maximum half-size before exact viewport intersection; result order
  is preserved. Giant queries scan finite populated buckets rather than empty grid space.
- Continuous pan/zoom/distance alpha are read inside each card's existing graphicsLayer,
  not captured as per-frame subcomposition inputs. Discrete reflow/light animations,
  resolution buckets, culling margins and draw order are unchanged; no extra GPU layer.
- `JokesongApplication` supplies one shared Coil loader. Strong image cache uses 1/8
  max heap (4–64 MiB), no unbounded weak-reference index; fetch concurrency 4, decode 2.
  This limits cache/in-flight pressure, not the memory of currently visible bitmaps or
  an oversized native embeddedPicture allocation; the known native limitation remains.
- D-056 removes the remaining first48 child-folder summary scans from
  `SafDirectoryScanner`. Listing queries only its own children and own title; never
  enumerates child folders, opens audio or decodes artwork. LibraryItem no longer
  carries child/audio counts. Unused Room schema columns remain for compatibility.
- `readDirectFolderArtwork` requests only document ID/name/MIME and retains only a
  deterministic direct named-cover candidate, not audio/folder counts or first audio.
  Null provider cursors are errors, not cacheable missing-cover results. It never
  opens media or recurses. Normal scanner retains size/modified for audio versions.
- AudioArtworkProvider's folder branch uses FolderArtworkLookupCache:256 metadata
  entries (positive AND absent), LRU,32 bounded coordination locks. Same-key concurrent
  requests coalesce; no global LRU lock covers I/O. Waiting and before-publication
  checkpoints propagate cancellation; errors/cancellation are not cached. Full folder
  URI includes scan+FolderCoverRepository write revision, so explicit refresh/manual
  replacement/batch completion invalidate misses/hits; snapshot navigation reuses them.
  No bitmap cache enlargement, on-disk derived images, broad permission or writes.
- Settings → Library can explicitly copy numeric runtime/last-three self-process
  ApplicationExitInfo facts (API30+), library count/layout/zoom/density and build ID.
  No descriptions, process names, media paths/names, trace streams or automatic upload.
  This is a diagnosis aid, not an independent crash reproduction or a profiling result.


### Batch cover metadata catalog (D-050)
`CoverDirectoryRows` builds/sorts one folder's supported audio/folder/cover metadata,
with 50k-entry / estimated 16 MiB limits. `CoverDirectoryCatalog` atomically inserts
its snapshot and completion marker into a private temporary SQLite file. Pages use
indexed owner/kind/position and URI-to-position lookup, never re-enumerate SAF for
traversal. DB maximum 256 MiB; memory journal and synchronous OFF only for disposable
metadata. The image writer's durable journal/fsync are unchanged. Completion/cancel
closes/deletes only this owned scratch DB; process death may leave a private cache file.
Fresh cover conflict scans remain live; transaction caches store only positive URIs
and update on rename. No negative conflict cache. Empty legacy directory absence
skips unnecessary Room queries. Progress publishes entry before expensive extraction.

## Local annotations (2026-10-24, D-051)

`core/annotations` owns Room v2 entities, policy, DAO and the explicit additive 1->2
migration. Existing eight tables/rows are untouched; no destructive fallback.
`user_items` is a small snapshot keyed by root tree URI + document URI, not the
unimplemented full library index. `user_tags` has a root/normalized-name unique
index; `user_item_tags` references snapshot/tag identities. The transaction validates
same-tree ownership, tag-root ownership, bounded names/counts and audio-only scores,
then atomically replaces assignments/score. Normal browsing never queries/writes
annotations per poster or observes a playback tick.

`ui/annotations` is a native Compose editor plus paged tag/score results. The editor
captures a Serializable scalar snapshot, keeps drafts through rotation/process-state
restoration, suspends Save while loading/saving, preserves selections outside the
searched page, and commits only on Save. Current selected root guards the host.
The player badge observes only the one root/document score. Queries are lifecycle
aware; local I/O failures are caught and reported. All user strings are resources.

MainActivity plays a tag-result audio as one captured queue entry, with its matched
subtitle extras; LibraryViewModel retains that one external subtitle match and its
parent origin so immersive/desktop lyrics and reveal can reuse existing paths.
Folder results open root -> exact folder, not an incorrect inferred parent chain.
No new player, permission, network/API dependency, recursive scan or media writes.

Verification layers: policy/snapshot/serialization JVM tests; `tools/test-annotation-sql.py`
checks actual migration/query SQL on generated in-memory desktop SQLite; generated
Android `AnnotationDatabaseTest` checks Room validation, v1 bookmark preservation,
root isolation, transactional failures and reopen durability. Building that Android
test APK is NOT executing the test; phone/Room runtime acceptance remains explicit.

## Portable annotation backups (2026-10-24, D-052)

Room remains v2 (new bounded DAO readers/merge methods, no schema change).
`PortableAnnotations` is JVM-only: versioned binary records, explicit UTF-8 lengths,
strict relative components and bounded counts. Password-derived AES-256-GCM uses
PBKDF2-HMAC-SHA256/210k rounds + fresh 16-byte salt, 8-byte random nonce prefix and
32-bit frame counter. <=64KiB frames authenticate header/counter/plaintext length;
a mandatory authenticated empty final frame plus EOF rejects truncation, ordering,
duplication and trailing data. Cipher internals never buffer a whole 128MiB file.
Passwords are transient, never in saved state/DB/headers; no device keystore lock-in.

Export reads the selected root's tags/items in 50-row stable-key pages inside a Room
read snapshot, writes only encrypted app-cache staging, then copies to user-chosen
CreateDocument URI. Read back length/SHA256 before reporting success. Partial provider
writes aren't labelled backups; no real media is mutated. *.jst is Git-ignored.

Import authenticates/parses the whole stream into a generated private SQLite stage
(256MiB cap) before provider resolution or live writes. Duplicate record paths/URIs,
invalid paths/counts/scores fail. Stage metadata queries materialize one bounded record
at a time; relative-path resolution caches at most 128 documents. The only document-ID
path shortcut is Android ExternalStorageProvider; generic export uses findDocumentPath,
import resolves exact unique child names. Root IDs/new volume prefixes are never
copied from the backup. Missing/type/ambiguity skips are previewed; explicit root
confirmation required. Restore uses one outer Room transaction, normalized union,
keep-current non-null score, fresh artwork provider endpoints and revalidated optional
same-folder subtitle paths. Re-run is idempotent. Root grant is checked again at commit.

Activity-owned AnnotationBackupViewModel retains the job/encrypted stage/preview
across rotation. UI owns only transient password strings and picker launchers. Stop
is cooperative, with an honest already-committed race message. Closing a non-busy
session removes only generated cache files. Process death may leave private cache
artifacts for OS cleanup; no durable job/automatic cloud sync is claimed.

Pure JVM tests exercise crypto round trips/authentication/wrong passwords/truncation/
frame reorder/trailing/length bounds and portable paths/types/merge. Generated Room
instrumentation adds union/keep-zero/idempotency/whole-import rollback. Device SAF,
provider read-back, interruption and rotation still require explicit phone acceptance.

### Portrait cover/lyric measurement (D-053)

`PlayerCoverPage` measures the bounded lyric window first, keeps the former
artwork allocation and intrinsic fit, then places that window halfway between the
actual image bottom and the seek line. `coverPageVerticalPlacement` is a pure,
unit-tested geometry function; placement is clamped for constrained height. No
extra image decode, frame loop, player or media mutation is introduced.


D-055 implementation refinement: seekable plain ID3v2.3/v2.4 APIC artwork is read
as a bounded range directly from the granted SAF descriptor to private staging,
using <=64MiB tag/4096frames, bounded MIME/description parsing and32KiB copies.
Prefer Front Cover, otherwise first usable picture. Do not read/hash audio payload.
Unsupported tag transforms/other formats retain native extraction. Only that native
fallback retains embeddedPicture's pre-cap allocation limitation; do not promise
all tag variants or containers bypass it. Both paths feed the same normalizer.
