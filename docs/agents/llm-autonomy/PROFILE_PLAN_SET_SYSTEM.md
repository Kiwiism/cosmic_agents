# Profile Plan Set System

This document defines how Agents choose and mix plan cards through profile
archetypes. Most Agents should have weighted plan mixes, but the system must
also support specific profiles that behave in intentionally unique ways.

Examples:

- an `islander` who never leaves Maple Island.
- a cursed-doll farmer who repeatedly farms Cursed Dolls for quest demand.
- a Red Whip farmer who farms/sells Red Whips because many players need one to
  unlock the scrolling interface.
- a town mingler who spends most time talking/standing/socializing in early
  towns.
- a supplier who repeatedly farms or buys consumables/materials for others.

## Design Goal

Profiles should be able to say:

```text
this Agent is not just randomly choosing plans;
this Agent has an identity, boundaries, recurring behaviors, and preferences.
```

Normal Agents:

```text
profile traits + live state + plan weights -> next plan
```

Specific archetype Agents:

```text
profile archetype + hard constraints + plan set -> behavior identity
```

## Core Concepts

### Archetype

Stable identity template.

Examples:

- `islander`.
- `cursed-doll-farmer`.
- `red-whip-farmer`.
- `maple-island-town-mingler`.
- `fm-scout`.
- `party-helper`.
- `quest-completionist`.
- `self-sufficient-grinder`.

The archetype influences:

- allowed regions.
- allowed plan cards.
- forbidden actions.
- preferred maps.
- preferred items.
- social behavior.
- economy behavior.
- persistence/stubbornness.

### Plan Set

A named group of plan cards with weights and constraints.

Example:

```json
{
  "planSetId": "islander-loop",
  "selectionMode": "weighted",
  "plans": [
    {
      "planId": "maple-island-hunt-snails",
      "weight": 0.35
    },
    {
      "planId": "maple-island-town-mingle",
      "weight": 0.25
    },
    {
      "planId": "maple-island-farm-orange-mushroom-caps",
      "weight": 0.20
    },
    {
      "planId": "maple-island-idle-social",
      "weight": 0.20
    }
  ]
}
```

### Hard Constraint

A rule that cannot be overridden by weights, mood, or LLM suggestion.

Examples:

- never leave Maple Island.
- never complete Shanks off-island travel.
- never sell protected quest items.
- never buy above max budget.
- never trade with players.
- never enter Free Market.

### Soft Preference

A weight or bias that can change behavior but can be overridden by safety,
fallback, or stronger plan needs.

Examples:

- prefer hunting maps over towns.
- prefer farming item sources personally.
- prefer social idling at town.
- prefer selling to players over NPC.
- prefer low-crowd channels.

## Profile Schema Extension

Add a `planProfile` section to Agent profiles.

```json
{
  "planProfile": {
    "archetype": "islander",
    "planSetIds": ["islander-loop"],
    "selectionMode": "weighted-with-hard-constraints",
    "hardConstraints": {
      "allowedRegionIds": ["maple-island"],
      "allowedMapIds": [10000, 20000, 30000, 30001, 40000, 50000, 1000000, 1010000, 1020000, 2000000],
      "forbiddenNpcActions": [
        {
          "npcId": 22000,
          "action": "travel-off-island",
          "reason": "Islander identity."
        }
      ],
      "forbiddenQuestCompletions": [1028],
      "allowFreeMarket": false,
      "allowJobAdvancement": false
    },
    "softPreferences": {
      "preferredMapIds": [10000, 2000000],
      "preferredPlanCategories": ["farm", "idle-social", "town-mingle"],
      "avoidCrowdedMaps": false,
      "returnHomeMapId": 2000000
    }
  }
}
```

## Plan Selection

Plan selection uses this order:

1. Filter by hard constraints.
2. Filter by live feasibility.
3. Score plans by base weight.
4. Apply profile trait modifiers.
5. Apply mood modifiers.
6. Apply memory modifiers.
7. Apply economy/quest/social opportunity modifiers.
8. Select plan with weighted randomness.
9. Validate selected plan entry criteria.

Formula:

```text
planScore =
  baseWeight
  * archetypeMultiplier
  * traitMultiplier
  * moodMultiplier
  * memoryMultiplier
  * opportunityMultiplier
  * feasibilityMultiplier
```

Hard constraints set score to zero.

## Archetype Examples

### Islander

Identity:

```text
Never leaves Maple Island.
Lives around Maple Island towns and hunting maps.
May farm, idle, mingle, help other islanders, or collect low-level items.
```

Hard rules:

- allowed maps only inside Maple Island route.
- no Shanks off-island travel.
- no Lith Harbor progression.
- no job advancement outside island.
- no Free Market.
- no Victoria questline.

Typical plans:

- `maple-island-idle-social`.
- `maple-island-hunt-snails`.
- `maple-island-hunt-orange-mushrooms`.
- `maple-island-farm-quest-etc`.
- `maple-island-town-mingle`.
- `maple-island-help-new-agent`.

Good behavior:

- sometimes stands in Southperry or Amherst.
- sometimes hunts low-level mobs.
- sometimes follows another islander briefly.
- sometimes farms local etc items.
- never attempts the normal post-Maple-Island level 30 path.

### Cursed Doll Farmer

Identity:

```text
Specialized farmer who repeatedly farms Cursed Dolls because many quests need
large quantities.
```

Hard rules:

- must have map access to Cursed Doll farming areas.
- keep farming plan weight high.
- keep reserve of Cursed Dolls.
- sell only surplus above reserve.
- avoid selling below minimum policy price.

Typical plans:

- `farm-cursed-dolls`.
- `return-town-sell-surplus`.
- `restock-potions`.
- `scan-market-cursed-dolls`.
- `idle-near-market-or-town`.

Plan set:

```json
{
  "planSetId": "cursed-doll-specialist",
  "plans": [
    {
      "planId": "farm-cursed-dolls",
      "weight": 0.60
    },
    {
      "planId": "sell-cursed-doll-surplus",
      "weight": 0.18
    },
    {
      "planId": "resupply-consumables",
      "weight": 0.12
    },
    {
      "planId": "town-idle-social",
      "weight": 0.10
    }
  ]
}
```

### Red Whip Farmer

Identity:

```text
Farms Red Whip supply because players/Agents need it for scrolling interface
unlock flow.
```

Hard/soft rules:

- keep a few Red Whips in stash.
- sell or trade surplus.
- do not flood market too aggressively.
- price from market model with minimum margin.
- may help known Agents/players with lower price if relationship allows.

Typical plans:

- `farm-red-whip`.
- `sell-red-whip-surplus`.
- `trade-red-whip-to-requester`.
- `scan-red-whip-price`.
- `resupply-consumables`.

### Town Mingler

Identity:

```text
Spends much of the time in towns, creating social presence.
```

Plans:

- idle near NPC.
- greet nearby Agents/players.
- walk between common town points.
- respond to help/trade/party prompts.
- occasionally do small farming loop.

Hard rules:

- must not block important NPCs.
- must not spam chat.
- must respect interaction cooldowns.

## Recurring Farm Plan Design

Recurring farm plans should define:

- target item ids.
- reserve amount.
- surplus behavior.
- preferred maps.
- alternate maps.
- target mobs.
- expected farm duration.
- rest/resupply policy.
- sell/trade/stash policy.
- market impact limits.

Example:

```json
{
  "planId": "farm-cursed-dolls",
  "category": "farm",
  "targetItems": [
    {
      "itemId": 4000040,
      "name": "Cursed Doll",
      "reserveQuantity": 200,
      "sellSurplus": true
    }
  ],
  "targetMobs": [],
  "preferredMapIds": [],
  "exitCriteria": {
    "minRuntimeMs": 900000,
    "maxRuntimeMs": 3600000,
    "stopWhenInventoryNearFull": true,
    "stopWhenPotionLow": true
  },
  "fallbackPolicy": {
    "ifMapCrowded": "change-channel-or-alternate-map",
    "ifDeathLoop": "postpone-and-rest",
    "ifMarketOversupplied": "stash-surplus"
  }
}
```

## Interaction With Economy Engine

Specialized farmers should not blindly sell everything.

Before selling:

- check reserve.
- check current market confidence.
- check agent market-share cap.
- check price floor.
- check market flood risk.
- check relationship requests.

If market is oversupplied:

- stash surplus.
- vendor only if low value.
- switch to alternate plan.
- idle/socialize.
- farm different item.

## Interaction With LLM

LLM can assign or suggest archetypes:

```text
create 5 islanders
create 2 cursed doll farmers
create 1 red whip supplier
```

LLM cannot bypass hard constraints.

Example:

```text
LLM asks islander to go Lith Harbor
-> rejected by profile hard constraint
-> Agent may explain: islander identity forbids leaving Maple Island
```

## Decision Journal

Profile plan selection should record:

- selected plan id.
- plan set id.
- score breakdown.
- hard constraints applied.
- rejected plans and reasons.
- profile traits influencing choice.
- memory/economy/social influences.

Example:

```json
{
  "agentId": 301,
  "decisionType": "plan-selection",
  "archetype": "islander",
  "selectedPlanId": "maple-island-town-mingle",
  "rejectedPlans": [
    {
      "planId": "go-lith-harbor",
      "reason": "hard-constraint: islander-never-leaves-maple-island"
    }
  ],
  "influences": [
    "high-socialness",
    "recent-long-farming-session",
    "return-home-map-southperry"
  ]
}
```

## Implementation Notes

- Hard constraints must be checked before plan selection and again before
  capability execution.
- Plan sets should be data-driven JSON/YAML, not hardcoded.
- Archetypes should be reusable templates with per-Agent overrides.
- Weighted selection should use stable per-Agent seeds to avoid identical
  behavior.
- Weights can drift through memory, but hard constraints do not drift.
- Specific profiles should be rare enough that they feel intentional, not like
  every Agent is locked to a gimmick.

