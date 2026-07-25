# Agent engine modularity audit

## 2026-07-25 audit snapshot

This audit scanned the complete `server.agents` production tree, its Agent tests, configuration
references, architecture guards, compatibility annotations, reverse imports, and common temporary
file patterns. It also ran the architecture/configuration boundary suite, focused tests for every
changed runtime, and a clean compile. A complete-suite attempt produced 3,832 test results
before exceeding the execution window; its one reported TownLife failure was an outdated mocked
arrival position, corrected and verified separately. The assembly archive step could not read a
compiled class held by the live Windows server/build environment, so it was not treated as a code
failure or silently worked around by deleting the active build tree.

Current scale:

| Measure | Result | Interpretation |
|---|---:|---|
| Production Java files | 1,501 | Broad capability coverage, but costly to navigate |
| Agent test files | 839 | Strong unit and contract coverage |
| Files at or below 25 lines | 436 | Mostly records, ports, commands, and package markers; do not merge these indiscriminately |
| Capability imports of plan implementations | 19 | Known reverse-dependency migration debt; now prevented from increasing |
| Feature-specific top-level objective authorities | 5 files | Frozen by an architecture allowlist pending universal-executor migration |
| Source-tree temporary artifacts | 0 | No `.orig`, `.rej`, `.bak`, editor backup, or temporary files found |

The audit removed two redundant autonomy-cycle starts from executor exception paths. A plan step
already opens its cycle before invoking a step executor; reopening it in the catch block repeated
work and obscured the single-authority lifecycle. Completion remains idempotent if snapshot capture
itself fails.

The audit also restored configuration traceability for the independent observer controller and
moved the remaining Lith Harbor arrival threshold into `agent-engine.yaml`. These changes do not
alter the owning capability boundaries.

### Complexity that is justified

- Small immutable records and narrow gateway ports keep mutable Cosmic objects out of policy and
  orchestration. Their size alone is not evidence that they should be merged.
- Revalidation around delayed NPC, shop, trade, and inventory mutations is necessary because the
  live world may change between scheduled steps.
- Compatibility adapters remain while commands, tests, persisted checkpoints, or plan cards still
  reference them. Deleting an adapter before migrating its consumers would increase regression
  risk.
- Strict YAML parity and documentation checks intentionally enforce the project requirement that
  Agent policy numbers remain tunable and documented.

### Simplification priorities

1. Replace the five allowlisted feature-specific objective registrations with goal proposals
   consumed by the universal executor.
2. Move the 19 capability-to-plan imports behind neutral contracts or content catalogs, lowering
   the ratchet after each migration.
3. Retire deprecated Amherst, Southperry, movement, and ownership facades only after their test,
   command, checkpoint, and documentation consumers have moved.
4. Organize the 1,501-file tree through package registries and ownership documentation; merge only
   proven pass-through types with a single consumer, rather than applying a file-count target.
5. Keep capability-local validation, but remove duplicate orchestration checks when the same
   invariant is already guaranteed synchronously by the caller and covered by a contract test.

### Ratings

| Category | Rating | Basis |
|---|---:|---|
| Boundary clarity | 8/10 | Pure contracts, profiles, autonomy, TownLife, and Cosmic mutation boundaries are guarded |
| Modularity | 8/10 | Capabilities are well separated; 19 reverse plan imports remain |
| Maintainability | 7/10 | Strong naming/docs/tests, offset by 1,501 production files and compatibility breadth |
| Testability | 9/10 | 839 Agent tests plus schema, checkpoint, configuration, and architecture guards |
| Extensibility | 8/10 | Universal plans, typed capabilities, immutable snapshots, and LLM-safe gateways are in place |
| Regression resistance | 8/10 | Strong ratchets and focused tests; parallel legacy authorities remain the largest risk |
| Runtime scalability readiness | 7/10 | Central scheduling and simulation tiers exist; release-scale soak evidence remains incomplete |
| Overall | **7.9/10** | Sound foundation with bounded, explicit migration debt |

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
