# Handoff

## Current state

The repository began empty. Product discovery is complete and captured in the
documents under `docs/`. Android Studio and an SDK are installed locally, including
API 37 and Build Tools 36, but this terminal did not initially expose SDK tools in
`PATH`.

## Active implementation

The Kotlin/Compose application scaffold and the first complete focus-cover wall are
present. The library now selects exactly one SAF root, persists its URI grant with
Preferences DataStore, scans only the current folder's direct children, and allows
folder entry, parent navigation and manual refresh. Scans retain at most the first
500 naturally sorted display items while still counting all matching direct
children, so this first slice stays memory-bounded.

The wall uses deterministic world coordinates and a custom `SubcomposeLayout`.
Both orientations are predictable, screen-filling two-dimensional mosaics;
portrait prioritizes vertical exploration but never collapses into a single
column. Cards scale continuously by distance to the viewport centre,
so the centre cover is dominant and surrounding covers recede. Dragging,
inertial release, bounded whole-canvas pinch zoom and small/medium/large density
are implemented without relayout during a gesture. Only near-visible cards are
composed. Focus and playback presentation are independent;
focused folders expose explicit Play and Enter actions, and focused audio exposes
Play/Pause and queue affordances. Back clears focus before navigating upward.

An audio that has entered the local playing state opens a Poweramp-inspired dark
full-player surface when its focused card is clicked again. It has large artwork,
on-demand real duration/artist/album metadata, precise seek UI, configurable-style
10-second jumps, previous/next navigation, and queue/bookmark/chapter bottom
panels. Artwork opens a dedicated immersive subtitle/artwork layer. Timeline and
bookmark math are pure and tested; real decoding is now provided by Media3 (see
below), while durable queue/bookmark persistence still belongs to the pending
Room task.

The UI now uses scanned folders and supported audio instead of fixture data. The
player reads a small metadata subset on demand for its current item. The Room v1
schema exists, but scan results are not yet integrated into a persistent full
index; complete embedded metadata/artwork and the recursive full-library scan also
remain pending.

Direct raster artwork is now resolved for the current folder and for the first 48
naturally sorted child folders, then loaded through Coil 3.6.3 so decoding and
memory caching stay display-bounded. Audio inside a folder inherits that folder's
first direct image for now. Embedded audio artwork and recursive child-folder
fallback are still pending, so the full product artwork precedence is not yet
complete.

## Current slice: Folia presentation roadmap through immersive lyrics V1

The owner asked that the complete Folia research and recommended implementation
sequence become repository state so Codex and manually started Claude Code can
alternate without relying on either chat transcript. The new canonical record is
`docs/FOLIA_PRESENTATION_PLAN.md`. It contains:

- the inspected Folia commit/video and the AGPL clean-room boundary;
- the observed implementation/effect of ambient light, poster lyrics, the full
  visualizer host, the bottom-right controls, poster tint, theme, queue,
  auto-focus, visualization selection and Electron transparency;
- the Android/Compose translation and performance/privacy/accessibility limits;
- the dependency-ordered six-phase roadmap and acceptance criteria;
- the real-device questions that remain visual tuning, not code assumptions.

`PRODUCT_SPEC.md`, `ARCHITECTURE.md` and `DECISIONS.md` D-010 now lock the important
phase-1 decisions. `TASKS.md` carries the same six phases in recommended order.
The owner clarified after the first implementation that the effect must follow the
currently focused cover, not playback. It remains presentation-only, uses bounded
work, is currently Folia-wall-only, and must eventually receive the privacy alias
artwork rather than canonical artwork.

### Phase 1 implementation now in the working tree

- An intermediate implementation added the current Media3 item's artwork URI to
  `PlaybackState`. That was removed after the owner corrected the source-of-truth:
  playback and wall lighting are independent, so no new playback field remains.
- `CoverAccentLoader` still does one bounds pass plus one approximately 96 px
  decode and samples at most 4,096 pixels. The same pass now returns up to four
  deterministic, population-weighted, sufficiently distinct colour families as
  well as the existing accent and intrinsic dimensions. Transparent, near-black
  and near-white samples are ignored; very dark/bright selected swatches are
  gently balanced for use as light. The existing 96-entry LRU cache is reused.
- `FocusCoverWall` reuses the focused cover facts it already needs for hero shape
  and border. Those facts drive the Folia palette; clearing focus supplies an
  empty palette and fades back to neutral. If the focused item lacks resolved art,
  its deterministic placeholder accent supplies the temporary palette. The
  retained Bento/V2 style is still given an empty ambient palette.
- `CoverAmbientLayer` is composed once below and once above the
  `SubcomposeLayout`. The backdrop draws three oversized radial gradients and a
  shallow vignette; a much weaker poster wash repeats only the colour gradients
  above the opaque mosaic. The second pass was added after review found that a
  2 dp packed wall hid nearly all of a backdrop and made the first implementation
  effectively invisible. Neither pass owns pointer input. Light mode uses lower
  alpha and palette changes cross-fade over 900 ms. There is no full-size bitmap,
  bitmap blur, shader or permanent animation loop, and animation state does not
  re-run the wall's spatial layout.
- The permanent density chip/zoom percentage row above the wall is gone. Both
  walls now expose density and a continuous 72%–135% zoom slider (plus 100% reset)
  inside the bottom-right tools panel. The active wall, rather than always the
  dormant Bento state, now publishes zoom/pan into the folder snapshot.
- Folia adds an environment-light page in the same panel with
  off/low/medium/high. Medium preserves the original tuning; the multipliers are
  0/0.6/1/1.45 and animate for 260 ms. The ordinal is carried through
  `FocusWallViewportState`, `LibraryUiState` and the bounded `FolderSnapshot`, so
  returning to a warm-cached folder restores it without introducing UI types into
  storage.
- Palette unit coverage now checks stable order, distinct families, alpha/dark
  filtering, limit handling and the old dominant-colour contract.
- The five permanent library-layout icons were collapsed into one 36 dp circular
  layout button. Its glyph is always the current layout. Opening it shows a named
  five-row menu (detailed, compact, grid, Bento wall, Folia wall); the active row
  keeps the primary tint and an explicit check mark. Choosing the active row only
  closes the menu, while another row reuses the existing presentation-only switch
  and therefore triggers no scan or navigation reset.

### Phase 2 implementation now in the working tree

- The Folia root tools panel now has one `墙面显示` row. Its subpage holds
  lights-out, track-change auto-focus, poster tint and reduced motion; tint has its
  own off/low/medium/high page. Bento does not expose the Folia presentation page.
- Lights-out animates ordinary posters down to at most 22% alpha. The focused card
  and current Media3 item are exempt. Ambient light remains independently governed
  by its existing strength setting instead of being silently disabled.
- Poster tint uses the focused palette's primary colour (theme primary without a
  focus) at 0/10/20/34% alpha. It is layered after artwork and before the existing
  readability shade, and likewise exempts current/focused cards.
- Auto-focus is off by default and active only while the Folia layout is selected.
  Only an actual Media3 item-ID change invokes the same guarded reveal path as the
  manual tool; entering Folia or enabling the setting does not move focus. Thus
  cross-folder movement works only while the queue's source folder is known.
- Reduced motion cancels an active coast, disables new fling, and changes the
  280/320/900/260 ms wall, card, ambient, tint and border animations to immediate
  state changes. Direct pan, pinch, focus and playback controls are unchanged.
- All four settings are global and process-durable in
  `WallPresentationPreferences`, a separate DataStore from the SAF root. ViewModel
  updates are optimistic and every folder-loading path reapplies the latest values.
  No library rescan, Room write or media mutation is involved.
- The queue row is deliberately absent until queue persistence supplies a real
  destination. This is the planned phase-2 exception, not unfinished button wiring.

Phases 1 and 2 are **automatically verified but still awaiting real-device visual
acceptance**. No APK was installed and no emulator/phone UI was launched in these
slices. The safest next action is to install/run the current build on the phone,
select the Folia layout and check: palette relation and transition, light/dark
contrast, all ambient/tint levels, lights-out exemptions, next/previous auto-focus,
restart persistence, reduced-motion behaviour, panel ergonomics and visible jank.
Tune only the recorded alpha/radius/duration values from that evidence.

Exact verification on 2026-09-26:

The commands and counts below were rerun after the full-wall poster wash, the
owner's correction from playback-driven to focus-driven colour, the wall-tools
relocation/strength control, the single-button library-layout menu and all phase-2
presentation controls.

```powershell
$env:GRADLE_USER_HOME='C:\Users\LiuDeZhou\.gradle'
$env:TEMP='C:\jtmp'
$env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
```

- `testDebugUnitTest`: **109/109 passed**, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors and the same 12 pre-existing
  warnings.
- `git diff --check`: clean apart from Git's informational LF-to-CRLF notices.
- The first wrapper attempt inherited an invalid `C:\.gradle` home; the verification
  above explicitly used the owner's existing Gradle cache. A first Gradle launch
  then reproduced the documented loopback error, and setting only this process's
  `TEMP`/`TMP` to the existing `C:\jtmp` fixed it.
- `bash.exe.stackdump` remains the pre-existing untracked file and was not touched.

### Claude slice: level crests, a grouped settings surface, the accent in the top bar

The owner reviewed Codex's uncommitted global-appearance slice (`core/settings/AppStyle.kt`,
`core/storage/AppPreferences.kt`, the accent/scale wiring in `ui/theme/Theme.kt`, and
`ui/SettingsContent.kt`) on the phone and gave three instructions. All three are
implemented in the working tree; the build is green and no APK was installed.

**1. Wall settings show a level crest instead of a percentage.** `WallTool` gained
`levelFraction: Float?`, drawn by the new `ui/CrestLevelIcon.kt` as a decorative crest
filled from the bottom up. Rows carrying it are 封面密度, 画布缩放 and 环境光效 — the three
root rows the owner circled — plus 海报叠色 and 动态效果 on the 墙面显示 page, which are the
same kind of control and would otherwise be the only two left showing numbers.

- The fill is `levelFraction(value, that setting's own range)`, added as
  `core/settings/LevelFraction.kt`, so the crest sits exactly where the slider
  underneath it does. That is a position, not the number the row used to print, and the
  two differ because the ranges do: density is 70..130, ambient 0..150 and canvas zoom
  72..135, so 100% is a half-filled crest for density and two-thirds for ambient.
  `LevelFractionTest` pins all three cases, the clamped ends and a degenerate range.
- **The 画布缩放 subpage header keeps its number**, as the owner asked, because the slider
  is being dragged right underneath it there. Every other page header is unchanged.
- The silhouette is the requested 淫纹-style mark: a spade flanked by two mirrored
  crescent wings, crowned by two dots and closed by a third below the tip, authored as
  one path so a single clipped rectangle fills all of it. It was rasterised in Python
  before being written as Kotlin, because a wrong vector cannot be seen until it is on
  the phone.
- Reset rows (`恢复 100%`, `R.string.wall_reset_percent`) were added below the slider on
  封面密度, 画布缩放, 环境光效 and 动态效果. All four default to 100, which is
  `DefaultWallPercent` in `FocusWallViewportState.kt` and is deliberately the same number
  the two storage defaults use. 海报叠色 has no reset row on purpose: its default is 0,
  which is already the slider's left end.

**2. The settings destination is a list of groups, not one page of appearance.**
`SettingsContent` became `SettingsScreen`: a page header, then 外观与样式 and 资料库 as rows
that expand in place — one open at a time, nothing open on arrival. The appearance
controls are unchanged inside their group; 资料库 carries 选择资料库根目录 and 刷新当前文件夹,
which the settings surface previously could not reach at all. `appearance_title` is gone,
since the group row's own label replaces it.

This is the **accordion** reading of the owner's 收纳起来. The alternative — appearance as
a separate subpage — was offered before implementation and not chosen, so if the intent
was the other one this is the thing to redirect.

**3. The global accent reaches the top bar.** `AsmrPlayerApp`'s top bar now sets
`titleContentColor`, `navigationIconContentColor` and `actionIconContentColor` to
`MaterialTheme.colorScheme.primary` — the title, the back arrow and the refresh/folder
actions, which are exactly the three the owner named. The container colour is untouched,
so the bar still follows the theme surface in both light and dark.

- **The immersive player bar is deliberately excluded.** Its container is a fixed
  `Color(0xFF090A0D)` that ignores the theme's light and dark surfaces, and the accent's
  light-theme variant is dark (`#00639A` for Ocean) and would be unreadable on it. A
  half-applied accent looked worse than a clearly scoped one, so that bar stays neutral.
  Veto this if the player chrome should follow the accent too: doing it safely needs a
  per-theme accent channel the player bar can ask for, not the light one.
- `UiAccentPalette.accent(darkTheme)` and the new `accentContentColor` moved from `private`
  to `internal` in `ui/theme/Theme.kt`, and the settings swatches now call them instead of
  the duplicated `previewColor()` they carried. A swatch therefore shows the colour the app
  will actually use in the current theme, and the five palette values exist once.

Verification on 2026-09-26, on the working tree that also contains Codex's uncommitted
appearance slice:

```powershell
$env:GRADLE_USER_HOME='C:\Users\LiuDeZhou\.gradle'
$env:TEMP='C:\jtmp'
$env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest lintDebug assembleDebug
```

- `testDebugUnitTest`: **118/118 passed**, 20 suites, 0 failures, 0 errors, 0 skipped. The
  baseline measured before this slice, on the same tree, was 113 tests in 19 suites, so
  the five new tests are all of `LevelFractionTest`.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors, 16 warnings and 1 hint. The 12 warnings
  recorded above became 16 because the ambient slice added four `ambient_strength_*`
  strings nothing references yet. No entry in the current report points at a line this
  slice added; the one hint (`FocusCoverWall.kt:733`, autoboxing) is on the pre-existing
  `WallPercentPanel` state.
