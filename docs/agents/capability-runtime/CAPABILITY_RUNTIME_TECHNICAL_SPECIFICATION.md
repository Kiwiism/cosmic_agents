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
- active capability frame state.
- paused parent-frame stack for explicit handoff/resume.
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
    CapabilityFrame.java
    CapabilityHandoff.java
    CapabilityResumeToken.java
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
    CapabilityFrameStack.java
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
    CapabilityResult tick(CapabilityContext context, CapabilityFrame frame);
    CapabilityResult cancel(CapabilityCommandId commandId, String reason);
}
```

Handlers should be tick-oriented for long-running actions. A command that can
complete immediately may return `SUCCEEDED` from its first tick.

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

Portable JSON contracts:

- `docs/agents/capability-runtime/capability-command.schema.json`
- `docs/agents/capability-runtime/capability-result.schema.json`

These schemas define the data envelope shared by Plan Runtime, LLM command
gateway, tests, and future capability packages. They do not implement any live
capability behavior.

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
    CapabilityEvidence evidence,
    CapabilityHandoff handoff
) {}
```

`handoff` must be null unless `status == NEEDS_CAPABILITY`.

## Frame And Handoff Model

The runtime stores one active frame per Agent and a bounded stack of paused
parent frames.

```java
record CapabilityFrame(
    CapabilityCommand command,
    String phase,
    Map<String, Object> localState,
    int attempt,
    long startedAtMs,
    long lastProgressAtMs,
    CapabilityResult lastChildResult
) {}
```

```java
record CapabilityHandoff(
    CapabilityCommand childCommand,
    CapabilityResumeToken resumeToken,
    boolean required
) {}
```

```java
record CapabilityResumeToken(
    CapabilityCommandId parentCommandId,
    String resumePhase,
    Map<String, Object> parentLocalState
) {}
```

Runtime behavior:

```text
active frame returns NEEDS_CAPABILITY with handoff
  -> push parent frame using resume token
  -> active frame = child command frame
child frame returns terminal result
  -> pop parent frame
  -> set parent.lastChildResult = child result
  -> resume parent at resumePhase
parent returns SUCCEEDED/BLOCKED/FAILED
  -> Plan Runtime advances, retries, or blocks
```

Only the runtime mutates the active-frame stack. Capability handlers must return
handoff requests, not call other handlers directly.

## Status Values

```text
ACCEPTED
VALIDATING
QUEUED
RUNNING
WAITING
NEEDS_CAPABILITY
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
  -> create active frame or reject if an incompatible frame is active
  -> tick active frame
  -> if NEEDS_CAPABILITY, push parent and start child frame
  -> if child terminal, resume parent with child result
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

## Primitive Capability Wrapper Commands

The first post-reconstruction gameplay implementation must wrap existing
nutnnut/reconstructed behavior before adding quest-specific constraints.

Navigation primitive:

```text
NAVIGATE_TO_POINT
NAVIGATE_TO_MAP
NAVIGATE_TO_NPC
NAVIGATE_TO_REACTOR
```

Implementation rule:

```text
call existing AgentMovementTickRuntime / AgentNavigationTargetService with the
same BotEntry, Character, target point, runAiTick, unstuck flag, stop distance,
movement config, and runtime state objects used by legacy mode ticks.
```

Combat primitive:

```text
COMBAT_TICK_LEGACY
COMBAT_ALLOWED_MOBS
```

Implementation rule:

```text
first wrap existing AgentGrindModeTickService and attack planning/execution
without changing scoring, timing, cooldown, ammo, AoE, or movement target
behavior. Add allowed-mob and quest stop conditions only after parity tests
pass.
```

Required parity tests:

- legacy movement tick versus NavigationCapability tick for the same target.
- legacy grind/combat tick versus CombatCapability tick for the same map,
  Agent, mobs, config, and state.
- capability runtime disabled path still uses the old fallback mode.
- capability runtime enabled with no active frame still uses the old fallback
  mode.
- active capability path does not call legacy fallback mode in the same tick
  unless the capability explicitly yields that behavior.

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
- `CAPABILITY_HANDOFF_REQUESTED`
- `CAPABILITY_HANDOFF_RESUMED`
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
- one active frame is enforced per Agent.
- `NEEDS_CAPABILITY` pushes a child frame and pauses the parent.
- child `SUCCEEDED` resumes the parent with child evidence.
- child `BLOCKED`/`FAILED` resumes or blocks parent according to handoff
  required flag.
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
