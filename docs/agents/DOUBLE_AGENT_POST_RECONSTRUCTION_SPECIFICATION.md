# Double Agent Post-Reconstruction Specification

Status: post-reconstruction review candidate.

Implementation status: blocked until the Agent reconstruction reaches the
required stable boundaries listed in this document.

Current production setting: disabled by default.

Historical proof of concept: the incomplete snapshot-based implementation was
removed after demonstrating that an in-place stock-client illusion is feasible.
Its behavior and limitations are preserved in
`docs/agents/DOUBLE_AGENT_POC_RETROSPECTIVE.md`. It must not become the
foundation of the final system.

Implementation preparation:

- `docs/agents/CHARACTER_PROFILE_RUNTIME_REFACTOR_ROADMAP.md` defines the
  general profile-backed Character refactor, one-to-one player and Agent
  adoption, N-way transition runtime, and Double Agent feature sequencing.
- `docs/agents/DOUBLE_AGENT_CHARACTER_STATE_EXTRACTION_PLAN.md` audits current
  player and Agent state handling and defines the parity-first extraction and
  test sequence that should precede profile switching.

## Purpose

Double Agent lets a real player and a same-account Agent exchange their complete
gameplay forms while each remains at its current map position and under its
current controller.

Example:

```text
Before

Kiwi actor
  controller: human client
  position: A
  profile: Kiwi

KiwiAgent actor
  controller: Agent runtime
  position: B
  profile: KiwiAgent

After

Kiwi actor
  controller: human client
  position: A
  profile: KiwiAgent

KiwiAgent actor
  controller: Agent runtime
  position: B
  profile: Kiwi
```

The player stays at position A and the camera does not move. The visible form at
position A becomes KiwiAgent. The Agent at position B becomes Kiwi and continues
ticking through the Agent runtime.

This is a simulated profile exchange. It is deliberately not a network client
handoff and not a same-channel reconnect.

## Product Goal

From the player's perspective, switching should feel immediate and complete:

- no logout or character-selection screen.
- no channel reconnect.
- no map loading screen.
- no camera jump.
- no visible pause beyond the switch animation.
- the active job, stats, appearance, equipment, inventory, skills, keymap,
  macros, pets, buffs, and cooldowns behave as the selected character's state.
- the Agent continues operating with the player's exchanged profile.

Simulation is acceptable whenever it is observationally equivalent to a real
switch for the player and does not corrupt canonical character ownership.

## Primary Design Decision

Use an owner-aware hot profile swap with fixed actors.

Do not:

- transfer the real network client to the Agent character.
- reconnect the client during the normal switch path.
- forward every player packet to the Agent and mirror every result back.
- copy fields ad hoc between two `Character` instances.
- rewrite both complete character database rows during every switch.

Each live actor resolves swappable state through its currently attached profile.
The switch exchanges two profile bindings atomically.

## Terminology

### Actor

The live map object and control endpoint. Actor state remains fixed during a
normal Double Agent switch.

Actor-owned state includes:

- live `Character` identity used by the map.
- map object ID.
- map, position, foothold, stance, and movement ownership.
- network client or headless Agent controller.
- party/map visibility membership.
- current trade, shop, NPC, storage, and event interaction handles.

### Profile

The swappable gameplay state. A profile always retains its canonical database
owner character ID even while attached to the other actor.

### Presentation Name

The name shown for the active profile. It is separate from the canonical
`Character.name` used for persistence, lookup, party identity, commands, and
account ownership.

### Pair Runtime

The shared runtime object referenced by the player actor and Agent actor. It
owns profile bindings, synchronization, swap generation, lifecycle status, and
recovery metadata.

## Proposed Runtime Model

```java
final class DoubleAgentPairRuntime {
    int playerActorId;
    int agentActorId;

    CharacterProfile playerProfile;
    CharacterProfile agentProfile;
    ProfileBindings bindings;

    DoubleAgentPairStatus status;
    long generation;
    Lock swapLock;
}
```

```java
record ProfileBindings(
        int playerActorProfileOwnerId,
        int agentActorProfileOwnerId
) {
    ProfileBindings reversed();
}
```

