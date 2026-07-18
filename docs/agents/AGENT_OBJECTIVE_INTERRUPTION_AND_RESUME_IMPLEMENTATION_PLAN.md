# Agent Objective Interruption and Resume Implementation Plan

Status: planned follow-up to the Agent engine modularization foundation.

This document defines how an Agent can temporarily suspend any foreground plan
objective, execute higher-priority maintenance such as potion restocking or
death recovery, and then safely resume autonomous progression. The first
production milestone targets HP/MP potion interruptions during Victoria Island
combat and navigation. The same architecture later supports inventory,
equipment, job advancement, recovery, relocation, and operator interruptions.

This is not a scheduler pause. The Agent must continue receiving ticks so it can
recover, navigate, shop, verify the result, and resume. Only the foreground
objective is suspended.

## 1. Goals

The system must:

- interrupt a long-running objective without treating it as failed;
- distinguish advisory, maintenance, urgent, and emergency conditions;
- wait for safe boundaries around atomic game operations;
- run maintenance through normal capabilities and plans;
- preserve durable intent without retaining live Cosmic objects;
- reconcile against current server state before resuming;
- freeze foreground deadlines and retry budgets during maintenance;
- survive relogging and server restart;
- stop repeated failure loops with bounded retries and circuit breakers;
- remain usable by every future Victoria Island and world plan.

The system must not:

- pause the Agent scheduler;
- serialize live `Character`, `MapleMap`, mob, drop, or packet objects;
- resume by restoring an obsolete exact movement frame;
- perform shopping directly from a supply event listener;
- use Maple chat as an Agent-to-Agent control channel;
- replay quest rewards or already-satisfied plan steps;
- change combat packets, damage handling, or movement physics.

## 2. Target flow

```text
foreground objective runs
  -> resource/recovery event occurs
  -> objective supervisor evaluates interruption policy
  -> interruption waits for a safe capability boundary if necessary
  -> foreground objective yields and becomes suspended
  -> maintenance objective becomes authoritative
  -> maintenance stabilizes, procures, and verifies
  -> resume validator reconciles current game state
  -> foreground objective succeeds, resumes, replans, or blocks
```

Example:

```text
Quest objective: collect 40 Orange Mushroom Caps (27/40)
  -> HP potions become critical
  -> suspend quest objective at a combat tick boundary
  -> navigate to an appropriate NPC shop
  -> sell permitted trash if inventory space is required
  -> buy and verify HP potions
  -> reconsider the best eligible hunting map
  -> resume from the live inventory/quest count, not from a saved combat frame
```

## 3. Core model

### 3.1 Interruption reason

Introduce `AgentInterruptionReason` with an initial set:

- `HP_POTIONS_LOW`
- `MP_POTIONS_LOW`
- `AMMO_LOW`
- `INVENTORY_FULL`
- `DANGEROUS_HP`
- `DEATH`
- `EQUIPMENT_REQUIRED`
- `JOB_ADVANCEMENT_READY`
- `MAP_UNAVAILABLE`
- `PARTY_REGROUP_REQUIRED`
- `OPERATOR_REQUEST`

Reasons describe why the interruption exists. They do not prescribe the
maintenance implementation.

### 3.2 Interruption priority

Introduce `AgentInterruptionPriority`:

| Priority | Meaning | Example |
|---|---|---|
| `ADVISORY` | Record and consider during normal replanning | Supplies below preferred target |
| `MAINTENANCE` | Yield at the next convenient safe point | Potions below low threshold |
| `URGENT` | Yield at the next capability tick boundary | Potions nearly empty |
| `EMERGENCY` | Override ordinary work after the current atomic mutation | Dangerous HP, death |

### 3.3 Suspension record

Introduce immutable `AgentSuspensionRecord` containing:

- suspension ID;
- foreground objective ID;
- plan ID and plan-step ID;
- interruption reason and priority;
- requested, suspended, and last-updated timestamps;
- remaining objective deadline;
- retry count before suspension;
- interruption count for this objective;
- original map ID;
- optional region or farming-anchor ID;
- objective source and behavior version;
- correlation ID;
- resume strategy;
- safe diagnostic attributes only.

It must never retain a live Cosmic runtime object.

### 3.4 Resume context

Introduce `AgentResumeContext` for best-effort return information:

- original map;
- assigned map region;
- farming anchor;
- party/cohort assignment;
- quest and collection requirements;
- navigation request identity;
- inventory reservations that must survive maintenance.

The exact old coordinate is diagnostic context, not an instruction to teleport
or blindly return to the same pixel.

### 3.5 Objective states

Extend objective status with:

- `SUSPEND_REQUESTED`
- `SUSPENDED`
- `RESUMING`

The objective journal must record each transition and the maintenance objective
that caused it.

