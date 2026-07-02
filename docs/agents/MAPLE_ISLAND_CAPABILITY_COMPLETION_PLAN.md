# Maple Island Capability Completion Plan

This document turns the Maple Island MVP into an implementation-ready checklist
for after bot-to-Agent reconstruction stabilizes.

Target:

```text
Spawn one Agent with a Maple Island plan card.
Agent completes all selected Maple Island quest objectives.
Agent stops at Southperry.
Agent does not talk to Shanks or leave for Lith Harbor.
```

## Source Data Checked

Local WZ/XML assets were checked from:

```text
wz/String.wz/Map.img.xml
wz/String.wz/Npc.img.xml
wz/Quest.wz/QuestInfo.img.xml
wz/Quest.wz/Check.img.xml
wz/Quest.wz/Act.img.xml
wz/Quest.wz/Say.img.xml
wz/Map.wz/Map/Map0/*.img.xml
```

Maple Island maps are under the `maple` string category. Important map ids:

| Map ID | Name |
| --- | --- |
| `000000000` | Entrance - Mushroom Town Training Camp |
| `000000001` | Upper level of the Training Camp |
| `000000002` | Lower level of the Training Camp |
| `000000003` | Entrance - Mushroom Town Training Camp |
| `000010000` | Mushroom Town |
| `000020000` | Snail Garden |
| `000020001` | Mushroom Town Townstreet |
| `000030000` | Snail Field of Flowers |
| `000030001` | Mushroom Town Townstreet |
| `000040000` | In a Small Forest |
| `000040001` | Snail Hunting Ground II |
| `000040002` | Snail Hunting Ground III |
| `000050000` | Dangerous Forest |
| `000050001` | The Field West of Southperry |
| `000060000` | Southperry |
| `000060001` | Southperry Armor Store |
| `001000000` | Amherst |
| `001000001` | Amherst Weapon Store |
| `001000002` | Amherst Townstreet |
| `001000003` | Amherst Department Store |
| `001000004` | Snail Garden |
| `001000005` | Hunting Ground Middle of the Forest I |
| `001000006` | Hunting Ground Middle of the Forest II |
| `001010000` | Entrance to Adventurer Training Center |
| `001010001` | Amherst Weapon Store |
| `001010002` | Amherst Townstreet |
| `001010003` | Amherst Department Store |
| `001010004` | Snail Field of Flowers |
| `001010100` | Adventurer Training Center 1 |
| `001010200` | Adventurer Training Center 2 |
| `001010300` | Adventurer Training Center 3 |
| `001010400` | Adventurer Training Center 4 |
| `001020000` | Split Road of Destiny |
| `001020001` | Tomato Field |
| `002000000` | Southperry |
| `002000001` | Southperry Armor Store |

Important NPC ids:

| NPC ID | Name | MVP Use |
| --- | --- | --- |
| `10000` | Pio | quest/recycling |
| `12000` | Lucas | Amherst quest flow |
| `12100` | Mai | training quest flow |
| `12101` | Rain | quiz/advice, optional unless selected plan includes it |
| `2000` | Roger | apple/tutorial quest |
| `20002` | Biggs | Southperry quest/advice |
| `2005` / `1011001` | Sam | Mushroom Town/Sam request flow |
| `20100` | Yoona | shopping quiz, optional |
| `2100` | Sera | early tutorial quest |
| `2101` | Heena | early tutorial quest |
| `2102` | Nina | early tutorial quest |
| `2103` | Maria | Maria/Lucas/Shanks quest flow |
| `22000` | Shanks | forbidden final-leave interaction |

Candidate Maple Island quest ids from `QuestInfo.img.xml`:

