# Populated 500-Agent Server Gate - 2026-07-15

## Scope

This server-only gate exercised the same 500 real Agent-only backing
characters in `central-sequential` and four-shard `central-sharded` modes. The
disposable roster was expanded idempotently from 250 to 500 through the normal
Agent account, account-lock, and backing-character gateways: 250 records were
reused and 250 were created. No schema or WZ data changed.

Both runs used normal authoritative Agent gameplay with simulation,
background abstraction, tick slicing, and load shedding disabled. The 20
population actions per sweep and stable roster ordering were unchanged.
Preflight passed all 11 checks before both runs. No MapleStory client was
connected, so this is lifecycle, scheduler, and server-health evidence rather
than visual parity evidence.

## Results

| Metric | Central sequential | Central sharded (4) |
|---|---:|---:|
| full population | 500 | 500 |
| population convergence after runtime start | 17m 49s | 17m 50s |
| scheduler cycles | 100,711 | 156,422 |
| Agent updates | 7,516,252 | 7,962,111 |
| failed / slow updates | 0 / 2 | 0 / 8 |
| queue lag p50 / p95 / p99 | 31 / 68 / 75 ms | 17 / 42 / 48 ms |
| work p50 / p95 / p99 | 93.3 / 142.4 / 519.2 us | 131.3 / 259.6 / 513.3 us |
| budget exhaustion | 92,399 | 62,753 |
| deferred work | 15,186,756 | 1,909,683 |
| starvation / map-budget deferral | 0 / 0 | 0 / 0 |
| ingress high-water | 499 | 336 |
| shutdown sessions / cancellations / remaining | 500 / 500 / 0 | 500 / 500 / 0 |
| async executors / unterminated | 3 / 0 | 3 / 0 |
| Agent runtime shutdown | 509 ms | 76 ms |

The four-shard run reduced queue-lag p95/p99, budget exhaustion, and deferred
work substantially. This is directional evidence only: cache state, startup
warm-up, and the short full-population hold were not normalized.

## Server Health

Every sampled population step remained `load=NORMAL`, with 244 loaded maps,
zero database-pool waiters, and zero failed character saves before shutdown.
At the full-population periodic sample, sequential used 734 MiB heap and
sharded used 592 MiB heap.

Ad hoc process samples after a one-minute 500-Agent hold were:

| Process sample | Central sequential | Central sharded (4) |
|---|---:|---:|
| average CPU cores since process start | 1.30 | 1.02 |
| working set | 638 MiB | 1,526 MiB |
| private memory | 1,188 MiB | 2,140 MiB |
| threads | 60 | 58 |

The elevated sharded process-memory sample is not explained by the scheduler
metrics and is retained as a follow-up risk. A short single sample cannot
distinguish JVM reservation, cache state, paging, or a retained-heap trend;
the 1,000-Agent and sustained stages need normalized GC/heap evidence before
any memory conclusion.

## Shutdown

Both modes cancelled all 500 schedules, drained due/ready/ingress state,
cleared pending async requests, and stopped all three initialized Agent async
executors with no timeout. All 500 characters then saved and all channels and
worlds reached the offline state. End-to-end channel shutdown took about 36
seconds sequentially and 40 seconds sharded, dominated by character saves.

## Remaining Gates

- one- and two-observer 250-Agent visual parity;
- normalized 500-Agent repeat if memory attribution is needed;
- provision and run the 1,000-Agent mixed presentation/background-active gate;
- 1,500-Agent load-shedding and 2,000-Agent long-duration stages;
- explicit legacy rollback and restart rehearsal.