```java
final class CharacterProfile {
    int ownerCharacterId;
    long version;

    CoreProgressionState progression;
    AppearanceState appearance;
    SkillLoadout skills;
    InventoryBundle inventory;
    PetProfile pets;
    BuffRuntimeProfile buffs;
    CooldownProfile cooldowns;
}
```

The pair stores references to two canonical profiles. It must not maintain a
third mutable copy of either profile.

Each profile must be attached to exactly one actor while the pair is active.

## State Ownership Matrix

### Required Swappable State

The first complete implementation should switch:

- job.
- level and EXP presentation.
- STR, DEX, INT, and LUK.
- current and maximum HP/MP.
- AP and SP pools.
- fame where displayed as part of the active form.
- mesos if the player must see and spend the active profile's mesos.
- gender, skin, face, and hair.
- equipped items and cash-equipped items.
- complete EQUIP, USE, SETUP, ETC, and CASH inventories.
- inventory slot limits.
- learned skills, skill levels, master levels, and expirations.
- keymap.
- skill macros.
- quickslot presentation if the two profiles are allowed to differ.
- active pets and pet equipment.
- active buffs and debuffs.
- skill cooldowns and remaining durations.
- mount presentation where supported by the pet/buff phase.

### Actor-Owned State

The following should stay attached to the live actor for the smooth fixed-camera
design:

- map object ID.
- map and channel.
- position, foothold, stance, ladder/rope state, and movement state.
- network connection.
- human versus Agent controller.
- map visibility and controlled-monster ownership.
- current interaction windows.

### Review-Required State

The post-reconstruction review must decide whether these follow the profile or
remain with the actor:

- quests and quest progress.
- medal and monster-book progress.
- party, guild, buddy, family, and marriage identity.
- event-instance identity.
- gachapon/secondary progression values.
- saved map locations and teleport-rock state.
- cash-shop state.
- merchant, trade, and storage sessions.

For an initial combat-focused release, these should remain actor-owned and all
related windows must be closed before switching. A later total-progression mode
may move selected state into the profile after explicit review.

## Current Server Appearance Behavior

Appearance is currently distributed across `Character` fields and equipped
inventory:

- `gender`, `skinColor`, `face`, and `hair` live on `Character`.
- visible normal and cash equipment comes from `InventoryType.EQUIPPED`.
- pet look comes from active pet state.
- ring and marriage look is serialized separately.

When a character enters a map, `PacketCreator.spawnPlayerMapObject` sends the
initial visible representation, including character ID, IGN, level, job,
appearance, equipment, foreign buffs, pets, position, and stance.

The client caches that representation. Later movement, attack, stance, effect,
pet, buff, and look packets update it. The server does not send every animation
frame.

`PacketCreator.updateCharLook` updates appearance, equipment, and ring look for
an existing map object. It does not update IGN.

## Packet Feasibility

The existing v83 protocol already provides incremental packets for most of the
required experience:

- `STAT_CHANGED` for job and stat changes.
- `UPDATE_SKILLS` for skill add/update/remove behavior.
- `KEYMAP` for complete keymap initialization.
- `MACRO_SYS_DATA_INIT` for complete macro initialization.
- `INVENTORY_OPERATION` for item removal, addition, movement, and quantity
  updates.
- `INVENTORY_GROW` for slot-limit changes.
- `UPDATE_CHAR_LOOK` for appearance and equipment.
- `SPAWN_PET` for pet removal and spawn.
- `GIVE_BUFF` and `CANCEL_BUFF` for local buff icons and values.
- foreign buff packets for map observers.
- `COOLDOWN` for skill cooldown display.

A normal switch therefore does not require `SET_FIELD` or a reconnect except as
an optional fallback for client state that cannot be refreshed safely.

## IGN Constraint

IGN is the main stock-client presentation limitation.

`SPAWN_PLAYER` contains the character name, while `UPDATE_CHAR_LOOK` does not.
The local client may also cache the login character name in UI elements that do
not have a standard incremental update packet.

Requirements:

- never mutate canonical `Character.name` to simulate a profile name.
- never change player-storage lookup keys during a switch.
- never persist the presentation alias as the canonical IGN.

Available strategies:

1. Stock-client private mode.
   - Keep the canonical self IGN in UI.
   - Change appearance and all gameplay state.
   - Lowest risk but not a perfect total illusion.

2. Stock-client field refresh.
   - Reinitialize the local field/self character using a full field packet.
   - Must be validated for camera stability and visible blinking.
   - Not preferred for the smooth path.

3. Small custom-client presentation packet.
   - Update the local presentation name without changing server identity.
   - Recommended if a completely indistinguishable switch is required.

Foreign map objects can be despawned and respawned with a presentation name,
but this may flicker and does not solve every local self-name cache.

The final review must explicitly choose whether custom-client support is
allowed. Perfect seamless IGN switching is not guaranteed on an unmodified v83
client.

## Inventory Design

### Inventory Bundle

All inventory categories should move together as one owner-aware bundle:

```java
final class InventoryBundle {
    int ownerCharacterId;
    long version;
    Inventory[] inventories;
}
```

The bundle includes:

- EQUIPPED.
- EQUIP.
- USE.
- SETUP.
- ETC.
- CASH.
- category slot limits.

The switch exchanges bundle references through profile bindings. It must not
move database rows between character IDs.

### Inventory Persistence

Saving must use `InventoryBundle.ownerCharacterId`, not the actor ID currently
holding the profile.

The existing character autosave iterates each live `Character` and writes its
attached inventory under that character ID. This behavior is incompatible with
owner-aware profile switching and must be refactored before inventory swapping
is enabled.

Required rule:

```text
runtime actor != persistence owner
```

Inventory changes made while Kiwi uses KiwiAgent's profile must persist to
KiwiAgent's canonical inventory rows.

### Inventory Client Refresh

The client can display one inventory set at a time.

Switch refresh order:

1. snapshot the old displayed bundle.
2. swap profile bindings server-side.
3. send removals for all old displayed items.
4. send new slot limits.
5. send additions for all new displayed items.
6. refresh equipped stats and appearance.

`INVENTORY_OPERATION` stores operation count in one byte. Large refreshes must
be chunked into safe packet sizes. The switch coordinator must not assume every
inventory fits in one packet.

Pets, rings, cash IDs, rechargeable items, coupon effects, auto HP/MP bindings,
and equipment-derived local stats require dedicated validation.

## Skill, Keymap, And Macro Design

Each profile owns one coherent skill loadout:

```java
final class SkillLoadout {
    int ownerCharacterId;
    int jobId;
    Map<Integer, SkillEntry> learnedSkills;
    Map<Integer, KeyBinding> keymap;
    SkillMacro[] macros;
    QuickslotBinding quickslots;
    long version;
}
```

Skills, keymap, and macros must switch together. Sending only a new keymap is
incorrect because macro definitions are stored separately.

### Strict Skill Eligibility

The current Agent combat cache scans every learned skill with positive level.
Out-of-job skills receive lower priority but are not categorically rejected.
This is not acceptable for Double Agent.

One shared `SkillEligibilityPolicy` must gate:

- cache construction.
- attack planning.
- buff planning.
- healing.
- summoning.
- movement/utility skill use.
- final skill execution.

Minimum eligibility:

```text
learned level > 0
AND skill belongs to the active profile
AND skill belongs to the active job tree or is an allowed beginner skill
AND weapon requirements pass
AND HP/MP/ammo requirements pass
AND cooldown requirements pass
AND GM/event permissions pass
```

Execution must re-check eligibility even when the cache says a skill is ready.

### Agent Skill Cache

Do not compute cache identity by iterating a mutable skill map during the Agent
tick. Use a stable cache key:

```text
(profile owner ID, profile version, job ID, level)
```

Switching or changing a skill increments profile version. Cache rebuild occurs
after the swap barrier and before the Agent resumes combat.

## Buff And Cooldown Design

### Why Buffs Cannot Be Raw Pointer-Swapped

Current buff state is distributed across several `Character` maps and scheduled
tasks. Multiple scheduled tasks capture `Character.this` directly. Moving only
the buff maps would cause expiry, recovery, summon, or periodic effects to fire
on the wrong actor.

### Buff Profile

The logical buff profile should record:

- source skill/item ID.
- source level where required.
- active statups and values.
- start timestamp.
- absolute expiry timestamp.
- disease source and expiry.
- periodic-effect metadata.
- map-bound entity metadata where supported.

Remaining duration must be calculated from absolute expiry. Switching must not
reset a buff to full duration.

### Buff Switch Procedure

1. snapshot both actors' active effects under buff locks.
2. cancel old client and foreign presentations.
3. cancel actor-bound scheduled tasks that must be rebound.
4. exchange profile bindings.
5. reconstruct effects on the new actors with original start/expiry times.
6. recreate periodic tasks against the new actor.
7. send local buff packets to the human client.
8. broadcast foreign-visible buff state where required.

### Complexity Classes

Straightforward:

- pure stat buffs.
- ordinary debuffs/diseases.
- cooldown display and expiry.

Requires dedicated adapters:

- recovery and periodic HP/MP effects.
- Dragon Blood and similar periodic skills.
- Beholder schedules.
- summons and puppets.
- doors.
- mounts.
- morphs.
- map chairs and map-bound effects.
- party buffs whose propagation state may outlive the switch.

Unsupported effects must fail the switch before any binding changes. They must
not be silently dropped or reset.

## Pet Design

Active pets are profile-owned but map-presented through the actor.

Switch procedure:

1. despawn the old actor pets from relevant clients.
2. detach active pet presentation from the old actor.
3. exchange profile bindings.
4. attach the new profile's active pets to the actor presentation.
5. validate pet inventory links and pet equipment.
6. spawn the new pets using the actor's current position.

Pet unique IDs and database ownership remain canonical to the profile owner.

Pet movement state may restart from the actor position during the switch. This
is acceptable if hidden by the switch animation and does not reset hunger,
closeness, level, or expiration.

## Presentation And Broadcast Rules

The Agent has a headless `BotClient`; sending presentation packets to that
client has no visible effect.

The human client requires:

- self stat/job refresh for the player actor.
- complete skill diff.
- keymap and macro initialization.
- inventory replacement and slot-limit updates.
- cooldown replacement.
- old pet removal and new pet spawn.
- old buff cancellation and new buff application.
- self appearance/equipment refresh.
- an appearance refresh for the Agent map object.

If Double Agent is private to the player, presentation packets may be targeted
only to the human client.

If other players must see the switch, updated appearance and foreign-visible
buff/pet state for both actor IDs must be broadcast to every real client in the
map. Otherwise observers retain stale cached forms.

The final product decision must choose private illusion versus public world
presentation. Public presentation is recommended for consistency.

## Switch Transaction

### Preconditions

Require:

- feature enabled.
- same account and world.
- both player and counterpart Agent loaded live.
- both profiles available and valid.
- no active pair switch already in progress.
- player and Agent not in trade, storage, shop, cash shop, NPC dialogue,
  minigame, scripted event, or other unsupported modal state.
- no unsupported buff/pet/map-bound effect active.
- Agent runtime can enter the swap barrier.

Same-map presence is recommended for the first release. It simplifies public
presentation and avoids cross-map form updates.

### Fast Path

1. receive trigger.
2. immediately broadcast/play switch effect.
3. mark pair status `SWAPPING` and increment generation.
4. pause Agent tick and new capability execution.
5. reject or queue incoming mutable action packets for the short barrier.
6. acquire actor/profile locks in stable character-ID order.
7. capture client refresh diffs and runtime state that must be rebound.
8. exchange profile bindings.
9. invalidate derived stats and runtime caches.
10. rebuild actor local stats from attached profiles.
11. release structural locks.
12. send client refresh packets in deterministic order.
13. refresh Agent movement/combat/inventory/equipment caches.
14. mark pair status `ACTIVE_SWAPPED` or `ACTIVE_NORMAL`.
15. resume the Agent tick and player actions.

### Packet Order

Recommended human-client order:

1. cancel old local buff/cooldown/pet presentation.
2. update job, stats, HP/MP, level, EXP, AP/SP, and mesos.
3. remove obsolete skills and add/update new skills.
4. initialize keymap, macros, and quickslots.
5. remove old displayed inventory in chunks.
6. update inventory slot limits.
7. add new inventory in chunks.
8. apply new equipped appearance and recalculate local stats.
9. spawn new pets.
10. apply new buffs and cooldowns with remaining duration.
11. enable actions.

