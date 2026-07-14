# Phase 5 Summary

Baseline commit: `26862a7fc7`

Phase 5 isolates scheduler-reachable blocking and external work:

- `AgentAsyncExecutorRegistry` owns lazy, workload-specific bounded lanes for
  navigation, persistence, LLM/network, catalog, and economy analysis.
- `AgentAsyncTaskGateway` stamps work with Agent identity, session generation,
  work kind, request key, and request ID.
- compact completions return through the bounded owning mailbox; stale sessions
  and superseded requests cannot apply results.
- saturation removes the rejected request's pending state and records queue
  pressure; one workload lane cannot consume another lane's capacity.
- navigation graph cache I/O/construction no longer exposes a scheduler-reachable
  future wait. Entry-aware warmups return a mailbox wake-up.
- LLM/network replies and trade/item analysis apply only from the owning
  mailbox.
- Amherst progress load/save is asynchronous in central modes and retains the
  legacy synchronous path in `LEGACY_PER_AGENT`.
- fixed 2048-sample windows expose async p50/p95/p99 duration plus outcome,
  stale, active-worker, capacity, and depth counters.
- process shutdown closes admission, drains or cancels pending requests, and
  stops every initialized Agent async lane within the configured deadline.

No gameplay result, cadence, schema, WZ data, or visible behavior is
intentionally changed. Legacy per-Agent scheduling remains the default.
