# Agent Central Scheduler

This document describes the scheduler that exists today and how to operate its
optional central-sequential mode. The production migration to a fully
centralized, sharded, asynchronous, budgeted scheduler is specified in
`AGENT_FULL_CENTRALIZED_SCHEDULER_IMPLEMENTATION_PLAN.md`.

## Current mode

The legacy one-repeating-task-per-Agent path remains the default. Set the JVM
property below only for parity or soak validation:

```text
-Dagents.scheduler.central.enabled=true
```

This flag enables the current sequential parity dispatcher, not the planned
full sharded scheduler. Do not treat it as the production 2000-Agent mode.

When enabled, one lazily started repeating task dispatches all due live Agent
sessions in stable registration order. Cancelling the last registration also
cancels the central loop. The same `AgentTickRuntime` and guarded
`AgentTickOrchestrator` execute in both modes.

## Configuration

```text
agents.scheduler.central.enabled=false
agents.scheduler.baseTickMs=50
agents.scheduler.logSlowTicks=true
agents.scheduler.slowTickMs=250
agents.scheduler.maxAgentsPerTick=0
```

`maxAgentsPerTick=0` means unlimited. A positive cap uses stable round-robin
selection so a busy cycle cannot permanently starve later registrations.

## Isolation and lifecycle

- Removed or replaced sessions are unregistered through their existing
  `ScheduledFuture` lifecycle handle.
- Paused, closed/despawning, stale, and invalid sessions are skipped.
- One uncaught Agent failure is recorded and does not stop later Agents.
- Missed fixed-rate periods are skipped rather than replayed in a burst.
- Mailbox actions drain inside the same guarded tick before gameplay work.

## Metrics

`AgentSchedulerMetrics.snapshot()` reports cycle duration, updated and skipped
Agents, failures, slow Agents, and queue lag. Enable slow-tick logging during
soak validation, then compare movement, combat, loot, dialogue, and lifecycle
parity against legacy mode before considering central scheduling as the default.

Automated validation includes callback-cadence parity and a deterministic
500-session, 20-cadence dispatcher soak (10,000 isolated updates). This validates
dispatcher mechanics only; live-client movement/combat parity and a sustained
server soak remain required before changing the default mode.

## Full Scheduler Readiness

The implementation-ready design is maintained in
`AGENT_FULL_CENTRALIZED_SCHEDULER_IMPLEMENTATION_PLAN.md`.

Current readiness:

```text
Phase 0-1 foundation: ready to begin
mailbox default enablement: blocked on nonblocking command results
single-shard heap: follows mailbox ownership scans
multi-shard execution: blocked on Cosmic thread-affinity audit
production default switch: blocked on parity and staged soak evidence
```

Important current limitations include O(N) registry session validation,
unclassified delayed callbacks, a blocking chat mailbox compatibility wait,
cumulative-only metrics, and no priority/budget/shard runtime. The repository
therefore has a useful migration foundation, not the completed centralized
scheduler.

Phase 0 baseline evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-0`. It covers deterministic
legacy and central-sequential callback behavior at 50, 100, 250, and 500
sessions. It does not represent live gameplay or production load evidence.

## Rollback

Remove or set `agents.scheduler.central.enabled=false` and restart. Registration
returns to the unchanged per-Agent `TimerManager.register` path.
