# Phase 11 Live Smoke - 2026-07-14

Status: `PARTIAL PASS`

## Isolation

- Worktree: `feature/agent-central-scheduler-runtime`
- Disposable database: `cosmic_scheduler_soak_20260714`
- Database clone verification: 80/80 tables, 27/27 accounts, and 30/30
  characters compared with the local `cosmic` source database.
- Agent population and navigation cache paths were redirected under
  `%TEMP%\cosmic-agent-scheduler-live-gate`.
- The shared WZ junction was treated as read-only.
- The live-gate preflight passed all 8 checks before startup.
- The normal `cosmic` database target was restored after shutdown.

The disposable clone initially had no Agent-only backing accounts. The
authenticated GM used the normal guarded `@spawnbot agent123 confirm` path to
provision one backing character in the disposable database. No schema change,
bulk roster shortcut, or synthetic scheduler registration was used.

## Runtime

The packaged server started with:

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

The login server and three channels reached listening state. A v83 client
authenticated as `Kiwi`, entered channel 1, and spawned `agent123` as Agent
character ID 21. The Agent joined Kiwi's party and remained visible with a
normal character HP bar.

Observed client-visible behavior:

- the Agent moved to Kiwi while the navigation graph warmed and fallback
  movement was active;
- the Agent followed Kiwi across multiple positions and platforms in map
  104040000 without a duplicate visible character;
- the Agent visibly attacked and defeated a Pig (level 7);
- the observer received the normal zero-EXP party message for that kill and a
  `+9` meso pickup notification;
- the Agent remained responsive after graph warm-up and combat activity.

This proves a one-client smoke for spawn, party presence, visible movement,
same-map navigation, basic combat, and basic loot/meso behavior. It does not
prove cross-map travel, dialogue, trade, death/recovery, two-client packet
consistency, or loaded behavior parity.

## Scheduler Diagnostics

The authenticated GM6 diagnostics reported one live registration:

```text
registrations: registered=1 ready=0 waiting=1 paused=0 quiescent=0 overdue=1
cycle budget: configuredMs=10 avgUtil=5.1% maxUtil=9483.0%
lifecycle: registered=1 replaced=0 cancel=0 cleanup=0
shards: count=1 imbalance=0
shard=3 agents=1 ingress=0 due=1 ready=0 priorities=none
```

Only shards with initialized metrics are printed, so a single registered Agent
produced one displayed shard even though the runtime was configured for four.
`@serverhealth` reported `Load level: NORMAL`. The periodic health snapshot
reported two online characters, zero database-pool waiters, and zero failed
character saves.

The first graph build and attack-data initialization produced three expected
slow-tick warnings. The final scheduler snapshot recorded:

```text
cycles=33848 updated=30419 skipped=34 failed=0 slow=3
queueLagMs p50/p95/p99=42/50/51 max=915
workUs p50/p95/p99=187.8/418.2/598.8
budgetExhaustions=0 deferred=0 starvationPromotions=0
mapBudgetDeferrals=0 continuations=0 ingressHighWaterMark=1
```

The 948.3035 ms maximum cycle was the one-time warm-up outlier. It is evidence
for retaining asynchronous graph warm-up and slow-tick visibility, not a
steady-state 10 ms budget claim.

## Shutdown

The process was stopped through the console Ctrl+C path while the Agent was
still live. Shutdown reported:

```text
sessions=1
cancellations=1
pendingAsync=0
remaining=0
asyncExecutors=0
queuedCancelled=0
unterminated=[]
elapsedMs=631
timedOut=false
```

All three channels and world 0 then completed shutdown. Kiwi and `agent123`
were saved, and the disconnected client was closed. This proves deterministic
single-Agent scheduler cleanup during real server shutdown.

## Remaining Gate

Keep `LEGACY_PER_AGENT` as the production default. Phase 11 still requires the
dedicated 250/500/1,000/1,500/2,000-Agent roster stages, sustained 8-hour and
24-hour runs, two-client packet parity, the untested capability cases above,
and a populated restart rollback rehearsal in legacy mode.
