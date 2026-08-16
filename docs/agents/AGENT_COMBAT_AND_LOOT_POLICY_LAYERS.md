# Agent Combat and Loot Policy Layers

## Purpose

This document defines the separation between the Agent combat kernel and the policy layers that modify its choices. The separation makes each decision observable and tuneable without changing the shared Cosmic player-combat handlers or packet formats.

The policy order keeps attack, quest and inventory mutation boundaries unchanged. Optional locality and navigation-reliability layers now prevent repeated remote retargeting and reject combat destinations that do not have a complete executable route.

## Responsibility boundary

| Layer | Responsibility | Must not own |
|---|---|---|
| Combat kernel | Discover alive candidates, score reachability and distance, choose a target, build an attack plan, execute the attack | Quest progression, personality, crowd policy, loot ownership |
| Objective policy | Restrict or prefer mobs required by the active objective; permit bounded incidental clearing | Attack packets, movement physics |
| Map-pressure policy | Keep a target species spawning by clearing overrepresented incidental species | Quest mutation, inventory mutation |
| Local-target lease | Turn one map-wide objective promotion into a bounded local work lease after arrival | Pathfinding, attack execution, quest counters |
| Crowd and claims policy | Delay reactions in crowds, avoid excessive multi-Agent claims, spread target choices | Base target discovery, damage calculation |
| Personality/presentation policy | Variation, anchoring, response cadence, idle presentation and emotes | Objective correctness, packet validation |
| Route-blocker policy | Temporarily attack or evade mobs obstructing active travel | Changing the travel objective |
| Attack planner/executor | Choose legal basic or skill attacks and perform them through the normal Agent combat integration | Selecting the progression objective |
| Post-kill loot policy | Decide when combat may yield to pickup and how far the Agent may move | Item ownership, inventory rules, quest eligibility |
| Loot eligibility/pickup | Enforce ownership, age, quest/PQ rules, inventory capacity and perform pickup | Target selection or combat scoring |
| Diagnostics | Record the last decision and policy counts | Changing any decision |
| Navigation path result | Report `COMPLETE`, `PARTIAL` or `UNREACHABLE`; retain partial frontier movement for non-combat callers | Deciding which combat objective is preferred |
| Edge validation | Verify live source region, movement state and graph anchors immediately before risky-edge execution | Rebuilding graph topology or injecting recovery movement |
| Edge reliability | Retain bounded per-Agent/map failure evidence, suppression and additive route penalties | Mutating graph edges, route overlays, combat targets or objectives |
| Route-loop recovery | Detect repeated region cycles under its existing mode gate | Mechanical edge-failure accounting or broad combat decisions |

## Combat target decision flow

The grind target path executes in this order:

1. Yield while pre-exit loot collection is active.
2. Discover alive monsters inside `GRIND_SEEK_RANGE`.
3. Apply the active objective allow-list.
4. If no nearby preferred objective mob exists, permit one map-wide promotion unless a local-target lease is active.
5. Require a `COMPLETE` route to each remote combat region and reject a structurally invalid first actionable edge when strict combat-route validation is enabled; same-region targets remain valid.
6. Record the promoted destination. Arrival activates the time/kill lease; preferred local sightings reset its empty-scan counter.
7. Apply quest preference, local-clear and spawn-pressure policy to the resulting local or promoted candidate set.
8. Apply target claims and crowd competition policy.
9. Apply crowd/personality response timing.
10. Score remaining candidates using local distance, foothold, vertical travel, reachability, objective priority, occupancy, AoE density and optional per-Agent reliability costs.
11. Choose a reachable best target, with bounded personality target variation.
12. Optionally establish or retain an anchor-farming platform.
13. Record the selected objective-policy reason and the passive decision trace.

The local lease releases when its duration expires, its local-objective kill quota is consumed, or the configured number of consecutive scans finds no nearby preferred objective mob. Eligible nearby fallback mobs remain in the ordinary candidate set, so the lease itself cannot create idle time. A map or objective change resets the state.

## Navigation edge execution flow

1. Resolve the live source and target navigation regions.
2. Observe any active risky-edge attempt. Reaching its destination clears prior failure evidence; physical or region progress refreshes its timeout.
3. Reuse a committed edge only if route overlays, structural validation and per-Agent reliability suppression allow it.
4. Otherwise run A* with suppressed edges filtered out and bounded reliability penalties added to edge cost. The cached graph remains immutable.
5. Immediately before jump, drop, rope or ladder execution, validate the expected source region, compatible grounded/airborne/climbing state and launch/landing/attachment anchors.
6. Approach a valid but not-yet-ready anchor through normal movement. A structural rejection or motionless attempt timeout records one failure and invalidates only the current navigation step.
7. At the configured threshold, suppress only that exact Agent/map/edge signature. The next search retains the combat target and objective while choosing an alternative route.
8. Successful arrival clears that edge's failures. Suppression and unused failure records also expire automatically, and the bounded ledger resets on map change.

