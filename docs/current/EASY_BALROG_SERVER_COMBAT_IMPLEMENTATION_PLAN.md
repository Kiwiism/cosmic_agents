# Easy Balrog Server Combat Implementation Plan

Status: implemented, with automated characterization and regression coverage. A regular-client visual pass and full live 12-Agent expedition remain release validation gates.

Authority: [Server Boss Combat Runtime Architecture](SERVER_BOSS_COMBAT_RUNTIME_ARCHITECTURE.md).

## Implementation status

Implemented on 2026-08-31:

- encounter-wide native controller pinning, deterministic eligible-human handoff, a two-second capability response deadline, a 15-second controller lease, and irreversible server takeover;
- explicit `NATIVE_MOB_SIMULATION`, `RENDER_ONLY`, and `HEADLESS` client capability states;
- standalone body/claw registration plus the full seal, two-claw, body, clear, abort, helper, and suppressed-revive encounter graph;
- WZ-backed ordinary attacks, HP-gated counter/undead, summons, attack MP/cooldown reservation, delayed impacts, deadly attacks, dispel, reverse input, and distributed regions;
- map-wide regular-client action broadcasts and duplicate client `MOVE_LIFE`/`TAKE_DAMAGE` rejection during sticky server authority; and
- Easy Balrog event hosting through `EasyBalrogEncounterService`, with the reusable expedition lobby remaining independent.

Automated tests cover WZ values, selection gates, region retention/encoding, encounter transitions, observer exclusion, human handoff, sticky takeover, render-only fallback, damage integration, and non-Balrog controller safeguards. Native visual interpretation of the region mask and the complete timed 12-Agent live run are deliberately reported as pending until observed against a running regular v83 client.

## Outcome

Easy Balrog becomes a hybrid-authority boss encounter whose combat routine is independent of the Agent expedition system and event script. A capable human participant keeps native client simulation; the server runtime is a sticky fallback when no capable human remains.

The completed system supports:

- a full Easy Balrog encounter created by the expedition with one encounter-wide authority decision;
- the same full encounter summoned on an arbitrary map by a GM or test fixture;
- an individual Balrog body or claw using a capable native controller when available or sticky server fallback otherwise; and
- an already-spawned supported Balrog actor explicitly transferred to sticky server combat ownership for diagnostics.

The expedition remains responsible for registration, parties, entry, rewards, timeout, and exits. It observes encounter lifecycle events but never selects an attack or applies Balrog damage.

## Definition of done

Easy Balrog is complete only when:

- At encounter creation, an eligible native-capable human is selected when one exists; otherwise the encounter enters sticky server authority immediately.
- The selected native client remains controller while connected, alive, in the encounter, in the exact map instance, and capable of native mob simulation.
- If that controller becomes unavailable, control first moves deterministically to another eligible human participant without starting the server runtime.
- If no eligible human remains, the full encounter transfers once to server authority and never returns to client authority before death, despawn, clear, or abort.
- Agent, headless, render-only, and WASM clients are never selected as native boss controllers; an encounter containing only those clients enters sticky server authority.
- In native mode, existing MOVE_LIFE, MobSkill, and victim TAKE_DAMAGE flows remain authoritative and are broadcast normally.
- In sticky server mode, client MOVE_LIFE and duplicate TAKE_DAMAGE claims cannot select attacks, cast skills, move stationary Balrog actors, or duplicate server impacts.
- In sticky server mode, the server executes every claw and body attack, debuff, self-buff, summon, HP gate, MP cost, cooldown, telegraph, impact, and recovery.
- Distributed attacks retain the correct selected regions, including three of eleven body pillars.
- Every ready regular-client observer in the map instance receives the same action and region option.
- Regular native clients render the matching attack, skill, and warning animations.
- Seal, claws, fake body, active body, helpers, and clear transition follow one encounter-owned phase graph.
- Every active combat actor in the encounter has the same authority mode and, in native mode, the same pinned controller.
- Body-only and claw-only summons work without creating a complete encounter.
- A full encounter works without an expedition or EventInstanceManager.
- The canonical arena and an unrelated suitable map both work.
- Native and sticky-server damage paths preserve established human and Agent gameplay semantics without applying either hit twice.
- Clear, abort, map disposal, and shutdown leave no actor or delayed callback.
- A 12-Agent run enters, fights, clears, and exits naturally.
- A 30-member observation/load run remains bounded.
- Non-owned monsters and other bosses retain their behavior.

## Architectural decisions

