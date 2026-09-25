# Offline ASMR Player — Product Specification

Status: agreed discovery baseline  
Working name: ASMR Player  
Primary user: the owner; possible store release later

## Product promise

An entirely offline Android player designed around ASMR works stored as folders.
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
played and favorites. A mini player remains available above bottom navigation.

## Library layouts

Supported layouts:

- Detailed list
- Compact list
- Regular grid
- Focus-cover wall

The app remembers a global layout and permits per-folder overrides. Lists default
to folders-first grouping with an optional mixed-sort mode. Supported deterministic
sorts include natural name, track number, modified time and date added. Custom drag
ordering is not supported.

### Focus-cover wall

This is a signature feature, not a decorative grid.

- A card can represent either a folder or an audio file; a small overlay glyph
  distinguishes the type.
- Tapping a non-focused card moves and enlarges it into the focus position without
  playing or navigating.
- Tapping a focused audio item starts it. Tapping the currently playing focused
  item opens the full player.
- Tapping a focused folder enters it. Folder focus also exposes separate Enter and
  Play actions.
- Focus and now-playing are independent states.
- Dragging explores the wall; release snaps the nearest item into focus. A light
  fling moves among neighboring items.
- Pinch zoom scales the canvas without reflowing it. Card density separately offers
  small, medium and large presets.
- The same sorted data and settings always produce the same spatial layout.
- Both orientations use a screen-filling two-dimensional mosaic similar to the
  approved reference image. The hero cover sits exactly at the viewport's
  horizontal and vertical centre, with smaller covers surrounding it in multiple
  rows and columns. Portrait prioritizes vertical exploration; landscape uses a
  wider composition.
- Back first clears focus; with no focused item, Back navigates to the parent.

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

- The full player uses a dense, configurable layout. Artwork occupies the upper
  region; seek and primary controls remain visible.
- Chapters, bookmarks and queue open as bottom panels. Users can choose two to four
  auxiliary controls to keep visible.
- Match LRC, SRT and VTT files in the same folder by base filename, including an
  optional language suffix.
- Tapping artwork opens a full-screen subtitle detail view while keeping playback
  controls available.
- Current timed text stays centered and highlighted. Manual scrolling pauses
  following and exposes a return-to-current control. Tapping a timed line seeks.
- Without subtitles, artwork tap opens an immersive artwork view and states that no
  matching subtitle was found.
- Manual subtitle rebinding, offset repair and editing are deferred.

## Metadata editing

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
- When enabled, the current media uses those values on the player, mini player,
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
