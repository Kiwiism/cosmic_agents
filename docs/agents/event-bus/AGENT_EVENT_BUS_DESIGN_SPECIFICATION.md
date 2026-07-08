# Agent Event Bus Design Specification

Purpose:

```text
Define the portable event channel that decouples Agent runtime packages after
reconstruction: plan runtime, capability runtime, profile adaptation, economy,
observability, LLM summaries, soak tests, and server adapter events.
```

This is a post-reconstruction contract. It must not be wired into live Agent
runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
Actions emit facts.
Consumers react within budgets.
No consumer may block real player server work.
```

The Event Bus should make Agent behavior explainable and replayable without
coupling packages directly to each other.

## Goals

- Decouple plan, capability, profile, economy, observability, LLM, and server
  adapter packages.
- Provide bounded queues and explicit priority classes.
- Preserve high-priority safety events.
- Compact or drop noisy low-value telemetry under load.
- Support decision journals and behavior learning.
- Support 2000-Agent scale without unbounded memory growth.
- Keep event payloads portable and free of raw Cosmic object references.

## Non-Goals

- Do not replace normal server packet/event handling.
- Do not run expensive LLM/economy/profile consumers inline with gameplay
  actions.
- Do not guarantee delivery for every low-priority telemetry event.
- Do not store raw `Character`, `MapleMap`, `MapObject`, packet, or BotClient
  references.
- Do not mutate server state from event consumers except through approved
  command/capability submission paths.

## Event Classes

### Safety Events

Must be preserved and processed quickly:

- Agent death.
- severe low HP/MP.
- stuck loop.
- repeated server validation rejection.
- forbidden action blocked.
- command cancellation.
- materialization failure.
- state reconciliation failure.

### Plan Events

- plan assigned.
- plan started.
- objective selected.
- objective completed.
- objective blocked.
- sidetrack started/completed.
- plan completed/failed/cancelled.

### Capability Events

- command received.
- validation failed.
- command started.
- progress milestone.
- command succeeded/blocked/cancelled/timed out.

### Server Adapter Events

- live snapshot refreshed.
- quest state changed.
- inventory changed.
- map changed.
- NPC/shop/trade interaction committed.
- server rejected an action.

### Profile Events

- decision made.
- preference influence used.
- relationship interaction observed.
- adaptation patch proposed/applied/rejected.
- decision journal entry created.

### Economy Events

- item acquired.
- item sold/bought/listed.
- market observation recorded.
- price decision made.
- manipulation suspicion raised.

### Observability Events

- slow Agent tick.
- scheduler backlog.
- capability latency spike.
- memory/cache high-water mark.
- simulation tier transition.
- soak test sample.

## Priority Classes

```text
CRITICAL
HIGH
NORMAL
LOW
TRACE
```

Rules:

- `CRITICAL`: never drop unless shutdown; used for safety and consistency.
- `HIGH`: bounded but protected; used for plan/capability blockers.
- `NORMAL`: normal gameplay progress and journal events.
- `LOW`: economy/profile learning, summaries, diagnostics.
- `TRACE`: detailed telemetry, can be sampled or dropped under load.

## Delivery Modes

### Inline Notify

Only for tiny, non-blocking local observers.

Examples:

- update current command status.
- increment counter.

### Async Bounded Queue

Default mode for most consumers.

Examples:

- profile adaptation.
- economy observation.
- observability aggregation.
- LLM summary preparation.

### Durable Append

Used for selected events that need replay/audit.

Examples:

- plan state transitions.
- decision journal entries.
- profile patches.
- economy market observations.
- safety incidents.

## Consumer Rules

Every consumer declares:

- subscribed event types.
- priority class.
- max events per tick/window.
- max processing time per window.
- drop/compact strategy.
- whether durable replay is needed.
- whether it may submit follow-up commands.

Consumers must not:

- block real player packet handling.
- hold raw server object references.
- call raw Cosmic mutation APIs.
- run LLM calls inline.
- produce infinite event loops.

## Backpressure

When pressure rises:

1. preserve `CRITICAL`.
2. preserve minimal `HIGH`.
3. compact repeated blockers.
4. sample `NORMAL`.
5. defer `LOW`.
6. drop or summarize `TRACE`.
7. disable non-critical consumers before slowing core Agent safety.

Backpressure should emit its own compact observability event.

## Event Payload Rules

Portable JSON contract:

- `docs/agents/event-bus/agent-event.schema.json`

Event payloads must be:

- small.
- serializable.
- bounded.
- versioned.
- free of server object references.
- explainable by ids, names, reason codes, and summaries.

Allowed:

- agent id.
- profile id.
- plan id.
- objective id.
- command id.
- map id.
- NPC id.
- quest id.
- item id.
- mob id.
- reason code.
- small evidence maps.

Avoid:

- full inventories.
- full map object lists.
- raw dialogue text beyond short summaries.
- unbounded stack traces.
- raw LLM prompts unless explicitly retained in a separate audit store.

## Replay

Replay is useful for:

- rebuilding profile state.
- debugging decisions.
- investigating economy behavior.
- reproducing stuck plans.
- soak test analysis.

Replay should be scoped by:

- agent id.
- time range.
- event type.
- plan id.
- incident id.

Replay should not require live Cosmic server objects.

## Relationship To Packages

Plan Runtime:

- publishes plan/objective transitions.
- consumes safety/recovery events only through defined policy.

Capability Runtime:

- publishes command lifecycle and evidence.
- consumes cancellation/backpressure events.

Profile Platform:

- consumes outcomes, relationships, failures, and economy events.
- publishes profile patch and decision events.

Economy Engine:

- consumes acquisition/sale/listing/market events.
- publishes price decision and manipulation suspicion events.

Observability:

- consumes all event classes through aggregation budgets.

LLM Gateway:

- consumes summaries, not raw high-volume event streams.
- may publish directives that become Plan Runtime or Capability Runtime
  commands after validation.

Server Adapter:

- publishes live-state commit and rejection events.
- must not depend on profile/economy/LLM consumers.

## Success Criteria

The Event Bus is ready when:

- event envelope is versioned and portable.
- producers can publish without knowing consumers.
- consumers are bounded and backpressure-aware.
- safety events are preserved.
- noisy telemetry can be compacted/dropped safely.
- selected events can be durably appended and replayed.
- no event payload stores raw server object references.
- Plan Runtime, Capability Runtime, Profile Platform, Economy Engine, and
  Observability can integrate through declared event types.
