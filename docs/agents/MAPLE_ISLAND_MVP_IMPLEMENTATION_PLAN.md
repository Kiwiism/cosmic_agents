# Maple Island MVP Implementation Plan

This plan starts after the bot-to-Agent reconstruction has completed enough that
legacy bot behavior is behind Agent runtime, capability, gateway, and policy
boundaries.

The milestone target is:

```text
Spawn one Agent with a Maple Island plan card.
Agent completes the Maple Island questline using server-validated capabilities.
Agent stops at Southperry and does not use Shanks to leave Maple Island.
```

This is not an LLM milestone. The plan should work deterministically first. LLM
control can be layered on later through the Plan Card and command gateway.

Detailed capability completion work is tracked in:

```text
docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md
```

The selected quest route and portable plan-card draft are tracked in:

```text
docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md
docs/agents/plans/maple-island-mvp.plan.json
```

Full MVP design and technical implementation specifications are tracked in:

```text
docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md
docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md
```

NPC approach randomness and dialogue-length delay are runtime presentation
policies, not Plan Card objectives. That design is tracked in:

```text
docs/agents/INTERACTION_REALISM_POLICY.md
```

The post-reconstruction implementation handoff is tracked in:

```text
docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md
```

Quest objective focus and mixed-mob spawn-pressure combat behavior are tracked
in:

```text
docs/agents/QUEST_FOCUS_AND_COMBAT_POLICY.md
```

## Scope

### In Scope

- Maple Island quest start/complete flow.
- NPC quest interaction without dialogue clicking.
- Quest objective tracking.
- Navigation to NPCs, mobs, portals, and final stop map.
- Combat for quest mobs.
- Looting for quest items.
- Inventory checks for required quest items.
- Recovery from common stalls.
- Maple Island sample Plan Card.
- Runtime progress persistence sufficient to resume after relog/restart.

### Out Of Scope

- Free Market.
- Shops, unless later Maple Island data proves required.
- Job advancement.
- Party quests.
- Trading.
- Full NPC dialogue simulation.
- LLM decision-making.
- Economy engine.
- Non-Maple-Island questlines.

## Required Capability Set

Minimum capabilities for this MVP:

```text
PlanCardCapability
QuestCapability
NpcQuestInteractionCapability
NavigationCapability
PortalTravelCapability
CombatCapability
LootCapability
InventoryCapability
RecoveryCapability
```

Existing nutnnut-derived behavior already covers parts of navigation, combat,
and looting. This milestone should finish the gaps that prevent autonomous
quest completion.

## Core Design

```text
Maple Island Plan Card
  -> Objective Runner
  -> Capability Router
  -> capability validators
  -> Cosmic adapter/gateways
  -> server quest/navigation/combat/loot operations
  -> objective progress update
  -> next objective
```

Capabilities must not directly decide the whole questline. They execute one
validated objective at a time and report result/state back to the Objective
Runner.

## Quest Interaction Rule

For this MVP, do not simulate dialogue clicking unless a quest requires a script
path that cannot be completed through the normal quest API.

Preferred path:

```java
quest.canStart(agent, npcId)
quest.start(agent, npcId)

quest.canComplete(agent, npcId)
quest.complete(agent, npcId)
```

Do not use normal runtime flow:

```java
quest.forceStart(...)
quest.forceComplete(...)
```

Those methods can bypass requirements or actions and should stay limited to GM,
script, migration, or test-only code.

Agent validation must still require:

- agent exists
- agent is alive
- agent is on the required map
- required NPC exists on the map
- agent is inside the configured NPC interaction box/range
- quest start/complete requirements pass
- objective is allowed by the active Plan Card
- forbidden actions are not violated

## Capability Details

### PlanCardCapability

Responsibilities:

- Load assigned plan card.
- Track objective statuses.
- Enforce plan entry criteria.
- Enforce objective dependencies.
- Enforce focus policy.
- Enforce exit criteria.
- Enforce forbidden actions.
- Resume from persisted progress after relog/restart.

Required commands:

```text
assign_plan(agentId, planId)
pause_plan(agentId, planId)
resume_plan(agentId, planId)
cancel_plan(agentId, planId)
get_plan_state(agentId)
```

MVP implementation can be local/server-side only. It does not need MCP or LLM
integration yet.

### QuestCapability

Responsibilities:

- Read current quest status.
- Determine whether quest can start.
- Determine whether quest can complete.
- Read required NPC for start/complete.
- Read objectives: mob kills, item requirements, NPC transitions, next quest.
- Detect objective completion.
- Emit plan objective success/failure state.

Required APIs:

```text
getQuestState(agentId, questId)
canStartQuest(agentId, questId, npcId)
startQuest(agentId, questId, npcId)
canCompleteQuest(agentId, questId, npcId)
completeQuest(agentId, questId, npcId, rewardSelection?)
getQuestObjectives(agentId, questId)
```

Implementation notes:

- Use Cosmic `Quest.canStart`, `Quest.start`, `Quest.canComplete`,
  `Quest.complete`.