1. Balrog is not an Agent capability. The Balrog module must not import Agent or expedition classes.
2. Native client simulation is preferred whenever an eligible human participant exists.
3. Native authority is pinned: normal aggro and damage ranking cannot replace the selected controller.
4. Controller loss first attempts deterministic human-to-human handoff. Server takeover occurs only when no eligible capable human remains.
5. Server takeover is one-way for the lifetime of the encounter or standalone actor.
6. The encounter coordinator always owns phase, lifecycle, cleanup, and role registration; simulation authority changes only combat decisions and damage production.
7. Full encounter creation is explicit. A body spawn creates one standalone actor; EASY_BALROG creates the complete phase graph.
8. Boss selection and generic execution remain separate. Server selection/execution runs only in sticky server mode.
9. A prepared server action is immutable. Facing, regions, targets, origin, wire option, and deadlines cannot be rerolled at impact.
10. Encounter transitions use registered actor roles, not map-wide monster counts.
11. In native mode clients use the established authoritative packet flow; in server mode clients render server decisions and never determine hits.
12. WZ is the numeric authority; Java owns phase, authority, and server-fallback selection policy.
13. Balrog does not depend on Alishar classes or assumptions. Existing generic code may be refactored when useful.
14. Runtime state is ephemeral and map-instance-scoped.

## Verified Easy Balrog behavior

### Actors

| Mob ID | Role | Combat |
|---:|---|---|
| 8830007 | Fake body, later active body | Body routine only when active |
| 8830008 | Released claw | Attacks and summons |
| 8830009 | Initially active claw | Attacks and reverse input |
| 8830013 | Release seal | None |
| 8830011 | Helper from 8830008 | None |
| 8830012 | Helper from 8830009 | None |
| 8830010 | Post-body WZ revive form | Suppressed after normal Easy clear |
| 6400007 | Baby Balrog add | Encounter-owned generic mob routine |
| 6400008 | Jr. Balrog add | Encounter-owned generic mob routine |
| 6400009 | Crimson Balrog add | Encounter-owned generic mob routine |

Canonical origin is (412, 258) in map 105100400. Arbitrary-map creation treats its supplied point as the encounter origin and resolves valid ground.

### Phase graph

~~~mermaid
stateDiagram-v2
    [*] --> Sealed: create encounter
    Sealed --> TwoClaws: release seal at T+60s
    TwoClaws --> OneClaw: either registered claw dies
    OneClaw --> Body: remaining registered claw dies
    Body --> Cleared: active body dies
    Sealed --> Aborted: host/map/channel ends
    TwoClaws --> Aborted: host/map/channel ends
    OneClaw --> Aborted: host/map/channel ends
    Body --> Aborted: host/map/channel ends
~~~

Creation produces fake body 8830007, active claw 8830009, and seal 8830013. At 60 seconds the coordinator releases the seal and creates claw 8830008. Only the two registered claw deaths activate the original body. Active body death clears the encounter. Standalone clear must not leave a delayed 8830010 spawn.

### Body actions

| Action | Verified WZ behavior | Required execution |
|---|---|---|
| Attack 1 | Magical tremor, MP 5, delay 3240 ms, start -4, areaCount 9, attackCount 9 | Resolve and broadcast all nine authored regions; tremble where supported |
| Attack 2 | Wide magical fire, MP 1, delay 930 ms | WZ range/effect timing; no invented pillar layout |
| Attack 3 | Deadly physical pillars, MP 10, delay 2040 ms, PAD 280, start -5, 11 regions, select 3 | Select three distinct regions before telegraph; apply characterized deadly semantics |
| Attack 4 | Wide magical dispel, MP 1, delay 1200 ms, disease 127/12 | Dispel only server-resolved hit targets through MobSkill |
| Skill 145/4 | Physical/magic counter, HP at or below 50%, MP 1, 7s, 60s interval, x 1000, y 400 | Self-targeted; no player envelope required |
| Skill 133/3 | Undead, HP at or below 25%, MP 1, 7s, 60s interval, 100% probability, wide range | Existing MobSkill area effect |

### Released claw 8830008

| Action | Verified WZ behavior | Required execution |
|---|---|---|
| Attack 1 | Physical warning regions, MP 10, delay 1500 ms, PAD 336, start -3, 7 regions, select 3 | Freeze three distinct regions and their option mask |
| Attack 2 | Directional magical sweep, MP 1, delay 2400 ms | Face primary target; mirrored WZ rectangle |
| Skill 200/162 | Summon | Existing MobSkill limit and interval |
| Skill 200/163 | Summon | Existing MobSkill limit and interval |

### Initially active claw 8830009

