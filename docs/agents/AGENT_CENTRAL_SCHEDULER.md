# Agent Central Scheduler

This document describes the scheduler that exists today and how to operate its
optional central-sequential mode. The production migration to a fully
centralized, sharded, asynchronous, budgeted scheduler is specified in
`AGENT_FULL_CENTRALIZED_SCHEDULER_IMPLEMENTATION_PLAN.md`.

## Current mode

The legacy one-repeating-task-per-Agent path remains the default. Mode is now
selected through the explicit JVM property:

```text
-Dagents.scheduler.mode=legacy
-Dagents.scheduler.mode=central-sequential
-Dagents.scheduler.mode=central-sharded
```

`central-sharded` is reserved and intentionally rejected until the shard
runtime is implemented and validated. The old compatibility property still
selects central-sequential when no explicit mode is present:

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
agents.scheduler.mode=legacy
agents.scheduler.baseTickMs=50
agents.scheduler.logSlowTicks=true
agents.scheduler.slowTickMs=250
agents.scheduler.maxAgentsPerTick=0
```

`maxAgentsPerTick=0` means unlimited. A positive cap uses stable round-robin
selection so a busy cycle cannot permanently starve later registrations.

## Isolation and lifecycle

- Removed or replaced sessions are unregistered through their existing
  generation-bound `AgentScheduleHandle` lifecycle handle.
- Active session and generation validation uses the O(1) Agent character ID
  index maintained by `AgentRuntimeRegistry` lifecycle mutations.
- Paused, closed/despawning, stale, and invalid sessions are skipped.
- One uncaught Agent failure is recorded and does not stop later Agents.
- Missed fixed-rate periods are skipped rather than replayed in a burst.
- Mailbox actions drain inside the same guarded tick before gameplay work.
- Selecting a central scheduler mode also enables mandatory bounded mailbox
  ownership. `agents.mailbox.enabled=true` remains available to exercise the
  same boundary while the legacy scheduler owns ticks.
- Accepted mailbox work wakes the owning registration. Multiple wake requests
  coalesce while one immediate dispatcher wake is pending.
- Mailbox submission reports accepted, rejected, or coalesced status and uses
  typed closed, full, stale-session, expired, coalesced, and discarded failure
  reasons. FIFO remains the default; latest-value coalescing is opt-in by key.

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
Phase 0 baseline: complete
Phase 1 stable scheduler API and O(1) session index: complete
Phase 2 mandatory mailbox ownership: complete
single-shard heap: next implementation phase
multi-shard execution: blocked on Cosmic thread-affinity audit
production default switch: blocked on parity and staged soak evidence
```

Important current limitations include the central-sequential global scan/sort,
a blocking navigation graph `join()` reserved for Phase 5 removal,
cumulative-only metrics, and no priority/budget/shard runtime. Cross-session
formation and leader-away operations also require a Phase 6 gateway/ownership
decision before multi-shard execution. The repository therefore has a safe
single-writer migration foundation, not the completed centralized scheduler.

Phase 0 baseline evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-0`. It covers deterministic
legacy and central-sequential callback behavior at 50, 100, 250, and 500
sessions. It does not represent live gameplay or production load evidence.
Phase 1 API, lifecycle, registry, and parity evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-1`.
Phase 2 mailbox, ingress, delayed-callback, and nonblocking-result evidence is
recorded under `docs/agents/evidence/central-scheduler/phase-2`.

## Rollback

Set `agents.scheduler.mode=legacy` and restart. Removing the explicit mode and
setting `agents.scheduler.central.enabled=false` is the compatibility rollback.
Registration returns to the unchanged per-Agent `TimerManager.register` path.
