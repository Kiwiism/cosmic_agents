# Server Player Scale Implementation Plan

Goal: improve the server path toward 500 concurrent real players while the
Agent reconstruction continues separately.

This plan is intentionally server-only. It should avoid `server.agents`,
`server.bots`, Agent command behavior, Agent combat/movement/loot behavior, and
BotClient-specific semantics unless the change is strictly diagnostic.

Related docs:

- `docs/SERVER_SCALE_TODO.md`
- `docs/SERVER_HARDENING_DIAGNOSTICS.md`
- `docs/SERVER_DEFERRED_SAFE_CHANGES.md`
- `docs/SERVER_DB_INDEX_REVIEW.md`
- `docs/SOAK_TEST_CHECKLIST.md`
- `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`

## Recommendation

Do the safe server-only foundation now:

1. diagnostics and observability.
2. non-behavior hardening.
3. config-only or diagnostics-only tuning.
4. soak-test readiness.

Defer deeper behavior optimization until Agent reconstruction is stable or
until it is isolated behind explicit config flags and proven by focused tests.

Do not chase performance blindly. Add measurement first, then optimize the
paths that actually show cost under load.

## Workstream Rules During Agent Reconstruction

- Do not edit `src/main/java/server/agents`.
- Do not edit `src/main/java/server/bots`.
- Do not change Agent tick, movement, combat, looting, shop, navigation, or
  LLM behavior.
- Avoid direct `BotClient` behavior changes unless the change is read-only
  diagnostics.
- Prefer docs, diagnostics, logging, metrics, and config guards first.
- Keep commits server-only and easy to review separately from reconstruction.
- If a change touches `Character.java`, `MapleMap.java`, `PacketCreator.java`,
  `Monster.java`, `InventoryManipulator.java`, or combat formulas, treat it as
  high blast radius and require focused regression testing.

## Expected Improvement Estimates

These are rough capacity/headroom estimates for a 500-player target. They are
not guarantees; the real result depends on whether DB, CPU, packet send, timer
queue, or lock contention becomes the first bottleneck.

| Order | Work | Direct Improvement | Confidence | Notes |
|---:|---|---:|---|---|
| 1 | Diagnostics and observability | 0-5% direct | High | Enables targeted 20-60% later wins. |
| 2 | Non-behavior hardening | 0-10% direct | High | Improves uptime and recovery more than raw throughput. |
| 3 | DB pool/timer visibility and config-only tuning | 5-20% | Medium | Can be 30%+ if current pool/queue settings are the bottleneck. |
| 4 | Character save optimization | 15-40% lower DB pressure, 5-25% overall headroom | High | Highest value, but high blast radius. Gate and test carefully. |
| 5 | Map broadcast and packet churn reduction | 10-35% lower CPU/network pressure in busy maps | Medium-high | Must preserve packet semantics. |
| 6 | Combat/item/skill lookup caching | 5-20% lower combat CPU | Medium | Grind-heavy load benefits most. |
| 7 | Inventory hot-path cleanup | 5-15% lower loot/trade/inventory cost | Medium | Useful if farming/trade/merchant activity is heavy. |
| 8 | Soak-test automation | 0% direct | High | Prevents regressions and proves capacity. |

Approximate combined headroom:

| Implementation Level | Estimated Practical Improvement |
|---|---:|
| Diagnostics plus config tuning | 5-15% |
| Add save diagnostics and low-risk guardrails | 10-25% |
| Add gated save optimization | 15-35% |
| Add measured broadcast/packet improvements | 25-60% in active-map load |
| Add combat/inventory cache work | 30-75% in grind-heavy load |
| Full measured cycle with soak tests | 50-100%+ possible if current bottlenecks are obvious |

## Implementation Phases

### Phase 0 - Branch And Safety Setup

Purpose:

- keep server-only work separate from Agent reconstruction.

Implementation:

- Use a dedicated branch or clearly separated commits for server-player-scale
  work.
- Before each implementation batch, confirm no Agent files are accidentally
  staged.
- Keep `config.yaml` value changes out of commits unless explicitly requested.
- Add any experimental behavior behind default-off config flags.

Files likely touched:

- docs only at first.
- later: diagnostics classes, GM health command, timer/DB monitoring, selected
  server core files.

Acceptance:

- `git diff --name-only` does not include `src/main/java/server/agents` or
  `src/main/java/server/bots`.
- Server starts normally.
- Existing Agent reconstruction branch changes remain untouched.

### Phase 1 - Diagnostics And Observability

Purpose:

- make bottlenecks measurable before changing behavior.

Implementation candidates:

- Add or extend save duration metrics:
  - full save duration.
  - autosave duration.
  - logout save duration.
  - merchant-triggered save duration.
  - top slow save sections if section timing is added.
- Add DB pool visibility:
  - active connections.
  - idle connections.
  - waiting threads.
  - timeout count if available.
  - slow query count by category.
- Add timer lane visibility:
  - queue depth by lane.
  - active tasks by lane.
  - completed tasks by lane.
  - slow task warning by lane.
- Add map/broadcast visibility:
  - broadcasts per map per interval.
  - slow broadcasts by map and packet type.
  - active players per map.
  - object/drop/reactor high-water marks.
- Add script/event visibility:
  - slow NPC script call.
  - slow event script call.
  - repeated script exception throttling summaries.

Primary files:

- `src/main/java/server/TimerManager.java`
- `src/main/java/tools/DatabaseConnection.java`
- `src/main/java/net/server/Server.java`
- `src/main/java/client/command/commands/gm6/ServerHealthCommand.java`
- `src/main/java/client/Character.java`
- `src/main/java/server/maps/MapleMap.java`
- scripting managers and event managers only for diagnostics.

Expected improvement:

- 0-5% direct.
- High indirect value because it shows where to safely spend effort.

Risk:

- Low if read-only/logging only.
- Watch log volume. Add throttling or thresholds for hot paths.

Acceptance:

- `!serverhealth` or diagnostics logs expose DB, timer, save, map, and cache
  state.
- No gameplay behavior changes.
- Slow warnings are thresholded and do not spam during normal local play.

### Phase 2 - Non-Behavior Hardening

Purpose:

- improve 30-day uptime and recovery without changing gameplay rules.

Implementation candidates:

- Stuck login/session recovery diagnostics and repair paths.
- Safer disconnect cleanup around null player/map/channel/script state.
- Better categorized logging for logout, save, map transition, and script
  failures.
- Runtime cache ownership documentation for every static map/set.
- Cleanup hooks for caches keyed by `Client`, character id, account id, map id,
  or object id.
- Defensive guards for rare null map/channel/player states where current code
  can crash during edge disconnects.

Primary files:

- `src/main/java/client/Client.java`
- `src/main/java/client/Character.java`
- script managers.
- event managers.
- `src/main/java/net/server/Server.java`
- selected runtime cache holders.

Expected improvement:

- 0-10% direct.
- High uptime value.

Risk:

- Low to medium.
- Avoid swallowing exceptions silently. Log with context and preserve current
  failure behavior where possible.

Acceptance:

- Login/logout/disconnect tests pass.
- Server can recover from common stuck login state without restart where safe.
- Diagnostics show cache counts after logout and script dispose.

### Phase 3 - Config-Only Tuning

Purpose:

- improve throughput using knobs that do not change gameplay semantics.

Implementation candidates:

- Make DB pool max size configurable if not already.
- Make timer lane thread counts configurable if the current hardcoded counts
  become limiting.
- Make slow-operation thresholds configurable.
- Make autosave backpressure warning threshold configurable.
- Add startup warnings for risky production settings.

Primary files:

- `src/main/java/tools/DatabaseConnection.java`
- `src/main/java/server/TimerManager.java`
- `src/main/java/config/ServerConfig.java`
- `config.yaml` comments only unless value changes are explicitly requested.

Expected improvement:

- 5-20%.
- Can be higher if current DB pool or timer pools are the immediate bottleneck.

Risk:

- Low if defaults preserve current values.
- Medium if production values are changed without soak evidence.

Acceptance:

- Defaults match current behavior.
- Server starts with old config.
- Diagnostics show effective configured values.

### Phase 4 - Character Save Optimization

Purpose:

- reduce DB pressure, which is likely one of the biggest real-player scaling
  bottlenecks.

Implementation candidates:

- Add save reason classification:
  - login load checkpoint.
  - autosave.
  - logout.
  - merchant/shop movement.
  - manual GM save.
  - shutdown.
- Add section timing inside save:
  - character stats.
  - inventory items.
  - equipment.
  - skills.
  - cooldowns/diseases.
  - saved locations.
  - quests/progress/medal maps.
  - buddies/social/event state.
- Add dirty-section flags after section timing proves value.
- Coalesce autosave requests by character id.
- Keep logout and shutdown saves durable.
- Consider batching inventory and quest writes only after tests exist.

Primary files:

- `src/main/java/client/Character.java`
- character save service classes.
- inventory/quest save helpers if extracted later.

