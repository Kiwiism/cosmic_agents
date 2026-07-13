# Phase 4 Remaining Risks

- A single monolithic callback can exceed the cycle deadline before returning;
  Phase 8 tick slicing is required for a hard per-Agent slice bound.
- Work classes narrower than the current visible full tick are not yet wired
  into capability slices.
- Navigation graph loading still exposes one blocking `join()`; Phase 5 owns
  the bounded async completion migration.
- Persistence, LLM/network, and other external workloads do not yet share one
  request/generation-stamped completion contract.
- Multi-shard mode remains blocked by the Phase 6 Cosmic mutation/thread-
  affinity audit.
- Map/simulation-aware priorities, load shedding, quiescence, live parity, and
  staged long-duration scale tests remain future phases.

Rollback remains `-Dagents.scheduler.mode=legacy` followed by restart.
