# Background Action Runtime Technical Specification

Purpose:

```text
Define interfaces, DTOs, execution flow, virtual state, reconciliation,
fairness budgets, audit events, and tests for the future Background Action
Runtime package.
```

Package name:

```text
agent-background-action-runtime
```

This specification is documentation only until Agent reconstruction is stable.

## Package Boundary

Background Action Runtime owns:

- background action routing.
- background navigation execution.
- background combat execution.
- background loot resolution.
- virtual loot/meso buffers.
- inventory reconciliation requests.
- background NPC/quest/shop execution wrappers.
- fairness budget checks.
- background action journals.
- strict debug comparison hooks.

It does not own:

- simulation tier decisions.
- plan selection.
- capability validation rules.
- catalog building.
- profile storage.
- economy price modeling.
- direct unvalidated server mutation.

## Suggested Layout

```text
agent-background-action-runtime/
  api/
    BackgroundActionRouter.java
    BackgroundNavigationResolver.java
    BackgroundCombatResolver.java
    BackgroundLootResolver.java
    BackgroundNpcQuestExecutor.java
    BackgroundShopExecutor.java
    BackgroundInventoryReconciler.java
    BackgroundFairnessBudgetService.java
    BackgroundActionJournal.java
  model/
    BackgroundActionRequest.java
    BackgroundActionDecision.java
    BackgroundActionResult.java
    BackgroundActionKind.java
    BackgroundExecutionMode.java
    VirtualAgentState.java
    VirtualRouteState.java
    VirtualCombatState.java
    VirtualLootBuffer.java
    VirtualInventoryMutation.java
    ReconciliationRequest.java
    ReconciliationResult.java
    BackgroundFairnessBudget.java
    BackgroundActionViolation.java
  runtime/
    BackgroundActionRuntime.java
    VirtualStateStore.java
    LootBufferStore.java
    BackgroundCommitQueue.java
    StrictDebugComparator.java
  audit/
    BackgroundActionAuditEvent.java
    BackgroundReconciliationEvent.java
    BackgroundViolationEvent.java
```

## Core Router

```java
public interface BackgroundActionRouter {
    BackgroundActionDecision decide(BackgroundActionRequest request);
    BackgroundActionResult execute(BackgroundActionRequest request);
}
```

## Request DTO

```java
record BackgroundActionRequest(
    String agentId,
    String planId,
    String objectiveId,
    String capability,
    BackgroundActionKind actionKind,
    SimulationMode simulationMode,
    AllowedShortcutSet allowedShortcuts,
    PerceptionSnapshot perception,
    AgentProfileSummary profile,
    CatalogContext catalog,
    long nowMs
) {}
```

## Action Kinds

```java
enum BackgroundActionKind {
    NAVIGATE_ROUTE,
    NAVIGATE_SAME_MAP,
    COMBAT_SLICE,
    LOOT_PICKUP,
    NPC_QUEST_START,
    NPC_QUEST_COMPLETE,
    SHOP_BUY,
    SHOP_SELL,
    INVENTORY_RECONCILE,
    RECOVERY_REST,
    RECOVERY_DEATH,
    PLAN_SLICE
}
```

## Decision DTO

```java
record BackgroundActionDecision(
    boolean allowed,
    BackgroundExecutionMode executionMode,
    List<String> requiredValidations,
    List<String> requiredReconciliations,
    List<String> reasons,
    List<BackgroundActionViolation> violations
) {}
```

Execution modes:

```java
enum BackgroundExecutionMode {
    DENIED,
    PRESENTATION_REQUIRED,
    BACKGROUND_ACTIVE_MINIMAL,
    BACKGROUND_ABSTRACT,
    STRATEGIC_SLICE
}
```

## Result DTO

```java
record BackgroundActionResult(
    boolean success,
    String resultCode,
    VirtualAgentState virtualState,
    List<VirtualInventoryMutation> pendingMutations,
    ReconciliationResult reconciliation,
    long elapsedVirtualMs,
    List<String> auditReasons,
    List<String> warnings
) {}
```

