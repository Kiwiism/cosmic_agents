# Amherst MVP Phased Implementation Plan

## Purpose

This document defines the implementation order for the Maple Island Amherst
sub-phase MVP after the Agent reconstruction. It complements, rather than
replaces, the existing scope, test, and plan-card documents:

- `MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md`
- `MAPLE_ISLAND_AMHERST_SUBPHASE_TEST_PLAN.md`
- `plans/maple-island-amherst-subphase.plan.json`

The work is divided into three independently verifiable phases:

```text
Phase 1: Capability Runtime and Primitive Capability Adapters
Phase 2: Objective Capabilities and Plan Composition Tests
Phase 3: Full Amherst MVP Integration Run
```

Do not begin a later phase until the preceding phase exit gate passes.

## Terminology

### Primitive Capability Adapter

A typed capability boundary around existing reconstructed gameplay behavior.
Navigation and combat adapters delegate to the current implementations; they do
not replace their algorithms during the Amherst MVP.

Examples:

- navigate to a map, portal, or position;
- fight selected mob ids until a count is satisfied;
- interact with one NPC;
- use one inventory item;
- hit one reactor;
- collect required items.

### Objective Capability

A coordinator for one gameplay objective. It requests primitive capability
handoffs, resumes after each child reaches a terminal state, and verifies the
objective against live server state.

Example:

```text
CombatQuestObjective
  -> QuestStart
  -> Navigation
  -> Combat
  -> QuestProgress verification
  -> Navigation
  -> QuestComplete
  -> final live-state verification
```

### Plan Objective Handler

The binding that translates a plan-card objective such as `kill-mobs` into a
typed Objective Capability command. It does not contain movement, combat,
quest, or inventory implementation logic.

## Phase 1: Complete Amherst Capabilities

### Goal

Build and independently verify the capability foundation and all primitive
capabilities required by Amherst. Phase 1 does not execute the full Amherst plan
and does not implement generalized post-Maple-Island autonomy.

### 1. Baseline And Reset Harness

Before rewiring runtime behavior:

- record focused navigation and combat parity baselines;
- implement a test-only, feature-flagged, allowlisted reset service;
- support `clean-lv1-start`, `quest-scenario`, `amherst-ready`, and
  `amherst-mvp-clean`;
- reset map, level, job, EXP, stats, HP/MP, meso, inventory, equipment, selected
  quest state, capability state, cooldowns, pending actions, drops, and reactors
  as required by each mode;
- permit direct state mutation only as fixture setup, never as the action being
  tested.

### 2. Capability Runtime Foundation

Implement:

- typed capability command and result contracts;
- explicit statuses and stable reason codes;
- one active capability frame per Agent;
- frame states for starting, running, waiting for a child, success, failure,
  blocker, and cancellation;
- explicit child handoff and parent resume;
- timeout, bounded retry, and cancellation behavior;
- terminal live-state verification;
- capability journal entries for start, handoff, resume, retry, blocker, and
  terminal result;
- tick integration that runs the active capability first and retains existing
  behavior when no capability is active.

Only one frame executes at a time. A waiting parent remains on the frame stack
but does not tick while its child is active.

### 3. Live State And Cosmic Boundaries

Provide read and execution boundaries for:

- character map, position, job, level, health, and death state;
- quest status, requirements, progress, start, and completion;
- NPC placement, approach range, and interaction;
- inventory counts, free slots, protected quest items, and item use;
- monsters, portals, reactors, and map scope;
- reactor hit execution and resulting item-state verification.

Use normal Cosmic validation and mutation paths. A capability must not force a
quest complete, grant its target items, or bypass its normal game rule.

### 4. Primitive Capability Adapters

Implement the Amherst-required primitives:

