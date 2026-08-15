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
- local entry portal names and allowed child-map declarations;
- navigation-derived geometry and platform policy;
- activity multipliers;
- traffic exclusions;
- authored rest/NPC/fallback points;
- semantic venues and capacity;
- facilities, local approach points, and service labels;
- hotspots with attraction, soft capacity, and hard capacity;
- registered local activity handler IDs.

Lith Harbor and Henesys were migrated to schema v2. Kerning City is the third-town proof: it uses
the WZ-backed `103000000` map, actual `west00`/`east00` portals and NPC positions, a PQ-plaza
hotspot, central/east/subway venues, and no town-specific Java handler.

An ordinary new town is JSON plus an index entry. A Java extension is allowed only for a unique
local mechanic that cannot be represented as a venue/activity. Profile validation rejects unknown
handler IDs, duplicate facility/hotspot IDs, missing hotspot venues, invalid capacity, traffic-zone
overlaps, and browse venues without a local approach spot.

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
3. Author traffic exclusions, reachable fallback points, venues, facilities, and hotspots.
4. Keep `activityHandlers` empty unless the town has a genuine unique mechanic.
5. Run `AgentTownLife*Test` and `AgentArchitectureBoundaryTest`.

The design is complete when ordinary towns remain data-only and changes to combat, progression,
or cross-map navigation do not require TownLife policy changes.
