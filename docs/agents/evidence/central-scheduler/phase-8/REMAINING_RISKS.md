# Phase 8 Remaining Risks

- Tick slicing is disabled by default and has not completed live-client parity.
- The four slices establish bounded checkpoints, but an individual capability
  hook can still exceed a cycle target; Phase 5 isolation covers known
  blocking work and future profiling must identify any remaining long hook.
- Immediate continuation traffic needs staged 500/1000/1500/2000-Agent load
  evidence together with real-player responsiveness measurements.
- Multi-shard Cosmic thread-affinity and shutdown behavior still require live
  and soak validation.
- Background abstract gameplay remains denied.

Rollback is `agents.scheduler.tickSlicing.enabled=false`, or the full
`agents.scheduler.mode=legacy` scheduler rollback.
