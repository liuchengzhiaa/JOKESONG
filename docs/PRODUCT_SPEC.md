# JOKESONG — Product Specification

## Desktop lyrics (2026-10-11)

- Opt-in, off by default. Remains visible inside this app, over the home screen and
  other apps, without recreating its window on app/Home transitions. Screen off/keyguard
  hides it. Owner explicitly replaced the former background-only rule (D-039).
- Uses the playing queue item's matched timed-text file and Media3 position, not
  the currently browsed folder. No matched/timed document means no overlay.
- Settings offer 1–4 lyric sentences, 12–36sp text, normal/highlight HSV palettes,
  bold, line or word sweep, 0–80% background opacity, position reset and locking.
  Defaults: 2 sentences, 20sp, line mode, white/light blue, bold, 25% backdrop.
- Each palette has hue/saturation/brightness gradient sliders, live swatch, six quick
  colours and reset; commit on release. Existing stored ARGB colours are preserved.
  A fully-transparent-background shortcut sets backdrop opacity to zero; locking
  additionally removes the control hints so only the lyric text remains.
- Word mode reuses the existing exact-first Smart timeline; ordinary LRC sweep is
  estimated and labelled as such. Pause freezes timing; seeking/cutting tracks
  updates it. A completed queue hides it.
- Unlocked: drag to move, lock/close controls. Locked: touches pass through; unlock
  in the app settings. Style and normalized screen position survive process death.
- First explicit enable opens system overlay permission settings. Denial/revocation
  never disables playback. No accessibility/usage-access/broad-storage permissions.
- Pause retains the last timed line; app settings can turn the overlay off at any
  time. Untimed lyric files are not given fabricated playback timestamps.

Status: agreed discovery baseline  
Product name: JOKESONG
Primary user: the owner; possible store release later

## Product promise

An offline Android audio player designed around albums and audio works stored as folders,
including but not limited to ASMR. The name must not restrict the audience to ASMR.
It combines Poweramp-like information density with reliable long-form playback,
subtitle support, direct metadata editing and a distinctive focus-cover wall.

## Library model

- The user grants persistent access to exactly one root directory with Android's
  system directory picker.
- A folder represents a work or a structural grouping. Audio files are tracks.
- Folder browsing shows direct children only. Child folders and audio can coexist.
- The library also offers all-audio, recent, favorite and playlist views.
- Removing a root, folder or item from the app removes only its index. The app does
  not delete real media files.
- Expected scale: 10,000 folders, 40,000 audio files, fewer images/subtitles than
  audio files, and individual files up to 4 GB.

### Display titles

- Prefer an embedded title; fall back to the filename without its extension.
- A setting can force filenames to be the primary display title.
- The original filename may be shown as secondary information.

### Folder artwork

For a folder, inspect direct children in natural filename order:

1. Use the embedded artwork of the first audio file that has artwork.
2. Otherwise use the first supported image file.
3. If the folder contains only child folders, recursively use the first child
   folder that resolves artwork.
4. Otherwise show the neutral empty-artwork treatment.

Audio items use their own embedded artwork and then fall back to their folder art.

## Supported media

Initial guaranteed audio formats:

- MP3
- FLAC
- WAV
- M4A/AAC
- OGG
- Opus

Playback aims to reproduce the source without app-added EQ, normalization or
spatial effects. Version one does not promise bit-perfect or exclusive USB DAC
output.

## Navigation

Bottom-level destinations:

1. 资料库
2. 搜索
3. 播放列表
4. 设置

The library opens to folders and can switch among folders, all audio, recently
played and favorites. A circular global record replaces the conventional mini
player: it floats above bottom navigation, shows the current presentation artwork,
turns slowly only while playback is moving, and opens the full player. It is shown
on ordinary app destinations and hidden inside the full player and immersive lyric
scene. The listener may drag it inside the safe content viewport; release snaps it
to the nearest horizontal edge, and its normalized edge/height survive rotation and
process restart. Privacy presentation is applied before its artwork is chosen.

## Settings

设置 is a short list of groups, not one page of controls. Each group expands in
place to show what it holds; one group is open at a time and nothing is open when
the page is first shown, so the surface first answers what can be set here rather
than presenting one group's contents as if they were the whole of it.

