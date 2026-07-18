# Agent Engine Modularization Implementation

Status: migration foundation implemented on `master`; legacy gameplay remains
available behind compatibility adapters while capabilities are migrated.

This implementation intentionally establishes boundaries before replacing all
legacy behavior. It does not change attack packet construction, player combat
handlers, navigation physics, or map portal mechanics. The purpose is to let
those behaviors move independently in later changes without another
`AgentRuntimeEntry`-wide rewrite.

## The 15 implemented recommendations

### 1. Narrow capability context and action port

`AgentCapabilityView` gives a capability a compact Agent snapshot, immutable
perception, typed state access, its event bus, and an action submission port.
`AgentCapabilityContext` retains its compatibility constructors while exposing
the view to reconstructed handlers.

The Cosmic action port currently returns `UNSUPPORTED`. This is deliberate:
reads can migrate immediately, while each mutation must receive an explicit,
server-thread-safe adapter rather than allowing capability code to mutate a
`client.Character` accidentally.

### 2. Typed per-capability state registry

`AgentCapabilityStateRegistry` and `AgentCapabilityStateKey<T>` provide lazy,
typed, collision-checked state ownership. New reconstructed state no longer
requires another field and accessor on `AgentRuntimeEntry`.

The capability runtime, event bus, objective kernel, behavior routes, resource
planning, reservations, and combat directives use this registry. Existing
legacy state fields remain until their owning capability is migrated.

### 3. Bounded per-Agent event bus

`BoundedAgentEventBus` provides:

- bounded queue capacity;
- event priorities and priority-aware eviction;
- deduplication keys;
- typed event-name subscriptions;
- listener failure isolation;
- budgeted draining and metrics snapshots;
- deterministic close/cleanup.

`AgentSessionEventRuntime` owns one bus per Agent session. The bus is internal
coordination infrastructure; it is not Maple chat and does not broadcast
Agent-to-Agent implementation details to players.

### 4. Immutable perception snapshot

`AgentPerceptionSnapshot` contains immutable mob/drop DTOs, observer presence,
map identity, and capture time. `CosmicAgentPerceptionSnapshotFactory` converts
live Cosmic objects at the integration boundary and reuses a short-lived map
snapshot.

Capability policy can therefore be tested without retaining `MapleMap`,
`MapleMonster`, `MapItem`, or `Character` objects. The existing live perception
API remains available during migration.

### 5. Durable objective kernel

`AgentObjectiveKernel` owns the active objective and a bounded transition
journal. Objectives carry type, priority, deadline, retry budget, source,
behavior version, and correlation identity. Starting, completing, blocking,
failing, cancelling, or superseding an objective emits a domain event.

Maple Island plan startup now registers a reconstructed progression objective.
`AmherstPlanRuntimeRunner` records its terminal outcome in the kernel. Plan
steps still execute through the proven Amherst handlers.

### 6. Decision provenance

`AgentCapabilityInvocationMetadata` travels with capability invocations.
Capability journal records now include objective id/source, behavior version,
and correlation id. This makes a later LLM or policy decision explainable
without coupling the capability runtime to the planner implementation.

### 7. Versioned behavior routing

`AgentBehaviorRouteTable` routes each capability to `LEGACY`, `RECONSTRUCTED`,
or `SHADOW_COMPARE`, with explicit primary and optional shadow versions.
`AgentBehaviorRoutingRuntime` is the session-facing access point.

Maple Island progression declares its reconstructed behavior version. Other
capabilities remain on their existing route until a parity test is available.

### 8. Shared resource contracts

Supplies, shopping, inventory, and equipment can now coordinate through
`AgentSupplyNeed`, `AgentProcurementRequest`, and
`AgentInventoryReservation`, rather than importing one another's runtime
states. Resource category, urgency, procurement method, and disposition are
explicit values.

`AgentInventoryReservationLedger` gives objectives bounded, expiring claims on
items or slots so independent capabilities do not consume the same resource.

### 9. Supplies-to-shopping vertical slice

`AgentSupplyPlanner` classifies HP, MP, star, bullet, and capsule levels and
produces a procurement request. `AgentResourcePlanningRuntime` stores the
latest need/request and emits threshold-change events only.

The existing potion and ammunition checks feed this planner as a sidecar. Their
proven sharing/restocking behavior is unchanged; reconstructed shopping can
consume the new request when its mutation adapter is ready.

### 10. Durable shop workflow

`AgentShopWorkflow` makes shop progress explicit:

`PLANNED -> APPROACHING -> TRANSACTING -> COMPLETED`, with `BLOCKED` and
`CANCELLED` terminal outcomes.

