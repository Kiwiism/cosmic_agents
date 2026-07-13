# Maple Island Realism And 100-Agent Validation Plan

## Purpose

This document defines the work that follows deterministic Maple Island MVP
validation. It covers profile-driven pacing, NPC approach variation, safe
movement fidgets, route variation, incidental mob behavior, and the final
100-Agent completion demonstration.

The order matters. Realism is enabled only after the same route is proven with
deterministic movement and timing. Random presentation must not be allowed to
hide a quest, navigation, combat, reactor, loot, or reset defect.

Related documents:

- `docs/agents/MAPLE_ISLAND_SOUTHPERRY_MVP.md`
- `docs/agents/MAPLE_ISLAND_SOUTHPERRY_MVP_BASELINE.md`
- `docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md`
- `docs/agents/plans/maple-island-southperry-mvp.plan.json`
- `docs/agents/plans/maple-island-mvp.plan.json`
- `docs/agents/INTERACTION_REALISM_POLICY.md`
- `docs/agents/AGENT_SOAK_TEST_IMPLEMENTATION_SPEC.md`

## Milestone Order

### Gate A: Current Southperry Segment

Test the current `maple-island-southperry-mvp` from the captured AmherstRun
baseline.

Entry:

```text
level 6 AmherstRun captured state
map 1000000 Amherst
18 selected Amherst quests complete
```

Exit:

```text
map 2000000 Southperry
quests 1039-1045 complete
quest 1046 active and incomplete
quest 1028 incomplete
Relaxer equipped and visibly in use
```

This gate remains focused on the post-Amherst route. It must not rerun the
level-1 Mushroom Town and Amherst objectives.

### Gate B: Full Maple Island To Southperry

After Gate A is stable, implement and validate the composed clean-character
run represented by `maple-island-mvp.plan.json`.

Entry:

```text
fresh level-1 character state
map 10000 Mushroom Town
selected Maple Island quests reset
normal starter appearance, equipment, inventory, and keymap
```

The full run should reuse the proven Amherst and Southperry objective handlers,
capabilities, portal travel, reset policy, and validators. It should not copy
the two implementations into a third special runtime.

Exit:

```text
all selected modern Maple Island objectives satisfied
map 2000000 Southperry
quest 1046 active and incomplete
quest 1028 incomplete
no Shanks transport to Lith Harbor
Relaxer visibly in use at a valid Southperry rest point
```

### Gate C: Single-Agent Realism

Run the full route with one assigned behavior profile and a fixed run seed.
Prove every selected delay, approach point, fidget, route edge, encounter
choice, and rest point can be replayed.

### Gate D: Small Cohorts

Use staged populations of `5`, `10`, `25`, and `50` Agents. Each stage must
pass correctness, movement, diversity, and stability criteria before the next
stage starts.

### Gate E: 100-Agent Demonstration

Release Agents gradually until 100 are in the game. Every Agent must complete
the full selected Maple Island route and visibly rest on a Relaxer at a valid,
varied Southperry position.

## Responsibility Boundaries

```text
Plan Card
  owns required objectives, order, entry, exit, and forbidden actions

Capability
  owns validated execution of navigation, NPC, quest, combat, loot, reactor,
  inventory, portal, and chair actions

Agent Profile
  owns stable timing ranges, movement style, encounter preference, rest style,
  and personality weights

Realism Policy
  samples bounded decisions from the profile and live context

Capability Runtime
  accepts or suppresses the sampled presentation action and executes it using
  normal server movement and gameplay rules
```

Profiles provide preferences and ranges. They do not move a character, attack
a mob, change a quest, or override a capability validator.

## Moving Current Delays Into Profiles

### Current State

The current showcase uses plan-specific server settings:

```yaml
AGENT_AMHERST_NPC_INTERACTION_DELAY_MIN_MS: 600
AGENT_AMHERST_NPC_INTERACTION_DELAY_MAX_MS: 1400
AGENT_AMHERST_NEXT_OBJECTIVE_DELAY_MIN_MS: 900
AGENT_AMHERST_NEXT_OBJECTIVE_DELAY_MAX_MS: 1800
```

This was useful for proving the MVP, but every Agent receives the same ranges
and the setting name is tied to Amherst even when the Southperry plan uses it.

### Target Ownership

Move normal pacing to the assigned Agent profile. Keep server configuration
only for mode selection, deterministic test overrides, fallback defaults, and
hard safety caps.

Recommended profile extension:

```json
{
  "presentation": {
    "mode": "LIGHT",
    "timing": {
      "beforeNpcInteractionMs": [600, 1400],
      "betweenObjectivesMs": [900, 1800],
      "reactionDelayMs": [350, 900],
      "microPauseChancePerWindow": 0.08
    },
    "npc": {
      "approachStyle": "crowd-aware-random",
      "preferredDistancePx": [70, 150],
      "sideBias": "either",
      "faceNpcBeforeInteraction": true
    },
    "movement": {
      "style": "opportunistic",
      "walkingJumpChancePerWindow": 0.08,
      "backtrackChancePerWindow": 0.02,
      "crouchChancePerIdleWindow": 0.05,
      "expressionChancePerIdleWindow": 0.03,
      "fidgetCooldownMs": [12000, 45000],
      "maxFidgetDurationMs": 1500,
      "alternateDropEdgeWeight": 0.35
    },
    "encounter": {
      "style": "attack-if-cheap",
      "maxEstimatedHits": 3,
      "maxDetourMs": 1200,
      "avoidTouchBelowHpPercent": 0.45
    },
    "rest": {
      "style": "reserved-random",
      "minimumSeparationPx": 32
    }
  }
}
```

These names are a proposed profile contract. The profile JSON schema must be
updated and validated before executable templates use them.

### Resolution Order

```text
deterministic test override
  -> assigned Agent profile
  -> archetype/template defaults
  -> server fallback defaults
  -> zero-delay and deterministic-nearest fail-safe
```

Global safety policy applies after resolution:

```text
mode OFF forces all cosmetic delays and fidgets off
server maximums clamp profile delay ranges
invalid ranges fall back instead of failing the plan
capability deadline always exceeds the allowed delay plus execution budget
```

### Migration

1. Add profile timing reads while retaining current Amherst settings as the
   fallback.
2. Assign an explicit deterministic profile to current MVP tests.
3. Add two or three realism templates and compare sampled output.
4. Change the showcase profile to use profile-owned ranges.
5. Deprecate the four `AGENT_AMHERST_*_DELAY_*` settings after no runtime path
   depends on them.

Do not put delay values into plan objectives. One Plan Card should work for a
fast, cautious, distracted, or impatient Agent.

## Seeded Variation

Use stable seeded randomness, not unrecorded `ThreadLocalRandom` calls, for
profile presentation choices.

Recommended seed domains:

```text
runSeed
agentIdentitySeed
timingSeed
routeSeed
fidgetSeed
encounterSeed
restSeed
```

Decision seed:

```text
hash(runSeed, agentIdentitySeed, objectiveId, phase, attempt, decisionDomain)
```

Requirements:

- the same run seed and Agent identity reproduce the same decisions;
- different Agent identities produce different bounded decisions;
- retries include the attempt number and may select a different valid option;
- each decision records the seed or replay key;
- no random choice is made every movement tick.

Random choices should happen at named decision windows, such as objective
start, NPC approach selection, grounded idle window, route-edge choice, mob
encounter, and final rest-point selection.

## NPC Interaction Variation

The plan identifies the NPC. The catalog and realism policy choose a valid
presentation position.

Candidate generation:

```text
NPC placement and interaction box
  -> valid nearby foothold samples
  -> points reachable by the normal navigation graph
  -> points from which range validation succeeds
  -> crowd and reservation scoring
  -> seeded weighted selection
```

Rules:

- never use an arbitrary `x/y` that is not grounded and reachable;
- always face the NPC immediately before the interaction;
- do not interact while airborne, climbing, down-jumping, or falling;
- reserve selected points briefly to reduce stacking;
- reservations are advisory and expire automatically;
- if all varied points fail, use the nearest deterministic valid point;
- retry a failed point only after another valid point has been considered.

Tests should include NPCs above, below, left, and right of the Agent, as well as
many Agents approaching the same NPC concurrently.

## Fidgets And Expressions

The existing Agent movement package already has physical fidget actions for:

```text
WAIT
JUMP
DIAGONAL_JUMP
PRONE
SPAM_PRONE
SPAM_SIDEWAYS
```

Those actions currently serve legacy-derived follow, idle, and social flows.
The plan-running feature should reuse the normal movement and packet execution,
but it needs a new profile-aware eligibility policy. It should not turn on the
legacy follow fidget roll during an active objective.

Safe eligibility:

- Agent is grounded on a valid foothold;
- no portal transition is pending;
- no NPC, quest, reactor, loot, chair, or attack action is committed;
- no jump, fall, rope, climb, down-jump, or recovery action is active;
- the active capability reports a presentation-safe window;
- the fidget cannot move the Agent outside the current navigation corridor;
- the objective still has enough timeout budget;
- health and nearby threat state permit the action.

Behavior budget:

- use a cooldown range instead of a chance every tick;
- cap fidget duration per event;
- cap total presentation delay per objective;
- cap backward travel distance;
- suppress repeated use of the same fidget;
- suppress expressions when no observing player is nearby if scale policy
  disables cosmetic packets.

Suggested first implementation order:

1. non-blocking expression while safely idle;
2. short crouch while grounded;
3. stationary jump;
4. bounded back-and-forth movement with origin return;
5. optional walking jump that is validated against the route corridor.

Walking jumps are last because they can affect route progress and recreate
mid-air interaction or stuck states if they are allowed at the wrong time.

## Navigation Variation

Variation should select among valid routes. It must not randomize physics,
teleport the Agent, or invent a landing point.

### Down-Jump Choice

When the navigation graph has more than one valid drop edge toward the same
goal:

```text
valid progress-making drop edges
  -> remove recent failed edges
  -> remove unsafe or occupied landing corridors
  -> score path cost, repetition, profile preference, and crowding
  -> choose a seeded weighted edge
```

Remember a short history of used drop edges so an Agent does not choose the
same point every run. A focused Agent may heavily favor the shortest edge,
while a curious Agent may give alternate valid edges more weight.

### Route Safety

- committed navigation edges remain atomic;
- no fidget may replace a required rope, jump, portal, or drop edge;
- airborne physics and landing remain deterministic after an edge is chosen;
- route variation must satisfy the same arrival tolerance;
- recovery teleport remains separately gated and must never be presented as
  normal navigation;
- every non-physical relocation is audited as recovery or test setup.

## Incidental Mob Encounter Styles

Quest-required combat always overrides presentation preference. The following
styles apply only to incidental mobs encountered while traveling.

### Evasive

- prefer a valid jump or short detour around the mob;
- accept the detour only inside the profile time and distance budget;
- continue normally when no safe evasion route exists.

### Opportunistic Fighter

- estimate normal hits-to-kill from current stats, weapon, skill policy, and
  mob HP;
- attack only when the estimate is within the profile threshold, normally
  `1-3` hits;
- use normal attack range, animation, cooldown, damage, knockback, and loot
  behavior;
- resume the interrupted navigation objective after the local fight.

### Direct Walker

- continue along the route and tolerate ordinary contact when health policy
  permits;
- do not detour merely to avoid a weak mob;
- switch to safety behavior if HP or death risk crosses the profile threshold.

No style may silently kill, ignore collision, extend attack range, or grant
loot. If an incidental fight threatens the plan deadline, the capability
returns to progress-first behavior.

## Suggested Maple Island Archetypes

Use a controlled mixture rather than assigning 100 fully independent random
profiles.

| Archetype | Timing | Movement | Incidental mobs | NPC approach |
| --- | --- | --- | --- | --- |
| Focused quester | short | direct, few fidgets | ignore unless required | nearest valid |
| Careful traveler | medium | cautious, evasive | avoid | safe and uncrowded |
| Opportunistic fighter | medium | normal | attack if 1-3 hits | either side |
| Curious wanderer | longer | alternate edges, bounded backtrack | mixed | varied distance |
| Relaxed walker | longer | crouch/expression, direct walking | tolerate contact | crowd-aware random |

Archetype values are defaults. Each Agent keeps an identity seed so two Agents
with the same archetype still do not synchronize.

## Southperry Rest Distribution

"Random spots" means random valid rest points, not arbitrary map coordinates.

Build a Southperry rest catalog containing:

- valid grounded foothold samples;
- points outside portals and NPC interaction boxes;
- points that do not obstruct narrow traversal paths;
- enough visual separation for the expected population;
- optional named zones with different crowd weights.

Selection:

```text
valid rest points
  -> remove blocked and unsafe points
  -> penalize reserved/crowded points
  -> apply profile zone preference
  -> seeded weighted choice
  -> navigate normally
  -> verify grounded
  -> equip/use Relaxer
  -> verify visible chair state
```

The terminal state is not satisfied merely because a chair item ID was stored.
The Agent must remain in Southperry, at the selected point, and visibly in the
chair state after a short verification window.

## 100-Agent Release Plan

The proposed rate of `5-10` Agents per minute is reasonable. Avoid releasing
all Agents at the same second of each minute. That produces artificial portal,
NPC, mob, reactor, and scheduler bursts.

Recommended release behavior:

```text
choose a seeded cohort size from 5-10 for each minute
spread that cohort across the minute with 6-12 second jittered intervals
stop when 100 Agents have entered
pause release automatically if health gates fail
```

Each Agent must:

- have a unique character and identity seed;
- start from a clean level-1 fixture;
- have no owner-follow or party mode;
- receive one approved behavior profile;
- receive the same plan version;
- record its run seed and profile version;
- use normal portals, quest APIs, combat, loot, reactor, inventory, and chair
  behavior.

Shared-world contention must be tested deliberately:

- multiple Agents needing tutorial mobs;
- multiple Agents needing the same quest item drop;
- reactor respawn and ownership timing;
- NPC approach reservations;
- portal crowding;
- Southperry rest-point reservations;
- map lifecycle and controller assignment when no player is visible.

## Test Ladder

