# Repository instructions

This repository contains an offline Android ASMR player. Read the smallest relevant
set of files before changing code:

- `docs/PRODUCT_SPEC.md` for product behavior and acceptance criteria.
- `docs/ARCHITECTURE.md` for module boundaries and implementation constraints.
- `docs/DECISIONS.md` for decisions that must not be silently reversed.
- `docs/TASKS.md` for current priorities and ownership.
- `docs/HANDOFF.md` before resuming work started by another agent.

## Engineering rules

- Kotlin and Jetpack Compose are the default UI stack.
- Media playback uses AndroidX Media3 and a `MediaSessionService`.
- Storage access uses the Storage Access Framework. Never request broad storage
  access and never delete user media; removal means removing an index entry only.
- Treat media metadata writes as transactions: write a sibling temporary file,
  validate it, then replace the original. Refuse unsafe writes.
- The library is expected to contain 10,000 folders, 40,000 audio files and files
  as large as 4 GB. Avoid eager loading, unbounded collections and full-size image
  decoding.
- Privacy aliases affect presentation only. Never write alias title or artwork to
  media files.
- Keep user-facing strings in Android resources even while Simplified Chinese is
  the only shipped locale.
- Add or update tests for non-trivial state and data behavior.
- Do not edit another agent's worktree. Exchange work through commits or patches.

## Verification

Run the narrowest relevant tests first. Before handing off a completed change, run:

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
```

If Android tooling is unavailable, document the exact command that remains to run
in `docs/HANDOFF.md`.
