# Quest Objective Policy Technical Specification

Purpose:

```text
Define the portable models, interfaces, policy algorithms, reason codes, and
tests for objective-focused quest combat, loot, reactor, and recovery decisions.
```

This specification is for post-reconstruction implementation. It should not be
implemented against live Agent runtime files until the reconstructed Plan
Runtime and Capability Runtime are stable.

## Suggested Package Layout

```text
agent-quest-objective-policy/
  api/
    QuestObjectivePolicyService
    ObjectiveFocusStateStore
    QuestObjectiveSnapshotProvider
    MobTargetPolicy
    FutureQuestLootPolicy
    SpawnPressurePolicy
  model/
    ObjectiveFocusState
    ObjectiveFocusMode
    QuestObjectivePolicyRequest
    QuestObjectivePolicyDecision
    ObjectiveExitCriteria
    ObjectiveLiveSnapshot
    MobCandidate
    LootCandidate
    ReactorCandidate
    SpawnPressureSnapshot
    TacticalRecommendation
    QuestObjectiveReasonCode
  runtime/
    DefaultQuestObjectivePolicyService
    DeterministicQuestObjectivePolicy
    AdaptiveQuestObjectivePolicy
    SpawnPressureEvaluator
    FutureLootEvaluator
    MobUtilityScorer
    ObjectiveExitCriteriaEvaluator
  audit/
    QuestObjectivePolicyAuditEvent
    QuestObjectivePolicyJournal
```

## Service Contract

### QuestObjectivePolicyService

```text
QuestObjectivePolicyDecision evaluate(QuestObjectivePolicyRequest request)
```

Responsibilities:

- reconcile live state against objective exit criteria.
- determine whether the objective is already complete.
- select current primary target when available.
- evaluate spawn pressure.
- select bounded filler target when allowed.
- emit tactical recommendation and reason codes.
- never execute the recommendation directly.

## Main Request Model

```json
{
  "agentId": 123,
  "planId": "maple-island-mvp",
  "objectiveId": "quest-1037-kill-snails",
  "mode": "DETERMINISTIC",
  "activeQuestId": 1037,
  "exitCriteria": {},
  "allowedPolicies": ["CURRENT_OBJECTIVE_ONLY"],
  "liveSnapshot": {},
  "catalogSnapshot": {},
  "profileHints": {},
  "retryState": {}
}
```

## Main Decision Model

```json
{
  "status": "RECOMMEND_ACTION",
  "recommendation": {
    "type": "ATTACK_MOB",
    "mobObjectId": 98231,
    "mobId": 100100,
    "priority": "PRIMARY_OBJECTIVE_TARGET"
  },
  "reasonCodes": ["PRIMARY_TARGET_AVAILABLE"],
  "audit": {}
}
```

Recommended statuses:

- `OBJECTIVE_COMPLETE`
- `RECOMMEND_ACTION`
- `RECOMMEND_REPOSITION`
- `RECOMMEND_WAIT`
- `YIELD_TO_RECOVERY`
- `BLOCKED`
- `POSTPONED`

## Objective Focus State

```json
{
  "agentId": 123,
  "planId": "maple-island-mvp",
  "objectiveId": "quest-1037-kill-snails",
  "focusMode": "OBJECTIVE_FOCUS",
  "focusLevel": "HIGH",
  "primaryQuestId": 1037,
  "startedAtMs": 0,
  "lastProgressAtMs": 0,
  "retryCount": 0,
  "fillerBurstCount": 0,
  "lastPrimaryCheckAtMs": 0,
  "lastDecisionReasonCodes": []
}
```

Focus modes:

- `PLAN_FOCUS`
- `OBJECTIVE_FOCUS`
- `TACTICAL_SUBTASK`
- `RECOVERY_FOCUS`
- `SIDETRACK_FOCUS`
- `BLOCKED`

## Live Snapshot Inputs

The policy should receive a bounded snapshot instead of reading server
collections directly.

