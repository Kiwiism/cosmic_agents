# Bot-Scope Audit Pass

> **Resolution (applied 2026-06-02).** Compiles clean; changes confined to `server.bots.*` except the one approved upstream fix (`MapFactory`).
> **Applied — lossless/safe:** 2.1 grind-block extraction (`tickGrindMode`, single SSOT body reusing the `LocalOpportunityAttackResult` consumed/targetPos protocol), 2.2 `runCommonTickSystems` (single body, allocation-free `if (perf)` timing guards) + `tickStuckDetection`/`doStuckDetection`, 2.3 `broadcastMovement`/`doBroadcastMovement`, 2.4 WZ DOM helpers → new `BotWzXml` (static-import, zero call-site churn; `parseXmlDocument` left per-provider to preserve distinct log strings), 2.5/2.6/2.7/2.8 dead-code removals, 2.9 tick-lambda `BotEntry` capture, 2.12 dead first-pass scan.
> **Applied — behavior change (approved):** 3.2 `forbidFallDown` in `MapFactory`; 3.3 heal-bbox reuse + null-guard; 3.4 — **revised fix** per owner direction: added `BotNavigationGraphProvider.peekBestGraph(map, profile)` SSOT (exact-then-closest) and routed the 4 profile-aware `peekGraph(map)` callers through it (region adjacency / stored `patrolRegionId` lookups in `BotInventoryManager:findNearestPatrolLootTarget`, `resolvePatrolWanderTarget`, `issuePatrol`, owner-wander). The 2 profile-less callers (`clampedOnOwnerRegion`, `resolveWalkRegionLookup`) keep `peekGraph(map)` — they use only profile-invariant region geometry. Also collapsed the open-coded exact-then-closest duplicates (`resolveActiveGraph`, `BotManager:3160`, `BotShopManager`) into `peekBestGraph`. The workflow's "index by mapId" proposal was **not** used — it only makes the arbitrary pick deterministic, not correct.
> **Deferred (with rationale):** 2.10 (`byBotCharId` index) and 2.11 (wall-by-X index) — both add parallel cached/index state to keep synchronized for marginal, non-hot-path (or build-time-amortized) benefit, contradicting the SSOT/simplicity guidance; flagged not applied. 3.1 — owner declined (MapleMap drop logic stays). 3.5 (A* heuristic) — **measured, then applied as `h=0`.** Added measurement tooling (`runSearch` zeroHeuristic flag, `measureOptimality`, `BotNavigationProbe --measure`). On Kerning City (map 103000000, 186 regions, 8 portals): **19.4%** of reachable region pairs took a non-optimal path under the legacy dx heuristic, **1,663** of them walking past a usable portal; worst cases 2–3× longer (e.g. region 1→149 = +206%). The admissible `h=0` (Dijkstra) search fixes all of these at **1.64×** node expansions (1,657→2,720 avg) — cheap, since pathfinds fire on retarget, not per tick. **Resolution:** production defaults to `h=0` via `BotNavigationManager.useAdmissibleHeuristic = true`; the legacy `heuristic()` and the `zeroHeuristic` branch are retained behind that single toggle. Portal-aware admissible bound left as a future option if pathfind CPU ever shows in profiling.

---


Wide refactor / optimization / bug-hunt pass across the `server.bots.*` cluster and the upstream code it depends on. Every finding below is a confirmed finding (verified by direct code reading); nothing here is speculative.

## 1. Summary

### By category

| Category | Count |
|----------|-------|
| bug | 3 |
| perf | 6 |
| ssot | 7 |
| upstream | 2 |
| player-flow | 0 |
| **Total** | **18** |

### By severity

| Severity | Count |
|----------|-------|
| high | 1 |
| medium | 2 |
| low | 15 |
| **Total** | **18** |

### Split by safety

| Group | Count |
|-------|-------|
| Safe to apply (behaviorChange = false) | 13 |
| Requires user approval (behaviorChange = true) | 5 |

---

## 2. Safe to apply (lossless / no behavior change)

These are behavior-preserving refactors, dead-code removals, and lossless micro-optimizations. They can be applied without user sign-off on behavior.

### 2.1 [medium / ssot] Grind-mode tick body is copy-pasted across perf and non-perf branches

