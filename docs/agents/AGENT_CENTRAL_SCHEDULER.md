# Agent Central Scheduler

This document describes the scheduler that exists today and how to operate its
optional central-sequential and central-sharded modes. The production migration
and remaining validation gates are specified in
`AGENT_FULL_CENTRALIZED_SCHEDULER_IMPLEMENTATION_PLAN.md`.

## Current mode

The legacy one-repeating-task-per-Agent path remains the default. Mode is now
selected through the explicit JVM property:

```text
-Dagents.scheduler.mode=legacy
-Dagents.scheduler.mode=central-sequential
-Dagents.scheduler.mode=central-sharded
```

`central-sharded` is implemented as an explicit opt-in. It creates a fixed
stable-hash owner set and is guarded by the closed Cosmic gateway-affinity
catalog. It is not production-approved or the default. The old compatibility
property still selects central-sequential when no explicit mode is present:

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
agents.scheduler.shardCount=max(1, min(4, availableProcessors / 2))
agents.scheduler.simulation.enabled=false
agents.scheduler.simulation.backgroundAbstract.enabled=false
agents.scheduler.simulation.backgroundActiveTickMs=250
agents.scheduler.simulation.backgroundAbstractHeartbeatMs=5000
agents.scheduler.simulation.backgroundMaxWorkPerMapPerCycle=32
agents.scheduler.tickSlicing.enabled=false
agents.scheduler.tickSlicing.maxSlicesPerTurn=2
agents.scheduler.tickSlicing.maxContinuationsPerFrame=8
agents.scheduler.loadShedding.enabled=false
agents.scheduler.loadShedding.sampleIntervalMs=1000
agents.scheduler.loadShedding.pressureCycles=3
agents.scheduler.loadShedding.recoveryCycles=20
agents.scheduler.loadShedding.queueLagLevel1Ms=100
agents.scheduler.loadShedding.readyDepthLevel1=256
agents.scheduler.loadShedding.ingressLevel2Percent=75
agents.scheduler.loadShedding.cpuLevel3Percent=85
agents.scheduler.loadShedding.heapLevel3Percent=85
agents.scheduler.loadShedding.gcPauseLevel3Ms=250
agents.scheduler.loadShedding.backgroundCadenceMultiplier=2
agents.scheduler.loadShedding.maxActiveAgents=2000
agents.scheduler.quiescenceTimeoutMs=5000
```

`shardCount` is read at scheduler initialization and must remain between 1 and
64. Changing it requires a server restart; live session migration between shard
sets is intentionally unsupported.

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

## Simulation-aware cadence

Simulation-aware cadence is implemented only for a central scheduler mode and
is disabled by default. `PRESENTATION` retains the registration's exact period,
work class, priority, and guarded authoritative tick. With
`agents.scheduler.simulation.enabled=true`, an Agent on a map with no real
client observer becomes `BACKGROUND_ACTIVE`; it continues to run the same tick
with a cadence no faster than `backgroundActiveTickMs` and background priority.

The first real observer entering a map and the last real observer leaving it
publish one transition event. The event performs O(1) active-session lookups
for the Agents already in that map and only wakes their scheduler handles.
Mode mutation remains owned by the destination scheduler shard. There is no
per-Agent channel or world scan.

`BACKGROUND_ABSTRACT` has policy, reconciliation, and materialization extension
points, but the production execution policy denies it even when its separate
flag is true. Phase 7 does not implement virtual combat, loot, travel, economy,
NPC, quest, packet, or other gameplay shortcuts. All modes still execute normal
authoritative gameplay. Returning from a background mode validates the live
character, map, and position; an eventual abstract implementation must also
reconcile pending outcomes before presentation resumes.

Background work is also bounded per map and cycle so one invisible crowded map
cannot monopolize a shard. The default limit is 32 authoritative callbacks per
map per cycle; `0` disables this guard. Presentation work is never subject to
the map limit. Deferred records retain their ready age and continue through the
normal bounded continuation path.

## Tick slicing

Central scheduler modes can opt into bounded guarded-tick slicing with
`agents.scheduler.tickSlicing.enabled=true`. The default remains disabled.
The existing guarded tick is represented as four ordered slices: preflight,
lifecycle, plan/gates, and capability/movement. The same hook calls and early
returns run in their legacy order; mailbox input is drained once when the frame
starts, and movement settlement plus failure reset happen only after the frame
completes.

Each scheduler turn runs at most `maxSlicesPerTurn` slices. Incomplete frames
request one coalesced immediate continuation from the owning scheduler shard.
`maxContinuationsPerFrame` rejects a frame that cannot complete within its
configured bound through the existing per-Agent failure policy. Despawn,
replacement, and lifecycle cancellation clear an incomplete frame and its
captured references. Legacy per-Agent scheduling never enables slicing.

## Load shedding and admission

Load shedding is implemented behind
`agents.scheduler.loadShedding.enabled=false`. Each shard samples bounded
scheduler lag/backlog/ingress metrics plus process CPU, heap use, and GC time
at `sampleIntervalMs`; these probes do not run in every dispatch cycle. Three
pressured samples are required by default to escalate. Recovery requires 20
healthy samples and lowers only one level at a time to avoid a thundering herd.

The explicit levels are:

```text
NORMAL
SUPPRESS_COSMETIC
REDUCE_BACKGROUND_CADENCE
PAUSE_DEFERRED_AND_LLM
PAUSE_LOW_PRIORITY_BACKGROUND
ADMISSION_CONTROL
```

Undirected cosmetic dialogue is rejected first. At higher levels, background
cadence is multiplied, LLM/catalog/economy submissions are rejected from their
bounded lanes, idle low-priority background ticks remain ready for a later
periodic cycle, and new Agent sessions are rejected. Leader-directed replies,
navigation work, presentation work, lifecycle-critical work, and any session
with pending mailbox/completion work remain admitted. Intentionally shed ready
work does not request an immediate scheduler wake.

`maxActiveAgents` is checked atomically under registry mutation ownership;
replacement sessions are exempt so recovery cannot strand an existing Agent.
The strongest shard level controls process-wide cosmetic, async, and admission
decisions. Every transition, suppression, and admission rejection records a
reason code. The 2000 value is a safety ceiling, not evidence that the 2000-
Agent soak gate has passed.

## Strong quiescence

`AgentQuiescenceService` exposes a generation-bound mutation barrier for
future profile exchange, character transfer, consistent snapshot, release,
and maintenance operations. It works in legacy, central-sequential, and
central-sharded modes. The default request timeout is five seconds and may be
changed with `agents.scheduler.quiescenceTimeoutMs`.

A successful token is issued only after the current bounded tick frame has
finished, no session-scoped async request remains pending, and all critical
completion mailbox work has drained. Ordinary mailbox actions remain queued
and frozen in FIFO order. Async completion and lifecycle work use a separate
bounded critical reserve so a full ordinary queue cannot prevent cleanup.

Quiescent central registrations leave the due/ready queues. Resume requires
the exact token for the same session generation. Timeout restores ordinary
execution and fails the request; cancellation, replacement, and shutdown fail
an outstanding request instead of returning a false-safe token. The legacy
timer path uses the same guard and remains the rollback mode.

The removed Double Agent proof of concept is not reintroduced here. Any future
profile operation must call `AgentQuiescenceService.requireValidToken` before
mutation and must prove canonical restoration before save/release.

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
`AgentWorkClass`. Each rolling window retains at most 2048 samples.
Equivalent bounded duration windows are available by `AgentSimulationMode`.
Tick-slice windows expose bounded duration percentiles by slice kind, and the
snapshot includes the number of requested frame continuations.
Load-shedding metrics expose current state by shard, transitions, suppressed
work, rejected admissions, and reason-coded counts.
`shardSnapshots()` adds registrations and queue depths by shard, while the
aggregate depth gauges sum all reporting shards. Registration imbalance is the
largest shard population minus the smallest.

GM6 `@agentscheduler` formats these counters as a bounded, read-only operator
snapshot. It includes current mode and active population, up to eight shard
details, the highest load-shedding level, quiescence outcomes, and up to twelve
initialized Agent async queues. Pair it with `@serverhealth` during staged live
and soak validation.

Enable slow-tick logging during soak validation, then compare movement, combat,
loot, dialogue, and lifecycle
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
Phase 6 gateway affinity and stable-hash sharding: locally complete, explicit opt-in
Phase 7 simulation-aware cadence and transition hooks: locally complete, disabled by default
Phase 8 bounded guarded-tick slicing: locally complete, disabled by default
Phase 9 load shedding and admission control: locally complete, disabled by default
Phase 10 scheduler quiescence contract: locally complete
Phase 11 deterministic 2000-session scale/cleanup gate: locally complete
production default switch: blocked on parity and staged soak evidence
```