- 外观与样式 holds the global appearance: a one-tap style preset, the global accent
  colour, the global motion strength, the global UI scale and the **收纳按钮样式**
  (毛玻璃 / 描边 / 实心 / 极简), plus a reset. It applies to the whole app -- the top
  bar's title and icons, the bottom navigation, the wall tools and later global
  controls -- and changes no library data. 收纳按钮样式 is one choice for the
  collapsible tool cluster everywhere it appears, so the player's copy and the wall's
  copy cannot drift into two controls that merely share a shape; 毛玻璃 is the
  default and is exactly what shipped before the setting existed, so an install that
  predates it renders unchanged.
- 歌词与可视化 uses one scene-header entry and a Folia-shaped four-tab overlay:
  **通用 / 背景 / 歌词动画 / 字幕**. It owns the persisted visualizer, background,
  global motion and Off / Smart / Exact-only word-glow choices. The background may
  be set to 跟随模式, which is the default and follows whichever treatment the
  chosen visualizer was designed around. None of these choices changes files or
  subtitle matching.
- A visualizer may expose **parameters of its own** inside 歌词动画 and persist them
  per mode: three for 流光 (breathing float, word spacing, word rotation), ten for
  时计 (draggable horizontal/vertical wheel position, radius, arc, tick snappiness, active scale, gear decor,
  centre gradient, cover on the dial, line glow), and 凝彩's artwork source, SAF
  image library and stage aspect. The production animation list is deliberately only
  流光 / 时计 / 回环 / 凝彩 while those four are taken to reference quality. Every
  parameter has a declared range and a reset to the mode's defaults. The global
  **motion** setting stays the master switch and is not one of them: it decides
  whether the scene moves at all, and a parameter decides the shape it moves in.
- 闪卡收藏室 opens the card collection room. It holds one entry rather than a set
  of settings, because the room is a place rather than a preference -- what can be
  done to a card is done to it there.
- 资料库 holds the library root selection and a manual refresh of the current
  folder.

The appearance accent reaches the top bar's title, navigation icon and actions. The
immersive full-player bar is deliberately excluded: its container is a fixed dark
surface independent of light and dark themes, so it keeps neutral content rather
than adopting an accent variant that would not be legible on it.

## Library layouts

Supported layouts:

- Detailed list
- Compact list
- Regular grid
- Focus-cover wall
- Folia-style focus wall

The five layouts are collected under the bottom-right tools button's Layout page;
it shows all five named choices and marks the active one. Lists and grid retain a
matching bottom-right chooser so users can always switch back to either wall.
No separate layout-switch row or gesture-instruction text reserves space above the
wall. The library folder header's content height and title size are two fifths of
the original app bar (25.6 dp / 8.8 sp with default Material metrics); system status
bar insets remain intact, action targets retain independent 48 dp widths, and other
destinations/player headers are unchanged. Scanning/error/truncation indicators
still appear when relevant, rather than silently hiding real problems. The app
remembers a global layout and permits per-folder overrides. Lists default
to folders-first grouping with an optional mixed-sort mode. Supported deterministic
sorts include natural name, track number, modified time and date added. Custom drag
ordering is not supported.

### Focus-cover wall

This is a signature feature, not a decorative grid.

The original centred-bento wall remains available unchanged. A second wall mode,
shown immediately after it in the layout menu, uses a denser Folia-style
12-item lattice. In that mode the focused item grows where it already is: the
hero keeps the configured focus shape, its position inside the block is derived
from the slot the card already occupied, and the remaining eleven slots are
regenerated as an exact cover around it, so neighbours change shape and move
rather than the hero jumping to a block corner. A card that already sat in a
corner still focuses in that corner. Both modes share the same focus, playback
and gesture semantics below.

The Folia focus shape and size are per-folder settings, offered from the wall's
tools panel beside the density control. Both default to the wall's original
behaviour, a square 6 x 6 hero, so a folder that never opens the panel looks
exactly as it always has. There are six shapes -- square, portrait, landscape,
full cover, heart and circle -- and three sizes, and the largest size of each
rectangle is capped so the eleven neighbours around the hero still have room to
be cards rather than slivers. The Bento wall has no such setting: its focused
block is a hand-written template, not a generated cover.

