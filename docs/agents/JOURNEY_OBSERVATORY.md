# Agent Journey Observatory

## Purpose

The Journey Observatory runs a bounded cohort through an existing Agent OS progression path and
produces enough evidence to explain success, delay, resource use, recovery, or failure for every
Agent. It is an experiment controller and a read-only projection. It does not execute quest steps,
move characters, award rewards, repair navigation, or bypass the universal plan executor.

The first supported scenario is `victoria-lv10-20`:

1. Existing live Agents in the command issuer's cohort are reset to the level-10 fixture.
2. Careers rotate through Warrior, Bowman, Magician, Thief (dagger), and Pirate (knuckle).
3. Each Agent runs the existing first-job, instructor-training, home-pack, rotation-pack, shopping,
   and level-15 completion path through the universal executor.
4. When that universal plan finishes and the Agent is at least level 15, the controller starts the
   existing `victoria-training` universal plan with target level 20 and quests enabled.
5. The Victoria training scheduler may select any supported, eligible, incomplete quest. A quest
   used in another career's fixed pack is not globally reserved and remains eligible for an Agent
   that has not completed it.
6. The run succeeds for an Agent when it reaches level 20. Gameplay failures remain failures; the
   observatory only records them.

This means the deterministic level-10-to-15 path and the free-pick level-15-to-20 path use the same
universal lifecycle, checkpoint, suspension, recovery, and capability contracts as normal Agents.

## Implementation order (1–16)

| # | Implemented slice | Result |
|---:|---|---|
| 1 | Versioned run identity | Every run has a manifest, scenario, requested mode, target level, start time, and fixed participants. |
| 2 | Canonical evidence envelope | Domain events and reconciliation facts share a sequenced, versioned JSONL record. |
| 3 | Passive event projection | Enrolled Agents subscribe through the session event bus; Agents outside a run pay only a binding lookup. |
| 4 | Reconciliation snapshots | Level, EXP, job, map, position, HP/MP, mesos, plan, objective, career stage, inventory, and quests are sampled read-only. |
| 5 | Plan lifecycle evidence | Plan/status/step/attempt/objective changes become explicit derived records even when no dedicated domain event exists. |
| 6 | Quest evidence | Quest starts/completions, counter changes, duration, class, starting level, maps, EXP, mesos, and item deltas are retained. |
| 7 | Combat and map evidence | Exact kill/recovery/map events feed per-map dwell, visit, kill, and recovery summaries. |
| 8 | Resource ledger | Gross acquired/consumed item counts and gross gained/spent mesos are separated from net change. |
| 9 | Semantic progress clock | Kills, quest counters, plan steps, levels, maps, objectives, and inventory changes reset the stall clock. |
| 10 | Stuck taxonomy | Blocked plans, semantic stalls, excessive dwell, A-B map loops, local position oscillation, and recovery thrashing are classified separately. |
| 11 | Failure flight recorder | A bounded pre-failure window is copied to a separate episode file when a new stuck classification appears. |
| 12 | Recovery outcome | A recovery is counted as successful only when later semantic progress is observed. |
| 13 | Deterministic level 10–15 dispatch | The controller starts the tested career fixture but never performs its plan steps itself. |
| 14 | Free-pick level 15–20 dispatch | At an idle stage boundary, the controller starts the existing universal Victoria training plan. |
| 15 | Operator commands and reports | Run, status, per-Agent detail, report, and stop commands expose bounded experiments without direct gameplay mutation. |
| 16 | Bounded persistence and validation | One asynchronous writer, non-critical shedding, strict tuning, JSON schemas, unit tests, and boundary tests keep diagnostics from destabilizing Agent ticks. |

## Evidence layout

Each run writes under:

```text
.runtime/agents/journeys/<run-id>/
├── manifest.json
├── agents/
│   └── <character-id>-<ign>.jsonl
├── failures/
│   └── <character-id>-<ign>-<episode-sequence>.jsonl
└── summaries/
    ├── cohort.json
    ├── report.md
    ├── agents.csv
    ├── quests.csv
    ├── maps.csv
    └── resources.csv
```

The versioned schemas live in
`src/main/resources/agents/journey/schemas`. JSONL schemas validate one line at a time.

### Agent summary

