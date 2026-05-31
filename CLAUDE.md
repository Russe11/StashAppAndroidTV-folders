# Stash Android-TV client (Claude + Codex instructions)

> Auto-loaded by Claude Code (`CLAUDE.md`) and Codex (`AGENTS.md` â symlink to this file).
> Android client of the NG ecosystem (one of 5 sibling repos under `~/projects/Stash/`).
> Workspace overview + cross-repo task→doc router: `../CLAUDE.md`. Local design: `CONTEXT.md`, `DEVELOPMENT.md`.
> **Note:** this repo's `docs/` is gitignored (local-only, not version-controlled).

## What this is

The world's best Android-TV **viewer**: zero-jank D-pad/TV navigation, instant playback. Kotlin Â·
Apollo GraphQL Â· Media3 Â· Compose, plus a Room "Folders/New" cache. The GraphQL contract is
**Apollo codegen** from the pinned `stash-server` git submodule (now repointed to the NG fork â the
app is no longer NG-blind). Auth is **API-key** (`ApiKey` header).

## Hard invariants (NEVER violate)

1. **NG-only** â the `stash-server` submodule pins the NG fork's schema; never add upstream-fallback
   logic.
2. **Viewer only â never destructive.** Do not delete/move files on disk and do not call destructive
   server mutations from the couch remote (the existing `delete_file` path is a **bug to gate**, not a
   feature). Destructive curation belongs to macOS.
3. **One contract, many copies** â a server schema change requires repointing/refreshing the
   `stash-server` submodule and re-running Apollo codegen.
4. **Secrets in EncryptedSharedPreferences; TOFU cert pinning â never plaintext.** No telemetry unless
   opt-in + local-first.

## Build / test

- Build: `./gradlew assembleDebug`
- Tests: `./gradlew testDebugUnitTest`
- Compile-check a module: `./gradlew :app:compileDebugKotlin -q`

## More

- Backlog: `docs/agent-memory/{bugs,fixes,tests,optimizations,ideas,questions}.md` (local-only) â
  remove entries when their work ships.
