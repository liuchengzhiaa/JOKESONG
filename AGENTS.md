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

## Agent continuity

- The owner starts Claude Code manually. Codex must not invoke Claude on the
  owner's behalf unless the owner explicitly reverses this rule for that task.
- Before ending a material development slice, update `docs/HANDOFF.md` with the
  current implementation, exact verification results, known blockers and the
  safest next action. Keep `docs/TASKS.md` aligned with it. This is the shared
  continuity layer between Codex and Claude; chat history is not.

## Verification

Run the narrowest relevant tests first. Before handing off a completed change, run:

```powershell
./gradlew.bat -I tools/ascii-build-dir.init.gradle testDebugUnitTest
./gradlew.bat -I tools/ascii-build-dir.init.gradle lintDebug
```

On this Windows host, if Gradle reports `Unable to establish loopback connection`,
set `TEMP` and `TMP` to the existing `C:\jtmp` directory for that terminal before
retrying. The init script deliberately keeps outputs on the checkout's drive so
KSP can relativize generated sources.

If Android tooling is unavailable, document the exact command that remains to run
in `docs/HANDOFF.md`.
