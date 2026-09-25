# Task Board

## Now

- [x] Record product decisions from the discovery interview.
- [x] Establish shared Codex/Claude repository guidance.
- [x] Bootstrap a Kotlin/Compose Android application with a verified Wrapper.
- [x] Deliver the deterministic portrait and landscape focus-cover wall.
- [x] Add centre-depth scaling, drag/snap/fling, pinch zoom and density controls.
- [x] Add unit tests for deterministic layout, centre emphasis and focus state behavior.
- [x] Build the first Poweramp-inspired long-form player detail surface.
- [x] Cache each visited folder and restore its focused item/viewport immediately on back navigation.
- [x] Introduce the Media3 playback service/controller and make SAF audio genuinely playable.
- [x] Connect the focus-card mini timeline and full-player controls to one seekable playback state.
- [x] Add detailed, compact and grid library layouts beside the focus wall.
- [x] Run the first clean build and lint pass. `testDebugUnitTest` passes 48/48 and
      `lintDebug` reports 0 errors; both need
      `-I tools/ascii-build-dir.init.gradle` on this machine. The previously
      recorded loopback blocker did not reproduce and was a misdiagnosis -- see
      `docs/HANDOFF.md`.
- [x] Verify the four completed features on a real device and an emulator, and fix
      what the run exposed: a play action on folder cards that silently did
      nothing, and an "add to queue" button wired to an empty lambda.
- [x] Install the fixed build on the phone. The phone had been left on the
      pre-fix 14:38 build, so the fixes had only ever been seen on the emulator;
      `adb install -r` at 15:25 preserved the persisted SAF root, and a UI dump
      confirms the focused folder card now exposes only the *enter* action.
- [x] Strip the redundant folder affordances: no arrow in the list and grid
      layouts, and no *enter* button (nor its glyph) on the focus wall's focused
      folder card -- tapping the cover already resolves to the same activation.
      Also stop announcing snapshot restores, and delete the long-unused
      `open_folder` string. Built, linted (0 errors, `UnusedResources` 8 -> 7),
      48/48 tests pass, entering a folder re-verified by cover tap alone, and
      installed on the phone.
- [x] Make a folder's summary line name only the counts it has: subfolders alone,
      audio alone, or both. A folder with neither keeps the full line rather than
      going blank. Extracted into one shared, unit-tested helper after the layout
      and focus-wall copies had drifted into duplicates; 54/54 tests pass and lint
      reports 0 errors.
- [x] Commit the accumulated work. `master` was the only branch and there is no
      remote, so the slice went to `master` as `1360e23` rather than to a side
      branch; `bash.exe.stackdump` was left untracked.
- [x] Give each agent its own git identity instead of one shared author: Codex's
      `fbea40e` stays `Codex <codex@local>`, and this slice's commits now carry
      `Claude <claude@local>`, so `git log --author` separates the two. No
      `user.name`/`user.email` is configured in any scope (the global config is
      still untouched), so each commit passes its own with `-c` and a bare commit
      fails loudly rather than misattributing the work. The rewrite left every
      tree byte-identical.
- [x] Review Claude's playback implementation and fix three edge cases: the grid
      Pause button restarted instead of pausing, a paused queue was discarded on
      task swipe, and Android 13+ notification permission was never requested.
- [x] Implement focus-wall V2 geometry: deterministic mixed-aspect bento blocks,
      2 dp gutters, local hero reflow with animated neighbour resizing, and a
      cached artwork-derived translucent focus border.
- [x] Verify the combined focus-wall V2, playback review fixes and Room draft:
      63/63 unit tests pass, lint has 0 errors (12 existing warnings), Room schema
      v1 exports successfully and `assembleDebug` produces a debug APK.
- [x] Preserve focus-wall V2 and add a second Folia-style wall immediately to its
      right: dense 12-item exact-cover blocks, per-original-slot quadrant reflow,
      nearest-slot neighbour assignment and a separate in-session viewport.
      Full verification passes 68/68 tests; lint reports 0 errors and the same 12
      pre-existing warnings.