Result codes:

- `COMPLETED`
- `SCHEDULED`
- `PARTIAL_PROGRESS`
- `REQUIRES_PRESENTATION`
- `REQUIRES_RECONCILIATION`
- `BLOCKED_BY_VALIDATION`
- `BLOCKED_BY_FAIRNESS_BUDGET`
- `BLOCKED_BY_SENSITIVE_MAP`
- `FAILED_CLOSED`

## Virtual Agent State

```java
record VirtualAgentState(
    String agentId,
    int mapId,
    Point virtualPosition,
    Integer virtualFootholdId,
    VirtualRouteState route,
    VirtualCombatState combat,
    VirtualLootBuffer lootBuffer,
    long updatedAtMs,
    List<String> flags
) {}
```

Virtual state must not retain raw `Character`, `MapleMap`, `MapObject`, or
packet references.

## Background Navigation

```java
public interface BackgroundNavigationResolver {
    BackgroundActionResult routeTo(BackgroundNavigationRequest request);
    BackgroundActionResult sameMapMoveTo(BackgroundNavigationRequest request);
}
```

Same-map first-version ETA:

```text
dx = abs(target.x - current.x)
dy = abs(target.y - current.y)
effectivePx = dx + dy * 2
if target.y < current.y: effectivePx *= 1.25
if currentFoothold != targetFoothold: effectivePx += 400
etaMs = effectivePx / speedPxPerSec * 1000
etaMs *= seededRandom(0.85, 1.25)
etaMs = clamp(500, 30000)
```

Commit rules:

- virtual position can update during background movement.
- real server position commits at arrival, materialization, or validation
  boundary.
- invalid destination fails closed and returns blocker.

## Background Combat

```java
public interface BackgroundCombatResolver {
    BackgroundCombatResult resolveSlice(BackgroundCombatRequest request);
}
```

Combat slice inputs:

- agent level/job/stats/equipment summary.
- skill/action profile.
- mob id/count/hp/defense/avoidability.
- expected spawn count and region crowding.
- active/future quest relevance.
- potion/rest policy.
- fairness budget.

Combat slice outputs:

- virtual kills.
- EXP.
- meso.
- drops.
- quest kill/item progress.
- potion/MP consumption.
- death/recovery event.
- next slice recommendation.

Abstract combat must use the same baseline combat formulas where practical.
Any simplified formula must be calibrated and strict-debug comparable.

## Background Loot

```java
public interface BackgroundLootResolver {
    VirtualLootBuffer resolveDrops(BackgroundLootRequest request);
}
```

Roll policy:

```text
common drops:
  expected value with controlled variance

uncommon drops:
  batched rolls

rare drops, equips, scrolls, event items:
  explicit individual roll and journal event
```

Loot buffer:

```java
record VirtualLootBuffer(
    Map<Integer, Integer> itemCounts,
    int mesos,
    List<VirtualInventoryMutation> rareItems,
    List<String> reservationWarnings,
    long updatedAtMs
) {}
```

## Inventory Reconciliation

```java
public interface BackgroundInventoryReconciler {
    ReconciliationResult reconcile(ReconciliationRequest request);
}
```

Reconciliation request:

```java
record ReconciliationRequest(
    String agentId,
    String reason,
    VirtualLootBuffer lootBuffer,
    List<VirtualInventoryMutation> pendingMutations,
    boolean strictCapacity,
    long nowMs
) {}
```

Reconciliation must:

1. reload/validate current inventory summary through Server Adapter.
2. apply item reservation policy.
3. stack common items where possible.
4. create real item objects for rare/equip/scroll items.
5. handle overflow through configured policy.
6. emit committed/discarded mutation summary.

Overflow policies:

- `FAIL_CLOSED`
- `SELL_TRASH_THEN_RETRY`
- `DROP_LOW_VALUE_AGENT_ONLY`
- `POSTPONE_AND_RECOVER`

For early implementation, prefer `FAIL_CLOSED` or `POSTPONE_AND_RECOVER`.

## Background NPC/Quest

