# Agent Soak Test Implementation Specification

Purpose:

```text
Provide a repeatable soak-test harness for proving that the reconstructed Agent
engine can scale toward 2000 concurrent agents without harming server
responsiveness or corrupting agent/player state.
```

This is post-reconstruction implementation work. Server-only diagnostics may be
prepared earlier, but the scenario runner and agent simulation assertions depend
on the reconstructed Agent runtime.

## Scope

Primary target:

```text
2000 concurrent agents
```

Player scaling is intentionally separate. Player tests only need enough real or
synthetic player presence to verify that agent load does not break normal player
login, movement, map change, combat, NPC, shop, and shutdown behavior.

Initial world scope:

```text
Maple Island
Victoria Island
levels 1-30
no second job requirement
no Ossyria/Ludibrium/Aqua Road/Zipangu/Mu Lung/Leafre/Ariant/Magatia
Sleepywood fringe allowed only in targeted scenarios
```

## Design Goals

- prove agents can exist and make believable progress.
- prove player-facing server work stays responsive.
- prove memory, queues, timers, and DB pressure stay bounded.
- prove hidden-agent shortcuts do not produce impossible state.
- prove materialization works when players enter maps containing background
  agents.
- prove shutdown/restart does not require manual DB cleanup.
- capture enough metrics to compare runs across commits.

## Non-Goals

- proving 500 real-player capacity.
- validating full economy engine behavior.
- validating LLM behavior.
- validating every quest in the game.
- replacing unit/integration tests for individual capabilities.

## Test Stages

### Stage 0 - Baseline Server

Population:

```text
0 agents
1-5 real/synthetic players
```

Duration:

```text
30-60 minutes
```

Purpose:

- establish server baseline.
- verify health snapshot and logging.
- verify no agent-independent memory growth.

### Stage 1 - Small Agent Population

Population:

```text
50 agents
```

Duration:

```text
1 hour
```

Purpose:

- verify spawn/despawn.
- verify basic scheduler/tick metrics.
- verify no immediate runaway behavior.

### Stage 2 - Medium Agent Population

Population:

```text
250 agents
```

Duration:

```text
2-4 hours
```

Purpose:

- verify map spread.
- verify route/combat/loot plans.
- verify agent DB and Cosmic DB pressure separation.

### Stage 3 - Heavy Agent Population

Population:

```text
500 agents
```

Duration:

```text
4-8 hours
```

Purpose:

- verify scheduler queues and materialization.
- verify hidden-agent background action savings.
- verify no loaded-map growth trend.

### Stage 4 - Large Agent Population

Population:

```text
1000 agents
```

Duration:

```text
8-24 hours
```

Purpose:

- verify long-running memory stability.
- verify checkpoint/save queues under sustained activity.
- verify player interactions while agents are active.

### Stage 5 - Target Agent Population

Population:

```text
2000 agents
```

Duration ladder:

```text
24 hours
3 days
7 days
30 days
```

Purpose:

- prove target scale.
- verify long uptime.
- verify shutdown/restart.
- verify no slow drift in heap, map count, queues, or agent state.

## Population Preset

Initial preset name:

```text
victoria_lt30_living_world_v1
```

### Lifecycle Distribution

Use ratios so every test size has the same shape.

```text
Maple Island beginners:       10%
Maple Island islanders:       15%
Fresh Victoria level 8-15:    20%
Early first job level 15-22:  35%
Late first job level 22-30:   20%
```

Example for 2000 agents:

```text
200 Maple Island beginners
300 islanders
400 fresh Victoria
700 early first job
400 late first job
```

### Job Distribution

For first-job Victoria population:

```text
Warrior:   22%
Magician:  22%
Bowman:    18%
Thief:     25%
Pirate:    13%
```

Beginner and islander populations are tracked separately from the first-job job
mix.

### Home Town Distribution

```text
Henesys:                  22%
Kerning City:             20%
Ellinia:                  18%
Perion:                   16%
Lith Harbor:              12%
Nautilus:                  7%
Sleepywood entrance/fringe: 5%
```

Maple Island agents use:

```text
Mushroom Town
Amherst
Southperry
nearby Maple Island training maps
```

### Archetype Distribution

```text
Questers:       25%
Grinders:       25%
Farmers:        15%
Casuals:        15%
Social idlers:  10%
Merchants:       5%
Islanders:       5%
```

Archetype affects:

- plan weights.
- rest behavior.
- map choices.
- item reservation.
- NPC/shop frequency.
- willingness to party.
- tolerance for slow quests.
- market/farming behavior.

### Life-History Seeding

Agents should not only be random level/job/location rows. Seed believable
history:

- level.
- job.
- home town.
- current map.
- current plan.
- completed quests.
- started/postponed quests.
- inventory.
- mesos.
- equipment quality.
- potion stock.
- social preference.
- market preference.
- fatigue/rest state.
- relationship seed.

This avoids 2000 agents behaving as if they all spawned at the same second.

## Spawn Wave Strategy

Do not spawn the whole test population instantly unless the scenario is
specifically testing burst behavior.

Default wave plan:

```text
Wave 1: towns and safe maps, 10-20%
Wave 2: nearby training maps, next 20%
Wave 3: quest/farming routes, next 30%
Wave 4: late first-job/Sleepywood fringe, remaining 30%
```

Jitter:

```text
spawnDelay = random(0, 30 minutes)
initialActionDelay = random(5 seconds, 5 minutes)
firstPlanDelay = random(10 seconds, 10 minutes)
```

## Map Capacity Policy

Each map catalog should expose:

```text
softAgentCap
hardAgentCap
roleCaps
allowedActivities
sensitiveMap
abstractSimulationEligible
```

Example town:

```json
{
  "mapId": 100000000,
  "role": "town",
  "softAgentCap": 30,
  "hardAgentCap": 80,
  "allowedActivities": ["social", "shop", "quest", "rest"]
}
```

Example training map:

```json
{
  "mapId": 100020000,
  "role": "training",
  "softAgentCap": 8,
  "hardAgentCap": 20,
  "allowedActivities": ["grind", "quest", "farm"]
}
```

Selection rule:

```text
below soft cap:
    normal selection

between soft and hard cap:
    reduce selection probability

above hard cap:
    avoid unless quest-critical, social-event-critical, or explicitly forced
```

## Scenarios

### 1. Idle Population

Agents are spawned and distributed but do minimal work.

Validates:

- memory baseline.
- idle tick cost.
- map spread.
- server health snapshot.

### 2. Grinding Population

Agents train on catalog-selected maps.

Validates:

- combat ticks.
- loot.
- potion use.
- death handling.
- map object pressure.
- background combat when no players observe.

### 3. Questing Population

Agents execute plan cards and quest objectives.

Validates:

- NPC/quest capability.
- requirement checks.
- item collection.
- kill counters.
- fallback/recovery.

### 4. Hidden Simulation

Most agents run where no real player is present.

Validates:

- simulation tier selection.
- broadcast suppression.
- route ETA.
- background action commits.
- virtual loot buffer.
- checkpoint saves.

### 5. Materialization Storm

A player or synthetic observer enters maps containing many background agents.

Validates:

- mode transition.
- valid foothold materialization.
- inventory/state reconciliation.
- no invalid positions.
- no packet burst crash.

### 6. DB Pressure

Many agents reach milestones/checkpoints in a short window.

Validates:

- agent save queue.
- Cosmic save queue.
- DB waiting count.
- coalescing.
- failure/retry behavior.

### 7. Economy/Market Pressure

Deferred until economy engine exists.

Validates later:

- price observations.
- buy/sell decisions.
- listing age.
- manipulation resistance.
- tax/sink behavior.

### 8. Shutdown/Restart

Stop the server while agents are online and active.

Validates:

- graceful shutdown.
- checkpoint flush.
- no stuck logged-in accounts.
- restart recovery.
- no duplicate agent registration.

### 9. Long Uptime

Run for 24h, 3d, 7d, then 30d.

Validates:

- heap trend.
- loaded map trend.
- stale runtime state.
- DB queue trend.
- log volume.
- cache ownership.

## Metrics

### Server Metrics

Capture:

- timestamp.
- uptime.
- online players.
- online agents.
- heap used/max.
- GC count/time.
- live thread count.
- `ThreadManager` active/queued/pool/completed/submitted/rejected.
- `TimerManager` active/queued/task count/completed.
- DB pool active/idle/total/waiting.
- slow DB acquire count.
- slow save count.
- slow map tick count.
- slow broadcast count.
- loaded maps.
- active maps.
- idle maps.
- high-water object/drop/monster/reactor counts.
- event instance count.
- merchant/shop count.

### Agent Metrics

