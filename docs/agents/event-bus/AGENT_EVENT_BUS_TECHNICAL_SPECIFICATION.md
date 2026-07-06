# Agent Event Bus Technical Specification

Purpose:

```text
Define interfaces, event envelope, priority queues, consumer contracts,
backpressure rules, durable append, replay, and tests for the future portable
Agent Event Bus package.
```

Package name:

```text
agent-event-bus
```

This spec is documentation only until Agent reconstruction is stable.

## Package Boundary

The Event Bus package owns:

- event envelope.
- publish API.
- subscription API.
- bounded priority queues.
- consumer budget enforcement.
- event compaction/drop rules.
- durable append interface.
- replay query API.
- diagnostics snapshots.

The Event Bus package does not own:

- plan state.
- capability execution.
- profile adaptation rules.
- economy valuation.
- LLM calls.
- server mutation.

## Suggested Layout

```text
agent-event-bus/
  api/
    AgentEventBus.java
    AgentEventPublisher.java
    AgentEventSubscriber.java
    AgentEventReplay.java
  model/
    AgentEvent.java
    AgentEventId.java
    AgentEventType.java
    AgentEventPriority.java
    AgentEventPayload.java
    AgentEventSource.java
    AgentEventContext.java
    AgentEventDeliveryMode.java
  runtime/
    BoundedAgentEventBus.java
    AgentEventQueue.java
    AgentEventDispatcher.java
    AgentEventConsumerBudget.java
    AgentEventBackpressurePolicy.java
    AgentEventCompactor.java
  storage/
    AgentEventAppendStore.java
    JsonlAgentEventAppendStore.java
    SqlAgentEventAppendStore.java
  diagnostics/
    AgentEventBusSnapshot.java
    AgentEventBusHealth.java
```

## Core Interfaces

### AgentEventBus

```java
public interface AgentEventBus extends AgentEventPublisher, AgentEventReplay {
    SubscriptionId subscribe(AgentEventSubscription subscription);
    void unsubscribe(SubscriptionId id);
    AgentEventBusSnapshot snapshot();
}
```

### AgentEventPublisher

```java
public interface AgentEventPublisher {
    PublishResult publish(AgentEvent event);
    PublishResult publishAll(List<AgentEvent> events);
}
```

### AgentEventSubscriber

```java
public interface AgentEventSubscriber {
    void onEvents(List<AgentEvent> events, AgentEventConsumerContext context);
}
```

### AgentEventReplay

```java
public interface AgentEventReplay {
    List<AgentEvent> replay(AgentEventReplayQuery query);
}
```

## Event Envelope

```java
record AgentEvent(
    AgentEventId eventId,
    int schemaVersion,
    long occurredAtMs,
    AgentEventPriority priority,
    AgentEventType eventType,
    AgentEventSource source,
    AgentEventContext context,
    AgentEventPayload payload,
    AgentEventDeliveryMode deliveryMode,
    String dedupeKey,
    String correlationId
) {}
```

## Event Context

```java
record AgentEventContext(
    String agentId,
    String profileId,
    String planId,
    String objectiveId,
    String commandId,
    Integer worldId,
    Integer channelId,
    Integer mapId
) {}
```

Context fields are optional but should be present when known.

## Payload Model

```java
record AgentEventPayload(
    Map<String, Integer> ids,
    Map<String, Long> counters,
    Map<String, Double> values,
    Map<String, String> labels,
    List<String> reasonCodes,
    List<String> warnings
) {}
```

Payload must be bounded. Large details should be stored elsewhere and linked by
summary id.

## Event Types

Minimum enum groups:

```text
PLAN_ASSIGNED
PLAN_STARTED
PLAN_COMPLETED
PLAN_FAILED
OBJECTIVE_SELECTED
OBJECTIVE_COMPLETED
OBJECTIVE_BLOCKED
OBJECTIVE_RETRIED

CAPABILITY_COMMAND_RECEIVED
CAPABILITY_STARTED
CAPABILITY_PROGRESS
CAPABILITY_SUCCEEDED
CAPABILITY_BLOCKED
CAPABILITY_CANCELLED
CAPABILITY_TIMED_OUT

SERVER_STATE_CHANGED
SERVER_ACTION_COMMITTED
SERVER_ACTION_REJECTED

PROFILE_DECISION_MADE
PROFILE_PATCH_PROPOSED
PROFILE_PATCH_APPLIED
PROFILE_PATCH_REJECTED
RELATIONSHIP_OBSERVED

ECONOMY_OBSERVATION_RECORDED
ECONOMY_DECISION_MADE
MARKET_MANIPULATION_SUSPECTED

AGENT_SAFETY_INCIDENT
AGENT_SIMULATION_TIER_CHANGED
AGENT_SCHEDULER_BACKPRESSURE
AGENT_MEMORY_HIGH_WATER
SOAK_SAMPLE_RECORDED
```

## Priorities

```java
enum AgentEventPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    TRACE
}
```

