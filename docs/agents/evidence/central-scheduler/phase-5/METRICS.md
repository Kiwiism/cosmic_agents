# Phase 5 Metrics

Implemented bounded defaults:

```text
navigation graph workers/queue:       1 / 64
fast navigation workers/queue:        1 / 64
persistence workers/queue:            1 / 256
LLM/network workers/queue:             2 / 64
catalog workers/queue:                 1 / 32
economy analysis workers/queue:        2 / 128
duration samples retained per lane:    2048
```

Focused tests prove independent lane capacity, concurrent progress in separate
lanes, saturation rejection without pending-state growth, stale-session
rejection, latest-request selection, timeout classification, and mailbox-owned
completion application.

These are deterministic unit/integration results. They do not measure real DB,
HTTP, graph-build, gameplay CPU, packet load, GC, or live-client latency.