- **File:** `src/main/java/server/bots/BotManager.java:2206-2527`
- **Confidence:** 0.90
- **Evidence:** The whole grind decision pipeline is written twice: once under `if (!perf)` (2207-2379) and a verbatim duplicate under `else` (2381-2526), the latter wrapped only by a `System.nanoTime()` try/finally recording `"tick-grind-dispatch"`. Both bodies contain the same executable logic — `seekRangeSq`/target validation, `planAttack`, retarget search, `findNearestGrindLootTarget`, wander fallback, `selectPriorityRangedAttackTarget`, `findCloserThreatMob`, degenerate-band/retreat computation, `resolveAoeReposition`, `attackMonster` (with `degenAttackDone` gate), idle-in-range branch, `selectGrindNavigationTarget`, and the convenient-loot detour. (~150 lines duplicated; not literally byte-identical — the perf branch strips some comments — but the executable logic is identical.)
- **Why it matters:** Any future grind fix must be applied in both copies or they silently diverge into a latent correctness bug on safety-critical combat code. Notably, the codebase already uses the correct "call shared method + optional timing wrapper" pattern everywhere else (`tickIdleEntry`, `tickAnchoredFarm`, `stepMovementCore`); the grind block is the lone anomaly.
- **Proposed fix:** Extract the shared body into a single private method (e.g. `tickGrindMode(entry, bot, botPos, targetPos, runAiTick, perf)` returning resolved `targetPos` or a "consumed" sentinel to preserve the early-`return` vs fall-through-to-`stepMovementCore` protocol), and wrap the single call site in the `BotPerformanceMonitor` timing block.

### 2.2 [low / ssot] `tickStuckDetection` and `runCommonTickSystems` duplicate their full body for perf instrumentation

- **File:** `src/main/java/server/bots/BotManager.java:3924-4000, 3475-3581`
- **Confidence:** 0.95
- **Evidence:** `tickStuckDetection` (3924-4000) writes identical unstuck-cooldown / moved-check / unstuck-trigger logic in the non-enabled arm (3925-3960) and the `nanoTime`-wrapped arm (3962-3999); only the `record("stuck-detect", ...)` differs. `runCommonTickSystems` (3475-3581) likewise invokes the same ordered subsystem sequence twice (tickMobDamage → dead-state return → tickReleaseMonsterControl → trade-gated tickPassiveLoot → tickPotionCheck → tickPassiveRecovery → checkLevelUp → tickAfkCheck → tickTrade → tickManualTrade → BotPqHooks.tick → tickScriptTasks → npc-lock return → tickActionLock → runAiTick block → final return), the perf arm only interleaving `nanoTime`/`record` calls.
- **Proposed fix:** Factor the shared body into a private helper called from both arms, or use a single `record(label, Runnable/lambda)` timing wrapper. The `runCommonTickSystems` case needs per-subsystem labels preserved, which a `record(label, lambda)` wrapper handles losslessly.

### 2.3 [low / ssot] `broadcastMovement` duplicates its entire dedup+send body across the two perf-monitor branches

- **File:** `src/main/java/server/bots/BotMovementManager.java:866-924`
- **Confidence:** 0.95
- **Evidence:** The snapshot/dedup/cache-update/send sequence is copy-pasted: once in the `!BotPerformanceMonitor.enabled()` fast path (868-892) and once inside the timed try/finally (897-920). Both grab bot/x/y, build the snapshot, resolve `fhId`, dedup-check the 7 last-broadcast fields, return early on match, otherwise update all cache fields and call `sendMovementPacket`. Only the `record("broadcast-move", ...)` wrapper differs.
- **Proposed fix:** Extract the snapshot+dedup+send body into a private helper (e.g. `doBroadcast(entry)`) called from both branches; keep only the timing wrapper duplicated.

### 2.4 [low / ssot] Duplicated XML-parsing helpers across the two bot WZ providers

