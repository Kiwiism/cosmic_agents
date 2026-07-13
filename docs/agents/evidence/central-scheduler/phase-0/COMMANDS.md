# Central Scheduler Phase 0 Commands

Baseline verification:

```powershell
.\mvnw.cmd -q "-Dtest=AgentTickSchedulerTest,AgentTickSchedulerSoakTest,AgentSchedulerParityTest,AgentActionMailboxTest,AgentMailboxTickIntegrationTest,AgentSchedulerMetricsTest,AgentChatMailboxBoundaryTest,CosmicSchedulerGatewayTest,AgentCapabilityRuntimeTest,AgentLiveModeTickServiceTest" test
```

Required scans:

```powershell
rg -n "AgentSchedulerRuntime\.(schedule|register)\(" src/main/java/server/agents
rg -n "TimerManager" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit|\.join\(\)|Thread\.sleep" src/main/java/server/agents
rg -n "agents\.mailbox\.enabled|agents\.scheduler\.central\.enabled" src/main/java src/test/java
rg -n "ScheduledFuture" src/main/java/server/agents
rg -n "AgentMailboxRuntime\.submit|actionMailbox\(\)\.submit" src/main/java
```

Phase completion verification:

```powershell
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentTickSchedulerTest,AgentTickSchedulerSoakTest,AgentSchedulerParityTest,AgentActionMailboxTest,AgentMailboxTickIntegrationTest,AgentSchedulerMetricsTest,AgentChatMailboxBoundaryTest,CosmicSchedulerGatewayTest,AgentCapabilityRuntimeTest,AgentLiveModeTickServiceTest" test
```
