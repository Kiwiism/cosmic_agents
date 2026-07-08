# Maple Island MVP Technical Specification

This specification defines the exact technical package to implement after Agent
reconstruction for the Maple Island MVP.

Design behavior lives in:
`docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md`.

Route source: `docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md`.

First smoke plan card:
`docs/agents/plans/maple-island-amherst-subphase.plan.json`.

Full MVP plan card: `docs/agents/plans/maple-island-mvp.plan.json`.

## Scope Boundary

Only implement enough for:

```text
One Agent first completes the Amherst sub-phase from the beginning map to
Amherst, then the same runtime expands to the selected Maple Island questline
and stops at Southperry.
```

Out of scope:

- LLM runtime control.
- Free Market.
- economy engine.
- job advancement.
- Victoria Island questing.
- party quests.
- social behavior.
- generalized NPC dialogue replay.
- generalized shops.

## Package Layout

Recommended package scope:

```text
server/agents/plans/
server/agents/capabilities/quest/
server/agents/capabilities/npc/
server/agents/capabilities/navigation/
server/agents/capabilities/combat/
server/agents/capabilities/looting/
server/agents/capabilities/inventory/
server/agents/capabilities/recovery/
server/agents/capabilities/reactor/
```

Important classes:

```text
AgentPlanCard
AgentPlanCardRepository
AgentPlanAssignmentService
AgentPlanProgress
AgentObjective
AgentObjectiveRunner
AgentObjectiveResult
AgentObjectiveJournalService
AgentQuestCapability
AgentNpcInteractionCapability
AgentObjectiveNavigationService
AgentQuestCombatService
AgentQuestLootService
AgentInventoryReadService
AgentObjectiveItemUseService
AgentRecoveryCapability
AgentReactorInteractionCapability
```

Cosmic-specific calls should be hidden behind adapter methods where feasible.

## Data Inputs

Required catalog/runtime inputs:

- `docs/agents/plans/maple-island-amherst-subphase.plan.json`.
- `docs/agents/plans/maple-island-mvp.plan.json`.
- Maple Island MVP catalog and fast indexes.
- NPC placements and approach points.
- NPC action catalog.
- quest objective catalog.
- item source index.
- mob spawn catalog.
- portal graph.
- live map state.
- live quest state.
- live inventory state.
- live HP/MP/death state.

Prepared files:

```text
tmp/agent-llm-catalog/generated_maple_island_mvp_catalog.json
tmp/agent-llm-catalog/generated_maple_island_mvp_fast_indexes.json
tmp/npc-catalog/generated_npc_placements.json
tmp/npc-catalog/generated_npc_approach_points.json
tmp/npc-catalog/generated_npc_action_catalog.json
tmp/agent-llm-catalog/generated_quest_objective_catalog.json
tmp/agent-llm-catalog/generated_item_source_index.json
tmp/agent-llm-catalog/generated_mob_spawn_catalog.json
tmp/agent-llm-catalog/generated_portal_graph.json
```

## Core Models

### AgentPlanCard

```java
String planId;
String title;
String category;
PlanEntryCriteria entryCriteria;
PlanExitCriteria exitCriteria;
PlanFocusPolicy focusPolicy;
QuestPolicy questPolicy;
List<RouteSegment> route;
FallbackPolicy fallbackPolicy;
DebugResolverPolicy debugResolverPolicy;
```

### AgentObjective

```java
String objectiveId;
ObjectiveKind kind;
Integer mapId;
Integer npcId;
Integer questId;
Integer itemId;
Integer mobId;
Integer count;
List<String> dependsOnObjectiveIds;
ObjectiveFallbackPolicy fallbackPolicy;
ObjectiveDebugPolicy debugPolicy;
```

Objective id format:

```text
maple-island-mvp:{sequence}:{kind}:{questId/itemId/mobId}
```

### AgentPlanProgress

```java
int agentId;
String planId;
String status;
String currentObjectiveId;
Map<String, ObjectiveProgress> objectiveProgress;
int deathCount;
long assignedAtMs;
long updatedAtMs;
```

