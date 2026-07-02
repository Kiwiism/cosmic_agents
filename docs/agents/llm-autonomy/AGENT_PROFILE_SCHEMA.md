# Agent Profile Schema

Agent behavior should come from layered profiles, not one fixed personality or
pure randomness.

Goal: agents should feel different, adapt over time, and avoid synchronized
behavior while still obeying server rules.

## Profile Layers

```text
Identity:
  stable who-this-agent-is

Role:
  current economic/gameplay purpose

Traits:
  long-term behavior weights

Mood:
  short-term state that changes with events

Memory:
  learned preferences, failures, prices, maps

Build Intent:
  long-term class, stat, skill, equipment, and upgrade direction

Policy:
  hard safety and permission limits
```

## Base Schema

```json
{
  "schemaVersion": 1,
  "agentId": 123,
  "identity": {
    "displayName": "Mira",
    "archetype": "careful-quester",
    "mainGoal": "leveling",
    "classPreference": "assassin",
    "economyRole": "self-sufficient-farmer"
  },
  "buildIntent": {
    "targetJobPath": ["beginner", "thief", "assassin", "hermit", "night-lord"],
    "statBuild": {
      "type": "dexless",
      "baseDexTarget": 25,
      "allowTemporaryDexGear": true,
      "avoidDexRequiredWeapons": true
    },
    "equipmentGoals": [
      {
        "itemId": 1472030,
        "name": "Maple Claw",
        "targetLevel": 35,
        "priority": "high",
        "acceptableQuality": "cheap-any-stat",
        "preferredQuality": "average-or-better",
        "upgradePlan": {
          "scrollStrategy": "safe-100-percent",
          "maxUpgradeSpendMesos": 500000
        },
        "acquisitionPreference": {
          "buyIfWithinBudget": true,
          "farmIfOverBudget": true,
          "acceptBelowAverageIfCheap": true
        }
      }
    ]
  },
  "traits": {
    "patience": 0.65,
    "curiosity": 0.40,
    "socialness": 0.25,
    "efficiency": 0.72,
    "caution": 0.58,
    "greed": 0.32,
    "stubbornness": 0.44,
    "routineBias": 0.38,
    "explorationBias": 0.35,
    "mistakeRate": 0.03
  },
  "timing": {
    "reactionDelayMs": [500, 1800],
    "dialogueReadingSpeed": "normal",
    "microPauseChance": 0.08,
    "afkChancePerHour": 0.02,
    "taskSwitchDelayMs": [1200, 6000]
  },
  "preferences": {
    "preferredTrainingStyle": "quest-then-grind",
    "shopScanStyle": "selective",
    "acquisitionStyle": "farm-before-overpaying",
    "equipmentPickiness": 0.25,
    "budgetStrictness": 0.70,
    "grindPersistence": 0.65,
    "favoredTownMapIds": [100000000],
    "avoidedMapIds": [],
    "preferredChannelBehavior": "avoid-crowds"
  },
  "policy": {
    "maxDeathRisk": "medium",
    "minReserveMesos": 50000,
    "maxSinglePurchaseMesos": 100000,
    "allowPlayerTrade": false,
    "allowScriptSensitiveNpc": false
  }
}
```

Trait values should be `0.0` to `1.0`.

## Build Intent

Build intent is the long-term gameplay direction that turns catalog facts into
agent intentions. It should answer questions such as:

- what job path does the agent want?
- what stat build does it follow?
- what equipment does the build require later?
- which items are acceptable substitutes?
- when should the agent buy, farm, craft, scroll, or postpone?
- how picky is the agent about stats and scroll quality?

Build intent should not be a hard script. It should influence plan selection and
economy decisions through profile decisions.

Example:

```text
Agent wants to become assassin.
Agent chooses dexless build with base DEX 25.
Agent cannot use normal DEX-required claws comfortably.
Agent marks Maple Claw as a level 35 target.
Agent checks market and drop sources.
Agent buys cheap below-average Maple Claw if budget is low and pickiness is low.
Agent farms for it if market price is too high and grind persistence is high.
Agent may stop farming after long dry streak if mesos gained are enough to buy.
```

## Decision Influences

Strategic decisions should record weighted influences. Recommended influence
groups:

