# Field Capacity and Spawn Balancing

The field observation harness separates two decisions:

1. **Static capacity planning** estimates how many Agents a map can sustain.
2. **Dynamic assignment** decides which Agent leases which live farming platform.

Neither decision uses a learned model or map-specific combat branches.

## Static capacity model

`AgentFieldCapacityEstimator` groups WZ spawn entries by their topology component. For each populated
platform it calculates:

```text
width capacity = ceil(populated horizontal width / 500 px)
spawn capacity = ceil(spawn entries / 4)
platform capacity = min(width capacity, spawn capacity)
```

The map total is capped again by `ceil(total spawn entries / 4)`. High-complexity maps whose spawn
evidence is mostly isolated one- or two-spawn components receive a 15% fragmentation penalty. This
prevents tall maps from being assigned one Agent to every tiny ledge.

Every observation phase keeps at least `ceil(maximum Agents × 40%)` active. The rotation begins at
that floor (or a higher geometry-derived recommended minimum), rises through the recommended and
maximum populations, and returns to the same floor. Inactive roster members remain staged normally.

The generated profile records every input, constraint, adjustment, confidence level, recommended
range, maximum, active-count rotation, and legal six-member party partition. There is no fixed
12-Agent ceiling.

Authored map eligibility remains in
`victoria-level15-25-observation-harness.json`. Generated capacity lives in
`victoria-field-capacities.json`. Exceptional observed maps can be changed sparsely through
`victoria-field-capacity-overrides.json` and then regenerated with:

```text
.\mvnw.cmd -Dagent.field.exportCapacity=true -Dtest=AgentFieldCapacityCatalogExportTest test
```

## Dynamic balancing algorithm

The runtime uses a **lease-based greedy capacitated auction**, a form of market-based multi-agent
task allocation:

- farming platforms are tasks with multiple capacity slots;
- capacity slots are spawn-weighted station points with midpoint-bounded horizontal territories;
- Agents bid using objective population, coverage, distance, combat-role fit, occupancy, and nearby
  real-player pressure;
- valid existing leases are reserved first;
- unoccupied platforms are filled before additional slots on an occupied platform;
- a platform must remain empty for seven continuous seconds before its lease is released;
- a five-second rebalance gate and retained-lease bonus provide hysteresis.

Within a shared platform, each Agent retains a distinct station lease. Target acquisition first
uses mobs inside that station's territory and uses existing peer target telemetry as a soft claim:
an unclaimed mob wins when available, while sharing remains legal if all local mobs are claimed.
After seven seconds without a territorial candidate, the existing bounded borrowing window permits
work outside the station territory.

These restrictions are opt-in metadata on field-session assignments. Quest assignments do not
carry territorial metadata and continue through the existing quest target-debt and combat policy.

This is intentionally not reinforcement learning. It is deterministic and exposes every decision
factor. It is also not a globally optimal Hungarian/min-cost-flow assignment: the current greedy
auction is smaller, faster, and easier to diagnose. The observation telemetry should justify a
global solver before one is introduced.

## Calibration evidence

Each completed population window reports:

- kills and EXP gained;
- kills per Agent-minute;
- attack count;
- searching plus idle time;
- assignment changes;
- route failures and stuck detections;
- occupied-platform conflicts;
- empty assigned platforms;
- Agents more than 600 px from their assigned anchor.

Use marginal kills per Agent-minute and non-combat time to lower an overestimated maximum. Increase
capacity only when additional Agents improve total throughput without sustained conflicts, empty
assignments, or navigation failures.

## Evidence limits

Topology components are a reproducible platform proxy, not proof that every live movement edge is
reliable. Hazardous or mechanically unreliable platforms must be represented by a manual override
after observation; the estimator does not invent hazard facts that are absent from the WZ catalogs.
