# Folia-inspired presentation plan

Status: active implementation plan, last updated 2026-09-27.

This document is the continuity record for the Folia-inspired visual work. Read
it together with `PRODUCT_SPEC.md`, `ARCHITECTURE.md`, `DECISIONS.md`, `TASKS.md`
and `HANDOFF.md` before continuing this area in either Codex or Claude.

## Reference and clean-room boundary

The behaviour was studied from:

- the owner's local demonstration video, `F:\41646818644-1-16.mp4` (Folia 0.7.4);
- Folia Major at <https://github.com/chthollyphile/folia-major>, inspected at
  commit `b97235f935f408c3fa92398ca7189766d78502a9` (reported as v0.7.8);
- especially the upstream `app/lattice`, `visualizer` and appearance settings
  code.

Folia Major is AGPL-3.0. It is a behavioural and visual reference, not a source
of code for this Android app. Keep our Kotlin/Compose implementation independently
authored. Do not paste or mechanically translate Folia source. The implementation
below deliberately maps the ideas to Android's lifecycle, Media3 and rendering
constraints rather than copying the Electron/Pixi architecture.

## What the reference actually does

### 1. Cover/theme light

The Lattice wall uses the active theme's accent and secondary colours in two broad
radial glows over the base background, then adds shallow edge falloff and a subtle
noise treatment. When the theme follows the cover, changing the song changes those
lights. This is why the demonstration reads as the entire room changing colour,
not as a blurred copy of the cover behind the posters.

Folia's separate full-player `LatentBackground` is a much heavier effect: it can
use more cover colours, GPU shaders, mesh/dither/noise and audio-reactive motion.
That is a later visualizer feature and must not be confused with the cheap, mostly
static light under the poster wall.

Android translation:

- in this app, derive the palette from the **currently focused** cover. This is an
  intentional owner-approved difference from following the playing song: the wall
  treats focus as its visual subject while playback remains independent;
- render oversized radial gradients and a vignette below the wall, plus a much
  weaker copy over the posters because a 2 dp packed mosaic otherwise hides the
  backdrop almost completely;
- cross-fade colours only when the media changes;
- do not blur a full-screen bitmap and do not run a continuous shader in phase 1.

### 2. Lyrics on the expanded poster

The reference mounts its lyric renderer only after the poster's expansion has
settled. It keeps only nearby lyric rows, distinguishes previous/current/upcoming
lines, and animates alpha, blur, scale and position. The active words use a timed
sweep; glow and optional keyword/chorus colours come from the active theme. The
cover is darkened by directional gradient shades so text and controls remain
readable. In the inspected version the poster artwork itself is not continuously
blurred; the layered shade plus blurred inactive lyric lines creates much of that
impression.

Android translation:

- first build one shared timed-text model from LRC/SRT/VTT;
- mount the on-cover lyric layer only for the expanded current audio card;
- show a bounded window of lines and update only the active timing state;
- use ordinary Compose text/shadow/alpha/scale first; reserve RuntimeShader or a
  custom renderer for a measured need;
- never parse or lay out an entire long subtitle document every frame.

### 3. The unusual full lyric screen

The icon on the expanded poster does not open one fixed lyric page. It opens the
full visualizer host. A registry selects among multiple lyric-scene implementations
and multiple background implementations; a shared playback clock, palette and
lyric model feed the selected pair. The inspected source has many visualizer modes
and several background families, which is why the result feels more like a scene
system than a decorated list of lyric lines.

Android translation:

- make a full-screen `ImmersiveLyricsHost` with stable inputs: playback clock,
  timed lines, representative palette, artwork reference and motion policy;
- ship one excellent mode before adding a registry of alternatives -- done: phase 5
  shipped one scene, and phase 6 turned it into the host plus the two registries
  described below;
- keep future scenes replaceable behind an interface so the player and subtitle
  parser do not depend on one animation design -- done: `LyricsVisualizer` and
  `LyricsBackground` are resolved by exhaustive dispatch, and the host owns the
  clock, the document and the transport for every implementation;