- `build`: job path, stat build, skill plan, equipment goals.
- `profile`: patience, greed, caution, efficiency, stubbornness, pickiness.
- `policy`: meso reserve, max purchase, risk caps, protected items.
- `catalog`: item requirements, drop sources, shops, quests, mobs, maps.
- `economy`: price, liquidity, supply, demand, confidence, market trend.
- `memory`: past failures, dry streaks, known shops, successful maps.
- `live`: current level, mesos, inventory pressure, location, danger.
- `social`: party/owner requests, player trade offers, nearby activity.
- `llm`: assigned goals, temporary directives, approved exceptions.

Each major decision should store enough reason data to reconstruct why the agent
acted, without logging every low-level tick.

## Expanded Profile Dimensions

The profile should be richer than a basic personality. These dimensions make
agents feel different while still keeping decisions explainable and bounded.

### Build Identity

Long-term character-building direction.

Examples:

- dexless assassin.
- normal-stat assassin.
- unfunded warrior.
- funded mage.
- casual quester.
- HP-focused build.
- crafting-oriented character.

Build identity affects stat allocation, skill priorities, equipment goals,
scrolling behavior, farming targets, and economy demand.

### Economic Personality

How the agent treats mesos, items, and market risk.

Examples:

- `frugal`: avoids spending unless clearly necessary.
- `hoarder`: keeps future-use materials and rare drops.
- `flipper`: scans for underpriced items and resale opportunities.
- `crafter`: protects ores, crystals, maker materials, and ingredients.
- `generous`: helps party/owner with supplies more often.
- `gambler`: accepts more scroll/speculation risk within policy caps.
- `minimalist`: sells aggressively to keep inventory clean.

Economic personality affects buy/sell/hold, stash, scroll, merchant, and farm
decisions.

### Progression Philosophy

How the agent prefers to advance.

Examples:

- `quest-first`: completes quest chains before grinding.
- `grind-first`: prefers EXP/hour and simple loops.
- `market-first`: uses economy to solve item/supply needs.
- `social-first`: accepts party/help requests more often.
- `completionist`: clears optional quests and collections.
- `explorer`: tries alternate maps and shops.

Progression philosophy influences plan card priority and sidetrack tolerance.

### Failure Adaptation

How the agent responds when a plan is not working.

Examples:

- gives up quickly and replans.
- farms stubbornly through dry streaks.
- switches from farming to buying after enough mesos are earned.
- asks party/owner/LLM for help.
- lowers target quality to continue progression.
- postpones and returns later at higher level.

Failure adaptation should be driven by patience, stubbornness, frustration,
budget, risk, and memory.

### Social Bias

How strongly the agent reacts to other players and agents.

Examples:

- solo and focused.
- helpful party supporter.
- shy but responsive.
- party-seeking.
- opportunistic trader.
- mentor-like high-level helper.

Social bias affects party requests, trades, map sharing, help behavior, and
whether social sidetracks can interrupt a plan.

### Relationship Memory

Relationship memory is the per-agent record of how this agent understands
another agent or player. It is better than a flat friend/enemy flag because it
can influence different decisions in different ways.

Recommended names:

- `RelationshipMemory`: implementation/storage name.
- `SocialRelationshipModel`: design/system name.
- `CounterpartyProfile`: economy/trade-specific view.

Each agent may maintain relationship records for:

- other agents.
- real players.
- parties.
- shops or merchant owners.
- future guild/friend groups if supported.

Relationship memory should affect:

- whether to accept party invites.
- whether to help with mobs/items/quests.
- whether to trust a trade.
- whether to give discounts or supplies.
- whether to avoid a map/channel.
- whether to respond socially.
- whether to prioritize a request over the current plan.
- whether to remember a player as a good buyer/seller.

Example:

```json
{
  "target": {
    "type": "player",
    "id": "character-456",
    "displayNameHash": "optional-privacy-safe-hash"
  },
  "relationship": {
    "familiarity": 0.72,
    "trust": 0.81,
    "affinity": 0.64,
    "helpfulnessDebt": 0.30,
    "tradeReliability": 0.90,
    "partyCompatibility": 0.75,
    "annoyance": 0.10,
    "avoidance": 0.05
  },
  "tags": [
    "fair-trader",
    "helped-with-quest",
    "safe-party-member"
  ],
  "memory": {
    "lastInteractionAt": 123456789,
    "interactionCount": 12,
    "positiveInteractions": 9,
    "negativeInteractions": 1,
    "lastOutcome": "successful-trade"
  },
  "policy": {
    "allowAutoTrade": true,
    "allowPartyInvite": true,
    "requiresReviewForHighValueTrade": true
  }
}
```

