# Non-Agent Server Hardening Implementation

Date: 2026-07-10  
Branch: `reconstruction/source-master-agent-base`

## Scope And Guarantees

This pass hardens the Cosmic server outside the agent reconstruction. It does
not change WZ/XML assets, YAML values, gameplay rates, combat formulas, quest
rewards, scripts, Database Console behavior, or valid player packet flows.
Malformed, exploitative, replayed, impossible, and failed operations are now
rejected or recovered more safely.

No file under `server/agents`, `server/bots`, agent tests, or `docs/agents` was
edited by this pass.

## Pre-Pass Runtime Baseline

The latest pre-pass `!serverhealth` capture supplied from a live two-character
session recorded:

```text
players=2
loaded/active/idle-candidate maps=245/110/129
loaded-map high watermark=245
heap=352/8032 MB
DB pool active/idle/total/waiting=0/10/10/0
DB max/connection timeout=10/30000 ms
ThreadManager active/queued/pool/completed/submitted/rejected=0/0/20/7617/7617/0
character saves total/failed/manual/autosave=3/0/2/1
character save average/max=394/906 ms
map broadcasts total/slow/ranged=8281/0/0
map broadcast average/max=0/2 ms
load=NORMAL
```

Timer queues in that capture were core `136`, save `3`, map `1`, event `3`,
and low-priority `3`; all lanes were inactive at the instant sampled. This is
an operational reference, not a controlled benchmark. Post-pass deltas require
the rebuilt server smoke and staged soak because the new executor lanes, dirty
saves, and dormant-map counters do not exist in the old process.

## Implemented Phases

### 1. Exploit And Crash Prevention

- Shop purchases validate slot, item, quantity, non-free recharge entries, and
  checked long cost arithmetic. The broken Golden Maple Leaf fallback was
  removed; normal meso, Perfect Pitch, and recharge behavior remains.
- Server-owned NPC prompt state validates prompt type, numeric range,
  menu/style membership, and one-time consumption before script invocation.
- Login exits immediately if client-lock acquisition fails.
- Packet opcode bounds, capped previews, malformed counters, throttled logs,
  and repeated-violation disconnects are enforced.
- Monster Carnival guardian selection uses the finite eligible set.
- Fredrick merchant storage is loaded before packet construction. SQL failure
  produces a contextual log and explicit retry message, never a truncated or
  false-empty storage response, and retrieval load failure cannot withdraw
  mesos or delete stored items.

### 2. Persistence Correctness

- Character deletion validates ownership, deletes dependencies before the
  character in one transaction, rolls back failures, and performs memory/social
  cleanup only after commit.
- Pet save/delete accepts the caller connection. Pet-ignore and pet rows share
  the surrounding transaction; cash IDs are released only after success.
- Character autosaves use versioned sections: `STATS`, `INVENTORY`, `SKILLS`,
  `QUESTS`, `SOCIAL`, `KEYMAP`, `LOCATIONS`, `PETS`, and `RELATED`. Central
  child ownership hooks cover inventory items/equipment, quest
  status/progress/medals, buddy-list entries, monster-book cards, pets, mounts,
  skill macros, account storage, Cash Shop state, family reputation, and event
  state. Hooks are attached after DB hydration so login loads do not create
  false dirty work.
- Storage save errors now escape to the enclosing character transaction. A
  failed transaction never completes its save plan, and storage remains marked
  for retry. A mutation racing an in-flight save advances that section version
  and therefore remains dirty after the older snapshot commits.
- Child callbacks run after the persisted value changes. Fully removed or
  replaced inventory, buddy, quest, and macro children are detached so later
  mutations cannot dirty their former owner.
- Skills are saved as an authoritative delete-and-insert snapshot, so a removed
  skill cannot remain as a stale database row if its earlier immediate delete
  failed.

Verified section ownership:

| Section | Persisted ownership boundary |
| --- | --- |
| `STATS` | Character row fields, AP/SP, map/spawn position, inventory limits, mount values, and monster-book cards |
| `INVENTORY` | Inventory membership and every persisted `Item`/`Equip` field |
| `SKILLS` | Immutable skill-entry snapshots and authoritative replacement |
| `QUESTS` | Quest status, counts, expiration, progress, and medal maps |
| `SOCIAL` | Buddy capacity, membership, group, and visibility |
| `KEYMAP` | Key bindings, quickslots, and skill macros |
| `LOCATIONS` | Saved locations plus regular and VIP teleport rocks |
| `PETS` | Active pets, persisted pet values, and item-ignore sets |
| `RELATED` | Area/event state, family reputation, Cash Shop account/inventory/wishlist state, and account storage |

### 3. Concurrency And Lifecycle

- Map HP updates are thread-safe and coalesced per character while retaining a
  pending death signal, party HP update, and Berserk check.
- Mutable collection getters return locked snapshots, including nested pet
  exclusions and the audited character/map/social/shop collections.
- Channel construction fails fast and cleans partial resources. Shutdown stages
  continue independently, always reach a terminal state, and server waiting is
  bounded to 60 seconds per channel.
- Session initialization and login-attempt membership use atomic concurrent
  sets. Diagnostics, metadata caches, monitor controls, player-NPC ranks, pet
  data, life/reactor data, and storage fees are concurrency-safe.
- Character quest-expiry cleanup and both PlayerStorage indexes are cleared.

### 4. Resource Control

- Bounded general, blocking, and database executors replace the 20-to-1000
  thread pool. Rejected work is measured and never caller-run on Netty.
- EXP audit logging is bounded, batched, transactional, retry-aware,
  observable, and synchronously flushed at shutdown.
- MonsterStats uses deterministic explicit copying with independent mutable
  collections; reflection and the ten-second failure sleep are removed.
- Runtime stack traces now use contextual, rate-limited structured logging.

### 5. Scaling

- Versioned dirty-section autosaves reduce repeated delete/reinsert work. A
  periodic full checkpoint covers legacy direct mutators; all manual/logout
  checkpoints remain full.
- Empty dormant map work is skipped. Optional idle unloading is guarded by
  strict runtime safety checks and defaults off.

## Files By Phase

### Phase 1

- `server/Shop.java`
- `scripting/npc/NpcPromptState.java`
- `scripting/npc/NPCConversationManager.java`
- `net/server/channel/handlers/NPCMoreTalkHandler.java`
- `net/server/channel/handlers/PlayerLoggedinHandler.java`
- `net/PacketProcessor.java`, `net/packet/InPacket.java`,
  `net/packet/ByteBufInPacket.java`, `net/packet/MalformedPacketTracker.java`
- `client/Client.java`, `server/maps/MapleMap.java`
- Focused shop, prompt, login-lock, packet, and guardian tests.

### Phase 2

- `client/Character.java`
- `client/inventory/Pet.java`
- `client/processor/npc/FredrickProcessor.java`
- Character-deletion and pet transaction tests.

### Phase 3

- Snapshot owners: `client/Character.java`, `client/BuddyList.java`,
  `client/MonsterBook.java`, `client/inventory/Inventory.java`,
  `server/Storage.java`, `server/maps/MapleMap.java`,
  `server/maps/PlayerShop.java`, `server/maps/HiredMerchant.java`,
  `net/server/world/Party.java`, `net/server/world/World.java`, and
  `net/server/channel/Channel.java`.
- Lifecycle/session: `net/server/channel/ShutdownStageRunner.java`,
  `net/server/Server.java`, `net/server/PlayerStorage.java`, and
  `net/server/coordinator/session/SessionInitialization.java`.
- Concurrent cache/control changes in `AutobanFactory`, `PetDataFactory`,
  `ItemConstants`, `MonitoredChrLogger`, `LifeFactory`,
  `MobAttackInfoFactory`, `PlayerNPC`, and `ReactorFactory`.
- Collection, HP coalescing, session, storage, party, and shutdown tests.

### Phase 4

- `server/ThreadManager.java` plus blocking/database call-site migrations in
  client disconnect, GM world/channel commands, ID lookup, and event runtime.