This flow adds no random movement, nudge or in-bounds recovery teleport. Route-cycle observation is diagnostic only; exact edge failures own all reliability penalties and suppression. Out-of-bounds teleport remains an independent safety invariant.

Patrol, follow-combat and route-blocker modes reuse the same base scoring helpers but have narrower candidate gates appropriate to their mode.

## Attack decision flow

1. Resolve the selected live monster and current Agent state.
2. Build the legal attack candidates from equipped weapon, learned skills, MP, range, facing and map geometry.
3. Score basic and skill attacks, including AoE cluster value.
4. Select the best legal attack plan.
5. Execute through the Agent combat integration and shared Cosmic damage validation/broadcast boundary.
6. Record the kill in post-kill loot state and emit normal Agent events.

Combat policy does not directly mutate quest counters, inventory, drops or player packet handlers.

## Loot decision flow

1. A kill records the killed map object and time in `AgentPostKillLootState`.
2. Melee and ranged post-kill policy decide whether pickup may interrupt target search:
   - melee: collect after a kill, but wait for the configured drop-settle age;
   - ranged: batch until no target remains, the kill threshold is reached, or the wait deadline expires.
3. A pickup candidate must pass ownership, quest/PQ, inventory and drop-age eligibility.
4. Melee stays near the kill and waits until the drop is old enough; it only walks when the drop is outside passive pickup radius.
5. Ranged combat may travel back for the selected batch after the batch policy releases combat.
6. Pre-exit loot gets a bounded final collection window before changing maps.
7. Pickup uses the normal Cosmic pickup boundary; successful pickup resolves drained recent-kill state and emits loot/equipment events.

## Observable decision records

`AgentCombatDecisionTraceState` records the last target-search mode and outcome plus candidate counts after each major policy seam:

- base candidates;
- objective-eligible candidates;
- candidates after objective/local-clear/spawn-pressure policy;
- candidates after claims;
- scored/reachable candidates;
- map-wide preferred escalation;
- ranked target variation;
- selected monster object and template IDs.

`AgentLootDecisionTraceState` records the last passive, melee, ranged or pre-exit loot decision:

- inhibited or trade-active;
- deferred by post-kill policy;
- waiting for the drop to settle;
- no eligible drop;
- target selected;
- pickup completed;
- inventory full or otherwise ineligible;
- required and observed drop age.

`AgentCombatPolicyDiagnostics` also exposes the local-target lease phase, destination,
expiry, remaining kills and empty-scan count, plus each retained navigation reliability
edge's failure count, additive penalty and suppression expiry.

These records are ephemeral diagnostic evidence. They do not participate in selection or mutation and are exposed through `AgentCombatPolicyDiagnostics`.

## Current tuneables

All values live in `agent-engine.yaml`. The values below are the behavior-preserving defaults at the time of this refactor.

### Targeting and objective policy

| Setting | Default | Effect |
|---|---:|---|
| `SPAWN_PRESSURE_MIN_TARGET_SHARE_PERCENT` | 80 | Begins incidental spawn-pressure clearing when required mobs are underrepresented |
| `MAX_CONSECUTIVE_INCIDENTAL_KILLS` | 3 | Limits consecutive non-objective kills before required debt regains priority |
| `MAX_INCIDENTAL_KILLS_PER_PLATFORM_LEASE` | 5 | Limits incidental clearing during one platform lease |
| `PLATFORM_LEASE_MS` | 6500 | Retains local platform focus for this duration |
| `LOCAL_TARGET_LEASE_MS` | 25000 | Time bound after reaching the promoted region |
| `LOCAL_TARGET_LEASE_KILLS` | 2 | Objective-eligible local kills before release |
| `LOCAL_TARGET_LEASE_EMPTY_SCANS` | 3 | Consecutive empty preferred-local scans before early release |

Local clearing, the local-target lease, complete remote routes and first-edge structural validation are correctness policy and are therefore always active.

### Navigation reliability

