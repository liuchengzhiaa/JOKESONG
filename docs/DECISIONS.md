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

The wall has deterministic spatial layout, explicit focus state, center snapping,
bounded canvas zoom and distinct portrait/landscape presentations. Both
orientations remain two-dimensional, screen-filling mosaics with the focused hero
at the exact viewport centre; portrait must never collapse into a single column.

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
