# Perception Runtime Design Specification

Purpose:

```text
Define the future package that converts live server state, catalog context,
plan context, and bounded memory into compact Agent/LLM perception snapshots.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
Perception is what the Agent can know now.
Memory is what the Agent can remember later.
Catalog is what the world generally contains.
```

Perception must be compact, bounded, explainable, and safe to expose to Agent
logic or LLM tools. It must not expose raw Cosmic object graphs, packet data, or
unbounded server internals.

## Goals

- Provide compact current-state snapshots for Agent planning and LLM summaries.
- Support nearby NPC, mob, drop, player, Agent, portal, reactor, and shop
  awareness.
- Include current plan/objective context without owning plan state.
- Merge live state with catalog facts such as quest relevance, drop relevance,
  map region, route affordances, and risk flags.
- Support different detail levels for urgent, active, strategic, and batch
  views.
- Adjust refresh cadence by simulation tier to support 2000-Agent scaling.
- Keep memory separate from immediate perception while allowing bounded memory
  hints to enrich snapshots.

## Non-Goals

- Do not decide what the Agent should do.
- Do not execute capabilities or mutate server state.
- Do not store full profile history.
- Do not replace Catalog Platform static indexes.
- Do not replace Economy Engine price/history models.
- Do not send raw server objects to LLM.
- Do not require LLM to run normal Agent loops.

## Snapshot Levels

### URGENT

Used when the Agent is blocked, dying, low on potions, in an unsafe map, or
needs immediate recovery.

Includes:

- HP/MP danger.
- death/respawn state.
- stuck/blocker reason.
- hostile nearby mobs.
- urgent drops/items/potions.
- active objective and immediate fallback hints.

### ACTIVE

Used by normal plan/capability loops.

Includes:

- location.
- status.
- nearby relevant entities.
- active plan/objective.
- route and interaction affordances.
- short objective memory.

### STRATEGIC

Used for higher-level planning, profile adaptation, economy decisions, and
LLM review.

Includes:

- build/level/job summary.
- mesos and equipment summary.
- plan backlog summary.
- market/economy hints from Economy Engine.
- relationship/social hints from Profile Platform.
- longer-term memories summarized, not raw.

### BATCH

Used when LLM or console needs to inspect many Agents cheaply.

Includes:

- one compact row per Agent.
- current map/region.
- current plan/objective.
- state: idle, moving, combat, shop, quest, blocked, recovery.
- blocker flag and short reason.
- last meaningful event.

## Perception Sources

Live Server Adapter:

- current map.
- position and foothold.
- HP/MP/status.
- inventory availability.
- nearby live entities.
- real-player presence.
- map/controller/sensitivity hints.

Catalog Platform:

- map names and regions.
- NPC placements and actions.
- mob/drop relevance.
- portal links.
- shop services.
- quest requirements/rewards.
- reactor/object affordances.
- risk/manual-review flags.

Plan Runtime:

- active plan.
- active objective.
- expected target entity/item/map.
- focus mode.
- retry/blocker state.

Profile Platform:

- visibility preferences.
- playstyle and risk tolerance.
- relationship summaries.
- relevant decision memories.

Economy Engine:

- item value hints.
- buy/sell/farm relevance.
- liquidity/confidence summaries.

Simulation Tier Runtime:

- current simulation mode.
- allowed refresh cadence.
- visible/background context.

## Nearby Entity Relevance

Perception should not list every possible object when a bounded top-N summary is
enough.

Entity scoring should consider:

- distance.
- active objective relevance.
- future quest relevance.
- danger/threat.
- market value.
- profile interest.
- route/interact availability.
- social relevance.

Example:

```text
nearby mobs = top objective targets + top threats + top future-loot mobs
nearby drops = quest items + valuable drops + potions + owned drops
nearby NPCs = current objective NPC + nearby service NPCs + important quests
```

## Memory Boundary

Perception may include small memory hints, but it does not own the profile
memory store.

Perception owns:

- short-lived snapshot cache.
- bounded last-seen entity cache.
- local stuck/retry hints.
- summarization view construction.

Profile Platform owns:

- personality and preferences.
- relationship memory.
- decision journal.
- long-lived adaptation.

Economy Engine owns:

- market observations.
- price memory.
- item valuation history.

Catalog Platform owns:

- static world facts.
- source metadata.
- generated indexes.

## Refresh Policy

Refresh cadence depends on simulation mode and consumer.

```text
PRESENTATION:
  active snapshots are fresh enough for visible behavior

BACKGROUND_ACTIVE:
  slower refresh, but live map state remains checked

BACKGROUND_ABSTRACT:
  snapshot at objective slice boundaries and materialization points

STRATEGIC_OFFLINE:
  coarse summary only
```

Urgent events can force refresh regardless of normal cadence.

## LLM Safety

LLM-facing perception must:

- hide raw object references.
- cap array sizes.
- include names alongside ids.
- include uncertainty/reason flags.
- include only action-relevant state.
- avoid private player details unless explicitly allowed by policy.
- summarize large histories.
- expose typed command affordances instead of raw methods.

## Success Criteria

The package is ready when:

- snapshot levels are explicit and testable.
- live state, catalog facts, plan context, profile hints, and economy hints have
  clear boundaries.
- snapshot sizes are bounded.
- nearby entity relevance scoring is defined.
- refresh cadence can adapt to simulation tier.
- LLM-safe summaries can be produced without raw server internals.
- batch perception can summarize hundreds of Agents cheaply.
