# Agent engine modularity audit

## Outcome

The supported progression, behavior, combat, TownLife, navigation, and interruption paths use
explicit ownership boundaries. Configuration and personality may change policy values without
owning objectives or concrete capability mechanics. TownLife may pause a progression plan without
knowing its schema or executor. A progression plan may invoke capabilities without absorbing their
internal state machines.

Architecture tests enforce the highest-risk boundaries. This document records the intended
dependency direction and the remaining compatibility seams so later work does not recreate hidden
coupling.

Agent-owned configuration follows
[`AGENT_ENGINE_CONFIGURATION.md`](AGENT_ENGINE_CONFIGURATION.md). Cosmic `config.yaml` no longer
owns Agent deployment or tuning. Capability policy numbers are resolved from `agent-engine.yaml`;
content catalogs and structural invariants remain versioned with their owning domain.

## Dependency direction

```text
declarative profile / catalog / plan JSON
                  |
                  v
       semantic policy and contracts
                  |
                  v
 objective / plan / TownLife orchestration
                  |
                  v
       independent capabilities
                  |
                  v
 neutral integration gateway contracts
                  |
                  v
          Cosmic adapters and server
```

Dependencies do not point upward:

- profiles do not import combat, movement, TownLife, progression, or Cosmic implementations;
- personality supplies semantic choices and ranges; policy adapters translate them into concrete
  capability modes;
- combat owns legality, target reachability, commitment, damage, and cooldowns;
- behavior policy may rank an already-legal target or delay optional acquisition, but cannot
  create a target, deal damage, or complete an objective;
- presentation owns visual-only emotes, fidgets, chairs, and flourishes;
- plans own lifecycle cursor, retry, timeout, checkpoint, resumption, and chaining;
- registered plan steps own capability-specific execution only;
- TownLife owns town activities, venues, reservations, encounters, fidelity, and controller
  directives, not quest-plan rows;
- navigation owns route feasibility and movement paths;
- Supplies and TownLife request routing through `PrimitiveCapabilityGateway.travelTo`, not a
  Victoria progression class;
- Cosmic adapters translate neutral contracts into mutable server calls.

## Personality layering

`AgentBehaviorProfile` is a versioned semantic data model. For example, navigation fidgets are
declared as `WAIT`, `PRONE`, or `PRONE_TAP`; the movement policy maps those values to animation
implementations. The profile never stores `AgentFidgetMode`.

The layering rule is:

1. generic capability settings define safe, correct behavior;
2. personality selects bounded policy preferences;
3. session adaptation adjusts bounded runtime signals;
4. capability code validates the resulting request and executes it;
5. presentation projects observer-visible effects.

Disabling personality or any individual variation must reveal generic behavior without changing
plan progress, objective identity, or capability state formats.

## Shared interruption contract

`AgentForegroundPauseRuntime` is runtime-owned rather than plan-owned. It supports overlapping
pause reasons and an effective clock. TownLife, crowd respite, and short behavioral interludes use
stable reason keys. Universal and compatibility plan runners consume the effective clock.

This provides:

- pause without discarding the active objective;
- safe overlap between TownLife, crowd respite, and future maintenance;
- resume only after the last owner releases its reason;
- timeout accounting that excludes paused time;
- no dependency from behavior or TownLife to a concrete executor.

Maintenance that replaces foreground work, such as resupply, remains an objective-kernel
suspension. A short presentation pause and a maintenance objective are deliberately separate.

## Progression plan boundary

All progression plans follow
[`UNIVERSAL_AGENT_PLAN_SCHEMA.md`](UNIVERSAL_AGENT_PLAN_SCHEMA.md). Amherst, Southperry, full Maple
Island, Southperry-to-Lith, and the five career paths are definitions loaded by one strict
repository and executed by one checkpointed executor.

The Amherst ordered-card runner and Victoria first-job state machine are compatibility step
implementations, not competing top-level plan schemas. New lifecycle semantics must be added to
the universal model and migrated across every applicable indexed plan.

## TownLife boundary

TownLife retains its specialized town-agnostic schema because venues, activities, roles,
encounters, and fidelity are not progression steps. Per-town files select extensions such as the
Lith Harbor arrival ceremony. Generic TownLife core is prohibited from importing progression or
plan implementations.

Town-specific extensions may reference local content identifiers needed for a ceremony. They must
not add a second TownLife lifecycle, control a progression cursor, or bypass neutral navigation.

## Compatibility seams and migration policy

Some older or test-only systems still contain named content adapters, including Amherst reset
harnesses, the legacy script-based party-quest runner, and event classes historically housed under
progression. They are not part of the new universal lifecycle and are not permission to add new
reverse dependencies.

When touching one of these seams:

1. extract a semantic contract or neutral gateway first;
2. keep mutable Cosmic calls in `integration/cosmic`;
3. move shared event contracts out of a feature implementation package when practical;
4. retain a delegating compatibility adapter only while an existing caller still needs it;
5. add an architecture test before removing the old exception.

## Enforced checks

`AgentArchitectureBoundaryTest` verifies:

- pure contracts and profiles do not leak mutable Cosmic runtime types;
- profiles do not own capability, plan, progression, or Cosmic implementations;
- generic TownLife core does not depend on progression or plans;
- the foreground pause contract is runtime-owned;
- high-risk concrete capability dependency counts cannot increase.

`AgentCosmicBoundaryAuditTest` verifies operational Cosmic dependencies remain in approved
adapters. Repository and executor tests verify every indexed plan uses the common schema and every
operation is registered.

`AgentConfigurationBoundaryTest` verifies:

- Cosmic configuration contains no Agent-owned keys;
- every typed Agent deployment setting exists exactly once;
- every runtime tuning reference has exactly one YAML value and no stale tuning survives;
- literal numeric policy constants and mutable literal policy defaults cannot return to Agent
  source.

## Completion checklist for cross-capability changes

- Is the new type data, policy, orchestration, capability, gateway, or adapter?
- Does its package match that ownership?
- Can generic behavior run when its personality variation is disabled?
- Can the personality/profile schema change without modifying combat or TownLife mechanics?
- Can TownLife stop without changing the progression cursor?
- Can a plan resume after TownLife, crowd respite, resupply, relog, and retry?
- Does routing go through a neutral contract?
- Are observer-only visuals separated from authoritative state?
- Are all switches independently reversible?
- Is every new timing, weight, threshold, policy coefficient, capacity, or bound in
  `agent-engine.yaml` rather than capability code?
- Is a number being correctly retained as content or a structural invariant instead of
  mislabeled as runtime tuning?
- Do architecture, schema, checkpoint, and focused behavior tests pass?