```json
{
  "mapId": 20000,
  "questStates": {},
  "inventoryCounts": {},
  "freeInventorySlots": {},
  "liveMobs": [],
  "liveMobCountsById": {},
  "liveTotalMobCount": 0,
  "agentHpRatio": 1.0,
  "agentMpRatio": 1.0,
  "agentPosition": {"x": 0, "y": 0},
  "dangerSnapshot": {}
}
```

## Catalog Snapshot Inputs

```json
{
  "expectedMobCountsById": {},
  "expectedTotalMobCount": 0,
  "questRequiredMobIds": [],
  "questRequiredItemIds": [],
  "itemDropSources": {},
  "futureQuestItems": {},
  "reactorRequirements": [],
  "mapRisk": {}
}
```

## Objective Exit Criteria Evaluation

Evaluation order:

1. live quest state says requirement complete.
2. live inventory state says required count exists.
3. live item-use state confirms item used.
4. live reactor state or inventory confirms reactor requirement done.
5. plan policy says objective skipped.
6. retry policy says blocked/postponed.

Live state wins over cached focus state.

## Deterministic Algorithm

Use for first Maple Island MVP run.

```text
evaluate(request):
  if exitCriteriaSatisfied(liveSnapshot):
    return OBJECTIVE_COMPLETE

  if recoveryNeeded(liveSnapshot):
    return YIELD_TO_RECOVERY

  primary = findPrimaryObjectiveCandidate()
  if primary exists:
    return ATTACK_OR_LOOT_PRIMARY

  if retryBudgetAvailable:
    return RECOMMEND_REPOSITION_OR_WAIT

  return BLOCKED_PRIMARY_TARGET_MISSING
```

No filler clearing and no future-loot priority.

## Adaptive Algorithm

Use after deterministic MVP is stable.

```text
evaluate(request):
  if exitCriteriaSatisfied(liveSnapshot):
    return OBJECTIVE_COMPLETE

  if recoveryNeeded(liveSnapshot):
    return YIELD_TO_RECOVERY

  primary = findPrimaryObjectiveCandidate()
  if primary exists:
    resetFillerBurstIfNeeded()
    return ATTACK_OR_LOOT_PRIMARY

  pressure = evaluateSpawnPressure()
  if pressure.active and fillerBurstRemaining:
    filler = chooseFillerCandidate()
    if filler exists:
      incrementFillerBurst()
      return ATTACK_FILLER_WITH_REASON

  if retryBudgetAvailable:
    return RECOMMEND_REPOSITION_OR_WAIT

  return BLOCKED_OR_POSTPONED
```

## Spawn Pressure Evaluation

Inputs:

- `expectedTargetCount`
- `liveTargetCount`
- `expectedTotalMobCount`
- `liveTotalMobCount`
- `targetLowRatio`
- `spawnCloggedRatio`

Formula:

```text
targetLow =
  liveTargetCount <= max(1, floor(expectedTargetCount * targetLowRatio))

spawnClogged =
  liveTotalMobCount >= floor(expectedTotalMobCount * spawnCloggedRatio)

spawnPressureActive = targetLow && spawnClogged
```

If expected spawn data is missing:

- deterministic mode should ignore spawn-pressure clearing.
- adaptive mode should return `CATALOG_MISSING_SPAWN_DATA` and avoid filler
  clearing unless the objective explicitly permits heuristic fallback.

## Filler Candidate Scoring

Recommended scoring:

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

Maple Island adaptive minimum:

```text
score =
  futureQuestLootValue
  - distanceCost
  - dangerCost
```

Hard filters:

- mob is too dangerous.
- mob is unreachable.
- profile forbids it.
- plan forbids filler clearing.
- filler burst limit reached.

## Future Quest Loot Evaluation

Future quest item can influence scoring when:

- item is needed by selected future objective.
- item is prelootable.
- inventory has free space.
- item is not protected/forbidden in a conflicting way.

Future quest item cannot count toward future objective when:

- item is quest-active-only.
- live quest state is not active.
- server does not count the item yet.

## Config Contract

Future config should be controlled by Agent runtime config, not global server
defaults until the package is implemented.

```yaml
agents:
  questCombat:
    mode: "DETERMINISTIC"
    enableSpawnPressureClearing: false
    enableFutureQuestLootPriority: false
    targetLowRatio: 0.30
    spawnCloggedRatio: 0.75
    fillerMobBurstLimit: 3
    retargetPrimaryCheckMs: 1500
    noProgressTimeoutMs: 60000
```

