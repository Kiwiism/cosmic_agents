# Profile Decision API

The Profile Decision API lets Agent engine and LLM tooling ask how an agent is
likely to behave.

Profile updates should be fed through portable experience events and bounded
patches as specified in
`docs/agents/profile-platform/PROFILE_ADAPTATION_SYSTEM.md`.

## Service Shape

```java
public interface AgentProfileRuntime {
    AgentProfileView getProfile(int agentId);
    AgentMoodView getMood(int agentId);
    AgentPolicyView getPolicy(int agentId);

    ProfileDecision decideNpcInteraction(ProfileDecisionContext context);
    ProfileDecision decideRoutePreference(ProfileDecisionContext context);
    ProfileDecision decideCombatRisk(ProfileDecisionContext context);
    ProfileDecision decideMarketAction(ProfileDecisionContext context);
    ProfileDecision decideEquipmentAcquisition(ProfileDecisionContext context);
    ProfileDecision decideBuildProgression(ProfileDecisionContext context);
    ProfileDecision decideRelationshipAction(ProfileDecisionContext context);
    ProfileDecision decideTradeCounterparty(ProfileDecisionContext context);
    ProfileDecision decidePartyOrHelpRequest(ProfileDecisionContext context);
    ProfileDecision decideTaskFit(ProfileDecisionContext context);
    ProfileDecision decideMicroBehavior(ProfileDecisionContext context);

    void recordEvent(ProfileEvent event);
    void recordExperienceEvent(AgentExperienceEvent event);
    void recordDecision(ProfileDecisionRecord decision);
    void recordRelationshipEvent(RelationshipEvent event);
    List<ProfilePatch> previewAdaptation(AgentExperienceEvent event);
    void applyProfilePatch(ProfilePatch patch);
    void updateRole(ProfileRoleUpdate update);
    void updatePolicy(ProfilePolicyPatch patch);
}
```

## Decision Context

```json
{
  "agentId": 123,
  "decisionKind": "npc-interaction",
  "task": {
    "kind": "equipment-acquisition",
    "targetItemId": 1472030,
    "priority": "normal"
  },
  "buildIntent": {
    "targetJobPath": ["beginner", "thief", "assassin"],
    "statBuild": "dexless",
    "baseDexTarget": 25,
    "avoidDexRequiredWeapons": true
  },
  "location": {
    "mapId": 100000000
  },
  "catalog": {
    "risk": "safe",
    "confidence": "generated",
    "itemRequirements": {
      "requiredDex": 0,
      "requiredLevel": 35
    },
    "knownDropSources": [9300102]
  },
  "live": {
    "crowding": "medium",
    "danger": "low",
    "mesos": 820000,
    "marketLowestPrice": 280000
  },
  "memory": {
    "recentFailureCount": 0,
    "farmDurationMs": 36000000,
    "targetDropSeen": false
  },
  "relationship": {
    "targetType": "player",
    "targetId": "character-456",
    "familiarity": 0.72,
    "trust": 0.81,
    "affinity": 0.64,
    "tradeReliability": 0.90,
    "partyCompatibility": 0.75,
    "annoyance": 0.10,
    "tags": ["fair-trader", "helped-with-quest"]
  }
}
```

## Decision Result

```json
{
  "kind": "npc-interaction",
  "status": "allowed",
  "style": "careful",
  "delayMsRange": [1400, 4200],
  "approachPreference": "random-nearby",
  "riskTolerance": "medium",
  "chosenAction": "buy-cheap-market-item",
  "summary": "Dexless assassin build needs Maple Claw at level 35; market listing is within budget after a long farming dry streak.",
  "reasons": [
    "dexless-build-target",
    "market-price-within-budget",
    "low-equipment-pickiness",
    "farm-dry-streak"
  ],
  "influences": [
    {
      "source": "build",
      "key": "avoidDexRequiredWeapons",
      "weight": 0.95
    },
    {
      "source": "economy",
      "key": "marketLowestPrice",
      "weight": 0.80
    },
    {
      "source": "memory",
      "key": "farmDurationMs",
      "weight": 0.70
    }
  ],
  "confidence": 0.84,
  "shouldRecordDecision": true
}
```

Statuses:

- `allowed`
- `discouraged`
- `blocked-by-policy`
- `needs-llm-review`
- `needs-engine-validation`

## Common Decisions

### NPC Interaction

Outputs:

- delay range
- approach style
- whether to allow lower/higher platform interaction
- retry tolerance
- script risk tolerance

### Route Preference

Outputs:

- shortest
- safest
- familiar
- low-crowd
- exploratory
- avoid-recent-failures

### Combat Risk

Outputs:

- max accepted danger
- potion threshold tendency
- retreat probability
- mob target preference
- grind persistence

### Market Action

Outputs:

- buy/sell/hold preference
- max spend pressure
- desired profit margin
- scan patience
- speculation tolerance

