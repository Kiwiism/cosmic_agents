# Server Hardening Diagnostics

Implementation status through 2026-07-10 is recorded in
`docs/SERVER_HARDENING_2026_07_IMPLEMENTATION.md`.

## Bounded Work Executors

- `ThreadManager` now owns separate bounded `general`, `blocking`, `database`,
  and routine `autosave` executors. The former 20-to-1000-thread pool and
  caller-run rejection fallback are gone.
- Every lane reports active, queued, pool, completed, submitted, and rejected
  counts. Thread names identify their lane.
- Sizing can be changed without editing YAML through
  `cosmic.threads.<lane>.core`, `.max`, and `.queue` system properties, or the
  corresponding uppercase environment variables.
- Blocking and database fallback work is never executed on a Netty caller.

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
- 2026-07-10 implementation:
  - autosaves use versioned dirty sections for stats, inventory, skills,
    quests, social state, keymaps/macros, locations, pets, and related state.
  - mutable child collections use locked point-in-time snapshots before their
    SQL work; Character scalar writes remain protected by versioned
    post-mutation signals and their existing field locks.
  - a mutation that races a save leaves its section dirty.
  - every sixth successful autosave is a conservative full checkpoint; tune
    with `cosmic.persistence.fullCheckpointAutosaves`.
  - logout, manual, Cash Shop, MTS, server-transition, save-all, and other
    `notAutosave` routes remain full transactional checkpoints.
  - diagnostics include clean autosaves skipped and per-section write counts.
  - 2026-07-17 scaling update:
    - each world snapshots logged-in character ids once per autosave window and
      randomizes their order;
    - saves are spaced across the complete one-hour window rather than fired as
      one synchronous hourly sweep;
    - dispatch resolves the current Character by id, so a logged-out instance
      is not retained for the rest of the window;
    - a dedicated bounded two-worker, 256-entry autosave executor isolates
      routine saves from gameplay, logout, and database task lanes;
    - duplicate character work coalesces, queue saturation applies backpressure
      without dropping the character from the current cycle, and catch-up work
      is capped per dispatcher pass;
    - `!serverhealth` reports autosave pending, accepted, coalesced, and
      backpressured counts beside the executor queue metrics.
  - autosave runtime controls preserve the production defaults:
    - `cosmic.persistence.autosaveWindowMs` (default `3600000`)
    - `cosmic.persistence.autosaveDispatchIntervalMs` (default `250`)
    - `cosmic.persistence.autosaveMaxDispatchPerRun` (default `8`)
    - `cosmic.threads.autosave.core` / `.max` (default `2` / `2`)
    - `cosmic.threads.autosave.queue` (default `256`)
  - 2026-07-11 ownership audit added Item/Equip, Pet, Mount, SkillMacro,
    CashShop, FamilyEntry, Storage, BuddyList, QuestStatus, MonsterBook, and
    Events callbacks, including detached-child cleanup and zero-row failures.
  - verification: 36 focused persistence tests and 2,012 non-Agent tests pass
    with zero failures, errors, or skips.
- Revisit later:
  - split real-player and Agent save routing after Agent reconstruction.
  - use soak evidence to decide whether inventory, quest, pet, or storage saves
    need targeted optimization.

## Map Lifecycle

- Added `MapleMap.isActiveForMaintenance()` and idle-time diagnostics.
- Added loaded/active/idle-candidate counts to `MapManager`.
- Empty, inactive maps skip respawn/MP-recovery work after the dormant
  threshold. Mob MP and normal respawn state are restored when the first
  character returns.
- Idle unload is implemented but remains opt-in. It rejects occupied, event,
  merchant/shop, owner, drop, active-reactor/status, scripted, transport,
  timer, and scheduled-task maps.
- Runtime controls:
  - `cosmic.maps.dormantSkipMillis` (default `60000`)
  - `cosmic.maps.idleUnloadEnabled` (default `false`)
  - `cosmic.maps.idleUnloadMillis` (default `1800000`)
- Map diagnostics include skipped dormant ticks and unload count.

## EXP Logging And Runtime Failures

- Optional EXP logging now has a bounded queue, configurable batch size and
  interval, transactional batches, failed-batch requeue, drop/failure metrics,
  interrupt-correct shutdown, and a synchronous final flush.
- Runtime controls are `cosmic.expLogger.queue`, `.batch`, and
  `.interval.seconds` (with uppercase environment equivalents).
- Legacy runtime `printStackTrace` calls outside offline tooling, agents, and
  Database Console were replaced by caller-aware structured logging.
  Equivalent bursts are summarized over a five-second window rather than
  flooding stdout. The runtime `tools.PacketCreator` Fredrick path now reports
  contextual character/account information through its caller rather than
  printing directly.

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