| Action | Verified WZ behavior | Required execution |
|---|---|---|
| Attack 1 | Physical pillars, MP 10, delay 1170 ms, PAD 336, start -6, 13 regions, select 4 | Freeze four distinct regions and their option mask |
| Attack 2 | Arena-wide magical reverse input, MP 1, delay 1200 ms, disease 132/3 | Apply only to server-resolved hit targets |
| Attack 3 | Directional magical sweep, MP 1, delay 1260 ms | Face primary target; mirrored WZ rectangle |

Map 105100400 environmental HP drain stays map behavior. It is not copied to arbitrary maps automatically.

## Target architecture

~~~mermaid
flowchart LR
    Host[Expedition / GM / script / test] --> API[Boss spawn API]
    API --> Encounter[Easy Balrog encounter runtime]
    Encounter --> Authority[Encounter simulation authority]
    Authority -->|capable human| Native[Pinned native controller]
    Authority -->|sticky fallback| Actors[Server body and claw runtimes]
    WZ[Cached WZ action catalog] --> Actors
    Actors --> Prepared[Immutable prepared action]
    Prepared --> Visual[Animation broadcaster]
    Prepared --> Impact[Delayed executor]
    Impact --> Damage[Shared mob damage pipeline]
    Impact --> Skills[Existing MobSkill effects]
    Encounter --> Events[Lifecycle bus]
    Host -. observes .-> Events
~~~

### Target packages

~~~text
server/life/autonomy/
  ServerBossCombatService
  ServerBossBehaviorRegistry
  ServerBossOwnership
  BossSimulationAuthorityPolicy
  BossClientSimulationCapability
  BossControllerLease
  ServerBossSpawnService
  BossActorBehavior
  BossActorRuntime
  BossActorContext
  BossActionDefinition
  PreparedBossAction
  BossActionGeometry
  BossActionCatalog
  BossActionExecutor
  BossAnimationBroadcaster
  BossEncounterBehavior
  BossEncounterHandle
  BossEncounterLifecycleBus

server/life/autonomy/balrog/
  EasyBalrogEncounterFactory
  EasyBalrogEncounterBehavior
  EasyBalrogEncounterState
  EasyBalrogActorRole
  EasyBalrogBodyBehavior
  EasyBalrogReleasedClawBehavior
  EasyBalrogInitialClawBehavior
  EasyBalrogActionPolicy
  EasyBalrogAreaLayoutPolicy
  EasyBalrogDefinition
~~~

Existing autonomy classes may evolve into these responsibilities. Do not create a second scheduler merely to avoid refactoring prototype interfaces.

## Spawn and authority contracts

~~~java
Monster spawnBossMob(int mobId, MapleMap map, Point position);

BossActorHandle forceServerTakeover(Monster monster);

BossEncounterHandle spawnEncounter(
        BossEncounterId.EASY_BALROG,
        MapleMap map,
        Point origin,
        BossEncounterOptions options);
~~~

Semantics:

- A real standalone 8830007, 8830008, or 8830009 selects a capable native controller from the exact map instance when available; otherwise it starts in sticky server mode.
- `forceServerTakeover` is immediate, idempotent, diagnostic/administrative, and permanently sticky for that actor lifetime.
- EASY_BALROG creates the complete encounter and makes one authority decision for all components.
- Normal map spawn paths register supported standalone actors after they become visible, then apply the authority policy.
- A fake encounter body is registered but suspended until body phase.
- Encounter-spawned components and adds inherit encounter authority and cleanup.
- Ownership identity includes channel, map-instance identity, object ID, and object generation.

## Simulation authority and controller lease

Authority is encounter-scoped for EASY_BALROG and actor-scoped for a standalone body or claw:

~~~text
NATIVE_CLIENT(controllerCharacterId)
    -> NATIVE_CLIENT(replacementCharacterId)
    -> SERVER_STICKY
    -> TERMINATED
~~~

There is no transition out of `SERVER_STICKY` except termination.

A native controller is eligible only when it is:

- a registered human boss participant for a hosted encounter, or an eligible human in the exact map instance for a standalone actor;
- connected, logged in, alive, not changing maps, and present in the exact encounter map instance;
- neither an Agent nor a spectator; and
- explicitly classified as capable of native mob simulation.

Client capability is immutable for the session and recorded explicitly, not inferred only from `BotClient`:

~~~java
enum BossClientSimulationCapability {
    NATIVE_MOB_SIMULATION,
    RENDER_ONLY,
    HEADLESS
}
~~~

Regular supported v83 clients declare `NATIVE_MOB_SIMULATION`; WASM declares `RENDER_ONLY`; Agent/BotClient sessions declare `HEADLESS`. Unknown clients are ineligible until classified safely.

