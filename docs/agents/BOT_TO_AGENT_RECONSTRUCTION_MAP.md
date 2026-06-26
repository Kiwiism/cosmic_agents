# Bot To Agent Reconstruction Map

Status values:

- `MIGRATE_TO_AGENT`: move behavior into one Agent module.
- `SPLIT_TO_MULTIPLE_AGENT_MODULES`: split behavior across multiple bins.
- `COMPATIBILITY_ALIAS_TEMPORARY`: keep only as a temporary wrapper during migration.
- `DELETE_AFTER_MIGRATION`: remove once behavior/callers are migrated.
- `LEGACY_PROFILE`: preserve as legacy behavior profile until deliberately replaced.

This map tracks reconstruction from the source/master bot baseline into neutral Agent modules.

| Current file | Target Agent destination | Status |
| --- | --- | --- |
| `src/main/java/client/BotClient.java` | `client.AgentClient` or `server.agents.integration.cosmic.CosmicAgentClientAdapter` | `MIGRATE_TO_AGENT` |
| `src/main/java/client/creator/BotCreator.java` | `client.creator.AgentCreator` | `MIGRATE_TO_AGENT` |
| `src/main/java/client/command/commands/gm0/RegisterBotCommand.java` | `server.agents.commands` or later deletion if ownership is removed | `LEGACY_PROFILE` |
| `src/main/java/client/command/commands/gm3/SpawnBotCommand.java` | `server.agents.commands.AgentSpawnCommandExecutor` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/TakeBotOwnerCommand.java` | `server.agents.commands` or later deletion if ownership is removed | `LEGACY_PROFILE` |
| `src/main/java/client/command/commands/gm3/BotCfgCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/BotLlmCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/BotNavCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/BotPerfDebugCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/BotAirshowManager.java` | `server.agents.capabilities.social.airshow` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotAmmoManager.java` | `server.agents.capabilities.supplies.AgentAmmoService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotAttackExecutionProvider.java` | `server.agents.capabilities.combat.AgentAttackExecutionProvider` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotBuffManager.java` | `server.agents.capabilities.combat.AgentBuffService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotBuildManager.java` | `server.agents.capabilities.build` and `server.agents.profiles.SkillBuildProfile` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotChatManager.java` | `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.dialogue.AgentChatCommandClassifier`, `server.agents.capabilities.dialogue.AgentTradeDialogueClassifier`, `server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier`, `server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier`, `server.agents.capabilities.dialogue.AgentSocialDialogueClassifier`, `server.agents.commands.AgentReplyQueue`, `server.agents.events` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; named random reply pools, reply queue, movement/follow/fidget, supply-request/direct supply, query/toggle, support/heal/buff toggle, logout/relog/away session request, report/debug, trade/drop/item, trade-invite/shop/maker utility, equipment/autoequip, greeting/fame, and upgrade-request classification are Agent-owned |
| `src/main/java/server/bots/BotCombatManager.java` | `server.agents.capabilities.combat` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotCommandParser.java` | `server.agents.commands.AgentCommandParser` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/BotEntry.java` | `server.agents.runtime.AgentSession` and capability state objects | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; message queue now uses Agent-owned queued message type |
| `src/main/java/server/bots/BotEquipManager.java` | `server.agents.capabilities.equipment` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotFallbackMovementManager.java` | `server.agents.capabilities.movement.AgentFallbackMovementService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotFidgetManager.java` | `server.agents.capabilities.social` and `server.agents.capabilities.movement` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotInventoryManager.java` | `server.agents.capabilities.inventory`, `looting`, `trade`, `server.agents.capabilities.dialogue.AgentItemQueryNormalizer` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; item query normalization is Agent-owned |
| `src/main/java/server/bots/BotLootEligibility.java` | `server.agents.capabilities.looting.AgentLootEligibility` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotMakerManager.java` | `server.agents.capabilities.build` or later deletion | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotManager.java` | `server.agents.runtime`, `commands`, `events`, capability orchestrators | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotMovementManager.java` | `server.agents.capabilities.movement` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotMovementProfile.java` | `server.agents.capabilities.movement.AgentMovementProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationDebugOverlay.java` | `server.agents.capabilities.navigation.AgentNavigationDebugOverlay` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationGraph.java` | `server.agents.capabilities.navigation.AgentNavigationGraph` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationGraphProvider.java` | `server.agents.capabilities.navigation.AgentNavigationGraphService` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotNavigationManager.java` | `server.agents.capabilities.navigation` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotNavigationMapLoader.java` | `server.agents.capabilities.navigation.AgentNavigationMapLoader` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationProbe.java` | `server.agents.capabilities.navigation.AgentNavigationProbe` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotOfferManager.java` | `server.agents.capabilities.trade.AgentOfferService` and `equipment` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotOwnershipService.java` | `server.agents.legacy.LegacyBotOwnershipAdapter` initially; later replace/remove | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotPathLogger.java` | `server.agents.runtime.AgentPathLogger` or monitoring package later | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotPerformanceMonitor.java` | `server.agents.runtime.AgentPerformanceMonitor` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotPhysicsEngine.java` | `server.agents.capabilities.movement.AgentPhysicsEngine` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotPotionManager.java` | `server.agents.capabilities.supplies.AgentPotionService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotScript.java` | `server.agents.plans.legacy.LegacyBotScript` or later deletion | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotScriptContext.java` | `server.agents.plans.legacy.LegacyBotScriptContext` | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotScriptRunner.java` | `server.agents.plans.legacy.LegacyBotScriptRunner` | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotScriptRuntime.java` | `server.agents.runtime` and `plans` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotScriptStep.java` | `server.agents.plans.AgentPlanStep` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotScrollReactionManager.java` | `server.agents.capabilities.social` and `dialogue` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotShopManager.java` | `server.agents.capabilities.shop` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotStarterKitManager.java` | `server.agents.capabilities.inventory.AgentStarterKitService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotTask.java` | `server.agents.plans.AgentPlanStep` or legacy plan adapter | `LEGACY_PROFILE` |
| `src/main/java/server/bots/Emote.java` | `server.agents.capabilities.dialogue.AgentEmote` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/ReplyChannel.java` | `server.agents.commands.AgentReplyChannel` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/build/BowmanBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/BuildStep.java` | `server.agents.profiles.SkillBuildStep` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/MageBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/ThiefBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/WarriorBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackDataProvider.java` | `server.agents.capabilities.combat.data.AgentAttackDataProvider` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackTiming.java` | `server.agents.capabilities.combat.data.AgentAttackTiming` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/combat/BotDefenseDataProvider.java` | `server.agents.capabilities.combat.data.AgentDefenseDataProvider` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/combat/BotMobHitboxProvider.java` | `server.agents.capabilities.combat.data.AgentMobHitboxProvider` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/combat/BotWzXml.java` | `server.agents.capabilities.combat.data.AgentWzXml` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/BotLlmConfig.java` | `server.agents.capabilities.dialogue.llm.AgentLlmConfig` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/BotLlmReplyManager.java` | `server.agents.capabilities.dialogue.llm.AgentLlmReplyService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/BotMemoryStore.java` | `server.agents.capabilities.dialogue.llm.AgentMemoryStore` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/CommandTypoSuggester.java` | `server.agents.commands.AgentCommandTypoSuggester` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/OllamaClient.java` | `server.agents.capabilities.dialogue.llm.OllamaClient` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/PromptBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentPromptBuilder` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/SenderRelation.java` | `server.agents.capabilities.dialogue.llm.AgentSenderRelation` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/llm/SituationBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentSituationBuilder` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/pq/BotKpqStage1.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage1` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/pq/BotKpqStage5.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage5` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/pq/BotKpqState.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqState` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/pq/BotPqHooks.java` | `server.agents.capabilities.partyquest.AgentPartyQuestHooks` | `MIGRATE_TO_AGENT` |
| `src/main/resources/db/tables/025-bot-ownership.sql` | `server.agents.legacy` documentation initially; later external registry or deletion | `LEGACY_PROFILE` |