| Capability | Minimum Command Inputs | Terminal Verification |
| --- | --- | --- |
| Navigation | map id, optional portal/position, tolerance, timeout | live map and position/arrival state |
| Portal travel | portal id or destination map | destination map reached |
| Combat | allowed mob ids, required counts, quest id, timeout | live quest kill progress/count |
| Loot | item ids, required counts, protection policy | live inventory counts |
| Inventory inspection | item ids and capacity request | snapshot returned |
| Item use | item id and optional quest id | item/quest state changed as expected |
| NPC interaction | NPC id, map id, action, optional quest id | action-specific live state |
| Quest state/start/complete | quest id, NPC id, expected state | live quest status |
| Reactor interaction | map/reactor selector, quest id, expected items | reactor action and resulting state |
| Recovery | blocker/death/stuck context and bounds | safe state or structured blocker |
| Final-state verification | expected map, quests, forbidden state | all assertions satisfied |

Navigation and combat must wrap the reconstructed implementations and pass
parity tests before any behavioral change is considered.

### 5. Phase 1 Test Order

1. Reset harness guard and each reset mode.
2. Capability frame lifecycle.
3. Child handoff, parent suspension, and parent resume.
4. Timeout, retry, cancellation, and blocker behavior.
5. Navigation parity and arrival verification.
6. Portal destination verification and Amherst scope guard.
7. Combat parity and bounded kill-count termination.
8. Quest state read/start/complete.
9. NPC validation and execution.
10. Inventory inspection and item use.
11. Loot required-item termination.
12. Reactor execution and item-state verification.
13. Recovery and final-state verification.
14. Tick fallback when no capability is active.

### Phase 1 Exit Gate

Phase 1 is complete only when:

- every listed primitive accepts typed parameters;
- every primitive reaches a deterministic terminal result;
- success is verified against live state rather than assumed from a method call;
- failures produce bounded, structured results instead of loops;
- navigation and combat parity tests pass;
- no primitive depends on an Amherst plan runner;
- fixture mutation is guarded and cannot be reached in normal Agent runtime;
- focused tests and the broader Agent regression suite pass.

### Implemented Phase 1 Contract

Phase 1 is implemented through `server.agents.capabilities.runtime`, the
primitive adapters in `server.agents.capabilities.primitive`, and
`PrimitiveCapabilityGateway` with its Cosmic adapter.

The runtime keeps a stack so a suspended parent can remain present, but only the
top frame executes. Its lifecycle is:

```text
assign -> start -> running
                  -> retry (bounded)
                  -> child handoff -> parent waits -> child terminal -> parent resumes
                  -> success / blocker / failure / timeout / cancellation
```

Every terminal path invokes capability cleanup. This is significant for
navigation and combat because cancellation, timeout, and failure must stop the
delegated movement/grind mode and clear the objective mob filter just as normal
success does. The per-Agent journal retains the newest 256 events.

Runtime statuses are `RUNNING`, `WAITING_CHILD`, `RETRY`, `SUCCESS`,
`MISSING_REQUIREMENT`, the scope-specific blocker statuses, `CANCELLED`,
`TIMED_OUT`, and `FAILED`. Stable runtime reason codes cover in-progress,
handoff, retry, retry exhaustion, cancellation, deadline expiry, missing
requirements, scope blockers, live-state mismatch, and execution failure.

| Primitive | Implemented command contract | Success evidence |
| --- | --- | --- |
| Navigation | current map, destination point, tolerance, precision | live map and point within tolerance |
| Portal travel | source map, portal id, destination map, scope flag | live destination map |
| Combat | quest id and positive mob-id/count map | live quest progress for every mob id |
| Loot | positive item-id/count map and protection policy | live inventory counts |
| Inventory inspection | item id, required count, required free slots | live count and capacity |
| Item use | item/count bounds and optional required quest state | live count reduction and quest precondition |
| NPC interaction | map, NPC, action, optional quest, range, scope flag | live placement/range and action-specific quest state |
| Quest state | quest id and expected status | live quest status |
| Quest start | quest id, NPC id, scope flag | live started or auto-completed status |
| Quest complete | quest id, NPC id, scope flag | live completed status |
| Reactor interaction | map, quest, reactor selector, range, expected items | normal reactor hit, normal drop pickup, live item counts |
| Recovery | alive requirement and maximum stuck duration | live alive/stuck state after legacy recovery ticks |
| Final verification | map, quest/item assertions, optional job/level, forbidden quests | all live assertions pass |

