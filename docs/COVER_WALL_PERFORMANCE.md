# Cover-wall performance — first reduction slice (2026-10-25)

## Research actually consulted

Owner identified the separate Mihon research conversation.
Read its existing local `MIHON_019_TO_0212_PERFORMANCE.md` and targeted Mihon source
read-only; did not edit/build/message that task or touch its signing files/worktree.
Upstream baseline e4de208cf75e273b425bf4b1fe4bd7bc3d9cc459 vs target
9612d955db3165e1578fdf9ff19ca9b7762c4a46. No upstream code copied.

Useful principles, not a new animation or a claim that Mihon DB uses paging:
- Stop queries that supply unused information; do not simply discard final UI data.
- Keep existing page/list state across navigation and avoid repeat filtering/mapping.
- Cache small cover facts/keys so card presentation does not repeatedly hit disk.
- Bound image memory/in-flight decoding and move I/O/CPU off the main thread.

Primary sources:
- [Flow/state and query gating](https://github.com/mihonapp/mihon/commit/260e31cd2c777ea29f04501825e65b9e6aa236cf)
- [Only load tracking data when needed](https://github.com/mihonapp/mihon/commit/940272f75a228938ff9f122a382ad2bae52f7ce3)
- [Threading and custom-cover file index](https://github.com/mihonapp/mihon/commit/c46317f099e6c14c19dce69dfab9482ea8089048)
- [Target cover fetch/cache coordination](https://github.com/mihonapp/mihon/blob/9612d955db3165e1578fdf9ff19ca9b7762c4a46/app/src/main/java/eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt)

The research confirmed LazyLibraryGrid/LibraryPager were identical across those
versions. Lazy layouts and fetch/decode limits are useful existing mechanisms,
not the new reason0.21.2 is faster. SQLDelight/WAL/reader-save optimizations do not
map directly onto our SAF folder browsing, so do not add a DB migration here.

## Our confirmed avoidable work

Before0.1.16, SafDirectoryScanner read current children, then enumerated up to48
retained child folders to calculate childFolderCount/audioCount, even though folder
artwork used a separate lazy content URI. Every new cover-size request could query
that child's full metadata again in AudioArtworkProvider. Direct-folder lookup also
classified/counts audio and retained first-audio metadata that was never used.

Change one slice: delete statistics and their queries, keep count-free folder title
chrome; add bounded requested-cover-address cache. On a generated folder with>=48
children this removes48 eager metadata queries: listing+own-name stays2, not50,
BEFORE any requested visible artwork. Visible cover lookup still enumerates direct
children once per retained cache key; Android's provider may materialize its cursor.
No promise that provider lookup is O(1) or that all I/O disappeared.

## Verification contract

- Android metadata-only generated provider with10000 folders: top500 natural order,
  own supported count10000/truncated, exactly2 queries,0 child listings/media opens.
- Direct lookup:3 columns, named cover only, no audio opens/descendant fallback;
  null query throws, cancellation closes cursor; audio+LRC matching retained.
- Pure cache tests: hits/misses, scan/write revisions, LRU, parallel coalescing,
  independent loads, errors and cancellation. Existing memory/spatial tests stay.
- Execute Android tests and actual wall smoke on isolated fixtures before publication;
  exact completed results belong in HANDOFF, not an assertion here.

No owner-library frame trace, A/B FPS, peak heap/GC or crash-rate measurement yet.
These changes remove proven queries and repeated lookups; they do not prove every
stutter/OOM is fixed. Next useful slice is controlled phone traces (same folder,
cache state, density/zoom) and targeted text/layout/decode work, not blind larger
caches/concurrency or swallowing errors. Existing500 retained-items limit is unchanged.
