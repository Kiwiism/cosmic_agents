# Double Agent POC Retrospective

Status: historical documentation. The experimental implementation was removed.

Original implementation commit:

```text
09d329486d Add experimental Double Agent swap POC
```

Default-disabled commit:

```text
72197720be Disable Double Agent POC by default
```

Future design documents:

- `docs/agents/CHARACTER_PROFILE_RUNTIME_REFACTOR_ROADMAP.md`
- `docs/agents/DOUBLE_AGENT_CHARACTER_STATE_EXTRACTION_PLAN.md`
- `docs/agents/DOUBLE_AGENT_POST_RECONSTRUCTION_SPECIFICATION.md`

## Purpose Of The POC

The proof of concept tested whether a stock v83 client could appear to switch
between a player character and a same-account Agent without reconnecting or
moving the camera to another server-side character object.

The intended demonstration was:

```text
Player Kiwi casts Nimble Feet
        -> Kiwi receives selected KiwiAgent stats and appearance
        -> active KiwiAgent receives selected Kiwi state
        -> both remain at their existing map positions
        -> the Agent runtime continues ticking its Agent actor
```

It was explicitly incomplete and was never intended as a production character
ownership model.

## Implementation Surface

The POC added or modified:

- `config.yaml`
  - `DOUBLE_AGENT_ENABLED`.
  - `DOUBLE_AGENT_COOLDOWN_MS`.
- `config.ServerConfig`
  - matching configuration fields.
- `server.doubleagent.DoubleAgentService`
  - trigger, snapshots, database replacement, live application, and recovery.
- `client.Character`
  - bulk POC methods for stats, skills, and equipped items.
- `SpecialMoveHandler`
  - intercepted beginner mobility skills before normal skill execution.
- `PlayerLoggedinHandler`
  - restored active snapshot sessions before loading a character.
- `DoubleAgentRestoreCommand`
  - manual restore command.
- `CommandsExecutor`
  - `doubleagentrestore` and `tagrestore` registration.
- `character_tag_sessions`
  - JSON recovery snapshots and session status.

No dedicated automated test suite was added with the experiment.

## Trigger Behavior

When enabled, the POC intercepted these beginner-family skills:

- Beginner Nimble Feet.
- Noblesse Nimble Feet.
- Legend Agile Body.
- Evan Nimble Feet.

The interception happened in `SpecialMoveHandler` before the normal effect,
cost, cooldown, and buff path. The POC returned from the handler after handling
the trigger, so the mobility buff itself was intentionally not applied while
the feature was active.

A separate in-memory timestamp map imposed a minimum one-second guard. This was
not the normal MapleStory skill cooldown system and did not survive restart.

With the POC disabled, the service returned false and the handler continued,
but the production code still retained an unnecessary database check in the
skill-level mismatch condition. Full removal restores the exact pre-POC handler
path.

## Counterpart Resolution

The counterpart name was derived as:

```text
<player IGN> + "Agent"
```

The lookup required:

- the same account.
- the same world.
- the derived exact character name.

If the counterpart was live, it had to be the Agent registered to the current
player in `AgentRuntimeRegistry`. Another online form of the counterpart was
rejected.

This pair-only naming convention was useful for a demonstration but unsuitable
for a future roster containing multiple characters and Agents.

## Safety Checks

The trigger rejected the player while any of these were active:

- trade.
- shop.
- minigame.
- hired merchant.
- event instance.
- cash shop.
- NPC conversation.
- quest conversation.

These checks reduced obvious interaction corruption but did not provide an
atomic transition barrier, Agent quiescence, generation cancellation, or a
complete inventory/profile lock.

## Snapshot Contents

The JSON/live snapshot contained:

- job and level.
- gender, skin, face, and hair.
- STR, DEX, INT, and LUK.
- current and maximum HP/MP.
- AP and SP books.
- learned skill levels, master levels, and expirations.
- keymap.
- equipped inventory entries, including normal and cash equipment.

It did not contain:

- complete inventory bags.
- mesos or experience.
- skill macros or quickslots.
- pets.
- buffs, debuffs, or cooldowns.
- quest progress.
- fame or social state.
- map/position state.
- event and interaction state.
- Agent capability/runtime state.

The result was an appearance, equipped-item, stat, job, skill, and keymap
overlay rather than a complete character switch.

## Persistence Behavior

Before applying live state, the POC opened a database transaction and replaced
selected data between the two canonical character IDs:

- character row fields for stats, job, level, appearance, HP/MP, AP, and SP.
- all learned skill rows.
- all keymap rows.
- all equipped inventory/equipment rows.

It then inserted an active `character_tag_sessions` row containing the original
and overlay JSON snapshots.

This meant the POC did not merely alter presentation. It temporarily rewrote
canonical database rows so normal character saving would be less likely to
immediately undo the overlay. That choice also created the largest corruption
and recovery risk.