- respect reduced-motion and battery/thermal constraints from the beginning.

### 4. Bottom-right wall controls

The reference opens a compact panel beside the bottom-right button. Its important
actions are: focus the currently playing poster, automatically focus on track
change, open the queue, switch the wall lights, and show help. These are wall
commands, not media metadata.

Android translation:

- extend the existing anchored wall-tools panel instead of adding a modal sheet;
- keep manual focus and playback identity separate;
- auto-focus remains opt-in and must be persisted;
- lights-out dims non-current posters while keeping current/focused controls
  usable;
- queue opens the app's queue surface once the durable queue exists.

### 5. Context/command options

The reference's commands map as follows:

- **Lattice poster tint**: a configurable colour overlay quiets ordinary posters;
  current, focused and hovered posters remove the overlay.
- **Light/dark**: changes the theme, including the base behind the glows.
- **Queue**: opens the play queue.
- **Lattice auto-focus**: recentres the playing poster when Media3 advances.
- **Choose visualization**: selects one registered full lyric scene.
- **Transparency**: makes the Electron player window transparent for OBS/window
  compositing. Android has no equivalent app-window workflow, so this must be
  reinterpreted as in-app background intensity/blur, or omitted; it must never try
  to expose content behind another Android app.

## Recommended implementation order

The order is dependency-driven. Later visual layers must consume stable playback,
palette and lyric models instead of each inventing their own.

### Phase 1 — cover palette and global ambient light (implemented; phone tuning open)

Goal: changing the focused cover gently changes the Folia wall's overall light.

Implementation contract:

- source identity is the wall's `focusedItemId` and that library item's
  `artworkUri`; playback identity does not choose the palette;
- decode only the focused cover at a small bound (currently 96 px) and sample no
  more than 4,096 pixels;
- quantize and rank colours deterministically, discard transparent and unusably
  black/white samples, keep up to four sufficiently distinct colours;
- reuse `CoverAccentLoader`'s bounded LRU cache; never scan all 40,000 audio files;
- when the focused item has no real cover available, use its
  deterministic placeholder accent so the transition does not flash empty;
- draw three broad gradients and a shallow vignette below the poster canvas, then
  repeat only the colour gradients at low alpha above the opaque posters so the
  effect is actually visible across a tightly packed wall;
- cross-fade palette changes over roughly 900 ms; after the transition, the layer
  is static and consumes no frame-rate animation work;
- expose off/low/medium/high strength in the anchored wall tools, default to
  medium, animate strength changes, and persist globally across folders (D-053);
- keep cover density and the live 72%–135% canvas zoom readout/control in that
  same tools surface instead of permanently spending wall height on a top row;
- apply the effect to the Folia wall, the sole wall renderer after D-053; the
  obsolete Bento/V2 layout/renderer has been removed;
- light and dark themes use different alpha strengths so text/card contrast stays
  usable;
- when privacy presentation is implemented, the palette input must be the alias
  artwork projection, never the hidden real cover.

Acceptance:

1. Focusing cover A gives a visibly related but restrained ambient colour across
   the Folia wall.
2. Focusing a differently coloured cover cross-fades rather than flashing.
3. A playback track change without a focus change does not replace the palette.
4. Clearing focus fades the light back to the neutral wall.
5. The low-alpha poster wash does not consume gestures or obscure controls.
6. A long pause produces no continuous animation or full-size bitmap allocation.
7. Palette extraction is unit-tested for determinism, distinct-colour selection,
   filtering and output bounds.

Deferred from phase 1: grain/noise, audio reactivity, a lights toggle, poster tint
and device visual profiling.

### Phase 2 — wall presentation controls (current)

Implemented in this order:

1. lights on/off, dimming only non-current posters;
2. auto-focus current track on queue advance (off by default and persisted);
3. configurable poster tint and strength, exempting current and focused cards;
4. queue entry once queue persistence has a real UI destination;
5. reduced-motion settings.

The existing explicit “focus playing item” action remains. None of these switches
may rewrite artwork, metadata, playback identity or library records.

