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

- [x] Rebuild the full player as the reference layout, and take the backdrop colour from the
      cover. Portrait is now the reference's column -- green current-lyric banner, title/artist
      with a `((•))` output mark, one large rounded cover, the current and next lyric under it,
      a thin bar with both times, three transport buttons, and a four-glyph tool row whose
      fifth slot is the collect button. The background is a gradient of up to four colours from
      `CoverAccentLoader`'s palette under a light scrim, where it used to be the cover itself
      blurred over black -- which is why the page read as black before. 476 tests pass, lint is
      0 errors / 26 warnings, and the APK is built; **the phone pass is outstanding**. See
      `docs/DECISIONS.md` D-033 and `docs/HANDOFF.md` for the acceptance checklist.
- [x] Move 闪卡 and the immersive lyric entry into the bottom-right ⋯ button, and change that
      glyph from the four-square `Widgets` to a horizontal ellipsis. `GlassToolCluster` gained
      `triggerIcon` and `triggerRotationDegrees`, both defaulted to the wall's current values so
      the wall is unchanged; the player passes `MoreHoriz` and `0f`, because the 45-degree turn
      exists for a glyph that closes into a cross and only makes an ellipsis look damaged.
      The panel grew to seven rows: 闪卡 / 歌词 / 后退 10 秒 / 前进 10 秒 / 定时关闭 / 书签 / 章节.
      D-035.
- [x] Add repeat mode -- off, list, one -- as a bottom-row control. `Player.repeatMode` already
      existed and the session already lives in the service, so this is read back off the player
      through `PlaybackState.repeatMode` and is durable across a locked screen by construction.
      `core/media/RepeatMode.kt` keeps Media3's ints out of the UI. D-034.
- [x] Add a sleep timer -- off / 5 / 10 / 15 / 30 / 45 / 60 / 90 minutes -- that pauses playback
      when it fires. It lives in `PlaybackService`, not the UI: `PlaybackConnection.disconnect()`
      fires on `ON_STOP`, so a countdown kept in the page would be cancelled by locking the
      screen, which is the one thing the owner is certain to do between arming it and needing
      it. Armed over a `SessionCommand`, read back through session extras. D-034.
- [ ] **Review this round on the phone.** Twelve items, itemised in `docs/HANDOFF.md`; the ones
      that matter most are whether the background is visibly the cover's own colour across three
      differently-coloured songs, whether locking the screen lets the sleep timer fire, and
      whether the ⋯ button really lands in the fifth slot of the tool row.
- [ ] Decide whether two lines is the right limit for the player's title. The reference truncates
      to one; Claude allowed two, on the grounds that a long Chinese title reads better folded
      than cut off. One number if the owner disagrees.
