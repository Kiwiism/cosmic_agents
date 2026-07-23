# Agent TownLife architecture

TownLife is the Agent-owned capability for spending non-progression time in a town. `ROAM` is one
activity inside TownLife; it is not the name of the subsystem.

## Responsibility boundaries

- `AgentTownLifeRuntime` owns the resumable stage machine and pauses ordinary Agent objectives while
  TownLife is active.
- `AgentForegroundPlanRuntime` is the global interruption seam. It ticks TownLife before delegating
  to the current concrete plan engine, so TownLife is not tied to Maple Island or Amherst.
- `AgentTownLifeActivityPolicy` selects an activity from role, memory, and personality traits.
- `AgentTownLifeControllerRuntime` is the policy seam. Deterministic policy remains the fallback;
  an optional controller can only propose a validated high-level activity, named venue, peer, and
  encounter type. It cannot provide coordinates or perform mutations.
- `AgentTownLifeActivitySequenceState` turns a selected activity into bounded `ORIENT`, `OPENING`,
  `PERFORMING`, `REACTION`, and `CLOSING` phases.
- `AgentTownLifeEncounterCoordinator` gives both participants typed encounter identity, role, phase,
  turn ownership, expiry, and peer identity. Agent-to-Agent coordination is structured state, not
  visible Maple chat.
- `AgentTownLifePlatformCatalog` converts the cached navigation graph into categorized reservation
  slots. It owns platform capacity, not movement.
- `AgentTownLifeSpotSampler` ranks catalog slots for an Agent's stable district/platform preference.
- `CharacterSpaceReservationRuntime` prevents two Agents from claiming the same slot.
- Navigation owns the route to a reserved point. TownLife never interpolates or teleports an Agent
  to an ordinary activity destination.
- Movement presentation owns fidgets, chairs, emotes, and weapon flourishes.
- `AgentTownLifeArrivalExtension` owns optional town-specific entry ceremonies. Lith Harbor uses it
  for Shanks and the outstanding Biggs/Olaf quest handoff.

## Per-town files

Each supported town has one JSON profile under `src/main/resources/agents/town-life/` and one entry
in `index.json`. A profile controls:

- upper/middle/lower Y boundaries;
- mini-platform width and generated reservation-slot spacing/capacity;
- stable population distribution across districts and mini platforms;
- occasional cross-district visit frequency;
- graph-validated arrival portal choices and weights;
- optional named extension handlers for town-specific arrival ceremonies;
- authored rest/map-seat points, fallback roam points, NPC pause offsets, and enterable shops.
- named semantic venues, their capacities, district/platform classification, concrete spots, and
  supported affordances such as rest, social, roam, shop, waiting, and sightseeing.

The engine catalogs every reachable non-rope navigation region at runtime, so a WZ foothold change
automatically creates a new set of platform slots. Authored points remain explicit because native
map seats, NPC offsets, shops, and arrival portals are semantic map content rather than geometry.

## Venue and controller contract

A venue describes what a place means; Navigation still decides how to reach it. The currently
cataloged Lith Harbor venues include the ship arrival deck, World Tour platform, central benches,
Olaf crossroads, east street, upper overlook, and the three shop interiors.

`AgentTownLifeDecisionContext` is the only view exposed to a future decision plugin. It contains:

- immutable Agent/town/profile identity;
- current TownLife stage, activity, role, venue, district, and platform preference;
- personality traits and recent activities;
- town Agent count and real-observer presence;
- bounded encounter state;
- venue IDs, labels, affordances, capacities, and current occupancy;
- decision time and sequence.

`AgentTownLifeDirective` is the only accepted controller output. It contains an activity, optional
venue ID, optional peer Agent ID, encounter type, expiry, source, and correlation ID. The runtime
rejects expired directives, missing or incompatible venues, invalid peers, and peers outside the
same active TownLife map. Destination resolution, reservation, navigation, animation, event
publication, and cleanup always remain deterministic.

The controller callback is a non-blocking hot-path interface. A future LLM adapter must perform
network inference asynchronously and expose only a fresh cached proposal here.

### Per-Agent support levels

| Level | TownLife decisions | Dialogue rendering |
|---|---|---|
| `DETERMINISTIC` | Built-in role/personality policy | Deterministic templates/emotes |
| `DIALOGUE_ONLY` | Built-in role/personality policy | May use an external language renderer later |
| `DIALOGUE_AND_DECISION` | Valid external proposals with deterministic fallback | May use an external language renderer later |

The support level grants eligibility, not direct mutation authority. Enabling either LLM mode does
not require TownLife execution code to know which model or provider produced a proposal.

## Events and presentation

TownLife publishes two ambient, session-local event families:

- `townlife.activity`: selection, arrival, phased performance, completion, or abandonment, including
  venue and controller provenance;
- `townlife.encounter`: invitation, approach, activation, reaction, closing, completion, or
  cancellation for each participant.

These events feed the existing bounded bus, durable-policy filter, monitoring, and LLM context
projection. A deterministic Dialogue listener converts only a stable subset of initiated encounters
into `AgentDialogueIntentEvent`. Observer presence and cooldown are still enforced by the shared
Dialogue projection. TownLife does not broadcast chat directly.

## Current LLM-ready flow

```text
Town perception/profile/venues
  -> immutable AgentTownLifeDecisionContext
  -> deterministic policy OR fresh external AgentTownLifeDirective
  -> validation and deterministic destination resolution
  -> reservation/navigation/activity sequence/encounter
  -> TownLife events
  -> deterministic presentation today
  -> optional LLM dialogue renderer later
```

## Remaining TownLife completion work

Recommended order after this slice:

1. Reserve coordinated two-person venue slots and let responders explicitly accept or decline an
   invitation before either participant moves.
2. Generalize encounters from pairs to bounded groups of three or four with one turn owner and
   staggered reactions.
3. Add traffic affordances and exclusion areas for doors, portals, ladders, and NPC interaction
   lanes so large populations do not obstruct traversal.
4. Add a visit lifecycle (`ARRIVING`, `ERRAND`, `FREE_TIME`, `DEPARTING`) and let progression,
   Supplies, and Shopping request TownLife time without TownLife owning those capabilities.
5. Add observer-aware foreground/background fidelity: preserve session state and decisions while
   coalescing movement and cosmetic phases when no real client can observe them.
6. Add short-term social memory and relationship summaries so Agents vary peers and remember recent
   encounters without storing live server objects.
7. Add profile validation tooling and seed equivalent venue data for each supported Victoria town.
8. Add population/venue/encounter metrics and debug commands before large-cohort tuning.

After those items, TownLife itself is feature-complete enough for broad deployment. LLM work should
then extract a provider-neutral language-model SPI, replace the existing live `Character`/`MapleMap`
prompt inputs with immutable request DTOs, add an asynchronous expiring proposal cache, and validate
all model output through the same Dialogue and TownLife directive contracts.

## Adding a town

1. Add `<town>.json` and list it in `index.json`.
2. Verify the arrival portals resolve to non-rope navigation regions.
3. Add an `AgentTownLifeArrivalExtension` and select its key in the town profile only if the town has
   a unique entry ceremony; otherwise the generic route-and-settle extension is used.
4. Add a presentation/activity extension only for behavior unique to that town. Generic movement,
   reservations, personality selection, and stage handling must remain in the shared engine.

The current identity-derived district preference is a deterministic fallback. A durable personality
policy can later assign the same fields without changing platform cataloging or reservations.
