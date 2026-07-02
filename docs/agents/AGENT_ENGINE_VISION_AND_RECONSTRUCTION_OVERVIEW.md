# Agent Engine Vision And Reconstruction Overview

This document explains the Agent reconstruction in a more reader-friendly way.
It is meant for someone who wants to understand what the project is trying to
achieve without reading every implementation note.

## Short Version

The reconstruction takes nutnnut's existing AI companion/bot system and turns it
into a cleaner Agent engine.

At this stage, the goal is not to add a lot of new gameplay features yet. The
goal is to rebuild the old behavior into a safer, more maintainable, modular
foundation so future features can be developed without constantly risking the
core engine.

In simple terms:

```text
Old system:
  one large companion/bot system with many behaviors tied together

Reconstructed system:
  a modular Agent engine with separated capabilities, runtime state,
  commands, policies, events, and server integration
```

The first big win is not flashy new gameplay. The first big win is structure.

## What This Agent Engine Is

The Agent engine is the server-side system that controls AI-driven characters.

An Agent can eventually:

- move around maps.
- fight monsters.
- loot items.
- follow plans.
- interact with NPCs.
- complete quests.
- use shops/trade later.
- behave differently based on profile later.
- be directed by an LLM later.

But after reconstruction alone, the main purpose is:

```text
Preserve existing nutnnut bot behavior while moving it into a cleaner
architecture.
```

This means the current companion features should still behave as before, but
the code underneath is split into better ownership boundaries.

## What Behavior It Preserves

The reconstruction is intended to preserve useful existing bot behavior:

- existing bot commands.
- dialogue and chat responses.
- following and movement behavior.
- combat behavior.
- looting and inventory behavior.
- trading/supply behavior where already supported.
- build/equipment helper behavior.
- existing command parsing behavior.
- existing runtime behavior unless explicitly changed later.

This matters because reconstruction is risky. If the engine is changed and the
behavior also changes at the same time, it becomes hard to know whether a bug
came from architecture work or feature work.

So the current philosophy is:

```text
First preserve behavior.
Then improve structure.
Then optimize scale.
Then add new gameplay capabilities.
```

## What It Hopes To Achieve

The reconstructed Agent engine aims to achieve several things.

### 1. Easier Maintenance

Instead of one large bot system where many features are tangled together, each
area gets its own home.

Examples:

- movement code lives in movement/navigation capability packages.
- combat code lives in combat capability packages.
- chat and command parsing live in dialogue/command packages.
- runtime lifecycle state lives in runtime packages.
- future quest logic lives in quest/NPC capability packages.

This makes it easier to fix one feature without accidentally breaking another.

### 2. Modular Capabilities

A capability is a focused action area.

Examples:

- navigation capability.
- combat capability.
- loot capability.
- inventory capability.
- NPC interaction capability.
- quest capability.
- shop capability.
- trade capability.

The idea is:

```text
Plan says what the Agent wants to do.
Capability knows how to do that one kind of thing.
Server adapter validates whether it is allowed.
```

This allows new features to be developed independently. For example, NPC quest
interaction can be improved without rewriting combat. Combat can be improved
without touching profile logic.

### 3. Safer Feature Development

The reconstructed engine makes it possible to add new systems later without
placing them directly into the core tick loop.

Future features such as:

- Maple Island questline automation.
- profile-based behavior.
- economy participation.
- LLM control.
- background simulation.

can be built as packages around the core engine instead of being hardcoded
inside one giant manager.

### 4. Better Scalability

The current goal after reconstruction is to optimize toward:

```text
2000 concurrent Agents
```

To reach that, Agents cannot all run full player-like simulation all the time.
The engine needs to know when full realism is needed and when cheaper
simulation is enough.

The simple principle:

```text
If a real player can see it, make it look real.
If no real player can see it, simulate the result more cheaply.
```

### 5. Better Debugging

A modular Agent engine should be easier to inspect.

Instead of asking:

```text
Why did the bot do something weird?
```

we want to ask:

```text
Which plan was active?
Which objective was running?
Which capability handled it?
What did the validator reject?
What event was emitted?
What was the last known state?
```

That is much easier to debug, especially with hundreds or thousands of Agents.

## Why Reconstruction Comes Before New Features

