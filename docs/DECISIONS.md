# Decision Log

## D-001 — Offline and single-root

The application is entirely offline and indexes exactly one SAF-selected root.
Changing or removing the root never deletes real files.

## D-002 — Folder-first domain model

Folders are first-class works/groupings; audio files are tracks. Folder and
all-audio views coexist.

## D-003 — Direct metadata writes with transaction safety

The app writes supported tags back to media files. Unsafe providers or formats are
refused. Privacy aliases are never tag writes.

## D-004 — Focus-cover wall is a core feature

The wall has deterministic spatial layout, explicit focus state, bounded canvas
zoom and distinct portrait/landscape presentations. Both orientations remain
two-dimensional, screen-filling mosaics with the focused hero at the exact viewport
centre; portrait must never collapse into a single column. Base geometry mixes
square, portrait and landscape cards in tightly packed logical blocks. Focusing
reflows only the selected card's block, and its translucent border uses an
artwork-derived accent without changing the canonical artwork or metadata.

A single tap on a card that does not hold focus moves focus to it; a single tap on
the card that already holds focus activates it. No double-tap gesture is bound, so
a quick double tap reads as "focus, then activate" and a single tap is delivered
without waiting out the double-tap timeout. Dragging never changes focus: release
is inertial and free, with no snapping to the nearest card, because re-focusing on
release reflowed the block and re-centred the canvas every time the user stopped
moving, which made the wall impossible to scroll. Pan is clamped so the viewport
centre stays inside the wall's extent; that bound exists only to keep a hard fling
from throwing the wall out of reach, and panning is otherwise free.

That bound was originally Claude's own addition rather than a requested behaviour,
and the owner has since explicitly accepted it as it is (2026-09-25). It is
therefore settled, not a temporary rail: inside the bound panning is completely
free, including the inertial coast, and the coast is cancelled the moment the edge
is reached. Bounding the viewport *centre* rather than the wall's edges is what
lets content smaller than the viewport still be centred, and it guarantees the
screen centre is always over wall content, so there is always something under the
finger to drag back from.

The approved centred-bento wall is retained as its own layout. A separate
Folia-style wall follows it in the single-button layout menu. The latter
uses twelve-item 12 x 8 exact-cover blocks and chooses a focus reflow from the
selected item's original slot instead of applying one universal surrounding
frame. The implementation may learn interaction concepts from Folia Major but
must keep independently authored geometry and code.

A focused Folia card grows where it already is. The hero keeps the configured
focus shape, so what the item's original slot decides is where inside the block the
hero sits: the hero is centred on the card's own centre and clamped to the block, and
the other eleven slots are regenerated as an exact cover around it. Only a card that
already sat in a corner therefore focuses in a corner. The earlier design pinned the
hero to one of two written corner templates and mirrored it, so every focus landed on
a block corner regardless of where the card had been. The greedy nearest-neighbour
assignment of neighbouring cards to slots is unchanged.

The Folia focus shape and size are per-folder settings, chosen from the wall's tools
panel beside the density control, and both default to what the wall did before they
existed: a square 6 x 6 hero. Six shapes are offered: square, portrait, landscape,
full cover, and the heart and circle silhouettes. Five of them are fixed footprints; full
cover is the one whose footprint follows the artwork. The Bento wall is unaffected, since
its focused block is a hand-written template rather than a generated cover. Sizes are
deliberately capped below what the block could hold: an 8 x 8 hero leaves eleven
neighbours thirty two cells between them, and the block stops reading as a mosaic with
a focus. Whatever the preset, the eleven neighbours must still tile the rest of the
block exactly, which is what the enumerating cover test enforces.

Two shapes are silhouettes, and they are a different kind of thing from the four
rectangles, so they are allowed to behave differently. The focused card is itself
drawn as the cells that spell the shape, with the neighbours tiling the remainder
exactly -- a pixel outline, not an outline drawn over a picture. A silhouette is
therefore written for a single place in the block and has a single size: it does not
follow the tapped card's slot the way a rectangular hero does, and the size control
is shown disabled rather than being given a second, differently drawn silhouette for
each preset. They live at the end of the shape enum so the ordinals a folder has
already persisted keep their meaning.

Full cover is the whole artwork, uncropped, in a card cut to the artwork's own
proportions rather than to a preset. A card is a number of the block's columns and rows,
so its proportions are always one of the rectangles the block can hold, and the cover's
proportions are not known until the artwork is read: the file listing carries a name and a
MIME type and no dimensions. So the focused cover is read once per focus, at the wall, and
the nearest of those rectangles is the hero. The size control still applies and still
means the same thing; it narrows the search to areas around the one its square preset
would have taken, and when nothing there comes within a tenth of the cover's proportions
the size gives way rather than the shape, because a card of the wrong shape letterboxes
the very thing this shape exists to show. A cover the block has no large rectangle of the
right shape for therefore keeps a smaller card at every size. A square cover gets exactly
the square presets, and so does a cover whose artwork has not been read yet.

The grid is the reason the rule has to be a nearest rather than an exact, and it is also
why the search is over a measured table instead of over every rectangle that fits: past
certain widths a hero's complement cannot produce eleven neighbours of three cells or
more, so 9 x 6 and everything wider than 7 x 7 are not coverable at all and an eight-row
hero is not coverable at any width. Whenever fitting leaves an edge, it is drawn in the
card's own palette gradient rather than left black.

The focused card also carries an icon-only action that clears focus, at its bottom-end
corner. Focus is independent of playback, so without it the only way out of a focused
block would be to tap a different cover, which also moves the canvas.

The wall also exposes a floating tools button at its bottom-end corner that opens
a small panel of wall actions, the first being "focus the playing item". Focus and
playback stay independent states, so the wall needs an explicit way back to the
track that is playing. The panel is anchored to its button rather than being a
modal sheet, because the wall is already a full-screen gesture surface and a sheet
would hide the very thing the action moves. The folder a queue belongs to is
recorded when playback starts, since that is the only moment that knows both the
listing and the track; following a track back needs no scan and no
document-URI-to-path map. An action that cannot be honoured is offered disabled,
never as a tap that silently does nothing.

## D-005 — Large-library performance is a product requirement

Design for 10,000 folders, 40,000 audio files and 4 GB individual files. Partial
scan results and virtualized UI are mandatory.

## D-006 — No destructive media operations

All remove actions affect only the app index. The app does not delete user media.

## D-007 — Standard high-quality playback for version one

Use the stable Android/Media3 audio path without app-added processing. Bit-perfect
and exclusive DAC output are not promised.

## D-008 — Manual library refresh

The user triggers current-folder refresh or a full rescan. No continuous recursive
watcher runs in the background.

## D-009 — Simplified Chinese first

Ship Simplified Chinese initially while keeping every string resource-ready.

## D-010 — The Folia ambient palette follows focus and remains presentation-only

