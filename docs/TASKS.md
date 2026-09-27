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
- [x] Collapse the five library-layout icons into one current-layout button with a
      named, checked five-choice menu; layout switching remains presentation-only.
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
- [x] Decode wall artwork at the size it is drawn instead of at its unmagnified
      layout size, which is why zoomed covers were soft. The canvas magnifies cards
      with a graphics layer, so the decode has to follow the magnification; the
      request is bucketed so a pinch re-decodes twice rather than every frame, and
      inexact so Coil stops upscaling small sources. 87/87 tests pass, lint 0 errors
      with the same 12 warnings, `assembleDebug` produces an APK. Not installed --
      sharpness needs the owner's eye on the phone.
- [x] Make the Folia hero's shape and size settings instead of a fixed 6 x 6 square:
      `FoliaFocusShape` (square / portrait / landscape) x `FoliaFocusSize` (three
      presets), chosen from the wall's tools panel beside the density control, per
      folder and restored with the snapshot, defaulting to exactly the old hero. The
      largest presets are capped so every neighbour still gets at least three cells:
      an 8 x 8 hero covers the block but leaves six slivers, so the largest square is
      7 x 7. Two guards close the `targetSlots[-1]` crash path an uncoverable hero
      would have hit. 93/93 tests pass, lint 0 errors with the same 12 warnings, and
      a clean `assembleDebug` produces the APK. Not installed -- the size presets
      need the owner's eye on the phone, and the heart silhouette is the next slice.
- [x] Add the heart and circle silhouettes, a sixth "full cover" shape and an
      icon-only way out of focus. A silhouette is the focused card itself drawn as
      the block cells that spell the shape -- a pixel outline, not an outline over a
      picture -- with the eleven neighbours tiling the rest of the block exactly; it
      is written for one place and one size, so it ignores the tapped slot and the
      size control (shown disabled) unlike a rectangular hero. The two new shapes
      are appended to the enum so persisted ordinals keep their meaning. Full cover
      is the square preset drawn with the artwork fitted instead of cropped, so it
      keeps the square's footprint and the size control. The unfocus button is a
      close glyph in a 38 dp disc at the focused card's bottom-end corner, with no
      label. 97/97 tests pass, lint 0 errors with the same 12 warnings, a clean
      `assembleDebug` produces the APK. Not installed -- whether a silhouette reads
      as the shape it is named for, and whether taps in its bounding-box corners
      reach the card underneath, need the owner's eye on the phone.
- [x] Cut the full-cover card to the cover's own shape instead of laying a square one
      over it. The owner rejected the square footprint -- "无论封面是矩形还是正方形，都是用
      一个足够大的正方形装著……呈现的有些臭" -- and chose 完整显示优先 for what is left over:
      never crop, show the whole cover. The hero is now the rectangle out of the block's
      45 coverable ones whose proportions come nearest the artwork's, read once per focus
      in `CoverAccentLoader` (which already computed the intrinsic size and discarded it);
      the size preset narrows the search to an area band and gives way when nothing there
      comes within a tenth of the cover's shape, and a square or unread cover still gets
      exactly the old square presets. 103/103 tests pass, lint 0 errors with the same 12
      warnings, a clean `assembleDebug` produces the APK. Verified by the owner on the
      phone on 2026-09-26 -- "我检查了，效果可以" -- with no defect reported.
- [x] Address the owner's three notes on Codex's uncommitted appearance slice.
      (a) The wall's continuous settings -- 封面密度, 画布缩放, 环境光效, plus 海报叠色 and
      动态效果 on the 墙面显示 page -- now end their row in a filled crest instead of a
      percentage, whose fill is the setting's position on its own range so it matches
      the slider underneath; the 画布缩放 subpage header keeps its number, and
      封面密度/画布缩放/环境光效/动态效果 each gained a `恢复 100%` row. (b) The settings
      destination is now a short list of expanding groups -- 外观与样式 and 资料库, one
      open at a time, nothing open on arrival -- instead of opening straight into
      appearance, and 资料库 reaches 选择资料库根目录 and 刷新当前文件夹 for the first time.
      (c) The top bar's title, back arrow and refresh/folder actions now take the global
      accent; the immersive player bar deliberately does not, because its fixed
      near-black container cannot carry the light theme's dark accent. 118/118 tests
      pass (up from 113), lint 0 errors with 16 warnings, `assembleDebug` produces the
      APK. Not installed -- see the phone-review entry below.
- [x] Build the timed-text foundation and a lyrics surface to prove the timing with:
      LRC/SRT/VTT are matched to a same-folder audio by base filename with an optional
      language suffix, read with a bounded decoder (UTF-8 then GBK), parsed into one
      normalized line-and-word model, and cached by document identity plus size and
      modified time. An LRC with no time tags is reported as untimed rather than given
      invented times, a version marker in a name is peeled off and the search continues
      so `Track.new.lrc` still reaches `Track.mp3`, and matching is keyed by the item id
      the player already holds. The surface follows playback, taps a line to seek, and
      offers a return-to-current button once the reader scrolls away. 194/194 tests pass
      (up from 118), lint 0 errors with the same 16 warnings, `assembleDebug` produces
      the APK. Verified by the owner on the phone; see the entries below.
