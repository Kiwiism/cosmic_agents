# Phase 5 Commands

```powershell
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentSchedulerBlockingBoundaryTest,AgentAsyncExecutorRegistryTest,AgentAsyncQueueBoundaryTest,AgentAsyncTaskGatewayTest,AgentAsyncQueueMetricsTest,AgentNavigationGraphWarmupLifecycleTest,AgentTransferRuntimeTest,AgentLlmReplyServiceTest,AmherstPlanAsyncPersistenceTest,AmherstPlanRuntimeRunnerTest" test
.\mvnw.cmd -q "-Dtest=AgentCycleBudgetTest,AgentSchedulerRollingWindowTest,AgentSchedulerMetricsTest,AgentIndexedMinHeapTest,AgentSchedulerShardTest,AgentTickSchedulerBudgetTest,AgentTickSchedulerHeapTest,AgentTickSchedulerTest,AgentSchedulerParityTest,AgentTickSchedulerSoakTest,AgentSchedulerConfigTest,AgentActionMailboxTest,AgentMailboxRuntimeTest,AgentSchedulerRuntimeTest,AgentNavigationGraphWarmupLifecycleTest,BotNavigationGraphProviderTest,BotNavigationGraphFallbackTest,AgentGroundTargetServiceTest,AgentMovementProfileServiceTest,AgentTransferRuntimeTest,AgentTransferServiceTest,AgentTransferCommandServiceTest,AgentChatTransferFlowTest,AgentLlmReplyServiceTest,AgentLlmReplyCoordinatorTest,AgentLlmRuntimeTest,AmherstPlanAsyncPersistenceTest,AmherstPlanRuntimeRunnerTest,AmherstPlanCommandServiceTest" test
.\mvnw.cmd -q -DskipTests package
rg -n "registrations\.values|due\.sort" src/main/java/server/agents/runtime/scheduler
rg -n "AgentSchedulerRuntime\.(schedule|register)\(" src/main/java/server/agents
rg -n "TimerManager" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit|\.join\(\)|Thread\.sleep" src/main/java/server/agents
rg -n "agents\.mailbox\.enabled|agents\.scheduler\.central\.enabled" src/main/java src/test/java
rg -n "ScheduledFuture" src/main/java/server/agents
rg -n "AgentNavigationGraphService\.getGraph\(" src/main/java/server/agents
```

Compile, focused Phase 5 tests, broader scheduler/capability tests, package,
diff check, and source scans pass. Mockito emits its existing dynamic-agent
warning on JDK 21; it is not a test failure.
