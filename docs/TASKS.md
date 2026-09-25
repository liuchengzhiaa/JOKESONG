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
- [ ] Re-run Gradle tests/lint and verify those three review fixes on the phone.
      Codex's restricted runner still blocks Gradle loopback; use the two commands
      recorded in `docs/HANDOFF.md` from Android Studio or a normal terminal.

## Next

- [x] Implement SAF root selection and persistable URI permission.
- [ ] Define Room schema for folders, audio, artwork, subtitles and playback state.
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