`AgentShopState` owns this workflow without changing the existing NPC shop
sequence. Repeated starts do not silently reset an in-flight visit, and service
completion/failure records a terminal result before legacy state is cleared.

### 11. Dialogue as event projection

`AgentDialogueIntentEvent` represents a presentation intent, not direct chat.
`AgentDialogueProjectionService` checks audience, observer presence, and
cooldown before using an integration gateway to display it.

Agent-only technical coordination should use structured domain messages. Maple
chat is reserved for human-facing projection when an observing player can
benefit from it. Existing dialogue calls are not yet globally rerouted; they
remain compatible until each event family has equivalent presentation tests.

### 12. Lifecycle transition and cleanup ownership

Lifecycle state now records phase, reason, timestamp, and transition sequence.
`AgentLifecycleTransitionService` is the single transition/event boundary.
`AgentSessionCleanupService` owns scheduled-task cancellation, task-scope
cleanup, mailbox clearing, event-bus closure, and typed-state disposal.

Existing registration, spawn, relogin, and scheduler coordinators continue to
perform their proven server operations. They can now call one transition and
one cleanup boundary rather than learning capability internals.

### 13. Navigation graph repository and movement result contracts

Graph persistence moved behind `AgentNavigationGraphRepository` with the file
implementation in `FileAgentNavigationGraphRepository`. Navigation requests,
route plans, and movement outcomes are explicit contracts.

Graph generation and movement physics remain unchanged. This protects the
physics branch: it can merge the repository seam without accepting a new
collision or packet model.

### 14. Map strategy and combat directive contracts

`AgentMapStrategy` can describe recommended population, region assignments,
target mobs, incidental-mob policy, and strategy metadata. The strategy
repository is a read boundary; it does not embed tactics in map objects.

`AgentCombatDirective` is the policy-to-execution handoff. It identifies the
objective, assigned region, required targets, incidental kill policy, and
deadline. No `BotCombatManager` packet or damage route was changed.

### 15. Owner-neutral naming and architecture guardrails

Follow motion now uses `AgentFollowTargetMotionState`; received trade items use
`AgentTradeReceivedItemState`. Owner-named runtime facades remain deprecated
compatibility adapters so existing commands and the physics branch can merge
without a flag day.

`AgentArchitectureBoundaryTest` prevents pure model/contract/policy packages
from importing Cosmic server types and sets explicit ceilings on the largest
remaining capability-to-capability dependency directions. New focused tests
cover the state registry, event bus, objective lifecycle, behavior routing,
resource planner/reservations, shop workflow, dialogue projection, and
lifecycle transitions.

## Runtime flow after this change

```text
planner / plan card
  -> objective kernel (durable intent + provenance)
  -> behavior route (legacy / reconstructed / shadow)
  -> capability runtime
       reads AgentCapabilityView
         - AgentSnapshot
         - AgentPerceptionSnapshot
         - typed capability state
         - event bus
       submits action intent through AgentCapabilityActionPort
  -> Cosmic integration adapter
  -> existing server-thread-safe game operation
```

The bottom action-adapter step is intentionally implemented one mutation family
at a time. Until an adapter exists, legacy execution remains authoritative.

## Compatibility and rollout rules

1. Do not remove a legacy implementation until its reconstructed route passes
   parity tests for success, failure, cancellation, relog, and cleanup.
2. Use `SHADOW_COMPARE` for read-only decisions before switching authority.
3. Do not let event listeners mutate live game state directly; they should
   enqueue an action or update capability-owned state.
4. Do not retain Cosmic entities in pure snapshots, objectives, strategies, or
   journals.
5. Preserve combat packet routes and physics behavior while moving policy.
6. Close the session event bus and typed states during every despawn/failure
   path.
7. Reduce dependency ceilings in the architecture test as migrations land;
   never increase them to accommodate a new shortcut.

## Next safe migrations

The foundation is complete, but these are deliberately not flag-day rewrites:

1. Add server-thread action adapters for movement, NPC interaction, inventory,
   and combat intent in that order.
2. Move existing direct dialogue sites to intent events family by family.
3. Let reconstructed shopping consume the resource planning request and
   inventory reservations.
4. Load real map strategy catalog entries and produce combat directives before
   changing target selection.
5. Convert remaining owner-named service methods to follow target, party,
   cohort, authority, or interaction target terminology, retaining adapters
   until callers are gone.
6. Route remaining spawn/relog/failure exits through lifecycle transitions and
   the session cleanup service.

These migrations can be reviewed independently and cherry-picked or merged by
the physics branch without combining unrelated gameplay changes.

The general interruption, maintenance, and resume design is specified in
`AGENT_OBJECTIVE_INTERRUPTION_AND_RESUME_IMPLEMENTATION_PLAN.md`.
