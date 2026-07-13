# Phase 6 Metrics

`AgentSchedulerMetrics.shardSnapshots()` exposes, by shard ID:

- live registrations;
- bounded ingress depth;
- indexed due-heap depth; and
- ready depth.

`shardRegistrationImbalance()` reports maximum minus minimum registrations.
Legacy aggregate depth gauges now sum all known shard snapshots in sharded mode
instead of exposing only the last shard to report.

The stable hash distribution test maps 2000 synthetic session identities over
four shards and requires the maximum/minimum difference to remain below 100.
This is deterministic distribution evidence, not a gameplay throughput result.

