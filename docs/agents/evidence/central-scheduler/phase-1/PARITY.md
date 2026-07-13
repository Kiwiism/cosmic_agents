# Phase 1 Parity

Verified automatically:

- legacy registration still invokes the supplied per-Agent scheduler;
- central-sequential ordering, cadence, cancellation, pause, and failure
  isolation remain covered;
- deterministic 50/100/250/500 session callback totals match Phase 0;
- immediate lifecycle ticks observe a published, indexed runtime entry;
- failed scheduling rolls registry publication back;
- replacement invalidates the old generation without invalidating the new one;
- repeated registration is idempotent and cross-leader replacement leaves one
  indexed live entry;
- dismissal, cleanup, and leader transfer retain their existing side-effect
  order.

Broader focused anchors also cover trade notifications, runtime cleanup,
grind-loot filtering, supplies, combat, follow targeting, scripted follow,
whisper routing, and target snapshots after their fixtures were moved to the
explicit registry mutation API.

Not proven by this phase:

- live-client movement, combat, loot, dialogue, or map-transfer parity;
- production load characteristics;
- central-sharded safety or behavior.
