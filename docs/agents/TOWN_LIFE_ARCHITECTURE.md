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
| Local admission, correlated session start/drain/stop, and checkpoint | TownLife lifecycle |
| Why and when a bounded visit ends | external caller/visit lease |
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
  -> caller supplies requestId + callerId + local visit request
  -> AgentTownLifeLifecycleRuntime admission returns a session handle
  -> foreground pause + durable local intent
  -> AgentTownLifeRuntime fidelity/session guard
  -> AgentTownLifeActivityRuntime
       SETTLING -> CHOOSE_ACTIVITY -> MOVE_TO_ACTIVITY -> DWELL -> COOLDOWN
  -> external graceful-exit request drains the current activity
  -> completed activity / bounded deadline / force / map exit
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
Disabling it force-stops active sessions and resumes their prior foreground work. A start request is
rejected with a typed result when the town is unsupported, disabled, full, or the Agent has not yet
been placed in that map.

Every non-legacy request has a caller-owned `requestId` and `callerId`. Admission returns an immutable
session handle. Repeating the same request is idempotent and returns the existing handle; a different
request cannot seize an active session. Exit requests must match both the session and caller. Normal
exit enters a drain: TownLife starts no new activity, lets the committed local activity reach a typed
terminal result, then exits. A caller-supplied deadline bounds failed movement, extensions, and future
conversation work. Emergency disable, map mismatch, and explicit force remain immediate cleanup paths.

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

An ordinary new town is JSON plus an index entry. The default registry deliberately has no activity
extensions, and all deployed profiles therefore use an empty `activityHandlers` list. The extension
execution seam is live: an explicitly installed handler can run only when a validated profile enables
`LOCAL_ACTIVITY`, and returns a typed completed, failed, timed-out, cancelled, or active result. A Java
extension is allowed only for a unique local mechanic that cannot be represented as venue/activity
data. Scenic spots remain manually authored profile data; none were invented by this rollout.

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

TownLife checkpoint schema 2 stores durable local intent plus stable session/request/caller identity,
drain request/deadline, and the last activity terminal result. Destinations, reservations, encounters,
extension instances, and live game objects remain transient. Registration restores a checkpoint only
when the Agent is already in the same supported town. A draining restore treats non-durable live work
as abandoned and completes the drain safely. Normal stop removes the checkpoint.

An optional external visit lease is persisted separately. It records only the session handle, absolute
departure time, graceful timeout, and reason. It watches the deadline as a non-exclusive coordinator;
TownLife still owns local activity and never decides the external purpose. The lease restores only
when the matching TownLife session restored first.

Exit, expiry, global disable, or map exit clears abstract execution, encounter/activity sequence,
fidgets, chairs, movement, and reservations before resuming the foreground clock.

Lifecycle events `STARTED`, `EXIT_REQUESTED`, `EXITED`, `FORCED`, and `TIMED_OUT` carry the session,
request, caller, final activity, and terminal activity result. External coordinators can observe these
events and submit their next objective; TownLife never advances a quest or travel cursor itself.

The registered universal-plan operation `town-life-visit` is the production adapter for this handoff.
Its required `durationMs` parameter creates a persisted external visit lease; optional `townMapId`,
`reason`, and `gracefulTimeoutMs` parameters refine the request. The plan cursor remains paused while
TownLife owns foreground execution and advances exactly once after the matching session reaches its
terminal boundary. Registration restores TownLife and its lease before reattaching the plan step.

Chat and trade use a bounded nested interaction lease. The TownLife session remains active while the
interaction owns foreground execution. Activity, sequence, free-time, role, and navigation-watchdog
clocks are parked. A graceful exit waits for the interaction; completion, timeout, cancellation, or
loss of the TownLife session releases the lease and resumes or drains TownLife. A trade has priority
over a concurrent chat. This lease does not give TownLife authority to initiate functional trades.

## Operational test harness

The GM6 command can run bounded tests in any already-authored town:

- `!townlife test readiness`
- `!townlife test start <seconds> [agent-count]`
- `!townlife test cycle <visit-seconds> <outside-seconds> <cycles> [agent-count] [standby]`
- `!townlife test social <chat|showoff> <agent-count> [venue-id]`
- `!townlife test status`
- `!townlife test stop`