## 4. Objective supervisor

Add `AgentObjectiveSupervisor` as the scheduling authority above individual
plan runners.

It runs before the foreground plan on an Agent tick and performs:

1. Read current interruption requests and authoritative capability state.
2. Discard stale or already-resolved requests.
3. Select the highest-priority eligible request.
4. Ask `AgentInterruptionPolicy` whether the current operation can yield.
5. Record `SUSPEND_REQUESTED` or defer with a bounded reason.
6. Suspend the foreground objective at a safe point.
7. Start or continue the maintenance objective.
8. Verify the maintenance result.
9. Reconcile the foreground objective against live state.
10. Resume, replan, complete, cancel, or block the foreground objective.

The supervisor must read current state even when awakened by an event. Events
are notifications; resource, lifecycle, inventory, and objective state remain
authoritative.

## 5. Objective stack behavior

The current objective kernel has one active objective. Extend it with a
backward-compatible stack or explicit foreground-plus-interrupt structure:

```text
Foreground: quest.collect-orange-caps
  status: SUSPENDED
  Interrupt: maintenance.restock-hp-potions
    status: ACTIVE
```

Required operations:

- start a foreground objective;
- request suspension;
- activate an interrupting maintenance objective;
- transition the top maintenance objective;
- resume the suspended foreground objective;
- inspect the active and suspended frames;
- journal the complete relationship;
- cancel the complete stack during terminal lifecycle cleanup.

Starting maintenance must not mark its foreground objective `SUPERSEDED`.
Existing single-objective APIs should remain as compatibility facades while
current Maple Island runners migrate.

## 6. Interruption policy and safe points

Add `AgentInterruptionPolicy`. Each active capability exposes one of:

- `INTERRUPTIBLE`: may yield now;
- `ATOMIC`: finish the current mutation before yielding;
- `TRANSITIONING`: wait for a short transition such as map entry settlement;
- `UNSAFE`: emergency policy decides whether to abort or continue.

Initial policy:

| Operation | Interruption behavior |
|---|---|
| Ordinary walking or target selection | Interruptible |
| Between combat attacks | Interruptible |
| Attack/damage application call | Finish atomic call |
| NPC transaction | Finish or safely abort transaction |
| Trade commit | Finish atomic commit |
| Portal transition | Wait for destination map settlement |
| Cash-shop field absence | Wait for field restoration |
| Airborne ordinary travel | Prefer waiting until grounded |
| Airborne recovery failure | Emergency recovery may override |
| Death | Immediate lifecycle/recovery authority |

Every deferral carries a maximum duration. An urgent request cannot be deferred
forever by a capability that fails to expose a safe point.

## 7. Cooperative capability yield

The first implementation should use cooperative yield instead of serializing
arbitrary runtime frames.

Add a capability result such as:

```text
status: SUSPENDED
reason: SUPPLY_REQUIRED
message: HP potions reached the critical threshold
```

`AgentCapabilityRuntime` closes the current execution attempt, but the
objective supervisor keeps the durable objective incomplete. The result must
not consume the objective retry budget.

Migrate in this order:

1. Combat.
2. Looting.
3. Navigation.
4. Quest/NPC interaction.

Capabilities should check for an accepted interruption at their normal tick
boundary. Atomic game mutations finish first.

True arbitrary stack preemption can be added later using explicit frame
relationships such as `ROOT`, `CHILD`, and `INTERRUPT`. It is not required for
the first production milestone because durable intent plus reconciliation is
safer across map changes and relogs.

## 8. Supply interruption source

Use the resource planning state introduced by the modularization foundation.

Initial threshold behavior:

| Resource state | Supervisor action |
|---|---|
| `HEALTHY` | Clear stale supply requests |
| `LOW` | Advisory or convenient maintenance |
| `CRITICAL` | Request interruption at the next safe point |
| `EMPTY` | Urgent interruption |
| Dangerous HP with no usable potion | Recovery precedes Shopping |

Initial resources:

- HP potions;
- MP potions;
- throwing stars;
- bullets;
- capsules.

Threshold changes publish bounded domain events. The supervisor then re-reads
`AgentResourcePlanningState` to confirm that the need still exists.

## 9. Supply maintenance plan

Add `AgentSupplyMaintenancePlanFactory` that creates a normal composite plan:

1. Stabilize immediate danger.
2. Count current supplies, inventory space, and mesos.
3. Resolve the required supply quality and target quantity.
4. Select permitted procurement methods.
5. Select a reachable suitable NPC shop.
6. Navigate to the shop.
7. Approach and open the shop.
8. Sell permitted trash if space or mesos are required.
9. Purchase the requested supplies.
10. Verify actual inventory quantities.
11. Complete or block the maintenance objective.