- `assembleDebug`: `app-debug.apk`, 74,623,684 bytes, sha256
  `eddc027a7bb9ff061a591f6ac848301ea593d2782e2a979232c5ee226a668e18`, under
  `D:\c\Documents\ChatGPT\.asmrplayer-build\app\outputs\apk\debug\`. That directory is
  overwritten by every build, so the hash identifies this build only. The APK is the
  owner's to install.
- The owner installed this build himself on 2026-09-26; this agent did not drive `adb`.
  The three points below are therefore on the device but not yet accepted by him, and
  the same build is also the first one to carry phases 1 and 2 of the Folia
  presentation roadmap, whose acceptance lists in `docs/FOLIA_PRESENTATION_PLAN.md`
  have likewise never been confirmed.

### What this slice still needs from the phone

- The crest at 20 dp: whether the silhouette is recognisable at that size and whether a
  bottom-up fill reads as a gauge rather than as a partially coloured icon.
  `CrestLevelIcon`'s `size` and its path constants are the only knobs and no test covers
  them.
- Whether 外观与样式 / 资料库 is the right split and whether expanding in place is what
  收纳起来 meant.
- Whether an accent-coloured title on the top bar is too strong next to the neutral
  container.

### Open question flagged, not changed

`resetAppearanceDefaults()` — the 恢复默认显示设置 button, which after this slice sits
visibly inside the group named appearance — also rewrites every cached folder snapshot's
`canvasZoom`, `wallDensityPercent`, `wallFocusShapeOrdinal`, `wallFocusSizeOrdinal` and
`wallAmbientPercent` (`LibraryViewModel.kt:342`). Those are per-folder browsing state, so
an appearance reset silently discards every folder's wall viewport. That predates this
slice and was left alone; the grouping change only makes the scope mismatch easier to
reach. Whether the wall viewport should survive an appearance reset is the owner's call,
and it is the same global-versus-per-folder overlap that `AppPreferences` and
`FolderSnapshot` both storing density, shape, size, ambient and zoom already creates.

### Claude slice: the timed-text foundation and a lyrics surface

Roadmap phase 3 in `docs/FOLIA_PRESENTATION_PLAN.md` is implemented in the working
tree: LRC/SRT/VTT are matched, read, parsed and cached, and there is a lyrics surface
to prove the timing with before any visual layer exists. Phases 4-6 consume this
model and were not started.

**Matching.** `core/subtitles/SubtitleMatching.kt` matches a same-folder `.lrc`,
`.srt` or `.vtt` to an audio by base filename, case-insensitively, with an optional
language suffix. It is called from `SafDirectoryScanner` and the result is carried as
`DirectoryListing.timedText`, keyed by `LibraryItem.id` rather than by title -- the
id is the handle the player already holds, and the earlier title-keyed map needed a
re-keying step whose semantics this agent got wrong once.

- A trailing segment is a language tag only if it looks like one (two or three
  letters plus optional subtags) and is not a version marker. `mix`, `alt`, `new`,
  `old`, `dat` and `bak` are peeled off and the search **continues** past them, so
  the owner's corrected `Track.new.lrc` still reaches `Track.mp3` while recording no
  language. This was a real correctness bug found by reading the first version back:
  the rule as written first made its own KDoc false and locked such files out
  entirely, which is the exact case the markers exist for.
- `Track.Part.lrc` is neither a tag nor a marker, so the search stops and it matches
  nothing. `Track.zh.en.lrc` carries two language tags and is likewise refused rather
  than guessed at. An untagged file beats a translated one, `zh` beats another
  language whatever order the folder listed them in, and `lrc` beats `srt` of the
  same name.
- The scanner recognises a lyric file before the audio test, so a `.lrc` can never be
  listed as a track, and the candidate list is bounded by the same `retainedItemLimit`
  the folder's own items are, so a 500-track folder with 500 lyric files does not
  retain more subtitle rows than it retains tracks.

**Model and parsing.** `core/subtitles/TimedText.kt` holds the normalized model:
`TimedTextLine(startMillis, endMillis, text, words)` with a line level and an
optional word level, `TimedTextDocument(format, lines, untimedText)`, and the pure
reads on top -- `isTimed`, `lineIndexAt` (binary search; -1 before the first line
starts), `lineAt`, `seekMillisForLine` and `neighbourhood(centre, radius)`.
`TimedTextParser.kt` produces it from bytes of any of the three formats.

- **An LRC with no time tags is reported as untimed, not given invented times.**
  Its lines land in `untimedText` and stay one line each, so the surface can say
  plainly that it cannot follow this file. Spacing the lines evenly would have made
  a lyric sheet look synced while being wrong for its whole length.
- Enhanced LRC word times (`<mm:ss.xx>`) are parsed into the word list; `[offset:]`
  shifts every line; a line with no tag of its own continues the previous line's
  words; `[ti:]`/`[ar:]`-style tags are metadata rather than times.
- SRT and VTT accept `HH:MM:SS,mmm`, `HH:MM:SS.mmm` and the hourless `MM:SS.mmm`,
  with the fraction scaled by its own digit count so `.5`, `.50` and `.500` are all
  half a second.
- One real bug, caught only because the tests existed: `String.split(String)` in
  Kotlin splits on a **literal**, so the `"\n\\s*\n"` cue separator matched nothing
  and every SRT was parsed as one concatenated line. It is a `Regex` now.
- **A bare time tag is a pause, not an empty lyric line.** Real files mark a pause
  with `[00:28.34]` and nothing after it. Taken as a lyric line each one is an empty
  row with nothing to read and nothing to seek to. Measured against the owner's own
  library rather than imagined: a sampled file had 61 of them in 285 non-blank lines,
  and 3,034 of its 3,774 `.lrc` files contain at least one. They now produce no line
  at all, so the previous line stays current through the silence -- which is what the
  pause means. Only visible by pointing the parser at real files; no generated fixture
  had ever contained the pattern.

**The device crash: the unit tests are blind to Android's regex engine.** The first
build of this slice was installed and **crashed the moment the lyrics surface opened**,
on every track, with:

```text
java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 8
\{[^}]*}
  at com.example.asmrplayer.core.subtitles.TimedTextParserKt.<clinit>(TimedTextParser.kt:303)