The controls live behind one `墙面显示` row in the anchored Folia tools panel so
the root panel stays short. Lights-out caps only ordinary poster alpha at 22%; the
focused card and current Media3 item are exempt and keep their controls readable.
Auto-focus is off by default and responds only to an actual Media3 item-ID change
while the Folia layout is visible; opening the wall or enabling the setting does
not move it. It reuses the explicit reveal path, including its cross-folder
queue-origin safety check. Poster tint is derived from the focused palette (theme primary is the
neutral fallback), affects only ordinary posters, and offers 0/10/20/34% alpha.
Reduced motion removes focus reflow/centering fades, ambient/tint fades and inertial
release rather than running shorter decorative animations.

All four choices are global presentation preferences stored in a separate
Preferences DataStore, so changing them cannot re-emit the SAF root preference or
trigger a library rescan. The queue row remains intentionally deferred: the current
queue panel is owned by the full player and the queue itself is not durable yet.

Acceptance still requiring a phone review:

1. Lights-out is visibly darker without hiding the current/focused card.
2. Auto-focus follows next/previous and never moves the wall while it is disabled.
3. Tint levels are distinct but titles and controls retain sufficient contrast.
4. All choices survive process restart; none rescans the folder.
5. Reduced motion stops coast/reflow/fades without breaking direct pan or focus.

### Phase 3 — timed-text foundation

- match same-directory LRC/SRT/VTT including optional language suffixes;
- parse into one normalized timed-line/word model;
- expose current line, bounded neighbours and seek-by-line from playback time;
- cache parsed text by document identity/modified time;
- support untimed LRC text without pretending it is synchronized;
- implement subtitle detail first so parser and timing can be verified without
  the expensive visual layers.

### Phase 4 — lyrics on the focused/current cover (implemented and owner-accepted)

- only the currently playing, expanded audio card receives live lyrics; the one
  shared subtitle state is requested while focus and Media3 identity agree and is
  cleared as soon as they do not;
- a pure projection reduces the cached document to at most previous/current/next,
  so the wall never composes the complete subtitle file;
- Folia follows the owner's
  470 x 467 reference as normalized poster geometry: status/title at upper-left,
  the current lyric centred at 50%, symmetric context centres at 35.5%/64.5%, small
  transport icons near 88%, and a thin seek line near 95%;
- line changes cross-fade according to the global motion percentage;
- a cover-wide asymmetric black field derives its alpha from the brightest
  representative cover colour (36%--72%). It protects bright-cover readability
  without permanently blurring the artwork bitmap or putting a rounded pill behind
  the words;
- Folia exposes only real actions in its reference-sized control cluster:
  play/pause, available previous/next, lyrics detail, clear focus and precise seek;
  it does not draw non-functional repeat or queue icons merely to copy the frame;
- non-focused Folia cards now share the hero's 2 dp corner, restrained shade and
  proportional lower-left typography, but omit lyrics and all transport/playback
  badges. A tiny square folder/audio mark remains because the product still needs
  to distinguish the two item types without inventing Folia's queue numbers;
- untimed text remains absent from this synchronized scene rather than pretending
  to follow playback. The current line now reuses phase 6's Off / Smart / Exact-only
  wipe and its presentation-only timeline; previous/next lines stay static.
  Only the focused/current poster owns the local sweep-front scheduler, so ordinary
  cards do not recompose at lyric cadence.

The owner accepted the current result after the measured Folia composition and the
frame-path optimization. Later visual tuning may still refine individual values,
but it no longer blocks the next presentation phase.

### Phase 5 — immersive lyric visualizer V1 (implemented and owner-accepted)

- the cover action now opens one full-screen Compose lyric scene for a timed file on
  the current Media3 item; all diagnostic/untimed/non-current states retain the
  proven reader;
- scene inputs are the shared playback clock, normalized lyrics, bounded focused
  palette and artwork; it creates neither a player nor another parser;
- a five-tier lyric field (three tiers in short landscape) changes only on line
  boundaries, moves in seek direction and follows global motion strength; enhanced
  LRC receives active-word colouring without pretending ordinary formats have it;
