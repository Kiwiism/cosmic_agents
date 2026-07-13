# Phase 6 Summary

Baseline commit: `cd3201b954`

Phase 6 adds explicit multi-shard scheduler ownership behind
`agents.scheduler.mode=central-sharded`:

- a stable avalanche hash maps each typed Agent session to exactly one fixed
  `AgentTickScheduler` shard;
- shard count is validated, fixed at startup, and defaults to half the available
  processors clamped to 1-4;
- each shard retains bounded ingress, indexed due-time ownership, ready queues,
  budgets, failure isolation, and generation validation from Phases 3-5;
- per-shard registration/queue depths and aggregate queue depths are exposed;
- every root integration gateway contract has an executable affinity
  classification and the sharded facade blocks unsafe classifications;
- formation, inactive-leader, and away/logout sibling mutations enter each
  destination Agent mailbox.

Legacy scheduling remains the default. No gameplay outcome, cadence, schema,
WZ data, or visible behavior is intentionally changed.

