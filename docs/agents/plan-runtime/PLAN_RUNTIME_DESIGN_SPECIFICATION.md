# Plan Runtime Design Specification

Purpose:

```text
Define the portable runtime that loads Plan Cards, selects objectives, manages
plan progress, handles sidetracks, and routes objectives to capabilities after
Agent reconstruction is stable.
```

The Plan Runtime is preparation only until the reconstructed Agent engine has
stable capability boundaries.

## Design Rule

```text
Plan Runtime owns plan flow.
Capabilities own action execution.
Profile owns preference.
Catalog owns static facts.
Server Adapter owns live validation.
```

The Plan Runtime must not directly move an Agent, attack a mob, mutate
inventory, start a quest, complete a quest, buy items, or call raw Cosmic APIs.
It chooses and tracks objectives, then submits typed capability commands.

Plan Runtime should submit objective capability commands, not manually
micromanage every movement/NPC/combat sub-step. Objective capabilities may
request primitive capability handoffs through Capability Runtime. Plan Runtime
advances only after the objective capability verifies the objective end state.

## Goals

- Load reusable Plan Cards from a portable bundle.
- Validate entry criteria before assignment.
- Track current objective, completed objectives, blocked objectives, retries,
  sidetracks, and exit criteria.
- Support ordered, dependency-based, opportunistic, profile-weighted, and
  LLM-directed objective modes.
- Keep plan progress resumable after relog or server restart.
- Emit structured events and decision journal entries.
- Let Maple Island MVP be the first concrete plan.
- Keep the runtime portable across Cosmic-like servers.

## Non-Goals

- Do not implement gameplay capabilities.
- Do not replace navigation, combat, looting, NPC, shop, or quest systems.
- Do not let LLM commands bypass validators.
- Do not force every Agent to use the same objective timing or approach style.
- Do not store raw `Character`, `MapleMap`, `MapObject`, or BotClient objects in
  portable plan state.

## Runtime Layers

```text
Plan Card Bundle
  reusable plan definitions and schemas

Plan Repository
  loads and validates plan cards

Plan Assignment Service
  assigns a plan to an Agent after entry criteria pass

Plan State Store
  persists active plan, objective statuses, sidetracks, retries, and journals

Plan Scheduler
  chooses which active plan/objective should run next

Objective Resolver
  converts plan objectives into objective capability command requests

Capability Router
  dispatches objective commands through Capability Runtime. Capability Runtime
  owns primitive handoffs such as navigation, NPC interaction, combat, loot,
  inventory, item use, and reactor interaction.

Event Bus
  publishes objective started/completed/blocked/retried/sidetracked events
```

## Plan Lifecycle

```text
available
  -> assigned
  -> validating-entry
  -> active
  -> objective-running
  -> sidetracked
  -> paused
  -> completed
  -> failed
  -> cancelled
```

State meanings:

- `available`: plan exists but is not assigned.
- `assigned`: plan was selected for an Agent.
- `validating-entry`: runtime checks level, map, quest state, profile policy,
  forbidden actions, and catalog availability.
- `active`: plan can advance objectives.
- `objective-running`: a capability command is in progress.
- `objective-waiting-on-capability`: an objective capability is paused while a
  child primitive capability frame runs.
- `sidetracked`: another temporary plan is stacked above this plan.
- `paused`: no objective should advance until resumed.
- `completed`: exit criteria passed.
- `failed`: plan reached a terminal blocker.
- `cancelled`: human, LLM, or profile policy stopped the plan.

## Objective Modes

### Ordered

Use the listed order exactly.

Example:

```text
Maple Island deterministic route.
```

### Dependency Order

Use dependency graph and select any unblocked objective whose dependencies are
complete.

Example:

```text
Collect multiple quest items in convenient map order.
```

### Opportunistic

Choose from eligible objectives by travel cost, profile preference, risk, and
live context.

Example:

```text
Complete nearby quests while already in Kerning.
```

### Profile Weighted

Let profile weights influence next objective.

Example:

```text
Grinder prefers high-efficiency training objective.
Quester prefers quest objective.
Farmer prefers valuable drop objective.
```

### LLM Directed

LLM can propose objective priority, sidetrack, or cancellation, but Plan
Runtime still validates and executes through capabilities.

Example:

```text
LLM tells a group of Agents to help one Agent farm a quest item.
```