The eligible participant roster is ordered deterministically by expedition registration order, with character ID as the stable tie-breaker. The selected controller is pinned across all current encounter actors. Valid MOVE_LIFE activity renews a conservative controller lease, but DPS, proximity, and ordinary aggro changes do not replace it.

Death, disconnect, logout, event departure, map-instance departure, capability loss, or lease expiry triggers one serialized reevaluation. If another eligible human exists, every current combat actor is reassigned to that human without starting server runtimes. If none exists, the coordinator atomically enters `SERVER_STICKY`, revokes client control for all current actors, invalidates old leases and in-flight client claims, attaches server runtimes, and ensures every future component/add is server-owned. A later join, revive, or reconnect cannot reverse takeover.

## Actor runtime

The actor runtime exists and schedules decisions only while authority is `SERVER_STICKY`. In native mode it is absent or suspended and cannot race the controller.

~~~mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Telegraphing: prepare and reserve
    Telegraphing --> Impact: impact deadline
    Impact --> Recovery: effect applied once
    Recovery --> Idle: recovery deadline
    Idle --> Suspended: phase disables actor
    Suspended --> Idle: phase enables actor
    Idle --> Detached: death/removal/abort
    Telegraphing --> Detached: death/removal/abort
    Recovery --> Detached: death/removal/abort
~~~

Actor state contains:

- ownership generation;
- map and monster identity;
- encounter and actor role;
- current state and prepared action;
- action lock and recovery deadline;
- per-action cooldown deadlines;
- injected deterministic random source; and
- next decision deadline.

A prepared action freezes:

- action definition and WZ source;
- actor and encounter-phase generations;
- origin and facing;
- primary target when directional;
- selected regions and target cap;
- encoded action option or region mask; and
- telegraph, impact, and recovery timestamps.

Impact never reruns selection.

## Action model and WZ loading

Action kinds:

~~~java
enum BossActionKind {
    ORDINARY_ATTACK,
    ON_HIT_DISEASE_ATTACK,
    AREA_MOB_SKILL,
    SELF_MOB_SKILL,
    SUMMON_MOB_SKILL
}
~~~

Geometry kinds:

~~~java
sealed interface BossActionGeometry {
    record DirectionalBox(Point lt, Point rb) implements BossActionGeometry {}
    record ArenaBox(Point lt, Point rb) implements BossActionGeometry {}
    record DistributedRegions(
            Point cellLt,
            Point cellRb,
            int start,
            int areaCount,
            int selectedCount) implements BossActionGeometry {}
}
~~~

The catalog loads and caches through DataProviderFactory and DataTool:

- attack index and animation frames;
- range lt/rb, start, areaCount, and attackCount;
- conMP, attackAfter, effectAfter, and frame duration;
- magic, attack-specific PAD/MAD, deadlyAttack, and MP burn;
- attack-linked disease and level;
- mob skill ID, level, animation action, and effect delay; and
- MobSkill HP gate, MP cost, probability, duration, interval, range, and summon limit.

Resolve linked mob data. Missing optional values get explicit defaults; missing required Balrog geometry fails fixture tests.

`BossActionGeometry` converts WZ region metadata to rectangles relative to the actor origin. The implementation emits the prepared selected regions as a 16-bit `pOption` mask and verifies the exact little-endian wire bytes in `PacketCreatorMoveMonsterTest`. The remaining release gate is confirming the regular v83 client interprets those bits as the matching Balrog warning regions; if a native capture differs, only the encoder should change while the frozen server-side region selection remains authoritative.

## Selection policy

This policy runs only for actors under `SERVER_STICKY` authority.

1. Reject selection for fake, suspended, dead, removed, neutralized, phase-inactive, or action-locked actors.
2. Build candidates satisfying HP, MP, cooldown, summon limit, target, and geometry requirements.
3. Self-buffs require no target; summons require active participants but no envelope.
4. Directional attacks choose the closest eligible primary target and freeze facing.
5. Arena and distributed attacks evaluate current positions at impact against prepared regions.
6. Avoid immediate ordinary-action repeats when alternatives exist.
7. Preserve WZ skill probability, HP gate, and interval.
8. Start with documented equal ordinary-action weights; tune only from live evidence.
9. Use a short deterministic pause after recovery.
10. Body, claws, seal, and helpers are stationary. Mobile adds may use server mob physics.

## Execution sequence

This sequence runs only for actors under `SERVER_STICKY` authority.

