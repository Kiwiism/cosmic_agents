# Phase 5 Remaining Risks

- A single guarded Agent callback remains monolithic and can exceed a cycle
  deadline before returning; Phase 8 owns tick slicing.
- Multi-shard execution remains blocked by the Phase 6 Cosmic mutation and
  thread-affinity audit.
- Catalog loading has a reserved bounded workload kind but current loads are
  startup or explicit command operations, not scheduler callbacks.
- Explicit navigation debug/probe tools may synchronously build a graph; the
  scheduler boundary test prevents this API from spreading to runtime paths.
- Process-wide async lanes other than navigation need deterministic server
  shutdown wiring in a later lifecycle phase.
- Timeout classification does not interrupt arbitrary Java work. LLM HTTP has
  its own hard request timeout; other tasks must remain bounded by their
  implementation.
- Live movement/combat/dialogue parity and sustained 500/1000/1500/2000-Agent
  soak evidence are not available locally and remain rollout gates.

Rollback remains `-Dagents.scheduler.mode=legacy` followed by restart.
