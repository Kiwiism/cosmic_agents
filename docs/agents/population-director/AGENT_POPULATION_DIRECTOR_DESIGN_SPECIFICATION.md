# Agent Population Director Design Specification

Purpose:

```text
Coordinate the long-term shape of the Agent population so the world feels
varied, believable, scalable, and economically alive instead of filling with
identical Agents following the same optimal route.
```

This is post-reconstruction implementation work. Before reconstruction is
complete, this document is only a contract for the future package.

## Design Position

The Population Director is not an Agent capability and does not execute game
actions. It is a world-level planning service that decides what kinds of Agents
should exist, where they should generally be, and what broad roles they should
fill.

Recommended package:

```text
agent-population-director
```

It sits above individual Agent profiles:

```text
Population Director decides the world mix.
Profile Platform decides the individual personality and constraints.
Plan Runtime decides the next plan.
Capabilities execute validated actions.
```

## Goals

- Maintain a varied population across jobs, levels, archetypes, regions, and
  economic roles.
- Prevent every Agent from choosing the same efficient route, map, build, or
  item strategy.
- Support special cohorts such as islanders, town idlers, farmers, merchants,
  Free Market scouts, helpers, questers, grinders, and suppliers.
- Keep Agent distribution compatible with map capacity, spawn density, and
  player-facing crowding.
- Provide seeded, reproducible population presets for soak tests.
- Feed the Economy Engine with class/job population demand signals.
- Feed Profile Platform with assignment intent without overwriting hard profile
  constraints.
- Allow future LLM or operator input to suggest population goals without
  bypassing validators.
- Support 2000 concurrent Agents by spreading load across maps, roles, and
  simulation tiers.

## Non-Goals

- Do not run navigation, combat, looting, NPC, quest, shop, trade, or LLM
  actions.
- Do not mutate live Agent runtime behavior directly.
- Do not replace the Profile Platform.
- Do not override hard profile constraints such as islander boundaries.
- Do not decide exact combat targets or objective steps.
- Do not require an LLM.
- Do not depend on Cosmic server classes directly.

## Core Concepts

### World Population Plan

A named desired population shape for a world or test scenario.

Example:

```text
victoria_lt30_living_world_v1
```

It defines:

- total Agent target.
- region ratios.
- level brackets.
- job distribution.
- archetype distribution.
- economic role distribution.
- special cohorts.
- map capacity rules.
- rebalance rules.

### Cohort

A group of Agents with a shared world-level identity.

Examples:

- `maple-island-beginners`
- `maple-island-islanders`
- `fresh-victoria-warriors`
- `ellinia-social-idlers`
- `cursed-doll-suppliers`
- `red-whip-farmers`
- `free-market-price-scouts`

Cohorts should point to profile templates or profile constraints, not duplicate
profile definitions.

### Role

A broad economic or social function.

Examples:

- quester.
- grinder.
- supplier.
- item farmer.
- market scout.
- merchant.
- social idler.
- helper.
- collector.

Roles are softer than archetypes. A single archetype may carry multiple role
weights.

### Archetype

A stable behavior identity owned by Profile Platform.

The Population Director may request an archetype distribution, but Profile
Platform owns the archetype schema, hard constraints, and adaptation rules.

### Map Capacity

The expected healthy Agent presence for a map.

Capacity may include:

- soft Agent count.
- hard Agent count.
- role-specific limits.
- visible full-simulation limit.
- background-simulation limit.
- spawn density.
- expected mob mix.
- town/social capacity.
- player crowding tolerance.

### Spawn Wave

A batch of new Agent assignments created from the current population gap.

Spawn waves should be deterministic when given the same seed, plan, and
snapshot so soak tests can be reproduced.

### Rebalance Proposal

A suggested change to bring the live population closer to the desired plan.

Examples:

- create 30 more early first-job Magician Agents.
- assign 12 islanders to Maple Island farming loops.
- move some idle Agents from Henesys to Ellinia.
- reduce new Assassin supply plans because throwing-star demand is saturated.

Rebalance proposals must avoid thrashing. The system should prefer gradual
changes over constantly moving Agents.

### Demand Signal

A structured output that tells the Economy Engine what population pressure may
change item demand.

Examples:

- Assassin population is growing, increasing demand for claws and throwing
  stars.
- Warrior population is under target, reducing demand for low-level warrior
  equipment.
- More Agents are approaching level 30, increasing demand for second-job
  preparation items.

## Operating Modes

### Off

The Population Director does nothing.

Use for:

- local debugging.
- minimal server mode.
- verifying behavior without population influence.

### Observe Only

The director reads snapshots and emits metrics/reports only.

Use for:

- validating catalog/profile inputs.
- comparing target population against current reality.
- proving it does not mutate state.

### Plan Only

The director produces assignments and rebalance proposals, but another service
or operator must approve them.

Use for:

- early integration.
- dashboard review.
- soak-test dry runs.

### Assignment Allowed

The director may submit validated assignment requests to the Plan/Profile
runtime, still through normal boundaries.

Use for:

- production Agent population shaping after reconstruction is stable.

### Test Fast

The director may use shorter cooldowns and faster rebalance intervals.

Use for:

- soak tests.
- local simulation.
- synthetic population experiments.

This mode must be clearly marked and should not be the default.

## Package Relationships

### Catalog Platform

Provides:

- regions.
- maps.
- map capacity metadata.
- mob spawn density.
- NPC/town/service availability.
- item source indexes.
- quest availability.
- travel graph summaries.

### Profile Platform

Provides:

- profile templates.
- archetype definitions.
- hard constraints.
- role preferences.
- adaptation state.
- decision journal APIs.

The director proposes assignments; Profile Platform validates whether an Agent
can accept them.

### Plan Runtime

Provides:

- plan availability.
- plan tags.
- plan entry constraints.
- current plan state.
- plan assignment API.

The director should assign broad plan sets or starting intentions, not internal
objective steps.

### Economy Engine

Consumes:

- job distribution.
- class growth.
- level bracket growth.
- role supply.
- farming/supplier population.
- market scout population.

Provides:

- market saturation.
- item scarcity.
- demand signals.
- role demand hints.

### Simulation Tier Runtime

Consumes:

- map population targets.
- crowding hints.
- visible/background/abstract target ratios.

Provides:

- current simulation tier counts.
- capacity pressure.
- materialization pressure.

### Soak Test Harness

Uses:

- seeded population plans.
- deterministic cohort assignment.
- target-vs-actual reports.
- map distribution reports.

### LLM Gateway

May propose:

- population themes.
- event-specific cohorts.
- temporary social behavior experiments.

The LLM cannot bypass hard constraints, map caps, validation, or safety gates.

## Population Diversity Model

Each world plan should define multiple dimensions instead of only total Agent
count.

Recommended dimensions:

- region.
- level bracket.
- job path.
- archetype.
- plan set.
- economy role.
- social behavior.
- progression speed.
- simulation tier preference.

Example:

```text
2000 Agents:
  10% Maple Island beginners
  15% Maple Island islanders
  20% fresh Victoria level 8-15
  35% early first-job level 15-22
  20% late first-job level 22-30
```

Within those brackets, the director should distribute jobs, roles, and maps so
Agents do not all converge on the same maps.

## Constraints

Hard constraints:

- never assign islanders outside Maple Island.
- never exceed map hard caps.
- never assign a plan forbidden by profile policy.
- never assign a job path incompatible with the Agent profile.
- never assign an Agent to a map unreachable under current catalog rules.
- never assign live behavior directly; only submit validated assignments.

Soft constraints:

- prefer underfilled cohorts.
- prefer underused maps.
- prefer economically useful roles.
- prefer profile-compatible roles.
- prefer stable assignment over frequent reassignment.
- prefer spreading Agents over crowded hotspots.

## Rebalance Rules

Rebalance should be slow and explainable.

Recommended rules:

- apply cooldowns per Agent before changing broad role or region.
- prefer assigning new Agents over disrupting existing ones.
- only move existing Agents when overcrowding, stuck states, or economy
  imbalance persists.
- never break active focus-mode plans unless the plan permits interruption.
- record why the rebalance happened.

## Decision Records

Important director outputs should be journaled.

Examples:

- population plan selected.
- spawn wave created.
- cohort underfilled.
- map cap prevented assignment.
- economy demand changed role weights.
- LLM suggestion accepted/rejected.
- profile hard constraint blocked proposal.

Each record should include:

- timestamp.
- world/channel.
- input snapshot hash.
- selected population plan id.
- seed.
- assignments proposed.
- assignments accepted.
- assignments rejected.
- reason codes.

## Success Criteria

The Population Director is ready when:

- it can load a population plan without server-specific classes.
- it can compare target population against current snapshots.
- it can produce deterministic assignments from a seed.
- it never violates profile hard constraints.
- it respects map capacity metadata.
- it can emit economy demand signals.
- it can run in observe-only mode with no mutations.
- it can support soak-test population presets.
- it records clear assignment and rebalance reasons.

## Deferred Until After Reconstruction

- live Agent assignment execution.
- integration with reconstructed Agent runtime entries.
- live simulation-tier balancing.
- live economy feedback loops.
- LLM-generated population experiments.
- dashboard controls in Agent Console.
