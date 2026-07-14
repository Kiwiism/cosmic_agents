# Phase 11 Parity

- The production default remains `LEGACY_PER_AGENT`.
- No gameplay, cadence, packet, Cosmic schema, WZ, inventory, quest, combat,
  movement, dialogue, or lifecycle implementation changed in the local gate.
- Central-sequential and central-sharded callbacks remain the same guarded tick
  callback used by legacy scheduling.
- The deterministic gate proves exact callback counts and no concurrent
  execution of one session.
- An authenticated one-client smoke additionally proves normal spawn/party
  visibility, same-map follow/navigation, a Pig kill, meso pickup feedback,
  and clean shutdown with one central-sharded Agent. This is partial parity,
  not a substitute for the remaining capability and two-client checks.
- A server-only 250-character roster reached the same live-session count under
  legacy, central-sequential, and four-shard scheduling. Every mode cancelled
  all 250 sessions and left no pending/remaining scheduler work. This supports
  lifecycle parity but does not prove packet or visual parity.
- Rollback remains a restart with `agents.scheduler.mode=legacy`.
- Normal runtime behavior is unchanged. The shutdown path executes only during
  process shutdown/restart, before Cosmic tears down channels and timers.
- The added scheduler/lifecycle/mailbox counters and read-only cost/state views
  observe existing decisions; they do not change cadence, ordering, admission,
  failure escalation, or gameplay execution.
- No intentional gameplay behavior change was made by the live validation.
