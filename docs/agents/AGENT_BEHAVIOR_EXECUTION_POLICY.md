# Agent behavior execution policy

## Purpose

This layer makes independently running Agents look different without allowing presentation or personality code to own objectives. It is initially enabled only for Maple Island cohort entries using `full` realism mode.

## Responsibility boundaries

1. **Personality identity** owns durable semantic traits and the durable behavior seed.
2. **Behavior policy** translates a personality profile into tunable response, targeting, crowd, idle, reaction, and navigation tendencies.
3. **Map perception** exposes immutable mobs, drops, real-player observers, and all live Agent peers in the same map instance.
4. **Combat** still owns legal targets, reachability, damage, cooldowns, target commitment, and objective-required mobs.
5. **Map activity policy** ranks eligible same-map Agents and grants a rotating share of active combat slots. It does not assign objectives.
6. **Presentation** owns cosmetic fidgets, weapon flourishes, chairs, and facial expressions. These never deal damage.
7. **Plans** own objective identity and progress. A respite pauses the logical plan clock; it does not replace, complete, or re-create an objective.
8. **Navigation** owns route feasibility. Behavior can request a bounded alternate route or a safe destination but cannot fabricate an edge.
9. **Adaptation** owns bounded session-only energy, confidence, frustration, rest debt, and miss streak. These modify policy decisions but never overwrite personality.
10. **Telemetry** exposes fixed-cardinality rollout counters through `!mapleisland stats`.

`cohort` remains a lifecycle/relationship grouping. It does not mean every Agent in a mass test is in one party. Target claims and crowd pressure are deliberately map-wide so singleton self-directed Agents can coordinate indirectly without fake party membership.

## Dynamic pause and resume

`AgentPlanPauseState` supports multiple simultaneous pause reasons. The first reason freezes one logical clock window; the last reason resumed closes it. `AgentAmherstPlanRuntime` supplies the resulting effective time to Maple Island, first-job, Victoria training, Southperry, town-life, and active capability ticks.

Crowd respite uses the reason `map-crowd-respite`. The execution gate runs before the seated-character short circuit, which lets an Agent later stand, release the reason, and continue the same objective. Objective state, journal identity, quest state, and navigation goal are not discarded. Maintenance suspensions remain separate and can overlap safely.

## Policy catalog

Behavior profiles are in `src/main/resources/agents/profiles/personality-behavior-policies.json` and are keyed to the existing durable personality IDs:

- `efficient-v1`: fast response, low contention, strong platform anchoring.
- `relaxed-v1`: slow response, high crowd avoidance, longer rests and more chair use.
- `restless-v1`: fastest response, high contention tolerance, frequent active fidgets.
- `explorer-v1`: moderate response, stronger middle-target and alternate-route preference.

The durable seed chooses a stable response baseline and anchor tendency. Decision sequences add bounded variation while remaining replayable for a newly restored session with the same seed.

## Generic behavior versus personality variation

Generic combat remains the minimum viable behavior: filter objective targets, reject unreachable targets, score distance/platform/path cost, attack, loot, and recover. Personality can vary only choices already allowed by generic policy:

- wait before acquiring a newly visible target;
- reject a target with too many map-wide claimants when another legal target exists;
- select best, near-best, or middle-ranked candidates;
- retain a target-bearing platform anchor;
- take bounded alternate routes and occasional travel hops;
- wait, patrol, hop, prone-fidget, or swing harmlessly when no target exists;
- take a rotating safe-spot respite under crowd pressure;
- show a rare observer-gated expression after repeated misses.

Survival, supplies, quest correctness, explicit commands, death recovery, and a committed legal attack are never delayed by the response gate.

## Runtime adaptation

Do not add one unbounded `aggression` value. Runtime decisions use bounded orthogonal signals:

- **energy** falls slowly during attacks and rises after rest;
- **confidence** rises after hits/kills and falls after misses/target loss;
- **frustration** rises after misses/target loss and falls after success/rest;
- **rest debt** rises with activity and falls after respite;
- **miss streak** drives rare F4/F5 reactions.

The derived combat drive can add one claim-tolerance slot. It cannot bypass objective target filters or reachability.

## Configuration and rollback

The master switch is `AGENT_COMBAT_BEHAVIOR_ENABLED`. Independent rollout switches cover response latency, map crowd respite, target claims, target-rank variation, platform anchoring, navigation variation, idle presentation, and combat emotes. Crowd active percentage, minimum population, and expression map budget are global safety limits. Profile-level weights remain in the JSON catalog.

Turning the master switch off restores the previous full-mode target variation and anchor settings. Turning an individual switch off restores only that generic subsystem's prior behavior.

## Uptime constraints

- Per-Agent live state is stored in the capability-state registry and dies with the session.
- Map-wide perception cache keys map instances, not map IDs, so separate instances never share peers; stale entries are pruned after the cache exceeds its bound.
- Target claims and region occupancy consume that shared 50 ms snapshot instead of rescanning every live Agent per target decision; map activity rankings are likewise shared for a 500 ms decision window.
- Telemetry uses fixed-cardinality counters and never creates labels for Agent IDs, map IDs, or monster IDs.
- Expression budgets are per map ID and bounded by one tiny window state. They contain no Agent references.
- Safe spots are limited to the Agent's current navigation connected component and avoid rope regions.

## Verification

Focused unit coverage verifies policy catalog coverage, durable-seed replay, overlapping pause reasons, pre-chair execution-gate ordering, and compatibility with existing combat variation. Run:

```powershell
.\mvnw.cmd "-Dtest=AgentBehaviorPolicyRepositoryTest,AgentBehaviorCalibrationStateTest,AgentPlanPauseStateTest,AgentLiveTickGateServiceTest,AgentCombatVariationRuntimeTest" test
```

For a live rollout, run a full-realism Maple Island cohort, inspect `!mapleisland stats`, then verify that disabling each YAML switch independently restores the corresponding prior behavior without resetting active plans.
