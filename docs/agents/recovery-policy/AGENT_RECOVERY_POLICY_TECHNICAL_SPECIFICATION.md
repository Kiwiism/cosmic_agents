# Agent Recovery Policy Technical Specification

Purpose:

```text
Define interfaces, DTOs, decision flow, reason codes, memory model, event
emission, and tests for the future Agent Recovery Policy package.
```

Package name:

```text
agent-recovery-policy
```

This spec is documentation only until Agent reconstruction is stable.

## Package Boundary

Recovery Policy owns:

- blocker classification.
- retry/postpone/fail decision.
- recovery recommendation.
- bounded failure memory.
- recovery events.

Recovery Policy does not own:

- direct server mutation.
- capability execution.
- plan objective completion.
- inventory item protection rules.
- economy valuation.
- LLM calls.

## Suggested Layout

```text
agent-recovery-policy/
  api/
    AgentRecoveryPolicy.java
    AgentRecoveryDecisionService.java
    AgentRecoveryMemoryStore.java
  model/
    RecoveryRequest.java
    RecoveryDecision.java
    RecoveryDecisionType.java
    RecoveryReasonCode.java
    RecoveryEvidence.java
    RecoveryContextSnapshot.java
    RecoveryMemory.java
    RecoveryActionProposal.java
  policy/
    LowHpRecoveryPolicy.java
    DeathLoopRecoveryPolicy.java
    NavigationStuckRecoveryPolicy.java
    NpcQuestRecoveryPolicy.java
    InventoryFullRecoveryPolicy.java
    ResourceShortageRecoveryPolicy.java
    NoTargetRecoveryPolicy.java
  events/
    RecoveryEvent.java
```

## Core Interface

```java
public interface AgentRecoveryPolicy {
    RecoveryDecision decide(RecoveryRequest request);
}
```

## Recovery Request

```java
record RecoveryRequest(
    String agentId,
    String profileId,
    String planId,
    String objectiveId,
    String capabilityFamily,
    String commandType,
    String capabilityReasonCode,
    RecoveryContextSnapshot context,
    RecoveryMemory memory,
    Map<String, String> planConstraints,
    Map<String, Object> evidence
) {}
```

## Recovery Context Snapshot

```java
record RecoveryContextSnapshot(
    Integer mapId,
    Integer hp,
    Integer maxHp,
    Integer mp,
    Integer maxMp,
    Integer meso,
    boolean dead,
    boolean inBlockingState,
    boolean inventoryFull,
    boolean hasUsablePotion,
    boolean hasSafeRestOption,
    boolean serverUnderPressure,
    String simulationMode,
    String profileRiskTolerance,
    String planFocusLevel
) {}
```

## Recovery Decision

```java
record RecoveryDecision(
    RecoveryDecisionType decisionType,
    RecoveryReasonCode reasonCode,
    String message,
    boolean retryable,
    boolean terminalForObjective,
    boolean terminalForPlan,
    long delayMs,
    RecoveryActionProposal actionProposal,
    RecoveryEvidence evidence
) {}
```

## Decision Types

```java
enum RecoveryDecisionType {
    RETRY_SAME_ACTION,
    RETRY_WITH_ALTERNATE_TARGET,
    REFRESH_LIVE_STATE,
    REST_UNTIL_SAFE,
    USE_ITEM,
    RETREAT_TO_SAFE_MAP,
    RESUPPLY,
    CLEAR_INVENTORY,
    ASK_FOR_HELP,
    POSTPONE_OBJECTIVE,
    SIDETRACK_PLAN,
    FAIL_OBJECTIVE,
    FAIL_PLAN,
    REQUEST_HUMAN_REVIEW,
    REQUEST_LLM_REPLAN
}
```

## Reason Codes

```text
LOW_HP
LOW_MP
NO_POTIONS
NO_MESO
DEAD
DEATH_LOOP
NAVIGATION_STUCK
MISSING_PORTAL
NPC_MISSING
NPC_UNREACHABLE
QUEST_REQUIREMENT_NOT_MET
QUEST_PROGRESS_INCOMPLETE
INVENTORY_FULL
REWARD_CHOICE_UNRESOLVED
TARGET_MOB_UNAVAILABLE
NO_SAFE_TARGET
DROP_DRY_STREAK
SERVER_PRESSURE
FORBIDDEN_ACTION
MANUAL_REVIEW_REQUIRED
SCRIPT_SENSITIVE
CAPABILITY_MISSING
CATALOG_FACT_MISSING
TIMEOUT
CANCELLED
UNKNOWN_BLOCKER
```

