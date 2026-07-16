# Agent monster server physics: implementation and reference record

## Upstream and license

The reusable physics behavior is translated from
[`nmnsnv/maplestory-wasm`](https://github.com/nmnsnv/maplestory-wasm), pinned to
commit `bc0234fe7c7f53322453e7bdd79564d9aca4cd8b`. The upstream and this port are
AGPL-3.0-or-later.

Translated sources:

- `src/client/Gameplay/Physics/Physics.cpp`: 8 ms update order, gravity,
  ground/slope friction, flying/swimming friction, bounds and collision.
- `src/client/Gameplay/Physics/PhysicsObject.h`: precise body state, modes,
  flags, force, acceleration, velocity and foothold state.
- `src/client/Gameplay/Physics/Foothold.cpp` and `Foothold.h`: precise slope,
  endpoint, wall and platform relationships.
- `src/client/Gameplay/Physics/FootholdTree.cpp` and `FootholdTree.h`: ID and
  below-ground lookup, wall/edge constraints and map bounds.
- `src/client/Gameplay/Mob/Mob.cpp` and `Mob.h`: WZ speed conversions,
  hit-force values, 31-step flinch lifetime, chase forces and jump force.

No rendering, audio, UI, NX wrapper, packet parser, damage calculation or
unrelated gameplay code was copied.

## Deliberate differences

- Journey's per-pixel foothold multimap is replaced by immutable 64-pixel
  buckets plus an O(1) ID map. This prevents memory growth on wide maps while
  retaining precise double-slope interpolation.
- The server advances all owned mobs from one 50 ms channel task and a bounded
  8 ms accumulator. A stalled scheduler drops excess backlog instead of
  entering an unbounded catch-up spiral.
- The requested 0.1 airborne/flying knockback force is applied explicitly.
  Journey's normal airborne hit path does not consistently consume its
  horizontal hit force, but the server needs visible, deterministic reaction.
- v83 `hit1` is not used because it does not render reliably. FLINCH publishes
  a neutral standing-facing stance while physics continues.
- Packet coordinates are rounded only at publication and use the existing v83
  two-pixel client Y convention. Internal state remains double precision.
- Jumping is intentionally a safe local heuristic, not platform pathfinding.
  A mob approaches and stops when the target requires full navigation.
- `ICE` is represented but intentionally unsupported, matching the unfinished
  upstream mode.

## Runtime architecture

`AgentMobReactionRouter` is the sole accepted-hit entry point. Its authoritative
mode is `OFF`, `SYNTHETIC`, or `PHYSICS`; there are no independent enable
booleans. `OFF` performs no Agent reaction, `SYNTHETIC` retains the one-shot
implementation, and `PHYSICS` registers one generation-updated session in the
channel's `MobPhysicsService`.

An Agent hit is eligible only when damage is positive, both entities are alive
in the same map, and at least one real client observes that map. Acquisition
revokes the real controller, assigns the BotClient logically, and marks the
monster `AGENT_PHYSICS`. BotClient packet handling remains unchanged and
headless. While that authority is active, ordinary controller switches and
client `MOVE_LIFE` updates are rejected.

The service validates observer, Agent, monster and map lifecycle on every outer
tick. Release commits the latest server state, removes the Agent's controlled
entry, clears authority, and sends the normal control packet to a real client
from that final position when one is available.

Lock order is:

1. Optional `Monster.monsterLock` for a compound entity update.
2. `Monster.aggroUpdateLock` for authority/controller mutation.
3. Character controlled-monster lock through `controlMonster` or
   `stopControllingMonster`.

The channel registry is concurrent and is never held while acquiring monster
or character locks. Session mutation is synchronized per session. Broadcasts
occur after authoritative state is committed, and one failed session is
released without terminating the service.

## Configuration and live diagnostics

The default is:

```yaml
AGENT_MOB_REACTION_MODE: OFF
```

The mode and all `MOB_PHYSICS_*` tuning fields can be changed live with
`!botcfg FIELD value`. Leaving PHYSICS releases all active sessions before
another strategy can handle hits. `!botcfg status` reports active sessions,
acquisitions, impacts, substeps, publications, capped catch-ups, recoveries,
missing footholds, handoffs, releases by reason, and average/maximum tick time.

Default behavior tuning:

| Live field | Default | Effect |
|---|---:|---|
| `MOB_PHYSICS_LEFT_EDGE_INSET_PX` | 18 | Keeps a walking mob's feet this far inside a connected platform's left edge. |
| `MOB_PHYSICS_RIGHT_EDGE_INSET_PX` | 10 | Equivalent right-edge inset. Knockback may still push a mob off an edge. |
| `MOB_PHYSICS_SPEED_PERCENT` | 75 | Global walk/fly chase-force scale. |
| `MOB_PHYSICS_SPEED_VARIANCE_PERCENT` | 10 | Deterministic per-mob variation around the global speed. |
| `MOB_PHYSICS_DIRECTION_REACTION_MAX_MS` | 500 | Per-mob sampled delay before reversing toward a target that crossed sides. |
| `MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT` | 60 | At an edge, chance to step away; otherwise the mob idles. |
| `MOB_PHYSICS_EDGE_IDLE_MIN_MS` / `MAX_MS` | 250 / 700 | Randomized edge/stuck idle duration. |
| `MOB_PHYSICS_EDGE_RETREAT_MIN_MS` / `MAX_MS` | 250 / 600 | Randomized retreat duration before aggro resumes. |
| `MOB_PHYSICS_STUCK_DETECT_MS` | 500 | No-progress window before bounded idle/retreat recovery. |
| `MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT` | 65 | Chance for stuck recovery to retreat rather than idle. |
| `MOB_PHYSICS_BEHAVIOR_JITTER_MS` | 250 | Per-mob extra jitter on stuck decision timing. |
| `MOB_PHYSICS_JUMP_COOLDOWN_MS` | 900 | Minimum interval between heuristic jumps. |
| `MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS` | 900 | Per-mob random time added to the jump cooldown and initial jump. |
| `MOB_PHYSICS_KNOCKBACK_PERCENT` | 50 | Scale for the translated hit force; 50 gives roughly half-distance knockback. |
| `MOB_PHYSICS_IMPACT_DELAY_PERCENT` | 100 | Scale for the WZ attack-frame hit delay. |
| `MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS` | 0 | Signed adjustment after scaling the hit delay. |

The physics step remains fixed at 8 ms. Variation is applied only to decisions
and force, using a reproducible seed derived from map and monster object IDs;
this prevents synchronized groups without making collision nondeterministic.
The reaction and client damage display are both scheduled from the same WZ
attack-frame delay. A small residual visible gap of up to one 50 ms channel
tick plus network latency is therefore normal. Reduce
`MOB_PHYSICS_IMPACT_DELAY_PERCENT` or use a negative
`MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS` only when captures show that movement
consistently trails the rendered hit by more than that residual gap.

## Numerical verification

Pure deterministic tests cover the reference constants and analytic
trajectories at `1e-9` or tighter tolerance: force/acceleration order, ground
friction and stop threshold, gravity and landing, ascending/descending slopes,
walls, edges, connected footholds, map bounds, fixed/ice modes, swimming,
flying acceleration/deceleration, jump arc, invalid-state recovery, and bounded
accumulation. Tests also cover asymmetric edge insets, connected platforms with
more than two segments, deterministic per-mob decision variance, reversal
delay, jump jitter, edge/stuck recovery, speed scaling and impact tuning. The
31-step hit trace is asserted at 8 ms per step (248 ms).

The Java tests use analytic values from the pinned C++ formulas rather than a
compiled C++ golden-trace executable. This avoids toolchain-dependent float
serialization; tolerated differences are limited to double rounding and final
integer packet-coordinate rounding.

The integration/stress suite exercises mode isolation, WZ profiles, authority,
late client movement, controller stealing, thresholded impacts, repeated hits,
multiple Agents, captured direction, observer release, publication and 100
simultaneous sessions. Allocation measurement is not asserted because the JVM
test environment has no stable allocation profiler; production hot paths use
cached terrain/profiles and one registry snapshot per outer tick, with no XML
reads or per-mob recurring task.