```

Android compiles regexes with ICU. ICU rejects an unescaped `}` as a syntax error
where the JVM's `java.util.regex` reads it as a literal. So `AssOverride`'s
`\{[^}]*}` compiled and passed every test on the JVM and could not load on the device.

- This is the one class of defect this slice's test suite structurally cannot catch:
  the tests run on the JVM, the app runs on ICU. 193 green tests and a crashed app
  were both correct.
- The `<clinit>` line number localised it: static initialisers run in source order, so
  the failure at line 303 proved lines 27-302 had all been accepted by ICU.
- Fixed by escaping the literal: `\{[^}]*\}`. Two other patterns in the same file had
  unescaped `]` (`?]` and `([^]]*)]`), which ICU happens to tolerate but which are now
  escaped too. A comment on the pattern records why, because escaping is the only thing
  keeping the two engines in agreement.
- All nine regexes in `app/src/main/java` are in `core/subtitles`; the rest were
  already escaped.
- **Verification that matters for this class of bug is on a device, not in the suite.**
  See the device run below.

**Reading and caching.** `TimedTextReader` decodes in the order BOM strip, strict
UTF-8, strict GBK, then lenient UTF-8, and refuses anything over 2 MiB. `TimedTextCache`
is the same bounded access-ordered LRU the folder snapshots use, at four entries,
keyed by `TimedTextKey(documentUri, sizeBytes, modifiedAtMs)`. Both the fresh-key and
the stale-key rewrites have tests.

**The lyrics surface.** `feature/subtitles/SubtitleDetailState.kt` folds what is on
screen into one of `Loading`, `NoFile`, `EmptyFile`, `Timed` or `Untimed`, and
`ui/SubtitleDetail.kt` draws it under the existing immersive player chrome -- the
artwork backdrop and its two colours moved from `private` to `internal` in
`PlayerDetail.kt` rather than being copied. Following, tapping a line to seek, and
the return-to-current button all work against real playback position.

- Lyrics state is a **separate stream** from `LibraryUiState`, because browsing
  replaces the whole library state on every folder change and the playing track's
  lyrics have to survive that.
- Reading happens on request rather than as a pre-built index: the current listing's
  `timedText` map, then the cache, then the file, parsed on `Dispatchers.IO`.
- The surface always shows the lyrics of whatever the player is on, so a queue
  advance while it is open loads the next track rather than leaving the previous
  track's words on screen.
- If playback is not on the item being viewed, the lines are shown without seek
  actions and with `subtitle_not_current`, rather than highlighting lines against
  another track's position.
- A file's name and language are shown from the moment the match is chosen, not only
  once it parses, so a file that fails to read still says which file it was.

**The one deliberate departure from the spec text.** `PRODUCT_SPEC.md` says the
current line stays *centered*. This implementation anchors it three lines below the
top instead, which keeps the upcoming lines visible and does not move the list once
per line. Exact centring is a visual decision, so it is left for the phase-4 visual
layer to make with the scene in hand; `LyricsFollowing.LeadInLines` is the single
knob.

Verification on 2026-09-27, on the working tree that also contains Codex's
uncommitted appearance slice:

```powershell
$env:GRADLE_USER_HOME='C:\Users\LiuDeZhou\.gradle'
$env:TEMP='C:\jtmp'
$env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest lintDebug assembleDebug
```

- `testDebugUnitTest`: **194/194 passed**, 27 suites, 0 failures, 0 errors, 0 skipped.
  The baseline on the same tree, before this slice, was 118 tests in 20 suites -- the
  count the appearance slice recorded -- so the seven new classes are all 76 of the
  new tests: 21 matching, 17 LRC/parser, 10 model, 7 decoding, 7 cache, 8 surface
  state and 6 following. This suite passed 193/193 on the tree that crashed on the
  device, which is the whole point of the section above.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors, 16 warnings and 1 hint -- the same
  counts as before this slice, and no entry points at a file or line this slice
  added. Both problems the first version introduced were removed rather than
  accepted: the `UseKtx` warning from a new `Uri.parse` call, fixed by using
  `toUri()`, and a literal U+FEFF character in the parser, which lint reports as a
  `ByteOrderMark` **error**, now written as the `"\uFEFF"` escape. Every new string
  is referenced, so there is no new `UnusedResources` entry.
- `assembleDebug`: `app-debug.apk`, 74,758,295 bytes, sha256
  `7d1dccb0578eb7fb92feec6f6a95122a518376a37c004ef91c3e6a2749eef7e8`, under
  `D:\c\Documents\ChatGPT\.asmrplayer-build\app\outputs\apk\debug\`. That directory is
  overwritten by every build, so the hash identifies this build only. The APK is the
  owner's to install. **This hash supersedes `4985c9e3...`**, which was the same size
  but carried the crash; only the hash separates them.

### Device verification of the lyrics surface, 2026-09-27

The first build was installed by the owner and **crashed**; the fixed build was
installed by the owner and **works**. He reported "我装了验证，功能没问题".

An earlier round of this session was driven by this agent over `adb` at the owner's
explicit request, which is how the crash was found rather than guessed at:

- The device is `23078RKD5C` (Redmi, `corot`), serial `VSFUUC4XLNSKHMRW`; `adb` is at
  `F:\AndroidSDK\platform-tools\adb.exe` and is **not** on `PATH` in this shell.
- Git Bash rewrites `/sdcard/...` into a Windows path before `adb` sees it. Prefix
  device-path commands with `MSYS_NO_PATHCONV=1` or they silently address
  `D:/git/Git/sdcard/...`. Screenshots are binary-safe through
  `adb exec-out screencap -p > file.png`.
- The library root is persisted in
  `files/datastore/library_preferences.preferences_pb` and readable with
  `run-as com.example.asmrplayer` (the app is debuggable): the root is
  `primary:Download/pikpak/要上传mp3后续在上传pikpak`.
- **The owner's real library is the best fixture this feature has.** It is where the
  bare-time-tag pattern and the `.en.lrc` suffix pairing both came from, and neither
  had ever appeared in a generated test. Matching was confirmed against real names:
  `X.mp3` beside both `X.lrc` and `X.en.lrc` resolves to the untagged `X.lrc`, as
  designed.
- Navigating the wall over `adb` is unreliable as a test method: the wall reflows
  between the focus tap and the activate tap, so a coordinate aimed at one cover
  landed on a neighbour. Reaching a named track is better done by the owner.

Those two questions now have an implementation answer in the Codex phase-4 slice
below, but still need the owner's visual acceptance on the phone.

### Codex slice: lyrics on the focused/current cover

Phase 4 is implemented in the current working tree without adding another parser,
subtitle cache or playback timer:

- `AsmrPlayerApp` chooses one subtitle request. The full-screen reader wins while
  open; otherwise a wall requests the item only when the selected layout is a wall
  and `focusedItemId == PlaybackState.mediaId`. Moving focus, advancing the queue,
  leaving the wall or opening another surface clears the old state immediately.
- `feature/subtitles/CoverLyrics.kt` is a pure bounded projection. It asks the
  shared `TimedTextDocument` for the line at Media3 position and returns at most
  previous/current/next. Before the first timed line, for an untimed document, or
  without a readable match, the cover scene is absent rather than invented.
- `FocusCoverCard` mounts that scene only for the focused current audio. Bento keeps
  the earlier compact centred scene. Folia has a separate measured poster chrome in
  `FoliaPosterChromeSpec`: the status/title are upper-left, the current line's own
  centre is at 50%, and context centres are exactly equidistant at
  35.5%/64.5%, controls sit near 88% and a thin progress line sits near 95%. Those are
  proportions of the focused card, not fixed phone pixels. The current font factor is
  0.070 (down from 0.094), with a 12--30 sp safety clamp. Four pure tests pin line
  ordering, exact symmetry, insets and typographic scale.
- The focused palette already decoded for the border and ambient light also drives
  a pure 36%--72% local shade: the brightest representative colour decides how
  much black the text needs. The bitmap itself is not blurred. A dark cover keeps
  more artwork; a bright cover receives more protection.
- Folia replaces the large type badge, filled 48 dp play button and Material slider
  with the reference's tiny status tag, small lower-left icons and a hairline seek
  track. Every shown icon is live: play/pause, available previous/next, direct lyrics
  detail and clear focus. The seek line has a larger invisible drag band and progress
  semantics. No fake repeat/queue icons were added while those destinations do not
  exist.
- Ordinary Folia cards no longer fall through to the generic wall card. They use
  the same 2 dp corner and subdued poster shade, a proportional lower-left title and
  metadata, and a tiny square folder/audio discriminator. They deliberately omit the
  generic round type icon, now-playing pill, lyrics and transport controls.

Pure coverage in `CoverLyricsTest` verifies the bounded three-line window, edge
clamping, absence before timing/for untimed text, adaptive shade ordering and the
empty-palette fallback. The first verification run on 2026-09-27 completed:

```powershell
$env:GRADLE_USER_HOME='C:\Users\LiuDeZhou\.gradle'
$env:TEMP='C:\jtmp'
$env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest lintDebug
```

- `testDebugUnitTest`: **203/203 passed**, 29 suites, 0 failures, 0 errors, 0 skipped.
- Kotlin compilation succeeded with only the same three deprecation warnings
  already present in `AsmrPlayerApp.kt` and `PlayerDetail.kt`.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors and 16 warnings, matching the
  pre-slice warning count; no warning points at a file added by this slice.
- `git diff --check`: clean apart from Git's informational LF-to-CRLF notices.
- No APK was installed or app launched; per the owner's standing instruction, he
  runs the build unless he explicitly asks the agent to do so.

Real-device review remains mandatory. Use a bright-cover track with a timed file,
focus it, start playback and inspect square plus portrait/landscape/full-cover
heroes. Confirm the Folia title, lyric tiers, icon row and progress line match the
reference's relative positions and remain usable; confirm the shade is sufficient,
context does not collide with transport and focus/track changes never leave stale
words. Android regex behavior was not changed in this slice, but the existing ICU
warning still applies to any future parser edit.

### Codex slice: focus-wall frame-path optimization

The owner reported that the accepted visuals felt low-frame-rate and asked for an
optimization that changed no feature. The current working tree therefore keeps the
same layout, focus policy, lighting, lyrics, seek/playback actions and transitions,
but removes avoidable work from the wall's hot paths:

- `FocusCoverWall` wraps the changing `PlaybackState` in a stable state holder and
  passes stable identity/transport flags to `FocusWallCanvas`. Only the subcomposition
  of the focused current card reads the live value; ordinary cards no longer observe
  the 500 ms Media3 position ticker.
- Distance alpha now follows pan directly. The previous implementation handed every
  visible card a new alpha target on every drag frame, so every card continuously
  restarted a 260 ms tween. Only the discrete lights-out fraction is animated now;
  its final alpha and exemptions are unchanged.
- One `VisibleWallCard` calculation supplies culling, depth order, alpha and final
  placement. Screen coordinates and the `hypot`-based depth scale are no longer
  recalculated up to three times per card per frame. Existing depth draw order is
  preserved.
- Near-visible composition remains buffered, but drops from 0.75/0.35 viewport on
  the vertical/horizontal axes to 0.50/0.25. Exact scaled-card intersection checks
  remain, so this changes precomposition cost rather than visible content.
- Stable environment-light brushes and per-poster tint/shade brushes use
  `drawWithCache`. Each card's three overlay boxes became one cached draw node.
- Ordinary cards no longer allocate a Material `Surface`, tonal elevation and a
  3 dp GPU shadow. The tightly packed posters were already clipped/background-filled;
  the focused card keeps a 12 dp shadow and every clickable/semantic child remains.

Automated verification after the optimization:

```powershell
$env:GRADLE_USER_HOME='C:\Users\LiuDeZhou\.gradle'
$env:TEMP='C:\jtmp'
$env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest lintDebug
```

- `testDebugUnitTest`: **203/203 passed**, 29 suites, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors and the same 16 warnings.
- `git diff --check`: clean apart from informational LF-to-CRLF notices.
- No APK was installed and no app/device was started. Automated tests protect
  behaviour, not frame time; the owner must compare drag, inertial release, pinch,
  focus reflow, ambient transition and live-lyric playback on the same phone.

### Codex slice: immersive lyric visualizer V1

After the owner accepted the phase-4 cover lyrics and the frame-path optimization,
phase 5 was implemented in the same uncommitted working tree:

- `feature/subtitles/ImmersiveLyrics.kt` is the pure scene projection. It retains at
  most two previous and two next lines, reports no frame before the first line starts
  and finds the last enhanced-LRC word whose timestamp has begun. Five tests cover
  bounds, pre-roll truthfulness and word selection.
- `ui/ImmersiveLyricsScene.kt` is one full-screen Compose scene over the existing
  Media3 `PlaybackState`. One static blurred cover, the already bounded/cached cover
  palette and three cached gradients form the background. There is no infinite
  animation or audio-reactive loop. The palette coroutine belongs to the composition
  and is cancelled when it leaves.
- A line-index change cross-fades and moves in the seek direction. Its 380 ms ceiling
  is scaled by the persisted global motion percentage; 0% swaps immediately. Position
  ticks update word colour and the transport without retargeting the whole scene
  animation. Ordinary timed formats retain line timing and do not receive invented
  word timing.
- Portrait composes five lyric tiers. A short landscape viewport removes the two far
  tiers, reduces type and uses a smaller centre slot so lyrics do not overlap the
  persistent bottom transport. Every visible line seeks to its real timestamp; the
  slider provides precise seek and previous/play-next call the shared session.
- `AsmrPlayerApp` hides its ordinary top app bar while subtitle detail is open. The
  scene and every reader fallback now own a truthful back affordance. A timed document
  enters the visualizer only when the viewed item is the current Media3 item. Loading,
  missing, unreadable, empty, untimed and timed-but-non-current states retain the
  existing diagnostic/scrolling reader.

Verification on 2026-09-27:

```powershell
$env:GRADLE_USER_HOME='C:\Users\LiuDeZhou\.gradle'
$env:TEMP='C:\jtmp'
$env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest lintDebug
```

- `testDebugUnitTest`: **208/208 passed**, 30 suites, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors and the same 16 warnings.
- Kotlin compilation succeeded; the existing top-app-bar/ViewList deprecation
  warnings remain unchanged.
- `git diff --check`: clean apart from informational LF-to-CRLF notices.
- No APK was installed and no app/device was launched, following the owner's standing
  instruction. Phone acceptance is still required for bright-cover contrast,
  long-line wrapping, enhanced-LRC word colour, portrait/landscape spacing, precise
  scrub and transition feel at both 100% and 0% motion.

The owner subsequently answered "没问题" and explicitly approved moving to the
next phase, so phase 5 is accepted even though no agent-driven device run occurred.

### Codex slice: phase-6 registry, word glow and glass wall tools

The first phase-6 slice is implemented in the same working tree:

- `feature/subtitles/LyricsVisualizerMode.kt` is a three-entry registry with stable
  IDs and defensive ordinal restoration: Flow (the accepted centred scene), Poster
  (Folia-like left-aligned typography and a clearer cover) and Spotlight (one
  dominant line plus a next-line cue). A top-right button in the full lyric scene
  opens the selector, and the choice is persisted through `AppPreferences` and
  `LibraryUiState`.
- Each mode has a distinct composition, background treatment and transition: Flow
  moves vertically, Poster moves horizontally in seek direction and Spotlight
  fades/scales. All three reuse one `ImmersiveLyricsFrame` and one Media3 clock.
  Global motion 0% still swaps immediately.
- Settings gained a separate `歌词与可视化` group with persisted word-glow
  presentation. This first version was an enhanced-LRC-only switch; the following
  slice supersedes it with Off / Smart / Exact-only modes while preserving the old
  preference through migration.
- The wall's 42 dp bottom-right tools button is now a translucent glass treatment
  with a one-pixel light edge, internal highlight and restrained shadow. Its panels
  share the translucent edged material and scale/fade from the button's bottom-right
  origin; the button icon turns 45 degrees while open. All duration is derived from
  the global motion percentage.
- This is deliberately **not live backdrop blur**. Standard Compose blur affects a
  layer's own pixels, not what is behind it; capturing and filtering the moving wall
  would add GPU work to the gesture hot path that was just optimized. The current
  treatment is the safe visual approximation. True backdrop sampling remains an
  optional, capability-gated experiment after phone profiling.

Verification on 2026-09-27:

- `testDebugUnitTest`: **211/211 passed**, 31 suites, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors and the same 16 warnings.
- `LyricsVisualizerModeTest` pins ordinal restoration, unknown-value fallback and
  unique stable IDs. Existing immersive/timed-text/wall tests remain green.
- No APK was installed and no app/device was launched.

Phone review should compare all three modes on the same timed track, compare Off /
Smart / Exact-only glow, rotate during each scene, restart to prove persistence,
and inspect the wall tool material over both a bright and a dark cover. Judge whether
the translucent approximation is sufficient before authorizing a costlier live blur.

### Codex slice: Smart grapheme glow for line-timed lyrics

The owner questioned the enhanced-LRC-only restriction after reviewing Folia's
source. The restriction is removed without weakening D-013's source-truth contract:

- `LyricsWordGlowMode` replaces the Boolean with three stable persisted policies:
  Off, Smart (default) and Exact only. Existing `lyrics_word_glow=false` migrates to
  Off; true or an absent preference migrates to Smart. A new ordinal is validated at
  the feature boundary before entering `LibraryUiState`.
- `LyricGlowTimeline` is presentation-only. Exact enhanced-LRC word times always
  win. Smart line-only fallback splits text with Unicode `BreakIterator`, gives CJK
  graphemes equal weight, groups Latin words with a small length weight, keeps
  punctuation brief, uses 90% of the line interval for reveal and caps implausibly
  long plain-LRC gaps with a reading-time bound. The timeline carries Exact or
  Estimated explicitly and never mutates `TimedTextDocument`.
- The immersive scene draws two coincident text layers. The body moves from dim to
  completed white with the active grapheme in the cover-derived accent. A separate
  transparent layer carries a hot shadow plus a two-grapheme afterglow, so the light
  can be stronger without making glyph bodies heavy. Its alpha peaks and recedes
  over the active grapheme interval.
- Media3 remains the clock of record at its existing 500 ms app-wide rate. A
  composition-local scheduler wakes only at the next grapheme boundary or at a
  500 ms resync while the lyric scene is visible and playing; it does not make the
  wall, player or library observe a faster ticker. The glow layer is removed from
  accessibility semantics so the same lyric is not announced twice.
- Settings now explains that Smart estimation affects display only and presents all
  three policies as explicit radio choices. `resetDefaults()` returns to Smart.

Verification on 2026-09-27:

- `testDebugUnitTest`: **223/223 passed**, 34 suites, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors, the same 16 warnings and one existing
  hint. No report entry points at this slice.
- New coverage pins mode restoration, legacy-Boolean migration, exact-time priority,
  ordinary-LRC estimation, long-gap bounding, Exact-only refusal, state boundaries
  and a combining Unicode grapheme.
- `git diff --check`: clean apart from informational LF-to-CRLF notices.
- No APK was installed and no emulator/phone UI was launched, following the owner's
  standing instruction. Phone review remains necessary for shadow strength, very
  long lines, pause/seek resynchronization and whether 20/13/8 px glow radii feel
  Folia-like on the actual display.

### Codex slice: Smart grapheme glow on the focused cover

The owner noticed that phase-6 glow initially reached only the full-screen lyric
scene. It now also reaches the lyric current line on the one focused cover that is
the current Media3 item:

- `ui/GlowingLyricText.kt` is the shared two-layer renderer and next-boundary
  scheduler. `ImmersiveLyricsScene` delegates to it instead of retaining a private
  copy, so the full-screen and cover treatments cannot silently diverge.
- `CoverLyrics.coverLyricsGlowTimeline` resolves the bounded cover frame back to its
  matching source line, including exact enhanced-LRC words and the next line start.
  A text-identity guard prevents an outgoing `AnimatedContent` frame from borrowing
  the next line's timing during the cross-fade.
- Both Bento and Folia focused covers receive the persisted Off / Smart / Exact-only
  mode. Folia preserves its measured 50% vertical anchor, width, type scale and
  readability shadow; only the current line gains the moving body/reveal and hot
  two-grapheme afterglow. Context lyrics and ordinary cards stay static.
- The existing wall performance boundary is preserved: `FocusWallCanvas` still
  supplies live `PlaybackState` only when a card is both focused and current. No
  other card creates a glow timeline, scheduler or high-frequency state.

Verification on 2026-09-27:

- Focused-cover narrow test: **BUILD SUCCESSFUL**.
- `testDebugUnitTest`: **225/225 passed**, 34 suites, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errors, the same 16 warnings and one existing
  hint; no report item points at this slice.
- New tests pin Smart cover-timeline reuse, Off behaviour and the outgoing-frame
  identity guard.
- No APK was installed and no emulator/phone was launched. Phone review should
  compare Bento and Folia with Off / Smart / Exact-only, especially glow strength on
  a bright cover, pause/seek synchronization and long two-line lyrics.

### Claude review round: independent verification of the uncommitted Codex slice

The owner asked for a status review of everything Codex had written since the timed
text commit. Nothing was modified by this round; the findings are recorded here so
they survive the next handover.

Verification was re-run rather than read off Codex's own output:

- `testDebugUnitTest --rerun-tasks`: **225/225 passed, 34 suites, 0 failures, 0
  errors, 0 skipped**, with the result XMLs rewritten at 14:30 on 2026-09-27 -- so
  this is a genuine execution of the current sources, not a cached report.
- `lintDebug`: **0 errors, 16 warnings, 1 hint**, the same counts every slice since
  the appearance work carried.
- `compileDebugKotlin` executes cleanly; the only warnings are the three
  pre-existing deprecations (`centerAlignedTopAppBarColors` twice in
  `AsmrPlayerApp.kt`, `Icons.Filled.ViewList` in `PlayerDetail.kt`).

The performance and ownership claims were checked in the source, not taken from the
slice notes. All of them hold:

- Only a card that is both focused and current reads the live `PlaybackState`
  (`FocusCoverWall.kt:1350-1354`); every other card receives an inert constant, and
  the lyric overlay re-asserts `isFocused && isCurrent` at `:1734-1740`, so an
  ordinary poster cannot paint words.
- Dragging no longer retargets a tween per card per frame. The four animated
  geometry values are **world** placements (`:1318-1337`) while pan is applied
  through `graphicsLayer` translation (`:1406-1407`), so a drag moves cards without
  restarting an animation; distance alpha is computed directly (`:1348`).
- Per-card geometry is calculated once and reused for culling, z-order, depth and
  placement (`:1264-1291`); the prefetch buffer is 0.50/0.25 viewport (`:175-176`);
  ordinary cards carry no GPU shadow and the focused card keeps 12 dp (`:1411`).
- The immersive scene binds its one palette request to the composition
  (`ImmersiveLyricsScene.kt:116-118`), caches its gradients in `drawWithCache`, and
  contains no infinite animation.
- The grapheme scheduler wakes only at the next boundary or a 500 ms resync
  (`GlowingLyricText.kt:156-162`); Media3's 500 ms tick remains the only app-wide
  clock.
- The preference migration is correct: a stored ordinal wins over the legacy
  Boolean, `false` becomes Off, `true` or absent becomes Smart, and both new
  ordinals are validated at the ViewModel boundary before entering `LibraryUiState`.

Two findings, both minor, neither reproduced on a device:

1. **A fading-out line borrows the new position's word index.**
   `ImmersiveLyricsScene.kt:436-437` rebuilds the outgoing frame with the *current*
   `positionMs`, so under real enhanced-LRC word timing the previous line can light
   its last word during the 380 ms cross-fade. `CoverLyrics.kt:39` guards exactly
   this case with a text-identity check; the immersive scene does not. Cosmetic and
   brief, but worth a look before phase 6 is called done.
2. **Grapheme segmentation is a second JVM-versus-ICU surface.**
   `LyricGlowTimeline.kt:218` splits text with `java.text.BreakIterator`, which the
   JVM implements in the JDK and Android delegates to ICU. This is the same class of
   divergence that produced the `PatternSyntaxException` crash: the test that pins a
   combining grapheme proves the JVM's behaviour, not the phone's. The failure mode
   here is only a wrong glow step for emoji ZWJ sequences and similar, never a
   crash, but the distinction is worth keeping straight.

### Claude slice: the word glow becomes a continuous left-to-right wipe

The owner reported that the per-grapheme highlight was "很生硬，从一个字很生硬地跳到下一个字",
and supplied the approved Folia poster as the reference: the colour is covered on from
left to right across the line, rather than switched on one character at a time.

The cause was structural, not a matter of tuning. `snapshotAt` returned only
`(completedCount, activeIndex)`, so the body renderer could map a display character to
one of three fixed colours at best, and every grapheme boundary produced four
simultaneous discontinuities: the outgoing character dropped `accent -> resting@0.92`,
the incoming one jumped `resting@0.40 -> accent`, the hot-core/afterglow spans shifted
by a whole grapheme, and `glowAlpha` was hard-reset with `snapTo(0.24f)` before each
pulse.

- `LyricGlowTimeline.sweepAt` now returns `LyricGlowSweep(completedCount, activeIndex,
  fraction)` -- the same discrete pair plus progress through the grapheme being sung.
  `wipeFrontAt` turns that into `LyricWipeFront(fromOffset, toOffset, fraction)`, the
  two text offsets the front travels between. It aims at the *next* grapheme's start,
  so when exact word timing leaves untimed text between two words the gap is swept
  during the preceding word instead of teleporting at the boundary.
- `snapshotAt`, `LyricGlowSnapshot` and `nextBoundaryAfter` are deleted; no surface
  consumed a per-boundary wake-up any more. `LyricGlowTimingSource`, both timeline
  builders and every timing constant are untouched, so the time table and its four
  pinned assertions do not move.
- `ui/GlowingLyricText.kt` is now the dim base `Text` plus an offscreen-composited
  overlay. The overlay reuses the base's measured `TextLayoutResult` (handed over
  through `onTextLayout`) and paints the accent fill with two `clipRect` + `drawText`
  passes -- whole lines above the front, then the swept part of the front's own line.
  A `SrcAtop` gradient rect adds a short glowing head at the front, sized by
  `motionPercent`, so 0% motion degrades to a clean hard edge. Nothing rebuilds an
  annotated string per frame, the per-frame allocation is one `Brush` plus a
  three-field value object, and the front state is read only inside the draw lambda.
- `rememberLyricWipeFront` interpolates with `withFrameNanos` between Media3's position
  updates. Paused, or with motion at 0%, the front holds where the last update left it,
  and no loop runs at all when the line is not on screen.
- The old hot-core/afterglow spans are gone. They only read as light while a single
  grapheme is lit; once the swept side is solid accent they are invisible, and their
  per-grapheme shift was itself one of the hard steps.
- The public signature is unchanged, so all three call sites -- the Folia poster
  current line, the focused cover card overlay and the immersive scene -- are
  untouched. Bento, ordinary cards and context lines do not change.

The visual change the owner should expect: the sung side of the line is now solid
`accent` (the Folia violet) instead of returning to a near-white `resting@0.92`, while
the unsung side keeps today's dim `resting@0.40`. If that contrast is wrong, the one
knob is `UnsungAlphaFraction` in `ui/GlowingLyricText.kt`.

**The owner accepted the result on his own phone on 2026-09-27** -- "我自己测试了，没
问题" -- after installing the build below himself. The wipe is therefore closed as a
presentation change; the automated numbers that follow were the pre-install check, and
the phone, not the JVM suite, is what settled it.

Verification on 2026-09-27:

- `testDebugUnitTest --rerun-tasks`, run once more after the final source edit:
  **229/229 passed**, 34 suites, 0 failures, 0 errors, 0 skipped, counted from the
  result XMLs rather than the console.
- `lintDebug`: **0 errors, 16 warnings, 1 hint** -- unchanged from every slice since
  the appearance work, and no report item points at either modified file.
- `assembleDebug`, rebuilt after the last source edit: APK 75,125,239 bytes, SHA-256
  `0A59CBE28930D1F325E48212B47D540BED99EA01600F92493DDD6BF39DC3F168`. That directory is
  a mutable build output, overwritten by every build, and it is not byte-reproducible:
  repacking behaviourally identical sources moved the artifact by 53 KB. Treat the
  recorded hash as "which build the owner installed", not as a verification value.
- The plan predicted 230 tests; the count is 229 because the planned "two layout lines"
  case was folded into the bounds-invariant test rather than written separately.
  Nothing was dropped, and `LyricGlowTimelineTest` grew from 6 cases to 10.
- No APK was installed by this round and no emulator or phone was launched from here,
  following the owner's standing instruction. The owner installed that APK himself and
  confirmed the wipe on 2026-09-27.

The renderer itself still has **no automated coverage**: the test stack is junit4 plus
`room.testing` only (`app/build.gradle.kts:76-77`), with no Robolectric and no Compose
test dependency, so the draw path cannot be exercised off-device. The four new tests
plus one rewritten one cover the pure half -- continuity across a boundary, both
parked fronts, the untimed gap, and the offset bounds that the `TextLayoutResult`
lookups depend on. How the wipe actually looks, and whether it wraps correctly on a
long two-line lyric, needs the owner's phone.

### Where the next agent starts

`master` carries three commits: the timed-text foundation (`d187138`), the handoff
pointer (`be04f5f`), and all of Codex's phase 4/5/6 work (`64be907`, 29 files,
+4073/-212).

**This wipe slice is the commit directly on top of `64be907`** -- 9 files, +446/-170,
three sources (`feature/subtitles/LyricGlowTimeline.kt`, `ui/GlowingLyricText.kt`,
`LyricGlowTimelineTest.kt`) and six docs (`ARCHITECTURE`, `DECISIONS`,
`FOLIA_PRESENTATION_PLAN`, `HANDOFF`, `PRODUCT_SPEC`, `TASKS`), authored by Claude and
carrying no Codex work. It waited for the owner's own phone confirmation before being
committed, which is why the acceptance note above sits inside it. The only untracked
file is the pre-existing `bash.exe.stackdump`, which must stay untracked.

Reading order for whoever picks this up next:

1. `AGENTS.md`, then `CLAUDE.md`, for the shared rules and the split of authorship.
2. This file: "Current slice" through the two sections above.
3. `docs/TASKS.md` -- what is open, and the roadmap ordering.
4. `docs/DECISIONS.md`, especially D-013 (timed text), D-016 (visualizer modes and
   word glow) and D-004/D-012 (the wall).
5. `docs/FOLIA_PRESENTATION_PLAN.md` -- phase 5 is accepted; phase 6 includes the
   registry, glass tools and the wipe on both immersive and focused cover current
   lines. GPU/audio-reactive work still requires profiling.
6. `docs/ARCHITECTURE.md` -- the module seams and the JVM-versus-ICU testing bullet.
7. `docs/PRODUCT_SPEC.md`, the "Player and subtitles" section.

The safest next action is phone review of the new wipe at 100% and 0% global motion,
on one ordinary LRC and one enhanced LRC, in both the focused cover and the immersive
scene -- plus the three visualizer modes and the glass tool treatment, which are still
awaiting their first device look.
Do not add RuntimeShader,
audio-reactive loops or live wall backdrop blur before profiling. Also do not repeat
the parser failure: a green JVM unit suite says nothing about regex behaviour on the
device. Escape every literal brace and bracket, and treat a run on the phone as the
only proof.

## Completed slice: folder snapshots, Media3 playback, card transport, layouts

All four "Now" items in `docs/TASKS.md` are implemented in the working tree.

### 1. Bounded per-folder snapshot cache

New `core/storage/FolderSnapshotCache.kt`:

- `FolderSnapshot` holds the folder key/title, the scanned item list, the
  supported-item count and truncation flag, plus the last focused item id and the
  focus-wall viewport (`canvasZoom`, `panX`, `panY`, `wallDensityOrdinal`).
- `FolderSnapshotCache` is an access-ordered `LinkedHashMap` with a `capacity`
  (default `12`, `require(capacity > 0)`) that evicts the least recently used
  folder. `update` is a no-op returning `false` when the folder is not resident, so
  a late viewport callback cannot resurrect an evicted folder.

`feature/library/LibraryViewModel.kt`:

- `loadFolder(folderUri, forceRefresh)` gained a restore path: when the snapshot is
  resident it rebuilds `LibraryUiState` from the snapshot with `isRestored = true`
  and returns **without touching the SAF scanner**.
- Entering a *different* folder never inherits the previous viewport: a `folderKey`
  change emits a fresh state, so pan/zoom/focus start clean per folder.
- `onViewportChange` writes only to the cache and deliberately emits no state, so
  panning does not recompose the library on every frame. `onFocusedItemChange` and
  `onWallDensityChange` write to both the cache and the UI state because they are
  discrete.
- `refresh()` invalidates the folder and rescans; `selectRoot` clears the folder
  stack and the whole snapshot cache.

### 2. Genuine playback through AndroidX Media3

- `core/media/PlaybackService.kt`: a `MediaSessionService` that builds an
  `ExoPlayer` (`AudioAttributes.DEFAULT`, `setHandleAudioBecomingNoisy(true)`) and
  a `MediaSession`. `onTaskRemoved` stops the service only when playback is not
  pending and the queue is empty, so an active session survives a task swipe.
- `core/media/PlaybackConnection.kt`: a lifecycle-safe `MediaController` holder.
  `connect()` uses a `SessionToken` from `ComponentName(context, PlaybackService::class.java)`
  and `MediaController.Builder(...).buildAsync()` with a Guava listener on
  `MoreExecutors.directExecutor()`, guarding against a stale future after
  reconnect. `rememberPlaybackConnection()` connects on `ON_START` and disconnects
  on `ON_STOP`/dispose. `state` is a `StateFlow<PlaybackState>` published from
  `Player.Listener.onEvents`; position advances from a 500 ms ticker that only runs
  while `playWhenReady`. Commands issued before the controller is ready are queued
  as a single pending action and replayed on connect.
- `core/media/PlaybackState.kt`: an immutable transport snapshot (`isConnected`,
  `mediaId`, `title`, `isPlaying`, `playWhenReady`, `isBuffering`, `positionMs`,
  `durationMs`, `hasNext`, `hasPrevious`, `hasQueue`, `errorMessage`) with pure
  `hasDuration`, `progress`, `isCurrent(id)` and `positionForFraction(fraction)`
  helpers, plus `toggleCommandFor(state)`.
- `core/media/PlaybackQueue.kt`: `buildFolderQueue(folderItems, selected)` converts
  the current folder listing into a deterministic queue — the visible audio
  children in existing display order, starting at the selected entry. Child folders
  and audio without a document URI are excluded. A selected audio outside the
  listing plays alone rather than silently starting a different track. Returns
  `null` when nothing is playable.
- `AndroidManifest.xml`: declares `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS` and registers
  `.core.media.PlaybackService` with `android:exported="false"` and
  `foregroundServiceType="mediaPlayback"`. **No storage permission was added** —
  all media access remains through the persisted SAF document grant.

### 3. One playback state behind every surface

`ui/PlayerDetail.kt` and the focused audio card both read the same
`PlaybackState`; neither simulates progress locally.

- The full player derives `positionMs`/`durationMs`/`isPlaying` from the shared
  state when `playbackState.isCurrent(item.id)`, publishes play/pause,
  previous/next and seek, commits a slider scrub on `onValueChangeFinished`, and
  highlights the queue row matching `currentMediaId`. When the opened item is not
  the current one it says so (`player_not_current_item`) rather than showing fake
  zeros, and surfaces `playbackState.errorMessage` via `player_error`.
- `feature/focuswall/FocusCoverWall.kt` gained a `CompactSeekBar`
  (local `scrubFraction`, committed through `onSeekTo(playbackState.positionForFraction(fraction))`)
  and an explicit play/pause action on the focused card, shown only when that card
  is the current item. The tap rule as it stood at that point was: clicking the
  currently playing focused cover opens the full player, clicking any other cover
  starts it. Superseded — see "Focus-wall gesture policy" below; only the focused
  cover answers a single tap now.

### 4. Library layout modes

`feature/library/LibraryLayoutMode.kt` defines `DetailedList`, `CompactList`,
`Grid` and `FocusWall` with a `@StringRes` label, `next()` for cycling and a
clamping `fromOrdinal`. `feature/library/LibraryLayouts.kt` renders the three new
modes and the shared `LibraryLayoutSwitcher`; all new labels live in
`res/values/strings.xml`.

Switching layouts rescans nothing: `LibraryContent` in `ui/AsmrPlayerApp.kt` hoists
`FocusWallViewportState` **above** the `when (layoutMode)` block, keyed on
`state.folderKey`, and the selected layout ordinal is kept in `rememberSaveable`.
A `snapshotFlow` inside `LibraryContent` persists zoom/pan back through
`onViewportChange`, so leaving and returning to the focus wall restores the same
viewport.

`ui/theme/Artwork.kt` now hosts the single shared `artworkBrush(seed)` palette,
replacing three private duplicates.

## Immediate next work

### Codex review after the Claude implementation

Codex reviewed the committed implementation and found three transport-edge
issues that are fixed in the current working tree:

- The grid rendered a Pause icon for the current playing item but still invoked
  `onPlay`, replacing/restarting the queue. `LibraryGrid` now receives the shared
  `onTogglePlayPause` callback and uses it for the current item, matching both
  list layouts and the focus wall.
- `PlaybackService.onTaskRemoved()` stopped the service whenever the queue was
  paused. The agreed default is to retain the service and queue after a task
  swipe, whether playing or paused, so it now stops only for an empty session.
  `PlaybackServicePolicyTest` covers empty and populated queues.
- `POST_NOTIFICATIONS` was declared but never requested. On Android 13+ the first
  explicit Play action now requests it once per saved Activity state; playback
  proceeds whether the user grants or denies it.

The formal Gradle XML reports produced in Claude's ordinary terminal were read
back independently and contain 54 tests, 0 failures, 0 errors and 0 skipped.
Codex's restricted command environment still fails before project evaluation
with `java.io.IOException: Unable to establish loopback connection`, even with
`--no-daemon`; therefore the new review changes and the two added policy tests
still need these commands from Android Studio or a normal terminal:

```powershell
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
```

No APK was built or installed during this Codex review.

### Focus-wall V2 in the working tree

The owner's four current visual priorities supersede further Room integration for
this slice. Codex inspected Folia Major's open-source lattice design for concepts,
then implemented independent geometry rather than copying its AGPL templates or
source:

- `FocusWallLayoutEngine` now groups ten sorted items into a 12 x 10 logical
  block. Three original exact-cover templates, stable template selection and
  deterministic horizontal/vertical reflection produce large and small square,
  portrait and landscape cards without random state.
- Default card gutters are 2 dp. Card size now comes from the mosaic itself; the
  former centre-distance scale no longer opens large gaps between cards.
- Cards below 116 dp wide or 112 dp high switch to a compact treatment (smaller
  type/glyph, no secondary line) so the smallest gears do not overflow.
- Focusing an item switches only its block to a centred 6 x 6 hero template. The
  other nine identities re-pack into the surrounding frame, and Compose animates
  their centre and width/height transition over 320 ms. Other blocks do not move.
- Drag release still selected/snapped the nearest card, and tapping an unfocused
  card still focused it without playing, as `PRODUCT_SPEC.md` then required. Both
  were removed on the owner's next request — see "Focus-wall gesture policy".
- `CoverAccentLoader` decodes at most a thumbnail-sized version of the focused
  cover on an IO dispatcher, samples a quantised colour histogram and caches the
  result. The 2.2 dp border uses that colour at 76% alpha; failed/no artwork falls
  back to the existing deterministic placeholder accent. Non-focused cards incur
  no palette decode.
- Pure tests now cover exact deterministic geometry, mixed aspect families,
  non-overlap, local-only focus reflow, large-scene viewport bounds and colour
  sampling/transparent-pixel rejection.

The owner subsequently requested automated verification. The combined focus-wall,
playback-review and Room working tree now has **63/63 passing unit tests**, lint
reports **0 errors and 12 warnings** (seven future-surface strings plus existing
version/manifest/icon notices), and `assembleDebug` succeeds. No device was started
and no APK was installed. The Room work and focus-wall V2 are still uncommitted in
the same working tree and must not be discarded.

The APK is at
`D:\c\Documents\ChatGPT\.asmrplayer-build\app\outputs\apk\debug\app-debug.apk`.

**Correction, and a warning about this path.** This section previously recorded that
file as 74,159,084 bytes with SHA-256 `0EEB0BD5...`. That record had gone stale --
the artifact was replaced and no build in the session could be tied to it. The file
at this path is a single mutable output that every `assembleDebug` overwrites, so a
byte count and hash written here is only true until the next build. The authoritative
fingerprint for whatever is currently on disk belongs next to the change that built
it, not in this section. Debug APKs here are also not byte-reproducible, so treat any
hash as a fingerprint of one build, not of the source tree. For the current build,
see the verification under "Focus-wall gesture policy" below.

On the first requested verification, KSP rejected the old ASCII build directory
because it lived on `C:` while this checkout lives on `D:`. The init script now
defaults to the repository's ASCII-named parent directory
(`D:\c\Documents\ChatGPT\.asmrplayer-build` here), keeping generated sources on
the same drive while still avoiding the UTF-8/GBK test-worker path problem.
The Codex terminal also needed `TEMP` and `TMP` set to `C:\jtmp`; otherwise JDK 17
failed while opening the local selector used by Gradle's daemon connection. This
environment override is process-local and is not an application change.

Agent continuity is now explicit: the owner starts Claude manually, Codex does not
invoke it automatically, and both `AGENTS.md` and `CLAUDE.md` require a resuming
agent to inspect this handoff, the task board, Git status/log and the current diff
before editing. Keeping these two docs current is the handoff mechanism.

### Alternate Folia-style wall in the working tree

The owner approved the current focus-wall V2 but requested that it remain intact
while a second layout more closely follows the Folia v0.7.4 demonstration. The
new `FoliaWall` entry is immediately to the right of `FocusWall` in the layout
switcher; the original engine and its fixed centred-bento behavior are unchanged.

- `FoliaWallLayoutEngine` uses independently-authored 12 x 8 exact-cover blocks
  with twelve items, two base gears and deterministic block reflection.
- Focusing no longer applies one universal top/side/bottom frame. The selected
  item's original slot chooses the nearest hero quadrant; two original focus
  gears plus horizontal/vertical reflection yield eight stable arrangements.
  **Superseded** by "Folia focus grows in place, and a wall tools button" below:
  those eight arrangements were all block corners, and the hero now takes its
  position from the card's own slot instead.
- Remaining identities are assigned to the globally nearest open slots with a
  small aspect-ratio penalty. Only the selected block changes, so distant blocks
  stay fixed and motion remains understandable.
- Both wall modes reuse the same renderer, artwork-derived translucent border,
  gestures, focused-card controls and playback behavior. Each keeps a separate
  in-session pan/zoom state while the user switches between them.
- `FoliaWallLayoutTest` covers determinism, mixed shapes, non-overlap, distinct
  per-slot focus gears, local-only reflow and a bounded 40,000-item visibility
  query. `LibraryLayoutModeTest` locks the new mode immediately after the old one.
- The owner supplied `F:\41646818644-1-16.mp4` (11,973,324 bytes). This sandbox
  confirmed the file but could not decode it: FFmpeg is absent, the package
  download failed TLS validation and browser automation correctly rejected the
  local `file://` URL. Final motion/visual comparison therefore remains a phone
  review item rather than a claimed automated video comparison.

