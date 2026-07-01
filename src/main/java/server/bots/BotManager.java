package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.combat.AgentAttackRoute;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentCombatScoringPolicy;
import server.agents.capabilities.combat.AgentProjectileHitbox;

import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.dialogue.AgentDialogueSelector;

import server.agents.runtime.AgentActionLockPhysicsService;
import server.agents.runtime.AgentAutopotRuntimeCleanupService;
import server.agents.runtime.AgentCommandModeService;
import server.agents.runtime.AgentDeathTickService;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentFollowAnchorService;
import server.agents.runtime.AgentFormationService;
import server.agents.runtime.AgentFollowTargetPositionService;
import server.agents.runtime.AgentHeartbeatService;
import server.agents.runtime.AgentIdlePhysicsService;
import server.agents.runtime.AgentLeaderSessionService;
import server.agents.runtime.AgentLeaderSafetyService;
import server.agents.runtime.AgentMapEnvironmentService;
import server.agents.runtime.AgentMapTransitionService;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentPartyLifecycleService;
import server.agents.runtime.AgentPositionService;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentReturnScrollService;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentScriptTaskCompletionService;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.agents.runtime.AgentScriptTaskStartService;
import server.agents.runtime.AgentScriptTaskTickService;
import server.agents.runtime.AgentTargetSnapshot;
import server.agents.runtime.AgentTargetSnapshotService;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSpawnPositionService;
import server.agents.runtime.AgentTickFailurePolicy;
import server.agents.runtime.AgentTickOrchestrator;

import server.agents.capabilities.looting.AgentLootEligibility;
import server.agents.capabilities.looting.AgentLootTargetService;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.social.AgentScrollReactionService;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.plans.AgentScriptMoveTargetService;


