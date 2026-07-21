# eco-folia Upstream Sync (2026.29) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge `Auxilor/eco` 2026.29 into `eco-folia` on a sync branch, preserving the Folia layer and fork infra, then publish.

**Architecture:** Work on `sync/upstream-2026.29`. One `git merge upstream/master` absorbs the already-applied (338) and upstream-only (128) files automatically. Resolve the ~100 both-touched conflicts area-by-area, taking upstream as the base and re-applying the Folia layer from a generated catalog. Gate each area on a compile; land only when the full build is green and the Folia static audit passes.

**Tech Stack:** Git, Gradle (Kotlin DSL), Java 21 / Kotlin, Paper/Folia API.

## Global Constraints

- Fork version scheme: `MAJOR.MINOR.PATCH-folia`. Bump per single-digit carry rule (9 carries left; `7.6.7-folia` → `7.6.8-folia`). Never two-digit segments.
- Commits: no AI attribution trailer (no `Co-Authored-By`, no "Generated with").
- Never resolve a conflict by blind two-diff merge. Upstream code is the base; re-apply the Folia patch from the catalog on top.
- Fork owns and keeps: `.github/workflows/build.yml`, `-folia` version, English `README.md`, GitHub Packages publish. Drop upstream `publish-release.yml` / `CODEOWNERS`.
- Do not touch `master` until the sync branch builds green and passes the Folia audit.
- Dependency order is fixed: eco is published first; libreforge and EcoEnchants are out of scope here.

---

## Task 1: Prep — sync branch + resolution artifacts

**Files:**
- Create branch `sync/upstream-2026.29`
- Create: `docs/superpowers/sync/eco-folia-catalog.diff` (Folia patch catalog)
- Create: `docs/superpowers/sync/eco-both-touched.txt`, `eco-upstream-only.txt`

**Interfaces:**
- Produces: `eco-both-touched.txt` (manual-merge worklist), `eco-folia-catalog.diff` (the Folia changes that must survive), consumed by Tasks 4–8.

- [ ] **Step 1: Verify clean tree and fresh fetch**

Run:
```bash
cd /c/Users/Pablo/Desktop/Desarrollo/Fork/eco-folia
git fetch upstream --quiet
git status --short
```
Expected: no output (clean tree).

- [ ] **Step 2: Create the sync branch from master**

Run:
```bash
git switch -c sync/upstream-2026.29
```
Expected: `Switched to a new branch 'sync/upstream-2026.29'`.

- [ ] **Step 3: Generate resolution artifacts**

Run:
```bash
mkdir -p docs/superpowers/sync
FP=$(git merge-base master upstream/master)
git diff "$FP" master -- '*.kt' '*.java' '*.kts' > docs/superpowers/sync/eco-folia-catalog.diff
: > docs/superpowers/sync/eco-both-touched.txt
: > docs/superpowers/sync/eco-upstream-only.txt
while IFS= read -r f; do
  git diff --quiet master upstream/master -- "$f" 2>/dev/null && continue   # already in sync -> skip
  if git diff --quiet "$FP" master -- "$f" 2>/dev/null; then
    echo "$f" >> docs/superpowers/sync/eco-upstream-only.txt
  else
    echo "$f" >> docs/superpowers/sync/eco-both-touched.txt
  fi
done < <(git diff --name-only "$FP" upstream/master)
wc -l docs/superpowers/sync/eco-both-touched.txt docs/superpowers/sync/eco-upstream-only.txt
```
Expected: both-touched ≈ 113 lines, upstream-only ≈ 128 lines.

- [ ] **Step 4: Commit the artifacts**

Run:
```bash
git add docs/superpowers/sync
git commit -m "chore(sync): eco resolution artifacts (catalog, worklists)"
```
Expected: one commit created.

---

## Task 2: Start the merge and bank the auto-merge

**Files:**
- Modify: repo-wide (merge in progress, not yet committed)

**Interfaces:**
- Consumes: `upstream/master`.
- Produces: an in-progress merge with conflicts staged for Tasks 3–7.

- [ ] **Step 1: Begin the merge without committing**

Run:
```bash
git merge --no-commit --no-ff upstream/master || true
```
Expected: `Automatic merge failed; fix conflicts and then commit the result.`

- [ ] **Step 2: Snapshot the conflict set**

