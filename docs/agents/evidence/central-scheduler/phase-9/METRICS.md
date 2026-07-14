# Phase 9 Metrics

`AgentSchedulerMetrics.loadSheddingSnapshot()` exposes:

- current load-shedding state by shard;
- transition count;
- suppressed-work count;
- rejected-admission count;
- suppression counts by reason;
- admission rejections by reason.

Reason codes include queue lag, ready backlog, ingress pressure, process CPU,
heap pressure, GC time, unhealthy player path, recovery hysteresis, and the
population limit. Phase 9 does not claim production responsiveness or recovery
numbers because staged server load was not run locally.