The narrow Folia/layout-mode run passed. The required full verification also
passes: **68/68 unit tests**, followed by `lintDebug` with **0 errors and 12
existing warnings**. Commands used on this host were:

```powershell
$env:TEMP='C:\jtmp'; $env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
```

### Room schema draft in the working tree

After the transport review commit, Codex started the next task without yet
connecting the database to Media3:

- `gradle/libs.versions.toml` and `app/build.gradle.kts` select stable Room 2.8.5,
  the Room Gradle plugin and KSP 2.3.6. AGP 9 built-in Kotlin is retained; kapt is
  not introduced. KSP exported the checked schema as
  `app/schemas/com.example.asmrplayer.core.database.AsmrDatabase/1.json`.
- `core/database/LibraryEntities.kt` defines indexed folder, audio, artwork and
  subtitle rows. Artwork stores references/cache identity and dimensions, never
  bitmap bytes. Missing media is retained through `isMissing`.
- `core/database/PlaybackEntities.kt` defines per-audio long-form progress, the
  singleton global restore point, durable queue order and time-point bookmarks.
- `LibraryDao` pages direct children with composite parent/sort indices and offers
  bounded-batch upserts. `PlaybackDao.replaceQueue` is transactional.
- `AsmrDatabase` is version 1 and intentionally has no destructive migration
  fallback.
