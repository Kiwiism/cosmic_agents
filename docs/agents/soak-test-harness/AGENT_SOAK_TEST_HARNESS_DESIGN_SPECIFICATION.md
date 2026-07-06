# Agent Soak Test Harness Design Specification

Purpose:

```text
Define the future package that runs repeatable Agent scale scenarios, captures
evidence, and decides whether the reconstructed Agent runtime is safe to scale.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
A soak run is a controlled experiment, not a command that spawns chaos.
```

The harness must create predictable staged load, collect comparable metrics, and
fail loudly when Agent work harms server responsiveness or produces invalid
state.

## Goals

- Validate 50, 250, 500, 1000, and 2000-Agent stages.
- Support 24-hour, 3-day, 7-day, and 30-day run ladders.
- Exercise idle, grind, quest, hidden simulation, materialization, DB pressure,
  shutdown/restart, and long-uptime scenarios.
- Keep player-facing server responsiveness measurable during Agent load.
- Produce stable CSV/JSONL/JSON summary outputs that can be compared across
  commits.
- Use seeded population presets so failures can be reproduced.
- Keep all Agent soak logic isolated from normal production behavior.

## Non-Goals

- Do not prove 500 real-player concurrency.
- Do not require LLM Gateway.
- Do not require full economy engine.
- Do not complete every Victoria Island quest.
- Do not change live Agent behavior outside explicit soak mode.
- Do not auto-enable on production servers.

## Soak Run Model

A soak run has:

- run id.
- stage.
- scenario.
- population preset.
- seed.
- target agent count.
- duration target.
- capture interval.
- command owner.
- safety limits.
- output directory.
- current runner state.

Runner states:

```text
IDLE
PREPARING
SPAWNING
RUNNING
PAUSED
STOPPING
STOPPED
FAILED
COMPLETED
```

Only one active run should exist per world/channel group unless the harness is
explicitly extended for isolated test shards.

## Command Surface

Primary commands:

```text
!soak agents prepare <stage> <scenario> [preset] [seed]
!soak agents start
!soak agents start <count> <scenario> [preset] [seed]
!soak agents pause
!soak agents resume
!soak agents stop [reason]
!soak agents status
!soak agents snapshot
!soak agents export
!soak agents cleanup
```

Scenario controls:

```text
!soak agents materialize <mapid|all>
!soak agents dematerialize <mapid|all>
!soak agents checkpoint
!soak agents killrandom <percent>
!soak agents resetstuck
!soak agents wave next
!soak agents wave status
```

Read-only helpers:

```text
!soak agents explain
!soak agents failures
!soak agents top slow
!soak agents top stuck
!soak agents top maps
!soak agents budgets
```

All mutating commands must require GM/admin permission and must be unavailable
unless soak mode is enabled.

## Scenarios

### IDLE_POPULATION

Agents spawn and remain mostly idle.

Validates:

- memory baseline.
- map spread.
- idle scheduler cost.
- snapshot output.

### GRINDING_POPULATION

Agents run simple training plans.

Validates:

- combat load.
- loot pressure.
- potion/death behavior.
- background combat savings.

### QUESTING_POPULATION

Agents run Plan Cards such as Maple Island MVP or later Victoria plans.

Validates:

- plan/objective state.
- NPC/quest interaction.
- fallback/recovery.
- catalog lookups.

### HIDDEN_SIMULATION

Most Agents operate in maps without real players.

Validates:

- simulation tier decisions.
- background action runtime.
- broadcast suppression.
- route ETA.
- virtual loot buffers.

### MATERIALIZATION_STORM

Synthetic or real observers enter maps containing background Agents.

Validates:

- mode transition.
- materialization point validity.
- inventory reconciliation.
- packet burst safety.
- fail-closed behavior.

### DB_PRESSURE

Many Agents hit checkpoint/milestone/save boundaries close together.

Validates:

- Agent save queue.
- player save priority.
- DB pool pressure.
- save coalescing and retry behavior.

### SHUTDOWN_RESTART

Server is stopped while Agents are active.

Validates:

- checkpoint flush.
- dirty Agent count.
- no stuck logged-in accounts.
- restart recovery.
- duplicate Agent registration prevention.

### LONG_UPTIME

Agents run for 24 hours to 30 days.

Validates:

- heap trend.
- loaded map trend.
- stale state cleanup.
- queue stability.
- log volume.

## Population Presets

Population presets must describe:

- level distribution.
- job distribution.
- map/home-town distribution.
- archetype distribution.
- plan distribution.
- initial inventory/potion/equipment quality.
- completed/active quest history.
- initial fatigue/rest/social state.

The first target preset remains:

```text
victoria_lt30_living_world_v1
```

Presets should use ratios instead of fixed counts so the same shape can run at
50, 250, 500, 1000, and 2000 Agents.

## Spawn Waves

Default behavior:

```text
prepare run
load preset
validate map caps
spawn safe-town wave
spawn training-map wave
spawn quest/farm wave
spawn late first-job wave
enter running state
```

Each wave should support:

- target count.
- delay window.
- max spawn rate.
- map capacity checks.
- retry policy.
- abort threshold.

## Safety Gates

Before start:

- server diagnostics available.
- Agent runtime reports ready.
- no active conflicting soak run.
- output directory writable.
- preset validates.
- scenario validates.
- target count within configured maximum.

During run:

- pause or fail if DB waiting remains high.
- pause or fail if scheduler queue grows continuously.
- pause or fail if materialization failures exceed threshold.
- pause or fail if real player responsiveness probes fail.
- pause or fail if invalid positions or impossible quest/item credits appear.

After stop:

- cleanup verifies no duplicate registrations.
- dirty Agent count drains or is reported.
- final summary is written.

## Output

Outputs:

```text
logs/soak/<runId>/snapshots.jsonl
logs/soak/<runId>/metrics.csv
logs/soak/<runId>/events.jsonl
logs/soak/<runId>/summary.json
logs/soak/<runId>/failures.json
```

The summary should be enough to answer:

- Did the stage pass?
- What failed or nearly failed?
- How many Agents ran in each mode?
- Did player responsiveness remain acceptable?
- Did heap, maps, queues, or DB pressure trend upward?
- Did materialization and shutdown/restart pass?

## Success Criteria

The package is ready when:

- command surface is explicit.
- runner states and transitions are defined.
- scenarios and presets are data-driven.
- pass/fail rules are machine-checkable.
- outputs are stable and comparable.
- safety gates prevent accidental production activation.
- soak can test Agent scaling without depending on LLM/economy/full-game quest
  completion.