### Stage 0: Deterministic Baseline

- one current Southperry segment run;
- one full clean Maple Island run;
- realism `OFF`;
- record objective duration baseline and terminal state.

### Stage 1: Policy Tests

- profile resolution and fallback;
- delay range clamping;
- same-seed replay;
- different-seed distribution;
- NPC approach reachability;
- fidget suppression reasons;
- alternate route-edge selection;
- encounter-style decisions;
- rest-point reservation and expiry.

### Stage 2: One-Agent Visual Replay

- realism `LIGHT`, then `FULL`;
- fixed run seed;
- review every map transition and NPC interaction;
- verify no flight, teleport-looking correction, mid-air interaction, or
  impossible attack range;
- replay the same seed and compare the decision journal.

### Stage 3: Small Cohorts

```text
5 Agents
10 Agents
25 Agents
50 Agents
```

At each stage, hold the population until every Agent reaches a terminal state
or the stage timeout expires. Do not advance merely because the server remains
online.

### Stage 4: 100-Agent Completion Run

- release 5-10 per minute using jittered inter-spawn timing;
- observe loaded maps during the route;
- stop release on health, corruption, or runaway retry failures;
- continue until all 100 complete or the measured deadline expires;
- export a final per-Agent and aggregate report.

## Pass Criteria

Functional:

- `100/100` Agents satisfy the selected quest exit contract;
- `100/100` finish in `2000000 Southperry`;
- `100/100` leave quest `1046` active and quest `1028` incomplete;
- `100/100` visibly sit on a Relaxer at a valid rest point;
- levels, EXP, mesos, inventories, equipment, and quest item consumption remain
  coherent;
- no duplicate rewards, duplicated items, or cross-Agent progress leakage.

Movement and presentation:

- no unexplained teleport, flight, or far-side map correction;
- no NPC or quest transition while airborne;
- no Agent remains silently stuck beyond the configured watchdog window;
- recovery actions are explicit and audited;
- all fidgets stay within their movement and time budgets;
- alternate NPC points, route edges, and rest points are actually represented
  when valid alternatives exist.

Performance:

- no unbounded scheduler queue, retry loop, heap growth, or loaded-map growth;
- game-server tick and player-facing latency remain inside the project health
  envelope;
- database saves and plan journals drain normally;
- shutdown and restart preserve or reconcile Agent progress.

Timing:

Do not choose an arbitrary fixed completion deadline first. Measure the
deterministic single-Agent and small-cohort distributions, then set the 100-run
deadline from the observed baseline, for example a bounded multiple of the
small-cohort `p95` duration.

## Required Observability

Record one structured event for each presentation decision:

```text
agentId
profileId and version
runSeed and decision replay key
planId, objectiveId, phase, attempt
chosen delay and configured range
NPC approach point and reservation result
fidget selected or suppression reason
navigation edge selected and alternatives considered
mob encounter style, estimated hits, and chosen response
rest point and chair verification result
capability state before and after the decision
```

Final report fields:

```text
startedAt, completedAt, duration
terminal map and quest contract
level and EXP delta
objective retries and blockers
stuck and recovery count
NPC point distribution
fidget distribution
route-edge distribution
encounter distribution
rest-zone distribution
server health summary during the Agent's run
```

The operator must be able to replay one failed Agent by profile version and
seed without rerunning the entire 100-Agent population.

## Recommendations

1. Treat realism as bounded variation, not maximum randomness. Human-looking
   behavior has habits and repeated preferences as well as variation.
2. Keep deterministic `OFF` mode permanently. It is the control group for
   every future route regression.
3. Add fidgets in the order of least movement risk. Expressions and crouches
   are safer than walking jumps and backtracking.
4. Keep the 100-Agent run separate from the 2000-Agent soak target. This is a
   correctness, contention, and visual-diversity cohort test with fully live
   Agents, not a proof of maximum background simulation scale.
5. Use reservations for NPC and rest positions, but make them advisory with
   expiry so one failed Agent cannot block a location.
6. Add an objective progress budget. Realism may consume a small percentage of
   an objective's time, but repeated cosmetic actions must yield to progress.
7. Test shared quest resources before 100 Agents. Reactor and loot ownership
   defects are easier to diagnose with 5 Agents than with 100.
8. Do not let profiles select impossible behavior. The profile says "prefer
   evasive"; navigation decides whether an evasive route currently exists.

## Completion Definition

This milestone is complete when:

- current Southperry and full clean Maple Island runs are deterministic and
  repeatable;
- pacing and presentation preferences resolve from Agent profiles;
- NPC, fidget, route, encounter, and rest variation remain capability-safe;
- the same seed is replayable;
- the 100-Agent staged run meets all functional and stability criteria; and
- every successful Agent is visibly resting at a varied valid point in
  Southperry.
