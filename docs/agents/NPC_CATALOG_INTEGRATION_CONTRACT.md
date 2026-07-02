# NPC Catalog Integration Contract

This contract is for the future Agent NPC capability. It defines how runtime code
should consume the offline catalog without trusting it blindly.

The catalog is advisory metadata. Runtime validators decide whether an action is
allowed right now.

## Lookup Request

Future runtime code should query the catalog with:

```text
agentId
mapId
npcId
interactionType
questId or shopId when applicable
attempt
```

Optional inputs:

```text
preferredPlacementKey
agentProfile
previousFailedApproachPoints
presentationMode
validationOnly
budgetPolicy
protectedItemPolicy
```

## Lookup Response

The catalog repository should return:

```text
npcId
npcName
mapId
placement key: mapId + lifeIndex + npcId
interaction types
automation confidence
do-not-auto-use flag
review reasons
interaction box
candidate approach points
dialogue timing ranges
source files
fast index ids used
```

## Command Shape

The future NPC capability should receive typed commands, not raw script calls:

```json
{
  "agentId": 123,
  "planId": "maple-island-mvp",
  "objectiveId": "quest-1000-start",
  "actionType": "quest-start",
  "mapId": 100000000,
  "npcId": 1012100,
  "questId": 1000,
  "shopId": null,
  "itemId": null,
  "quantity": null,
  "attempt": 1,
  "validationOnly": false,
  "presentationMode": true
}
```

Supported initial action types:

- `quest-start`
- `quest-complete`
- `shop-buy`
- `shop-sell`
- `travel-service`
- `dialogue-option`
- `job-advance`
- `storage-open`
- `event-entry`

Unsupported action types must return `BLOCKED_UNSUPPORTED_ACTION`, not fall
through to generic script execution.

## Required Runtime Validation

Before any direct quest, shop, or script action, runtime must verify:

- Agent is alive and not in a blocking state.
- Agent is on the expected map.
- NPC exists on the live map.
- NPC placement matches the selected catalog row where possible.
- Selected approach point is reachable.
- Agent is within the allowed interaction box/range.
- Quest level, prerequisite, item, and progress requirements are satisfied.
- Reward choice can be resolved when the quest/action requires selection.
- Shop or script action is allowed for this NPC.
- Buy/sell action passes budget and protected-item policies.
- `doNotAutoUse` is false.
- Confidence is not `blocked`.
- Script-sensitive/manual-review rows are gated.
- Capability has not been cancelled or timed out.

Runtime should offer a validation-only dry run:

```text
validateNpcCommand(command) -> AgentNpcInteractionResult
```

This is useful for planners and LLM tools to ask "can this agent do this now?"
without mutating quest, item, meso, map, or shop state.

## Result Shape

Every execution and dry run should return:

```json
{
  "status": "SUCCESS",
  "reasonCode": "QUEST_STARTED",
  "agentId": 123,
  "planId": "maple-island-mvp",
  "objectiveId": "quest-1000-start",
  "npcId": 1012100,
  "mapId": 100000000,
  "placementKey": "100000000:0:1012100",
  "approachPoint": {
    "x": 10,
    "y": 20,
    "footholdId": 3
  },
  "delayAppliedMs": 1200,
  "changedState": {
    "questIds": [1000],
    "itemIds": [],
    "mesosDelta": 0
  },
  "retry": {
    "retryable": false,
    "nextAttemptDelayMs": 0
  }
}
```

Recommended status values:

- `SUCCESS`
- `VALIDATION_ONLY_SUCCESS`
- `BLOCKED_REQUIREMENT`
- `BLOCKED_MANUAL_REVIEW`
- `BLOCKED_AGENT_STATE`
- `BLOCKED_UNSUPPORTED_ACTION`
- `NPC_MISSING`
- `PLACEMENT_MISMATCH`
- `OUT_OF_RANGE`
- `UNREACHABLE`
- `INVENTORY_FULL`
- `REWARD_CHOICE_UNRESOLVED`
- `SHOP_ITEM_UNAVAILABLE`
- `SCRIPT_FAILED`
- `TIMEOUT`
- `CANCELLED`

## Approach Point Selection

Use stable seeded randomness so agents do not all stand in the same spot:

```text
seed = agentId + mapId + npcId + interactionType + questId/shopId + attempt
```

Selection should prefer:

1. manual override points
2. same-foothold generated points
3. allowed lower/higher platform points
4. nearest reachable generated points

Runtime should reserve selected points briefly to reduce clustering.

## Dialogue Delay Selection

Use generated timing as a base, then apply runtime variation:

```text
delay = catalog range + agent reading profile + first/repeat modifier + jitter
```

The agent may skip human dialogue logic internally, but delay simulation should
still happen before direct-calling quest/shop completion where the behavior is
intended to look human-like.

## Failure Behavior

Recommended failure responses:

- missing live NPC: refresh map state and abort.
- unreachable point: try another candidate.
- out of range: move closer or choose another candidate.
- blocked/manual review: abort and log.
- quest requirements not met: report unmet requirement to planner.
- inventory full: schedule sell/store/drop/resupply plan if allowed.
- reward choice unresolved: block and request profile/economy/manual policy.
- shop item unavailable: refresh shop catalog/live shop state and abort.
- script-sensitive: use a specialized capability, not generic NPC automation.

## Audit And Replay Safety

Every mutating NPC action should produce a compact audit record:

```text
agentId
planId
objectiveId
actionType
npcId
questId/shopId/itemId/serviceId
status
reasonCode
durationMs
delayAppliedMs
retryCount
catalogConfidence
liveValidationSummary
```

On relog or restart, runtime must reconcile live quest/inventory state before
repeating any NPC action. Persisted objective state is advisory; live server
state wins.

## Non-Goals Before Restructure

Do not wire this into live agent behavior yet.

Do not let an LLM call raw quest, shop, or script functions directly. The future
LLM layer should call controlled Agent tools that use this contract and runtime
validators.
