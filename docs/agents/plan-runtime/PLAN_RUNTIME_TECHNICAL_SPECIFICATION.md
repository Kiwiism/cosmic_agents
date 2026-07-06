# Plan Runtime Technical Specification

Purpose:

```text
Define the implementation shape for the portable Plan Runtime package after the
Agent reconstruction exposes stable runtime and capability boundaries.
```

Package name:

```text
agent-plan-runtime
```

This spec is documentation only. Do not implement it in live Agent runtime until
reconstruction is stable.

## Package Boundary

The package should be portable and should not import Cosmic classes directly.
All server facts and mutations flow through interfaces.

Allowed dependencies:

- catalog query API.
- profile decision API.
- capability command/result API.
- server adapter DTOs.
- event bus.
- clock/random abstractions.

Forbidden dependencies:

- `server.bots.*`
- `server.agents.runtime.*` concrete classes, except through stable post-
  reconstruction interfaces.
- `client.Character`
- `server.maps.MapleMap`
- packet classes.
- script manager classes.

## Suggested Layout

```text
agent-plan-runtime/
  api/
    PlanRuntime.java
    PlanAssignmentRequest.java
    PlanRuntimeResult.java
    PlanRuntimeSnapshot.java
  model/
    PlanCard.java
    PlanObjective.java
    PlanEntryCriteria.java
    PlanExitCriteria.java
    PlanFocusPolicy.java
    PlanRetryPolicy.java
    PlanSidetrackPolicy.java
    PlanForbiddenAction.java
    PlanProgress.java
    ObjectiveProgress.java
    ObjectiveResult.java
    ObjectiveBlocker.java
  repository/
    PlanCardRepository.java
    JsonPlanCardRepository.java
    PlanSchemaValidator.java
  state/
    PlanStateStore.java
    InMemoryPlanStateStore.java
    PersistentPlanStateStore.java
  scheduler/
    PlanScheduler.java
    ObjectiveSelector.java
    SidetrackStack.java
  resolver/
    ObjectiveResolver.java
    CapabilityCommandBuilder.java
    CapabilityResultMapper.java
  policy/
    EntryCriteriaValidator.java
    ExitCriteriaValidator.java
    ForbiddenActionValidator.java
    RetryPolicyEvaluator.java
  events/
    PlanEvent.java
    PlanEventPublisher.java
  journal/
    PlanDecisionJournalWriter.java
```

## Core Interfaces

### PlanRuntime

```java
public interface PlanRuntime {
    PlanRuntimeResult assign(PlanAssignmentRequest request);
    PlanRuntimeResult tick(String agentId);
    PlanRuntimeResult pause(String agentId, String planId, String reason);
    PlanRuntimeResult resume(String agentId, String planId);
    PlanRuntimeResult cancel(String agentId, String planId, String reason);
    PlanRuntimeSnapshot snapshot(String agentId);
}
```

### PlanCardRepository

```java
public interface PlanCardRepository {
    Optional<PlanCard> findById(String planId);
    List<PlanCardSummary> listAvailable(PlanQuery query);
    PlanValidationReport validate(String planId);
}
```

### PlanStateStore

```java
public interface PlanStateStore {
    Optional<PlanProgress> loadActive(String agentId);
    void save(PlanProgress progress);
    void appendEvent(PlanEvent event);
    List<PlanEvent> recentEvents(String agentId, int limit);
}
```

### ObjectiveResolver

```java
public interface ObjectiveResolver {
    ObjectiveResolution resolve(
        PlanRuntimeContext context,
        PlanCard plan,
        PlanObjective objective
    );
}
```

### Capability Router

The Plan Runtime should depend on a small command gateway, not concrete
capability classes:

```java
public interface CapabilityCommandGateway {
    CapabilityCommandResult submit(CapabilityCommand command);
    CapabilityCommandStatus status(String commandId);
    void cancel(String commandId, String reason);
}
```

## Required DTOs

### PlanRuntimeContext

```java
record PlanRuntimeContext(
    String agentId,
    String profileId,
    long nowMs,
    LiveAgentSnapshot live,
    ProfileDecisionSnapshot profile,
    CatalogQuerySession catalog
) {}
```

`LiveAgentSnapshot` should be a portable adapter DTO, not a `Character`.

### PlanProgress

```java
record PlanProgress(
    String agentId,
    String planId,
    PlanStatus status,
    String currentObjectiveId,
    List<String> completedObjectiveIds,
    Map<String, ObjectiveProgress> objectives,
    List<SidetrackFrame> sidetrackStack,
    int totalRetries,
    int deathCount,
    long assignedAtMs,
    long updatedAtMs,
    long planVersion
) {}
```

### ObjectiveProgress

```java
record ObjectiveProgress(
    String objectiveId,
    ObjectiveStatus status,
    int attempts,
    String activeCommandId,
    String lastReasonCode,
    String lastMessage,
    long startedAtMs,
    long updatedAtMs
) {}
```

### ObjectiveResult

```java
record ObjectiveResult(
    ObjectiveStatus status,
    String reasonCode,
    String message,
    boolean liveStateChanged,
    boolean retryable,
    boolean requiresRecovery,
    boolean requiresReplan,
    Map<String, Object> evidence
) {}
```

## Objective Selection Algorithm

Per tick:

