# Bot To Agent Reconstruction Map

Status values:

- `MIGRATE_TO_AGENT`: move behavior into one Agent module.
- `SPLIT_TO_MULTIPLE_AGENT_MODULES`: split behavior across multiple bins.
- `COMPATIBILITY_ALIAS_TEMPORARY`: keep only as a temporary wrapper during migration.
- `DELETE_AFTER_MIGRATION`: remove once behavior/callers are migrated.
- `LEGACY_PROFILE`: preserve as legacy behavior profile until deliberately replaced.

This map tracks reconstruction from the source/master bot baseline into neutral Agent modules.

Recent map updates:

- Dead `BotInventoryManager` helper bodies for trash-equip collection and
  own-class equip checks were removed; their active callers already route
  through Agent inventory/trade/equipment services. The private
  `startTradeSequence` compatibility wrapper remains for legacy invite-once
  regression coverage.
- `BotInventoryManager` and `AgentInventoryTransferService` equip trade
  classification orchestration now lives in
  `AgentEquipTradeClassificationService`; callers only wire temporary bag scan,
  self-reserve, reservation-check, owner-lookup, and slow-log callbacks.
- `BotInventoryManager` transfer availability/count routing now lives in
  `AgentTradeTransferAvailabilityService`; the bot inventory shell only wires
  current equipped-slot, named-item, and category-collection callbacks.
- `BotInventoryManager.tickManualTrade` top-level manual trade tick
  orchestration now lives in `AgentManualTradeTickService`; the bot inventory
  shell only supplies temporary callbacks for active transfer suppression,
  trade-window lookup, timeout handling, and owner/peer branch wiring.
- `BotInventoryManager.tickManualTrade` owner-side manual trade routing now
  lives in `AgentManualOwnerTradeService`; the bot inventory shell only supplies
  temporary callbacks for delayed owner-invite accept, one-time greeting,
  completion, and post-trade auto-equip refill.
- `BotCombatManager` debug-stat target search and attack-plan lookup no longer
  sit on the report path. `AgentBotCombatReportRuntime` now calls
  `AgentBotCombatTargetRuntime` and `AgentBotCombatPlanRuntime` directly, while
  `BotCombatManager.describeDebugStats` remains only a temporary compatibility
  delegate.
- `BotManager` grind/local-opportunity combat now uses Agent combat plan,
  target, reposition, and attack runtimes directly. The bot combat facade still
  exists for older callers, but the main tick path no longer depends on
  `BotCombatManager.planAttack`, `attackMonster`, `findGrindTarget`,
  `findPatrolTarget`, `findFollowAttackTarget`, or `aoeRepositionTarget`.
- Ammo, inventory, movement, potion, shop, and BotManager production callers now
  read live combat config through `AgentCombatConfig.cfg` instead of the
  temporary `BotCombatManager.cfg` compatibility alias.
- BotManager common tick combat lifecycle now calls Agent runtimes directly for
  mob damage, death-state entry, skill-cache rebuild, support healing, and
  combat buff ticks.
- BotPhysicsEngine fall-damage dispatch now calls
  `AgentBotCombatDamageRuntime.applyFallDamage` directly instead of the
  temporary `BotCombatManager` facade.
- Combat skill-cache tests now exercise
  `AgentBotCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded` directly, reducing
  remaining `BotCombatManager` usage to compatibility-specific plan, target,
  damage, and config checks.
- Combat plan, target-search, and AoE-reposition tests now exercise Agent-owned
  runtimes directly. The old `BotCombatManager.AttackPlan`, `planAttack`,
  `attackMonster`, target-search, reachable-target, and AoE-reposition
  compatibility delegates have been removed; remaining `BotCombatManager`
  surface is now limited to config, damage/death, support, skill-cache, and
  debug-stat compatibility.
- `src/main/java/server/bots/BotCombatManager.java` has been deleted. Remaining
  combat behavior is reached through Agent combat modules and integration
  runtimes; `BotCombatManagerTest` remains only as a historical parity test
  class name.
- `src/main/java/server/bots/BotChatManager.java` has been deleted. `BotManager`
  now calls `AgentChatRuntime` with `AgentBotChatOrchestratorContext` directly;
  `BotChatManagerTest` remains only as a historical parity test class name.
- `server.bots.llm.CommandTypoSuggester` has moved to
  `server.agents.commands.AgentCommandTypoSuggester`; production and focused
  tests now use the Agent-owned command typo utility directly.