The Folia wall's ambient palette follows the card with browsing focus, not the
currently playing Media3 item. Focus is the wall's explicit visual subject; moving
it deliberately changes the full-wall light even while another item keeps playing.
Clearing focus fades back to the neutral wall. Focus and playback remain independent
states as required by D-004.

Palette extraction is bounded to a small decode of the one focused cover, samples
a capped number of pixels, returns a few deterministic representative colours and
uses a bounded cache. It never scans the library or retains a full bitmap. The
base ambient treatment is a small number of gradients that animate only while
colours change, not a blurred cover or a continuously running shader. A stronger
copy sits behind the wall and a restrained copy washes over its opaque posters;
otherwise the 2 dp packed mosaic would make the feature effectively invisible.
The effect belongs to the separately selectable Folia wall; the retained Bento
wall stays unchanged.

Density and the live zoom percentage belong in the anchored bottom-right wall
tools, not in a permanent strip above the mosaic. Zoom remains continuous from
72% to 135%, with a 100% reset. Folia light strength is explicitly adjustable as
off/low/medium/high, defaults to medium and is restored with the folder snapshot.

Future privacy presentation must replace the palette input with the configured
alias artwork before this layer renders. The palette and every later visualizer
effect are presentation state only and may not write tags or canonical library
records.

Folia Major is an AGPL behavioural reference. Its code is not copied or translated;
this app keeps an independently authored Kotlin/Compose implementation. The full
research map and staged sequence live in `FOLIA_PRESENTATION_PLAN.md`.

## D-011 — Folia wall presentation controls are durable and presentation-only

The Folia tools expose one `墙面显示` subpage rather than adding four permanent
rows to the wall or top bar. Lights-out dims only ordinary posters; the focused
card and current Media3 item remain legible. Poster tint likewise exempts those
two identities and derives its colour from the focus palette. Ambient strength is
independent, so lights-out does not silently rewrite or disable ambient light.

Track-change auto-focus is opt-in, off by default and dormant outside the Folia
layout. Only an actual Media3 item-ID change triggers it; entering Folia or enabling
the setting does not. It reuses the explicit reveal path instead of introducing a second folder
navigation mechanism. Reduced motion disables decorative transition durations and
fling, while direct dragging, tapping and playback controls remain available.

These choices persist globally in a dedicated DataStore and never alter tags,
canonical artwork, playback identity or indexed library rows. Queue access remains
deferred until the queue has a durable destination.

## D-012 — A continuous setting shows its position on the control, not its number

A wall setting that is a continuous range ends its tools row in a filled crest
rather than a percentage. The fill is the value's position in that setting's own
range, not the percentage itself, because the ranges differ: density runs 70..130,
ambient light 0..150 and canvas zoom 72..135, so "100%" is half a crest in one and
two-thirds in another. Position is the reading that matches the slider on the
setting's own page, which is what makes a shape worth showing at all. The number
stays where the value is being set — the canvas zoom subpage header is the case the
owner named — and every such setting has a row that restores its default in one tap.

The global appearance accent reaches the top bar's title, navigation icon and
actions, and the theme's surface still owns the bar's container. The immersive
player bar is excluded and stays a fixed near-black with neutral content: its
container deliberately ignores light and dark, so the light theme's accent variant
would be unreadable on it. Applying the accent there needs a per-theme accent
channel first; until then a clearly scoped accent beats a half-applied one.

Settings are grouped and expanding rather than one flat page. Appearance is one
group among several, and a settings screen whose whole first screen is colour reads
as if colour were all there is to set. Groups open in place, one at a time, and
nothing is open on arrival.

## D-013 — Parsing reports source truth; visual estimates stay presentation-only

A matched subtitle is turned into one normalized model -- lines with optional word
times plus a separate list of untimed lines -- and every surface reads that model.
Nothing downstream decides what a file meant.

- **An LRC with no time tags is untimed, not evenly spaced.** Its lines are kept
  apart in their own list and the surface says it cannot follow the file. Spacing
  them across the track's duration would make a lyric sheet look synced while being
  wrong from the first line to the last, which is worse than saying so.
- **A line-timed file remains line-timed in the normalized model.** The optional
  Smart word-glow presentation may derive a temporary grapheme timeline from a
  line's real start/end (or the next LRC line's start), but it labels that timeline
  estimated and never writes it into `TimedTextLine.words`, Room or the source file.
  `Exact only` remains available for people who prefer no inferred progress.
- **A bare time tag is a pause, not a line.** `[00:28.34]` with no text after it
  produces no lyric line, so the previous line stays current through the silence
  rather than the reader seeing an empty row they cannot read or seek to. Real files
  use this to mark a pause and a real library is full of them -- 3,034 of the owner's
  3,774 `.lrc` files contain at least one -- so treating each as a line would fill
  the surface with blanks.
- **A version marker in a filename is peeled off and the search continues.** `mix`,
  `alt`, `new`, `old`, `dat` and `bak` are corrections of a file, not languages, so
  `Track.new.lrc` still reaches `Track.mp3`. A segment that is neither a language tag
  nor a marker ends the search, so `Track.Part.lrc` matches nothing rather than
  claiming `Track`'s lyrics; two language tags in one name are refused rather than
  guessed at.
- **A matched file's name and language are shown from the moment the file is chosen**,
  including when it then fails to read or parses empty. Which file was picked is what
  makes a wrong match diagnosable.
- **Following is relative to the item being viewed, and stops when the reader
  scrolls.** Lines are drawn without seek actions when playback is on some other
  item, rather than highlighting against another track's position. A reader who
  scrolls away is not moved again until they ask for it.
- **The current line is anchored a few lines below the top rather than centered.**
  `PRODUCT_SPEC.md` says centered; this deliberately departs from it because the
  anchor is a visual decision that belongs with the scene it sits in, and the phase-4
  visual layer is where that gets decided. The line being sung stays visible with the
  upcoming lines beside it either way, and the anchor is one constant.

Timed-text parsing stays outside visualizer scenes and is cached by document
identity -- URI, size and modified time -- so an edited file is re-read rather than
served stale.

## D-014 — Cover lyrics are a bounded scene owned by focus and playback together

Lyrics appear on a wall card only while that card is both focused and the current
Media3 item. Browsing focus alone must not start a lyric clock, and playback alone
must not paint words onto an unrelated focused cover. Moving focus, changing track,
leaving the wall or opening another surface clears the old request immediately.
The wall reuses D-013's parsed-document stream and playback position; it never
parses files or runs a second timer.

The cover scene holds at most previous/current/next rather than composing the full
document. The Bento wall keeps the compact centred composition. The Folia wall is
instead a measured poster composition: header near 10% height, the current line's
own centre exactly at 50%, context-line centres at 35.5% and 64.5%, the transport
band near 88% and a thin progress line near 95%. These normalized positions follow
the poster's own dimensions, so square, portrait and landscape focus shapes share
one visual hierarchy rather than a collection of phone-dp exceptions. The full
reader keeps its separate three-line lead-in because it is a scrolling document,
not a poster. Untimed text is not animated as if it followed playback.

