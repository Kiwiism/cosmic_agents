# Capability Runtime Technical Specification

Purpose:

```text
Define DTOs, interfaces, reason codes, validation flow, cancellation, and test
requirements for the common Agent Capability Runtime package.
```

Package name:

```text
agent-capability-runtime
```

This spec is documentation only until Agent reconstruction is stable.

## Package Boundary

The common runtime package owns command routing and shared contracts. Individual
capability packages own implementation details.

Common runtime owns:

- command envelope.
- result envelope.
- validation pipeline.
- capability registry.
- command status tracking.
- cancellation tokens.
- audit/event publication.
- timeout handling.

Specific capability packages own:

- navigation behavior.
- combat behavior.
- loot behavior.
- NPC/quest behavior.
- shop behavior.
- inventory mutation behavior.
- skill-specific behavior.
- recovery decisions.

## Suggested Layout

```text
agent-capability-runtime/
  api/
    CapabilityGateway.java
    CapabilityRegistry.java
    CapabilityHandler.java
    CapabilityValidator.java
    CapabilityStatusReader.java
  model/
    CapabilityCommand.java
    CapabilityCommandId.java
    CapabilityCommandSource.java
    CapabilityFamily.java
    CapabilityStatus.java
    CapabilityResult.java
    CapabilityReasonCode.java
    CapabilityTarget.java
    CapabilityEvidence.java
    CapabilityRetryPolicy.java
    CapabilityTimeoutPolicy.java
  validation/
    StaticCapabilityValidator.java
    LiveCapabilityValidator.java
    CommitCapabilityValidator.java
    ForbiddenActionCapabilityValidator.java
  runtime/
    CapabilityCommandRuntime.java
    CapabilityCommandTracker.java
    CapabilityCancellationToken.java
    CapabilityTimeoutService.java
  events/
    CapabilityEvent.java
    CapabilityEventPublisher.java
  audit/
    CapabilityAuditWriter.java
```

## Core Interfaces

### CapabilityGateway

```java
public interface CapabilityGateway {
    CapabilityResult submit(CapabilityCommand command);
    CapabilityResult cancel(CapabilityCommandId commandId, String reason);
    CapabilityStatusSnapshot status(CapabilityCommandId commandId);
}
```

### CapabilityHandler

```java
public interface CapabilityHandler {
    CapabilityFamily family();
    Set<String> supportedCommandTypes();
    CapabilityResult validate(CapabilityContext context, CapabilityCommand command);
    CapabilityResult execute(CapabilityContext context, CapabilityCommand command);
    CapabilityResult cancel(CapabilityCommandId commandId, String reason);
}
```

### CapabilityRegistry

```java
public interface CapabilityRegistry {
    Optional<CapabilityHandler> find(CapabilityFamily family, String commandType);
    List<CapabilityDeclaration> declarations();
}
```

### CapabilityContext

```java
record CapabilityContext(
    String agentId,
    long nowMs,
    LiveAgentSnapshot live,
    CatalogQuerySession catalog,
    ProfileDecisionSnapshot profile,
    ServerAdapter adapter,
    CapabilityCancellationToken cancellationToken
) {}
```

## Command Envelope

```java
record CapabilityCommand(
    CapabilityCommandId commandId,
    String agentId,
    CapabilityFamily family,
    String commandType,
    CapabilityCommandSource source,
    String planId,
    String objectiveId,
    CapabilityTarget target,
    Map<String, Object> inputs,
    CapabilityRetryPolicy retryPolicy,
    CapabilityTimeoutPolicy timeoutPolicy,
    Map<String, String> auditTags
) {}
```

Command source values:

- `PLAN`
- `LLM`
- `MANUAL`
- `RECOVERY`
- `PROFILE`
- `ECONOMY`
- `TEST`
- `SYSTEM`

## Result Envelope

```java
record CapabilityResult(
    CapabilityCommandId commandId,
    CapabilityStatus status,
    CapabilityReasonCode reasonCode,
    String message,
    boolean liveStateChanged,
    boolean retryable,
    boolean terminal,
    long durationMs,
    CapabilityEvidence evidence
) {}
```

## Status Values

```text
ACCEPTED
VALIDATING
QUEUED
RUNNING
WAITING
SUCCEEDED
FAILED_RETRYABLE
BLOCKED
CANCELLED
TIMED_OUT
CAPABILITY_MISSING
VALIDATION_FAILED
ERROR
```

## Common Reason Codes

General:

- `OK`
- `ALREADY_SATISFIED`
- `CAPABILITY_MISSING`
- `UNSUPPORTED_COMMAND`
- `INVALID_COMMAND`
- `FORBIDDEN_ACTION`
- `LIVE_STATE_MISSING`
- `SERVER_VALIDATION_REJECTED`
- `TIMEOUT`
- `CANCELLED`
- `INTERNAL_ERROR`

Navigation:

- `MISSING_MAP`
- `MISSING_PORTAL`
- `UNREACHABLE`
- `OUT_OF_RANGE`
- `STUCK`
- `ARRIVAL_MISMATCH`

NPC/Quest:

- `NPC_MISSING`
- `NPC_OUT_OF_RANGE`
- `QUEST_NOT_AVAILABLE`
- `QUEST_REQUIREMENT_NOT_MET`
- `QUEST_STATE_MISMATCH`
- `REWARD_CHOICE_REQUIRED`
- `MANUAL_REVIEW_REQUIRED`
- `SCRIPT_SENSITIVE`