1. Revalidate actor, map, phase, HP, MP, and cooldown.
2. Atomically reserve MP, cooldown, and action lock.
3. Prepare facing, regions, option, targets, and deadlines.
4. Store the immutable prepared action.
5. Broadcast animation and the exact region option.
6. Schedule impact on the channel/map scheduler.
7. Revalidate actor and encounter generations at impact.
8. Re-read dynamic target state and position.
9. Apply shared damage and on-hit disease, or call MobSkill.
10. Enter recovery and emit diagnostics.
11. Select again after recovery.

No actor owns a thread. One channel service manages deadlines.

## Shared damage authority

Extract packet-independent gameplay mutation from TakeDamageHandler. Do not retain an Agent-only copy as the generic boss damage implementation. Native mode continues to accept the established victim-client TAKE_DAMAGE path; sticky server mode invokes the shared core directly and rejects a duplicate report for the same boss action.

Suggested contract:

~~~java
MobDamageResult applyMobDamage(MobDamageRequest request);
~~~

The request includes attacker, target, attack index, physical/magical kind, attack-specific PAD/MAD, deadly mode, MP burn, disease, and contact/ordinary classification.

Characterization covers:

- accuracy and miss;
- physical/magical defense;
- Dark Sight where applicable;
- Magic Guard and Meso Guard;
- Achilles, High Defense, and Combo Barrier;
- contact-only Power Guard behavior;
- deadly attacks and Dojo configuration;
- MP burn and on-hit disease;
- Battleship HP;
- HP/MP mutation, death, banish, and packets; and
- Agent autopot after authoritative loss.

Body attack 3 must be characterized before implementation. The target is successful deadly damage reducing HP and MP to one, subject to verified v83 and existing Dojo semantics. Do not apply an ordinary PAD 280 roll by assumption.

## Client authority boundary

In `NATIVE_CLIENT` mode:

- the pinned eligible human is controller for every current combat actor;
- established MOVE_LIFE, MOVE_MONSTER_RESPONSE, MobSkill, victim TAKE_DAMAGE, validation, mutation, and observer broadcast paths remain active;
- controller assignment cannot change through DPS, proximity, or ordinary aggro while its encounter lease is valid;
- non-controller proposals cannot steal authority; and
- server actor runtimes are absent or suspended.

In `SERVER_STICKY` mode:

- MoveLifeHandler ignores proposed attacks, skills, movement, MP/cooldowns, and next-skill decisions;
- it sends any acknowledgement needed to prevent resend stalls, without accepting authority;
- TakeDamageHandler rejects client ordinary-attack reports already resolved by the server;
- contact damage is explicitly server-owned or legacy-owned, never both; and
- client movement cannot move stationary Balrog components.

The handoff/takeover transaction is serialized at the encounter level. It finishes or invalidates the current native action boundary before enabling the replacement authority, so one attack cannot be produced by both modes. Non-owned monsters remain unchanged.

Ownership lookup is channel-local and constant-time; packet handlers must not scan static service instances.

## Animation and observing clients

### Standard packet

Native mode uses the established client-generated MOVE_MONSTER path. Sticky server mode generates MOVE_MONSTER with:

~~~text
mobOid
reserved byte
skillPossible
rawActivity
skillId
skillLevel
pOption
startPos
movement fragments
~~~

Encoding must preserve:

- ordinary attack base activity 24 plus attack index/facing;
- mob skill base activity 42 plus WZ animation action/facing;
- skill ID/level only for skill actions;
- verified Balrog pOption region/animation data; and
- stationary position, foothold, and facing.

Packet tests decode bytes back into fields. “A packet was broadcast” is not an adequate test.

### Broadcast scope

Boss action/warning packets use a dedicated map-wide broadcast to every ready network recipient in the same map instance. Do not use the range-limited broadcastMessage(packet, origin) overload.

The broadcaster:

- includes controller and spectators;
- excludes recipients not ready in the field;
- sends identical bytes and region option to every observer;
- records recipient count and map-instance identity; and
- never crosses another instance with the same map ID.

### Native-client gate

For every attack and skill:

- in native mode, the controller's accepted action is rebroadcast to spectators through the established path;
- in sticky server mode, all ready clients receive the server-prepared action;
- controller and spectator receive identical accepted action fields for their authority mode;
- both start the correct attack or skill animation;
- warning regions match the prepared regions;
- damage occurs after the visible warning and once;
- Counter and Undead visuals appear and expire;
- Dispel and Reverse Input affect correct characters; and
- a far-side spectator still receives the action.

Packet/timestamp evidence is required in addition to screenshots or video.

### Optional WASM troubleshooting support

