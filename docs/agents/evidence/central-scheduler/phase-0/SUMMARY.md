# Central Scheduler Phase 0 Summary

Baseline commit: `26264f4cc2`

Phase 0 freezes and measures the existing scheduler behavior without changing
production code. `LEGACY_PER_AGENT` remains the default. The optional central
path remains the existing sequential scan-and-sort dispatcher.

Completed evidence:

- deterministic legacy and central-sequential callback harnesses at 50, 100,
  250, and 500 sessions.
- 20 cadences per population, producing 1,000, 2,000, 5,000, and 10,000
  callbacks per mode.
- focused scheduler, mailbox, capability, and Cosmic gateway baseline.
- required source scans and initial ownership classifications.

This is dispatcher-only evidence. It does not prove live movement, combat,
loot, dialogue, packet, database, or 500-Agent gameplay performance.

Production behavior changes: none.

Rollback: no runtime rollback is needed because Phase 0 changes tests and
documentation only.
