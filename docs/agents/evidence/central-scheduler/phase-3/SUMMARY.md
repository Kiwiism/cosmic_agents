# Phase 3 Summary

Baseline commit: `9d3bd20354`

Phase 3 replaces the central-sequential scheduler's global registration scan
and due-list sort with one scheduler-owned shard:

- `AgentSchedulerShard` owns a bounded `ArrayBlockingQueue` ingress and an
  indexed minimum due heap.
- `AgentIndexedMinHeap` supports O(log n) insert/update/removal and stable
  `(nextDueMs, registrationSequence)` selection.
- producers submit one coalesced synchronization marker per registration;
  they do not mutate the heap or periodic due time.
- the ingress capacity also bounds scheduler-owned live/closing records. The
  default is 4096, leaving headroom over the 2000-Agent target while ensuring
  admitted cancellation cannot be displaced by ordinary registration.
- cancellation completes its public handle immediately, then shard ingress
  removes retained heap and ownership state before the central loop stops.
- missed periods still coalesce to one current update and one future due time.
- the existing guarded full Agent tick remains the parity work item.

`LEGACY_PER_AGENT` remains the default and unchanged rollback path.
`CENTRAL_SHARDED` remains unavailable. No gameplay behavior, cadence, database
schema, WZ data, or Cosmic authority path was intentionally changed.
