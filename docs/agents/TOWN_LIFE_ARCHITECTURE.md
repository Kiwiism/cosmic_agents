# Agent TownLife architecture

TownLife is a local, non-progression foreground capability. One session belongs to one Agent in one
town map. It never owns travel to the town, taxi scripts, quests, required shopping, storage,
combat, or progression recovery.

## Ownership model

| Concern | Owner |
|---|---|
| Why an Agent should visit a town or facility | autonomy/progression/economy |
| Foreground priority and pause/resume | foreground runtime |
| Taxi, portal, ship, and cross-map travel | travel capability |
| Buying, selling, and storage transactions | shop/storage capabilities |
| Local admission, session start/stop, and checkpoint | TownLife lifecycle |
| Local activity selection and bounded execution | TownLife activity runtime |
| Movement to a local reserved point | navigation |
| Town geometry, venues, facilities, hotspots, weights | profile JSON |
| Truly unique local mechanics | bounded extension registry |

A taxi is an exit boundary. TownLife ends before travel begins. If the destination town admits the
Agent, it starts a new local session there. A cross-town purpose or correlation ID belongs to the
outer objective, not either TownLife session.

## Runtime shape

```text
outer objective/travel places Agent in town
  -> AgentTownLifeLifecycleRuntime admission
  -> foreground pause + durable local intent
  -> AgentTownLifeRuntime fidelity/session guard
  -> AgentTownLifeActivityRuntime
       SETTLING -> CHOOSE_ACTIVITY -> MOVE_TO_ACTIVITY -> DWELL -> COOLDOWN
  -> stop/map exit/expiry/global disable
  -> reservations and encounters released
  -> checkpoint removed + foreground resumed
```

`AgentTownLifeRuntime` remains the compatibility facade used by existing foreground and abstract
execution integrations. Lifecycle and activity execution are separate beneath that facade. All
Agents remain on the existing central Agent scheduler; TownLife does not create threads or
per-town schedulers.

## Admission and shutdown

Each schema-v2 profile declares `DISABLED`, `MANUAL_ONLY`, or `AMBIENT` admission plus an ambient
population cap and visit/cooldown bounds. `AgentTownLifeControlRuntime` is the process-wide switch.
Disabling it stops active sessions and resumes their prior foreground work. A start request is
rejected with a typed result when the town is unsupported, disabled, full, or the Agent has not yet
been placed in that map.

Unexpected map exit ends the session. TownLife does not navigate back or infer that the Agent is in
a shop. Functional callers may start a new session after their task or travel completes.

## Activities and facilities

The canonical ambient activities are:

- `REST`
- `SOCIALIZE`
- `LINGER`
- `STROLL`
- `BROWSE`
- `SHOW_OFF`
- `LOCAL_ACTIVITY` for a registered bounded extension

`BROWSE` is cosmetic and local. It can approach a shopfront but cannot enter an interior or buy an
item. Facilities are discoverable service metadata (`GENERAL_SHOP`, `STORAGE`, `TAXI`,
`FREE_MARKET`, `PARTY_QUEST`, and related types); the capability named by the outer objective owns
the actual service transaction.

The deterministic policy consumes an immutable activity context. Controller/LLM adapters receive
the existing immutable decision context. Population and occupancy are scoped by world, channel,
and map through `AgentTownLifePopulationPort`; policy does not count same-map IDs from another
channel.

Candidate selection combines role/personality weights, per-town activity multipliers, recent-use
penalties, venue attraction, occupancy below soft/hard capacity, approximate local travel cost,
and deterministic jitter. A selected destination is leased until completion or bounded failure;
the loop does not reselect every tick.

## Profiles

Profiles under `src/main/resources/agents/town-life/` use schema version 2. They contain:

- admission policy;
- local entry portal names;
- navigation-derived geometry and platform policy;
- activity multipliers;
- traffic exclusions;
- authored rest/NPC/fallback points;
- semantic venues and capacity;
- facilities, local approach points, and service labels;
- hotspots with attraction, soft capacity, and hard capacity;
- registered local activity handler IDs.

Venue and activity destinations are strictly local points. They cannot carry a destination map ID.
Facilities may expose a destination map ID as read-only metadata for the outer travel/shop capability,
but the TownLife runtime does not consume it and cannot change maps.

Lith Harbor and Henesys were migrated to schema v2. Kerning City is the third-town proof: it uses
the WZ-backed `103000000` map, actual `west00`/`east00` portals and NPC positions, a PQ-plaza
hotspot, central/east/subway venues, and no town-specific Java handler.

An ordinary new town is JSON plus an index entry. The current baseline registry deliberately has no
activity extensions, and all deployed profiles therefore use an empty `activityHandlers` list. The
extension interface is a reserved boundary, not an active generic behavior. A future Java extension
is allowed only for a unique local mechanic that cannot be represented as a venue/activity, and must
be registered and wired through the activity runtime before a profile may reference it.

Profile loading rejects duplicate profile or map identities. Profile validation rejects unknown
handler IDs, duplicate portal/facility/hotspot/traffic-zone/platform-policy IDs, duplicate venue
affordances or facility services, missing hotspot venues, invalid or inconsistent capacity, excluded
occupancy points, mismatched native seats, non-executable activity weights, and browse venues without
a local approach spot.