It is tempting to add NPC quests, LLM, economy, and profile behavior directly.
But if those are added before the core is clean, every new feature increases
the mess.

The reconstruction gives future systems proper places to live.

Example:

```text
Without reconstruction:
  Add quest logic into existing bot manager.
  Later add profile checks inside quest logic.
  Later add LLM commands inside the same flow.
  Later add economy exceptions.
  Eventually everything depends on everything.

With reconstruction:
  Plan runtime selects objective.
  Profile runtime influences preference.
  Quest capability validates and executes quest action.
  Server adapter commits it safely.
  Event bus reports the outcome.
```

The second approach is slower at the beginning, but much safer long-term.

## Main Design Ideas

### Capability Pattern

What it means:

```text
Each action type is handled by a dedicated module.
```

Examples:

- navigation moves the Agent.
- combat attacks monsters.
- looting picks up items.
- NPC capability talks to NPCs.
- quest capability starts/completes quests.

Why use it:

- easier to test.
- easier to replace.
- easier to optimize.
- easier to prevent one feature from breaking another.

### Adapter Pattern

What it means:

```text
The Agent platform talks to Cosmic through a controlled adapter layer.
```

The Agent engine should not freely reach into every server class whenever it
wants. Instead, server-specific logic is placed behind an adapter.

Why use it:

- keeps the Agent platform more portable.
- makes future installation into another Cosmic-like server easier.
- makes server validation explicit.
- prevents gameplay systems from bypassing safety checks.

### Event-Driven Design

What it means:

```text
When something happens, emit an event.
Other systems can listen without being tightly coupled.
```

Examples:

- Agent completed an objective.
- Agent failed navigation.
- Agent died.
- Agent looted an item.
- Agent finished a quest.

Why use it:

- profile learning can consume events.
- observability can consume events.
- economy can consume events later.
- debugging becomes easier.
- systems do not need to call each other directly.

### Plan / Objective Model

What it means:

```text
A plan is a bigger goal.
An objective is one small step.
```

Example:

```text
Plan:
  Complete Maple Island questline.

Objectives:
  go to NPC
  start quest
  kill required mobs
  loot required item
  complete quest
  move to next map
```

Why use it:

- easier to resume after relog/restart.
- easier to debug where the Agent got stuck.
- easier to let future LLM assign high-level work.
- easier to add fallback behavior.

### Policy / Validator Pattern

What it means:

```text
Before an action changes server state, it must pass checks.
```

Examples:

- Is the Agent on the correct map?
- Is the NPC actually present?
- Is the Agent in range?
- Are quest requirements met?
- Is the action forbidden by the current plan?
- Is the item protected?

Why use it:

- prevents unsafe automation.
- avoids invalid quest completions.
- makes failures structured instead of mysterious.
- keeps the live server authoritative.

### Profile As A Decision Service

What it means:

```text
The profile system decides preferences, not actions.
```

A profile might say:

- this Agent is cautious.
- this Agent prefers quests.
- this Agent likes farming.
- this Agent avoids risky maps.
- this Agent is an islander and must never leave Maple Island.

But the profile does not directly move, attack, or complete quests.

Why use it:

- behavior variation stays separate from action execution.
- capabilities remain reusable.
- future LLM can understand the Agent's personality and history.

### Scheduler And Simulation Tiers

What it means:

```text
Not every Agent needs the same amount of simulation every moment.
```

Modes:

- Presentation mode: real player can see the Agent, so run full behavior.
- Background active mode: no player sees it, but map is important, so run
  reduced behavior.
- Background abstract mode: no player sees it and map is safe, so simulate
  outcomes cheaply.

Why use it:

- required for 2000 concurrent Agents.
- avoids wasting CPU on invisible movement packets.
- avoids broadcast spam.
- lets Agents continue making progress without full physics every tick.

## Optimization Techniques

### 1. Suppress Invisible Broadcasts

If no real player is in a map, there is no need to build and send visual
movement, attack, emote, or cosmetic packets.

Benefit:

- saves packet work.
- saves CPU.
- reduces noise.

### 2. Reduce Background Tick Rate

Invisible Agents do not need to update movement/perception as often as visible
Agents.

Benefit:

- fewer per-Agent calculations.
- smoother scaling.

