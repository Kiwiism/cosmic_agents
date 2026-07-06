# Server Hardening Diagnostics

These items were implemented as diagnostics-first changes because enabling full behavior changes immediately could affect gameplay or the in-progress agent reconstruction.

## Scheduler Lanes

- Added named `TimerManager` lanes: `CORE`, `SAVE`, `MAP`, `EVENT`, and `LOW_PRIORITY`.
- Moved selected recurring tasks onto server-owned lanes:
  - ranking and scale health: `LOW_PRIORITY`
  - login storage, Duey/Fredrick, boss log: `SAVE`
  - invitation task: `EVENT`
  - respawn task: `MAP`
- Revisit later:
  - migrate more call sites once timer ownership is fully audited.
  - add per-lane slow-task counters, not only queue/active/completed stats.
  - decide whether future agent work gets a separate executor outside `TimerManager`.
- 2026-07-07 update:
  - Timer diagnostics include configured thread counts per lane.
  - Optional runtime overrides exist for soak tests while preserving existing
    defaults:
    - `-Dcosmic.timer.coreThreads` or `COSMIC_TIMER_CORE_THREADS`
    - `-Dcosmic.timer.saveThreads` or `COSMIC_TIMER_SAVE_THREADS`
    - `-Dcosmic.timer.mapThreads` or `COSMIC_TIMER_MAP_THREADS`
    - `-Dcosmic.timer.eventThreads` or `COSMIC_TIMER_EVENT_THREADS`
    - `-Dcosmic.timer.lowPriorityThreads` or `COSMIC_TIMER_LOW_PRIORITY_THREADS`

## Server Metrics And Load

- Added compact scale-health snapshots with:
  - online player count
  - loaded map count
  - active map count
  - idle map candidate count
  - heap usage
  - DB pool stats
  - thread pool stats
  - timer lane stats
  - server load level
  - EXP debug, monitored-character, and pending NPC runtime cache counts
- Added `ServerLoadMonitor` with `NORMAL`, `BUSY`, `DEGRADED`, and `CRITICAL`.
- Revisit later:
  - include GC pause metrics.
  - include DB wait/timeout counters if Hikari exposes them cleanly.
  - expose metrics through a command or admin endpoint, not only logs.
  - wire `ServerLoadMonitor.allowNonCriticalWork()` into optional debug work after soak testing.
- 2026-07-07 update:
  - `!serverhealth` and scale-health logs include aggregate character save
    pressure.
  - `!serverhealth` and scale-health logs include aggregate map broadcast
    pressure.
  - DB pool diagnostics include configured max pool size and connection timeout.
  - `!serverhealth` includes the current slow-operation warning thresholds for
    login, login-state, character load/save/delete, startup DB work,
    map-update, and map-broadcast paths.
  - Optional DB runtime overrides exist for soak tests while preserving existing
    defaults:
    - `-Dcosmic.db.maxPoolSize` or `COSMIC_DB_MAX_POOL_SIZE`
    - `-Dcosmic.db.connectionTimeoutSeconds` or
      `COSMIC_DB_CONNECTION_TIMEOUT_SECONDS`

## Character Save Diagnostics

- Added diagnostics-only save pressure tracking:
  - total saves.
  - failed saves.
  - autosave versus manual/full save counts.
  - average, last, and max save duration.
  - last and slowest character identity.
- Added save reason labels for major non-Agent save paths:
  - autosave.
  - logout.
  - channel/server transition.
  - Cash Shop.
  - MTS.
  - hired merchant.
  - GM save-all.
  - GM world-warp.
- Added section timing around the existing save chunks:
  - character row.
  - pets.
  - keymap.
  - skill macros.
  - inventory.
  - skills.
  - locations.
  - buddies.
  - area/event data.
  - quests.
  - family/cash storage.
- Behavior unchanged:
  - no save section is skipped.
  - no dirty-save behavior is enabled.
  - no Agent save queue or Agent persistence route is installed.
- Revisit later:
  - split real-player and Agent save routing after Agent reconstruction.
  - use soak evidence to decide whether inventory, quest, pet, or storage saves
    need targeted optimization.

## Map Lifecycle

- Added `MapleMap.isActiveForMaintenance()` and idle-time diagnostics.
- Added loaded/active/idle-candidate counts to `MapManager`.
- `MapManager.updateMaps()` now logs idle map tick candidates but still performs normal respawn and MP recovery.
- Revisit later:
  - define pinned maps and event-map unload rules.
  - add an opt-in config for idle map unload after diagnostics prove it is safe.
  - add per-map high-watermark reports for object/drop/reactor leaks.

## Broadcast Path

- Map broadcast monitoring now avoids packet byte copies unless monitored-character logging is active.
- Map broadcasts now warn when they exceed the slow-operation threshold.
- 2026-07-07 update:
  - Added aggregate broadcast pressure diagnostics:
    - total broadcasts.
    - ranged broadcasts.
    - slow broadcasts.
    - average recipients.
    - average, last, and max broadcast duration.
    - last/max map id and recipient count.
  - Broadcast packet semantics are unchanged.
  - No packet suppression, recipient filtering, or Agent-visible broadcast
    policy was added.
- Revisit later:
  - audit specialized broadcast methods that generate per-recipient packets.
  - keep player packet semantics unchanged until agent perception is decoupled.

## Autosave Backpressure

- Character save scheduler now exposes pending-save count.
- Save queue logs a backpressure warning when pending saves exceed the current warning threshold.
- Revisit later:
  - make the threshold configurable.
  - add save reason and priority lanes.
  - keep logout saves synchronous unless there is a durable retry queue.

## Hired Merchant Guard

- Hired merchant creation now rejects duplicate owner state across character, world, and channel maps.
- World/channel merchant registration now has `tryRegister` / `tryAdd` APIs.
- Merchant creation rolls back world registration if channel registration fails.
- Remote merchant access now requires Store Remote Controller item `5470000` when accessing from a different map or channel.
- Revisit later:
  - add packet-level regression tests for duplicate merchant packets.
  - decide whether cross-channel remote access should consume or only require the controller item.
  - audit Fredrick retrieval and force-close paths for matching state cleanup.

## Static Runtime Cache Cleanup

- Logout now clears pending NPC/dressing-room runtime state keyed by character id.
- Scale health logs pending NPC runtime cache count, monitored packet character count, and active EXP debug session count.
- 2026-07-03 update:
  - Logout now also disposes active NPC and quest script manager state for the client before character references are emptied.
  - Scale health now logs login-attempt account count, active login-bypass count, pending New Year card runtime count, and in-login-state client count.
  - New Year card runtime storage is concurrent and card deletion stops any repeating card notification task.
- Revisit later:
  - keep reviewing all static maps keyed by character id/account id/object id.
  - add explicit cleanup hooks for any newly introduced runtime caches.
