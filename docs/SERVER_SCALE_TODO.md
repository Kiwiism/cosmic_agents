# Server Scale TODO

Target: 500 concurrent players, 2000 concurrent agents, and 30 days uptime.

Scope: server-side improvements that can be prepared before the agent restructure. Avoid changing agent behavior until the reconstruction is ready.

Related design notes:

- `docs/agents/AGENT_ENGINE_OPTIMIZATION.md`.
- `docs/COSMIC_PR_APPLICABILITY_TODO.md`.
- `docs/SOAK_TEST_CHECKLIST.md`.
- `docs/SERVER_DB_INDEX_REVIEW.md`.
- `docs/SERVER_DEFERRED_SAFE_CHANGES.md`.
- Agent-only bottleneck work moved to `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`.

## Server-Only Tweaks While Waiting For Agent Reconstruction

These items can be prepared before the reconstructed Agent engine is ready.
They should not change Agent gameplay behavior yet. The goal is to make the
server ready to host the later 2000-Agent scaling model with minimal risk to
players.

### 1. Player/Agent Save Routing Shell

Scope:

- server-only persistence routing.
- no partial Agent save behavior yet.

Implementation:

- Add `SaveActorType`, `SaveReason`, and `SaveProfile` enums.
- Add a small `CharacterPersistenceService` that decides whether a character is
  a player or Agent.
- Initially route both players and Agents to the existing full
  `Character.saveCharToDB(...)` behavior.
- Keep player save behavior unchanged.

Later Agent reconstruction hook:

- Replace the Agent route with checkpoint/dirty-section saves.

Value:

- Creates the separation point for Agent DB throttling without touching
  gameplay capabilities.

### 2. Separate Agent Save Queue Placeholder

Scope:

- server scheduling/persistence shell.
- safe even if it still calls the existing full save internally.

Implementation:

- Add an Agent save queue separate from player logout/autosave work.
- Coalesce queued Agent saves by character id.
- Add a configurable cap for Agent save executions per second.
- Add queue depth, waiting time, and dropped/coalesced save diagnostics.
- Keep shutdown/manual full saves available.

Later Agent reconstruction hook:

- Route `AGENT_CHECKPOINT`, `AGENT_LIGHT`, and `AGENT_FULL` through this queue.

Value:

- Prevents Agent save storms from competing directly with real player logout
  saves.

### 3. Agent Detection Boundary

Scope:

- server-side classification only.
- do not couple core server code deeply to concrete Agent runtime classes.

Implementation:

- Add a small `AgentPresence` or `CharacterRoleResolver` boundary.
- Default implementation can detect legacy Agents by `BotClient` or existing
  bot registration, but core code should depend on the boundary instead of
  scattered `instanceof BotClient` checks.
- Expose diagnostics:
  - online players.
  - online Agents.
  - Agent counts per channel/map.

Later Agent reconstruction hook:

- Portable Agent package installs a richer provider.

Value:

- Lets server systems make player/Agent policy decisions without importing the
  full Agent engine.

### 4. Broadcast Suppression Readiness

Scope:

- instrumentation and gateway preparation only.
- no packet behavior changes until targeted tests exist.

Implementation:

- Keep slow broadcast logging.
- Add per-map broadcast count/cost diagnostics.
- Identify packets that are safe same-bytes broadcasts versus owner/recipient
  specific packets.
- Add a future `PacketGateway`/broadcast-policy seam for Agent-originated
  cosmetic packets.

Later Agent reconstruction hook:

- Suppress Agent movement/combat/cosmetic packets when no real player is in the
  map.

Value:

- Prepares one of the largest Agent scaling wins without risking client-visible
  server behavior early.

### 5. Real-Player Map Presence Index

Scope:

- server map diagnostics/indexing.
- do not change Agent tick behavior yet.

Implementation:

- Track/count real players per map separately from Agent characters.
- Add helper methods:
  - `hasRealPlayers(mapId)`.
  - `realPlayerCount(mapId)`.
  - `agentCount(mapId)`.
- Include counts in scale-health and `!serverhealth`.

Later Agent reconstruction hook:

- Simulation tier selection uses these counts.

Value:

- This is the foundation for Presentation versus Background simulation mode.

### 6. Map Active/Idle Classification Hardening

