# Agent Journey Observatory

## Purpose

The Journey Observatory runs a bounded cohort through an existing Agent OS progression path and
produces enough evidence to explain success, delay, resource use, recovery, or failure for every
Agent. Its evidence projection is read-only. A scenario may perform a declared admission fixture
through an existing capability service and may dispatch a universal plan at a stage boundary; it
does not execute quest steps, move characters, award rewards, repair navigation, or bypass the
universal plan executor.

The clean-start sustainability scenario is `victoria-lv1-21`:

1. It admits 25 already-live reusable Agents, resets each participant atomically to a legitimate
   level-1 Maple Island start, and dispatches `maple-island-full-mvp` itself.
2. Career allocation is five Agents per Explorer family. Thief builds alternate dagger/claw and
   Pirate builds alternate knuckle/gun.
3. Maple Island hands off to the existing first-job and level-15 plan, then to resumable individual
   Victoria quests and level-appropriate Hunting until level 21.
4. TownLife is disabled for this scenario. Shopping, chairs, low-risk income recovery, questing,
   and Hunting continue through their existing public contracts.
5. Participants receive no cohort resource transfers. They preserve legitimate starting resources,
   sell only unreserved surplus ETC items, retain a wallet reserve, buy supplies (including an
   initial throwing-star or bullet stack), and may sit on an owned Relaxer or Sky Blue Chair.
6. An objective stays suspended while resupply recovery runs. Recovery is bounded; a participant
   ends as `SUCCEEDED` at level 21 or `STALLED` with an explicit reason.
7. The `decisions` evidence mode retains plans, quest and map choices, resource maintenance,
   recovery, progression, and terminal reasons while omitting individual combat actions.

The older `victoria-lv10-20` scenario remains available:

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

Populate the GM cohort first. Wait until all 25 reusable Agents are live; early progress is safe
because Journey owns and atomically reapplies the clean-start baseline on admission:

```text
!mapleisland run 25 25 1 off
!journey run victoria-lv1-21 25 decisions
```

Do not start the full run merely to validate a build. Use one participant for the smoke gate:

```text
!mapleisland run 1 1 1 off
!journey run victoria-lv1-21 1 decisions
```

Careers are balanced across Explorer families; alternate builds are deterministic:

```text
warrior, bowman, magician, thief-dagger/thief-claw, pirate-knuckle/pirate-gun
```

For `victoria-lv1-21`, use `decisions` unless detailed combat evidence is specifically needed.
The older scenario also accepts `off`, `light`, and `full`.
The Agent engine's normal observation-aware simulation tier remains authoritative; the Journey
controller does not force a tier or change combat personality.

Inspect a run:

```text
!journey status <run-id>
!journey agent <run-id> <ign>
!journey report <run-id>
```

For periodic unattended inspection, use `!journey status` for the cohort, then
`!journey agent <run-id> <ign>` for any delayed participant. The durable evidence is under
`.runtime/agents/journeys/<run-id>/agents/`; `summaries/report.md` and the CSV summaries are
refreshed by `!journey report <run-id>` without mutating gameplay.

Stop the run and cancel only the universal plan dispatched by it:

```text
!journey stop <run-id>
```

The run ID may be omitted from `status`, `report`, and `stop` to select the newest in-memory run.
Per-Agent inspection always requires the run ID and IGN.

## Proposed validation sequence

### Clean-start smoke

```text
!mapleisland run 1 1 1 off
!journey run victoria-lv1-21 1 decisions
!journey status
!journey agent <run-id> <ign>
!journey report <run-id>
```

Confirm the manifest is written, the Agent is reset only once, JSONL sequences increase, and
report generation does not cancel the active plan.

### Five-career parity

```text
!journey run victoria-lv1-21 5 decisions
```

Confirm exactly one of each career reaches the normal first-job/instructor path, continues through
the expected home/rotation pack, and enters the free-pick pool after level 15.

### Repetition target (only after smoke/parity pass)

```text
!journey run victoria-lv1-21 25 decisions
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