- `server/ExpLogger.java`, `server/BoundedBatchBuffer.java`.
- `server/life/MonsterStats.java`.
- `monitoring/RuntimeFailureLogger.java` plus mechanical replacement of raw
  runtime stack traces in non-agent server handlers, commands, scripting,
  persistence, networking, guild, map, and provider catch paths.
- Executor configuration, EXP backlog, and monster-copy tests.

### Phase 5

- `server/persistence/DirtySectionTracker.java`.
- Dirty marking/snapshots in `client/Character.java`,
  `client/inventory/Inventory.java`, and `client/inventory/Pet.java`.
- `server/maps/MapManager.java`, `server/maps/MapleMap.java`,
  `server/life/Monster.java`, `server/maps/Reactor.java`, and map metrics in
  `net/server/channel/Channel.java` / `net/server/Server.java`.
- `server/monitoring/CharacterSaveDiagnostics.java` and dirty/map lifecycle
  tests.

## Verification

- Java 21 production compile: pass.
- Dirty persistence focused suite: 36 tests across 13 classes, all pass. It
  covers every section with a
  representative mutation; clean autosave selection; explicit and periodic
  full checkpoints; in-flight mutation races; failed-plan retention; unchanged
  version clearing; quest, buddy, monster-book, inventory/equipment, macro,
  Cash Shop, family, pet, mount, event, and storage ownership; zero-row SQL
  failure propagation; detached-child behavior; and immutable point-in-time
  child snapshots.
- Focused hardening suite: 20 test classes, pass. This includes a saturated
  one-worker/one-queue executor test proving rejected work is counted and is
  never caller-run.
- Fredrick addendum: 7 focused tests pass for successful cached display load,
  empty and stored-item packet framing, direct/display load failure, explicit
  retry reporting, and non-destructive retrieval load failure.
- Current non-agent suite: 2,008 tests across 51 generated test reports; all
  pass with no skips. The three previously recorded
  `CombatFormulaProviderTest` fixture failures are resolved in the current
  working tree.
- Static runtime logging audit: no active `printStackTrace`, `System.out`, or
  `System.err` calls outside excluded tools/agent/Database Console scope.
- Packaged-server startup smoke: pass. The reviewed build reached login plus
  three channel listeners in 13.3 seconds and exposed the Database Console
  bridge without startup errors.
- Two real v83 clients launched concurrently and both established login-server
  connections. Windows runs this legacy client elevated, so the available
  desktop-control process could not activate or send input to either window.
  Authenticated gameplay scenarios therefore remain a required manual gate;
  simultaneous sockets alone are not reported as a full two-client pass.
- Production shutdown/restart smoke: pass. All three channels completed their
  independent shutdown stages, the world released its resources, and all five
  listeners closed. A fresh JVM returned to `UP`/`NORMAL` with login, three
  channels, and the admin bridge in 10.6 seconds, then repeated the same clean
  terminal shutdown.

The first post-change idle health sample recorded `players=0`, `maps=243/7`,
`heap=353/8032MB`, DB pool `0/10/10/0`, and zero executor rejections. The
second sample remained `load=NORMAL`, had zero DB waiters/rejections, and showed
bounded lanes processing `1,078` blocking and `1` database tasks. These are
startup observations rather than a load comparison.

## Expected Impact

- Thread creation is capped at configured lane maxima instead of up to 1000.
- EXP backlog memory is capped at 100,000 records by default.
- Repeated clean autosaves avoid unchanged DB sections; periodic checkpoints
  cap the maximum time to recover an unmarked legacy mutation.
- Empty dormant maps avoid per-monster MP scans; optional unload can release
  complete map object graphs after safety checks.
- Snapshot getters and concurrent caches remove iteration races and accidental
  mutation through supposedly read-only views.

Runtime percentages require the baseline and canary soak samples; no synthetic
percentage is claimed from unit tests.

## Rollback And Controls

- Map unloading: leave or set `cosmic.maps.idleUnloadEnabled=false`.
- Dormant skipping: set `cosmic.maps.dormantSkipMillis` above the intended
  uptime while investigating.
- Dirty-save safety frequency: set
  `cosmic.persistence.fullCheckpointAutosaves=1` to make every autosave a full
  checkpoint without reverting code.
