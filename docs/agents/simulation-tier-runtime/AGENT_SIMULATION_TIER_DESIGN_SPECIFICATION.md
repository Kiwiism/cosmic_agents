# Agent Simulation Tier Runtime Design Specification

Purpose:

```text
Define the future runtime package that chooses Agent simulation fidelity based
on real-player map presence, map sensitivity, server load, and action needs.
```

This is a post-reconstruction scaling package. It must not be wired into live
Agent behavior until reconstructed Agent boundaries are stable.

## Design Rule

```text
Visible Agents need presentation fidelity.
Invisible Agents need validated world-state fidelity.
```

The Simulation Tier Runtime decides how expensive an Agent is allowed to be. It
does not decide what the Agent wants to do and does not bypass server
validation.

## Goals

- Support 2000 concurrent Agents by avoiding full simulation when not visible.
- Preserve normal visible behavior when real players share a map.
- Define safe background modes for navigation, combat, loot, NPC, shop, quest,
  and recovery.
- Keep "Agent cheats" isolated to background paths.
- Materialize Agents safely when real players can observe them.
- Expose mode decisions and savings through observability.

## Non-Goals

- Do not use client viewport/resolution visibility.
- Do not suppress player-visible packets in presentation mode.
- Do not change player combat/loot/server behavior.
- Do not mutate server state without capability/server-adapter validation.
- Do not run LLM/economy/profile decisions inside tier selection.

## Simulation Modes

### PRESENTATION

Condition:

```text
at least one real player is in the same map as the Agent
```

Behavior:

- full movement/physics fidelity.
- normal visible packets.
- full visible combat and effects.
- normal NPC/shop presentation.
- normal loot presentation.
- normal interaction realism.

### BACKGROUND_ACTIVE

Condition:

```text
no real player in map, but map is sensitive or pinned
```

Examples:

- event maps.
- party quest maps.
- boss maps.
- Free Market/shop/merchant maps.
- maps with shared sensitive state.

Behavior:

- reduce movement/perception frequency.
- suppress cosmetics when unobserved.
- keep stronger map-state consistency.
- avoid aggressive combat/loot abstraction for sensitive mobs/drops/events.

### BACKGROUND_ABSTRACT

Condition:

```text
no real player in map, and map is safe to abstract
```

Behavior:

- route ETA instead of full movement physics.
- abstract combat slices.
- direct validated loot credit when safe.
- direct validated NPC/shop/quest actions.
- no visual packet generation.
- commit final state at arrival/objective completion/materialization.

### STRATEGIC_OFFLINE

Condition:

```text
Agent does not need to occupy full active map runtime
```

Behavior:

- coarse plan progress slices.
- expected EXP/meso/loot/potion/death-risk models.
- no continuous map character simulation.
- materialize only for observable/shared interactions.

This mode is later-stage and should not be used for Maple Island MVP first run.

## Mode Selection Policy

```text
if map has real players:
    PRESENTATION
else if map is sensitive/pinned:
    BACKGROUND_ACTIVE
else if agent can safely abstract current action:
    BACKGROUND_ABSTRACT
else:
    BACKGROUND_ACTIVE
```

Server load can bias toward lower-cost modes only when doing so preserves
player-visible correctness and plan safety.

## Sensitive Map Classifier

Sensitive reasons:

- real player present.
- event instance active.
- party quest state.
- boss/area boss active.
- Free Market/shop/merchant state.
- manual pinned map.
- high-value public drop/object state.
- script-sensitive map.
- map marked no-abstract by catalog override.

## Background Cheat Boundary

Allowed only outside PRESENTATION:

- no movement packet generation.
- no cosmetic broadcast.
- route ETA navigation.
- same-map ETA navigation.
- abstract combat rounds.
- direct loot-to-inventory when no player can observe map drops.
- virtual loot/meso buffers.
- batched inventory reconciliation.
- direct validated NPC/shop/quest action.
- deferred Agent save/checkpoint.

Still required:

- live validation before mutation.
- catalog/source evidence.
- audit events.
- materialization safety.
- fairness budgets.
- rare-drop explicit rolls where needed.

## Materialization

When a real player enters the Agent map, or the Agent enters a visible/shared
interaction:

```text
pause background action
resolve virtual progress
choose valid materialization point
validate map/foothold/state
commit visible state
switch to PRESENTATION
resume normal capability behavior
```

Materialization points:

- route travel: nearest valid foothold along route.
- NPC/shop: selected approach point.
- combat: safe attack position near target region.
- loot: near last virtual loot position or safe foothold.
- idle: current/nearest valid idle point.

## Correctness Guarantees

- No background shortcut may produce a result impossible for normal server
  rules.
- No background shortcut may bypass quest, item, meso, cooldown, range, or map
  validation.
- Player-visible state must be coherent after materialization.
- Background action should be replayable/explainable from events.
- Strict debug mode should compare background outcomes with presentation path
  for selected scenarios.

## Relationship To Packages

Scheduler:

- asks tier runtime for next allowed mode and cost budget.

Capability Runtime:

- routes command behavior according to current mode.

Perception Runtime:

- changes refresh cadence by mode.

Route ETA Runtime:

- supplies travel estimates for abstract movement.

Background Action Runtime:

- owns concrete abstract combat/loot/shop/NPC execution.

Observability:

- records mode transitions, savings, materialization, and violations.

Server Adapter:

- provides live map/player/sensitive-state snapshots.

## Success Criteria

The package is ready when:

- modes are explicit and testable.
- real-player same-map presence forces PRESENTATION.
- sensitive maps avoid unsafe abstraction.
- materialization rules are defined.
- background shortcuts are isolated from player-visible paths.
- every mode transition is observable.
- capability packages can ask what behavior is allowed in the current mode.