- `shouldMarkPlaybackCompleted` codifies the 95%-or-last-30-seconds rule while
  preventing short fixture tracks from becoming complete immediately. Four pure
  tests cover the policy.

Room/KSP dependencies are now cached, the generated implementation compiles and
the schema export, tests, lint and APK build all succeed. The original percentage
test used a 100-second track, so its 94.999% sample also satisfied the independent
last-30-seconds rule; the fixture now uses a 1,000-second track and isolates the
95% boundary without changing production behavior. There are 63 passing test
methods in total.

```powershell
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
.\gradlew.bat -I tools\ascii-build-dir.init.gradle assembleDebug
```

- Inspect/commit the Room v1 schema draft with the focus-wall slice, or separate
  them deliberately. After that, connect
  `PlaybackService` to `PlaybackDao` for periodic progress and atomic queue
  persistence; the queue is still rebuilt from the folder listing on each play.
- Durable bookmarks: the full player's bookmark list is still local UI state only.
- Queue insertion. The focused card's "add to queue" affordance was removed
  because it was wired to an empty lambda; bringing it back needs a queue-insertion
  API on `PlaybackConnection` and the durable queue above, or an appended item is
  lost on the next play.
- Verify the first-play `POST_NOTIFICATIONS` request and the paused task-swipe
  behavior on the phone.
- Embedded artwork and recursive child-folder artwork fallback, so the documented
  artwork precedence is complete.
- Extend the bounded direct-folder scanner into a full-library scanner.

The earlier Claude implementation described before the "Immediate next work"
heading is committed on `master`, on top of Codex's `fbea40e` ("封面墙V1"):

- `1360e23` "Make SAF audio playable and finish the library surfaces" -- the slice
  itself, 28 files, +3133/-293.
- `461220e` "Record the commit and the missing Git identity in the docs" -- the doc
  correction, including this section.
- `053921a` "Correct the docs for the rewritten commits and shared author" -- first
  gave both agents one shared author.
- the tip commit reverses that again and gives each agent its own name.

That earlier baseline was clean apart from `bash.exe.stackdump`. The current
working tree intentionally also contains the later playback review, Room v1 and
focus-wall V2 described above; none of those newer changes is committed yet. The
crash dump remains unrelated and must not be staged.

### Focus-wall gesture policy: a single tap moves focus, dragging never does

The owner reported a product defect on both walls: "我一滑动就会聚焦在我停留的那个
封面" -- any drag re-focused whatever card the viewport happened to stop on. Two
coupled mechanisms caused it, and it was **not** a coding slip: the committed code
matched `PRODUCT_SPEC.md` and `DECISIONS.md` as written. Drag release called
`nearestPlacementToCenter` and re-focused that card, and
`LaunchedEffect(focusedItemId, placements)` then re-centred the canvas on it. So
every release reflowed a block and moved the viewport, which is exactly what made
the wall feel unscrollable. Fixing it therefore required changing the spec, not
just the code.

This went through two revisions. The first removed tap-to-focus entirely, leaving a
double tap as the only way to move focus and making a single tap act only on the
already-focused card. The owner then asked for the simpler rule -- "能正常拖动
（拖动时不改变聚焦封面），同时单击就能改变聚焦封面" -- and the final policy is:

- A single tap on a card that does **not** hold focus moves focus to it: the card
  re-packs its block around the hero slot and the canvas centres on it. Nothing is
  played or entered yet.
- A single tap on the card that **already** holds focus activates it: audio starts,
  the currently playing item opens the full player, a folder is entered.
- **No double-tap gesture is bound to the cards.** A quick double tap therefore
  reads as "focus, then activate" for free.
- Drag **release is inertial and free**: no snapping to the nearest card, no
  re-centring, no focus change. Panning never moves focus.

Worth noting for whoever reads this next: `strings.xml` still carries an orphaned,
unused `demo_hint` reading "点击卡片使其聚焦，再次点击执行操作" -- tap a card to focus
it, tap again to act. That is the rule above, word for word, left behind from the
fixture-data prototype. So this is the wall's originally designed interaction
restored, not a new one invented to patch the defect.

Implementation, all in `feature/focuswall`:

- `FocusWallInteraction.kt` holds one pure function,
  `resolveCardTap(focusedItemId, tappedItemId): FocusWallIntent`, returning
  `Focus` when the tapped card is not the focused one and `Activate` when it is.
  Both sealed-interface branches are genuinely reachable, so no dead branch is
  forced on the caller.
- `FocusCoverWall.kt` binds cards with plain `clickable(onClick)`, **not**
  `combinedClickable`. That is deliberate: binding `onDoubleClick` makes Compose
  hold every single tap back for the double-tap timeout (~300 ms) before delivering
  it, which is felt as lag on the wall's primary gesture. "Double tap to open" does
  not need the handler -- it is the same rule applied twice. This build is
  therefore also more responsive on a single tap than the double-tap revision that
  preceded it.
- `settlePan(...)` runs on release: `Animatable.animateDecay` with
  `exponentialDecay(frictionMultiplier = 1.35f)`. `nearestPlacementToCenter` and
  `directionalNeighbor` were deleted; with the release path gone they had no
  remaining caller.
- Because release no longer snaps, the pan became unbounded and a hard fling could
  throw the wall out of reach with nothing left to grab. `FocusWallLayout.kt` now
  exports `wallWorldBounds()`, `FocusWallWorldBounds`, `FocusWallPan` and
  `clampWallPan()`. **This bound is Claude's own addition, not something the owner
  asked for** -- it is a safety rail that keeps the viewport *centre* inside the
  wall's extent, and panning is completely free within it. The owner has not yet
  accepted or rejected it.
- `focus_wall_hint` now reads "拖动浏览 · 双指缩放 · 单击聚焦 · 再单击已聚焦封面执行操作".

Both wall modes share this code path: `FocusWallStyle.Bento` and
`FocusWallStyle.Folia` differ only in the layout engine, so the single gesture
change covers the Bento wall, focus-wall V2 and the Folia wall at once.

**A first action is a single tap again.** `focusedItemId` starts as null for a
folder with no cached snapshot, but `resolveCardTap` returns `Focus` whenever the
tapped card is not the focused one -- including when nothing is focused at all --
so entering a folder and tapping a cover focuses it, exactly as before the defect
was reported. The mid-round double-tap revision had made that tap a no-op, which
would have cost two gestures to play anything; the owner's second request removed
that cost. Playing an item is still two taps when it was not already focused (one
to focus, one to activate), but the second tap is now the same gesture, not a
different one.

`PRODUCT_SPEC.md` and `DECISIONS.md` (D-004) were updated to state the new policy,
since the old text was what the removed behaviour had been written to satisfy.

Verification of this change:

```powershell
$env:TEMP='C:\jtmp'; $env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest --rerun lintDebug assembleDebug
```

The first attempt failed to compile: `cancelAnimation()` is a member of
`AnimationScope`, not of the `Animatable` receiver that `Animatable.animateDecay`
hands its block, so `settlePan` now cancels the job it owns instead. The rerun
passes **76/76 unit tests, 0 failures, 0 errors, 0 skipped** (read back from the
JUnit XML under `.asmrplayer-build/app/test-results/testDebugUnitTest/`, not from
the Gradle console) and `lintDebug` reports **0 errors and 12 warnings** -- the same
twelve the previous slice recorded, so this change added none. The count is 76
rather than the double-tap revision's 77 because the two rewritten
`FocusWallInteractionTest` cases collapsed into one "focus, then activate" case.

The six new pure tests from the earlier revision still stand: world bounds
enclosing every placement in all four quadrants, absent bounds, the no-op without
bounds, the rail stopping the centre exactly at the wall's extent, the bound
scaling with zoom, and a degenerate zero zoom staying finite.

The APK carrying this policy is 74,175,884 bytes, SHA-256
`FDDB98B95FCD901ACAC46119AE763E6FAE4407ABD96EDBC347B4BC16B354AA47`, built
2026-09-25 21:17. **It has not been installed.** The owner installs APKs himself.

**Install history, so the two builds are not confused.** The build installed with
`adb install -r` at 2026-09-25 21:11:04 (device checksum
`7a5a5295e9564f04cbbc65461e88ebd8`) carried the **double-tap** revision, and is
therefore already superseded by the build above. It replaced the 20:16 build, which
predated any of this work and is what the owner was seeing when reporting the
defect; `firstInstallTime` stayed at 12:52:55, so app data including the persisted
SAF root survived, and no media was read, written or deleted.

Note for the next agent: the owner installs APKs himself and does not want `adb`
driven on his phone without asking, even for a read-only query like `dumpsys`. That
21:11 install was authorised in the moment ("装到手机上看看") and was already
complete when he said he would rather do it himself; do not treat it as standing
permission.

**What is still unverified.** The app has not been driven by hand on any of these
policies. The gesture rule, the pan bound and the tap-to-focus recentring are all
awaiting a manual pass on the device; a checksum only proves which build is
running, not that the behaviour is right.

### Folia focus grows in place, and a wall tools button

Two independent requests from the owner, done in one slice.

**1. The focused Folia cover no longer jumps to a corner.** This was not a
transient bug but the direct consequence of the design: `FoliaFocusTemplates`
held two written templates with the hero pinned at `FoliaSlot(0, 0, 6, 6)` and
`focusedSlotsByItem` mirrored them with `focusOnRight`/`focusOnBottom` derived
from the original slot's centre, so the hero could only ever land on one of eight
mirror combinations -- all of them block corners.

The replacement keeps the focus *shape* fixed (a 6 x 6 hero; the shape is the
configured focus shape and was never the variable) and instead derives the hero's
*position* from the card's own slot:

- `foliaFocusCover(heroX, heroY, variant)` builds an exact cover of the 12 x 8
  block: the hero, plus four complement regions -- top strip, bottom strip, left
  band and right band, where the bands span only the hero's six rows -- each
  allocated a share of the remaining eleven slots by capped largest-remainder
  (`allocateFoliaPieces`) and cut into integer cells by recursive guillotine
  halving (`splitFoliaRegion`). `variant` keeps the determinism and the
  per-block variety that `stableFoliaBucket(seed, 2)` already provided.
- `heroX`/`heroY` centre that shape on the card's original centre and clamp it
  into the block.
- The greedy nearest-neighbour assignment of neighbours to slots
  (`movementCost`) is untouched -- that is the "the shapes around it move" half,
  and it already worked.

