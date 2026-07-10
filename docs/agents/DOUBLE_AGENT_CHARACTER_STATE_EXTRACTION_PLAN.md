# Double Agent Character State Extraction Plan

Status: implementation preparation for post-reconstruction review.

Related specification:

- `docs/agents/CHARACTER_PROFILE_RUNTIME_REFACTOR_ROADMAP.md`
- `docs/agents/DOUBLE_AGENT_POST_RECONSTRUCTION_SPECIFICATION.md`

This document records how the current server owns character state and defines a
parity-first extraction sequence. The extraction is useful beyond Double Agent:
both human-controlled characters and headless Agents should use the same state
model without separate behavior implementations.

The experimental Double Agent POC was removed and must not be recreated as the
foundation for this work. See
`docs/agents/DOUBLE_AGENT_POC_RETROSPECTIVE.md` for its historical behavior and
findings.

## Decision Summary

The proposed direction is feasible, but the new class must not initially
replace `Character` as the gameplay object.

Use this shape:

```text
Character actor
  owns client, map object, position, controller, locks, packet routing,
  interactions, scheduled runtime work, and derived caches

  delegates switchable reads and writes to

CharacterProfile
  owns canonical character identity and persistent gameplay state
  contains appearance, progression, inventory, skills/loadout, and pet state

CharacterEffectRuntime
  owns actor-bound execution of buffs, cooldowns, summons, and scheduled tasks
  can export/import profile-owned effect snapshots when switching is added
```

Existing callers should continue to call `Character.getInventory()`,
`Character.getSkills()`, `Character.getJob()`, `Character.getHair()`, and the
existing mutation methods during extraction. Those methods become delegates.
This preserves both player and Agent behavior while storage moves underneath.

Do not begin with a bulk rewrite of gameplay logic into `CharacterProfile`.
That would combine state, packet emission, persistence, map behavior, and task
scheduling in another monolith and make parity difficult to prove.

## Current Runtime Model

### Human Character

A human player uses a database-backed `client.Character` attached to a real
`Client`. Packet handlers mutate the `Character`, send self packets through the
client, and broadcast public changes through the map.

### Agent Character

An Agent also uses a database-backed `client.Character`.

The Agent lifecycle:

1. Creates a `BotClient` with no network channel.
2. Loads the normal character through `Character.loadCharFromDB(...)`.
3. Attaches that `Character` to the `BotClient`.
4. Registers it in channel, world, and map player collections.
5. Runs Agent ticks against the same `Character` APIs used by players.

`BotClient.sendPacket(...)` is a no-op, so self-only packets are harmless.
Public map broadcasts still reach nearby real clients. Therefore, an Agent is
not an alternate character data model and must not receive a separate profile
implementation.

## Current State Audit

### Inventory And Equipment

Current storage:

- `Character.inventory` is an `Inventory[]` indexed by `InventoryType`.
- Every `Inventory` contains items, its own lock, slot limit, type, and a direct
  `Character owner` reference.
- Normal and cash equipment are both represented in the `EQUIPPED` inventory.
  Cash appearance uses the extended negative equipment slots.
- The `CASH` inventory is the cash-item bag; it is not the complete set of
  visible cash equipment.

Current mutation behavior:

- Player inventory operations normally enter through
  `InventoryManipulator`, which takes a `Client`, resolves
  `client.getPlayer()`, mutates inventories, and sends inventory packets.
- Equip/unequip performs ring bookkeeping, mount updates, weapon/buff checks,
  inventory slot movement, `equipChanged()`, derived-stat recalculation, and
  public appearance broadcast.
- `Inventory` directly calls its owner for coupon-rate updates when certain
  items are added or removed.

Current persistence:

- `Character.saveCharToDB(...)` collects every item from every inventory and
  writes them using `Character.id`.
- Slot limits are stored in the character row.
- Item and equipment rows use the character ID supplied by the save path.

Current Agent use:

- Agent combat reads weapon and ammunition from `getInventory(...)`.
- Agent looting, shops, trades, supplies, and equipment optimization use the
  same inventories.
- Cosmic Agent inventory adapters call the normal `InventoryManipulator` with
  the Agent's `BotClient`.

Extraction constraints:

