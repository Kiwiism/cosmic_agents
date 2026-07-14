# Phase 11 Commands

Local deterministic scale gate:

```text
.\mvnw.cmd -q "-Dtest=AgentTickSchedulerSoakTest,AgentTickSchedulerScaleGateTest" test
```

Release checks:

```text
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=Agent*Scheduler*Test,Agent*Tick*Test,Agent*Mailbox*Test,Agent*Lifecycle*Test,Agent*RuntimeRegistry*Test,Agent*Async*Test,Agent*ReplyQueueTest,Agent*Map*Test,Agent*Combat*Test,Agent*Loot*Test,Agent*Trade*Test,CosmicMapGatewayTest,CosmicAgentServerAdapterTest,MapPlayerObserverStateTest,AgentGatewayAffinityCatalogTest,AgentQuiescenceControllerTest" test
.\mvnw.cmd -q -DskipTests package
git diff --check
```

The live and long-duration commands are environment-specific and must use the
staged settings in `LIVE_SOAK_RUNBOOK.md`.
