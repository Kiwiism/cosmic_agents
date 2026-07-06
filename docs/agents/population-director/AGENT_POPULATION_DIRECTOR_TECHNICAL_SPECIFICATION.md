# Agent Population Director Technical Specification

Purpose:

```text
Define the portable APIs, data models, algorithms, validation rules, and tests
for the future Agent Population Director package.
```

This document is implementation-ready guidance for after Agent reconstruction.
It must not be implemented against the live Agent runtime until the
reconstructed runtime boundaries are stable.

## Suggested Package Layout

```text
agent-population-director/
  api/
    PopulationDirectorService
    PopulationPlanProvider
    PopulationSnapshotProvider
    PopulationAssignmentPlanner
    PopulationRebalancePlanner
    PopulationDemandSignalPublisher
  model/
    WorldPopulationPlan
    PopulationCohort
    PopulationTarget
    PopulationSnapshot
    PopulationAssignment
    PopulationAssignmentRequest
    PopulationDirectorDecision
    PopulationRebalanceProposal
    PopulationConstraintViolation
    MapCapacityPolicy
    EconomicDemandSignal
    SpawnWavePlan
  runtime/
    DefaultPopulationDirectorService
    GapBasedPopulationAssignmentPlanner
    CapacityAwarePlacementPlanner
    StableRebalancePlanner
    PopulationConstraintValidator
    PopulationDemandSignalBuilder
  store/
    PopulationPlanStore
    PopulationDecisionJournal
  test/
    SeededPopulationScenarioRunner
```

Concrete names can be adapted to the final codebase, but the package should
keep API, model, runtime, and store concerns separate.

## Public Service Contract

### PopulationDirectorService

Primary entry point.

```text
PopulationDirectorDecision evaluate(PopulationDirectorRequest request)
```

Responsibilities:

- load the requested world population plan.
- read current population snapshot.
- compute target gaps.
- create assignment proposals.
- create rebalance proposals.
- validate proposals.
- emit demand signals.
- record decision journal entry.

It must not directly spawn Agents, move Agents, mutate profiles, or execute
plans. It returns proposals to the caller.

### PopulationPlanProvider

Loads population plans from portable data.

```text
WorldPopulationPlan getPlan(String planId)
List<WorldPopulationPlanSummary> listPlans()
ValidationReport validatePlan(String planId)
```

Plan source may be JSON, YAML, database records, or Agent Console config, but
the runtime should consume a normalized `WorldPopulationPlan`.

### PopulationSnapshotProvider

Provides current world state.

```text
PopulationSnapshot getSnapshot(PopulationSnapshotRequest request)
```

Snapshot inputs:

- Agent ids.
- current map ids.
- current region ids.
- current job ids.
- current level brackets.
- profile archetypes.
- active plan tags.
- simulation tiers.
- stuck/recovery states.
- online player counts by map if available.

### PopulationAssignmentPlanner

Creates assignment proposals for missing population.

```text
SpawnWavePlan planSpawnWave(WorldPopulationPlan plan, PopulationSnapshot snapshot, PopulationPlanningOptions options)
```

### PopulationRebalancePlanner

Creates gradual correction proposals for existing Agents.

```text
List<PopulationRebalanceProposal> planRebalance(WorldPopulationPlan plan, PopulationSnapshot snapshot, PopulationPlanningOptions options)
```

### PopulationDemandSignalPublisher

Publishes structured demand signals to Economy Engine or observability.

```text
List<EconomicDemandSignal> buildSignals(WorldPopulationPlan plan, PopulationSnapshot snapshot)
```

## Core DTOs

### WorldPopulationPlan

```json
{
  "planId": "victoria_lt30_living_world_v1",
  "version": 1,
  "seedPolicy": "explicit",
  "defaultSeed": 831942,
  "totalTarget": 2000,
  "modes": {
    "defaultMode": "PLAN_ONLY",
    "allowAssignment": false
  },
  "targets": [],
  "mapCapacityPolicies": [],
  "rebalancePolicy": {},
  "demandSignalPolicy": {}
}
```

### PopulationTarget

```json
{
  "targetId": "maple-island-islanders",
  "ratio": 0.15,
  "minCount": 0,
  "maxCount": 400,
  "cohortIds": ["maple-island-islander"],
  "regionIds": ["maple-island"],
  "levelRange": {"min": 1, "max": 30},
  "jobGroups": ["beginner"],
  "roleWeights": {
    "farm": 0.45,
    "town_mingle": 0.25,
    "social_idle": 0.20,
    "help": 0.10
  }
}
```