| Setting | Default | Effect |
|---|---:|---|
| `ROUTING_MODE` | ACTIVE | `OBSERVE` records failures; `ACTIVE` also applies bounded penalties and suppression |
| `FAILURE_THRESHOLD` | 3 | Failures before hard suppression |
| `SUPPRESSION_MS` | 30000 | Duration of hard suppression |
| `FAILURE_RETENTION_MS` | 60000 | Quiet lifetime of failure/penalty evidence |
| `FAILURE_PENALTY_MS` | 2000 | Additive cost per retained failure |
| `MAX_EDGE_PENALTY_MS` | 10000 | Maximum additive cost for one edge |
| `MAX_TRACKED_EDGES` | 32 | Per-Agent reliability ledger bound |
| `ATTEMPT_TIMEOUT_MS` | 3500 | Motionless executed-edge timeout |
| `PROGRESS_TOLERANCE_PX` | 6 | Minimum movement that refreshes an attempt |
| `LAUNCH_TOLERANCE_PX` | 12 | Structural source/launch-anchor tolerance |
| `LANDING_TOLERANCE_PX` | 20 | Structural destination-anchor tolerance |
| `ATTACHMENT_TOLERANCE_PX` | 12 | Rope/ladder anchor tolerance |

The five feature switches are independent. With local leasing disabled, map-wide promotion behaves as before. With strict combat routes disabled and both reliability routing features disabled, combat uses the former target-score path and edge-cost calculation. With all three navigation reliability switches disabled, live edge selection and execution follow the former path unchanged.

### Route blocker policy

| Setting | Default | Effect |
|---|---:|---|
| `ROUTE_BLOCKER_CORRIDOR_WIDTH` | 80 | Width around the travel segment considered blocked |
| `ROUTE_BLOCKER_TIMEOUT_MS` | 2500 | Maximum blocker-combat episode |
| `ROUTE_BLOCKER_MAX_KILLS` | 5 | Maximum kills before forced travel resumes |
| `ROUTE_BLOCKER_TRAVEL_COOLDOWN_MS` | 7000 | Travel-only cooldown after a blocker episode |

### Scoring policy

| Setting | Default | Effect |
|---|---:|---|
| `AOE_CLUSTER_RADIUS_PX` | 150 | Radius used to value nearby mobs for AoE |
| `AOE_CLUSTER_BONUS_PER_MOB` | 200 | Score contribution for each useful mob in the AoE cluster |
| `LOCAL_TRAVEL_VERTICAL_COST_PER_PX` | 4 | Estimated travel cost of vertical separation |
| `LOCAL_TARGET_VERTICAL_WEIGHT` | 8 | Direct target-score penalty per vertical pixel |
| `LOCAL_TARGET_OFF_LEVEL_PENALTY` | 600 | Penalty for leaving attack-height alignment |
| `LOCAL_TARGET_OTHER_FOOTHOLD_PENALTY` | 1200 | Penalty for targets on another foothold |
| `UPWARD_PLATFORM_TOLERANCE_PX` | 60 | Upward separation tolerated without extra penalty |
| `UPWARD_PLATFORM_BASE_PENALTY` | 2500 | Base penalty above the upward tolerance |
| `UPWARD_PLATFORM_PENALTY_PER_PX` | 6 | Additional penalty per excess upward pixel |
| `MINIMUM_SINGLE_TARGET_SCORE` | 100 | Floor used when comparing single-target and AoE attacks |

### Personality and crowd extensions

`AGENT_BEHAVIOR_PROFILE` controls response timing, crowd respite, claim avoidance, target and route variation, and platform anchoring. `AGENT_PRESENTATION_PROFILE` independently controls personality presentation, idle combat presentation, and combat emotes. Both accept `OFF` or `STANDARD`; neither changes combat or navigation correctness.

### Loot policy

| Setting | Default | Effect |
|---|---:|---|
| `MELEE_IMMEDIATE_RADIUS` | 180 | Maximum local radius for immediate melee post-kill loot |
| `MELEE_RECENT_KILL_TARGET_AGE_MS` | 1000 | Time a melee drop settles before it can be targeted/picked up |
| `RANGED_BATCH_KILLS` | 5 | Ranged kills accumulated before returning to loot |
| `RANGED_BATCH_MAX_WAIT_MS` | 4500 | Maximum ranged batching time |
| `MAX_TRACKED_KILLS` | 8 | Bound on recent killed map objects retained per Agent |
| `TRACKED_KILL_LIFETIME_MS` | 20000 | Lifetime of recent kill evidence |
| `PRE_EXIT_MAX_WAIT_MS` | 4000 | Final bounded pickup window before map exit |
| `PRE_EXIT_LOOT_RADIUS` | 450 | Maximum pre-exit loot search radius |

To make melee pickup slightly slower or faster, change only `MELEE_RECENT_KILL_TARGET_AGE_MS`. A practical visible-but-responsive range is 900-1,300 ms. This setting does not change drop ownership, pickup eligibility, or how far the Agent walks.

## Safe extension rule

New behavior must be implemented as a policy input or filter with an observable reason. It must not be embedded in damage execution, packet formatting, quest mutation or pickup mutation. If a new policy changes the candidate set, its before/after count and decision reason should be added to the diagnostic trace so the layer can be evaluated independently or run in shadow mode later.