- Support reward selection, but default to catalog-defined choice or first valid
  selection when no choice matters.
- Record result effects: quest status, exp, mesos, items added/removed.

### NpcQuestInteractionCapability

Responsibilities:

- Find target NPC on the current map.
- Validate interaction box/range.
- Request navigation to approach point if needed.
- Start or complete quest through QuestCapability.
- Apply human-like interaction delay based on NPC/quest metadata.

Required commands:

```text
navigateToNpc(agentId, mapId, npcId)
startQuestAtNpc(agentId, questId, npcId)
completeQuestAtNpc(agentId, questId, npcId, rewardSelection?)
```

MVP interaction box:

```text
defaultNpcInteractBox:
  xRadius: 180
  yRadius: 120
  allowBelowPlatformClick: true
```

The random stop point near NPC should come from catalog overrides when
available. If missing, choose a valid foothold point inside the default box.

### NavigationCapability

Responsibilities:

- Route to target map.
- Route to target NPC/mob/portal/point.
- Use portal chains.
- Detect stuck state.
- Retry or request recovery.

Required commands:

```text
navigateToMap(agentId, mapId)
navigateToNpc(agentId, mapId, npcId)
navigateToMobArea(agentId, mapId, mobIds)
navigateToPortal(agentId, mapId, portalNameOrId)
navigateToPoint(agentId, mapId, x, y, tolerance)
```

Gaps to finish:

- portal-chain reliability
- valid stop points near NPC
- same-map target recovery
- map unavailable fallback
- stuck detection and retry policy

### PortalTravelCapability

Responsibilities:

- Resolve portal path between maps.
- Move agent to portal.
- Trigger portal transition.
- Verify arrival map.
- Retry on failed transition.

This may be implemented inside NavigationCapability, but it should be modeled
as a distinct objective/capability because Maple Island questline depends on
reliable map transitions.

Required validations:

- source portal exists
- destination map exists/loadable
- agent is close enough to portal
- plan permits travel to destination
- travel does not violate final forbidden action, especially Shanks/Lith Harbor

### CombatCapability

Responsibilities:

- Target only quest-relevant mobs when running quest objectives.
- Attack until kill count or drop requirement is satisfied.
- Stop combat when the objective completes.
- Maintain objective focus until the objective's exit criteria is met.
- Optionally clear bounded filler mobs when target mobs are scarce and map
  spawns are clogged.
- Prefer filler mobs that provide prelootable future quest value when safe.
- Avoid overfarming due to stale quest/inventory state.
- Handle low HP/MP by requesting recovery.

Required commands:

```text
killMobCount(agentId, mobId, count, questId)
farmMobDrops(agentId, mobIds, itemId, quantity, questId)
clearThreatsNear(agentId, point)
```

Gaps to finish:

- mob objective awareness
- objective stop condition
- objective focus state
- spawn-pressure target policy
- future quest loot policy
- potion/buff survival parity
- death recovery signal
- no-mob retry/backoff

### LootCapability

Responsibilities:

- Prioritize required quest drops.
- Loot only useful items under the current objective unless policy allows more.
- Verify inventory count after looting.
- Stop looting once objective is complete.
- Report full inventory or unreachable drop.

Required commands:

```text
lootRequiredItem(agentId, itemId, quantity, questId)
lootNearbyUsefulDrops(agentId, policy)
```

MVP loot priority:

```text
1. current quest required item
2. mesos
3. useful potion/equip if inventory space is safe
4. ignore low-value unrelated drops
```

### InventoryCapability

Responsibilities:

- Count required items.
- Validate item requirements before quest completion.
- Detect full inventory.
- Prevent discarding protected quest/future-quest items.
- Provide objective state to QuestCapability.

Required APIs:

```text
getItemCount(agentId, itemId)
hasRequiredItems(agentId, requirements)
getFreeSlots(agentId, inventoryType)
isInventoryPressureHigh(agentId)
```

MVP does not need full sell-trash behavior. If inventory is full, Recovery should
pause/block the plan with a clear reason.

### RecoveryCapability

Responsibilities:

- Detect stuck navigation.
- Detect repeated failed interaction.
- Detect missing NPC/mob/portal.
- Detect full inventory.
- Detect death.
- Detect quest state mismatch.
- Decide retry, replan, pause, or fail objective.

Required failure reasons:

```text
stuck-navigation
missing-npc
missing-portal
missing-map
missing-mob
inventory-full
quest-requirement-not-met
quest-state-mismatch
death
unsafe-hp-mp
forbidden-action-risk
```

MVP recovery policy:

```text
retry same action up to 3 times
refresh live state after each failure
if still blocked, pause plan and record blocker
never bypass quest requirements with forceStart/forceComplete
never take Shanks as recovery
```

## Maple Island Plan Card

Create a sample plan card:

```text
docs/agents/plans/maple-island-mvp.plan.json
```

The draft plan card uses the current route decision:

- start from `10000 Mushroom Town`.
- do Yoona before Mai in `1010000`.
- start `1046 Biggs's Story on Victoria Island`, but leave it incomplete.
- exclude `1028` and `8142`.
- keep Todd `1018` and `1035` as optional-review until client availability is
  verified.

