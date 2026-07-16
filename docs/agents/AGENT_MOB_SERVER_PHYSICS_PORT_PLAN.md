# Agent-Owned Mob Server Physics Port Plan

Status: implementation specification
Target repository: Cosmic Agents
Reference client: `nmnsnv/maplestory-wasm` / Journey physics
Reference revision inspected: `bc0234fe7c7f53322453e7bdd79564d9aca4cd8b`

## 1. Objective

Port the reusable physics behavior from the C++ WASM MapleStory client into Java and integrate it into Cosmic as a general server physics engine.

The first consumer is monster simulation. When an Agent `BotClient` lands an accepted hit on a monster in a map observed by at least one real player, the Agent becomes the monster's logical controller and the server physics engine takes over movement from the real player client. The server simulates flinch, knockback, foothold movement, jumping, flying, and simple aggro toward the Agent, then broadcasts standard monster movement packets to real clients.

The engine must remain independent of Agent and monster domain classes so it can later support BotClient pets without redesigning the physics kernel.

The current synthetic mob reaction remains available as a standalone mode. Exactly one mode is active:

- `OFF`: no Agent-specific mob reaction or ownership change.
- `SYNTHETIC`: current one-shot synthetic displacement behavior.
- `PHYSICS`: continuous server physics, Agent ownership, flinch, knockback, and aggro.

## 2. Corrected assumptions

The implementation must follow these corrections rather than treating all initial assumptions as facts:

1. Any accepted positive Agent hit may establish aggro and physics ownership. Damage does not need to meet `info/pushed` for ownership.
2. Physical knockback occurs only when the accepted damage meets the mob's `info/pushed` threshold.
3. The WASM engine supplies jump integration but not intelligent jump navigation. A server aggro policy must decide when a jump is useful.
4. “Always move toward the Agent” requires a stopping radius and hysteresis. Otherwise the mob repeatedly crosses the target and reverses direction.
5. `BotClient.sendPacket()` should remain a no-op. The BotClient is a logical controller; a direct server service performs simulation.
6. Server authority should be explicit rather than inferred only from `Monster.controller`, because many existing controller paths assume a live network client.
7. No NX files are required. Cosmic already loads extracted WZ XML into runtime map and mob objects.
8. The port should preserve physics behavior, not mechanically reproduce rendering, audio, animation, NX access, packet parsing, or client damage calculation.
9. No `hit1` animation is required. Flinch is a server motion state and can be represented to clients using a standing-facing stance while the mob slides briefly.
10. Full platform pathfinding is outside the first implementation. Simple chase must not teleport a mob to unreachable platforms.

## 3. Required runtime behavior

### 3.1 Authority matrix

Authority is tracked per monster, not per map.

| Map and monster state | Authority and behavior |
|---|---|
| Agents only | Dormant; no server mob simulation |
| Players only | Normal real client control |
| Players and Agents before an Agent hit | Normal real client control |
| Players and Agents after an accepted Agent hit | BotClient logical owner plus server physics |
| Controlling Agent leaves, dies, or disconnects | Commit final state and return control to a real player |
| Last real observer leaves | Stop physics and clear Agent ownership |
| Monster dies, despawns, or changes map | Destroy the simulation session |

A human entering a previously Agent-only map receives normal client control. An Agent must land a new accepted hit to acquire that monster.

### 3.2 Acquisition

When `AgentPresence.mobHitAccepted` receives an accepted positive Agent hit in `PHYSICS` mode:

1. Validate that the attacker is a live Agent BotClient in the monster's map.
2. Require at least one real human observer.
3. Require a live, mobile monster supported by the initial physics scope.
4. Acquire the monster ownership lock using a documented lock order.
5. Capture the latest server-known position, foothold, stance, facing, and map instance.
6. Revoke the current real client controller and send `STOP_CONTROLLING_MONSTER` to it.
7. Assign the Agent BotClient as logical controller and add the monster to its controlled-monster collection.
8. Mark the explicit authority as `AGENT_PHYSICS`.
9. Seed and register a `MobSimulationSession`.
10. Schedule the hit impact using the accepted attack's existing reaction delay.