```text
1000 Borrowing Sera's Mirror
1001 Bringing a Mirror to Heena
1003 What Sen wants to eat
1004 Returning to Nina
1005 Letter for Lucas
1006 Lucas' Reply
1007 Bigg's Collection of Items
1008 Pio's Collecting Recycled Goods
1009-1015 Rain's Maple Quiz chain
1016 Mai's Training
1017 Mai's Final Training
1018 Todd's How-to-Hunt
1019 Sam's Suggestion
1020 Pio and the Recycling
1021 Roger's Apple
1022 Lucas' Cute Daughter
1025 Maria's Nutritious Juice
1026 Delivering Nutritious Juice to Shanks
1027 Mai's Request
1028 To Lith Harbor
1029 Sam's Advice
1030 Maria's Map Reading
1031 Heena and Sera
1032 Nina's Brother Sen
1033 What Sen Wants
1034 Tasty Mushroom Candy
1035 Todd's Hunting Method
1037 Help Hunt the Snails
1038 Maria's Letter
1039 Helping Out Yoona
1040 Chief's Introduction
1041 Mai's First Training
1042 Mai's Second Training
1043 Mai's Third Training
1044 Mai's Last Training
1046 Biggs's Story on Victoria Island
8020-8025 Yoona's Quiz on Shopping
8031 Protect Lucas's Farm
8142 Todd's How-to-Hunt
```

Not every candidate must be in the first MVP plan. The plan card should define
the selected questline explicitly. The catalog should still include all rows so
the planner can block, skip, or postpone optional quests intentionally.

## Capability Readiness

| Capability | Current State After Reconstruction | MVP Readiness | Required To Reach 100% |
| --- | --- | --- | --- |
| PlanCardCapability | Legacy `AgentPlan`, `AgentTask`, and script runner exist, but not new objective-card runtime | Partial | JSON schema, loader, objective runner, progress persistence, dependency graph, forbidden action gate |
| QuestCapability | Party quest sync exists; general quest start/complete API not implemented | Missing | live quest state, requirement checks, start/complete execution, reward selection, result effects |
| NpcQuestInteractionCapability | NPC package is placeholder; catalog contracts exist | Missing | catalog repository, approach chooser, live validator, delay policy, typed command/result |
| NavigationCapability | Reconstructed bot navigation, graph, movement, map transition support exists | Mostly present | map/NPC/portal/point objective adapters, stuck retry policy, direct point command |
| PortalTravelCapability | Mostly implicit in navigation/map transition | Partial | explicit portal objective, path verification, arrival verification, Shanks/Lith Harbor block |
| CombatCapability | Reconstructed combat planning/targeting/execution exists | Mostly present | quest-objective targeting mode, stop condition, no-mob backoff, danger result |
| LootCapability | Reconstructed passive loot, target selection, eligibility exists | Mostly present | quest-required item priority, objective stop condition, unreachable/full inventory result |
| InventoryCapability | Inventory/trade/drop policies exist; objective read APIs incomplete | Partial | item count, free slots, protected quest/future-quest items, inventory pressure blocker |
| RecoveryCapability | Death/recovery teleport/return scroll/runtime safety services exist | Partial | MVP retry/block policy, live state refresh, blocker persistence, no-Shanks recovery |
| Capability Command/Result | No single objective command/result contract | Missing | shared DTOs/status/reason codes/audit logging |
| Catalog Runtime | Offline catalog docs/tools exist; runtime repository not wired | Missing | Maple Island slice bundle, fast indexes, read-only repository |
| Resume/Persistence | Agent runtime state exists; plan objective state not persisted | Missing | plan state persistence and live quest/inventory reconciliation |

## Required Implementation Work

### 1. Maple Island Catalog Slice

Create:

```text
tmp/agent-catalog/maple-island/
  maps.json
  portals.json
  npcs.json
  npc_placements.json
  npc_approach_points.json
  quests.json
  quest_objectives.json
  quest_reward_choices.json
  mobs.json
  drops.json
  items.json
  reactors.json
  fast_indexes.json
  validation.md
```

Minimum indexes:

```text
mapId_to_npcPlacements
mapId_to_mobSpawns
mapId_to_portals
npcId_to_placements
questId_to_startNpcIds
questId_to_completeNpcIds
questId_to_requiredItems
questId_to_requiredMobs
itemId_to_dropSources
mobId_to_mapIds
placementKey_to_approachPoints
questId_phase_to_dialogueTiming
forbidden_action_index
```

Forbidden action rows:

```text
NPC 22000 Shanks
any quest/action that moves agent from Maple Island to Lith Harbor
any portal/script transition to Lith Harbor or Victoria Island before MVP exit
```

Validation report must list:

- missing NPC names.
- missing map placements.
- missing approach points.
- quest NPCs not present in selected map slice.
- quest objectives without mob/drop source.
- reward choices without policy.
- script-sensitive rows.
- Shanks/Lith Harbor forbidden route coverage.

### 2. Plan Card Runtime

Add:

```text
AgentPlanCard
AgentPlanObjective
AgentObjectiveDependency
AgentObjectiveStatus
AgentPlanProgress
AgentPlanCardRepository
AgentObjectiveRunner
AgentCapabilityRouter
```

Status values:

```text
PENDING
READY
RUNNING
SUCCEEDED
FAILED_RETRYABLE
BLOCKED
SKIPPED
CANCELLED
```

Plan rules:

- entry map must be Maple Island or test setup moves the agent there.
- selected quests must be explicit.
- objective dependencies must be resolved before action.
- Shanks/Lith Harbor forbidden action must be enforced globally.
- final success requires agent at Southperry and no Shanks travel.

### 3. Shared Capability Command/Result Model

Add:

```text
AgentCapabilityCommand
AgentCapabilityResult
AgentCapabilityStatus
AgentCapabilityReasonCode
AgentCapabilityAuditEvent
```

Every result must include:

```text
agentId
planId
objectiveId
capability
status
reasonCode
durationMs
retryCount
entity ids: mapId/npcId/questId/itemId/mobId/portalName
changed state summary
```

### 4. QuestCapability

Add APIs:

```text
getQuestState(agentId, questId)
getQuestRequirements(agentId, questId, phase)
canStartQuest(agentId, questId, npcId)
startQuest(agentId, questId, npcId)
canCompleteQuest(agentId, questId, npcId)
completeQuest(agentId, questId, npcId, rewardSelection)
getQuestObjectives(agentId, questId)
explainUnmetRequirements(agentId, questId, phase)
```

Rules:

- use `Quest.canStart`, `Quest.start`, `Quest.canComplete`,
  `Quest.complete`.
- do not use `forceStart` or `forceComplete` in normal agent runtime.
- live server state wins over persisted objective state.
- reward selection must come from catalog/profile policy or block.

Tests:

- start valid quest.
- reject wrong NPC.
- reject unmet level/prerequisite/item/mob requirement.
- complete valid quest.
- apply reward and item removal.
- repeated completion does not duplicate reward.

### 5. NpcQuestInteractionCapability

Add:

```text
AgentNpcCatalogRepository
AgentNpcApproachPointChooser
AgentNpcRuntimeValidator
AgentNpcInteractionCommand
AgentNpcInteractionResult
AgentNpcInteractionAudit
AgentNpcDialogueDelayPolicy
```

Required command types:

```text
navigate-to-npc
quest-start-at-npc
quest-complete-at-npc
shop-buy-at-npc
shop-sell-at-npc
travel-service-at-npc
validation-only
```

MVP only needs:

```text
navigate-to-npc
quest-start-at-npc
quest-complete-at-npc
validation-only
```

Validation:

- agent alive.
- agent not in trade/miniroom/cash shop/MTS/script.
- agent on expected map.
- live NPC exists.
- placement matches catalog where possible.
- selected approach point reachable.
- agent inside interaction box.
- quest requirements pass.
- reward choice resolvable.
- action allowed by active plan.
- not Shanks forbidden route.

### 6. Navigation And Portal Travel

Add objective adapters:

```text
navigateToMapObjective
navigateToNpcObjective
navigateToMobAreaObjective
navigateToPortalObjective
navigateToPointObjective
```

Add portal travel result:

```text
source map
source portal
destination map
expected destination
actual destination
status
reason
```

Hard block:

```text
destination == Lith Harbor or non-Maple-Island map before MVP completion
interaction NPC == Shanks and action is travel/leave-island
```

### 7. Combat Objective Mode

Add:

```text
AgentQuestCombatObjectiveService
AgentMobObjectiveTargetPolicy
AgentCombatStopCondition
AgentNoMobBackoffPolicy
```

