package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentGrindTargetSearchService;
import server.agents.capabilities.combat.AgentGrindTargetCommitmentService;
import server.agents.capabilities.combat.AgentGrindModeTickService;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackService;
import server.agents.capabilities.combat.AgentGrindRangedEngagementService;
import server.agents.capabilities.combat.AgentGrindNavigationTailService;
import server.agents.capabilities.quest.AgentPartyQuestSyncService;

import server.agents.capabilities.dialogue.AgentChatIngressService;
import server.agents.capabilities.dialogue.AgentTargetedChatRouteService;
import server.agents.capabilities.dialogue.AgentUntargetedChatRouteService;
import server.agents.capabilities.dialogue.AgentWhisperCommandService;

import server.agents.runtime.AgentAnchoredFarmModeTickService;
import server.agents.runtime.AgentAnchoredFarmRuntime;
import server.agents.runtime.AgentCommonTickRuntime;
import server.agents.runtime.AgentDeathTickService;
import server.agents.runtime.AgentFinalMovementTailService;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentFollowAnchorService;
import server.agents.runtime.AgentFormationService;
import server.agents.runtime.AgentFollowIdleMovementRuntime;
import server.agents.runtime.AgentFollowTargetRuntime;
import server.agents.runtime.AgentFollowTargetPositionService;
import server.agents.runtime.AgentFollowMapSyncRuntime;
import server.agents.runtime.AgentFollowOpportunityTickService;
import server.agents.runtime.AgentFormationCommandRuntime;
import server.agents.runtime.AgentGrindCombatRuntime;
import server.agents.runtime.AgentGrindNavigationRuntime;
import server.agents.runtime.AgentGrindModeDispatchService;
import server.agents.runtime.AgentIdleModeTickService;
import server.agents.runtime.AgentGrindNoTargetFallbackService;
import server.agents.runtime.AgentGrindTargetRuntime;
import server.agents.runtime.AgentIdlePhysicsRuntime;
import server.agents.runtime.AgentLeaderSessionService;
import server.agents.runtime.AgentLeaderSafetyService;
import server.agents.runtime.AgentLifecycleChatCommandRuntime;
import server.agents.runtime.AgentLiveTickContextRuntime;
import server.agents.runtime.AgentLiveModeTickService;
import server.agents.runtime.AgentLiveTickGateService;
import server.agents.runtime.AgentLocalAttackMoveWindowRuntime;
import server.agents.runtime.AgentLocalOpportunityAttackRuntime;
import server.agents.runtime.AgentMapEnvironmentService;
import server.agents.runtime.AgentMapTransitionRuntime;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentMovementOnlyRuntime;
import server.agents.runtime.AgentMovementTickRuntime;
import server.agents.runtime.AgentOwnerlessTickService;
import server.agents.runtime.AgentOfflineLoadRuntime;
import server.agents.runtime.AgentPartyLifecycleService;
import server.agents.runtime.AgentPositionService;
import server.agents.runtime.AgentRegistrationRuntime;
import server.agents.runtime.AgentReloginRuntime;
import server.agents.runtime.AgentRecoveryTickService;
import server.agents.runtime.AgentRecoveryTeleportRuntime;
import server.agents.runtime.AgentRespawnRuntime;
import server.agents.runtime.AgentReturnScrollService;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentScriptedMoveCombatTickService;
import server.agents.runtime.AgentScriptTaskExecutionService;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.agents.runtime.AgentScriptTaskTickService;
import server.agents.runtime.AgentShopVisitTickService;
import server.agents.runtime.AgentTargetSnapshot;
import server.agents.runtime.AgentTargetSnapshotService;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSpawnPositionService;
import server.agents.runtime.AgentSpawnRuntime;
import server.agents.runtime.AgentStandaloneMoveTargetRuntime;
import server.agents.runtime.AgentStuckDetectionRuntime;
import server.agents.runtime.AgentTickFailureRuntime;
import server.agents.runtime.AgentTickCoreService;
import server.agents.runtime.AgentTickOrchestrator;
import server.agents.runtime.AgentTickPreflightRuntime;
import server.agents.runtime.AgentTickStateMaintenanceService;
import server.agents.runtime.AgentTradeWindowTickService;
import server.agents.runtime.AgentTrackedMapChangeTickService;

import server.agents.capabilities.looting.AgentGrindLootTargetService;
import server.agents.capabilities.social.AgentScrollReactionNotificationService;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.supplies.AgentGroupSupplyResponderSelector;
import server.agents.capabilities.supplies.AgentPotionCheckRequestService;
import server.agents.capabilities.trade.AgentOwnerItemNotificationService;
import server.agents.capabilities.trade.AgentPendingOfferChatRouteService;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.plans.AgentScriptMoveTargetService;


import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotCommandParser;
import server.agents.integration.AgentBotCombatAttackRuntime;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotCombatPlanRuntime;
import server.agents.integration.AgentBotCombatTargetRuntime;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotLeaderStateRuntime;
import server.agents.integration.AgentBotMapStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotPotionStateRuntime;
import server.agents.integration.AgentBotPqRuntime;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.integration.AgentBotShopStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.agents.integration.AgentBotTargetedCommandMatch;
import server.agents.plans.AgentTask;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.capabilities.dialogue.AgentChatRuntime;
import server.agents.capabilities.dialogue.llm.AgentLlmConfig;
import server.agents.integration.AgentBotChatOrchestratorContext;
import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentCommandTypoSuggester;
import server.agents.auth.AgentAuthorizationResult;
import server.agents.registry.AgentResolvedCharacter;
import client.Character;
import client.Disease;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.life.Monster;
import server.life.MobSkill;
import server.maps.MapItem;
import server.maps.MapleMap;
import server.quest.Quest;
import tools.PacketCreator;
import tools.Pair;

