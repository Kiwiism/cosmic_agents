# Phase 10 Metrics

`AgentSchedulerMetrics.quiescenceSnapshot()` exposes bounded counters and
latency percentiles for:

- accepted requests;
- completed barriers;
- timed-out barriers;
- lifecycle-cancelled barriers;
- valid resumes;
- p50/p95/p99 completion latency with a 2048-sample bound.

Mailbox and async queue depth/rejection metrics continue to cover frozen
ordinary work and critical completion delivery. Production quiescence latency
numbers require staged server and profile-operation tests.
