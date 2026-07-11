# NuTNNuT LOD Comparison

## Decision

NuTNNuT's unobserved-map LOD implementation is not part of the navigation and
physics correctness port. Cosmic Agents keeps its existing Simulation Tier
Runtime design as the future scaling direction.

The current Agent runtime does not enable either system. Agents continue to run
the presentation path until the Simulation Tier Runtime is implemented and
validated.

## NuTNNuT LOD

NuTNNuT LOD is a binary runtime optimization:

- `LOD0` runs full movement, navigation, combat, travel, and 50 ms ticks.
- `LOD1` is entered after an unobserved map and its portal neighbors remain
  player-free for a hysteresis period.
- LOD1 can replace ground movement with a wall-clock motion plan, portal travel
  with a timed real portal transition, and grinding with calibrated kill
  deadlines that still damage live spawned monsters.
- A fully covered LOD1 Agent can be retasked from 50 ms to 500 ms.
- Real-player observation, a real player in the party, an active trade, or a
  manually pinned map forces LOD0.
- Promotion back to LOD0 materializes the interpolated position onto live
  physics before the entering player's map-object placement is sent.

This design is effective at reducing CPU use, but its policy, scheduling,
movement abstraction, travel abstraction, combat calibration, and
materialization are coordinated inside the legacy bot runtime.

## Cosmic Agents Simulation Tiers

The planned Agent design has four modes:

- `PRESENTATION`: full client-visible simulation.
- `BACKGROUND_ACTIVE`: reduced cadence with live authoritative state.
- `BACKGROUND_ABSTRACT`: validated abstract capability execution.
- `STRATEGIC_OFFLINE`: coarse plan progress without a continuously materialized
  map character; this is a later-stage mode.

The Simulation Tier Runtime chooses a mode and an explicit set of allowed
shortcuts. Movement, combat, loot, travel, NPC, quest, shop, and persistence
capabilities remain responsible for their own presentation and background
execution paths.

## Material Differences

| Concern | NuTNNuT LOD | Cosmic Agents tier design |
| --- | --- | --- |
| Modes | Binary LOD0/LOD1 | Four explicit simulation modes |
| Ownership | Legacy runtime manager coordinates all substitutions | Tier policy selects permissions; capabilities own execution |
| Scheduling | Per-bot task retasked between 50 ms and 500 ms | Central scheduler consumes a per-Agent cost budget and next-tick hint |
| Eligibility | Observation, neighboring observation, party, trade, and pins | Observation plus catalog-backed PQ, event, boss, FM, merchant, script, drop, and capability sensitivity |
| Shortcuts | Four global simplification flags | Typed per-capability `AllowedShortcutSet` |
| Movement | Ground-only linear motion plan | Route/same-map ETA shortcut selected by navigation capability |
| Combat | Calibrated deadlines kill live spawned mobs immediately | Abstract rounds must be explicitly allowed, validated, journaled, and reconciled |
| Loot/state | Real death path immediately creates normal outcomes | Virtual mutations may be buffered and committed once during materialization |
| Materialization | Position interpolation, grounding, reset, and broadcast | Position plus HP/MP, inventory, loot, quest, map, and pending-mutation reconciliation |
| Failure behavior | Falls back to full runtime for uncovered states | Fails closed to `BACKGROUND_ACTIVE` or paused recovery |
| Auditability | Counters and calibration endpoints | Decision, transition, shortcut, mutation, and materialization events |

## Reuse Guidance

NuTNNuT LOD remains useful as reference material for:

- O(1) real-player observation tracking.
- neighboring-map prewarming.
- downgrade hysteresis and immediate promotion.
- wall-clock movement timing.
- cadence-independent AI accumulators.
- calibrated presentation-versus-background parity measurements.

Those ideas should be adapted behind Agent simulation-tier interfaces rather
than porting `BotEntry.Lod`, LOD fields, or BotManager orchestration.

## Current Port Boundary

Navigation, navigation-graph, movement, physics, cache, and packet correctness
fixes may be ported independently because they improve the presentation runtime
that every future simulation tier must materialize into. LOD motion plans,
timed travel, abstract grind, and 500 ms retasking are explicitly excluded from
the current correctness port.
