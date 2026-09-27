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
  when the current line index changes. There is no visualizer coroutine or permanent
  frame loop, and disposing the surface cancels its one bounded palette request.
- A timed document opens the immersive scene only when the viewed item is the current
  Media3 item. Loading, missing, empty, untimed and non-current cases stay on the
  diagnostic reader, which preserves truthful failure states and full-document
  browsing rather than inventing a visualizer clock.
- `LyricsVisualizerMode` is the registry boundary for full-screen lyric scenes.
  Storage persists only its validated ordinal, while the UI maps each entry to a
  distinct composition and transition. Switching modes cannot alter the normalized
  document, Media3 clock or queue. The active mode and word-glow timing policy
  are global presentation preferences and survive process recreation.
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
