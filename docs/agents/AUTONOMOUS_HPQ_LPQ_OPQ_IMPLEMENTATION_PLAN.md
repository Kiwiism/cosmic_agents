# Autonomous HPQ, LPQ, and OPQ Implementation Plan

## Status

Planning only. This document does not authorize or include runtime changes.

Recommended delivery order:

1. Extract the remaining Kerning-specific runtime seams into a reusable party-quest platform.
2. Implement and validate Henesys PQ (Moon Bunny's Rice Cake / HPQ).
3. Implement and validate Ludibrium PQ (LPQ).
4. Repair, implement, and validate Orbis PQ (OPQ).

HPQ should precede LPQ and OPQ because it exercises the shared reactor-hit,
item-trigger-reactor, leader-item, friendly-mob defense, and reward lifecycle on
a smaller map and shorter event. LPQ then proves required roster capabilities,
split rooms, portal mazes, and a five-participant formation. OPQ remains last
because it combines all of those concerns with parallel rooms, randomized
scripted portals, multi-step reactors, and a friendly-object/boss finale.

## Goals

- Let managed Agents form autonomous parties and complete the local HPQ, LPQ,
  and OPQ scripts through ordinary server-authoritative gameplay.
- Support Agent-led and mixed human/Agent parties without moving, clicking,
  looting for, or speaking for human participants.
- Preserve the existing KPQ implementation and its exact background simulation.
- Reuse generic lobby, engagement, navigation, combat, loot, NPC, portal,
  reactor, activity, and recovery capabilities.
- Keep each PQ's stage rules isolated in its own package and coordinator.
- Provide deterministic tests and GM observation/checkpoint harnesses before
  enabling autonomous population admission.

## Non-goals

- Do not create outcome-only or abstract PQ simulation.
- Do not expose hidden puzzle answers to human-led parties.
- Do not bypass local event, NPC, portal, reactor, drop, reward, or timer scripts.
- Do not provision production Agents directly to an ineligible level, job,
  skill, or equipment state. Test fixtures may create controlled characters.
- Do not combine HPQ, KPQ, LPQ, and OPQ stage logic into one universal coordinator.

## Current reusable foundation

The following components are already broadly reusable:

- `AgentPartyQuestEngagement` and `AgentPartyQuestEngagementRegistry`
- `server.agents.capabilities.partyquest.lobby`
- exclusive `PARTY_QUEST` activity ownership
- transactional KPQ lobby-to-session handoff pattern
- exact background movement, combat, physics, NPC, portal, loot, and rewards
- coordinator leases, watchdogs, bounded recovery, and structured diagnostics
- mixed human/Agent roster semantics
- primitive navigation, grind, NPC, portal, item-drop, and gateway-level reactor
  operations

The stage coordinators should remain PQ-specific, but the runtime dispatch and
lifecycle boundaries must become quest-neutral before adding another production
PQ.

## Phase 0: Freeze and verify the local content contract

Before Agent work begins for any PQ:

1. Run each PQ manually with humans on the current scripts.
2. Record the authoritative party size, level range, event time, maps, NPCs,
   portal scripts, reactor IDs/names, exclusive items, reward flow, and failure
   behavior.
3. Fix ordinary-content defects independently of Agent automation.
4. Add script-level smoke checks for every event callback used by the PQ.
5. Treat the local repository behavior as authoritative when it differs from
   other MapleStory versions or online guides.

Known content findings to resolve in this phase:

- `scripts/reactor/2006001.js` uses `eim` without defining it when Minerva is
  spawned at the end of OPQ.
- `scripts/event/HenesysPQ.js` calls an unqualified `broadcastMessage(...)` in
  `friendlyDamaged`; the intended map receiver is not specified.
- HPQ's full-moon/flower scripts contain overlapping Moon Bunny spawn paths and
  `scripts/reactor/9101000.js` calls `showAllMonsters()`, for which no matching
  Java map method was found. Confirm actual live behavior before designing the
  Agent state machine around it.

Exit gate:

- A human party can complete the unchanged gameplay loop reliably, or all
  ordinary-content defects are separately fixed and smoke-tested.

## Phase 1: Generalize the party-quest platform

### 1.1 Party-quest descriptor and registry

Introduce a quest-neutral descriptor/registration boundary with:

- stable quest key (`hpq`, `kpq`, `lpq`, `opq`)
- display name and event-manager name
- recruit, entry, clear, bonus, exit, and recovery maps
- entry, stage, reward, and exit NPC IDs
- minimum/maximum party size and level
- event map membership predicate
- lobby profile and roster requirements
- lobby/capacity policy
- admission, session lookup, tick, graceful stop, force stop, cleanup, and
  activity projection adapters

The registry must reject duplicate quest keys and overlapping active ownership
for the same character.

### 1.2 Remove KPQ hard-coding from shared seams

Convert these callers to dispatch through the registered active PQ:

- `AgentPartyQuestHooks`
- `AgentPartyQuestLifecycleRuntime`
- `PartyQuestActivitySessionAdapter`
- `AgentStandardWorldActivityBindingResolver`
- `AgentPopulationRuntime`
- `CosmicAgentPopulationBackend`
- `AgentRuntimeCleanupService`
- party-quest portions of loot eligibility and dialogue suppression

Recovery must use the active engagement's recovery map instead of always
returning Agents to Kerning City.

The typed party-quest activity request must carry a quest key. Scenario ID,
party size, and run count remain separate request fields.

### 1.3 Session-neutral activity projection

Define a small session projection interface containing only shared lifecycle
facts:

- session ID and quest key
- member IDs and Agent member IDs
- active/paused/terminal state
- start and last-progress timestamps
- failure and diagnostics
- pause/resume/stop operations
- activity outcome attributes

Do not make PQ sessions inherit stage behavior from one another.

### 1.4 Shared admission utilities

Extract only proven duplication from KPQ admission:

- authoritative online party snapshot validation
- same-world/channel/map checks
- Agent activity acquisition
- complete roster publication and rollback
- lobby reservation/handoff restoration
- inventory-capacity validation hooks
- owned-party cleanup

Each PQ supplies its own eligibility and inventory requirements.

### 1.5 Shared capacity and population director

Support multiple PQ population sources without starting one scheduler per PQ.
The director should:

- read per-PQ enablement, sweep, cooldown, party size, global cap, per-channel
  cap, and human-reserved lobby count
- consider only enabled managed Agents
- validate level, map access, party state, required skills/jobs, supplies, and
  cooldown
- choose at most one primary activity for an Agent
- reserve capacity before releasing current activities
- use the same production admission boundary as mixed/manual runs

Initial rollout limits for every new PQ should be zero background parties until
the corresponding live gate passes.

### 1.6 Shared item-trigger reactor operation

Add a server-authoritative operation that:

1. selects an eligible inventory slot and quantity,
2. navigates to a safe grounded point inside the reactor's authored trigger box,
3. drops the item through the normal inventory gateway,
4. verifies item consumption and reactor/event-state transition,
5. bounds retries without duplicating or vacuuming items.

This is needed by HPQ seeds and OPQ clouds, LP records, statue pieces, seeds,
Root of Life, and Minerva restoration. It should not be embedded independently
inside each coordinator.

### 1.7 Shared reactor-hit scope

The existing reusable reactor primitive defaults to Amherst-only scope. Add an
explicit party-quest-owned scope keyed by the active registered session and
event instance. A character must never hit a PQ reactor merely because it is on
a matching map ID outside its owned event.

### Phase 1 verification

- All existing KPQ focused tests remain green without behavior changes.
- Registry and dispatch tests cover no session, one session, duplicate
  registration, cleanup, stop, recovery destination, and activity outcome.
- Admission rollback tests prove that no partial lobby/session index survives.
- Loot and reactor tests prove that PQ exclusives remain blocked outside their
  matching session.

## Phase 2: Henesys PQ / Moon Bunny's Rice Cake

### Local contract

- Event manager: `HenesysPQ`
- Recruit map: `100000200`
- Event map: `910010000`
- Clear map: `910010100`
- Bonus map: `910010200`
- Exit/recovery maps: `910010300` / `100000200`
- Party size: 3-6
- Level range: 10-255
- Event time: 10 minutes
- Seeds: `4001095` through `4001100`
- Rice cake: `4001101`
- Moon Bunny: `9300061`

### HPQ package

Create `server.agents.capabilities.partyquest.hpq` with:

- `AgentHpqDefinition`
- `AgentHpqLobbyProfile`
- `AgentHpqSession` and member state
- `AgentHpqSessionRegistry`
- `AgentHpqAdmissionService`
- `AgentHpqCoordinator`
- `AgentHpqTerminationService`
- `AgentHpqPopulationPolicy`
- `AgentHpqWatchdogRuntime`
- `AgentHpqDialogue`
- `AgentHpqCheckpointService`
- `AgentHpqTestService`

### HPQ phase model

1. `WAITING_FOR_EVENT_ENTRY`
2. `HARVESTING_SEEDS`
3. `PLANTING_SEEDS`
4. `PROTECTING_MOON_BUNNY`
5. `DELIVERING_RICE_CAKES`
6. `REWARD_MAP`
7. `OPTIONAL_BONUS`
8. `EXITING`
9. terminal completed/failed

### HPQ stage behavior

#### Seed harvesting

- Use ordinary reactor hits on the `nut` flower reactors.
- Reserve reactors so Agents do not converge on the same flower.
- Pick up only required seed colors while preserving ordinary ownership and
  pickup timing.
- Maintain one shared inventory accounting view across members.
- Stop hitting flowers once all six colors are available or already planted.

#### Seed planting

- Map the local WZ contract exactly:
  - moonflower1 / reactor `9108000` <- green seed `4001095`
  - moonflower2 / reactor `9108001` <- purple seed `4001096`
  - moonflower3 / reactor `9108002` <- pink seed `4001097`
  - moonflower4 / reactor `9108003` <- brown seed `4001098`
  - moonflower5 / reactor `9108004` <- yellow seed `4001099`
  - moonflower6 / reactor `9108005` <- blue seed `4001100`
- Assign one planter at a time per platform.
- Navigate into the authored 39-by-58-pixel item trigger area and use the shared
  item-trigger operation.
- Verify each reactor transition rather than trusting that the drop landed.

#### Moon Bunny defense

- Transition only after the live Moon Bunny appears and hostile monsters are
  targetable.
- Treat the Bunny as friendly and never select it as a combat target.
- Assign at least two defenders to threat zones around the Bunny; remaining
  members may patrol spawn lanes.
- Reuse ordinary combat, target leases, movement, potions, and skills.
- Track live Bunny presence/HP and fail through the normal event result if it
  dies.
- Do not fake cake production or manipulate `bunnyCake`.

#### Rice-cake collection and submission

- Replace the current unconditional Agent loot ban for item `4001101` with a
  session-aware HPQ policy.
- Prefer the human leader for normal pickup/submission in human-led parties.
- In Agent-led parties, assign exactly one cake collector, normally the leader.
- Allow collection only during `PROTECTING_MOON_BUNNY`, only up to ten cakes,
  and only for a registered member in the correct event instance.
- All defenders continue fighting while the collector retrieves safe cakes.
- The leader navigates to Growlie and submits through the normal NPC dialogue.

#### Reward and optional bonus

- Use the normal reward NPC and inventory-space checks.
- Default background behavior should exit after the normal reward.
- Make the Pig Town bonus an explicit policy option after the base HPQ is stable.

### HPQ human seams

- Humans may harvest, plant, defend, collect, submit, or choose the bonus.
- The coordinator observes live reactor, inventory, Bunny, cake, and event state.
- Agents fill missing work without moving or clicking for humans.
- Human leader submission remains authoritative.

### HPQ verification

- Unit tests: seed mapping, assignment, cake eligibility, collector cap,
  friendly-target exclusion, phase transitions, and recovery.
- Integration tests: reactor hit -> seed drop -> pickup; seed inventory drop ->
  reactor state; timed cake -> authorized pickup.
- GM checkpoints: seed harvest, partially planted, Bunny active, nine cakes,
  clear map, bonus map.
- Live gates: Agent-only 3- and 6-member runs; human-led mixed run; Bunny death;
  inventory-full recovery; timeout; disconnect; repeated run.

## Phase 3: Ludibrium PQ

### Local contract

- Event manager: `LudiPQ`
- Recruit map: `221024500`
- Entry through boss maps: `922010100` through `922010900`
- Clear/bonus maps: `922011000` and `922011100`
- Exit map: `922010000`
- Party size: 5-6
- Level range: 35-50
- Event time: 45 minutes
- Pass/key items: `4001022`, `4001023`

### LPQ package

Create `server.agents.capabilities.partyquest.lpq` with the same lifecycle
components as HPQ, plus:

- `AgentLpqRosterRequirementPolicy`
- `AgentLpqRoomAssignment`
- `AgentLpqPortalMazeState`
- `AgentLpqCombinationOrder`

### LPQ admission requirements

Validate the actual live skills, not job names alone:

- one member able to use Teleport and deal magic damage
- one member able to use Dark Sight
- one effective ranged attacker for Stage 7
- one effective physical attacker for physical-only targets

A single character may satisfy multiple requirements. Human characters may
satisfy requirements but are never commanded to perform them; mixed-party
readiness should explain which required action still lacks an Agent volunteer.

### LPQ phase model and behavior

1. Stage 1: ordinary grind and leader collection/submission of 25 passes.
2. Stage 2: break main/alternate-room boxes and submit 15 passes.
3. Stage 3: ordinary grind and submit 32 passes.
4. Stage 4: assign physical and magical attackers across immunity rooms; return
   six passes to the leader.
5. Stage 5: assign six rooms, using Dark Sight and Teleport where required;
   break boxes and return 24 passes.
6. Stage 6: traverse the authored number-portal maze through normal portal
   behavior; store observed route progress per session, not globally.
7. Stage 7: position ranged attackers, kill the three trigger mobs, collect
   three passes, and submit normally.
8. Stage 8: place exactly five participants on five of nine authored areas and
   enumerate deterministic one-change combinations. Human positions are
   observed and held fixed where possible.
9. Stage 9: trigger and defeat Alishar, collect key `4001023`, and submit.
10. Reward/bonus/exit through the normal scripts.

### LPQ specific safeguards

- Do not assume all six members remain together in split rooms.
- Maintain per-room liveness and local recovery timers.
- Do not direct an Agent into the guarded room without live Dark Sight.
- Do not treat Teleport navigation edges as available unless the assigned Agent
  can currently cast Teleport.
- Preserve a human-first window for boss loot and optional rewards.
- Stage 8 must fail safely if fewer than five live participants remain, matching
  the local minimum-party event behavior.

### LPQ verification

- Definition tests for every map, NPC, item, stage property, portal, and area.
- Roster tests for all valid/invalid skill combinations.
- Room assignment and reassignment tests.
- Portal-maze progress and reset tests.
- Five-of-nine combination coverage and one-mover invariant tests.
- Boss key, reward, timeout, disconnect, and inventory-full tests.
- GM checkpoints for Stages 1-9 and a full live Agent-only/mixed smoke matrix.

## Phase 4: Orbis PQ

### Local contract

- Event manager: `OrbisPQ`
- Recruit map: `200080101`
- Event maps: `920010000` through `920011300`
- Exit map: `920011200`
- Clear map: `920011300`
- Party size: 5-6
- Level range: 51-70
- Event time: 45 minutes
- Exclusive items: `4001044` through `4001063`

The all-five-job composition grants an optional event buff; it is not a local
entry requirement. Autonomous roster selection may prefer it but must not reject
otherwise eligible parties unless the local scripts are changed separately.

### OPQ package

Create `server.agents.capabilities.partyquest.opq` with the same lifecycle
components, plus:

- `AgentOpqRoomAssignment`
- `AgentOpqStatueInventory`
- `AgentOpqPlatformDistributionOrder`
- `AgentOpqLeverCombinationOrder`
- `AgentOpqPortalTowerState`
- `AgentOpqGardenState`

### OPQ phase model and behavior

1. Cloud entry: break 20 cloud reactors, collect pieces, drop them at Eak, and
   enter the center tower.
2. Statue-room assignment: select incomplete rooms and assign teams without
   stranding the leader or violating the five-member minimum.
3. Walkway: grind and submit 30 pieces for statue piece 1.
4. Storage: break the randomized reactor chain, kill spawned mobs, and obtain
   statue piece 2.
5. Lobby/music: obtain the LP matching the event's initialized day, drop it on
   the music player, break the revealed box, and obtain statue piece 3.
6. Sealed room: distribute exactly three participants across three platforms,
   enumerate feedback-guided counts, break the revealed box, and obtain statue
   piece 4.
7. Lounge: clear split rooms and submit 40 pieces for statue piece 5.
8. On the way up: toggle exactly two of five levers using feedback-guided
   combinations, break the revealed box, and obtain statue piece 6.
9. Statue aggregation: verify all six pieces with the leader and open the garden.
10. Portal tower/jail: explicitly drive scripted portals, remember successful
    choices per row, solve jail levers, and recover from reset destinations.
11. Garden: kill Nependeaths for seeds, plant them in eligible pots, handle
    randomized spawns, trigger Papa Pixie, defeat him, and collect Root of Life
    `4001055`.
12. Restoration: return the leader and Root to the statue, drop it through the
    authored reactor flow, spawn Minerva, and clear.
13. Reward/bonus/exit through ordinary NPC/event scripts.

### OPQ portal-tower constraint

The WZ map `920010700` contains 88 portals, while the current navigation graph
probe exposes only 17 portal edges. The randomized scripted portals therefore
cannot be treated as ordinary destination edges. The coordinator must:

- navigate to each scripted portal's physical approach point,
- enter it explicitly through the normal portal gateway,
- observe the resulting map position/portal receipt,
- remember the successful column for each row within the current event only,
- detect reset/fallback destinations and retry with bounded exploration.

Prewarm this map's navigation graph before admission; a local probe rebuild took
approximately 12 seconds on the current workspace.

### OPQ room concurrency

- Start with sequential room completion for correctness.
- Add parallel room teams only after sequential full runs are stable.
- Each room owns a local assignment, progress timestamp, collector, and recovery
  timeout.
- The center coordinator must reconcile statue pieces held by any live member
  before choosing the next room.
- Human-held pieces remain authoritative and are never transferred by force.

### OPQ verification

- Definition tests for all maps, items, reactors, portals, properties, and rooms.
- Day/LP mapping and item-trigger tests.
- Platform and lever feedback-enumeration tests.
- Randomized portal tower exploration/memory/reset tests.
- Garden seed, pot, boss, Root, restoration, and friendly-state tests.
- Sequential room full-run gate before parallel-room tests.
- GM checkpoints for each room, tower row, jail, garden, restoration, reward,
  timeout, and failure.
- Repeated live Agent-only and mixed runs across every day-of-week LP case.

## Phase 5: Progression, fixtures, and roster supply

### Production eligibility

- HPQ: existing level-10+ Victoria population may participate once equipped and
  supplied.
- LPQ: requires genuine level-35-50 second-job Agents and the roster skills
  listed above.
- OPQ: requires genuine level-51-70 Agents with adequate accuracy, damage,
  survival, equipment, inventory, and consumables.

Population admission must fail closed when the managed population cannot form a
legal party. It must not mutate jobs, skills, or levels to satisfy a PQ.

### GM fixtures

Add separate fixed-name fixture pools for HPQ, LPQ, and OPQ. Fixture creation
must validate:

- legal AP/SP allocation
- required skills at usable levels
- level-appropriate weapons and armor
- ammunition and projectiles
- HP/MP recovery supplies
- ETC and reward inventory space
- ordinary movement stats

Fixtures are for observation and deterministic checkpoints only.

## Phase 6: Observability and operator controls

Provide parallel command families:

- `!hpqtest ...`
- `!lpqtest ...`
- `!opqtest ...`

Shared verbs should include:

- `start`, `withme`, `invite`, `party`
- `checkpoint`, `complete`, `status`
- `pause`, `resume`, `coordination`
- `run`, `switch`, `stop`

Status output must include quest key, engagement/session IDs, event instance,
phase/room, leader, execution lease, member roles, inventories relevant to the
stage, active blockers, last progress, and recovery deadline.

Metrics/logs should distinguish:

- lobby acquisition and admission rollback
- stage/room duration
- reactor and item-trigger retries
- navigation/portal resets
- combat and friendly-object failures
- item ownership and submission stalls
- reward and exit stalls
- termination reason

## Phase 7: Rollout gates

For each PQ independently:

1. Focused unit and integration tests pass.
2. Existing KPQ regression suite passes.
3. Agent-only test harness completes every checkpoint.
4. Human-led mixed party completes the full event.
5. Disconnect, death, inventory-full, timeout, portal failure, NPC failure, and
   event-disposal paths terminate or recover cleanly.
6. Repeated full runs show no leaked session, lobby, party, reactor reservation,
   loot reservation, navigation target, or primary activity.
7. Background enablement starts at one party globally and one per channel with
   one lobby reserved for humans.
8. Increase capacity only after measured scheduler, navigation, map, combat,
   memory, and database load remains within established budgets.

## Proposed implementation sequence

| Milestone | Deliverable | Verification |
|---|---|---|
| 1 | PQ descriptor, registry, dispatch, recovery destination | KPQ parity tests |
| 2 | Shared admission and session projection | rollback and activity tests |
| 3 | PQ reactor-hit and item-trigger operations | isolated reactor/drop tests |
| 4 | HPQ definition, session, lobby, harness | definition/admission tests |
| 5 | HPQ seeds and Moon Bunny defense | checkpoints and live HPQ run |
| 6 | HPQ reward/exit and production population | repeated HPQ soak |
| 7 | LPQ definition, roster, lobby, harness | roster/admission tests |
| 8 | LPQ Stages 1-5 | per-stage checkpoints |
| 9 | LPQ Stages 6-9, reward, exit | full LPQ live run |
| 10 | LPQ production population | repeated LPQ soak |
| 11 | OPQ ordinary-script repair and definition | human script smoke |
| 12 | OPQ rooms sequentially | room checkpoints |
| 13 | OPQ tower, jail, garden, restoration | full OPQ live run |
| 14 | OPQ parallel rooms | concurrency/recovery tests |
| 15 | OPQ production population | repeated OPQ soak |

## Completion criteria

The work is complete only when HPQ, LPQ, and OPQ each support:

- Agent-only production admission
- mixed human/Agent admission
- normal authoritative stage completion
- ordinary rewards and exit
- bounded failure and recovery
- checkpoint and full-run observation
- session-specific exclusive-item eligibility
- no ownership or reservation leaks
- conservative independently configurable background rollout
