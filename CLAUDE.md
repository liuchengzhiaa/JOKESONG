# Claude Code entry point

The canonical repository guidance is in `AGENTS.md`. Read it first, then read only
the product or architecture documents relevant to the assigned task.

When the owner resumes this project manually, always read `docs/HANDOFF.md`,
`docs/TASKS.md`, `git status`, the recent Git log and the current diff before
planning or editing. The working tree may intentionally contain verified Codex
work that has not been committed yet; preserve it and continue from the recorded
state instead of assuming a clean checkout.

Product/repository branding is **JOKESONG**. The owner has standing instructions to
sync each completed code update, its tested APK and its changelog to the configured
private GitHub repository. Follow AGENTS' delivery rules and `docs/RELEASING.md`;
update CHANGELOG/HANDOFF/TASKS, never upload secrets/private media, and report failed
or pending delivery honestly. Browser sign-in is not CLI authentication.

When working as a delegated implementation agent:

1. Work only on the task named in `docs/TASKS.md` or in the invocation prompt.
2. Do not broaden product scope or silently change settled decisions.
3. Record notable implementation choices and remaining verification in
   `docs/HANDOFF.md`.
4. Do not modify or delete real user media in tests; use generated fixtures.
5. Return a concise summary of changed files, tests run and unresolved risks.
