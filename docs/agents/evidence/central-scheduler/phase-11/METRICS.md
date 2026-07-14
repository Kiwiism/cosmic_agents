# Phase 11 Metrics

Deterministic local gates:

| Gate | Population | Cadences | Expected updates | Result |
|---|---:|---:|---:|---|
| central-sequential | up to 2,000 | 20 | population x 20 | pass |
| four concurrent shards | 2,000 | 20 | 40,000 | pass |

The four-shard gate also requires:

- registration imbalance below 100 sessions;
- zero overlapping executions for every session;
- zero registrations after cancellation;
- zero owned registrations after cancellation;
- zero due, ready, and ingress entries after cancellation.

Runtime operator evidence is available through the bounded GM6
`@agentscheduler` command. The command reads the same rolling metrics used by
the deterministic gates and includes initialized async-lane depth, capacity,
high-water, rejection, timeout, stale, expired, and drained counts. It also
reports registration state and ready priority, cycle-budget utilization, and
lifecycle register/replace/cancel/cleanup counts. `@agentscheduler costs`
reports bounded p50/p95/p99 work-class, simulation-mode, and tick-slice cost.
Use `@serverhealth` for the complementary JVM, database, and core server
snapshot.

Detail diagnostics expose bounded current registration cost/overdue state,
active map population, mailbox depth, current failure-window count, and the
existing instrumented capability totals. They retain no new per-tick history
and cap every operator ranking at ten rows.

The shutdown report records sessions observed, schedule cancellation requests,
session IDs that failed cancellation, pending async requests invalidated,
remaining scheduler registrations, async executors stopped, queued tasks
cancelled, unterminated lanes, interruption/timeout state, elapsed time, and the
final scheduler snapshot.

Heap plateau, GC pause, database-pool wait, real-player latency, scheduler p95/
p99 stabilization, and shutdown elapsed time require a running server and are
not inferred from this unit-scale gate.
