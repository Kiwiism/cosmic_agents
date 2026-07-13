# Agent Async Queue Limits

Agent-owned asynchronous queues have explicit capacity and saturation semantics.
Normal-load FIFO behavior is unchanged.

| Queue | Default capacity | Property | Saturation behavior |
| --- | ---: | --- | --- |
| Dialogue replies | 32 | `agents.async.dialogue.queueCapacity` | Coalesce an exact duplicate at capacity; leader-directed replies replace the oldest cosmetic message; otherwise reject new cosmetic dialogue. |
| LLM work | 64 | `agents.async.llm.queueCapacity` | Reject new cosmetic inference or compaction work and release its in-flight permit. |
| Navigation warmup | 64 | `agents.async.navigation.queueCapacity` | Reject the warmup, remove its pending future, and allow a later request to retry. |
| Fast navigation warmup | 64 | `agents.async.navigation.fastQueueCapacity` | Same as navigation warmup. |
| Amherst progress persistence | 256 | `agents.async.persistence.queueCapacity` | Reject a new load/save request and surface a plan persistence error; never block the scheduler worker. |
| Trade/item query | 128 | `agents.async.trade.queueCapacity` | Reject the stale background query; a later command can retry. |
| Catalog rebuild | 32 | `agents.async.catalog.queueCapacity` | Reserved bounded lane; current catalog loading remains startup/explicit work until migrated. |
| Script actions | 256 | `agents.async.scriptTasks.queueCapacity` | Throw `RejectedExecutionException`; authoritative movement, grind, follow, drop, and stop work is never silently discarded. |
| Per-Agent mailbox | 128 | `agents.mailbox.capacity` | Return an explicitly failed future. |

`AgentAsyncQueueMetrics` exposes submitted, rejected, coalesced, current-depth,
maximum-depth, capacity, active-worker, completed, failed, timed-out, stale,
and bounded rolling p50/p95/p99 duration values. The script-task queue is
synchronized because it is currently written by compatibility command paths
and consumed by the Agent tick. Future mailbox migration can remove that
cross-thread compatibility concern.