```java
public interface BackgroundNpcQuestExecutor {
    BackgroundActionResult startQuest(BackgroundNpcQuestRequest request);
    BackgroundActionResult completeQuest(BackgroundNpcQuestRequest request);
}
```

Must validate:

- NPC exists in current map or reachable interaction map.
- Agent has arrived/materialized enough for range policy.
- quest state allows start/complete.
- level/prequest/item/meso requirements pass.
- reward inventory capacity is available.
- script is safe for direct action.

If script is not direct-safe, return `REQUIRES_PRESENTATION` or
`BLOCKED_BY_VALIDATION`.

## Background Shop

```java
public interface BackgroundShopExecutor {
    BackgroundActionResult buy(BackgroundShopRequest request);
    BackgroundActionResult sell(BackgroundShopRequest request);
}
```

Must reconcile inventory before any transaction that depends on buffered items.

Sell logic must consult:

- current quest reservations.
- likely future quest reservations.
- crafting profile.
- economy value.
- profile carelessness.
- item locks.

## Fairness Budget

```java
public interface BackgroundFairnessBudgetService {
    FairnessDecision check(BackgroundFairnessRequest request);
    void record(BackgroundFairnessUsage usage);
}
```

Budget dimensions:

- EXP/hour.
- meso/hour.
- rare rolls/hour.
- quest completions/hour.
- shop transactions/hour.
- map-region crowding.
- background action CPU quota.

Budget decision:

- `ALLOW`
- `ALLOW_WITH_PENALTY`
- `DELAY`
- `DENY`

## Materialization Interaction

Background Action Runtime does not choose global mode. It cooperates with the
Simulation Tier materialization plan by:

- pausing active background actions.
- resolving or canceling current slice.
- reconciling required virtual state.
- providing candidate visible positions.
- reporting pending mutations.
- failing closed when validation fails.

## Strict Debug Comparator

Strict mode should compare sampled background outcomes against closer-to-real
execution.

Compare:

- route ETA versus real movement duration.
- abstract combat result versus presentation combat result.
- buffered loot versus explicit per-kill drops.
- buffered inventory reconciliation versus immediate inventory mutation.
- background quest/shop action versus presentation capability path.

Strict mode outputs warnings and calibration data. It should not run for all
Agents in normal 2000-Agent mode.

## Audit Events

Emit:

- background action decision.
- background action start.
- background action complete.
- denied background action.
- virtual state mutation.
- loot buffer update.
- reconciliation start/complete/failure.
- fairness budget delay/deny.
- rare event roll.
- strict-debug mismatch.
- fail-closed event.

Minimum fields:

- agent id.
- plan/objective id.
- action kind.
- simulation mode.
- map id.
- virtual elapsed time.
- committed mutations.
- discarded mutations.
- savings estimate.
- reason codes.

## Tests

Unit tests:

- background action is denied in `PRESENTATION`.
- background action is denied without matching allowed shortcut.
- same-map ETA is deterministic for same seed and bounded.
- rare drops use explicit roll path.
- common drops can use expected-value buffer.
- reconciliation fails closed on full inventory.
- sell action respects quest item reservation.
- fairness budget can delay excessive EXP/meso/rare rolls.
- virtual state never stores raw server object references.

Integration tests later:

- background navigation materializes at valid foothold.
- background combat produces valid kill/EXP/loot progress.
- player entering map pauses background action and triggers materialization.
- background NPC quest start/complete validates requirements.
- background shop sell reconciles inventory first.
- 1000-Agent background abstract soak records savings.
- strict debug comparison reports acceptable error bounds.

## Implementation Gates

Do not implement live integration until:

- reconstructed Agent scheduler/capability boundaries are stable.
- Simulation Tier Runtime provides allowed shortcut decisions.
- Server Adapter can validate and commit mutations safely.
- Catalog Platform exposes map/mob/drop/NPC/shop/quest indexes.
- Perception Runtime provides bounded snapshots.
- Observability can record background action metrics.
- Recovery Policy can handle fail-closed blockers.
