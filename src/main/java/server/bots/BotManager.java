package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.quest.AgentPartyQuestSyncService;

import server.agents.capabilities.dialogue.AgentWhisperCommandService;

import server.agents.runtime.AgentAnchoredFarmRuntime;
import server.agents.runtime.AgentChatRouteRuntime;
import server.agents.runtime.AgentCommonTickRuntime;
import server.agents.runtime.AgentDeathTickService;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentFollowAnchorService;
import server.agents.runtime.AgentFormationService;
import server.agents.runtime.AgentFormationRuntime;
import server.agents.runtime.AgentFollowIdleMovementRuntime;
import server.agents.runtime.AgentFollowTargetPositionService;
import server.agents.runtime.AgentFormationCommandRuntime;
import server.agents.runtime.AgentGrindCombatRuntime;
import server.agents.runtime.AgentGrindModeRuntime;
import server.agents.runtime.AgentGrindNavigationRuntime;
import server.agents.runtime.AgentGrindTargetRuntime;
import server.agents.runtime.AgentLeaderSessionService;
import server.agents.runtime.AgentLeaderSafetyRuntime;
import server.agents.runtime.AgentLifecycleChatCommandRuntime;
import server.agents.runtime.AgentLiveModeTickRuntime;
import server.agents.runtime.AgentLocalOpportunityAttackRuntime;
import server.agents.runtime.AgentMapEnvironmentService;
import server.agents.runtime.AgentMapTransitionRuntime;
import server.agents.runtime.AgentMovementOnlyRuntime;
import server.agents.runtime.AgentMovementTickRuntime;
import server.agents.runtime.AgentOfflineLoadRuntime;
import server.agents.runtime.AgentPartyLifecycleService;
import server.agents.runtime.AgentPositionService;
import server.agents.runtime.AgentRegistrationRuntime;
import server.agents.runtime.AgentReloginRuntime;
import server.agents.runtime.AgentRespawnRuntime;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.agents.runtime.AgentScriptTaskRuntime;
import server.agents.runtime.AgentTargetSnapshot;
import server.agents.runtime.AgentTargetSnapshotService;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSpawnPositionService;
import server.agents.runtime.AgentSpawnRuntime;
import server.agents.runtime.AgentStandaloneMoveTargetRuntime;
import server.agents.runtime.AgentStuckDetectionRuntime;
import server.agents.runtime.AgentTickFailureRuntime;
import server.agents.runtime.AgentTickCoreRuntime;
import server.agents.runtime.AgentTickOrchestrator;
import server.agents.runtime.AgentTickStateMaintenanceService;

import server.agents.capabilities.looting.AgentGrindLootTargetService;
import server.agents.capabilities.social.AgentScrollReactionNotificationService;
import server.agents.capabilities.supplies.AgentPotionCheckRequestService;
import server.agents.capabilities.trade.AgentOwnerItemNotificationService;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.plans.AgentScriptMoveTargetService;


import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
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
import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
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
import server.agents.commands.AgentReplyChannel;
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
        AgentChatRouteRuntime.handleChat(
                owner,
                message,
                channel,
                bots,
                this::recruitBot,
                this::giveBot,
                this::dismissBot,
                AgentFormationRuntime.defaultFormationState(),
                cfg.FOLLOW_STAGGER,
                BotMovementManager.cfg.FOLLOW_Y_CAP);
    }

    // -------------------------------------------------------------------------
    AgentFormationService.FormationState formationStateFor(BotEntry entry) {
        return AgentFormationRuntime.formationStateFor(entry);
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
        AgentFormationRuntime.setFormationState(owner, type, px, snapRange, entries);
    }

    public AgentTargetSnapshot captureTargetSnapshot(BotEntry entry) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        List<BotEntry> siblingEntries = owner == null ? List.of() : getBotEntries(owner.getId());
        return AgentTargetSnapshotService.capture(
                entry,
                siblingEntries,
                AgentFormationService.formationsByLeaderId(),
                AgentFormationRuntime.defaultFormationState(),
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
        AgentCommonTickRuntime.runCommonTickSystems(
                entry,
                bot,
                owner,
                runAiTick,
                entryToTick -> AgentScriptTaskRuntime.tick(entryToTick, BotMovementManager.cfg.STOP_DIST));
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
        AgentTickCoreRuntime.tickCore(
                entry,
                ownerCharId,
                botCharId,
                this::resolveTickOwner,
                this::handleOwnerOfflineOrDead,
                this::groundAfterMapChange,
                this::tickStandaloneMoveTarget,
                this::handleDeadTick,
                this::resolveFollowAnchor,
                this::captureTargetSnapshot,
                entryToTick -> AgentScriptTaskRuntime.tick(entryToTick, BotMovementManager.cfg.STOP_DIST),
                this::issueGrind,
                this::issueFollowOwner,
                AgentLocalOpportunityAttackRuntime::tryLocalOpportunityAttackForLiveMode,
                this::stepMovementCore,
                this::tickAnchoredFarm,
                (grindEntry, grindAgent, grindAgentPosition, grindTargetPosition, grindRunAiTick) ->
                        AgentGrindModeRuntime.tickGrindMode(
                                grindEntry,
                                grindAgent,
                                grindAgentPosition,
                                grindTargetPosition,
                                grindRunAiTick,
                                this::stepMovementCore,
                                BotManager.cfg.LOOT_RADIUS),
                BotMovementManager.cfg.TELEPORT_DIST,
                BotMovementManager.cfg.OOB_TELEPORT_DIST,
                cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER,
                BotMovementManager.cfg.FOLLOW_DIST);
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
        return AgentLeaderSafetyRuntime.handleInactiveLeaderTick(
                entry,
                bot,
                owner,
                nowMs,
                ownerCharId,
                cfg.OWNER_INACTIVE_TOWN_RETURN_MS);
    }

    private boolean shouldTownWarpForOwnerInactive(BotEntry entry) {
        return AgentLeaderSafetyRuntime.shouldTownWarpForInactiveEntry(entry);
    }

    public boolean shouldOfferTownForAwayCommand(BotEntry entry) {
        return shouldTownWarpForOwnerInactive(entry);
    }

    public boolean isFirstBotEntry(BotEntry entry) {
        return AgentRuntimeRegistry.isFirstEntryForLeader(entry);
    }

    public void issueOwnerAwaySafeModeForOwner(int ownerCharId, boolean town) {
        AgentLeaderSafetyRuntime.issueInactiveSafeModeForLeader(ownerCharId, town);
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

    /**
     * Apply Return Scroll - Nearest Town (item 2030000) via StatEffect.applyTo.
     * The standard scroll effect handles random-portal warp inside applyTo;
     * we only need to remove the consumable afterwards (mirrors ScrollHandler).
     * Returns false when no 2030000 is in the bot's USE inventory or applyTo failed.
     */
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