### AgentObjectiveResult

```java
ObjectiveStatus status;
String reasonCode;
String message;
Integer mapId;
Integer questId;
Integer npcId;
Integer itemId;
Integer mobId;
int attemptsUsed;
boolean liveStateChanged;
boolean debugResolved;
```

Reason codes:

- `completed`.
- `already-satisfied`.
- `navigation-stuck`.
- `missing-npc`.
- `quest-requirement-not-met`.
- `quest-state-mismatch`.
- `required-item-missing`.
- `target-mob-unavailable`.
- `inventory-full`.
- `low-hp-recovery`.
- `insufficient-sustain`.
- `death-loop`.
- `capability-missing`.
- `forbidden-action`.
- `debug-resolved`.

## Capability Contracts

### PlanCardCapability

```java
void assignPlan(int agentId, String planId);
void pausePlan(int agentId, String reason);
void resumePlan(int agentId);
void cancelPlan(int agentId, String reason);
AgentPlanProgress getPlanProgress(int agentId);
AgentObjectiveResult tickActivePlan(int agentId);
```

Responsibilities:

- load plan.
- validate entry criteria.
- select next objective.
- enforce dependencies.
- call objective runner.
- persist progress.
- check exit criteria.
- enforce forbidden actions.

### ObjectiveRunner

```java
AgentObjectiveResult runObjective(
    AgentSession agent,
    AgentPlanCard plan,
    AgentObjective objective
);
```

Dispatch:

| Objective kind | Capability |
| --- | --- |
| `navigate-to-map` | Navigation / PortalTravel |
| `navigate-to-npc` | Navigation / NPC |
| `quest-start` | NPC / Quest |
| `quest-complete` | NPC / Quest |
| `quest-chain` | Quest special planner |
| `kill-mob-count` | Combat |
| `loot-item-count` | Combat / Loot |
| `use-item` | Inventory / ItemUse |
| `reactor-box-items` | Reactor / Loot |
| `grant-scripted-item` | Quest special case |
| `auto-complete-quest` | Quest special case |
| `stop-plan` | Plan runner |

### QuestCapability

```java
QuestState getQuestState(AgentSession agent, int questId);
QuestRequirementStatus canStartQuest(AgentSession agent, int questId, int npcId);
QuestRequirementStatus canCompleteQuest(AgentSession agent, int questId, int npcId);
AgentObjectiveResult startQuest(AgentSession agent, int questId, int npcId);
AgentObjectiveResult completeQuest(AgentSession agent, int questId, int npcId);
List<QuestObjectiveRequirement> getOpenRequirements(AgentSession agent, int questId);
```

Rules:

- use normal server quest methods.
- live server quest state is authority.
- no `forceStart` or `forceComplete` in normal runtime.
- reject quest ids not allowed by active plan.
- reject completion of `1028` and `1046`.

### NpcQuestInteractionCapability

```java
NpcInteractionValidation validateNpcInteraction(
    AgentSession agent,
    int mapId,
    int npcId,
    NpcInteractionType type,
    Integer questId
);

AgentObjectiveResult interactQuestStart(AgentSession agent, int npcId, int questId);
AgentObjectiveResult interactQuestComplete(AgentSession agent, int npcId, int questId);
```

Validation:

- current map matches.
- NPC exists on live map.
- NPC matches catalog placement when possible.
- Agent is within range/interaction box.
- Agent is not dead, changing map, in trade, shop, or script lock.

### NavigationCapability

```java
AgentObjectiveResult navigateToMap(AgentSession agent, int mapId);
AgentObjectiveResult navigateToNpc(AgentSession agent, int mapId, int npcId);
AgentObjectiveResult navigateToPoint(AgentSession agent, int mapId, int x, int y);
AgentObjectiveResult navigateToPortal(AgentSession agent, int mapId, String portalName);
```

Required:

- use portal graph.
- handle map transition wait.
- recover from stuck state.
- support direct point target for future LLM control.

### CombatCapability

