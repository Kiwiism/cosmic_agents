# NuTNNuT Fix Port Status

Branch: `port/nutnnut-agent-correctness-population`

## Quest-status placeholder persistence

Source reference: `85249c8f0f` from
`source/fix-queststatus-notstarted-bloat`.

The current immutable persistence-snapshot pipeline now omits only an empty
`NOT_STARTED` placeholder. Records with forfeits, progress, medal-map state, or
a `STARTED`/`COMPLETED` status remain persisted. This reduces save pressure and
database growth without changing meaningful quest state.

## Navigation: joined-fork foothold continuity

Source reference: `7c0d287015` from `source/dev`.

Ground-region sampling now prefers the standing foothold and its direct
`prev`/`next` chain before overlapping non-chain segments. This matches the
client's standing-foothold model and prevents a walk across a joined fork from
snapping onto a lower dead-end arm. Agent navigation graph cache version 47
invalidates graphs authored with the previous walk simulation.

## Navigation: shared-ground continuity

Source reference: `59c5288994` from `source/dev`.

Jump edges whose landing lies exactly on the source region's own surface are
now rejected as phantom cross-region transitions. Runtime region resolution
also remembers the occupied region while coincident chains share the same
surface, resetting that memory when the graph instance changes. Agent graph
cache version 48 invalidates graphs containing the old edges.

## Movement: overlapping-foothold motor continuity

Source reference: `235b6aefa4` from `source/dev`.

Ground synchronization now resolves the foothold from the remembered navigation
region before falling back to coordinate-only lookup. Ground action planning
also passes its already-resolved standing foothold into collision preview. This
keeps the movement motor on the same coincident chain selected by navigation.

## Movement: client-compatible wall collision

Source reference: wall-collision portion of `43b9f06c8c` from `source/dev`.

Footholds now retain their WZ layer and zMass group. Region-constrained ground
walking follows its foothold chain without scanning unrelated vertical walls,
while airborne collision considers only base-group walls and walls belonging
to the mover's last grounded zMass group. Synthetic footholds with no group
metadata retain conservative all-wall collision. Agent graph cache version 49
invalidates graphs authored with the former chain-reaches-ground heuristic.

## Navigation: stale target and blocked-route recovery

Source reference: runtime-recovery portion of `43b9f06c8c` from `source/dev`.

Retained Agent navigation edges now carry the goal point used to plan them and
are rejected after that goal moves more than 128 pixels, including movement
within the same region. The reconstruction does not retain the donor's full
committed-route list; its independent movement stuck state already survives
navigation-edge clears and counts both active-edge and direct-target stalls,
providing the source watchdog behavior without restoring that monolith state.

## Navigation: foothold detour continuity

Source reference: `f5fe4b0a4d` from `source/dev`.

Branch-shaped source regions now use a sticky foothold-chain detour when an
edge launch can only be approached by initially walking away from its X
coordinate. The detour remains active until its crossing is completed and is
cleared when the edge or movement phase changes. Detour steering uses zero
stop distance so normal follow hysteresis cannot park the Agent before the
crossing.

## Movement: off-graph target recovery

Source reference: `f78f85d4bd` from `source/dev`.

This behavior was already present in the reconstructed Agent recovery pipeline:
target-distance and map-bounds recovery runs before movement core, consumes the
tick after teleport, resets transient movement state, and broadcasts the new
position. Existing recovery and movement-only ordering tests verify the source
invariant, so no duplicate travel-specific path was introduced.

## Navigation: reproducible directional walk-off drops

Source reference: `8a7e7b3a55` from `source/dev`.

Directional DROP edges are now authored by the same ground-motion walk-off
simulation used at execution time instead of a fall from the exact lip pixel.
Execution accepts any real descending dismount out of the source region rather
than requiring a knife-edge landing-region match. Narrow jump windows expand
to at least one motor step, and the stuck watchdog treats movement inside a
16-pixel drift radius as a bounce rather than progress. Agent graph cache
version 50 invalidates lip-authored DROP edges.

## Combat: magic passive damage refresh

Source reference: `71c86ad516` from `source/dev`.

The reconstructed combat path does not retain the donor's damage-profile cache;
it resolves a fresh profile for every attack, while the shared formula provider
already applies Element Amplification. A regression now changes the passive
skill level between consecutive resolutions and verifies that magic damage
updates immediately, proving that stale cached passive state cannot survive.

## Combat: combo-finisher orb guard

Source reference: `0399459152` from `source/dev`.

Agent attack planning now rejects Panic and Coma unless the COMBO buff value
represents at least one held orb. The shared close-range execution handler also
consumes an orb only while the buff still exists, covering expiry between plan
and execution without changing ordinary close-range attack behavior.

## Combat: Magic Guard and defensive parity

Source reference: `930ca5a01a` from `source/dev`.

Incoming Agent damage now passes through an Agent-owned policy that splits HP
and MP by the active Magic Guard percentage and returns insufficient MP to HP.
Magic Guard variants are treated as critical survival buffs and are attempted
from the common tick before ordinary combat-only buff gating. Defense data also
exposes the HP/MP-limited effective health pool used for survival valuation.
No-buff damage, zero MP, insufficient MP, redundant casting, and buff-disable
behavior are covered by focused tests.

## Combat: Shadow Partner route and packet parity

Source references: `3445fef5f3` and `eddb5bc65e` from `source/dev`, extended
to the required CLOSE, RANGED, and MAGIC Agent routes.

Shadow Partner now doubles planned lines for every attack route, derives each
partner line from its corresponding original using the active buff percentage,
and copies physical critical metadata instead of rerolling hit, miss, damage,
or critical state. Planning caps the original side at seven lines so the
largest partnered packet is 7 original / 14 total; target and line counts are
also clamped independently before packing their four-bit fields. Ranged ammo
cost behavior remains doubled, while CLOSE and MAGIC add no ammo cost.

## Population: external roster foundation

The Agent population implementation now has an Agent-owned, externally stored
roster foundation under `server.agents.population`. The JSON store writes by
atomic replacement, defaults to disabled when absent, validates bounded finite
multipliers, rejects duplicate character identities/names, and publishes state
only after persistence succeeds. Stable case-insensitive ordering and bounded
per-sweep/list policy constants prepare the roster for deterministic
reconciliation without adding Cosmic schema state or starting live sessions.

Population reconciliation now atomically claims each character transition,
rechecks Agent eligibility at the lifecycle boundary, converges in stable roster
order, bounds each sweep, and isolates individual failures. A disabled registry
does not disturb existing sessions. The Agent-owned steady scheduler is
cancellable and catches top-level sweep failures, while target/live/managed,
failure, and sweep timing metrics remain bounded counters.

The first Cosmic population adapter now treats the Agent-only account lock as
the offline eligibility boundary, rejects already-online and unavailable-world
loads, self-registers accepted sessions into autonomous grind mode, and removes
runtime/map state if registration fails. Session stop removes the Agent runtime
before synchronously disconnecting its headless client.