- Inventory ownership has two meanings that must be separated:
  canonical profile owner for persistence and currently attached actor for
  callbacks, packets, derived stats, and map broadcasts.
- A raw `Inventory[]` pointer swap is unsafe while each `Inventory.owner` still
  points at the old actor.
- All inventory categories, slot limits, equipped items, cash equipment, item
  pets, rings, and item metadata must remain one ownership bundle.

### Job, Stats, And Skills

Current storage:

- Base stats, HP/MP, AP, and SP live in `AbstractCharacterObject`.
- Job, level, experience, and related progression fields live in `Character`.
- Learned skills live in `Character.skills`, keyed by `Skill`.
- Key bindings live in `Character.keymap`.
- Five skill macros live in `Character.skillMacros`.
- Quickslot layout is persisted by account ID rather than character ID and is
  therefore not currently character-owned in the same way as keymap/macros.

Current mutation behavior:

- `changeJob(...)` is a job-advancement operation. It awards AP, SP, inventory
  slots, and randomized HP/MP growth, recalculates stats, sends packets, and
  respawns the public character representation.
- `setJob(...)` only assigns the field and sends no synchronization.
- `changeSkillLevel(...)` changes memory and sends a skill packet. Removing a
  skill also immediately deletes a database row using `Character.id`.
- Keymap and macro maps/arrays are mutable in place and are later persisted by
  the character save path.

Current persistence:

- Skills, keymap, and macros are loaded by character ID.
- Character save rewrites keymap and macros using `Character.id`.
- Skill save uses `REPLACE`; absent skills are not generally deleted by that
  save loop. The removed POC worked around this with explicit deletion, which
  must not be copied into the new profile runtime.

Current Agent use:

- Agent combat and equipment policy call `getJob()`, `getSkills()`, and
  `getSkillLevel(...)` directly.
- Agent combat caches derive a signature from the current learned-skill map.
- Agent cache classification gives in-job skills higher priority, but does not
  universally reject learned out-of-job skills.

Known eligibility gap:

- The player `SpecialMoveHandler` contains a job-tree validation block, but it
  is currently commented out.
- Keymap changes do enforce job-tree restrictions for bound skills.
- Production profile switching needs one shared skill-eligibility rule used by
  player packet execution and Agent planning/execution. That is a separate
  behavior correction and must not be hidden inside parity extraction.

Extraction constraints:

- Attaching an existing profile must not call `changeJob(...)`; doing so would
  grant advancement rewards each time.
- Job, base stats, SP, learned skills, keymap, and macros form one coherent
  loadout/progression boundary.
- Quickslot ownership needs an explicit product decision because current
  persistence is account-scoped.
- Cache invalidation must be explicit whenever the attached profile version,
  job, level, skills, or equipment changes.

### Appearance

Current storage:

- Gender, skin, face, and hair are scalar fields on `Character`.
- Visible equipment is derived from the `EQUIPPED` inventory.
- Active pet appearance comes from `Character.pets`.
- Name is part of the actor's spawn representation and is not included in the
  normal look-update packet.

Current synchronization:

- Hair/face/skin commands assign the field, send a self stat update, and call
  `equipChanged()`.
- `equipChanged()` recalculates equipment stats and broadcasts
  `UPDATE_CHAR_LOOK` to other map clients.
- `UPDATE_CHAR_LOOK` contains gender, skin, face, hair, equipment, rings, and
  related look data, but not IGN.
- `SPAWN_PLAYER` contains character ID, level, IGN, job, appearance, equipment,
  buffs, pets, position, and stance.

Extraction constraints:

- Appearance is the safest first live extraction because its scalar fields and
  packet behavior are easy to characterize.
- Visible equipment cannot be treated as a separate appearance copy; packet
  generation must continue reading the attached inventory.
- IGN switching remains a presentation problem and is outside the first state
  extraction.

### Buffs, Debuffs, Cooldowns, And Summons

Current storage:

- Active buff values are spread across `effects`, `buffEffects`,
  `buffEffectsCount`, and `buffExpires`.
- Diseases and their expirations use separate maps.
- Skill cooldowns live in `coolDowns`.
- Summons, doors, mount state, chairs, and several special effects have their
  own fields.