### Relationship Action

Outputs:

- accept/reject/ignore social interaction.
- willingness to help.
- willingness to sidetrack current plan.
- map/channel avoidance tendency.
- social response style.
- relationship reason codes.

### Trade Counterparty

Outputs:

- trade trust adjustment.
- whether LLM/human review is needed.
- acceptable value/risk range.
- discount or generosity tendency.
- buyer/seller reliability.
- scam/abuse suspicion reason codes.

### Party Or Help Request

Outputs:

- accept party invite.
- assist with combat.
- assist with loot/farming.
- provide supplies.
- postpone own plan.
- reject due to focus/risk/history.

### Equipment Acquisition

Outputs:

- buy/farm/craft/scroll/postpone preference
- target quality tolerance
- max spend from profile and policy
- willingness to accept below-average stats
- farm persistence before buying
- substitute item preference
- scroll strategy preference
- reason codes and weighted influences

### Build Progression

Outputs:

- target job path
- stat build constraints
- skill/equipment priorities
- plan urgency
- allowed deviations
- when to request LLM review

### Micro Behavior

Outputs:

- micro-pause chance
- idle town pause style
- social response likelihood
- channel-switch preference
- boredom detour chance

## Decision Record

The profile runtime should support compact strategic decision records. These are
for lifecycle study and debugging, not tick-level telemetry.

Each record should split readable explanation from structured diagnostics:

- `overview`: short title, summary, main reasons, confidence.
- `details`: context, influence breakdown, alternatives, and final result.

```json
{
  "decisionId": "agent-123-2035-maple-claw",
  "agentId": 123,
  "profileVersion": 7,
  "timestamp": 123456789,
  "decisionKind": "equipment-acquisition",
  "planId": "acquire-maple-claw",
  "objectiveId": "choose-acquisition-method",
  "intention": "prepare-dexless-assassin-weapon",
  "chosenAction": "buy-cheap-market-item",
  "overview": {
    "title": "Bought cheap Maple Claw for dexless assassin build",
    "summary": "Agent bought a cheap below-average Maple Claw because the dexless build needed it, the agent was not picky, and farming had produced no drop after 10 hours.",
    "mainReasons": [
      "dexless-build-target",
      "cheap-market-listing",
      "low-equipment-pickiness",
      "farm-dry-streak"
    ],
    "confidence": 0.84
  },
  "details": {
    "context": {
      "level": 35,
      "mesos": 820000,
      "budget": 500000,
      "targetItemId": 1472030
    },
    "influenceBreakdown": [
      {
        "source": "build",
        "key": "baseDexTarget",
        "value": 25,
        "direction": "toward-dexless-compatible-weapon",
        "weight": 0.95,
        "effect": "normal DEX-required claws are poor fit"
      },
      {
        "source": "profile",
        "key": "equipmentPickiness",
        "value": 0.25,
        "direction": "toward-cheap-low-stat-item",
        "weight": 0.60,
        "effect": "agent accepts below-average stats"
      },
      {
        "source": "economy",
        "key": "marketLowestPrice",
        "value": 280000,
        "direction": "toward-buying",
        "weight": 0.80,
        "effect": "listing is within purchase budget"
      },
      {
        "source": "memory",
        "key": "farmDurationMs",
        "value": 36000000,
        "direction": "away-from-continued-farming",
        "weight": 0.70,
        "effect": "long dry streak makes buying more attractive"
      }
    ],
    "alternativesConsidered": [
      {
        "action": "continue-farming",
        "score": 0.42,
        "rejectedBecause": ["long-dry-streak", "cheap-market-option-found"]
      },
      {
        "action": "buy-well-scrolled-item",
        "score": 0.18,
        "rejectedBecause": ["over-budget", "low-pickiness"]
      }
    ],
    "result": {
      "status": "completed",
      "mesosDelta": -280000,
      "itemsChanged": [1472030]
    }
  }
}
```

Recommended record triggers:

- build goal created or changed.
- major equipment target chosen.
- buy/farm/craft/scroll decision made.
- farming attempt abandoned after a threshold.
- expensive purchase accepted or rejected.
- social/economy opportunity interrupts active plan.
- repeated failures change profile preference.

## Deterministic Randomness

For reproducible but non-cloned behavior:

```text
decisionSeed = agent seed + decision kind + task key + attempt + time bucket
```

Use time buckets instead of current milliseconds for strategic decisions so
agents remain stable during one planning window.

## LLM Access

The LLM should use summarized views:

```json
{
  "agentId": 123,
  "archetype": "careful-quester",
  "currentRole": "quester",
  "moodSummary": "calm but slightly bored",
  "bestTaskTypes": ["quest-chain", "safe-farming"],
  "avoid": ["high-death-risk", "large-market-spend"]
}
```

The LLM may request role/profile patches, but policy limits remain enforced by
the profile runtime and Agent engine.
