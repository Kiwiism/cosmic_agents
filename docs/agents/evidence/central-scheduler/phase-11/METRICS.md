# Phase 11 Metrics

Deterministic local gates:

| Gate | Population | Cadences | Expected updates | Result |
|---|---:|---:|---:|---|
| central-sequential | up to 2,000 | 20 | population x 20 | pass |
| four concurrent shards | 2,000 | 20 | 40,000 | pass |

The four-shard gate also requires:

- registration imbalance below 100 sessions;
- zero overlapping executions for every session;
- zero registrations after cancellation;
- zero owned registrations after cancellation;
- zero due, ready, and ingress entries after cancellation.

Runtime operator evidence is available through the bounded GM6
`@agentscheduler` command. The command reads the same rolling metrics used by
the deterministic gates and includes initialized async-lane depth, capacity,
high-water, rejection, timeout, stale, expired, and drained counts. It also
reports registration state and global/per-shard ready-priority current and
high-water depth, cycle-budget utilization, and lifecycle
register/replace/cancel/cleanup counts. `@agentscheduler costs`
reports bounded p50/p95/p99 work-class, simulation-mode, and tick-slice cost.
Use `@serverhealth` for the complementary JVM, database, and core server
snapshot.

Detail diagnostics expose bounded current registration cost/overdue state,
active map population, mailbox depth, current failure-window count, and the
existing instrumented capability totals. They retain no new per-tick history
and cap every operator ranking at ten rows.

The shutdown report records sessions observed, schedule cancellation requests,
session IDs that failed cancellation, pending async requests invalidated,
remaining scheduler registrations, async executors stopped, queued tasks
cancelled, unterminated lanes, interruption/timeout state, elapsed time, and the
final scheduler snapshot.

Authenticated one-Agent live smoke:

| Metric | Result |
|---|---:|
| registered Agents | 1 |
| initialized shard | 3 |
| cycles / Agent updates | 33,848 / 30,419 |
| failed / slow Agent updates | 0 / 3 |
| queue lag p50 / p95 / p99 | 42 / 50 / 51 ms |
| work p50 / p95 / p99 | 187.8 / 418.2 / 598.8 us |
| maximum cycle | 948.3035 ms warm-up outlier |
| budget exhaustion / deferred / starvation | 0 / 0 / 0 |
| shutdown sessions / cancellations / remaining | 1 / 1 / 0 |
| shutdown pending async / unterminated | 0 / 0 |
| Agent shutdown elapsed | 631 ms |

The live server reported `Load level: NORMAL`, zero database-pool waiters, and
zero failed saves. Heap plateau, GC pause, real-player latency under load,
scheduler p95/p99 stabilization at roster scale, and long-run shutdown trends
still require the staged populated soaks.

Populated 250-Agent server-only comparison:

| Metric | Central sequential | Central sharded (4) |
|---|---:|---:|
| Agent updates | 1,447,200 | 1,707,059 |
| failed / slow updates | 0 / 1 | 0 / 4 |
| queue lag p50 / p95 / p99 | 47 / 65 / 68 ms | 21 / 48 / 55 ms |
| work p50 / p95 / p99 | 164.6 / 558.0 / 2,642.2 us | 139.9 / 396.3 / 1,414.2 us |
| budget exhaustion | 37,158 | 34,252 |
| deferred work | 4,236,431 | 614,852 |
| ingress high-water | 250 | 74 |
| shutdown sessions / cancellations / remaining | 250 / 250 / 0 | 250 / 250 / 0 |

Legacy also reached and drained 250 sessions; centralized scheduler counters
correctly remained zero in that mode. These short server-only runs demonstrate
bounded mechanics and clean lifecycle behavior, not a normalized performance
benchmark or a replacement for client-visible and long-duration evidence.

The post-population-lane four-shard safety rerun reached 250 online characters
and remained at `loadLevel=NORMAL`. Its final snapshot recorded zero failed
updates, 25/46/49 ms queue-lag p50/p95/p99, 109.2/245.5/892.1 us work
p50/p95/p99, and 381,706 deferred items. Shutdown stopped three initialized
async executors with no unterminated lane, cancelled all 250 sessions, and
left zero scheduler registrations. This short rerun verifies the new lane and
shutdown boundary; it is not added to the normalized comparison table.