An ordinary Folia poster uses the same near-square edge, shade and lower-left type
hierarchy as the hero, but never inherits its lyric or transport chrome. It keeps
only artwork, a small truthful folder/audio discriminator, title and metadata.

Readability comes from a local black field whose alpha is derived from the bounded
focused-cover palette. Bright artwork receives more shade, dark artwork less. The
bitmap itself is not permanently blurred; only context text receives a restrained
blur/alpha treatment. Line transitions use the global motion percentage. Folia's
small bottom icons and progress line retain real play/pause, previous/next, seek,
lyrics-detail and clear-focus actions; visual parity must never be obtained with
dead replica buttons.

## D-015 — The first immersive lyric visualizer is a bounded Media3 view

The first full-screen lyric scene replaces the app bar with its own translucent
back/title overlay and reuses the full player's navigation ownership. It receives
the current `PlaybackState`, normalized timed document, cover artwork and bounded
cover palette; it never creates a player, reparses a file or runs a permanent frame
loop. The palette request is for one current cover and is cancelled with the scene.

The scene composes at most two previous and two next lines. A line-index change
cross-fades and moves in the seek direction using the global motion percentage;
zero percent changes immediately. Word glow can be off, exact-only, or Smart. Smart
prefers source word timestamps, then derives a presentation-only grapheme timeline
from a line-timed LRC/SRT/VTT; untimed text never enters the scene. Tapping any
visible line and the bottom slider both seek the shared Media3 session, while
previous/play/next controls remain real session actions.

Only a timed document for the item Media3 is actually playing enters this scene.
Before the first line, the first line is a dim preview rather than an early current
line. Loading, missing, unreadable, empty, untimed and timed-but-non-current states
keep the existing diagnostic/scrolling reader. Portrait shows five lyric tiers;
short landscape space drops the two far tiers and reduces type so transport and
lyrics do not overlap. The background may use one static blurred cover and cached
palette gradients, but no audio-reactive shader or continuously animated field is
part of V1.

## D-016 — The full-screen lyric scene is a host over two append-only registries

The lyric surface is not one decorated page. It is a host that resolves two
independent registries -- a **visualizer** for the lines and a **background** for the
artwork -- from the same playback clock, parsed document and cover palette, and then
mounts each. Adding a presentation means adding a registry entry, not editing the
scene: both dispatchers are exhaustive `when`s with no `else`, so a new enum entry
does not compile until it has an implementation.

This supersedes the earlier "three modes rather than a large preset catalogue"
position. The owner chose the reference's scale, and the reference (Folia, AGPL-3.0)
is a behavioural and visual reference only -- its source is not translated or
copied, and the modes are implemented independently in Compose.

Six modes ship: `Flow` (centred five-tier flow), `Poster` (left-aligned hierarchy
over a readable cover), `Spotlight` (one dominant line, a rule, a cue), `Luminous`
(the current line inside a wide halo built from the cover's leading colour, changing
by breathing rather than by moving), `Diorama` (the artwork as a lit, tilted card
with the lyrics set beneath it, the card lifting as each line arrives) and `Pendolo`
(the line hung from a pivot, settling on a damped spring after each change, the next
line following a beat later). Six backgrounds ship: blurred cover, sharp poster,
dark spotlight, colour field, lattice and drift.

Ordinals are persisted user settings in both registries, so entries may only ever be
**appended**, and an unknown or out-of-range ordinal falls back to the default
(`Flow`, or the mode's own background) rather than failing. **Backgrounds are
orthogonal**: any background combines with any mode, and a background is handed a
deliberately smaller input -- artwork, palette, line index and motion, never the
lyric text -- so it cannot take over typography. Because
the three original modes' treatments are now the first three backgrounds, and because
a background setting of `null` means *follow the mode*, an install that never opens
the background picker looks exactly as it did before that setting existed.

The reference has more modes than these (Mindscape, Monet, Tempera and others); they
are recorded as follow-ups rather than silently dropped. Nothing in this change adds
a frame loop: the newer modes move on line boundaries only, and a motion setting of
0% is asserted motionless by unit test rather than judged by eye.

Word glow is an independent persisted three-way setting: Off, Smart (default), and
Exact only. Smart prefers real word timestamps and otherwise derives display
grapheme intervals from the line's real timing. The derived intervals are explicitly
presentation state and never enrich the parsed document. Exact only preserves the
old strict behaviour, while Off returns the whole line to normal.

Those intervals reach the screen as one continuous left-to-right colour wipe rather
than a per-character highlight: the front is interpolated in x between the grapheme
being sung and the next one, so it crosses a display character and can rest inside
it. The wipe is a pure rendering projection of the same intervals -- it introduces
no new timing state and no new stored setting, and it is what the approved Folia
poster reference shows. The old per-grapheme projection is still readable from the
same lookup -- it returns the discrete pair alongside the fraction, so a caller that
ignores the fraction gets exactly the previous behaviour.

The wall's bottom-right entry and its panels use a frosted-glass visual treatment:
translucent surfaces, a light edge, internal highlight and restrained shadow. The
panel grows and fades from the button's bottom-right origin, and global motion 0%
makes that transition immediate. This is not a live backdrop-filter implementation;
capturing and blurring a moving wall would sit on the gesture hot path and is deferred
until a real phone proves the cost acceptable.

## D-017 — A bounded frame loop, a second registry shape, and per-mode parameters

The owner rejected the first visualizer slice on looks rather than on structure: the
modes were shallow, not wrong. Checking the reference confirmed it. `Flow`, `Poster`
and `Spotlight` were reproductions of the reference's *lowest* family (a still,
parameterless mode with no background layer); `Luminous` and `Pendolo` carried the
reference's names but drew nothing like it. The fix was depth in two modes rather
than more modes, so `流光` (formerly `Luminous`) and `时计` (formerly `Pendolo`) are
taken to reference grade, their mode names kept, and the other four are left exactly
as they were.

This changes three settled rules, each deliberately:

**A frame loop is now allowed in the foreground lyric scene, and nowhere else.** D-015
and D-016 both promised no visualizer coroutine and no permanent frame loop. That
promise survives for the wall, ordinary cards and the cover lyric layer -- none of
them may run one -- and for the four modes that do not need it. It is lifted only for
the full-screen scene, and only while three things hold at once: the scene is composed,
playback is running, and the global motion setting is above zero. Leaving the scene,
pausing, or 0% motion ends the coroutine (`ui/lyrics/LyricsSceneClock.kt`); the loop
lives inside the mode's composable, so Compose's own lifecycle is the visibility test
and there is no second visibility flag to fall out of step. This was approved by the
owner explicitly, and the alternative -- interpolating a cloud that breathes on a
7-second cycle from line boundaries alone -- does not exist.

