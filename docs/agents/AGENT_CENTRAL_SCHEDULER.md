# Agent Central Scheduler

This document describes the scheduler that exists today and how to operate its
optional single-shard central-sequential mode. The production migration to a fully
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

When enabled, one lazily started repeating task drains bounded registration
ingress and dispatches due live Agent sessions from an indexed minimum heap.
Equal due times retain stable registration order. Cancelling the last
registration drains its lifecycle cleanup and then cancels the central loop.
The same `AgentTickRuntime` and guarded
`AgentTickOrchestrator` execute in both modes.

## Configuration

```text
agents.scheduler.central.enabled=false
agents.scheduler.mode=legacy
agents.scheduler.baseTickMs=50
agents.scheduler.logSlowTicks=true
agents.scheduler.slowTickMs=250
agents.scheduler.maxAgentsPerTick=0
agents.scheduler.ingressCapacityPerShard=4096
agents.scheduler.cycleBudgetMs=10
agents.scheduler.maxWorkItemsPerCycle=256
agents.scheduler.visibleReservePercent=40
agents.scheduler.criticalReservePercent=10
agents.scheduler.starvationPromotionMs=2000
```

`maxAgentsPerTick=0` means unlimited. A positive cap selects the oldest due
records first, using registration sequence as the stable tie-break. Updated
records move to their next future cadence, so another overdue record runs on
the next cycle instead of being starved.

`ingressCapacityPerShard` bounds both the one-shard multi-producer ingress and
the number of scheduler-owned live/closing registrations. One coalesced ingress
marker is retained per admitted registration, which reserves lifecycle cleanup
capacity without an unbounded critical queue. New registration is rejected
when the bound is full; an already admitted cancellation is not dropped.

Each central cycle also has a wall-clock deadline and hard work-count guard.
Critical and visible passes consume their reserved shares before general
background selection. An expensive background record is deferred when its
EWMA estimate does not fit the remaining cycle time; critical and visible work
is never rejected by that estimate. Remaining ready work schedules one
coalesced immediate continuation, so it is delayed rather than lost.

`maxAgentsPerTick` remains a compatibility cap. When positive, the effective
work guard is the lower of it and `maxWorkItemsPerCycle`.

Ready work has these priority classes, from highest to lowest:

```text
CRITICAL
INTERACTIVE
VISIBLE
BACKGROUND_ACTIVE
BACKGROUND_ABSTRACT
DEFERRED
```

Waiting work promotes one level per `starvationPromotionMs`, up to
`INTERACTIVE`; ordinary work therefore makes bounded progress without becoming
lifecycle-critical.

## Isolation and lifecycle

- Removed or replaced sessions are unregistered through their existing
  generation-bound `AgentScheduleHandle` lifecycle handle.
- Active session and generation validation uses the O(1) Agent character ID
  index maintained by `AgentRuntimeRegistry` lifecycle mutations.
- Paused, closed/despawning, stale, and invalid sessions are skipped.
- One uncaught Agent failure is recorded and does not stop later Agents.
- Missed fixed-rate periods are skipped rather than replayed in a burst.
- Producers never mutate the due heap or periodic due time. They submit a
  coalesced synchronization marker; the scheduler cycle is the sole heap writer.
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
Agents, failures, slow Agents, queue lag, bounded rolling p50/p95/p99 delay and
work duration, budget exhaustion, deferral, starvation promotion, and
ingress/due/ready depth. Work-duration windows are also available by
`AgentWorkClass`. Each rolling window retains at most 2048 samples. Enable
slow-tick logging during
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
Phase 3 bounded one-shard ingress and indexed due-time heap: complete
Phase 4 priority, time/cost budgets, aging, and rolling metrics: complete
Phase 5 bounded async completion contract: complete
multi-shard execution: blocked on Cosmic thread-affinity audit
production default switch: blocked on parity and staged soak evidence
```

The central-sequential global scan/sort has been removed. Important current
limitations include the unsliced full Agent callback and unaudited Cosmic
thread affinity. Scheduler-reachable navigation graph construction, Amherst
progress persistence, LLM/network work, and trade/item analysis now run on
separate bounded workload lanes. Their compact results return through the
generation/request-stamped owning mailbox, and a source boundary test rejects
direct future waits in Agent production code. Explicit navigation debug/probe
tools may still build a graph synchronously outside scheduler execution.
Cross-session formation and leader-away operations require a Phase 6 gateway/
ownership decision before multi-shard execution. The repository therefore has
a safe single-writer migration foundation, not the completed centralized
scheduler.

Phase 0 baseline evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-0`. It covers deterministic
legacy and central-sequential callback behavior at 50, 100, 250, and 500
sessions. It does not represent live gameplay or production load evidence.
Phase 1 API, lifecycle, registry, and parity evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-1`.
Phase 2 mailbox, ingress, delayed-callback, and nonblocking-result evidence is
recorded under `docs/agents/evidence/central-scheduler/phase-2`.
Phase 3 heap, bounded-ingress, lifecycle, cadence, and deterministic 500-session
evidence is recorded under `docs/agents/evidence/central-scheduler/phase-3`.
Phase 4 priority, budget, cost, aging, rolling-metric, and overload evidence is
recorded under `docs/agents/evidence/central-scheduler/phase-4`.
Phase 5 bounded workload, stale-completion, saturation, timeout, blocking-scan,
and async persistence evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-5`.

## Rollback

Set `agents.scheduler.mode=legacy` and restart. Removing the explicit mode and
setting `agents.scheduler.central.enabled=false` is the compatibility rollback.
Registration returns to the unchanged per-Agent `TimerManager.register` path.