## Restore Behavior

Casting the trigger again while an active session existed restored both JSON
snapshots and marked the session restored.

The manual commands provided the same recovery path:

```text
@doubleagentrestore
@tagrestore
```

Before normal character loading, `PlayerLoggedinHandler` also searched for an
active session and restored both database snapshots. Login disconnected on
runtime recovery failure.

This recovery path was necessary only because the POC rewrote canonical rows.
The future owner-aware binding model must not require row swapping on each
transition.

## What The POC Demonstrated

The experiment established that the stock client could convincingly refresh a
large portion of the controlled actor in place:

- job and primary stats changed without reconnecting.
- appearance changed without moving the camera.
- normal and cash equipment could be refreshed.
- job-exclusive learned skills became available to the controlled actor.
- keymap could be refreshed.
- the server-side Agent actor could continue existing at its position.

The player-facing illusion was therefore feasible without transferring the real
network client to the Agent `Character` object.

## Observed Problems And Limitations

### Incomplete State

The POC did not switch full inventory, macros, pets, buffs, cooldowns, quests,
mesos, experience, or other profile state. The controlled actor could therefore
look switched while significant gameplay ownership remained unchanged.

### Buff Trigger Conflict

Nimble Feet was both the trigger and a real buff skill. Interception occurred
before normal skill execution, so enabling the POC replaced the buff behavior.
This confirmed that a production trigger must invoke a tested transition
service without accidentally changing the underlying skill when disabled.

### Concurrent Agent Skill Mutation

Live skill replacement occurred while the Agent runtime could be iterating its
learned-skill map and rebuilding combat caches. This produced
`ConcurrentModificationException` warnings during testing. Refreshing only the
movement profile did not pause Agent ticks or invalidate all affected caches.

### Equipment Replacement

Early iterations exposed asymmetry where the Agent received the player's
equipment while the player retained its own. The final POC rebuilt equipped
inventory and refreshed the look, but this still bypassed the normal complete
inventory ownership and equip/unequip lifecycle.

### Skill And Persistence Side Effects

Removing skills through `Character.changeSkillLevel(...)` performed immediate
database deletes using the live actor's character ID. Bulk database replacement
also assumed a fixed schema and canonical row ownership.

### Pair-Only Architecture

The implementation assumed one `<IGN>Agent` counterpart and stored one
controlled/counterpart pair. It could not safely express detached profiles,
selection from a roster, or N-way rotations.

### No Atomic Runtime Transition

There was no shared transition lock covering player packets, Agent ticks,
inventory mutation, effects, persistence, and rollback. JSON database snapshots
were a recovery mechanism, not an atomic live-state model.

## Why The POC Was Removed

The useful conclusion was feasibility, not suitability of the implementation.

Keeping the POC would:

- preserve a second, unsafe character-state mutation path.
- complicate the planned `Character` refactor.
- retain a skill interception that changes normal Nimble Feet behavior when
  enabled.
- retain pair-only assumptions before the N-way framework exists.
- retain direct SQL and schema coupling in gameplay code.
- risk stale Agent caches and concurrent mutation.
- encourage extending snapshots instead of establishing owner-aware profile
  boundaries.

The production direction is now:

```text
refactor Character behind compatible APIs
        -> prove one-to-one player profile runtime
        -> prove independent Agent profile runtime
        -> implement generic atomic N-way transitions
        -> implement Double Agent as a thin gameplay feature
```

## Removal And Restoration Map

Removal restores these pre-POC behaviors:

- beginner mobility skills always follow the normal
  `SpecialMoveHandler` validation, cost, cooldown, and effect path.
- skill-level mismatch validation no longer queries overlay session state.
- character login loads the canonical row directly without tag-session
  recovery or POC-specific runtime failure handling.
- `Character` has no bulk Double Agent mutation methods.
- restore commands are absent.
- Double Agent configuration fields are absent.
- no POC gameplay service or package remains.
- fresh databases do not create `character_tag_sessions`.

For existing installations, a retirement changeset drops the obsolete session
table only if it exists. Before removal, the local development database was
checked and contained no active sessions, so no recovery snapshot was pending.

## Lessons Preserved For Production

- keep actor/controller identity separate from profile ownership.
- preserve the controlled actor and camera for a smooth stock-client illusion.
- use owner-aware profile bindings instead of copying fields or swapping rows.
- pause and drain Agent execution before changing bindings.
- invalidate all profile-dependent caches by generation/version.
- keep buffs as portable snapshots plus actor-bound execution.
- synchronize inventory, equipment, skills, macros, pets, effects, and
  presentation through one service.
- support a roster and arbitrary binding permutation rather than a hard-coded
  pair.
- keep recovery journals small and never use them as the primary runtime state.