**The registry has two shapes.** A windowed mode is handed a `LyricRailWindow` (the
active line plus seven either side, indices allowed to fall off both ends) and lays
the whole slice out for itself as one moving object, because a wheel whose neighbours
curve away cannot be drawn one line at a time. The stage gives such a mode no
per-line `AnimatedContent` and no compact height, and the registry `when` still has no
`else`, so a new mode must choose a shape at compile time.

**A mode may own parameters.** `流光` has three, `时计` has nine, each with a declared
integer range and a pure clamp, and they are persisted per mode. They are deliberately
*not* part of the global motion setting: motion is the master switch that decides
whether anything moves and scales how far, while a parameter is an absolute position
that decides the shape -- folding the two together would make them fight. The scene
header offers the sheet only where `hasTunings` is true; a drag is drawn live but
written only on release, and a commit writes that mode's whole parameter set in one
storage edit, so storage never sees half a change. The preview lives beside the stored
value rather than inside it, and is dropped when the sheet closes, so a gesture that is
never released cannot leave an unpersisted value on screen. Storage keeps these as the
feature's own value types rather than raw ordinals, unlike the registry choices, and
the reasoning is recorded in `AppPreferences.kt`: the one place where raw storage
protects something (an enum whose contents the feature owns) does not apply to a flat
record of primitives that *is* the stored shape.

Declared degradations are in `docs/HANDOFF.md`; the reference's audio-reactive terms
are answered with the reference's own no-audio default rather than by inventing an
audio pipeline.

## D-018 — The reference's 镜台 is a 3D particle corridor; Compose does not attempt it

The reference's `diorama` is a react-three-fiber scene -- thousands of lines across
23 files, a GLSL particle shader and a camera path -- in which the lyric text is
rasterised into 3D world space along a winding corridor. Our `Diorama` shares the name
and nothing else: it is a lit, tilted cover card with the lines set beneath it.

That is a deliberate stopping point, not an unfinished one. A 2D Compose scene cannot
be an equivalent of a 3D world-space text renderer, and a half-version would be a
worse lie than an honest different picture. `Diorama` therefore keeps its current
implementation and its name, and this entry exists so that nobody later compares the
two by name and reads the difference as a bug. If the 3D corridor is ever wanted, it
is a new rendering decision with its own cost, not a fix to this mode.

## D-019 — Full-screen visualizers are an edge-to-edge stage, and reference modes return one by one

The owner explicitly reopened the breadth decision after reviewing Folia's three
published demonstrations and the official source. This supersedes D-017's final
"two modes, not thirteen" stopping point and D-018's *permanent* stop, without
pretending the technical warning was wrong: 镜台 will still require a real GPU/3D
renderer when its turn comes, not a deeper Compose card.

The official project is used as an AGPL-3.0 behavioural reference only. Its renderer
or shader source and assets are not copied or translated. Each Android mode is a
clean-room implementation against an observed contract and is appended to the
persisted enum; existing ordinals are never renumbered. Legacy app-specific modes
remain available until a separate migration decision removes them.

The common scene shell is now a true stage:

- the timed immersive route clears `Scaffold` content insets and draws under the
  physical status/navigation areas;
- system bars hide only while that route is composed, remain transiently reachable
  by an edge swipe, and are restored on disposal;
- renderer bounds never change when controls appear;
- the title/actions and transport are small palette-tinted glass overlays with 48dp
  hit targets, scale/fade/slide in, and auto-hide after 3.2 seconds while playing;
- a first tap on the hidden stage reveals controls rather than seeking accidentally;
  paused playback keeps them visible.

`Claddagh` (回环) is the first newly appended reference mode. It is a
`WindowedLyricsVisualizer`: a bounded rail advances as one spring-driven tilted
ellipse, keeps history/current/future present during hand-off, projects scale,
opacity, blur and tangent angle from depth, and sends the current line through the
shared grapheme wipe. It has no second player, parser, document, or permanent clock.

`Tempera` (凝彩) is the second appended mode, at ordinal 7. Its first implementation
establishes the required pure compiler/runtime split: a deterministic, bounded shot
program (initially eight families, no more than twelve expressive slices) is projected
from absolute playback time, and the renderer separates artwork, geometry, decorative
type and the always-readable current lyric. This is an architectural milestone, not
a parity claim; GPU post effects, paragraph carry and broader shot vocabulary remain
explicitly incomplete.

The remaining order follows implementation truth rather than menu order:
云阶/倾诉/莫奈 first (Compose typography); 心象/浮名 next (single Canvas with
cached measurement); 群唱 after its speaker/avatar model; 凝彩 then 商籁 as
compiled, absolute-time GPU scene programs; and 镜台 last as a genuine 3D camera
scene. 凝彩 is not accepted as "large text plus transitions": it needs deterministic
shot compilation, layered artwork/geometry/type, a stable readable subtitle and
capability-gated post-processing.

## D-020: Narrow the production visualizer registry and unify its settings

**Decision:** While the four selected modes are taken to reference quality, the app
ships only 流光 (`Luminous`), 时计 (`Pendolo`), 回环 (`Claddagh`) and 凝彩
(`Tempera`). Flow, Poster, Spotlight and the knowingly non-equivalent 2D Diorama are
removed rather than left as misleading presets. Early builds persisted enum ordinals,
so the survivors expose legacy stored values 3, 5, 6 and 7; removed and unknown values
degrade to 流光. New enum ordinals are never written.

The scene exposes one settings entry whose overlay has stable 通用 / 背景 / 歌词动画 /
字幕 tabs. Background remains independent from foreground animation. 凝彩's first
mode-specific settings are cover versus a bounded persistable-SAF image library and a
full-screen, 4:4 or 4:3 fitted stage. Image URIs are presentation preferences; images
are neither copied nor written back. This supersedes D-019's plan to immediately add
the rest of the thirteen-mode catalogue, but retains its clean-room and depth rules.

## D-021 — One global record and three shells over one full player

The conventional mini player is replaced, not supplemented, by one circular global
record at the lower-left of ordinary app destinations. It displays current artwork,
rotates slowly while Media3 is playing and opens the full player. It is absent when
there is no current media and while either the full player or immersive lyric scene
is already open. The abandoned roll-up playback-bar proposal is not part of the
product.

The full player has three persisted visual shells: Folia, Poweramp and Fusion, with
Fusion as the default. They are not three player implementations. Media3 connection,
metadata read, timeline, seek state, queue, bookmarks, chapters and actions are
created once and passed into the selected arrangement. The same rule applies to
future features: add one shared capability and expose it through each shell rather
than copying behaviour.

The record-to-player motion is presentation only. The record rotates by updating a
render layer rather than recomposing every frame; the route keeps the outgoing item
long enough to finish its reverse animation; 0% global motion removes the scale
travel. This preserves a lower-cost path and makes future navigation replacement
possible without putting route state inside the record composable.

## D-022 — The full player has one layout; lyrics are entered by an artwork swipe