Relationship values should be bounded and decay toward neutral unless refreshed.
Hard safety policy still wins over relationship memory. A trusted player should
not allow the agent to bypass trade limits, item protection, or anti-abuse
checks.

### Memory-Based Preference

The agent should learn preferences from previous outcomes.

Examples:

- this map gave good drops.
- this farming route had poor results.
- this shop was overpriced.
- this FM room often has cheap supplies.
- this player/agent often trades fairly.
- this monster caused too many deaths.

Memory should influence future route, market, combat, and plan decisions without
becoming an absolute rule.

### Narrative Tags

Small descriptive labels can help the LLM and admin tools summarize agents.

Examples:

- `unlucky-farmer`.
- `cheap-upgrader`.
- `overconfident-grinder`.
- `patient-crafter`.
- `market-scanner`.
- `helpful-party-member`.

Tags should be derived from repeated behavior or assigned by profile templates.
They are summaries, not hard behavior rules.

### Decision Pressure Model

Important decisions should combine multiple pressures instead of using one
if/else rule.

Recommended pressure inputs:

- build need.
- level urgency.
- meso pressure.
- inventory pressure.
- boredom.
- danger.
- market opportunity.
- social opportunity.
- relationship pressure.
- plan focus.
- LLM directive strength.

Example:

```text
Decision: buy Maple Claw now or keep farming.

build need: high
level urgency: medium
meso pressure: low
inventory pressure: medium
boredom: high
danger: low
market opportunity: high
social opportunity: low
relationship pressure: neutral
plan focus: medium

Result: buy cheap Maple Claw and resume grinding.
```

## Mental Model

Agent behavior should follow this model:

```text
Agent is not choosing actions randomly.

Agent has:
  a long-term build,
  a personality,
  a current mood,
  memories,
  hard limits,
  current plans,
  and live server context.

Those combine into intention.

Intention becomes a plan or objective.

Objective activates capabilities.

Capability result updates mood, memory, plan progress, and decision journal.
```

The useful separation is:

```text
Profile decides preference.
Plan card decides objective.
Capability executes behavior.
Server adapter validates reality.
Decision journal records why.
```

## Decision Journal

The decision journal is an append-only profile history for studying the agent's
lifecycle. It should record strategic choices, not every movement packet.

Each entry should have two layers:

- `overview`: short human-readable summary for LLM, dashboards, and lifecycle
  review.
- `details`: structured facts, scored influences, alternatives, and outcome for
  debugging and later analytics.

Example:

```json
{
  "decisionId": "agent-123-2035-maple-claw",
  "agentId": 123,
  "timestamp": 123456789,
  "decisionKind": "equipment-acquisition",
  "intention": "prepare-dexless-assassin-weapon",
  "chosenAction": "buy-cheap-market-item",
  "overview": {
    "title": "Bought cheap Maple Claw for dexless build",
    "summary": "Agent is dexless and wants Maple Claw for level 35. Market had a cheap below-average claw within budget, so agent bought it and used 100% scrolls.",
    "mainReasons": [
      "dexless build needs a DEX-free claw",
      "agent is not picky about weapon stats",
      "farming had no drop after 10 hours",
      "market price was within budget"
    ],
    "confidence": 0.84
  },
  "details": {
    "context": {
      "level": 35,
      "mesos": 820000,
      "targetItemId": 1472030,
      "build": "dexless-assassin",
      "budget": 500000,
      "marketLowestPrice": 280000,
      "dropSourcesKnown": true,
      "farmDurationMs": 36000000,
      "dryStreak": true
    },
    "influenceBreakdown": [
      {
        "source": "build",
        "key": "avoidDexRequiredWeapons",
        "value": true,
        "direction": "toward-buy-or-farm-maple-claw",
        "weight": 0.95,
        "effect": "requires dexless-compatible claw"
      },
      {
        "source": "profile",
        "key": "equipmentPickiness",
        "value": 0.25,
        "direction": "toward-cheap-low-stat-item",
        "weight": 0.60,
        "effect": "accept below-average stats if cheap"
      },
      {
        "source": "memory",
        "key": "farmDryStreak",
        "value": "10-hours-no-drop",
        "direction": "away-from-continued-farming",
        "weight": 0.70,
        "effect": "prefer buying after long unsuccessful farming"
      },
      {
        "source": "economy",
        "key": "marketPriceWithinBudget",
        "value": true,
        "direction": "toward-buying",
        "weight": 0.80,
        "effect": "buy instead of continue farming"
      }
    ],
    "alternativesConsidered": [
      {
        "action": "continue-farming",
        "score": 0.42,
        "rejectedBecause": ["long-dry-streak", "cheap-market-option-found"]
      },
      {
        "action": "buy-well-scrolled-claw",
        "score": 0.18,
        "rejectedBecause": ["over-budget", "low-equipment-pickiness"]
      }
    ],
    "result": {
      "planId": "acquire-maple-claw",
      "objectiveId": "buy-maple-claw",
      "outcome": "completed",
      "itemsChanged": [1472030],
      "mesosDelta": -280000
    }
  }
}
```

