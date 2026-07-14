# Phase 11 Pre-Soak Summary

Baseline commit: `d1ea0f3ecd`

The locally automatable part of Phase 11 is complete:

- central-sequential cadence runs 50, 100, 250, 500, 1,000, 1,500, and 2,000
  isolated sessions for 20 periods;
- four stable-hash shards run 2,000 sessions for 20 periods (40,000 updates);
- the sharded gate detects overlapping execution of one session;
- cancellation drains registration, owned-registration, due-heap, ready, and
  ingress state back to zero;
- legacy parity and rollback tests remain in the focused scheduler suite.
- GM6 `@agentscheduler` exposes a bounded, read-only snapshot of scheduler,
  shard, load-shedding, quiescence, and Agent async-queue metrics for live
  evidence capture.
- bounded top slow/overdue/map/capability/mailbox/failure views and Agent/map
  drill-down expose current pressure without retaining event history or
  dumping the full Agent population;
- live registration state, global/per-shard priority depth and high-water,
  cycle-budget utilization, lifecycle transitions, mailbox outcomes, and
  bounded scheduler cost windows complete the locally automatable scheduler
  observability gate;
- process shutdown now closes admission, cancels session schedules, drains
  sequential/sharded state, invalidates pending async requests, stops bounded
  Agent executors, and publishes a structured final snapshot before Cosmic
  channel/timer teardown;
- deterministic shutdown tests cover timeout, eventual cleanup, idempotency,
  and clean restart admission.

This evidence validates scheduler mechanics plus a partial authenticated
one-Agent live-client smoke. Phase 11 and the production default change remain
blocked on complete live parity, multi-client validation, and sustained server
soaks.

The live-gate preflight now verifies the scheduler branch and build, shared WZ
junction, external runtime/cache paths, free server ports, running client, and
an explicitly pinned disposable database before a live process is started. It
is read-only and rejects the normal `cosmic` database.

The guarded disposable-database smoke authenticated a v83 client, provisioned
and spawned one Agent through the normal command path, and visibly exercised
same-map movement/navigation, basic combat, and loot/meso behavior. GM6
diagnostics confirmed one central-sharded registration, and shutdown drained
the live session with no remaining work. See `LIVE_SMOKE_2026-07-14.md`.

A guarded test-only provisioner subsequently created a 250-character external
roster through the same normal backing-character gateways. Server-only runs in
legacy, central-sequential, and four-shard modes each reached 250 sessions and
drained every session on shutdown with zero scheduler failures or remaining
work. Four shards reduced p95/p99 lag and deferred work compared with the
sequential bridge. See `POPULATED_250_SERVER_SMOKE_2026-07-15.md`. Client
parity and sustained soak gates remain open.

Population reconciliation now runs on a bounded single-worker Agent async
lane rather than the timer callback. A post-change four-shard run again
reached 250 sessions at `loadLevel=NORMAL`; shutdown cancelled all 250,
reported zero remaining registrations, and stopped all three initialized
async lanes with no timeout. The existing 20-action limit and stable roster
ordering remain unchanged.

The final local validation reran the explicit 2,000-session scale gates after
the observability work; they passed. See `REMAINING_RISKS.md` for the unrelated
failures observed during an optional incomplete full-repository test run.