This supersedes D-021's three-shell full-player experiment immediately after device
review. Folia/Poweramp/Fusion are removed as product choices, including their stored
preference and settings UI. The player has one adaptive layout: artwork above, no
top chrome, and one rounded lower surface containing metadata, queue position, thin
seek line, previous/next, backward/forward jumps and secondary player panels.

Artwork tap is deliberately unbound. A left horizontal swipe on the artwork opens
the immersive lyric scene, which prevents an ordinary cover tap from unexpectedly
changing routes and leaves room for future artwork gestures. Android Back remains the
way out of both the player and lyric scene.

The lyric scene's top overlay contains only the settings icon, drawn directly over
the scene without a separate disc, boundary or filename. Settings-open state belongs
to the scene host rather than the transient header; while it is open the 3.2-second
chrome timer is suspended, so recomposition or inactivity cannot close the panel.
The focus wall, full player and lyric transport share `FoliaSeekBar`.

## D-023 — Records and the 时计 wheel use direct manipulation

The global record is a movable control, not a permanently lower-left decoration.
Dragging is constrained to the current safe content viewport; release snaps to the
nearest horizontal edge. Only the normalized settled edge and height are persisted,
so rotation and different screen sizes do not restore an obsolete pixel coordinate.
Drag frames never write DataStore.

“轮盘” also names the complete mechanical lyric wheel in 时计. Its clockwork and lyric
rail move together under a two-axis drag because both continue to derive from one
`PendoloTuning` centre. The renderer previews locally and commits only on release;
the horizontal and vertical settings remain equivalent controls. Tapping a neighbour
lyric and opening scene chrome remain separate gestures.

The full-player artwork follows the focused Folia poster's visual vocabulary without
duplicating its playback/lyric chrome: bounded near-square size, 2 dp edge, translucent
cover accent border and the same dark vertical/horizontal shade. Metadata and player
actions remain solely in the lower control surface.

## D-024 — The card workshop is three pages, and the viewer is the workbench

闪卡工作台 is one screen with an internal three-page route (warehouse -> form ->
viewer) and one `BackHandler`, rather than three destinations on the app's own
navigation or a sheet over the player. The app's navigation is a hand-written
`rememberSaveable` flag plus a render branch; a workshop that is entered once and
left once does not justify becoming a fourth route, and keeping it internal means
its three pages cannot be reached in a state their entry point did not set up.
The form is a **full page, not a bottom sheet** -- the deviation from the original
plan -- because seven text fields, a cover and four materials do not fit a sheet at
phone height without scrolling inside scrolling.

The warehouse is a shelf and the viewer is the workbench. The four materials and the
four depth sliders are adjusted **where the card is**, not on a grid of thumbnails,
so there is one set of those controls instead of a second copy to keep in step. The
consequence, accepted deliberately: a card's material and depth are **not written
back** when they are changed in the viewer, so reopening a card shows what it was
built with rather than the last thing done to it. That is the reference's own
behaviour -- its sliders rewrite uniforms live and never touch the config -- and
reproducing it was chosen over improving it.

Deletion removes a directory this app created and nothing else, so the repository
rule that deletion means removing an index entry rather than user media holds here
without any code written for it. A card already saved to Pictures is not deleted
with it: by then it is the owner's picture.

## D-025 — The reference render engine is vendored; Android supplies an embedded shell

The card is rendered by the reference's Three.js/GLB/shader engine in
`app/src/main/assets/card/` and run in a `WebView` over intercepted requests, rather
than reimplemented in Compose. The requirement was the closest possible reproduction
of its holographic material, four finishes and four depth planes; a Compose rewrite
would have been a different product that merely resembled the target.

"Vendored" does not mean the desktop website is the Android UI. The original one-page
atelier made the app look as though it had navigated to a white web page and squeezed
its controls below a small card. Android therefore opts into a presentation layer with
`?embedded=1`: full-bleed dark stage, transparent WebGL canvas, glass actions, a
collapsed workbench, and a native safe-area back control. Opening the same assets
without that query still produces the desktop reference shell. This adaptation may
change CSS, shell markup and input semantics, but must not fork the card schema, GLB,
material shaders, depth uniforms or PNG export path.

The full horizontal drag is also a deliberate Android interaction change. A physical
card follows the finger through edge-on to its reverse over `-PI..PI`, then settles to
the nearest face after release. The former small `+/-0.65` tilt was retained nowhere:
it could demonstrate foil but could not satisfy the owner's request to turn a card over
slowly. Reduced-motion still removes decorative animation; it does not remove direct
manipulation.

Hosting it costs one dependency the plan listed and did not need
(`androidx.webkit` is not used; the interceptor is written directly), and **no
`INTERNET` permission**: every request is answered from an allow-list inside the
app's own assets and one card's own directory, so the page has no origin it could
reach even if it were hostile. JavaScript is enabled because the viewer *is* a
script; the review lint asks for is written into the call site.

`effects.png` and `lineart.png` are **omitted by design**, and the viewer is built to
accept their absence by swapping in 1x1 textures. The reference's line art is a
contour a model drew; thresholding or edge-detecting a photograph produces speckle,
and shipping nothing is better than shipping a worse approximation of a specific
look. Writing the config key absent is the correct expression of absent.

The owner's model/API path -- a custom API, DeepSeek, a local Codex or a local Claude
Code -- is a real decision that is **not built in this version**: the switch exists
and defaults to off, and the first version of 闪卡 derives every card from a cover the
library already has. No AI image generation is involved anywhere in this slice.

## D-026 — The collection room is shelved by the card's own series, and has a picture of every card

The 收藏室 (the warehouse, reachable from Settings as well as from the player) groups,
searches, sorts and batch-edits, and its shelves are the cards' **own `collection`
line** rather than a folder the owner maintains. The line is already printed on every
card, so the shelf a card lands on is something the card itself says, and no second
piece of state has to be kept true against the first. Pinned cards get a shelf of
their own at the top; cards with no series get 未归类 at the *end*, because the one
group that is not a series should not sit in the middle of the ones that are.

**Pinning is the only property of a card the owner changes after making it.** It is
recorded in the card's `meta.json` and nowhere else. This is the deliberate limit of
the room: materials, depth and finish stay in the viewer where the change can be seen
against the card (D-024), so a grid of thumbnails does not grow a second set of those
controls to keep in step.

Batch pin is one control that means one thing -- pin all of them or unpin all of them,
decided by whether *every* selected card is pinned -- rather than a toggle whose label
would have to describe a mixed result.

A batch delete goes through the same single-card path as a single delete, id by id, so
a batch can never remove something a single delete would have refused. Every id is
validated as one path segment before it is joined, which is what keeps deletion inside
the cards root and away from user media (D-006).

