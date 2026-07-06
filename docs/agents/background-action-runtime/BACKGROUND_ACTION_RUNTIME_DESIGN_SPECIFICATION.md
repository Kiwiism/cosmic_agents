# Background Action Runtime Design Specification

Purpose:

```text
Define the future package that executes unobserved Agent actions through
validated low-fidelity simulation instead of player-visible presentation paths.
```

This is a post-reconstruction scaling package. It must not be wired into live
Agent runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
Presentation mechanics are optional when no player can observe them.
Server validation is never optional.
```

The Background Action Runtime owns the cheap execution path for unobserved
Agents. It does not decide what Agents want to do. It only decides how an
already-approved capability command can be executed cheaply when the Simulation
Tier Runtime says background execution is allowed.

## Goals

- Make 2000 concurrent Agents plausible by removing invisible presentation
  work.
- Keep normal visible/player-observed behavior on the presentation path.
- Resolve background movement through route ETA and same-map ETA.
- Resolve background combat through bounded abstract rounds.
- Resolve background loot through direct validated credits and loot buffers.
- Resolve background NPC, quest, and shop actions without visual dialogue or
  packet work when safe.
- Reconcile virtual state into real server state at materialization,
  checkpoints, trades, shops, quest checks, shutdown, or inspection.
- Keep rare, economy-sensitive, or story-worthy outcomes explicitly rolled,
  journaled, and checkpointed.

## Non-Goals

- Do not replace normal player combat, movement, loot, NPC, or shop logic.
- Do not execute actions in maps where real players can observe the Agent.
- Do not bypass quest requirements, item checks, level checks, map checks,
  inventory capacity, cooldowns, or shop validation.
- Do not create an independent game server inside the Agent package.
- Do not make background Agents more efficient than plausible manual play.
- Do not store long-lived profile/economy decisions here.

## Two-Path Model

### Presentation Path

Used when a real player can observe the Agent map or interaction.

Behavior:

- normal movement and physics.
- normal packets and broadcasts.
- normal visible combat.
- normal visible loot/drop/pickup flow.
- normal NPC/shop presentation timing.
- full materialized inventory and character state.

### Background Path

Used only when the Simulation Tier Runtime allows it.

Behavior:

- no visual packets.
- route ETA or same-map ETA instead of full physics.
- abstract combat rounds instead of visible attacks.
- direct validated loot credit instead of map item creation.
- virtual loot/meso buffers.
- batched inventory reconciliation.
- direct validated NPC/shop/quest action.
- deferred low-value Agent persistence.
- journaled milestone and rare outcomes.

## Action Families

### Background Navigation

Use:

- portal-to-portal route ETA.
- same-map point-to-target ETA.
- movement profile speed/jump modifiers.
- map traversal difficulty.
- route jitter from profile/realism policy.

Commit real visible position only at:

- arrival.
- interruption.
- materialization.
- interaction range check.
- checkpoint.

### Background Combat

Use abstract rounds based on:

- shared combat formulas.
- estimated DPS.
- hit chance.
- skill cost.
- mob HP/defense/avoidability.
- agent HP/MP/potions.
- map spawn pool and crowding.
- safety/death risk.

Combat may produce:

- EXP.
- mesos.
- common drops.
- rare drops.
- quest kill progress.
- quest item progress.
- potion/resource consumption.
- death/recovery events.

### Background Loot

Use:

- direct validated loot credit.
- expected value for common drops.
- batched rolls for uncommon drops.
- explicit rolls for rare drops, scrolls, equips, and event items.
- loot buffer for routine item/meso credits.

Loot must respect:

- drop table source.
- quest-only restrictions.
- ownership rules where relevant.
- inventory capacity at reconciliation.
- item reservation policy.
- economy/fairness budgets.

### Background NPC And Quest

Use:

- direct validated quest start/complete.
- direct service action when script-safe.
- no visual dialogue delay unless realism policy requests simulated timing.

Must validate:

- map id.
- NPC placement and service.
- range/materialization rule.
- level.
- prequests.
- item/meso requirements.
- reward capacity.
- script-sensitive restrictions.

### Background Shop

Use:

- direct validated buy/sell.
- batched sell-trash operations.
- inventory buffer reconciliation before transaction.

Must validate:

- shop exists.
- NPC/shop accessible from current map.
- mesos.
- inventory space.
- item lock/reservation policy.
- price/source rules.

## Virtual State

Virtual state is temporary Agent state that is not fully materialized as normal
server-visible objects yet.

Examples:

- virtual position.
- route progress.
- combat slice progress.
- buffered mesos.
- buffered common ETC counts.
- pending rare drop objects.
- expected potion use.
- pending quest item progress.
- background death/recovery state.

Virtual state must be bounded, auditable, and reconciled before player-visible
or durable boundaries.

## Reconciliation Boundaries

Strict reconciliation happens when:

- a real player enters the Agent map.
- the Agent trades with a player or opens a public shop.
- the Agent needs a quest item/reward check.
- the Agent sells/drops/equips/uses an item.
- the Agent enters a sensitive map.
- the Agent despawns.
- the server shuts down.
- a milestone happens.
- a debug/soak verifier requests strict state.

Milestones:

- level up.
- job advance.
- quest complete.
- rare item obtained.
- player trade.
- market listing/purchase.
- unusual death/recovery event.

## Fairness And Anti-Exploitation

Background Agents must not become perfect machines.

Apply bounded penalties for:

- route inefficiency.
- repositioning.
- overkill.
- missed spawns.
- fatigue.
- crowding.
- inventory sorting.
- potion/rest delays.
- profile impatience/carelessness.
- social sidetracks.

Fairness budgets should cap:

- EXP/hour by level/job/map band.
- meso/hour by map/economy band.
- rare rolls/hour.
- quest objective completions/hour.
- market actions/hour.

## Relationship To Packages

Simulation Tier Runtime:

- decides whether background action is allowed and which shortcuts are enabled.

Capability Runtime:

- submits validated capability commands and receives results.

Route ETA Runtime:

- supplies route and same-map timing estimates.

Catalog Platform:

- supplies maps, drops, mobs, shops, NPCs, quests, regions, spawn density, and
  sensitivity metadata.

Perception Runtime:

- supplies bounded current-state summaries and invalidation triggers.

Recovery Policy:

- handles death, low supplies, stuck state, full inventory, and blocked actions.

Economy Engine:

- provides item value and sell/hold/buy/farm hints.

Profile Platform:

- provides playstyle, risk tolerance, patience, and item reservation policy.

Observability:

- records background action savings, commits, failures, materialization, and
  strict-debug mismatches.

Server Adapter:

- validates and commits final mutations into Cosmic server state.

## Success Criteria

The package is ready when:

- presentation path and background path are clearly separated.
- background actions only run when Simulation Tier allows them.
- every background mutation has validation and reconciliation rules.
- movement, combat, loot, NPC/quest, shop, death, and recovery have defined
  background behavior.
- rare/economy-sensitive events are explicit and journaled.
- materialization is safe and fails closed.
- strict debug mode can compare background outcomes against closer-to-real
  execution.
- 2000-Agent soak tests can measure background savings and correctness.