Default mapping:

- safety incident: `CRITICAL`.
- objective blocked/forbidden action/server rejected: `HIGH`.
- objective complete/capability result: `NORMAL`.
- profile/economy observation: `LOW`.
- per-step telemetry: `TRACE`.

## Delivery Modes

```java
enum AgentEventDeliveryMode {
    INLINE_COUNTER_ONLY,
    ASYNC,
    DURABLE,
    ASYNC_AND_DURABLE
}
```

`INLINE_COUNTER_ONLY` may only update local counters and must not run arbitrary
consumer code.

## Subscription Contract

```java
record AgentEventSubscription(
    String subscriberName,
    Set<AgentEventType> eventTypes,
    Set<AgentEventPriority> priorities,
    AgentEventConsumerBudget budget,
    AgentEventDropPolicy dropPolicy,
    boolean durableReplayRequired,
    AgentEventSubscriber subscriber
) {}
```

## Consumer Budget

```java
record AgentEventConsumerBudget(
    int maxEventsPerDispatch,
    int maxEventsPerSecond,
    long maxProcessingMsPerDispatch,
    int maxQueueDepth
) {}
```

If a consumer exceeds budget:

- pause low-priority deliveries.
- compact repeated events.
- emit backpressure diagnostics.
- never block producers indefinitely.

## Drop And Compact Policy

```java
enum AgentEventDropPolicy {
    NEVER_DROP,
    DROP_OLDEST,
    DROP_NEWEST,
    COMPACT_BY_DEDUPE_KEY,
    SAMPLE,
    SUMMARY_ONLY
}
```

Rules:

- `CRITICAL` should use `NEVER_DROP` or durable append.
- `HIGH` should compact only when repeated and equivalent.
- `LOW` and `TRACE` can sample/compact under load.

## Durable Append Store

```java
public interface AgentEventAppendStore {
    void append(AgentEvent event);
    void appendAll(List<AgentEvent> events);
    List<AgentEvent> query(AgentEventReplayQuery query);
}
```

Storage choices:

- JSONL for early development and tests.
- separate Agent database for production.
- not Cosmic's original player tables.

Durable event categories:

- plan transitions.
- objective blockers.
- capability terminal results.
- safety incidents.
- profile patches.
- economy observations/transactions.
- soak samples.

## Replay Query

```java
record AgentEventReplayQuery(
    String agentId,
    String planId,
    String objectiveId,
    String correlationId,
    Set<AgentEventType> eventTypes,
    long fromMs,
    long toMs,
    int limit
) {}
```

Replay should be bounded and paginated.

## Backpressure Snapshot

```java
record AgentEventBusSnapshot(
    long publishedTotal,
    long droppedTotal,
    long compactedTotal,
    int criticalQueueDepth,
    int highQueueDepth,
    int normalQueueDepth,
    int lowQueueDepth,
    int traceQueueDepth,
    Map<String, ConsumerSnapshot> consumers
) {}
```

Expose this to Agent Observability and soak tests.

## Producer Guidelines

Plan Runtime:

- publish objective/plan terminal events as durable.
- publish selection decisions as normal/durable when journaled.

Capability Runtime:

- publish command terminal results as normal/durable.
- publish progress milestones as low or trace.

Server Adapter:

- publish rejected/committed actions as high or normal.

Profile/Economy:

- publish learning observations as low/durable when needed.

LLM Gateway:

- publish directives and accepted/rejected tool calls as durable.

## Consumer Guidelines

Profile adaptation:

- async.
- bounded.
- durable replay capable.
- no direct server mutation.

Economy:

- async.
- can batch observations.
- no direct inventory/trade mutation.

Observability:

- consume all event groups with aggregation budgets.
- drop/summarize trace under pressure.

LLM summary builder:

- consume summarized events only.
- never call LLM from event dispatch thread.

## Tests

Unit tests:

- publish event reaches matching subscriber.
- unmatched event is ignored.
- priority queues preserve CRITICAL before LOW.
- queue depth cap applies.
- low-priority events drop/compact under pressure.
- critical events are not dropped.
- consumer budget is enforced.
- durable append receives requested events.
- replay query filters by agent/plan/type/time.
- payload rejects oversized fields.
- raw object references cannot be stored in payload model.

Integration tests later:

- Plan Runtime emits objective events.
- Capability Runtime emits command events.
- Profile Platform consumes outcome events.
- Economy Engine consumes acquisition/sale events.
- Observability reports queue depth and dropped counts.
- 2000-Agent soak test does not create unbounded event memory.

## Implementation Gates

Do not implement live integration until:

- reconstructed Agent runtime exposes stable agent id/runtime handles.
- Plan Runtime and Capability Runtime event types are finalized.
- Agent Observability can read snapshots.
- storage choice for durable events is decided.

Do not enable profile/economy/LLM consumers until:

- consumer budgets are enforced.
- backpressure behavior is tested.
- durable replay cannot block gameplay threads.