- **File:** `src/main/java/server/bots/combat/BotAttackDataProvider.java:857-866` (and `BotMobHitboxProvider.java:161-209`)
- **Confidence:** 0.70
- **Evidence:** Both providers carry private copies of the same four WZ DOM helpers: `parseXmlDocument`, `findNamedChild`, `getIntAttribute`, `getIntValue`. **Caveat:** these are functionally equivalent but **not** byte-for-byte identical as the original evidence stated — `parseXmlDocument` differs in its log string; `findNamedChild` differs structurally (one delegates to `getNamedChildren()`, the other inlines a sibling walk); `getIntAttribute` differs in its blank-attribute guard. All variants return the same values, so extraction is lossless.
- **Proposed fix:** Extract the four DOM helpers into a single package-private utility (e.g. `BotWzXml`) in `server.bots.combat`; both providers live in that package.

### 2.5 [low / ssot] Dead private `isBasicAttackInRange` and unused `toAttackSpeedFactor` passthrough

- **File:** `src/main/java/server/bots/BotAttackExecutionProvider.java:668-674, 751-753`
- **Confidence:** 0.95
- **Evidence:** `private static boolean isBasicAttackInRange(Point,Point)` (668) has no caller — the live range check is the identical private copy in `BotCombatManager` (1589-1595), used at 1160. `private static float toAttackSpeedFactor(int)` (751) is a passthrough to `BotAttackTiming` with no caller. Both are `private static`, so no external/reflective reach is possible.
- **Proposed fix:** Delete both unused private methods. (Follow-up: the remaining duplicate `isBasicAttackInRange` across the two files could be unified into one helper.)

### 2.6 [low / ssot] Dead `estimatePlanDamage` helper in `BotCombatManager`

- **File:** `src/main/java/server/bots/BotCombatManager.java:1138-1140`
- **Confidence:** 0.97
- **Evidence:** `private static double estimatePlanDamage(Character bot, AttackPlan attackPlan) { return scoreAttackPlan(bot, attackPlan).usefulDamage; }` — a repo-wide grep returns only the definition, zero callers. Plan scoring goes directly through `scoreAttackPlan` / `selectBestAttackPlan`.
- **Proposed fix:** Remove `estimatePlanDamage`.

### 2.7 [low / ssot] Dead code: private `findAirLanding` never called

- **File:** `src/main/java/server/bots/BotPhysicsEngine.java:1543-1549`
- **Confidence:** 0.97
- **Evidence:** `private static JumpLanding findAirLanding(...)` — repo-wide grep returns only the definition (1543), zero call sites, no test/reflection references. Its LAND-branch `JumpLanding` construction is duplicated inline elsewhere (e.g. 2225-2226). (Minor: the method *invokes* `resolveAirCollision`, it does not duplicate it.)
- **Proposed fix:** Delete the unused `findAirLanding` method (final grep confirm before removing).

### 2.8 [low / perf] `variantOffset` computed but never used in attack-data builders

- **File:** `src/main/java/server/bots/BotAttackExecutionProvider.java:76, 123`
- **Confidence:** 0.98
- **Evidence:** Both `buildBasicAttackDataFromProfile` (76) and `fallbackBasicAttackData` (123) contain `int variantOffset = Math.max(0, attackSpec.actions().indexOf(action));`, an O(n) list scan whose result is never read — the sampled `action` string is used directly. (The unrelated `variantOffset` at 545-546 is a separate local that *is* consumed.)
- **Proposed fix:** Delete both `variantOffset` locals.

### 2.9 [low / perf] Per-tick O(n) registry lookup in `tick()` via `getBotEntry`

- **File:** `src/main/java/server/bots/BotManager.java:1918-1931, 373-380`
- **Confidence:** 0.85
- **Evidence:** Every 50 ms tick re-resolves the `BotEntry` via `getBotEntry(ownerCharId, botCharId)` = `ConcurrentHashMap.get(ownerCharId)` + linear scan of the owner's `CopyOnWriteArrayList` comparing `e.bot.getId()`. The scheduled task (`registerBotInternal` 462-464) is bound to exactly one `BotEntry`; `BotEntry.bot` and `.task` are `final`, never reassigned, and the task is cancelled on every removal/replace path. So a captured entry equals the scanned one.
- **Proposed fix:** Capture the `BotEntry` directly in the scheduled lambda (`() -> tick(entry)`); fall back to the registry scan only for test hooks. (One narrow non-design-path cancellation-race edge exists, but in steady state the result is identical.)
- **Note:** Confirmed lossless for all normal (non-cancelled) ticks; the only divergence is a cancellation-vs-execution race that is not the design path.