Navigation and combat issue the existing `AgentModeService` intents and return
the tick to reconstructed movement/combat. Combat adds only a temporary allowed
mob-id constraint; it filters primary and area-attack secondary targets and is
empty for ordinary Agent behavior. Portal, quest, item, NPC, reactor, and loot
actions use normal Cosmic paths behind `PrimitiveCapabilityGateway`.

The reset harness is disabled unless `agents.amherst.reset.enabled=true` and
the requested online Agent matches an id or exact name in:

```text
agents.amherst.reset.characterIds=7,8
agents.amherst.reset.characterNames=AmherstTestAgent
```

Malformed ids are ignored, so configuration fails closed. The live port also
checks that id and name identify the same online Agent. Reset removes only map
drops owned by that Agent, resets reactors, and clears transient capability,
movement, combat, loot, mailbox, dialogue, task, trade, cooldown, buff, and
debuff state.

Reset mode semantics:

| Mode | Implemented fixture state |
| --- | --- |
| `clean-lv1-start` | deterministic level-1 beginner, starter inventory/equipment, all Amherst quests reset, map `10000` |
| `quest-scenario` | selected covered quest and known target items reset, warped to its representative map |
| `amherst-ready` | deterministic baseline in `1000000`; pre-Amherst quests seeded complete and `1037` ready to report |
| `amherst-mvp-clean` | deterministic level-1 beginner, all Amherst quests reset, map `10000` |

Automated Phase 1 evidence includes focused lifecycle/primitive/reset/parity
tests, all 626 Agent test source classes (2,444 tests in the broad accounting,
zero failures/errors/skips, followed by passing final focused additions), the
78-test combat integration suite, the nine-scenario movement simulation lab,
and a clean Maven package. The broad suite was executed in controlled groups
because concurrent worktrees were also running long Surefire jobs; one
contention-sensitive movement assertion failed in the initial aggregate run and
passed immediately in isolation before complete class accounting reached zero
unrun classes.

Live-client validation is still required in Phase 2/3 for visible movement,
portal transitions, NPC/quest packet presentation, reactor drops, and recovery.
Phase 1 does not include objective capabilities, a plan loader/runner, persisted
plan progress, or the full Amherst sequence.

## Phase 2: Objective Capabilities And Plan Composition

### Goal

Compose Phase 1 primitives into each objective pattern required by Amherst and
prove a small ordered plan before attempting the full route.

Implement:

- NPC quest objective;
- item-use quest objective;
- combat quest objective;
- quest-item delivery objective;
- quiz objective;
- reactor-and-loot objective;
- plan-stop objective;
- plan-card loader, progress model, objective handlers, and live-state
  reconciliation;
- safe relog/restart resume and objective journal.

Representative tests:

| Pattern | Quest |
| --- | --- |
| NPC delivery | `1031` |
| Item use | `1021` |
| Combat | `1037` |
| Quest-item delivery | `1038` |
| Quiz | `1009` |
| Reactor and loot | `1008` |
| Scope blocker | `1028`, Training Center, or Shanks travel |

Run a minimal multi-objective plan such as `1031 -> 1021 -> stop`.
Verify child failure, cancellation, persisted progress, relog reconciliation,
and resume without duplicate rewards.

### Phase 2 Exit Gate

- every Amherst objective type passes independently;
- all gameplay work is delegated to Phase 1 primitives;
- a multi-objective plan completes through handoff/resume;
- live state overrides stale persisted progress;
- relog/restart resumes at the first unsatisfied objective;
- forbidden travel becomes a structured blocker;
- no objective force-completes its quest.

### Implemented Phase 2 Contract

