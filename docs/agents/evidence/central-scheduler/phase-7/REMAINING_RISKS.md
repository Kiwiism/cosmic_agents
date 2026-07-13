# Phase 7 Remaining Risks

- Simulation-aware cadence is not production-enabled or live-client validated.
- Reducing the authoritative tick cadence changes invisible-world progress and
  needs capability-specific parity review before rollout.
- `BACKGROUND_ABSTRACT` has no production execution policy and performs no
  virtual gameplay; enabling its flag alone cannot select it.
- Materialization currently validates a live character, map, and position. A
  future abstract outcome implementation must add foothold selection, journal
  reconciliation, and capability-specific state validation.
- No packet suppression, ETA navigation, shared perception, or compressed
  combat/loot/economy behavior is part of Phase 7.
- Central-sharded mode still requires live Cosmic thread-affinity validation,
  shutdown/replacement rehearsal, and staged mixed-mode soaks.

Rollback remains `agents.scheduler.mode=legacy`; simulation can independently
be disabled with `agents.scheduler.simulation.enabled=false`.