- [ ] Decide whether 歌词 in the ⋯ panel and 可视化 in the tool row are redundant. They open the
      same immersive lyric scene, because the owner asked for both a lyric entry in ⋯ and a
      waveform entry in the bottom row, and this app has one visualiser surface. Deleting the
      panel row is the cheap fix if it reads as a duplicate on the phone.

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
- [x] **6. Visualizer registry and advanced backgrounds.** *(Rejected on looks by the
      owner on 2026-09-27, then kept rather than reverted: phase 7 below shows the
      framework was sound and the content was shallow. Its phone checklist was replaced
      by phase 7's, which covers the same ground plus the two rewritten modes. The
      framework is still uncommitted, inside phase 7's slice.)*
      The framework slice is
      implemented and verified (243/243 tests, lint 0 errors / 16 warnings / 1 hint):
      the 913-line `ui/ImmersiveLyricsScene.kt` is now a host
      (`ui/lyrics/LyricsSceneHost.kt`) over two append-only registries --
      `LyricsVisualizer` (lines and line-change transition) and `LyricsBackground`
      (artwork treatment and shade) -- each dispatched by an exhaustive `when`, so a
      new entry cannot compile until it is implemented. Six modes (Flow, Poster,
      Spotlight, Luminous, Diorama, Pendolo) and six backgrounds (blurred cover,
      sharp poster, dark spotlight, colour field, lattice, drift) ship, chosen by two
      selectors in the scene's own top bar. A background stores `Int?`, where `null`
      means *follow the mode*; each mode declares a default, and the three original
      modes' defaults are exactly the treatments they had before backgrounds existed,
      so a pre-existing install is unchanged. Backgrounds receive a deliberately
      smaller input that excludes the lyric text, which is what keeps the two
      dimensions orthogonal. The newer modes move on line boundaries only -- no new
      frame loop -- and 0% motion is asserted motionless by unit test.
      Kept open for phone acceptance: the three newer modes, the six backgrounds, the
      orthogonality check, persistence across restart, and above all that the three
      original modes are visually unchanged under their default background.
      Settings keeps persisted Off, Smart and Exact-only word glow; Smart prefers
      source word timing and otherwise builds a presentation-only grapheme timeline
      from real line timing, and one renderer paints one continuous left-to-right wipe
      for the immersive scene and the focused/current cover.
      The wall tool button/panels use a translucent
      glass treatment and scale/fade from the bottom-right origin, honouring 0% global
      motion. Later profiled GPU/audio-reactive backgrounds remain open; live backdrop
      capture/blur is not on the wall gesture path.

- [ ] **7. 流光 and 时计 taken to reference grade.** *(Implemented, verified and
      consolidated 2026-09-28; awaiting the owner's phone check.)*
      The owner's answer to phase 6 was to make two or three modes reference-grade
      rather than more modes, keeping the names because the pictures would earn them.
      `Luminous` now draws the reference's `classic` -- one line, its words thrown into
      a deterministic cloud seeded by the line's start time, three word states, a
      per-word wipe, an entry blur and a 7-second breathing float -- and `Pendolo` now
      draws the reference's `pendolo` -- nine lines on an arc whose hub sits on the left
      edge, spacing measured from neighbouring line heights, an escapement drawn on a
      Compose `Canvas`, and one animated line number driving both the lyric wheel and
      the gear so they ratchet together. Both gained their own parameters (three and
      nine), persisted per mode and edited from a third header icon; the global motion
      setting remains the master switch above them. New framework pieces: a second
      registry shape (`WindowedLyricsVisualizer`) for a mode that lays out a whole
      window of lines itself, and a frame clock that runs only while the full-screen
      scene is composed, playing and above 0% motion -- the wall, ordinary cards and the
      cover lyric layer still never run one. `ARCHITECTURE.md`'s no-frame-loop rule was
      rewritten for that, and `DECISIONS.md` D-017 records the change.
      Automated verification is 316/316 with lint at 0 errors / 16 warnings; rendering
      still has no automated coverage, so acceptance is the phone checklist in
      `docs/HANDOFF.md`. Deliberate degradations there too: no audio-reactive dial, no
      exit blur, paused is still, waiting words are not individually blurred.
      `Diorama` deliberately keeps its 2D picture -- the reference's 镜台 is a 3D
      particle corridor and Compose does not attempt an equivalent (D-018). The other
      eleven reference modes and the audio-reactive backgrounds remain unimplemented.

- [ ] **8. Four reference visualizers, one real renderer at a time.** *(Active,
      2026-09-27; unified settings, four-mode registry and the first 凝彩 runtime
      implemented; phone acceptance open.)*
      The owner explicitly superseded the earlier two-mode stopping point after
      reviewing the 时计, 回环 and 凝彩 PVs and the official registry. The timed
      scene now clears Scaffold's system-bar insets, hides/restores system bars only
      for this route, gives every renderer the physical full-screen bounds, and uses
      palette-tinted overlay chrome that auto-hides after 3.2 seconds while playing.
      Its visible controls are smaller but keep 48dp targets; appearing controls do
      not relayout the scene. The tuning sliders also now commit on release after
      their lifted preview is fed back, fixing a restart-persistence failure.
      The owner narrowed the production selector to 流光 / 时计 / 回环 / 凝彩.
      The Flow, Poster, Spotlight and 2D Diorama prototype implementations and labels
      are removed. Surviving modes retain legacy persisted values 3/5/6/7, so an old
      时计/回环/凝彩 choice survives while a removed or unknown choice becomes 流光.
      `Claddagh` (回环) uses a
      bounded lyric rail advances around one tilted ellipse, old/current/future lines
      coexist during the spring hand-off, depth drives scale/alpha/blur, and the
      focal line uses the shared timed wipe. Pure orbit tests and registry tests pass.
      `Tempera` (凝彩) retains stored value 7. Its new pure compiler deterministically
      selects among eight initial shot families, bounds each shot to twelve expressive
      slices and assigns absolute-time entrances. Its renderer separates artwork,
      geometry, expressive type and a stable readable subtitle. It is intentionally
      the first runtime foundation rather than a false 121-shot parity claim. Next is
      paragraph/shot continuity and low-cost capability-tiered post processing, after
      The scene header now has one settings entry. Its right-side/near-full-width
      overlay mirrors Folia's 通用 / 背景 / 歌词动画 / 字幕 hierarchy. 凝彩 can use
      the cover or a bounded persistable-SAF image library and can render into a
      full-screen, 4:4 or 4:3 fitted canvas. Next is phone acceptance, then paragraph/
      shot continuity and low-cost capability-tiered post processing. Automated
      verification is 327/327 tests with lint at 0 errors / 17 warnings / 1 hint
      after obsolete lyric resources are removed.

- [ ] **9. Unified player surface and global record.** *(Implemented, verified and
      consolidated 2026-09-28; phone visual acceptance open.)*
      The ordinary mini-player concept is replaced by one lower-left circular record
      on library/search/playlists/settings surfaces. It uses current Media3 metadata,
      survives leaving the queue's source folder, rotates only while playing, and
      opens the full player with a reduced-motion-aware lower-left expansion. The
      full player now has one adaptive immersive layout: no top bar over the artwork,
      and one lower surface for metadata, the shared thin timeline, five transport
      actions and queue/bookmark/chapter entry points. Lyrics open only through a left
      artwork swipe. The lyric scene header is reduced to an unbacked settings icon,
      an open settings panel suspends chrome auto-hide, and the lyric transport uses
      the same seek primitive as the focus wall and full player. Remaining work is
      real-phone visual tuning in portrait/landscape, especially compact-height
      overflow and the exact record-to-artwork spatial handoff.
      The global record is now directly draggable, snaps to the nearest horizontal
      edge and persists normalized placement. 时计's complete lyric/clockwork wheel
      is also directly draggable in two axes and commits its centre on release. The
      player artwork is reduced to a centred bounded poster and shares the focused
      Folia card's edge, tint-border and directional shade language. Phone gesture
      and visual acceptance remains open.

- [ ] **10. The glass tool cluster, its one-tap style, and the cover flash card.**
      *(Implemented and verified 2026-09-28; uncommitted; phone acceptance open.)*
      The wall's collapsible cluster is extracted into `ui/components/GlassToolCluster.kt`
      and planted in the full player's bottom-right corner; `ToolClusterStyle`
      (`Frosted` default / `Outline` / `Solid` / `Minimal`) restyles every copy of it
      from one row in 设置 -> 外观, stored as `AppSettings.toolClusterStyleOrdinal` and
      passed down as a parameter. The cluster's first entry opens the card workshop:
      a warehouse of cards already made, a form pre-filled from the song, and the
      vendored reference viewer hosted in a `WebView` that renders the card in 3D and
      saves a 1400x1800 PNG into `Pictures/白相/`. The three layers are derived from
      the cover that already exists -- a shared centre crop, a subject cut out through
      ML Kit's confidence mask (with the whole cover as a designed fallback), a blurred
      depth-plane background, and a Skia port of the reference's own typography script
      in a subset LXGW WenKai. No AI image generation, no model and no API path in this
      version, and still **no `INTERNET` permission**. Verification: `testDebugUnitTest`
      **404/404**, `lintDebug` **0 errors / 25 warnings**, `assembleDebug` produces the
      APK, `git diff --check` clean, and no device or `adb` was touched. Lint's
      `UnusedResources` list is what exposed the one missing piece mid-round -- the
      setting's strings, enum, storage and threading were all in place with no page
      rendering them. Remaining: phone acceptance (see `docs/HANDOFF.md`), the model
      path behind the owner's off-by-default switch, and the warehouse's own layout.

      The 2026-09-29 viewer pass removes the desktop-page break in the app: the same
      Three.js scene now has an `?embedded=1` presentation with a full-bleed dark stage,
      native glass back control, glass information/save controls, and one bottom-corner
      button that animates the title/material/depth workbench in and out. Horizontal
      finger movement is no longer clamped to a decorative tilt: it continuously rotates
      through the edge-on point over the full `-PI..PI` range, updates the front/back
      state while crossing 90 degrees, and settles to the nearest face on release.
      Browser verification at 390 x 844 covered the closed and open workbench and a
      front-to-back drag. Real-device visual acceptance remains open.

      **Real-device correction, 2026-09-29:** the owner's System WebView reported
      `innerHeight=904` but resolved both `svh` and `vh`-based document heights to
      `0px` inside this edge-to-edge screen. Three.js was healthy (`ready=true`, six
      draw calls, 414 triangles), but its canvas was literally `407 x 0`; the material
      panel was also capped to 0 and collapsed to its padding. The bundle now measures
      `visualViewport.height`/`innerHeight`, publishes pixel CSS variables before scene
      construction and refreshes them on both viewport resize paths. A source-contract
      test protects the ordering and CSS consumption. CDP injection of the same pixel
      values restored the current phone canvas to `407 x 904` and made the card visible.
      The fixed APK still awaits owner installation under the standing execution rule.

      **Claude review and inset fix, 2026-09-29:** the slice above was reviewed against the
      working tree and holds -- both asset-server fixes are preserved, the bundle's
      `syncEmbeddedViewport()` runs before `init()` and its `if (renderer) resize()` is
      legal because `var renderer` is hoisted, and the save anchor and bridge hook are
      unchanged. One hole was closed. The embedded shell positioned its own glass actions
      and workbench from `env(safe-area-inset-*)` in eleven places; Android System WebView
      resolves that to zero inside this edge-to-edge app, so the page's information/save
      buttons drew 14 px from the physical top of the screen -- under the status bar --
      beside a Compose back control that cleared it correctly. The host now measures
      `WindowInsets.safeDrawing`, the same inset the back control already uses, and
      publishes `--safe-top/right/bottom/left` to the page, with `env()` kept as a second
      source behind a `max()` so that with neither the offsets fall back to exactly the
      constants they had. Guarded by `CardViewerSafeAreaTest` and a CSS case in
      `CardViewerAssetContractTest`. The slice is built and green; **nothing is installed
      on the phone**, so every visual question this item raises is still open.

- [x] **11. The viewer's WebView rendered blank on the owner's phone: two bugs.**
      *(Found and fixed 2026-09-28 on the device. Bug 1 verified; bug 2 installed but
      awaiting re-verification.)*
      The page was not blank because of the card build. It was being rendered as **plain
      text**: `CardAssetServer` passed a parameterised mime type
      (`"text/html; charset=utf-8"`) as well as the charset in the `encoding` argument, and
      a main frame typed that way goes to Chromium's plain-text viewer -- the document that
      results is `<body><pre>&lt;!doctype html&gt;...`, the card's own source on a white
      page. Fixed by making every type in both route tables bare. Verified on the device:
      the shell, the parameter panel, the four finishes and the gloss slider all render.
      That exposed the second bug, which the first had been hiding: `assets/card.glb`
      404'd, because `AssetRoutes` derived the asset path by prefixing `card/` while the
      vendored template keeps the model beside its other files rather than under
      `assets/`. Fixed by naming the asset path in the table; **installed but not yet
      re-verified** -- the device was disconnected first.
      The `ViewerMessage` colour bug from the previous round was real and is also fixed
      (theme colours drawn on the shell's light background, i.e. invisible), but it was not
      what the owner was looking at.
      The on-device WebView can be inspected from the PC over the DevTools protocol; the
      recipe, including the port that must not be used, is in `docs/HANDOFF.md`.
      **Remaining**: open a card and confirm the card itself draws and Save writes a PNG.

- [ ] **12. The owner's four card asks: a picture at once, a collection room, pinch to
      zoom, and the card as the cover.** *(Implemented and verified 2026-09-29;
      uncommitted; phone acceptance open. Installed by the owner.)*
      (a) A card is never a blank frame: `CardLayerFactory` now writes `preview.png`, a
      flat 512x768 composite of the same three layers the viewer loads, and
      `CardSummary.thumbnail` is the viewer's capture when one exists and the preview
      otherwise. This closes the one phone-pass item that described known-wrong
      behaviour rather than an unknown one. (b) The warehouse is now a collection room:
      a search box, three sort orders and a selection mode with batch pin and batch
      delete, and the wall of cards is shelved -- 置顶 first, then one shelf per series,
      then the ungrouped remainder -- so a growing collection stays navigable. Pinning
      is a key in the card's own `meta.json`, and shelves are recomputed on every read
      rather than stored, so no index can disagree with the files. The room is reached
      from 设置 -> 闪卡 as well as from the player, and reached from Settings it starts
      a new card blank rather than from whatever was last playing. (c) A card in the
      viewer can be pinched: the gesture is injected as the wheel events the vendored
      page already handles, so the page's own zoom zooms (its clamp widened to
      0.72-2.4), a double tap resets it, and a Kotlin test reads the clamp out of the
      bundle so the two cannot drift. (d) On the full player, a song that has a card
      shows the card instead of its artwork -- always live, per the owner's explicit
      choice of power draw over a still image -- in a new `?bare=1` shell mode that
      hides the page's own save button and panels before first paint. The lyrics
      surface keeps an explicit 闪卡 row so the lost artwork swipe still reaches the
      studio.
      Verification: `testDebugUnitTest` **438/438**, `lintDebug` **0 errors / 26
      warnings**, `assembleDebug` produces the APK, and no device and no `adb` was
      touched. Remaining: phone acceptance, the model path behind the owner's
      off-by-default switch, and the room's own visual tuning.
- [ ] **13. The owner's correction to the card cover: it is asked for, it is the card's
      own shape, and the entry says what it does.** *(Implemented and verified
      2026-09-29; uncommitted; phone acceptance open.)*
      (a) The card cover is now a choice, not a state: the default is the original
      artwork, showing the card is per song and per visit, and a new off-by-default
      setting 封面默认闪卡 covers the owner who would rather not ask every time. The fold
      is `showsCardCover`, a pure function with a unit test
      (`CardCoverChoiceTest`). **A card built from the player still does not switch the
      cover to itself** -- the owner said the card is asked for each time, and this is
      the literal reading; flagged in `HANDOFF.md` as the one place he may have meant
      otherwise. (b) The black frame around a card in the cover slot is gone: the cause
      was the slot's *shape*, not a colour -- the page's `resize()` fits a 2:3 card to
      ~58% of a square viewport's width and leaves the rest to its own near-black
      background. The slot now takes the card's own proportions and draws none of the
      artwork's hairline, square or shade. The 2:3 is read from
      `CardLayerGeometry.CanvasAspect` rather than written a third time. (c) 闪卡制作 is
      now 闪卡 and no longer opens the room: with no card on the song it opens the form
      directly (the room is one back away, and back from the form leaves the workshop),
      and with a card it switches the cover -- 原封面 while the card is showing, so the
      label states what pressing it does rather than what the song has. Opening from
      Settings is unchanged and still the room.
      Verification: `testDebugUnitTest` **442/442**, `lintDebug` **0 errors / 26
      warnings**, `assembleDebug` produces the APK, and no device and no `adb` was
      touched. Remaining: phone acceptance, and the two judgement calls flagged in
      `HANDOFF.md` (no auto-switch after a build; back from the viewer lands on the
      room on the direct-from-a-song path).

## Next

- [x] Implement SAF root selection and persistable URI permission.
- [x] Define Room schema for folders, audio, artwork, subtitles and playback state.
      The v1 entities, paged DAOs, KSP wiring and completion policy compile, tests
      pass, and `app/schemas/.../1.json` is exported. Integration with the scanner
      and Media3 remains separate work.
- [ ] Extend the bounded direct-folder scanner into a full-library scanner with partial progress.
- [ ] Complete artwork resolution (embedded art and recursive fallback); direct image covers and bounded Coil thumbnails are working.
- [ ] Persist the Media3 queue and playback positions in Room.
- [x] **Historical decision, superseded by phase 8: two modes to reference grade
      before expanding the catalogue.**
      The earlier version of this item said the remaining modes were "one registry
      entry plus one file each". That was wrong and is withdrawn. Reading the
      reference's source on 2026-09-27 (see `docs/HANDOFF.md`, "Claude investigation")
      showed each of its thirteen modes is a distinct visual language thousands of
      lines deep -- Tempera alone has 121 shot kinds in thirteen families, Sonnet and
      Tempera are compile-time storyboard systems, Diorama is a 3D particle scene --
      and each carries its own parameter set on the lyric-animation settings page.
      The owner first chose to take `流光` and `时计` to reference grade. After the
      later source/video audit the owner explicitly reopened the remaining catalogue;
      phase 8 and D-019 are now authoritative. The useful part of this decision still
      holds: no shallow preset may use a reference name, and existing mode ordinals
      must never be renumbered.
- [ ] Restore queue insertion on the focused card, on top of the durable queue.
- [x] Request `POST_NOTIFICATIONS` on the first explicit Play action on API 33+;
      playback remains available if the user denies it.

## Later

- [x] Subtitle parsing, and a subtitle detail view reached from the player. Not yet
      full-screen: it reuses the immersive player chrome, and the dedicated lyric
      scene is roadmap phase 5.
- [x] First single-track transactional metadata editor with capability checks (D-040).
      Native/SAF device acceptance and broader metadata features remain below.
- [ ] Search, pinyin index and collections.
- [ ] Privacy presentation projection.
- [ ] Encrypted export/import.
- [x] Landscape two-dimensional focus wall.
- [ ] Large-library macrobenchmarks.

## Current slice — 2026-10-11 player alignment
- [x] Preserve incoming card/player work; intrinsic-fit artwork and smaller default sizing.
- [x] Raw dominant tonal backdrop; existing wall palette unchanged.
- [x] Explicit transport skips; ordinary swipe lyric page before existing visualizer.
- [x] Durable cover/title/lyric percentages in Appearance and player tools.
- [x] Final verification: 480 JVM tests passed; lint 0 errors / 29 warnings; fresh APK built.
- [ ] Owner phone pass: proportions/fonts/colour, swipe + scroll + seek, return retention
      and persistent sizing (see latest HANDOFF).

## Current follow-up — colour, lyric context, audio artwork
- [x] Diagnose identical thumbnails: scanner assigned one folder image to all audio.
- [x] Mid-tone chromatic player colour selection + dark crown/soft lateral field.
- [x] Cover lyric previous/current/next window, current highlighted.
- [x] Lazy private per-track embedded art with same-name/folder fallback, bounded cache.
- [x] JVM/Lint/APK verification: 486 tests passed; lint 0 errors / 29 warnings; APK built.
- [ ] Owner phone refresh/compare: verify distinct embedded covers and colour/three-line lyrics.

## Current slice — desktop lyrics (2026-10-11)
- [x] Independent durable settings: opt-in, font/colours, 1–4 sentences, line/word,
      bold, opacity, locking and normalized position.
- [x] Playback-service overlay; shared matching/parser/timing, screen-off/
      keyguard/permission gating, drag/close and touch-through lock.
- [x] Final tests/lint/APK: 495 tests passed; lint 0 errors / 30 warnings; APK built.
- [ ] Owner phone pass: grant/deny/revoke, home/other app/return, locked touch-through,
      drag/rotation/restart, seek/pause/next/previous/missing and untimed lyrics.

## Current follow-up — continuous overlay and friendly colour controls
- [x] Owner changed visibility: keep the same desktop lyric window inside/outside app.
- [x] HSV gradient sliders, circular thumbs, swatches/presets/reset; existing colours preserved.
- [x] Explicit transparent-backdrop shortcut and lock hint; metrics cached between configuration changes.
- [x] Final tests/lint/APK: 501 tests passed; lint 0 errors / 29 warnings; APK built.
- [ ] Owner transition/palette/persistence/transparent-background + lock/rotation pass.

## Current request — direct metadata editing (2026-10-18)
- [x] Review current source, D-003, safety contract and native metadata library candidate.
- [x] Owner “写入吧” accepts explicitly recoverable/non-atomic local-provider commit;
      record D-040 and supersede former architecture strict-atomic-only clause.
- [x] Details-page editor, nine tag fields, embedded front-cover replacement/removal,
      SAF picker initial-directory hint and direct file writer (TagLib 1.0.6).
- [x] Sibling-only mutation, independent audio/tag/art validation, durable journal,
      startup/editor recovery and conflict refusal; latest bounded old-tag/art snapshot.
- [x] Playback metadata/artwork refresh, captured edit target, folder focus/viewport retained.
- [x] JVM generated-fixture container/transaction/fault/recovery tests; generated PCM
      Android native round-trip test added and instrumentation APK compiles.
- [x] Final verification of latest code: 69 suites / 522 JVM tests passed (21 metadata
      tests), lint 0 errors / 29 warnings; app APK + instrumentation APK built.
- [ ] Owner-authorized device acceptance on disposable/generated MP3/FLAC/M4A/WAV:
      actual native tag/art round trips, SAF commit/restart recovery, rotation and
      playback/cache/notification refresh. No original user media used as tests.
- [x] Owner reports basic metadata editing flow verified successfully (2026-10-18).
      Detailed fault/format coverage and generated native instrumentation remain unverified.
- [ ] Undo UI, batch writes, circle/publisher/custom-tag editing and broader providers.
- [ ] Native corresponding-source/relinking licence review before public release.
      Implementation/limitations: `docs/METADATA_EDITOR_PLAN.md`; latest HANDOFF entry.

## Current slice — JOKESONG and private GitHub delivery (2026-10-18)
- [x] Owner chooses JOKESONG and authorizes full source + APK + record synchronization.
- [x] Official CLI device authorization completed; credentials verified in keyring.
- [x] Created and verified private `liuchengzhiaa/JOKESONG`.
- [x] JOKESONG launcher/docs branding, version 0.1.1 / code 2; application ID unchanged.
- [x] Shared AGENTS/CLAUDE delivery policy, CHANGELOG, RELEASING and sensitive-file ignores.
- [x] Publish script with clean-commit/private-origin/version/signer guards and remote
      commit/asset digest verification; local parser/PlanOnly passed; fingerprint pinned.
- [x] Focused common-secret/private-file/large-blob screening of source and 19 old commits.
- [x] New version: 522 tests passed; lint 0 errors / 29 warnings; app/test APKs built.
- [x] Initial source/history pushed; v0.1.1 Release published and independently verified:
      private repo, exact source/tag SHA, all three attachment sizes/SHA256 digests.
- [ ] Owner phone Release download/cover-install acceptance without USB.

## Current follow-up — public downloads (2026-10-18)
- [x] Owner explicitly authorizes public source/history/APKs; D-042 supersedes private-only delivery.
- [x] Re-screen all 22 existing commits for common secrets/sensitive-media paths: no matches.
- [x] Repository switched to public; anonymous repo/latest-release reads and APK HEAD succeed (200).
- [x] Publisher expected-visibility guard and shared delivery docs updated; APK v0.1.1 retained.
- [x] Publisher visibility guard: four combinations pass; PowerShell 7/5 PlanOnly passes.
      Standard JVM/lint command succeeds (cached 522 tests; lint 0 errors / 29 warnings).
- [ ] Owner phone download/install acceptance. Native licence/source/relinking review remains open.

## Current slice — more cover-wall space (2026-10-18)
- [x] Move five layout choices into the wall's corner tools; matching list/grid chooser.
- [x] Remove the standalone layout/playing row and gesture hint; canvas takes freed space.
- [x] Library header content height/title/line-height at 2/5; keep insets and separate action targets.
- [x] Existing focus/viewport/settings callbacks retained; reference attachments excluded from Git.
- [x] Final layout/interaction: 17 targeted tests; full 69 suites / 523 tests passed,
      lint 0 errors / 28 warnings, app/test APKs built and app signer/version verified.
- [x] Source/docs pushed; v0.1.2 / code 3 published, source/tag and all asset digests
      verified; latest APK responds HTTP 200 without login.
- [ ] Owner phone acceptance: header readability/actions, five-way switching, wall gestures/return retention.

## Current slice — folder artwork and large-wall stability (2026-10-18)
- [x] Remove left up icon, raise compact header type/height to 55%, action glyphs to 20 dp.
- [x] Direct-image / first-natural-audio cover fallback, lazy covers beyond 48 summary probes.
- [x] Scene construction off-main, two-axis spatial query and drawing-layer continuous pan/alpha.
- [x] Shared bounded Coil cache/fetch/decode work, original resolution/effects retained.
- [x] Owner-initiated numeric exit diagnostics in Settings → Library, no file information/uploads.
- [x] Final verification: 72 suites / 536 tests, lint 0 errors / 28 warnings;
      app/test APKs built and app package/version/signer verified. No phone connected for reproduction.
- [x] Reviewed/pushed source and documentation; v0.1.3 / versionCode 4 published.
      Exact source/tag SHA and three asset digests verified; anonymous latest APK HTTP 200.
- [ ] Owner checks fallback after refreshing directory and stresses large-wall pan/focus.
      If exit recurs, copy runtime diagnostics. Actual native allocation/exit cause remains unproven.

## Current slice — manual folder covers (2026-10-18)
- [x] Long-press folder actions in both walls, lists and grid; shared song-cover SAF picker hint.
- [x] Private bounded PNG copy + existing Room artwork metadata, no media writes/new permissions.
- [x] Explicit automatic reset, raw snapshots preserved, no-rescan cover projection and URI isolation.
- [x] Narrow 19 tests; final 73 suites / 540 JVM tests passed, lint 0 errors / 28 warnings.
      App/test APKs built, package/version and unchanged signer independently verified (0.1.4 / code 5).
- [x] Reviewed source/docs pushed; v0.1.4 published, exact tag/source SHA and all three
      attachment digests verified; anonymous latest APK HTTP 200. Receipt in HANDOFF.
- [ ] Historical v0.1.4 private-reset acceptance superseded by D-048; check legacy migration,
      long press (focused/unfocused), cancelled picker and save/replace instead.
      same-name folders, refresh/restart, all five layouts and retained focus/viewport.
      Android repository tests compiled only; SAF image decode/copy not device-executed.


## Current slice — audio long-press cover editing (2026-10-18)
- [x] Item long-press dispatch in all five layouts; folder private override behavior retained.
- [x] Cover-only existing metadata editor, shared SAF picker hint, explicit real-file save/removal.
- [x] No hidden tag edits or text-tag writer; unchanged-tag verification and original transaction safeguards.
- [x] Captured target across rotation/queue changes; existing snapshot/queue/artwork refresh without rescan.
- [x] Narrow 31 tests; full 74 suites / 543 JVM tests pass, lint 0 errors / 28 warnings;
      app/test APK built; unchanged package/signer and 0.1.5 / code 6 verified.
- [x] Source/docs pushed; v0.1.5 published, exact source/tag and three asset digests verified.
      Anonymous latest APK returns HTTP 200; actual delivery receipt is in HANDOFF.
- [ ] Owner phone checks audio long press, cancel/replace/remove/save, target stability during
      queue changes/rotation, current/other-track pause behavior and refreshed covers in all layouts.
      Native/SAF checks are compiled only; use disposable copied/generated media for acceptance.


## Current slice — intrinsic player art and multicolour field (2026-10-18)
- [x] Display decoder aspect first, sampled bounds fallback, Fit and size settings retained.
- [x] Shared bounded nine-region/mid-tone colour extraction with regional/global dark coverage.
- [x] Pure Gaussian field renderer, small cached texture and motion-aware track crossfade.
- [x] Narrow 25 tests; final 75 suites / 557 tests pass, lint 0 errors / 28 warnings;
      app/test APKs built, package/signer and 0.1.6 / code 7 independently verified.
- [x] Synthetic and reference-colour-only local previews inspected; private screenshots not staged.
- [x] Reviewed source/docs pushed; v0.1.6 published, source/tag and three attachment digests
      independently verified; anonymous latest APK HTTP 200. Delivery receipt in HANDOFF.
- [ ] Owner phone checks identical portrait/wide/square source art, cold/failing palette load,
      percentages/rotation, dark vs colourful covers, rapid cuts and ordinary-lyrics paging.
      Android generated-image decoder test compiled only; no phone FPS or exact Salt match claimed.


## Current slice — portable recursive folder covers (2026-10-18)
- [x] Replace folder-private presentation overrides with exact disk cover priority.
- [x] Lazy natural-order direct-audio search, recursive children and bottom-up real cover copies.
- [x] Manual byte-preserving cover copy, sibling stage/fsync/hash, recoverable rename journal and retained backups.
- [x] Lazy legacy migration, read-only truthful display fallback, old reset removed; no audio tag edits/new permission.
- [x] Bounded natural paging/traversal, independent folder/audio locks and hidden artifact exclusion.
- [x] Final 77 suites / 587 JVM tests pass, lint 0 errors / 28 warnings; app/test APKs
      built, unchanged package/signer and v0.1.7 / code 8 independently verified.
- [x] Source/docs pushed; v0.1.7 / code 8 published, exact source/tag and all three
      attachment digests verified; anonymous latest APK HTTP 200. Receipt in HANDOFF.
- [ ] Owner phone disposable-tree checks: actual cover files, later-track artwork, nested backfill,
      manual replace/recovery, read-only/regranted roots, update/restart/phone-copy and wall responsiveness.


## Current slice — direct-cover browsing and explicit batch generation (2026-10-18)
- [x] Folder display reads only its direct named cover; no audio/legacy/recursion/write/lock.
- [x] Settings → Library confirmed whole-root batch, all descendants/siblings, existing covers preserved.
- [x] Progress and counts, stop/rerun, separate ViewModel lifetime and root-change cancellation/join.
- [x] Audio long-press chooser: embedded-cover editor OR set embedded image as captured direct folder cover.
- [x] Keep stage/hash/backup/journal writer, explicit-only recovery and no audio writes/new permissions.
- [x] Final 77 suites / 589 JVM tests pass, lint 0 errors / 28 warnings; app/test APKs
      built, version 0.1.8 / code 9 and pinned package/signer verified.
- [x] Source/docs pushed; v0.1.8 published, exact source/tag plus all three asset digests
      independently verified; anonymous latest APK HTTP 200. Receipt in HANDOFF.
- [ ] Phone: compare first wall load; run batch with covered parent/uncovered descendants, stop/rerun,
      read-only errors and all five layouts' audio actions. Keep app open while processing.


## Current slice — batch metadata enumeration speed (2026-10-19)
- [x] One SAF traversal snapshot per folder, natural ordered scratch catalog pages; bounded memory/disk.
- [x] Reuse positive document URIs and source digest; keep live conflict/safe-write checks.
- [x] Early progress and skip irrelevant legacy queries; no display-policy or permissions change.
- [x] assembleDebug compiled v0.1.9 / code 10, same package/signer; no tests/Lint per owner.
- [x] Published explicitly untested v0.1.9 APK and source/docs; source/tag and asset
      digests verified, anonymous version APK HTTP 200; test fields null/skipped.
- [ ] Owner phone speed/stop/rerun/large-folder acceptance; no FPS/throughput claim from desktop tests.
- [ ] JVM/Lint/instrumentation verification explicitly skipped for this release at owner's request;
      run normal full verification next time, including new catalog tests, before claiming them passed.