import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotAoeRepositionStateRuntime;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotBreakoutStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotCommandParser;
import server.agents.integration.AgentBotCombatActionLockRuntime;
import server.agents.integration.AgentBotCombatAoeRepositionRuntime;
import server.agents.integration.AgentBotCombatAttackRuntime;
import server.agents.integration.AgentBotCombatBuffRuntime;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotCombatHealRuntime;
import server.agents.integration.AgentBotCombatPlanRuntime;
import server.agents.integration.AgentBotCombatSkillCacheStateRuntime;
import server.agents.integration.AgentBotCombatSkillCacheRuntime;
import server.agents.integration.AgentBotCombatTargetRuntime;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotFidgetRuntime;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindSearchStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotGrindWanderStateRuntime;
import server.agents.integration.AgentBotLeaderStateRuntime;
import server.agents.integration.AgentBotMapStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMovementStuckStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
import server.agents.integration.AgentBotPotionStateRuntime;
import server.agents.integration.AgentBotPqRuntime;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.agents.integration.AgentBotRetreatHoldStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.integration.AgentBotShopStateRuntime;
import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.agents.integration.AgentBotTargetedCommandMatch;
import server.agents.integration.AgentBotTransferCommand;
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
import client.BotClient;
import config.YamlConfig;
import client.Character;
import client.Disease;
import client.QuestStatus;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.inventory.ItemConstants;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final Map<Integer, Point> townClusterAnchors = new ConcurrentHashMap<>();
    private record LocalOpportunityAttackResult(boolean consumedTick, Point targetPos) {}

    private static final Pattern DISMISS_PATTERN = Pattern.compile(
            "\\b(dismiss|disown|release)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECRUIT_PATTERN = Pattern.compile(
            "\\b(recruit|adopt|hire|claim)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMATION_PATTERN = Pattern.compile(
            "\\b(?:formation|form)\\b(?:\\s+(stagger|split|random|stack|spread|tight|loose|left|right|snap)(?:\\s+(\\d+|tight|loose|on|off))?)?",
            Pattern.CASE_INSENSITIVE);
    private static final int MIN_PREFIX_TARGET_LENGTH = 2;
    private static final int PLATFORM_EDGE_INSET_PX = 12;
    private Character resolveFollowTarget(Character owner, String targetToken) {
        if (owner == null || targetToken == null || targetToken.isBlank()) {
            if (owner != null) {
                owner.yellowMessage("Can't follow that target.");
            }
            return null;
        }

        List<Character> candidates = new ArrayList<>();
        if (owner.isLoggedinWorld()) {
            candidates.add(owner);
        }
        if (owner.getParty() != null) {
            for (Character member : owner.getPartyMembersOnline()) {
                if (member == null || !member.isLoggedinWorld() || member.getId() == owner.getId()) {
                    continue;
                }
                candidates.add(member);
            }
        }
        for (BotEntry sibling : getBotEntries(owner.getId())) {
            Character siblingBot = AgentBotRuntimeIdentityRuntime.bot(sibling);
            if (siblingBot == null || !siblingBot.isLoggedinWorld()) {
                continue;
            }
            boolean duplicate = false;
            for (Character candidate : candidates) {
                if (candidate.getId() == siblingBot.getId()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                candidates.add(siblingBot);
            }
        }

        for (Character candidate : candidates) {
            if (candidate.getName().equalsIgnoreCase(targetToken)) {
                return candidate;
            }
        }

        if (targetToken.length() < MIN_PREFIX_TARGET_LENGTH) {
            owner.yellowMessage("Follow target must use at least " + MIN_PREFIX_TARGET_LENGTH + " letters.");
            return null;
        }

        List<Character> prefixMatches = new ArrayList<>();
        for (Character candidate : candidates) {
            if (candidate.getName().regionMatches(true, 0, targetToken, 0, targetToken.length())) {
                prefixMatches.add(candidate);
            }
        }
        if (prefixMatches.size() == 1) {
            return prefixMatches.get(0);
        }
        if (prefixMatches.size() > 1) {
            StringBuilder message = new StringBuilder("Ambiguous follow target '")
                    .append(targetToken)
                    .append("': ");
            for (int i = 0; i < prefixMatches.size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(prefixMatches.get(i).getName());
            }
            owner.yellowMessage(message.toString());
            return null;
        }

        owner.yellowMessage("Can't follow '" + targetToken + "'. Target must be a same-party character or one of your active bots.");
        return null;
    }

    private boolean applyFollowTargetCommand(Character owner, List<BotEntry> entries, String targetToken) {
        Character target = resolveFollowTarget(owner, targetToken);
        if (target == null) {
            return true;
        }
        for (BotEntry entry : entries) {
            if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry)
                    || AgentBotRuntimeIdentityRuntime.botIs(entry, target.getId())) {
                continue;
            }
            AgentBotManagerReplyRuntime.queueReply(entry, randomReply(List.of(
                    "ok",
                    "k",
                    "sure",
                    "omw",
                    "got it",
                    "following " + target.getName(),
                    "ok, following " + target.getName()
            )));
            AgentBotManagerSchedulerRuntime.afterDelay(randMs(250, 750), () -> {
                BotEquipManager.autoEquip(
                        AgentBotRuntimeIdentityRuntime.bot(entry),
                        AgentBotRuntimeIdentityRuntime.owner(entry),
                        AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
                AgentPotionService.checkPotShareOnModeStart(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
                issueFollow(entry, target);
            });
        }
        return true;
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
        AgentOwnershipService ownershipService = AgentOwnershipService.getInstance();
        AgentResolvedCharacter resolved = ownershipService.resolveCharacterByName(botName);
        if (resolved == null) {
            return SpawnResult.fail("No character named '" + botName + "' exists.");
        }
        if (resolved.isOnline() && !resolved.isOnlineAsBot()) {
            return SpawnResult.fail("'" + botName + "' is currently being played by a real player.");
        }
        AgentAuthorizationResult auth = ownershipService.ensureCanControl(owner, resolved);
        if (!auth.allowed()) {
            return SpawnResult.fail(auth.failureMessage());
        }
        MapleMap map = owner.getMap();
        Point pos = resolveSpawnPosition(map, owner.getPosition());
        if (resolved.isOnline()) {
            Character botChar = resolved.onlineCharacter();
            Character activeOwner = getActiveOwnerByBotCharId(botChar.getId());
            if (activeOwner != null && activeOwner.getId() != owner.getId()) {
                return SpawnResult.fail("Bot '" + botName + "' is controlled by " + activeOwner.getName() + ".");
            }
            BotEntry entry = activeOwner == null
                    ? registerSpawnedBot(owner.getId(), owner, botChar)
                    : getBotEntry(owner.getId(), botChar.getId());
            if (botChar.getMapId() != map.getId()) {
                botChar.forceChangeMap(map, map.findClosestPortal(pos));
            }
            placeSpawnedOnlineBot(entry, botChar, map, pos);
            if (entry != null) {
                issueFollowOwner(entry);
            }
            return SpawnResult.ok(botChar, auth.autoRegistered());
        } else {
            try {
                Character botChar = loadOfflineBot(resolved.id(), owner.getClient().getWorld(), owner.getClient().getChannel(), map, pos);
                BotEntry entry = registerSpawnedBot(owner.getId(), owner, botChar);
                issueFollowOwner(entry);
                return SpawnResult.ok(botChar, auth.autoRegistered());
            } catch (SQLException e) {
                log.warn("Failed to load bot character '{}' for owner '{}'", botName, owner.getName(), e);
                return SpawnResult.fail("Failed to load bot character '" + botName + "'.");
            }
        }
    }

    public void joinBotToOwnerParty(Character owner, Character bot) {
        AgentPartyLifecycleService.joinAgentToLeaderParty(owner, bot);
    }

    private BotEntry getBotEntry(int ownerCharId, int botCharId) {
        return AgentRuntimeRegistry.findByCharacterId(bots, ownerCharId, botCharId);
    }

    public Character loadOfflineBot(int charId, int world, int channel, MapleMap targetMap, Point desiredPosition) throws SQLException {
        BotClient botClient = new BotClient(world, channel);
        Character botChar = Character.loadCharFromDB(charId, botClient, true);
        botClient.setPlayer(botChar);
        botClient.setAccID(botChar.getAccountID());
        Map<Disease, Pair<Long, MobSkill>> diseases =
                Server.getInstance().getPlayerBuffStorage().getDiseasesFromStorage(charId);
        if (diseases != null) {
            botChar.silentApplyDiseases(diseases);
        }

        MapleMap spawnMap = targetMap != null
                ? targetMap
                : Server.getInstance().getChannel(world, channel).getMapFactory().getMap(botChar.getMapId());
        Point spawnPos = resolveSpawnPosition(spawnMap, desiredPosition != null ? desiredPosition : botChar.getPosition());

        botChar.setMapId(spawnMap.getId());
        botChar.newClient(botClient);
        botChar.recalcLocalStats();

        botChar.resetPlayerRates();
        if (YamlConfig.config.server.USE_ADD_RATES_BY_LEVEL) {
            botChar.setPlayerRates();
        }
        botChar.setWorldRates();
        botChar.updateCouponRates();

        botChar.setPosition(spawnPos);

        var channelServer = Server.getInstance().getChannel(world, channel);
        channelServer.addPlayer(botChar);
        channelServer.getWorldServer().addPlayer(botChar);
        botChar.setEnteredChannelWorld();
        spawnMap.addPlayer(botChar);
        botChar.visitMap(spawnMap);
        botChar.diseaseExpireTask();
        return botChar;
    }

    static void placeSpawnedOnlineBot(BotEntry entry, Character botChar, MapleMap spawnMap, Point spawnPos) {
        if (entry == null) {
            botChar.setPosition(spawnPos);
            botChar.broadcastStance();
            botChar.updatePartyMemberHP();
            return;
        }

        BotPhysicsEngine.teleportTo(entry, botChar, spawnPos);
        BotMovementManager.resetEntryStateAfterTeleport(entry);
        AgentBotDeathStateRuntime.clear(entry);
        int spawnMapId = spawnMap != null ? spawnMap.getId() : botChar.getMapId();
        if (spawnMap != null && spawnMap.getFootholds() != null) {
            AgentBotMapStateRuntime.setMapTracking(entry, spawnMapId, BotMovementManager.buildFhIndex(spawnMap));
            AgentNavigationGraphService.warmGraphAsync(spawnMap, AgentBotMovementStateRuntime.movementProfile(entry));
        } else {
            AgentBotMapStateRuntime.setMapTracking(entry, spawnMapId, null);
        }
        AgentBotTickCadenceStateRuntime.reset(entry);
        AgentBotMovementStateRuntime.clearMoveDirection(entry);
        AgentBotMovementBroadcastStateRuntime.invalidate(entry);
        BotMovementManager.broadcastMovement(entry);
        botChar.updatePartyMemberHP();
    }

    public Point resolveSpawnPosition(MapleMap map, Point desiredPosition) {
        return AgentSpawnPositionService.resolveSpawnPosition(map, desiredPosition);
    }

    private BotEntry registerBotInternal(int ownerCharId, Character owner, Character bot, boolean normalizeSpawnState) {
        List<BotEntry> entries = AgentRuntimeRegistry.mutableEntriesForLeader(ownerCharId);
        // Replace if same bot character is already registered (e.g. relog)
        entries.removeIf(e -> {
            if (AgentBotRuntimeIdentityRuntime.botIs(e, bot.getId())) {
                AgentBotManagerSchedulerRuntime.cancelScheduledTask(e);
                return true;
            }
            return false;
        });
        int botCharId = bot.getId();
        // Capture the BotEntry directly in the tick lambda instead of re-resolving it from the
        // registry every tick (ConcurrentHashMap.get + linear CopyOnWriteArrayList scan). The
        // task is bound to exactly one entry and is cancelled on every removal/replace path, so
        // the captured reference is always the live entry. The holder breaks the task<->entry
        // construction cycle (BotEntry.task is final); the only window where ref[0] is null is
        // before the assignment two lines below, which tickCore already tolerates.
        BotEntry[] ref = new BotEntry[1];
        ScheduledFuture<?> task = TimerManager.getInstance().register(
                () -> tick(ref[0], ownerCharId, botCharId), BotMovementManager.cfg.TICK_MS);
        BotEntry entry = new BotEntry(bot, owner, task);
        ref[0] = entry;
        AgentBotMovementStateRuntime.refreshMovementProfile(entry, bot);
        AgentNavigationGraphService.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        entries.add(entry);
        AgentFormationService.FormationState fs =
                AgentFormationService.stateForLeader(AgentFormationService.formationsByLeaderId(), ownerCharId, defaultFormationState());
        AgentFormationService.applyOffsets(entries, fs);
        if (normalizeSpawnState) {
            normalizeSpawnedBot(entry);
        }
        AgentBotManagerStatusRuntime.scheduleSpawnStatusCheck(entry, bot, randMs(30_000, 31_000));
        return entry;
    }

    private void normalizeSpawnedBot(BotEntry entry) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Point spawnPos = resolveSpawnPosition(bot.getMap(), bot.getPosition());
        if (bot.getHp() <= 0) {
            bot.updateHp(Math.max(1, bot.getCurrentMaxHp()));
        }

        BotPhysicsEngine.teleportTo(entry, bot, spawnPos != null ? spawnPos : bot.getPosition());
        BotMovementManager.resetEntryStateAfterTeleport(entry);
        AgentBotDeathStateRuntime.clear(entry);
        AgentBotMapStateRuntime.setMapTracking(entry, bot.getMapId(), BotMovementManager.buildFhIndex(bot.getMap()));
        AgentBotTickCadenceStateRuntime.reset(entry);
        AgentBotMovementStateRuntime.clearMoveDirection(entry);
        AgentBotMovementBroadcastStateRuntime.invalidate(entry);
        BotMovementManager.broadcastMovement(entry);
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner != null) {
            AgentPartyLifecycleService.joinAgentToLeaderParty(owner, bot);
        }
    }

    public void removeBot(int ownerCharId) {
        AgentLifecycleService.removeLeaderEntries(
                bots, AgentFormationService.formationsByLeaderId(), townClusterAnchors, ownerCharId, this::cancelBotTask);
    }

    /** Cancel and remove a bot by the bot character's own ID (used during shutdown/disconnect). */
    public boolean removeBotByCharId(int botCharId) {
        return AgentLifecycleService.removeAgentByCharacterId(
                bots, AgentFormationService.formationsByLeaderId(), townClusterAnchors, botCharId, this::cancelBotTask);
    }

    /** Release bot-owned runtime state before this character leaves bot control. */
    public boolean cleanupBotRuntimeState(Character bot) {
        if (bot == null) {
            return false;
        }

        boolean removed = removeBotByCharId(bot.getId());
        AgentAutopotRuntimeCleanupService.clearBotOnlyAutopotState(bot);
        return removed;
    }

    private void cancelBotTask(BotEntry entry) {
        if (AgentBotManagerSchedulerRuntime.hasScheduledTask(entry)) {
            AgentBotManagerSchedulerRuntime.cancelScheduledTask(entry);
        }
    }

    /** Disown a bot by name - cancels its AI tick and leaves it idle in the map. */
    public boolean dismissBot(int ownerCharId, String botName) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return false;
        BotEntry entry = getBotEntry(ownerCharId, botName);
        if (entry == null) return false;
        entries.remove(entry);
        AgentBotManagerSchedulerRuntime.cancelScheduledTask(entry);
        issueStop(entry);
        AgentBotManagerSchedulerRuntime.afterDelay(randMs(400, 600), () ->
                AgentBotManagerReplyRuntime.replyNow(entry, randomReply(List.of(
                        "ok", "sure", "alright", "gotcha",
                        "later!", "see ya", "take care", "cya", "peace out"))));
        return true;
    }

    /** Recruit an ownerless bot by name into the owner's group. Returns an error string on failure, null on success. */
    public String recruitBot(int ownerCharId, Character owner, String botName) {
        Character bot = findOwnerlessBot(botName, owner.getWorld());
        if (bot == null) return "No ownerless bot named '" + botName + "' found.";

        AgentAuthorizationResult auth =
                AgentOwnershipService.getInstance().ensureCanControl(
                        owner,
                        new AgentResolvedCharacter(
                                bot.getId(),
                                bot.getName(),
                                bot.getAccountID(),
                                bot));
        if (!auth.allowed()) {
            return auth.failureMessage();
        }

        registerSpawnedBot(ownerCharId, owner, bot);
        return null;
    }

    /** Transfer a bot from this owner to another player in the same map. Returns an error string on failure, null on success. */
    public String giveBot(int ownerCharId, Character owner, String botName, String targetName) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return "You have no bots.";
        BotEntry found = getBotEntry(ownerCharId, botName);
        if (found == null) return "No bot named '" + botName + "' in your group.";

        // Find target player in the same map
        Character target = owner.getMap().getCharacterByName(targetName);
        if (target == null) return "Player '" + targetName + "' not found in this map.";
        if (target.getId() == ownerCharId) return "That's you.";

        AgentAuthorizationResult auth =
                AgentOwnershipService.getInstance().ensureCanControl(
                        target,
                        new AgentResolvedCharacter(
                                AgentBotRuntimeIdentityRuntime.botId(found),
                                AgentBotRuntimeIdentityRuntime.botName(found),
                                AgentBotRuntimeIdentityRuntime.botAccountId(found),
                                AgentBotRuntimeIdentityRuntime.bot(found)));
        if (!auth.allowed()) {
            return auth.failureMessage();
        }

        // Disown from current owner
        Character bot = AgentBotRuntimeIdentityRuntime.bot(found);
        entries.remove(found);
        AgentBotManagerSchedulerRuntime.cancelScheduledTask(found);
        issueStop(found);

        // Register under new owner
        registerBot(target.getId(), target, bot);
        AgentBotManagerSchedulerRuntime.afterDelay(randMs(700, 900), () ->
                botSay(bot, randomReply(List.of("ok!", "sure!", "hey " + target.getName() + "!", "hi " + target.getName() + "!"))));
        return null;
    }

    public Character getActiveOwnerByBotCharId(int botCharId) {
        return AgentRuntimeRegistry.activeLeaderByAgentCharacterId(bots, botCharId);
    }

    public void requestBotPotionCheckSoon(Character bot) {
        if (bot == null || !(bot.getClient() instanceof BotClient)) {
            return;
        }
        Character owner = getActiveOwnerByBotCharId(bot.getId());
        if (owner == null) {
            return;
        }
        BotEntry entry = getBotEntry(owner.getId(), bot.getId());
        if (entry == null) {
            return;
        }
        int soonDelayMs = Math.max(0, cfg.POT_CHECK_RETRY_SOON_MS);
        AgentBotPotionStateRuntime.requestPotionCheckSoon(entry, soonDelayMs);
    }

    /** Finds a bot-client character with the given name that is not currently owned by anyone. */
    private Character findOwnerlessBot(String name, int world) {
        for (var ch : Server.getInstance().getWorld(world).getChannels()) {
            Character c = ch.getPlayerStorage().getCharacterByName(name);
            if (c == null || !(c.getClient() instanceof BotClient)) continue;
            if (getActiveOwnerByBotCharId(c.getId()) == null) return c;
        }
        return null;
    }

    public Character getBot(int ownerCharId) {
        return AgentRuntimeRegistry.firstAgent(bots, ownerCharId);
    }

    BotEntry getFirstBotEntry(int ownerCharId) {
        return AgentRuntimeRegistry.firstEntry(bots, ownerCharId);
    }

    public List<BotEntry> getBotEntries(int ownerCharId) {
        return AgentRuntimeRegistry.entriesForLeader(bots, ownerCharId);
    }

    /** Called when the owner picks up or receives an item; notifies bots that might want it. */
    public void notifyOwnerGainedItem(Character owner, Item item) {
        if (owner == null || item == null) return;
        if (ItemConstants.getInventoryType(item.getItemId()) != InventoryType.EQUIP) return;
        List<BotEntry> entries = getBotEntries(owner.getId());
        if (entries.isEmpty()) return;
        // Run the per-bot upgrade-recommendation scan off the player's pickup thread. The
        // scan is fire-and-forget (its only effect is a possible chat-prompt) so deferring
        // by one timer tick keeps rapid pickups from stalling the player behind K×N DPs.
        AgentBotManagerSchedulerRuntime.afterDelay(0L, () -> {
            for (BotEntry entry : entries) {
                AgentOfferService.notifyOwnerGainedEquip(entry, AgentBotRuntimeIdentityRuntime.bot(entry), item);
            }
        });
    }

    /** Called when a trade recipient receives an item; skips circular own-bot trade scans. */
    public void notifyOwnerGainedTradeItem(Character recipient, Item item, Character source) {
        if (isItemFromOwnedBot(recipient, source)) {
            return;
        }
        notifyOwnerGainedItem(recipient, item);
    }

    private boolean isItemFromOwnedBot(Character owner, Character source) {
        if (owner == null || source == null || !(source.getClient() instanceof BotClient)) {
            return false;
        }
        Character activeOwner = getActiveOwnerByBotCharId(source.getId());
        return activeOwner != null && activeOwner.getId() == owner.getId();
    }

    public void notifyNearbyBotsOfScroll(Character source,
                                         client.inventory.Equip.ScrollResult result,
                                         int scrollItemId,
                                         long delayMs) {
        AgentBotManagerSchedulerRuntime.afterDelay(Math.max(0L, delayMs), () ->
                AgentScrollReactionService.handleScrollEvent(source, result, scrollItemId, bots.values()));
    }

    public BotEntry getBotEntry(int ownerCharId, String botName) {
        return AgentRuntimeRegistry.findByName(bots, ownerCharId, botName);
    }

    public void syncPartyBotsQuestStart(Character source, Quest quest, int npc) {
        if (quest == null) {
            return;
        }

        for (Character bot : getPartyBots(source)) {
            if (bot.getQuest(quest).getStatus() == QuestStatus.Status.STARTED) {
                continue;
            }
            quest.forceStartWithActions(bot, resolveQuestNpc(source, quest, npc));
        }
    }

    public void syncPartyBotsQuestProgress(Character source, int questId, int infoNumber, String progress) {
        if (progress == null) {
            return;
        }

        Quest quest = Quest.getInstance(questId);
        int npc = resolveQuestNpc(source, quest, source.getQuest(quest).getNpc());
        for (Character bot : getPartyBots(source)) {
            ensureQuestStarted(bot, quest, npc);
            bot.setQuestProgress(questId, infoNumber, progress);
        }
    }

    public void syncPartyBotsQuestComplete(Character source, Quest quest, int npc, Integer selection) {
        if (quest == null) {
            return;
        }

        int resolvedNpc = resolveQuestNpc(source, quest, npc);
        for (Character bot : getPartyBots(source)) {
            ensureQuestStarted(bot, quest, resolvedNpc);
            quest.forceCompleteWithActions(bot, resolvedNpc, selection);
        }
    }

    public String manualTradeGreeting() {
        return AgentTradeDialogueService.manualTradeGreeting();
    }

    private List<Character> getPartyBots(Character source) {
        if (source == null || source.getParty() == null || source.getClient() instanceof BotClient) {
            return List.of();
        }

        List<Character> partyBots = new ArrayList<>();
        for (Character member : source.getPartyMembersOnline()) {
            if (member == null || member.getId() == source.getId()) {
                continue;
            }
            if (member.getClient() instanceof BotClient) {
                partyBots.add(member);
            }
        }
        return partyBots;
    }

    private void ensureQuestStarted(Character bot, Quest quest, int npc) {
        if (bot.getQuest(quest).getStatus() == QuestStatus.Status.STARTED) {
            return;
        }

        quest.forceStartWithActions(bot, npc);
    }

    private int resolveQuestNpc(Character source, Quest quest, int fallbackNpc) {
        if (fallbackNpc > 0) {
            return fallbackNpc;
        }

        if (source != null) {
            int sourceNpc = source.getQuest(quest).getNpc();
            if (sourceNpc > 0) {
                return sourceNpc;
            }
        }

        return constants.id.NpcId.MAPLE_ADMINISTRATOR;
    }

    public void handleChat(Character owner, String message, AgentReplyChannel channel) {
        if (handlePendingLootOfferResponse(owner, message)) {
            return;
        }

        // Recruit must work even when owner has no bots yet
        Matcher rm = RECRUIT_PATTERN.matcher(message);
        if (rm.find()) {
            String name = rm.group(2);
            String err = recruitBot(owner.getId(), owner, name);
            if (err == null) {
                owner.yellowMessage("Bot '" + name + "' recruited!");
            } else {
                owner.yellowMessage(err);
            }
            return;
        }

        AgentBotTransferCommand transferCommand = AgentBotCommandParser.matchBotTransferCommand(message);
        if (transferCommand != null) {
            String err = giveBot(owner.getId(), owner, transferCommand.botName(), transferCommand.targetName());
            if (err != null) owner.yellowMessage(err);
            else owner.yellowMessage("Bot '" + transferCommand.botName() + "' transferred to " + transferCommand.targetName() + ".");
            return;
        }

        // Formation command
        Matcher fm = FORMATION_PATTERN.matcher(message);
        if (fm.find()) {
            String typeStr = fm.group(1);
            List<BotEntry> fEntries = bots.get(owner.getId());
            if (typeStr == null) {
                String help = "formations: stagger/split/random/spread/left/right <px>, stack, tight, loose | snap <px/on/off>";
                if (fEntries != null && !fEntries.isEmpty()) AgentBotManagerReplyRuntime.queueReply(fEntries.get(0), help);
                else owner.yellowMessage(help);
                return;
            }
            AgentFormationService.FormationState current =
                    AgentFormationService.stateForLeader(AgentFormationService.formationsByLeaderId(), owner.getId(), defaultFormationState());
            // snap [px|on|off] — changes Y-snap range, preserves type/px
            if (typeStr.equalsIgnoreCase("snap")) {
                String qualifier = fm.group(2);
                int newSnapRange;
                if (qualifier == null) {
                    String status = current.snapRange() > 0 ? "on (" + current.snapRange() + "px)" : "off";
                    if (fEntries != null && !fEntries.isEmpty()) AgentBotManagerReplyRuntime.queueReply(fEntries.get(0), "snap: " + status);
                    else owner.yellowMessage("snap: " + status);
                    return;
                } else if (qualifier.equalsIgnoreCase("off")) {
                    newSnapRange = 0;
                } else if (qualifier.equalsIgnoreCase("on")) {
                    newSnapRange = current.snapRange() > 0 ? current.snapRange() : BotMovementManager.cfg.FOLLOW_Y_CAP;
                } else {
                    newSnapRange = Integer.parseInt(qualifier);
                }
                AgentFormationService.FormationState fs =
                        new AgentFormationService.FormationState(current.type(), current.px(), newSnapRange);
                AgentFormationService.formationsByLeaderId().put(owner.getId(), fs);
                String status = newSnapRange > 0 ? "on (" + newSnapRange + "px)" : "off";
                if (fEntries != null && !fEntries.isEmpty())
                    AgentBotManagerReplyRuntime.queueReply(fEntries.get(0), "snap: " + status);
                return;
            }
            String pxToken = fm.group(2);
            int defaultPx = pxToken == null                      ? cfg.FOLLOW_STAGGER
                          : pxToken.equalsIgnoreCase("tight")    ? 30
                          : pxToken.equalsIgnoreCase("loose")    ? 120
                          : pxToken.equalsIgnoreCase("on")
                            || pxToken.equalsIgnoreCase("off")   ? cfg.FOLLOW_STAGGER
                          : Integer.parseInt(pxToken);
            AgentFormationService.FormationType type;
            int px = defaultPx;
            switch (typeStr.toLowerCase()) {
                case "tight"          -> { type = AgentFormationService.FormationType.STAGGER; px = 30; }
                case "loose"          -> { type = AgentFormationService.FormationType.STAGGER; px = 120; }
                case "stack"          -> { type = AgentFormationService.FormationType.STACK;   px = 0; }
                case "spread"         -> { type = AgentFormationService.FormationType.SPREAD;  px = defaultPx; }
                case "left"           -> { type = AgentFormationService.FormationType.LEFT;    px = defaultPx; }
                case "right"          -> { type = AgentFormationService.FormationType.RIGHT;   px = defaultPx; }
                case "random"         -> { type = AgentFormationService.FormationType.RANDOM;  px = defaultPx; }
                case "split","stagger"-> { type = AgentFormationService.FormationType.STAGGER; px = defaultPx; }
                default               -> { type = AgentFormationService.FormationType.STAGGER; px = defaultPx; }
            }
            AgentFormationService.FormationState fs =
                    new AgentFormationService.FormationState(type, px, current.snapRange());
            AgentFormationService.formationsByLeaderId().put(owner.getId(), fs);
            if (fEntries != null) {
                AgentFormationService.applyOffsets(fEntries, fs);
                if (!fEntries.isEmpty()) {
                    String label = typeStr.toLowerCase() + (px > 0 ? " " + px + "px" : "");
                    AgentBotManagerReplyRuntime.queueReply(fEntries.get(0), "formation: " + label);
                }
            }
            return;
        }

        List<BotEntry> entries = bots.get(owner.getId());
        if (entries == null || entries.isEmpty()) return;

        // Dismiss: disown bot, leaves it idle in map
        Matcher dm = DISMISS_PATTERN.matcher(message);
        if (dm.find()) {
            String name = dm.group(2);
            if (dismissBot(owner.getId(), name)) {
                owner.yellowMessage("Bot '" + name + "' disowned - now idle.");
            } else {
                owner.yellowMessage("No bot named '" + name + "' in your group.");
            }
            return;
        }

        // Name-prefix routing: "Jason pots?" → only Jason responds
        AgentBotTargetedCommandMatch targetedBot = AgentBotCommandParser.resolveTargetedBot(entries, message);
        if (targetedBot.entry() != null) {
            String followTargetToken = AgentChatCommandClassifier.matchFollowTarget(targetedBot.commandText());
            if (followTargetToken != null) {
                applyFollowTargetCommand(owner, List.of(targetedBot.entry()), followTargetToken);
                return;
            }
            AgentBotReplyChannelStateRuntime.setReplyChannel(targetedBot.entry(), channel);
            String cmd = targetedBot.commandText();
            if (AgentLlmConfig.typoSuggesterEnabled) {
                String typo = AgentCommandTypoSuggester.suggest(cmd);
                if (typo != null) {
                    AgentBotManagerReplyRuntime.queueReply(targetedBot.entry(), "did you mean '" + typo + "'?");
                    return;
                }
            }
            handleAgentChat(targetedBot.entry(), cmd);
            boolean matched = AgentChatRuntime.wasLastChatHandled();
            if (matched && targetedBot.entry().getOwner() != null
                    && owner.getId() == targetedBot.entry().getOwner().getId()) {
                AgentBotActivityStateRuntime.recordLastOwnerCommand(
                        targetedBot.entry(), cmd, System.currentTimeMillis());
            }
            // Fall through to LLM only if no command pattern matched.
            if (AgentLlmConfig.enabled && !matched) {
                server.agents.capabilities.dialogue.llm.AgentLlmReplyService.maybeRespond(targetedBot.entry(), owner, cmd);
            }
            return;
        }
        if (targetedBot.feedbackMessage() != null) {
            owner.yellowMessage(targetedBot.feedbackMessage());
            return;
        }

        String followTargetToken = AgentChatCommandClassifier.matchFollowTarget(message);
        if (followTargetToken != null) {
            applyFollowTargetCommand(owner, entries, followTargetToken);
            return;
        }

        // Group supply requests ("need pots", "anyone have hp pots", "need arrows"
        // etc.) elicit a single response from the bot group. Broadcasting these
        // would have every bot run handleNeedPotionCommand independently, each
        // selecting the same best-stocked donor → duplicate offer messages and
        // duplicate trade requests to the owner.
        if (AgentChatCommandClassifier.isGroupSupplyRequest(message)) {
            BotEntry responder = pickGroupSupplyResponder(owner, entries);
            if (responder != null) {
                AgentBotReplyChannelStateRuntime.setReplyChannel(responder, channel);
                handleAgentChat(responder, message);
            }
            return;
        }

        // No name prefix — typo-suggest once via the first bot, otherwise broadcast.
        if (AgentLlmConfig.typoSuggesterEnabled) {
            String typo = AgentCommandTypoSuggester.suggest(message);
            if (typo != null) {
                BotEntry first = entries.get(0);
                AgentBotReplyChannelStateRuntime.setReplyChannel(first, channel);
                AgentBotManagerReplyRuntime.queueReply(first, "did you mean '" + typo + "'?");
                return;
            }
        }
        for (BotEntry entry : entries) {
            AgentBotReplyChannelStateRuntime.setReplyChannel(entry, channel);
            handleAgentChat(entry, message);
        }
    }

    /** Prefer a bot in the owner's current map so its reply/trade is visible. */
    private static BotEntry pickGroupSupplyResponder(Character owner, List<BotEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        int ownerMapId = owner != null ? owner.getMapId() : -1;
        for (BotEntry entry : entries) {
            if (AgentBotRuntimeIdentityRuntime.botMapId(entry) == ownerMapId) {
                return entry;
            }
        }
        return entries.get(0);
    }

    private boolean handlePendingLootOfferResponse(Character speaker, String message) {
        List<BotEntry> matches = new ArrayList<>();
        for (List<BotEntry> entries : bots.values()) {
            for (BotEntry entry : entries) {
                AgentOfferService.expirePendingOffer(entry);
                if (!isPendingLootOfferTarget(entry, speaker)) {
                    continue;
                }

                matches.add(entry);
            }
        }

        AgentBotTargetedCommandMatch targetedBot = AgentBotCommandParser.resolveTargetedBot(matches, message);
        if (targetedBot.entry() != null) {
            return AgentOfferService.handlePendingOfferResponse(targetedBot.entry(), speaker, targetedBot.commandText());
        }
        if (targetedBot.feedbackMessage() != null) {
            speaker.dropMessage(5, targetedBot.feedbackMessage());
            return true;
        }

        if (matches.size() == 1) {
            return AgentOfferService.handlePendingOfferResponse(matches.get(0), speaker, message);
        }
        if (matches.size() > 1 && looksLikeConfirmation(message)) {
            speaker.dropMessage(5, "More than one bot is waiting on you. Say '<botname> yes' or '<slot> yes'.");
            return true;
        }

        return false;
    }

    private boolean isPendingLootOfferTarget(BotEntry entry, Character speaker) {
        return entry != null
                && AgentOfferService.hasPendingOffer(entry)
                && AgentBotOfferStateRuntime.pendingOfferRecipientIs(entry, speaker)
                && AgentBotRuntimeIdentityRuntime.botMapId(entry) == speaker.getMapId();
    }

    private boolean looksLikeConfirmation(String message) {
        String normalized = message.trim().toLowerCase();
        return normalized.matches(".*\\b(yes|yep|yeah|yea|y|ok|sure|confirm|no|nope|nah|nvm|never\\s*mind|dont|don't|not\\s+now|skip)\\b.*");
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

    private static final int RETREAT_HOLD_MS = 600;
    private static final int RETREAT_ARRIVAL_TOLERANCE_X = 25; // 50ms tick can't land on an exact pixel

    // AoE reposition commitment: returns the sweet-spot Point to walk to before firing, or null to
    // fire now. Scores once when a commitment starts (AgentBotCombatAoeRepositionRuntime); while
    // committed it just returns the stored anchor — no further scoring — until the bot arrives, the
    // bounded-chase deadline expires, or the target dies/clears.
    private static Point resolveAoeReposition(BotEntry entry, Character bot, Monster target,
                                              AgentAttackPlan attackPlan, Point botPos) {
        long now = System.currentTimeMillis();
        if (AgentBotAoeRepositionStateRuntime.hasAnchor(entry)) {
            boolean done = AgentBotAoeRepositionStateRuntime.isExpiredOrArrived(
                    entry, botPos, now, AgentCombatConfig.cfg.AOE_REPOSITION_ARRIVAL_X)
                    || target == null || !target.isAlive();
            if (done) {
                AgentBotAoeRepositionStateRuntime.clear(entry);
                return null;
            }
            return AgentBotAoeRepositionStateRuntime.anchor(entry);
        }
        Point anchor = AgentBotCombatAoeRepositionRuntime.aoeRepositionTarget(
                entry, bot, target, attackPlan, AgentCombatConfig.cfg);
        if (anchor != null) {
            AgentBotAoeRepositionStateRuntime.setAnchor(
                    entry, anchor, now + AgentCombatConfig.cfg.AOE_REPOSITION_MAX_MS);
        }
        return anchor;
    }

    static Point selectGrindNavigationTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        return selectGrindNavigationTarget(entry, botPos, combatTargetPos, false);
    }

    private static Point selectGrindNavigationTarget(BotEntry entry,
                                                     Point botPos,
                                                     Point combatTargetPos,
                                                     boolean crossRegionRetreatChecked) {
        if (entry == null || botPos == null || combatTargetPos == null) {
            return combatTargetPos;
        }

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return combatTargetPos;
        }

        long now = System.currentTimeMillis();
        boolean retreatNeeded = AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(
                AgentAttackExecutionProvider.getEquippedWeaponType(bot), botPos, combatTargetPos);

        // Surround-breakout commitment: once pincered, keep bursting the SAME way until the
        // bot is no longer flanked on both sides (or a safety timeout), re-issuing a forward
        // step each tick. A per-step local retreat here would walk into the opposite mob and
        // flip direction every time it arrives — the swarm oscillation we are fixing.
        if (AgentBotBreakoutStateRuntime.hasBreakoutCommitment(entry)) {
            if (AgentBotBreakoutStateRuntime.isExpired(entry, now)
                    || !AgentAttackExecutionProvider.isSurrounded(bot, botPos)) {
                AgentBotBreakoutStateRuntime.clear(entry);
            } else {
                return breakoutStep(botPos, AgentBotBreakoutStateRuntime.direction(entry));
            }
        }

        // Hysteresis: a previously committed retreat keeps its goal until either the
        // hold expires, the bot has effectively arrived, or the bot wandered too far
        // from the hold pos for it to still be the right answer.
        if (AgentBotRetreatHoldStateRuntime.hasActiveHold(entry, now)) {
            int dxHold = AgentBotRetreatHoldStateRuntime.distanceFromHoldX(entry, botPos);
            if (dxHold <= RETREAT_ARRIVAL_TOLERANCE_X) {
                AgentBotRetreatHoldStateRuntime.clear(entry);
            } else if (dxHold > AgentCombatConfig.cfg.RANGED_RETREAT_DISTANCE_X * 2) {
                AgentBotRetreatHoldStateRuntime.clear(entry);
            } else {
                return AgentBotRetreatHoldStateRuntime.holdPosition(entry);
            }
        } else if (AgentBotRetreatHoldStateRuntime.hasHold(entry)) {
            AgentBotRetreatHoldStateRuntime.clear(entry);
        }

        if (!retreatNeeded) {
            return combatTargetPos;
        }

        // Prefer landing on a different walkable region than the target — mobs there can't
        // path to the bot without traversing a nav edge, while the bot can still shoot across.
        // Empty/sparse adjacent regions score highest. Cross-region uses the nav-edge
        // pipeline for stickiness, so no separate hysteresis is set here.
        Point crossRegionPos = crossRegionRetreatChecked
                ? null
                : selectCrossRegionRetreatTarget(entry, botPos, combatTargetPos);
        if (crossRegionPos != null) {
            return crossRegionPos;
        }

        // No separated region to flee to (e.g. one open platform). If the bot is pincered,
        // commit to bursting out one side instead of micro-retreating into the other wall.
        if (AgentAttackExecutionProvider.isSurrounded(bot, botPos)) {
            int dir = pickBreakoutDirection(entry, botPos, combatTargetPos);
            AgentBotBreakoutStateRuntime.setBreakoutCommitment(
                    entry, dir, now + AgentCombatConfig.cfg.BREAKOUT_MAX_MS);
            // Drop any stale one-step hysteresis so it can't fight the committed breakout.
            AgentBotRetreatHoldStateRuntime.clear(entry);
            return breakoutStep(botPos, dir);
        }

        Point retreatPos = AgentAttackExecutionProvider.retreatTargetPosition(bot, botPos, combatTargetPos);
        if (shouldUseLocalCombatRetreatTarget(entry, botPos, combatTargetPos, retreatPos)) {
            AgentBotRetreatHoldStateRuntime.setHold(entry, retreatPos, now + RETREAT_HOLD_MS);
            return retreatPos;
        }
        return combatTargetPos;
    }

    private static Point breakoutStep(Point botPos, int dir) {
        return new Point(botPos.x + dir * AgentCombatConfig.cfg.RANGED_RETREAT_DISTANCE_X, botPos.y);
    }

    /**
     * Choose which way to burst out of a surrounding swarm. Base preference is the more open
     * side (the flank whose nearest mob is farther). When the nav graph is available, override
     * with the side whose reachable walkable neighbor is less crowded, so the bot runs toward
     * an exit instead of into a dead-end wall. Ties fall back to the open-side preference.
     */
    private static int pickBreakoutDirection(BotEntry entry, Point botPos, Point combatTargetPos) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        int base = AgentAttackExecutionProvider.pickRetreatDirection(bot, botPos, combatTargetPos);
        MapleMap map = bot != null ? bot.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return base;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return base;
        }
        int botRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        if (botRegionId < 0) {
            return base;
        }
        int leftBestMobs = Integer.MAX_VALUE;
        int rightBestMobs = Integer.MAX_VALUE;
        for (AgentNavigationGraph.Edge edge : graph.getOutgoing(botRegionId)) {
            if (edge.type != AgentNavigationGraph.EdgeType.WALK) {
                continue;
            }
            AgentNavigationGraph.Region region = graph.getRegion(edge.toRegionId);
            if (region == null || region.isRopeRegion) {
                continue;
            }
            int side = Integer.signum(edge.endPoint.x - botPos.x);
            int mobs = countMobsInRegion(graph, map, region);
            if (side < 0) {
                leftBestMobs = Math.min(leftBestMobs, mobs);
            } else if (side > 0) {
                rightBestMobs = Math.min(rightBestMobs, mobs);
            }
        }
        if (leftBestMobs == rightBestMobs) {
            return base;
        }
        return leftBestMobs < rightBestMobs ? -1 : 1;
    }

    /**
     * Pick a one-edge-away region to retreat into, preferring regions that are NOT
     * the target's region and contain the fewest mobs. Returns the edge's landing
     * point so nav can route through the existing edge-traversal pipeline. Null
     * means no separated region qualifies — caller falls back to in-region retreat.
     *
     * Scoring is simple by design (easy to eyeball in-game):
     *   +1000  destination region has zero live mobs ("the empty platform next door")
     *   -100*N each live mob in the destination region
     *   -dx/10  prefer anchors closer to the target so projectiles still land
     */
    static Point selectCrossRegionRetreatTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        if (entry == null || botPos == null || combatTargetPos == null) {
            return null;
        }
        if (AgentBotMovementStateRuntime.climbing(entry) || AgentBotMovementStateRuntime.inAir(entry) || AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            return null;
        }
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        MapleMap map = bot != null ? bot.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return null;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, AgentBotMovementStateRuntime.movementProfile(entry));
            return null;
        }

        int botRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        if (botRegionId < 0) {
            return null;
        }
        int targetRegionId = BotNavigationManager.resolveTargetRegionId(graph, entry, map, combatTargetPos);

        int projectileRange = AgentProjectileHitbox.CLIENT_PROJECTILE_BASE_RANGE
                + AgentProjectileHitbox.passiveProjectileRangeBonus(bot);
        int yReachable = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y * 2;

        Point reachableRetreat = selectReachableProjectileRetreatTarget(
                graph, map, botPos, botRegionId, targetRegionId, combatTargetPos, projectileRange, yReachable);
        if (reachableRetreat != null) {
            return reachableRetreat;
        }

        AgentNavigationGraph.Edge bestEdge = null;
        int bestScore = Integer.MIN_VALUE;
        for (AgentNavigationGraph.Edge edge : graph.getOutgoing(botRegionId)) {
            if (edge.type != AgentNavigationGraph.EdgeType.WALK) {
                continue;
            }
            int toRegionId = edge.toRegionId;
            if (toRegionId == botRegionId || toRegionId == targetRegionId) {
                continue;
            }
            AgentNavigationGraph.Region region = graph.getRegion(toRegionId);
            if (region == null || region.isRopeRegion) {
                continue;
            }
            Point anchor = edge.endPoint;
            int dx = Math.abs(anchor.x - combatTargetPos.x);
            int dy = Math.abs(anchor.y - combatTargetPos.y);
            if (dx > projectileRange || dy > yReachable) {
                continue;
            }
            // Don't land back inside the degenerate band — that defeats the retreat.
            if (dx <= AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X) {
                continue;
            }

            int mobsInRegion = countMobsInRegion(graph, map, region);
            int score = (mobsInRegion == 0 ? 1000 : 0) - mobsInRegion * 100 - dx / 10;
            if (score > bestScore) {
                bestScore = score;
                bestEdge = edge;
            }
        }

        return bestEdge != null ? new Point(bestEdge.endPoint) : null;
    }

    private static Point selectReachableProjectileRetreatTarget(AgentNavigationGraph graph,
                                                                MapleMap map,
                                                                Point botPos,
                                                                int botRegionId,
                                                                int targetRegionId,
                                                                Point combatTargetPos,
                                                                int projectileRange,
                                                                int yReachable) {
        Point bestPoint = null;
        int bestScore = Integer.MIN_VALUE;
        for (AgentNavigationGraph.Region region : graph.regions) {
            if (region == null || region.isRopeRegion) {
                continue;
            }
            if (region.id == botRegionId || region.id == targetRegionId) {
                continue;
            }

            Point candidate = selectProjectileRetreatPoint(region, combatTargetPos, projectileRange, yReachable);
            if (candidate == null) {
                continue;
            }

            List<AgentNavigationGraph.Edge> path = BotNavigationManager.findPath(
                    graph, map, botPos, botRegionId, region.id, candidate);
            if (path.isEmpty() || pathUsesPortal(path)) {
                continue;
            }

            int pathCost = path.stream().mapToInt(pathEdge -> pathEdge.cost).sum();
            int mobsInRegion = countMobsInRegion(graph, map, region);
            int dx = Math.abs(candidate.x - combatTargetPos.x);
            int score = (mobsInRegion == 0 ? 1500 : 0) - mobsInRegion * 150 - pathCost / 10 - dx / 10;
            if (score > bestScore) {
                bestScore = score;
                bestPoint = candidate;
            }
        }
        return bestPoint;
    }

    private static boolean pathUsesPortal(List<AgentNavigationGraph.Edge> path) {
        for (AgentNavigationGraph.Edge edge : path) {
            if (edge.type == AgentNavigationGraph.EdgeType.PORTAL) {
                return true;
            }
        }
        return false;
    }

    private static Point selectProjectileRetreatPoint(AgentNavigationGraph.Region region,
                                                      Point combatTargetPos,
                                                      int projectileRange,
                                                      int yReachable) {
        int edgeMargin = Math.min(BotMovementManager.cfg.GRIND_EDGE_MARGIN, Math.max(0, region.width() / 4));
        int minX = Math.max(region.minX + edgeMargin, combatTargetPos.x - projectileRange);
        int maxX = Math.min(region.maxX - edgeMargin, combatTargetPos.x + projectileRange);
        if (minX > maxX) {
            minX = Math.max(region.minX, combatTargetPos.x - projectileRange);
            maxX = Math.min(region.maxX, combatTargetPos.x + projectileRange);
        }
        if (minX > maxX) {
            return null;
        }

        int minShootDx = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X + 20;
        int bestScore = Integer.MIN_VALUE;
        Point bestPoint = null;
        int[] probes = {
                minX,
                maxX,
                combatTargetPos.x - minShootDx,
                combatTargetPos.x + minShootDx,
                (minX + maxX) / 2
        };
        for (int probe : probes) {
            Point candidate = projectileRetreatCandidate(region, probe, minX, maxX,
                    combatTargetPos, projectileRange, yReachable, minShootDx);
            if (candidate == null) {
                continue;
            }
            int dx = Math.abs(candidate.x - combatTargetPos.x);
            int dy = Math.abs(candidate.y - combatTargetPos.y);
            int score = -dx * 10 - dy;
            if (score > bestScore) {
                bestScore = score;
                bestPoint = candidate;
            }
        }
        return bestPoint;
    }

    private static Point projectileRetreatCandidate(AgentNavigationGraph.Region region,
                                                    int probeX,
                                                    int minX,
                                                    int maxX,
                                                    Point combatTargetPos,
                                                    int projectileRange,
                                                    int yReachable,
                                                    int minShootDx) {
        int x = Math.max(minX, Math.min(maxX, probeX));
        if (x < combatTargetPos.x && combatTargetPos.x - x < minShootDx) {
            x = combatTargetPos.x - minShootDx;
        } else if (x > combatTargetPos.x && x - combatTargetPos.x < minShootDx) {
            x = combatTargetPos.x + minShootDx;
        } else if (x == combatTargetPos.x) {
            int leftX = combatTargetPos.x - minShootDx;
            int rightX = combatTargetPos.x + minShootDx;
            x = leftX >= minX ? leftX : rightX;
        }
        if (x < minX || x > maxX) {
            return null;
        }

        Point point = region.pointAt(x);
        int dx = Math.abs(point.x - combatTargetPos.x);
        int dy = Math.abs(point.y - combatTargetPos.y);
        if (dx < minShootDx
                || dx > projectileRange
                || dy > yReachable
                || point.y > combatTargetPos.y + BotMovementManager.cfg.JUMP_Y_THRESH) {
            return null;
        }
        return point;
    }

    private static int countMobsInRegion(AgentNavigationGraph graph,
                                         MapleMap map,
                                         AgentNavigationGraph.Region region) {
        int count = 0;
        for (server.life.Monster m : map.getAllMonsters()) {
            if (!m.isAlive()) {
                continue;
            }
            Point mp = m.getPosition();
            if (mp == null) {
                continue;
            }
            // Cheap bbox prefilter — region.findRegionId does foothold lookup, more expensive.
            if (mp.x < region.minX - 5 || mp.x > region.maxX + 5
                    || mp.y < region.minY - 80 || mp.y > region.maxY + 80) {
                continue;
            }
            if (graph.findRegionId(map, mp) == region.id) {
                count++;
            }
        }
        return count;
    }

    static boolean shouldUseLocalCombatRetreatTarget(BotEntry entry,
                                                     Point botPos,
                                                     Point combatTargetPos,
                                                     Point retreatPos) {
        if (entry == null || botPos == null || combatTargetPos == null || retreatPos == null) {
            return false;
        }
        if (AgentBotMovementStateRuntime.climbing(entry) || AgentBotMovementStateRuntime.inAir(entry) || AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            return false;
        }

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        MapleMap map = bot != null ? bot.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return false;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, AgentBotMovementStateRuntime.movementProfile(entry));
            return false;
        }
        int botRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        int combatTargetRegionId = BotNavigationManager.resolveTargetRegionId(graph, entry, map, combatTargetPos);
        if (botRegionId < 0 || combatTargetRegionId < 0 || botRegionId != combatTargetRegionId) {
            return false;
        }

        int retreatRegionId = BotNavigationManager.resolveTargetRegionId(graph, entry, map, retreatPos);
        return retreatRegionId == botRegionId;
    }

    static boolean shouldSearchForGrindTarget(BotEntry entry,
                                              Character bot,
                                              Monster currentTarget,
                                              AgentAttackPlan currentAttackPlan,
                                              long now) {
        if (entry == null) {
            return false;
        }
        if (currentTarget == null) {
            return true;
        }
        if (AgentBotGrindSearchStateRuntime.searchBlocked(entry, now)) {
            return false;
        }
        if (bot == null
                || currentAttackPlan == null
                || !AgentCombatRangePolicy.isTargetInAttackRange(currentAttackPlan, bot, currentTarget)) {
            return true;
        }
        // In range we normally stay committed (avoids flip-flop). Exception: an AoE bot stuck
        // single-targeting keeps scanning for a better cluster — the switch itself is gated by
        // cluster-size hysteresis in shouldSwitchToSearchedTarget.
        return AgentCombatScoringPolicy.isAoeSingleTargeting(
                currentAttackPlan.skillId,
                currentAttackPlan.targets.size(),
                AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry),
                AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
    }

    /**
     * Decide whether to adopt a freshly searched grind target over the current one. Always adopts
     * when not committed (current null, no plan, or current out of attack range). When committed to
     * an in-range target, only switches if the searched target anchors a strictly larger AoE cluster
     * — hysteresis that prevents flip-flop between near-equal targets.
     */
    static boolean shouldSwitchToSearchedTarget(BotEntry entry, Character bot, Monster current,
                                                Monster searched, AgentAttackPlan currentPlan) {
        if (searched == null || searched == current) {
            return false;
        }
        if (current == null || bot == null || currentPlan == null
                || !AgentCombatRangePolicy.isTargetInAttackRange(currentPlan, bot, current)) {
            return true;
        }
        int searchedClusterSize = bot.getMap() == null || searched.getPosition() == null
                ? 0
                : AgentCombatScoringPolicy.legacyCappedAoeClusterSize(
                        searched,
                        bot.getMap().getAllMonsters(),
                        AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        int currentClusterSize = bot.getMap() == null || current.getPosition() == null
                ? 0
                : AgentCombatScoringPolicy.legacyCappedAoeClusterSize(
                        current,
                        bot.getMap().getAllMonsters(),
                        AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        return searchedClusterSize > currentClusterSize;
    }

    static Point resolveNoGrindTargetPosition(BotEntry entry, Point botPos, MapleMap map) {
        if (entry == null || botPos == null) {
            return botPos;
        }
        if (AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            Point lootPos = activeGrindLootPosition(entry, botPos);
            if (lootPos != null) {
                return lootPos;
            }
        }

        AgentNavigationGraph graph = map != null
                ? AgentNavigationGraphService.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry))
                : null;
        int regionId = graph != null ? BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos) : -1;
        AgentNavigationGraph.Region region = graph != null ? graph.getRegion(regionId) : null;
        if (region != null && !region.isRopeRegion && region.width() > 0) {
            Point wander = AgentBotPatrolStateRuntime.patrolWanderTarget(entry);
            if (wander == null || AgentPositionService.isNear(botPos, wander, BotMovementManager.cfg.STOP_DIST)) {
                int x = ThreadLocalRandom.current().nextInt(region.minX, region.maxX + 1);
                wander = region.pointAt(x);
                AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
            }
            return wander;
        }

        AgentBotPatrolStateRuntime.clearPatrolWanderTarget(entry);
        return new Point(botPos.x + AgentBotGrindWanderStateRuntime.ensureWanderDirection(entry) * 200, botPos.y);
    }

    static Point resolveNoGrindTargetPosition(BotEntry entry, Point botPos) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        MapleMap map = bot != null ? bot.getMap() : null;
        return resolveNoGrindTargetPosition(entry, botPos, map);
    }

    private static Point activeGrindLootPosition(BotEntry entry, Point botPos) {
        MapItem loot = AgentBotGrindLootStateRuntime.grindLootTarget(entry);
        if (loot == null || botPos == null) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (loot.isPickedUp() || bot == null || bot.getMap() == null
                || bot.getMap().getMapObject(loot.getObjectId()) != loot) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        if (!AgentLootEligibility.canBotTargetLoot(entry, bot, bot.getMap(), loot, System.currentTimeMillis())) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        Point lootPos = loot.getPosition();
        if (Math.abs(lootPos.x - botPos.x) <= cfg.LOOT_RADIUS
                && Math.abs(lootPos.y - botPos.y) <= cfg.LOOT_RADIUS) {
            suppressGrindLootRetry(entry, loot);
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        return lootPos;
    }

    static void suppressGrindLootRetry(BotEntry entry, MapItem loot) {
        if (entry == null || loot == null) {
            return;
        }
        AgentBotGrindLootStateRuntime.suppressRetry(
                entry,
                loot,
                System.currentTimeMillis() + cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    static double activeLootTravelDistSq(Point botPos, Point lootPos) {
        if (botPos == null || lootPos == null) {
            return Double.MAX_VALUE;
        }
        int dx = Math.max(0, Math.abs(lootPos.x - botPos.x) - cfg.LOOT_RADIUS);
        int dy = Math.max(0, Math.abs(lootPos.y - botPos.y) - cfg.LOOT_RADIUS);
        return (double) dx * dx + (double) dy * dy;
    }

    static Point convenientLootTarget(BotEntry entry, Point botPos, Point mobPos) {
        Point lootPos = activeGrindLootPosition(entry, botPos);
        if (lootPos == null) {
            return null;
        }
        double lootDistSq = activeLootTravelDistSq(botPos, lootPos);
        double mobDistSq = mobPos.distanceSq(botPos);
        return lootDistSq < mobDistSq * cfg.GRIND_LOOT_CONVENIENCE_RATIO ? lootPos : null;
    }

    private static Point resolvePatrolWanderTarget(BotEntry entry, Point botPos, MapleMap map) {
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        int patrolRegionId = AgentBotPatrolStateRuntime.patrolRegionId(entry);
        AgentNavigationGraph.Region region = graph != null ? graph.getRegion(patrolRegionId) : null;
        if (region == null || region.isRopeRegion || region.width() == 0) {
            return resolveNoGrindTargetPosition(entry, botPos, map);
        }
        // Seek loot before roaming
        Point lootTarget = AgentLootTargetService.findNearestPatrolLootTarget(entry, patrolRegionId);
        if (lootTarget != null) {
            AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, lootTarget);
            return lootTarget;
        }
        // Roam within region
        Point wander = AgentBotPatrolStateRuntime.patrolWanderTarget(entry);
        if (wander == null || AgentPositionService.isNear(botPos, wander, BotMovementManager.cfg.STOP_DIST)) {
            int x = ThreadLocalRandom.current().nextInt(region.minX, region.maxX + 1);
            wander = region.pointAt(x);
            AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
        }
        return wander;
    }

    // Main tick
    // -------------------------------------------------------------------------

    private void tick(BotEntry entry, int ownerCharId, int botCharId) {
        long startedAt = AgentPerformanceMonitor.enabled() ? System.nanoTime() : 0L;
        try {
            tickCore(entry, ownerCharId, botCharId);
            resetBotTickFailures(entry);
        } catch (Throwable t) {
            handleBotTickFailure(entry, ownerCharId, botCharId, t);
        } finally {
            if (startedAt != 0L) {
                AgentPerformanceMonitor.record("tick-total", System.nanoTime() - startedAt);
            }
        }
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
        if (entry == null) return;
        if (AgentBotManagerStatusRuntime.airshowActive(entry)) return;
        if (AgentBotTickCadenceStateRuntime.consumeSkipDelay(entry, BotMovementManager.cfg.TICK_MS)) {
            return;
        }
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);

        // Guard: bot was removed from its map externally (e.g. a prior disconnect race).
        // Stop ticking and clean up rather than NPE-spamming TimerManager workers.
        if (bot.getMap() == null) {
            removeBotByCharId(botCharId);
            return;
        }

        // Heartbeat: keep the bot's lastPacket fresh and broadcast a standing-in-place
        // movement packet every 10 minutes so the server never considers the bot idle.
        // Covers all modes: idle, follow, and grind.
        long nowMs = System.currentTimeMillis();
        AgentHeartbeatService.tickHeartbeat(
                entry, bot, nowMs, 600_000L, agent -> agent.getClient().updateLastPacket(), BotMovementManager::broadcastMovement);

        AgentOfferService.expirePendingOffer(entry);
        boolean runAiTick = AgentTickOrchestrator.prepareTick(
                entry, BotMovementManager.cfg.TICK_MS, cfg.AI_TICK_MS, System.currentTimeMillis());

        Character owner = resolveTickOwner(entry, ownerCharId);
        if (handleOwnerOfflineOrDead(entry, bot, owner, nowMs, ownerCharId)) {
            return;
        }
        if (owner == null) {
            AgentBotModeStateRuntime.setFollowing(entry, false);
            if (groundAfterMapChange(entry, bot)) {
                return;
            }
            // Owner-offline pass-through: when explicit move target is set (currently by
            // the offline-town cluster, but any caller of issueMoveTo) the bot still
            // walks toward it. Same field and movement core as the player "here"
            // command — only the tick gate differs because the regular pipeline is
            // tightly coupled to `owner` for combat/follow/heal logic that's all
            // skipped here.
            if (AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
                tickStandaloneMoveTarget(entry, bot, runAiTick);
            } else {
                tickIdleEntry(entry, bot);
            }
            return;
        }

        // Dead state: skip AI until respawn timer expires.
        // Also catch stale hp=0 (e.g. deadUntil was lost on save/reconnect) — re-enter dead state.
        if (handleDeadTick(entry, bot, owner)) {
            return;
        }

        BotMovementManager.refreshMovementProfile(entry);

        Point botPos = bot.getPosition();
        Character followAnchor = resolveFollowAnchor(entry, owner);
        AgentTargetSnapshot targetSnapshot = captureTargetSnapshot(entry);
        Point ownerPos = targetSnapshot.rawOwnerPos();
        updateObservedOwnerMotion(entry, ownerPos);
        AgentBotOwnerMotionStateRuntime.rememberOwnerPosition(entry, ownerPos); // raw owner pos before formation offset/snap
        clearFarmAnchorOnMapChange(entry, bot);
        clearPatrolOnMapChange(entry, bot);
        Point targetPos = targetSnapshot.primaryTargetPos();
        boolean perf = AgentPerformanceMonitor.enabled();
        clearFollowActionMoveWindowIfSettled(entry, botPos, targetSnapshot);

        // These run in all modes (idle, follow, grind)
        if (runCommonTickSystems(entry, bot, owner, runAiTick)) {
            return;
        }

        // Trade window open: keep physics consistent (gravity / swim / idle stance) but
        // do not issue any movement input — no follow, grind, attack, teleport, or shop visit.
        // Prevents the bot from wandering away or auto-equipping while the player is mid-trade.
        if (bot.getTrade() != null) {
            if (!perf) {
                tickTradePhysicsOnly(entry, bot);
            } else {
                long tTrade = System.nanoTime();
                try { tickTradePhysicsOnly(entry, bot); }
                finally { AgentPerformanceMonitor.record("tick-trade-physics", System.nanoTime() - tTrade); }
            }
            return;
        }

        boolean idleConsumed;
        if (!perf) {
            idleConsumed = tickIdleEntry(entry, bot);
        } else {
            long tIdle = System.nanoTime();
            idleConsumed = tickIdleEntry(entry, bot);
            AgentPerformanceMonitor.record("tick-idle", System.nanoTime() - tIdle);
        }
        if (idleConsumed) {
            return;
        }

        // Map change and teleport checks only apply when following a live anchor.
        // Shop visits are intentional same-map detours and must not be pulled back
        // to the owner while walking to the NPC.
        if (!AgentBotShopStateRuntime.shopVisitPending(entry) && syncFollowMap(entry, bot, followAnchor)) {
            return;
        }
        if (recoverGrindPartyTeleportDistance(entry, bot, followAnchor)) {
            return;
        }
        // Teleport if hopelessly far — applies to both follow and grind (catches falling off map)
        if (recoverTeleportDistance(entry, bot, targetPos)) {
            return;
        }

        // On any map change (e.g. NPC-triggered portal): rebuild footholds, reset physics,
        // and snap to ground so the bot doesn't carry over airborne state from the previous map.
        if (!AgentBotMapStateRuntime.isTrackingMap(entry, bot.getMapId())) {
            if (!perf) {
                AgentBotMapStateRuntime.setMapTracking(entry, bot.getMapId(), BotMovementManager.buildFhIndex(bot.getMap()));
                Point cur = bot.getPosition();
                Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
                BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
                BotMovementManager.resetEntryStateAfterTeleport(entry);
                AgentNavigationGraphService.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                BotMovementManager.broadcastMovement(entry);
                if (AgentPartyQuestHooks.requiresGrind(entry, bot)) { issueGrind(entry); }
                else if (AgentPartyQuestHooks.requiresFollow(entry, bot)) { issueFollowOwner(entry); }
                else { AgentBotPqRuntime.resetKpqStage5Claimed(entry); } // left KPQ — reset for next run
                AgentShopService.onMapChange(entry, bot);
                AgentBotManagerStatusRuntime.checkManagerStatus(entry, bot);
            } else {
                long tMapChange = System.nanoTime();
                try {
                    AgentBotMapStateRuntime.setMapTracking(entry, bot.getMapId(), BotMovementManager.buildFhIndex(bot.getMap()));
                    Point cur = bot.getPosition();
                    Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
                    BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
                    BotMovementManager.resetEntryStateAfterTeleport(entry);
                    AgentNavigationGraphService.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                    BotMovementManager.broadcastMovement(entry);
                    if (AgentPartyQuestHooks.requiresGrind(entry, bot)) { issueGrind(entry); }
                    else if (AgentPartyQuestHooks.requiresFollow(entry, bot)) { issueFollowOwner(entry); }
                    else { AgentBotPqRuntime.resetKpqStage5Claimed(entry); } // left KPQ — reset for next run
                    AgentShopService.onMapChange(entry, bot);
                    AgentBotManagerStatusRuntime.checkManagerStatus(entry, bot);
                } finally {
                    AgentPerformanceMonitor.record("tick-map-change", System.nanoTime() - tMapChange);
                }
            }
            return;
        }

        // Shop visit: navigate to approach point before resuming normal flow.
        // Keep this ahead of follow/combat/grind logic so resupply movement is not
        // coupled to owner proximity.
        if (AgentBotShopStateRuntime.shopVisitPending(entry)) {
            boolean consumed;
            if (!perf) {
                consumed = AgentShopService.tickShopVisit(entry, bot);
            } else {
                long tShop = System.nanoTime();
                consumed = AgentShopService.tickShopVisit(entry, bot);
                AgentPerformanceMonitor.record("tick-shop-visit", System.nanoTime() - tShop);
            }
            targetPos = AgentBotShopStateRuntime.activeShopTargetPosition(entry);
            if (!consumed && AgentBotShopStateRuntime.shopApproachDelayMs(entry) > 0) {
                return;
            }
            if (targetPos != null) {
                stepMovementCore(entry, targetPos, runAiTick);
            }
            return;
        }

        // Follow mode: attack monsters already in attack range without chasing
        if (AgentBotModeStateRuntime.following(entry) && runAiTick && !AgentBotMovementStateRuntime.climbing(entry)
                && followAnchor != null
                && bot.getMapId() == followAnchor.getMapId()
                && Math.abs(botPos.x - followAnchor.getPosition().x) <= BotMovementManager.cfg.FOLLOW_DIST * 5) {
            LocalOpportunityAttackResult result;
            if (!perf) {
                result = tryLocalOpportunityAttack(
                        entry, bot, botPos, targetPos, targetSnapshot.followTargetPos(), true, true);
            } else {
                long tOpp = System.nanoTime();
                result = tryLocalOpportunityAttack(
                        entry, bot, botPos, targetPos, targetSnapshot.followTargetPos(), true, true);
                AgentPerformanceMonitor.record("opportunity-attack", System.nanoTime() - tOpp);
            }
            targetPos = result.targetPos();
            if (result.consumedTick()) {
                return;
            }
        }

        if (tryFollowIdleMovementFastPath(entry, bot, targetPos, nowMs)) {
            return;
        }

        if (runAiTick && shouldUseScriptedMoveLocalCombat(entry, targetPos)) {
            clearActionMoveWindowIfSettled(entry, botPos, targetPos);
            LocalOpportunityAttackResult result;
            if (!perf) {
                result = tryLocalOpportunityAttack(
                        entry, bot, botPos, targetPos, targetPos, true, true);
            } else {
                long tOppS = System.nanoTime();
                result = tryLocalOpportunityAttack(
                        entry, bot, botPos, targetPos, targetPos, true, true);
                AgentPerformanceMonitor.record("opportunity-attack", System.nanoTime() - tOppS);
            }
            if (result.consumedTick()) {
                return;
            }
            targetPos = result.targetPos();
            if (!perf) {
                stepMovementCore(entry, targetPos, runAiTick);
            } else {
                long tStep = System.nanoTime();
                try { stepMovementCore(entry, targetPos, runAiTick); }
                finally { AgentPerformanceMonitor.record("step-movement-core", System.nanoTime() - tStep); }
            }
            return;
        }

        if (AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            if (!perf) {
                tickAnchoredFarm(entry, bot, botPos, runAiTick);
            } else {
                long tFarm = System.nanoTime();
                try { tickAnchoredFarm(entry, bot, botPos, runAiTick); }
                finally { AgentPerformanceMonitor.record("tick-anchored-farm", System.nanoTime() - tFarm); }
            }
            return;
        }

        // Grind mode: navigate toward nearest monster, attack when in range
        if (AgentBotModeStateRuntime.grinding(entry)) {
            LocalOpportunityAttackResult grindResult;
            if (!perf) {
                grindResult = tickGrindMode(entry, bot, botPos, targetPos, runAiTick);
            } else {
                long tGrindDispatch = System.nanoTime();
                try {
                    grindResult = tickGrindMode(entry, bot, botPos, targetPos, runAiTick);
                } finally {
                    AgentPerformanceMonitor.record("tick-grind-dispatch", System.nanoTime() - tGrindDispatch);
                }
            }
            if (grindResult.consumedTick()) {
                return;
            }
            targetPos = grindResult.targetPos();
        }

        if (!perf) {
            stepMovementCore(entry, targetPos, runAiTick);
        } else {
            long tStepTail = System.nanoTime();
            try { stepMovementCore(entry, targetPos, runAiTick); }
            finally { AgentPerformanceMonitor.record("step-movement-core", System.nanoTime() - tStepTail); }
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
        double seekRangeSq = (double) AgentCombatConfig.cfg.GRIND_SEEK_RANGE * AgentCombatConfig.cfg.GRIND_SEEK_RANGE;
        Monster target = AgentBotGrindTargetStateRuntime.targetInSeekRange(entry, bot, botPos, seekRangeSq);
        long now = System.currentTimeMillis();
        AgentAttackPlan attackPlan = target == null
                ? null
                : AgentBotCombatPlanRuntime.planAttack(entry, bot, target, AgentCombatConfig.cfg);
        // Validate cached loot target
        if (AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            MapItem loot = AgentBotGrindLootStateRuntime.grindLootTarget(entry);
            if (loot.isPickedUp() || bot.getMap().getMapObject(loot.getObjectId()) != loot) {
                AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            }
        }
        if (runAiTick && shouldSearchForGrindTarget(entry, bot, target, attackPlan, now)) {
            Monster searchedTarget = AgentBotPatrolStateRuntime.hasPatrolRegion(entry)
                    ? AgentBotCombatTargetRuntime.findPatrolTarget(entry, bot, AgentCombatConfig.cfg)
                    : AgentBotCombatTargetRuntime.findGrindTarget(entry, bot, AgentCombatConfig.cfg);
            if (shouldSwitchToSearchedTarget(entry, bot, target, searchedTarget, attackPlan)) {
                target = searchedTarget;
                attackPlan = null;
            }
            AgentBotGrindSearchStateRuntime.scheduleNextSearch(
                    entry, now + AgentCombatConfig.cfg.GRIND_RETARGET_INTERVAL_MS);
        }
        // Search for a convenient loot drop every AI tick (grind mode only)
        if (runAiTick && !AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, AgentLootTargetService.findNearestGrindLootTarget(
                    entry,
                    bot,
                    BotManager.cfg.LOOT_RADIUS,
                    AgentBotGrindLootStateRuntime::isRetrySuppressed));
        }
        if (target == null) {
            AgentBotGrindTargetStateRuntime.clear(entry);
            if (AgentMapEnvironmentService.isSwimMap(entry) && AgentBotMovementStateRuntime.inAir(entry)) {
                BotMovementManager.tickSwimming(entry, targetPos);
                return new LocalOpportunityAttackResult(true, targetPos);
            } else if (AgentBotMovementStateRuntime.inAir(entry)) {
                BotMovementManager.tickAirborne(entry, targetPos);
                return new LocalOpportunityAttackResult(true, targetPos);
            } else {
                // No mob in seek range — pick a wander direction once and walk that way until
                // a mob enters range. Beats standing still and lets the bot self-relocate.
                targetPos = new Point(botPos.x + AgentBotGrindWanderStateRuntime.ensureWanderDirection(entry) * 200, botPos.y);
                // falls through to stepMovementCore below
            }
        }
        if (target == null) {
            targetPos = AgentBotPatrolStateRuntime.hasPatrolRegion(entry)
                    ? resolvePatrolWanderTarget(entry, botPos, bot.getMap())
                    : resolveNoGrindTargetPosition(entry, botPos, bot.getMap());
            stepMovementCore(entry, targetPos, runAiTick);
            return new LocalOpportunityAttackResult(true, targetPos);
        }
        AgentBotGrindTargetStateRuntime.setTarget(entry, target);
        AgentBotGrindWanderStateRuntime.clearWanderDirection(entry);
        AgentBotPatrolStateRuntime.clearPatrolWanderTarget(entry);
        Point tp = target.getPosition();
        Monster rangedPriorityTarget = selectPriorityRangedAttackTarget(entry, bot, botPos, target);
        if (rangedPriorityTarget != null && rangedPriorityTarget != target) {
            target = rangedPriorityTarget;
            AgentBotGrindTargetStateRuntime.setTarget(entry, rangedPriorityTarget);
            tp = target.getPosition();
            attackPlan = null;
        }
        // Crowding swap: if a closer mob is breaching the retreat band, attack THAT mob
        // instead of fleeing the original far target. The bot would have retreated either
        // way (the close mob also triggers retreat), but with the right target our shots
        // land on the actual threat instead of pointing at the far one.
        server.life.Monster closerThreat = rangedPriorityTarget == null
                ? AgentAttackExecutionProvider.findCloserThreatMob(bot, botPos, tp)
                : null;
        if (closerThreat != null && closerThreat != target) {
            target = closerThreat;
            AgentBotGrindTargetStateRuntime.setTarget(entry, closerThreat);
            tp = target.getPosition();
            attackPlan = null;
        }
        if (attackPlan == null) {
            attackPlan = AgentBotCombatPlanRuntime.planAttack(entry, bot, target, AgentCombatConfig.cfg);
        }
        WeaponType grindWeaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean targetInDegenerateBand = AgentAttackExecutionProvider.shouldDegenerateRangedAttack(grindWeaponType, botPos, tp);
        boolean degenAttackDone = AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry);
        boolean allowOneDegenerateAttack = targetInDegenerateBand && !degenAttackDone && rangedPriorityTarget == null;
        boolean shouldRetreatForRangedSpacing = degenAttackDone
                || (AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(grindWeaponType, botPos, tp)
                && !allowOneDegenerateAttack);
        // Opportunity attack: keep firing during retreat as long as the shot would land
        // as a true ranged hit. Suppress only inside the degenerate band, since firing
        // there would re-trigger degenAttackDone and extend the retreat indefinitely.
        boolean canFireWithoutDegen = grindWeaponType == null
                || !AgentAttackExecutionProvider.shouldDegenerateRangedAttack(grindWeaponType, botPos, tp);
        boolean attackGateOpen = !shouldRetreatForRangedSpacing || canFireWithoutDegen || allowOneDegenerateAttack;
        // Sticky cross-region retreat: pre-compute so an opportunity attack doesn't stall
        // the traversal — bot fires AND keeps walking toward the safe vantage in the same tick.
        Point crossRegionRetreatPos = shouldRetreatForRangedSpacing
                ? selectCrossRegionRetreatTarget(entry, botPos, tp)
                : null;
        // AoE positioning: when in range but the chosen plan is single-target, defer the shot
        // and walk into the cluster centroid if the AoE would beat it on DPS there (bounded).
        // Suppressed during ranged-spacing/cross-region retreats — spacing takes priority.
        Point aoeRepositionPos = (!shouldRetreatForRangedSpacing && crossRegionRetreatPos == null
                && attackGateOpen && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, bot, target))
                ? resolveAoeReposition(entry, bot, target, attackPlan, botPos)
                : null;

        boolean attackAttemptedInRange = false;
        if (!AgentBotMovementStateRuntime.climbing(entry)) {
            if (aoeRepositionPos == null
                    && attackGateOpen && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, bot, target)
                    && AgentCombatRangePolicy.canUseAttackPlanNow(
                            AgentBotMovementStateRuntime.grounded(entry), grindWeaponType, attackPlan.route)) {
                attackAttemptedInRange = true;
                // In range — attack if grounded, or during ascent of a jump
                int prevCooldown = AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry);
                AgentBotCombatAttackRuntime.attackMonster(entry, bot, attackPlan);
                boolean attacked = AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry) != prevCooldown;
                // If a ranged bot just did a degenerate close-range hit, force retreat next tick
                if (attacked && attackPlan.isCloseRangeRoute()
                        && AgentCombatAmmoCounter.isRangedAmmoWeapon(grindWeaponType)) {
                    AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);
                }
                // Don't short-circuit when a cross-region retreat is in progress — the
                // bot must still walk to the edge launch this tick.
                if (attacked && !AgentBotMovementStateRuntime.inAir(entry) && crossRegionRetreatPos == null) {
                    return new LocalOpportunityAttackResult(true, targetPos);
                }
            } else if (!AgentBotMovementStateRuntime.inAir(entry)
                    && attackPlan != null
                    && AgentCombatRangePolicy.isTargetJumpable(
                            AgentBotMovementStateRuntime.movementProfile(entry),
                            attackPlan.isCloseRangeRoute(),
                            botPos,
                            tp,
                            BotPhysicsEngine.calculateMaxJumpHeight(AgentBotMovementStateRuntime.movementProfile(entry)))
                    && grindWeaponType != WeaponType.BOW && grindWeaponType != WeaponType.CROSSBOW
                    && grindWeaponType != WeaponType.WAND && grindWeaponType != WeaponType.STAFF) {
                // Target is above but within jump height — jump toward it
                BotMovementManager.initiateJump(entry, bot, tp.x - botPos.x);
                return new LocalOpportunityAttackResult(true, targetPos);
            }
        }
        // Stand still when in attack range and no retreat is needed. Walking toward the
        // mob during cooldown drops dx into the retreat band, triggering a walk-away the
        // next tick — produces a tight (~100 ms) left-right oscillation when the bot is
        // already in firing position.
        if (target != null && !AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)
                && !shouldRetreatForRangedSpacing && crossRegionRetreatPos == null
                && aoeRepositionPos == null
                && !attackAttemptedInRange
                && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, bot, target)) {
            BotPhysicsEngine.idleOnGround(entry, bot);
            BotMovementManager.broadcastMovement(entry);
            return new LocalOpportunityAttackResult(true, targetPos);
        }
        // Retreat positioning is a local combat adjustment, not an inter-region path target.
        // Feeding a synthetic same-Y retreat point into nav while the monster is elsewhere
        // can make rope/ladder bots path back onto the nearby foothold instead of toward
        // the monster's actual region.
        targetPos = crossRegionRetreatPos != null
                ? crossRegionRetreatPos
                : aoeRepositionPos != null
                ? selectGrindNavigationTarget(entry, botPos, aoeRepositionPos)
                : selectGrindNavigationTarget(entry, botPos, tp, shouldRetreatForRangedSpacing);
        // Clear only once the bot has physically left the retreat zone, not after the
        // first retreat tick — otherwise the flag resets while the bot is still overlapping
        // and allowOneDegenerateAttack re-opens the attack gate next tick.
        if (AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry)
                && !AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(grindWeaponType, botPos, tp)) {
            AgentBotDegenerateAttackStateRuntime.clear(entry);
        }
        // Small detour: take a very close loot drop on the way when not retreating.
        if (crossRegionRetreatPos == null && !shouldRetreatForRangedSpacing
                && aoeRepositionPos == null && !AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            Point lootPos = convenientLootTarget(entry, botPos, tp);
            if (lootPos != null) targetPos = lootPos;
        }
        return new LocalOpportunityAttackResult(false, targetPos);
    }

    private void handleBotTickFailure(BotEntry entry, int ownerCharId, int botCharId, Throwable t) {
        if (entry == null) {
            log.error("Bot tick failed for missing entry ownerCharId={} botCharId={}", ownerCharId, botCharId, t);
            return;
        }

        long now = System.currentTimeMillis();
        AgentTickFailurePolicy.Decision decision = AgentTickFailurePolicy.recordFailure(entry, now);
        int failureCount = decision.failureCount();

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        String botName = bot != null ? bot.getName() : "?";
        String ownerName = owner != null ? owner.getName() : "?";
        int mapId = bot != null ? bot.getMapId() : -1;

        clearBotVolatileActions(entry);
        if (decision.disableAgent()) {
            log.error("Disabling bot '{}' after {} tick failures within {} ms (owner={}, map={}, grinding={}, following={})",
                    botName, failureCount, AgentTickFailurePolicy.FAILURE_WINDOW_MS, ownerName, mapId,
                    AgentBotModeStateRuntime.grinding(entry), AgentBotModeStateRuntime.following(entry), t);
            removeBotByCharId(botCharId);
            return;
        }

        if (decision.forceIdle()) {
            forceBotIdleAfterTickFailure(entry);
        }

        log.warn("Bot '{}' tick failed {}/{} (owner={}, map={}, grinding={}, following={})",
                botName, failureCount, AgentTickFailurePolicy.FAILURE_LIMIT, ownerName, mapId,
                AgentBotModeStateRuntime.grinding(entry), AgentBotModeStateRuntime.following(entry), t);
    }

    private static void resetBotTickFailures(BotEntry entry) {
        AgentTickFailurePolicy.resetFailures(entry);
    }

    private static void clearBotVolatileActions(BotEntry entry) {
        AgentBotPendingActionStateRuntime.clearPendingAction(entry);
        AgentBotPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
        AgentBotPatrolStateRuntime.clearPatrolWanderTarget(entry);
        BotMovementManager.resetEntryStateAfterTeleport(entry);
    }

    private void forceBotIdleAfterTickFailure(BotEntry entry) {
        issueStop(entry);
        try {
            AgentBotManagerReplyRuntime.replyNow(entry, "unrecoverable error caught, idling");
        } catch (Throwable chatError) {
            Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
            log.warn("Failed to send bot failure idle message for '{}'",
                    bot != null ? bot.getName() : "?", chatError);
        }
    }

    static Monster selectPriorityRangedAttackTarget(BotEntry entry,
                                                   Character bot,
                                                   Point botPos,
                                                   Monster preferredTarget) {
        if (entry == null || AgentBotAmmoStateRuntime.noAmmo(entry) || bot == null || botPos == null) {
            return null;
        }

        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType)) {
            return null;
        }
        if (isNonDegenerateRangedAttackTarget(entry, bot, botPos, weaponType, preferredTarget)) {
            return preferredTarget;
        }

        Monster best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (Monster candidate : bot.getMap().getAllMonsters()) {
            if (candidate == preferredTarget) {
                continue;
            }
            if (!isNonDegenerateRangedAttackTarget(entry, bot, botPos, weaponType, candidate)) {
                continue;
            }
            double distanceSq = candidate.getPosition().distanceSq(botPos);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isNonDegenerateRangedAttackTarget(BotEntry entry,
                                                            Character bot,
                                                            Point botPos,
                                                            WeaponType weaponType,
                                                            Monster target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        Point targetPos = target.getPosition();
        if (AgentAttackExecutionProvider.shouldDegenerateRangedAttack(weaponType, botPos, targetPos)) {
            return false;
        }
        AgentAttackPlan plan = AgentBotCombatPlanRuntime.planAttack(entry, bot, target, AgentCombatConfig.cfg);
        return plan != null
                && plan.route == AgentAttackRoute.RANGED
                && AgentCombatRangePolicy.isTargetInAttackRange(plan, bot, target)
                && AgentCombatRangePolicy.canUseAttackPlanNow(
                        AgentBotMovementStateRuntime.grounded(entry), weaponType, plan.route);
    }

    private void tickAnchoredFarm(BotEntry entry, Character bot, Point botPos, boolean runAiTick) {
        if (!AgentBotFarmAnchorStateRuntime.isFarmAnchorInMap(entry, bot.getMapId())) {
            clearFarmAnchorOnMapChange(entry, bot);
            tickIdleEntry(entry, bot);
            return;
        }

        Point anchor = AgentBotFarmAnchorStateRuntime.farmAnchor(entry);
        if (runAiTick) {
            LocalOpportunityAttackResult attackResult = tryLocalOpportunityAttack(
                    entry, bot, botPos, anchor, anchor, false, false);
            if (attackResult.consumedTick()) {
                return;
            }
        }

        if (AgentPositionService.isNear(botPos, anchor, 8) && !AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)) {
            AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
            BotPhysicsEngine.idleOnGround(entry, bot);
            BotMovementManager.broadcastMovement(entry);
            return;
        }

        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, anchor);
        stepMovementCore(entry, anchor, runAiTick);
    }

    private static boolean shouldUseScriptedMoveLocalCombat(BotEntry entry, Point targetPos) {
        return AgentBotScriptTaskStateRuntime.isActiveLocalOpportunityMoveTo(entry, targetPos);
    }

    private LocalOpportunityAttackResult tryLocalOpportunityAttack(BotEntry entry,
                                                                  Character bot,
                                                                  Point botPos,
                                                                  Point movementTargetPos,
                                                                  Point moveWindowReferencePos,
                                                                  boolean allowCombatMovement,
                                                                  boolean allowJumpTowardTarget) {
        Point targetPos = movementTargetPos;
        if (AgentBotAmmoStateRuntime.noAmmo(entry) || bot == null || botPos == null) {
            return new LocalOpportunityAttackResult(false, targetPos);
        }

        Monster localTarget = AgentBotCombatTargetRuntime.findFollowAttackTarget(entry, bot, AgentCombatConfig.cfg);
        if (localTarget == null) {
            return new LocalOpportunityAttackResult(false, targetPos);
        }

        Point localTargetPos = localTarget.getPosition();
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean shouldRetreat = allowCombatMovement
                && (AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry)
                || AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(weaponType, botPos, localTargetPos)
                || AgentAttackExecutionProvider.isAnyMobNearerThanTarget(bot, botPos, localTargetPos));
        if (shouldRetreat) {
            AgentBotDegenerateAttackStateRuntime.clear(entry);
            return new LocalOpportunityAttackResult(
                    false, selectGrindNavigationTarget(entry, botPos, localTargetPos));
        }

        AgentAttackPlan attackPlan = AgentBotCombatPlanRuntime.planAttack(entry, bot, localTarget, AgentCombatConfig.cfg);
        if (attackPlan == null) {
            return new LocalOpportunityAttackResult(false, targetPos);
        }
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            if (AgentCombatRangePolicy.canUseAttackPlanNow(
                    AgentBotMovementStateRuntime.grounded(entry), weaponType, attackPlan.route)
                    && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, bot, localTarget)) {
                AgentBotCombatAttackRuntime.attackMonster(entry, bot, attackPlan);
                if (allowCombatMovement && attackPlan.isCloseRangeRoute()
                        && AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType)) {
                    AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);
                }
            }
            return new LocalOpportunityAttackResult(false, targetPos);
        }

        if (allowJumpTowardTarget
                && weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW
                && weaponType != WeaponType.WAND && weaponType != WeaponType.STAFF
                && AgentCombatRangePolicy.isTargetJumpable(
                        AgentBotMovementStateRuntime.movementProfile(entry),
                        true,
                        botPos,
                        localTargetPos,
                        BotPhysicsEngine.calculateMaxJumpHeight(AgentBotMovementStateRuntime.movementProfile(entry)))) {
            BotMovementManager.initiateJump(entry, bot, localTargetPos.x - botPos.x);
            return new LocalOpportunityAttackResult(true, targetPos);
        }

        if (!AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry)
                && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, bot, localTarget)) {
            AgentBotCombatAttackRuntime.attackMonster(entry, bot, attackPlan);
            setLocalAttackMoveWindow(entry, botPos, moveWindowReferencePos);
            if (allowCombatMovement && attackPlan.isCloseRangeRoute()
                    && AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType)) {
                AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);
            }
            return new LocalOpportunityAttackResult(!AgentBotMovementStateRuntime.inAir(entry), targetPos);
        }

        return new LocalOpportunityAttackResult(false, targetPos);
    }

    private static void setLocalAttackMoveWindow(BotEntry entry, Point botPos, Point referencePos) {
        if (botPos == null || referencePos == null) {
            AgentBotCombatCooldownStateRuntime.clearMoveWindow(entry);
            return;
        }
        int dx = Math.abs(botPos.x - referencePos.x);
        AgentBotCombatCooldownStateRuntime.setMoveWindowMs(entry,
                dx > BotMovementManager.cfg.FOLLOW_DIST * 3 ? 1000
                        : dx > BotMovementManager.cfg.FOLLOW_DIST ? 200
                        : 0);
        clearActionMoveWindowIfSettled(entry, botPos, referencePos);
    }

    private static void clearFollowActionMoveWindowIfSettled(BotEntry entry,
                                                             Point botPos,
                                                             AgentTargetSnapshot targetSnapshot) {
        if (entry == null || !AgentBotModeStateRuntime.following(entry) || targetSnapshot == null) {
            return;
        }
        clearActionMoveWindowIfSettled(entry, botPos, targetSnapshot.followTargetPos());
    }

    private static void clearActionMoveWindowIfSettled(BotEntry entry,
                                                       Point botPos,
                                                       Point targetPos) {
        if (entry == null || !AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry)
                || botPos == null || targetPos == null) {
            return;
        }

        int followStopBand = Math.max(BotMovementManager.cfg.STOP_DIST, BotMovementManager.cfg.FOLLOW_DIST);
        if (Math.abs(botPos.x - targetPos.x) <= followStopBand
                && Math.abs(botPos.y - targetPos.y) <= BotMovementManager.cfg.FOLLOW_Y_CAP) {
            AgentBotCombatCooldownStateRuntime.clearMoveWindow(entry);
        }
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
        boolean inactive = (owner == null) || (owner.getHp() <= 0);

        if (!inactive) {
            AgentLeaderSafetyService.handleActiveLeaderReturn(
                    entry,
                    () -> AgentBotMoveTargetStateRuntime.clearMoveTarget(entry),
                    () -> townClusterAnchors.remove(ownerCharId),
                    () -> AgentBotManagerStatusRuntime.announceOwnerReturnedFromOffline(entry));
            return false;
        }

        if (!AgentLeaderSafetyService.shouldEnterInactiveSafeMode(entry, nowMs, cfg.OWNER_INACTIVE_TOWN_RETURN_MS)) {
            return false;
        }

        return enterOwnerInactiveSafeMode(entry, bot, ownerCharId, shouldTownWarpForOwnerInactive(entry));
    }

    private boolean shouldTownWarpForOwnerInactive(BotEntry entry) {
        MapleMap currentMap = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(currentMap);
    }

    public boolean shouldOfferTownForAwayCommand(BotEntry entry) {
        return shouldTownWarpForOwnerInactive(entry);
    }

    public boolean isFirstBotEntry(BotEntry entry) {
        return AgentRuntimeRegistry.isFirstEntryForLeader(bots, entry);
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
                        target -> startMoveTo(entry, target, true)));
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
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> dest != null,
                () -> clearScriptTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> startMoveTo(entry, dest, precise));
    }

    private void startMoveTo(BotEntry entry, Point dest, boolean precise) {
        AgentModeService.startMoveTo(entry, dest, precise);
    }

    public void issueFarmHere(BotEntry entry, Point dest) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> dest != null && AgentBotRuntimeIdentityRuntime.hasBot(entry),
                () -> clearScriptTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> startFarmHere(entry, dest));
    }

    private void startFarmHere(BotEntry entry, Point dest) {
        // Sentry/farm-here is an active combat mode that just anchors to a fixed spot.
        // Route through the shared active-mode reset so it stays in lock-step with
        // grind/patrol (self-buff, pot-share, ammo-low, "low on pots" fallback all
        // gate on Agent mode state — see kb feedback_bot_coding_guidelines).
        AgentModeService.startFarmHere(entry, dest, BotMovementManager::clearNavigationState);
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
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> clearScriptTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> startFollow(entry, target));
    }

    private void startFollow(BotEntry entry, Character target) {
        AgentModeService.startFollow(entry, target);
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

    private void startGrind(BotEntry entry) {
        AgentModeService.startGrind(entry, BotMovementManager::clearNavigationState);
    }

    /** Public hook: stop all scripted movement/combat mode and idle in place. */
    public void issueStop(BotEntry entry) {
        AgentBotMovementCommandRuntime.stop(entry);
    }

    private void startStop(BotEntry entry) {
        AgentModeService.startStop(entry);
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
        AgentScriptTaskStartService.start(
                entry,
                task,
                new AgentScriptTaskStartService.StartHooks(
                        (point, precise) -> startMoveTo(entry, point, precise),
                        target -> startFollow(entry, target),
                        targetId -> resolveFollowCharacterById(entry, targetId),
                        () -> startGrind(entry),
                        () -> startStop(entry),
                        (type, itemId, quantity) -> AgentScriptItemActionService.dropItem(entry, type, itemId, quantity)));
    }

    private boolean isScriptTaskComplete(BotEntry entry, AgentTask task) {
        return AgentScriptTaskCompletionService.isComplete(
                entry, task, BotMovementManager.cfg.STOP_DIST, targetId -> resolveFollowCharacterById(entry, targetId));
    }

    private Character resolveFollowCharacterById(BotEntry entry, int targetCharacterId) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        List<BotEntry> siblingEntries = owner == null ? List.of() : getBotEntries(owner.getId());
        return AgentFollowAnchorService.resolveTarget(entry, owner, targetCharacterId, siblingEntries);
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
        // Just warped in (likely via applyTo): rebuild physics state once.
        if (groundAfterMapChange(entry, bot)) {
            return;
        }

        BotMovementManager.refreshMovementProfile(entry);
        stepMovementCore(entry, AgentBotMoveTargetStateRuntime.moveTarget(entry), runAiTick);
    }

    private boolean groundAfterMapChange(BotEntry entry, Character bot) {
        return AgentMapTransitionService.groundAfterMapChange(entry, bot, mapTransitionHooks());
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
        // Single source of truth for the subsystem sequence. Perf instrumentation is gated by
        // `perf` so the hot (monitor-disabled) path pays only cheap branch checks and never
        // allocates timing state, while the enabled path keeps every per-subsystem label.
        boolean perf = AgentPerformanceMonitor.enabled();
        long t = perf ? System.nanoTime() : 0L;
        AgentBotCombatDamageRuntime.tickMobDamage(entry, bot, AgentCombatConfig.cfg, BotMovementManager::tickDown);
        if (perf) AgentPerformanceMonitor.record("common-mob-damage", System.nanoTime() - t);
        if (bot.getHp() <= 0) {
            if (!AgentBotDeathStateRuntime.isDead(entry)) {
                AgentBotCombatDeathRuntime.enterDeadState(entry, bot, false, AgentCombatConfig.cfg);
            }
            return true;
        }
        if (perf) t = System.nanoTime();
        tickReleaseMonsterControl(bot);
        if (perf) AgentPerformanceMonitor.record("common-release-mob", System.nanoTime() - t);
        // While a trade window is open, suppress passive loot pickup. pickupItem() runs on
        // this scheduler thread and races Trade.completeTrade()'s addFromDrop on the packet
        // thread: fitsInInventory() can pass, then this fills the last slot before addFromDrop
        // runs, and the silently-ignored false return loses the partner's item.
        // See memory/kb_bot_trade_dupe_loss_audit.md.
        if (bot.getTrade() == null) {
            if (perf) t = System.nanoTime();
            BotInventoryManager.tickPassiveLoot(entry, bot);
            if (perf) AgentPerformanceMonitor.record("common-passive-loot", System.nanoTime() - t);
        }
        if (perf) t = System.nanoTime();
        AgentPotionService.tickPotionCheck(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-potion-check", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        AgentPotionService.tickPassiveRecovery(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-passive-recovery", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        AgentBuildService.checkLevelUp(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-build-levelup", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        AgentBotManagerStatusRuntime.tickAfkCheck(entry, owner);
        if (perf) AgentPerformanceMonitor.record("common-afk-check", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        BotInventoryManager.tickTrade(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-trade", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        BotInventoryManager.tickManualTrade(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-manual-trade", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        AgentPartyQuestHooks.tick(entry, bot, owner);
        if (perf) AgentPerformanceMonitor.record("common-pq-hooks", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        tickScriptTasks(entry);
        if (perf) AgentPerformanceMonitor.record("common-script-tasks", System.nanoTime() - t);
        if (AgentPartyQuestHooks.isNpcLocked(entry)) {
            return true;
        }
        if (perf) t = System.nanoTime();
        AgentBotCombatActionLockRuntime.tickActionLock(entry);
        if (perf) AgentPerformanceMonitor.record("common-action-lock", System.nanoTime() - t);
        if (runAiTick) {
            if (perf) t = System.nanoTime();
            AgentBotCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            if (perf) AgentPerformanceMonitor.record("common-skill-cache", System.nanoTime() - t);
            // Support healing is top priority — runs before buffs so that a bot below the heal
            // threshold casts Heal before a rebuff uses up this tick's action window. If it fires,
            // Agent combat cooldown state is set to the heal animation lock and tickActionLocked()
            // will return true, causing the caller to skip attack logic this tick.
            if (perf) t = System.nanoTime();
            AgentBotCombatHealRuntime.tickSupportHealing(entry, bot, AgentCombatConfig.cfg);
            if (perf) AgentPerformanceMonitor.record("common-support-heal", System.nanoTime() - t);
            if (perf) t = System.nanoTime();
            AgentBotCombatBuffRuntime.tickBuffs(entry, bot, AgentCombatConfig.cfg);
            if (perf) AgentPerformanceMonitor.record("common-combat-buffs", System.nanoTime() - t);
            if (perf) t = System.nanoTime();
            AgentBuffService.tick(entry, bot);
            if (perf) AgentPerformanceMonitor.record("common-buff-pots", System.nanoTime() - t);
        }
        return tickActionLocked(entry);
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
        if (!AgentBotModeStateRuntime.following(entry) || followAnchor == null || bot.getMapId() == followAnchor.getMapId()) {
            return false;
        }
        // Ground against the anchor's actual position in their NEW map. The previously-passed
        // followTargetPos was computed from the bot's OLD map (foothold snaps, formation offsets),
        // so it could land off-map in the new map and cause far-away/OOB spawns.
        MapleMap targetMap = followAnchor.getMap();
        Point anchorPos = followAnchor.getPosition();
        Point spawn = BotPhysicsEngine.findGroundPoint(targetMap, new Point(anchorPos.x, anchorPos.y - 1));
        if (spawn == null) {
            spawn = anchorPos;
        }
        BotPhysicsEngine.idleOnGround(entry, bot);
        bot.changeMap(targetMap, spawn);
        BotMovementManager.resetEntryState(entry);
        return true;
    }

    private boolean recoverTeleportDistance(BotEntry entry, Character bot, Point targetPos) {
        Point botPos = bot.getPosition();
        int manhattan = Math.abs(botPos.x - targetPos.x) + Math.abs(botPos.y - targetPos.y);
        if (manhattan > BotMovementManager.cfg.TELEPORT_DIST) {
            return executeRecoveryTeleport(entry, bot, targetPos);
        }
        // Out-of-bounds recovery: airborne physics has no VR-bottom hard stop, so a bot that
        // slips below the floor (or past the side walls in rare cases) keeps free-falling
        // indefinitely until the 4000 Manhattan fallback catches it. That can leave the bot
        // out of the map for several seconds with the owner still on the same screen
        // (manhattan < 4000). Trigger a tighter teleport once we can prove the bot is
        // outside the map's VR rectangle.
        Rectangle area = bot.getMap() == null ? null : bot.getMap().getMapArea();
        if (area != null && area.width > 0 && area.height > 0
                && !area.contains(botPos)
                && manhattan > BotMovementManager.cfg.OOB_TELEPORT_DIST) {
            return executeRecoveryTeleport(entry, bot, targetPos);
        }
        return false;
    }

    private boolean recoverGrindPartyTeleportDistance(BotEntry entry, Character bot, Character partyAnchor) {
        if (entry == null || bot == null || partyAnchor == null || !AgentBotModeStateRuntime.grinding(entry)
                || AgentBotShopStateRuntime.shopVisitPending(entry)) {
            return false;
        }
        if (AgentBotMoveTargetStateRuntime.hasMoveTarget(entry) || AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return false;
        }
        if (bot.getMap() == null || partyAnchor.getMap() != bot.getMap()) {
            return false;
        }

        Point botPos = bot.getPosition();
        Point anchorPos = partyAnchor.getPosition();
        if (botPos == null || anchorPos == null || !isInKnownMapBounds(bot.getMap(), anchorPos)) {
            return false;
        }

        int manhattan = Math.abs(botPos.x - anchorPos.x) + Math.abs(botPos.y - anchorPos.y);
        int multiplier = Math.max(1, cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER);
        if (manhattan > BotMovementManager.cfg.TELEPORT_DIST * multiplier) {
            return executeRecoveryTeleport(entry, bot, anchorPos);
        }

        Rectangle area = bot.getMap().getMapArea();
        if (hasKnownMapBounds(area)
                && !area.contains(botPos)
                && manhattan > BotMovementManager.cfg.OOB_TELEPORT_DIST * multiplier) {
            return executeRecoveryTeleport(entry, bot, anchorPos);
        }
        return false;
    }

    private static boolean isInKnownMapBounds(MapleMap map, Point point) {
        Rectangle area = map == null ? null : map.getMapArea();
        return !hasKnownMapBounds(area) || area.contains(point);
    }

    private static boolean hasKnownMapBounds(Rectangle area) {
        return area != null && area.width > 0 && area.height > 0;
    }

    private boolean executeRecoveryTeleport(BotEntry entry, Character bot, Point targetPos) {
        Point spawn = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(targetPos.x, targetPos.y - 1));
        if (spawn == null) {
            spawn = targetPos;
        }
        BotPhysicsEngine.teleportTo(entry, bot, spawn);
        BotMovementManager.resetEntryStateAfterTeleport(entry);
        BotMovementManager.broadcastMovement(entry);
        return true;
    }

    boolean stepMovementOnly(BotEntry entry, long tickAtMs) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return false;
        }

        boolean runAiTick = AgentTickOrchestrator.prepareTick(
                entry, BotMovementManager.cfg.TICK_MS, cfg.AI_TICK_MS, tickAtMs);

        AgentTargetSnapshot targetSnapshot = captureTargetSnapshot(entry);
        Point ownerPos = targetSnapshot.rawOwnerPos();
        updateObservedOwnerMotion(entry, ownerPos);
        AgentBotOwnerMotionStateRuntime.rememberOwnerPosition(entry, ownerPos);
        stepMovementOnly(entry, targetSnapshot.primaryTargetPos(), ownerPos, runAiTick);
        return runAiTick;
    }

    void stepMovementOnly(BotEntry entry,
                          Point targetPos,
                          Point ownerPos,
                          boolean runAiTick) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry) || targetPos == null) {
            return;
        }

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);

        if (tickIdleEntry(entry, bot)) {
            return;
        }

        if (owner != null && !AgentBotShopStateRuntime.shopVisitPending(entry) && syncFollowMap(entry, bot, owner)) {
            return;
        }
        Character followAnchor = resolveFollowAnchor(entry, owner);
        if (recoverGrindPartyTeleportDistance(entry, bot, followAnchor)) {
            return;
        }
        if (recoverTeleportDistance(entry, bot, targetPos)) {
            return;
        }

        if (!AgentBotMapStateRuntime.isTrackingMap(entry, bot.getMapId())) {
            AgentBotMapStateRuntime.setMapTracking(entry, bot.getMapId(), BotMovementManager.buildFhIndex(bot.getMap()));
            Point cur = bot.getPosition();
            Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
            BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
            BotMovementManager.resetEntryStateAfterTeleport(entry);
            BotMovementManager.broadcastMovement(entry);
            AgentShopService.onMapChange(entry, bot);
            AgentBotManagerStatusRuntime.checkManagerStatus(entry, bot);
            return;
        }

        // Shop visit: navigate to approach point before resuming normal flow.
        if (AgentBotShopStateRuntime.shopVisitPending(entry)) {
            boolean consumed = AgentShopService.tickShopVisit(entry, bot);
            targetPos = AgentBotShopStateRuntime.activeShopTargetPosition(entry);
            if (!consumed && AgentBotShopStateRuntime.shopApproachDelayMs(entry) > 0) {
                return;
            }
            if (targetPos != null) {
                stepMovementCore(entry, targetPos, runAiTick);
            }
            return;
        }

        if (tryFollowIdleMovementFastPath(entry, bot, targetPos, AgentBotTickStateRuntime.lastTickAtMs(entry))) {
            return;
        }

        stepMovementCore(entry, targetPos, runAiTick);
    }

    static boolean tryFollowIdleMovementFastPath(BotEntry entry, Character bot, Point targetPos, long nowMs) {
        if (!isFollowIdleMovementFastPathEligible(entry, bot, targetPos)) {
            return false;
        }

        if (AgentBotTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry) == 0L) {
            AgentBotTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, nowMs + 1000L);
        } else if (nowMs >= AgentBotTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry)) {
            AgentBotTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, nowMs + 1000L);
            return false;
        }

        AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "idle-fast");
        AgentBotMovementStuckStateRuntime.resetStuckProgress(entry);
        return true;
    }

    private static boolean isFollowIdleMovementFastPathEligible(BotEntry entry, Character bot, Point targetPos) {
        if (entry == null || bot == null || targetPos == null) {
            return false;
        }
        if (!AgentBotModeStateRuntime.following(entry) || AgentBotModeStateRuntime.grinding(entry) || AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return false;
        }
        if (AgentBotMovementStateRuntime.inAir(entry) || AgentBotMovementStateRuntime.climbing(entry) || AgentBotMovementStateRuntime.downJumpPending(entry) || AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry)) {
            return false;
        }
        if (AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry)
                || AgentBotFidgetRuntime.hasActiveFidgetMode(entry)) {
            return false;
        }
        if (AgentBotShopStateRuntime.hasActiveShopTransition(entry)) {
            return false;
        }
        if (AgentBotMovementStateRuntime.wasMovingX(entry) || AgentBotMovementStateRuntime.hasMoveDirection(entry)
                || AgentBotMovementStateRuntime.hasMovementVelocity(entry)) {
            return false;
        }
        if (AgentBotOwnerMotionStateRuntime.observedOwnerMoved(entry)) {
            return false;
        }

        Point botPos = bot.getPosition();
        return Math.abs(targetPos.x - botPos.x) <= BotMovementManager.cfg.FOLLOW_DIST
                && Math.abs(targetPos.y - botPos.y) <= BotMovementManager.cfg.STOP_DIST;
    }

    private void stepMovementCore(BotEntry entry,
                                  Point targetPos,
                                  boolean runAiTick) {
        BotNavigationManager.NavigationDirective navDirective = BotNavigationManager.resolveTarget(entry, targetPos, runAiTick);
        if (navDirective.consumedTick) {
            return;
        }

        Point steeringTarget = navDirective.targetPos;
        if (AgentBotMoveTargetStateRuntime.isPrecise(entry)
                && !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            AgentBotNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);
        }
        if (AgentFidgetService.tryHandleTick(entry, steeringTarget, runAiTick)) {
            return;
        }

        tickMovementPhase(entry, steeringTarget, runAiTick);
        if (runAiTick && !AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)) {
            BotNavigationManager.tryExecuteCommittedEdgeAfterGroundMovement(entry, targetPos);
        }
        tickStuckDetection(entry);
        clearReachedMoveTarget(entry);
    }

    private void tickMovementPhase(BotEntry entry, Point targetPos, boolean runAiTick) {
        if (AgentBotMovementStateRuntime.climbing(entry)) {
            BotMovementManager.tickClimbing(entry, targetPos, runAiTick);
        } else if (AgentMapEnvironmentService.isSwimMap(entry) && AgentBotMovementStateRuntime.inAir(entry)) {
            BotMovementManager.tickSwimming(entry, targetPos);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            BotMovementManager.tickAirborne(entry, targetPos);
        } else {
            BotMovementManager.tickGrounded(entry, targetPos);
        }
    }

    private void clearReachedMoveTarget(BotEntry entry) {
        if (!AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return;
        }
        Point botPos = AgentBotRuntimeIdentityRuntime.botPosition(entry);
        if (AgentBotMoveTargetStateRuntime.hasReachedMoveTarget(entry, botPos, BotMovementManager.cfg.STOP_DIST)) {
            AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        }
    }

    private static void updateObservedOwnerMotion(BotEntry entry, Point ownerPos) {
        if (entry == null || ownerPos == null) {
            return;
        }
        AgentBotOwnerMotionStateRuntime.updateObservedOwnerStep(entry, ownerPos);
    }

    private static void clearFarmAnchorOnMapChange(BotEntry entry, Character bot) {
        if (entry == null || bot == null || !AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return;
        }
        if (AgentBotFarmAnchorStateRuntime.clearFarmAnchorIfMapChanged(entry, bot.getMapId())) {
            if (AgentBotMoveTargetStateRuntime.isPrecise(entry)) {
                AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
            }
        }
    }

    private static void clearPatrolOnMapChange(BotEntry entry, Character bot) {
        if (entry == null || bot == null || !AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            return;
        }
        AgentBotPatrolStateRuntime.clearPatrolIfMapChanged(entry, bot.getMapId());
    }

    private static void tickStuckDetection(BotEntry entry) {
        if (!AgentPerformanceMonitor.enabled()) {
            doStuckDetection(entry);
            return;
        }

        long startedAt = System.nanoTime();
        try {
            doStuckDetection(entry);
        } finally {
            AgentPerformanceMonitor.record("stuck-detect", System.nanoTime() - startedAt);
        }
    }

    private static void doStuckDetection(BotEntry entry) {
        AgentBotMovementStuckStateRuntime.setUnstuckCooldownMs(
                entry,
                BotMovementManager.tickDown(AgentBotMovementStuckStateRuntime.unstuckCooldownMs(entry)));

        // Only detect/act while actively navigating — idling near owner is not stuck.
        if (AgentBotMovementStateRuntime.inAir(entry) || AgentBotMovementStateRuntime.climbing(entry)
                || AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry)
                || (!AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                        && !AgentBotMoveTargetStateRuntime.hasMoveTarget(entry))) {
            AgentBotMovementStuckStateRuntime.resetStuckProgress(entry);
            return;
        }

        Point botPos = AgentBotRuntimeIdentityRuntime.botPosition(entry);
        if (!AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry)) {
            AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, botPos);
            return;
        }

        boolean moved = AgentBotMovementStuckStateRuntime.movedSinceStuckCheck(entry, botPos, 8);
        if (moved) {
            AgentBotMovementStuckStateRuntime.resetStuckMs(entry);
            AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, botPos);
        } else {
            AgentBotMovementStuckStateRuntime.addStuckMs(entry, BotPhysicsEngine.cfg.TICK_MS);
        }

        if (cfg.ENABLE_UNSTUCK
                && AgentBotMovementStuckStateRuntime.stuckForAtLeast(entry, 500)
                && !AgentBotMovementStuckStateRuntime.hasUnstuckCooldown(entry)) {
            AgentBotMovementStuckStateRuntime.resetStuckProgress(entry);
            BotMovementManager.tickUnstuck(entry);
        }
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
        Character owner = Server.getInstance()
                .getWorld(world)
                .getPlayerStorage()
                .getCharacterById(ownerCharId);
        if (owner == null) return; // owner logged off — skip

        try {
            MapleMap map = owner.getMap();
            Point pos = resolveSpawnPosition(map, owner.getPosition());
            Character botChar = loadOfflineBot(charId, world, channel, map, pos);

            registerSpawnedBot(ownerCharId, owner, botChar);
            AgentBotManagerSchedulerRuntime.afterDelay(randMs(900, 1100), () -> {
                botSay(botChar, "back!!");
                botChar.changeFaceExpression(AgentEmote.HAPPY.getValue());
            });
        } catch (SQLException e) {
            log.warn("reloginBot: failed to reload charId={}", charId, e);
        }
    }

    private void respawnBot(BotEntry entry, Character bot, Character owner) {
        AgentBotDeathStateRuntime.clear(entry);
        bot.updateHp(bot.getMaxHp());

        if (bot.getMapId() != owner.getMapId()) {
            bot.forceChangeMap(owner.getMap(), owner.getMap().findClosestPortal(owner.getPosition()));
        }
        Point ownerPos = owner.getPosition();
        Point spawnPos = bot.getMap().getPointBelow(new Point(ownerPos.x, ownerPos.y - 1));
        BotPhysicsEngine.teleportTo(entry, bot, spawnPos != null ? spawnPos : ownerPos);
        BotMovementManager.resetEntryStateAfterTeleport(entry);
        BotMovementManager.broadcastMovement(entry);
        botSay(bot, "back!");
        bot.changeFaceExpression(AgentEmote.GLARE.getValue());
    }

    // -------------------------------------------------------------------------
    // Monster control hand-off
    // -------------------------------------------------------------------------

    /**
     * Bots can't drive mob AI (BotClient.sendPacket is a no-op), so any monster
     * assigned to a bot as controller would freeze. Release it from the bot and
     * let the upstream controller-selection SSOT ({@link Monster#aggroUpdateController})
     * pick an eligible real player, or leave it released if none is eligible.
     * Bots and hidden GMs are excluded by that SSOT, so neither is ever handed control.
     */
    private static void tickReleaseMonsterControl(Character bot) {
        java.util.Collection<Monster> controlled = bot.getControlledMonsters();
        if (controlled.isEmpty()) return;

        for (Monster monster : controlled) {
            monster.aggroRedirectController();
        }
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
        if (owner == null || target == null || message == null) {
            return;
        }
        if (!(target.getClient() instanceof BotClient)) {
            return;
        }
        BotEntry entry = getBotEntry(owner.getId(), target.getId());
        if (entry == null) {
            return;
        }
        AgentBotReplyChannelStateRuntime.setWhisper(entry);
        handleAgentChat(entry, message);
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