Capture:

- total agents.
- agents by simulation mode.
- agents by map.
- agents by plan state.
- agents by capability currently running.
- agent tick p50/p95/p99.
- scheduler delay p50/p95/p99.
- background action commits.
- abstract combat rounds.
- route ETA usage.
- broadcasts suppressed.
- materialization count.
- materialization failure count.
- stuck agents.
- death-loop agents.
- objective failure count.
- fallback count.

### Persistence Metrics

Capture:

- agent DB queue depth.
- agent DB write batch size.
- agent DB write latency p50/p95/p99.
- Cosmic save queue depth.
- Cosmic save latency p50/p95/p99.
- coalesced save count.
- skipped clean-section count.
- failed save count.
- retry count.
- shutdown dirty-agent count.

### Gameplay Validity Metrics

Capture:

- EXP/hour by level band.
- meso/hour by map/job band.
- item/hour by item category.
- rare drop count.
- potion use/hour.
- death rate.
- quest start/complete count.
- invalid quest completion count.
- invalid item credit count.
- inventory overflow/reconciliation count.
- invalid position count.

## Output Format

Write periodic snapshots to:

```text
logs/soak/agent-soak-YYYYMMDD-HHmm.csv
logs/soak/agent-soak-summary-YYYYMMDD-HHmm.json
```

Suggested JSON summary shape:

```json
{
  "runId": "agent-soak-20260703-1200",
  "stage": "stage4-1000-agents",
  "scenario": "hidden-simulation",
  "startedAt": "2026-07-03T12:00:00+08:00",
  "latest": {
    "agents": 1000,
    "players": 1,
    "modes": {
      "presentation": 80,
      "backgroundActive": 220,
      "backgroundAbstract": 650,
      "strategicOffline": 50
    },
    "heapMb": 1820,
    "dbWaiting": 0,
    "agentTickP95Ms": 12,
    "materializationFailures": 0,
    "slowSaves": 2,
    "loadedMaps": 430
  }
}
```

## Command Surface

Recommended GM/admin commands:

```text
!soak agents start <count> <scenario> [preset]
!soak agents stop
!soak agents pause
!soak agents resume
!soak agents status
!soak agents snapshot
!soak agents materialize <mapid>
!soak agents dematerialize <mapid>
!soak agents checkpoint
!soak agents killrandom <percent>
!soak agents resetstuck
!soak agents export
```

Read-only dashboard commands:

```text
!serverhealth
!agenthealth
!agentmaps
!agenttop slow
!agenttop stuck
!agenttop db
```

## Pass Criteria

A stage passes when:

- server stays responsive to real players.
- no unbounded heap growth after warmup.
- loaded map count stabilizes or has explained growth.
- DB waiting is normally zero.
- queues remain bounded.
- agent tick delay remains bounded.
- real player can login, move, change maps, attack, use NPC/shop.
- background agents materialize at valid positions.
- hidden agents do not emit full presentation broadcasts.
- shutdown completes cleanly.
- restart works without manual DB edits.
- no mass duplicate agent registration.
- no mass stuck/death loop.

## Fail Criteria

Investigate immediately if:

- heap climbs continuously for hours.
- DB waiting remains above zero across repeated samples.
- TimerManager or agent scheduler queue grows continuously.
- agent save queue grows without draining.
- materialization failures occur repeatedly.
- invalid positions are generated.
- real player actions lag badly.
- loaded maps only increase.
- stuck logged-in accounts appear.
- shutdown dirty-agent count remains high after grace period.

## Implementation Order

1. Expand `!serverhealth` with agent-related counts when safe.
2. Add agent soak snapshot collector.
3. Add CSV/JSON soak log writer.
4. Add population preset loader.
5. Add seeded agent creation/spawn wave runner.
6. Add scenario runner.
7. Add simulation-mode metrics.
8. Add materialization storm test.
9. Add DB pressure scenario.
10. Add shutdown/restart verification checklist.
11. Add strict debug comparison mode for background cheats.
12. Add 24h, 3d, 7d, and 30d runbooks.

## Integration Points

Depends on:

- reconstructed Agent scheduler.
- simulation tier runtime.
- background action runtime.
- agent persistence separation.
- profile/preset loader.
- catalog platform map metadata.
- server health diagnostics.

Should not depend on:

- LLM gateway.
- full economy engine.
- all Victoria quests being complete.
- player 500-concurrency tooling.

