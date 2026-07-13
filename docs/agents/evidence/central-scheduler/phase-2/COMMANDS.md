# Phase 2 Commands

```powershell
git diff --check
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=AgentActionMailboxTest,AgentMailboxRuntimeTest,AgentSchedulerRuntimeTest,AgentTickSchedulerTest,AgentChatMailboxBoundaryTest,AgentTargetedChatRouteServiceTest,AgentUntargetedChatRouteServiceTest,AgentWhisperCommandServiceTest,AgentFollowTargetCommandServiceTest,AgentFollowTargetCommandCoordinatorTest,AgentPotionCheckRequestServiceTest,AgentPendingOfferResponseServiceTest,AgentPendingOfferChatRouteServiceTest,AgentFormationCommandServiceTest,AgentChatRouteCoordinatorTest,AgentChatIngressServiceTest,AgentEquipMailboxBoundaryTest,AgentAirshowServiceTest,AgentMakerRuntimeTest,AgentCombatRuntimeTest,AgentInventoryRuntimeTest,AgentShopRuntimeTest,AgentScrollReactionRuntimeTest,AgentAmmoRuntimeTest,AgentPotionRuntimeTest,AgentOfferRuntimeTest" test
.\mvnw.cmd -q -DskipTests package
rg -n "AgentSchedulerRuntime\.(schedule|register)\(" src/main/java/server/agents
rg -n "TimerManager" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit|\.join\(\)|Thread\.sleep" src/main/java/server/agents
rg -n "agents\.mailbox\.enabled|agents\.scheduler\.central\.enabled" src/main/java src/test/java
rg -n "ScheduledFuture" src/main/java/server/agents
rg -n "\.get\([^)]*TimeUnit" src/main/java/server/agents/capabilities/dialogue src/main/java/net/server/channel/handlers
```

Compile and the focused boundary suite pass. Package and mandatory scan
results are classified in the other Phase 2 evidence files.
