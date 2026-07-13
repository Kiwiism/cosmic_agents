# Phase 4 Summary

Baseline commit: `8c7397ebd3`

Phase 4 adds bounded priority and cost policy to the one-shard scheduler:

- `AgentWorkClass` identifies lifecycle, player-directed, presentation,
  background, plan, completion, social, cosmetic, and maintenance work.
- `AgentPriorityClass` orders critical through deferred work.
- `AgentCycleBudget` enforces a cycle deadline and hard work-count guard, with
  critical and visible reserved passes.
- each registration retains a cost EWMA used to avoid starting learned
  expensive background work when it cannot fit the remaining cycle.
- waiting ready work promotes repeatedly, one level per configured interval,
  up to interactive priority.
- excess work remains in bounded shard-ready queues and schedules one
  coalesced immediate continuation.
- `AgentSchedulerMetrics` exposes bounded rolling p50/p95/p99 delay and work
  duration globally and by work class, plus pressure/depth counters.

Current production registration is deliberately classified as
`PRESENTATION_GAMEPLAY` / `VISIBLE`, preserving the existing full guarded tick.
`LEGACY_PER_AGENT` remains the default. No gameplay outcomes, schema, WZ data,
or Cosmic authority path were intentionally changed.