- `server.bots.llm.BotLlmConfig` has moved to
  `server.agents.capabilities.dialogue.llm.AgentLlmConfig`; existing LLM
  runtime, memory, Ollama client, command bridge, and chat routing code now
  share the Agent-owned static config.
- The remaining `server.bots.llm` runtime cluster has moved to
  `server.agents.capabilities.dialogue.llm`: `AgentLlmReplyService`,
  `AgentMemoryStore`, `AgentPromptBuilder`, `AgentSituationBuilder`,
  `AgentSenderRelation`, and `OllamaClient`. The old source/test package is
  empty, and `BotManager` calls the Agent reply service directly.
- `server.bots.BotOwnershipService` has moved to
  `server.agents.auth.AgentOwnershipService`. Character resolution,
  `bot_owners` lookup/registration, same-account adoption, denial messages, and
  authorization results are unchanged.
- `server.bots.BotPathLogger` has moved to
  `server.agents.monitoring.AgentPathLogger`. Navigation tick capture, path-log
  formatting, graph snapshot resolution, and file output are unchanged.
- Static skill build profile tables have moved from `server.bots.build` to
  `server.agents.capabilities.build.profiles`. Warrior, bowman, thief, mage,
  and build-step ordering data are unchanged.
- `server.bots.BotAirshowManager` has moved to
  `server.agents.capabilities.social.airshow.AgentAirshowService`. Airshow
  command syntax, frame timing, trail monster packets, and restore behavior are
  unchanged.
- `server.bots.BotNavigationProbe` has moved to
  `server.agents.capabilities.navigation.AgentNavigationProbe`. `@regennav`,
  CLI probe formatting, graph build reporting, point/edge/path probes, and
  optimality measurement behavior are unchanged.
- `server.bots.BotNavigationDebugOverlay` has moved to
  `server.agents.capabilities.navigation.AgentNavigationDebugOverlay`. `!botnav`
  graph/path/pathlog/clear command routing, fake-mist drawing, active-edge
  highlighting, and auto-clear behavior are unchanged.
- `server.bots.BotScrollReactionManager` has moved to
  `server.agents.capabilities.social.AgentScrollReactionService`. Range
  filtering, reaction chances, streak/load math, emote/chat/fidget behavior, and
  scheduler/reply adapter calls are unchanged.
- `server.bots.BotBuffManager` has moved to
  `server.agents.capabilities.combat.AgentBuffService`. Consumable buff-pot
  tick behavior, relevant-stat filtering, cheap-mode attack cap, chat summary,
  and debug-line formatting are unchanged.
- `server.bots.BotCommandParser` has moved to
  `server.agents.integration.AgentBotCommandParser`. Bot-entry target
  adaptation, transfer command wrapping, and targeted-command feedback are
  unchanged; `AgentCommandParser` remains the shared parser core.
- `server.bots.BotFidgetSideEffects` has moved to
  `server.agents.integration.AgentBotFidgetSideEffects`. Greeting/social
  fidget dispatch still delegates to the unchanged legacy fidget runtime, but
  the Agent movement callback no longer imports the bot-side shim.
- `server.bots.BotSessionLifecycleSideEffects` has moved to
  `server.agents.integration.AgentBotSessionLifecycleSideEffects`. Relog and
  owner-entry lookup behavior still delegates to `BotManager`, but session
  orchestration no longer imports a bot-side lifecycle shim.
- `server.bots.BotMovementTargetSideEffects` has moved to
  `server.agents.integration.AgentBotMovementTargetSideEffects`. Snapshot
  conversion and raw navigation-target override behavior are unchanged while
  BotManager remains the temporary target-snapshot source.
- `server.bots.BotScriptRuntime` has moved to
  `server.agents.plans.AgentScriptRuntimeState`. `BotEntry` still carries the
  state during reconstruction, but the script runtime state bag is Agent-owned.
- `server.bots.BotScript`, `BotScriptContext`, `BotScriptStep`, and
  `BotScriptRunner` have moved to `server.agents.plans` as `AgentScript`,
  `AgentScriptContext`, `AgentScriptStep`, and `AgentScriptRunner`. KPQ script
  content still lives under the legacy PQ package and behavior is unchanged.
