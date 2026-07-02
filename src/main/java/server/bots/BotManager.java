package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.combat.AgentAoeRepositionService;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentGrindTargetSearchService;
import server.agents.capabilities.combat.AgentGrindTargetCommitmentService;
import server.agents.capabilities.combat.AgentGrindModeTickService;
import server.agents.capabilities.combat.AgentGrindNavigationTargetSelector;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackService;
import server.agents.capabilities.combat.AgentRangedPriorityTargetSelector;
import server.agents.capabilities.combat.AgentGrindRangedEngagementService;
import server.agents.capabilities.combat.AgentGrindNavigationTailService;
import server.agents.capabilities.quest.AgentPartyQuestSyncService;

import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.dialogue.AgentChatIngressService;
import server.agents.capabilities.dialogue.AgentTargetedChatRouteService;
import server.agents.capabilities.dialogue.AgentUntargetedChatRouteService;
import server.agents.capabilities.dialogue.AgentWhisperCommandService;

import server.agents.runtime.AgentActionLockPhysicsService;
import server.agents.runtime.AgentAnchoredFarmModeTickService;
import server.agents.runtime.AgentAnchoredFarmTickService;
import server.agents.runtime.AgentCommonTickService;
import server.agents.runtime.AgentDeathTickService;
import server.agents.runtime.AgentDismissCommandService;
import server.agents.runtime.AgentDismissRuntime;
import server.agents.runtime.AgentFinalMovementTailService;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentFollowAnchorService;
import server.agents.runtime.AgentFormationService;
import server.agents.runtime.AgentFollowIdleMovementService;
import server.agents.runtime.AgentFollowTargetCandidateService;
import server.agents.runtime.AgentFollowTargetCommandService;
import server.agents.runtime.AgentFollowTargetResolutionService;
import server.agents.runtime.AgentFollowTargetPositionService;
import server.agents.runtime.AgentFollowMapSyncService;
import server.agents.runtime.AgentFollowOpportunityTickService;
import server.agents.runtime.AgentFormationCommandService;
import server.agents.runtime.AgentGrindModeDispatchService;
import server.agents.runtime.AgentHeartbeatService;
import server.agents.runtime.AgentIdleModeTickService;
import server.agents.runtime.AgentGrindNoTargetFallbackService;
import server.agents.runtime.AgentGrindTargetPositionService;
import server.agents.runtime.AgentIdlePhysicsService;
import server.agents.runtime.AgentLeaderSessionService;
import server.agents.runtime.AgentLeaderSafetyService;
import server.agents.runtime.AgentLiveTickContextService;
import server.agents.runtime.AgentLiveModeTickService;
import server.agents.runtime.AgentLiveTickGateService;
import server.agents.runtime.AgentLocalAttackMoveWindowService;
import server.agents.runtime.AgentMapEnvironmentService;
import server.agents.runtime.AgentMapTransitionService;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentMonsterControlService;
import server.agents.runtime.AgentMovementPhaseService;
import server.agents.runtime.AgentMovementOnlyTickService;
import server.agents.runtime.AgentMovementOnlyMapChangeService;
import server.agents.runtime.AgentMovementTickService;
import server.agents.runtime.AgentOwnerlessTickService;
import server.agents.runtime.AgentOfflineLoadRuntime;
import server.agents.runtime.AgentPartyLifecycleService;
import server.agents.runtime.AgentPositionService;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRecruitRuntime;
import server.agents.runtime.AgentRegistrationRuntime;
import server.agents.runtime.AgentReloginRuntime;
import server.agents.runtime.AgentRecoveryTickService;
import server.agents.runtime.AgentRecoveryTeleportService;
import server.agents.runtime.AgentReturnScrollService;
import server.agents.runtime.AgentRecruitCommandService;
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
import server.agents.runtime.AgentStandaloneMoveTargetTickService;
import server.agents.runtime.AgentStuckDetectionService;
import server.agents.runtime.AgentTickFailureRuntime;
import server.agents.runtime.AgentTickCoreService;
import server.agents.runtime.AgentTickOrchestrator;
import server.agents.runtime.AgentTickPreflightService;
import server.agents.runtime.AgentTickStateMaintenanceService;
import server.agents.runtime.AgentTradeWindowTickService;
import server.agents.runtime.AgentTrackedMapChangeTickService;
import server.agents.runtime.AgentTransferCommandService;
import server.agents.runtime.AgentTransferRuntime;