- [x] Fix the crash the first build of that surface had on the device. Android compiles
      regexes with ICU, which rejects an unescaped `}` where the JVM's `java.util.regex`
      treats it as a literal, so `AssOverride`'s `\{[^}]*}` passed all 193 JVM tests and
      then threw `PatternSyntaxException` from `<clinit>` the moment a subtitle opened --
      on every track. Escaped it and the two other unescaped `]` in the same file, with
      a comment on the pattern recording why. **No unit test can catch this class of
      bug** because the tests and the app run on different regex engines; the guard is
      escaping literals, plus a device run. Found by driving the app over `adb` at the
      owner's request, not by reading the code.
- [x] Stop a bare time tag from becoming an empty lyric line. Real LRC files mark a pause
      with `[00:28.34]` and no text; each one was an empty row with nothing to read and
      nothing to seek to. Measured on the owner's own library: 61 such lines in one
      sampled file of 285, and 3,034 of its 3,774 `.lrc` files contain at least one. The
      previous line now stays current through the pause. No generated fixture had ever
      contained the pattern -- it came from pointing the parser at real files.
- [x] Review the lyrics surface on the phone. Installed by the owner and confirmed
      working on 2026-09-27 -- "我装了验证，功能没问题". Matching was also confirmed
      against his real library, where `X.mp3` beside both `X.lrc` and `X.en.lrc`
      resolves to the untagged file, as the rules intend. Two visual questions are
      deliberately left to the phase-4 layer rather than closed here: whether the lines
      are legible over a bright cover, and whether a three-line lead-in above the
      current line is the right anchor (the spec says centered; this does not).
- [ ] Review the crest rows, the grouped settings surface and the accent top bar on the
      phone: whether the crest reads at 20 dp and a bottom-up fill reads as a gauge,
      whether 外观与样式 / 资料库 is the right split, and whether an accent title next to a
      neutral container is too strong. Installed by the owner himself on 2026-09-26;
      his verdict on these three points is still outstanding. The same build is also
      the first to carry roadmap phases 1 and 2, so the two entries below can be closed
      in the same review.
- [ ] Review the last two Codex slices on the phone, which have no acceptance record
      anywhere: the Off / Smart / Exact-only word-glow policy in Settings and on the
      focused cover, and the translucent glass treatment of the wall tools button and
      its panels. Compare all three glow policies on the same timed track, check glow
      strength over a bright cover, pause/seek resynchronization, and whether the
      translucent approximation is good enough before authorizing a costlier live
      blur. The same build also carries the frame-path optimization, which needs the
      owner's comparison of drag, inertial release, pinch and focus reflow. Verified
      automatically at 229/229 tests with lint at 0 errors; see the Claude review
      round in `docs/HANDOFF.md` for two minor findings that a device run would settle.
