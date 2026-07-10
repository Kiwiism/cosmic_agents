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
ThreadManager general/blocking/database lane saturation
TimerManager active/queued/task count/completed
EXP logger queued/capacity/accepted/persisted/dropped/failures
character save slow warnings
character save clean autosaves skipped and section write counts
disconnect slow warnings
slow DB acquire warnings
map dormant ticks skipped and unload/load counts
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

Before Stage 1, complete a two-client acceptance gate on the exact packaged
build being soaked. Record pass/fail and a timestamp for each item rather than
using a single overall observation:

```text
both accounts authenticate and enter the same map
logout and immediate relogin leave neither account stuck online
each client changes channel and returns
map movement, combat, drop creation, and item pickup work
NPC prompts accept valid replies and reject replayed/mismatched replies
shop open, normal purchase, sell, and recharge work
quest progress changes and survives an autosave/relogin
inventory stack count and one equipped item's stats survive autosave/relogin
skill level and unused SP survive autosave/relogin
key binding, quickslot, and skill macro changes survive autosave/relogin
monster-book card count survives autosave/relogin
pet name, level, closeness, fullness, summon state, and ignores survive relogin
mount level, EXP, and tiredness survive autosave/relogin
storage items, slots, and mesos survive autosave/relogin
buddy visibility/group and capacity survive autosave/relogin
saved location, regular rock, and VIP rock destinations survive autosave/relogin
Cash Shop balances, account inventory, and wishlist survive entry/exit/relogin
family reputation and character event/area state survive autosave/relogin
logout, Cash Shop, MTS, channel/server transition, save-all, world-warp, and manual save paths each produce a full checkpoint
mutate one field during an intentionally slow save; the next autosave persists it
force one character-save SQL failure; the next save retries without losing the mutation
manual shutdown reaches terminal channel state
restart accepts both accounts without DB cleanup
```

Launching two clients or observing two login sockets is only partial evidence;
it does not satisfy this gate without the authenticated scenarios above.

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
EXP logger dropped/failure counts remain zero
dirty-save periodic full checkpoints complete without save failures
clean autosaves are skipped between mutations
per-section write counts match the exercised mutation category
```

Record exact numbers before moving to the next stage.

## Map Unload Rollout

Keep `cosmic.maps.idleUnloadEnabled=false` for the initial 24-hour baseline.
First validate dormant tick skipping, map re-entry spawn state, PQ/event maps,
shops/merchants, reactors, scripted entries, and transports. Enable unloading
only for a separate canary stage, then compare loaded-map high watermarks and
reload behavior before continuing.

## Dirty Persistence Soak Controls

Keep the production default during the normal soak. For a conservative control
run, start the JVM with:

```text
-Dcosmic.persistence.fullCheckpointAutosaves=1
```

This makes every autosave a full checkpoint without changing player-visible
behavior. Compare character-save count, skipped saves, per-section writes,
average/max save latency, DB waiters, and save failures against the default
interval. Restore the default after the control run; do not encode the soak
override in `config.yaml`.

## Exact Pre-Soak Verification Commands

```powershell
# Build the packaged Java 21 server.
.\mvnw.cmd -q -DskipTests package

# Real-MySQL merchant transaction checks (isolated fixtures, cleaned in finally).
.\mvnw.cmd -q "-Dcosmic.test.mysql=true" `
  "-Dtest=client.inventory.ItemFactoryMerchantMySqlIntegrationTest,client.processor.npc.FredrickRetrievalMySqlIntegrationTest" test

# Conservative persistence comparison run; do not put this in config.yaml.
java -Dcosmic.persistence.fullCheckpointAutosaves=1 -jar target/Cosmic.jar
```

For each duration stage, capture `!serverhealth` at start, hourly, before
shutdown, and after restart. The snapshot now includes world account/storage,
family, Messenger, shop/merchant, and transition-buff cache counts. A cache
that only rises after its owners leave fails the stage even if heap has not yet
crossed a threshold.
