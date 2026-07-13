# Phase 7 Parity

Default parity is structural and deterministic:

- `agents.scheduler.simulation.enabled` defaults to `false`;
- `agents.scheduler.simulation.backgroundAbstract.enabled` defaults to `false`;
- `PRESENTATION` preserves each registration's original cadence, work class,
  priority, and exact guarded tick callback;
- the deterministic scheduler test observes two updates across two 50 ms
  presentation cadences;
- `BACKGROUND_ACTIVE` invokes that same callback once per configured 250 ms
  cadence in the focused test;
- an observation wake immediately re-evaluates policy and restores the 50 ms
  presentation cadence;
- observer counting excludes headless `BotClient` Agents and publishes only
  zero-to-one and one-to-zero real-player transitions.
- per-map cycle limits apply only to background modes; presentation work is
  exempt.

Live-client movement, combat, loot, dialogue, map transition, and visual parity
must still be validated before enabling simulation-aware cadence in production.