### 3. Shared Perception

Instead of every Agent scanning the same map separately, the map can provide a
shared perception snapshot.

Benefit:

- avoids duplicated map scans.
- reduces CPU when many Agents are in the same area.

### 4. Route ETA Instead Of Full Physics

For invisible travel, the engine can estimate travel time instead of simulating
every step.

Example:

```text
Agent is going from portal A to NPC B.
Nobody can see it.
Calculate expected travel time.
Commit arrival later after validation.
```

Benefit:

- huge savings for background movement.
- still preserves believable timing.

### 5. Abstract Background Combat

If nobody can see the fight, the Agent does not need to perform every visible
attack animation.

The engine can run combat rounds using shared formulas and commit results.

Benefit:

- lower CPU.
- lower packet cost.
- Agents can still farm or progress.

### 6. Load Shedding

When the server is under pressure, lower-priority Agent work can be delayed.

Examples:

- pause cosmetic behavior.
- reduce background perception.
- delay LLM calls.
- delay economy analysis.
- keep visible/safety-critical Agents running.

Benefit:

- protects real player experience.
- prevents overload spirals.

### 7. Bounded Queues And Budgets

Agent work should have limits.

Examples:

- max work per tick.
- max events queued.
- max journal backlog.
- max background Agents processed per interval.

Benefit:

- prevents runaway loops.
- makes server performance predictable.

## Benefits Of The Reconstruction

### For Maintenance

- code is easier to locate.
- responsibilities are clearer.
- fewer hidden side effects.
- old bot behavior can be preserved while internals improve.
- future cleanup becomes safer.

### For Feature Development

- new capabilities can be added independently.
- Maple Island MVP can be built without changing the whole engine.
- economy can be built without rewriting movement.
- LLM control can call typed commands instead of touching server internals.
- profiles can influence behavior without owning execution.

### For Stability

- validators reduce invalid server actions.
- live state remains authoritative.
- structured blockers replace infinite loops.
- lifecycle cleanup reduces stale Agent state.
- observability makes failures easier to diagnose.

### For Scalability

- invisible Agents become cheaper.
- scheduler budgets control work.
- simulation tiers reduce unnecessary fidelity.
- shared perception avoids repeated scans.
- event queues can be bounded.
- 2000 concurrent Agents becomes a realistic target instead of every Agent
  running full behavior all the time.

### For Future LLM Integration

- LLM can operate through safe tools.
- LLM can assign plans instead of micromanaging packets.
- LLM can read profile/catalog/perception summaries.
- LLM can suggest profile changes without directly mutating server state.
- Agent engine remains responsible for validation and execution.

## What This Does Not Do Yet

After reconstruction alone, this does not automatically mean:

- full autonomous questing is complete.
- LLM control is active.
- economy engine is running.
- every skill/spell is supported.
- every NPC interaction is automated.
- 2000 Agents are already optimized.

Reconstruction creates the foundation.

Optimization and gameplay expansion come after.

## Roadmap In Plain Language

### Step 1: Reconstruct

Move nutnnut bot behavior into clean Agent-owned modules while preserving
behavior.

### Step 2: Optimize The Engine

Make the Agent runtime measurable and scalable:

- observability.
- scheduler.
- simulation modes.
- background abstraction.
- lifecycle cleanup.

Goal:

```text
work toward 2000 concurrent Agents
```

### Step 3: Add First Gameplay MVP

Build the Maple Island questline package:

- plan card.
- NPC quest interaction.
- combat/loot objectives.
- recovery.
- journal.

Goal:

```text
one Agent completes Maple Island and stops at Southperry
```

### Step 4: Expand Autonomy

Add:

- profiles.
- economy.
- Victoria Island progression.
- job paths.
- LLM control.
- population behavior.

## Final Vision

The reconstructed Agent engine should become the foundation for a living server
population.

Not just bots that repeat scripts, but Agents that can eventually:

- follow plans.
- make decisions through profiles.
- remember outcomes.
- participate in the world economy.
- behave differently from each other.
- scale to thousands of concurrent characters.
- remain safe and understandable to server operators.

The reconstruction is the quiet but important first layer: turning an existing
AI companion system into a maintainable, modular, scalable Agent platform.