Corner cards still focus in their corner, and the corner case regenerates the
same structure the hand-written template had, so there is no visual regression
there. The corner geometry was checked by hand before implementing: `heroX=0,
heroY=0` yields a right band of six 3 x 2 cells and a bottom strip of five, which
is what the old template produced.

Test changes in `FoliaWallLayoutTest.kt`:

- **Deliberately replaced, not deleted:** `focus reflows only the selected block
  and avoids one fixed top strip` is now `focus reflows only the selected block`.
  The first half still holds and is still asserted; the "not one fixed top strip"
  half described the old design's intent and is now false by construction, so
  those assertions were removed rather than left to rot.
- **New:** `the focused hero lands as close to the tapped card as its shape
  allows` computes, for each of 24 items, the best reachable hero centre over all
  21 legal origins and asserts the implemented centre is within 0.05 units of it.
  This is a discriminating bound, not a tautology: for a mid-block slot like
  `(4, 0, 5, 4)` the old code was 2.69 units off while the best reachable
  distance is 1.118.
- **New:** `focused heroes are no longer confined to block corners`.
- **New:** `every focus shape remains an exact cover of its block` -- recovers the
  cell grid from the placement rectangles and asserts 96 covered cells with no
  overlap for every focused item. This is the main safety net for the generator.
- One comment written during this slice was corrected before landing: block 0
  yields seven distinct origins but only **three** interior ones (`(4,0)`,
  `(3,2)`, `(5,2)`), not four. The threshold follows the corrected count.

**2. A floating tools button at the wall's bottom-end corner.** `FocusCoverWall`
now wraps its canvas in a `Box` and adds `WallToolsOverlay`, which floats above
the wall so it keeps its size and position under zoom and pan. It opens a small
`Surface` panel anchored to the button -- deliberately not a `ModalBottomSheet`,
which would hide the wall the action is about to move -- with a full-wall
transparent scrim behind it so a tap meant to close the panel cannot also land on
a card. Tools are modelled as a `WallTool` row list so later additions are one
more entry.

Its first entry, "聚焦当前播放位置", moves focus to the playing track. Focus and
playback are independent states, so the wall needed an explicit way back.

- `LibraryViewModel.notePlaybackOrigin()` records `folderStack` at play time.
  That is the only moment that knows both the listing and the track, and it needs
  no scan and no document-URI-to-path map. `MainActivity.onPlayItem` calls it
  before `playFromFolder`.
- `revealPlayingItem(itemId)` focuses directly when the item is already listed;
  otherwise it restores the recorded folder chain, loads that folder, and parks
  the focus in `pendingFocusItemId` for `applyPendingFocus()` to apply once the
  listing is published -- the load is asynchronous and would otherwise overwrite a
  focus set immediately.
- The snapshot-restore path publishes `isRestored = false` for such a jump. A jump
  is not back navigation, and restoring the saved viewport would have swallowed
  the one animation that brings the camera to the track.
- `canRevealPlayingItem(...)` gates the entry, and the answer is computed in
  `LibraryContent` so both walls see the same one. It is disabled when nothing is
  playing, and when the track is in another folder but no origin was recorded --
  which is exactly the case of a queue that outlived the process. A queue that
  cannot be traced back shows a disabled entry rather than a tap that does
  nothing. `MainActivity` passes `onRevealPlayingItem` as a lambda, so the
  discarded `Boolean` return is safe.
- Two strings were added: `wall_tools` and `focus_playing_item`.

Verification:

```powershell
$env:TEMP='C:\jtmp'; $env:TMP='C:\jtmp'
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest --rerun lintDebug assembleDebug
```

**83/83 unit tests pass, 0 failures, 0 errors, 0 skipped**, read back from the
JUnit XML under `.asmrplayer-build/app/test-results/testDebugUnitTest/` rather
than from the Gradle console. `lintDebug` reports **0 errors and 12 warnings** --
the same twelve as the previous slice, and the seven `UnusedResources` warnings do
not include either new string, so both are wired up. `assembleDebug` succeeds.

The APK is 74,209,472 bytes, SHA-256
`f1d0ba4cdefe2ca7ee3a60c329c9183c4005aa4b3892e4ab276ef807fa597ea5`, built
2026-09-25 22:08. **It has not been installed**, and no `adb` command was run.
This file is overwritten in place by every `assembleDebug`, so that size and hash
identify only this build; the path
`.asmrplayer-build/app/outputs/apk/debug/app-debug.apk` is a mutable output, not an
archive.

**Known trade-offs, accepted:**

- No global document-URI-to-folder map was built. A queue that outlived the process
  cannot be traced back, so the entry stays disabled in that case. This is
  deliberate; see `canRevealPlayingItem`.
- If the folder jumped back to has since been refreshed and the track is gone from
  it, the pending focus lands on nothing and the wall simply shows that folder. Rare,
  and accepted.
- The Bento wall was not touched. Its hero was already centred in its block, so it
  never had this problem, and the owner raised only Folia. It does get the tools
  button, since that is shared.

**Verified by hand by the owner on 2026-09-25**, on the phone: the Folia focus
shape now appears where the tapped cover already was with its neighbours reshaping
around it, and the wall tools button and its panel work. No defect was reported
from that pass. The pan bound was not part of it: the owner asked what it was for
and then accepted it as it is, so it is now a settled product decision recorded in
`docs/DECISIONS.md` D-004 rather than an open question. Note that the earlier
"Claude's own addition, not something the owner asked for" wording elsewhere in
this file is now history -- the acceptance is what stands.

**How this was committed, and why it is two commits.** The working tree had
accumulated several slices that had never been committed: Codex's Room schema
draft, focus-wall V2, the Folia wall and `CoverAccentLoader`, plus the gesture
policy above and now this slice. The owner asked for the record, then chose to
split it by agent so the one-author-per-agent rule survives.

The split is by file. Codex's commit `4d22e30` carries the Room/KSP wiring,
`core/database/`, `core/media/PlaybackCompletion.kt`, `core/artwork/`,
`ui/theme/Artwork.kt`, `FocusWallLayout.kt`, `FoliaWallLayout.kt`, their tests,
the `LibraryLayoutMode.FoliaWall` plumbing and `tools/ascii-build-dir.init.gradle`.
This commit carries `FocusCoverWall.kt`, `FocusWallInteraction.kt`,
`LibraryViewModel.kt`, `MainActivity.kt`, `ui/AsmrPlayerApp.kt`, `strings.xml`,
the README/AGENTS/CLAUDE notes and the docs.

Two consequences, recorded rather than papered over:

- **The file boundary is an approximation.** `FocusCoverWall.kt`,
  `FocusWallLayout.kt` and `ui/AsmrPlayerApp.kt` each hold both agents' work, and
  the interleaving is at line level, not hunk level: `ui/AsmrPlayerApp.kt` alone
  mixes the FoliaWall dispatch (Codex) with the reveal wiring (Claude) inside one
  hunk. Each file went to the agent whose work dominates it, so Codex's commit also
  contains the pan-bound helpers and the six pan-clamp tests that are Claude's, and
  this commit also contains the V2 renderer and the `FocusWallStyle` enum.
- **Codex's commit does not compile on its own.** `LibraryLayoutMode.FoliaWall`
  lands there while the exhaustive `when (layoutMode)` over that enum in
  `ui/AsmrPlayerApp.kt` lands here, because that file belongs to this commit. The
  dependency is genuinely circular -- every other assignment of the shared files
  breaks one of the two trees -- so no ordering fixes it; only hand-editing an
  intermediate state that never existed would, and that was not worth fabricating
  history. The tree at the tip of this commit is the verified one, and there is no
  remote, so the split remains local and reversible.

The commit that carries the code another agent wrote does not also carry the
`Co-Authored-By: Claude Code` trailer, so `git log` does not imply Claude co-wrote
it; `git log --author` separates the two agents as before.

### Wall artwork decodes at the size it is drawn

The owner asked why some covers go soft once the wall is zoomed. The cause was
structural, not a Coil defect. World units equal physical pixels at zoom 1
(`FocusCoverWall.kt`, `viewportWidth = maxWidth.toPx()`), so the engine's
placements are pixel sizes for an unmagnified canvas. Each card is composed and
measured at exactly that size (`Constraints.fixed(placement.width, ...)`) and is
then magnified by `graphicsLayer { scaleX = zoom * animatedWidth / placement.width }`.
`AsyncImage` sized its request from those layout constraints, so the bitmap was
decoded for the *unmagnified* card and every bit of zoom — up to 1.35 by the canvas
and another 1.16 by the large density preset — magnified a bitmap that was never
asked to be bigger. It stays soft because nothing ever re-decodes it.

Two new pure functions in `FocusWallLayout.kt` sit beside `focusWallDepthScale`:

- `artworkDecodeScale(zoom)` rounds up into one of three buckets
  (`1f`, `1.15f`, `MaximumCanvasZoom`). Bucketing matters: a pinch crosses a bucket
  a couple of times instead of re-decoding on every frame, and every bucket already
  used stays in Coil's memory cache.
- `artworkDecodeSize(widthPx, heightPx, zoom)` multiplies the layout size by that
  bucket and caps each side at `MaximumArtworkDecodePx` (1536).

`FocusCoverCard` now builds an explicit `ImageRequest` with that size and
`Precision.INEXACT`. Inexact is deliberate: it stops Coil upscaling a source that is
smaller than the request, so the card scales what the artwork actually has instead of
paying for an interpolated bitmap. The bucket is read in `BoxWithConstraints`'s
composition scope, not inside the canvas, because cards are placed by a layout pass;
a zoom read inside the canvas would never reach them.

**Recorded trade-offs.** The 320 ms focus transition still softens the cards that are
*shrinking* — the old hero slot is composed at its new, smaller size and scaled up
from it for the length of the animation. Keeping the previous decode size alive for
one transition would fix it at the cost of tracking that state per card; not worth it
now. And a cover whose source is smaller than the card cannot be rescued at all: the
request is inexact, so a low-resolution source stays soft however far the wall is
zoomed. Both are properties of the design, not defects to be chased later.

Verified: 87/87 unit tests (`ArtworkDecodeSizeTest` adds four: unmagnified decode,
never shrinking below the layout size, monotonic bucketing, and the cap), lint 0
errors with the same 12 warnings, `assembleDebug` produces a 74,209,472-byte debug
APK (SHA-256 `c245a454ff8806aba3a011f5265d402369fc6fbf998a428f7244afdfd8883817`).
Not installed and not driven by hand: the owner installs APKs himself, and sharpness
is a judgement only he can make on the phone.

### The Folia focus shape and size are settings

The owner asked for the focused cover to offer more than one shape and more than one
size. Scope is the Folia wall only: the Bento wall's focused block is a hand-written
`FocusedBlockTemplate`, so there is no generated cover there to parameterise.

`FoliaWallLayout.kt` no longer holds `FoliaHeroColumns` / `FoliaHeroRows`. In their
place are `FoliaFocusShape` (Square, Portrait, Landscape — Heart is the next slice),
`FoliaFocusSize` (Small, Medium, Large) and one table, `foliaHeroSlot`, mapping the
two to a rectangle. `foliaFocusCover`, `focusedSlotsByItem` and `layout` take the hero
dimensions as parameters, and `layout`'s new `focusShape` / `focusSize` default to
`Square` / `Medium`, which is exactly the 6 x 6 hero the wall shipped with. Every
existing layout test therefore still runs against unchanged behaviour and none of them
was edited except the helper that now passes the two parameters through.

**The size caps are the interesting part.** A preset is only legal if eleven
neighbours can still tile the rest of a 12 x 8 block, and that is a much tighter
constraint than it looks. The plan's starting table had an 8 x 8 large square, which
does cover the block — but it leaves 32 cells for eleven neighbours, and at the
origins a card in the middle of a block asks for, six of them come out at two cells or
less. The block then reads as one huge cover with slivers around it. The sizes were
chosen from a table of measurements rather than by eye: each preset was scored on its
smallest neighbour and on how many origins it still allows, and the largest square
that keeps every neighbour at three cells or more is 7 x 7. Final table, in cells:

| | Small | Medium | Large |
|---|---|---|---|
| Square | 4 x 4 | 6 x 6 | 7 x 7 |
| Portrait | 3 x 4 | 4 x 6 | 5 x 7 |
| Landscape | 5 x 3 | 7 x 5 | 8 x 6 |

Every preset still leaves at least twelve reachable hero origins, so "the hero grows
where the card already was" survives all nine.

**Two guards, because the failure mode was a crash.** `allocateFoliaPieces` starts
every region at one piece and stops when a region hits its cap, so a hero whose
complement cannot yield eleven pieces used to leave the nearest-pair loop with nothing
to pick and index `targetSlots[-1]`. `focusedSlotsByItem` now carries
`require(targetSlots.size >= itemCount)` with a message naming the hero, and
`foliaHeroXRange` / `foliaHeroYRange` keep a hero that fills the block's height (or
width) at least one cell from each edge, since such a hero has only the two side bands
left to hold its neighbours. No preset is that tall today; the rule is there so one
that is fails in the test rather than on a phone, and it is tested directly.

**Tests.** `FoliaWallLayoutTest` gained `every focus preset still lays out a whole
block without a hole`, which drives all nine presets through the engine for the first
twenty-four items and re-checks the pixel-level exact cover. `FoliaFocusPresetTest` is
new and goes at the cover itself: for every preset, every legal origin and both
variants it asserts twelve slots, the hero first and where it was asked to be, 96
cells covered exactly once, nothing outside the block, and no neighbour under three
cells; plus ascending preset areas, each shape's aspect, and the two edge rules.
`FoliaCellRect` / `FoliaHeroCells` and the two `internal` engine functions
(`heroCells`, `focusCoverCells`) exist for those tests — the same shapes the engine
uses, not a re-implementation of them.

Plumbing follows `wallDensityOrdinal` exactly: `FolderSnapshot.wallFocusShapeOrdinal`
/ `wallFocusSizeOrdinal` (memory-only LRU cache, ordinals only, no UI types),
`FocusWallViewportState.focusShapeOrdinal` / `focusSizeOrdinal` with `focusShape` /
`focusSize`, `LibraryUiState` and two ViewModel callbacks, `MainActivity`, and
`AsmrPlayerApp`. The wall's tools panel became two-level (`WallPanelPage`: Root / Shape
/ Size) with a back row and a check on the chosen option; the two setting rows appear
only for Folia. Size labels reuse `density_small` / `density_medium` /
`density_large`.

**Recorded trade-offs.** The shape and size are per folder, not global, because they
travel with the snapshot the density already lives in; a global preference would need
a second storage path for one setting. The presets stop short of the largest cover the
block could hold, for the reason above — the wall is a mosaic with a focus, not a
focus with a frame. And the choice is a per-folder override with no "apply to all
folders" action yet; if the owner wants one, that is a product decision, not a bug.

Verified: 93/93 unit tests (18 suites, 0 failures, 0 errors — `FoliaFocusPresetTest`
adds five and `FoliaWallLayoutTest` one), lint 0 errors with the same 12 warnings,
`assembleDebug` produces a 74,260,656-byte debug APK (SHA-256
`36c2cb6e9838e8308d2eefc82bae5e5ee8c17aedadc8a54b040fc4583a75215a`). Not installed and
not driven by hand: the owner installs APKs himself, and whether a 7 x 7 hero reads as
"large" next to a 6 x 6 one is a judgement only he can make on the phone.

