# Populated 1,000-Agent Gate Attempt - 2026-07-15

## Result

`ABORTED_HOST_MEMORY_GUARD`

This attempt did not satisfy the 1,000-Agent gate and must not be reported as
a pass. The disposable roster was successfully expanded to 1,000 real
Agent-only backing characters, but the live server run was stopped at 220
sessions when host free physical memory crossed the predeclared 2 GiB safety
threshold.

## Provisioning

- deterministic names: `Sched0001` through `Sched1000`;
- existing backing characters reused: 500;
- new backing characters created through normal Agent gateways: 500;
- schema changes: none;
- normal `cosmic` database used: no;
- 1,000-Agent preflight: all 11 checks passed.

## Runtime Configuration

- scheduler: `central-sharded`;
- shards: 4;
- base tick: 50 ms;
- simulation policy: enabled;
- background-active tick: 250 ms;
- background abstraction: disabled;
- tick slicing: disabled;
- load shedding: disabled;
- server-only: no MapleStory observer connected.

## Safety Stop

Health remained `load=NORMAL`, with 244 loaded maps, zero DB waiters, and no
failed Agent update. Samples were:

| Live Agents | Java private | Java working set | Host free memory |
|---:|---:|---:|---:|
| 180 | 836 MiB | 782 MiB | 6.8 GiB |
| 200 | 836 MiB | 782 MiB | 14.6 GiB |
| 220 | 837 MiB | 735 MiB | 1.8 GiB |

The server was stopped immediately at the final sample. After Java exited,
host free memory was still 1.2 GiB. A non-server Codex desktop process then
held about 20.8 GiB working set and 38.5 GiB private memory, while the stopped
server had used less than 1 GiB. This makes the abort a host-capacity guard,
not evidence of an Agent scheduler memory leak. A clean host is required for
the next attempt.

## Partial Scheduler Evidence

At shutdown the scheduler reported 18,744 cycles, 165,601 Agent updates, zero
failed updates, lag p50/p95/p99 of 23/49/51 ms, work p50/p95/p99 of
89.1/483.6/1,551.1 us, 292 budget exhaustions, 929 deferred work items, and
zero starvation or map-budget deferrals.

Shutdown observed and cancelled all 220 sessions, left zero registrations and
pending async requests, stopped all three initialized async executors with no
unterminated lane, and completed Agent runtime cleanup in 70 ms. All 220
characters saved and all worlds/channels reached offline. The normal `cosmic`
configuration was restored afterward.

## Retry Requirement

Repeat this same configuration on a host with at least 8 GiB reliably free
after all tooling and IDE processes are accounted for. Do not proceed to the
1,500- or 2,000-Agent stages until the 1,000-Agent run reaches full population,
holds long enough for normalized post-GC evidence, and shuts down cleanly.
