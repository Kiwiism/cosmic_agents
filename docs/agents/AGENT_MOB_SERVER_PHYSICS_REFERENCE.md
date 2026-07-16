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
  hit-force values, 31-step knockback lifetime, chase forces and jump force.

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
- Optional experimental v83 `hit1` activity is sent once at FLINCH entry. FLINCH
  stops physical movement, and chase explicitly restores the proven walk loop.
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
from that final position when one is available. Generation-checked invalidation
prevents an older tick from releasing a session refreshed by a newer hit.
Every accepted Agent hit renews a configurable aggro lease, even when a current
knockback/recovery prevents another reaction. After seven seconds without an
Agent hit, the service stabilizes the mob pose and hands it to a real client
without immediate aggro, restoring ordinary client-controlled wandering.
Observer and transition state is evaluated once per map per outer tick rather
than once per active mob. Unchanged rounded mob positions skip redundant map
visibility work, and physics broadcasts omit BotClients because their packet
sink is intentionally a no-op.

Physics movement broadcasts skip characters that have not completed their map
transition. This prevents `MOVE_MONSTER` from arriving before the destination
client has rebuilt its mob objects. When a real client enters, active physics is
released before object placement and monster-controller assignment is deferred
until `PLAYER_MAP_TRANSFER`. The acknowledgement performs one full control
handoff per eligible monster; it does not destroy and respawn every monster.
Agent movement, combat, and physics remain paused for the configurable observer warm-up,
while normal death packets resume immediately after acknowledgement so a kill
during the handoff cannot leave a client-only ghost. Observer loss converts a
transient physics walk/flinch stance to a stable pose before the next placement.
Controller bookkeeping is cleared when a player departs, but the server does
not send an old-field `STOP_CONTROLLING_MONSTER` after `SET_FIELD`; same-map
warps reuse those object IDs in the destination field.

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

The checked-in server configuration is:

```yaml
AGENT_MOB_REACTION_MODE: PHYSICS
```

The mode and all `MOB_PHYSICS_*` tuning fields can be changed live with
`!botcfg FIELD value`. Leaving PHYSICS releases all active sessions before
another strategy can handle hits. `!botcfg status` reports active sessions,
acquisitions, impacts, substeps, publications, capped catch-ups, recoveries,
missing footholds, handoffs, releases by reason, and average/maximum tick time.
Live physics values are range-checked, and paired stop/resume or minimum/maximum
values are rejected when they would form an invalid interval.

Default behavior tuning:

| Live field | Default | Effect |
|---|---:|---|
| `MOB_PHYSICS_LEFT_EDGE_INSET_PX` | 18 | Keeps a walking mob's feet this far inside a connected platform's left edge. |
| `MOB_PHYSICS_RIGHT_EDGE_INSET_PX` | 10 | Equivalent right-edge inset. Knockback may still push a mob off an edge. |
| `MOB_PHYSICS_SPEED_PERCENT` | 100 | Global walk/fly chase-force scale shared by all mobs. |
| `MOB_PHYSICS_DIRECTION_REACTION_MAX_MS` | 500 | Per-mob sampled delay before reversing toward a target that crossed sides. |
| `MOB_PHYSICS_OBSERVER_WARMUP_MS` | 500 | Post-acknowledgement pause before Agent movement, combat, and mob physics resume for the arriving observer. |
| `MOB_PHYSICS_AGGRO_TIMEOUT_MS` | 7000 | Time since the last accepted Agent hit before server physics ends and non-aggro control returns to a real client; 0 disables expiry. |
| `MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT` | 60 | At an edge, chance to step away; otherwise the mob idles. |
| `MOB_PHYSICS_EDGE_IDLE_MIN_MS` / `MAX_MS` | 250 / 700 | Randomized edge/stuck idle duration. |
| `MOB_PHYSICS_EDGE_RETREAT_MIN_MS` / `MAX_MS` | 250 / 600 | Safety time limit for a retreat before aggro resumes. |
| `MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX` / `MAX_DISTANCE_PX` | 8 / 24 | Per-retreat sampled travel distance before aggro resumes. |
| `MOB_PHYSICS_STUCK_DETECT_MS` | 500 | No-progress window before bounded idle/retreat recovery. |
| `MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT` | 65 | Chance for stuck recovery to retreat rather than idle. |
| `MOB_PHYSICS_BEHAVIOR_JITTER_MS` | 250 | Per-mob extra jitter on stuck decision timing. |
| `MOB_PHYSICS_JUMP_COOLDOWN_MS` | 900 | Minimum interval between heuristic jumps. |
| `MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS` | 900 | Per-mob random time added to the jump cooldown and initial jump. |
| `MOB_PHYSICS_KNOCKBACK_PERCENT` | 40 | Scale for the translated hit force. |
| `MOB_PHYSICS_FLINCH_RECOVERY_MS` | 250 | Stationary recovery after knockback. Hits cannot add, restart, or reverse knockback until recovery finishes. |
| `MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS` | 0 | Disabled after worsening pull-in interpolation. Nonzero values linearly restore chase force after recovery. |
| `MOB_PHYSICS_HIT1_ENABLED` | false | Sends one experimental `hit1` activity at flinch entry; disabled because v83 did not render it in testing. |
| `MOB_PHYSICS_IMPACT_DELAY_PERCENT` | 100 | Retains the full WZ attack-frame hit delay. |
| `MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS` | 0 | Signed adjustment after scaling the hit delay. |

The physics step remains fixed at 8 ms. Variation is applied only to behavior
decisions and retreat distance, using a reproducible seed derived from map and
monster object IDs. Chase force has no per-mob variance: every mob retains its
own WZ-defined speed, scaled by the shared `MOB_PHYSICS_SPEED_PERCENT` value.
This desynchronizes groups without making collision or chase speed random.

With the configured 100% impact delay, knockback begins at the full WZ
attack-frame timing. The next physics service pass can add up to 50 ms, plus
network latency. Set `MOB_PHYSICS_IMPACT_DELAY_PERCENT` to `0` for the first
physics opportunity after the server accepts the hit. The accepted hit stops
existing horizontal chase velocity immediately, so the delay cannot pull the
mob farther into the Agent. `MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS` provides a
signed adjustment.

The WZ `pushed` value is a damage threshold, not a displacement value. A hit
enters knockback only when its applied damage is at least the monster's `pushed`
value. Fixed/non-mobile monsters remain ineligible. After the 248 ms knockback
trace, horizontal movement stops for `MOB_PHYSICS_FLINCH_RECOVERY_MS` before
aggro movement resumes. From impact scheduling through knockback and recovery,
additional hits still deal damage and may refresh the eventual aggro target,
but cannot stack velocity, restart the reaction, or reverse its saved direction.

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
cached terrain/profiles and an allocation-free weakly consistent registry view,
with no XML reads or per-mob recurring task.