- Buff, cooldown, recovery, Dragon Blood, Beholder, chair, disease, and other
  behavior use scheduled tasks stored on `Character`.

Current execution behavior:

- `StatEffect.applyTo(...)` ultimately registers effects on a specific
  `Character`.
- Many scheduled callbacks capture `Character.this` and later mutate its HP,
  buffs, map objects, or packets.
- Derived stats read both equipped items and current buff state.
- Player login starts buff, disease, cooldown, item-expiry, and quest-expiry
  tasks.

Current Agent difference:

- Offline Agent loading currently starts the disease-expiry task, but does not
  mirror the complete player-login startup sequence for character buff,
  cooldown, and item-expiry tasks.
- Agent combat also owns separate planning cooldown/cache state.
- This difference must be recorded as baseline behavior. It should be reviewed
  independently rather than accidentally changed during extraction.

Current persistence:

- Normal character save does not persist active buffs as ordinary character
  row state.
- Buffs are transferred through `PlayerBuffStorage` during channel/cash-shop
  transitions.
- Cooldowns and diseases have dedicated save/load handling.

Extraction constraints:

- Scheduled tasks and map objects cannot be moved by changing a profile
  pointer.
- Buffs must be represented as portable snapshots containing source, value,
  start time, and absolute expiry, then cancelled and rebuilt on the attached
  actor.
- Special effects require adapters because their runtime behavior is more than
  a stat-value map.
- Buff/effect extraction must be the last state domain, after profile binding,
  owner-aware persistence, and Agent quiescence exist.

### Pets

Current storage:

- Summoned pets live in a three-slot array on `Character`.
- Pet items live in inventory and are loaded with the rest of character items.
- Pet records persist independently and refer to their owning character during
  behavior such as tameness/fullness updates.

Extraction constraints:

- Pet item ownership and summoned-pet runtime must remain consistent.
- A switch requires despawn, runtime rebind, and respawn; copying the pet array
  is insufficient.
- Pet hunger/world registrations must be transferred or restarted explicitly.

## Target Classes

Names are provisional and should be reviewed against reconstruction package
conventions.

```java
final class CharacterProfile {
    private final int ownerCharacterId;
    private final int ownerAccountId;
    private final ProfileProgressionState progression;
    private final ProfileAppearanceState appearance;
    private final ProfileInventoryState inventory;
    private final ProfileSkillLoadoutState skillLoadout;
    private final ProfilePetState pets;
    private long version;
}

final class CharacterProfileBinding {
    private CharacterProfile attachedProfile;
    private long generation;
}

final class CharacterEffectRuntime {
    private final Character actor;
    // Existing effect maps and scheduled execution move here only in the final
    // extraction phase.
}
```

`CharacterProfile` is a state aggregate, not a service locator. It must not know
about maps, network clients, packet opcodes, Agent entries, or database
connections.

`Character` remains responsible for actor behavior and delegates profile-backed
accessors. Packet builders should continue accepting `Character`, so existing
player and observer packet behavior remains unchanged.

## Persistence Rule

Every profile-backed repository operation must use
`CharacterProfile.ownerCharacterId`, never the ID of the actor currently using
the profile.

This applies to:

- character progression and appearance row fields.
- inventory and equipped items.
- skills.
- keymap and macros.
- pets and pet ignores.
- cooldown/profile snapshot persistence when added.

Map position, map ID, actor OID, controller/client, and transient interactions
remain actor-owned and continue using actor identity.

During extraction, no switch is enabled until save/reload tests prove that a
profile attached to a different actor still saves to its canonical owner.

## Parity-First Migration

### Phase 0: Characterization Tests

Before moving fields, capture current behavior for both real `Client` and
`BotClient` paths.

Verify:

- getters return loaded DB state.
- appearance command packet sequence and public look packet bytes.
- inventory add/remove/move/equip/unequip state and packets.
- cash equipment masking and visible look.
- job advancement side effects remain limited to `changeJob(...)`.
- skill add/update/remove packets and persistence.
- keymap and macro packets/persistence.
- item and skill buff application, expiry, cancellation, and derived stats.
- Agent equipment, inventory, skill selection, buff use, and combat results.

These tests establish the behavior baseline. Known current defects should be
labelled, not silently encoded as desired production behavior.

### Phase 1: Appearance State