- `server.bots.BotTask` has moved to `server.agents.plans.AgentTask`. The
  queue/execution path is still temporarily backed by BotEntry and BotManager,
  but the task value model is Agent-owned.
- `server.bots.BotStarterKitManager` has moved to
  `server.agents.capabilities.build.AgentStarterKitService`. Job-change,
  starter-kit grant, auto-equip, and build-status behavior are unchanged.
- `server.bots.BotFidgetManager` has moved to
  `server.agents.capabilities.movement.fidget.AgentFidgetService`. Active
  fidget ticking, idle/social/greeting fidget rolls, speed-mismatch fidget
  eligibility, jump/prone/sideways fidget execution, origin-return cleanup, and
  prone attack visuals are unchanged; BotEntry, BotMovementManager, BotManager,
  and BotPhysicsEngine remain temporary backing seams during movement
  reconstruction.
- `server.bots.BotFallbackMovementManager` has moved to
  `server.agents.capabilities.movement.AgentFallbackMovementService`. Rope
  fallback target selection, immediate rope attach/jump, swim jump-up, down-jump
  fallback, ledge walk-off targeting, and jump-probe fallback behavior are
  unchanged; BotEntry, BotMovementManager, and BotPhysicsEngine remain
  temporary backing seams during movement reconstruction.
