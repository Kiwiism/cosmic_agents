# Phase 1 Commands

```powershell
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentSchedulerConfigTest,AgentSessionIdTest,AgentRuntimeRegistryTest,AgentLifecycleServiceTest,AgentRuntimeCleanupServiceTest,AgentTransferServiceTest,AgentTickSchedulingServiceTest,AgentTickSchedulerTest,AgentTickSchedulerSoakTest,AgentSchedulerParityTest,AgentSchedulerRuntimeTest,AgentFoundationStructureTest,AgentActionMailboxTest,AgentMailboxTickIntegrationTest,AgentSchedulerMetricsTest,AgentChatMailboxBoundaryTest,CosmicSchedulerGatewayTest,AgentCapabilityRuntimeTest,AgentLiveModeTickServiceTest" test
.\mvnw.cmd -q "-Dtest=AgentOwnerItemNotificationServiceTest,BotManagerTest,AgentPotionCheckRequestServiceTest,AgentWhisperCommandServiceTest,AgentFollowTargetCommandCoordinatorTest,AgentFollowAnchorServiceTest,AgentScriptTaskExecutionServiceTest,AgentTargetSnapshotCoordinatorTest,AgentTickSchedulerTest,AgentAmmoServiceTest,AgentPotionServiceTest,BotCombatManagerTest" test
.\mvnw.cmd -q -DskipTests package
.\mvnw.cmd -q test
git grep "mutableEntriesForLeader" -- src/main/java
git grep -E "entriesByLeaderId\(\).*(put|remove|clear|compute)" -- src/main/java
rg -n "AgentSchedulerRuntime\.(schedule|register)\(" src/main/java/server/agents
rg -n "TimerManager" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit|\.join\(\)|Thread\.sleep" src/main/java/server/agents
rg -n "agents\.mailbox\.enabled|agents\.scheduler\.central\.enabled" src/main/java src/test/java
rg -n "ScheduledFuture" src/main/java/server/agents
```

Compile, package, both focused suites, and the mandatory scans passed or were
classified before this evidence was committed. The full-suite attempt did not
complete cleanly: after reporting unrelated failures, its Surefire child exited
but the Maven wrapper remained alive and was terminated. No server process was
terminated.