**Every card now has a picture of itself from the moment it is built.** The build
writes `preview.png`, a flat composite of the same three layers the viewer loads, at
half size (512x768 -- a quarter of the card's pixels, so a second picture does not
double what a card costs on disk). Before this, a card's only image was `card.png`,
which only the viewer's own Save produces, so a card that had been made but never
saved appeared in the warehouse as an empty frame -- which reads as a card that failed
rather than as one that has not been saved. The tile shows `card.png` when it exists
and the preview otherwise. The preview is not a substitute for the capture: no foil,
no tilt, no lighting; it is what the card looks like lying still.

## D-027 — The pinch drives the page's own zoom rather than a transform of its own

The card page already had a magnification: a `wheel` listener writes a `zoom` value
that `resize()` divides the camera frustum by, then re-renders. Nothing on a phone
could reach it -- a WebView never delivers a wheel event. The Android shell therefore
translates a two-finger pinch into the wheel events the page already handles, in the
page's own unit (`deltaY * 1e-3`), rather than applying a CSS transform.

The transform was rejected on two counts. It would magnify a raster, so a card zoomed
in would go soft exactly where the owner wants to look, while the frustum path
re-renders at the display's full resolution. And it would be a second zoom
implementation to keep in step with the first, with its own state, its own reset and
its own idea of the limits.

The clamp in the vendored bundle was **widened to `0.72 .. 2.4`** because the
original 0.82-to-1.05 range is a framing nudge, not a magnification: a pinch that
stops moving after a few percent reads as a broken gesture. The range is duplicated in
`CardGesture` and asserted against the bundle by `CardGestureTest`, since the Kotlin
constant cannot read the JavaScript one.

Two fingers conflict with the page's one-finger turn, because it tracks "a finger is
down" in a single flag that all pointers share; left alone, the second finger would
keep turning the card while it was being magnified. The gesture therefore ends the
drag with a synthetic `pointerup` before it starts magnifying -- which settles the card
to the nearest face, the same thing lifting a finger does -- and every listener sits on
`document` in the **capture** phase so it can stop the event before the page's own
stage listeners see it. A listener on the stage itself would be queued behind the ones
already registered there.

Double-tap returns the card to 1.0. Nothing else was added: no pan, no bounds, no
inertia. The page has one zoom and this is the phone's way to it.

## D-028 — A card records the song it was struck from

`meta.json` carries the library item's **id**, not its title. A title is free text that
two songs may share, that may be empty, and that a rename changes; the id is the
document URI's own identity and is the thing the library, the queue and the player all
key on. Without it, "does this audio already have a card?" has no answer at all --
nothing in a card's files names the audio it came from.

A card built before the field existed matches nothing, which is the honest answer
rather than a wrong one: nothing recorded where it came from. A song carded twice
resolves to the newer card; the older one is still in the room, and pinning is how the
owner says which is the one that counts.

`meta.json` is the file a future version will add keys to, so its reader is the one
reader in the package written to be forgiving: every field is optional and every
unreadable one falls back to its default. A truncated or hand-edited file costs the
card its pin or its provenance, never its place in the room. It also ignores keys it
has never heard of, which is what lets an older build keep opening cards a newer one
wrote.

## D-029 — A song's card becomes the cover on the full player, and the lyrics keep a row of their own

When a song has a card, the full player's artwork slot *is* the card: a live WebGL
surface with the card's own turn and pinch, at the cover's position and size. The
owner asked for this having already accepted the power cost of an always-live card in
that slot, and there is no second still picture underneath -- a card that stopped
animating when the page settled would be a different feature.

The card is shown in a new `bare` mode (`?embedded=1&bare=1`) that strips the shell's
own chrome as well: the save button, the depth panel and the artwork bar are controls
drawn on top of the cover they are about, and at cover size they would cover it. The
class is set by the inline bootstrap before the stylesheet is parsed, so a cover never
paints its own save button for one frame. Save, the materials and the depths remain
where they belong -- in the workshop, one entry away in the tool cluster.

**This took a route away, and the route was replaced rather than lost.** The artwork
owned the left swipe into the lyric scene. A card in that slot takes the box and its
own one-finger gesture turns it, so carding a song would have quietly cost the owner
their only way to its lyrics from this screen. The tool cluster therefore gained a 歌词
row. It duplicates a gesture on purpose: the gesture belongs to the artwork, and when
the artwork is a card the gesture is the card's.

The studio also became reachable from Settings, with no song behind it. Its `item` is
nullable rather than the studio being hidden until something plays: the collection room
is a place to look at what exists, and requiring a song would make browsing the
collection depend on the player. Opened from the player it seeds from that song; opened
from Settings it seeds from nothing. The seed is captured when the studio is opened
rather than read while it is open, so the last song ever opened in the player cannot
leak into a session that is about the collection.

## D-030 — The card cover is asked for per song, and 封面默认闪卡 is a default rather than a rule

D-029 made a song's card its cover, and that turned out to be a decision the owner did
not want made for him: the card belongs to the song, but *showing* it is something he
wants to choose each time. So the state is three values folded into one -- does this song
have a card, has the owner said anything about it on this visit, and what does the setting
say -- and the fold is a pure function (`showsCardCover`) so the interesting case, that a
choice outranks the setting in both directions, is a unit test rather than a phone pass.

The stored half is `AppPreferences.cardCoverByDefault`, on the Cards page of Settings,
off by default. Off by default matters: an install that never opens the setting behaves
exactly as it did before the setting existed. On, it is still a default and not a rule --
the entry takes the card away again, and the choice outranks it until the song changes.

The choice is keyed to the item and is deliberately not written to disk. Moving to the next
song is a fresh question, which is what makes the card something asked for rather than
something that follows him down the queue; and a choice that outlived the process would be
the setting again, only harder to find and harder to undo. It does ride
`rememberSaveable`, so a rotation is not a fresh question -- the phone turning while a card
is on screen is not the owner changing his mind. This follows D-011's habit for wall
controls -- a change the owner makes while looking at something applies to what he is
looking at -- with the difference that this one is deliberately not durable, because the
owner asked for the *default* to be the artwork.

