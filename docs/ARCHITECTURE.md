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
  core/storage        SAF traversal and document operations
  core/media          Media3 player, queue and media session
  core/metadata       Parsing and transactional tag writes
  core/subtitles      LRC/SRT/VTT parsing and matching
  core/artwork        Artwork resolution, thumbnail cache and privacy aliases
  feature/library     Folder/audio browsing and scan UI
  feature/focuswall   Deterministic spatial layout and gestures
  feature/player      Player, queue, chapters and bookmarks
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
- Do not hash complete multi-gigabyte files during routine scans. Fingerprints use
  URI, size, modified time and bounded content sampling when needed.

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
- Room migration and query tests with large generated fixture sets.
- Instrumented SAF tests using controlled document providers.
- Media service tests for restore, audio focus and noisy-output behavior.
- Compose tests for focus-versus-playing state and navigation semantics.
- Macrobenchmarks for cold library launch, scrolling, thumbnail loading and focus
  wall gestures at representative scale.
