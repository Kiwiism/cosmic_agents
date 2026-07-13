# Phase 7 Commands

The phase was checked with:

```text
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentSimulationPolicyTest,AgentSimulationTransitionServiceTest,AgentSimulationMapPresenceListenerTest,AgentTickSchedulerSimulationTest,AgentSchedulerConfigTest,AgentSchedulerMetricsTest,MapPlayerObserverStateTest,CosmicMapGatewayTest" test
.\mvnw.cmd -q "-Dtest=Agent*Scheduler*Test,Agent*Simulation*Test,Agent*Mailbox*Test,Agent*Map*Test,Agent*Lifecycle*Test,Agent*Combat*Test,Agent*Loot*Test,Agent*Trade*Test,CosmicMapGatewayTest,CosmicAgentServerAdapterTest,MapPlayerObserverStateTest,AgentSchedulerMetricsTest,AgentGatewayAffinityCatalogTest" test
.\mvnw.cmd -q -DskipTests package
```

The mandatory scheduler, timer, blocking, compatibility-property, and
`ScheduledFuture` source scans were also rerun. All listed builds and tests
passed.