Two of the six are silhouettes rather than rectangles. The heart and the circle
are the focused card itself, drawn as the block cells that spell the shape, with
the eleven neighbours tiling everything else in the block exactly; none of them
is a picture inside a cut-out outline. A silhouette is written for one place in
the block and has one size, so it sits where it is rather than following the slot
the tapped card occupied, and the size control does not apply to it -- the size
row is offered disabled while a silhouette is selected, in keeping with the rule
that an action which cannot be honoured is shown disabled rather than as a tap
that does nothing.

Full cover shows the whole artwork with nothing cropped and nothing letterboxed to
speak of: the card takes the cover's own proportions, and the artwork is fitted
inside a card that already has its shape. A card can only be as many columns and
rows of the block as the grid holds, so the proportions are the nearest the block
can come rather than the cover's exactly; the size control still chooses how large
that card is, and picks within the range of areas its square preset would have
taken. A cover the block has no large rectangle of the right shape for keeps the
size its shape fits instead of taking a large card it would sit letterboxed
inside. A square cover gets exactly the square presets, and so does a cover whose
artwork has not been read yet, so an unread cover cannot make the wall shift.
Whatever is left over after fitting is the card's own palette gradient, never a
black bar.

The focused card carries an icon-only action at its bottom-end corner that clears
focus and returns the block to its ordinary mosaic. Focus and playback are
independent states, so the wall needs a way back out that does not depend on
tapping another cover.

- A card can represent either a folder or an audio file; a small overlay glyph
  distinguishes the type.
- A single tap on a card that does not hold focus moves focus to it: the card
  re-packs its block around the hero slot and the canvas centres on it. It does not
  play or navigate yet.
- A single tap on the card that already holds focus activates it. A focused audio
  item starts playing; the currently playing focused item opens the full player; a
  focused folder is entered.
- No double-tap gesture is bound to the cards. A quick double tap therefore reads
  as "focus, then activate", which is what makes opening an item a natural second
  tap rather than a special gesture. Leaving the double-tap handler off is also
  what lets a single tap be delivered immediately instead of being held back for
  the double-tap timeout.
- Focus and now-playing are independent states.
- Dragging explores the wall and never changes focus. Release coasts to a stop on
  the drag's own momentum; there is no snapping to the nearest item and no
  re-focusing on release. Focus used to follow whichever card came nearest the
  viewport centre when the drag ended, and the canvas then re-centred on it, so
  every scroll reflowed a block and moved the wall.
- Panning is bounded so a hard fling cannot carry the whole wall off screen and
  leave nothing to grab. Inside that bound panning is completely free; the clamp
  is a safety rail, not a snapping rule.
- Pinch zoom scales the canvas without reflowing it. Card density separately offers
  small, medium and large presets.
- The same sorted data and settings always produce the same spatial layout.
- Unfocused cards form a tightly packed deterministic mosaic with deliberately
  mixed square, portrait and landscape footprints; they are not one repeated
  card size with perspective applied afterward.
- Focusing a card re-packs the other cards in its local layout block around a
  centred hero slot. Neighbouring blocks keep their footprint and identity, so
  the change remains predictable rather than cascading across the whole wall.
- Card gutters stay visually narrow (about 2 dp at the default zoom). The focused
  outline is translucent and takes its accent from the focused cover, with the
  deterministic placeholder palette as fallback.
- Both orientations use a screen-filling two-dimensional mosaic similar to the
  approved reference image. The hero cover sits exactly at the viewport's
  horizontal and vertical centre, with smaller covers surrounding it in multiple
  rows and columns. Portrait prioritizes vertical exploration; landscape uses a
  wider composition.
- Back first clears focus; with no focused item, Back navigates to the parent.

