<!-- GENERATED FILE — do not edit directly.
     Source of truth: stash-ng-workspace/tools/gen-brains/
       (invariants.md + repos/android.md). Regenerate from the workspace root:
         python3 tools/gen-brains/gen_brains.py
     The drift guard (.github/workflows/brains.yml) fails if this is stale. -->

# Stash Android-TV client (Claude + Codex instructions)

> Auto-loaded by Claude Code (`CLAUDE.md`) and Codex (`AGENTS.md` → symlink to this file).
> Android client of the NG ecosystem (one of 5 sibling repos under `~/projects/Stash/`).
> Cross-cutting brains (invariants, contract, glossary, cross-repo ADRs) live in the workspace
> root — `../CLAUDE.md` in the workspace, or `Russe11/stash-ng-workspace` standalone. Local design:
> `CONTEXT.md`, `DEVELOPMENT.md`.
> **Note:** this repo's `docs/` is gitignored (local-only, not version-controlled).

## What this is

The world's best Android-TV **viewer**: zero-jank D-pad/TV navigation, instant playback. Kotlin ·
Apollo GraphQL · Media3 · Compose, plus a Room "Folders/New" cache. The GraphQL contract is
**Apollo codegen** from the pinned `stash-server` git submodule (now repointed to the NG fork — the
app is no longer NG-blind). Auth is **API-key** (`ApiKey` header).

### This repo's role in the invariants

**Viewer only — never destructive:** the existing couch-remote `delete_file` path is a **bug to gate**,
not a feature. Contract via **Apollo codegen** from the `stash-server` submodule (repointed to the NG
fork); a server schema change requires repointing/refreshing the submodule and re-running codegen.
Secrets live in **EncryptedSharedPreferences** with **TOFU cert pinning**.

## Hard invariants (NEVER violate)

> These five are **product-wide** and identical across every repo. They are the canonical source;
> do not paraphrase them per-repo. Each repo states *its role* in them under "This repo's role" above.

1. **NG-only.** Upstream `stashapp/stash` is **not** a support target. Never add upstream-fallback
   logic or "missing capability = upstream" assumptions. "Compatibility" means NG-client ↔ NG-server
   version skew and contract drift *between clients* — never upstream compatibility.
2. **One contract, multiple copies.** The schema has one source (`Stash/graphql/schema/**`) and three
   derived copies (tvOS via StashKit; macOS via its own forked strings; Android via Apollo codegen from
   the `stash-server` submodule). A schema change must land in the server **and** every client copy.
   The root `contract/` Go test goes red on drift — keep it green.
3. **Destructive ops are macOS-only**, routed through server mutations so DB + disk stay in sync. Never
   add disk-delete to a viewer (Android's couch-remote `delete_file` is a bug to gate, not a feature).
4. **Secrets in the platform secure store** (Keychain / EncryptedSharedPreferences) — **never
   plaintext.** No telemetry unless explicitly opt-in + local-first.
5. **Trash is OFF on the live server** (`delete_trash_path` unset → deletes are permanent). Don't claim
   deletes are recoverable until that's set.

## Build / test

- Build: `./gradlew assembleDebug`
- Tests: `./gradlew testDebugUnitTest`
- Compile-check a module: `./gradlew :app:compileDebugKotlin -q`

## More

- Backlog: `docs/agent-memory/{bugs,fixes,tests,optimizations,ideas,questions}.md` (local-only) —
  remove entries when their work ships.
