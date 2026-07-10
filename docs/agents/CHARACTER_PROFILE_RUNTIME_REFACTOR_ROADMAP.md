# Character Profile Runtime Refactor Roadmap

Status: design for later review and implementation.

Related documents:

- `docs/agents/DOUBLE_AGENT_CHARACTER_STATE_EXTRACTION_PLAN.md`
- `docs/agents/DOUBLE_AGENT_POST_RECONSTRUCTION_SPECIFICATION.md`
- `docs/agents/ACCOUNT_QUEST_INHERITANCE_SPECIFICATION.md`
- `docs/agents/POST_RECONSTRUCTION_AGENT_PLATFORM_SPECIFICATION.md`

## Purpose

This roadmap defines how to refactor `client.Character` without changing
existing gameplay behavior, introduce a general profile-backed character
runtime, migrate headless Agents to the same runtime, and finally implement
Double Agent as a gameplay feature.

The architecture must support more than two characters. Pair switching is the
first feature, not a permanent limitation of the framework.

## Primary Decision

Double Agent must not become the replacement character framework.

Use this dependency direction:

```text
Character compatibility facade
        -> Character Profile Runtime
        -> Profile Binding and Transition Runtime
        -> Double Agent gameplay feature
```

The removed experimental `server.doubleagent.DoubleAgentService` directly
copied fields, replaced database records, and stored JSON snapshots. Its
retrospective is recorded in `docs/agents/DOUBLE_AGENT_POC_RETROSPECTIVE.md`.
Production Double Agent should eventually contain only gameplay policy and
invoke the generic transition runtime.

## Core Concepts

### Canonical Character Profile

The persistent state belonging to one database character:

- canonical character and account IDs.
- permanent name and identity metadata.
- progression, job, level, experience, AP, and SP.
- base HP/MP and stats.
- inventory, equipment, cash equipment, and mesos.
- learned skills, keymap, and macros.
- quest state, fame, achievements, and other persistent progress.
- persistent pet ownership.
- profile version and persistence metadata.

Canonical ownership never changes when a profile is used by another actor.

### Live Character Actor

The live server object occupying the world:

- map object ID.
- map, position, foothold, stance, and movement state.
- real or headless client endpoint.
- human, Agent, script, or future controller.
- current map visibility.
- current NPC, trade, shop, storage, and event interactions.
- actor locks and actor-bound scheduled execution.

An actor may temporarily use a profile owned by another canonical character.

### Controller

The source of actions for an actor:

- real player client.
- Agent runtime.
- scripted sequence.
- future possession, replay, or cutscene controller.

Controller identity is independent of profile ownership.

### Effective Form

The gameplay state resolved by the actor at a moment in time. For full profile
use, the effective form comes from the attached profile. For lighter features,
it may include temporary overlays:

```text
canonical attached profile
    + temporary form overlay
    + equipment-derived values
    + buffs/debuffs
    + map/event modifiers
    = effective character state
```

Full Double Agent switching uses profile binding. Disguises, temporary forms,
event normalization, and borrowed skills should prefer overlays.

### Presentation

What clients see:

- appearance and visible equipment.
- job and level where exposed.
- pets and foreign buffs.
- display name or alias where the client supports it.
- switch effects and animations.

Presentation is synchronized from effective state but is not itself canonical
persistence.

## Compatibility Strategy

Keep `client.Character` as the public facade throughout the migration.

Existing code continues to call:

```java
character.getInventory(type);
character.getJob();
character.getSkills();
character.getSkillLevel(skill);
character.getBuffedValue(stat);
character.getHair();
character.saveCharToDB();
```

The implementation behind those methods changes incrementally. This protects
the hundreds of packet handlers, commands, scripts, quests, map services, and
Agent classes that currently depend on `Character`.

Do not combine storage extraction with public API replacement. Public contracts
can be tightened after profile-backed behavior is stable.

## Target Components

Names remain provisional.

```text
Character
  compatibility facade and live actor orchestration

CharacterProfile
  canonical owner-aware persistent aggregate

ProfileAppearanceState
  gender, skin, face, hair, presentation metadata

ProfileProgressionState
  job, level, experience, base stats, HP/MP, AP/SP

ProfileInventoryState
  all inventory categories, slots, equipment, cash equipment, mesos

ProfileSkillLoadoutState
  learned skills, keymap, macros, version

ProfileQuestState
  quest statuses, progress, completion metadata

ProfilePetState
  canonical pet ownership and configuration

CharacterEffectRuntime
  actor-bound buffs, cooldowns, summons, tasks, and portable snapshots

CharacterProfileBinding
  current actor-to-profile attachment and generation

CharacterProfileSynchronizer
  self-client and observer refresh

CharacterProfileRepository
  owner-aware load/save

CharacterControlGroup
  authorized actors and profiles participating in transitions

CharacterTransitionService
  atomic one-way attachment, swap, and N-way rotation
```