Exact order must be validated against the v83 client. The implementation may
split cosmetic and inventory refresh into adjacent ticks if one packet burst
causes client hitching, but gameplay input must not resume against a partially
switched server state.

## Smoothness Requirements

The switch critical path must contain no:

- database load.
- complete character save.
- WZ/XML scan.
- pathfinding or navigation rebuild.
- LLM request.
- remote service call.
- blocking wait.

Both profiles and required packet data must already be in memory.

Recommended optimizations:

- precompute profile diffs when profile version changes.
- cache immutable presentation snapshots.
- batch and chunk inventory operations safely.
- send the visible switch effect before heavier UI refresh packets.
- rebuild only caches whose profile/version key changed.
- keep the Agent hidden/offscreen policy independent of profile ownership.

The server-side binding exchange is O(1). Client refresh cost is proportional
to changed skills, inventory items, buffs, and pets.

No fixed latency promise should be made before real-client testing. Acceptance
should be based on frame continuity, input freeze duration, packet count, and
observed client hitching across empty, normal, and full inventories.

## Agent Runtime Requirements

The reconstruction must provide:

- an explicit per-entry pause/resume or swap barrier.
- capability cancellation or quiescence before switching.
- no tick access to mutable profile state while bindings change.
- profile-aware character, skill, inventory, and buff reads.
- strict active-job skill eligibility.
- deterministic cache invalidation.
- runtime generation checks for delayed tasks.
- a safe way to reject stale callbacks created before the switch.

Runtime caches requiring review include:

- combat skill cache.
- combat cooldown/action locks.
- equipped weapon and attack-route assumptions.
- ammo and potion supply state.
- equipment recommendation/build state.
- movement speed/jump profile.
- buff planning state.
- shop/trade/inventory cooldown state.
- scripted capability queues.

Any delayed task captures the pair generation. If generation changes before it
commits, it must abort or resolve state again through the current profile.

## Persistence Model

Canonical database ownership never changes during a switch.

```text
Kiwi profile data      -> Kiwi character ID
KiwiAgent profile data -> KiwiAgent character ID
```

This remains true regardless of actor attachment.

Required persistence changes:

- profile-owned save APIs.
- inventory save by bundle owner ID.
- skill/keymap/macro save by profile owner ID.
- pet save by profile owner ID.
- progression/stat save by profile owner ID where included.
- autosave awareness of active pair bindings.

The switch itself should not rewrite all profile rows.

A minimal pair-session journal may record:

- account ID.
- player actor ID.
- Agent actor ID.
- binding orientation.
- generation.
- status.
- activation and last-update timestamps.
- failure/restore reason.

Because canonical profile data remains under canonical owners, a server crash
can safely return to normal bindings on restart. The journal is used for audit,
reconciliation, and detecting incomplete persistence rather than restoring
copied database overlays.

## Failure And Recovery

### Before Binding Exchange

Abort without visible state mutation. Re-enable actions and resume the Agent.

### After Binding Exchange But Before Client Refresh Completes

Keep the server binding authoritative. Retry a complete client refresh from the
current binding rather than attempting packet-by-packet rollback.

### Agent Runtime Refresh Failure

Pause the Agent, restore normal bindings under the same coordinator, refresh the
human client, and mark the session failed.

### Player Disconnect

Recommended behavior:

1. pause the Agent.
2. save both profiles to canonical owners.
3. restore normal profile bindings in memory.
4. return the Agent runtime to its canonical profile.
5. despawn or retain the Agent according to normal lifecycle policy.
6. close the pair session.

### Server Restart

Load canonical character data normally. Do not replay a swapped binding unless
the post-reconstruction product explicitly chooses persistent switched login.

### Manual Recovery

Retain an administrative restore command, but implement it through the final
coordinator. The current snapshot restore code must not be reused as the final
recovery mechanism.

## Security And Validation

- counterpart must belong to the same account and world.
- counterpart must match the configured relationship/naming or explicit pair
  record.
