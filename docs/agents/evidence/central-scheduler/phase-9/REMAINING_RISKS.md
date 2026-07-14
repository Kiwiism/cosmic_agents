# Phase 9 Remaining Risks

- Load shedding is disabled by default and has not completed live-player
  latency validation.
- CPU, heap, and GC signals are process-local; a future server health adapter
  may add login/channel/database latency without changing the policy contract.
- The 2000-Agent population ceiling is an admission guard, not scale proof.
- Staged overload must verify that recovery thresholds avoid oscillation and
  that retained background work does not grow beyond scheduler bounds.
- Multi-shard affinity, tick slicing, simulation cadence, and load shedding
  still need combined 500/1000/1500/2000-Agent tests.
- Background abstract gameplay remains denied.

Rollback is `agents.scheduler.loadShedding.enabled=false`, or the full
`agents.scheduler.mode=legacy` scheduler rollback.