Plan-level requirements:

- entry: agent is on Maple Island or can be moved there by test setup
- objective mode: dependency or ordered
- focus: high
- sidetracks: emergency only
- final map: Southperry
- forbidden action: use Shanks travel to leave Maple Island

Minimum plan skeleton:

```json
{
  "schemaVersion": 1,
  "planId": "maple-island-mvp",
  "title": "Maple Island MVP Questline",
  "category": "questline",
  "objectiveMode": "dependency",
  "focusPolicy": {
    "focusLevel": "high",
    "allowSidetracks": true,
    "allowedSidetrackTypes": ["emergency"],
    "returnToPlan": "always"
  },
  "entryCriteria": {
    "requiredRegion": "maple-island"
  },
  "exitCriteria": {
    "completeWhen": "all-required-objectives-cleared",
    "alsoRequire": [
      {
        "type": "agent-location",
        "mapId": 2000000,
        "description": "Stop at Southperry."
      }
    ],
    "forbiddenActions": [
      {
        "type": "npc-travel",
        "npcId": 22000,
        "description": "Do not use Shanks to leave Maple Island."
      }
    ]
  },
  "objectives": []
}
```

Objective generation should come from catalog data where possible, then be
manually overridden where Cosmic scripts/WZ data are ambiguous.

## Catalog Requirements

The runtime needs read-only catalog data for:

- Maple Island maps.
- Map portal graph.
- NPC placements.
- NPC interaction boxes/random stop points.
- Quest start NPCs.
- Quest complete NPCs.
- Quest prerequisites.
- Quest item requirements.
- Quest mob requirements.
- Quest reward choices.
- Item drop sources.
- Mob spawn maps.
- Final Southperry stop point.
- Shanks forbidden interaction metadata.
- Maple Island MVP quest availability classifications.
- Special objective overrides for Pio reactor boxes, Roger's Apple, Yoona's Cash
  Shop shopping guide, no-complete-NPC auto-complete quests, and Biggs `1046`
  start-only behavior.

Missing catalog data should not cause silent behavior. It should produce a
blocked objective with a specific missing-data reason.

## Runtime State

Persist enough state to resume:

```json
{
  "agentId": 123,
  "activePlanId": "maple-island-mvp",
  "currentObjectiveId": "quest-1000-start",
  "completedObjectives": [],
  "blockedObjective": null,
  "lastKnownMapId": 0,
  "lastKnownPosition": {
    "x": 0,
    "y": 0,
    "footholdId": null
  },
  "updatedAt": 0
}
```

On restart/relog:

```text
load plan state
refresh live quest/inventory/map state
reconcile objective statuses
resume first ready incomplete objective
```

Do not trust persisted objective state over live quest state.

## Implementation Order

1. Add read-only plan card loader.
2. Add plan progress state model.
3. Add objective status model.
4. Add capability command/result model.
5. Add QuestCapability read APIs.
6. Add NpcQuestInteractionCapability validation-only path.
7. Add direct quest start/complete execution path using `Quest.start/complete`.
8. Add navigation objective adapters for NPC, map, portal, point.
9. Add portal travel verification.
10. Add inventory count/free-slot APIs.
11. Add loot objective stop conditions.
12. Add combat objective stop conditions.
13. Add RecoveryCapability basic retry/block policy.
14. Build Maple Island catalog slice.
15. Generate or write Maple Island MVP plan card.
16. Add test command to assign `maple-island-mvp` to one agent.
17. Add objective progress logging.
18. Add integration test script for one full run.
19. Add resume test from partial progress.
20. Add forbidden Shanks interaction test.

## Verification Checklist

### Unit-Level

- Quest start validates NPC requirement.
- Quest complete validates NPC requirement.
- Direct quest completion applies rewards/actions.
- `forceStart` and `forceComplete` are not used by the runtime path.
- Inventory count reflects live item state.
- Plan exit criteria blocks Shanks travel.
- Missing NPC returns blocked state, not exception.
- Missing portal returns blocked state, not exception.

### Integration-Level

- Agent starts assigned Maple Island plan.
- Agent navigates to required NPCs.
- Agent starts required quests.
- Agent kills required mobs.
- Agent loots required quest items.
- Agent completes required quests.
- Agent transitions maps through portals.
- Agent resumes after relog/restart.
- Agent stops at Southperry.
- Agent does not use Shanks to leave Maple Island.

### Runtime Observability

Each objective result should log:

```text
agentId
planId
objectiveId
capabilityUsed
status
reason
questId/itemId/mobId/npcId/mapId when relevant
durationMs
retryCount
```

This log becomes the base for later decision journal integration.

## Completion Definition

The milestone is complete when:

- one newly spawned Agent can run the `maple-island-mvp` plan without manual
  command intervention
- the Agent completes all required Maple Island quest objectives defined in the
  plan
- the Agent ends in Southperry
- the Agent does not leave Maple Island through Shanks
- the run survives at least one forced relog/restart resume test
- failures produce clear blocked states instead of silent loops
