# Phase 1 Metrics

Phase 1 intentionally changes scheduler ownership APIs, not execution cost or
cadence. The deterministic Phase 0 dispatcher measurements remain the parity
baseline.

Automated observations:

- 50/100/250/500 central-sequential callback totals remain deterministic.
- the 500-session harness completes 10,000 isolated dispatcher callbacks.
- active-session validation is now one concurrent-map lookup by Agent
  character ID plus generation comparison.
- lifecycle leader-list scans are no longer used for scheduler hot-path
  session validation.
- scheduler queue, gameplay CPU, heap, GC, DB, and packet-latency production
  measurements are unchanged and remain unproven without live/soak evidence.

This phase does not claim production throughput improvement. The global
central-sequential registration scan and due-list sort remain until Phase 3.