```text
1. Load active PlanProgress.
2. Refresh live snapshot through Server Adapter.
3. If active command exists, poll command status.
4. Map command result to objective result.
5. Update objective state and emit events.
6. Evaluate exit criteria.
7. If complete, close plan.
8. If blocked, evaluate retry/recovery/sidetrack policy.
9. Select next eligible objective by objective mode.
10. Validate forbidden actions and live prerequisites.
11. Resolve objective into capability command.
12. Submit command and persist active command id.
```

No step may directly mutate server state except capability command submission.

## Objective Eligibility

An objective is eligible when:

- all required dependencies are complete.
- objective is not complete.
- objective is not terminally blocked.
- plan focus policy allows it.
- live state does not already satisfy it.
- forbidden action validation passes.
- required catalog facts are available or marked optional.

If live state already satisfies the objective, mark it `completed` with reason
`ALREADY_SATISFIED`.

## Persistence

Minimum persistence:

- active plan id.
- plan version/hash.
- current objective id.
- objective statuses.
- active capability command id.
- retry counts.
- sidetrack stack.
- terminal blockers.
- decision journal ids.

Storage options:

- MVP: local JSON or server-side table behind `PlanStateStore`.
- Later: separate Agent database or portable profile/plan store.

The Plan Runtime should tolerate missing or stale active command ids by
reconciling against live state.

## Events

Publish events for:

- `PLAN_ASSIGNED`
- `PLAN_STARTED`
- `PLAN_PAUSED`
- `PLAN_RESUMED`
- `PLAN_CANCELLED`
- `PLAN_COMPLETED`
- `PLAN_FAILED`
- `OBJECTIVE_SELECTED`
- `OBJECTIVE_STARTED`
- `OBJECTIVE_ALREADY_SATISFIED`
- `OBJECTIVE_COMPLETED`
- `OBJECTIVE_BLOCKED`
- `OBJECTIVE_RETRIED`
- `SIDETRACK_STARTED`
- `SIDETRACK_COMPLETED`
- `FORBIDDEN_ACTION_BLOCKED`

Events should include:

- agent id.
- plan id.
- objective id when applicable.
- reason code.
- live evidence summary.
- profile influence summary when applicable.
- catalog source ids when applicable.

## Decision Journal

For decisions that are not purely deterministic, record:

- selected objective.
- alternatives considered.
- reason codes.
- profile weights.
- catalog facts used.
- live state used.
- economy signal if used.
- LLM directive if used.
- expected risk/cost.
- outcome.

The decision journal belongs to profile/observability storage, but Plan Runtime
should emit enough structured data to create it.

## Validation

Plan validation checks:

- valid JSON schema version.
- unique objective ids.
- dependency ids exist.
- no dependency cycles.
- exit criteria are machine-checkable.
- forbidden actions have action type and target.
- objective kinds are supported by installed capability declarations.
- required catalog hints resolve if catalog is available.
- Maple Island MVP forbids Shanks travel while allowing Shanks quest completion.

Runtime validation checks:

- entry criteria.
- live agent exists.
- plan is allowed by profile hard constraints.
- objective does not violate forbidden actions.
- capability is installed.
- capability validator accepts the command.

## Maple Island MVP First Slice

First implementation order:

1. Load `docs/agents/plans/maple-island-mvp.plan.json`.
2. Validate schema and forbidden actions.
3. Assign plan to one Agent in deterministic mode.
4. Implement objective state model and progress snapshot.
5. Resolve only these objective kinds first:
   - `quest-start`
   - `quest-complete`
   - `use-item`
   - `navigate-through`
   - `reactor-box-items`
   - `grant-scripted-item`
   - `stop-plan`
6. Add unsupported objective handling as `CAPABILITY_MISSING`, not exception.
7. Add resume after relog/restart.
8. Add full-route test.

## LLM Command Bridge

The LLM should be able to call:

```text
assignPlan(agentId, planId)
pausePlan(agentId, planId, reason)
resumePlan(agentId, planId)
cancelPlan(agentId, planId, reason)
requestSidetrack(agentId, sidetrackPlan)
navigateToPoint(agentId, mapId, x, y, reason)
getPlanSnapshot(agentId)
```

Every command goes through Plan Runtime policy and Server Adapter validation.

`navigateToPoint` should be represented as a temporary sidetrack objective so it
uses the same safety, audit, timeout, and resume behavior as normal plans.

## Tests

Unit tests:

- JSON plan loads.
- duplicate objective id rejected.
- dependency cycle rejected.
- missing dependency rejected.
- forbidden action blocks command before capability dispatch.
- already-satisfied objective completes without command.
- retry limit blocks objective.
- sidetrack returns to main plan.
- unknown objective kind returns `CAPABILITY_MISSING`.

Maple Island tests:

- Shanks travel is blocked.
- Shanks quest complete for `1026` is allowed.
- `1046` start-only rule is respected.
- `1028` completion is blocked.
- final map must be Southperry.
- resume keeps completed objectives completed.

## Observability

Expose:

- active plan.
- current objective.
- objective status counts.
- last blocker.
- last capability command.
- sidetrack stack.
- retry counts.
- plan age.
- time spent in current objective.
- last decision reason.

This data should feed future Agent Console and `agent-observability`.

## Implementation Gates

Do not implement live runtime until:

- Agent reconstruction exposes stable runtime entry points.
- Capability command/result model exists.
- Server Adapter read-only snapshots exist.
- Catalog loader can provide required Maple Island facts.
- Plan JSON schema is finalized.

Do not enable LLM plan commands until:

- permission and rate-limit policy exists.
- audit logging exists.
- command cancellation exists.
- forbidden action validation is tested.
