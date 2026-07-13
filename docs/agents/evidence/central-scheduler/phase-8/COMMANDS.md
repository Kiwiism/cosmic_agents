# Phase 8 Commands

The phase was checked with:

```text
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentTickCoreServiceTest,AgentTickCoreRuntimeTest,AgentTickRuntimeTest,AgentTickOrchestratorTest,AgentTickSlicingServiceTest,AgentTickSchedulerSliceContinuationTest,AgentLifecycleServiceTest,AgentSchedulerConfigTest,AgentSchedulerMetricsTest" test
.\mvnw.cmd -q "-Dtest=Agent*Scheduler*Test,Agent*Tick*Test,Agent*Mailbox*Test,Agent*Lifecycle*Test,Agent*Map*Test,Agent*Combat*Test,Agent*Loot*Test,Agent*Trade*Test,CosmicMapGatewayTest,CosmicAgentServerAdapterTest,MapPlayerObserverStateTest,AgentSchedulerMetricsTest,AgentGatewayAffinityCatalogTest" test
.\mvnw.cmd -q -DskipTests package
```

The mandatory scheduler, timer, blocking, compatibility-property, and
`ScheduledFuture` source scans were also rerun.
