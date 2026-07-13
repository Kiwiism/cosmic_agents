# Phase 7 Source Scans

Mandatory scans were rerun before the phase commit.

`AgentSchedulerRuntime.schedule/register` matches remain limited to the central
loop/wake implementation, global population scheduling, generation-scoped
Amherst and airshow callbacks, and the navigation debug overlay.

`TimerManager` appears only in `CosmicSchedulerGateway`, the documented Cosmic
scheduling adapter.

The production Agent blocking scan for timed `get`, `join`, and `Thread.sleep`
returned no matches.

Legacy mailbox/central compatibility property matches remain in their runtime
selectors and tests. `ScheduledFuture` matches remain lifecycle handles,
central/global scheduler ownership, scoped delayed cleanup, and integration
gateway signatures; central modes do not create one repeating future per Agent.

Phase 7 adds no blocking call, file/SQL/network operation, unbounded queue, or
new per-Agent timer ownership.
