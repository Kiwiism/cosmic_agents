# Quest Focus And Combat Policy

This document defines how an Agent should stay focused on quest objectives while
still behaving intelligently in mixed-mob maps.

The key rule is:

```text
Plan Card says what objective to finish.
Focus state keeps the Agent committed until exit criteria.
Combat policy decides tactical target choices while pursuing that objective.
```

Do not put tactical mob-clearing details directly into the Plan Card.

## Focus State

When an Agent starts a plan or objective, the runtime should enter a focus state.

Suggested stack:

```text
Plan Focus
  -> Objective Focus
  -> Capability Execution
  -> Tactical Subpolicy
  -> Objective done / blocked / postponed
```

Example:

```text
Plan Focus:
  maple-island-mvp

Objective Focus:
  complete quest requirement for quest 1037

Capability Execution:
  navigate to target map
  combat quest mobs
  loot required drops

Tactical Subpolicy:
  if target mob count is too low, clear useful filler mobs
```

The focus state should be explicit runtime state, not just an implied loop.

## Focus State Data

Suggested model:

```json
{
  "agentId": 123,
  "planId": "maple-island-mvp",
  "focusState": {
    "mode": "OBJECTIVE_FOCUS",
    "focusLevel": "high",
    "currentObjectiveId": "quest-1037-kill-snails",
    "primaryQuestId": 1037,
    "allowedCapabilities": ["navigation", "combat", "loot", "inventory", "recovery"],
    "allowedTacticalPolicies": ["spawn-pressure-clearing", "future-quest-loot"],
    "startedAt": 0,
    "lastProgressAt": 0,
    "retryCount": 0
  }
}
```

Recommended focus modes:

```text
PLAN_FOCUS
  Agent is following the active plan.

OBJECTIVE_FOCUS
  Agent is trying to complete one current objective.

TACTICAL_SUBTASK
  Agent temporarily does supporting work for the objective, such as clearing
  filler mobs or looting a future-use item.

RECOVERY_FOCUS
  Agent is recovering from stuck movement, death, missing mobs, or inventory
  pressure.

SIDETRACK_FOCUS
  Agent temporarily follows an approved sidetrack plan.

BLOCKED
  Agent cannot make safe progress without replan/manual/LLM intervention.
```

## Objective Exit Criteria

Each objective must define machine-checkable exit criteria.

Examples:

```json
{
  "objectiveId": "quest-1037-kill-snails",
  "exitCriteria": {
    "type": "quest-requirement-satisfied",
    "questId": 1037,
    "requirement": "mob-or-item-requirement"
  }
}
```

```json
{
  "objectiveId": "quest-1021-use-apple",
  "exitCriteria": {
    "type": "item-used",
    "itemId": 2010007,
    "questId": 1021
  }
}
```

Focus ends when:

- live quest state says the requirement is satisfied.
- live inventory state says the required item quantity exists.
- the objective is completed by quest API.
- the objective is explicitly skipped by plan policy.
- the objective is blocked after retry policy.
- the plan is cancelled or superseded.

Persisted state is advisory. Live server state wins.

## Focus Interruptions

Not every interruption should break the plan.

Allowed while focused:

- emergency recovery.
- potion/safety handling.
- stuck-path recovery.
- short tactical mob clearing that helps the objective.
- loot collection for current objective.
- loot collection for future selected quests when safe.

Blocked while focused for Maple Island MVP:

- market scanning.
- sell-trash.
- optional social sidetracks.
- Shanks travel/off-island movement.
- unrelated exploration.

## Quest-Aware Combat Target Policy

For quest objectives, combat should use a target policy rather than a hardcoded
single-mob loop.

Priority order:

```text
1. Current quest target mob.
2. Current quest drop source mob.
3. Spawn-pressure filler mob with future quest value.
4. Spawn-pressure filler mob with useful supply/meso/EXP value.
5. Safe nearby filler mob.
6. Wait/reposition/retry if no useful target exists.
```

The Agent should mostly kill the current quest target. But if the target mob
becomes scarce because the map is full of other mobs, the Agent may clear other
mobs to let spawns cycle.

## Spawn Pressure Detection

Use catalog expected spawn data plus live map state.

Inputs:

```text
expectedTargetCount
liveTargetCount
expectedTotalMobCount
liveTotalMobCount
targetLowRatio
spawnCloggedRatio
```

Recommended defaults:

```yaml
agents:
  questCombat:
    enableSpawnPressureClearing: true
    enableFutureQuestLootPriority: true
    targetLowRatio: 0.30
    spawnCloggedRatio: 0.75
    fillerMobBurstLimit: 3
    retargetPrimaryCheckMs: 1500
```

Definitions:

```text
targetLow =
  liveTargetCount <= max(1, floor(expectedTargetCount * targetLowRatio))

spawnClogged =
  liveTotalMobCount >= floor(expectedTotalMobCount * spawnCloggedRatio)
```

Policy:

```text
if target mob is available:
  kill target mob

else if targetLow and spawnClogged:
  kill best filler mob for a short burst
  then re-check primary target

else:
  reposition/search/wait briefly
```

