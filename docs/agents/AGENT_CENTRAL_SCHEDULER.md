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

## Rollback

Remove or set `agents.scheduler.central.enabled=false` and restart. Registration
returns to the unchanged per-Agent `TimerManager.register` path.