Scope:

- server diagnostics and classification.
- no aggressive unload behavior until soak data supports it.

Implementation:

- Keep active/idle map diagnostics.
- Add explicit reasons a map is active:
  - real players.
  - Agents.
  - event instance.
  - boss/special mob.
  - shop/merchant.
  - drops/reactors.
  - pinned/sensitive flag.
- Log loaded-map growth and high-watermarks.

Later Agent reconstruction hook:

- Agents in idle/safe maps can switch to abstract simulation.

Value:

- Prevents long-uptime map growth and supports safe Agent dematerialization.

### 7. Server Load Level As Agent Backpressure Input

Scope:

- diagnostics-first.
- no Agent action shedding yet.

Implementation:

- Continue `ServerLoadMonitor` work.
- Expose load level to future Agent engine through a tiny read-only API.
- Include DB waiting count, scheduler queue pressure, heap pressure, and slow
  map/broadcast signals.

Later Agent reconstruction hook:

- Agent engine sheds work in order:
  - LLM calls.
  - cosmetic chat.
  - proactive social offers.
  - long-range planning.
  - background catalog queries.
  - non-critical background movement/combat.

Value:

- Real player work remains priority when the server is stressed.

### 8. DB Pool And Index Prep

Scope:

- review and diagnostics unless a specific migration is approved.

Implementation:

- Keep DB pool stats in health output.
- Review `docs/SERVER_DB_INDEX_REVIEW.md` before applying migrations.
- Add slow query wrappers around any remaining high-volume login/save/shop
  paths.
- Consider making Hikari pool size configurable later instead of hardcoded 10.

Later Agent reconstruction hook:

- Agent save queue uses DB pressure to throttle checkpoint writes.

Value:

- Makes DB contention visible before Agent persistence is split.

### 9. Runtime Cache Ownership Audit

Scope:

- server-only memory-leak prevention.

Implementation:

- For every static/runtime cache, document:
  - owner.
  - expected max size.
  - cleanup hook.
  - diagnostic count.
- Continue logout cleanup for scripts, NPC/dressing-room state, pending runtime
  state, and event/map disposals.

Later Agent reconstruction hook:

- Agent caches follow the same ownership pattern for plans, perception, route
  state, combat targets, journals, and social memory.

Value:

- Helps the 30-day uptime goal before 2000 Agents are introduced.

### 10. Server Health Command Expansion

Scope:

- diagnostics only.

Implementation:

- Keep `!serverhealth` read-only.
- Add compact lines for:
  - real players versus Agents.
  - maps with highest Agent count.
  - DB pool waiting count.
  - save queue counts.
  - timer queue counts.
  - top loaded-map/object/drop high-watermarks.

Later Agent reconstruction hook:

- Add Agent scheduler, simulation tier, and materialization counts.

Value:

- Gives quick feedback during local tests and soak tests without attaching a
  profiler.

### 11. Safe Config Surface For Future Agent Scaling

Scope:

- config definitions only when needed; do not rewrite runtime values casually.

Potential future config:

```yaml
agents:
  persistence:
    max_saves_per_second: 10
    checkpoint_jitter_ms: 30000
  simulation:
    presentation_tick_ms: 150
    background_active_tick_ms: 750
    background_abstract_tick_ms: 10000
    strategic_tick_ms: 60000
  load_shedding:
    disable_cosmetics_at: ELEVATED
    disable_background_planning_at: HIGH
```

Later Agent reconstruction hook:

- Reconstructed Agent engine owns and consumes these settings.

Value:

- Keeps scaling knobs explicit and avoids hardcoded Agent tuning.

### 12. Soak Test Harness Prep

Scope:

- server test/diagnostic procedure.

Implementation:

- Extend `docs/SOAK_TEST_CHECKLIST.md` with Agent-specific checkpoints.
- Prepare stages:
  - 50 Agents.
  - 100 Agents.
  - 250 Agents.
  - 500 Agents.
  - 1000 Agents.
  - 2000 Agents.
- Record CPU, heap, GC, DB waiting, save queue depth, map counts, scheduler
  delay, and player login/move responsiveness.

Later Agent reconstruction hook:

- Run each stage after scheduler, simulation tiers, and persistence separation
  land.

Value:

- Turns the 2000-Agent goal into measurable gates instead of vibes.

## Completed Server-Only Batch - 2026-07-01

- [x] Added guaranteed logout account unlock fallback in `Client.disconnectInternal`.
- [x] Isolated messenger logout cleanup so messenger failures do not abort disconnect.
- [x] Added empty `NOT_STARTED` queststatus placeholder save filter.
- [x] Added DB pool stats and slow DB connection-acquire warning.
- [x] Added slow character-save and disconnect warnings.
- [x] Added ThreadManager submitted/rejected diagnostics.
- [x] Replaced ThreadManager unbounded rejected-task raw thread creation with caller-run fallback.
- [x] Verified mob skill probability already uses `(float) iprop / 100`.
- [x] Fixed monster/global drop chance rolls to use `Randomizer.nextInt(1000000)`.
- [x] Fixed monster/global drop quantity rolls to use inclusive min/max.
- [x] Added EXP debug session timeout cleanup.
- [x] Added cache-size diagnostics helper and wired storage fee cache warnings.
- [x] Added `!heapdump` GM6 trigger and heap dump service.
- [x] Added soak test checklist.

## Completed Server-Only Batch 2 - 2026-07-01

- [x] Added anchored local-address matching for `IpAddresses` / PR #318.
- [x] Added quest 6225/6315 NPC gating fix for `2041023.js` / PR #295.
- [x] Added capped Aran Combo Drain HP recovery / PR #281.
- [x] Added nullable server-message packet safety / PR #330.
- [x] Added slow character-load, storage-load, ranking-load, and character-save warnings.
- [x] Added logout save success/failure return path and disconnect logging.
- [x] Avoided monitored packet byte copies when no monitored character is active.
- [x] Added character logout retained-schedule audit warning.
- [x] Added map dispose object/drop/timer count diagnostics.
- [x] Added event instance dispose state diagnostics.
- [x] Deduplicated disease announce queues.
- [x] Added server cache audit record: `docs/SERVER_CACHE_AUDIT.md`.

## Completed Server-Only Batch 3 - 2026-07-01

- [x] Added named `TimerManager` scheduler lanes and moved selected recurring server work to save/map/event/low-priority lanes.
- [x] Expanded scale-health logging with online player, loaded/active/idle map, runtime cache, DB, thread, timer, and load-level diagnostics.
- [x] Added `ServerLoadMonitor` and `ServerLoadLevel` for diagnostics-first graceful degradation.
- [x] Added active/idle map classification and idle map tick candidate diagnostics.
- [x] Added loaded map high-watermark/growth watch logging.
- [x] Added slow map-update and map-broadcast warnings.
- [x] Added character save scheduler pending-save diagnostics and backpressure warnings.
- [x] Added rollback-safe hired merchant owner registration guards.
- [x] Added Store Remote Controller requirement for remote merchant access.
- [x] Added logout cleanup for pending NPC/dressing-room runtime state keyed by character id.
- [x] Added `docs/SERVER_HARDENING_DIAGNOSTICS.md` for diagnostics-first changes and revisit notes.

## Completed Server-Only Batch 4 - 2026-07-02

- [x] Replaced selected hot-path raw stack traces with structured logging in login state and character-load paths.
- [x] Added reusable repeated-exception throttling helper for script/event failure logging.
- [x] Added script manager active conversation/action/script counts into periodic scale-health logs.
- [x] Added event instance dispose retained-count diagnostics and throttled event callback exception logging.
- [x] Added per-map object/drop/reactor/monster high-water diagnostics.
- [x] Added slow operation wrappers/timing for startup DB tasks, login DB state, character load, and character deletion.
- [x] Added startup warnings for optional high-impact runtime features without changing config values.
- [x] Added non-loading `MapManager.getLoadedMap(...)` and used it for script `getPlayerCount(mapid)`.
- [x] Rechecked and corrected monster/global drop million-scale chance and inclusive min/max quantity rolls.
- [x] Added character deletion duration diagnostics; transaction consolidation remains a later reviewed change.

## Completed Server-Only Batch 5 - 2026-07-03