Phase 2 is implemented by the typed objective parents in
`server.agents.capabilities.objective` and the Amherst plan runtime in
`server.agents.plans.amherst`.

The existing JSON card is parsed into `AmherstPlanCard` and
`AmherstPlanObjective`. Parsing, structured validation, command construction,
and execution are separate stages. Every current JSON kind is accepted by the
declarative handler registry:

| Card kind | Typed objective command |
| --- | --- |
| `quest-start`, `quest-complete` | `NpcQuestObjectiveCapability.Command` |
| `quest-chain`, `quest-chain-if-available` | `NpcQuestObjectiveCapability.Command` with ordered operations |
| `use-item` | `InventoryUseObjectiveCapability.Command` |
| `kill-mobs` | `CombatQuestObjectiveCapability.Command` |
| `reactor-hit`, `reactor-box-items` | `ReactorLootObjectiveCapability.Command` |
| `stop-plan` | `PlanStopObjectiveCapability.Command` |

Specialized quest-item delivery, quiz, and auto-complete parents are also typed
and independently tested. The current compact card expresses those quests in
chains, so the chain handler coordinates their normal quest transitions while
the specialized parents remain available for the expanded Phase 3 card.

Objective progress is a per-character, per-plan atomic JSON snapshot. Its
lifecycle states are `PENDING`, `RUNNING`, `SATISFIED`, `BLOCKED`, `FAILED`, and
`CANCELLED`; attempts, reason/message, timestamps, and capability journal
correlation are durable. Runtime frames and gameplay objects are transient.
Equivalent progress transitions are idempotent.

Before assignment and after capability success, `AmherstObjectiveReconciler`
checks live quest status/progress, inventory, map, and stop constraints. Live
completion can satisfy missing progress, while a reset can reopen stale durable
success. The runner therefore resumes from the first objective that is truly
unsatisfied and cannot replay a reward solely because a file is absent or
outdated.

The runner is integrated ahead of the normal active-capability gate, assigns
only one objective, and otherwise leaves ordinary Agent ticks unchanged. Its
plan journal records objective start, child handoff/result, retry,
reconciliation, blocker, cancellation, terminal result, and completion.

Automated Phase 2 evidence includes all eight objective parents, structured
scope blockers, retries/timeouts/cancellation, card validation, atomic progress,
stale-state reconciliation, and a fresh-runtime
`1031 -> 1021 -> stop` proof without repeated quest completion.

This does not complete Phase 3. No full 26-quest Amherst route has been run.
Live-client movement/dialogue/reactor packet presentation remains unverified,
and non-adjacent map jumps must be expanded to direct portal steps or handled by
the Phase 3 route planner.

For controlled live validation, the GM6 `!amherst` command provides guarded
reset, paginated objective listing, status/journal inspection, cancellation,
and manual one-objective-at-a-time execution. Manual mode requires an explicit
`next` or `retry` after every terminal objective and reports child capability
events to the owning player. It does not add a force-skip or bypass any live
reconciliation rule.

## Phase 3: Full Amherst MVP

### Goal

Execute `plans/maple-island-amherst-subphase.plan.json` from a fresh test Agent
and prove the complete beginning-to-Amherst vertical slice.

Run:

1. Apply `amherst-mvp-clean`.
2. Load and validate the Amherst plan card.
3. Execute the ordered route from map `10000`.
4. Complete the selected 26-quest roster.
5. Stop at map `1000000` Amherst.
6. Verify final live quest, inventory, map, and journal state.

### Phase 3 Acceptance

- final map is `1000000`;
- every required quest has the expected live status;
- excluded and later-map quests remain outside completion scope;
- no Training Center entry or Shanks/off-island travel occurs;
- every objective runs through Plan Runtime and Capability Runtime;
- no direct quest or item bypass is used;
- no unresolved retry loop remains;
- relog/restart recovery does not duplicate rewards;
- the objective journal contains the complete capability trace.

## Phase 1 Goal Prompt

