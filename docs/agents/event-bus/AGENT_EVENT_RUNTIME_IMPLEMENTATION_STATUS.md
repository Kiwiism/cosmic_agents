# Agent Event Runtime Implementation Status

Status: implemented on `master` as of 2026-07-20.

This document describes the runtime that exists in Cosmic Agents today. The
adjacent design and technical specifications remain the larger portable
package target. They should not be read as claims that every future profile,
economy, or distributed-event feature is already present.

## Implemented Boundary

The current flow is:

```text
authoritative game/capability boundary
  -> immutable typed AgentEvent
  -> per-session BoundedAgentEventBus
  -> bounded tick drain
       -> monitoring read models
       -> mailbox-deferred maintenance/evaluation
       -> observer-gated dialogue intents
       -> structured Agent coordination
       -> bounded LLM context projection
       -> optional process-wide durable journal queue
```

Producers do not know their consumers. Event listeners do not directly mutate
Cosmic game state. A listener that needs work performed submits it through an
Agent mailbox or another explicit capability boundary.

## Runtime Contract

`AgentEvent` supplies:

- Agent identity;
- occurrence time;
- stable event type;
- schema version, currently `1`;
- optional deduplication key;
- derived `AgentEventContext`.

`AgentContextualEvent` adds objective, correlation, causation, and map context.
The default correlation is the objective ID. A non-contextual event receives
`agent:<id>` as its correlation ID and map `-1`.

`AgentEventEnvelope` separates delivery metadata from the immutable payload:

- session sequence;
- event ID;
- versioned context;
- payload event;
- priority;
- enqueue timestamp used for latency metrics.

The JSON-lines durable representation is specified by
`agent-event-journal-record.schema.json`.

## Published Typed Event Families

### Progression

- `progression.level-changed`
- `progression.job-advanced`
- `progression.ap-assigned`
- `progression.skill-learned`
- `progression.quest-state-changed`
- `progression.checkpoint-reached`

### Resources, inventory, equipment, and shopping

- `inventory.item-quantity-changed`
- `loot.collected`
- `inventory.threshold-changed`
- `equipment.candidate-detected`
- `equipment.loadout-changed`
- `equipment.scroll-resolved`
- `shopping.transaction-resolved`
- `supply.threshold-changed`

### Combat, navigation, recovery, and lifecycle

- `combat.target-changed`
- `combat.mob-killed`
- `combat.life-state-changed`
- `navigation.route-failed`
- `navigation.map-transitioned`
- `navigation.stuck-detected`
- `recovery.performed`
- `lifecycle.transition`

Objective lifecycle facts currently use bounded `AgentDomainEvent` payloads
with `objective.<status>` types. This preserves objective provenance while a
future portable objective-event model is evaluated.

## Bounds And Failure Isolation

- One bus exists per Agent session.
- Default capacity is 256 events and can be set with
  `agents.events.capacity`.
- Default drain budget is 32 events per completed Agent frame and can be set
  with `agents.events.maxPerTick`.
- Priorities are `AMBIENT`, `NORMAL`, `IMPORTANT`, and `CRITICAL`.
- An incoming important/critical event evicts older lower-priority work before
  it is rejected.
- Equivalent queued events can compact through their type plus dedupe key.
- One listener exception cannot prevent other listeners from receiving the
  event.
- Queue latency, listener latency/failures, high-water mark, drops, and
  deduplication are counted.
- Session cleanup closes subscriptions and clears the queue.

The current implementation uses one queue with priority-aware eviction. It is
not a promise that an unlimited stream of critical events can be retained;
memory remains strictly bounded.

## Reactions And Presentation

Supply, inventory, progression-checkpoint, and operational reactions submit
bounded mailbox work. Acceptance, coalescing, and rejection are tracked per
reaction name.

Dialogue is a presentation projection. It requires the configured audience,
an observing real client where applicable, and a satisfied cooldown. Agent to
Agent operational coordination uses structured messages instead of Maple chat.

