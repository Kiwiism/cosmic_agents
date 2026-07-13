# Phase 4 Commands

```powershell
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentCycleBudgetTest,AgentSchedulerRollingWindowTest,AgentSchedulerMetricsTest,AgentIndexedMinHeapTest,AgentSchedulerShardTest,AgentTickSchedulerBudgetTest,AgentTickSchedulerHeapTest,AgentTickSchedulerTest,AgentSchedulerParityTest,AgentTickSchedulerSoakTest,AgentSchedulerConfigTest,AgentActionMailboxTest,AgentMailboxRuntimeTest,AgentSchedulerRuntimeTest" test
.\mvnw.cmd -q "-Dtest=AgentActionMailboxTest,AgentMailboxRuntimeTest,AgentSchedulerRuntimeTest,AgentTickSchedulerTest,AgentChatMailboxBoundaryTest,AgentTargetedChatRouteServiceTest,AgentUntargetedChatRouteServiceTest,AgentWhisperCommandServiceTest,AgentFollowTargetCommandServiceTest,AgentFollowTargetCommandCoordinatorTest,AgentPotionCheckRequestServiceTest,AgentPendingOfferResponseServiceTest,AgentPendingOfferChatRouteServiceTest,AgentFormationCommandServiceTest,AgentChatRouteCoordinatorTest,AgentChatIngressServiceTest,AgentEquipMailboxBoundaryTest,AgentAirshowServiceTest,AgentMakerRuntimeTest,AgentCombatRuntimeTest,AgentInventoryRuntimeTest,AgentShopRuntimeTest,AgentScrollReactionRuntimeTest,AgentAmmoRuntimeTest,AgentPotionRuntimeTest,AgentOfferRuntimeTest" test
.\mvnw.cmd -q -DskipTests package
rg -n "registrations\.values|due\.sort" src/main/java/server/agents/runtime/scheduler
rg -n "AgentSchedulerRuntime\.(schedule|register)\(" src/main/java/server/agents
rg -n "TimerManager" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit|\.join\(\)|Thread\.sleep" src/main/java/server/agents
rg -n "agents\.mailbox\.enabled|agents\.scheduler\.central\.enabled" src/main/java src/test/java
rg -n "ScheduledFuture" src/main/java/server/agents
```

The scheduler/metrics/parity/500-session suite, broader Phase 2 ownership suite,
compile, package build, and diff check pass.

Scan classification is unchanged from Phase 3:

- no `registrations.values` or `due.sort` scheduler match remains.
- direct scheduler runtime calls remain generation-scoped callbacks or
  classified global/presentation maintenance.
- `TimerManager` remains isolated to the Cosmic scheduler gateway.
- one navigation graph `join()` remains assigned to Phase 5.
- compatibility flags occur in runtime configuration and focused tests.
- retained `ScheduledFuture` uses are central/legacy lifecycle handles or
  previously classified global/presentation cleanup.