The harness validates the current profile and selects only already registered Agents belonging to the
operator and present in the exact same live map instance. It creates an external visit lease for each
accepted Agent and requests graceful exit at the deadline. It deliberately does not spawn Agents,
move them to town, transact with facilities, edit plans, or synthesize scenic points.

Test sessions opt into observer narration. Agents announce session entry/exit, selected activity,
venue, committed dwell duration, local failures, standby movement, re-entry, and directed social
encounter phases. Narration is produced from structured events, is rate-limited, and is disabled for
ordinary TownLife sessions.

The cyclic harness remains an external non-exclusive coordinator. After each visit drains, it stages
the Agent at a safe local point, waits outside TownLife, and starts a fresh correlated session. The
optional standby value is `fallback`, `portal:<name>`, `npc:<id>`, or `facility:<id>`. Portal and NPC
targets are grounded and offset; excluded portal, door, ladder, and NPC traffic zones are never used
as occupancy. The harness does not enter a portal or invoke the NPC.

The directed social command selects an exact group from already-active local TownLife Agents.
`chat` supports two to four participants and `showoff` requires two. It uses the production encounter
reservations, approach, facing, expressions, fidgets, reaction, closing, timeout, and cleanup. The
test narrator adds deterministic placeholder lines; it is not a general conversation model.

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
  town map. A typed result reports unsupported, disabled, non-local, full, invalid, idempotently active,
  conflicting ownership, started, stopped, or inactive outcomes and returns the stable session handle.
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
- Visit expiry or requested stop asks the session to drain. A safe boundary exits immediately; an
  in-progress activity finishes first; the deadline turns an unfinishable activity into `TIMED_OUT`.
  Global disable, explicit force, or unexpected map exit remains immediate. Cleanup releases all
  destinations and encounters, clears fidgets/chairs/movement and abstract execution, deletes the
  local checkpoint, and resumes the paused foreground clock.
- A bounded chat or trade can temporarily own foreground execution without ending TownLife. The
  current local activity is parked, or an incompatible active group encounter is cancelled and
  replanned. Interaction completion resumes the parked clocks; an already-requested exit then drains.
- Checkpoints preserve stable correlation identity and drain state but never destinations, peers,
  reservations, extension instances, or live game objects. Restore is accepted only on the same
  supported town map. External visit leases use an independent checkpoint and ownership boundary.
- Taxi, portal, ship, shop, storage, quest, combat, and cross-town decisions are always external. Taking
  a taxi ends the source session; after travel, the destination town may independently admit a new
  session. TownLife never links the two sessions or owns their purpose.
- The process-wide control can prevent starts and force-stop all active TownLife sessions cleanly.
  Profile admission supports ambient requests and caps, but there is intentionally no autonomous
  ambient population coordinator: callers explicitly own admission and exit criteria. The bounded
  GM harness provides operational validation without becoming such a coordinator.

The design is complete when ordinary towns remain data-only and changes to combat, progression,
or cross-map navigation do not require TownLife policy changes.

## Ambient observation baseline

The TownLife capability still has no autonomous population authority. The external observation
coordinator under `server.agents.runtime.townlife.ambient` leases a reusable pool and calls the same
public entry/exit contract as quest plans and future world coordination.

`agents/town-life/ambient-deployment.json` configures Lith Harbor, Henesys, Kerning City, Perion,
Ellinia, Nautilus, and Sleepywood. It owns only population concerns: the global active percentage,
per-town allocation, per-activity soft targets and hard caps, long-tail dwell, post-activity
continue/relocate/switch/exit weights, chair provisioning, and visible or unmaterialized standby.
Town profiles continue to own all geometry, venues, traffic exclusions, facilities, and hotspots.

```text
!townlife ambient start [pool-size] [active-percent] [visible|unmaterialized]
!townlife ambient status
!townlife ambient percent <0-100>
!townlife ambient rebalance
!townlife ambient inspect <agent-name>
!townlife ambient stop
```

Stop and population reductions request a graceful exit; the current committed activity finishes
before the pool member is staged or unmaterialized. Ambient observation opts into the existing test
intention narration. It does not choose quests, grinding, travel, or economic work.