`AgentCoordinationRuntime` supports bounded cohort, party, and direct-Agent
routes with TTL, correlation, optional acknowledgement, receipts, and listener
failure isolation.

## Durable Journal And Replay

The journal is disabled by default. Enable it at server startup with:

```text
-Dagents.events.journal.enabled=true
```

Defaults:

| Property | Default | Meaning |
| --- | ---: | --- |
| `agents.events.journal.path` | `runtime/agents/events/agent-events.jsonl` | Current JSON-lines file |
| `agents.events.journal.capacity` | `8192` | Non-blocking writer queue capacity |
| `agents.events.journal.maxFileBytes` | `67108864` | Current segment size before one-file rotation |

The event listener only calls `offer`. One daemon writer performs serialization
and disk I/O. Queue overflow rejects journal work without blocking gameplay and
is visible in diagnostics.

Selected durable facts are:

- level and job advancement;
- completed quests;
- terminal objective outcomes;
- NPC-shop transactions;
- scroll outcomes;
- death transitions;
- failed or quarantined Agent lifecycle transitions.

`AgentEventJournalRuntime.replay` is an explicit blocking diagnostic/offline
API. It queries the current and one rotated segment by Agent, objective,
correlation, event type, and time. Results are capped at 1,000. It must not be
called from an Agent tick or listener.

## LLM Context Boundary

`AgentLlmContextProjectionRuntime.snapshot` exposes structured state only:

- current progression, map, objective, blocker, life, supply, inventory,
  equipment, shopping, and recovery facts;
- at most 48 current fact keys;
- at most 24 recent significant milestones.

It performs no model call and produces no chat. Future LLM adapters should read
this projection outside event dispatch, add profile/relationship context, and
submit validated commands through normal capability boundaries.

## Rollout And Rollback

Optional consumer families are independently controlled at startup:

| Property | Default |
| --- | ---: |
| `agents.events.reactions.enabled` | `true` |
| `agents.events.dialogue.enabled` | `true` |
| `agents.events.coordination.enabled` | `true` |
| `agents.events.llmContext.enabled` | `true` |

Monitoring listeners remain registered when optional consumers are disabled,
which preserves shadow evidence. Configuration is read when a session is
wired; restart the server or recreate affected Agent sessions after changing
these properties.

Recommended production rollout:

1. Keep durable journaling disabled and record event queue/drop/latency
   baselines.
2. Run the Maple Island and Victoria level-15 paths with all default consumers.
3. Enable journaling for a bounded canary and confirm zero writer rejection and
   failure counts.
4. Run visible-client dialogue/coordination parity and relog/cleanup tests.
5. Run mixed real-player/Agent load, followed by the sustained soak gate.

Rollback disables the affected consumer property. Publication and monitoring
remain available for diagnosis.

## Operator Evidence

Scheduler diagnostics report:

- aggregate event capacity, depth, high-water mark, publication, delivery,
  drop, dedupe, queue latency, and listener failures;
- mailbox reaction acceptance/coalescing/rejection;
- coordination route depth, rejection, expiry, and listener failures;
- journal enablement, depth, acceptance, rejection, writes, and failures.

Automated coverage includes ordered/budgeted delivery, priority eviction,
deduplication, listener failure isolation, typed producer integrations,
projection/reaction integrations, observer-gated dialogue, session cleanup,
rollout gates, durable selection, rotation/replay, LLM context bounds, and a
deterministic 2,000-bus pressure test.

## Deliberately Deferred Platform Work

The implemented migration slice is complete. These remain future platform or
capability integrations, not hidden requirements for the current runtime:

- separate physical priority lanes and per-consumer queues/time budgets;
- SQL/object-store append implementations and paginated multi-segment replay;
- typed replacements for the remaining generic objective/lifecycle facts;
- profile adaptation and economy consumers;
- validated LLM planner/tool-call consumers;
- cross-process/distributed transport;
- a live 2,000-Agent plus 500-player 30-day soak and operational tuning.

None should be introduced into a gameplay mutation path without a bounded
queue, failure isolation, shadow/parity evidence, and a rollback switch.