Move gender, skin, face, and hair into `ProfileAppearanceState`.

Keep all existing `Character` getters and setters. Change only their backing
storage. Commands, NPC scripts, packet builders, and Agent visibility should
continue to use the existing APIs.

Live smoke test:

1. Use a feature-gated GM test command to apply a temporary appearance.
2. Verify self stat updates.
3. Verify nearby clients receive the same look update.
4. Save/reload and verify canonical persistence.
5. Run the same mutation against an Agent character and verify observers see
   the update even though its `BotClient` discards self packets.

### Phase 2: Skill Loadout State

Move learned skills, keymap, macros, and their version counter behind
`ProfileSkillLoadoutState`.

Keep existing getters during the first cutover. Add controlled mutation methods
before removing direct mutable-map access.

Required parity checks:

- skill window contents.
- skill level/master level/expiration.
- key bindings.
- macro definitions and macro key bindings.
- save/reload.
- Agent combat cache rebuild after version change.

Do not change skill job eligibility in this phase. Add central eligibility as a
separately tested hardening change after parity is demonstrated.

### Phase 3: Inventory And Equipment State

Move the complete inventory array and slot limits into
`ProfileInventoryState`.

First separate:

- persistence owner: profile owner character ID.
- runtime actor: currently attached `Character` used for coupon updates,
  derived stats, packet routing, ring/mount behavior, and map broadcasts.

Do not permit profile rebinding while an inventory mutation is in flight.

Required parity checks include every inventory type, stack merge/split, drops,
loot, shops, trades, equip/unequip, cash equipment, rings, pets, rechargeables,
slot expansion, full-inventory behavior, and Agent auto-equip.

### Phase 4: Progression And Core Stats

Move job, level, experience, base stats, HP/MP, AP, SP, and related persistent
progression state behind `ProfileProgressionState`.

Retain actor-owned derived-stat caches initially. Recalculate them whenever the
profile version changes.

Create a raw profile-attach path that does not invoke advancement behavior.
`changeJob(...)` must remain the explicit gameplay operation that awards job
advancement benefits.

### Phase 5: Pets

Move canonical pet ownership into `ProfilePetState`, while retaining an
actor-bound summoned-pet runtime. Test unsummoned and summoned pets, pet items,
hunger registration, pickup behavior, observer packets, save/reload, and Agent
operation.

### Phase 6: Cooldown And Effect Runtime

Extract current effect execution into `CharacterEffectRuntime` without changing
behavior. Only after parity is proven, add export/import snapshots for profile
switching.

Implement special adapters for at least:

- recovery and extra recovery.
- Dragon Blood.
- Beholder healing and buffs.
- summons and puppets.
- doors.
- mounts and morphs.
- chairs and map-chair effects.
- diseases.
- Battleship state.

Preserve absolute expiry so switching does not reset duration.

### Phase 7: One-Way Binding Rehearsal

Test profile binding before symmetric Double Agent behavior.

Appearance-only rehearsal may use a temporary synthetic profile because it
cannot duplicate inventory or economic state.

For a complete one-way rehearsal:

1. Pause and quiesce the Agent runtime.
2. Detach the Agent profile from the Agent actor.
3. Hold the player's original profile inactive.
4. Attach the Agent profile to the player actor.
5. Refresh the player client and map observers.
6. Exercise inventory, equipment, skills, keymap/macros, pets, and supported
   effects.
7. Restore both original bindings and verify persistence ownership.

Never attach one mutable inventory/profile to both actors concurrently.

### Phase 8: Agent Rewire And Parity

The Agent should not receive a new profile-specific behavior layer. Existing
Agent code should continue receiving its `Character` actor, whose getters now
resolve through the attached profile.

Add explicit hooks for:

- pause/quiescence before profile changes.
- generation checks for deferred callbacks.
- movement profile refresh.
- combat skill-cache invalidation.
- equipment and ammo cache invalidation.
- cancellation of stale scripted/capability work.
- resume only after the new profile is fully attached.

Run the Agent without a human owner present and prove that navigation, combat,
loot, inventory, equipment, buffs, saving, dismissal, and relogging retain the
same behavior.

### Phase 9: Symmetric Double Agent Switch

