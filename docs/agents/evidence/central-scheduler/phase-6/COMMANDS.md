# Phase 6 Commands

The phase was checked with:

```text
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentGatewayAffinityCatalogTest,AgentShardedTickSchedulerTest,AgentSchedulerConfigTest,AgentSchedulerMetricsTest,AgentFormationServiceTest,AgentFormationCommandServiceTest,AgentFormationCommandCoordinatorTest,AgentLeaderSafetyCoordinatorTest,AgentLeaderSafetyServiceTest,AgentSessionCommandCoordinatorTest,AgentTickSchedulerTest,AgentTickSchedulerHeapTest,AgentTickSchedulerBudgetTest" test
.\mvnw.cmd -q "-Dtest=Agent*Scheduler*Test,Agent*Mailbox*Test,Agent*Map*Test,Agent*Combat*Test,Agent*Loot*Test,Agent*Trade*Test,CosmicMapGatewayTest,CosmicCombatGatewayTest,AgentGatewayAffinityCatalogTest,AgentFormationServiceTest,AgentFormationCommandServiceTest,AgentFormationCommandCoordinatorTest,AgentLeaderSafetyCoordinatorTest,AgentLeaderSafetyServiceTest,AgentSessionCommandCoordinatorTest" test
.\mvnw.cmd -q -DskipTests package
```

Mandatory blocking, timer, future, and scheduler-mode source scans were also
rerun before the phase commit.

All listed build and test commands passed.

