# eco-folia upstream sync (2026.29) — progress ledger

Branch: `sync/upstream-2026.29` (from master @ 976a5a9c). Merge IN PROGRESS (uncommitted).
Recover: `git merge --abort` discards everything; artifacts + this ledger persist on disk.
Plan: docs/superpowers/plans/2026-07-21-eco-upstream-sync.md. Spec: docs/superpowers/specs/2026-07-21-eco-upstream-sync-design.md.

## Done
- Task 1: sync branch + artifacts (catalog, both-touched, upstream-only) committed @ 976a5a9c.
- Task 2: `git merge --no-commit --no-ff upstream/master` started. 184 files auto-merged (dedup worked). 54 conflicts.
- Task 3: infra resolved & staged — build.yml=ours, publish-release.yml+CODEOWNERS removed, .gitignore=ours.
- Build files resolved & staged (decisions below).
- Non-content conflicts resolved: Paste.java deleted (upstream), v26_2 packet kept (upstream), 3x v1_21_8 AA added (identical).

## Resolution decisions locked (fork-side kept vs upstream)
- version: `7.6.7-folia` (fork scheme).
- caffeine: `compileOnly` + not-shaded (FORK — deliberate, so eco plugins access directly).
- eco-api dep: `paper-api` not spigot-api (FORK — Folia needs Paper).
- publishing: FORK only — eco-api → GitHub Packages MrNickax/eco-folia; root plugin → Releases. Dropped all Auxilor publishing + publishToAuxilor task.
- craft-engine: `26.6.2` (UPSTREAM — newer).
- IridiumSkyblock: local jar in lib/ (FORK — no longer on maven).
- excellentshop/nightcore: `5.1.3 / 2.16.3` (FORK — rewrote integration for 5.x).
- settings: added `v26_2` NMS module include (UPSTREAM).
- Removed duplicate kotlin relocate block (auto-merge artifact).

## Progress: 37/54 conflicts resolved (17 remain)
Batch-resolved (small/medium): all eco-api util/gui/core small hunks, discord/*, integrations imports,
MenuHandler, RenderedInventory (combined imports+decls, deduped UUID). Decisions:
- caffeine→EcoCache/Duration migration files (StringUtils, PatternUtils, Items): took UPSTREAM.
- TeamUtils.java header: combined javadoc + @SuppressWarnings. EcoPlugin: kept fork TeamUtils.init.
- ProxyConstants: added v26_2. Display.kt: kept fork Bukkit import (FLAG for audit).
REMAINING 17 (substantial/high-risk — NOT yet resolved):
  eco-api: Blocks.java, CustomBlock.java, TestableBlock.java, Recipes.java,
    ShapedCraftingRecipe.java, ShapelessCraftingRecipe.java, PlayableSound.java
  core-nms: RecipeManager.kt
  core-plugin: DiscordWebhookClient.kt, EcoSpigotPlugin.kt[HR], PlayerHealthPatch.kt[HR],
    CustomBlocksCraftEngine/Nexo/Oraxen.kt, CustomItemsNexo.kt, ExpressionEvaluator.kt, config.yml(coupled w/ HealthPatch)

## Resolution philosophy (for remaining code conflicts)
Base = upstream code (feature-current). Re-apply Folia layer from catalog on top:
scheduler (`.scheduler.run`, region/global dispatch), `ConcurrentHashMap`/thread-safe, paper-api.
Catalog: docs/superpowers/sync/eco-folia-catalog.diff. Reference per file:
`git diff 66346fc9 master -- <file>`.

## Remaining conflicts (40 code files) — resolve then finish merge (Task 7)
HIGH-RISK (user wants to review):
- eco-core/.../spigot/EcoSpigotPlugin.kt (3 hunks) — scheduler/region init
- eco-core/.../eventlisteners/PlayerHealthPatch.kt (2 hunks) — thread safety
- eco-core/.../gui/menu/MenuHandler.kt, RenderedInventory.kt — region menu work
SUBSTANTIAL:
- eco-api/.../sound/PlayableSound.java (6 hunks, 163 lines)
- eco-core/.../proxy/common/recipes/RecipeManager.kt (128 lines)
- eco-api/.../recipe/Recipes.java (5), blocks/Blocks.java (5), blocks/CustomBlock.java (4)
SMALL (~30 files, 4-15 lines): eco-api util/gui/core + core-plugin discord/integrations + config.yml/plugin.yml.
Full live list: `git diff --name-only --diff-filter=U`.

## MERGE COMPLETE — all 54 conflicts resolved, `./gradlew build -x test` GREEN.
Post-merge compile fixes: removed dup field Recipes.lastScheduledRegistration; deduped imports
MenuHandler (WeakHashMap), EcoSpigotPlugin (DiscordManager/DiscordIntegrationImpl).
High-risk resolutions: PlayerHealthPatch = upstream feature (config duration/interval + getNewHealth %)
re-scheduled on player.scheduler.runAtFixedRate (entity, Folia-safe) instead of global runTimer.
EcoSpigotPlugin = kept fork wildcard imports (cover upstream's new explicit classes).
RecipeManager = upstream Bukkit API (obsoletes fork NMS hack). PlayableSound = upstream AbstractPlayableSound
(covers fork's variable-pitch intent). caffeine→EcoCache files = upstream.

## FOLIA GAP — NEW upstream feature not yet Folia-safe (adapt before landing to master)
New files from upstream (absent in fork), use raw Bukkit scheduler (throws on Folia):
- eco-core/core-plugin/.../recipes/workstation/BrewingPacketHandler.kt (Bukkit.getScheduler runTask/runTaskLater/runTaskTimer, server.scheduler.runTaskTimer)
- eco-core/core-plugin/.../recipes/workstation/GrindstonePacketHandler.kt (Bukkit.getScheduler.runTask)
- eco-core/core-plugin/.../recipes/workstation/WorkstationRecipeListener.kt (server.scheduler.runTask)
Fix: route through region scheduler at the workstation block location (Bukkit.getRegionScheduler().run(plugin, location, ...))
or player entity scheduler for player.updateInventory. NOT a regression (feature is new).

## ECO SYNC COMPLETE ✅ (2026-07-21)
- Workstation Folia fix committed (157fe05c). Static audit CLEAN. build -x test + test both green.
- Released 7.6.8-folia. Merged sync branch to master (d784ac8e). Pushed. CI green: published to
  GitHub Packages (MrNickax/eco-folia) + release jar. Run 29853299771 success.

## NEXT CYCLES (separate spec/plan, not done this session)
- libreforge-folia: 232 behind. Bump its gradle.properties eco-version 7.6.3-folia -> 7.6.8-folia.
  Same method: dedup analysis, sync branch, upstream base + re-apply Folia. Biggest (228 both-touched core/common).
- EcoEnchants-folia: 120 behind. After libreforge published.
