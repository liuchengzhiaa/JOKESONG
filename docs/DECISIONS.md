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
Folia-style wall sits immediately to its right in the layout switcher. The latter
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
