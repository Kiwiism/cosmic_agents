# Phase 8 Metrics

`AgentSchedulerMetrics` now provides:

- bounded p50/p95/p99 execution duration by `AgentTickSliceKind`;
- sample count by slice kind;
- total requested tick-frame continuations.

Each slice window uses the existing fixed 2048-sample rolling capacity. Phase
8 does not claim production p99 targets because no live or staged population
run was performed locally.