**A card built from the player does not switch the cover to itself.** The owner said the
card is asked for each time, and that is the literal reading: the studio reports the new
card up (`onCardCreated`, which is what makes the player re-read the song's card at all),
and the cover still is the artwork until he presses 闪卡. Flagged in `HANDOFF.md` as the
one place a reasonable owner might have meant something else, and a one-line change if so.

## D-031 — The card is drawn as itself, not inside the artwork's frame

The artwork slot is a square with a 1.1dp accent hairline on a 2dp radius, and the owner
reported the card sitting inside it as a **black frame**. The cause is not a colour: the
viewer's `resize()` fits the card to the viewport, and in a square viewport a 2:3 card
occupies about 58% of the width, leaving the rest of the box to the page's own near-black
`#0d0e13`. The frame is the *shape* of the slot showing through, not a border anyone drew.

So the slot takes the card's shape when the card is what is in it, and the hairline, the
square and the artwork's shade are not drawn at all -- a card is its own object and not a
picture in the artwork's frame. The artwork branch is untouched: it keeps the square, the
hairline, the gradient and the left swipe, all of which belong to it.

The 2:3 is read from `CardLayerGeometry.CanvasAspect`, derived from the canvas the layers
are built on, rather than written as `1024f / 1536f` in a third place. Three surfaces draw
a card as itself -- the compose form's cover preview, the warehouse tile and this slot --
and a second copy of the number is a second thing to keep in step with the canvas.

## D-032 — The 闪卡 entry does one of three things, and the song's page opens the form

An entry labelled 闪卡制作 that always opened the collection room asked the owner to walk
past a shelf of every card he owns to reach the one he was making, and to know from the
label which of the two things it meant. It is now one row whose *label states what
pressing it does*: 闪卡 when there is nothing to switch to or the switch has not been
made, 原封面 once the card is showing. The three states and their labels are a table in
`PRODUCT_SPEC.md`; the important part is that the label is never the noun alone, because
a row that said only 闪卡 would leave him to look at the artwork and work out where he was.

The studio therefore has two ways in that are not the same errand, and
`CardStudioScreen.startAtCompose` is what tells them apart. From Settings it is a place:
the room is the point and a card is one of the things that happen in it, so it opens on
the room. From a song with no card it is a single act -- make this song's card -- so it
opens on the form, and `composeIsRoot` keeps back from the form leaving the studio rather
than opening a room that was never on the way in. That flag is cleared the moment the room
is used, after which the room *is* behind him and belongs on the way back out.

Back from the viewer still returns to the room, including on the root-compose path. The
alternative -- back from the viewer to the form -- was rejected because the form is a dead
end that invites building the same card twice, and because D-024's existing behaviour is
already that the room is what is behind the viewer. It costs a second back press to leave
the workshop on that path, which is the cheaper of the two surprises.

## D-033 — The player's backdrop is coloured from the cover's extracted palette, not from a blurred copy of the cover

The old full-player backdrop was the cover artwork itself, scaled to 1.18, drawn at 34% alpha and
blurred 46 dp over a near-black page. The intent was "colour from the cover", but that is not what
a blurred copy at a third opacity over `0xFF090A0D` produces: every sample is multiplied by the
page before it reaches the eye, so the result is a dark page with a faint texture on it. The
owner's reference makes the colour the *ground* rather than a wash over the ground, and asked for
that specifically -- *"特别是它背景取色的方式"*.

`CoverAccentLoader.resolve` was already in the repository doing exactly this extraction, bounded
to a 96 px decode, cached in an LRU, and already wired to the wall's ambient light. It returns
`CoverFacts.paletteArgbs`: up to a handful of representative colours, population-weighted,
de-duplicated and brightness-balanced by `balanceCoverColor`. The player now takes up to four of
them as the stops of a diagonal gradient, puts the blurred cover over that at 1.35 / 50% / 56 dp
so it contributes texture and not shape, and scrims it much more lightly than before. The result
is the reference's behaviour: three songs with three different dominant colours give three
different pages.

Two consequences worth stating. **A song with no cover does not go black**: the gradient falls
back to `artworkBrush(item.paletteSeed)`, the wall's deterministic placeholder, so an unnamed song
still gets a deliberate page. And **the palette is the loader's, so the player and the wall agree**
-- the same cover cannot produce one ambient colour on the wall and a different one on the
player, because there is one extraction and one cache behind both.

## D-034 — The sleep timer lives in the playback service, and repeat lives on the player

Both of these are new in the slice that rebuilt the player, and both were put where they are for
the same reason: **the moment they matter is the moment the UI is not there.**

`PlaybackConnection` is deliberately lifecycle-scoped -- it connects on `ON_START` and
`disconnect()`s on `ON_STOP`. A countdown kept in the player page would therefore be cancelled by
the owner locking the screen, which is the single thing he is certain to do between setting a
sleep timer and needing it to fire. So the deadline is the service's: `PlaybackService` holds a
`Job` on its own scope and pauses the player when it comes due. The UI only ever reads it.

Getting it there took the one channel a Media3 session offers for app-specific state, and the two
directions use different carriers because they have different shapes:

- **Arming is a command.** It asks the service to do something the UI cannot, so it goes over a
  `SessionCommand` and is answered with a `SessionResult`. `onConnect` grants it *on top of* the
  session's default command set (`defaults.availableSessionCommands.buildUpon().add(...)`) --
  building a fresh set there would strip the queue, transport and seek commands, and the symptom
  would have been a player page that had lost its controls.
- **Reading it back is state.** The deadline has to be visible to a UI that connected *after* the
  timer was armed, and session extras are the only part of a session a controller reads on connect
  without a round trip. The service therefore publishes the timer in `onCreate`, before anyone has
  connected, so no surface ever starts out believing there is no timer.

Two details that are load-bearing rather than incidental. The extras carry **the duration chosen
as well as the deadline**, because a countdown cannot identify which row of a list is the live
one -- it is always slightly less than the duration picked. And `PlaybackConnection` listens for
`onExtrasChanged`, because the 500 ms position ticker that would otherwise pick the change up only
runs while the player is playing, and arming a timer while paused is precisely the bedside case.

Repeat needed none of that. `Player.repeatMode` already existed, the player already lives in the
session, so `PlaybackState.repeatMode` is read straight back off it. That is not an implementation
shortcut; it is what makes repeat survive a locked screen and a torn-down UI with no second copy
to go stale. `RepeatMode` exists only to keep Media3's `REPEAT_MODE_*` ints on the far side of the
UI boundary.

Neither is written to disk. Repeat dies with the service, the same lifetime the queue already has;
the timer dies with the service too, because a deadline that outlived its process would be a
promise nobody is left to keep.

## D-035 — The collect button's glyph is a parameter, defaulted to the wall's

`GlassToolCluster` hard-coded `Icons.Default.Widgets` and a 45-degree turn as the trigger's open
animation. Both were right for the focus wall, whose four-square glyph closes into a cross as the
panel grows out of the corner it sits in -- the turn reads as a lid. The player's reference puts a
horizontal ellipsis in that corner, and the same turn applied to it produces a *slanted* ellipsis:
a glyph that reads as damaged rather than as open.

Both are now parameters with the wall's values as their defaults, so `FocusCoverWall` renders
byte-identically without being touched, and a surface that has its own idea of what "more" looks
like says so. The player passes `MoreHoriz` and `0f`.

The same slice parameterised the button's *position* in a different way: rather than adding a
"line me up with a row" option to the component, the player's tool row is laid out so that the
button's existing anchor already *is* the fifth slot. The trigger sits in a `Column` aligned
`BottomEnd` with 16 dp of padding around a 42 dp button, so its centre is 16 + 21 dp above the
inset box's foot. A row of four 42 dp boxes with `padding(start = 16.dp, end = 58.dp,
bottom = 16.dp)` under `Arrangement.SpaceEvenly` leaves a trailing gap of exactly `42 + g`, where
`g = (width - 168) / 5` is the gap between two of the icons -- so the button lands where a fifth
slot would have been. The component keeps one layout and one behaviour; only the caller's numbers
changed.

## D-036 — Intrinsic artwork and a three-stage player reading flow (2026-10-11)

Owner supersedes the square crop, green banner, three-button transport and direct
cover-to-visualizer swipe from D-033's slice. Screenshot targets are Salt Player-like,
not a claim that its internal implementation was inspected. Preserve raw dominant
cover colours for the player's broad tonal backdrop while retaining the wall's existing
balanced palette. Covers fit their own ratio; size/font percentages are durable global
choices with commit-on-release sliders. Cover → ordinary lyrics → existing immersive
visualizer uses one playback session/document; first two pages keep common chrome.
Previous/±10s/play/next are explicit. The old visualizers and tools entry remain intact.

## D-037 — Mid-tone colour field, three-line context and genuine per-track artwork

2026-10-11 owner phone comparison rejects the pink/light tonal backdrop and single
cover lyric. Player colour selection excludes pale/neutral highlights and paints a
richer dark-to-soft field with subtle lateral hue variation; this is screenshot-driven,
not a claim to know Salt's closed/internal colour algorithm. Three lyric slots show
previous/current/next with separate emphasis. Audio own embedded artwork has priority;
same-name image and folder artwork are fallbacks. Extraction is lazy, bounded/cached,
not an eager 40,000-file tag scan. Identical real artworks are not randomized or replaced.

## D-038 — Desktop lyrics are an opt-in playback-service surface

Owner requested editable font/colours, sentence count and word/line styles, with
display only over the home screen/other apps. Use the same scanner matching and
exact-first Smart timeline; ordinary LRC's word sweep remains an estimate. Own no
second player/foreground service, never depend on an open lyric page. Default off,
explicit system overlay permission, hide while app visible/screen off/keyguard.
Locked windows pass touches through; unlocking is always available from app settings.
The bounded native overlay View is a deliberate exception to the Compose UI default
to avoid fake Activity/ViewTree lifecycle owners inside MediaSessionService.

## D-039 — Keep desktop lyrics attached inside and outside the app; HSV controls

Owner explicitly reversed D-038's background-only visibility after observing a hitch
when returning Home. The overlay now remains attached over our app too, with no
foreground event remove/add cycle. Disabled/permission-revoked/missing lyrics/ended
queue/screen off/keyguard still hide it. No promise of measured frame parity is made.
Replace HEX entry with reference-inspired hue, saturation, brightness gradient sliders,
round thumbs, live swatches, quick presets and reset. Persist existing ARGB values,
not a new incompatible colour schema. Fully transparent backdrop means opacity zero;
lock hides the drag/control hints and permits touch-through, retaining lyric text/shadow.

## D-040 — Owner-approved recoverable local tag writes (2026-10-18)

After being told standard SAF has no atomic whole-file replacement guarantee, the
owner answered “写入吧”, accepting the proposed journaled, recoverable two-rename
commit. This narrowly replaces the architecture's former strict-atomic-only clause;
it does not claim an atomic swap or relax D-003's refusal of unsafe writes.

Initial writable scope is a persistently read/write-granted **primary internal**
`com.android.externalstorage.documents` tree and MP3/FLAC/M4A/RIFF-WAV files that pass
all capability, size, codec and integrity checks. Unknown/cloud/SD providers, RF64,
oversized/unsupported containers remain read-only. The source is never opened for
writing or truncated. Copy to a UUID-named sibling, mutate only its FD with pinned
TagLib 1.0.6, compare tags/art and independent streamed audio digests/Android codec
probes, fsync and journal before each rename. Rename source to backup, stage to the
original name, read-back hash, then remove the verified backup. An interrupted save
or conflicting third-party edit leaves a journal and recoverable artifacts, not a
blind overwrite. Recovery may itself be restarted.

Edit canonical TITLE/ARTIST/etc and embedded front art, never aliases. Keep physical
filename unchanged to retain lyric matching and SAF path identity. Picker initial
directory is a hint, not a new storage grant. The editor captures its target across
queue changes/rotation. Retain one bounded old-tag/art snapshot, not duplicate audio;
undo UI and batch editing are deferred. Native/SAF device round trips must still be
verified on disposable copied/generated media before relying on this for valuable
originals. Public release also needs native LGPL/source/relinking compliance review.

## D-041 — JOKESONG branding and complete private GitHub delivery (2026-10-18)

Owner names the project/repository **JOKESONG**, not an ASMR-specific name. Change
the launcher/product brand, not the existing `com.example.asmrplayer` application
ID, storage identity or signer: installed owner data must remain update-compatible.
The historical internal package/classes/checkout name need not be mass-renamed.

Owner explicitly extends the earlier APK-only proposal: upload the complete project
source as well, and sync each completed update with documentation and a tested APK.
Use the confirmed private `liuchengzhiaa/JOKESONG` repository. Keep secrets, signing
private keys, local configuration, tool/auth caches and private media out of Git;
publish APK/checksum/build manifest as Release attachments, not Git blobs. Keep
source history and no force push, duplicate tag/asset overwrite or visibility change.
Documentation-only delivery receipts do not require another identical APK release.

AGENTS/CLAUDE carry this standing rule across manual agent switches. Native tests
not run on a device stay marked unverified. Repository creation, source push and APK
publication are separate verifiable stages; a blocked stage is reported honestly.
CI/cloud signing and automatic app installation are not authorized by this decision.

## D-042 — Public JOKESONG for login-free phone downloads (2026-10-18)

Owner explicitly requests changing the existing repository to public because phone
GitHub login is unavailable. This supersedes D-041's private-only destination, not
its source/APK/documentation sync, secret exclusions, signing compatibility or
no-overwrite rules. Source, history and existing Release assets become public.
Future publisher runs check the explicitly expected visibility (Public by default)
and never change it. No application behavior/version or existing APK is changed by
the visibility operation. Anonymous HTTP access must be verified, not inferred from
an authenticated CLI response. Third-party licence review remains a separate open
task; changing visibility does not certify that review complete or grant a new
licence to the project's own source.

## D-043 — Give the cover wall the library's header space (2026-10-18)

Owner requests moving the header layout-switch affordance into the existing
bottom-right tools cluster and removing the gesture hint. Both walls offer a Layout
subpage with the existing five choices; list/grid get a matching bottom-right
chooser, so selecting them does not remove the way back. Use the existing durable
layout callback and hoisted viewport/focus states; no folder rescan, density/zoom
reset or gesture/layout-engine change is part of this slice. Back dismisses the
chooser/subpage before navigating the library.

Only the library folder bar's content height and title type are scaled to **2/5**
of the former Material top bar. Preserve system-bar insets, global accent/theme
scaling, separate 48 dp wide action targets, and refresh/root/back actions. Do not
shrink unrelated destinations, player chrome or bottom navigation. Keep real
scanning/error/truncation status; remove the permanently reserved presentation row.