`overview` should be concise enough for:

- LLM context summaries.
- admin/debug dashboards.
- agent lifecycle timelines.
- human review of why an agent behaved a certain way.

`details.influenceBreakdown` should be structured enough for:

- tuning profile traits.
- debugging bad decisions.
- economy balancing.
- comparing agents with different personalities.
- replaying why a plan objective was selected or rejected.

Retention recommendation:

- keep full journal for major decisions permanently or for long-term analysis.
- compact repeated low-value choices into summaries.
- keep raw event/tick data out of the profile journal.
- link journal entries to plan ids, objective ids, market observations, and
  profile version.

## Dynamic Mood

Mood changes during play and should decay toward baseline.

```json
{
  "mood": {
    "energy": 0.75,
    "boredom": 0.20,
    "frustration": 0.05,
    "confidence": 0.62,
    "urgency": 0.30,
    "socialOpenness": 0.22
  }
}
```

Events that affect mood:

- repeated path failure increases frustration
- rare drop increases confidence
- death decreases confidence and energy
- crowded map increases or decreases comfort depending on socialness
- long repeated farming increases boredom
- successful market flip increases greed/confidence

## Behavior Archetypes

Use archetypes as starting templates, then add per-agent variation.

### Careful Quester

- High caution.
- Medium patience.
- Avoids maps above safe level range.
- Reads NPC dialogue longer.
- Completes quest chains before grinding.

### Efficient Grinder

- High efficiency.
- Low patience.
- Chooses high EXP routes.
- Leaves inefficient quests.
- Buys supplies quickly.

### Market Scout

- High curiosity.
- High patience.
- Regularly scans FM.
- Records prices.
- Rarely buys unless price confidence is high.

### Opportunistic Trader

- High greed.
- Medium risk tolerance.
- Buys underpriced items.
- Lists items with profit margin.
- Avoids tying up all mesos.

### Social Helper

- High socialness.
- Responds to nearby activity.
- Assists party members.
- Slower solo progression.

### Stubborn Farmer

- High stubbornness.
- Low exploration.
- Continues farming until inventory pressure or supply shortage.
- May ignore better alternatives unless LLM redirects.

## Human-Like Variation Rules

Use policies instead of raw exact behavior.

Good:

```text
npcApproachStyle = random-nearby
dialogueDelayProfile = normal-reader
routePreference = safe
shopScanStyle = selective
```

Avoid:

```text
stand at x=523
wait exactly 1732ms
repeat same route every time
```

## Anti-Cloning Controls

Each agent should have stable seeds:

```text
identitySeed
routeSeed
timingSeed
marketSeed
socialSeed
```

Use stable seeded randomness for:

- NPC approach point selection
- route tie-breakers
- channel selection
- FM room scan order
- farming map alternatives
- dialogue delay ranges
- micro-pauses
- task switching

## Surprise Model

Surprises should be bounded and explainable.

Types:

- `curiosity-detour`: checks nearby NPC/map/shop
- `market-opportunity`: changes plan for underpriced item
- `supply-panic`: interrupts task to buy potions
- `boredom-switch`: rotates farming map
- `social-pause`: pauses in town or responds to event
- `risk-retreat`: leaves dangerous map after bad outcome
- `rare-drop-shift`: sells/stores/plans around valuable drop

Each surprise needs:

```json
{
  "type": "market-opportunity",
  "trigger": "priceBelowMedian",
  "probability": 0.12,
  "cooldownMs": 1800000,
  "maxDurationMs": 600000,
  "allowedProfiles": ["market-scout", "opportunistic-trader"]
}
```

## Profile Update Rules

The LLM may update profile policy, role, or short-term goals. It should not
rewrite core identity constantly.

Allowed LLM updates:

- current role
- active goal
- risk tolerance within policy bounds
- economy budget within policy bounds
- temporary mood modifiers
- preferred task style

Engine-owned updates:

- current state
- cooldowns
- exact delays
- exact movement choices
- live combat decisions
- task execution progress