import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class BotManager {
    private static final Logger log = LoggerFactory.getLogger(BotManager.class);
    private static final BotManager instance = new BotManager();

    /** Compatibility alias for the Agent-owned runtime config. */
    public static AgentRuntimeConfig.Config cfg = AgentRuntimeConfig.cfg;

    public static BotManager getInstance() { return instance; }

    // Public facade for the !botcfg GM command.
    public static List<String> botCombatConfigLines() { return AgentCombatConfig.configFieldLines(); }
    public static String botCombatConfigLine(String name) { return AgentCombatConfig.configFieldLine(name); }
    public static String setBotCombatConfig(String name, String value) { return AgentCombatConfig.setConfigField(name, value); }

    // ownerCharId → list of owned bot entries (1:N)
    private final Map<Integer, List<BotEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
    // ownerCharId → cluster-anchor town position. First bot to warp picks a random
    // portal in the return map; later bots warp to a randomized nearby offset.
    // Cleared when the owner becomes active again.
    private final Map<Integer, Point> townClusterAnchors = AgentLeaderSafetyService.townClusterAnchorsByLeaderId();
    private record LocalOpportunityAttackResult(boolean consumedTick, Point targetPos) {}

    private static final int PLATFORM_EDGE_INSET_PX = 12;
    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void registerBot(int ownerCharId, Character owner, Character bot) {
        registerBotInternal(ownerCharId, owner, bot, false);
    }

    public BotEntry registerSpawnedBot(int ownerCharId, Character owner, Character bot) {
        return registerBotInternal(ownerCharId, owner, bot, true);
    }

    public record SpawnResult(boolean success, Character bot, boolean autoRegistered, String errorMessage) {
        static SpawnResult ok(Character bot, boolean autoRegistered) {
            return new SpawnResult(true, bot, autoRegistered, null);
        }
        static SpawnResult fail(String msg) {
            return new SpawnResult(false, null, false, msg);
        }
    }

    /** Spawn a registered bot for the given owner, placing it at the owner's current position in follow mode. */
    public SpawnResult spawnBotForOwner(Character owner, String botName) {
        AgentLifecycleService.AgentSpawnResult result = AgentSpawnRuntime.spawnAgentForLeader(
                owner, botName, this::registerSpawnedBot, this::issueFollowOwner, log);
        if (!result.success()) {
            return SpawnResult.fail(result.errorMessage());
        }
        return SpawnResult.ok(result.agent(), result.autoRegistered());
    }

    public void joinBotToOwnerParty(Character owner, Character bot) {
        AgentPartyLifecycleService.joinAgentToLeaderParty(owner, bot);
    }

    private BotEntry getBotEntry(int ownerCharId, int botCharId) {
        return AgentRuntimeRegistry.findByCharacterId(ownerCharId, botCharId);
    }

    public Character loadOfflineBot(int charId, int world, int channel, MapleMap targetMap, Point desiredPosition) throws SQLException {
        return AgentOfflineLoadRuntime.loadOfflineAgent(charId, world, channel, targetMap, desiredPosition);
    }

    public Point resolveSpawnPosition(MapleMap map, Point desiredPosition) {
        return AgentSpawnPositionService.resolveSpawnPosition(map, desiredPosition);
    }

    private BotEntry registerBotInternal(int ownerCharId, Character owner, Character bot, boolean normalizeSpawnState) {
        return AgentRegistrationRuntime.registerAgent(ownerCharId, owner, bot, normalizeSpawnState, this::tick);
    }

    public void removeBot(int ownerCharId) {
        AgentRuntimeCleanupService.removeAgentsForLeader(ownerCharId);
    }

    /** Cancel and remove a bot by the bot character's own ID (used during shutdown/disconnect). */
    public boolean removeBotByCharId(int botCharId) {
        return AgentRuntimeCleanupService.removeAgentByCharacterId(botCharId);
    }

    /** Release bot-owned runtime state before this character leaves bot control. */
    public boolean cleanupBotRuntimeState(Character bot) {
        return AgentRuntimeCleanupService.cleanupAgentRuntimeState(bot);
    }

    /** Disown a bot by name - cancels its AI tick and leaves it idle in the map. */
    public boolean dismissBot(int ownerCharId, String botName) {
        return AgentLifecycleChatCommandRuntime.dismissAgent(ownerCharId, botName, this::issueStop);
    }

    /** Recruit an ownerless bot by name into the owner's group. Returns an error string on failure, null on success. */
    public String recruitBot(int ownerCharId, Character owner, String botName) {
        return AgentLifecycleChatCommandRuntime.recruitAgent(ownerCharId, owner, botName, this::registerBot);
    }

    /** Transfer a bot from this owner to another player in the same map. Returns an error string on failure, null on success. */
    public String giveBot(int ownerCharId, Character owner, String botName, String targetName) {
        return AgentLifecycleChatCommandRuntime.transferAgent(
                ownerCharId, owner, botName, targetName, this::issueStop, this::registerBot);
    }

    public Character getActiveOwnerByBotCharId(int botCharId) {
        return AgentRuntimeRegistry.activeLeaderByAgentCharacterId(botCharId);
    }

    public void requestBotPotionCheckSoon(Character bot) {
        AgentPotionCheckRequestService.requestPotionCheckSoon(bot);
    }

    public Character getBot(int ownerCharId) {
        return AgentRuntimeRegistry.firstAgent(ownerCharId);
    }

    BotEntry getFirstBotEntry(int ownerCharId) {
        return AgentRuntimeRegistry.firstEntry(ownerCharId);
    }

    public List<BotEntry> getBotEntries(int ownerCharId) {
        return AgentRuntimeRegistry.entriesForLeader(ownerCharId);
    }

    /** Called when the owner picks up or receives an item; notifies bots that might want it. */
    public void notifyOwnerGainedItem(Character owner, Item item) {
        AgentOwnerItemNotificationService.notifyOwnerGainedItem(owner, item);
    }

    /** Called when a trade recipient receives an item; skips circular own-bot trade scans. */
    public void notifyOwnerGainedTradeItem(Character recipient, Item item, Character source) {
        AgentOwnerItemNotificationService.notifyOwnerGainedTradeItem(recipient, item, source);
    }

    public void notifyNearbyBotsOfScroll(Character source,
                                         client.inventory.Equip.ScrollResult result,
                                         int scrollItemId,
                                         long delayMs) {
        AgentScrollReactionNotificationService.notifyNearbyAgentsOfScroll(source, result, scrollItemId, delayMs);
    }

    public BotEntry getBotEntry(int ownerCharId, String botName) {
        return AgentRuntimeRegistry.findByName(ownerCharId, botName);
    }

    public void syncPartyBotsQuestStart(Character source, Quest quest, int npc) {
        AgentPartyQuestSyncService.syncPartyAgentsQuestStart(source, quest, npc);
    }

    public void syncPartyBotsQuestProgress(Character source, int questId, int infoNumber, String progress) {
        AgentPartyQuestSyncService.syncPartyAgentsQuestProgress(source, questId, infoNumber, progress);
    }

    public void syncPartyBotsQuestComplete(Character source, Quest quest, int npc, Integer selection) {
        AgentPartyQuestSyncService.syncPartyAgentsQuestComplete(source, quest, npc, selection);
    }

    public String manualTradeGreeting() {
        return AgentTradeDialogueService.manualTradeGreeting();
    }

    public void handleChat(Character owner, String message, AgentReplyChannel channel) {
        AgentChatIngressService.handleChat(owner, message, channel, chatIngressHooks());
    }

    private AgentChatIngressService.Hooks chatIngressHooks() {
        return new AgentChatIngressService.Hooks(
                this::handlePendingLootOfferResponse,
                (leader, message) -> AgentLifecycleChatCommandRuntime.handleRecruitCommand(
                        leader, message, this::recruitBot),
                (leader, message) -> AgentLifecycleChatCommandRuntime.handleTransferCommand(
                        leader, message, this::giveBot),
                (leader, message) -> AgentFormationCommandRuntime.handleFormationCommand(
                        leader,
                        message,
                        bots::get,
                        defaultFormationState(),
                        cfg.FOLLOW_STAGGER,
                        BotMovementManager.cfg.FOLLOW_Y_CAP),
                bots::get,
                (leader, message) -> AgentLifecycleChatCommandRuntime.handleDismissCommand(
                        leader, message, this::dismissBot),
                (leader, entries, message, channel) -> AgentTargetedChatRouteService.handleTargetedChat(
                        leader,
                        entries,
                        message,
                        channel,
                        targetedChatHooks()),
                (leader, entries, message, channel) -> AgentUntargetedChatRouteService.handleUntargetedChat(
                        leader,
                        entries,
                        message,
                        channel,
                        untargetedChatHooks()));
    }
    private boolean handlePendingLootOfferResponse(Character speaker, String message) {
        return AgentPendingOfferChatRouteService.handlePendingOfferResponse(bots.values(), speaker, message);
    }

    private AgentTargetedChatRouteService.Hooks targetedChatHooks() {
        return new AgentTargetedChatRouteService.Hooks(
                AgentBotCommandParser::resolveTargetedBot,
                AgentChatCommandClassifier::matchFollowTarget,
                AgentFollowTargetRuntime::applyFollowTargetCommand,
                AgentBotReplyChannelStateRuntime::setReplyChannel,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentBotManagerReplyRuntime::queueReply,
                BotManager::handleAgentChat,
                AgentChatRuntime::wasLastChatHandled,
                System::currentTimeMillis,
                AgentBotActivityStateRuntime::recordLastOwnerCommand,
                () -> AgentLlmConfig.enabled,
                server.agents.capabilities.dialogue.llm.AgentLlmReplyService::maybeRespond,
                Character::yellowMessage);
    }

    private AgentUntargetedChatRouteService.Hooks untargetedChatHooks() {
        return new AgentUntargetedChatRouteService.Hooks(
                AgentChatCommandClassifier::matchFollowTarget,
                AgentFollowTargetRuntime::applyFollowTargetCommand,
                AgentChatCommandClassifier::isGroupSupplyRequest,
                AgentGroupSupplyResponderSelector::select,
                AgentBotReplyChannelStateRuntime::setReplyChannel,
                BotManager::handleAgentChat,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentBotManagerReplyRuntime::queueReply);
    }

    // -------------------------------------------------------------------------
    AgentFormationService.FormationState formationStateFor(BotEntry entry) {
        return AgentFormationService.stateForEntry(entry, AgentFormationService.formationsByLeaderId(), defaultFormationState());
    }

    public Character resolveFollowAnchor(BotEntry entry, Character owner) {
        List<BotEntry> siblingEntries = owner == null ? List.of() : getBotEntries(owner.getId());
        return AgentFollowAnchorService.resolve(entry, owner, siblingEntries);
    }

    void setFormationState(Character owner,
                           AgentFormationService.FormationType type,
                           int px,
                           int snapRange,
                           List<BotEntry> entries) {
        if (owner == null) {
            return;
        }

        AgentFormationService.FormationState formation = new AgentFormationService.FormationState(type, px, snapRange);
        AgentFormationService.formationsByLeaderId().put(owner.getId(), formation);
        if (entries == null) {
            return;
        }

        AgentFormationService.applyOffsets(entries, formation);
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(cfg.FOLLOW_STAGGER, BotMovementManager.cfg.FOLLOW_Y_CAP);
    }

    public AgentTargetSnapshot captureTargetSnapshot(BotEntry entry) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        List<BotEntry> siblingEntries = owner == null ? List.of() : getBotEntries(owner.getId());
        return AgentTargetSnapshotService.capture(
                entry,
                siblingEntries,
                AgentFormationService.formationsByLeaderId(),
                defaultFormationState(),
                (followBase, followAnchor, followAnchorPos, snapRange, map) ->
                        AgentFollowTargetPositionService.resolve(
                                followBase, followAnchor, followAnchorPos, snapRange, map, PLATFORM_EDGE_INSET_PX));
    }

    // AoE reposition commitment: returns the sweet-spot Point to walk to before firing, or null to
    // fire now. Scores once when a commitment starts (AgentBotCombatAoeRepositionRuntime); while
    // committed it just returns the stored anchor — no further scoring — until the bot arrives, the
    // bounded-chase deadline expires, or the target dies/clears.
    private static Point resolveAoeReposition(BotEntry entry, Character bot, Monster target,
                                              AgentAttackPlan attackPlan, Point botPos) {
        return AgentGrindCombatRuntime.resolveAoeReposition(entry, bot, target, attackPlan, botPos);
    }

    public static Point selectGrindNavigationTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        return AgentGrindNavigationRuntime.selectGrindNavigationTarget(entry, botPos, combatTargetPos);
    }

    private static Point selectGrindNavigationTarget(BotEntry entry,
                                                     Point botPos,
                                                     Point combatTargetPos,
                                                     boolean crossRegionRetreatChecked) {
        return AgentGrindNavigationRuntime.selectGrindNavigationTarget(
                entry, botPos, combatTargetPos, crossRegionRetreatChecked);
    }

    static Point selectCrossRegionRetreatTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        return AgentGrindNavigationRuntime.selectCrossRegionRetreatTarget(entry, botPos, combatTargetPos);
    }

    static boolean shouldUseLocalCombatRetreatTarget(BotEntry entry,
                                                     Point botPos,
                                                     Point combatTargetPos,
                                                     Point retreatPos) {
        return AgentGrindNavigationRuntime.shouldUseLocalCombatRetreatTarget(
                entry, botPos, combatTargetPos, retreatPos);
    }

    static Point resolveNoGrindTargetPosition(BotEntry entry, Point botPos, MapleMap map) {
        return AgentGrindTargetRuntime.resolveNoGrindTargetPosition(entry, botPos, map);
    }

    static Point resolveNoGrindTargetPosition(BotEntry entry, Point botPos) {
        return AgentGrindTargetRuntime.resolveNoGrindTargetPosition(entry, botPos);
    }

    private static Point activeGrindLootPosition(BotEntry entry, Point botPos) {
        return AgentGrindTargetRuntime.activeGrindLootPosition(entry, botPos);
    }

    static void suppressGrindLootRetry(BotEntry entry, MapItem loot) {
        AgentGrindTargetRuntime.suppressGrindLootRetry(entry, loot);
    }

    static double activeLootTravelDistSq(Point botPos, Point lootPos) {
        return AgentGrindTargetRuntime.activeLootTravelDistSq(botPos, lootPos);
    }

    static Point convenientLootTarget(BotEntry entry, Point botPos, Point mobPos) {
        return AgentGrindTargetRuntime.convenientLootTarget(entry, botPos, mobPos);
    }

    private static Point resolvePatrolWanderTarget(BotEntry entry, Point botPos, MapleMap map) {
        return AgentGrindTargetRuntime.resolvePatrolWanderTarget(entry, botPos, map);
    }

    // Main tick
    // -------------------------------------------------------------------------

    private void tick(BotEntry entry, int ownerCharId, int botCharId) {
        AgentTickOrchestrator.runGuardedTick(entry, ownerCharId, botCharId, this::tickCore, this::handleBotTickFailure);
    }

    /** Test-only hook: invokes Agent common tick systems on a caller-owned entry. */
    void runCommonTickSystemsForTest(BotEntry entry, Character bot, Character owner, boolean runAiTick) {
        AgentCommonTickRuntime.runCommonTickSystems(entry, bot, owner, runAiTick, this::tickScriptTasks);
    }

    /**
     * Test-only entry point that runs the full bot tick (including all common systems,
     * AI, navigation, and movement) against a {@link BotEntry} the caller already owns.
     * Bypasses the {@link #bots} registry lookup so harnesses can drive mocked bots
     * without going through {@code registerBotInternal}.
     */
    void runTickForTest(BotEntry entry) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }
        int botCharId = AgentBotRuntimeIdentityRuntime.botId(entry);
        int ownerCharId = AgentBotRuntimeIdentityRuntime.ownerId(entry);
        long startedAt = AgentPerformanceMonitor.enabled() ? System.nanoTime() : 0L;
        try {
            tickCore(entry, ownerCharId, botCharId);
        } catch (Throwable t) {
            log.warn("runTickForTest: tickCore threw for bot {}", AgentBotRuntimeIdentityRuntime.botName(entry), t);
        } finally {
            if (startedAt != 0L) {
                AgentPerformanceMonitor.record("tick-total", System.nanoTime() - startedAt);
            }
        }
    }

    private void tickCore(BotEntry entry, int ownerCharId, int botCharId) {
        AgentTickCoreService.tickCore(entry, ownerCharId, botCharId, tickCoreHooks());
    }

    private AgentTickCoreService.Hooks tickCoreHooks() {
        return new AgentTickCoreService.Hooks(
                System::currentTimeMillis,
                AgentTickPreflightRuntime::runPreflight,
                this::resolveTickOwner,
                this::handleOwnerOfflineOrDead,
                (ownerlessEntry, ownerlessBot, ownerlessRunAiTick) -> AgentOwnerlessTickService.tickOwnerless(
                        ownerlessEntry,
                        ownerlessBot,
                        ownerlessRunAiTick,
                        this::groundAfterMapChange,
                        this::tickStandaloneMoveTarget,
                        () -> AgentIdlePhysicsRuntime.tickIdleEntry(ownerlessEntry, ownerlessBot)),
                this::handleDeadTick,
                (liveEntry, liveBot, liveOwner) -> AgentLiveTickContextRuntime.prepareLiveTickContext(
                        liveEntry,
                        liveBot,
                        liveOwner,
                        this::resolveFollowAnchor,
                        this::captureTargetSnapshot),
                AgentPerformanceMonitor::enabled,
                (gateEntry, gateBot, gateOwner, gateFollowAnchor, liveContext, gateRunAiTick, perf) ->
                        AgentLiveTickGateService.tickLiveGates(
                                new AgentLiveTickGateService.Context(
                                        gateEntry,
                                        gateBot,
                                        gateOwner,
                                        gateFollowAnchor,
                                        liveContext.targetPosition(),
                                        gateRunAiTick),
                                liveTickGateHooks(perf)),
                (modeEntry, modeBot, modeFollowAnchor, liveContext, modeRunAiTick, nowMs, perf) ->
                        AgentLiveModeTickService.tickLiveModes(
                                new AgentLiveModeTickService.Context(
                                        modeEntry,
                                        modeBot,
                                        liveContext.agentPosition(),
                                        liveContext.targetPosition(),
                                        liveContext.targetSnapshot().followTargetPos(),
                                        modeFollowAnchor,
                                        modeRunAiTick,
                                        nowMs),
                                liveModeTickHooks(perf)));
    }
    private AgentLiveModeTickService.Hooks liveModeTickHooks(boolean perf) {
        return new AgentLiveModeTickService.Hooks(
                (shopEntry, shopBot, shopRunAiTick) -> {
                    AgentShopVisitTickService.Result shopVisitResult = AgentShopVisitTickService.tickShopVisitIfPending(
                            shopEntry,
                            shopBot,
                            shopRunAiTick,
                            new AgentShopVisitTickService.Hooks(
                                    (visitEntry, visitBot) -> {
                                        if (!perf) {
                                            return AgentShopService.tickShopVisit(visitEntry, visitBot);
                                        }
                                        long tShop = System.nanoTime();
                                        boolean consumed = AgentShopService.tickShopVisit(visitEntry, visitBot);
                                        AgentPerformanceMonitor.record("tick-shop-visit", System.nanoTime() - tShop);
                                        return consumed;
                                    },
                                    this::stepMovementCore));
                    return new AgentLiveModeTickService.PhaseResult(
                            shopVisitResult.consumedTick(),
                            shopVisitResult.targetPos());
                },
                (attackEntry, attackBot, attackBotPos, attackTargetPos, attackFollowTargetPos, attackFollowAnchor, attackRunAiTick) -> {
                    AgentFollowOpportunityTickService.Result followOpportunity =
                            AgentFollowOpportunityTickService.tickFollowOpportunity(
                                    attackEntry,
                                    attackBot,
                                    attackBotPos,
                                    attackTargetPos,
                                    attackFollowTargetPos,
                                    attackFollowAnchor,
                                    attackRunAiTick,
                                    new AgentFollowOpportunityTickService.Hooks(
                                            (localEntry, localBot, localBotPos, localTargetPos, localFollowTargetPos) -> {
                                                LocalOpportunityAttackResult result;
                                                if (!perf) {
                                                    result = tryLocalOpportunityAttack(
                                                            localEntry, localBot, localBotPos, localTargetPos,
                                                            localFollowTargetPos, true, true);
                                                } else {
                                                    long tOpp = System.nanoTime();
                                                    result = tryLocalOpportunityAttack(
                                                            localEntry, localBot, localBotPos, localTargetPos,
                                                            localFollowTargetPos, true, true);
                                                    AgentPerformanceMonitor.record("opportunity-attack", System.nanoTime() - tOpp);
                                                }
                                                return new AgentFollowOpportunityTickService.Result(
                                                        result.consumedTick(), result.targetPos());
                                            },
                                            BotMovementManager.cfg.FOLLOW_DIST));
                    return new AgentLiveModeTickService.PhaseResult(
                            followOpportunity.consumedTick(),
                            followOpportunity.targetPos());
                },
                AgentFollowIdleMovementRuntime::tryFollowIdleMovementFastPath,
                (scriptEntry, scriptBot, scriptBotPos, scriptTargetPos, scriptRunAiTick) -> {
                    AgentScriptedMoveCombatTickService.Result scriptedMoveCombat =
                            AgentScriptedMoveCombatTickService.tickScriptedMoveCombat(
                                    scriptEntry,
                                    scriptBot,
                                    scriptBotPos,
                                    scriptTargetPos,
                                    scriptRunAiTick,
                                    new AgentScriptedMoveCombatTickService.Hooks(
                                            AgentLocalAttackMoveWindowRuntime::clearActionMoveWindowIfSettled,
                                            (attackEntry, attackBot, attackBotPos, attackTargetPos) -> {
                                                LocalOpportunityAttackResult result;
                                                if (!perf) {
                                                    result = tryLocalOpportunityAttack(
                                                            attackEntry, attackBot, attackBotPos, attackTargetPos,
                                                            attackTargetPos, true, true);
                                                } else {
                                                    long tOppS = System.nanoTime();
                                                    result = tryLocalOpportunityAttack(
                                                            attackEntry, attackBot, attackBotPos, attackTargetPos,
                                                            attackTargetPos, true, true);
                                                    AgentPerformanceMonitor.record("opportunity-attack", System.nanoTime() - tOppS);
                                                }
                                                return new AgentScriptedMoveCombatTickService.Result(
                                                        result.consumedTick(), result.targetPos());
                                            },
                                            (moveEntry, moveTargetPos, moveRunAiTick) -> timedMovementCoreStep(
                                                    moveEntry,
                                                    moveTargetPos,
                                                    moveRunAiTick,
                                                    perf)));
                    return new AgentLiveModeTickService.PhaseResult(
                            scriptedMoveCombat.consumedTick(),
                            scriptedMoveCombat.targetPos());
                },
                (farmEntry, farmBot, farmBotPos, farmRunAiTick) -> AgentAnchoredFarmModeTickService.tickIfAnchoredFarm(
                        farmEntry,
                        farmBot,
                        farmBotPos,
                        farmRunAiTick,
                        new AgentAnchoredFarmModeTickService.Hooks((anchoredEntry, anchoredBot, anchoredBotPos, anchoredRunAiTick) -> {
                            if (!perf) {
                                tickAnchoredFarm(anchoredEntry, anchoredBot, anchoredBotPos, anchoredRunAiTick);
                            } else {
                                long tFarm = System.nanoTime();
                                try { tickAnchoredFarm(anchoredEntry, anchoredBot, anchoredBotPos, anchoredRunAiTick); }
                                finally { AgentPerformanceMonitor.record("tick-anchored-farm", System.nanoTime() - tFarm); }
                            }
                        })),
                (grindEntry, grindBot, grindBotPos, grindTargetPos, grindRunAiTick) -> {
                    AgentGrindModeDispatchService.Result grindDispatch = AgentGrindModeDispatchService.tickIfGrinding(
                            grindEntry,
                            grindBot,
                            grindBotPos,
                            grindTargetPos,
                            grindRunAiTick,
                            new AgentGrindModeDispatchService.Hooks((dispatchEntry, dispatchBot, dispatchBotPos, dispatchTargetPos, dispatchRunAiTick) -> {
                                LocalOpportunityAttackResult grindResult;
                                if (!perf) {
                                    grindResult = tickGrindMode(dispatchEntry, dispatchBot, dispatchBotPos, dispatchTargetPos, dispatchRunAiTick);
                                } else {
                                    long tGrindDispatch = System.nanoTime();
                                    try {
                                        grindResult = tickGrindMode(dispatchEntry, dispatchBot, dispatchBotPos, dispatchTargetPos, dispatchRunAiTick);
                                    } finally {
                                        AgentPerformanceMonitor.record("tick-grind-dispatch", System.nanoTime() - tGrindDispatch);
                                    }
                                }
                                return new AgentGrindModeDispatchService.Result(grindResult.consumedTick(), grindResult.targetPos());
                            }));
                    return new AgentLiveModeTickService.PhaseResult(
                            grindDispatch.consumedTick(),
                            grindDispatch.targetPos());
                },
                (moveEntry, moveTargetPos, moveRunAiTick) -> AgentFinalMovementTailService.stepFinalMovement(
                        moveEntry,
                        moveTargetPos,
                        moveRunAiTick,
                        new AgentFinalMovementTailService.Hooks((tailEntry, tailTargetPos, tailRunAiTick) ->
                                timedMovementCoreStep(tailEntry, tailTargetPos, tailRunAiTick, perf))));
    }

    private AgentLiveTickGateService.Hooks liveTickGateHooks(boolean perf) {
        return new AgentLiveTickGateService.Hooks(
                (entry, bot, owner, runAiTick) ->
                        AgentCommonTickRuntime.runCommonTickSystems(entry, bot, owner, runAiTick, this::tickScriptTasks),
                (tradeEntry, tradeBot) -> AgentTradeWindowTickService.tickIfTradeWindowOpen(tradeEntry, tradeBot, (physicsEntry, physicsBot) -> {
                    if (!perf) {
                        AgentIdlePhysicsRuntime.tickPhysicsOnly(physicsEntry, physicsBot);
                    } else {
                        long tTrade = System.nanoTime();
                        try { AgentIdlePhysicsRuntime.tickPhysicsOnly(physicsEntry, physicsBot); }
                        finally { AgentPerformanceMonitor.record("tick-trade-physics", System.nanoTime() - tTrade); }
                    }
                }),
                (idleEntry, idleBot) -> AgentIdleModeTickService.tickIdleMode(
                        idleEntry,
                        idleBot,
                        new AgentIdleModeTickService.Hooks((physicsEntry, physicsBot) -> {
                            if (!perf) {
                                return AgentIdlePhysicsRuntime.tickIdleEntry(physicsEntry, physicsBot);
                            }
                            long tIdle = System.nanoTime();
                            boolean consumed = AgentIdlePhysicsRuntime.tickIdleEntry(physicsEntry, physicsBot);
                            AgentPerformanceMonitor.record("tick-idle", System.nanoTime() - tIdle);
                            return consumed;
                        })),
                (recoveryEntry, recoveryBot, recoveryFollowAnchor, recoveryTargetPos) -> AgentRecoveryTickService.tickRecovery(
                        recoveryEntry,
                        recoveryBot,
                        recoveryFollowAnchor,
                        recoveryTargetPos,
                        new AgentRecoveryTickService.Hooks(
                                AgentFollowMapSyncRuntime::syncFollowMap,
                                (entry, bot, anchor) -> AgentRecoveryTeleportRuntime.recoverGrindPartyTeleportDistance(
                                        entry,
                                        bot,
                                        anchor,
                                        BotMovementManager.cfg.TELEPORT_DIST,
                                        BotMovementManager.cfg.OOB_TELEPORT_DIST,
                                        cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER),
                                (entry, bot, targetPos) -> AgentRecoveryTeleportRuntime.recoverTeleportDistance(
                                        entry,
                                        bot,
                                        targetPos,
                                        BotMovementManager.cfg.TELEPORT_DIST,
                                        BotMovementManager.cfg.OOB_TELEPORT_DIST))),
                (mapEntry, mapBot) -> AgentTrackedMapChangeTickService.tickTrackedMapChange(
                        mapEntry,
                        mapBot,
                        new AgentTrackedMapChangeTickService.Hooks((trackedEntry, trackedBot) -> {
                            if (!perf) {
                                return AgentMapTransitionRuntime.handleTrackedMapChange(
                                        trackedEntry, trackedBot, this::issueGrind, this::issueFollowOwner);
                            }
                            long tMapChange = System.nanoTime();
                            boolean changed = false;
                            try {
                                changed = AgentMapTransitionRuntime.handleTrackedMapChange(
                                        trackedEntry, trackedBot, this::issueGrind, this::issueFollowOwner);
                            } finally {
                                if (changed) {
                                    AgentPerformanceMonitor.record("tick-map-change", System.nanoTime() - tMapChange);
                                }
                            }
                            return changed;
                        })));
    }

    private void timedMovementCoreStep(BotEntry entry, Point targetPos, boolean runAiTick, boolean perf) {
        if (!perf) {
            stepMovementCore(entry, targetPos, runAiTick);
            return;
        }
        long tStep = System.nanoTime();
        try {
            stepMovementCore(entry, targetPos, runAiTick);
        } finally {
            AgentPerformanceMonitor.record("step-movement-core", System.nanoTime() - tStep);
        }
    }

    /**
     * Grind-mode decision pipeline. Returns a consumed result (caller returns immediately, any
     * required movement already issued) or a fall-through result carrying the resolved targetPos
     * for the shared stepMovementCore tail. Single source of truth shared by the perf and non-perf
     * dispatch arms in the bot tick.
     */
    private LocalOpportunityAttackResult tickGrindMode(BotEntry entry, Character bot, Point botPos,
            Point targetPos, boolean runAiTick) {
        AgentGrindModeTickService.Result result = AgentGrindModeTickService.tickGrindMode(
                entry,
                bot,
                botPos,
                targetPos,
                runAiTick,
                new AgentGrindModeTickService.Hooks(
                        grindTargetSearchHooks(),
                        grindNoTargetFallbackHooks(),
                        grindTargetCommitmentHooks(),
                        grindRangedEngagementHooks(),
                        grindNavigationTailHooks(),
                        AgentCombatConfig.cfg.GRIND_SEEK_RANGE,
                        BotManager.cfg.LOOT_RADIUS));
        return new LocalOpportunityAttackResult(result.consumedTick(), result.targetPos());
    }

    private static AgentGrindTargetSearchService.SearchHooks grindTargetSearchHooks() {
        return new AgentGrindTargetSearchService.SearchHooks(
                (entry, bot) -> AgentBotCombatTargetRuntime.findPatrolTarget(entry, bot, AgentCombatConfig.cfg),
                (entry, bot) -> AgentBotCombatTargetRuntime.findGrindTarget(entry, bot, AgentCombatConfig.cfg),
                AgentCombatConfig.cfg.GRIND_RETARGET_INTERVAL_MS);
    }

    private AgentGrindNoTargetFallbackService.Hooks grindNoTargetFallbackHooks() {
        return new AgentGrindNoTargetFallbackService.Hooks(
                BotMovementManager::tickSwimming,
                BotMovementManager::tickAirborne,
                AgentGrindTargetRuntime::resolvePatrolWanderTarget,
                AgentGrindTargetRuntime::resolveNoGrindTargetPosition,
                this::stepMovementCore);
    }

    private static AgentGrindTargetCommitmentService.Hooks grindTargetCommitmentHooks() {
        return new AgentGrindTargetCommitmentService.Hooks(
                AgentGrindCombatRuntime::selectPriorityRangedAttackTarget,
                AgentAttackExecutionProvider::findCloserThreatMob);
    }

    private AgentGrindRangedEngagementService.Hooks grindRangedEngagementHooks() {
        return new AgentGrindRangedEngagementService.Hooks(
                AgentAttackExecutionProvider::getEquippedWeaponType,
                AgentAttackExecutionProvider::shouldDegenerateRangedAttack,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                AgentGrindNavigationRuntime::selectCrossRegionRetreatTarget,
                AgentCombatRangePolicy::isTargetInAttackRange,
                AgentGrindCombatRuntime::resolveAoeReposition,
                AgentCombatRangePolicy::canUseAttackPlanNow,
                AgentBotCombatAttackRuntime::attackMonster,
                AgentCombatAmmoCounter::isRangedAmmoWeapon,
                AgentCombatRangePolicy::isTargetJumpable,
                BotPhysicsEngine::calculateMaxJumpHeight,
                BotMovementManager::initiateJump,
                BotPhysicsEngine::idleOnGround,
                BotMovementManager::broadcastMovement);
    }

    private static AgentGrindNavigationTailService.Hooks grindNavigationTailHooks() {
        return new AgentGrindNavigationTailService.Hooks(
                AgentGrindNavigationRuntime::selectGrindNavigationTarget,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                AgentGrindTargetRuntime::convenientLootTarget);
    }

    private void handleBotTickFailure(BotEntry entry, int ownerCharId, int botCharId, Throwable t) {
        AgentTickFailureRuntime.handleFailure(entry, ownerCharId, botCharId, t, log, this::issueStop);
    }

    static Monster selectPriorityRangedAttackTarget(BotEntry entry,
                                                   Character bot,
                                                   Point botPos,
                                                   Monster preferredTarget) {
        return AgentGrindCombatRuntime.selectPriorityRangedAttackTarget(
                entry,
                bot,
                botPos,
                preferredTarget);
    }

    private void tickAnchoredFarm(BotEntry entry, Character bot, Point botPos, boolean runAiTick) {
        AgentAnchoredFarmRuntime.tickAnchoredFarm(
                entry, bot, botPos, runAiTick, cfg.ENABLE_UNSTUCK, BotMovementManager.cfg.STOP_DIST);
    }

    private LocalOpportunityAttackResult tryLocalOpportunityAttack(BotEntry entry,
                                                                  Character bot,
                                                                  Point botPos,
                                                                  Point movementTargetPos,
                                                                  Point moveWindowReferencePos,
                                                                  boolean allowCombatMovement,
                                                                  boolean allowJumpTowardTarget) {
        AgentLocalOpportunityAttackService.Result result =
                AgentLocalOpportunityAttackRuntime.tryLocalOpportunityAttack(
                        entry,
                        bot,
                        botPos,
                        movementTargetPos,
                        moveWindowReferencePos,
                        allowCombatMovement,
                        allowJumpTowardTarget);
        return new LocalOpportunityAttackResult(result.consumedTick(), result.targetPos());
    }

    private Character resolveTickOwner(BotEntry entry, int ownerCharId) {
        return AgentLeaderSessionService.resolveTickLeader(entry, ownerCharId, id -> Server.getInstance()
                .getWorld(AgentBotRuntimeIdentityRuntime.bot(entry).getWorld())
                .getPlayerStorage()
                .getCharacterById(id));
    }

    /**
     * If the owner has been offline or dead for >= 5 minutes, scroll/warp the
     * bot to the nearest town and idle there. Prevents pot drain, death-loops
     * with no anchor, and silent grinding while the owner is unable to leech.
     *
     * Returns true when a town warp was performed this tick (caller should
     * short-circuit; map-change reset runs on the next tick).
     */
    private boolean handleOwnerOfflineOrDead(BotEntry entry, Character bot, Character owner, long nowMs, int ownerCharId) {
        return AgentLeaderSafetyService.handleInactiveLeaderTick(
                entry,
                owner,
                nowMs,
                new AgentLeaderSafetyService.InactiveLeaderTickHooks(
                        activeEntry -> AgentLeaderSafetyService.handleActiveLeaderReturn(
                                activeEntry,
                                () -> AgentBotMoveTargetStateRuntime.clearMoveTarget(activeEntry),
                                () -> townClusterAnchors.remove(ownerCharId),
                                () -> AgentBotManagerStatusRuntime.announceOwnerReturnedFromOffline(activeEntry)),
                        this::shouldTownWarpForOwnerInactive,
                        (inactiveEntry, town) -> enterOwnerInactiveSafeMode(inactiveEntry, bot, ownerCharId, town),
                        cfg.OWNER_INACTIVE_TOWN_RETURN_MS));
    }

    private boolean shouldTownWarpForOwnerInactive(BotEntry entry) {
        MapleMap currentMap = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(currentMap);
    }

    public boolean shouldOfferTownForAwayCommand(BotEntry entry) {
        return shouldTownWarpForOwnerInactive(entry);
    }

    public boolean isFirstBotEntry(BotEntry entry) {
        return AgentRuntimeRegistry.isFirstEntryForLeader(entry);
    }

    public void issueOwnerAwaySafeModeForOwner(int ownerCharId, boolean town) {
        AgentLeaderSafetyService.issueInactiveSafeModeForLeader(
                getBotEntries(ownerCharId),
                town,
                AgentBotRuntimeIdentityRuntime::botHasMap,
                this::shouldTownWarpForOwnerInactive,
                (entry, shouldTown) -> enterOwnerInactiveSafeMode(
                        entry, AgentBotRuntimeIdentityRuntime.bot(entry), ownerCharId, shouldTown));
    }

    private boolean enterOwnerInactiveSafeMode(BotEntry entry, Character bot, int ownerCharId, boolean town) {
        return AgentLeaderSafetyService.enterInactiveSafeMode(
                () -> prepareOwnerInactiveIdle(entry, ownerCharId),
                town,
                () -> scrollBotToTown(entry, bot, ownerCharId),
                () -> AgentLeaderSafetyService.idleInactiveAgentInPlace(
                        entry,
                        () -> BotPhysicsEngine.idleOnGround(entry, bot),
                        () -> BotMovementManager.broadcastMovement(entry)));
    }

    private void prepareOwnerInactiveIdle(BotEntry entry, int ownerCharId) {
        AgentLeaderSafetyService.prepareInactiveIdle(
                entry,
                () -> clearScriptTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> clearMode(entry));
    }

    private boolean scrollBotToTown(BotEntry entry, Character bot, int ownerCharId) {
        return AgentLeaderSafetyService.scrollInactiveAgentToTown(
                entry,
                new AgentLeaderSafetyService.TownScrollHooks(
                        bot::getMap,
                        () -> AgentLeaderSafetyService.markInactiveTownReturnHandled(entry),
                        () -> BotPhysicsEngine.idleOnGround(entry, bot),
                        () -> tryUseReturnScroll(bot),
                        bot::changeMap,
                        () -> groundAfterMapChange(entry, bot),
                        bot::getPosition,
                        post -> townClusterAnchors.putIfAbsent(ownerCharId, post),
                        (returnMap, anchor) -> resolveTownClusterTarget(entry, ownerCharId, returnMap, anchor),
                        () -> BotMovementManager.resetEntryState(entry),
                        target -> AgentModeService.startMoveTo(entry, target, true)));
    }

    private Point resolveTownClusterTarget(BotEntry entry, int ownerCharId, MapleMap map, Point anchor) {
        List<BotEntry> entries = getBotEntries(ownerCharId);
        AgentFormationService.FormationState formation =
                AgentFormationService.stateForLeader(AgentFormationService.formationsByLeaderId(), ownerCharId, defaultFormationState());
        return AgentLeaderSafetyService.resolveTownClusterTarget(
                entry, map, anchor, entries, formation, PLATFORM_EDGE_INSET_PX, BotPhysicsEngine::findGroundPoint);
    }

    /**
     * Public hook: tell a bot to walk to a fixed point using the same field and
     * pipeline as the player "here" command. No owner reference required, so it
     * works for bots whose owner is offline (e.g. owner-inactive town clustering)
     * as well as any future code-driven "go here" feature.
     *
     * Movement runs through the regular tick when the owner is online, and
     * through tickStandaloneMoveTarget when the owner is offline. Arrival is
     * detected by the existing clearReachedMoveTarget logic.
     */
    public void issueMoveTo(BotEntry entry, Point dest, boolean precise) {
        AgentBotMovementCommandRuntime.moveTo(entry, dest, precise);
    }


    public void issueFarmHere(BotEntry entry, Point dest) {
        AgentBotMovementCommandRuntime.farmHere(entry, dest);
    }


    public void issuePatrol(BotEntry entry, Point ownerPos) {
        AgentBotMovementCommandRuntime.patrol(entry, ownerPos);
    }

    /**
     * Public hook: return the bot to ordinary owner-follow mode. Scripted map
     * automation and chat commands should use this instead of writing mode
     * fields directly.
     */
    public void issueFollowOwner(BotEntry entry) {
        AgentBotMovementCommandRuntime.followOwner(entry);
    }

    /**
     * Public hook: follow a concrete party/member/bot target. Passing the owner
     * (or null) means regular owner-follow.
     */
    public void issueFollow(BotEntry entry, Character target) {
        AgentBotMovementCommandRuntime.follow(entry, target);
    }


    /**
     * Public hook: enter autonomous grind/combat mode using the same setup as
     * player chat commands. This deliberately clears fixed movement and follow
     * targets so scripted "grind" steps do not run in parallel with a stale
     * navigation command.
     */
    public void issueGrind(BotEntry entry) {
        AgentBotMovementCommandRuntime.grind(entry);
    }


    /** Public hook: stop all scripted movement/combat mode and idle in place. */
    public void issueStop(BotEntry entry) {
        AgentBotMovementCommandRuntime.stop(entry);
    }


    /**
     * Public hook for map scripts: drop up to {@code quantity} from the first
     * stack of {@code itemId}. Use {@code quantity <= 0} to drop the whole stack.
     */
    public boolean issueDropItem(BotEntry entry, InventoryType type, int itemId, short quantity) {
        return AgentScriptItemActionService.dropItem(entry, type, itemId, quantity);
    }

    public void clearScriptTasks(BotEntry entry) {
        AgentScriptTaskQueueService.clearTasks(entry);   // signal background batches (Maker craft/disassembly) to self-interrupt
    }

    public void queueTask(BotEntry entry, AgentTask task) {
        AgentScriptTaskQueueService.queueTask(entry, task);
    }

    public void queueMoveTo(BotEntry entry, Point point, boolean precise) {
        AgentScriptTaskQueueService.queueMoveTo(entry, point, precise);
    }

    public void queueMoveTo(BotEntry entry, Point point, boolean precise, AgentTask.MoveCombatMode moveCombatMode) {
        AgentScriptTaskQueueService.queueMoveTo(entry, point, precise, moveCombatMode);
    }

    public void queueMoveThenDropItem(BotEntry entry, Point point, boolean precise, InventoryType type, int itemId, short quantity) {
        AgentScriptTaskQueueService.queueMoveThenDropItem(entry, point, precise, type, itemId, quantity);
    }

    public void queueFollowThenDropItem(BotEntry entry, Character target, int nearPx, InventoryType type, int itemId, short quantity) {
        AgentScriptTaskQueueService.queueFollowThenDropItem(entry, target, nearPx, type, itemId, quantity);
    }

    public boolean hasQueuedTasks(BotEntry entry) {
        return AgentScriptTaskQueueService.hasQueuedTasks(entry);
    }

    public boolean isCheapScriptMoveTarget(BotEntry entry,
                                           Point targetPos,
                                           int maxPathCost,
                                           int fallbackRangeX,
                                           int fallbackRangeY) {
        return AgentScriptMoveTargetService.isCheapMoveTarget(
                entry,
                targetPos,
                maxPathCost,
                fallbackRangeX,
                fallbackRangeY,
                cfg.LOOT_RADIUS);
    }

    private static void clearMode(BotEntry entry) {
        AgentModeService.clearMode(entry);
    }

    private void tickScriptTasks(BotEntry entry) {
        AgentScriptTaskTickService.tick(entry, this::startScriptTask, this::isScriptTaskComplete);
    }

    private void startScriptTask(BotEntry entry, AgentTask task) {
        AgentScriptTaskExecutionService.start(entry, task);
    }

    private boolean isScriptTaskComplete(BotEntry entry, AgentTask task) {
        return AgentScriptTaskExecutionService.isComplete(entry, task, BotMovementManager.cfg.STOP_DIST);
    }

    /**
     * Apply Return Scroll - Nearest Town (item 2030000) via StatEffect.applyTo.
     * The standard scroll effect handles random-portal warp inside applyTo;
     * we only need to remove the consumable afterwards (mirrors ScrollHandler).
     * Returns false when no 2030000 is in the bot's USE inventory or applyTo failed.
     */
    private boolean tryUseReturnScroll(Character bot) {
        return AgentReturnScrollService.tryUseNearestTownReturnScroll(bot);
    }

    /**
     * Owner-offline tick path for Agent move-target state — drives the bot to its
     * point using the same stepMovementCore as the regular pipeline. The
     * regular tick handles moveTarget when owner is online; this is the
     * minimal version for owner-null sessions (currently the offline-town
     * cluster walk). Arrival is auto-detected by clearReachedMoveTarget
     * inside stepMovementCore.
     */
    private void tickStandaloneMoveTarget(BotEntry entry, Character bot, boolean runAiTick) {
        AgentStandaloneMoveTargetRuntime.tickStandaloneMoveTarget(
                entry, bot, runAiTick, cfg.ENABLE_UNSTUCK, BotMovementManager.cfg.STOP_DIST);
    }

    private boolean groundAfterMapChange(BotEntry entry, Character bot) {
        return AgentMapTransitionRuntime.groundAfterMapChange(entry, bot);
    }

    private boolean handleDeadTick(BotEntry entry, Character bot, Character owner) {
        return AgentDeathTickService.handleDeadTick(
                entry,
                bot,
                () -> AgentBotDeathStateRuntime.shouldEnterDeadState(entry, bot.getHp()),
                (deadEntry, deadBot) -> AgentBotCombatDeathRuntime.enterDeadState(deadEntry, deadBot, false, AgentCombatConfig.cfg),
                () -> AgentRespawnRuntime.respawnNearLeader(entry, bot, owner),
                System.currentTimeMillis());
    }

    boolean stepMovementOnly(BotEntry entry, long tickAtMs) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return false;
        }

        boolean runAiTick = AgentTickOrchestrator.prepareTick(
                entry, BotMovementManager.cfg.TICK_MS, cfg.AI_TICK_MS, tickAtMs);

        AgentTargetSnapshot targetSnapshot = captureTargetSnapshot(entry);
        Point ownerPos = targetSnapshot.rawOwnerPos();
        AgentTickStateMaintenanceService.updateObservedLeaderMotion(entry, ownerPos);
        AgentBotOwnerMotionStateRuntime.rememberOwnerPosition(entry, ownerPos);
        stepMovementOnly(entry, targetSnapshot.primaryTargetPos(), ownerPos, runAiTick);
        return runAiTick;
    }

    void stepMovementOnly(BotEntry entry,
                          Point targetPos,
                          Point ownerPos,
                          boolean runAiTick) {
        AgentMovementOnlyRuntime.stepMovementOnly(
                entry,
                targetPos,
                runAiTick,
                AgentBotTickStateRuntime.lastTickAtMs(entry),
                this::resolveFollowAnchor,
                new AgentMovementOnlyRuntime.MovementOnlyConfig(
                        BotMovementManager.cfg.TELEPORT_DIST,
                        BotMovementManager.cfg.OOB_TELEPORT_DIST,
                        cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER,
                        BotMovementManager.cfg.FOLLOW_DIST,
                        BotMovementManager.cfg.STOP_DIST,
                        cfg.ENABLE_UNSTUCK));
    }

    static boolean tryFollowIdleMovementFastPath(BotEntry entry, Character bot, Point targetPos, long nowMs) {
        return AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath(entry, bot, targetPos, nowMs);
    }

    private void stepMovementCore(BotEntry entry,
                                  Point targetPos,
                                  boolean runAiTick) {
        AgentMovementTickRuntime.stepMovementCore(entry, targetPos, runAiTick, cfg.ENABLE_UNSTUCK, BotMovementManager.cfg.STOP_DIST);
    }

    private static void tickStuckDetection(BotEntry entry) {
        AgentStuckDetectionRuntime.tickStuckDetection(entry, cfg.ENABLE_UNSTUCK);
    }


    public void reloginBot(int charId, int ownerCharId, int world, int channel) {
        AgentReloginRuntime.reloginAgent(charId, ownerCharId, world, channel, this::registerSpawnedBot, log);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    void botSay(Character bot, String text) {
        AgentBotManagerReplyRuntime.sayMapNow(bot, text);
    }

    static synchronized String sanitizeChat(String text) {
        return AgentChatTextSanitizer.sanitize(text);
    }

    void botSay(Character bot, AgentReplyChannel channel, String text) {
        AgentBotManagerReplyRuntime.sayNow(bot, channel, text);
    }

    /** Bot-to-bot visible say — routes MAP→map broadcast, PARTY→party, WHISPER→party fallback. */
    void botSay(BotEntry entry, String text) {
        botSay(AgentBotRuntimeIdentityRuntime.bot(entry), AgentBotReplyChannelStateRuntime.replyChannel(entry), text);
    }

    /**
     * Broadcasts via party chat so the owner sees the message even when they're
     * on a different map. Falls back to map chat if the bot has no party.
     */
    public void botSayParty(Character bot, String text) {
        AgentBotManagerReplyRuntime.sayPartyNow(bot, text);
    }

    /**
     * Whisper-driven command to a specific owned bot. Bypasses the global name-
     * prefix routing in handleChat because the whisper target already identifies
     * the bot uniquely. No-op if target isn't a bot owned by the speaker.
     */
    public void handleWhisperToBot(Character owner, Character target, String message) {
        AgentWhisperCommandService.handleWhisperToAgent(owner, target, message);
    }

    private static void handleAgentChat(BotEntry entry, String message) {
        AgentChatRuntime.handleChat(message, new AgentBotChatOrchestratorContext(entry));
    }

    // ===== Owned-bot accessors used by the androidequip.cpp BotEquipHandler =====
    /** Number of bots currently spawned (active) under this owner. */
    public int spawnedBotCount(int ownerCharId) {
        return AgentRuntimeRegistry.activeAgentCountForLeader(ownerCharId);
    }

    /** The Character objects of every spawned bot owned by the given player (empty if none). */
    public List<Character> getOwnedBotCharacters(int ownerCharId) {
        return AgentRuntimeRegistry.activeAgentCharactersForLeader(ownerCharId);
    }

}

