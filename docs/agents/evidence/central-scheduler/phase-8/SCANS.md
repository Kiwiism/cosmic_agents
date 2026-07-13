# Phase 8 Scans

The mandatory scheduler-runtime scans were rerun for:

- direct `AgentSchedulerRuntime.schedule/register` use;
- `TimerManager` references under Agent production code;
- blocking `Future.get`, `join`, and `Thread.sleep` patterns;
- mailbox and central compatibility properties;
- `ScheduledFuture` ownership.

No new blocking scheduler call, unbounded queue, direct due-heap mutation, or
legacy per-Agent timer owner was introduced by tick slicing. Existing allowed
legacy scheduling, compatibility configuration, explicit tool paths, and
documented integration references remain unchanged.