- [x] Fixed `LoginBypassCoordinator` removal to use the actual `Pair<Hwid, Integer>` key type and removed NPE-based lookup flow.
- [x] Hardened login-attempt storage by pruning expired attempts during registration and exposing tracked account count diagnostics.
- [x] Added defensive `SessionCoordinator.closeSession(...)` null-context handling and structured transition-load logging.
- [x] Added disconnect-time NPC/quest script cleanup so script managers do not retain `Client` references after logout.
- [x] Hardened New Year card runtime tasks with structured DB logging, safer task cancellation, and concurrent runtime storage.
- [x] Added login-attempt, login-bypass, New Year card, and login-state counts to periodic scale-health logs.
- [x] Corrected the `TimerManager` MBean name and replaced registration stack trace output with structured logging.
- [x] Replaced selected `Client` account/session raw stack traces with structured logs.

## Completed Server-Only Batch 6 - 2026-07-03

- [x] Added read-only GM6 `!serverhealth` command for compact runtime diagnostics.
- [x] Registered existing GM6 `!heapdump` command.
- [x] Added reusable `Server.diagnosticLines()` snapshot output.
- [x] Replaced selected low-risk raw stack traces in map/portal/storage/shop paths with structured logs.
- [x] Documented DB index review candidates without applying migrations.
- [x] Documented deferred character-deletion transaction, broadcast optimization, runtime cache ownership, and config-review follow-ups.

### Best Implementation Order - 2026-07-02 Result

Implemented now:

1. Structured logging in selected hot server paths.
2. Script manager leak/count diagnostics.
3. Event and map high-water diagnostics.
4. Slow query/operation wrappers for login, character load, startup DB, and character deletion paths.
5. Runtime startup warnings for risky optional features.
6. Broadcast audit retained as diagnostics-first; no packet semantic change was made in this batch.
7. Non-loading loaded-map helper for simple count queries.
8. DB index review recorded as migration-review work, not applied blindly.
9. Character deletion transaction cleanup kept as timing/audit only; consolidation is deferred.

The three useful candidates not included in the original best-order list were:

- Repeated-exception throttling.
  - Status: implemented for script/event failure logging because it supports the structured logging work.
- Config comments for local-only versus production-safe features.
  - Status: documented later without changing `config.yaml` values.
- Compact admin diagnostics snapshot command.
  - Status: implemented later as GM6 `!serverhealth`.

## Phase 1 - Server Durability First

### Threading and Scheduling

- [x] Harden `ThreadManager` rejection behavior.
  - Category: server hardening and agent hardening.
  - Current risk: rejected work can create extra emergency threads under pressure.
  - Goal: bounded behavior, clear rejection metrics, and no unbounded thread growth.
  - Agent impact: 2000 agents can generate many small tasks; rejection must degrade predictably instead of creating unbounded emergency work.
- [x] Add separate scheduler ownership for core server work.
  - Category: server hardening and agent hardening.
  - Candidate split: core maintenance, map updates, saves, delayed actions.
  - Keep agent tick restructuring for later.
  - Agent impact: agent work should not delay autosave, logout, respawn, DB cleanup, or timed core tasks.
- [x] Move separate scheduler ownership for future agent work to Agent scaling track.
  - Category: agent hardening.
  - Scope for now: design and diagnostics only; do not replace current bot tick behavior before the agent restructure.
  - Goal: reserve a bounded scheduler lane for future agent ticks, LLM callbacks, plan execution, and non-critical agent async work.
  - 2026-07-03 result: moved to `docs/agents/AGENT_ENGINE_SCALING_TRACK.md` as sharded/budgeted Agent scheduler work.
- [x] Add scheduler diagnostics.
  - Category: server hardening and agent hardening.
  - Track queue size, active thread count, completed task count, rejected task count, and slow task warnings.
  - Agent impact: makes agent scheduler pressure visible before it becomes server lag.

### Metrics and Observability

- [x] Add lightweight server metrics.
  - Category: server hardening and agent hardening.
  - Online players.
  - Online agents.
  - Loaded maps.
  - Active maps.
  - Per-map character, monster, drop, reactor, and object counts.
  - Heap usage.
  - GC pauses.
  - DB pool active, idle, waiting, and timeout counts.
- [x] Add periodic scale health logging.
  - Category: server hardening and agent hardening.
  - Keep logs compact so 30-day uptime does not create excessive log volume.