Combat/Loot:

- `TARGET_MOB_UNAVAILABLE`
- `NO_SAFE_TARGET`
- `LOW_HP`
- `LOW_MP`
- `DEATH_RISK`
- `REQUIRED_ITEM_MISSING`
- `INVENTORY_FULL`
- `LOOT_UNREACHABLE`

Shop/Economy:

- `SHOP_MISSING`
- `INSUFFICIENT_MESO`
- `BUDGET_BLOCKED`
- `PROTECTED_ITEM`
- `PRICE_REJECTED`

## Validation Flow

```text
submit(command)
  -> assign command id if missing
  -> find handler
  -> static validation
  -> live validation
  -> forbidden-action validation
  -> queue or execute
  -> commit validation before mutation
  -> map handler output to CapabilityResult
  -> emit event and audit
```

Validation must be rerun before committing a mutation if command execution takes
long enough for live state to change.

## Capability Declarations

Each handler declares:

```java
record CapabilityDeclaration(
    CapabilityFamily family,
    String commandType,
    boolean mutatesServerState,
    boolean longRunning,
    boolean cancellable,
    boolean requiresCatalog,
    boolean requiresLiveValidation,
    Set<String> requiredTargetFields,
    Set<String> producedReasonCodes
) {}
```

Plan validation can use declarations to reject unsupported plans early.

## Maple Island MVP Required Commands

Navigation:

```text
NAVIGATE_TO_MAP
NAVIGATE_TO_NPC
NAVIGATE_TO_POINT
USE_PORTAL
VERIFY_ARRIVAL
```

NPC/Quest:

```text
VALIDATE_NPC_INTERACTION
START_QUEST
COMPLETE_QUEST
```

Inventory/Item:

```text
GET_ITEM_COUNT
GET_FREE_SLOTS
USE_ITEM
GRANT_SCRIPTED_TEST_ITEM
```

Combat/Loot:

```text
KILL_MOB_COUNT
FARM_ITEM_COUNT
COLLECT_LOOT
```

Recovery:

```text
RECOVER_HP_MP
RETURN_TO_SAFE_POINT
BLOCK_WITH_REASON
```

Reactor:

```text
TRIGGER_REACTOR_FOR_ITEMS
```

Unsupported commands should return `CAPABILITY_MISSING` until implemented.

## Cancellation Model

`CapabilityCancellationToken`:

```java
public interface CapabilityCancellationToken {
    boolean isCancelled();
    String reason();
    void throwIfCancelled();
}
```

Long-running handlers must check cancellation:

- before each route segment.
- before each attack batch.
- before each loot sweep.
- before opening shop/NPC interaction.
- before committing quest or inventory mutation.

## Timeout Model

Timeout policy:

```java
record CapabilityTimeoutPolicy(
    long hardTimeoutMs,
    long noProgressTimeoutMs,
    long heartbeatIntervalMs
) {}
```

Handlers should update progress heartbeat. If no progress is reported within
`noProgressTimeoutMs`, return `TIMED_OUT` or `FAILED_RETRYABLE` depending on
policy.

## Audit Events

Event types:

- `CAPABILITY_COMMAND_RECEIVED`
- `CAPABILITY_VALIDATION_FAILED`
- `CAPABILITY_STARTED`
- `CAPABILITY_PROGRESS`
- `CAPABILITY_SUCCEEDED`
- `CAPABILITY_BLOCKED`
- `CAPABILITY_FAILED_RETRYABLE`
- `CAPABILITY_CANCELLED`
- `CAPABILITY_TIMED_OUT`
- `CAPABILITY_ERROR`

Event payload:

- command id.
- agent id.
- family.
- command type.
- source.
- plan id.
- objective id.
- target ids.
- status.
- reason code.
- duration.
- evidence summary.

## Evidence Model

Evidence should be structured and bounded:

```java
record CapabilityEvidence(
    Map<String, Integer> ids,
    Map<String, Long> counts,
    Map<String, String> facts,
    List<String> warnings
) {}
```

Examples:

- current map id.
- target map id.
- NPC id.
- quest id.
- item id.
- current item count.
- required item count.
- HP/MP snapshot.
- selected portal id.
- selected approach point id.

## Tests

Common runtime tests:

- missing handler returns `CAPABILITY_MISSING`.
- invalid command returns `INVALID_COMMAND`.
- forbidden action blocks before handler execution.
- cancellation clears tracked command.
- timeout returns `TIMED_OUT`.
- handler exception maps to `ERROR` and emits audit event.
- live validation reruns before mutation.
- audit event contains agent id, plan id, objective id, status, and reason.

Maple Island integration tests later:

- wrong NPC quest start returns `QUEST_REQUIREMENT_NOT_MET` or
  `QUEST_STATE_MISMATCH`.
- Shanks travel returns `FORBIDDEN_ACTION`.
- Shanks quest completion for `1026` is allowed.
- inventory full returns `INVENTORY_FULL`.
- missing NPC returns `NPC_MISSING`.

## Implementation Gates

Do not implement until:

- reconstructed Agent runtime has stable runtime entry/identity interfaces.
- server adapter read-only snapshots exist.
- plan runtime can submit commands through the gateway.
- event bus or temporary audit writer exists.

Do not enable LLM direct capability commands until:

- command permissions exist.
- rate limits exist.
- cancellation works.
- every mutating command has live and commit validation.
