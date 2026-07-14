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

Heap plateau, GC pause, database-pool wait, real-player latency, scheduler p95/
p99 stabilization, and shutdown elapsed time require a running server and are
not inferred from this unit-scale gate.
