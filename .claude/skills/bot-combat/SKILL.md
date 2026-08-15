---
name: agent-combat
description: Use when implementing, debugging, or auditing Agent combat behavior, attack planning/execution, packet visibility, hitboxes, ammo and buff gates, target selection, ranged spacing, or mob-reaction routing in server.agents.*.
---

# Agent Combat

Current project guide for the reconstructed Agent combat pipeline. Production
`server.bots.*` no longer exists; historical Bot names may remain in parity-test
names or archived documentation only.

## Boundary rules

1. Shared player damage handlers remain authoritative for legality and damage.
2. Agent code owns client-side attack planning: action/stance, hitbox choice,
   target ordering, projectile cosmetic data, and packet route selection.
3. Tactical policy may choose among legal actions but must never widen hitboxes,
   bypass cooldown/ammo/MP gates, or alter authoritative damage.
4. Cosmic `Character`, `MapleMap`, packet, and handler access belongs in runtime
   adapters or execution gateways. Pure policy consumes immutable snapshots.
5. Every committed attack returns `AgentAttackTransactionResult`; callers must
   not infer success from cooldown or animation side effects.

## Current file map

| Concern | Current owner |
| --- | --- |
| Attack-plan construction | `server/agents/capabilities/combat/AgentCombatPlanRuntime.java` and the basic/skill planners |
| Server-authoritative transaction | `server/agents/capabilities/combat/AgentCombatAttackRuntime.java` |
| Route/packet execution bridge | `server/agents/capabilities/combat/AgentAttackExecutionProvider.java` |
| Transaction status/evidence | `server/agents/capabilities/combat/AgentAttackTransactionResult.java` |
| Attack animation/data | `server/agents/capabilities/combat/data/AgentAttackDataProvider.java` and `AgentAttackTiming.java` |
| Hitbox and target legality | `AgentCombatSkillHitboxPolicy`, `AgentProjectileHitbox`, and target eligibility policies |
| Grind target choice | `AgentCombatTargetRuntime` plus objective/locality/commitment policies |
| Grind orchestration | `AgentGrindModeCoordinator` and `AgentGrindModeTickService` |
| Ranged spacing/jump shots | `AgentGrindRangedEngagementService` and `AgentRangedKitingPolicy` |
| Mob reactions | `server/agents/capabilities/mobcontrol` (`PHYSICS` is the production default) |
| Cosmic gateway audit | `src/test/java/server/agents/reconstruction/AgentCosmicBoundaryAuditTest.java` |

## Execution pipeline

```text
AgentGrindModeTickService
  -> target/objective policy
  -> AgentCombatPlanRuntime
  -> AgentAttackPlan
  -> AgentCombatAttackRuntime.attackMonster
  -> validate cooldown, resources, target/map, and route
  -> AgentAttackExecutionProvider.applyAttackRoute
  -> shared damage handler and observer packets
  -> AgentAttackTransactionResult
  -> progress/telemetry consumers
```

The packet families remain:

- close-range attack: `0xBA`
- ranged attack: `0xBB`
- magic attack: `0xBC`

Do not select a packet family from weapon type alone. Skills can deliberately
use a different route, and close-range fallbacks for projectile weapons need a
valid swing action so watching clients can render them safely.

## Debugging checklist

1. Capture one immutable perception snapshot for the decision.
2. Confirm the target is alive, in the same map, objective-eligible, and inside
   the plan's authoritative hitbox.
3. Inspect the selected `AgentAttackRoute`, skill ID, action, stance, projectile,
   target count, and damage-line count.
4. Inspect `AgentAttackTransactionResult.status` and `.reason`; do not use a
   cooldown delta as an attack-success signal.
5. Distinguish a committed hit from client presentation. Missing animation or
   damage numbers with server knockback usually indicates observer-packet
   geometry/order or a route/action mismatch, not failed damage application.
6. For ranged stalls, inspect spacing intent, firing-position reachability,
   maneuver commitment, and whether the next tick discarded the prior intent.
7. For target wandering, inspect target-search mode, objective tier, route cost,
   platform batch, local lease, recent failed maps/edges, and decision trace.
8. For mob motion, verify `AgentMobReactionMode.PHYSICS` and inspect the mob
   physics service before changing combat hit logic.

## Change checklist

When adding or changing an attack skill:

1. Classify the correct attack route from real-client behavior.
2. Resolve WZ action, hit count, mob count, MP cost, and range data.
3. Add only the smallest skill-specific override required by evidence.
4. Keep mechanics in the planner/executor and preferences in policy.
5. Add planner, transaction, packet-shape, and observer-visibility tests.
6. Run combat gateway and Cosmic boundary tests.

When changing target or ranged behavior:

1. Add a pure policy test for the decision.
2. Add an orchestration test proving the returned intent is executed once.
3. Preserve objective admissibility and complete-route validation.
4. Emit a typed decision reason and progress evidence.
5. Verify melee, bow, crossbow, claw, gun, and magician compatibility where the
   policy is shared.

## Focused tests

```powershell
.\mvnw.cmd -q "-Dtest=BotCombatManagerTest,AgentCombatAttackRuntimeTest,AgentGrindModeTickServiceTest,AgentGrindRangedEngagementServiceTest,AgentCombatGatewayBoundaryTest,AgentCosmicBoundaryAuditTest" test
```

`BotCombatManagerTest` is a historical parity-test name under
`src/test/java/server/agents/integration`; it does not imply a live
`server.bots.BotCombatManager` production class.
