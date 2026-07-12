# Agent Mob Reaction and Last-Hit Aggro

## Status

- Feature branch: `feature/agent-mob-reaction-aggro`
- Master base: `28555684e8d867793251e646d421fc30498ad74e`
- Both runtime features are disabled by default.
- Automated coverage validates gating, controller selection, WZ threshold use,
  logical-target transfer/expiry, and the absence of a second damage call.
- Multiplayer packet parity and visible Agent pursuit remain live-validation
  gates before either flag should be enabled in production.

## Intent

An observed Agent attack should use the same v83 client path as a foreign
player attack. A connected real client is granted mob control before the
existing Agent attack packet is broadcast. That client replays the foreign
attack, displays its normal damage and hit effects, applies the WZ `pushed`
threshold, and emits the ordinary `MOVE_MONSTER` result for eligible
knockback. Agent-only maps keep the existing lightweight path.

The implementation deliberately does not create an animation-only damage
attack. Damage, death, drops, EXP, quests, and ownership continue through the
existing Cosmic handler exactly once.

## Call Chains

Player attack:

```text
attack packet -> melee/ranged/magic handler -> broadcast attack
  -> AbstractDealDamageHandler.applyAttack
  -> Monster.aggroMonsterDamage -> MapleMap.damageMonster
```

Agent attack:

```text
AgentCombatAttackRuntime builds the ordinary AttackInfo
  -> AgentMobHitReactionService
  -> CosmicMobReactionGateway prepares a real simulation controller
  -> existing melee/ranged/magic handler broadcasts the attack once
  -> AbstractDealDamageHandler.applyAttack
  -> Monster.aggroMonsterDamage -> MapleMap.damageMonster once
```

The preparation step only selects/control-signals a client. It never calls
`MapleMap.damageMonster` and never creates an extra attack packet.

## Observer Gate

`MonsterSimulationControllerResolver` centralizes observer eligibility. Full
response requires `MapleMap.isObservedByPlayer()` and at least one character
that:

- is in the same map;
- is logged into the world;
- is not transitioning maps;
- is not an Agent;
- does not use `BotClient`.

Hidden GMs remain eligible. A current eligible controller is retained;
otherwise the resolver chooses the least-loaded real client, then the nearest,
then the lowest character ID for deterministic tie-breaking.

## Knockback Semantics

`LifeFactory` loads `Mob/<id>.img/info/pushed` into `MonsterStats.pushed`, and
`MonsterStats.copy()` preserves it. The controlling v83 client applies its
normal per-line predicate:

```text
mob controlled && mob alive && damage line >= pushed
```

The attack packet already contains per-target delay and all damage lines, so
the client preserves projectile, multi-hit, and multi-target impact timing.
No delayed server callback is introduced, which also removes the risk of a
callback affecting a replacement spawn that reused an OID. Immobile mobs still
receive the foreign attack/hurt presentation but are not counted as prepared
for displacement. Skill-specific movement such as Rush remains on its existing
path and is not applied a second time here.

## Controller and Logical Target

These are intentionally separate:

- **Simulation controller:** a real client that can emit valid mob movement.
- **Logical aggro target:** the latest valid damaging attacker; it may be a
  player or Agent.

`MonsterAggroTargetService` stores weak references and invalidates a target
when it dies, disconnects, transitions, changes map, or exceeds
`targetTimeoutMs`. Monster death/disposal clears the record explicitly.
Damage accounting in `MonsterAggroCoordinator` still runs before the new
last-hit policy, preserving analytics and rewards while preventing the older
highest-DPS controller choice from replacing a newer valid last hitter.

### v83 Protocol Limitation

The inspected v83 protocol has controller mode (`SPAWN_MONSTER_CONTROL`, mode
1/2) but no explicit per-player aggro-target ID in `MOVE_LIFE`. Controller mode
is enough for foreign-attack hurt/knockback replay. It does not by itself prove
that an unmodified client will visibly pursue a headless Agent different from
the controller.

Therefore `lastHitAggro` records and diagnoses the correct logical target, but
production acceptance requires the live two-client test below. If the retail
client follows only its controller, the follow-up must be a separately tested,
narrow server steering/proxy adapter. Assigning a headless Agent as controller
is explicitly forbidden because it cannot emit `MOVE_LIFE`.

## Configuration

```yaml
agents:
    combat:
        observedMobReaction:
            enabled: false
        lastHitAggro:
            enabled: false
            targetTimeoutMs: 10000
```

Startup logs warn independently when either experimental feature is enabled.

## Diagnostics

- `!agentmobdebug` shows flags, timeout, accepted Agent hits, hurt preparation,
  threshold matches, prepared/suppressed knockback, no-observer skips, target
  changes, stale targets, controller failures, and duplicate-damage guards.
- `!mobaggro <mob oid>` shows HP, WZ `pushed`, observer state, logical target,
  real simulation controller, latest damage, and latest reaction result.

The mob OID can be obtained with the existing map/debug commands.

## Automated Coverage

Focused tests cover:

- enabled/observer policy combinations;
- visible and hidden real observers;
- exclusion of headless Agents and transitioning clients;
- below, equal, and above `pushed` damage;
- immobile mobs;
- Agent/player/Agent last-hit transfer;
- dead, disconnected, transitioning, moved, timed-out, and explicitly cleared
  targets;
- controller preparation without any `MapleMap.damageMonster` call;
- copied `MonsterStats.pushed` values.

Existing attack-handler tests remain responsible for the single broadcast,
damage, death, drop, EXP, and quest path. Run the full Maven suite before merge.

## Live Validation

Use one Agent and two real clients on the same channel. Keep server logs and a
packet capture if available.

1. Leave both flags off and record a real player hitting the chosen mobile mob.
2. Enable both flags, restart, and run `!agentmobdebug`.
3. Put both clients and the Agent in one map. Confirm the Agent is not listed as
   the mob controller with `!mobaggro <oid>`.
4. Make an Agent hit below `pushed`. Both clients must show one damage result;
   no displacement should occur.
5. Make an Agent hit equal to and then above `pushed`. Both clients must show
   the normal hurt stance and converge on the same resulting position.
6. Run `!mobaggro <oid>` and confirm target=Agent while controller=real client.
7. Confirm whether the mob visibly pursues/attacks that Agent. This is the
   protocol acceptance gate described above.
8. Hit with player A and confirm target/controller transfer to player A. Hit
   with the Agent again and confirm target transfer back without assigning the
   Agent as controller.
9. Hide the GM, remove the other visible client, and repeat. The hidden GM alone
   must keep full reaction active.
10. Remove every real client. Agent combat must continue without controller
    preparation or extra packets; `noObserver` must increase.
11. Re-enter and verify valid mob position/state. Repeat with melee, ranged,
    magic, multi-hit, AoE, boss/high-`pushed`, immobile, and flying mobs.
12. Confirm one death, one drop set, one EXP award, and one quest update for a
    kill. Compare HP before/after to ensure damage was applied once.

Do not enable the flags by default until steps 4-12 pass with the actual client
used by the server.