The central-sequential global scan/sort has been removed. Important current
limitations include disabled-by-default tick slicing and Cosmic thread
affinity that is classified but not yet live/soak validated. Scheduler-reachable navigation graph construction, Amherst
progress persistence, LLM/network work, and trade/item analysis now run on
separate bounded workload lanes. Their compact results return through the
generation/request-stamped owning mailbox, and a source boundary test rejects
direct future waits in Agent production code. Explicit navigation debug/probe
tools may still build a graph synchronously outside scheduler execution.
Cross-session formation, leader-away, and away/logout operations now dispatch
mutations through each destination mailbox. The repository has an implemented
multi-shard foundation, not a production-validated centralized scheduler.

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
Phase 6 gateway-affinity, stable-hash ownership, race, failure-isolation, and
per-shard metric evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-6`.
Phase 7 map-presence, simulation-policy, cadence, materialization-boundary, and
mode-metric evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-7`.
Phase 8 tick-frame ordering, continuation bounds, lifecycle cleanup, and
slice-metric evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-8`.
Phase 9 overload levels, hysteresis, protected-work, async suppression, and
atomic admission evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-9`.
Phase 10 generation-bound quiescence, frozen ordinary ingress, critical
completion drain, timeout, cancellation, legacy compatibility, and metric
evidence is recorded under
`docs/agents/evidence/central-scheduler/phase-10`.
Phase 11 deterministic central-sequential and four-shard scale/cleanup evidence,
plus the live rollout runbook, is recorded under
`docs/agents/evidence/central-scheduler/phase-11`.

## Rollback

Set `agents.scheduler.mode=legacy` and restart. Removing the explicit mode and
setting `agents.scheduler.central.enabled=false` is the compatibility rollback.
Registration returns to the unchanged per-Agent `TimerManager.register` path.
