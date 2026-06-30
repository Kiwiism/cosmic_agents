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
| `src/main/java/server/bots/BotAmmoManager.java` | `server.agents.capabilities.supplies.AgentAmmoService`, `server.agents.capabilities.supplies.AgentAmmoSharePolicy`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATE_TO_AGENT`; ammo-share request eligibility, donor quantity math, donor tie-break policy, and ammo request/offer dialogue pools are Agent-owned |
| `src/main/java/server/bots/BotAttackExecutionProvider.java` | `server.agents.capabilities.combat.AgentAttackExecutionProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotBuffManager.java` | `server.agents.capabilities.combat.AgentBuffService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotBuildManager.java` | `server.agents.capabilities.build` and `server.agents.profiles.SkillBuildProfile` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotChatManager.java` | `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.dialogue.AgentChatCommandClassifier`, `server.agents.capabilities.dialogue.AgentTradeDialogueClassifier`, `server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier`, `server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier`, `server.agents.capabilities.dialogue.AgentSocialDialogueClassifier`, `server.agents.capabilities.dialogue.AgentBuildDialogueClassifier`, `server.agents.capabilities.dialogue.AgentDialogueReportFormatter`, `server.agents.commands.AgentReplyQueue`, `server.agents.events` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; named random reply pools, reply queue, movement/follow/fidget, supply-request/direct supply, query/toggle, support/heal/buff toggle, logout/relog/away session request and confirmation normalization, report/debug, trade/drop/item and pending drop-choice, trade-invite/shop/maker utility, equipment/autoequip, greeting/fame, build/job/AP/SP classification, skill-tree choice resolution, job advancement resolution, report/AP-build/job-display/skill-tree/learned-skill formatting, and upgrade-request classification are Agent-owned; thin bot classification wrappers removed |
| `src/main/java/server/bots/BotCombatManager.java` | `server.agents.capabilities.combat`, `server.agents.capabilities.dialogue`, and `server.agents.integration.AgentBotCombatReportRuntime` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; config and config command helpers, attack route, attack-plan value model/scoring/selection, basic attack target-selection/pivot policy, attack execution preflight policy, skill attack preflight/ammo/primary-target-resolution/packet-field policy, ammo counting/check-decision policy, projectile hitbox geometry plus removal of the bot-side passive projectile range wrapper, fall-damage formula plus removal of the bot-side formula wrapper, physical mob touch damage provider, skill classification/cache-signature/cache-bucket/heal-cache-stop/AoE-cache-score/support-buff-cache-eligibility/best-skill comparison/buff-blacklist plus removal of bot-side skill-classification wrappers, target eligibility/selection/opposite-facing pivoting/strike-point primary resolution, immediate projectile target policy, grind-target locality/scored-target ordering/grind-score dispatch/reachable-target selection/reachable-grind-target decision/local-score-list construction/region-score construction/follow-local-score construction/grouping/occupancy-penalty/occupant-counting/graph-path-cost policy, weapon policy plus removal of the bot-side Dragon Knight weapon-gate wrapper, skill hitbox policy plus removal of the bot-side strike-point anchor wrapper, hit-count policy plus removal of the bot-side hit-count wrapper, range/basic-reach/target-in-range/airborne-use policy, support safety/party-filtering/healer-ally/Dragon-Roar-threshold/support-buff candidate/readiness/failure-summary/outcome-summary/cooldown-summary/refresh-timing/cast-cooldown/skill-buff tick preflight/no-living-mobs preflight/support-heal preflight/cast-trigger policy, support special-move packet layout, skill-use affordability policy, mob-touch/knockback/vector policy, hitbox/monster intersection, scoring/target-score/single-target score floor/AOE-cluster default constants/AOE-vs-single-target/AOE-single-targeting/AOE-cluster-size/AOE-reposition preflight/AOE-reposition math, attack-plan tie-break ordering, combat skill-label formatting, skill-buff debug report assembly, and combat reply pools are Agent-owned |
| `src/main/java/server/bots/BotCommandParser.java` | `server.agents.commands.AgentCommandParser` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/BotEntry.java` | `server.agents.runtime.AgentSession` and capability state objects | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; message queue now uses Agent-owned queued message type |
| `src/main/java/server/bots/BotEquipManager.java` | `server.agents.capabilities.equipment` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotFallbackMovementManager.java` | `server.agents.capabilities.movement.AgentFallbackMovementService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotFidgetManager.java` | `server.agents.capabilities.social` and `server.agents.capabilities.movement` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotInventoryManager.java` | `server.agents.capabilities.inventory`, `looting`, `trade`, `server.agents.capabilities.dialogue.AgentItemQueryNormalizer`, `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.supplies.AgentPotionSharePolicy` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; item query normalization, trade page/meso/item-quantity policy, sell-trash equipment protection policy, ammo item-to-weapon classification, potion-share recovery scoring/slot eligibility, USE-item recovery/buff classification, drop-limited-map reply, and trade reply/result/error pools are Agent-owned |
| `src/main/java/server/bots/BotLootEligibility.java` | `server.agents.capabilities.looting.AgentLootEligibility` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotMakerManager.java` | `server.agents.capabilities.build` or later deletion | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotManager.java` | `server.agents.runtime`, `commands`, `events`, capability orchestrators | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotMovementManager.java` | `server.agents.capabilities.movement` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; cooldown/delay countdown math, climb idle/snap/rope identity decision policy, and ground horizontal step policy are Agent-owned |
| `src/main/java/server/bots/BotMovementProfile.java` | `server.agents.capabilities.movement.AgentMovementProfile` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationDebugOverlay.java` | `server.agents.capabilities.navigation.AgentNavigationDebugOverlay` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationGraph.java` | `server.agents.capabilities.navigation.AgentNavigationGraph` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationGraphProvider.java` | `server.agents.capabilities.navigation.AgentNavigationGraphService` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotNavigationManager.java` | `server.agents.capabilities.navigation` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotNavigationMapLoader.java` | `server.agents.capabilities.navigation.AgentNavigationMapLoader` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationProbe.java` | `server.agents.capabilities.navigation.AgentNavigationProbe` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotOfferManager.java` | `server.agents.capabilities.trade.AgentOfferService`, `equipment`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; offer accept/decline replies, owner-upgrade request prompts, and loot-offer prompt templates are Agent-owned |
| `src/main/java/server/bots/BotOwnershipService.java` | `server.agents.legacy.LegacyBotOwnershipAdapter` initially; later replace/remove | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotPathLogger.java` | `server.agents.runtime.AgentPathLogger` or monitoring package later | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotPerformanceMonitor.java` | `server.agents.runtime.AgentPerformanceMonitor` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPhysicsEngine.java` | `server.agents.capabilities.movement.AgentPhysicsEngine` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotPotionManager.java` | `server.agents.capabilities.supplies.AgentPotionService`, `server.agents.capabilities.supplies.AgentAutopotPolicy`, `server.agents.capabilities.supplies.AgentPotionInventoryPolicy`, `server.agents.capabilities.supplies.AgentPassiveRecoveryPolicy`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATE_TO_AGENT`; autopot potion tier ranking, HP/MP slot choice policy, pure recovery potion stack counting, passive HP/MP recovery formula/skill-bonus lookup, and potion request/offer/low-donor/low-return dialogue pools are Agent-owned |
| `src/main/java/server/bots/BotScript.java` | `server.agents.plans.legacy.LegacyBotScript` or later deletion | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotScriptContext.java` | `server.agents.plans.legacy.LegacyBotScriptContext` | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotScriptRunner.java` | `server.agents.plans.legacy.LegacyBotScriptRunner` | `LEGACY_PROFILE` |
| `src/main/java/server/bots/BotScriptRuntime.java` | `server.agents.runtime` and `plans` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotScriptStep.java` | `server.agents.plans.AgentPlanStep` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotScrollReactionManager.java` | `server.agents.capabilities.social` and `dialogue` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotShopManager.java` | `server.agents.capabilities.shop`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; shop resupply/shopping dialogue pools, fixed sell-trash/shop visit/shortfall result messages, shop approach geometry, ammo resupply/recharge policy, and potion shop selection policy are Agent-owned |
| `src/main/java/server/bots/BotStarterKitManager.java` | `server.agents.capabilities.inventory.AgentStarterKitService` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotTask.java` | `server.agents.plans.AgentPlanStep` or legacy plan adapter | `LEGACY_PROFILE` |
| `src/main/java/server/bots/Emote.java` | `server.agents.capabilities.dialogue.AgentEmote` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/ReplyChannel.java` | `server.agents.commands.AgentReplyChannel` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/build/BowmanBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/BuildStep.java` | `server.agents.profiles.SkillBuildStep` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/MageBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/ThiefBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/build/WarriorBuilds.java` | `server.agents.profiles.SkillBuildProfile` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackDataProvider.java` | `server.agents.capabilities.combat.data.AgentAttackDataProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackTiming.java` | `server.agents.capabilities.combat.data.AgentAttackTiming` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotDefenseDataProvider.java` | `server.agents.capabilities.combat.data.AgentDefenseDataProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotMobHitboxProvider.java` | `server.agents.capabilities.combat.data.AgentMobHitboxProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotWzXml.java` | `server.agents.capabilities.combat.data.AgentWzXml` | `MIGRATED_TO_AGENT` |
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