- [x] Add slow operation warnings.
  - Category: server hardening and agent hardening.
  - Map tick duration.
  - Save duration.
  - DB query duration.
  - Broadcast duration.
  - Agent impact: identifies which map, save, broadcast, or agent-adjacent action is causing latency.

### Map Lifecycle

- [x] Track active versus idle maps.
  - Category: server hardening and agent hardening.
  - Active maps should include maps with players, agents, event instances, shops, drops, reactors, bosses, or other pinned state.
  - Agent impact: agents travel and idle across many maps; active-state tracking prevents unnecessary map work and informs future agent tick gating.
- [x] Tick only active maps where safe.
  - Category: server hardening and agent hardening.
  - Start with diagnostics-only mode before changing behavior.
- [x] Add idle map unload plan.
  - Category: server hardening and agent hardening.
  - Pinned maps should opt out.
  - Event maps and special maps need explicit rules.
  - Unload should call full dispose cleanup.
  - Agent impact: agents can load many maps over long uptime; unload must release map, monster, drop, reactor, timer, and agent references safely.
- [x] Add loaded map growth alerts.
  - Category: server hardening and agent hardening.
  - Warn if loaded maps keep increasing over time without returning to baseline.
  - Agent impact: detects agent-driven map growth or map unload leaks.

### DB and Save Pressure

- [x] Add DB pool monitoring.
  - Category: server hardening and agent hardening.
- [x] Add slow query logging for server hot paths.
  - Category: server hardening and agent hardening.