- [x] Replace the per-grapheme highlight with a left-to-right lyric wipe. The owner
      asked for it on 2026-09-27 after finding the highlight too stiff ("一个字很生硬地
      跳到下一个字") and supplied the approved Folia poster as the reference: the colour
      is covered on across the line, and the edge may rest inside a character. The
      renderer is now a continuous wipe over the measured layout, and the sung side is
      solid accent rather than a near-white completed state. Installed and confirmed
      by the owner himself on 2026-09-27 -- "我自己测试了，没问题". Automated verification
      is 229/229 with lint at 0 errors, but note the draw path itself has **no**
      automated coverage -- the repository has no Robolectric or Compose test
      dependency -- so the phone remains the only proof for how the wipe looks. If the
      contrast between the sung and unsung halves is ever reopened, the one knob is
      `UnsungAlphaFraction` in `ui/GlowingLyricText.kt`.
- [ ] Decide whether an appearance reset should also wipe every folder's wall viewport.
      `resetAppearanceDefaults()` currently rewrites all cached snapshots' zoom, density,
      focus shape/size and ambient, and the new grouping puts that button visibly inside
      a group named appearance. Owner's call; flagged in `docs/HANDOFF.md`.
- [ ] Review focus-wall V2, notification permission and paused task-swipe behavior
      on the phone.
- [x] Review the new Folia-style wall's focus transitions on the phone. Verified by
      the owner on 2026-09-25: the focus shape appears where the tapped cover
      already was, with neighbours reshaping around it, including covers that were
      not in a block corner.
- [x] Review the wall tools button and its panel on the phone. Verified by the owner
      on 2026-09-25; no defect reported.
- [ ] Review the single-tap focus policy and the added pan bounds on the phone. The
      build installed at 21:11 carried an earlier double-tap policy and is
      superseded; the single-tap build has been assembled but not installed and
      nothing has been driven by hand.
- [x] Decide whether to keep the pan bound. Claude's own safety rail, added because
      removing release-snapping left pan unbounded; the owner asked what it was for
      and then **accepted it as it is** on 2026-09-25. It is now a settled product
      decision, recorded in `docs/DECISIONS.md` D-004, not an open question.

## Folia presentation roadmap (recommended order)

The researched behaviour, Android mapping, acceptance criteria and clean-room
boundary are recorded in `docs/FOLIA_PRESENTATION_PLAN.md`. Implement in this
order because each layer consumes the stable model built by the one before it:

- [ ] **1. Cover palette + global ambient light.** The implementation and automated
      verification are complete (105/105 tests, lint 0 errors): it follows the
      focused wall item, extracts a bounded cached palette, and cross-fades a
      backdrop plus restrained poster wash in only the Folia wall. Density and
      continuous zoom have moved into the wall tools, which also offer
      off/low/medium/high light strength. Keep this open until phone review accepts
      the colour strength, transition, panel ergonomics and light/dark contrast.
- [ ] **2. Wall presentation controls.** Implementation and automated verification
      are complete: the bottom-right panel now has a `墙面显示` subpage for durable
      lights-out, opt-in Folia-only auto-focus, four poster-tint levels and reduced
      motion. Queue remains correctly deferred until it has a durable destination.
      Keep this open until phone review accepts contrast, transitions and restart
      persistence.
- [x] **3. Timed-text foundation.** Complete and accepted on the phone on 2026-09-27
      (194/194 tests, lint 0 errors): LRC/SRT/VTT match by base filename with an
      optional language suffix, parse into one normalized line-and-word model, and are
      cached by document identity; a lyric-only LRC is reported as untimed rather than
      given invented times; `ui/SubtitleDetail.kt` follows playback, seeks on a line
      tap and returns to the current line. The owner confirmed the surface works. Two
      visual questions -- legibility over a bright cover and the exact anchor of the
      current line -- are carried into phase 4 rather than left open here, because that
      is the layer that owns them.
- [x] **4. Lyrics on the focused/current cover.** Implementation and automated
      verification are complete (203/203 tests): the wall reuses the phase-3
      parsed-document stream, requests it only when focus and Media3 current item
      agree, and mounts at most previous/current/next on that expanded card. Bento
      stays compact and centred; Folia now uses the measured reference composition
      with upper-left metadata, a vertically centred current lyric, equidistant
      context lines, a small real-action control cluster and a thin bottom seek line.
      The focused/current cover now shares the phase-6 Off / Smart / Exact-only
      grapheme glow renderer with the immersive scene; ordinary cards never create
      a timeline or observe the local boundary scheduler.
      Ordinary Folia cards now use the same shaded square-edged poster language with
      only type/title/metadata. A palette-luminance shade protects bright artwork
      without blurring the bitmap. Keep this open until phone review accepts
      readability, proportional placement, hit targets and transport coexistence.
      The same working tree includes a no-feature-change frame-path optimization:
      position ticks now reach only the focused current card, drag alpha no longer
      restarts a tween per card per frame, geometry is calculated once, stable
      gradients are cached, ordinary shadows are removed and off-screen composition
      is bounded more tightly. Automated verification remains 203/203 with lint 0
      errors. The owner accepted the result and frame pacing on 2026-09-27, then
      explicitly moved the roadmap to the next optimization.
- [x] **5. Immersive lyric visualizer V1.** Implementation and automated verification
      are complete (208/208 tests, lint 0 errors): a current timed track opens one
      full-screen Compose scene driven by the shared playback clock, cached cover
      palette and normalized document. It keeps at most five lyric lines (three in
      short landscape), honours global motion including zero-motion, highlights
      enhanced-LRC words, seeks by line or precise slider and exposes real
      previous/play/next actions. It owns no player/parser/frame loop. Keep open until
      phone review accepts contrast, long-line layout, word colouring, scrub accuracy
      and portrait/landscape spacing. The owner accepted V1 on 2026-09-27 and moved
      the roadmap to phase 6.
- [ ] **6. Visualizer registry and advanced backgrounds.** The first slice is
      implemented and verified (211/211 tests, lint 0 errors): Flow, Folia Poster and
      Spotlight share one bounded lyric frame and Media3 clock; mode selection is
      available in the lyric scene and persists. Settings now has persisted Off,
      Smart and Exact-only word glow; Smart prefers source word timing and otherwise
      builds a presentation-only grapheme timeline from real line timing. The same
      policy and renderer now drive the one focused/current cover in both wall styles,
      and that renderer paints one continuous left-to-right wipe instead of lighting
      graphemes one at a time (see the wipe review item above).
      The wall tool button/panels use a translucent
      glass treatment and scale/fade from the bottom-right origin, honouring 0% global
      motion. Keep open for phone acceptance and for later profiled GPU/audio-reactive
      backgrounds; live backdrop capture/blur is not on the wall gesture path.

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

- [x] Subtitle parsing, and a subtitle detail view reached from the player. Not yet
      full-screen: it reuses the immersive player chrome, and the dedicated lyric
      scene is roadmap phase 5.
- [ ] Transactional metadata editor with capability checks.
- [ ] Search, pinyin index and collections.
- [ ] Privacy presentation projection.
- [ ] Encrypted export/import.
- [x] Landscape two-dimensional focus wall.
- [ ] Large-library macrobenchmarks.