Late `MOVE_LIFE` packets from the old controller must be rejected by the existing controller identity validation. No packet is sent to or decoded by the BotClient.

### 3.3 Release and handoff

Release the session when the controlling Agent leaves the map, dies, disconnects, the map becomes unobserved, the mode no longer allows physics, or the monster disappears.

Release sequence:

1. Invalidate pending impacts with a session generation token.
2. Stop further physics steps.
3. Commit the final position, foothold, stance, and facing to `Monster`.
4. Remove the monster from the Agent's controlled collection.
5. Clear `AGENT_PHYSICS` authority and the BotClient controller.
6. If a human observer remains, run normal client-controller selection.
7. Send the selected client a control packet seeded from the final server state.
8. If no human observer remains, leave the monster dormant.

### 3.4 Repeated hits and multiple Agents

- A repeated hit on an already simulated monster must not create a second session.
- A qualifying repeated hit replaces the scheduled/current knockback direction and resets flinch at impact time.
- If another Agent hits the mob, update the physics session's controlling/aggro Agent without handing control briefly back to a player.
- If the current controlling Agent leaves, hand the mob back to a player. Another Agent may reacquire it with a later hit.
- Use generation tokens so delayed impacts from earlier ownership cannot mutate the current session.

## 4. Recommended package architecture

```text
server.physics
├── PhysicsBody.java
├── PhysicsMode.java
├── PhysicsFlags.java
├── PhysicsInput.java
├── PhysicsStepResult.java
├── PhysicsTerrain.java
├── PhysicsBounds.java
├── MaplePhysicsConstants.java
├── MaplePhysicsIntegrator.java
└── foothold
    ├── FootholdSegment.java
    ├── FootholdPhysicsIndex.java
    └── MapleFootholdTerrain.java

server.life.simulation
├── MobPhysicsProfile.java
├── MobPhysicsProfileFactory.java
├── MobPhysicsState.java
├── MobMotionState.java
├── MobControlAuthority.java
├── MobSimulationSession.java
├── MobPhysicsSimulator.java
└── MobSimulationRegistry.java

server.agents.capabilities.mobcontrol
├── AgentMobReactionMode.java
├── AgentMobReactionRouter.java
├── AgentMobReactionStrategy.java
├── AgentMobControlService.java
├── AgentMobAggroService.java
├── AgentMobKnockbackService.java
├── OffMobReactionStrategy.java
├── SyntheticMobReactionStrategy.java
└── PhysicsMobReactionStrategy.java

net.server.services.task.channel
└── MobPhysicsService.java
```
### 4.1 Dependency direction

- `server.physics` must not import `Monster`, `Character`, `BotClient`, packet classes, or Agent packages.
- `server.life.simulation` adapts monster data and state to the general physics API.
- `server.agents.capabilities.mobcontrol` decides acquisition, release, aggro target, and knockback initiation.
- `MobPhysicsService` schedules active sessions and delegates domain work.
- Existing packet code serializes simulation results.

This boundary permits a future pet adapter to reuse `PhysicsBody`, `PhysicsTerrain`, and `MaplePhysicsIntegrator` without depending on monster logic.

## 5. C++ port scope

Pin the C++ source revision and document it in ported files. Preserve AGPL attribution.

Port behavior from:

- `src/client/Gameplay/Physics/Physics.cpp`
- `src/client/Gameplay/Physics/PhysicsObject.h`
- `src/client/Gameplay/Physics/Footholdtree.cpp`
- `src/client/Gameplay/Physics/Foothold.cpp`
- Relevant movement, jump, flying, and knockback sections of `Mob.cpp`

Do not port:

- Rendering and frame interpolation.
- Sprite or animation objects.
- Sound and visual effects.
- NX node wrappers.
- UI code.
- Client packet parsing or dispatch.
- Client damage calculation.
- Random roaming unless later desired as a separate policy.

## 6. General physics model

### 6.1 Physics body

The general body holds precise state rather than integer packet coordinates:

```java
public final class PhysicsBody {
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private double forceX;
    private double forceY;
    private int footholdId;
    private double footholdSlope;
    private int footholdLayer;
    private boolean grounded;
    private PhysicsMode mode;
    private int flags;
}
```

Initial modes:

- `NORMAL`
- `FLYING`
- `SWIMMING`
- `FIXED`
- `ICE`

Represent `ICE`, but leave it explicitly unsupported because the reference implementation also leaves it as `TODO`.

### 6.2 Inputs and results

Physics receives intention as force, not Agent or monster concepts:

```java
public record PhysicsInput(
        double horizontalForce,
        double verticalForce,
        boolean turnAtEdges,
        boolean checkBelow) {
}
```

`PhysicsStepResult` should expose important transitions without allocations on every substep where possible:

- Final precise position and velocity.
- Current foothold and layer.
- Landed or left ground.
- Hit a wall.
- Reached an edge.
- Changed foothold.
- Recovered from an invalid/out-of-bounds state.

### 6.3 Fixed-step parity

Retain the WASM 8 ms internal step initially. Run the channel service less frequently and consume fixed substeps from an accumulator.

```java
session.addElapsed(elapsedMs);

int steps = 0;
while (session.accumulatorMs() >= 8 && steps < MAX_CATCH_UP_STEPS) {
    simulator.stepEightMilliseconds(session);
    session.consumeAccumulator(8);
    steps++;
}
```

Reference constants:

| Constant | Value |
|---|---:|
| Gravity | `0.14` |
| Swimming gravity | `0.03` |
| Ground friction | `0.30` |
| Slope factor | `0.10` |
| Ground slip | `3.00` |
| Flying friction | `0.05` |
| Swimming friction | `0.08` |

Preserve update order:

1. Resolve current foothold.
2. Calculate force and acceleration.
3. Update velocity.
4. Predict next position.
5. Resolve walls, edges, bounds, and landing.
6. Apply final position.
7. Return collision/state events.

Cap catch-up, for example at 250 ms, to prevent a scheduler stall from causing a simulation spiral.

## 7. Server XML and terrain cache

No NX files or new asset pipeline are required.

`MapFactory` already reads the following Map WZ XML values into runtime footholds:

- `x1`, `y1`, `x2`, `y2`
- `prev`, `next`
- `forbidFallDown`
- layer
- z-mass

The physics engine must consume these runtime objects and never parse XML during a tick.

### 7.1 Physics-friendly index

Build an immutable terrain adapter once when a map is constructed:

```java
public interface PhysicsTerrain {
    FootholdSegment foothold(int id);
    FootholdSegment findBelow(double x, double y);
    FootholdSegment wallBetween(double x1, double y1, double x2, double y2);
    double groundY(FootholdSegment foothold, double x);
    PhysicsBounds bounds();
}
```

The implementation should provide:

- O(1) foothold-by-ID lookup.
- Previous/next lookup through IDs.
- Existing quadtree or spatial lookup for `findBelow`.
- Existing wall lookup where suitable.
- Accurate double-precision `groundYAt(x)`.
- Cached map bounds.

Do not reproduce the C++ per-pixel X multimap. It can waste memory on large maps. The current quadtree plus an ID map is a better fit.

Do not use the current integer-slope `Foothold.calculateFooting()` for physics. Use double interpolation:

```java
double ratio = (x - x1) / (double) (x2 - x1);
double y = y1 + ratio * (y2 - y1);
```

## 8. Mob WZ physics profile

Cosmic already loads `Mob.wz/*.img.xml` through `LifeFactory`. Extend `MonsterStats` or build a cached immutable profile with:

```java
public record MobPhysicsProfile(
        double walkingForce,
        double flyingForce,
        int pushed,
        boolean mobile,
        boolean canJump,
        boolean flying,
        boolean fixed) {
}
```

Read:

- `info/speed`
- `info/flySpeed`
- `info/pushed`
- presence of `move`
- presence of `jump`
- presence of `fly`
- fixed stance