One correction to how earlier slices recorded this. A debug APK's size and hash are
only meaningful from a `clean` build: two clean builds of the same tree here produced
byte-identical APKs, but an incremental `assembleDebug` after a source edit produced a
different, larger one from the same sources (74,904,758 and then 75,157,238 bytes for
code that only differed by a comment). Recording an incremental APK's hash as if it
identified the source revision was wrong; the numbers above are the clean build, and a
future reader comparing hashes should rebuild `clean` first.

### The heart, the circle, full cover, and a way out of focus

The four things the owner asked for in one message: "1、开始做心形状；2、做圆形状；
3、聚焦的封面右下角应该加一个图标（不用加文字）用来取消聚焦；4、能不能再多加一种形状，
即能完全显示音频封面内容的". This is the slice the section above ends by naming as
next; the shape table there now has six rows rather than three.

**Two of the new shapes are silhouettes.** The owner chose, when asked, "整块卡片排成轮廓
（与已定的心形一致）" -- the focused card is itself a pixel heart or pixel disc, made of the
block's cells, with the eleven neighbours tiling everything else in the block exactly.
None of them is a picture inside a cut-out outline. `FoliaFocusShape` gained `FullCover`,
`Heart` and `Circle` **appended** rather than inserted, because the ordinals are persisted
per folder and an insertion would silently re-shape folders that had already chosen one.

The two masks are hand-written and were verified before anything was built on them: an
independent cell count over both, of the hero, the eleven neighbour regions, duplicates,
missed cells and cells outside the block, in every combination. The heart is 8 x 6 at
block `(2, 1)`, 32 cells, with the 2 x 2 notch between its lobes; its complement is 64
cells in nine regions, five of them rectangles and four of them 1 x 3 or 3-cell Ls. The
circle is an 8 x 8 disc at block `(2, 0)`, 52 cells, with a 44-cell complement in six
regions. Four of the heart's regions and all of the circle's are genuinely non-rectangular,
which is why `FoliaPiece` grew its own `cells` list and stopped being a rectangle with a
box: the renderer, the allocator and the tests all read the cells a piece actually fills
rather than assuming `columns x rows`.

**A silhouette ignores the tapped card and the size preset**, and that is a deliberate
departure from the rule the rectangular heroes follow. A rectangle hero can be generated
at any legal origin from one set of formulas; a hand-written silhouette would need one
written cover per origin (and one per size). The owner's answer to "「完整显示封面」放在
哪里？" was "作为形状列表的第六项" -- and full cover turned out to be the cheap one: it is
the square preset with `ContentScale.Fit` instead of `Crop`, so it keeps the square's
footprint and the size control, and it is asserted equal to the square preset in the tests
rather than maintained as a second table. **That last part did not survive the owner's
next look at it** -- see "The full cover takes the cover's own shape" below, which is what
the shape does now.

**The third request is the unfocus icon.** `ClearFocusButton` is icon-only -- `Icons.Default.Close`
in a 38 dp translucent disc at the card's bottom-end corner, no label, as asked. It sits at
the end of the focused audio card's action row, which became `SpaceBetween` so it is pushed
to the trailing edge; a focused folder gets its own row holding only the button, since folders
have no other action. It removes focus, never the item, so there is nothing destructive to
guard and no confirmation.

**What the rendering needed.** `FocusWallCellShape` is the one new renderer: it builds a
Compose `Path` with `addRect` per cell and returns `Outline.Generic(Path().op(cellPath,
cellPath, PathOperation.Union))`, so `border` strokes the silhouette's own outline and not
the seams between its cells. `FocusWallShape` is normalised to the card's own 0..1 box, so
the same mask survives the 320 ms animation between two card sizes. Cards with no shape
keep the existing `RoundedCornerShape` path untouched.

**The allocator cap, and why it cannot change today's wall.** `allocateFoliaPieces` now caps
each region at `min(area, max(columns, rows), area / FoliaMinPieceCells)` for shaped covers
and leaves rectangular covers on the old rule, where `FoliaMinPieceCells` is 3. The proof
that this is a no-op for every existing preset is short: the cover test already asserts no
neighbour receives fewer than three cells, and a region of `n` cells can never be handed
more than `floor(n / 3)` such pieces. So the cap was already satisfied, and the owner-approved
phone behaviour is untouched.

**Tests: four added, several rewritten rather than deleted.** The `FoliaFocusPresetTest`
enumeration now runs all six shapes x three sizes x every legal origin x both variants, and
its coverage assertion moved from "the boxes sum to 96 cells" to a real cell set, because for
a silhouette the bounding boxes deliberately overlap -- that is what a pixel outline is.
`FoliaWallLayoutTest` gained the same treatment for the engine: a new `paintedCells` helper
decodes each placement back to block cells (a rectangle enumerates its box; a shaped card
reads its own cell rects) and `assertPaintedOnce` asserts the twelve cards paint all 96 cells
of the block exactly once, with no hole, no duplicate and nothing outside. `assertNoOverlap`
is kept on the rectangular presets and skipped when a silhouette is present, with the reason
written where the skip happens. Three new assertions go at the shapes themselves: both are
connected and mirrored about the block's centre line, the circle is symmetric top to bottom
with rows that widen to its middle and narrow after it, and the heart is not.

Three test-writing mistakes were caught by the tests failing, and they are worth knowing
about because they look like engine bugs:

- The circle's row-width assertion first read `assertEquals(widths.sorted(), widths)` and
  failed with `expected [4,4,6,6,8,8,8,8] but was [4,6,8,8,8,8,6,4]` -- the disc widens and
  then narrows, so only each half is monotonic.
- `paintedCells` first counted a rectangular card as one cell, giving
  `did not paint the whole block expected:<96> but was:<12>`; it now enumerates the box.
- `painted (9, 4) twice` was a mapping bug in the test, not the layout: an eroded cell's
  centre can land exactly on a cell boundary -- cell (5,6) spans `[5u + g/2, 6u]`, so its
  centre is 5.5056 cells and rounds to 6. Cells are now read from the rect's leading edges,
  with an assertion that each piece is exactly one cell wide and tall.

Two of the fixes above were in the *shape* helper, and the first attempt at the second one
was wrong in a way worth recording. `FoliaPiece.foliaShape` computed cell edges block-relative
while the card's own left/top included the block's world origin, so every rect was offset by
the block's place on the wall: `painted (14, 6) outside the block`. Adding `originX`/`originY`
parameters did not fix it, and a `--rerun-tasks` debug dump that came back byte-identical is
what proved the edit had not taken effect in the path being run. The correct fix keeps
everything block-relative inside the helper and drops the origin parameters entirely, with a
comment saying why they cancel: the shape is normalised to the card's own box.

Verified: **97/97 unit tests** across 18 suites, 0 failures, 0 errors, 0 skipped, read back
from the JUnit XML under `.asmrplayer-build/app/test-results/testDebugUnitTest/` rather than
from the Gradle console -- the baseline before this slice was 93. `lintDebug` reports
**0 errors and 12 warnings**, the same twelve as the previous slice, so this change added
none. `clean assembleDebug` produces a 74,295,032-byte debug APK, SHA-256
`1B8EC7F66246E33F8FD4733DDDEFEFCE6CD29FCCEA014D5B289AB2A74B4A5140`; an incremental
`assembleDebug` of the same tree produced byte-identical output, and the tests were re-run
after the clean build to confirm the count below rather than carrying it over. **Not installed and not
driven by hand** -- the owner installs APKs himself, and every question this slice raises is
one only an eye on the phone can answer:

- whether the heart reads as a heart at that size, and the circle as a circle, rather than as
  a lumpy blob with cards around it;
- whether a tap in a silhouette's bounding-box corner reaches the card *underneath* it. The
  hero's box overlaps its neighbours by design, and the expectation is that Compose's
  clip-aware hit testing sends those taps to the neighbour. If it does not, the fallback is an
  explicit pointer check on the box corners, and that has not been written;
- whether the unfocus icon is big enough and far enough from the card's own tap target;
- ~~whether full cover letterboxes a portrait cover readably inside a square card~~ -- the
  owner answered this by rejecting the square footprint entirely; see the next section.

### The full cover takes the cover's own shape

The owner's follow-up to the slice above: "完整封面的实现方式有些问题，不够优雅。你现在是
无论封面是矩形还是正方形，都是用一个足够大的正方形装著，边长等于封面图片的最长边就好，
可是这样呈现的有些臭，因为有的封面本身是矩形，边长不一定相等的。你懂我意思吗". A square
hero with the artwork fitted inside it letterboxes every cover that is not square, and the
bars are as wide as the cover is far from square. He asked for the card itself to take the
cover's proportions. When asked what to do about the mismatch that is left over, he chose
**完整显示优先**: never crop, show the whole cover.

**Two constraints decided the whole design**, and they are worth stating plainly because
every number below follows from them:

- A card is a whole number of the block's 12 x 8 cells, so it can only take the proportions
  the grid can express -- 5 x 7, 4 x 6, 8 x 5 and so on. There is no arbitrary scale here.
- The cover's proportions are *not in the file listing*. `SafDirectoryScanner` queries a
  document's id, name and MIME type and never opens an image, and `LibraryItem` has no
  dimensions. The shape can only be known after the artwork has been read.

So `CoverAccentLoader` grew into the thing that reads it. It already ran
`BitmapFactory.Options { inJustDecodeBounds = true }` to choose a sample size and then threw
the size away, so the intrinsic width and height cost nothing extra; `resolve` now returns
`CoverFacts(accentArgb, width, height)` instead of a bare `Int`. The size comes from the
*bounds*, not from the decoded bitmap, which is a downsampled copy -- a card cut to the
sample's proportions would be cut to whatever the sample size ladder happened to pick. The
accent stayed independently nullable, so a cover with no colour worth sampling still
reports its shape.

**Where the read happens was hoisted**: the focused cover is read once, at `FocusCoverWall`,
in a `produceState` keyed on the focused item's artwork URI. That was forced by the data
flow -- the hero's footprint has to be known while `FoliaWallLayoutEngine.layout` runs, and
the placement `remember` is keyed on the aspect -- and it simplified the card on the way:
`FocusCoverCard` no longer loads its own accent, it is handed `coverAccentArgb`, so the
focused cover is decoded once per focus instead of once per reader.

**The candidate table is measured, not derived.** A hero's complement has to yield eleven
neighbours of three cells or more, and past a certain width it does not: 10 x 5 and 9 x 6
leave a 1 x 2 or 2 x 1 piece, and from 11 x 6 up there are not eleven pieces to hand out at
all; an eight-row hero is not coverable at any width, because it fills the block's height
and eleven cards do not fit in the two side bands it leaves. So the usable rectangles are
`FoliaHeroMaxColumnsByRows = 12, 12, 9, 8, 7` for three rows through seven -- 45 rectangles.
That edge was found by a temporary test that enumerated everything and printed the reason
each rejection failed, run once and deleted; `FoliaHeroRectangles` is the table itself and
`FoliaFocusPresetTest` now re-derives every entry through the real cover generator, so a
change to the splitting or the allocator that moves the edge fails in the tests rather than
on the phone. `coverCellsForHero` was added to the engine for exactly that: the table's
rectangles are not presets, so nothing else could reach them.

**The selection rule is aspect first, size second.** Among the 45, the closest proportions
win; `FoliaFocusSize` narrows the search to areas within half to one and a half times the
area its square preset would have taken (16, 36, 49 cells), and inside that band the closest
proportions still win with the closest area as the tie-break. If even the band's best is
more than a tenth off, the band is dropped rather than the shape -- a card of the wrong
shape letterboxes the cover it exists to show, while a card of the wrong *size* is still a
mosaic with a focus in it. Consequences worth knowing:

- A square cover, and a cover that has not been read yet, get exactly the square presets,
  because 4 x 4, 6 x 6 and 7 x 7 are the best rectangles in their bands. That is what keeps
  the default and the first frame of every focus unchanged.
- The size control moves the card for nearly every cover shape: 10 of the 13 ratios
  measured grow from small to medium and 4 of 13 from medium to large.
- Two alternatives were measured and rejected. Picking by area band first costs up to 27%
  aspect error (a 9:16 cover at Large has no large tall rectangle, so it was offered 5 x 7),
  and picking by aspect alone with neither band nor tolerance makes the size control dead --
  for most ratios the block holds exactly one rectangle of that shape, so all three sizes
  returned the same card.
- The worst case left is a 16:10 cover at Large, which takes 4 x 7 at 9.4% off, and a 1:2
  cover, for which the block holds one rectangle within tolerance, so all three sizes give
  3 x 6. Both are asserted, the second on purpose, so the trade-off cannot change quietly.
- Whatever is left after fitting is already the card's own palette gradient --
  `artworkBrush(item.paletteSeed)` has always been painted under the artwork -- so no black
  bars were ever at risk.

**Tests: six added, one replaced.** `the full-cover shape is the square presets` asserted
the very thing the owner rejected and was replaced by four: the hero is the closest
rectangle to a spread of cover shapes and is always one the block can cover; a square or
unread cover keeps the square presets; the size preset still chooses the hero; and a shape
the block has no large rectangle for keeps the size of its shape (the 1:2 case). A fifth
re-derives the whole 45-rectangle table through `coverCellsForHero`, and a sixth pins the
table's shape. `FoliaWallLayoutTest` gained one that drives the real engine for a spread of
shapes, sizes and tapped cards, which is the path the preset test cannot reach: the origin
comes from the tapped card and the rule chooses the rectangle without knowing it. The cell-cube
assertions the preset test already had moved into an `assertCover` helper so both tests use
one.

Verified: **103/103 unit tests** across 18 suites, 0 failures, 0 errors, 0 skipped, read
back from the JUnit XML under `.asmrplayer-build/app/test-results/testDebugUnitTest/` rather
than from the Gradle console; the baseline before this slice was 97. `lintDebug` reports
**0 errors and 12 warnings**, the same twelve. A `clean testDebugUnitTest assembleDebug`
produces a 74,311,416-byte debug APK, SHA-256
`d329cdd98049b0b42ab3e69d44f5a2d1e2b92da6bc5ffe34cf4dcbe634538524`, and the tests in that
same clean run are the 103 counted above.

**The hash identifies a build, not a source revision**, and the previous slice's
"byte-identical" claim does not generalise. Two APKs of the same 74,311,416 bytes came out
of trees that differed only in comments and in whether one helper was `private` or
`internal` -- `d329cdd9...524`, then `665f879a...3ed` for the final tree. The size is
stable across both; the digest is not, so anything comparing hashes across builds should
compare the sources instead.

**Verified by the owner on the phone (2026-09-26)**, who installed the APK himself and
reported the result as good -- "我检查了，效果可以", no defect named. The three questions
this slice was written against, and which that look answers:

- whether a portrait cover at the three sizes reads as "the card is the cover's shape"
  rather than as a card that happens to be a bit narrower;
