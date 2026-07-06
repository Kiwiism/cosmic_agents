# Agent Observability Design Specification

Purpose:

```text
Define the future observability package that explains what Agents are doing,
why they are doing it, how expensive they are, where they are blocked, and how
the platform behaves at 2000-Agent scale.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
Every Agent action should be explainable.
Every scaling bottleneck should be measurable.
Every metric path must be bounded.
```

Observability should make the system debuggable without adding heavy work to
every Agent tick.

## Goals

- Show per-Agent current mode, plan, objective, capability, and blocker.
- Show per-map Agent count and simulation-mode breakdown.
- Show scheduler queue depth, delay, and budget overruns.
- Show capability latency and failure rates.
- Show Event Bus queue pressure and dropped/compacted event counts.
- Show memory/cache counts for Agent state, route state, perception, journals,
  and profile/economy buffers.
- Show top slow Agents, top slow maps, and top failing objectives.
- Support soak tests and postmortem debugging.
- Feed future Agent Console views.

## Non-Goals

- Do not store full raw event streams in memory.
- Do not run expensive diagnostics every tick.
- Do not call LLM for explanations inline.
- Do not expose raw server objects.
- Do not replace server-side `!serverhealth`; consume or correlate with it.

## Observability Surfaces

### Agent Snapshot

Shows one Agent:

- agent id.
- display name.
- current map.
- simulation mode.
- active plan.
- current objective.
- active capability command.
- last result.
- last blocker.
- retry counts.
- HP/MP/resource summary.
- recent decision reason.
- current scheduler priority.
- last tick duration.

### Map Snapshot

Shows one map:

- map id/name.
- real player count.
- Agent count.
- mode breakdown.
- active/background/abstract Agents.
- top active plans.
- recent materializations.
- broadcast suppression eligibility.
- map-sensitive reason.
- average tick/capability cost for Agents on map.

### Scheduler Snapshot

Shows:

- queue depth.
- due/overdue work.
- p50/p95/p99 scheduler delay.
- per-priority work counts.
- budget overrun counts.
- shed/deferred work counts.
- top slow work kinds.

### Capability Snapshot

Shows by capability family:

- command count.
- success count.
- blocker count.
- timeout count.
- cancellation count.
- average/p95/p99 duration.
- top reason codes.
- active command count.

### Plan Snapshot

Shows:

- active plan counts.
- completed/failed plan counts.
- blocked objective counts.
- top blocker reason codes.
- average objective duration.
- sidetrack counts.
- resume/reconciliation counts.

### Event Bus Snapshot

Shows:

- published events.
- dropped/compacted events.
- queue depths by priority.
- consumer lag.
- durable append backlog.
- top noisy event types.

### Memory Snapshot

Shows:

- active Agent runtime entries.
- plan progress entries.
- route state entries.
- perception snapshots.
- combat target states.
- NPC/dialogue states.
- decision journals.
- profile memory buffers.
- economy observation buffers.

## Explanation Model

Each Agent should answer:

```text
What am I doing?
Why am I doing it?
What am I waiting for?
What blocked me last?
What will I try next?
How expensive was my recent work?
```

The explanation should combine:

- plan objective.
- profile influence.
- catalog facts.
- live server state.
- capability result.
- recovery decision.
- recent event history.

## Data Retention

Use layers:

- live snapshot: current compact state.
- rolling window: recent counters and samples.
- durable incidents: blockers, deaths, forbidden actions, materialization
  failures, LLM commands, profile patches.
- soak output: periodic CSV/JSON snapshots.

Do not keep unbounded per-tick history.

## Sampling

High-frequency telemetry should be sampled:

- per-Agent tick durations.
- perception refresh durations.
- route ETA updates.
- background combat slices.
- trace-level capability progress.

Always keep:

- terminal capability results.
- safety incidents.
- forbidden action blockers.
- plan completion/failure.
- materialization failures.

## Relationship To Server Health

Agent Observability should correlate with server-only diagnostics:

- DB pool pressure.
- timer lanes.
- save pressure.
- broadcast pressure.
- slow-operation thresholds.
- map activity.
- runtime cache counts.

Agent Observability should not require core server diagnostics to import Agent
runtime classes. Use a provider/adapter boundary.

## Agent Console Readiness

Future Agent Console pages should be able to query:

- current population overview.
- per-Agent details.
- plan/objective details.
- capability latency.
- blocked Agents.
- map distribution.
- economy/profile summaries.
- LLM command audit.
- soak test progress.

## Success Criteria

The Observability package is ready when:

- every Agent has a compact current snapshot.
- every capability terminal result is counted.
- every plan/objective blocker is visible.
- scheduler and Event Bus pressure are visible.
- memory/cache counts are bounded and visible.
- top slow Agents/maps/objectives can be reported.
- soak tests can export periodic snapshots.
- no metric path requires scanning all Agents every tick.