Reference conversions:

```java
walkingForce = (rawSpeed + 100) * 0.001;
flyingForce = (rawFlySpeed + 100) * 0.0005;
```

Reference defaults:

```text
speed = 0
flySpeed = 0
pushed = 0
```

Resolve `info/link` as a fallback for physical values and movement capability. Protect linked resolution from cycles.

The current server already derives mobile and flying status from animation-node presence. Add an explicit `canJump()` based on `jump` presence.

## 9. Mob motion states

```java
public enum MobMotionState {
    PENDING_IMPACT,
    FLINCH,
    CHASE,
    JUMPING,
    IDLE
}
```

### 9.1 Damage below `pushed`

- Acquire Agent physics ownership and aggro.
- Do not apply knockback.
- At impact time, transition into or continue `CHASE`.
- Do not invent a flinch if strict WASM parity is desired.

### 9.2 Damage at or above `pushed`

At the scheduled impact:

1. Determine direction away from the attacker.
2. Enter `FLINCH`.
3. Suspend aggro steering.
4. Apply WASM horizontal knockback force:
   - Grounded: `0.2` per 8 ms step.
   - Airborne/flying: `0.1` per 8 ms step.
5. Continue gravity, friction, slope, wall, and foothold processing.
6. Run the flinch for 31 internal steps, approximately 248 ms.
7. Transition to `CHASE`.

No `hit1` animation is required. Broadcast a standing-facing stance during flinch, then moving or jumping stance when chase resumes.

## 10. Aggro policy

Physics resolves motion; the aggro layer supplies force intent.

### 10.1 Ground mobs

- Walk left when the Agent is left of the stopping region.
- Walk right when the Agent is right of the stopping region.
- Stop inside the region.
- Face the Agent.
- Apply no chase force during flinch.
- Respect walls and platform edges.

Use hysteresis, initially:

```text
stop within 24 px
resume outside 36 px
```

Make tuning configurable or centralized, not scattered through the simulator.

### 10.2 Flying mobs

Apply horizontal and vertical force toward the Agent using independent dead zones. Use the mob's `flySpeed` profile and flying friction.

### 10.3 Jump-capable mobs

The reference engine supplies jump physics but only random jump choice. Implement a minimal deterministic policy:

- Jump only while grounded.
- Target must be forward of the mob.
- Jump when a forward wall blocks movement, a safe forward landing is detected across an edge, or the target is moderately above and forward.
- Apply reference vertical force `-5.0`.
- Retain horizontal chase force in air.
- Enforce a jump cooldown.

Do not add full navigation in the first implementation. If the Agent is on an unreachable platform, approach the nearest safe edge and stop. Never teleport as part of ordinary aggro.

## 11. Scheduling and publication

Add `MOB_PHYSICS` to `ChannelServices` and use one periodic service per channel, not one scheduled task per mob.

Suggested timing:

| Operation | Interval |
|---|---:|
| Outer service tick | 50 ms |
| Internal physics substep | 8 ms |
| Moving snapshot broadcast | 50–100 ms |
| Impact, landing, stopping, direction change | Immediate |

Only tick sessions when all conditions remain true:

- Mode is `PHYSICS`.
- Monster is alive and remains in the registered map.
- Controlling Agent remains alive and in that map.
- At least one real player observes the map.
- Authority remains `AGENT_PHYSICS`.

Update authoritative monster position every outer tick for Agent targeting and combat. Avoid running the full map visibility scan on every 8 ms substep. Refresh visibility at publication cadence or after a significant position change.

Build movement through the existing server packet overload using `LifeMovementFragment` objects. Populate endpoint, foothold, stance, facing, duration, and velocity fields consistently. Use ranged map broadcasts and do not send the movement packet to the BotClient.

## 12. Mode isolation

Replace direct synthetic invocation in `CosmicAgentPresenceProvider` with a router:

```java
switch (mode) {
    case OFF -> {
    }
    case SYNTHETIC -> synthetic.acceptedHit(...);
    case PHYSICS -> physics.acceptedHit(...);
}
```

