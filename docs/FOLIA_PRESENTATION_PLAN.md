# Folia-inspired presentation plan

Status: active implementation plan, last updated 2026-09-26.

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
- ship one excellent mode before adding a registry of alternatives;
- keep future scenes replaceable behind an interface so the player and subtitle
  parser do not depend on one animation design;
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
  medium, animate strength changes, and keep the choice in the folder snapshot;
- keep cover density and the live 72%–135% canvas zoom readout/control in that
  same tools surface instead of permanently spending wall height on a top row;
- apply the effect to `FocusWallStyle.Folia` only. The retained Bento/V2 wall must
  remain visually unchanged;
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
5. The low-alpha poster wash does not consume gestures or obscure controls, and
   Bento/V2 rendering is unchanged.
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

### Phase 4 — lyrics on the focused/current cover

- only the currently playing, expanded audio card receives live lyrics;
- previous/current/next lines use bounded blur/alpha/scale transitions;
- add a cover shade whose strength follows contrast, not a permanent destructive
  blur of the bitmap;
- word-progress colouring is optional after line timing works;
- the compact seek bar and playback controls remain operable above the layer.

### Phase 5 — immersive lyric visualizer V1

- the cover action opens one full-screen Compose lyric scene;
- scene inputs are playback clock, normalized lyrics, palette and artwork;
- the first scene prioritizes excellent typography, transitions and reliable seek;
- implement reduced-motion, lifecycle pause and a conservative frame budget;
- the full player remains the navigation owner; the scene does not create another
  player instance.

### Phase 6 — visualizer registry and advanced backgrounds

- introduce a registry only after V1 is stable;
- add a small set of meaningfully different modes rather than many weak presets;
- add optional GPU/audio-reactive backgrounds only after profiling a modern Xiaomi
  device and an emulator;
- expose per-mode parameters with safe defaults and export/import support;
- degrade gracefully when RuntimeShader/API/GPU capability is unavailable.

## Cross-cutting constraints

- **Scale:** palette work is O(one focused thumbnail), never O(library size).
- **Playback:** Media3 remains the authority for current media and time.
- **Focus:** browsing focus stays independent unless the user enables auto-focus.
- **Privacy:** all presentation inputs pass through the future privacy projection.
- **Lifecycle:** stop frame loops when the surface is not visible; no background
  visualizer service.
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