The `fillerMobBurstLimit` prevents an Agent from accidentally turning a quest
objective into generic grinding.

## Future Quest Loot Policy

When clearing filler mobs, prefer mobs that help later selected quests.

Important split:

```text
prelootable
  normal drops or etc items obtainable before the quest starts

quest-active-only
  quest drops that only drop/count while the quest is active
```

The Agent may collect `prelootable` future quest items if inventory pressure is
safe.

The Agent should not assume `quest-active-only` drops can be farmed before the
quest starts. Those mobs may still be useful as spawn-clearing targets, but the
loot should not be counted toward the future objective until live state confirms
it.

Catalog should expose:

```json
{
  "itemId": 4000000,
  "futureQuestIds": [1037],
  "prelootable": true,
  "dropSources": [
    {
      "mobId": 100100,
      "mapIds": [50000],
      "questId": 0
    }
  ]
}
```

For quest-only drops:

```json
{
  "itemId": 4031161,
  "futureQuestIds": [1008],
  "prelootable": false,
  "dropSources": [
    {
      "sourceType": "reactor",
      "questId": 1008
    }
  ]
}
```

## Filler Mob Scoring

When spawn pressure says to clear filler mobs, score candidates.

Suggested scoring inputs:

```text
futureQuestLootValue
currentQuestDropValue
distanceCost
dangerCost
timeToKillCost
expValue
mesoOrSupplyValue
inventoryPressureCost
crowdingCost
```

Example formula:

```text
score =
  currentQuestDropValue * 100
  + futureQuestLootValue * 60
  + expValue * 5
  + mesoOrSupplyValue * 4
  - distanceCost * 8
  - dangerCost * 50
  - timeToKillCost * 6
  - inventoryPressureCost * 30
```

For Maple Island MVP, keep the first implementation simple:

```text
score =
  futureQuestLootValue
  - distanceCost
  - dangerCost
```

Only add richer economy/EXP scoring after the deterministic questline works.

## Combat Execution Loop

Pseudo-flow:

```text
while objective not complete:
  refresh live quest and inventory state
  if objective satisfied:
    return SUCCESS

  refresh live map mob counts
  primary = find current quest target mob

  if primary exists:
    attack primary
    continue

  pressure = evaluate spawn pressure
  if pressure.targetLow and pressure.spawnClogged:
    filler = choose best filler mob
    if filler exists:
      attack filler for bounded burst
      continue

  if retry budget remains:
    reposition or wait briefly
    continue

  return BLOCKED_MISSING_TARGET
```

Every filler kill should still be attached to the current objective audit:

```json
{
  "objectiveId": "quest-1037-kill-snails",
  "combatMode": "spawn-pressure-filler",
  "primaryMobId": 100100,
  "fillerMobId": 100101,
  "reason": "primary-low-map-clogged-future-quest-value",
  "liveTargetCount": 0,
  "expectedTargetCount": 4,
  "liveTotalMobCount": 12,
  "expectedTotalMobCount": 14
}
```

## Toggle Modes

For deterministic tests:

```yaml
agents:
  questCombat:
    enableSpawnPressureClearing: false
    enableFutureQuestLootPriority: false
```

For production-like behavior:

```yaml
agents:
  questCombat:
    enableSpawnPressureClearing: true
    enableFutureQuestLootPriority: true
    targetLowRatio: 0.30
    spawnCloggedRatio: 0.75
    fillerMobBurstLimit: 3
    retargetPrimaryCheckMs: 1500
```

## Required Runtime Classes

Recommended names:

```text
AgentObjectiveFocusState
AgentObjectiveFocusStore
AgentObjectiveExitCriteria
AgentQuestCombatPolicy
AgentSpawnPressureTargetPolicy
AgentFutureQuestLootPolicy
AgentMobUtilityScorer
AgentQuestCombatAudit
```

These should sit under the Agent runtime/capability layer after reconstruction.

## Tests

Unit tests:

- focus state enters `OBJECTIVE_FOCUS` when objective starts.
- focus state exits when live quest requirement is satisfied.
- persisted objective completion does not override live quest state.
- spawn pressure is false when primary mob count is healthy.
- spawn pressure is true when primary mob count is low and map is clogged.
- filler mob selection prefers future quest loot when safe.
- quest-active-only drops are not counted as prelootable.
- filler burst limit returns control to primary target checks.
- deterministic mode disables filler clearing.

Integration tests:

- Agent completes a kill quest in a mixed-mob map.
- Agent clears filler mobs only when primary target is low.
- Agent returns to primary mob as soon as it respawns.
- Agent stops combat immediately when quest objective is complete.
- Agent does not grind indefinitely after objective completion.

## Maple Island MVP Recommendation

For the very first green run:

```yaml
agents:
  questCombat:
    enableSpawnPressureClearing: false
    enableFutureQuestLootPriority: false
```

After the deterministic route works, enable:

```yaml
agents:
  questCombat:
    enableSpawnPressureClearing: true
    enableFutureQuestLootPriority: true
```

That gives two phases:

```text
Phase 1:
  prove the questline sequence works

Phase 2:
  make combat adaptive and less robotic
```
