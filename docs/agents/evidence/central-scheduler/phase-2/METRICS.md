# Phase 2 Metrics

Phase 2 changes ownership and backpressure contracts; it does not claim a new
gameplay or production-load benchmark.

Bounded values exercised by deterministic tests:

- default mailbox capacity: 128 actions;
- default drain budget: 32 actions per guarded tick;
- full mailbox: new FIFO submission is rejected with `FULL`;
- coalescing: only an explicitly matching key replaces its older queued value;
- expiry: expired work completes with `EXPIRED` and does not execute;
- wake burst: multiple accepted wake requests produce one pending immediate
  dispatcher task;
- failure isolation: one action failure completes only that action exceptionally
  and later actions continue draining.

Existing scheduler cycle, updated, skipped, lag, slow, and failure counters are
unchanged. Rolling mailbox and scheduler percentiles are Phase 4 work. No heap,
GC, DB, packet-latency, or live-population metric is inferred from these unit
tests.
