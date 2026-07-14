# Phase 11 Live Smoke - 2026-07-14

Status: `INCOMPLETE`

## Isolation

- Worktree: `feature/agent-central-scheduler-runtime`
- Disposable database: `cosmic_scheduler_soak_20260714`
- Database clone verification: 80/80 tables, 27/27 accounts, and 30/30
  characters compared with the local `cosmic` source database.
- A follow-up read-only capacity audit found zero characters on Agent-only
  backing accounts in the disposable clone. It cannot run a populated Agent
  stage until a dedicated test roster is provisioned outside this branch.
- Agent population and navigation cache paths were redirected under
  `%TEMP%\cosmic-agent-scheduler-live-gate`.
- The shared WZ junction was treated as read-only.
- The live-gate preflight passed all 8 checks before startup.

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

The login server and three channels reached the listening state in two bounded
runs. A freshly launched v83 client connected to the login server from
`127.0.0.1`, proving client-to-server reachability against the disposable
run.

The second run isolated the authentication failure without logging password
content: the server received the exact expected account name, but the legacy
client sent a five-character password after displaying only one password
marker. This proves the disposable database and login route were reached and
identifies stale hidden client-field content as the pre-authentication
boundary. A cleared-field retry was interrupted by active desktop input before
submission, so no authenticated character was observed or controlled.

The run therefore did not spawn an Agent and does not prove movement,
navigation, combat, loot, dialogue, trade, death/recovery, packet parity, or
load behavior. The temporary input-length diagnostic was removed after the
run, the normal database target was restored, and the disposable account hash
was restored from the source database.

## Shutdown

The server was stopped through the console Ctrl+C path. Its final Agent runtime
report recorded:

```text
sessions=0
cancellations=0
pendingAsync=0
remaining=0
asyncExecutors=0
queuedCancelled=0
unterminated=[]
elapsedMs=3000
timedOut=false
```

The second bounded run also reported zero sessions, pending async work,
remaining work, or unterminated executors, with `timedOut=false` and
`elapsedMs=2660`. All three channels and world 0 completed shutdown in both
runs. This proves clean zero-Agent startup/shutdown wiring only; it does not
replace shutdown validation with a live Agent population.

## Next Gate

Repeat the live parity script with an authenticated client after explicitly
clearing the legacy password field. Capture `@agentscheduler` and
`@serverhealth`, then run the staged
250/500/1,000/1,500/2,000-Agent sequence from `LIVE_SOAK_RUNBOOK.md`.