- [x] Stop the wall from re-focusing on every drag. Release now coasts freely with
      no snap, no re-centring and no focus change; focus moves on a single tap
      only -- tap a cover to focus it, tap it again to open or play it. No
      double-tap handler is bound, which also means a single tap is not held back
      for the double-tap timeout. Both wall modes share the path, so Bento, V2 and
      Folia are all covered in one change. `PRODUCT_SPEC.md` and `DECISIONS.md`
      D-004 were updated, since the old text was what the removed behaviour had
      been written to satisfy. 76/76 tests pass.
- [x] Make the Folia hero grow in place instead of jumping to a block corner: the
      item's original slot now decides where inside the block the fixed 6 x 6 hero
      sits, and the other eleven slots are regenerated as an exact cover around it.
      Replaces the two written corner templates and their mirroring. One existing
      test's stale "not one fixed top strip" assertion was deliberately replaced,
      not dropped -- see `docs/HANDOFF.md`.
- [x] Add a floating tools button at each wall's bottom-end corner with a small
      panel anchored to it, and make its first entry "focus the playing item". A
      cross-folder reveal follows the queue back to the folder it started from,
      recorded at play time; an unreachable track leaves the entry disabled rather
      than silently doing nothing. 83/83 tests pass, lint 0 errors and the same 12
      warnings, `assembleDebug` produces an APK.
- [x] Commit the accumulated working tree, which by then held several slices that
      had never been committed. Split by agent at the owner's request: Codex's
      `4d22e30` for the Room draft, focus-wall V2, Folia wall and artwork accent;
      the gesture policy and this slice follow in Claude's commit. The split is by
      file and two of the shared files interleave both agents' work line by line,
      so Codex's commit does not compile on its own -- see `docs/HANDOFF.md`.
- [ ] Review focus-wall V2, notification permission and paused task-swipe behavior
      on the phone.
- [ ] Review the new Folia-style wall's per-slot focus transitions on the phone
      against the downloaded v0.7.4 demonstration video. In particular: tap a cover
      that is not in a block corner and check that the focus shape appears where the
      cover already was, with neighbours reshaping around it.
- [ ] Review the wall tools button and its panel on the phone, both for a track in
      the current folder and for one in another folder (the reveal should navigate
      there and centre on it).
- [ ] Review the single-tap focus policy and the added pan bounds on the phone. The
      build installed at 21:11 carried an earlier double-tap policy and is
      superseded; the single-tap build has been assembled but not installed and
      nothing has been driven by hand.
- [ ] Decide whether to keep the pan bound. It is Claude's own safety rail, added
      because removing release-snapping left pan unbounded; the owner has not yet
      accepted or rejected it.

## Next

- [x] Implement SAF root selection and persistable URI permission.
- [x] Define Room schema for folders, audio, artwork, subtitles and playback state.
      The v1 entities, paged DAOs, KSP wiring and completion policy compile, tests
      pass, and `app/schemas/.../1.json` is exported. Integration with the scanner
      and Media3 remains separate work.
- [ ] Extend the bounded direct-folder scanner into a full-library scanner with partial progress.
- [ ] Complete artwork resolution (embedded art and recursive fallback); direct image covers and bounded Coil thumbnails are working.
- [ ] Persist the Media3 queue and playback positions in Room.
- [ ] Restore queue insertion on the focused card, on top of the durable queue.
- [x] Request `POST_NOTIFICATIONS` on the first explicit Play action on API 33+;
      playback remains available if the user denies it.

## Later

- [ ] Subtitle parsing and full-screen subtitle view.
- [ ] Transactional metadata editor with capability checks.
- [ ] Search, pinyin index and collections.
- [ ] Privacy presentation projection.
- [ ] Encrypted export/import.
- [x] Landscape two-dimensional focus wall.
- [ ] Large-library macrobenchmarks.