Do not change `config.yaml` during pre-reconstruction prep.

## Reason Codes

Recommended enum:

```text
OBJECTIVE_ALREADY_SATISFIED
PRIMARY_TARGET_AVAILABLE
PRIMARY_TARGET_MISSING
PRIMARY_DROP_SOURCE_AVAILABLE
PRIMARY_DROP_SOURCE_MISSING
SPAWN_PRESSURE_CLEARING_DISABLED
SPAWN_PRESSURE_ACTIVE
SPAWN_PRESSURE_INACTIVE
NO_SAFE_FILLER_TARGET
FILLER_TARGET_SELECTED
FILLER_BURST_LIMIT_REACHED
FUTURE_LOOT_DISABLED
FUTURE_LOOT_SELECTED
FUTURE_LOOT_NOT_PRELOOTABLE
QUEST_ACTIVE_ONLY_DROP
INVENTORY_PRESSURE
DANGER_TOO_HIGH
NO_PROGRESS_TIMEOUT
LIVE_STATE_MISMATCH
CATALOG_MISSING_SPAWN_DATA
POSTPONE_LOW_RESOURCES
BLOCKED_CAPABILITY_MISSING
YIELD_TO_RECOVERY
```

## Audit Event

```json
{
  "eventType": "QUEST_OBJECTIVE_POLICY_DECISION",
  "agentId": 123,
  "planId": "maple-island-mvp",
  "objectiveId": "quest-1037-kill-snails",
  "mode": "ADAPTIVE",
  "decisionStatus": "RECOMMEND_ACTION",
  "recommendationType": "ATTACK_MOB",
  "primaryMobId": 100100,
  "selectedMobId": 100101,
  "reasonCodes": ["SPAWN_PRESSURE_ACTIVE", "FILLER_TARGET_SELECTED"],
  "liveTargetCount": 0,
  "expectedTargetCount": 4,
  "liveTotalMobCount": 12,
  "expectedTotalMobCount": 14,
  "fillerBurstCount": 1
}
```

## Tests

### Unit Tests

- focus state enters `OBJECTIVE_FOCUS` when objective starts.
- focus state exits when live quest requirement is satisfied.
- persisted objective state does not override live quest state.
- deterministic mode disables filler clearing.
- deterministic mode disables future-loot priority.
- spawn pressure false when primary count is healthy.
- spawn pressure true when primary count is low and map is clogged.
- missing catalog spawn data blocks adaptive filler clearing.
- filler burst limit returns control to primary checks.
- filler scoring prefers safe future quest value.
- quest-active-only drops are not counted as prelootable.
- danger filter rejects unsafe filler mobs.
- no-progress timeout returns blocker/postpone reason.

### Integration Tests

- Agent completes a kill quest in a mixed-mob map.
- Agent clears filler mobs only when primary target is low.
- Agent returns to primary target as soon as it respawns.
- Agent stops combat immediately when objective is complete.
- Agent does not grind indefinitely after objective completion.
- Maple Island deterministic policy produces no filler recommendations.

### Replay Tests

- same live snapshot and same focus state produce same decision.
- audit events reconstruct policy decision sequence.
- blocker reason is stable and explainable.

## Implementation Gates

Requires:

- Plan Runtime objective state.
- Capability Runtime command/result model.
- Catalog Runtime spawn and item indexes.
- live map snapshot provider from Server Adapter.
- Recovery Policy reason-code contract.
- Observability audit sink.
- reconstructed Agent runtime entry points.

## Safe Pre-Reconstruction Work

Allowed now:

- maintain this specification.
- maintain `docs/agents/QUEST_FOCUS_AND_COMBAT_POLICY.md`.
- add offline catalog fields needed by the policy.
- add sample JSON schema drafts.

Not allowed now:

- live Agent combat targeting changes.
- live Agent loot priority changes.
- live focus-state runtime changes.
- edits to `src/main/java/server/agents`.
- edits to `src/main/java/server/bots`.