## Extension contract

`AgentTownLifeActivityExtension` receives immutable identity, scope, venue, time, and deadline. It
returns `ACTIVE`, `SUCCEEDED`, `BLOCKED`, `FAILED`, or `CANCELLED`. It may coordinate local
navigation, reservations, presentation, and events through injected ports in its adapter, but it
must not:

- start or stop TownLife;
- choose another town;
- execute taxi, quest, shop, storage, or progression logic;
- teleport or bypass reservations;
- retain live `Character`/`MapleMap` objects;
- create a thread or scheduler.

## Persistence and cleanup

TownLife checkpoints store only durable local intent: character ID, town map, visit purpose/reason,
remaining bounded free time, and update time. Destinations, reservations, encounters, and live game
objects are transient. Registration restores a checkpoint only when the Agent is already in the
same supported town. Normal stop removes the checkpoint.

Stop, expiry, global disable, or map exit clears abstract execution, encounter/activity sequence,
fidgets, chairs, movement, and reservations before resuming the foreground clock.

## Adding a town

1. Read the map's WZ portal, foothold, seat, and NPC data.
2. Add a schema-v2 profile and index entry.
3. Choose `DISABLED`, `MANUAL_ONLY`, or `AMBIENT` admission and bounded ambient capacity/visit times.
4. Author reachable fallback/rest/venue points and keep portal, door, ladder, and NPC transit lanes
   out of occupancy.
5. Describe facilities only as discoverable metadata; keep all activity/venue points on the profile's
   own map.
6. Add capacity-bounded hotspots only where the town should naturally gather a crowd.
7. Keep `activityHandlers` empty unless a genuine unique mechanic has first been implemented and
   registered through the extension boundary.
8. Run `AgentTownLife*Test`, `AgentArchitectureBoundaryTest`, and the configuration boundary tests.

The currently deployed data-only profiles are Lith Harbor (`104000000`), Henesys (`100000000`), and
Kerning City (`103000000`). Adding another ordinary town does not require a town-specific loop,
scheduler, Java policy class, combat change, or progression change.

## Baseline behavior contract

- TownLife starts only after another capability has already placed the Agent on the exact supported
  town map. A typed result reports unsupported, disabled, non-local, full, invalid, already-active,
  started, stopped, or inactive outcomes.
- Starting a session pauses the previous foreground plan, records bounded local visit intent, assigns
  a deterministic home district/platform preference, enables TownLife abstract execution, and enters
  `SETTLING`.
- The local loop progresses through settling, deterministic activity choice, destination reservation,
  physical or abstract movement, bounded dwell, and cooldown. A leased destination is retained until
  arrival, success, timeout, or bounded failure; it is not replaced every tick.
- The first placement is a stroll. Later choices use activity weights, role/personality, recent-use
  memory, district/platform preference, venue attraction and occupancy, approximate local travel
  cost, and deterministic jitter. Stationed and wanderer roles vary how broadly Agents move.
- `REST` uses an authored/native seat when available and otherwise performs local rest presentation.
  `SOCIALIZE` and `SHOW_OFF` may form bounded encounters with another active local TownLife Agent.
  `LINGER`, `STROLL`, and cosmetic `BROWSE` remain local. No baseline profile executes
  `LOCAL_ACTIVITY`.
- Reservations, population, and peer selection are scoped by world, channel, and map. Venue/hotspot
  soft and hard capacities spread Agents while still allowing authored gathering places. Traffic
  exclusions keep portal, door, ladder, and NPC lanes clear.
- Observed maps use presentation fidelity and emit physical movement, chairs, expressions, fidgets,
  and encounters. Unobserved active maps continue physical behavior without ambient presentation.
  Background-abstract sessions skip physical navigation and encounters while advancing bounded dwell.
- A progress watchdog abandons a destination that does not make movement/region progress, releases its
  reservation, remembers a temporary failure cooldown, and replans locally. It does not teleport,
  invoke combat recovery, change maps, or seize progression recovery.
- Visit expiry, requested stop, global disable, unexpected map exit, or Agent cleanup terminates the
  session. Termination releases destinations and encounters, clears fidgets/chairs/movement and
  abstract execution, deletes the local checkpoint, and resumes the paused foreground clock.
- Checkpoints contain only character ID, town map, purpose/reason, remaining bounded free time, and
  update time. Restore is accepted only when the Agent is already on the same supported town map;
  destinations, peers, reservations, and live game objects are never persisted.
- Taxi, portal, ship, shop, storage, quest, combat, and cross-town decisions are always external. Taking
  a taxi ends the source session; after travel, the destination town may independently admit a new
  session. TownLife never links the two sessions or owns their purpose.
- The process-wide control can prevent starts and stop all active TownLife sessions cleanly. Profile
  admission supports ambient requests and caps, but the baseline does not yet include an autonomous
  ambient population coordinator or an operational control command that creates those requests.

The design is complete when ordinary towns remain data-only and changes to combat, progression,
or cross-map navigation do not require TownLife policy changes.
