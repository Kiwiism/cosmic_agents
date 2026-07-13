# Phase 3 Metrics

The deterministic dispatcher harness runs 50, 100, 250, and 500 sessions for
20 cadences. At 500 sessions it proves:

```text
expected callbacks: 10000
observed callbacks: 10000
ingress capacity: 512 in the focused bounded test
ingress high-water mark: 500
ingress depth after first drain: 0
scheduled heap records after run: 500
registration scan/sort per cycle: removed
```

The production default ingress capacity is 4096. The focused capacity test uses
512 to prove the 500-session burst remains bounded, while a two-record test
proves admission rejection and cancellation behavior at saturation.

These are deterministic in-process dispatcher measurements. They do not
represent gameplay CPU, packet volume, database load, live-client latency, GC,
or a sustained server soak. Rolling p50/p95/p99 delay and cost metrics belong to
Phase 4.