### 2.10 [low / perf] `getActiveOwnerByBotCharId` / `handlePendingLootOfferResponse` scan all owners' bot lists

- **File:** `src/main/java/server/bots/BotManager.java:638-647, 719-725, 1036-1047`
- **Confidence:** 0.70
- **Evidence:** `getActiveOwnerByBotCharId` (638-647) double-nested scans `bots.values()` to find a bot by char id; invoked from `requestBotPotionCheckSoon`, `isItemFromOwnedBot` (recipient trade-item gain), `findOwnerlessBot`, `spawnBotForOwner`, and `BotLootEligibility`. No reverse `botCharId → BotEntry/owner` index exists.
- **Proposed fix:** Maintain a `ConcurrentHashMap<Integer,BotEntry> byBotCharId` updated in register/remove/dismiss/give paths and resolve through it. **Scope the fix to `getActiveOwnerByBotCharId` only** — `handlePendingLootOfferResponse` also calls `expirePendingOffer(entry)` as an all-entries side effect during its sweep and cannot be replaced by a single lookup without losing that expiry pass.
- **Note:** Real and lossless, but N (online companion bots per owner) is tiny and call sites are not per-tick combat loops, so practical benefit is marginal.

### 2.11 [low / perf] `isBlockedWallBoundaryLaunch` scans ALL footholds on every launch-X probe during graph build

- **File:** `src/main/java/server/bots/BotNavigationGraphProvider.java:1507-1531` (callers 1341, 1378, 1402-1409)
- **Confidence:** 0.90
- **Evidence:** The method iterates `map.getFootholds().getAllFootholds()` in full, filtering by `foothold.getX1() != launchPoint.x`. Called from `validateDownJumpLaunchX` (1378) and `isApproachableJumpLaunchX` (1407), both inside the exponential+binary-search boundary loops run per anchor and per launch probe → O(anchors · probes · footholds). Worse: `getAllFootholds()` allocates a new `ArrayList` and walks the whole quadtree each call. (`getCachedCollidableWallIds` is already cached; the per-x filtering is not.)
- **Proposed fix:** In `buildGraph`, precompute a `Map<Integer,List<Foothold>>` of collidable wall footholds keyed by `getX1()` (walls are vertical, `x1==x2`); have the method look up only walls at `launchPoint.x` and keep the inner Y-range check. Pure indexing change, identical results.
- **Note:** Graph-build-time work (gated/amortized/cached), not a steady-state per-tick hot loop — hence low severity.

### 2.12 [low / perf] `findWalkRegionGroundSample` iterates `region.segments` twice per call

- **File:** `src/main/java/server/bots/BotPhysicsEngine.java:435-497`
- **Confidence:** 0.80
- **Evidence:** A first full pass (454-463) over `region.segments` exists *only* to set boolean `foundContainingSegment`, gated solely by `segment.containsX(x)`; the `distanceToSegmentX(segment, x)` call in that pass is dead (its `dx` only feeds a `continue` that never affects the flag). A second full pass (465-486) does the actual best-segment selection. Runs inside `previewGroundStep`, on the per-tick grounded path (`canWalkGroundStep` / `simulateGroundMotion` plus walk-off loops).
- **Proposed fix:** Keep a separate cheap precompute pass that calls only `containsX` (skip `distanceToSegmentX` in pass 1), keep the tie-break unchanged. **Important:** only the precompute-pass variant is lossless — a naive lazy single-loop merge would change selection.

### 2.13 [low / upstream] Monster control excludes `BotClient` via `instanceof` check

- **File:** `src/main/java/server/life/Monster.java:1123-1124`
- **Confidence:** 0.95
- **Evidence:** Controller selection filters bots with `if (!chr.isHidden() && !(chr.getClient() instanceof BotClient))`. The original concern flagged this as upstream drift; verification concluded the **opposite**: this check centralizes bot-exclusion into the upstream SSOT (`getNextControllerCandidate`), replacing ~20 lines of incomplete duplicate logic in `BotManager.tickReleaseMonsterControl` with 3 lines. It does not alter observable behavior (bots were already excluded) and respects all selection preferences (alive/puppet, no hidden-GM hand-off).
- **Proposed fix:** No code change required — this is a correct consolidation, recorded here for completeness. (If purity is desired later, abstract eligibility onto `Client`/`Character`, but this is not a fix-now item.)