Expected improvement:

- 15-40% lower DB pressure.
- 5-25% better overall headroom if DB save pressure is the bottleneck.

Risk:

- High.
- Character persistence touches most player state and can cause data loss if
  wrong.

Safety rules:

- Start with timing only.
- Then add default-off dirty-save mode.
- Never skip logout/shutdown full saves until dirty saves survive soak testing.
- Keep full-save fallback command or config.

Acceptance:

- Create character, level up, change map, gain/lose items, change equipment,
  skill/AP changes, quest progress, buddy/social changes, and merchant actions
  all persist after restart.
- Save logs show section duration and total duration.
- No data loss after forced disconnect test.

### Phase 5 - Broadcast And Packet Churn Optimization

Purpose:

- reduce CPU, allocation, and network pressure in busy maps.

Implementation candidates:

- Identify hot broadcast packet paths from logs.
- Classify each packet:
  - same bytes for every recipient.
  - owner/GM/party/visibility variant.
  - truly per-recipient.
- Cache or reuse packet bytes only for same-byte cases.
- Avoid building packets when recipient list is empty.
- Avoid repeated map/player list snapshots where safe.
- Add packet trace regression for changed paths.

Primary files:

- `src/main/java/server/maps/MapleMap.java`
- `src/main/java/tools/PacketCreator.java`
- packet handlers only if evidence points there.

Expected improvement:

- 10-35% lower CPU/network pressure in busy maps.

Risk:

- Medium to high.
- Packet behavior is client-visible and subtle.

Safety rules:

- Do not change Agent perception or Agent packet suppression here.
- Do not optimize per-recipient packets as same-byte packets unless proven.
- Keep changes small and targeted.

Acceptance:

- Players entering/leaving map, movement, chat, skills, drops, summons, pets,
  damage, and GM hide visibility still behave correctly.
- Busy-map broadcast counters drop for the targeted path.

### Phase 6 - Combat, Item, Skill, And Inventory Hot-Path Caching

Purpose:

- reduce CPU and allocation in grind-heavy gameplay.

Implementation candidates:

- Cache repeated skill/effect lookups inside a single attack flow.
- Avoid repeated equipped inventory scans where the result is already known for
  the calculation.
- Cache item metadata that is immutable game data.
- Reduce allocation in damage line generation where safe.
- Add inventory slot lookup helpers for repeated add/remove operations.

Primary files:

- `src/main/java/server/combat/CombatFormulaProvider.java`
- `src/main/java/net/server/channel/handlers/AbstractDealDamageHandler.java`
- `src/main/java/server/StatEffect.java`
- `src/main/java/server/ItemInformationProvider.java`
- `src/main/java/client/inventory/manipulator/InventoryManipulator.java`

Expected improvement:

- 5-20% lower combat CPU.
- 5-15% lower loot/inventory cost if inventory operations are hot.

Risk:

- Medium to high.
- Combat and inventory correctness matters for players and economy.

Safety rules:

- Prefer local caching within one method call before persistent caches.
- Persistent caches must be bounded or tied to immutable WZ/DB metadata.
- Avoid formula changes while shared combat review is pending.

Acceptance:

- Damage parity tests or manual comparisons for representative jobs.
- Scroll/equip/item stat behavior unchanged.
- Inventory add/remove/drop/trade/storage behavior unchanged.

### Phase 7 - Soak-Test Readiness

Purpose:

- prove the changes and find the next bottleneck.

Implementation candidates:

- Player-style soak scenarios:
  - login/logout churn.
  - map movement.
  - mob kill and loot.
  - shop buy/sell.
  - storage use.
  - NPC conversation.
  - quest progress.
  - party/guild/buddy operations.
  - merchant open/close/item movement.
- Capture interval snapshots:
  - online players.
  - loaded/active maps.
  - heap.
  - GC pause if available.
  - DB pool.
  - timer lanes.
  - slow saves.
  - slow queries.
  - slow broadcasts.
  - script exceptions.

Primary files:

- docs first.
- later a dedicated soak harness or GM/admin command surface.

Expected improvement:

- 0% direct.
- High confidence gain and regression prevention.

Acceptance:

- 1-hour local smoke soak.
- 24-hour stability soak.
- staged 50, 100, 250, 500 player-style load plan.
- no unbounded heap growth trend.
- no repeated stuck-login or failed-save pattern.

## Safe Concurrent Track

These can be implemented while Agent reconstruction is active:

- diagnostics and metrics.
- server health command expansion.
- slow save/query/script/broadcast logs.
- cache-count diagnostics.
- config comments and default-preserving config knobs.
- null guards and cleanup logging around login/logout/script disposal.
- soak-test docs and harness scaffolding that does not spawn Agents.

## Deferred Until Reconstruction Stabilizes

These should wait unless isolated and tested:

- combat formula optimization or formula cleanup.
- mob controller logic changes.
- visible-map broadcast behavior changes.
- inventory/loot behavior changes.
- `Character` persistence dirty-save behavior enabled by default.
- Agent save routing behavior.
- any edit to `server.agents` or `server.bots`.

## First Implementation Batch

Recommended first batch:

1. Expand `!serverhealth` with save, DB, timer, map, and script counters where
   missing.
2. Add save section timing in diagnostics-only mode.
3. Add broadcast count per map in diagnostics-only mode.
4. Make current slow-operation thresholds visible in diagnostics.
5. Add docs for interpreting diagnostics and deciding the next optimization.

Why this batch:

- It is low risk.
- It does not change gameplay.
- It does not touch Agent reconstruction code.
- It gives evidence for whether the next real win is saves, broadcasts, DB
  pool, timer lanes, combat, or scripts.

## Implementation Log

### 2026-07-07 - Diagnostics-Only Foundation Batch

Implemented:

- Added aggregate character save pressure diagnostics.
  - total saves.
  - failed saves.
  - manual versus autosave counts.
  - average, last, and max save duration.
  - last and slowest character identity for operator debugging.
- Added character save section timing for the major existing save chunks:
  - `character-row`
  - `pets`
  - `keymap`
  - `skill-macros`
  - `inventory`
  - `skills`
  - `locations`
  - `buddies`
  - `area-event`
  - `quests`
  - `family-cash-storage`
- Added aggregate map broadcast pressure diagnostics.
  - total broadcasts.
  - ranged broadcasts.
  - slow broadcasts.
  - average recipients.
  - average, last, and max broadcast duration.
  - map id and recipient count for last/max broadcasts.
- Exposed the new diagnostics through `Server.diagnosticLines()`, used by
  `!serverhealth`.
- Added the same save and broadcast pressure summaries into periodic scale
  health logs.
- Added save reason labels for major non-Agent save paths:
  - `AUTO_SAVE`
  - `FULL_SAVE`
  - `LOGOUT`
  - `SERVER_TRANSITION`
  - `CASHSHOP`
  - `MTS`
  - `MERCHANT`
  - `SAVE_ALL`
  - `WARP_WORLD`
- Added default-preserving runtime tuning visibility:
  - DB pool diagnostics now include max pool size and connection timeout.
  - timer diagnostics now include configured thread count per lane.
- Added `!serverhealth` visibility for current slow-operation warning
  thresholds:
  - login/login-state: 1000 ms.
  - character load: 5000 ms.
  - character save: 1000 ms.
  - character delete: 1000/5000 ms.
  - startup DB work: 5000 ms.
  - map update: 250 ms.
  - map broadcast: 100 ms.
- Added optional default-preserving runtime knobs:
  - `-Dcosmic.db.maxPoolSize` or `COSMIC_DB_MAX_POOL_SIZE`
  - `-Dcosmic.db.connectionTimeoutSeconds` or
    `COSMIC_DB_CONNECTION_TIMEOUT_SECONDS`
  - `-Dcosmic.timer.coreThreads` or `COSMIC_TIMER_CORE_THREADS`
  - `-Dcosmic.timer.saveThreads` or `COSMIC_TIMER_SAVE_THREADS`
  - `-Dcosmic.timer.mapThreads` or `COSMIC_TIMER_MAP_THREADS`
  - `-Dcosmic.timer.eventThreads` or `COSMIC_TIMER_EVENT_THREADS`
  - `-Dcosmic.timer.lowPriorityThreads` or
    `COSMIC_TIMER_LOW_PRIORITY_THREADS`

Safety:

- No save behavior changed.
- No packet semantics changed.
- No broadcast suppression was added.
- No `src/main/java/server/agents` or `src/main/java/server/bots` files were
  edited for this diagnostics batch.
- No `config.yaml` values were changed.
- The new runtime knobs preserve current values unless explicitly set by an
  operator.

Deferred:

- Dirty-save behavior remains deferred.
- Save coalescing behavior changes remain deferred.
- Broadcast packet caching/reuse remains deferred.
- Combat, inventory, and formula optimization remain deferred until targeted
  evidence or Agent reconstruction stability.
