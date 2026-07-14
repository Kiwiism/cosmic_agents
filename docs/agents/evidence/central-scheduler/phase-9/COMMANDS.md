# Phase 9 Commands

The phase was checked with:

```text
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentLoadShedding*Test,AgentDefaultLoadSheddingPolicyTest,AgentRuntimeRegistryAdmissionTest,AgentTickSchedulerLoadSheddingTest,AgentSchedulerMetricsTest,AgentReplyQueueTest,AgentAsyncExecutorRegistryTest,AgentAsyncTaskGatewayTest,AgentLifecycleServiceTest,AgentRuntimeRegistryTest,AgentTickSchedulerTest,AgentTickSchedulerSimulationTest,AgentTickSchedulerSliceContinuationTest" test
.\mvnw.cmd -q "-Dtest=Agent*Scheduler*Test,Agent*Tick*Test,Agent*Mailbox*Test,Agent*Lifecycle*Test,Agent*RuntimeRegistry*Test,Agent*Async*Test,Agent*ReplyQueueTest,Agent*Map*Test,Agent*Combat*Test,Agent*Loot*Test,Agent*Trade*Test,CosmicMapGatewayTest,CosmicAgentServerAdapterTest,MapPlayerObserverStateTest,AgentGatewayAffinityCatalogTest" test
.\mvnw.cmd -q -DskipTests package
```

The mandatory scheduler, timer, blocking, compatibility-property, and
`ScheduledFuture` source scans were also rerun.