## Sidetrack Model

Plans may allow sidetracks for:

- emergency recovery.
- social help request.
- market opportunity.
- LLM command.
- direct navigation-to-point.
- supply refill.
- rare drop handling.

Sidetracks are stacked:

```text
main plan
  sidetrack plan
    emergency recovery objective
```

Rules:

- Main plan state remains intact.
- Sidetrack has its own exit criteria and time budget.
- Sidetrack must declare whether the Agent returns to the main plan.
- Hard plan constraints still apply unless explicitly suspended by policy.
- Forbidden actions are inherited by default.

## Focus Policy

Each plan declares a focus policy:

- `LOW`: Agent may freely sidetrack for social/economy/profile reasons.
- `MEDIUM`: Agent may sidetrack within time/risk limits.
- `HIGH`: Agent only sidetracks for emergency or explicitly allowed reasons.
- `LOCKED`: no sidetracks except survival/recovery.

Maple Island MVP should start with `HIGH` focus.

## Exit Criteria

Exit criteria must be machine-checkable:

- all required objectives complete.
- specific quest states.
- specific final map.
- required inventory state.
- allowed incomplete quests.
- blocked completed quests.
- forbidden actions not taken.
- optional profile decision to postpone.
- LLM/human cancellation if allowed.

Plan Runtime should verify exit criteria from live server state, not only local
plan state.

## Failure And Recovery

Failure should produce structured blockers:

- `MISSING_NPC`
- `MISSING_PORTAL`
- `QUEST_REQUIREMENT_NOT_MET`
- `QUEST_STATE_MISMATCH`
- `INVENTORY_FULL`
- `LOW_HP_RECOVERY`
- `INSUFFICIENT_SUSTAIN`
- `DEATH_LOOP`
- `FORBIDDEN_ACTION`
- `CAPABILITY_MISSING`
- `CATALOG_FACT_MISSING`
- `SERVER_VALIDATION_REJECTED`

Recovery is not embedded inside every objective. The Plan Runtime should call a
Recovery Policy package when the objective result is recoverable.

Capability handoff failures remain visible to Plan Runtime as objective
results. For example, a navigation child failure should resume or block the
parent objective, then the parent objective reports `BLOCKED` or
`FAILED_RETRYABLE` with evidence. Plan Runtime should not inspect primitive
movement internals to decide objective success.

## Relationship To Other Packages

Catalog Platform:

- supplies static route, NPC, quest, mob, drop, shop, and map facts.
- Plan Runtime treats catalog as hints, not live truth.

Profile Platform:

- supplies plan weights, focus preferences, risk tolerance, and hard identity
  constraints.
- Plan Runtime records decisions and outcomes back as events.

Economy Engine:

- may propose buy/sell/farm/hold objectives.
- does not directly mutate plan state.

Server Adapter:

- validates live quest, map, NPC, item, skill, HP/MP, and inventory state.
- executes capability-approved mutations.

LLM Gateway:

- may assign plans, propose sidetracks, request direct navigation, or suggest
  profile patches.
- cannot bypass Plan Runtime policy.

## Maple Island MVP Requirements

The first implementation must support:

- load and run `maple-island-amherst-subphase.plan.json` as the first smoke
  before the full Southperry plan.
- load `maple-island-mvp.plan.json`.
- objective capability commands with explicit primitive handoff/resume.
- ordered route mode.
- live quest validation before each quest start/complete.
- forbidden Shanks travel rule.
- allowed Shanks quest completion for quest `1026`.
- start-only quest `1046`.
- block quest `1028` completion.
- recovery blockers instead of force-complete.
- deterministic mode with interaction realism disabled.
- resume state after relog/restart.

## Success Criteria

The Plan Runtime is ready when:

- plans load from JSON and validate against schema.
- a plan can be assigned to one Agent.
- objective progress is persisted and resumable.
- objective runner emits structured results.
- forbidden actions are enforced before capability dispatch.
- one active capability frame and child handoff/resume are visible in objective
  journal output.
- Amherst sub-phase runs fully through Plan Runtime plus Capability Runtime,
  without direct scripted bypass around capabilities.
- Maple Island MVP can run through the Plan Runtime without hardcoded route
  logic outside the plan card and catalog.
- LLM can assign or sidetrack plans through typed commands without direct server
  mutation.
