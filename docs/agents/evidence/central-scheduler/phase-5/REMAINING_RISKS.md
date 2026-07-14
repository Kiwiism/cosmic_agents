# Phase 5 Remaining Risks

- Phase 8 tick slicing is implemented but disabled by default until its
  live-client parity and measured p99 rollout gates pass.
- Multi-shard execution is implemented behind explicit opt-in; Cosmic gateway
  affinity is classified but still requires live packet-visible validation.
- Catalog loading has a reserved bounded workload kind but current loads are
  startup or explicit command operations, not scheduler callbacks.
- Explicit navigation debug/probe tools may synchronously build a graph; the
  scheduler boundary test prevents this API from spreading to runtime paths.
- Timeout classification does not interrupt arbitrary Java work. LLM HTTP has
  its own hard request timeout; other tasks must remain bounded by their
  implementation.
- Observer-based movement/combat/dialogue parity and sustained
  1000/1500/2000-Agent soak evidence remain rollout gates. Server-only 250- and
  500-Agent runs are recorded under Phase 11 and do not replace those gates.

Rollback remains `-Dagents.scheduler.mode=legacy` followed by restart.
