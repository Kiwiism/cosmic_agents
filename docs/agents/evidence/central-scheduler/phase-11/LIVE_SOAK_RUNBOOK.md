# Phase 11 Live Soak Runbook

## Safety Rules

- Use a disposable test database and a dedicated server process.
- Keep `LEGACY_PER_AGENT` as the known rollback startup mode.
- Change one scheduler feature at a time and restart between stages.
- Keep background abstraction disabled; it has no production implementation.
- Stop a stage on stale execution, duplicate action, invalid inventory/economy
  state, unbounded queue growth, or visible parity failure.

## Stage Order

1. Legacy baseline at 250 Agents with one and two observing clients.
2. `central-sequential` at 250, then 500 Agents.
3. `central-sharded` at 500 Agents with simulation and tick slicing disabled.
4. Sharded mixed presentation/background-active policy at 1,000 Agents.
5. Sharded tick slicing at 1,000 Agents after unsliced parity passes.
6. Sharded load shedding at 1,500 Agents; force pressure and observe recovery.
7. Sharded 2,000-Agent soak for 8 hours, then 24 hours, then multi-day.
8. Clean shutdown/restart and explicit legacy rollback rehearsal.

## JVM Properties

Start with these explicit properties so the evidence records the exact mode:

```text
-Dagents.scheduler.mode=central-sharded
-Dagents.scheduler.shardCount=4
-Dagents.scheduler.baseTickMs=50
-Dagents.scheduler.simulation.enabled=false
-Dagents.scheduler.simulation.backgroundAbstract.enabled=false
-Dagents.scheduler.tickSlicing.enabled=false
-Dagents.scheduler.loadShedding.enabled=false
-Dagents.scheduler.logSlowTicks=true
```

Enable simulation, tick slicing, and load shedding only at their listed stage.
Do not enable background abstraction.

## Capture Every 5 Minutes

Run `@agentscheduler` from a GM6 character for the bounded Agent scheduler,
shard, load-shedding, quiescence, and async-queue snapshot. Run
`@serverhealth` beside it for JVM, database-pool, and core server health.
Record both outputs with a timestamp and current stage/population.
Use the bounded `@agentscheduler top slow`, `top overdue`, `top maps`, `top
mailboxes`, and `top failures` views when the aggregate snapshot shows pressure.
Use `@agentscheduler costs` to compare work-class, simulation-mode, and
tick-slice p50/p95/p99 cost between stages.
Enable the existing Agent performance monitor before using `top capabilities`.

- Agent population and per-shard registration counts;
- ingress, due, and ready depths plus high-water marks;
- scheduler delay and work-duration p50/p95/p99;
- cycle-budget average/maximum utilization and global/per-shard ready work
  current/high-water depth by priority;
- budget exhaustion, deferral, starvation, failure, and stale counts;
- mailbox and async-lane depth, rejection, timeout, stale, expired, and drained
  counts;
- lifecycle register, replacement, cancellation, and cleanup counts;
- load-shedding level, reason, suppression, and recovery counts;
- heap used after full GC trend, GC pause trend, process CPU, and thread count;
- database pool active/waiting counts;
- real-player login, map-change, combat, NPC, trade, and shop latency;
- spawned, removed, replaced, dead/recovered, and stuck Agent counts.

`@agentscheduler` is read-only. It caps detail at eight shards, twelve
initialized async queues, and ten top/detail rows so diagnostics cannot create
an unbounded chat dump.

## Live Parity Script

With one observer, verify spawn, idle, follow, cross-map navigation, climb,
combat, loot, dialogue, trade, death/recovery, despawn, and relogin. Repeat the
movement/combat/map-transition subset with two observers and compare position,
effects, mob state, and duplicate packets.

## Pass Conditions

- all queue depths stay bounded and return after Agent removal;
- p95/p99 delay stabilizes after warmup;
- heap usage plateaus and no scheduler-owned object count trends upward;
- no session executes concurrently or after generation replacement;
- real-player actions remain responsive;
- shutdown drains within its deadline and restart is clean;
- legacy rollback starts and preserves the same visible behavior.
