# Phase 4 Metrics

Deterministic tests prove these policy outcomes:

```text
priority order under a two-item guard: CRITICAL, VISIBLE, then BACKGROUND
500 simultaneously due records: 256 first cycle, 244 immediate continuation
lost callbacks across continuation: 0
learned background cost: 8 ms
cycle budget in cost test: 10 ms
remaining time after first learned callback: 2 ms
second learned callback: deferred, then completed on continuation
starvation interval: 50 ms in focused test
deferred-to-interactive promotions before execution: 4
rolling global/work-class window capacity: 2048 samples
```

The 500-record test is deterministic dispatcher work with zero simulated
callback cost. The 8 ms cost test uses a fake monotonic clock. These results do
not measure real gameplay CPU, packet load, DB latency, GC, or live-client
responsiveness.
