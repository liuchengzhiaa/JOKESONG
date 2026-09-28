# Architecture

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
- Density, continuous zoom and Folia ambient strength are properties of the
  hoisted `FocusWallViewportState`. The active wall publishes its viewport to the
  bounded folder snapshot; ambient strength is stored as an ordinal alongside the
  existing density/focus-shape/focus-size ordinals. UI types never enter the
  storage package.
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
that shared control layer. A horizontal left swipe on artwork is the sole transition
to timed lyrics; a tap has no lyric-navigation meaning.

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

## Transactional tag writes

Tag writing operates through a provider capability check and explicit job state.
The source document is copied or rewritten into a sibling temporary document,
re-opened and validated, then swapped only when the provider supports a safe
replacement sequence. A failed or interrupted job is recoverable on next launch.

No implementation may claim atomic replacement when the underlying document
provider cannot provide it; such providers are read-only for metadata editing.

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
