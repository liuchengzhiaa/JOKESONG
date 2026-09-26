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

## D-013 — Timed text reports what it actually has, and never invents timing

A matched subtitle is turned into one normalized model -- lines with optional word
times plus a separate list of untimed lines -- and every surface reads that model.
Nothing downstream decides what a file meant.

- **An LRC with no time tags is untimed, not evenly spaced.** Its lines are kept
  apart in their own list and the surface says it cannot follow the file. Spacing
  them across the track's duration would make a lyric sheet look synced while being
  wrong from the first line to the last, which is worse than saying so.
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