### PopulationCohort

```json
{
  "cohortId": "maple-island-islander",
  "profileTemplateIds": ["islander_basic", "islander_social", "islander_farmer"],
  "planSetIds": ["islander-loop"],
  "hardConstraintRefs": ["never_leave_maple_island"],
  "preferredMapIds": [10000, 2000000],
  "forbiddenMapIds": [],
  "economicRoleTags": ["low_level_supplier"]
}
```

### PopulationSnapshot

```json
{
  "worldId": 0,
  "channelId": null,
  "timestamp": "2026-07-07T00:00:00Z",
  "agentCount": 1250,
  "byRegion": {},
  "byMap": {},
  "byLevelBracket": {},
  "byJobGroup": {},
  "byArchetype": {},
  "byRole": {},
  "simulationTierCounts": {},
  "playerCountsByMap": {}
}
```

### PopulationAssignment

```json
{
  "assignmentId": "spawn-831942-000001",
  "agentId": null,
  "targetId": "fresh-victoria-warriors",
  "cohortId": "victoria-warrior-quester",
  "profileTemplateId": "careful_warrior_quester",
  "startingMapId": 102000000,
  "startingLevel": 12,
  "jobId": 100,
  "planSetIds": ["warrior-lv10-20-mixed"],
  "reasonCodes": ["TARGET_UNDERFILLED", "MAP_UNDER_CAPACITY"]
}
```

### PopulationRebalanceProposal

```json
{
  "proposalId": "rebalance-831942-000044",
  "agentId": 12345,
  "changeType": "PLAN_SET_SHIFT",
  "from": {"planSetId": "henesys-social-idle"},
  "to": {"planSetId": "ellinia-social-idle"},
  "priority": 0.35,
  "cooldownUntil": "2026-07-07T01:00:00Z",
  "reasonCodes": ["MAP_OVER_SOFT_CAP", "ELLINIA_UNDERFILLED"]
}
```

### MapCapacityPolicy

```json
{
  "mapId": 100000000,
  "softAgentCap": 30,
  "hardAgentCap": 60,
  "visibleFullSimulationCap": 12,
  "backgroundSimulationCap": 40,
  "roleCaps": {
    "social_idle": 20,
    "merchant": 5
  },
  "crowdingPenalty": 0.4
}
```

### EconomicDemandSignal

```json
{
  "signalId": "assassin-growth-lv15-30",
  "signalType": "CLASS_POPULATION_DEMAND",
  "itemTags": ["throwing_star", "claw", "luk_equip"],
  "jobGroup": "assassin",
  "levelRange": {"min": 15, "max": 30},
  "strength": 0.72,
  "reasonCodes": ["TARGET_ASSASSIN_RATIO_HIGH", "LIVE_ASSASSIN_GROWTH"]
}
```

## Planning Algorithm

### Step 1: Normalize Targets

Convert ratios to count targets.

```text
targetCount = round(totalTarget * ratio)
targetCount = clamp(targetCount, minCount, maxCount)
```

Validate:

- target ratios should not accidentally exceed 1.0 unless plan explicitly
  allows overlapping dimensions.
- target count total should be explainable.
- each target must reference valid cohorts and profile templates.

### Step 2: Build Current Snapshot Buckets

Bucket live Agents by:

- region.
- map.
- level bracket.
- job group.
- archetype.
- role.
- plan set.
- simulation tier.

Snapshots should be precomputed or bounded. The Director must not scan heavy
server collections during hot ticks.

### Step 3: Compute Gaps

For each target:

```text
gap = targetCount - currentMatchingCount
```

Classify:

- underfilled.
- satisfied.
- overfilled.
- blocked by map capacity.
- blocked by missing profile template.
- blocked by missing catalog data.

### Step 4: Generate Spawn Wave

For underfilled targets:

1. Pick cohort by weighted random using deterministic seed.
2. Pick profile template from cohort.
3. Pick starting map from allowed maps under capacity.
4. Pick level/job/profile details within allowed ranges.
5. Attach plan set ids.
6. Validate all constraints.
7. Add assignment to spawn wave.

Selection must be stable for the same seed and input snapshot.

### Step 5: Generate Rebalance Proposals

For overfilled targets or crowded maps:

1. Find Agents eligible for broad reassignment.
2. Exclude Agents in focus-mode plans unless interruptible.
3. Exclude Agents under cooldown.
4. Score candidate moves.
5. Prefer least disruptive changes.
6. Validate profile and plan constraints.
7. Emit proposals with low priority unless urgent.