Run:
```bash
git diff --name-only --diff-filter=U | tee docs/superpowers/sync/eco-conflicts.txt | wc -l
git diff --name-only --diff-filter=U | sed -E 's#/[^/]+$##' | awk -F/ '{print $1"/"$2}' | sort | uniq -c | sort -rn
```
Expected: on the order of ~100–170 conflicted files, concentrated in `eco-api/src`, `eco-core/core-backend`, `eco-core/core-plugin`.

- [ ] **Step 3: Verify auto-merged files are staged (dedup worked)**

Run:
```bash
git diff --name-only --cached --diff-filter=U | wc -l   # conflicts are NOT cached
echo "staged clean:"; git diff --name-only --cached | wc -l
```
Expected: staged-clean count is large (hundreds) — the already-applied + upstream-only files merged automatically. No commit yet.

---

## Task 3: Reconcile infra and build files

**Files:**
- Modify: `.github/workflows/build.yml`, `build.gradle.kts`, `settings.gradle.kts`, `eco-api/build.gradle.kts`, `eco-core/*/build.gradle.kts`, `gradle.properties`, `.gitignore`
- Delete (keep upstream's out): `.github/workflows/publish-release.yml`, `.github/CODEOWNERS` if reintroduced

**Interfaces:**
- Produces: a buildable Gradle setup — upstream dependency/module changes folded in, fork publish config kept.

- [ ] **Step 1: Keep fork CI, drop upstream release workflow**

Run:
```bash
git checkout --ours .github/workflows/build.yml && git add .github/workflows/build.yml
git rm -f .github/workflows/publish-release.yml 2>/dev/null || true
git rm -f .github/CODEOWNERS 2>/dev/null || true
```
Expected: build.yml resolved to fork version; upstream-only publish workflow removed.

- [ ] **Step 2: Resolve gradle.properties — keep -folia version, take upstream eco-version bumps of deps**

Open `gradle.properties`. Keep the fork `version = <x.y.z>-folia` line. For every other property (dependency versions), take upstream's value. Remove conflict markers.

- [ ] **Step 3: Resolve build files — upstream deps/modules as base, re-add fork publish blocks**

For each conflicted `*.gradle.kts` and `settings.gradle.kts`: take upstream's plugin/dependency/module declarations as the base, then re-add the fork's `publishing { }` / `maven { url = GitHub Packages }` / artifact-naming blocks from the catalog. Remove markers.

Run after editing:
```bash
git add build.gradle.kts settings.gradle.kts eco-api/build.gradle.kts eco-core/*/build.gradle.kts gradle.properties .gitignore
grep -rn '^<<<<<<<\|^=======\|^>>>>>>>' --include='*.kts' --include='*.properties' . || echo "no markers in build files"
```
Expected: `no markers in build files`.

- [ ] **Step 4: Commit the infra resolution as a checkpoint (merge still open)**

Note: with a merge in progress you cannot make an intermediate commit without finishing the merge. Instead stage and continue; the single merge commit lands in Task 7. Just verify staging:
```bash
git diff --name-only --diff-filter=U | grep -E '\.(kts|properties)$|\.github/' || echo "infra conflicts cleared"
```
Expected: `infra conflicts cleared`.

---

## Task 4: Resolve `eco-api/src` conflicts

**Files:**
- Modify: conflicted files under `eco-api/src` (~55; see `eco-both-touched.txt`)

**Interfaces:**
- Consumes: `eco-folia-catalog.diff`.
- Produces: conflict-free `eco-api` sources that compile.

- [ ] **Step 1: List remaining eco-api conflicts**

Run:
```bash
git diff --name-only --diff-filter=U | grep '^eco-api/' | tee /tmp/api_conflicts.txt
```
Expected: the eco-api conflict list.

- [ ] **Step 2: Resolve each file — upstream base, re-apply Folia**

For each file in `/tmp/api_conflicts.txt`, open it and for every conflict hunk:
- Take the upstream (`>>>>>>> upstream/master`) side as the base.
- If the catalog (`eco-folia-catalog.diff`) shows a Folia/concurrency change to that file (e.g. `ConcurrentHashMap`, `.scheduler.run`, region-thread dispatch, non-negative repair cost, TeamUtils hardening), re-apply that change onto the upstream base. If upstream refactored the surrounding code, re-derive the change to fit the new shape — preserve intent, not literal lines.
- If the hunk is pure feature/logic with no catalog entry, keep upstream unchanged.
- Remove all conflict markers.

Reference the catalog for a specific file:
```bash
git --no-pager diff "$(git merge-base master upstream/master)" master -- eco-api/src/main/java/com/willfp/eco/util/TeamUtils.java
```

- [ ] **Step 3: Stage and verify no markers in eco-api**

Run:
```bash
git add eco-api/
grep -rn '^<<<<<<<\|^>>>>>>>' eco-api/ || echo "eco-api clean"
```
Expected: `eco-api clean`.

- [ ] **Step 4: Compile eco-api**

Run:
```bash
./gradlew :eco-api:compileJava :eco-api:compileKotlin --stacktrace 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`. Fix compile errors from upstream API drift before proceeding.

---

## Task 5: Resolve `eco-core/core-backend` conflicts

**Files:**
- Modify: conflicted files under `eco-core/core-backend` (~27)

**Interfaces:**
- Consumes: `eco-api` (Task 4), catalog.
- Produces: compiling `core-backend`.

- [ ] **Step 1: List remaining core-backend conflicts**

Run:
```bash
git diff --name-only --diff-filter=U | grep '^eco-core/core-backend/' | tee /tmp/backend_conflicts.txt
```

- [ ] **Step 2: Resolve each file — upstream base, re-apply Folia**

Same rule as Task 4 Step 2. Backend hotspots from the catalog: `EcoDropQueue` / `EcoFastCollatedDropQueue` (don't clear every tick), `EcoEventManager` (thread-safe maps), `MenuHandler` / `RenderedInventory` (region-thread menu work), `DefaultMap`/`ListMap` (mutable/thread-safe). Take upstream logic, re-apply these Folia/concurrency changes.

- [ ] **Step 3: Stage and verify**

Run:
```bash
git add eco-core/core-backend/
grep -rn '^<<<<<<<\|^>>>>>>>' eco-core/core-backend/ || echo "core-backend clean"
```
Expected: `core-backend clean`.

- [ ] **Step 4: Compile core-backend**

Run:
```bash
./gradlew :eco-core:core-backend:compileKotlin --stacktrace 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 6: Resolve `eco-core/core-plugin` conflicts

**Files:**
- Modify: conflicted files under `eco-core/core-plugin` (~22)

**Interfaces:**
- Consumes: `eco-api`, `core-backend`, catalog.
- Produces: compiling `core-plugin`.

- [ ] **Step 1: List remaining core-plugin conflicts**

Run:
```bash
git diff --name-only --diff-filter=U | grep '^eco-core/core-plugin/' | tee /tmp/plugin_conflicts.txt
```

- [ ] **Step 2: Resolve each file — upstream base, re-apply Folia**

Same rule. Plugin hotspots from the catalog: `EcoSpigotPlugin` (scheduler init / region setup), `PlayerHealthPatch`, `PlayerJumpListeners`, recipe listeners, `ExpressionEvaluator`, custom-block/custom-item integrations. Take upstream, re-apply region-thread/concurrency changes; ExcellentShop integration stays on the fork's 5.x rewrite; AnticheatNCP stays removed.

- [ ] **Step 3: Stage and verify**

Run:
```bash
git add eco-core/core-plugin/
grep -rn '^<<<<<<<\|^>>>>>>>' eco-core/core-plugin/ || echo "core-plugin clean"
```
Expected: `core-plugin clean`.

- [ ] **Step 4: Compile core-plugin**

Run:
```bash
./gradlew :eco-core:core-plugin:compileKotlin --stacktrace 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 7: Clear residual conflicts, full build, finish merge

**Files:**
- Modify: any remaining conflicted files (nms, misc)

**Interfaces:**
- Produces: a single merge commit on `sync/upstream-2026.29` with a green full build.

- [ ] **Step 1: Resolve anything left**

Run:
```bash
git diff --name-only --diff-filter=U
```
For each remaining file apply the Task 4 rule (upstream base + re-apply Folia). For `modify/delete` (e.g. removed NMS version dirs) prefer upstream's deletion unless the file is a fork-only NMS version (`1.21.11`) — keep those. Then:
```bash
git add -A
git diff --name-only --diff-filter=U || echo "no conflicts remain"
```
Expected: `no conflicts remain`.

- [ ] **Step 2: Full build**

Run:
```bash
./gradlew build --stacktrace 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`. Fix remaining compile errors module by module until green.

- [ ] **Step 3: Finish the merge commit**

Run:
```bash
git commit -m "merge: sync upstream Auxilor/eco 2026.29 into folia fork

Base = upstream feature code; Folia scheduler/concurrency layer re-applied.
Dedup: 338 already-applied + 128 upstream-only files auto-merged; ~100
both-touched files resolved by hand."
```
Expected: merge commit created.

---

## Task 8: Folia static audit + catalog checklist

**Files:**
- Read-only verification; may Modify files if audit finds regressions.

**Interfaces:**
- Consumes: `eco-folia-catalog.diff`, merged tree.
- Produces: confidence the Folia layer survived.

- [ ] **Step 1: Grep for reintroduced main-thread scheduler patterns**

Run:
```bash
grep -rnE 'BukkitRunnable|runTaskTimer|runTaskAsynchronously|Bukkit\.getScheduler|\.runTask\(' \
  eco-api/src eco-core/*/src --include='*.kt' --include='*.java' | grep -viE 'folia|region|scheduler\.run' | tee /tmp/eco_audit.txt | wc -l
```
Expected: review each hit in `/tmp/eco_audit.txt`. Any match inside a file the catalog marked Folia-adapted is a regression — re-apply the region-scheduler fix. Legitimately-unchanged upstream call sites are fine.

- [ ] **Step 2: Confirm catalog signatures present**

Run:
```bash
grep -rc 'ConcurrentHashMap' eco-core/core-backend/src | grep -v ':0' | wc -l
grep -rn 'scheduler.run' eco-core/core-plugin/src --include='*.kt' | wc -l
```
Expected: non-zero counts consistent with the catalog (backend concurrency + plugin scheduler dispatch intact).

- [ ] **Step 3: Spot-review highest-risk files**

Open and eyeball for correct Folia handling: `EcoSpigotPlugin.kt`, `EcoFastCollatedDropQueue.kt`, `EcoEventManager.kt`, `MenuHandler.kt`, `PlayerHealthPatch.kt`. Confirm region/global scheduler usage and thread-safe collections match intent.

- [ ] **Step 4: Commit any audit fixes**

Run:
```bash
git add -A && git commit -m "fix(folia): restore scheduler/concurrency intent after merge" || echo "no fixes needed"
```

---

## Task 9: Land and publish

**Files:**
- Modify: `gradle.properties` (version bump)

**Interfaces:**
- Produces: published eco `-folia` artifact for libreforge's next cycle.

- [ ] **Step 1: Bump the fork version (carry rule)**

Read current: `grep '^version' gradle.properties`. Compute next patch with single-digit carry (e.g. `7.6.7-folia` → `7.6.8-folia`; `7.6.9-folia` → `7.7.0-folia`). Edit `gradle.properties`.

- [ ] **Step 2: Final build on the bumped version**

Run:
```bash
./gradlew build --stacktrace 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit the bump**

Run:
```bash
git add gradle.properties
git commit -m "chore: release <new-version> (upstream 2026.29 sync)"
```

- [ ] **Step 4: Merge sync branch to master**

Run:
```bash
git switch master
git merge --ff-only sync/upstream-2026.29 || git merge --no-ff sync/upstream-2026.29 -m "merge: upstream 2026.29 sync"
```
Expected: master advanced to the synced state.

- [ ] **Step 5: Push and watch CI**

Run:
```bash
git push origin master
gh run list --limit 1 --branch master
```
Expected: `build.yml` run starts; watch to success (`gh run watch <id> --exit-status`). GitHub Packages publish + release jar succeed.

- [ ] **Step 6: Verify downstream resolves the new eco**

Record the published version. Sanity-check that `libreforge-folia/gradle.properties` `eco-version` can be pointed at it in the next cycle (not changed here).

---

## Self-Review notes

- **Spec coverage:** dedup respect (Tasks 1–2 auto-merge), upstream-base+re-apply-Folia (Tasks 3–7), infra kept (Task 3), validation without runtime harness (Task 8), versioning/publish (Task 9), sync-branch workflow (Task 1, 9). All spec sections mapped.
- **No hidden placeholders:** per-file resolution is inherently manual for a merge; the plan gives the exact rule, the catalog artifact, and per-area hotspots rather than fake code blocks.
- **Order:** eco only; libreforge/EcoEnchants explicitly out of scope, consistent with spec.