Both walls also carry a floating tools button at their bottom-end corner. It sits
above the wall rather than inside it, so it keeps its size and place under zoom
and pan. Tapping it opens a small panel anchored to the button -- not a modal
sheet -- and a full-wall transparent scrim behind it, so a tap meant to close the
panel cannot also land on a card. Its first entry is "focus the playing item":
the wall's focus moves to the track that is playing, and when that track is not in
the current listing the app first navigates to the folder the queue was started
from. The entry is shown disabled when nothing is playing, or when the track
cannot be reached because the queue outlived the process that recorded its origin.
Further tools are added as rows to this same panel.

A tool whose setting is a continuous range shows how far along that range it is as
a filled level mark at the end of its row, not as a percentage. The fill is the
value's position on that setting's own control, so it always agrees with the slider
on the setting's own page; the ranges differ, so the same percentage is not the
same position in two of them. The number itself still appears where the value is
being set, on the setting's own page and under the slider being dragged. Every such
setting also carries a row on its page that restores its default in one tap, and a
setting whose default is already the end of its range has no such row because the
slider's own end is it.

In All Audio, the wall is a discovery mode containing a persistent random sample.
It defaults to 15 items, with 10/15/20 choices. The sample changes only when the
user requests another batch. The current audio remains in the replacement sample.

## Queue and playback

- Tapping an audio in a normal list replaces the queue with that visible list and
  starts at the tapped item.
- Context actions include play only, play next and add to queue.
- Folder playback normally includes direct audio children; recursively including
  child folders is an explicit option.
- The queue persists across process death and supports drag reorder, remove, clear,
  undo-clear and save as playlist.
- Shuffle creates and persists a fixed shuffled order.
- Playback is gapless where supported. Crossfade is off. Optional short fades may
  soften manual pause, resume and stop.
- Locking the screen or switching apps continues playback. Swiping the task away
  continues by default, with a setting to stop instead.
- Headphone disconnect pauses immediately. Reboot restores state but never starts
  audible playback automatically.
- Calls pause playback; brief transient audio focus loss ducks volume and resumes.

### Long-form state

- Persist global last playback state and a position for every audio file.
- A folder resumes its most recently played track and position.
- Mark a track complete at 95% or within 30 seconds of the end.
- Completed tracks retain their last position. Normal replay starts at zero, with
  a separate resume-from-last-position action.
- Provide configurable backward/forward skip intervals, precise seek, time-point
  bookmarks and standard embedded chapters.

## Player and subtitles

- The player has one adaptive cover/lyrics layout. The title is a compact single line;
  artist is shown only when known. Ordinary artwork uses its intrinsic aspect ratio and
  `Fit` (never crop), defaults to 85% screen width with a height safety bound, and has
  small rounded corners. Cover size (70–115%), title size (75–140%) and lyric size
  (75–150%) persist globally, available in Appearance and the player's tools.
- The backdrop is a broad vertical tonal gradient from the cover's **raw dominant**
  colour family selected from mid-tone chromatic samples (not pale highlights), with a
  dark saturated top, softer lower field and subtle side glow, no blurred artwork overlay. Wall lighting
  continues using its separate brightness-balanced presentation of the same extraction.
- The cover page reserves a stable three-slot lyric window: previous / current / next.
  Current is bright, neighbours are subdued; no green banner.
  The normal lyric page uses left-aligned paragraphs, subdued inactive lines, edge fades,
  tap-to-seek, and auto-follow that yields to manual scrolling.
- Swipe left: cover → ordinary lyrics → immersive visualizer. Swipe right from ordinary
  lyrics returns to cover. Header, background, seek line and five-button transport stay
  fixed between the first two pages. Back from the immersive scene restores the previous
  cover/ordinary-lyrics section; tools retain direct access to the visualizer.
- Transport order: previous track / skip backward / play-pause / skip forward / next
  track. Skip controls are no longer hidden in the tool menu.
- The audio output mark at the top right is a **read-only status indicator**, not a
  control: this app has no output picker. It names the active route for TalkBack and
  prefers wired, then Bluetooth, then the built-in speaker.
- **The tool row's four glyphs are 循环, 定时, 可视化 and 队列**, and the collect button is
  the fifth slot of the same row. 可视化 opens the immersive lyric scene, which is this
  app's only visualiser surface.
