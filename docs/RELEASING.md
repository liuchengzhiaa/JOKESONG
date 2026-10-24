# JOKESONG source and APK delivery

## Standing owner requirement

For each completed material code change: update source + CHANGELOG + HANDOFF + TASKS,
verify, commit/push to the owner's **JOKESONG** repository, and publish the
tested APK as a GitHub Release attachment. Documentation-only changes need syncing,
not another identical APK. Never claim success from a local build alone.

Current visibility is **public**, explicitly requested by the owner on 2026-10-18
because the phone cannot sign into GitHub. Source, Git history and Release assets
are public. This supersedes the earlier private-only destination policy (D-042).
The publisher checks the expected visibility; it never changes it itself.

The source upload is now authorized; the earlier binary-only proposal is superseded.
Do not publish private media, exported cards, machine settings, auth caches or signing
private keys. APKs/AABs are excluded from Git and belong in Release attachments.
Review the exact upload set (including incoming uncommitted work) before staging.

## Authentication and initial setup

The owner signs in/authorizes the official GitHub CLI himself. Chrome sign-in alone
does not authenticate `git`/`gh`, and this session cannot control Chrome. Never extract
browser cookies/passwords or ask the owner to paste tokens/recovery codes into chat.

This checkout currently uses a checksum-verified portable official CLI under
`.tools/github-cli/v2.102.0/bin/gh.exe`; `.tools/` is ignored. Its isolated configuration
is `.tools/gh-config` with credentials handled by the system credential store. Do not
pass `--insecure-storage` or upload this directory. Inspect only masked auth status.
If secure storage fails, stop and resolve it rather than intentionally keeping a token
in plaintext. No project-specific password or token is needed in source code.

Create any new repository only under the confirmed authenticated owner with separately
approved visibility. Prefer an empty
repository for the first push of this existing Git history. If a JOKESONG repo already
exists, inspect it rather than overwriting it. Keep history and avoid force pushes.

## Each code release

Normal entry point after source/docs review and commit:

```powershell
.\tools\publish-release.ps1 -PlanOnly  # local inspection only; no network/writes
.\tools\publish-release.ps1            # tests/build/source push/tag/Release
```

The script rejects dirty source, wrong destination/unexpected visibility, duplicate tags, unchanged
versionCode relative to local release tags, changed signer/package/version, failed
checks and unexpected source changes during build. The public certificate fingerprint
in `tools/release-signing-certificate.sha256` is **not a private key**; it pins update
compatibility. Keep it unchanged unless explicitly planning a signing migration.
Current destination is https://github.com/liuchengzhiaa/JOKESONG (verified public).
`-ExpectedVisibility Public` is the default; `Private` is available only for a
separately approved private destination. A mismatch stops publication; do not change
visibility merely to bypass a failed check.
Read back the branch SHA and all uploaded asset sizes/SHA256 before calling it done.
Interrupted publication is not silently retried with overwrite: inspect the remote
branch/tag/release and finish only the missing step while preserving existing assets.

1. Update `CHANGELOG.md` with date/version, changes, exact test/device status and limits.
   Update HANDOFF/TASKS for continuity. Increase versionCode and use a new version/tag.
2. Run the narrow relevant tests, then the standard full checks and build:

   ```powershell
   $env:TEMP = 'C:\jtmp'
   $env:TMP = 'C:\jtmp'
   .\gradlew.bat -I tools/ascii-build-dir.init.gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
   ```

   When changing an interface or its forwarding adapters, clean the affected build
   outputs before final verification. v0.1.13 shipped a stale anonymous Kotlin
   delegate missing `FolderCoverStore.image`, despite passing pure primitive tests.
   Exercise the production adapter and actual APK entry flow on generated fixtures;
   a successful probe-only instrumentation test is not an end-to-end batch test.

3. Confirm the correct APK under `../.asmrplayer-build/app/outputs/apk/debug/` on this
   host. Verify package ID/signing compatibility, version, timestamp and SHA256.
   Keep the current signer for existing installations. Never copy a signing key into
   Git; a future CI signing design is a separate security decision.
4. Review diff/upload exclusions, commit the completed slice and push normally. Read
   back the remote branch SHA and check it equals the intended local commit. If remote
   history differs, inspect/integrate it safely; do not force-push or discard work.
5. Create a unique versioned release targeting that exact commit. Attach the tested
   `JOKESONG.apk`, SHA256/build manifest and release notes. Version lives in the tag,
   Android package and manifest; a constant asset name keeps the latest-download URL
   valid across updates. Verify the asset
   name/size/digest remotely. Do not replace another release/asset silently.
6. Give the owner the repository/release/latest-download links. Public Release assets
   need no GitHub login; the phone downloads the APK and confirms the Android install.
   No USB, remote ADB or automatic device install is needed.
7. Record the actual delivery receipt or exact blocker in HANDOFF/TASKS. A follow-up
   documentation-only commit can record the release URL; distinguish its SHA from
   the code/APK release commit. This avoids an endless release-for-receipt loop.

If auth/network/tests block delivery, preserve local commits/artifacts and clearly
state **pending**, not uploaded/published. Never put a token in the Git remote URL.

## Initial state (2026-10-18)

The owner authorized official CLI access and the active account is `liuchengzhiaa`.
Credentials are verified stored in the system keyring, not plaintext. The private
JOKESONG repository was created and verified empty before the initial source push.
See the latest HANDOFF for actual source/Release verification, not just creation.


## One-time v0.1.9 phone acceptance exception (2026-10-19)
Owner explicitly requested compile-only delivery, skipping automated/device checks.
Only this release uses an ignored derived build/quick-release.ps1: assembleDebug,
normal signer/package/source/asset checks, null/skipped JVM and Lint fields and honest
notes. The checked-in publisher and future default verification policy are unchanged.
Do not cite older test XML or declare this build tested. Device speed acceptance is
with the owner; restore standard full verification for the next normal development slice.