- every visible line is a seek target, and a precise slider plus previous/play/next
  controls operate the existing Media3 session;
- one static blurred cover and cached palette gradients provide depth. There is no
  permanent frame loop; the palette load is cancelled when the scene leaves.

The owner accepted V1 on the phone and explicitly moved the work to phase 6.

### Phase 6 — the lyric scene is a host over two registries (framework implemented)

`ui/ImmersiveLyricsScene.kt` was one 913-line file with three hard-coded `when (mode)`
branches; background and mode were the same choice. It is now a host plus two
registries, so a presentation is data rather than an edit:

- `ui/lyrics/LyricsSceneHost.kt` owns the resolved palette, the bounded frame, the
  transport and the two lookups, and mounts a `LyricsVisualizer` and a
  `LyricsBackground` independently;
- `LyricsVisualizer` and `LyricsBackground` are dispatched by exhaustive `when`s with
  no `else`, so a new mode or background cannot compile until it is implemented;
- a visualizer receives `LyricsSceneInputs` (item, frame, format, position, playing,
  accent, palette, compact, glow policy, motion, seek) and owns both its lines and
  its line-change transition. A **background** receives only
  `LyricsBackgroundInputs` (item, palette, line index, motion) -- never the lyric
  text -- which is what keeps the two dimensions orthogonal by type;
- six modes ship: Flow, Poster, Spotlight (the three accepted ones, moved verbatim
  into their own files), plus Luminous, Diorama and Pendolo;
- six backgrounds ship: blurred cover, sharp poster, dark spotlight (which are
  exactly the three original treatments), plus colour field, lattice and drift;
- the background setting stores `Int?`, where `null` means *follow the mode*. Each
  mode declares a default background, so an install that predates the setting is
  pixel-identical to before;
- the newer modes and the drift background move on line boundaries only -- no frame
  loop (superseded in phase 7, below, for the two reference-grade modes only), and 0%
  motion is asserted motionless by unit test;
- word glow keeps its persisted Off / Smart / Exact-only policy and its continuous
  left-to-right wipe, shared by every mode through one renderer;
- the wall tools keep the translucent glass treatment. Live backdrop capture/blur is
  intentionally not on the gesture path.

**Correction, 2026-09-27: the six modes above are not the reference's modes.** The
owner rejected this slice on looks and asked for the reference's source to be read
properly. It was (see `docs/HANDOFF.md`, "Claude investigation"), and the gap is much
larger than this plan assumed:

- the reference's thirteen modes are each a **different visual language**, not a
  different arrangement of the same one. Ours reproduce its `still` (静止) mode -- its
  lowest, parameterless one -- six times over, with the line rendered as a caption.
  There, the current line is display typography composed into a scene (2.25--4.5rem in
  `classic`, up to 132 px in `cadenza`) with per-word and per-grapheme animation;
- its modes have per-mode parameters on a dedicated `歌词样式` animation page, which is
  most of what makes them feel authored. Ours have none;
- the three names reused here are attached to unrelated visuals: our `Luminous` is a
  halo, but the reference's Luminous *is* `classic`; our `Pendolo` is a text swing,
  but the reference's is a clockwork dial with an escapement and an arc lyric wheel;
  our `Diorama` is a tilted card, but the reference's is a 3D particle scene with a
  camera path. Rename or implement -- do not leave them as they are;
- its backgrounds are **audio-reactive** (`audioPower`/`audioBands`); ours are
  line-boundary-driven. The orthogonality rule this repository enforces -- a background
  never receives lyric text -- does match the reference exactly.

The owner then chose a direction, which phase 7 carries out. The framework itself was
kept: it was shallow content, not a wrong structure.

Still open, and deliberately not claimed as done:

- the reference has roughly thirteen visualizer modes and six background families.
  Mindscape, Monet and Tempera among others are not implemented; they are recorded in
  `TASKS.md` as follow-ups rather than dropped;
- add optional GPU/audio-reactive backgrounds only after profiling a modern Xiaomi
  device and an emulator;
