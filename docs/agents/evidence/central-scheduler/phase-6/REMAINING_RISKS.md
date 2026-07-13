# Phase 6 Remaining Risks

- Agent capabilities still directly import many Cosmic runtime classes; the
  gateway audit is not full SPI decoupling.
- Deterministic tests do not prove live map/combat/loot/trade lock ordering.
- `CENTRAL_SHARDED` has not passed live-client parity or a staged population
  soak.
- The full guarded Agent callback is still monolithic and can overrun one shard
  turn; Phase 8 owns bounded tick slicing.
- Load shedding, quiescence, shutdown draining, and materialization gates remain
  future phases.
- Changing shard count requires a restart; live shard migration is not allowed.

Rollback is `agents.scheduler.mode=legacy` followed by a restart.

