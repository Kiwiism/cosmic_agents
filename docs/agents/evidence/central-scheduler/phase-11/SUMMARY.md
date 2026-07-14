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

This evidence validates scheduler mechanics only. Phase 11 and the production
default change remain blocked on live-client behavior and sustained server
soaks.

The live-gate preflight now verifies the scheduler branch and build, shared WZ
junction, external runtime/cache paths, free server ports, running client, and
an explicitly pinned disposable database before a live process is started. It
is read-only and rejects the normal `cosmic` database.

The first guarded disposable-database smoke reached all server listeners and
accepted a v83 login connection, then completed bounded Agent/runtime and
channel shutdown. Client capture failed before authentication, so no Agent was
spawned and visible parity remains unproven. See `LIVE_SMOKE_2026-07-14.md`.

The final local validation reran the explicit 2,000-session scale gates after
the observability work; they passed. See `REMAINING_RISKS.md` for the unrelated
failures observed during an optional incomplete full-repository test run.