- **循环** cycles off -> list -> one -> off on each press, and shows the current mode in
  the glyph. It is the player's own repeat mode, so it survives a locked screen and a
  rebuilt UI. **定时** arms a sleep timer -- off, or 5, 10, 15, 30, 45, 60 or 90 minutes --
  which **pauses** playback when it comes due, and shows its remaining time on the panel
  row that opens it. It lives in the playback service so that locking the screen does not
  cancel it; see `DECISIONS.md` D-034.
- The collect button (⋯) opens the collection of things this song can do -- 闪卡, 歌词,
  播放页尺寸, 定时关闭, 书签, 章节 -- from the bottom-right corner.
- Chapters, bookmarks and queue open as bottom panels, and the queue's heading names the
  playing item's position in it. Users can choose two to four auxiliary controls to keep
  visible.
- Match LRC, SRT and VTT files in the same folder by base filename, including an
  optional language suffix. Matching is case-insensitive; a correction marker such as
  `new` or `mix` is skipped and the search continues, so a fixed file beside the track
  still matches; a name ending in something that is neither a language nor a marker
  matches nothing rather than being guessed at. An untagged file is preferred over a
  translated one, Simplified Chinese over another language, and LRC over a same-named
  SRT.
- Swiping left again from ordinary lyrics opens the full-screen subtitle detail
  view while keeping playback controls available. A timed file for the current Media3 item opens the immersive
  lyric scene: artwork and its bounded palette form the background, up to two lines
  on either side provide context, the current line is covered left to right by one
  continuous colour wipe that advances with the display intervals of its graphemes
  (exact where the file carries word timing, otherwise estimated) rather than
  switching character by character, and every visible line plus the precise slider can seek. The scene follows
  global motion strength and compacts to three lines in short landscape space. It
  owns no player or timer. Other states use the diagnostic reader, which names the
  file it matched and states plainly when no matching file was found, when the file
  could not be read, and when the file parsed to nothing.
- The immersive reader offers two persisted registries, configured through one
  four-tab scene-settings panel and independent of each other. The current production
  modes are 流光, 时计, 回环 and 凝彩. Backgrounds are blurred cover, sharp poster,
  dark spotlight, colour field, lattice and drift, plus 跟随模式 -- the default, which
  uses whichever background the selected mode was designed around. Switching either
  one never changes playback or subtitle selection. Settings provides a
  persisted word-glow policy: Smart prefers real word timestamps and estimates from
  line timing otherwise; Exact only refuses estimates; Off removes the moving wipe.
  The policy applies consistently to the immersive current line and the current line
  on a focused/current wall cover. Estimates affect presentation only and are never
  written into parsed or media data.
- 凝彩 may use the current audio cover or a user-selected SAF image library. Read
  permission is persisted and the bounded URI list is presentation data only. Its
  authored canvas may follow the entire phone, fit a 4:4 square, or fit a 4:3
  landscape stage; a constrained stage is centred and clipped without resizing the
  full-screen controls or changing playback.
- Current timed text stays visible and highlighted. Manual scrolling pauses following
  and exposes a return-to-current control. Tapping a timed line seeks. Following only
  applies while playback is on the item being viewed; otherwise the lines are shown
  without seek actions.
- On a focus wall, lyrics appear only when the expanded focused audio is also the
  current playing media. The cover scene shows a bounded previous/current/next
  context, uses an artwork-aware contrast shade and restrained context blur, and
  yields space rather than covering playback controls. Its current line uses the
  global word-glow policy -- the same left-to-right wipe, on the same grapheme
  intervals -- while the previous/next context remains visually quiet.
  Bento uses the compact
  centred treatment. Folia follows the approved poster reference: tiny status and
  media information at upper-left, a left-aligned lyric hierarchy whose current
  line is vertically centred and whose context lines are equally distant above and
  below it, small controls near the lower-left and a thin seek line close to the
  bottom edge. Its layout is proportional to the focused poster rather than fixed
  phone pixels. Ordinary Folia cards use the same square-edged shaded-poster style
  with only a small type mark and lower-left title/metadata; no lyrics or transport
  chrome leak onto them. Untimed text remains available in the reader and is not
  falsely animated on the cover.
