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