- whether the leftover edge is unobtrusive now that the bars are a few per cent rather than
  a couple of dozen;
- whether the 320 ms transition between two differently-shaped heroes looks like the card
  turning into the cover's shape or like a bounce.

The owner then moved his own work to Codex, so the next session of either agent should
resume from `ec2f7b4` rather than from this section alone.

### Git identity: one author per agent

`user.name` and `user.email` are unset both globally and in this repository, so a
bare `git commit` fails. That is deliberate. Two agents work in this repository, so
any repo-local default would silently stamp one agent's name on the other's commits;
a refusal is a bug report, a wrong author is not. Each commit names its own author
on the command line instead:

```powershell
git -c user.name='Codex'  -c user.email='codex@local'  commit ...
git -c user.name='Claude' -c user.email='claude@local' commit ...
```

`fbea40e` is Codex's and is untouched. The three commits that carry this slice are
Claude's, so `git log --author=Claude` and `--author=Codex` cleanly separate the two
agents. Both addresses are placeholders, not real mailboxes.

Those three were rewritten twice: once to move them off a per-command author onto a
shared one, then again to split that shared author apart. Their hashes therefore do
not match the ones first reported. No content changed either time -- every tree is
byte-identical to its pre-rewrite object, which is why the working tree stayed clean
across both rewrites.

The user's global Git configuration is still untouched and must not be changed
without explicit approval.

## Local environment

- Android SDK: `F:\AndroidSDK`
- Android Studio: `F:\Android`
- System JDK: Microsoft OpenJDK 17
- Android Studio runtime: JetBrains Runtime 21

## Known repository issue

Git reports dubious ownership because the repository owner SID differs from the
execution user. Use a command-scoped override when inspecting Git:

```powershell
git -c safe.directory='D:/c/Documents/ChatGPT/安卓音乐播放器' status
```

Do not change the user's global Git configuration without explicit approval.

## Verification: the recorded Gradle blocker is gone

Earlier handoffs recorded that every Gradle task failed before project evaluation
with `java.io.IOException: Unable to establish loopback connection`. **That no
longer reproduces, and the recorded diagnosis was wrong.** `./gradlew.bat`
configures and runs normally: `assembleDebug`, `lintDebug` and
`testDebugUnitTest` all execute.

### The real failure, and its fix

`testDebugUnitTest` did fail, but later and for a different reason: compilation
succeeded, then all 12 test classes failed to load with `ClassNotFoundException`.

Root cause, established by experiment rather than inference:

- Gradle 9.6.1 writes the test worker's classpath into a Java `@argfile` encoded
  as **UTF-8**.
- The `java` launcher decodes `@argfile` using **`sun.jnu.encoding`**, which is
  **GBK (codepage 936)** on this Simplified-Chinese Windows install.
- This repository's path contains non-ASCII characters (`...\安卓音乐播放器\...`),
  so every classpath entry was decoded as mojibake and no directory resolved.
- A direct probe confirmed the mechanism: the identical classpath in an argfile
  written as UTF-8 produced `ClassNotFoundException`, while the same content
  written as GBK loaded the class. `JAVA_TOOL_OPTIONS` and `JDK_JAVA_OPTIONS`
  cannot help, because the launcher parses the argfile before any option applies.

`tools/ascii-build-dir.init.gradle` redirects `layout.buildDirectory` to an ASCII
path, which removes every non-ASCII entry from the worker classpath (dependency
JARs already live under the ASCII `~/.gradle`). Run the mandatory commands as:

```powershell
.\gradlew.bat -I tools\ascii-build-dir.init.gradle testDebugUnitTest
.\gradlew.bat -I tools\ascii-build-dir.init.gradle lintDebug
```

Results with that init script:

- `testDebugUnitTest` -- BUILD SUCCESSFUL, **48 of 48 tests pass, 0 failures,
  0 errors** across 11 test classes. This is the first time these tests have run
  under Gradle; every earlier "48 of 48" claim came from hand-invoking
  `JUnitCore` against a hand-compiled classpath, not from the real task.
- `lintDebug` -- BUILD SUCCESSFUL, **0 errors**, 12 warnings (9 `UnusedResources`,
  1 `DataExtractionRules`, 1 `MissingApplicationIcon`, 1 `UseKtx`).
- `assembleDebug` -- BUILD SUCCESSFUL; the installable APK lands at
  `<asciiBuildDir>/app/outputs/apk/debug/app-debug.apk`.

The init script is a workaround for the launcher, not a product change: sources,
resources and the APK are unaffected. It becomes unnecessary once the repository
sits on an ASCII path or the machine enables the Windows UTF-8 system locale.

Local, Git-ignored test media is under `local-test-media/ASMR_Test`. It contains
17 OGG fixture tracks, eight generated PNG covers, nested folders, an empty
folder, root-level audio, and matching LRC/VTT fixtures. The audio is copied from
three CC0/public-domain originals; source and license links are recorded in
`local-test-media/SOURCES.md`. Fixture durations run from 2.57 s to 18.9 s, which
matters when exercising transport behaviour: a 2.57 s track advances to the next
queue entry before a screenshot can be taken.

## Device verification

Verified on a real Redmi (Android 15 / API 35 / arm64-v8a) against the user's own
SAF-selected library, and on `emulator-5554` (API 36 / x86_64) against the
generated fixtures. All media access is read-only and goes through the persisted
SAF document grant; no storage permission is requested and nothing was modified or
deleted on either device.

**Which build each device actually ran.** The two devices did not run the same
APK, and that matters when reading the list below. The phone's install was
`lastUpdateTime` 14:38:41; the two defects were fixed in source at 15:02:48
(`FocusCoverWall.kt`), 15:03:18 (`LibraryLayouts.kt`) and 15:05:48
(`strings.xml`). So the phone ran the **pre-fix** build for the whole of that
round -- it is what *found* the defects -- and the fixed build went only to
`emulator-5554`, which was updated at 15:06:51 from the 15:06:44 APK. Anything
below describing behaviour after the fixes was observed on the emulator, not the
phone.

That gap is now closed: the current APK (73,352,596 bytes, built 15:06:44) was
installed over the phone's existing build at 15:25:09 with `adb install -r`, which
preserves app data, so the phone's persisted SAF root is unchanged. A UI dump of
that build on the phone shows the focused folder card exposing exactly one action
-- the *enter* button at `[638,1670][972,1840]` -- and no play or queue
affordance, confirming both fixes are present in what the phone now runs.

Confirmed working:

- **Folder snapshot cache.** Navigating up restores the folder from the in-memory
  snapshot with the `library_restored_hint` notice and no rescan, restoring both
  the focused card and the wall density that folder was left with. Entering a
  *different* folder starts clean (density back to its default), so per-folder
  isolation holds.
- **Media3 playback.** A real `AudioTrack` at 44.1 kHz in `state:started`; the
  session reports `PLAYING`, the queue is built from the folder listing starting at
  the selected track, tracks auto-advance, and real duration is reported.
- **Focused-card seek bar.** The compact bar renders live position and duration
  from the shared state, and a real drag moved the player from 9007 ms to
  17578 ms on an 18903 ms track. The canvas gesture loop yields to it correctly --
  the interaction the previous handoff flagged as the likeliest regression.
- **Four layouts.** Detailed, compact, grid and focus wall all render the same
  sorted listing with real artwork; switching rescans nothing.
- **Focus wall geometry.** The focused hero's centre measured (610, 1553) against
  a wall viewport centre of (610, 1553.5), so D-004's exact-centre rule holds.
- **Full player.** Opens from the focused current card, follows the session across
  a track change, and its timeline drag (5577 ms to 8554 ms) and pause act on the
  real player. Interaction note: the first tap on a card only focuses it; a second
  tap on the card body opens the player.

### Defects found on device and fixed

1. **Play on a folder card was a silent no-op.** The focused folder card offered a
   play button, but `buildFolderQueue` excludes child folders, so `playFromFolder`
   returned without acting or saying anything. Reproduced on both devices. Folders
   now expose only the explicit *enter* action on the focus wall, and carry no
   trailing affordance at all in the list and grid layouts -- an intermediate
   arrow was tried there first and removed again, see below.
2. **The focused card's "add to queue" button was wired to an empty lambda**
   (`IconButton(onClick = {})`) -- an affordance that could never respond. It has
   been removed rather than left as a placeholder, and its now-unused string
   resource was dropped with it. Making it real needs a queue-insertion API on
   `PlaybackConnection` *and* a durable queue: the queue is currently rebuilt from
   the live folder listing on every play, so an appended item would not survive the
   next play.

### Presentation changes after the first device look

Two things read badly on the phone and were removed at the owner's request:

1. **The folder affordance glyph in the list and grid layouts.** These layouts
   briefly showed an `ArrowForward` on every folder row and in each folder cover's
   bottom-right corner. It was visual noise repeated on every single row, so it is
   gone: a folder now carries no trailing action in either layout, and the row or
   cell itself is what opens it. Audio is unaffected and keeps its play/pause
   button. The focus wall's focused folder card lost the arrow inside its *enter*
   button first, and then the button itself, so a focused folder now shows nothing
   but its own title and counts.

   The card action row was decoration on top of an action the cover already
   performs, and `resolveCardTap` proves it: a tap on the focused folder cover
   resolves to `FocusWallIntent.Activate`, and `onActivate` is exactly what the
   button called (`onEnterFolder = onActivate`), so entering a folder never needed
   the button. With it gone, `FocusCoverCard` and `FocusWallCanvas` lost their
   `onEnterFolder` parameter and `R.string.enter_folder` was deleted. The action
   row is now gated on `isFocused && item.type == LibraryItemType.Audio`, so only
   audio renders one.

   `R.string.open_folder` ("进入文件夹") was already unused before this change and
   has since been deleted too, taking `UnusedResources` from 8 to 7. The seven
   that remain are `all_audio`, `recent`, `favorites`, `sort`, `layout`,
   `demo_hint` and `local_audio` -- all strings for surfaces that do not exist
   yet, so they are placeholders rather than dead weight.
2. **The `library_restored_hint` notice** ("已从内存快照恢复（未重新扫描）…") no
   longer renders, and its string resource was deleted. Restoring from the
   in-memory snapshot is an implementation detail; the folder should simply look
   the way it was left. `LibraryUiState.isRestored` is kept, because
   `FocusWallViewportState` still uses it to skip the restore animation -- only the
   announcement is gone. The error and truncation notices still appear; the notice
   row now triggers on `isTruncated || hasError` alone.

Verified on `emulator-5554` against the fixtures: the grid and detailed list render
folders with no glyph, the root audio row still shows its play button, the focused
folder card on the wall shows only its title and counts, and the screenshot taken
after entering a folder and pressing Back is byte-identical to the one taken before
entering -- the restored folder renders exactly as a fresh scan would, with no
notice row. Entering was re-tested *after* the button was removed, since the cover
tap is now the only way in: tapping the focused 双耳录音室 cover navigated into it
and listed 01_左耳轻语 / 02_右耳轻语, and focusing 01_左耳轻语 there still renders
the circular play button. Each resulting APK was installed on the phone, confirmed
by matching MD5 (`82efa4c27bb30f472f3dbbe2e6dccb8b` for the final one).

### Folder summary line

A folder's secondary line used to be a fixed `%1$d 个子文件夹 · %2$d 条音频`, so a
folder holding only audio still spent half the line on "0 个子文件夹". It is now
built from the counts that are actually present, via a single shared helper rather
than the two private duplicates that existed before:

- `feature/FolderSummary.kt` holds `folderSummaryParts(childFolderCount, audioCount)`
  -- a pure, unit-tested function returning which halves to show, in display order
  -- and `folderSummaryLine(...)`, the `@Composable` renderer. It sits at the
  `feature` package root because the library layouts and the focus wall both need
  it and neither should own it.
- `R.string.folder_contents` was replaced by `folder_subfolder_count` and
  `folder_audio_count`; the separator " · " is applied by the helper.
- **An entirely empty folder is a deliberate exception**: with no non-zero count to
  carry the line it still renders "0 个子文件夹 · 0 条音频". Going blank there would
  read as a failure to load rather than as an empty folder, which matters because
  folders holding only images are common in a real library. This is the one case the
  new rule does not change, and it is covered by a test.

`app/src/test/.../feature/FolderSummaryTest.kt` covers all four quadrants, including
the empty and negative-count cases. Verified on `emulator-5554`: 双耳录音室 and the
other audio-only fixture folders now read just "2 条音频", while 环境声 (one
subfolder, one audio) still reads "1 个子文件夹 · 1 条音频".

### Remaining verification and risks

- **Media3 session lifecycle.** Foreground-service promotion, the media
  notification, audio-focus ducking and behaviour across process death have still
  not been exercised. `POST_NOTIFICATIONS` is declared but never requested at
  runtime, so on API 33+ the playback notification will not appear until a runtime
  request is added.
- **`android:exported="false"`** deliberately keeps external controllers (Android
  Auto, assistant, Bluetooth transport controls) out. Revisit when that becomes a
  product decision.
- **Queue and position are not durable.** `buildFolderQueue` rebuilds from the live
  folder listing on every play, so a queue does not survive process death and is
  not yet persisted in Room; the full player's bookmarks remain local UI state.
- **Restore path assumes a warm cache.** Back navigation restores instantly from
  `FolderSnapshotCache`, but the cache is memory-only with a capacity of 12, so a
  deep folder stack or a process restart falls back to a real rescan. That is the
  intended bounded behaviour, not a defect.
- **SAF URIs may go stale.** A persisted document grant can be revoked between
  sessions; playback failure currently surfaces only through
  `playbackState.errorMessage`.
- **Artwork precedence is incomplete.** Direct raster artwork resolves for the
  current folder and the first 48 naturally sorted child folders; folders that hold
  only subfolders fall back to the generated gradient placeholder. Recursive
  child-folder artwork fallback is still pending.
- **The reveal target is a folder, not an identity.** "Focus the playing item"
  follows the folder chain recorded at play time. If that folder has been refreshed
  since and no longer lists the track, the pending focus lands on nothing and the
  wall just shows the folder. A queue that outlived the process cannot be traced at
  all and leaves the entry disabled. Both are accepted trade-offs of not building a
  global document-URI-to-folder map; neither is verified on a device.

Gradle has a 2 GiB heap and 1 GiB metaspace in `gradle.properties`, replacing the
defaults that caused an Android Studio out-of-memory build failure.

## Claude collaboration status

Claude Code 2.1.281 is configured by cc switch to use OpenCode Go with
`deepseek-v4-flash`. Direct non-interactive invocation works:

```powershell
claude -p --verbose "<task>"
```

Claude Code prints an `unrecognized_model` warning because the third-party model is
not described by its local model catalog, but the request completes successfully.
Do not override it with `--model sonnet`: the current Sonnet tier mapping points to
the older `deepseek-flash` identifier. Allow several minutes for repository review
tasks before treating the process as hung.

A read-only review of the initial scaffold completed successfully. It found no
definite Kotlin/Compose source compilation errors and independently confirmed that
the remaining requirement is a real Gradle build. Some version/JVM warnings from
the review were static false positives already answered by current Android docs.
