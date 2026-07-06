# NPC Quest Capability Technical Specification

Purpose:

```text
Define implementation interfaces, DTOs, validation flow, command/result shapes,
reason codes, persistence expectations, and tests for the future NPC Quest
Capability package.
```

Package name:

```text
agent-npc-quest-capability
```

This spec is documentation only until Agent reconstruction is stable.

## Package Boundary

This package depends on:

- Capability Runtime command/result envelope.
- Catalog Runtime query interfaces.
- Server Adapter live state and quest commit interfaces.
- Navigation capability for approach.
- Interaction Realism policy for delay/approach variation.
- Profile/Economy reward choice policy.
- Event Bus/Audit writer.

This package must not directly depend on:

- `server.bots.*`
- concrete reconstructed Agent runtime classes.
- packet classes.
- raw script manager classes.
- raw `client.Character` outside Server Adapter implementation.

## Suggested Layout

```text
agent-npc-quest-capability/
  api/
    NpcQuestCapability.java
    NpcQuestCommandFactory.java
    NpcQuestStatusReader.java
  model/
    NpcQuestCommand.java
    NpcQuestActionType.java
    NpcQuestResult.java
    NpcQuestReasonCode.java
    NpcQuestTarget.java
    NpcQuestValidationSnapshot.java
    NpcQuestChangedState.java
  validation/
    NpcQuestStaticValidator.java
    NpcQuestLiveValidator.java
    NpcQuestRequirementValidator.java
    NpcQuestRewardValidator.java
    NpcQuestForbiddenActionValidator.java
  runtime/
    NpcQuestCapabilityHandler.java
    NpcQuestApproachService.java
    NpcQuestDelayService.java
    NpcQuestCommitService.java
    NpcQuestResumeReconciler.java
  policy/
    QuestRewardChoicePolicy.java
    AutoCompleteQuestPolicy.java
    ScriptSensitiveQuestPolicy.java
  events/
    NpcQuestCapabilityEvent.java
```

## Capability Commands

The package should register these commands in Capability Runtime:

```text
VALIDATE_NPC_QUEST_ACTION
APPROACH_NPC_FOR_QUEST
START_QUEST_AT_NPC
COMPLETE_QUEST_AT_NPC
AUTO_COMPLETE_QUEST
SELECT_QUEST_REWARD
```

## Action Types

```java
enum NpcQuestActionType {
    VALIDATE,
    APPROACH,
    START_QUEST,
    COMPLETE_QUEST,
    AUTO_COMPLETE_QUEST,
    SELECT_REWARD
}
```

## Command DTO

```java
record NpcQuestCommand(
    String commandId,
    String agentId,
    String planId,
    String objectiveId,
    NpcQuestActionType actionType,
    int questId,
    Integer npcId,
    Integer mapId,
    String placementKey,
    boolean validationOnly,
    boolean allowAutoComplete,
    boolean allowAlreadySatisfied,
    boolean presentationMode,
    String interactionRealismMode,
    int attempt,
    long timeoutMs
) {}
```

## Result DTO

```java
record NpcQuestResult(
    String commandId,
    String agentId,
    String planId,
    String objectiveId,
    int questId,
    Integer npcId,
    Integer mapId,
    NpcQuestStatus status,
    NpcQuestReasonCode reasonCode,
    String message,
    String placementKey,
    ApproachPoint selectedApproachPoint,
    long delayAppliedMs,
    NpcQuestValidationSnapshot validation,
    NpcQuestChangedState changedState,
    boolean retryable,
    long durationMs
) {}
```

## Status Values

```text
SUCCESS
VALIDATION_ONLY_SUCCESS
ALREADY_SATISFIED
FAILED_RETRYABLE
BLOCKED
CANCELLED
TIMED_OUT
ERROR
```

## Reason Codes

Successful:

- `QUEST_STARTED`
- `QUEST_COMPLETED`
- `QUEST_ALREADY_STARTED`
- `QUEST_ALREADY_COMPLETED`
- `AUTO_COMPLETE_VALID`
- `VALIDATION_ONLY_OK`
- `REWARD_SELECTED`

Blocked:

- `NPC_MISSING`
- `NPC_OUT_OF_RANGE`
- `NPC_UNREACHABLE`
- `PLACEMENT_MISMATCH`
- `QUEST_NOT_AVAILABLE`
- `QUEST_REQUIREMENT_NOT_MET`
- `QUEST_PROGRESS_INCOMPLETE`
- `REQUIRED_ITEM_MISSING`
- `INVENTORY_FULL`
- `REWARD_CHOICE_UNRESOLVED`
- `MANUAL_REVIEW_REQUIRED`
- `SCRIPT_SENSITIVE`
- `AUTO_COMPLETE_NOT_ALLOWED`
- `FORBIDDEN_ACTION`
- `AGENT_STATE_BLOCKED`
- `SERVER_REJECTED`

Control:

- `CANCELLED`
- `TIMED_OUT`
- `CAPABILITY_MISSING`
- `CATALOG_FACT_MISSING`
- `INTERNAL_ERROR`

## Catalog Queries

Required catalog calls:

```java
Optional<NpcActionRow> findNpcQuestAction(int npcId, String actionType, int questId);
List<NpcPlacement> findNpcPlacements(int npcId, int mapId);
List<ApproachPoint> findApproachPoints(String placementKey);
Optional<DialogueTiming> findDialogueTiming(int questId, String phase);
Optional<QuestRequirementSummary> findQuestRequirements(int questId, String phase);
Optional<QuestRewardSummary> findQuestRewards(int questId);
QuestAutomationPolicy findAutomationPolicy(int questId, int npcId, String actionType);
```

Catalog outputs are advisory. Live validation must still call Server Adapter.

## Server Adapter Interfaces

Required read methods:

```java
LiveAgentSnapshot readAgent(String agentId);
LiveNpcSnapshot findNpcOnMap(int mapId, int npcId);
QuestState readQuestState(String agentId, int questId);
QuestRequirementCheck checkQuestStartRequirements(String agentId, int questId, int npcId);
QuestRequirementCheck checkQuestCompleteRequirements(String agentId, int questId, int npcId);
InventorySnapshot readInventory(String agentId);
boolean hasFreeRewardSlots(String agentId, QuestRewardSummary rewards);
```

Required commit methods:

```java
QuestCommitResult startQuest(String agentId, int questId, int npcId);
QuestCommitResult completeQuest(String agentId, int questId, int npcId, RewardChoice choice);
QuestCommitResult autoCompleteQuest(String agentId, int questId, RewardChoice choice);
```

Normal runtime should not use force APIs. If a debug/test force path is ever
added, it must be separate, gated, audited, and unavailable to normal plans.

## Validation Pipeline

```text
1. Validate command shape.
2. Load catalog action row.
3. Check automation policy and manual-review flags.
4. Read live Agent snapshot.
5. Validate Agent blocking state.
6. Validate map and live NPC presence unless auto-complete.
7. Select/validate approach point when NPC interaction is required.
8. Ask Navigation capability to approach when out of range.
9. Re-read live state after movement.
10. Validate quest state and requirements through Server Adapter.
11. Resolve reward choice when completion grants choice reward.
12. Validate inventory space for rewards.
13. Apply interaction realism delay if enabled.
14. Re-check cancellation/timeout.
15. Re-run commit validation.
16. Commit start/complete/auto-complete through Server Adapter.
17. Return result and emit event.
```

Validation-only stops after step 12 and returns
`VALIDATION_ONLY_SUCCESS` or a structured blocker.

## Approach Integration

When NPC interaction is required:

1. find placement candidates from catalog.
2. choose placement/approach point through Interaction Realism policy.
3. ask Navigation capability to reach selected point.
4. validate live distance/box/range.
5. on failure, try another point if retry policy allows.

The selected point and delay should be returned in the result for audits.

## Dialogue Delay

Delay formula is owned by Interaction Realism, but this package passes:

- quest id.
- phase: start/complete.
- NPC id.
- dialogue timing row.
- Agent profile reading speed.
- repeat-dialogue memory.
- presentation mode.
- attempt.

In deterministic test mode:

```text
delayAppliedMs = 0
```

## Reward Choice

`QuestRewardChoicePolicy`:

```java
RewardChoiceResult chooseReward(
    String agentId,
    int questId,
    QuestRewardSummary rewards,
    InventorySnapshot inventory,
    ProfileDecisionSnapshot profile,
    EconomyValuationSnapshot economy
);
```

If unavailable:

- choose first deterministic reward only when catalog marks the choice safe.
- otherwise return `REWARD_CHOICE_UNRESOLVED`.

## Auto-Complete Policy

`AutoCompleteQuestPolicy` should require:

- quest id explicitly allowlisted by plan/catalog.
- live requirement check passes.
- no manual-review flag.
- plan exit rules allow completion.

Maple Island MVP allowlist:

```text
1030
8023
```

## Maple Island Rule Table

| Rule | Expected Runtime Behavior |
| --- | --- |
| Quest `1046` | `START_QUEST_AT_NPC` allowed; `COMPLETE_QUEST_AT_NPC` returns `FORBIDDEN_ACTION`. |
| Quest `1028` | Completion returns `FORBIDDEN_ACTION`. |
| Quest `8142` | Any start/complete returns `FORBIDDEN_ACTION` for MVP plan. |
| Shanks `22000` quest `1026` | Completion allowed when requirements pass. |
| Shanks `22000` travel | Not owned by this package; Server/Travel capability must block via plan forbidden action. |
| Quests `1018`, `1035` | Return `MANUAL_REVIEW_REQUIRED` unless explicitly enabled. |
| Quests `1030`, `8023` | Auto-complete allowed only via explicit policy. |

## Resume/Reconciliation

Before running an objective after relog/restart:

1. read live quest state.
2. if desired state is already true, return `ALREADY_SATISFIED`.
3. if quest state is impossible for objective, return `QUEST_STATE_MISMATCH`.
4. if completion already happened, do not repeat reward-granting action.

Persisted plan state is advisory. Live quest state wins.

## Events

Publish:

- `NPC_QUEST_VALIDATION_STARTED`
- `NPC_QUEST_VALIDATION_BLOCKED`
- `NPC_QUEST_APPROACH_SELECTED`
- `NPC_QUEST_DELAY_APPLIED`
- `NPC_QUEST_START_COMMITTED`
- `NPC_QUEST_COMPLETE_COMMITTED`
- `NPC_QUEST_AUTO_COMPLETE_COMMITTED`
- `NPC_QUEST_ALREADY_SATISFIED`
- `NPC_QUEST_CANCELLED`
- `NPC_QUEST_TIMED_OUT`
- `NPC_QUEST_SERVER_REJECTED`

Event fields:

- command id.
- agent id.
- plan id.
- objective id.
- quest id.
- NPC id.
- map id.
- placement key.
- approach point id.
- delay applied.
- status.
- reason code.
- changed state summary.

## Tests

Unit tests:

- invalid command returns `INVALID_COMMAND`.
- missing catalog action returns `CATALOG_FACT_MISSING`.
- manual-review action returns `MANUAL_REVIEW_REQUIRED`.
- missing live NPC returns `NPC_MISSING`.
- wrong map returns `QUEST_REQUIREMENT_NOT_MET` or map-specific blocker.
- wrong NPC returns `QUEST_REQUIREMENT_NOT_MET`.
- unmet level/prerequisite/item requirement returns
  `QUEST_REQUIREMENT_NOT_MET`.
- incomplete mob/item progress returns `QUEST_PROGRESS_INCOMPLETE`.
- reward choice missing returns `REWARD_CHOICE_UNRESOLVED`.
- validation-only does not mutate live state.
- cancellation before commit returns `CANCELLED`.
- timeout returns `TIMED_OUT`.

Maple Island tests:

- quest `1046` start allowed.
- quest `1046` complete blocked.
- quest `1028` complete blocked.
- Shanks quest `1026` complete allowed.
- Shanks travel command is not handled here and remains blocked by travel/plan
  forbidden action.
- auto-complete `1030` and `8023` allowed only when explicit policy is present.
- Todd quests return manual review by default.
- relog/restart reconciliation does not duplicate completion rewards.

Integration tests later:

- first three Maple Island quests run through Plan Runtime and this capability.
- Roger apple objective calls Item Use capability before completion.
- Pio reactor objective calls Reactor capability before completion.
- Yoona scripted item grant is visible before completion.
- full route stops at Southperry with `1046` active and incomplete.

## Implementation Gates

Do not implement until:

- Capability Runtime command/result envelope exists.
- Plan Runtime can submit objectives as capability commands.
- Server Adapter can read quest state and commit normal quest start/complete.
- Catalog Runtime can load NPC action and quest requirement rows.
- Navigation capability exposes approach-to-point/NPC command.

Do not enable in live autonomous Agents until:

- Maple Island dry-run validation passes.
- wrong NPC/map/range tests pass.
- Shanks travel forbidden action is tested.
- reward choice blocker is tested.
- resume/reconciliation test prevents duplicate rewards.