- [x] Review and likely port `P0nk/Cosmic` PR [#348](https://github.com/P0nk/Cosmic/pull/348): unbloat `queststatus` table.
  - Category: server hardening and agent hardening.
  - Name: `QuestStatus Placeholder Save Filter`.
  - Current behavior: `Character.getQuest(Quest)` creates and stores a `NOT_STARTED` `QuestStatus` when a quest is only looked up.
  - Current save path deletes all quest status rows for the character, then reinserts every in-memory quest status.
  - Confirmed nuance: this does not duplicate rows every save, but empty `NOT_STARTED` placeholders become permanent saved rows after lookup and are rewritten on every future save.
  - Fix direction: skip saving pure placeholder rows where:
    - status is `NOT_STARTED`.
    - forfeited count is `0`.
    - completed count is `0`.
    - progress map is empty.
    - medal map list is empty.
  - Recommendation over upstream PR: include `qs.getCompleted() == 0` in the skip condition so repeat/completion-count state is not lost.
  - Preserve rows with forfeits, completion count, progress, medal maps, custom quest info, or any other real state.
  - Agent impact: agents will evaluate many quest requirements and can create many lookup-only quest statuses; filtering placeholders reduces DB size and autosave/logout save pressure.
  - Validation later: create/look up several unstarted quests, save/reload, confirm empty placeholders are not persisted; confirm started/completed/forfeited/progress/medal/completed-count quests still persist.
- [x] Review autosave batching and backpressure.
  - Category: server hardening and agent hardening.
  - Avoid save storms during peak load.
  - Avoid blocking important gameplay threads on slow DB writes where possible.
  - Agent impact: agents multiply inventory, quest, trade, loot, and movement persistence pressure.
- [x] Review player logout save reliability.
  - Category: server hardening.
  - Make sure failed saves are visible and retried or reported.
- [x] Harden logout account-unlock path.
  - Category: server hardening and agent hardening.
  - Name: `Guaranteed Logout Unlock`.
  - Current risk: `Client.disconnectInternal` can throw before `updateLoginState(LOGIN_NOTLOGGEDIN)`, leaving `accounts.loggedin = 2` and causing "already logged in" on relog.
  - Recent example: missing `net.server.world.MessengerCharacter` during logout/shutdown crashed cleanup before the account was marked offline.
  - Fix direction: make account unlock run from a `finally` path for non-transition disconnects, and isolate optional cleanup failures so messenger/guild/buddy/party/map cleanup cannot block login-state reset.
  - Agent impact: bot/agent characters also depend on reliable disconnect cleanup and must not leave stale login/session state.
- [x] Lazy and isolate messenger logout cleanup.
  - Category: server hardening.
  - Name: `Messenger Logout Isolation`.
  - Current risk: `MessengerCharacter` is created even when the character is not in messenger, and before the main disconnect cleanup guard.
  - Fix direction: only create `MessengerCharacter` when `messengerid > 0`, wrap messenger leave in its own guarded cleanup section, and log failures without aborting the rest of disconnect.

### Gameplay Correctness Affecting Scale

- [x] Review and port `P0nk/Cosmic` PR [#279](https://github.com/P0nk/Cosmic/pull/279): mob skill probability integer-division fix.
  - Category: server hardening and agent hardening.
  - Fix direction: compute mob skill `prop` as `(float) iprop / 100` instead of integer division.
  - Agent impact: mob debuff/danger behavior affects autonomous training risk, potion/debuff planning, and background combat simulation.
- [x] Review and port `P0nk/Cosmic` PR [#287](https://github.com/P0nk/Cosmic/pull/287): drop chance and inclusive quantity range fixes.
  - Category: server hardening, economy hardening, and agent hardening.
  - Fix direction: use million-scale `Randomizer.nextInt(1000000)` and inclusive `nextInt(max - min + 1) + min` quantity rolls.
  - Agent impact: accurate farming/drop quantities are required for catalog validation, item valuation, and economy simulation.

### Packet and Broadcast Path

- [x] Avoid unnecessary packet byte generation for monitoring/logging when no monitored character is active.
  - Category: server hardening.
- [x] Review broadcast paths for avoidable work.
  - Category: server hardening and agent hardening.
  - Player clients need encoded packets.
  - Agents usually need server state, not encoded client packets.
  - Do not restructure agent perception yet; only remove clearly unnecessary server-side work.
  - Agent impact: future agents should consume structured state/perception, not encoded client packet work.

## Phase 2 - Memory Leak and Long-Uptime Audits

### Lifecycle Cleanup

- [x] Audit character logout cleanup.
  - Category: server hardening and agent hardening.
  - Buff schedules.
  - Cooldown schedules.
  - Recovery schedules.
  - Pet schedules.
  - Expiration schedules.
  - Quest/disease/item timers.
- [x] Move bot removal cleanup audit to Agent scaling/fix track.
  - Category: agent hardening.
  - Confirm scheduled futures are cancelled.
  - Confirm runtime cooldown/static maps release character IDs.
  - Confirm map references are released.
- [x] Move future agent runtime reference ownership audit to Agent scaling track.
  - Category: agent hardening.
  - Confirm plans, profiles, perception snapshots, navigation paths, targets, conversations, and LLM state do not retain stale `Character`, `MapleMap`, or `MapObject` references after despawn/logout/map unload.
- [x] Audit map dispose cleanup.
  - Category: server hardening and agent hardening.
  - Characters.
  - Monsters.
  - Drops.
  - Reactors.
  - Timers.
  - Event references.
  - Shops/merchants.
- [x] Manually port useful parts of `P0nk/Cosmic` PR [#277](https://github.com/P0nk/Cosmic/pull/277): hired merchant duplicate guard and remote-store validation.
  - Category: server hardening and agent hardening.
  - Name: `Hired Merchant Registration Guard`.
  - Do not apply upstream PR as-is; rewrite cleanly.
  - Current risk: merchant creation registers owner-keyed merchant state into character/world/channel maps without a complete duplicate-state check.
  - Current risk: same-channel remote merchant access does not require Store Remote Controller.
  - Fix direction: atomically reject duplicate owner merchant state across `chr.getHiredMerchant()`, `chr.hasMerchant()`, `World.getHiredMerchant(ownerId)`, and channel merchant map before registering a new merchant.
  - Fix direction: send clear error/`enableActions` on rejection instead of silently doing nothing.
  - Fix direction: require Store Remote Controller item `5470000` for off-map remote merchant access, including deciding whether cross-channel remote switch should require it too.
  - Agent impact: future economy agents may create, manage, or inspect merchants; merchant state must be consistent to avoid dupes, orphaned shops, and stale market state.
- [x] Audit event instance cleanup.
  - Category: server hardening and agent hardening.
  - Party quest state.
  - Timers.
  - Map references.
  - Player references.

### Static Cache and Queue Cleanup

- [x] Add timeout/cleanup for EXP debug sessions.
  - Category: server hardening.
- [x] Deduplicate and cleanup disease announce queues.
  - Category: server hardening.
- [x] Review static maps keyed by character ID.
  - Category: server hardening and agent hardening.
  - Add cleanup on logout/bot removal.
  - Add size diagnostics where cleanup cannot be guaranteed.
- [x] Review caches that grow with item IDs, skill IDs, map IDs, or character IDs.
  - Category: server hardening and agent hardening.
  - Mark each as bounded, intentionally permanent, or needing cleanup.

### Long-Run Safety

- [x] Add graceful degradation rules.
  - Category: server hardening and agent hardening.
  - Drop non-critical debug work first.
  - Delay non-critical saves with visibility.
  - Warn before server health becomes critical.
  - Agent impact: agent chatter, cosmetic actions, non-critical LLM calls, and optional plan sidetracks should degrade before core gameplay.
- [x] Move agent-specific degradation policy to Agent scaling track.
  - Category: agent hardening.
  - Define which agent work can be paused first under pressure: LLM calls, cosmetic chat, proactive offers, long-range planning, background catalog queries, then non-critical movement/combat.
  - Core server tasks and real player packet handling must remain higher priority.
- [x] Add heap dump trigger plan for suspected leaks.
  - Category: server hardening and agent hardening.
- [x] Add 24-hour, 72-hour, 7-day, and 30-day soak test checklist.
  - Category: server hardening and agent hardening.
  - Agent impact: include scenarios with 500 players simulated or real clients, 2000 agents, map travel, loot, shop use, questing, and shutdown/restart cycles.

## Phase 3 - Moved To Agent Restructure

These are no longer tracked as server-only hardening. They are owned by the
post-reconstruction Agent scaling track:

- [x] Install real `AgentPresence` provider from the reconstructed portable agent package.
  - Category: agent hardening.
  - Server prep exists as a noop default in `server.integration.AgentPresence`.
  - Real provider should identify reconstructed agents without hard-wiring core server files to `BotClient`.
  - Until this provider is installed, hidden-character mob simulation behaves like normal Cosmic.
- [x] Replace per-agent scheduled futures with a sharded agent scheduler.
  - Category: agent hardening.
- [x] Add lightweight `AgentActor` or reduced agent runtime state.
  - Category: agent hardening.
- [x] Replace packet-based agent perception with server-state perception.
  - Category: agent hardening.
- [x] Add NPC/quest capability runtime.
  - Category: agent hardening.
- [x] Add randomized agent NPC stop spots and dialogue-delay simulation.
  - Category: agent hardening.
- [x] Rework agent movement, inventory, equipment, or combat runtime behavior.
  - Category: agent hardening.

## Suggested Build Order

1. Metrics and diagnostics.
2. `ThreadManager` hardening.
3. Active-map tracking in diagnostics-only mode.
4. Map lifecycle cleanup audit.
5. DB pool and slow query visibility.
6. Broadcast/logging unnecessary work cleanup.
7. Idle map unload design and guarded implementation.
8. Long-running soak tests.

## Additional Server-Only Review Candidates - 2026-07-02

These are server-side hardening or low-risk optimization candidates found during a light scan. They should avoid changing bot/agent runtime behavior before reconstruction.

### Low-Risk Diagnostics / Logging Cleanup

- [x] Replace hot-path `printStackTrace()` calls with structured logger calls.
  - Category: server hardening.
  - Rationale: uncategorized stack traces are noisy during soak tests and can hide repeated failure patterns.
  - Low-risk path: start with login/session/script managers and packet handlers; preserve exception handling behavior, only change reporting.
  - Candidate areas: `Client`, `Character`, script managers, MTS/cash-shop handlers, event managers.
  - 2026-07-03 result: completed another low-risk pass over `Client`, New Year card, session, and timer paths. Many lower-priority script/event/item/world stack traces remain for future targeted passes.

- [x] Add repeated-exception throttling for script and event handler failures.
  - Category: server hardening.
  - Rationale: a broken script or malformed packet can spam logs and increase IO pressure.
  - Low-risk path: central helper that logs the first N occurrences per key and then periodic summaries.

- [x] Add a compact admin command or log section for current diagnostics snapshot.
  - Category: server hardening.
  - Rationale: current scale health is log-only; operators should be able to request heap/DB/timer/thread/map/cache state on demand.
  - Low-risk path: read-only GM6 command, no behavior change.
  - 2026-07-03 result: added GM6 `!serverhealth` and reusable `Server.diagnosticLines()`.

### Runtime Cache / Leak Watch

- [x] Audit script manager maps keyed by `Client`.
  - Category: server hardening.
  - Rationale: `NPCScriptManager`, `QuestScriptManager`, and related managers keep maps keyed by client/session; missed dispose paths can retain clients.
  - Low-risk path: add size diagnostics and disconnect-time cleanup checks before changing ownership model.
  - 2026-07-03 result: logout cleanup now disposes NPC and quest script state for the client before the character object is emptied.

- [x] Audit event manager and event instance maps/lists for long-retained character references.
  - Category: server hardening.
  - Rationale: event instances store characters, kill counts, map references, timers, gates, and properties.
  - Low-risk path: add retained-count logging after dispose and during scale health; larger refactor later if leak is observed.

- [x] Add diagnostics for map object/drop/reactor count growth by map id.
  - Category: server hardening.
  - Rationale: current loaded/active map counts are visible, but per-map object growth is easier to diagnose with high-water marks.
  - Low-risk path: log top N maps by objects/drops/reactors only when thresholds are exceeded.

### DB / Save Pressure

- [x] Add slow query wrappers for remaining login and character-load DB paths.
  - Category: server hardening.
  - Rationale: many login/load methods call DB directly; under 500 players, slow account/character queries are a major bottleneck.
  - Low-risk path: timing/logging only, then optimize indexes or query shape after evidence.

- [x] Review indexes for hot login/save/query tables.
  - Category: server hardening.
  - Candidate tables: `accounts`, `characters`, `queststatus`, `questprogress`, `inventoryitems`, `inventoryequipment`, `cooldowns`, `playerdiseases`, `buddies`, merchant/shop tables.
  - Low-risk path: document current schema/indexes first; add indexes only with migration review.
  - 2026-07-02 result: no index migration applied in this batch. Add candidate indexes only after comparing the current Liquibase table definitions with slow-query evidence from soak logs.
  - 2026-07-03 result: current schema and candidate indexes documented in `docs/SERVER_DB_INDEX_REVIEW.md`; no migration applied without slow-query evidence.

- [x] Batch or consolidate character deletion queries later.
  - Category: server hardening.
  - Rationale: deletion has many sequential deletes/selects; not hot path, but safer and faster with a reviewed transaction plan.
  - Low-risk path: keep current behavior for now; add audit/logging around delete duration.
  - 2026-07-02 result: added character deletion duration diagnostics. Transaction consolidation remains deferred.
  - 2026-07-03 result: deferred implementation plan documented in `docs/SERVER_DEFERRED_SAFE_CHANGES.md`.

### Packet / Broadcast Low-Risk Optimizations

- [x] Audit specialized broadcast methods for avoidable per-recipient packet generation.
  - Category: server hardening.
  - Rationale: map broadcast warning exists, but specialized paths may still build packet bytes even when no recipients need them.
  - Low-risk path: diagnostics first; preserve player packet semantics.
  - 2026-07-02 result: no broadcast semantic change was made; current slow broadcast diagnostics remain the safe evidence-gathering layer.
  - 2026-07-03 result: packet classification and implementation rules documented in `docs/SERVER_DEFERRED_SAFE_CHANGES.md`.

- [x] Avoid loading maps for simple count queries where possible.
  - Category: server hardening.
  - Example: script helpers that call `getMap(mapid).getCharacters().size()` may load a map just to count characters.
  - Low-risk path: add non-loading `peekLoadedMap` helper and use it only where loading is not intended.

### Config / Feature Safety

- [x] Add config comments for local-only versus production-safe features.
  - Category: server hardening.
  - Rationale: commands/features like debug EXP, dupe, delete character, dressing room, high HP/MP cap, and custom item behavior should be easy to audit before public uptime tests.
  - Low-risk path: documentation/comments only, then optional config gates later.
  - 2026-07-03 result: documented as config review notes without changing `config.yaml` values.

- [x] Add runtime warnings for risky debug/dev features enabled in non-local deployments.
  - Category: server hardening.
  - Rationale: helps avoid accidentally running with dev convenience features enabled.
  - Low-risk path: startup warnings only.
