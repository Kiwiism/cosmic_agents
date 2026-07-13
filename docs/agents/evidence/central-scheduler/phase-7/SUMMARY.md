# Phase 7 Summary

Baseline commit: `797f9ab322`

Phase 7 adds simulation-aware scheduler policy behind
`agents.scheduler.simulation.enabled=false`:

- runtime entries own `PRESENTATION`, `BACKGROUND_ACTIVE`, or
  `BACKGROUND_ABSTRACT` state;
- first/last real-player map-observation transitions wake affected Agent
  scheduler handles without waiting or mutating Agent state;
- presentation retains the original period, work class, priority, and guarded
  authoritative tick;
- unobserved Agents may run the same tick at a configurable reduced cadence;
- background work has a configurable per-map cycle budget while presentation
  work is never map-capped;
- abstract execution requires a separate policy and remains denied in the
  production wiring;
- reconciliation and materialization hooks guard a future transition from
  abstract work back to presentation;
- bounded work-duration metrics are available by simulation mode.

Legacy scheduling and both simulation flags remain the defaults. No gameplay
outcome, database schema, WZ data, or visible behavior is intentionally changed.