import server.agents.capabilities.looting.AgentGrindLootTargetService;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.social.AgentScrollReactionNotificationService;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.supplies.AgentGroupSupplyResponderSelector;
import server.agents.capabilities.supplies.AgentPotionCheckRequestService;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.trade.AgentOwnerItemNotificationService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentPendingOfferChatRouteService;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.plans.AgentScriptMoveTargetService;


import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotCommandParser;
import server.agents.integration.AgentBotCombatActionLockRuntime;
import server.agents.integration.AgentBotCombatAttackRuntime;
import server.agents.integration.AgentBotCombatBuffRuntime;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotCombatHealRuntime;
import server.agents.integration.AgentBotCombatPlanRuntime;
import server.agents.integration.AgentBotCombatSkillCacheRuntime;
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
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotPotionStateRuntime;
import server.agents.integration.AgentBotPqRuntime;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.integration.AgentBotShopStateRuntime;
import server.agents.integration.AgentBotTickCadenceStateRuntime;
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
    private Character resolveFollowTarget(Character owner, String targetToken) {
        return AgentFollowTargetResolutionService.resolveFollowTarget(
                owner,
                targetToken,
                new AgentFollowTargetResolutionService.Hooks(
                        this::followTargetCandidates,
                        Character::yellowMessage));
    }

    private List<Character> followTargetCandidates(Character owner) {
        return AgentFollowTargetCandidateService.candidates(
                owner,
                new AgentFollowTargetCandidateService.Hooks(this::getBotEntries));
    }

    private boolean applyFollowTargetCommand(Character owner, List<BotEntry> entries, String targetToken) {
        return AgentFollowTargetCommandService.applyFollowTargetCommand(
                owner,
                entries,
                targetToken,
                new AgentFollowTargetCommandService.Hooks(
                        this::resolveFollowTarget,
                        target -> randomReply(List.of(
                                "ok",
                                "k",
                                "sure",
                                "omw",
                                "got it",
                                "following " + target.getName(),
                                "ok, following " + target.getName()
                        )),
                        AgentBotManagerReplyRuntime::queueReply,
                        () -> randMs(250, 750),
                        AgentBotManagerSchedulerRuntime::afterDelay,
                        entry -> BotEquipManager.autoEquip(
                                AgentBotRuntimeIdentityRuntime.bot(entry),
                                AgentBotRuntimeIdentityRuntime.owner(entry),
                                AgentBotOfferStateRuntime.pendingLootOfferItem(entry)),
                        entry -> AgentPotionService.checkPotShareOnModeStart(
                                entry,
                                AgentBotRuntimeIdentityRuntime.bot(entry)),
                        this::issueFollow));
    }

    public static String randomReply(List<String> list) {
        return AgentDialogueSelector.randomReply(list);
    }

    /** Uniform random delay in [lo, hi) ms — use wherever a fixed delay would feel robotic. */
    public static long randMs(int lo, int hi) {
        return AgentRandom.randMs(lo, hi);
    }

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
        return AgentDismissRuntime.dismissAgentByName(ownerCharId, botName, this::issueStop);
    }

    /** Recruit an ownerless bot by name into the owner's group. Returns an error string on failure, null on success. */
    public String recruitBot(int ownerCharId, Character owner, String botName) {
        return AgentRecruitRuntime.recruitAgent(ownerCharId, owner, botName, this::registerBot);
    }

    /** Transfer a bot from this owner to another player in the same map. Returns an error string on failure, null on success. */
    public String giveBot(int ownerCharId, Character owner, String botName, String targetName) {
        return AgentTransferRuntime.transferAgent(
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
                (leader, message) -> AgentRecruitCommandService.handleRecruitCommand(
                        leader,
                        message,
                        new AgentRecruitCommandService.Hooks(
                                this::recruitBot,
                                Character::yellowMessage)),
                (leader, message) -> AgentTransferCommandService.handleTransferCommand(
                        leader,
                        message,
                        new AgentTransferCommandService.Hooks(
                                this::giveBot,
                                Character::yellowMessage)),
                (leader, message) -> AgentFormationCommandService.handleFormationCommand(
                        leader,
                        message,
                        formationCommandHooks()),
                bots::get,
                (leader, message) -> AgentDismissCommandService.handleDismissCommand(
                        leader,
                        message,
                        new AgentDismissCommandService.Hooks(
                                this::dismissBot,
                                Character::yellowMessage)),
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
                this::applyFollowTargetCommand,
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
                this::applyFollowTargetCommand,
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

    private AgentFormationCommandService.Hooks formationCommandHooks() {
        return new AgentFormationCommandService.Hooks(
                bots::get,
                (leaderCharId, defaultFormation) -> AgentFormationService.stateForLeader(
                        AgentFormationService.formationsByLeaderId(),
                        leaderCharId,
                        defaultFormation),
                AgentFormationService.formationsByLeaderId()::put,
                AgentFormationService::applyOffsets,
                AgentBotManagerReplyRuntime::queueReply,
                Character::yellowMessage,
                defaultFormationState(),
                cfg.FOLLOW_STAGGER,
                BotMovementManager.cfg.FOLLOW_Y_CAP);
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
        return AgentAoeRepositionService.resolveAoeReposition(entry, bot, target, attackPlan, botPos);
    }

    static Point selectGrindNavigationTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        return AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(
                entry, botPos, combatTargetPos, grindNavigationHooks());
    }

    private static Point selectGrindNavigationTarget(BotEntry entry,
                                                     Point botPos,
                                                     Point combatTargetPos,
                                                     boolean crossRegionRetreatChecked) {
        return AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(
                entry, botPos, combatTargetPos, crossRegionRetreatChecked, grindNavigationHooks());
    }

    static Point selectCrossRegionRetreatTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        return AgentGrindNavigationTargetSelector.selectCrossRegionRetreatTarget(
                entry, botPos, combatTargetPos, grindNavigationHooks());
    }

    static boolean shouldUseLocalCombatRetreatTarget(BotEntry entry,
                                                     Point botPos,
                                                     Point combatTargetPos,
                                                     Point retreatPos) {
        return AgentGrindNavigationTargetSelector.shouldUseLocalCombatRetreatTarget(
                entry, botPos, combatTargetPos, retreatPos, grindNavigationHooks());
    }

    private static AgentGrindNavigationTargetSelector.NavigationHooks grindNavigationHooks() {
        return new AgentGrindNavigationTargetSelector.NavigationHooks(
                BotNavigationManager::resolveCurrentRegionId,
                BotNavigationManager::resolveTargetRegionId,
                BotNavigationManager::findPath,
                BotMovementManager.cfg.GRIND_EDGE_MARGIN,
                BotMovementManager.cfg.JUMP_Y_THRESH);
    }

    static Point resolveNoGrindTargetPosition(BotEntry entry, Point botPos, MapleMap map) {
        return AgentGrindTargetPositionService.resolveNoGrindTargetPosition(
                entry,
                botPos,
                map,
                cfg.LOOT_RADIUS,
                BotMovementManager.cfg.STOP_DIST,
                cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                BotNavigationManager::resolveCurrentRegionId);
    }

    static Point resolveNoGrindTargetPosition(BotEntry entry, Point botPos) {
        return AgentGrindTargetPositionService.resolveNoGrindTargetPosition(
                entry,
                botPos,
                cfg.LOOT_RADIUS,
                BotMovementManager.cfg.STOP_DIST,
                cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                BotNavigationManager::resolveCurrentRegionId);
    }

    private static Point activeGrindLootPosition(BotEntry entry, Point botPos) {
        return AgentGrindTargetPositionService.activeGrindLootPosition(
                entry,
                botPos,
                cfg.LOOT_RADIUS,
                cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    static void suppressGrindLootRetry(BotEntry entry, MapItem loot) {
        AgentGrindTargetPositionService.suppressGrindLootRetry(
                entry,
                loot,
                cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    static double activeLootTravelDistSq(Point botPos, Point lootPos) {
        return AgentGrindTargetPositionService.activeLootTravelDistSq(botPos, lootPos, cfg.LOOT_RADIUS);
    }

    static Point convenientLootTarget(BotEntry entry, Point botPos, Point mobPos) {
        return AgentGrindTargetPositionService.convenientLootTarget(
                entry,
                botPos,
                mobPos,
                cfg.LOOT_RADIUS,
                cfg.GRIND_LOOT_CONVENIENCE_RATIO,
                cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    private static Point resolvePatrolWanderTarget(BotEntry entry, Point botPos, MapleMap map) {
        return AgentGrindTargetPositionService.resolvePatrolWanderTarget(
                entry,
                botPos,
                map,
                cfg.LOOT_RADIUS,
                BotMovementManager.cfg.STOP_DIST,
                cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                BotNavigationManager::resolveCurrentRegionId);
    }

    // Main tick
    // -------------------------------------------------------------------------

    private void tick(BotEntry entry, int ownerCharId, int botCharId) {
        AgentTickOrchestrator.runGuardedTick(entry, ownerCharId, botCharId, this::tickCore, this::handleBotTickFailure);
    }

    /** Test-only hook: invokes {@link #runCommonTickSystems} on a caller-owned entry. */
    void runCommonTickSystemsForTest(BotEntry entry, Character bot, Character owner, boolean runAiTick) {
        runCommonTickSystems(entry, bot, owner, runAiTick);
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
                (entry, botCharId, nowMs) -> AgentTickPreflightService.runPreflight(
                        entry,
                        botCharId,
                        nowMs,
                        tickPreflightHooks()),
                this::resolveTickOwner,
                this::handleOwnerOfflineOrDead,
                (ownerlessEntry, ownerlessBot, ownerlessRunAiTick) -> AgentOwnerlessTickService.tickOwnerless(
                        ownerlessEntry,
                        ownerlessBot,
                        ownerlessRunAiTick,
                        this::groundAfterMapChange,
                        this::tickStandaloneMoveTarget,
                        () -> tickIdleEntry(ownerlessEntry, ownerlessBot)),
                this::handleDeadTick,
                (liveEntry, liveBot, liveOwner) -> AgentLiveTickContextService.prepareLiveTickContext(
                        liveEntry,
                        liveBot,
                        liveOwner,
                        liveTickContextHooks()),
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
    private AgentTickPreflightService.Hooks tickPreflightHooks() {
        return new AgentTickPreflightService.Hooks(
                AgentBotManagerStatusRuntime::airshowActive,
                AgentBotTickCadenceStateRuntime::consumeSkipDelay,
                this::removeBotByCharId,
                (entry, agent, nowMs, heartbeatIntervalMs) -> AgentHeartbeatService.tickHeartbeat(
                        entry,
                        agent,
                        nowMs,
                        heartbeatIntervalMs,
                        heartbeatAgent -> heartbeatAgent.getClient().updateLastPacket(),
                        BotMovementManager::broadcastMovement),
                AgentOfferService::expirePendingOffer,
                AgentTickOrchestrator::prepareTick,
                BotMovementManager.cfg.TICK_MS,
                cfg.AI_TICK_MS,
                600_000L);
    }

    private AgentLiveTickContextService.Hooks liveTickContextHooks() {
        return new AgentLiveTickContextService.Hooks(
                BotMovementManager::refreshMovementProfile,
                this::resolveFollowAnchor,
                this::captureTargetSnapshot,
                AgentTickStateMaintenanceService::updateObservedLeaderMotion,
                AgentBotOwnerMotionStateRuntime::rememberOwnerPosition,
                AgentTickStateMaintenanceService::clearFarmAnchorOnMapChange,
                AgentTickStateMaintenanceService::clearPatrolOnMapChange,
                BotManager::clearFollowActionMoveWindowIfSettled);
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
                BotManager::tryFollowIdleMovementFastPath,
                (scriptEntry, scriptBot, scriptBotPos, scriptTargetPos, scriptRunAiTick) -> {
                    AgentScriptedMoveCombatTickService.Result scriptedMoveCombat =
                            AgentScriptedMoveCombatTickService.tickScriptedMoveCombat(
                                    scriptEntry,
                                    scriptBot,
                                    scriptBotPos,
                                    scriptTargetPos,
                                    scriptRunAiTick,
                                    new AgentScriptedMoveCombatTickService.Hooks(
                                            BotManager::clearActionMoveWindowIfSettled,
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
                this::runCommonTickSystems,
                (tradeEntry, tradeBot) -> AgentTradeWindowTickService.tickIfTradeWindowOpen(tradeEntry, tradeBot, (physicsEntry, physicsBot) -> {
                    if (!perf) {
                        tickTradePhysicsOnly(physicsEntry, physicsBot);
                    } else {
                        long tTrade = System.nanoTime();
                        try { tickTradePhysicsOnly(physicsEntry, physicsBot); }
                        finally { AgentPerformanceMonitor.record("tick-trade-physics", System.nanoTime() - tTrade); }
                    }
                }),
                (idleEntry, idleBot) -> AgentIdleModeTickService.tickIdleMode(
                        idleEntry,
                        idleBot,
                        new AgentIdleModeTickService.Hooks((physicsEntry, physicsBot) -> {
                            if (!perf) {
                                return tickIdleEntry(physicsEntry, physicsBot);
                            }
                            long tIdle = System.nanoTime();
                            boolean consumed = tickIdleEntry(physicsEntry, physicsBot);
                            AgentPerformanceMonitor.record("tick-idle", System.nanoTime() - tIdle);
                            return consumed;
                        })),
                (recoveryEntry, recoveryBot, recoveryFollowAnchor, recoveryTargetPos) -> AgentRecoveryTickService.tickRecovery(
                        recoveryEntry,
                        recoveryBot,
                        recoveryFollowAnchor,
                        recoveryTargetPos,
                        new AgentRecoveryTickService.Hooks(
                                this::syncFollowMap,
                                this::recoverGrindPartyTeleportDistance,
                                this::recoverTeleportDistance)),
                (mapEntry, mapBot) -> AgentTrackedMapChangeTickService.tickTrackedMapChange(
                        mapEntry,
                        mapBot,
                        new AgentTrackedMapChangeTickService.Hooks((trackedEntry, trackedBot) -> {
                            if (!perf) {
                                return handleTrackedMapChange(trackedEntry, trackedBot);
                            }
                            long tMapChange = System.nanoTime();
                            boolean changed = false;
                            try {
                                changed = handleTrackedMapChange(trackedEntry, trackedBot);
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
                BotManager::resolvePatrolWanderTarget,
                BotManager::resolveNoGrindTargetPosition,
                this::stepMovementCore);
    }

    private static AgentGrindTargetCommitmentService.Hooks grindTargetCommitmentHooks() {
        return new AgentGrindTargetCommitmentService.Hooks(
                BotManager::selectPriorityRangedAttackTarget,
                AgentAttackExecutionProvider::findCloserThreatMob);
    }

    private AgentGrindRangedEngagementService.Hooks grindRangedEngagementHooks() {
        return new AgentGrindRangedEngagementService.Hooks(
                AgentAttackExecutionProvider::getEquippedWeaponType,
                AgentAttackExecutionProvider::shouldDegenerateRangedAttack,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                BotManager::selectCrossRegionRetreatTarget,
                AgentCombatRangePolicy::isTargetInAttackRange,
                BotManager::resolveAoeReposition,
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
                BotManager::selectGrindNavigationTarget,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                BotManager::convenientLootTarget);
    }

    private void handleBotTickFailure(BotEntry entry, int ownerCharId, int botCharId, Throwable t) {
        AgentTickFailureRuntime.handleFailure(entry, ownerCharId, botCharId, t, log, this::issueStop);
    }

    static Monster selectPriorityRangedAttackTarget(BotEntry entry,
                                                   Character bot,
                                                   Point botPos,
                                                   Monster preferredTarget) {
        return AgentRangedPriorityTargetSelector.selectPriorityRangedAttackTarget(
                entry,
                bot,
                botPos,
                preferredTarget);
    }

    private void tickAnchoredFarm(BotEntry entry, Character bot, Point botPos, boolean runAiTick) {
        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                bot,
                botPos,
                runAiTick,
                anchoredFarmHooks());
    }

    private AgentAnchoredFarmTickService.AnchoredFarmHooks anchoredFarmHooks() {
        return new AgentAnchoredFarmTickService.AnchoredFarmHooks(
                (entry, bot, botPos, movementTargetPos, moveWindowReferencePos,
                 allowCombatMovement, allowJumpTowardTarget) -> {
                    LocalOpportunityAttackResult result = tryLocalOpportunityAttack(
                            entry,
                            bot,
                            botPos,
                            movementTargetPos,
                            moveWindowReferencePos,
                            allowCombatMovement,
                            allowJumpTowardTarget);
                    return new AgentAnchoredFarmTickService.LocalOpportunityResult(
                            result.consumedTick(), result.targetPos());
                },
                this::tickIdleEntry,
                (entry, bot) -> {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                },
                this::stepMovementCore);
    }

    private LocalOpportunityAttackResult tryLocalOpportunityAttack(BotEntry entry,
                                                                  Character bot,
                                                                  Point botPos,
                                                                  Point movementTargetPos,
                                                                  Point moveWindowReferencePos,
                                                                  boolean allowCombatMovement,
                                                                  boolean allowJumpTowardTarget) {
        AgentLocalOpportunityAttackService.Result result =
                AgentLocalOpportunityAttackService.tryLocalOpportunityAttack(
                        entry,
                        bot,
                        botPos,
                        movementTargetPos,
                        moveWindowReferencePos,
                        allowCombatMovement,
                        allowJumpTowardTarget,
                        localOpportunityAttackHooks());
        return new LocalOpportunityAttackResult(result.consumedTick(), result.targetPos());
    }

    private static AgentLocalOpportunityAttackService.Hooks localOpportunityAttackHooks() {
        return new AgentLocalOpportunityAttackService.Hooks(
                BotManager::selectGrindNavigationTarget,
                BotPhysicsEngine::calculateMaxJumpHeight,
                BotMovementManager::initiateJump,
                BotManager::setLocalAttackMoveWindow);
    }

    private static void setLocalAttackMoveWindow(BotEntry entry, Point botPos, Point referencePos) {
        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
                entry,
                botPos,
                referencePos,
                BotMovementManager.cfg.FOLLOW_DIST,
                BotMovementManager.cfg.STOP_DIST,
                BotMovementManager.cfg.FOLLOW_Y_CAP);
    }

    private static void clearFollowActionMoveWindowIfSettled(BotEntry entry,
                                                             Point botPos,
                                                             AgentTargetSnapshot targetSnapshot) {
        AgentLocalAttackMoveWindowService.clearFollowActionMoveWindowIfSettled(
                entry,
                botPos,
                targetSnapshot,
                BotMovementManager.cfg.FOLLOW_DIST,
                BotMovementManager.cfg.STOP_DIST,
                BotMovementManager.cfg.FOLLOW_Y_CAP);
    }

    private static void clearActionMoveWindowIfSettled(BotEntry entry,
                                                       Point botPos,
                                                       Point targetPos) {
        AgentLocalAttackMoveWindowService.clearActionMoveWindowIfSettled(
                entry,
                botPos,
                targetPos,
                BotMovementManager.cfg.FOLLOW_DIST,
                BotMovementManager.cfg.STOP_DIST,
                BotMovementManager.cfg.FOLLOW_Y_CAP);
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
        AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                entry,
                bot,
                runAiTick,
                new AgentStandaloneMoveTargetTickService.Hooks(
                        this::groundAfterMapChange,
                        BotMovementManager::refreshMovementProfile,
                        this::stepMovementCore));
    }

    private boolean groundAfterMapChange(BotEntry entry, Character bot) {
        return AgentMapTransitionService.groundAfterMapChange(entry, bot, mapTransitionHooks());
    }

    private boolean handleTrackedMapChange(BotEntry entry, Character bot) {
        return AgentMapTransitionService.handleTrackedMapChange(
                entry,
                bot,
                new AgentMapTransitionService.MapChangeHooks(
                        mapTransitionHooks(),
                        AgentPartyQuestHooks::requiresGrind,
                        this::issueGrind,
                        AgentPartyQuestHooks::requiresFollow,
                        this::issueFollowOwner,
                        AgentBotPqRuntime::resetKpqStage5Claimed,
                        AgentShopService::onMapChange,
                        AgentBotManagerStatusRuntime::checkManagerStatus));
    }

    private AgentMapTransitionService.GroundingHooks mapTransitionHooks() {
        return new AgentMapTransitionService.GroundingHooks(
                BotMovementManager::buildFhIndex,
                BotPhysicsEngine::findGroundPoint,
                BotPhysicsEngine::teleportTo,
                BotMovementManager::resetEntryStateAfterTeleport,
                AgentNavigationGraphService::warmGraphAsync,
                BotMovementManager::broadcastMovement);
    }

    private boolean handleDeadTick(BotEntry entry, Character bot, Character owner) {
        return AgentDeathTickService.handleDeadTick(
                entry,
                bot,
                () -> AgentBotDeathStateRuntime.shouldEnterDeadState(entry, bot.getHp()),
                (deadEntry, deadBot) -> AgentBotCombatDeathRuntime.enterDeadState(deadEntry, deadBot, false, AgentCombatConfig.cfg),
                () -> respawnBot(entry, bot, owner),
                System.currentTimeMillis());
    }

    private boolean runCommonTickSystems(BotEntry entry, Character bot, Character owner, boolean runAiTick) {
        return AgentCommonTickService.runCommonTickSystems(
                entry,
                bot,
                owner,
                runAiTick,
                new AgentCommonTickService.CommonTickHooks(
                        (tickEntry, tickBot) -> AgentBotCombatDamageRuntime.tickMobDamage(
                                tickEntry, tickBot, AgentCombatConfig.cfg, BotMovementManager::tickDown),
                        (tickEntry, tickBot) -> AgentBotDeathStateRuntime.isDead(tickEntry),
                        (tickEntry, tickBot) -> AgentBotCombatDeathRuntime.enterDeadState(
                                tickEntry, tickBot, false, AgentCombatConfig.cfg),
                        AgentMonsterControlService::releaseControlledMonsters,
                        BotInventoryManager::tickPassiveLoot,
                        AgentPotionService::tickPotionCheck,
                        AgentPotionService::tickPassiveRecovery,
                        AgentBuildService::checkLevelUp,
                        (tickEntry, tickBot, tickOwner) -> AgentBotManagerStatusRuntime.tickAfkCheck(tickEntry, tickOwner),
                        BotInventoryManager::tickTrade,
                        BotInventoryManager::tickManualTrade,
                        AgentPartyQuestHooks::tick,
                        this::tickScriptTasks,
                        AgentPartyQuestHooks::isNpcLocked,
                        AgentBotCombatActionLockRuntime::tickActionLock,
                        AgentBotCombatSkillCacheRuntime::rebuildSkillCacheIfNeeded,
                        (tickEntry, tickBot) -> AgentBotCombatHealRuntime.tickSupportHealing(
                                tickEntry, tickBot, AgentCombatConfig.cfg),
                        (tickEntry, tickBot) -> AgentBotCombatBuffRuntime.tickBuffs(
                                tickEntry, tickBot, AgentCombatConfig.cfg),
                        AgentBuffService::tick,
                        this::tickActionLocked));
    }

    /**
     * Physics-only tick used while a trade window is open. Mirrors {@link #tickIdleEntry}'s
     * physics body but skips the active-mode early-return so gravity / swim / stance stay
     * consistent even if the bot was following or grinding when the trade started. Issues
     * no movement input (no follow, grind, teleport, shop visit, or attack).
     */
    private void tickTradePhysicsOnly(BotEntry entry, Character bot) {
        AgentIdlePhysicsService.tickPhysicsOnly(entry, bot, idlePhysicsHooks());
    }

    private boolean tickIdleEntry(BotEntry entry, Character bot) {
        return AgentIdlePhysicsService.tickIdleEntry(entry, bot, idlePhysicsHooks());
    }

    private AgentIdlePhysicsService.PhysicsHooks idlePhysicsHooks() {
        return new AgentIdlePhysicsService.PhysicsHooks(
                AgentMapEnvironmentService::isSwimMap,
                entry -> BotMovementManager.tickSwimming(entry, null),
                entry -> BotMovementManager.tickAirborne(entry, null),
                BotPhysicsEngine::resolveIdleGroundStance,
                BotPhysicsEngine::resolveStance,
                BotPhysicsEngine::idleOnGround,
                BotMovementManager::broadcastMovement);
    }

    private boolean syncFollowMap(BotEntry entry, Character bot, Character followAnchor) {
        return AgentFollowMapSyncService.syncFollowMap(entry, bot, followAnchor, followMapSyncHooks());
    }

    private AgentFollowMapSyncService.FollowMapSyncHooks followMapSyncHooks() {
        return new AgentFollowMapSyncService.FollowMapSyncHooks(
                BotPhysicsEngine::findGroundPoint,
                BotPhysicsEngine::idleOnGround,
                Character::changeMap,
                BotMovementManager::resetEntryState);
    }

    private boolean recoverTeleportDistance(BotEntry entry, Character bot, Point targetPos) {
        return AgentRecoveryTeleportService.recoverTeleportDistance(
                entry,
                bot,
                targetPos,
                BotMovementManager.cfg.TELEPORT_DIST,
                BotMovementManager.cfg.OOB_TELEPORT_DIST,
                recoveryTeleportHooks());
    }

    private boolean recoverGrindPartyTeleportDistance(BotEntry entry, Character bot, Character partyAnchor) {
        return AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry,
                bot,
                partyAnchor,
                BotMovementManager.cfg.TELEPORT_DIST,
                BotMovementManager.cfg.OOB_TELEPORT_DIST,
                cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER,
                recoveryTeleportHooks());
    }

    private AgentRecoveryTeleportService.RecoveryHooks recoveryTeleportHooks() {
        return new AgentRecoveryTeleportService.RecoveryHooks(
                BotPhysicsEngine::findGroundPoint,
                BotPhysicsEngine::teleportTo,
                BotMovementManager::resetEntryStateAfterTeleport,
                BotMovementManager::broadcastMovement);
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
        AgentMovementOnlyTickService.stepMovementOnly(
                entry,
                targetPos,
                runAiTick,
                AgentBotTickStateRuntime.lastTickAtMs(entry),
                movementOnlyHooks());
    }

    private AgentMovementOnlyTickService.MovementOnlyHooks movementOnlyHooks() {
        return new AgentMovementOnlyTickService.MovementOnlyHooks(
                this::tickIdleEntry,
                (entry, bot) -> AgentBotShopStateRuntime.shopVisitPending(entry),
                this::syncFollowMap,
                this::resolveFollowAnchor,
                this::recoverGrindPartyTeleportDistance,
                this::recoverTeleportDistance,
                this::handleMovementOnlyMapChange,
                AgentShopService::tickShopVisit,
                AgentBotShopStateRuntime::activeShopTargetPosition,
                AgentBotShopStateRuntime::shopApproachDelayMs,
                BotManager::tryFollowIdleMovementFastPath,
                this::stepMovementCore);
    }

    private boolean handleMovementOnlyMapChange(BotEntry entry, Character bot) {
        return AgentMovementOnlyMapChangeService.handleMapChange(
                entry,
                bot,
                new AgentMovementOnlyMapChangeService.Hooks(
                        BotMovementManager::buildFhIndex,
                        BotPhysicsEngine::findGroundPoint,
                        BotPhysicsEngine::teleportTo,
                        BotMovementManager::resetEntryStateAfterTeleport,
                        BotMovementManager::broadcastMovement,
                        AgentShopService::onMapChange,
                        AgentBotManagerStatusRuntime::checkManagerStatus));
    }

    static boolean tryFollowIdleMovementFastPath(BotEntry entry, Character bot, Point targetPos, long nowMs) {
        return AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry,
                bot,
                targetPos,
                nowMs,
                BotMovementManager.cfg.FOLLOW_DIST,
                BotMovementManager.cfg.STOP_DIST);
    }

    private void stepMovementCore(BotEntry entry,
                                  Point targetPos,
                                  boolean runAiTick) {
        AgentMovementTickService.stepMovementCore(entry, targetPos, runAiTick, movementTickHooks());
    }

    private AgentMovementTickService.MovementTickHooks movementTickHooks() {
        return new AgentMovementTickService.MovementTickHooks(
                (entry, targetPos, runAiTick) -> {
                    BotNavigationManager.NavigationDirective directive =
                            BotNavigationManager.resolveTarget(entry, targetPos, runAiTick);
                    return new AgentMovementTickService.NavigationResult(directive.consumedTick, directive.targetPos);
                },
                AgentFidgetService::tryHandleTick,
                this::tickMovementPhase,
                BotNavigationManager::tryExecuteCommittedEdgeAfterGroundMovement,
                BotManager::tickStuckDetection,
                entry -> AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, BotMovementManager.cfg.STOP_DIST));
    }

    private void tickMovementPhase(BotEntry entry, Point targetPos, boolean runAiTick) {
        AgentMovementPhaseService.tickMovementPhase(entry, targetPos, runAiTick, movementPhaseHooks());
    }

    private AgentMovementPhaseService.MovementPhaseHooks movementPhaseHooks() {
        return new AgentMovementPhaseService.MovementPhaseHooks(
                (entry, target) -> AgentMapEnvironmentService.isSwimMap(entry),
                BotMovementManager::tickClimbing,
                BotMovementManager::tickSwimming,
                BotMovementManager::tickAirborne,
                BotMovementManager::tickGrounded);
    }
    private static void tickStuckDetection(BotEntry entry) {
        AgentStuckDetectionService.tickStuckDetection(entry, stuckDetectionHooks());
    }

    private static AgentStuckDetectionService.StuckDetectionHooks stuckDetectionHooks() {
        return new AgentStuckDetectionService.StuckDetectionHooks(
                BotMovementManager::tickDown,
                BotMovementManager::tickUnstuck,
                BotPhysicsEngine.cfg.TICK_MS,
                cfg.ENABLE_UNSTUCK);
    }

    private boolean tickActionLocked(BotEntry entry) {
        return AgentActionLockPhysicsService.tickActionLocked(
                entry,
                AgentMapEnvironmentService::isSwimMap,
                locked -> BotMovementManager.tickSwimming(locked, null),
                locked -> BotMovementManager.tickAirborne(locked, null),
                locked -> BotMovementManager.tickGrounded(locked, null));
    }


    public void reloginBot(int charId, int ownerCharId, int world, int channel) {
        AgentReloginRuntime.reloginAgent(charId, ownerCharId, world, channel, this::registerSpawnedBot, log);
    }

    private void respawnBot(BotEntry entry, Character bot, Character owner) {
        AgentDeathTickService.respawnNearLeader(
                entry,
                bot,
                owner,
                new AgentDeathTickService.RespawnHooks(
                        (respawnBot, leaderMap, leaderPosition) ->
                                respawnBot.forceChangeMap(leaderMap, leaderMap.findClosestPortal(leaderPosition)),
                        MapleMap::getPointBelow,
                        BotPhysicsEngine::teleportTo,
                        (respawnEntry, ignoredBot) -> BotMovementManager.resetEntryStateAfterTeleport(respawnEntry),
                        (respawnEntry, ignoredBot) -> BotMovementManager.broadcastMovement(respawnEntry),
                        this::botSay));
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