## Recovery Memory

```java
record RecoveryMemory(
    int objectiveAttempts,
    int capabilityAttempts,
    int deathCountForPlan,
    int deathCountWindow,
    int timeoutCount,
    int noTargetCount,
    int dryStreakCount,
    Set<String> failedRouteKeys,
    Set<String> failedApproachPointKeys,
    List<String> recentDecisionTypes,
    long updatedAtMs
) {}
```

Memory limits:

- cap failed route keys.
- cap failed approach points.
- cap recent decisions.
- expire old windows.
- never store raw server objects.

## Action Proposal

```java
record RecoveryActionProposal(
    String capabilityFamily,
    String commandType,
    Map<String, Object> inputs,
    String sidetrackPlanId,
    String explanation
) {}
```

Examples:

- `ITEM_USE / USE_POTION`
- `NAVIGATION / NAVIGATE_TO_SAFE_MAP`
- `RECOVERY / REST_UNTIL_HP`
- `INVENTORY / CLEAR_SAFE_TRASH`
- `PLAN / POSTPONE_OBJECTIVE`

## Decision Flow

```text
1. Normalize capability reason code into recovery reason.
2. Load bounded recovery memory.
3. Check hard forbidden/manual-review/script-sensitive blockers.
4. Check death and low HP/MP safety.
5. Check inventory and resource blockers.
6. Check navigation and NPC approach blockers.
7. Check repeated no-target/dry-streak blockers.
8. Apply plan focus and hard constraints.
9. Apply profile tolerance within safety limits.
10. Produce recovery decision.
11. Update recovery memory.
12. Emit recovery decision event.
```

## Maple Island MVP Default Thresholds

These are initial test defaults, not global server config:

```text
maxObjectiveAttempts = 3
maxDeathCountForPlan = 3
maxNpcApproachAttempts = 3
maxNavigationRetries = 3
restUntilHpPercent = 80
restUntilMpPercent = 50
combatPostponeIfNoPotionAndHpBelowPercent = 35
```

For the first deterministic run:

- no sell-trash recovery.
- no economy recovery.
- no Shanks travel.
- no force quest recovery.

## Event Emission

Publish:

- `RECOVERY_DECISION_MADE`
- `RECOVERY_ACTION_PROPOSED`
- `RECOVERY_ACTION_COMPLETED`
- `RECOVERY_ACTION_FAILED`
- `RECOVERY_OBJECTIVE_POSTPONED`
- `RECOVERY_PLAN_FAILED`
- `RECOVERY_HUMAN_REVIEW_REQUIRED`

Fields:

- agent id.
- plan id.
- objective id.
- original capability reason.
- recovery reason.
- decision type.
- thresholds used.
- memory counters.
- profile influence summary.

## Tests

Unit tests:

- low HP with potion proposes `USE_ITEM`.
- low HP without potion proposes `REST_UNTIL_SAFE`.
- repeated death returns `FAIL_PLAN` or `POSTPONE_OBJECTIVE`.
- navigation stuck tries alternate target before blocking.
- NPC missing returns terminal objective blocker after refresh attempt.
- inventory full in Maple Island MVP blocks instead of selling trash.
- forbidden action returns terminal blocker.
- manual-review returns human-review decision.
- profile risk changes retry count within bounds.
- memory caps failed approach points.

Integration tests later:

- Maple Island combat objective rests when out of potions.
- repeated missing NPC blocks with `NPC_MISSING`, not exception.
- repeated no target alternates safe map or blocks.
- recovery decision emits event and objective journal entry.
- relog/restart keeps recovery memory bounded and useful.

## Implementation Gates

Do not implement live integration until:

- Capability Runtime reason codes are stable.
- Plan Runtime can consume recovery decisions.
- Event Bus can receive recovery events.
- Profile snapshot API exists.
- live state snapshots exist through Server Adapter.
