# Central Scheduler Phase 0 Metrics

Environment date: 2026-07-13

Focused baseline suite before Phase 0 edits:

```text
result: PASS
wall time: 50.33 seconds
```

Focused suite with the completed Phase 0 population matrix:

```text
result: PASS
wall time: 36.19 seconds
```

The wall times are local observations, not service-level objectives. Maven and
JVM warmup differ between runs.

Deterministic callback matrix:

| Mode | Sessions | Cadences | Expected callbacks |
| --- | ---: | ---: | ---: |
| legacy per-Agent | 50 | 20 | 1,000 |
| legacy per-Agent | 100 | 20 | 2,000 |
| legacy per-Agent | 250 | 20 | 5,000 |
| legacy per-Agent | 500 | 20 | 10,000 |
| central sequential | 50 | 20 | 1,000 |
| central sequential | 100 | 20 | 2,000 |
| central sequential | 250 | 20 | 5,000 |
| central sequential | 500 | 20 | 10,000 |

The deterministic harness uses a fake clock and isolated callbacks. It records
functional cadence and callback counts, not CPU capacity or live server
latency. Current production metrics are cumulative counters only; rolling
p50/p95/p99 delay, shard pressure, and work-class cost belong to Phase 4.

Unavailable in Phase 0 automation:

- real-player packet latency.
- live Agent presentation parity.
- database and navigation workload pressure.
- heap/GC behavior under a sustained real server population.
- multi-hour and multi-day soak evidence.