---

## 3. Requires user approval (behavior change)

These findings alter observable behavior, even if subtly or only at the margin. Per the standing instruction, do **not** apply without explicit user confirmation.

### 3.1 [high / upstream] Item drop logic conditionalized on `UNTRADEABLE_ITEMS_TRADEABLE` config

- **File:** `src/main/java/server/maps/MapleMap.java:2265-2266`
- **Confidence:** 0.95 — **behaviorChange: true**
- **Evidence:** `spawnItemDrop()` changed the condition from `if (FieldLimit.DROP_LIMIT.check(...))` to `if (FieldLimit.DROP_LIMIT.check(...) && !YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE)`. This couples a core map-drop mechanic (DROP_LIMIT, which discards items on maps like the free market) to an item-tradeability config that is primarily a bot-testing toggle. When `UNTRADEABLE_ITEMS_TRADEABLE=true`, drops now persist on maps that normally discard them.
- **Proposed fix:** Remove the `UNTRADEABLE_ITEMS_TRADEABLE` condition from `MapleMap`. If bots need drops on restricted maps, handle it in bot-specific code (a bot-map wrapper / temporary drop-right grant), or introduce a dedicated bot-exemption flag — do not patch core drop logic with a trading toggle.
- **Why approval needed:** Directly changes drop persistence on drop-limited maps for all players whenever the config flag is set.

### 3.2 [medium / bug] Live map loader never sets `forbidFallDown`; runtime nav graph ignores it

- **File:** `src/main/java/server/maps/MapFactory.java:198-228` (compare `BotNavigationMapLoader.java:116`)
- **Confidence:** 0.90 — **behaviorChange: true**
- **Evidence:** `MapFactory.loadMapFromWz`'s foothold loop sets only x1/y1/x2/y2/prev/next and never calls `setForbidFallDown`, so every live-map `Foothold` has `forbidFallDown=false`. The runtime graph (`BotNavigationGraphProvider.buildGraph` → `map.getFootholds().getAllFootholds()`) builds from these footholds; `Segment` captures the flag via `foothold.isForbidFallDown()` (`BotNavigationGraph.java:58`). With the flag always false, `addDirectionalDropEdge` (977) and `validateDownJumpLaunchX` (1382) never suppress drops on no-fall platforms. Only the offline probe path (`BotNavigationMapLoader.java:116`) sets the flag, so probe and production graphs diverge — in-game bots will walk/down-jump off platforms the map author marked no-fall.
- **Proposed fix:** Add `fh.setForbidFallDown(DataTool.getInt(footHold.getChildByPath("forbidFallDown"), 0) != 0);` in `MapFactory.loadMapFromWz`'s foothold loop, mirroring `BotNavigationMapLoader`. Minimal upstream change that fixes the probe-vs-production divergence at its root.
- **Why approval needed:** Changes bot pathing — suppresses drop/down-jump edges on no-fall platforms in production.

### 3.3 [low / bug] `getUndeadMobsInHealRange` recomputes heal bounding box already computed by caller

- **File:** `src/main/java/server/bots/BotCombatManager.java:825-838`
- **Confidence:** 0.70 — **behaviorChange: true**
- **Evidence:** `tickSupportHealing` computes `healBounds = fx.hasBoundingBox() ? fx.calculateBoundingBox(bot.getPosition(), bot.isFacingLeft()) : null` (719), then `getUndeadMobsInHealRange` recomputes `fx.calculateBoundingBox(bot.getPosition(), bot.isFacingLeft())` with identical args (826). Redundant once-per-heal-tick `Rectangle` allocation, plus a latent two-source-of-truth risk on facing/anchor.
- **Proposed fix:** Pass `healBounds` into `getUndeadMobsInHealRange` (with a null guard = no bbox) instead of recomputing.
- **Why approval needed:** The proposed null guard changes the no-bbox edge case. `getUndeadMobsInHealRange` is called unconditionally before the early-return at 725, and `StatEffect.calculateBoundingBox` dereferences lt/rb without a null check — so a heal skill with no bbox currently NPEs every heal tick, and the guard would convert that into "no undead targets". On the real path (Heal 2301002 has a WZ bbox) it is behavior-neutral.

