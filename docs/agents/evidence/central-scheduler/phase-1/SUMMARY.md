# Phase 1 Summary

Baseline commit: `e4d51f237e`

Phase 1 establishes the stable scheduler ownership boundary without changing
Agent gameplay behavior:

- scheduler implementation types live under `server.agents.runtime.scheduler`;
- `AgentTickSchedulingService` remains the lifecycle-facing facade;
- `AgentScheduler` selects legacy or central-sequential execution;
- immutable `AgentSchedulerConfig` validates explicit scheduler settings;
- `AgentSessionId` and `AgentScheduleHandle` bind registration to character ID
  and session generation;
- `AgentRuntimeRegistry` maintains an O(1) active-session generation index;
- spawn, replacement, dismissal, cleanup, and leader transfer update the index
  through explicit registry operations.
- registry registration is idempotent and atomically removes the prior
  leader-view entry when an Agent character generation is replaced.
- tests no longer mutate the compatibility leader map directly; they use the
  same explicit registry boundary as production lifecycle code.

`LEGACY_PER_AGENT` remains the default. `CENTRAL_SHARDED` is parsed but rejected
until the shard scheduler exists. No gameplay cadence or capability behavior
was intentionally changed.

The full Maven suite was attempted. It exposed and helped repair stale test
fixtures that bypassed the new index, then left its Maven wrapper orphaned
after the Surefire child exited. Remaining reported failures are outside this
phase: dialogue assertions run while legacy dialogue is configured off,
Amherst quest policy expectations, missing generated catalog fixtures, and a
movement randomness assertion. Focused scheduler, lifecycle, registry,
capability, trade, combat, loot, and supply anchors pass.
