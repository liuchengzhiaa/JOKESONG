# Claude Code entry point

The canonical repository guidance is in `AGENTS.md`. Read it first, then read only
the product or architecture documents relevant to the assigned task.

When working as a delegated implementation agent:

1. Work only on the task named in `docs/TASKS.md` or in the invocation prompt.
2. Do not broaden product scope or silently change settled decisions.
3. Record notable implementation choices and remaining verification in
   `docs/HANDOFF.md`.
4. Do not modify or delete real user media in tests; use generated fixtures.
5. Return a concise summary of changed files, tests run and unresolved risks.