Recommended configuration:

```yaml
AGENT_MOB_REACTION_MODE: PHYSICS
```

Keep synthetic tuning settings for `SYNTHETIC` mode. Remove or deprecate `AGENT_SYNTHETIC_MOB_REACTION_ENABLED` after migration so dual activation is impossible.

Invalid mode values must fail clearly during startup rather than silently selecting a behavior.

## 13. Concurrency and recovery

Document and enforce one lock order. A possible order is:

```text
monster ownership lock
→ simulation registry lock
→ monster state lock
```

Validate this order against existing Monster methods before implementation. Do not call broad map callbacks while holding the registry lock.

Protect against:

- Duplicate acquisition.
- Old client movement arriving after revocation.
- Delayed impact after release/reacquisition.
- Monster death during pending impact.
- Agent map transition or death.
- Last-player observation transition.
- Runtime mode mismatch.
- NaN or infinite position/velocity.
- Missing foothold.
- Out-of-bounds position.
- Excessive scheduler catch-up.
- Linked WZ cycles.
- Rapid repeated hits.
- Multiple Agents attacking one monster.

If state becomes invalid, prefer a safe release to normal client control over continuing corrupt physics.

## 14. Diagnostics and performance

Expose counters or debug diagnostics for:

- Active sessions.
- Acquisition and release counts by reason.
- Physics steps.
- Broadcasts.
- Catch-up steps and capped catch-up events.
- Missing foothold/recovery events.
- Invalid-state releases.
- Average and maximum service tick duration.

Avoid per-substep logging. Use aggregated metrics and debug logging only on important state transitions.

Performance goals:

- No XML or NX access during ticks.
- No enumeration of all map footholds during ticks.
- No scheduled task per monster.
- Minimal allocations per 8 ms substep.
- Reasonable operation with at least 100 active simulated mobs in a channel stress test.

## 15. Testing strategy

### 15.1 Physics parity

Use synthetic footholds and deterministic initial state to test:

- Ground acceleration.
- Friction and stopping.
- Gravity and landing.
- Uphill and downhill travel.
- Wall collision.
- Platform edge behavior.
- Previous/next foothold transition.
- Ground knockback for approximately 248 ms.
- Airborne/flying knockback.
- Flying acceleration and deceleration.
- Jump trajectory.
- Fixed body behavior.
- Bounded scheduler catch-up.

Generate golden trajectories from the pinned C++ implementation where practical.

### 15.2 WZ profile

Verify with representative local XML entries:

- Ground mob `speed`.
- Flying mob `flySpeed`.
- `pushed` threshold.
- Jump animation presence.
- Immobile mob.
- Linked mob fallback.
- Missing values and defaults.

### 15.3 Ownership and lifecycle

Test:

- Player controls before an Agent hit.
- Accepted hit acquires Agent physics authority.
- Damage below `pushed` acquires without knockback.
- Damage meeting `pushed` flinches, knocks back, then chases.
- Old real-client `MOVE_LIFE` is ignored.
- Repeated hit reuses the session.
- Different Agent retargets the session.
- Controlling Agent leaves and player reacquires final state.
- Last player leaves and simulation stops.
- Monster dies or despawns during pending impact.
- No acquisition in an Agent-only map.

### 15.4 Mode isolation

- `OFF` invokes neither strategy.
- `SYNTHETIC` invokes only synthetic.
- `PHYSICS` invokes only physics.
- Invalid configuration fails clearly.
- Existing synthetic tests remain green.

### 15.5 Performance

Measure 100 active mobs at 20 outer ticks per second and a larger stress scenario. Record allocations, terrain lookup cost, service duration, and broadcast count.

## 16. Implementation phases

### Phase 1: Reference baseline

- Pin and document the C++ revision.
- Preserve source attribution.
- Capture reference trajectories for representative physics cases.
- Record current synthetic and client-control behavior.

Exit gate: reference inputs, outputs, and source provenance are reproducible.

### Phase 2: Pure physics kernel

