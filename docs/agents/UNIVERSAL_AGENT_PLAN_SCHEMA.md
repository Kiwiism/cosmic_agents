# Universal Agent plan schema and chaining contract

## Decision

All progression plans use one strict, versioned schema and one lifecycle executor. A plan may use
different capabilities and registered step operations, but it may not introduce private lifecycle
fields or its own top-level execution loop.

This is a normative compatibility rule for all future progression work:

1. Every new progression plan must deserialize as `AgentPlanDefinition`, pass
   `AgentPlanSchemaValidator`, appear in `agents/plans/index.json`, and execute through
   `AgentPlanExecutor`.
2. A plan may not add an unrecognized top-level field, maintain an independent lifecycle cursor, or
   bypass the universal executor.
3. A plan-specific capability is added as a registered step operation with validated parameters.
4. If a requirement changes lifecycle semantics—branching, dependencies, retry, timeout,
   interruption, checkpoint, resumption, cancellation, or successor handoff—the common schema must
   be versioned and extended.
5. The same change must update the common Java model, strict loader, validator, executor,
   checkpoint migration, documentation, and tests.
6. Every existing plan to which the new common field or semantic applies must be migrated in that
   same change. Plans that do not use an optional field must receive an explicit, documented
   default rather than silently retaining old behavior.
7. A schema version is not considered supported until every indexed plan validates against it.

No plan is allowed to solve an immediate special case by creating a second progression schema.

TownLife is intentionally not represented as a progression plan. It keeps its specialized,
town-agnostic activity/directive schema and per-town extensions. TownLife and progression share the
foreground lifecycle contract: explicit activation, objective suspension, checkpoint ownership,
resumption, cancellation, and handoff. Town-specific behavior must not be added to the universal
progression schema.

## Current normalized plan catalog

| Plan ID | Scope | Registered operations | Successor policy |
|---|---|---|---|
| `maple-island-amherst-subphase` | Mushroom Town through the Amherst subphase | `ordered-objective-card` | none |
| `maple-island-southperry-mvp` | Amherst baseline through Southperry | `ordered-objective-card` | none |
| `maple-island-full-mvp` | Full Mushroom Town through Southperry run | `ordered-objective-card` | transfer is available |
| `southperry-to-lith-harbor` | Walk to Shanks, cross, and reach navigable Lith Harbor town-side ground | `southperry-lith-transfer` | five career plans are available |
| `victoria-level15-mvp` | Assigned career from Lith Harbor through first job and level 15 | `staged-first-job-journey` | none |
| `victoria-training` | Reusable level-15+ training to a supplied target up to level 30 | `victoria-training` | none |
| `victoria-*-level30` | One career bundle from its level-9/10 handoff through level 30 | `staged-first-job-journey`, `victoria-training` | none |

The five career plans are:

- `victoria-warrior-level30`
- `victoria-bowman-level30`
- `victoria-magician-level30`
- `victoria-thief-level30`
- `victoria-pirate-level30`

Thief and Pirate plan parameters admit both of their configured weapon build bundles.

All current cross-plan successors use `AVAILABLE`. This is deliberate while each slice is being
tested independently. Chaining can select an available successor without losing the existing chain
ID. Changing a successor to `AUTOMATIC` later enables unattended chaining without changing either
plan's executor.

## Required top-level fields

Every progression plan contains:

- `schemaVersion`
- `planId`
- `planVersion`
- `title`
- `status`
- `objective`
- `entryCriteria`
- `steps`
- `exitCriteria`
- `successors`

The repository uses strict JSON deserialization. Unknown fields fail startup, duplicate IDs fail
validation, missing successor references fail validation, and every step operation must be present
in the shared executor registry.

## Shared step contract

Every step contains:

- stable `stepId`;
- registered `operation`;
- declared `capabilityIds`;
- operation-specific `parameters`;
- `timeoutMs`;
- `retryBudget`.

`parameters` configure the registered operation; they do not grant it a private lifecycle. The
universal executor owns cursor advancement, timeout detection, bounded retry with backoff,
terminal status, successor availability, cancellation, and persistence.

Adding a capability-specific step requires:

1. implement `AgentPlanStepExecutor`;
2. register its unique operation key in `AgentUniversalPlanRuntime`;
3. express all generic lifecycle behavior through existing schema fields;
4. add a common schema field only when the new behavior applies consistently to every plan;
5. add repository and executor tests.

Do not dispatch on a plan ID inside the universal executor. Plan-specific behavior belongs behind a
registered operation.

## Schema change checklist

Every pull request that changes the plan schema must demonstrate:

- a deliberate `schemaVersion` decision and backward-compatibility statement;
- updated `AgentPlanDefinition` records;
- strict loader behavior for unknown or removed fields;
- validator rules and useful failure messages;
- executor semantics for every new field;
- durable checkpoint compatibility or migration;
- updates to all applicable JSON definitions and the plan index;
- repository, executor, retry/timeout, pause/resume, reattachment, and chain tests;
- updated examples and this contract document;
- confirmation that internal capability contracts were not accidentally placed in
  `agents/plans`.

An extension is incomplete if only the JSON accepts it. The runtime must enforce it consistently,
and all applicable plans must adopt it before the schema change is merged.

## Durable suspend and resume

The universal checkpoint stores:

- plan and version;
- chain ID;
- current step and attempt;
- whether the step was attached and when it started;
- serializable plan inputs;
- execution status and reason;
- delayed automatic successor;
- available successors;
- state revision and update time.

Transient observers and runtime attachments are deliberately excluded. On registration:

1. the objective checkpoint is restored;
2. the career checkpoint is restored;
3. the universal plan checkpoint is restored;
4. the current registered step reattaches its capability-specific runner.

Only an explicitly active universal checkpoint resumes execution. A career/build assignment by
itself does not activate Victoria progression. This prevents an ordinary spawned or follow-mode
Agent from unexpectedly starting a career plan.

Maintenance objectives suspend the durable foreground objective through `AgentObjectiveKernel`.
Short foreground interruptions use the capability-neutral `AgentForegroundPauseRuntime`, whose
effective clock excludes paused time. TownLife is checked before progression in
`AgentForegroundPlanRuntime`, allowing it to own presentation while the progression cursor remains
resumable. TownLife and behavior code therefore never import a plan executor merely to pause it.

## Compatibility adapters

The existing Amherst/Maple Island ordered-objective cards and the Victoria level-15 state machine
are retained as internal mechanics:

- `ordered-objective-card` delegates to the proven Amherst runtime;
- `staged-first-job-journey` delegates to the proven career state machine;
- `victoria-training` delegates to the occupancy-aware training runtime.

These resources are not alternative plan schemas. The Victoria stage resource lives under
`agents/progression/victoria-level15-stage-contract.json` and is explicitly a step implementation
contract. Overall ownership, chaining, pause/resume, and terminal state belong only to the universal
executor.

## Future order-independent plans

Later Victoria plans may be selected in any order when their entry criteria match. The decision
layer should rank eligible plan IDs from the repository; it must not execute quests directly.
Completion exposes successors or returns control to the selector. Quest packs, hunting objectives,
shopping trips, and equipment work should become reusable registered operations rather than new
top-level plan formats.

The long-term progression lifecycle is therefore:

```text
selector
  -> universal plan definition
  -> universal executor/checkpoint
  -> registered capability steps
  -> terminal postconditions
  -> available or automatic successor
  -> selector
```

## TownLife boundary

TownLife has different domain semantics: activities, venues, roles, encounters, reservations,
fidelity, and controller directives. Its specialized schema should be deployable to every town,
with optional per-town extensions selected by profile. It should share only the lifecycle envelope
with progression:

- one foreground owner at a time;
- suspend/resume rather than overwrite;
- durable intent where a visit must survive relog;
- bounded activity stages;
- cancellation and cleanup;
- an explicit completion/handoff result.

This keeps TownLife expressive and LLM-ready without coupling town behavior to quest-plan rows.

Cross-map travel follows the same dependency rule. TownLife, Supplies, and future interruption
capabilities call `PrimitiveCapabilityGateway.travelTo` and consume the neutral
`AgentRouteOutcome`. The Cosmic adapter selects the current route engine. A requesting capability
must not import a Victoria progression route runtime or route catalog directly.