State classes must not directly access maps, clients, packet opcodes, Agent
entries, or database connections.

## Ownership Matrix

### Profile-Owned

- canonical character/account IDs.
- progression and base stats.
- experience, AP, SP, and mesos.
- inventory and all equipment.
- skills, keymap, and macros.
- quest progress and completion.
- persistent pet records.
- fame and profile achievements.

Mutations to this state save to the profile owner ID.

### Actor-Owned

- client connection and authentication session.
- map object ID.
- map, position, foothold, stance, and camera anchor.
- movement controller and visible-object set.
- current NPC conversation, trade, shop, storage, or event interaction.
- map-specific controlled monsters and summons until portable effect handling
  explicitly supports them.

### Policy-Dependent

- public name/display alias.
- party, guild, buddy, and family presentation.
- event-instance membership.
- quest script session.
- drop ownership and kill attribution during transitions.
- death penalties and respawn state.
- mounts, doors, chairs, and special map objects.

Every policy-dependent field needs an explicit decision before full switching.

## Transition Invariants

The transition runtime must enforce:

- each actor has at most one active profile.
- each mutable profile is attached to at most one actor.
- detached profiles may be held inactive.
- canonical profile owner IDs never change.
- all bindings in one transition commit atomically.
- all involved actors are authorized for the control group.
- no involved actor has an unsafe modal interaction.
- Agent controllers are paused and drained before binding changes.
- every committed transition increments a generation.
- deferred work from an older generation aborts.
- failure restores the complete previous binding map.
- persistence is not performed on the latency-critical switch path.

## N-Way Control Groups

Do not encode a fixed counterpart field in the generic runtime.

Example group:

```text
actors:
  HumanActor
  AgentActorA
  AgentActorB

profiles:
  Kiwi
  KiwiAgent
  LimeAgent

initial bindings:
  HumanActor -> Kiwi
  AgentActorA -> KiwiAgent
  AgentActorB -> LimeAgent
```

Supported transitions can include:

- two-way swap.
- select a detached profile.
- cycle profiles forward or backward.
- rotate three or more active bindings.
- park a profile while an actor is inactive.
- restore canonical bindings.

The generic API should accept a complete desired binding map:

```java
TransitionResult transition(
        CharacterControlGroup group,
        Map<CharacterActorId, CharacterProfileId> desiredBindings,
        TransitionReason reason);
```

The service validates the complete permutation before changing any binding.

## Transition Flow

1. Resolve the control group and authorization.
2. Validate desired bindings and profile uniqueness.
3. Reject unsafe actor interaction states.
4. Acquire group and actor locks in stable order.
5. Mark transition status and increment pending generation.
6. Pause and drain Agent/script controllers.
7. Gate incoming state mutations for involved actors.
8. Export portable actor-bound effect state where supported.
9. Exchange or replace profile bindings.
10. Rebind inventory runtime callbacks to attached actors.
11. Recalculate derived stats and validate HP/MP.
12. Rebuild pets and supported effect runtimes.
13. Invalidate skill, equipment, combat, and movement caches.
14. Commit generation and binding map.
15. Synchronize human clients and map observers.
16. Resume controllers.
17. Append the transition journal asynchronously.

The critical path must not perform database, WZ, LLM, pathfinding, or remote
work.

## Delivery Phases

### Phase 0: Behavioral Baseline

Create characterization tests before moving storage.

Required baselines:

- character DB load/save/reload.
- appearance and equipment packets.
- inventory operations.
- job advancement and stat recalculation.
- skill, keymap, macro, and cooldown behavior.
- buffs, pets, summons, and expiry.
- quest progression and rewards.
- normal client behavior.
- `BotClient` and Agent behavior.

Known defects are documented separately from desired behavior. Refactoring must
not silently fix or preserve them without an explicit decision.

### Phase 1: Incremental Character Refactor

Extract storage behind unchanged `Character` methods in this order:

1. appearance.
2. skills, keymap, and macros.
3. inventory and equipment.
4. progression and base stats.
5. profile quest state.
6. pets.
7. owner-aware persistence.
8. effect runtime.

After every extraction:

- run focused unit and characterization tests.
- run player smoke tests.
- run headless Agent smoke tests.
- compare packet behavior.
- save and reload.

No profile switching exists in this phase.

### Phase 2: One-To-One Profile Runtime

Every live actor receives a profile binding, but only to its own canonical
profile:

```text
Kiwi actor -> Kiwi profile
KiwiAgent actor -> KiwiAgent profile
```

Introduce:

- binding generation.
- profile versioning.
- centralized mutation and synchronization.
- owner-aware repository operations.
- full client refresh operations.
- transition journal schema, initially unused for switching.