- Implement body, modes, inputs, results, constants, and integrator.
- Port normal, flying, swimming, and fixed behavior.
- Add deterministic unit tests.

Exit gate: pure Java trajectories match reference behavior within defined tolerances.

### Phase 3: Foothold adapter and cache

- Build immutable runtime terrain from existing XML-loaded footholds.
- Add ID lookup, accurate slopes, wall/edge/landing behavior, and bounds.
- Prohibit XML access and full foothold enumeration during ticks.

Exit gate: terrain collision tests pass on flat, slope, wall, edge, and landing fixtures.

### Phase 4: Mob profile loading

- Load speed, fly speed, pushed, jump, fly, mobile, and fixed capability.
- Add linked-mob fallback and cycle protection.
- Cache immutable profiles.

Exit gate: representative WZ profile tests pass.

### Phase 5: Standalone mob simulation

- Implement simulation state/session/registry without controller or packets.
- Implement flinch and knockback transitions.
- Implement ground and flying bodies.

Exit gate: a test monster produces deterministic authoritative positions through flinch and movement.

### Phase 6: Mode router

- Add OFF/SYNTHETIC/PHYSICS enum and parser.
- Route accepted hits through one exclusive strategy.
- Preserve synthetic behavior unchanged.

Exit gate: mode-isolation tests pass.

### Phase 7: Ownership integration

- Add explicit authority state.
- Implement atomic real-client-to-BotClient acquisition.
- Reject late real-client movement.
- Implement all release/handoff paths.

Exit gate: lifecycle and race-oriented ownership tests pass.

### Phase 8: Aggro and jumping

- Add grounded and flying chase.
- Add stopping hysteresis.
- Add minimal deterministic jump policy and cooldown.
- Keep pathfinding out of scope.

Exit gate: mobs chase without jitter, unsafe falls, or teleportation on representative maps.

### Phase 9: Network publication

- Generate standard movement fragments.
- Broadcast at bounded cadence and on significant transitions.
- Keep authoritative server position current.
- Verify handoff begins from the final simulated state.

Exit gate: real clients observe smooth movement and regain control without a large correction.

### Phase 10: Hardening and performance

- Add catch-up limits, state validation, cleanup, diagnostics, and stress tests.
- Run focused tests and the complete Maven suite.

Exit gate: no session leaks, no dual strategy execution, acceptable service timing, and all tests pass.

## 17. Acceptance criteria

The project is complete when:

1. `OFF`, `SYNTHETIC`, and `PHYSICS` are mutually exclusive and tested.
2. Existing synthetic behavior remains available and unchanged in `SYNTHETIC` mode.
3. No NX files are required; existing server WZ XML supplies terrain and mob profiles.
4. A real client controls mobs normally until an Agent lands an accepted hit.
5. That hit atomically transfers logical control to the Agent physics path.
6. Damage below `pushed` causes aggro without physical knockback.
7. Qualifying damage produces a brief flinch and terrain-aware knockback, followed by chase.
8. Ground movement uses mob speed, slopes, friction, walls, edges, and footholds.
9. Flying movement uses mob fly speed and two-axis steering.
10. Jump-capable mobs can perform safe heuristic jumps without full pathfinding.
11. Server position is authoritative during physics ownership.
12. Real clients receive valid, bounded-cadence monster movement broadcasts.
13. Agent departure or observation loss safely stops simulation and returns or clears control.
14. Late packets and delayed impacts cannot corrupt a newer ownership generation.
15. The core engine remains entity-independent and suitable for a future pet adapter.
16. Focused tests and `./mvnw test` pass.

## 18. Copyable implementation goal prompt