- `server.bots.BotNavigationGraph` and `server.bots.BotNavigationGraphProvider`
  have moved to `server.agents.capabilities.navigation.AgentNavigationGraph`
  and `AgentNavigationGraphService`. Graph cache shape, graph version, warmup
  executors, build reports, region/edge construction, collidable-footing caches,
  and pathfinding inputs are unchanged; BotNavigationManager and BotPhysicsEngine
  remain temporary movement/navigation backing seams.

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
| `src/main/java/server/bots/BotAirshowManager.java` | `server.agents.capabilities.social.airshow.AgentAirshowService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotAmmoManager.java` | `server.agents.capabilities.supplies.AgentAmmoService`, `server.agents.capabilities.supplies.AgentAmmoSharePolicy`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; ammo-share request eligibility, donor selection, scheduling, owner-offer routing, donor quantity math, donor tie-break policy, and ammo request/offer dialogue pools are Agent-owned |
| `src/main/java/server/bots/BotAttackExecutionProvider.java` | `server.agents.capabilities.combat.AgentAttackExecutionProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotBuffManager.java` | `server.agents.capabilities.combat.AgentBuffService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotBuildManager.java` | `server.agents.capabilities.build.AgentBuildService` and `server.agents.capabilities.build.profiles.*` | `MIGRATED_TO_AGENT`; AP/SP/job prompt and assignment orchestration moved unchanged, with later internal splitting still recommended |
| `src/main/java/server/bots/BotChatManager.java` | `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.dialogue.AgentChatRuntime`, `server.agents.capabilities.dialogue.AgentChatCommandClassifier`, `server.agents.capabilities.dialogue.AgentTradeDialogueClassifier`, `server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier`, `server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier`, `server.agents.capabilities.dialogue.AgentSocialDialogueClassifier`, `server.agents.capabilities.dialogue.AgentBuildDialogueClassifier`, `server.agents.capabilities.dialogue.AgentDialogueReportFormatter`, `server.agents.commands.AgentReplyQueue`, `server.agents.events` | `MIGRATED_TO_AGENT`; source file deleted after named random reply pools, reply queue, movement/follow/fidget, supply-request/direct supply, query/toggle, support/heal/buff toggle, logout/relog/away session request and confirmation normalization, report/debug, trade/drop/item and pending drop-choice, trade-invite/shop/maker utility, equipment/autoequip, greeting/fame, build/job/AP/SP classification, skill-tree choice resolution, job advancement resolution, report/AP-build/job-display/skill-tree/learned-skill formatting, upgrade-request classification, handled-state, and top-level chat orchestration moved to Agent-owned modules |
| `src/main/java/server/bots/BotCombatManager.java` | `server.agents.capabilities.combat`, `server.agents.capabilities.dialogue`, and `server.agents.integration.AgentBotCombatReportRuntime` | `MIGRATED_TO_AGENT`; source file deleted after config, combat planning, target search, AoE reposition, damage/death, support, skill-cache, debug-stat, packet execution, and combat reply behavior moved to Agent-owned modules |
| `src/main/java/server/bots/BotCommandParser.java` | `server.agents.integration.AgentBotCommandParser` and `server.agents.commands.AgentCommandParser` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotEntry.java` | `server.agents.runtime.AgentSession` and capability state objects | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; message queue now uses Agent-owned queued message type |
| `src/main/java/server/bots/BotEquipManager.java` | `server.agents.capabilities.equipment` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; map-damage benchmark snapshot/selection now lives in `AgentMapDamageProfile`, weapon/job compatibility plus self-reserve weapon track labels live in `AgentWeaponCompatibilityPolicy`, recommendation result data uses `AgentEquipRecommendation`, and owned/incoming equipment reserve, requirement-gate, requirement-comparison, and future-track policy lives in `AgentEquipmentReservePolicy`, while optimizer and equip execution remain temporary bot seams |
| `src/main/java/server/bots/BotFallbackMovementManager.java` | `server.agents.capabilities.movement.AgentFallbackMovementService` | `MIGRATED_TO_AGENT`; fallback steering, rope/drop/swim/jump immediate actions, and ledge targeting moved unchanged |
| `src/main/java/server/bots/BotFidgetManager.java` | `server.agents.capabilities.movement.fidget.AgentFidgetService` | `MIGRATED_TO_AGENT`; active fidget state machine and social/greeting fidget start behavior moved unchanged, while BotEntry and movement/physics helpers remain temporary backing seams |
| `src/main/java/server/bots/BotFidgetSideEffects.java` | `server.agents.integration.AgentBotFidgetSideEffects` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotInventoryManager.java` | `server.agents.capabilities.inventory`, `looting`, `trade`, `server.agents.capabilities.dialogue.AgentItemQueryNormalizer`, `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.supplies.AgentAmmoService`, `server.agents.capabilities.supplies.AgentPotionService`, `server.agents.capabilities.supplies.AgentPotionSharePolicy`, `server.agents.capabilities.trade.AgentSupplyShareTradeService`, `server.agents.capabilities.trade.AgentTradeCommandProfiler`, `server.agents.capabilities.trade.AgentInventoryTransferService`, `server.agents.capabilities.trade.AgentManualTradeService`, `server.agents.capabilities.trade.AgentManualPeerTradeService`, `server.agents.capabilities.trade.AgentGroupedTradeTransferService`, `server.agents.capabilities.trade.AgentReservedEquipTradeTransferService`, `server.agents.capabilities.trade.AgentPreparedTradeTransferService`, `server.agents.capabilities.trade.AgentTradeTransferRouter`, `server.agents.capabilities.trade.AgentTradeRecipientService`, `server.agents.capabilities.trade.AgentMesoTradeService`, `server.agents.capabilities.trade.AgentDirectItemTradeService`, `server.agents.capabilities.trade.AgentTradeStateService`, `server.agents.capabilities.trade.AgentTradeBatchService`, `server.agents.capabilities.trade.AgentTradeCancellationService`, `server.agents.capabilities.trade.AgentTradeCompletionService`, `server.agents.capabilities.trade.AgentTradeSequenceService`, `server.agents.capabilities.trade.AgentTradeResetService`, `server.agents.capabilities.trade.AgentTradeMesoAddService`, `server.agents.capabilities.trade.AgentTradeItemAddService`, `server.agents.capabilities.trade.AgentTradeAllItemsAddedService`, `server.agents.capabilities.trade.AgentTradeCategoryAnnouncementService`, `server.agents.capabilities.trade.AgentTradeInviteWaitService`, `server.agents.capabilities.trade.AgentTradeConfirmWaitService`, `server.agents.capabilities.trade.AgentTradeClosedWindowService`, `server.agents.capabilities.trade.AgentTradeTransferStartGuard`, `server.agents.capabilities.trade.AgentTradeQueuedRetryService`, `server.agents.capabilities.trade.AgentTradeBetweenBatchService`, `server.agents.capabilities.trade.AgentTradeItemAddTickService`, `server.agents.capabilities.trade.AgentTradeTickService`, `server.agents.capabilities.trade.AgentTradeSequenceOrchestrator` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; item query normalization and USE-item effect/recovery/buff classification now use Agent policies directly, and item-presence/drop-safety/safe-bag collection/drop-slot selection/named-item collection/counting/name-cache checks, generic safe bag collection, trade category collection/name preparation, category/direct transfer start entry points, top-level category transfer routing, transfer availability/count decisions, direct item trade preflight and reply/retry/start routing, queued bot-initiated trade retry ticking, between-batch trade progression, trade item-add tick ordering, top-level trade tick ordering, item-choice trade/drop branching and loot-inhibit timing, manual trade timeout/clear/greeting/accept-delay handling, manual peer-bot trade routing, equip/ammo grouped trade transfer routing, reserved-equips transfer routing, prepared-items transfer routing, trade state initialization/batch-progress/reset helpers, trade sequence recipient guard/initialization/open-batch orchestration, trade reset restore/manual-clear/sequence-clear/refill ordering and dead legacy reset removal, trade-window meso add/insufficient-meso cancellation decision, trade-window item copy/add/remove/packet side effects, trade all-items-added flag/timer/chat step, trade category announcement chat/timer step, trade invite-wait timer/timeout cancellation, trade owner-confirm wait completion/timeout handling and dead fallback removal, trade closed-window completion/cancel/decline handling and dead fallback removal, trade transfer start guard replies, trade cancellation reply/cancel/reset orchestration, trade completion owner-given snapshot/complete/reaction orchestration, equipped-slot named trade preparation, inventory floor-drop command execution/replies, grind/patrol loot target selection, stale loot cleanup, trade recipient resolution, meso-trade start decision/reply/start routing, trade page/meso/item-quantity policy, equip/ammo category parsing, equip/ammo category selection, equip trade group model/classification/navigation/page helpers, item-id ordering, recipient-duplicate trade prioritization, USE-item trade grouping, ammo trade grouping/category selection, reserved-equipment trade ordering/page-message/page-slicing, sell-trash equipment protection/collection policy, ammo item-to-weapon classification/share collection/trade-weapon eligibility, potion-share recovery scoring/slot eligibility/stack collection, supply-share trade startup/retry, trade command profiling, transfer command boundary, drop-limited-map reply, and trade reply/result/error pools are Agent-owned; dead report/supply/share/profiler pass-through wrappers and dead transfer-start helper bodies have been removed |
| `src/main/java/server/bots/BotLootEligibility.java` | `server.agents.capabilities.looting.AgentLootEligibility` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotMakerManager.java` | `server.agents.capabilities.build.AgentMakerService` | `MIGRATED_TO_AGENT`; Maker crystal and trash-disassembly batch orchestration moved unchanged |
| `src/main/java/server/bots/BotManager.java` | `server.agents.runtime`, `commands`, `events`, capability orchestrators | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotMovementManager.java` | `server.agents.capabilities.movement` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; cooldown/delay countdown math, climb idle/snap/rope identity decision policy, and ground horizontal step policy are Agent-owned |
| `src/main/java/server/bots/BotMovementTargetSideEffects.java` | `server.agents.integration.AgentBotMovementTargetSideEffects` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotMovementProfile.java` | `server.agents.capabilities.movement.AgentMovementProfile` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationDebugOverlay.java` | `server.agents.capabilities.navigation.AgentNavigationDebugOverlay` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationGraph.java` | `server.agents.capabilities.navigation.AgentNavigationGraph` | `MIGRATED_TO_AGENT`; graph model, region/edge/segment data, cache serialization shape, and lookup helpers moved unchanged |
| `src/main/java/server/bots/BotNavigationGraphProvider.java` | `server.agents.capabilities.navigation.AgentNavigationGraphService` | `MIGRATED_TO_AGENT`; graph build/warmup/cache/report/collidable helper behavior moved unchanged, with BotPhysicsEngine/BotMovementManager as temporary explicit seams |
| `src/main/java/server/bots/BotNavigationManager.java` | `server.agents.capabilities.navigation` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotNavigationMapLoader.java` | `server.agents.capabilities.navigation.AgentNavigationMapLoader` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationProbe.java` | `server.agents.capabilities.navigation.AgentNavigationProbe` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotOfferManager.java` | `server.agents.capabilities.trade.AgentOfferService`, `equipment`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; owner/sibling gear offer orchestration, pending offer responses, loot-offer prompts, reservation checks, and best-upgrade request routing now live in Agent trade |
| `src/main/java/server/bots/BotOwnershipService.java` | `server.agents.auth.AgentOwnershipService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPathLogger.java` | `server.agents.monitoring.AgentPathLogger` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPerformanceMonitor.java` | `server.agents.runtime.AgentPerformanceMonitor` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPhysicsEngine.java` | `server.agents.capabilities.movement.AgentPhysicsEngine` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotPotionManager.java` | `server.agents.capabilities.supplies.AgentPotionService`, `server.agents.capabilities.supplies.AgentAutopotPolicy`, `server.agents.capabilities.supplies.AgentPotionInventoryPolicy`, `server.agents.capabilities.supplies.AgentPassiveRecoveryPolicy`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; potion tick orchestration, autopot setup/debug reporting, low-pot supply sharing, donor selection, passive recovery, and grind-start supply reporting now live in Agent supplies |
| `src/main/java/server/bots/BotScript.java` | `server.agents.plans.AgentScript` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptContext.java` | `server.agents.plans.AgentScriptContext` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptRunner.java` | `server.agents.plans.AgentScriptRunner` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptRuntime.java` | `server.agents.plans.AgentScriptRuntimeState` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptStep.java` | `server.agents.plans.AgentScriptStep` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScrollReactionManager.java` | `server.agents.capabilities.social.AgentScrollReactionService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotSessionLifecycleSideEffects.java` | `server.agents.integration.AgentBotSessionLifecycleSideEffects` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotShopManager.java` | `server.agents.capabilities.shop.AgentShopService`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; shop visit orchestration, sell-trash visit routing, resupply/recharge purchases, shop approach selection, timeout handling, and purchase sequence callbacks now live in Agent shop |
| `src/main/java/server/bots/BotStarterKitManager.java` | `server.agents.capabilities.build.AgentStarterKitService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotTask.java` | `server.agents.plans.AgentTask` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/Emote.java` | `server.agents.capabilities.dialogue.AgentEmote` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/ReplyChannel.java` | `server.agents.commands.AgentReplyChannel` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/build/BowmanBuilds.java` | `server.agents.capabilities.build.profiles.BowmanBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/BuildStep.java` | `server.agents.capabilities.build.profiles.BuildStep` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/MageBuilds.java` | `server.agents.capabilities.build.profiles.MageBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/ThiefBuilds.java` | `server.agents.capabilities.build.profiles.ThiefBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/WarriorBuilds.java` | `server.agents.capabilities.build.profiles.WarriorBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackDataProvider.java` | `server.agents.capabilities.combat.data.AgentAttackDataProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackTiming.java` | `server.agents.capabilities.combat.data.AgentAttackTiming` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotDefenseDataProvider.java` | `server.agents.capabilities.combat.data.AgentDefenseDataProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotMobHitboxProvider.java` | `server.agents.capabilities.combat.data.AgentMobHitboxProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotWzXml.java` | `server.agents.capabilities.combat.data.AgentWzXml` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/BotLlmConfig.java` | `server.agents.capabilities.dialogue.llm.AgentLlmConfig` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/BotLlmReplyManager.java` | `server.agents.capabilities.dialogue.llm.AgentLlmReplyService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/BotMemoryStore.java` | `server.agents.capabilities.dialogue.llm.AgentMemoryStore` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/CommandTypoSuggester.java` | `server.agents.commands.AgentCommandTypoSuggester` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/OllamaClient.java` | `server.agents.capabilities.dialogue.llm.OllamaClient` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/PromptBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentPromptBuilder` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/SenderRelation.java` | `server.agents.capabilities.dialogue.llm.AgentSenderRelation` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/SituationBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentSituationBuilder` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/pq/BotKpqStage1.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage1` | `MIGRATED_TO_AGENT`; KPQ stage-1 scripted movement, coupon target, grind, exchange, and pass delivery behavior moved unchanged |
| `src/main/java/server/bots/pq/BotKpqStage5.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage5` | `MIGRATED_TO_AGENT`; KPQ stage-5 reward claim and announcement behavior moved unchanged |
| `src/main/java/server/bots/pq/BotKpqState.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqState` | `MIGRATED_TO_AGENT`; temporary BotEntry-backed KPQ state bag now uses Agent-owned type |
| `src/main/java/server/bots/pq/BotPqHooks.java` | `server.agents.capabilities.partyquest.AgentPartyQuestHooks` | `MIGRATED_TO_AGENT`; PQ tick, NPC lock, grind/follow defaults, and coupon-loot gating moved unchanged |
| `src/main/resources/db/tables/025-bot-ownership.sql` | `server.agents.legacy` documentation initially; later external registry or deletion | `LEGACY_PROFILE` |
