# Direct audio metadata editor — first implementation, 2026-10-18

Status: editor and guarded writer implemented; final verification is recorded in
the latest HANDOFF. Actual native/SAF device execution is still pending.
Owner requested Poweramp-like editing from song details, including embedded cover,
title and artist. This is canonical file mutation, not a presentation alias.

## Accepted safety decision (D-040)

AGENTS/D-003 and the current architecture require a sibling rewrite, validation,
safe replacement and refusal for unsupported providers. Standard SAF does not expose
an atomic whole-file replacement contract. Rename support alone proves neither a
whole-transaction atomic swap nor rollback capabilities.

The owner answered “写入吧” to the safety choice, accepting an explicitly **recoverable,
non-atomic** local-provider commit: validated sibling -> original renamed to temporary
backup -> staged file takes original name -> read-back validation -> backup cleanup.
D-040 narrows the old strict-atomic/read-only clause. Never truncate the original or
claim an atomic swap; a SAF write grant alone is still insufficient.

## Implemented first delivery

- Add "编辑歌曲信息" to the existing details-page tool cluster. Editor reads real
  tags, captures the target item/URI so queue changes cannot retarget the operation.
- Initial fields: title, artist/voice actor, album, album artist, year, track/disc,
  genre and comment; embedded front artwork. Preserve unchanged fields, other artwork
  and unknown metadata rather than reconstructing only visible fields.
- Title means the embedded TITLE tag; leave the physical filename unchanged by default.
  This preserves SAF identity and same-basename lyric matching. No disguised alias.
- Image chooser: ACTION_OPEN_DOCUMENT/image/* with EXTRA_INITIAL_URI pointing to
  `content://com.android.externalstorage.documents/document/primary%3ADownload%2Fmihon%2Flocal`.
  It is a starting-location hint, not an automatic grant or a guaranteed picker root.
  User can browse elsewhere; cancelled selection must not clear an existing cover.
- Decode selected artwork with an explicit pixel budget, normalize embedded JPEG,
  keep a bounded byte size. Show preview and actual source filename in the editor.
- Save progress/busy state survives rotation (ViewModel). No dismiss/reentrant save
  during commit; cancellation only before source-name mutation. Never silently export
  a copy and report that the original was edited.

## Engine and transaction implementation

Pinned `io.github.kyant0:taglib:1.0.6` (Android native TagLib wrapper), verified in
published Maven metadata and the downloaded published source archive, rather than
hand-writing ID3/FLAC/MP4 serializers. Pass duplicated/detached FDs because native
FileStream takes ownership. Keep the AAR unmodified. Wrapper Apache-2.0 and native
LGPL/alternative MPL notices are under `app/src/main/assets/licenses/`. Before public
release, verify exact native corresponding-source/relinking obligations. Generated
WAV native instrumentation exists and compiles, but has not run on a device yet.

- Native writes ONLY to the sibling stage FD. Original remains read-only until commit.
- Stream source copy/digests in fixed buffers; use Long lengths for 4GB files, no
  full-file byte arrays or full audio cache copy on top of the sibling stage.
- Capability checks: actual persisted read/write tree, local known provider, sibling
  create/rename/cleanup, seekable regular FDs, source stat and available space.
  A source staged safely is not itself proof of safe replacement support.
- Validate format/audio properties, changed tags and image bytes after reopening.
  Prefer independent streaming audio-payload hashes (MP3/FLAC/MP4 mdat/WAV data) to
  prove audio unchanged; unsupported containers remain read-only initially.
- Recoverable commit approved: persist an AtomicFile journal BEFORE every
  potentially source-affecting operation, including planned artifact names; discover
  interrupted operations without guessing/deleting unrelated files. Never overwrite
  a conflicting third-party document during recovery. Inject faults at every phase.
- Keep old-tag/old-cover undo snapshots with a bounded retention policy, not permanent
  duplicate audio. Backups may only be cleaned after the final original-name document
  is validated and recovery no longer needs them.
- Pause the affected current Media3 track on Save; reload after commit, retain queue,
  position, repeat/timer and lyric extras. Do not stop an unrelated playing track.
- Invalidate artwork version/negative cache and folder snapshots after success; make
  LocalAudioMetadataReader include real title and refresh the details header. Metadata
  display and notifications must reflect the file, not a second editable alias store.

## Entry points and delivered boundaries

- `ui/PlayerDetail.kt`: PlayerToolsPanel/GlassToolCluster, per-item metadata reader.
- `core/metadata/LocalAudioMetadataReader.kt`: on-demand duration/title/artist/album.
- `core/metadata/AudioTagEditor.kt`, `SafTagDocuments.kt`, `MetadataTransaction.kt`:
  canonical fields/cover, narrow provider adapter, durable commit/recovery.
- `core/metadata/AudioPayload.kt`, `AudioProbe.kt`: independent integrity verification.
- `ui/MetadataEditorDialog.kt`, `feature/library/MetadataEditViewModel.kt`: form,
  rotation-safe/captured target and recovery entry.
- `core/media/PlaybackConnection.kt`: Media3 queue and per-item timed-text extras.
- `core/storage/SafDirectoryScanner.kt`: filename-based subtitle matching, bounded scan.
- `feature/library/LibraryViewModel.kt`: root URI/stack and folder snapshot invalidation.
- `core/artwork/AudioArtworkProvider.kt`: lazy embedded art, versioned SHA key/cache.

Current limits: primary internal ExternalStorageProvider only; no broad-storage
permission, unknown provider, cloud, SD, RF64 or in-place truncation. MP3/FLAC/M4A/WAV
are attempted only if every read-back check passes. Limit container metadata to
16 MiB, existing pictures to 16 / 8 MiB total, selected JPEG to 1024px / 4 MiB. On
MP4 the first image is the front because its picture format lacks front/back types.
Filename stays unchanged, including filename-based browser titles and lyric lookup.
One latest private old-tag/art snapshot has a commit digest, but **no Undo UI** yet.
Circle/publisher/custom-tag editing, batch writes and more providers remain roadmap.
Do not interpret compiled JNI tests as actual phone/SAF acceptance.
V1 also refuses DRM/encrypted samples and multi-track containers (including a
separate chapter/video track), whose auxiliary/sample tables are not fully verified.

## Sources inspected (primary)

- https://developer.android.com/reference/android/provider/DocumentsContract
- https://developer.android.com/reference/android/provider/DocumentsContract.Document
- https://github.com/Kyant0/taglib
- https://github.com/Kyant0/taglib/blob/main/src/main/java/com/kyant/taglib/TagLib.kt
- https://github.com/Kyant0/taglib/blob/main/src/androidTest/kotlin/Tests.kt

Verification uses generated disposable fixtures only. JVM tests cover transaction and
recovery faults, cancellation/conflicts, property preservation, malformed containers,
audio digest stability and a sparse 4 GiB layout boundary without reading payload.
Full JVM/lint/app/instrumentation build results are in the latest HANDOFF. Native
tag/art round trips and actual SAF/provider/restart behavior still need an authorized
device run. No real user audio modified in tests; no automatic APK installation.
