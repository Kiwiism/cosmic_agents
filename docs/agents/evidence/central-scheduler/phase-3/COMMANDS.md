# Phase 3 Commands

```powershell
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentIndexedMinHeapTest,AgentSchedulerShardTest,AgentTickSchedulerHeapTest,AgentTickSchedulerTest,AgentSchedulerParityTest,AgentTickSchedulerSoakTest,AgentSchedulerConfigTest" test
.\mvnw.cmd -q "-Dtest=AgentActionMailboxTest,AgentMailboxRuntimeTest,AgentSchedulerRuntimeTest,AgentTickSchedulerTest,AgentChatMailboxBoundaryTest,AgentTargetedChatRouteServiceTest,AgentUntargetedChatRouteServiceTest,AgentWhisperCommandServiceTest,AgentFollowTargetCommandServiceTest,AgentFollowTargetCommandCoordinatorTest,AgentPotionCheckRequestServiceTest,AgentPendingOfferResponseServiceTest,AgentPendingOfferChatRouteServiceTest,AgentFormationCommandServiceTest,AgentChatRouteCoordinatorTest,AgentChatIngressServiceTest,AgentEquipMailboxBoundaryTest,AgentAirshowServiceTest,AgentMakerRuntimeTest,AgentCombatRuntimeTest,AgentInventoryRuntimeTest,AgentShopRuntimeTest,AgentScrollReactionRuntimeTest,AgentAmmoRuntimeTest,AgentPotionRuntimeTest,AgentOfferRuntimeTest" test
.\mvnw.cmd -q -DskipTests package
rg -n "registrations\.values|due\.sort" src/main/java/server/agents/runtime/scheduler
rg -n "AgentSchedulerRuntime\.(schedule|register)\(" src/main/java/server/agents
rg -n "TimerManager" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit|\.join\(\)|Thread\.sleep" src/main/java/server/agents
rg -n "agents\.mailbox\.enabled|agents\.scheduler\.central\.enabled" src/main/java src/test/java
rg -n "ScheduledFuture" src/main/java/server/agents
```

The focused Phase 3 mechanics suite, broader Phase 2 ownership suite, compile,
package build, and diff check pass.

Scan classification:

- `registrations.values` / `due.sort`: no scheduler matches; the per-cycle
  registration scan and sort are removed.
- direct `AgentSchedulerRuntime.schedule/register`: generation-scoped Amherst
  and airshow callbacks, global population/presentation maintenance, and the
  central loop/wake backend remain as classified in Phase 2.
- `TimerManager`: only the Cosmic scheduler gateway imports it.
- blocking calls: one navigation graph `join()` remains assigned to Phase 5.
- compatibility flags: runtime configuration and focused tests only.
- `ScheduledFuture`: central/legacy lifecycle handles plus previously
  classified global maintenance and presentation cleanup remain.
