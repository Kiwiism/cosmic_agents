# Phase 7 Metrics

Implemented bounded observations:

- work-duration p50/p95/p99 and sample count by `AgentSimulationMode`;
- background per-map budget deferral count;
- existing queue-lag, work-class, cycle-budget, shard-depth, and failure metrics
  remain unchanged;
- each simulation-mode rolling window retains at most 2048 samples.

Deterministic cadence evidence:

- presentation: two authoritative callbacks at 50 ms spacing;
- background-active: one callback before 250 ms and a second at 250 ms;
- observer transition: one wake path restores presentation immediately.
- map fairness: two background maps each advance one Agent under a one-item
  per-map cycle limit, then both deferred Agents advance in the next cycle.

These are scheduler mechanics, not production throughput measurements. Mixed
500/1000/1500/2000-Agent load measurements remain staged soak gates.