Current WASM discards action/skill fields in MobMovedHandler and its Mob renderer loads normal movement stances, not attack/skill/warning animations. This does not block Easy Balrog completion: WASM is a troubleshooting and testing surface, while regular native-client delivery is the release requirement.

Optional WASM work may:

1. Parse skillPossible, rawActivity, skill ID/level, and pOption into a render-only action.
2. Add MapMobs and Mob action dispatch separate from movement authority.
3. Load linked attack1..attack4, required skill animations, effect, and areaWarning nodes.
4. Decode raw activity and facing.
5. Decode the verified region option.
6. Play attack effects, tremble, and skill effects where supported.
7. Return to stand without sending attack decisions or damage reports.
8. Add decoder, animation-state, and no-outbound-authority tests.

WASM must declare `RENDER_ONLY` and remain non-authoritative even if these optional diagnostics are implemented.

## Encounter coordination

### Creation

1. Resolve encounter origin.
2. Snapshot registered participants and select `NATIVE_CLIENT` or `SERVER_STICKY` authority.
3. Spawn fake body 8830007 as suspended BODY.
4. Spawn 8830009 as active INITIAL_CLAW; pin it to the encounter controller or attach its server runtime according to authority.
5. Spawn 8830013 as RELEASE_SEAL without combat behavior.
6. Schedule release at 60 seconds using encounter generation.
7. Publish ENCOUNTER_STARTED and SEALED.

### Seal release

1. Revalidate encounter and exact seal.
2. Remove the seal through encounter-managed revive handling.
3. Spawn exactly one 8830008 as RELEASED_CLAW.
4. Apply the current encounter authority before it can act: pin the native controller or attach its server runtime.
5. Enter TWO_CLAWS and publish the event.

### Claw deaths

- Mark only the registered actor defeated.
- Detach its server runtime and invalidate pending impacts when in sticky server mode.
- Optionally create its WZ helper under the matching role.
- Enter ONE_CLAW after the first distinct death.
- Activate body only after both registered claws die.
- Make duplicate death callbacks idempotent.

### Body activation

1. Revalidate the original fake body.
2. Use makeMonsterReal for the visual transition.
3. Atomically mark it damageable and actor-enabled.
4. Apply the current encounter authority before its first action.
5. Enter BODY and publish the event.

### Clear

1. Detach body and invalidate prepared impacts.
2. Mark CLEARED once.
3. Cancel release and revive work.
4. Suppress/remove post-body 8830010.
5. Publish ENCOUNTER_CLEARED.
6. Leave rewards, victory notice, and exits to the host.
7. Terminate the authority lease/runtime and remove encounter registry state.

### Managed revives

Add instance-level revive interception for encounter actors. It delegates seal/claw/body follow-up to the coordinator, prevents duplicate generic revive scheduling, preserves unrelated WZ revives, and cancels post-clear body revival on persistent arbitrary maps.

## Host integration

Lifecycle events:

~~~text
ENCOUNTER_STARTED
ACTOR_SPAWNED
PHASE_CHANGED
ACTOR_DEFEATED
ENCOUNTER_CLEARED
ENCOUNTER_FAILED
ENCOUNTER_ABORTED
~~~

The expedition only:

1. navigates/registers up to 30 members;
2. partitions six-member parties;
3. enters after the countdown;
4. supplies the registered participant roster and calls spawnEncounter(EASY_BALROG);
5. lets Agents use normal combat/support/positioning;
6. observes lifecycle; and
7. rewards and exits naturally.

BalrogBattle_Easy.js becomes a thin host adapter. Remove duplicate component spawning, seal timing, boss-count phase logic, and revives after Java owns them. Keep entry, timer, registration, clear effects, rewards, and exits.

AgentEasyBalrogScenario retains only Agent targeting, support, recovery, positioning, and reporting.

## Arbitrary maps

- Resolve origin and summons to valid footholds.
- Build all geometry relative to encounter origin.
- Clip effective hit regions to map bounds.
- Preserve selected region count where valid regions exist.
- Emit geometryDegraded when clipping changes coverage.
- Reject creation clearly if no usable combat geometry exists.
- Do not copy canonical environmental drain.

## Cleanup, diagnostics, and commands

Cleanup occurs on death, removal, clear/fail/abort, map reset/disposal, event disposal, channel shutdown, and explicit GM stop. Every callback carries actor and encounter generations and becomes a no-op after invalidation.

Structured diagnostics include encounter/map instance, phase generation, actor role/object generation, action/raw activity/skill/option, regions, targets, deadlines, recipient count, result, and detach reason. Do not log every tick.

Recommended commands:

~~~text
!bosscombat status
!bosscombat takeover <mobOid>
!spawnencounter easy-balrog
!spawnencounter easy-balrog here
!stopencounter <encounterId>
~~~

Status is read-only.

## Implementation phases

### Phase 0 — Evidence and protocol freeze

Deliver:

- WZ fixtures for every action, skill, revive, delay, range, HP gate, and summon limit.
- Native packet evidence for raw activity, facing, and pOption region selection.
- Deadly HP/MP characterization.
- Native-client baseline animation evidence; optional WASM packet traces may assist troubleshooting.
- Current native controller, handoff, MOVE_LIFE lease cadence, victim TAKE_DAMAGE, and observer rebroadcast characterization.

Gate: distributed geometry does not proceed until layout and option encoding are proven.

### Phase 1 — Shared damage pipeline

Deliver:

- packet-independent mob damage core;
- physical, magical, deadly, MP burn, and disease requests;
- legacy handler and server attack integration; and
- removal of Agent-only dependencies from generic damage.

Gate: characterization passes for humans and Agents without silent formula changes.

### Phase 2 — Simulation authority and sticky takeover

Deliver:

- explicit native/render-only/headless client capability metadata;
- encounter participant eligibility and deterministic selection;
- encounter-wide pinned controller assignment;
- controller lease, loss detection, and human-to-human handoff;
- atomic one-way transition to `SERVER_STICKY`;
- constant-time channel authority lookup;
- spawn and force-takeover hooks;
- generation-safe detach;
- mode-aware packet acceptance/suppression;
- stationary Balrog movement policy; and
- runtime/Balrog feature flags.

Gate: a capable human remains the pinned native controller; controller loss hands to another eligible human; loss of the last eligible human or a WASM/Agent-only roster starts sticky server authority; no revive, reconnect, or join returns it to native mode.

### Phase 3 — Complete action and geometry model

Deliver:

- immutable WZ definitions;
- directional, arena, and distributed geometry;
- damage overrides and on-hit disease;
- immutable prepared actions; and
- deterministic region selection.

Gate: tests prove stable 3/11, 3/7, 4/13, and 9/9 region masks.

### Phase 4 — Executor and animation broadcaster

Deliver:

- actor state machine and channel timing;
- atomic reservation;
- map-wide boss action broadcast;
- verified pOption packet support;
- delayed impact and revalidation; and
- diagnostics.

Gate: under forced sticky server authority, decoded packets match, two observers receive identical bytes, and one action impacts once.

### Phase 5 — Standalone actor behaviors

Deliver body and both claw policies, including self counter, Undead, summons, dispel, reverse input, deadly, and ordinary attacks.

Gate: each actor runs alone under forced sticky server authority on an unrelated map with fake clock/seed and cleans on removal.

### Phase 6 — Full encounter

Deliver factory, roles, phase graph, fake body, release timer, managed revives, clear suppression, lifecycle, and abort.

Gate: full encounter clears without EventInstanceManager in canonical and unrelated maps, and later phase actors inherit the encounter's unchanged authority mode.

### Phase 7 — Expedition adapter

Deliver thin event script, lifecycle observation, preserved 1–30 lobby/party/countdown/return, and removal of duplicate boss authority from Agent scenario.

Gate: standalone and expedition authority selection follows the same policy; a human-led expedition stays native, an Agent-only expedition uses sticky server fallback, and timeout/stop leaves no tasks.

### Phase 8 — Regular-client animation completion

Deliver regular native-client packet and visual evidence. WASM decoder/render-only animation support is optional diagnostic work and is not part of this phase's release gate.

Gate: native controller/spectators see the accepted client action through the normal path, while sticky-server observers see the same prepared server action and warnings before the same server impact.

### Phase 9 — End-to-end rollout

Deliver deterministic suite, human/12-Agent/30-member runs, timing/action/damage/cleanup report, feature flags, and rollback instructions.

Gate: every definition-of-done item passes.

## Test matrix

Catalog:

- action counts and indices;
- all WZ numeric data;
- links and mandatory geometry failures.

Geometry:

- directional mirroring;
- unique in-range distributed selections;
- stable prepared masks;
- canonical golden regions;
- safe arbitrary-map clipping.

Actors:

- fake body never attacks;
- Counter at 50% without a target envelope;
- Undead at 25%;
- MP/cooldown/summon limits;
- anti-repeat and deterministic seed.

Executor:

- telegraph before impact;
- impact exactly once;
- leave/death/phase invalidation;
- on-hit disease only on hit;
- deadly parity;
- identical observer packets;
- ignored client claims.

Authority:

- explicit capability classification, including WASM render-only and Agent headless;
- participant eligibility and exact map-instance checks;
- deterministic initial controller and replacement selection;
- controller pin survives DPS, proximity, and ordinary aggro changes;
- disconnect, death, departure, and lease expiry hand to another eligible human;
- last eligible human loss enters sticky server mode exactly once;
- revive, reconnect, or later join never reverses sticky server takeover;
- every current and future encounter actor uses one authority mode; and
- native and server paths cannot produce the same action twice.

Encounter:

- exact roles;
- one 60-second release;
- one claw cannot activate body;
- two distinct claws activate once;
- unrelated mobs/duplicate deaths do not advance;
- clear once and no 8830010;
- abort in every phase cleans all work.

Integration:

- standalone actor and full encounter without event;
- canonical arena;
- expedition enter/clear/return;
- human-only and mixed-participant native-controller runs;
- native controller disconnect/death handoff and last-human takeover;
- 12 Agents using immediate sticky server fallback;
- WASM-only observation using immediate sticky server fallback;
- 30 participants/five parties;
- native controller/spectator parity;
- optional WASM packet/observer diagnostics, when useful;
- non-owned mob and Alishar regression.

## Live validation

1. Enable only explicit GM/test spawning.
2. Run a capable-human encounter and verify it remains native while the controller is eligible.
3. Add a second eligible human, disconnect/kill the controller, and verify direct human-to-human handoff.
4. Remove the final eligible human, verify one sticky server takeover, then reconnect/revive and verify no reversal.
5. Force each server action through a debug-only command and verify fields, warning mask, delay, and impact.
6. Add a far-side spectator and compare bytes in both authority modes.
7. Optionally collect WASM packet traces for troubleshooting; verify WASM is never selected as controller.
8. Test body-only and full encounter on an unrelated spacious map.
9. Run the natural 12-Agent expedition and verify immediate server fallback.
10. Run 30 participants for authority, broadcast, and scheduler load.
11. Confirm bosscombat status has no orphan after every clear/abort.

Record release/body/clear timings, action distribution, telegraph deltas, prepared versus rendered masks, damage/disease/death/potions, recipient counts, pending tasks, and return status.

## Feature flags and rollback

~~~text
SERVER_BOSS_COMBAT_ENABLED
SERVER_BOSS_COMBAT_EASY_BALROG_ENABLED
SERVER_BOSS_COMBAT_ALLOW_STICKY_TAKEOVER
SERVER_BOSS_NATIVE_CONTROLLER_LEASE_MS
SERVER_BOSS_COMBAT_DIAGNOSTICS
~~~

Roll out through explicit forced-server GM spawn, native-capable test encounter, canonical event, Agent expedition, then default Balrog authority policy.

Rollback disables sticky takeover first and leaves native behavior intact. Never allow mixed mode where client and server both resolve an attack.

## Expected impact areas

Server:

- src/main/java/server/life/autonomy/
- src/main/java/server/life/autonomy/balrog/
- src/main/java/server/combat/
- MoveLifeHandler
- TakeDamageHandler
- ChannelServices
- MapleMap
- Monster
- PacketCreator
- BalrogBattle_Easy.js
- Agent Balrog expedition adapter

Optional WASM diagnostics:

- monster movement handler;
- map-mob action dispatch;
- mob action animation state;
- linked mob attack/skill/effect/warning loading.

## Recommended commits

1. WZ/protocol fixtures.
2. Shared damage extraction.
3. Simulation authority, controller lease, and sticky takeover.
4. Action/geometry model.
5. Executor/animation broadcaster.
6. Standalone Balrog actors.
7. Encounter coordinator.
8. Expedition/event adapter.
9. Regular-client animation evidence; optional WASM diagnostics may be committed separately.
10. Live diagnostics and tuning.

Every commit compiles independently and excludes unrelated work.

## Handoff instruction

> Read CLAUDE.md, .claude/skills/wz-data/SKILL.md, docs/current/SERVER_BOSS_COMBAT_RUNTIME_ARCHITECTURE.md, and docs/current/EASY_BALROG_SERVER_COMBAT_IMPLEMENTATION_PLAN.md completely. Implement only the assigned phase and its gate. Balrog combat must prefer one pinned native-capable human expedition controller, hand directly to another eligible human on loss, and enter irreversible server authority only when none remains or all clients are incapable (including WASM/Agents). The encounter coordinator remains server-owned in every mode. Preserve native packet/damage behavior before takeover, and broadcast verified server action packets and warning-region options to every ready observer after takeover. Do not proceed past an unverified WZ or packet assumption.