- counterpart must be the registered active Agent for the player.
- one profile cannot be attached to two actors.
- one actor cannot participate in two active pairs.
- switch trigger must be server-authorized and rate-limited.
- item, pet, ring, and cash IDs must retain canonical ownership.
- out-of-job skills must fail at execution even if a stale cache contains them.
- GM/event skills require explicit permission.
- all switch failures must be auditable without exposing private inventory data.

## Historical POC Disposition

Useful concepts to retain:

- feature flag and cooldown guard.
- same-account counterpart lookup.
- trigger interception.
- safety preconditions for modal interactions.
- active-session audit concept.
- manual recovery entry point.

Implementation patterns to retire:

- direct ad hoc field replacement.
- database swapping of both character records.
- JSON snapshots as the primary runtime model.
- replacing mutable skill maps while the Agent tick may iterate them.
- rebuilding only movement profile after a complete form change.
- treating keymap as complete without macros.

The POC configuration and runtime have been removed. Any future feature flag
belongs to the profile-transition implementation and must remain disabled until
the acceptance criteria below pass.

## Reconstruction Gates

Do not start final implementation until all required gates are true:

1. Agent runtime reconstruction is formally closed or stable for the affected
   packages.
2. character, inventory, skill, packet, combat, map, and life side effects have
   stable integration boundaries.
3. Agent tick has an explicit pause/quiescence contract.
4. capability runtime supports cancellation/generation invalidation.
5. skill eligibility is centralized and strict by active job.
6. inventory and skill caches have explicit invalidation contracts.
7. autosave/persistence ownership can be separated from live actor identity.
8. real-client packet capture/testing infrastructure is available.

Design and test fixtures may be prepared earlier. Production runtime rewiring
must wait for these gates.

## Implementation Phases

### Phase 0: Review And Packet Proofs

- approve actor/profile ownership boundaries.
- decide private versus public presentation.
- decide stock-client versus custom-client IGN behavior.
- capture real-client packet behavior for complete skill/inventory/pet/buff
  refreshes.
- define unsupported buff/effect policy.

### Phase 1: Pair Runtime And Barrier

- implement pair lifecycle and binding model.
- implement Agent pause/resume and generation checks.
- add observability and failure recovery without switching gameplay state.

### Phase 2: Appearance And Core Stats

- switch job, stats, HP/MP, AP/SP, appearance, and equipped look.
- refresh both actor presentations.
- validate camera and movement continuity.

### Phase 3: Skills, Keymap, And Macros

- introduce coherent skill loadout.
- implement strict eligibility.
- replace mutable cache signature with profile version.
- refresh skills, keymap, macros, and quickslots.

### Phase 4: Full Inventory

- introduce owner-aware inventory bundles.
- refactor canonical persistence.
- implement chunked client replacement.
- verify equipment, cash equipment, ammunition, coupons, rings, and pet links.

### Phase 5: Cooldowns, Buffs, And Pets

- switch cooldowns by remaining duration.
- implement pure buff reconstruction.
- add periodic effect adapters.
- add pet despawn/rebind/spawn.
- add summons, mounts, and map-bound effects only after dedicated tests.

### Phase 6: Presentation Name And Public Visibility

- choose and implement presentation-name strategy.
- broadcast both actor form changes to map observers.
- verify party/map UI consistency.

### Phase 7: Persistence And Recovery Hardening

- canonical-owner autosave.
- disconnect and restart behavior.
- audit journal.
- manual restore through coordinator.
- fault injection at every switch stage.

### Phase 8: Enablement

- soak test repeated switching.
- leave disabled by default through one release/test cycle.
- enable only after acceptance and rollback criteria are met.

## Test Plan

### Unit Tests

- binding reversal and exactly-one-profile-per-actor invariant.
- stable lock ordering.
- profile owner persistence routing.
- strict job-tree skill eligibility.
- cache invalidation by profile version.
- inventory packet chunking.
- remaining-duration calculations for buffs and cooldowns.
- generation rejection for delayed tasks.
- unsupported-effect precondition failures.

### Integration Tests