- **A file with no time tags is shown as text, not as a timeline.** Its lines are
  listed in order with a notice that they cannot follow playback, rather than being
  spread across the track's duration.
- Without subtitles, the same left swipe opens an immersive artwork view and states that no
  matching subtitle was found.
- Manual subtitle rebinding, offset repair and editing are deferred.

## Cover flash cards (闪卡)

The full player carries a collapsible tool cluster in its lower-right corner. Its
first entry is 闪卡, and what it does depends on the song, because the three states
are three different errands and one label for all of them would leave the owner to
work out which he was in by looking at the artwork:

| The song | The entry says | Pressing it |
|---|---|---|
| has no card | 闪卡 | opens the form directly, on this song |
| has a card, showing its artwork | 闪卡 | switches the cover to the card |
| has a card, showing the card | 原封面 | switches back to the artwork |

The switch is **per song and per visit**: the default is the artwork, showing the
card is something the owner asks for each time, and moving to the next song is a
fresh question. Settings offers **封面默认闪卡** (off by default) for the owner who
would rather not ask: with it on, a song that has a card opens on it. It is a
default and not a rule, so the entry still takes the card away again.

The song's page opens the workshop at its form rather than at the collection room,
because that is the errand: make this song's card. The room is one back away, and
back from the form leaves the workshop instead of opening a room the owner did not
ask for. A card built from that form does not by itself switch the cover to it --
the owner said the card is asked for each time, and the switch is how he asks.

The workshop opens from Settings as well as from the player, and has three pages:

1. **The collection room (收藏室).** Every card this app has made, shelved by the
   series printed on it, with the pinned ones on a shelf of their own at the top and
   the ones with no series grouped last. It can be searched across title and series,
   ordered by newest, oldest or title, and its cards can be selected in batches to be
   pinned or deleted together. Tapping a card reopens it; long-pressing one offers
   pinning and deletion. Every card shows a picture of itself: the viewer's own saved
   capture if there is one, and otherwise a flat preview written when the card was
   built, so a card that has just been made is a card rather than an empty frame.
   The room is reachable from Settings as well as from the player. Opened from
   Settings it has no song behind it, which is a real state rather than a missing one:
   the collection does not require something to be playing.
2. **The form.** The cover, a material, and the copy. Opened from a song it is
   pre-filled from that song -- title and artist from the file, the rest from the house
   defaults the reference cards used -- so building a card takes no typing at all, and
   every field is editable so it can take some. Opened from the collection room it
   starts blank. The cover button replaces the cover through the system photo picker,
   which asks for no permission at any Android version. Nothing can be built without a
   cover, and the form says so rather than leaving a dead button.
3. **The card itself**, rendered live: a holographic card that turns with the finger
   and can be magnified by pinching, with the cover's subject floating above a blurred
   atmosphere layer, the four materials switchable, and four depth sliders. The save
   button puts a 1400x1800 PNG into `Pictures/白相/`.

Inside the Android app the card is an immersive player surface, not the reference
site squeezed into a phone. It fills the window with a dark, lightly tinted stage and
keeps only small glass actions over the scene. The material/title/depth workbench is
closed by default and expands from one bottom-corner control; closing it returns the
entire surface to the card. The Android-native back control remains above the WebView
so leaving still works if WebGL is slow or its renderer stops.

Dragging left or right directly turns the physical card over the complete front-edge-
back arc. The angle follows the finger during the gesture rather than waiting for a
binary flip, can cross the edge-on point in either direction, and settles to the nearest
face only after release. The explicit front/back controls remain available in the
collapsed workbench.

**Two fingers magnify**, from 0.72x to 2.4x, and a double tap returns the card to 1x.
The magnification is the card's own: it re-renders the scene at full resolution rather
than scaling a picture of it, so a card zoomed in stays sharp. A pinch takes the card
out of the turn first, so one finger keeps turning it and two change how close it is;
the card settles to its nearest face as the pinch begins.

**The card can stand in the full player's cover position.** It is shown there live, and
it turns and magnifies under the same gestures. Its own save button, material swatches
and depth sliders are not drawn in that position -- they are controls painted over the
cover they are about -- and remain one entry away in the workshop. Because the card takes
the cover's box and its one-finger gesture, the tool cluster carries a 歌词 entry of its
own: without it, carding a song would cost the owner the only way to its lyrics from this
screen.

