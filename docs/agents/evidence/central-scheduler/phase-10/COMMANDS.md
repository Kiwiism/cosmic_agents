# Phase 10 Commands

The phase was checked with:

```text
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentActionMailboxTest,AgentAsyncTaskGatewayTest,AgentQuiescenceControllerTest,AgentTickSchedulerQuiescenceTest,AgentTickSchedulingServiceTest,AgentSchedulerMetricsTest" test
.\mvnw.cmd -q "-Dtest=Agent*Scheduler*Test,Agent*Tick*Test,Agent*Mailbox*Test,Agent*Lifecycle*Test,Agent*RuntimeRegistry*Test,Agent*Async*Test,Agent*ReplyQueueTest,Agent*Map*Test,Agent*Combat*Test,Agent*Loot*Test,Agent*Trade*Test,CosmicMapGatewayTest,CosmicAgentServerAdapterTest,MapPlayerObserverStateTest,AgentGatewayAffinityCatalogTest,AgentQuiescenceControllerTest" test
.\mvnw.cmd -q -DskipTests package
```

The mandatory scheduler, timer, blocking-wait, compatibility-property, and
`ScheduledFuture` source scans were rerun.

A full `.\mvnw.cmd -q test` was also attempted while another full Maven run
was active in the main worktree. It was stopped after more than twenty minutes
of CPU-heavy execution and after recording unrelated baseline failures in
dialogue/supply fixtures, one quest expectation, missing generated catalog
fixtures, and a randomized movement assertion. The non-catalog failures were
rerun in isolation; the dialogue/supply and quest failures reproduced while
the movement assertion passed. None of those source areas changed in Phase 10.