- per-mode parameters have no export/import yet; they reset with the appearance
  defaults;
- degrade gracefully when RuntimeShader/API/GPU capability is unavailable.

### Phase 7 — 流光 and 时计 taken to reference grade (implemented; phone acceptance open)

The owner's direction was "make two or three modes reference-grade; the names stand
because the pictures will". Two were chosen, and the other four are explicitly out of
scope for this round.

**流光 (was `Luminous`) is the reference's `classic`**: one line at a time, its words
scattered into a cloud. Words are placed by a deterministic pseudo-random projection
seeded from the line's own start time, so a repeat or a seek reproduces the picture
instead of reshuffling it. A word is waiting, then active (accent colour, grown,
glowing along the wipe), then passed (back to the base colour); the whole block
breathes up and down on a 7-second cycle. Timing is projected per display word by
grouping the existing `LyricGlowTimeline` segments, so exact word timing automatically
wins and there is no second estimator to disagree with the first.

**时计 (was `Pendolo`) is the reference's `pendolo`**: nine lines along an arc whose hub
sits on the left edge, spacing widened by measured neighbouring line heights, opacity
falling off with distance, the focal line scaled up. Behind it a Compose `Canvas`
draws the escapement: concentric rails, radial ticks, a geared wheel, a balance wheel
whose phase advances with the frame clock, and a dial. One animated value -- the
continuous line number -- drives **both** the lyric wheel's rotation and the gear's,
so they can never come apart, and because that value only moves on a line change the
gear ratchets only on a line change, which is what the reference does.

Changes the framework needed:

- `WindowedLyricsVisualizer`, a second registry shape for a mode that lays out a
  whole bounded window of lines itself (see `ARCHITECTURE.md`, `DECISIONS.md` D-017);
- `ui/lyrics/LyricsSceneClock.kt`: a frame clock and a per-frame position, both
  started only while the scene is composed, playing and above 0% motion;
- `feature/subtitles/LyricModeTuning.kt` and a settings sheet in the scene header,
  three parameters for 流光 and nine for 时计, persisted per mode;
- `feature/subtitles/LuminousLayout.kt`, `PendoloGeometry.kt`, `LyricRailWindow.kt`
  and `LyricWordTimeline.kt`: the pure geometry and projection, all unit tested.

Deliberate degradations, so nobody goes looking for them:

- the dial's audio-bass term uses the reference's own no-audio default (`0.18`)
  rather than inventing an audio pipeline this app does not have;
- the reference blurs on exit; a `ContentTransform` cannot, so the exit is fade plus
  scale. The entry blur is real, and is done by the mode itself with the frame clock;
- the reference keeps breathing while paused. We stop: paused is a still picture,
  which continues the rule the rest of the app already follows;
- the reference's `useLegacyLayout` parameter is not implemented -- it exists to
  support a web layout this app never had. 流光 therefore has three parameters, not
  four;
- waiting words are not individually blurred, only the block on entry;
- the wipe's position smoothing and the cloud's are separate; unifying them would mean
  touching the accepted wipe renderer.

Explicitly not done, and recorded in `DECISIONS.md` D-018: the reference's `diorama`
(镜台) is a 3D particle corridor built with react-three-fiber, and our `Diorama` stays
the 2D tilted card it is. A 2D Compose scene cannot be an equivalent, and a half
version would be a worse answer than an honest different picture.

### Phase 8 — deep reference catalogue and an actually immersive shell (active)

The owner reopened the catalogue after reviewing the reference PVs. Phase 7's depth
standard remains, but it is no longer a reason to stop after two modes. The official
registry contains thirteen foregrounds -- 静止, 流光, 心象, 云阶, 浮名,
群唱, 倾诉, 回环, 莫奈, 镜台, 时计, 商籁 and 凝彩 -- plus six
independent background families. They do not share one layout: the set spans DOM-like
typography, a single measured Canvas, chat/avatar UI, an elliptical lyric orbit,
Pixi-style compiled storyboards and a real 3D camera corridor.

The common shell is now implemented before another renderer is judged:

- the scene is edge-to-edge and temporarily hides system bars;
- controls overlay rather than shrink the renderer, use the cover accent instead of
  fixed Material blue/black, retain 48dp targets, and auto-hide while playing;
- after D-022 the top overlay is only a bare settings icon -- no header boundary,
  back control or media filename. An open settings panel suspends auto-hide until it
  is explicitly dismissed. The bottom transport uses the same thin `FoliaSeekBar`
  as the focus wall and full player rather than a Material slider;
- hidden controls consume one reveal tap, then real seek/play/navigation actions work;
- a slider-preview feedback bug was fixed so a released per-mode value is committed
  even when the live preview has already been fed back through the host.

`回环` is the first appended mode in this phase. It keeps a stable artwork/background
anchor while a bounded line rail moves around a tilted ellipse. One spring advances
the whole orbit, depth controls size/opacity/blur, tangent controls type angle, and
the focal line keeps the shared timed colour sweep. This matches the reference's
observable contract (old line down-left, next line up-right, simultaneous hand-off)
without copying its AGPL implementation.

`凝彩` now has its first Android-native scene-program/runtime boundary. A pure,
deterministic compiler selects one of eight initial composition families and emits at
most twelve absolutely timed expressive slices. The renderer keeps artwork,
geometric composition, expressive type and the readable current lyric on separate
planes. It is seek-stable and has a zero-motion path. This establishes the correct
architecture but is not reference parity: the remaining work includes paragraph/
shot continuity, carried transitions, subject-aware artwork, a larger authored shot
vocabulary and measured capability-tiered post effects.

The owner subsequently chose to finish four modes before reopening the catalogue.
The production selector and implementation tree therefore contain only 流光, 时计,
回环 and 凝彩; the earlier Flow/Poster/Spotlight/2D-Diorama prototypes were removed.
The other reference modes below remain research order, not dormant product code.

Implementation order from the source and video audit:

1. finish phone acceptance of the edge-to-edge shell, 流光, 时计 and 回环;
2. deepen the new 凝彩 compiler/runtime with paragraph continuity, transition carry,
   more authored shots and capability-tiered grain/chromatic/warp passes;
3. only after these four pass owner acceptance, reconsider 云阶/倾诉/莫奈, then
   心象/浮名, 群唱, 商籁 and a genuine GPU 镜台 as separate future decisions.

Every step keeps absolute playback time as authority, mounts only the selected
renderer, caches text/path/bitmap/shader inputs, offers a 0%-motion/static path, and
adds a lower-cost capability tier before high-cost blur or post-processing ships.

## Cross-cutting constraints

- **Scale:** palette work is O(one focused thumbnail), never O(library size).
- **Playback:** Media3 remains the authority for current media and time.
- **Focus:** browsing focus stays independent unless the user enables auto-focus.
- **Privacy:** all presentation inputs pass through the future privacy projection.
- **Lifecycle:** stop frame loops when the surface is not visible; no background
  visualizer service. The only frame loop in the app belongs to the foreground lyric
  scene and requires visible + playing + motion above 0% at once.
- **Accessibility:** keep controls explicit, maintain contrast and offer reduced
  motion; colour alone cannot carry playback state.
- **Testing:** pure palette/timeline/selection logic gets unit tests; visual tuning
  also needs phone screenshots and interaction checks.
- **Continuity:** after every material phase, update this file only if the plan or
  acceptance criteria changed, and always update `TASKS.md` and `HANDOFF.md` with
  exact implementation and verification results.

## Open visual-tuning questions

These do not block phase 1 code, but must be answered on a real phone before its
visual values are treated as final:

- whether dark-mode strengths 32%/28%/20% and light-mode strengths 20%/17%/12%
  are restrained enough behind bright covers;
- whether the 900 ms colour transition feels calm for long-form ASMR rather than
  sluggish during rapid manual skipping;
- whether a subtle noise layer improves depth enough to justify another texture;
- whether the accepted lights-out value should stay at the current 22% poster cap;
  ambient light deliberately remains independently controlled.