- Executor and EXP logger values can be restored by removing their system
  property/environment overrides.
- Code rollback should be performed phase-by-phase from the file list in the
  final task report; do not revert agent reconstruction or unrelated dirty
  files.

## Deferred Risks

- Idle unload is default-off until event/PQ/reactor/transport canary coverage
  passes.
- Dirty markers cover the audited non-Agent central mutation paths and are
  backed by periodic full checkpoints. `Character.getKeymap()` still exposes
  the legacy mutable map because Agent potion cleanup directly removes keys;
  those Agent-only removals bypass the `KEYMAP` marker and remain a
  post-reconstruction boundary cleanup. The unused `Pet.setUniqueId(...)`
  mutation surface was removed after a full source/test reference scan found
  no caller. Pet identity changes now require an explicit future migration API
  rather than silently severing DB links. New mutators must mark their owning
  section rather than using mark-all.
- DB hydration intentionally assigns some fields before child ownership hooks
  are attached. A freshly loaded character starts with all sections dirty, so
  its first successful autosave/checkpoint remains conservative.
- Unit tests prove section selection, snapshots, transaction behavior, and
  child ownership signals. A real MySQL relogin gate remains required for
  inventory/equipment, skills, quest progress, monster book, pets, storage,
  buddies, and saved-location round trips on the packaged server.
- A real two-client smoke and long soak cannot be replaced by unit tests.
- Timed-out channel shutdown work is interrupted, detached, and forced to a
  terminal state, but Java cannot kill a task stuck in non-interruptible native
  or driver code; restart testing should confirm no such task survives.
- DB index changes remain evidence-driven and were not included.
- Fredrick retrieval is transactionally recoverable. It locks the character
  balance and merchant rows, builds the resulting inventory while character
  saves are excluded, then commits the complete inventory snapshot, meso
  settlement, merchant/equipment deletion, and reminder deletion together.
  SQL failure rolls back and restores the pre-retrieval live inventory.
- A JVM failure after SQL commit but before the final success packet is
  idempotent on relogin because the committed inventory is authoritative and
  merchant rows are gone. Packet-disconnect timing remains a manual gate.

## 2026-07-11 Completion Pass

Implemented:

- Fredrick retrieval transaction and live rollback recovery.
- Existing age-based Fredrick storage fees preserved for positive and negative
  merchant balances, with null/future/expired timestamp regression coverage.
- Atomic hired-merchant snapshot plus Fredrick reminder writes.
- One-query merchant item/bundle loading and corrupt quantity validation.
- Identity-checked world/channel merchant removal, including force close.
- Empty Messenger registry cleanup and immutable synchronized member snapshots.
- Null-safe account-view cleanup and safe guild-summary iteration.
- World/transition cache counts in `!serverhealth`.
- Bounded timer-executor termination wait.
- Startup-failure world-resource cleanup and nullable login-listener shutdown.
- Effective 200-result cap for merchant search results.

Evidence:

- 2,012 tests across 56 concrete non-Agent test classes: zero failures,
  errors, or skips. `testutil.HandlerTest` is an abstract test fixture.
- Real MySQL opt-in integration tests: merchant bundle load, corrupt quantity
  rejection, Fredrick commit, and forced rollback all pass.
- Java 21 production compile and package: pass.
- Packaged startup: login, channels 1-3, and Database Console bridge reached
  ready state in 8.6 seconds.

Evidence-gated, not implemented:

- DB indexes: no slow-query/`EXPLAIN` evidence.
- Packet-byte reuse: no measured broadcast hotspot or proven identical-packet
  candidate in the available logs.
- Idle-map unloading: remains default-off pending reactor, PQ/event, boss,
  transport, merchant, summon/drop, return-visit, and long-soak canaries.
- Buff handoff TTL: would alter Cash Shop/MTS restoration behavior without a
  reproduced leak.

Manual gates:

- authenticated two-client gameplay/relogin across every save route;
- Fredrick retry/disconnect/full-inventory behavior in the v83 client;
- graceful admin shutdown and restart with players/merchants online;
- staged 24-hour, 72-hour, 7-day, and 30-day soak runs.