### Step 6: Emit Demand Signals

Convert population shape into economy hints.

Examples:

- high Assassin growth -> throwing star demand.
- many Magicians near level 20 -> wand/staff demand.
- many islanders -> Maple Island item supply.
- many cursed-doll farmers -> cursed doll supply pressure.

Economy Engine remains the authority for prices and market behavior. The
Population Director only emits demand/supply pressure signals.

## Placement Scoring

Suggested map placement score:

```text
score =
  baseWeight
  * regionPreference
  * mapCapacityMultiplier
  * spawnDensityMultiplier
  * profileCompatibility
  * crowdingMultiplier
  * simulationTierMultiplier
```

Hard invalid maps receive score `0`.

Capacity multiplier:

```text
if current >= hardCap: 0
else if current <= softCap: 1
else max(0.05, 1 - ((current - softCap) / (hardCap - softCap)))
```

## Rebalance Anti-Thrash Rules

- minimum cooldown per Agent after reassignment.
- minimum cooldown per map after mass rebalancing.
- never rebalance only because a target is off by one Agent.
- prefer spawn-wave correction before moving existing Agents.
- prefer plan-set changes over forced map relocation.
- record previous proposal result and avoid repeating rejected proposals.

## Validation Rules

Every assignment and rebalance proposal must validate:

- referenced map exists in catalog.
- referenced profile template exists.
- referenced plan set exists.
- referenced job id is valid.
- level is inside allowed range.
- hard profile constraints are not violated.
- map hard cap is not exceeded.
- required catalog indexes are available.
- assignment mode allows the requested mutation type.
- LLM/operator suggestions do not bypass policy.

## Persistence

Recommended stores:

- population plan store.
- population decision journal.
- assignment result log.
- rebalance proposal result log.
- demand signal history.

The decision journal should be append-only.

## Observability

Metrics:

- target population.
- current population.
- target gap by cohort.
- assignments proposed.
- assignments accepted.
- assignments rejected.
- rebalances proposed.
- rebalances accepted.
- validation failures.
- map capacity pressure.
- economy demand signals emitted.
- planning time.

Reports:

- target-vs-actual population table.
- map crowding table.
- cohort underfill/overfill table.
- assignment rejection reasons.
- deterministic seed replay summary.

## Agent Console Hooks

Future Agent Console should allow:

- view population plan.
- compare target vs current population.
- preview spawn wave.
- preview rebalance proposals.
- switch mode between off, observe-only, plan-only, and assignment-allowed.
- inspect decision journal.
- filter by cohort, archetype, map, job, level, and role.

Console actions must still call the same validators.

## Test Plan

### Unit Tests

- target ratios normalize to expected counts.
- invalid ratios fail validation.
- unknown cohort id fails validation.
- unknown profile template fails validation.
- unknown map id fails validation.
- islander cohort cannot place Agents outside Maple Island.
- map hard cap prevents placement.
- deterministic seed produces stable assignments.
- different seed produces different but valid assignments.
- overfilled target produces rebalance proposal only after threshold.
- cooldown prevents repeated rebalance.
- demand signals reflect job distribution.

### Integration Tests

- load `victoria_lt30_living_world_v1`.
- generate 50-Agent, 250-Agent, 500-Agent, 1000-Agent, and 2000-Agent spawn
  waves.
- verify no generated assignment violates map capacity.
- verify soak-test population ratios are within tolerance.
- verify plan-only mode produces no mutations.
- verify observe-only mode produces metrics only.

### Replay Tests

- same plan + same snapshot + same seed = same assignments.
- same accepted assignments can reconstruct decision journal.
- rejected proposals are not repeated immediately.

## Implementation Gates

Do not implement live assignment until these packages are stable:

- Profile Platform read-only profile templates and constraints.
- Catalog Platform map/region/capacity indexes.
- Plan Runtime plan-set metadata.
- Event Bus or equivalent decision journal sink.
- Observability metrics sink.
- Server Adapter snapshot interface.
- Reconstructed Agent runtime entry points.

## Safe Pre-Reconstruction Work

Allowed before Agent reconstruction:

- maintain this specification.
- add JSON schema drafts.
- add sample population plan files.
- add offline validators.
- add documentation-only Agent Console mockups.

Not allowed before Agent reconstruction:

- live Agent spawn/move/rebalance execution.
- edits to `src/main/java/server/agents`.
- edits to `src/main/java/server/bots`.
- BotClient behavior changes.
- runtime profile mutation.
- runtime plan assignment.
