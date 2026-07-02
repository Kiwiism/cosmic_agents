# Maple Island Questline MVP Vision And Overview

This document explains the Maple Island questline MVP in a reader-friendly way.
It describes what the MVP is, why it is the first gameplay milestone, what the
Agent must be able to do, and what is intentionally excluded.

## Short Version

The Maple Island MVP is the first real proof that the reconstructed Agent engine
can complete a meaningful questline through modular capabilities.

The target:

```text
Spawn one Agent with a Maple Island plan card.
Agent completes the selected Maple Island questline.
Agent stops at Southperry.
Agent does not use Shanks to leave Maple Island.
```

This is not full autonomy yet. It is a controlled vertical slice that proves the
core loop works:

```text
plan -> objective -> capability -> live validation -> action -> result journal
```

## Why Maple Island First

Maple Island is a good first milestone because it is small enough to control but
complete enough to test the important Agent systems.

It exercises:

- ordered quest progression.
- NPC interaction.
- quest start and completion.
- navigation between maps.
- portal travel.
- item use.
- combat.
- looting.
- inventory checks.
- special-case scripted items.
- reactor interaction.
- recovery from failure.
- plan exit criteria.

It also has clear boundaries:

- no Free Market.
- no job advancement.
- no Victoria Island progression.
- no LLM decisions.
- no general economy.
- no broad NPC dialogue replay.

That makes it a practical first gameplay package.

## What This MVP Proves

The MVP proves that an Agent can follow a plan card and complete real server
objectives without relying on a human player.

Specifically, it proves:

- plan runtime can load and run ordered objectives.
- Agent can navigate to maps and NPCs.
- Agent can validate NPC presence and interaction range.
- Agent can start and complete quests through normal server APIs.
- Agent can fight required mobs.
- Agent can loot required items.
- Agent can use required items.
- Agent can handle special cases safely.
- Agent can stop at the intended endpoint.
- Agent can record what happened.
- Agent can fail cleanly with structured blockers instead of looping forever.

## What It Does Not Prove Yet

This MVP does not mean the Agent is fully autonomous.

It does not include:

- LLM planning.
- dynamic economy behavior.
- Free Market shopping.
- general shop automation.
- party/social behavior.
- job advancement.
- Victoria Island questing.
- arbitrary NPC dialogue options.
- every skill/spell.
- advanced buff/damage parity.
- 2000-Agent scaling.

Those are later packages.

## Core Idea

The MVP should not be a hardcoded bot script that blindly clicks through steps.

It should be a structured plan:

```text
Plan:
  complete selected Maple Island questline

Objectives:
  navigate to NPC
  start quest
  satisfy requirement
  complete quest
  move to next map
  stop at Southperry
```

Each objective does one thing. This keeps the system debuggable and reusable.

## Plan Card Design

The plan card describes the route and rules.

It should contain:

- plan id.
- start map.
- required quests.
- start-only quests.
- excluded quests.
- optional-review quests.
- ordered route groups.
- special handling.
- forbidden actions.
- fallback policy.
- exit criteria.

The plan card should not contain low-level movement loops or combat loops.

Those belong to capabilities.

Reason:

```text
Plan card says what should happen.
Capabilities know how to do it.
Server validation decides whether it is allowed.
```

## Objective Model

Each objective should be small and inspectable.

Examples:

- navigate to map.
- navigate to NPC.
- start quest.
- complete quest.
- kill mob count.
- loot item count.
- use item.
- collect reactor box items.
- auto-complete known no-complete-NPC quest.
- grant known scripted item.
- skip optional-review quest.
- stop plan.

If an objective fails, it should return a structured reason:

- missing NPC.
- out of range.
- quest requirement not met.
- required item missing.
- target mob unavailable.
- inventory full.
- navigation stuck.
- death loop.
- forbidden action.

This is important because failure must be understandable.

## Required Capabilities

### Plan Runtime

Needed for:

- loading the plan.
- selecting next objective.
- tracking progress.
- enforcing dependencies.
- resuming after relog/restart.
- checking exit criteria.

### Quest Capability

Needed for:

- reading live quest state.
- checking start requirements.
- checking completion requirements.
- starting quests.
- completing quests.
- rejecting forbidden quest completions.

Important rule:

```text
Normal runtime should not use forceStart or forceComplete.
```

### NPC Quest Interaction Capability

Needed for:

- checking that the NPC exists in the live map.
- checking map and range.
- validating that the action is allowed by the plan.
- starting or completing quests through the quest capability.

Agents do not need to click through normal dialogue for MVP. They can call
validated quest actions directly after meeting the requirements.

### Navigation And Portal Travel

Needed for:

- moving to maps.
- moving to NPCs.
- moving to portals.
- moving to exact objective points.
- verifying arrival.
- blocking off-island travel.

### Combat Capability

Needed for:

- killing required mobs.
- stopping when live quest kill count is satisfied.
- backing off when no target exists.
- reporting danger or recovery needs.

### Loot Capability

Needed for:

- prioritizing quest items.
- stopping when item count is satisfied.
- reporting full inventory or unreachable loot.

### Inventory And Item Use

Needed for:

- counting items.
- checking free slots.
- protecting quest items.
- using Roger's Apple.
- handling known scripted items.

### Reactor Interaction

Needed for:

- Pio's reactor-box items.

If not implemented yet, the objective must block cleanly or use a debug-only
resolver when explicitly enabled.

### Recovery Capability

Needed for:

- low HP.
- low MP.
- death.
- stuck movement.
- no progress.
- no potions and no mesos.

Recovery must never use Shanks or off-island travel.

### Plan Journal

Needed for:

- objective start/end.
- result status.
- retry count.
- blocker reason.
- debug resolver use.
- map/NPC/quest/item/mob ids.

