# eco-folia — Upstream Sync (Auxilor 2026.29) Design

**Date:** 2026-07-21
**Repo:** eco-folia (`MrNickax/eco-folia`, fork of `Auxilor/eco`)
**Status:** Design — approved decisions recorded; execution in later sessions.

## Goal

Bring `eco-folia` up to date with upstream `Auxilor/eco` (HEAD `2026.29`) while
preserving the Folia adaptations, fork-specific features, and publishing infra that
make this fork usable. eco is the base dependency (`eco ← libreforge ← EcoEnchants`),
so it is synced and published first.

This spec covers **eco only**. libreforge and EcoEnchants get their own spec/plan cycles
afterwards, against the newly published eco.

## Current state (measured 2026-07-21)

- Fork point: `66346fc9` (2026-01-22). Upstream HEAD: `bede5404` (2026.29, 2026-07-18).
- Behind upstream: **232 commits**. Ahead (fork work): **23 commits**.
- File-level sync analysis of the 579 files upstream changed since fork point:

| Bucket | Files | Action |
|---|---|---|
| Already in sync (fork content already matches upstream) | 338 (58%) | Discard — no work |
| Upstream-only (fork never touched) | 128 | Take upstream wholesale |
| Both touched (fork + upstream) | 113 (~100 code) | Manual merge, Folia-sensitive |

"Already in sync" = the fork already carries upstream's change, whether cherry-picked or
manually re-implemented. This is the dedup the sync must respect: **58% of upstream's delta
is already applied and must not be redone.**

## Folia layer to preserve (catalog)

The fork's 23 ahead commits are a thin cross-cutting layer plus a few features. What must
survive any merge:

1. **Scheduler adaptation** — eco's own scheduling abstraction (`RunnableTask`, `*.scheduler.run`)
   backed by Folia region schedulers; per-player work on the owning region thread; console/menu
   dispatch on the global region thread; Display reads off the netty thread fixed.
   Signatures: `.scheduler.run` (~11 sites), region-thread dispatch in listeners.
2. **Concurrency** — `ConcurrentHashMap` and thread-safe collections/event maps (~23 sites),
   mutable-list enforcement, `EcoFastCollatedDropQueue` not cleared every tick, PlayerHealthPatch
   thread-safety, ArmorChange/EntityDeathByEntity listener concurrency.
3. **Platform/features** — NMS `1.21.11` support; ExcellentShop integration rewritten for 5.x;
   AnticheatNCP integration removed; `TeamUtils` scoreboard hardening; non-negative repair cost
   in `EcoFastItemStack`.
4. **Publishing infra** — GitHub Packages publish, `-folia` version scheme, `.github/workflows/build.yml`,
   English README, release jar. These intentionally diverge from upstream CI and are kept fork-side.

The manual-merge worklist concentrates in `eco-api/src` (55), `eco-core/core-backend` (27),
`eco-core/core-plugin` (22). First execution step produces the precise per-file Folia patch
catalog (`git diff <fork-point> master -- <both-touched files>`) as the resolution guide.

## Strategy

**Decisions (approved):**
- Conflict resolution philosophy: **upstream code as the base, re-apply the Folia layer on top** —
  never blind-merge two diffs. Upstream owns feature logic; the fork owns the Folia/concurrency layer.
- Work on a **sync branch** (`sync/upstream-2026.29`), not master. Master stays intact and buildable
  until the branch is green and validated.
- Merge mechanic: `git merge upstream/master`. Auto-merge absorbs the 338 in-sync + 128 upstream-only
  files (automatic dedup). Human effort = the conflicting hunks only.

## Execution outline (phased)

Detailed task breakdown goes in the implementation plan; phases:

1. **Prep** — create `sync/upstream-2026.29` from master; generate the Folia patch catalog and the
   both-touched file list as resolution artifacts.
2. **Merge start** — `git merge --no-commit --no-ff upstream/master`; let auto-merge run.
3. **Resolve conflicts by area** — walk `eco-api` → `core-backend` → `core-plugin` → build files.
   Per conflicted file: take upstream hunk as base, re-apply the matching Folia patch from the catalog.
   Bucket trivial conflicts (version strings, headers, imports) for fast resolution.
4. **Infra reconciliation** — keep fork `build.yml`, `-folia` version, README; drop upstream's
   `publish-release.yml`; reconcile `build.gradle.kts` / `settings.gradle.kts` (upstream deps + fork publish).
5. **Compile fixes** — resolve breakages from upstream API changes across the 232 commits, module by module.
6. **Validation** (see below).
7. **Land** — commit merge on the sync branch; bump version per house carry rule; open/merge to master; CI publishes; verify the package is consumable.

## Conflict resolution rules

- **Feature/logic conflict** → take upstream, then re-apply any Folia scheduler/concurrency change that
  targeted that code (from the catalog). If upstream refactored the code the Folia patch targeted,
  re-derive the patch against the new shape — preserve intent, not literal lines.
- **Pure Folia file** (scheduler/concurrency) → keep fork intent; fold in any upstream change to the same file.
- **Infra file** (CI, version, README, publish) → keep fork.
- **Already-applied appearing as conflict** (both sides made the same change) → either side; take upstream.
- When unsure whether a hunk is feature or Folia, default to upstream and flag for review.

## Validation (no Folia runtime harness)

- `./gradlew build` green for every module.
- **Static Folia audit:** grep the merged tree for reintroduced main-thread/Bukkit-scheduler patterns
  that the fork had removed (`BukkitRunnable`, `runTask(`, `runTaskTimer`, direct `Bukkit.getScheduler`)
  in files the catalog marks as Folia-adapted; confirm scheduler abstraction is intact.
- Confirm every catalog item is still present in the merged tree (checklist).
- Spot-review the highest-risk files (`EcoSpigotPlugin`, drop queues, event manager, menu handler).
- Downstream smoke: after publish, confirm libreforge-folia still resolves and builds against the new eco.

## Versioning & publish

- Keep the fork's `MAJOR.MINOR.PATCH-folia` scheme; bump per the single-digit carry house rule.
- Push to master triggers `build.yml` → GitHub Packages + release jar.
- Record the published eco version; it becomes libreforge's `eco-version` in the next cycle.

## Risks

- **Silent feature loss** — a Folia-side conflict resolution that discards an upstream feature. Mitigated
  by upstream-as-base philosophy + catalog checklist + spot review.
- **Reintroduced main-thread calls** — an upstream refactor reverting a scheduler swap. Mitigated by static audit.
- **API-change cascade** — 232 commits of API drift breaking fork code. Handled in the compile-fix phase, module by module.
- **Scale** — ~100 manual-merge code files; multi-session. Sync branch keeps it interruptible and reviewable.

## Out of scope

- libreforge and EcoEnchants sync (separate cycles, after eco is published).
- Runtime/gameplay testing on a live Folia server (owner-side; no harness in repo).
- Adopting upstream's CalVer scheme (fork keeps `-folia` semver).