Objective types:

```text
kill mob id count for quest
farm item from mob list for quest
clear threats near required NPC/portal
```

Stop immediately when:

- live quest kill count satisfies requirement.
- inventory count satisfies required item quantity.
- plan objective is cancelled.
- HP/MP unsafe result asks recovery.
- no target found after retry limit.

### 8. Loot Objective Mode

Add:

```text
AgentQuestLootObjectiveService
AgentLootStopCondition
AgentRequiredItemLootPolicy
```

Priority:

```text
1. current quest required item
2. mesos
3. safe useful recovery item
4. selected useful equip/material if policy allows
5. ignore unrelated low-value drops
```

Stop when live inventory count satisfies objective.

### 9. Inventory Objective APIs

Add:

```text
getItemCount(agentId, itemId)
hasRequiredItems(agentId, requirements)
getFreeSlots(agentId, inventoryType)
isInventoryPressureHigh(agentId)
isProtectedItem(agentId, itemId, planContext)
```

Protected items:

- active quest required items.
- future selected Maple Island quest items.
- reward choice items selected by plan/profile.
- recovery items if no replacement available.
- forbidden travel/recommendation items if needed to avoid accidental Shanks
  completion path.

### 10. Recovery Policy

Add:

```text
AgentObjectiveRecoveryPolicy
AgentObjectiveRetryState
AgentObjectiveBlocker
```

Rules:

- retry same action up to 3 times.
- refresh live map/NPC/quest/inventory state between retries.
- try alternate approach point after unreachable point.
- try alternate mob area after no mob found.
- block on missing catalog row.
- block on inventory full for MVP.
- block on death until respawn/recovery completed.
- never use Shanks/Lith Harbor as recovery.

### 11. Plan Persistence And Resume

Persist:

```json
{
  "agentId": 0,
  "planId": "maple-island-mvp",
  "currentObjectiveId": "quest-1037-start",
  "completedObjectiveIds": [],
  "blockedObjective": null,
  "retryState": {},
  "lastKnownMapId": 0,
  "updatedAt": 0
}
```

On relog/restart:

```text
load persisted plan state
load live character quest state
load live inventory state
load live map state
mark objectives already satisfied
resume first ready incomplete objective
```

Do not trust persisted objective completion over live quest status.

## Suggested 100% Implementation Order

1. Generate Maple Island catalog slice and validation report.
2. Add plan card JSON schema and sample `maple-island-mvp.plan.json`.
3. Add read-only plan loader.
4. Add plan progress state model and persistence.
5. Add shared capability command/result/audit model.
6. Add QuestCapability read APIs.
7. Add QuestCapability start/complete validation-only methods.
8. Add QuestCapability execution methods.
9. Add NPC catalog repository and fast index loader.
10. Add NPC approach point chooser.
11. Add NPC runtime validator.
12. Add NPC validation-only command.
13. Add NPC quest start/complete command.
14. Add navigation objective adapters.
15. Add portal travel objective and destination verification.
16. Add Shanks/Lith Harbor forbidden action gate.
17. Add combat quest-objective mode.
18. Add loot required-item mode.
19. Add inventory count/free-slot/protected-item APIs.
20. Add recovery retry/block policy.
21. Add objective runner and capability router.
22. Add `maple-island-mvp` assignment test command.
23. Add unit tests for every capability result.
24. Add one-agent full Maple Island integration test.
25. Add relog/restart resume test.
26. Add forbidden Shanks interaction test.
27. Add debug/audit log view for plan progress.

## Completion Criteria

100% complete means:

- all selected Maple Island quests in the plan card finish.
- every quest start/complete uses live validation.
- every NPC interaction has catalog lookup, approach selection, range check, and
  result audit.
- every combat/loot objective stops when live quest/inventory state satisfies
  it.
- inventory full, missing NPC, missing mob, missing portal, bad route, death,
  and quest mismatch produce structured blockers.
- relog/restart resumes without duplicate quest rewards.
- Shanks travel is blocked.
- agent ends at Southperry.
- integration test passes from fresh agent state.