- empty inventories.
- typical inventories.
- every inventory category near slot capacity.
- equipped and cash-equipped masking.
- different jobs and weapon families.
- macros referencing switched skills.
- agent attempts an out-of-job database skill.
- active pets with pet equipment.
- pure buffs with measured remaining duration.
- diseases and cooldowns.
- periodic buffs.
- summons, puppets, mounts, and doors.
- player and Agent in the same map at distant positions.
- nearby real observer receives both new looks.
- private presentation mode does not leak inconsistent public state.
- autosave while switched writes to canonical owners.
- item pickup/use/drop while switched updates the correct profile.
- Agent loot, potion, ammo, shop, and equipment behavior while switched.

### Fault Injection

- disconnect before binding exchange.
- disconnect after binding exchange.
- disconnect during inventory refresh.
- Agent tick exception during cache rebuild.
- database save failure while switched.
- server restart with an active journal row.
- stale delayed capability callback after generation change.
- repeated trigger inside cooldown window.
- simultaneous switch requests.

### Real Client Acceptance

Validate on the actual v83 client:

- no client crash.
- no map reload in normal path.
- no camera movement.
- no visible actor teleport.
- no stale skill icons.
- macros switch with keymap.
- inventory opens immediately with the active profile's items.
- item use affects the active profile owner.
- pets and buff icons match the active profile.
- cooldown timers retain remaining duration.
- nearby observers see the intended public presentation.
- IGN behavior matches the approved product decision.
- repeated switching does not accumulate stale map objects or tasks.

### Performance Acceptance

Measure:

- coordinator lock duration.
- time Agent tick remains paused.
- packet count and bytes per switch.
- event-loop blocking time.
- client frame hitch during empty, typical, and full inventory swaps.
- cache rebuild duration.
- heap/task growth over repeated switches.

Do not define a numeric latency SLA until packet captures and real-client tests
establish a stable baseline. The qualitative target is that switching feels like
one combat action, not a channel change.

## Observability

Record one structured switch trace containing:

- pair/session ID.
- actor IDs and profile owner IDs.
- old and new orientation.
- generation.
- precondition result.
- pause duration.
- lock duration.
- packet counts by category.
- inventory/skill/buff/pet counts.
- cache refresh result.
- persistence result.
- final status and failure reason.

Never log complete inventory contents, macro text, or sensitive account data at
normal log levels.

## Open Review Decisions

The post-reconstruction review must resolve:

1. Is Double Agent private to the player or visible publicly?
2. Is a custom client presentation-name packet allowed?
3. Must quests/progression follow the profile in the first release?
4. Do mesos, EXP, and fame follow the profile?
5. Which map-bound buffs/effects are supported initially?
6. Must both actors be in the same map?
7. Is the counterpart selected by `<IGN>Agent`, explicit pairing, or both?
8. What happens if the Agent is dead, in a scripted capability, or in a modal
   interaction?
9. Does disconnect always restore normal orientation?
10. Is switched state ever allowed to persist across login?

## Definition Of Done

Double Agent is complete only when:

- the removed experimental POC is not reintroduced as a switching path.
- profile ownership and actor ownership are explicit and tested.
- switch critical path performs no database or remote work.
- Agent pause/resume prevents concurrent profile mutation.
- skills are strictly legal for the active job.
- keymap and macros always switch together.
- full inventory operations persist to canonical profile owners.
- pets, supported buffs, and cooldowns retain correct state and duration.
- client and public presentation are synchronized.
- disconnect, restart, and failure recovery are deterministic.
- no duplicate item, stale task, or cross-character save is observed.
- real-client smoothness and soak tests pass.
- feature remains independently disableable and has a tested rollback path.

## Final Recommendation

After reconstruction, review this specification before writing production code.
If the stock-client IGN limitation is acceptable or a small custom client packet
is approved, the owner-aware hot profile swap is the best fit for the desired
gameplay:

- it preserves camera and position.
- it avoids reconnect lag.
- it allows the human and Agent to exchange complete gameplay forms.
- it makes the switch fast through reference binding rather than database copy.
- it keeps canonical database ownership stable.

The largest engineering work is not appearance broadcasting. It is separating
profile persistence from actor identity and safely rebinding buffs, pets, and
Agent runtime caches.