### 3.4 [low / perf] `peekGraph(MapleMap)` linear-scans the whole `GRAPHS` map by `mapId`

- **File:** `src/main/java/server/bots/BotNavigationGraphProvider.java:316-326`
- **Confidence:** 0.80 — **behaviorChange: true**
- **Evidence:** `peekGraph(MapleMap)` iterates the entire `GRAPHS` (`ConcurrentHashMap<GraphCacheKey, BotNavigationGraph>` keyed by mapId+speed+jump) returning the first entry whose `key.mapId()` matches → O(total cached graphs), growing as profiles warm. Called on many hot paths (BotManager ×6, BotPhysicsEngine, BotInventoryManager, etc.).
- **Proposed fix:** Maintain a `Map<Integer,BotNavigationGraph> byMapId` updated at the two `GRAPHS.put` sites (379, 411); read it directly in `peekGraph(MapleMap)`.
- **Why approval needed:** Current code returns the *first* entry in arbitrary `ConcurrentHashMap` iteration order; the index returns the *last-written* profile's graph. When multiple profiles are cached for one map, per-profile graphs differ (speed/jump change reachability), so the specific graph returned can change. Common single-profile-per-map case is unaffected.

### 3.5 [low / bug] A* heuristic counts only horizontal distance / walk speed (inadmissible)

- **File:** `src/main/java/server/bots/BotNavigationManager.java:1206-1222`
- **Confidence:** 0.60 — **behaviorChange: true**
- **Evidence:** `heuristic()` returns `intraRegionTravelCost(from, target)` = `|dx| * 1000 / walkVelocityPxs` with no Y term. The decisive inadmissibility source is same-map PORTAL edges (`BotNavigationGraphProvider.java:1794`, cost=0 between regions whose X can be arbitrarily far apart): a node next to a portal whose endpoint is near the target has tiny true remaining cost but a huge dx-based heuristic. The A* early-exit `if (bestGoalState != null && current.score >= bestGoalCost) break;` (835) relies on f-scores being lower bounds; the inflated node trips the break before being expanded, so a non-optimal path can be returned. (Note: the description's climb-dominant rationale is weak — CLIMB_SPEED ~100 < walk ~125 makes climb portions *under*-estimate; portals are the robust cause.)
- **Proposed fix:** Make the heuristic an admissible lower bound (divide dx by the fastest horizontal speed, drop or use fastest-vertical term, or scale down to guarantee underestimation). Verify against existing path-probe traces so common-case routes are unchanged.
- **Why approval needed:** Any admissibility fix alters route selection in affected portal cases.

---

## 4. Recommended next steps

1. **Apply the Safe group first (Section 2).** These are mechanical and behavior-preserving. Suggested order: dead-code deletions (2.5, 2.6, 2.7, 2.8) → perf micro-opts (2.9, 2.11, 2.12) → SSOT de-duplication refactors (2.1, 2.2, 2.3, 2.4). The grind-block extraction (2.1) is the highest-value maintainability win and the most error-prone; do it carefully with the consumed-sentinel return protocol and run the bot combat tests before/after.
2. **Surface the behavior-changing group (Section 3) to the user for a go/no-go**, highlighting 3.1 (high — core drop logic coupled to a trading toggle) and 3.2 (medium — production bots ignore `forbidFallDown`) first; these are the two genuine correctness defects.
3. **For each Safe SSOT extraction, route per-subsystem timing through a single `record(label, lambda)` wrapper** so the "call shared method + optional timing wrapper" pattern (already used for `tickIdleEntry`/`tickAnchoredFarm`/`stepMovementCore`) becomes uniform across `BotManager`/`BotMovementManager`.
4. **Treat 2.13 as already-correct** (no action) and 2.10 as marginal (defer unless profiling shows it).
5. **Verify before/after** with the bot navigation path-probe traces (for 2.11, 2.12, 3.2, 3.5) and the bot combat suite (for 2.1, 2.5, 2.6, 3.3) to confirm losslessness where claimed.
