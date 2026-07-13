# Phase 3 Remaining Risks

- One slow guarded Agent tick still delays later work in the one-shard cycle.
- Selection has only a work-count cap; Phase 4 must add time/cost budgets,
  priorities, reserves, aging, and rolling metrics.
- `AgentNavigationGraphService.getGraph(...).join()` remains a Phase 5 blocking
  completion migration item and must not become reachable from scheduler work.
- Cross-session formation and leader-away mutations still require the Phase 6
  Cosmic gateway/thread-affinity audit before multi-shard execution.
- `CENTRAL_SHARDED` remains rejected and must not be made the default.
- The 500-session result is dispatcher-only. Live movement, combat, loot,
  dialogue, lifecycle, shutdown, and sustained 500/1000/1500/2000-Agent tests
  remain future acceptance gates.

Rollback remains `-Dagents.scheduler.mode=legacy` followed by restart.