Initial permitted procurement methods:

1. Existing inventory.
2. NPC shop.

Later methods:

- storage withdrawal;
- party/cohort structured supply request;
- approved trade;
- market purchase;
- emergency return-scroll relocation.

Maintenance succeeds only after inventory verification. A successful shop
packet or method return value is not sufficient evidence.

## 10. Resume reconciliation

Add `AgentResumeValidator`. It re-reads live game state before resuming:

1. Is the foreground objective already satisfied?
2. Is its quest still active and eligible?
3. What are the current kill, collection, and inventory counts?
4. Is its target map still suitable and reachable?
5. Is its region or party assignment still valid?
6. Should it return, choose another catalogued map, or replan entirely?
7. Are retained inventory reservations still valid?

Outcomes:

- `COMPLETE_WITHOUT_RESUME`: live state already satisfies the objective;
- `RESUME`: rerun the same objective from current state;
- `REPLAN`: preserve intent but select a new route/map/target;
- `BLOCK`: prerequisite or maintenance failure makes progress impossible;
- `CANCEL`: the plan or operator no longer wants the objective.

Quest rewards and completed interactions must never be replayed. Existing plan
reconcilers remain the authority for their quest-specific live checks.

## 11. Deadlines and retries

Foreground timing rules:

- freeze the foreground deadline at actual suspension;
- maintenance has its own deadline and retry budget;
- maintenance time does not consume the foreground deadline;
- a successful maintenance interruption does not consume a foreground retry;
- resume restores the remaining duration, not the original absolute deadline;
- safe-point deferral remains part of foreground execution time;
- repeated interruptions increment an interruption counter.

Use saturated time arithmetic and journal every adjustment for diagnostics.

## 12. Persistence and relogging

Add `AgentSuspensionStore` behind an interface. Persist only meaningful
transitions:

- interruption requested;
- foreground suspended;
- maintenance started;
- maintenance completed or failed;
- foreground resumed;
- stack terminated.

Do not persist per tick.

On relog or server restart:

1. Load the suspension record.
2. Re-read lifecycle, resources, inventory, quest, and map state.
3. Continue maintenance if the condition remains unresolved.
4. Otherwise reconcile and resume the foreground objective.
5. Reopen interrupted runtime attempts rather than restoring Java frames.

Persistence writes should use the existing bounded asynchronous persistence
gateway and generation-bound completion handling.

## 13. Circuit breakers and fallback policy

Required circuit breakers:

- maximum maintenance interruptions per foreground objective;
- maximum consecutive shop failures;
- cooldown before retrying the same unavailable shop;
- maximum alternative shops per maintenance attempt;
- maximum resume/replan failures;
- stale suspension expiration;
- one active maintenance objective per resource family.

Specific terminal reasons:

- `SUPPLY_UNAVAILABLE`
- `INSUFFICIENT_MESOS`
- `INVENTORY_SPACE_UNAVAILABLE`
- `SHOP_UNREACHABLE`
- `SHOP_MISSING_ITEM`
- `MAINTENANCE_RETRIES_EXHAUSTED`
- `RESUME_PRECONDITION_FAILED`

The supervisor blocks the foreground objective with a precise result instead
of repeatedly travelling between the same maps and shop.

## 14. Events, dialogue, and observability

Internal events:

- `objective.suspend-requested`
- `objective.suspended`
- `maintenance.started`
- `maintenance.succeeded`
- `maintenance.failed`
- `objective.resuming`
- `objective.resumed`
- `objective.resume-blocked`

Events include objective ID, interruption ID, reason, priority, behavior
version, correlation ID, map ID, and bounded diagnostic attributes.

Maple chat remains an optional presentation projection. If an observing human
is present, an Agent may say it needs supplies or is returning to its task. No
chat is required for Agent-to-Agent coordination.

Metrics:

- interruption requests by reason/priority;
- deferred and accepted interruptions;
- suspension duration;
- maintenance success/failure;
- shop fallback count;
- resume/replan outcomes;
- circuit-breaker trips;
- stale suspension recovery;
- active suspended objective count.

## 15. Configuration and behavior routing

Recommended configuration shape:

```yaml
agents:
  objective_interruptions:
    enabled: true
    shadow_only: true
    supplies_enabled: true
    inventory_enabled: false
    equipment_enabled: false
    recovery_enabled: true
    maximum_interruptions_per_objective: 5
    maximum_shop_failures: 3
    maximum_alternative_shops: 3
    freeze_deadlines_during_maintenance: true
    resume_strategy: reconcile
```

Behavior routes default to legacy. Enable reconstructed interruption behavior
per capability/profile after parity testing. Shadow mode detects, journals, and
reports the interruption decision without suspending gameplay.