The position is the card's while it is in it. The artwork's square, the accent hairline
drawn around it and the swipe it owns are the *artwork's* frame, so none of them are
drawn around a card: the slot takes the card's own 2:3 proportions and the card fills it.
A portrait card centred in a square is what puts a wide dark margin down either side of
it, which reads as a black border around something that is not a picture in a frame.

The card is **derived from the cover**, not drawn by a model: no AI image generation
is involved, and the app asks the network for nothing -- it holds no `INTERNET`
permission and never will for this. The card's own copy is set in a bundled kai (楷体)
face, chained to the system's so a glyph the bundled subset lacks changes face instead
of becoming a blank box.

A card is a **record of a moment, not a live view**: material and depth can be adjusted
while looking at it, and those adjustments are not written back, so reopening a card
shows what it was built with. That is the reference's own behaviour and is reproduced
rather than improved.

Deleting a card removes only a directory this app created. It never touches the
owner's music, and a card already saved to Pictures stays there, because by then it is
the owner's picture.

**Deferred:** the model/API path (an off-by-default switch whose backends -- a custom
API, DeepSeek, a local Codex or a local Claude Code -- are not built in this version),
importing a card from elsewhere, and generating cards for many songs at once. (Pinning,
deleting and searching a selection of cards *are* built; batch here means batch
*generation*.)

## Metadata editing

First implementation (2026-10-18, D-040): details-page tool menu → 编辑歌曲信息.
Single-track title, artist/voice actor, album, album artist, track/disc, date/year,
genre, comment and embedded front cover are written to the actual audio file, not
an alias/database overlay. Physical filename is unchanged; therefore the folder
browser still uses its filename and same-name lyric matching is preserved.

Cover picker initially hints `/storage/emulated/0/Download/mihon/local/` through SAF;
the system may ignore this hint, and the user may browse elsewhere. Cancelling does
not clear artwork. Cover preview is bounded; selected images become sampled JPEGs.
Writes initially require primary internal storage, a persisted folder read/write
grant, sufficient free space, and a validated MP3/FLAC/M4A/RIFF-WAV. Other locations/
containers and failed integrity checks are refused with no in-place truncation.
Saving pauses the edited current track; close and resume manually. Other playing
tracks are not stopped. Drafts survive rotation; closing is disabled during save.

This is explicitly a recoverable, **non-atomic** sibling + backup + rename commit.
Pending jobs offer recovery on app launch or in the editor. Conflicts preserve the
files/journal instead of overwriting them. One old-tag/art snapshot is retained;
user-facing undo, circle/publisher/custom-tag editors and batch editing below are
roadmap, not delivered in this first editor. Native/device acceptance remains pending.

Version one edits the original media tags for:

- Title
- Artist / voice actor
- Album / work name
- Circle / publisher
- Track and disc number
- Year
- Genre
- Comment
- Custom tags
- Embedded artwork

Batch editing supports shared fields, artwork replacement and numbering by current
sort order.

Every write is transactional:

1. Confirm enough free space.
2. Write a sibling temporary file.
3. Validate the rewritten media and tags.
4. Replace the original only after successful validation.
5. Keep an old-tag snapshot for undo, but do not retain a duplicate audio file.
6. Refuse formats or providers that cannot be updated safely.

## Scanning and missing files

- The user initiates refreshes; there is no continuous watcher.
- Provide Refresh Current Folder and Rescan Entire Library.
- Full scans report checked folders, discovered media and errors and may continue
  while the user navigates elsewhere.
- Browsing becomes available before a full scan finishes.
- Missing items remain as indexed placeholders with progress, bookmarks and playlist
  references intact. They can reconnect if they reappear.

## Search and collections

- Search filename, folder name/path, title, artist/voice actor, album, circle and
  tags. Group results into folders and audio.
- Support Chinese pinyin matching. Japanese romanization is deferred.
- Collections include manual playlists, favorites, recently played, recently added,
  completed and unfinished.
- Manual playlists preserve order and may contain duplicates.

