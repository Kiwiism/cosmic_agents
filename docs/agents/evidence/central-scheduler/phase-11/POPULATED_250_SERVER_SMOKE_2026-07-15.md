# Populated 250-Agent Server Smoke - 2026-07-15

## Scope

This server-only smoke exercised the same 250 real Agent-only backing
characters in three scheduler modes:

1. `legacy`;
2. `central-sequential`;
3. `central-sharded` with four shards.

The characters were created on `cosmic_scheduler_soak_20260714` through the
normal Agent account, account-lock, and backing-character gateways. The
external roster was enabled at multiplier `1.0`; simulation, background
abstraction, tick slicing, and load shedding remained disabled. The normal
`cosmic` database was never used for the runs and was restored in
`config.yaml` afterward.

This is scheduler and lifecycle evidence. No MapleStory client was connected,
so it does not satisfy the one- or two-observer visual parity gates.

## Roster

- deterministic names: `Sched0001` through `Sched0250`;
- managed records: 250;
- Agent-only backing accounts: 250;
- new records created in the expansion run: 249;
- eligible dry-run record reused: 1;
- external file: `%TEMP%/cosmic-agent-scheduler-live-gate/population.json`;
- schema changes: none.

The live-gate preflight passed all 11 checks before every mode, including the
branch, clean/config-override boundary, WZ junction, packaged artifact,
explicit disposable database, external runtime root, free ports, roster
shape/uniqueness, and 250-Agent configured target.

## Results

| Metric | Legacy | Central sequential | Central sharded (4) |
|---|---:|---:|---:|
| live sessions at shutdown | 250 | 250 | 250 |
| cancellations requested | 250 | 250 | 250 |
| remaining registrations | 0 | 0 | 0 |
| pending async / unterminated lanes | 0 / 0 | 0 / 0 | 0 / 0 |
| Agent shutdown elapsed | 20 ms | 18 ms | 60 ms |
| scheduler cycles | n/a | 38,295 | 65,663 |
| Agent updates | n/a | 1,447,200 | 1,707,059 |
| failed / slow updates | n/a | 0 / 1 | 0 / 4 |
| lag p50 / p95 / p99 | n/a | 47 / 65 / 68 ms | 21 / 48 / 55 ms |
| work p50 / p95 / p99 | n/a | 164.6 / 558.0 / 2,642.2 us | 139.9 / 396.3 / 1,414.2 us |
| budget exhaustion | n/a | 37,158 | 34,252 |
| deferred work | n/a | 4,236,431 | 614,852 |
| failed / starvation / map deferral | n/a | 0 / 0 / 0 | 0 / 0 / 0 |
| ingress high-water | n/a | 250 | 74 |

Four shards reduced p95/p99 lag and deferred work materially compared with
the sequential bridge. The comparison is directional rather than a formal
benchmark: startup cache state, map distribution, warm-up timing, and the
short full-population observation window were not normalized.

## Server Health

The periodic health sample remained `load=NORMAL`, with zero database-pool
waiters and zero failed character saves. At the available samples:

- legacy had 240 live characters, 409 MiB used heap, and 610 queued core timer
  tasks while population was still converging;
- central sequential had 240 live characters, 390 MiB used heap, and 370
  queued core timer tasks;
- central sharded had 220 live characters, 701 MiB used heap, and 349 queued
  core timer tasks.

Ad hoc process samples near full population showed about 1.0-1.5 GiB private
memory and 56-57 threads. These short samples do not establish a heap plateau.
Navigation graph warm-up and pathfinding produced the observed startup slow
ticks. No Agent tick failed.

## Shutdown

Every run first drained all Agent scheduler registrations, then saved the 250
characters during channel shutdown. Runtime scheduler cleanup remained below
60 ms. End-to-end channel shutdown took roughly 10-16 seconds because
character persistence is intentionally outside the scheduler cancellation
measurement. All worlds and channels reached the offline state.

## Remaining Boundary

- repeat 250 with one and two observing clients;
- provision and run 500 central-sequential and central-sharded gates;
- validate population convergence without blocking timer/scheduler workers;
- continue 1,000/1,500/2,000 staged and long-duration gates;
- preserve `legacy` as the production default.

## Population Async-Lane Verification

After population reconciliation moved off timer workers, a second guarded
four-shard server-only run used the same 250-character roster and disposable
database. Preflight again passed all 11 checks. The first health sample after
full convergence reported 250 online characters, 244 loaded maps, and
`loadLevel=NORMAL`; a second sample 92 seconds later remained at 250 and
`NORMAL`. The normal `cosmic` configuration was restored after shutdown.

The final scheduler snapshot reported 57,388 cycles, 1,813,207 updates, zero
failed updates, lag p50/p95/p99 of 25/46/49 ms, work p50/p95/p99 of
109.2/245.5/892.1 us, 23,035 budget exhaustions, 381,706 deferred work items,
and zero starvation or map-budget deferrals. These values are a short safety
sample, not a normalized benchmark.

Shutdown observed and cancelled all 250 sessions, left zero registrations and
pending async requests, stopped three initialized async executors including
the population lifecycle lane, and reported no unterminated executor or
timeout. Agent runtime shutdown took 61 ms. All 250 characters were then saved
and all worlds/channels reached the offline state. No late population session
appeared behind shutdown cleanup.