Records career, start/end level, total EXP earned across level boundaries, gross mesos gained and
spent, net mesos, elapsed time, kills, completed quests, recovery attempts, recoveries followed by
progress, stuck episodes, maps visited, terminal plan/objective, and failure reason.

### Quest summary

Records Agent, quest, job and level when the quest began, start/end maps, duration, EXP gain across
level boundaries, meso delta, and item delta. This permits comparison by class, level, quest, and
hunt route.

### Map summary

Records visits, total dwell, kills, and recoveries. Long dwell is not automatically failure:
`MAP_DWELL` requires both excessive dwell and a semantic-progress stall.

### Resource summary

Records starting and ending quantity, net change, total acquired, and total consumed/sold. Periodic
inventory reconciliation covers mutations that do not yet publish a dedicated Agent event.

## Commands

Spawn or activate the reusable test Agents in the same Agent cohort as the GM, then run:

```text
!journey run victoria-lv10-20 10 full
```

`10` is the participant count. Careers rotate in this order:

```text
warrior, bowman, magician, thief-dagger, pirate-knuckle
```

The final argument is an experiment label (`off`, `light`, or `full`) retained in the manifest.
The Agent engine's normal observation-aware simulation tier remains authoritative; the Journey
controller does not force a tier or change combat personality.

Inspect a run:

```text
!journey status <run-id>
!journey agent <run-id> <ign>
!journey report <run-id>
```

Stop the run and cancel only the universal plan dispatched by it:

```text
!journey stop <run-id>
```

The run ID may be omitted from `status`, `report`, and `stop` to select the newest in-memory run.
Per-Agent inspection always requires the run ID and IGN.

## Proposed validation sequence

### Smoke

```text
!journey run victoria-lv10-20 1 full
!journey status
!journey agent <run-id> <ign>
!journey report <run-id>
```

Confirm the manifest is written, the Agent is reset only once, JSONL sequences increase, and
report generation does not cancel the active plan.

### Five-career parity

```text
!journey run victoria-lv10-20 5 full
```

Confirm exactly one of each career reaches the normal first-job/instructor path, continues through
the expected home/rotation pack, and enters the free-pick pool after level 15.

### Repetition target

```text
!journey run victoria-lv10-20 25 light
```

This supplies five samples per career. Compare `quests.csv` and `maps.csv` for route variance,
duration outliers, prerequisite gaps, and class-specific failures.

### Failure evidence

During a bounded test, deliberately make one route unavailable or remove required supplies. Do not
repair it through the Journey subsystem. Confirm the applicable stuck classification appears and a
flight-recorder file contains the preceding plan, map, counter, and recovery evidence.

### Scale gate

```text
!journey run victoria-lv10-20 100 off
```

Use only after 1-, 5-, and 25-Agent runs are clean. Verify dropped non-critical samples remain zero
or acceptably low, the game tick stays healthy, and critical failure records remain present. Larger
detailed cohorts require raising `MAX_DETAILED_AGENTS` only after disk and allocation evidence.

## Success analysis

For autonomous level-20 consistency, group results by:

- career and level at quest start;
- quest completion rate and p50/p95 duration;
- map dwell and map-transition loop count;
- recovery attempts versus recoveries followed by progress;
- HP/MP potion, ammunition, return-scroll, and meso use;
- EXP, mesos, and item gain per quest and per elapsed minute;
- terminal plan/objective and stuck classification.

The headline success rate is `succeeded / participantCount`. A target above 95% should be accepted
only when repeated cohorts also have bounded p95 completion time, no reserved-item violations, no
silent participant loss, and explainable failure episodes.

## Boundaries and operational behavior

- Universal plans remain the only progression lifecycle authority.
- Capability implementations remain the only gameplay mutation authority.
- Journey sampling reads live state; it never edits that state.
- The controller may start a universal plan only when the Agent is idle at a stage boundary.
- Evidence writing occurs on one daemon writer with a bounded queue.
- Queue pressure drops older non-critical samples before critical lifecycle/failure evidence.
- `!journey report` is non-mutating. `!journey stop` is explicitly mutating and cancels the plan
  that the experiment dispatched.
- Turning `server.agents.journey.AgentJourneyConfig.ENABLED` off disables new runs and leaves
  ordinary Agent execution unchanged.