## 16. Implementation phases

### Phase 1: contracts and state

- Add interruption reason, priority, suspension, and resume contracts.
- Add objective suspension statuses and journal fields.
- Add typed session state.
- Add validation and transition tests.

Exit criterion: no gameplay change; all state transitions are testable.

### Phase 2: objective supervisor

- Add supervisor selection and stack management.
- Preserve compatibility with current single-objective calls.
- Add maintenance/foreground correlation.

Exit criterion: synthetic objectives suspend, run maintenance, and resume in
deterministic order.

### Phase 3: safe-point policy

- Add capability interruption-state reporting.
- Implement bounded deferral.
- Protect NPC, trade, portal, and attack atomic regions.

Exit criterion: tests prove maintenance never begins inside an atomic mutation.

### Phase 4: cooperative yield

- Add suspended capability result and reason codes.
- Teach plan runners to distinguish suspension from failure.
- Freeze deadlines and preserve retry budgets.

Exit criterion: partial objectives yield without becoming failed or completed.

### Phase 5: HP/MP resource integration

- Feed supply events into the supervisor.
- Implement low, critical, empty, and dangerous-HP policy.
- Add stale-request clearing and deduplication.

Exit criterion: forced potion depletion reliably produces one valid maintenance
request.

### Phase 6: NPC supply maintenance

- Implement shop selection, navigation, purchase, and verification.
- Add inventory-space and insufficient-mesos handling.
- Add shop circuit breakers.

Exit criterion: an Agent can restock HP/MP potions without a foreground plan.

### Phase 7: resume reconciliation

- Integrate quest, combat, loot, and navigation reconciliation.
- Restore remaining deadlines.
- Reassign the foreground objective with its correlation identity.

Exit criterion: an Agent interrupted during partial combat resumes from live
progress without repeating rewards.

### Phase 8: persistence and lifecycle

- Add suspension store and asynchronous writes.
- Restore after relog and server restart.
- Clean stacks during terminal lifecycle cleanup.

Exit criterion: restart/relog tests recover both maintenance and foreground
intent.

### Phase 9: generalization and rollout

- Add inventory-full, death, equipment, advancement, and relocation reasons.
- Enable shadow and reconstructed routes by profile.
- Complete staged cohort and soak validation.

Exit criterion: the same supervisor works for non-supply maintenance without
plan-specific branching.

## 17. Test matrix

Unit tests:

- priority ordering and request replacement;
- safe-point acceptance and bounded deferral;
- objective stack transitions;
- deadline freezing and restoration;
- retry-budget preservation;
- stale request clearing;
- shop and interruption circuit breakers;
- event deduplication and listener failure isolation.

Integration tests:

- HP potions become critical halfway through combat;
- MP potions become empty during navigation;
- ammunition runs out after partial quest progress;
- interruption arrives during NPC interaction;
- interruption arrives during portal travel;
- Agent dies while travelling to the shop;
- insufficient mesos;
- no free inventory slot;
- selected shop lacks the item;
- selected shop is unreachable;
- objective becomes satisfied while suspended;
- party/map assignment changes during maintenance;
- relog during maintenance;
- server restart while the foreground objective is suspended;
- repeated supply failures trip the circuit breaker;
- observing player receives optional dialogue;
- no observer produces no unnecessary Maple chat.

Scale validation:

- verify event and persistence queues under forced simultaneous depletion;
- stagger supply checks to prevent synchronized shop waves;
- run 5, 25, and 100 controlled Victoria Agents;
- run 500-Agent shadow and reconstructed soaks;
- validate 2,000-Agent event, state, persistence, and memory bounds.

## 18. Recommended first milestone

Implement Phases 1 through 7 for HP and MP potions only:

- cooperative capability yield;
- one objective supervisor;
- safe combat/navigation interruption;
- NPC shop procurement;
- inventory verification;
- live-state resume reconciliation;
- circuit breakers;
- no arbitrary Java-frame persistence.

This delivers useful Victoria Island interruption/resume behavior with a small
enough risk surface to validate before adding persistence and other maintenance
families.

## 19. Relationship to the modularization foundation

The implementation builds on:

- the typed capability state registry for supervisor and suspension state;
- the bounded Agent event bus for interruption notification;
- the objective kernel and provenance journal for durable intent;
- behavior routing for shadow/legacy/reconstructed rollout;
- resource planning contracts for supply state;
- inventory reservations for protected quest and equipment items;
- the shop workflow for procurement execution;
- immutable perception and action ports for policy isolation;
- lifecycle cleanup for terminal stack disposal;
- dialogue projection for observer-aware presentation.

See `AGENT_ENGINE_MODULARIZATION_IMPLEMENTATION.md` for the implemented
foundation and compatibility rules.
