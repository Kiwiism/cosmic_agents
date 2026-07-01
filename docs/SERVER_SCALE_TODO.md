# Server Scale TODO

Target: 500 concurrent players, 2000 concurrent agents, and 30 days uptime.

Scope: server-side improvements that can be prepared before the agent restructure. Avoid changing agent behavior until the reconstruction is ready.

Related design notes:

- `docs/agents/AGENT_ENGINE_OPTIMIZATION.md`.
- `docs/COSMIC_PR_APPLICABILITY_TODO.md`.
- `docs/SOAK_TEST_CHECKLIST.md`.

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
- [ ] Add separate scheduler ownership for future agent work.
  - Category: agent hardening.
  - Scope for now: design and diagnostics only; do not replace current bot tick behavior before the agent restructure.
  - Goal: reserve a bounded scheduler lane for future agent ticks, LLM callbacks, plan execution, and non-critical agent async work.
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
- [ ] Audit bot removal cleanup without changing behavior.
  - Category: agent hardening.
  - Confirm scheduled futures are cancelled.
  - Confirm runtime cooldown/static maps release character IDs.
  - Confirm map references are released.
- [ ] Audit future agent runtime reference ownership.
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
- [ ] Add agent-specific degradation policy.
  - Category: agent hardening.
  - Define which agent work can be paused first under pressure: LLM calls, cosmetic chat, proactive offers, long-range planning, background catalog queries, then non-critical movement/combat.
  - Core server tasks and real player packet handling must remain higher priority.
- [x] Add heap dump trigger plan for suspected leaks.
  - Category: server hardening and agent hardening.
- [x] Add 24-hour, 72-hour, 7-day, and 30-day soak test checklist.
  - Category: server hardening and agent hardening.
  - Agent impact: include scenarios with 500 players simulated or real clients, 2000 agents, map travel, loot, shop use, questing, and shutdown/restart cycles.

## Phase 3 - Defer Until Agent Restructure

Do not implement these before the agent architecture is ready:

- [ ] Install real `AgentPresence` provider from the reconstructed portable agent package.
  - Category: agent hardening.
  - Server prep exists as a noop default in `server.integration.AgentPresence`.
  - Real provider should identify reconstructed agents without hard-wiring core server files to `BotClient`.
  - Until this provider is installed, hidden-character mob simulation behaves like normal Cosmic.
- [ ] Replace per-agent scheduled futures with a sharded agent scheduler.
  - Category: agent hardening.
- [ ] Add lightweight `AgentActor` or reduced agent runtime state.
  - Category: agent hardening.
- [ ] Replace packet-based agent perception with server-state perception.
  - Category: agent hardening.
- [ ] Add NPC/quest capability runtime.
  - Category: agent hardening.
- [ ] Add randomized agent NPC stop spots and dialogue-delay simulation.
  - Category: agent hardening.
- [ ] Rework agent movement, inventory, equipment, or combat runtime behavior.
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