2026-07-17 population and recipient update:

- `PlayerStorage` maintains immutable, lazily rebuilt snapshots for all
  characters, real players, headless Agents, and attached network recipients.
- Player and Agent counts are maintained independently without rebuilding the
  snapshots. Channel/world capacity uses real-player counts only.
- `!serverhealth`, scale-health logs, and the internal health endpoint report
  players, Agents, and total characters separately.
- Map, channel, and world packet broadcasts iterate real network recipients;
  Agent characters remain in the combined gameplay collections.
- Session timeout checks inspect only attached real-network clients.

## Shared Agent Perception

- Each `MapleMap` exposes one immutable membership snapshot containing its
  monsters, dropped items, and NPCs.
- The snapshot is reused across Agent reads and invalidated under the existing
  map-object write lock whenever relevant membership changes.
- Snapshot entries retain live map-object references, so HP, position, pickup,
  and other mutable gameplay state are not frozen.
- The original direct map-query methods remain available as a compatibility
  fallback.

## JVM And Disk Guardrails

- `launch.bat` and the Docker entrypoint enable bounded GC/safepoint logs,
  heap dumps on OOM, fatal JVM error files, and process exit on OOM.
- Docker Compose persists the log directory and restarts unexpected exits.
- Disk space is checked on startup and every five minutes. State transitions
  are logged and current state is included in health diagnostics.
- Manual heap dumps are rejected when the estimated dump would breach the
  configured critical free-space reserve.
- Full settings and IntelliJ VM options are documented in
  `docs/PRODUCTION_JVM_GUARDRAILS.md`.

## Autosave Backpressure

- Character autosaves use a dedicated bounded executor and expose its active,
  queued, submitted, completed, and rejected counts.
- The dispatcher exposes pending, accepted, coalesced, and backpressured totals.
- A full queue pauses the current staggered cycle at that character and retries
  on a later dispatcher pass; it does not push routine saves onto a caller or
  gameplay thread.
- Revisit later:
  - add durable priority ordering if logout saves are ever made asynchronous.
  - keep logout saves synchronous unless there is a durable retry queue.

## Production Logging

- The production root and console levels default to `INFO`; packet logging and
  JDBI, Netty, and Hikari library noise default to `WARN`.
- General file output uses an 8,192-entry blocking asynchronous appender. The
  queue remains finite and never silently discards an accepted log event.
- Every file appender now rolls daily and at 20 MB. There are no remaining
  plain unbounded file appenders.
- General archives retain at most 30 days and 2 GB. Chat retains 30 days and
  1 GB. Trade retains 30 days and 512 MB. Monitored packets retain 7 days and
  512 MB. Other specialist archives retain 30 days and 256 MB each.
- Temporary diagnostic overrides are available without editing the XML:
  - `-Dcosmic.log.rootLevel=debug`
  - `-Dcosmic.log.consoleLevel=debug`
  - `-Dcosmic.log.packetLevel=debug`

## Hired Merchant Guard

- Hired merchant creation now rejects duplicate owner state across character, world, and channel maps.
- World/channel merchant registration now has `tryRegister` / `tryAdd` APIs.
- Merchant creation rolls back world registration if channel registration fails.
- Remote merchant access now requires Store Remote Controller item `5470000` when accessing from a different map or channel.
- Fredrick storage is loaded before the response packet is constructed. A load
  failure therefore sends no partial packet, is shown as an explicit retryable
  error instead of empty storage, and cannot reach meso withdrawal or item
  deletion. A successful NPC eligibility lookup is reused when opening the
  Fredrick view, avoiding the previous duplicate DB load.
- Revisit later:
  - add packet-level regression tests for duplicate merchant packets.
  - decide whether cross-channel remote access should consume or only require the controller item.
  - run packet-level disconnect/retry/full-inventory tests with the v83 client.

2026-07-11 merchant update:

- Successful retrieval is one SQL transaction across resulting inventory,
  merchant balance, merchant item/equipment rows, and reminder state.
- Failed SQL restores the original live inventory.
- Merchant snapshot and reminder writes share one transaction.
- World/channel cleanup removes only the exact registered merchant instance.
- Merchant item/bundle loading uses one joined query instead of `1 + N`.

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

2026-07-11 cache update:

- Empty Messenger instances are released when their final member leaves.
- Account view/storage cleanup is null-safe and lock-protected.
- World cache and transition buff/disease counts are visible through
  `!serverhealth`.
- Metadata caches remain intentionally bounded by game-data IDs.
