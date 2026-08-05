# Agent Combat and Loot Policy Layers

## Purpose

This document defines the separation between the Agent combat kernel and the policy layers that modify its choices. The separation makes each decision observable and tuneable without changing the shared Cosmic player-combat handlers or packet formats.

The current policy order and default values preserve the existing combat behavior. The only intentional behavior adjustment in this refactor is the melee post-kill drop-settle delay, increased from 750 ms to 1,000 ms so a nearby drop is visible briefly before pickup.

## Responsibility boundary

| Layer | Responsibility | Must not own |
|---|---|---|
| Combat kernel | Discover alive candidates, score reachability and distance, choose a target, build an attack plan, execute the attack | Quest progression, personality, crowd policy, loot ownership |
| Objective policy | Restrict or prefer mobs required by the active objective; permit bounded incidental clearing | Attack packets, movement physics |
| Map-pressure policy | Keep a target species spawning by clearing overrepresented incidental species | Quest mutation, inventory mutation |
| Crowd and claims policy | Delay reactions in crowds, avoid excessive multi-Agent claims, spread target choices | Base target discovery, damage calculation |
| Personality/presentation policy | Variation, anchoring, response cadence, idle presentation and emotes | Objective correctness, packet validation |
| Route-blocker policy | Temporarily attack or evade mobs obstructing active travel | Changing the travel objective |
| Attack planner/executor | Choose legal basic or skill attacks and perform them through the normal Agent combat integration | Selecting the progression objective |
| Post-kill loot policy | Decide when combat may yield to pickup and how far the Agent may move | Item ownership, inventory rules, quest eligibility |
| Loot eligibility/pickup | Enforce ownership, age, quest/PQ rules, inventory capacity and perform pickup | Target selection or combat scoring |
| Diagnostics | Record the last decision and policy counts | Changing any decision |

## Combat target decision flow

The grind target path executes in this order:

1. Yield while pre-exit loot collection is active.
2. Discover alive monsters inside `GRIND_SEEK_RANGE`.
3. Apply the active objective allow-list.
4. Escalate to map-wide preferred targets when required mobs exist outside the local search window.
5. Apply quest preference, local-clear and spawn-pressure policy.
6. Apply target claims and crowd competition policy.
7. Apply crowd/personality response timing.
8. Score remaining candidates using local distance, foothold, vertical travel, reachability, objective priority, occupancy and AoE density.
9. Choose a reachable best target, with bounded personality target variation.
10. Optionally establish or retain an anchor-farming platform.
11. Record the selected objective-policy reason and the passive decision trace.

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

These records are ephemeral diagnostic evidence. They do not participate in selection or mutation and are exposed through `AgentCombatPolicyDiagnostics`.

## Current tuneables

All values live in `agent-engine.yaml`. The values below are the behavior-preserving defaults at the time of this refactor.

### Targeting and objective policy

| Setting | Default | Effect |
|---|---:|---|
| `SPAWN_PRESSURE_MIN_TARGET_SHARE_PERCENT` | 80 | Begins incidental spawn-pressure clearing when required mobs are underrepresented |
| `MAX_CONSECUTIVE_INCIDENTAL_KILLS` | 2 | Limits consecutive non-objective kills |
| `MAX_INCIDENTAL_KILLS_PER_PLATFORM_LEASE` | 3 | Limits incidental clearing during one platform lease |
| `PLATFORM_LEASE_MS` | 6500 | Retains local platform focus for this duration |
| `QUEST_LOCAL_CLEAR_ENFORCED` | true | Applies local platform clearing before distant preferred targets |
| `QUEST_LOCAL_CLEAR_SHADOW_ENABLED` | false | Computes local-clear evidence without enforcing it when enabled alone |

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

The following switches independently enable the already-existing behavior layers: `AGENT_COMBAT_BEHAVIOR_ENABLED`, `AGENT_RESPONSE_LATENCY_ENABLED`, `AGENT_MAP_CROWD_RESPITE_ENABLED`, `AGENT_TARGET_CLAIM_POLICY_ENABLED`, `AGENT_TARGET_VARIATION_ENABLED`, `AGENT_PLATFORM_ANCHOR_BEHAVIOR_ENABLED`, `AGENT_IDLE_COMBAT_PRESENTATION_ENABLED` and `AGENT_COMBAT_EMOTES_ENABLED`.

Their profile-specific weights and timing remain in the personality/combat sections of `agent-engine.yaml`; disabling a switch removes that extension while leaving the combat kernel intact.

### Loot policy

| Setting | Default | Effect |
|---|---:|---|
| `MELEE_IMMEDIATE_RADIUS` | 180 | Maximum local radius for immediate melee post-kill loot |
| `MELEE_RECENT_KILL_TARGET_AGE_MS` | 1000 | Time a melee drop settles before it can be targeted/picked up |
| `RANGED_BATCH_KILLS` | 3 | Ranged kills accumulated before returning to loot |
| `RANGED_BATCH_MAX_WAIT_MS` | 4500 | Maximum ranged batching time |
| `MAX_TRACKED_KILLS` | 8 | Bound on recent killed map objects retained per Agent |
| `TRACKED_KILL_LIFETIME_MS` | 20000 | Lifetime of recent kill evidence |
| `PRE_EXIT_MAX_WAIT_MS` | 4000 | Final bounded pickup window before map exit |
| `PRE_EXIT_LOOT_RADIUS` | 450 | Maximum pre-exit loot search radius |

To make melee pickup slightly slower or faster, change only `MELEE_RECENT_KILL_TARGET_AGE_MS`. A practical visible-but-responsive range is 900-1,300 ms. This setting does not change drop ownership, pickup eligibility, or how far the Agent walks.

## Safe extension rule

New behavior must be implemented as a policy input or filter with an observable reason. It must not be embedded in damage execution, packet formatting, quest mutation or pickup mutation. If a new policy changes the candidate set, its before/after count and decision reason should be added to the diagnostic trace so the layer can be evaluated independently or run in shadow mode later.
