# Agent Mob Reaction and Last-Hit Aggro

## Status

- Feature branch: `feature/agent-mob-reaction-aggro`
- Master base: `28555684e8d867793251e646d421fc30498ad74e`
- Both runtime features are disabled by default.
- Automated tests cover observer gating, native impact preparation, WZ
  knockback rules, latest-attacker transfer, server pursuit, lifecycle cleanup,
  stale-spawn rejection, and the absence of a second damage route.
- Actual v83 client parity remains a live-validation gate. Do not enable these
  flags by default until the two-client procedure below passes.

## Intent

When a real client can render the map, an Agent attack should produce the same
visible hurt and knockback response as a foreign player attack. The mob should
then pursue the latest valid attacker, including a headless Agent. When no real
client observes the map, Agent combat keeps its existing lightweight behavior.

The feature never creates an animation-only damage attack. Damage, death,
drops, EXP, quests, and ownership continue through Cosmic's existing combat
handler exactly once.

## Call Chains

Player attack:

```text
client attack packet -> melee/ranged/magic handler -> broadcast attack
  -> AbstractDealDamageHandler.applyAttack
  -> Monster.aggroMonsterDamage -> MapleMap.damageMonster
```

Agent attack:

```text
AgentCombatAttackRuntime builds the ordinary AttackInfo
  -> AgentMobHitReactionService
  -> CosmicMobReactionGateway prepares a real simulation controller
  -> existing Agent attack route broadcasts the attack once
  -> AbstractDealDamageHandler.applyAttack
  -> Monster.aggroMonsterDamage -> MapleMap.damageMonster once
  -> MonsterAggroTargetService records the accepted latest attacker
  -> CosmicMonsterPursuitRuntime maintains visible pursuit when needed
```

The preparation and pursuit paths never call `MapleMap.damageMonster`, kill a
mob, create drops, grant EXP, or update quests.

## Observer Gate

`MonsterSimulationControllerResolver` centralizes observer eligibility. Full
response requires `MapleMap.isObservedByPlayer()` and at least one character
that:

- is in the same map and logged into the world;
- is not transitioning maps;
- is not an Agent and does not use `BotClient`;
- has a real client connection capable of rendering monster packets.

Hidden GMs remain eligible. A current eligible controller is retained;
otherwise the resolver chooses deterministically by controller load, distance,
and character ID. A headless Agent is never selected as network controller.

## Knockback and Timing

`LifeFactory` loads the authoritative WZ `info/pushed` and `info/speed` values
into `MonsterStats`. `MonsterStats.copy()` preserves them. The attack packet
contains every damage line and the per-target impact delay, so the real v83
controller receives the foreign attack before the server proxy can take over.

Knockback preparation requires at least one positive line where:

```text
damage line >= MonsterStats.pushed
```

The monster must also be mobile and not have fixed stance. Normal client
handling remains responsible for the immediate hurt/flinch and native
knockback command. `MoveLifeHandler` records the accepted first movement
command; command 2 confirms native knockback for diagnostics. Rush, Power
Knockback, and other skill movement are not synthesized a second time.

Prepared reactions retain the exact Monster object, map, attacker, and target
delay. A replacement spawn that reuses the same OID cannot consume stale state.

## Controller and Logical Target

These are separate concepts:

- **Simulation controller:** a connected real player client that can supply
  valid v83 monster movement.
- **Logical aggro target:** the most recent accepted damaging attacker. It may
  be a player or an Agent.

`MonsterAggroCoordinator` still records damage for analytics and rewards.
While the feature is active, its older highest-DPS controller selection does
not override the newer last hitter.

### Selected v83 Strategy

The v83 `MOVE_LIFE` and monster-control packets do not carry a separate target
character ID. A real client naturally simulates a mob relative to its own
character, so it cannot be instructed to chase a different headless Agent.

The implementation therefore uses two phases:

1. A real observer controls the mob through the Agent attack's impact frame.
   This preserves native hurt/flinch and eligible knockback behavior.
2. After the target hit delay plus a 300 ms settle window, only an Agent-target
   mob releases that client controller and enters narrow server-side pursuit.

The centralized pursuit task runs every 120 ms only while targets exist. It:

- sends the existing v83 `MOVE_MONSTER` shape through the Cosmic integration
  layer and then updates the authoritative map position;
