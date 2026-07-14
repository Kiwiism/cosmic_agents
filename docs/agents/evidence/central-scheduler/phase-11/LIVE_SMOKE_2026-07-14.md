# Phase 11 Live Smoke - 2026-07-14

Status: `INCOMPLETE`

## Isolation

- Worktree: `feature/agent-central-scheduler-runtime`
- Disposable database: `cosmic_scheduler_soak_20260714`
- Database clone verification: 80/80 tables, 27/27 accounts, and 30/30
  characters compared with the local `cosmic` source database.
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

The login server and three channels reached the listening state. A freshly
launched v83 client connected to the login server from `127.0.0.1`, proving
client-to-server reachability against the disposable run.

Windows capture timed out before an authenticated character could be observed
or controlled. The run therefore did not spawn an Agent and does not prove
movement, navigation, combat, loot, dialogue, trade, death/recovery, packet
parity, or load behavior.

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

All three channels and world 0 completed shutdown. This proves clean zero-Agent
startup/shutdown wiring only; it does not replace shutdown validation with a
live Agent population.

## Next Gate

Repeat the live parity script with a targetable authenticated client. Capture
`@agentscheduler` and `@serverhealth`, then run the staged
250/500/1,000/1,500/2,000-Agent sequence from `LIVE_SOAK_RUNBOOK.md`.
