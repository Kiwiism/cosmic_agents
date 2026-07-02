# Soak Test Checklist

Target:

```text
500 concurrent players
2000 concurrent agents
30 days uptime
```

Use this checklist to make long-running stability measurable. A soak test should
record the same fields at fixed intervals so regressions are visible instead of
anecdotal.

Agent-specific implementation details:

- `docs/agents/AGENT_SOAK_TEST_IMPLEMENTATION_SPEC.md`

## Test Stages

```text
Stage 1: 24 hours, no agents, baseline player/server load
Stage 2: 24 hours, 100 agents
Stage 3: 72 hours, 500 agents
Stage 4: 7 days, 1000 agents
Stage 5: 30 days, 2000 agents
```

Do not skip the baseline stage. It separates server leaks from agent-driven
load.

## Capture Interval

Record every 5 minutes:

```text
timestamp
uptime
online players
online agents
loaded maps
active maps
heap used MB
heap max MB
GC count/time if available
DB pool active/idle/total/waiting
ThreadManager active/queued/pool/completed/submitted/rejected
TimerManager active/queued/task count/completed
character save slow warnings
disconnect slow warnings
slow DB acquire warnings
map unload/load counts when available
drop/mob/reactor counts when available
event instance count when available
merchant/shop count when available
```

Current server health log already emits:

```text
heap
DB pool stats
ThreadManager diagnostics
TimerManager active/queued
EXP debug cleanup count
```

## Required Scenarios

Run these during each stage:

```text
login/logout cycles
channel changes
map travel across many regions
mob killing and drops
item pickup
character save/autosave
manual saveall
quest lookup and quest completion
shop open/buy/sell
merchant open/close if enabled
party create/join/leave
event/PQ enter/exit if enabled
server shutdown/restart at end of stage
```

Agent stages should include:

```text
spawn/despawn waves
map travel
combat
looting
inventory pressure
shop/supply behavior if enabled
quest lookup pressure if enabled
shutdown/restart with agents online
```

## Failure Signals

Investigate if any of these appear:

```text
heap grows for hours and does not fall after GC
loaded map count only increases
DB waiting threads > 0 for repeated samples
ThreadManager rejected task count increases
TimerManager queue grows continuously
character save warnings become frequent
disconnect warnings become frequent
accounts stuck as already logged in
event instances remain after all players leave
merchant/shop owner state becomes inconsistent
shutdown takes longer each stage
```

## Heap Dump Triggers

Use `!heapdump` when:

```text
heap usage exceeds expected stage budget
heap keeps growing after forced low-activity period
GC pauses spike repeatedly
loaded map/object counts suggest retained references
before restarting a failing soak stage
```

Heap dumps are written to:

```text
logs/heapdumps/
```

## Stage Exit Criteria

A stage passes when:

```text
no stuck logged-in accounts
no continuous heap growth without explanation
no continuous loaded-map growth without explanation
DB waiting threads are normally zero
ThreadManager rejected tasks stay zero under expected load
slow save/disconnect warnings are rare and explainable
shutdown completes cleanly
restart works without manual DB cleanup
```

Record exact numbers before moving to the next stage.