- uses WZ speed, map bounds, foothold interpolation, and wall checks for ground
  mobs;
- uses bounded two-axis movement for flying mobs;
- does not move fixed or immobile mobs;
- emits one shared map broadcast, regardless of observer count;
- pauses native controller reassignment while server pursuit owns movement;
- restores normal player control when a player becomes the latest hitter or
  the Agent target expires.

This proxy currently provides pursuit, not synthetic monster attack decisions.
Disconnected-platform routes that require full navigation are marked
unreachable and expire after `targetTimeoutMs`; they are not teleported across
invalid geometry.

## Target Lifecycle

`MonsterAggroTargetService` keys state by the exact weak Monster identity, not
OID alone. It clears or replaces a target when the monster or target:

- dies or is disposed;
- disconnects or leaves the world;
- changes map or begins a map transition;
- is removed from Agent ownership;
- is replaced by another spawn with the same OID;
- remains geometrically unreachable beyond the configured timeout;
- loses all real map observers or the feature is disabled.

Expired Agent pursuit restores an eligible native client controller. Monster
death and disposal also clear prepared and active state directly.

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

- `!agentmobdebug` shows flags, timeout, accepted Agent hits, hurt reactions,
  threshold matches, prepared/applied/suppressed knockback, no-observer skips,
  target changes, stale state, controller failures, duplicate-damage guards,
  pursuit moves, unreachable targets, and observer losses.
- `!mobaggro <mob oid>` shows HP, WZ `pushed`, observer state, target and target
  type, real controller, active simulator, latest damage/reaction, and latest
  movement result.

## Automated Coverage

Focused tests cover:

- visible players and hidden GMs as observers;
- exclusion of Agent-only, disconnected, and transitioning controllers;
- below, equal, and above WZ `pushed` damage;
- immobile/fixed mobs and ground/flying pursuit;
- Agent -> player -> Agent last-hit transfer;
- dead, disconnected, transitioning, map-changed, removed-Agent, unreachable,
  observer-loss, and explicitly cleared targets;
- delayed pursuit and replacement-spawn rejection;
- one movement broadcast with multiple observers;
- one bounded scheduler registration;
- client command 2 knockback diagnostics;
- multi-hit/multi-target preparation without a second damage call;
- disabled flags preserving the legacy path.

The exact-once death/drop/reward guarantee also follows structurally from the
unchanged single `AbstractDealDamageHandler.applyAttack` ->
`MapleMap.damageMonster` route. Reaction and pursuit tests assert that neither
new path calls `damageMonster`.

## Live Validation

Use one Agent and two real clients on the same channel. Keep server logs and,
where possible, capture the real-player and Agent `MOVE_MONSTER` sequences.

1. With both flags off, record a player hitting a mobile ground mob below,
   equal to, and above its `pushed` threshold.
2. Enable both flags, restart, and run `!agentmobdebug`.
3. Put both clients and the Agent in the same map. Obtain the mob OID and run
   `!mobaggro <oid>`; the Agent must never appear as simulation controller.
4. Make the Agent hit below threshold. Both clients must see one damage result
   and normal hurt feedback without displacement.
5. Hit equal to and above threshold. Both clients must see the same native
   knockback and converge on the same mob position.
6. Confirm `knockbackApplied` increments after the controller reports command
   2, then confirm pursuit begins after the impact delay.
7. Run `!mobaggro <oid>` and confirm `targetType=agent` and
   `simulator=server-proxy` during pursuit.
8. Hit with player A. Confirm target and native controller transfer to player
   A. Hit with the Agent again and confirm transfer back without making the
   Agent a network controller.
9. Hide the GM and remove the other visible client. Repeat; the hidden GM alone
   must activate full response.
10. Remove every real client. Agent combat must remain lightweight and produce
    no pursuit movement packets. Re-enter and verify valid mob state.
11. Repeat with melee, ranged, magic, multi-hit, AoE, fixed, boss, and flying
    monsters. Include same-platform reachable pursuit and an unreachable
    disconnected platform.
12. Kill one mob and confirm one death packet, one drop set, one EXP award, one
    quest update, and exactly the expected HP reduction.

Do not enable the flags by default until all live checks pass with the actual
client used by the server.