```java
AgentObjectiveResult killMobCount(AgentSession agent, int mobId, int count, Integer questId);
AgentObjectiveResult farmItemFromMobs(AgentSession agent, int itemId, int count, int questId);
```

Stop when:

- live quest kill count is satisfied.
- live inventory item count is satisfied.
- objective timeout occurs.
- HP/MP recovery requested.
- target unavailable.

### InventoryCapability

```java
int getItemCount(AgentSession agent, int itemId);
boolean hasFreeSlot(AgentSession agent, InventoryType type);
AgentObjectiveResult useItem(AgentSession agent, int itemId, Integer questId);
boolean isProtectedItem(AgentSession agent, int itemId);
```

MVP item use:

- Roger apple `2010007`.
- HP/MP potions.
- chair/rest item if available.

### RecoveryCapability

```java
AgentObjectiveResult recoverHp(AgentSession agent, RecoveryContext context);
AgentObjectiveResult recoverMp(AgentSession agent, RecoveryContext context);
AgentObjectiveResult recoverFromDeath(AgentSession agent, RecoveryContext context);
AgentObjectiveResult recoverFromStuck(AgentSession agent, RecoveryContext context);
AgentObjectiveResult handleObjectiveTimeout(AgentSession agent, AgentObjective objective);
```

HP recovery order:

1. potion.
2. safer position.
3. chair.
4. idle passive recovery.
5. lower combat pace.
6. block.

### ReactorInteractionCapability

```java
AgentObjectiveResult collectFromReactors(
    AgentSession agent,
    int questId,
    List<Integer> itemIds,
    int mapId
);
```

MVP only needs Pio `1008`.

## Objective Resolution

### `quest-chain`

```text
refresh live quest state
if already complete: mark satisfied-live
if not started: navigate to start NPC and start
refresh requirements
for each open requirement:
  resolve kill/loot/use/reactor/special objective
if complete-ready: navigate to complete NPC and complete
```

### `quest-if-available`

```text
if server says quest can start or is active:
  run normal quest resolution
else:
  skip with reason unavailable
```

### `grant-scripted-item`

For `8020` item `4031180` only.

Rules:

- allowed only because plan explicitly declares it.
- record as special-case scripted item.
- do not expose as generic grant API.

### `auto-complete-quest`

For known no-complete-NPC quests only.

Rules:

- validate quest is active.
- validate requirements complete.
- use normal server completion path if available.
- otherwise use Agent-specific adapter.
- if no safe adapter exists, block.

## Fallback Policies

General defaults:

```text
maxAttempts = 3
noProgressTimeoutMs = 120000
hardTimeoutMs = 600000
maxDeathsPerObjective = 3
```

After failure:

```text
refresh live state
run objective-specific fallback
retry if fallback changed state
block if no progress
```

### No Potion / No Meso

If no potions and no meso:

- rest/chair until full HP.
- idle passive recovery.
- lower combat pace.
- attempt only at safe HP.
- block as `insufficient-sustain` if repeated deaths continue.

### Debug Resolver

Config:

```yaml
agents:
  mapleIslandMvp:
    allowDebugResolvers: false
```

Allowed only when enabled:

| Resolver | Scope |
| --- | --- |
| `grant-roger-apple` | Quest `1021`, item `2010007`. |
| `grant-yoona-guide` | Quest `8020`, item `4031180`. |
| `grant-pio-reactor-items` | Quest `1008`, items `4031161`, `4031162`. |
| `debug-complete-auto-quest` | Known no-complete-NPC quests only. |

Never debug-resolve:

- Shanks off-island travel.
- completion of `1028`.
- completion of `1046`.
- arbitrary items.
- arbitrary mesos.
- whole-plan completion.

## Persistence

Minimum persistence:

- assigned plan id.
- current objective id.
- objective statuses.
- attempt counts.
- blocker reason.
- debug resolver usage.
- last known map.
- journal rows.

Suggested table:

```text
agent_plan_progress
  id
  character_id
  agent_id
  plan_id
  status
  current_objective_id
  progress_json
  created_at
  updated_at
```

Journal table:

```text
agent_plan_journal
  id
  character_id
  agent_id
  plan_id
  objective_id
  status
  reason_code
  payload_json
  created_at
```

## Configuration

```yaml
agents:
  mapleIslandMvp:
    enabled: true
    interactionRealism: OFF
    spawnPressureClearing: false
    futureQuestLootPriority: false
    allowDebugResolvers: false
    allowShopResupply: false
    maxObjectiveRetries: 3
    noProgressTimeoutMs: 120000
    hardObjectiveTimeoutMs: 600000
    maxDeathsPerObjective: 3
    lowHpPotionThresholdPercent: 45
    restUntilHpPercent: 95
```

## Implementation Order

1. Load and validate `maple-island-amherst-subphase.plan.json`.
2. Add plan assignment/progress state.
3. Add Capability Runtime active frame, paused parent-frame stack, and resume.
4. Add objective capability dispatch and journal output.
5. Add primitive navigation wrapper over reconstructed movement/navigation.
6. Add navigation parity tests before Amherst-specific constraints.
7. Add primitive combat wrapper over reconstructed grind/combat.
8. Add combat parity tests before Amherst-specific mob constraints.
9. Add live quest state read.
10. Add quest requirement read/explain.
11. Add NPC validation-only interaction.
12. Add quest start/complete through normal server APIs.
13. Add inventory count/free-slot/read APIs.
14. Add `use-item` for Roger apple.
15. Add combat kill objective stop conditions.
16. Add loot item objective stop conditions.
17. Add HP/MP/death/stuck/no-progress recovery.
18. Add reactor hit/item objectives for Amherst.
19. Add exit criteria check for stop at Amherst map `1000000`.
20. Add resume/reconciliation for parent/child capability frames.
21. Add deterministic one-Agent Amherst integration test.
22. Load and validate `maple-island-mvp.plan.json`.
23. Expand the same capability path to Yoona/Pio/Rain/Maria/Lucas/Mai and
    Southperry.
24. Add special-case Yoona guide grant if still required by live scripts.
25. Add auto-complete handling for known no-complete-NPC quests.
26. Add forbidden Shanks travel guard.
27. Add deterministic one-Agent Southperry integration test.
28. Add optional interaction realism `LIGHT`.
29. Add spawn-pressure clearing after deterministic success.

## Tests

Unit tests:

- Amherst and full MVP plan JSON load.
- objective dispatch submits objective capability commands.
- child capability handoff/resume preserves parent objective state.
- required quest ids match policy.
- forbidden `1028` completion rejected.
- `1046` completion rejected.
- Shanks off-island action rejected.
- quest start rejects wrong NPC.
- quest complete rejects unmet requirements.
- debug resolver disabled by default.
- protected item policy protects quest items.
- HP recovery chooses potion before rest.
- no-potion/no-meso recovery chooses rest.

Integration tests:

- assign plan to one Agent.
- complete Amherst sub-phase from `10000` to `1000000` through capability
  runtime handoff/resume.
- complete first quests `1000`, `1001`, `1021`.
- use Roger apple.
- navigate through route maps.
- complete a Sam quest requiring combat/loot.
- handle Yoona guide grant.
- block or complete Pio reactor objective depending on capability state.
- finish at Southperry.
- start `1046`.
- do not complete `1028`.
- do not leave island through Shanks.

Failure tests:

- NPC missing returns blocker.
- no route returns blocker.
- full inventory returns blocker.
- repeated death returns `death-loop`.
- missing required item returns blocker.
- objective timeout records retry history.
- debug resolver records `debug-resolved` when enabled.

## Acceptance Checklist

- Agent can run full plan from `10000` to `2000000`.
- Amherst sub-phase can run from `10000` to `1000000` before the full plan.
- Plan Runtime never directly scripts movement, combat, NPC, item, or reactor
  mutation outside capability commands.
- every objective is resolved by live state.
- required quests complete.
- `1046` active/incomplete.
- `1028` incomplete.
- no Shanks travel.
- no force completion in normal mode.
- no infinite loop on missing data.
- journal is sufficient to diagnose failures.