Only after the preceding phases pass should the pair runtime atomically exchange
profiles between the fixed human and Agent actors.

## Test Trigger Policy

### Commands

A feature-gated GM command is the preferred live smoke-test trigger during
extraction. It is explicit, repeatable, can target a player or Agent, and does
not alter normal skill behavior.

Suggested command surface:

```text
!profilestate inspect <character>
!profilestate appearance <character> <skin> <face> <hair>
!profilestate restore <character>
!profilestate verify-save <character>
```

The exact command name can change. It must remain disabled outside development
configuration.

### Beginner Skill Hook

A beginner-skill hook is suitable as a final real-client acceptance trigger,
not as the primary unit-test mechanism.

If Nimble Feet is reused:

- require a dedicated development flag.
- route through the same tested profile-binding service as the GM command.
- preserve normal Nimble Feet behavior when the flag is off.
- preserve server cooldown handling.
- never put persistence or profile-copy logic directly in
  `SpecialMoveHandler`.
- remove or disable the hook after acceptance testing unless it becomes the
  approved production trigger.

The removed Double Agent POC previously intercepted beginner mobility skills.
A future test hook must remain development-only and preserve normal skill
behavior whenever it is disabled.

## Test Matrix

### Pure Unit Tests

- profile component getters and mutation versioning.
- canonical owner ID remains stable across actor attachment.
- profile binding generation changes atomically.
- no profile may be attached mutably to two actors.
- raw profile attach does not grant AP, SP, slots, HP, or MP.
- inventory runtime actor callback changes without changing persistence owner.
- effect snapshots preserve absolute expiry and remaining duration.

### Characterization And Packet Tests

- self stat packets before and after extraction are byte-equivalent.
- public look packets before and after extraction are byte-equivalent.
- inventory operation packets retain ordering and contents.
- skill update, keymap, macro, pet, buff, and cooldown packets remain equivalent.
- Agent `BotClient` paths do not require a network session.

### Database Integration Tests

- load, mutate, save, reload for each profile component.
- save always targets the profile owner ID.
- one-way attachment cannot overwrite the actor's canonical row.
- inventory item IDs and pet IDs are neither duplicated nor lost.
- removed skills do not reappear after reload.
- rollback leaves both profiles canonical after injected failure.

### Agent Parity Tests

- offline Agent load exposes the same profile state as before extraction.
- movement and combat output remain unchanged for a fixed scenario and seed.
- skill cache rebuilds exactly once after a profile version change.
- auto-equip produces the same selected items and derived stats.
- loot, shop, trade, potion, ammo, and buff behavior use the attached profile.
- Agent save/dismiss/relogin persists to the profile owner.
- ownerless Agent operation does not dereference a human client.

### Real Client Smoke Tests

- change appearance live and observe from self and a second client.
- change equipped and cash-equipped items and verify both views.
- refresh full inventory without relogging.
- refresh skill window, keymap, and macros.
- apply a timed buff, exercise it, and verify exact expiry.
- perform one-way attach, play briefly, restore, relog both characters, and
  verify no state crossed database owners.

## Stop Conditions

Do not proceed to profile switching if any of these remain true:

- save paths still use actor ID for profile-owned state.
- an `Inventory` callback still targets a stale actor after binding.
- Agent ticks cannot be paused and drained.
- deferred work has no generation/cancellation guard.
- skill and equipment caches remain stale after profile changes.
- full inventory refresh cannot be applied safely to the stock client.
- buffs can only be copied by moving live scheduled tasks.
- player and Agent paths require separate state implementations.

## Recommended First Implementation Slice

The safest first implementation is intentionally small:

1. Add characterization tests for current appearance getters and packets.
2. Introduce `ProfileAppearanceState` owned by a default
   `CharacterProfile` inside every `Character`.
3. Delegate existing gender/skin/face/hair getters and setters to it.
4. Keep current commands, NPC scripts, packet builders, and save/load behavior
   unchanged except for the delegated storage access.
5. Add a development-only GM command for one-way appearance mutation and
   restore.
6. Run the same smoke test against a normal player and a headless Agent.

This slice proves the central compatibility claim: player and Agent behavior can
remain identical while state storage moves behind the existing `Character`
contract. Skills, inventory, progression, pets, and effects should only follow
after that proof is stable.