The journal is how we know what happened.

## Route Overview

Start:

```text
10000 Mushroom Town
```

End:

```text
2000000 Southperry
```

High-level route:

1. Mushroom Town.
2. Snail Garden.
3. Nina and Sen route.
4. Todd/Peter optional-review stop.
5. Sam route.
6. Amherst route.
7. Yoona before Mai.
8. Mai training route.
9. Maria/Shanks-related Maple Island quest completion.
10. Southperry.
11. Start Biggs `1046`.
12. Stop without leaving Maple Island.

Full ordered details live in:

```text
docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md
```

## Important Route Decisions

### Yoona Before Mai

Yoona's questline should run before Mai.

Reason:

- this is the selected MVP route ordering.
- it keeps known special handling explicit.

### Biggs `1046`

The Agent should start Biggs's Victoria Island story but leave it incomplete.

Reason:

- it marks the transition point without actually leaving Maple Island.

### Shanks

Shanks may be used for Maple Island quest completion where required.

Shanks must not be used to leave Maple Island.

Reason:

- MVP exit condition is Southperry, not Lith Harbor.

### Quest `1028`

Do not complete `1028 To Lith Harbor!`.

Reason:

- it requires leaving Maple Island.

### Todd Quests

Todd `1018` and `1035` are skipped by default.

Reason:

- they may be legacy/tutorial-sensitive.
- they need manual review before enabling.

## Special Handling

### Roger's Apple `1021`

The Agent must:

1. start quest.
2. use apple item `2010007`.
3. complete quest.

If the apple is missing, block or use a debug-only resolver when enabled.

### Pio `1008`

Required items come from reactor boxes:

- `4031161`
- `4031162`

The Agent needs minimal reactor-box interaction.

If reactor support is missing, block cleanly as:

```text
capability-missing: reactor-interaction
```

### Yoona `8020`

Yoona's shopping guide item is special:

- required item: `4031180`

For MVP, this may be granted/spawned through a controlled plan objective. This
must not become a generic arbitrary item grant.

### Auto-Complete Quests

Known candidates:

- `1030`
- `8023`

If no complete NPC exists, the Agent may use safe auto-complete handling after
requirements are validated.

## Forbidden Behavior

The MVP must not:

- leave Maple Island.
- use Shanks travel to Lith Harbor.
- complete `1028`.
- complete `1046`.
- force-complete arbitrary quests.
- grant arbitrary items.
- loop forever on missing data.
- drop protected quest items.

## Fast Test Mode

First implementation should use:

```text
interactionRealism = OFF
spawnPressureClearing = OFF
futureQuestLootPriority = OFF
allowDebugResolvers = false
allowShopResupply = false
```

Reason:

- first prove the sequence and validation.
- then add realism and smarter behavior later.

## Interaction Realism Later

After deterministic completion works, realism can be enabled.

Examples:

- random NPC approach spots.
- dialogue-length delay.
- profile-specific delay jitter.
- anti-stacking reservation around NPCs.

This should not change the objective sequence.

## Combat Behavior

First version:

- target only required mobs.
- stop when live quest state says done.
- block cleanly if mob unavailable.

Later version:

- if target mob count is low and map is clogged, kill limited filler mobs.
- prefer filler mobs that drop future quest-relevant items.
- still stop as soon as objective is satisfied.

## Recovery Behavior

If the Agent is low HP:

1. use potion if available.
2. move to safer position.
3. sit on chair if available.
4. idle/rest until safe HP.
5. resume objective.
6. block if repeated death/near-death occurs.

If no potions and no mesos:

- rest instead of shopping.
- lower combat pace.
- block if objective remains unsafe.

## Success Criteria

The MVP is successful when:

- Agent starts from `10000 Mushroom Town`.
- Agent ends at `2000000 Southperry`.
- selected required Maple Island quests are complete.
- `1046` is active/incomplete.
- `1028` is incomplete.
- Shanks travel off-island never happens.
- every objective has a journal row.
- failures produce blockers, not infinite loops.
- relog/restart can resume safely.

## Benefits Of This MVP

### Proves The New Architecture

It proves the reconstructed Agent engine can run a real plan using modular
capabilities.

### Keeps Scope Controlled

It avoids LLM, economy, broad dialogue, shops, and Victoria Island until the
core gameplay loop is stable.

### Creates Reusable Building Blocks

The same pieces can later support:

- Victoria Island quests.
- job advancement.
- training routes.
- economy plans.
- LLM-assigned objectives.

### Makes Failures Debuggable

Every objective has a result, reason, and journal row.

This is required before scaling to many Agents.

## Roadmap In Plain Language

### Step 1: Load The Plan

Read `maple-island-mvp.plan.json` and validate the route.

### Step 2: Track Progress

Store current objective, completed objectives, retries, and blockers.

### Step 3: Add Quest/NPC Actions

Read quest state, validate NPC interaction, start and complete quests.

### Step 4: Add Movement Objectives

Navigate to maps, portals, NPCs, mobs, and points.

### Step 5: Add Combat/Loot Objectives

Kill required mobs and loot required items.

### Step 6: Add Special Cases

Handle Roger apple, Yoona guide, auto-complete quests, and Pio reactors.

### Step 7: Add Recovery

Handle death, low HP/MP, stuck movement, no mobs, and full inventory.

### Step 8: Run Full Integration Test

Spawn one Agent and run the full questline to Southperry.

## Final Vision

The Maple Island MVP is the first controlled gameplay proof for the
reconstructed Agent engine.

It should show that an Agent can follow a plan, use capabilities, validate live
server state, recover from basic failures, and complete a real questline without
hardcoding everything into the core engine.

It is intentionally narrow. That is the point. Once this works, larger
autonomous progression can be built on the same foundation.