Exercise the runtime with one normal player and prove gameplay is unchanged.
This is not yet Double Agent.

### Phase 3: Independent Agent Adoption

Run a database-backed Agent through the same one-to-one profile runtime without
a human player present.

Verify:

- movement and navigation remain actor-owned.
- combat uses attached job, skills, equipment, and buffs.
- loot, shops, trades, and rewards mutate the attached profile.
- EXP, mesos, quest progress, and death consequences target profile ownership.
- self packets remain optional through `BotClient`.
- public map updates still reach real observers.
- caches refresh from profile version changes.
- save, dismissal, relog, and server restart retain behavior.

Add Agent quiescence and generation cancellation here.

### Phase 4: Generic Transition Runtime

Enable transitions in controlled development mode.

Test in order:

1. detach and reattach one actor's own profile.
2. attach a synthetic appearance-only overlay.
3. park an Agent profile and attach it one-way to a player actor.
4. restore canonical binding.
5. perform a two-actor profile swap.
6. perform a three-actor rotation.
7. inject failure at every transition stage and verify rollback.

Never attach one mutable profile to two actors concurrently.

### Phase 5: Double Agent Gameplay

Implement Double Agent as a thin feature layer:

- resolve allowed roster or counterpart.
- choose target profile or cycle direction.
- enforce skill trigger and cooldown.
- validate gameplay restrictions.
- invoke `CharacterTransitionService`.
- play effects and user-facing messages.

The feature layer must not:

- copy profile fields.
- replace inventories directly.
- execute SQL.
- rebuild buffs itself.
- own rollback snapshots.
- know Agent cache internals.

### Phase 6: Hardening And Expansion

- soak testing with repeated rotations.
- disconnect and crash recovery.
- multiple human clients where authorized.
- detached/background profile behavior.
- roster selection UI or command policy.
- additional form-overlay gameplay features.
- performance profiling and packet minimization.

## Centralized Mutation

Profile mutations should pass through one coordinated boundary so state, derived
values, versioning, persistence dirtiness, and packets cannot diverge.

Illustrative API:

```java
character.mutateProfile(MutationReason.EQUIP_ITEM, mutation -> {
    mutation.moveInventoryItem(source, destination);
    mutation.markEquipmentChanged();
});
```

A mutation should:

- validate ownership and generation.
- acquire locks once.
- mutate one attached profile.
- update profile version once.
- invalidate affected caches once.
- produce a synchronization delta.
- avoid immediate database work unless explicitly required.

## Synchronization Contract

The profile synchronizer should support focused deltas and complete refreshes:

- self stats.
- appearance and public look.
- inventory categories and slot limits.
- equipment and cash equipment.
- learned skills.
- keymap, quickslots, and macros.
- pets.
- buffs, debuffs, and cooldowns.
- party-visible HP/job information.
- map observer spawn/look state.

The stock client may require remove/re-add sequences or chunked inventory
operations. Dynamic IGN remains a separate client-presentation limitation.

## Persistence And Recovery

Repositories must save profile-owned state using the canonical profile owner ID,
not the currently attached actor ID.

The normal switch path does not swap database rows. Canonical rows remain
canonical, so restart can safely restore normal actor-to-profile bindings.

Maintain a small journal containing:

- control-group ID.
- transition ID and reason.
- old and new binding maps.
- generation.
- status and timestamps.
- failure detail.

The journal is for recovery and audit, not the primary source of character
state.

## Test Strategy

### Unit

- state component behavior.
- ownership and binding invariants.
- N-way permutation validation.
- generation checks.
- mutation versioning.
- synchronization-delta construction.
- effect snapshot duration preservation.

### Integration

- DB load/save ownership.
- inventory and item ID integrity.
- skill and quest persistence.
- real and headless client paths.
- Agent pause/drain/resume.
- transition rollback.
- server restart recovery.

### Real Client

- appearance and equipment refresh.
- full inventory interaction.
- skill window, keymap, and macros.
- pets and supported buffs.
- no camera or position jump.
- repeated pair swap and N-way rotation.
- disconnect at each transition stage.

### Performance

- transition latency percentiles.
- packet count and payload size.
- no database work in the critical path.
- no duplicate Agent scheduled work after generation changes.
- no per-character task growth caused solely by class decomposition.

## Definition Of Done

The framework is ready for Double Agent when:

- all extracted domains preserve baseline behavior.
- player and Agent paths use the same profile-backed `Character` facade.
- one-to-one bindings survive save/reload and restart.
- profile ownership is independent of actor identity.
- Agent controllers can be paused, drained, invalidated, and resumed.
- complete client synchronization is proven.
- one-way attachment and restoration are lossless.
- N-way transition rollback is deterministic.
- no item, reward, quest progress, or skill is saved to the wrong owner.
- the experimental snapshot POC can be removed without losing required
  production behavior.