```text
Implement an AGPL-compatible Java port of the maplestory-wasm/Journey physics
kernel in the Cosmic Agents repository.

Create a reusable server.physics engine and initially use it only to simulate
monsters when an Agent BotClient acquires a monster after an accepted hit in a
map with at least one real human observer. The BotClient is the logical
controller but must not receive or parse packets. The server physics service
advances authoritative monster state and broadcasts normal MOVE_MONSTER
packets to real clients.

Implement mutually exclusive OFF, SYNTHETIC, and PHYSICS reaction modes through
one router. Preserve the existing synthetic implementation unchanged and never
run synthetic and physics for the same hit.

Port the physics behavior from the pinned maplestory-wasm Physics.cpp,
PhysicsObject.h, Footholdtree.cpp, Foothold.cpp, and relevant Mob.cpp movement
sections. Preserve AGPL attribution. Do not port rendering, audio, animations,
NX wrappers, UI, packet parsing, or client damage calculation.

The server.physics package must not import Monster, Character, BotClient,
packets, or Agent packages. Add a mob adapter under server.life.simulation,
Agent acquisition/aggro/knockback under server.agents.capabilities.mobcontrol,
and one channel-level MobPhysicsService rather than one task per mob.

Use Cosmic's existing extracted WZ XML. Do not require NX files. Build an
immutable physics terrain from MapFactory's runtime footholds with O(1)
foothold-by-ID lookup, accurate double slopes, wall/edge/landing queries, and
cached bounds. Do not parse XML or enumerate all footholds during a tick.

Load and cache info/speed, info/flySpeed, info/pushed, move/jump/fly presence,
and fixed stance from existing Mob XML, including info/link fallback with cycle
protection. Match the WASM force conversions:
walkingForce = (speed + 100) * 0.001
flyingForce = (flySpeed + 100) * 0.0005

Preserve the 8 ms internal physics step and original constants/order. Run an
outer channel service around every 50 ms using a bounded fixed-step
accumulator. Port NORMAL, FLYING, SWIMMING, and FIXED behavior. Represent ICE
but leave it unsupported because the reference implementation is TODO.

Ownership is per monster:
- Agents only: no simulation.
- Players only: normal real-client control.
- Mixed map before Agent hit: normal real-client control.
- Mixed map after accepted Agent hit: BotClient logical controller and server
  physics authority.
- Agent leaves/dies/disconnects: commit final state and return control to a
  real client.
- Last real observer leaves: stop simulation and clear Agent ownership.
- Mob death/despawn/map change: remove the session.

Add an explicit CLIENT/AGENT_PHYSICS/NONE authority instead of relying only on
Monster.controller. Keep BotClient.sendPacket() as a no-op. Acquire authority
atomically, revoke the old client controller, reject its late MOVE_LIFE
packets, and use generation tokens for delayed impact and release safety.

Every accepted positive Agent hit may acquire aggro authority. At impact time:
- If damage is below info/pushed, do not knock back; begin or continue chase.
- If damage meets info/pushed, enter FLINCH, suppress chase, apply WASM
  horizontal knockback force (0.2 grounded, 0.1 airborne/flying) for 31
  internal steps/about 248 ms, then enter CHASE.

Do not require hit1 animation. Publish a standing-facing stance while the mob
briefly flinches/slides and a move/jump stance when chase resumes.

Ground mobs steer horizontally toward the Agent using WZ speed and stop/resume
hysteresis to prevent oscillation. Flying mobs steer on both axes using WZ
flySpeed and dead zones. Jump-capable ground mobs may jump only when grounded
and blocked by a wall/edge or when the target is moderately above and forward;
use reference jump force -5.0 and a cooldown. Do not implement full pathfinding
and never teleport for normal aggro.

Update authoritative monster position every outer tick. Broadcast movement at
a bounded 50-100 ms cadence while moving and immediately on impact, landing,
stopping, or direction change. Use existing PacketCreator movement overloads
and LifeMovementFragment classes. Do not send movement to BotClient and do not
run full visibility scans on every 8 ms substep.

Add deterministic parity tests for ground, friction, slope, gravity, landing,
wall, edge, foothold transition, flying, jump, and knockback. Add WZ profile,
ownership, lifecycle, repeated-hit, multiple-Agent, mode-isolation, packet,
and performance tests. Preserve existing synthetic tests. Finish by running
focused tests and ./mvnw test.

Do not migrate existing Agent character movement and do not implement pets in
this change. Keep the physics kernel general enough for a later pet adapter.
```
