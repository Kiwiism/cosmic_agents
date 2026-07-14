# Phase 11 Commands

Local deterministic scale gate:

```text
.\mvnw.cmd -q "-Dtest=AgentTickSchedulerSoakTest,AgentTickSchedulerScaleGateTest" test
```

Bounded shutdown gate:

```text
.\mvnw.cmd -q "-Dtest=AgentTickSchedulerTest,AgentSchedulerShardTest,AgentIndexedMinHeapTest,AgentAsyncExecutorRegistryTest,AgentAsyncTaskGatewayTest,AgentRuntimeShutdownCoordinatorTest" test
```

Release checks:

```text
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=Agent*Scheduler*Test,Agent*Tick*Test,Agent*Mailbox*Test,Agent*Lifecycle*Test,Agent*RuntimeRegistry*Test,Agent*Async*Test,Agent*ReplyQueueTest,Agent*Map*Test,Agent*Combat*Test,Agent*Loot*Test,Agent*Trade*Test,CosmicMapGatewayTest,CosmicAgentServerAdapterTest,MapPlayerObserverStateTest,AgentGatewayAffinityCatalogTest,AgentQuiescenceControllerTest" test
.\mvnw.cmd -q -DskipTests package
git diff --check
rg -n "AgentSchedulerRuntime\.(schedule|register)\(" src/main/java/server/agents
rg -n "TimerManager" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit|\.join\(\)|Thread\.sleep" src/main/java/server/agents
rg -n "agents\.mailbox\.enabled|agents\.scheduler\.central\.enabled" src/main/java src/test/java
rg -n "ScheduledFuture" src/main/java/server/agents
```

Live operator snapshots (GM6):

```text
@agentscheduler
@serverhealth
```

`@agentscheduler` reports scheduler mode, active Agent count, cadence metrics,
queue pressure, bounded shard detail, load shedding, quiescence, and initialized
Agent async queues without mutating runtime state.

The live and long-duration commands are environment-specific and must use the
staged settings in `LIVE_SOAK_RUNBOOK.md`.