## Privacy presentation mode

- Configuration contains one replacement artwork image and one fixed replacement
  title.
- When enabled, the current media uses those values on the player, global record,
  notifications, lock screen, external media surfaces and its library card.
- History is visually obscured while the mode is active.
- Mode state persists across process death and is applied before reconstructing
  media notifications to avoid an unmasked flash.
- Only title and artwork are in the initial privacy scope. Other metadata leakage is
  a known deferred limitation.
- The alias never modifies media tags or canonical library records.
- Configuration and the mode switch live in Settings. Further privacy UX is deferred.

## Backup

- Manually export and import an encrypted backup protected with a user password.
- Include progress, bookmarks, playlists, favorites, custom layout choices, missing
  item state, metadata undo snapshots and privacy configuration.
- Do not include audio files.
- Restore previews a summary and supports merge or replace.

## Appearance and localization

- Poweramp-like information density, fast direct controls and restrained animation.
- The focus wall may use richer transitions; long lists must prioritize smoothness.
- Support system, light, dark and OLED-black themes plus optional artwork-derived
  colors with enforced contrast.
- The Folia-style wall has a cover-derived ambient layer. It follows the currently
  focused card, independent of the playing media, and cross-fades when focus moves.
  Phase one uses a bounded palette from only the focused cover plus a few static
  gradients behind the wall plus a restrained low-alpha wash across its opaque
  posters; it must not blur a full-screen bitmap, scan the library, or run a
  continuous animation after the colour transition settles. The retained Bento
  wall is unaffected.
- Wall density and canvas zoom live in the bottom-right wall tools instead of a
  permanent row above the mosaic. Zoom remains continuously pinchable and is also
  adjustable from 72% to 135% with a 100% reset. Folia ambient strength offers
  off/low/medium/high and is restored with the folder's wall snapshot.
- The Folia wall's `墙面显示` page provides persistent lights-out, opt-in
  track-change auto-focus, off/low/medium/high cover-colour poster tint and reduced
  motion. Lights-out and tint exempt both the focused card and current media;
  auto-focus is active only while the Folia layout is visible. Ambient strength
  remains an independent control. The queue shortcut waits for a durable queue
  destination rather than exposing a dead action.
- Animated lyrics and visualizer modes follow the staged contract in
  `FOLIA_PRESENTATION_PLAN.md`. The two reference-grade modes run a frame loop, but
  only while the full-screen scene is on screen, playing, and above 0% motion; the
  wall, ordinary cards and the cover lyric layer never run one. Privacy mode must substitute its
  alias artwork before any palette or lyric presentation layer sees the current
  media.
- The timed full-screen lyric route is a physical edge-to-edge stage, not a player
  page inside a top and bottom bar. System bars are hidden only while the stage is
  present and remain transiently reachable; title/actions and transport are
  palette-derived overlays that never resize the renderer, auto-hide during
  playback, and retain at least 48dp touch targets. A tap on a hidden stage reveals
  controls before it can seek.
- Reference-named lyric modes must implement that mode's distinct spatial and timing
  contract. In particular, 回环 is a depth-sorted tilted lyric orbit, and 凝彩 is a
  deterministic multi-layer shot/transition program with a stable readable subtitle,
  not a common lyric stack with a different entrance animation. Expensive post
  effects and 3D require capability-gated fallbacks and reduced-motion paths.
- Ship Simplified Chinese first; all strings remain resource-based.

## Explicitly deferred

- Network playback, downloads, accounts and cloud sync
- Sleep timer
- EQ, speed/pitch controls, channel processing and normalization
- Bit-perfect and exclusive USB DAC output
- A–B repeat and generated waveforms
- Subtitle repair/editor
- Japanese romanized search
- Advanced smart playlists
- Further privacy-mode design beyond the settled baseline

### Per-track artwork source
Audio artwork is resolved lazily in this order: embedded audio picture → same-basename
sidecar image → common folder picture → placeholder. Folder cards retain folder-image
selection. Distinct tracks must never be assigned another track's embedded image. No
artificial differentiation if their actual embedded images are identical. Manual folder
refresh updates versioned per-track URIs; originals and tags are never modified.
