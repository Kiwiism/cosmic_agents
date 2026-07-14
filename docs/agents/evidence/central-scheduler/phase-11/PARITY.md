# Phase 11 Parity

- The production default remains `LEGACY_PER_AGENT`.
- No gameplay, cadence, packet, Cosmic schema, WZ, inventory, quest, combat,
  movement, dialogue, or lifecycle implementation changed in the local gate.
- Central-sequential and central-sharded callbacks remain the same guarded tick
  callback used by legacy scheduling.
- The deterministic gate proves exact callback counts and no concurrent
  execution of one session; it does not prove what a MapleStory client sees.
- Rollback remains a restart with `agents.scheduler.mode=legacy`.
- Normal runtime behavior is unchanged. The new path executes only during
  process shutdown/restart, before Cosmic tears down channels and timers.