Use the following prompt when Phase 1 implementation begins:

```text
Work on Phase 1 of the Amherst MVP in the Cosmic Agents repository.

Goal:
Implement and verify the complete capability runtime foundation and all
primitive capability adapters required by the Amherst sub-phase. Do not build
the full Amherst plan runner or execute the full questline in this phase.

Authoritative scope:
- docs/agents/AMHERST_MVP_PHASED_IMPLEMENTATION_PLAN.md
- docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md
- docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_TEST_PLAN.md
- docs/agents/plans/maple-island-amherst-subphase.plan.json

Required work:
1. Audit the current reconstructed Agent tick, navigation, combat, loot,
   inventory, quest, NPC, reactor, recovery, and integration boundaries before
   editing. Preserve existing behavior and work with current repository
   patterns.
2. Record focused navigation and combat baseline/parity tests before rewiring.
3. Implement an allowlisted, feature-flagged Amherst test reset service with
   clean-lv1-start, quest-scenario, amherst-ready, and amherst-mvp-clean modes.
   Direct mutation is allowed only for fixture setup.
4. Replace the marker-only capability contract with typed commands, results,
   statuses, reason codes, and an active-frame runtime supporting one executing
   frame, child handoff, parent suspension/resume, timeout, bounded retry,
   cancellation, blockers, and terminal live-state verification.
5. Integrate the active capability into the Agent tick while retaining exact
   legacy behavior when no capability is active. Do not rewrite navigation,
   combat, or loot algorithms.
6. Add the minimum live-state readers and Cosmic execution gateways required
   for map/position, quest state/start/complete, NPC interaction, inventory,
   item use, monsters, portals, reactors, and recovery.
7. Implement primitive capability adapters for Navigation, PortalTravel,
   Combat, Loot, InventoryInspection, ItemUse, NpcInteraction,
   QuestState/Start/Complete, ReactorInteraction, Recovery, and
   FinalStateVerification. Navigation and Combat must delegate to the existing
   reconstructed behavior.
8. Add capability journal events for start, handoff, child start/result, parent
   resume, retry, blocker, cancellation, and terminal result.
9. Add focused unit and smoke tests in the order specified by the Phase 1 test
   plan. Prove navigation/combat parity and legacy tick fallback.
10. Run focused tests, the Agent regression suite, and a clean compile/package.
    Fix regressions attributable to this work without changing unrelated code.

Constraints:
- Only Phase 1 is in scope. Do not implement Objective Capabilities, the Amherst
  plan loader/runner, the full quest sequence, generalized autonomy, economy,
  LLM behavior, or unrelated refactors.
- Only one capability frame may execute at a time. A waiting parent remains on
  the stack but does not tick while its child runs.
- Capabilities must return structured terminal results and verify live state.
- No quest force-completion, target-item grant, teleport-as-navigation success,
  or other gameplay bypass is allowed in capability tests. Test setup may reset
  or seed fixtures explicitly.
- Preserve the current reconstructed navigation, combat, movement, packet,
  timing, loot, and recovery behavior unless a failing parity test proves a bug.
- Keep Cosmic-specific operations behind the established integration boundary.
- Do not modify or revert unrelated user changes in the worktree.

Phase 1 completion criteria:
- every required primitive accepts typed parameters and reaches a deterministic
  terminal result;
- success is confirmed from live server state;
- retry, timeout, cancellation, blocker, and handoff/resume tests pass;
- navigation and combat parity tests pass;
- legacy Agent behavior remains active when no capability is assigned;
- reset functionality is inaccessible outside explicit test configuration and
  allowlisted Agents;
- focused and broader regression tests pass;
- implementation documentation reflects the final contracts and any accepted
  limitations.

Work autonomously through inspection, implementation, tests, and documentation.
Do not stop at a proposal. At completion, report the changed architecture,
capability contracts, tests run, remaining Phase 2 work, and any live-client
validation that could not be automated.
```
