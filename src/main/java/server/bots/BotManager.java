package server.bots;

import server.agents.capabilities.combat.AgentAttackRoute;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentCombatScoringPolicy;
import server.agents.capabilities.combat.AgentProjectileHitbox;

import server.agents.capabilities.dialogue.AgentEmote;

import server.agents.runtime.AgentPerformanceMonitor;

import server.agents.capabilities.looting.AgentLootEligibility;


import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotAoeRepositionStateRuntime;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotBreakoutStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotCombatSkillCacheStateRuntime;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotFidgetRuntime;
import server.agents.integration.AgentBotFormationStateRuntime;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindSearchStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotGrindWanderStateRuntime;
import server.agents.integration.AgentBotLeaderStateRuntime;
import server.agents.integration.AgentBotMapStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
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
import server.agents.integration.AgentBotTickFailureStateRuntime;
import server.agents.integration.AgentBotTargetedCommandMatch;
import server.agents.integration.AgentBotTransferCommand;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.commands.AgentReplyChannel;
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
import client.inventory.manipulator.InventoryManipulator;
import client.keybind.KeyBinding;
import constants.game.CharacterStance;
import constants.inventory.ItemConstants;
import net.server.Server;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.PartyOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.StatEffect;
import server.TimerManager;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.bots.pq.BotPqHooks;
import server.life.Monster;
import server.life.MobSkill;
import server.maps.Foothold;
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

    /**
     * All tunable constants in one place. Fields are non-final so the class can
     * be hotswapped in debug mode without the JVM inlining the values.
     */
    public static class Config {
        public int   AI_TICK_MS       = 100;   // ms between heavier bot decision passes

        // Passive loot
        public int   LOOT_RADIUS         = 100;   // px; pickup items within this box radius
        public int   INV_FULL_WARN_CD_MS = 10_000;

        // Potion management
        public int   POT_LOW_WARN          = 100;   // warn on grind start below this count
        public int   POT_STOP              = 10;    // stop grinding below this HP pot count
        public int   POT_CHECK_INTERVAL_MS = 45_000;
        public int   POT_CHECK_RETRY_SOON_MS = 250;
        public int   MP_RECOVERY_INTERVAL_MS = 10_000;
        public int   BASE_HP_RECOVERY = 10;
        public int   BASE_MP_RECOVERY = 3;
        public float AUTOPOT_HP_THRESH = 0.7f; // use HP pot when HP falls below this ratio
        public float AUTOPOT_MP_THRESH = 0.5f; // use MP pot when MP falls below this ratio

        // Follow stagger: each bot is offset this many px from the owner (index-based, alternating left/right)
        public int FOLLOW_STAGGER = 60;

        // Owner inactivity (offline or dead) before bot scrolls/warps to nearest town and idles.
        public long OWNER_INACTIVE_TOWN_RETURN_MS = 5L * 60_000L;

        // Grind recovery is looser than follow recovery so bots can work nearby platforms,
        // but still get pulled back to a same-map party anchor if they fall far out of bounds.
        public int GRIND_PARTY_TELEPORT_DIST_MULTIPLIER = 2;

        // Grind loot convenience: loot competes with mob navigation only when
        // lootDistSq < mobDistSq * ratio. 0.09 ≈ loot within 30% of mob distance.
        public float GRIND_LOOT_CONVENIENCE_RATIO = 0.09f;
        public int   GRIND_LOOT_RETRY_SUPPRESS_MS = 5_000;

        // Debug aid: keep stuck detection/logging active, but disable automatic recovery jumps
        // so pathing failures remain visible in logs and at runtime.
        public boolean ENABLE_UNSTUCK = false;

    }

    /** Singleton config — replace with `cfg = new Config()` after hotswapping to reset. */
    public static Config cfg = new Config();

    public static BotManager getInstance() { return instance; }

    // Public facade for the !botcfg GM command (BotCombatManager is package-private).
    public static List<String> botCombatConfigLines() { return AgentCombatConfig.configFieldLines(); }
    public static String botCombatConfigLine(String name) { return AgentCombatConfig.configFieldLine(name); }
    public static String setBotCombatConfig(String name, String value) { return AgentCombatConfig.setConfigField(name, value); }

    // ownerCharId → list of owned bot entries (1:N)
    private final Map<Integer, List<BotEntry>> bots = new ConcurrentHashMap<>();
    // ownerCharId → current formation (in-memory only, defaults to stagger)
    private final Map<Integer, FormationState> ownerFormations = new ConcurrentHashMap<>();
    // ownerCharId → cluster-anchor town position. First bot to warp picks a random
    // portal in the return map; later bots warp to a randomized nearby offset.
    // Cleared when the owner becomes active again.
    private final Map<Integer, Point> townClusterAnchors = new ConcurrentHashMap<>();
    enum FormationType { STAGGER, RANDOM, STACK, SPREAD, LEFT, RIGHT }

    record FormationState(FormationType type, int px, int snapRange) {
        static FormationState defaultStagger() { return new FormationState(FormationType.STAGGER, cfg.FOLLOW_STAGGER, BotMovementManager.cfg.FOLLOW_Y_CAP); }
        int offsetFor(int idx, int total) {
            return switch (type) {
                case STAGGER -> (idx % 2 == 0 ? 1 : -1) * (idx / 2 + 1) * px;
                // range scales with total so avg spread matches stagger: ±(px/2 * total)
                case RANDOM  -> { int range = px * total / 2; yield range > 0 ? ThreadLocalRandom.current().nextInt(-range, range + 1) : 0; }
                case STACK   -> 0;
                // idx 0 = owner, then alternating ±: 0, +px, -px, +2px, -2px …
                case SPREAD  -> idx == 0 ? 0 : (idx % 2 == 1 ? 1 : -1) * ((idx + 1) / 2) * px;
                case LEFT    -> -(idx + 1) * px;
                case RIGHT   ->  (idx + 1) * px;
            };
        }
    }

    record TargetSnapshot(FormationState formation,
                          Point rawOwnerPos,
                          Point followAnchorPos,
                          String followAnchorName,
                          Point followBasePos,
                          Point followTargetPos,
                          Point moveTargetPos,
                          Point farmAnchorPos,
                          Point grindTargetPos,
                          Point primaryTargetPos,
                          String primaryTargetSource) {
        Point steeringTargetPos(BotEntry entry) {
            Point navTargetPos = AgentBotNavigationDebugStateRuntime.navTargetPosition(entry);
            return navTargetPos != null ? navTargetPos : new Point(primaryTargetPos);
        }

        String steeringTargetSource(BotEntry entry) {
            return AgentBotNavigationDebugStateRuntime.hasNavTargetPosition(entry) ? "nav-waypoint" : primaryTargetSource;
        }
    }

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
    private static final int BOT_TICK_FAILURE_LIMIT = 3;
    private static final long BOT_TICK_FAILURE_WINDOW_MS = 10_000L;

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
                BotPotionManager.checkPotShareOnModeStart(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
                issueFollow(entry, target);
            });
        }
        return true;
    }

    static String randomReply(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** Uniform random delay in [lo, hi) ms — use wherever a fixed delay would feel robotic. */
    static long randMs(int lo, int hi) {
        return lo + ThreadLocalRandom.current().nextInt(hi - lo);
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
        BotOwnershipService ownershipService = BotOwnershipService.getInstance();
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
        net.server.world.Party botParty = bot.getParty();
        if (botParty != null) {
            net.server.world.Party ownerParty = owner.getParty();
            if (ownerParty != null && botParty.getId() == ownerParty.getId()) {
                // Ensure the party member entry is marked online with a live character reference
                PartyCharacter pchar = new PartyCharacter(bot);
                pchar.setChannel(bot.getClient().getChannel());
                pchar.setMapId(bot.getMapId());
                bot.getWorldServer().updateParty(ownerParty.getId(), PartyOperation.LOG_ONOFF, pchar);
                bot.updatePartyMemberHP();
                return;
            }
            // Bot is in a different party — leave it first
            Party.leaveParty(botParty, bot.getClient());
        }
        net.server.world.Party ownerParty = owner.getParty();
        if (ownerParty == null) {
            if (!Party.createParty(owner, true)) return;
            ownerParty = owner.getParty();
        }
        if (ownerParty == null) return;
        if (Party.joinParty(bot, ownerParty.getId(), true)) {
            bot.updatePartyMemberHP();
        }
    }

    private BotEntry getBotEntry(int ownerCharId, int botCharId) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return null;
        for (BotEntry e : entries) {
            if (AgentBotRuntimeIdentityRuntime.botIs(e, botCharId)) return e;
        }
        return null;
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
            BotNavigationGraphProvider.warmGraphAsync(spawnMap, AgentBotMovementStateRuntime.movementProfile(entry));
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
        if (map == null || desiredPosition == null) {
            return desiredPosition;
        }

        Point groundPoint = BotPhysicsEngine.findGroundPoint(map, new Point(desiredPosition.x, desiredPosition.y - 1));
        return groundPoint != null ? groundPoint : desiredPosition;
    }

    private BotEntry registerBotInternal(int ownerCharId, Character owner, Character bot, boolean normalizeSpawnState) {
        List<BotEntry> entries = bots.computeIfAbsent(ownerCharId, k -> new CopyOnWriteArrayList<>());
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
        BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        entries.add(entry);
        FormationState fs = ownerFormations.getOrDefault(ownerCharId, FormationState.defaultStagger());
        for (int i = 0; i < entries.size(); i++) {
            AgentBotFormationStateRuntime.setFollowOffsetX(entries.get(i), fs.offsetFor(i, entries.size()));
        }
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
            joinBotToOwnerParty(owner, bot);
        }
    }

    public void removeBot(int ownerCharId) {
        List<BotEntry> entries = bots.remove(ownerCharId);
        if (entries != null) {
            entries.forEach(this::cancelBotTask);
        }
        ownerFormations.remove(ownerCharId);
        townClusterAnchors.remove(ownerCharId);
    }

    /** Cancel and remove a bot by the bot character's own ID (used during shutdown/disconnect). */
    public boolean removeBotByCharId(int botCharId) {
        boolean removed = false;
        for (Map.Entry<Integer, List<BotEntry>> ownerEntry : bots.entrySet()) {
            List<BotEntry> entries = ownerEntry.getValue();
            boolean removedFromOwner = entries.removeIf(e -> {
                if (AgentBotRuntimeIdentityRuntime.botIs(e, botCharId)) {
                    cancelBotTask(e);
                    return true;
                }
                return false;
            });
            if (removedFromOwner) {
                removed = true;
                if (entries.isEmpty() && bots.remove(ownerEntry.getKey(), entries)) {
                    ownerFormations.remove(ownerEntry.getKey());
                    townClusterAnchors.remove(ownerEntry.getKey());
                }
            }
        }
        return removed;
    }

    /** Release bot-owned runtime state before this character leaves bot control. */
    public boolean cleanupBotRuntimeState(Character bot) {
        if (bot == null) {
            return false;
        }

        boolean removed = removeBotByCharId(bot.getId());
        clearBotOnlyAutopotState(bot);
        return removed;
    }

    private void cancelBotTask(BotEntry entry) {
        if (AgentBotManagerSchedulerRuntime.hasScheduledTask(entry)) {
            AgentBotManagerSchedulerRuntime.cancelScheduledTask(entry);
        }
    }

    private static void clearBotOnlyAutopotState(Character bot) {
        bot.setAutopotHpAlert(0f);
        bot.setAutopotMpAlert(0f);
        normalizeAutopotKey(bot, 91);
        normalizeAutopotKey(bot, 92);
    }

    private static void normalizeAutopotKey(Character bot, int key) {
        KeyBinding binding = bot.getKeymap().get(key);
        if (binding != null && binding.getType() != 7 && binding.getAction() > 0) {
            bot.changeKeybinding(key, new KeyBinding(7, binding.getAction()));
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
                BotOwnershipService.getInstance().ensureCanControl(
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
                BotOwnershipService.getInstance().ensureCanControl(
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
        for (List<BotEntry> entries : bots.values()) {
            for (BotEntry entry : entries) {
                if (AgentBotRuntimeIdentityRuntime.botIs(entry, botCharId)) {
                    return AgentBotRuntimeIdentityRuntime.owner(entry);
                }
            }
        }
        return null;
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
        List<BotEntry> entries = bots.get(ownerCharId);
        return (entries != null && !entries.isEmpty()) ? AgentBotRuntimeIdentityRuntime.bot(entries.get(0)) : null;
    }

    BotEntry getFirstBotEntry(int ownerCharId) {
        List<BotEntry> entries = bots.get(ownerCharId);
        return (entries != null && !entries.isEmpty()) ? entries.get(0) : null;
    }

    List<BotEntry> getBotEntries(int ownerCharId) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries);
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
                BotOfferManager.notifyOwnerGainedEquip(entry, AgentBotRuntimeIdentityRuntime.bot(entry), item);
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
                BotScrollReactionManager.handleScrollEvent(source, result, scrollItemId, bots.values()));
    }

    BotEntry getBotEntry(int ownerCharId, String botName) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null || botName == null) {
            return null;
        }

        for (BotEntry entry : entries) {
            if (AgentBotRuntimeIdentityRuntime.botNameEquals(entry, botName)) {
                return entry;
            }
        }
        return null;
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
        return randomReply(List.of(
                "?",
                "got something for me?",
                "what you got?",
                "trade?",
                "show me",
                "lets see",
                "whatcha got",
                "tryna trade?",
                "yes?",
                "sup",
                "ooh what is it",
                "what's up"));
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

        AgentBotTransferCommand transferCommand = BotCommandParser.matchBotTransferCommand(message);
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
            FormationState current = ownerFormations.getOrDefault(owner.getId(), FormationState.defaultStagger());
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
                FormationState fs = new FormationState(current.type(), current.px(), newSnapRange);
                ownerFormations.put(owner.getId(), fs);
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
            FormationType type;
            int px = defaultPx;
            switch (typeStr.toLowerCase()) {
                case "tight"          -> { type = FormationType.STAGGER; px = 30; }
                case "loose"          -> { type = FormationType.STAGGER; px = 120; }
                case "stack"          -> { type = FormationType.STACK;   px = 0; }
                case "spread"         -> { type = FormationType.SPREAD;  px = defaultPx; }
                case "left"           -> { type = FormationType.LEFT;    px = defaultPx; }
                case "right"          -> { type = FormationType.RIGHT;   px = defaultPx; }
                case "random"         -> { type = FormationType.RANDOM;  px = defaultPx; }
                case "split","stagger"-> { type = FormationType.STAGGER; px = defaultPx; }
                default               -> { type = FormationType.STAGGER; px = defaultPx; }
            }
            FormationState fs = new FormationState(type, px, current.snapRange());
            ownerFormations.put(owner.getId(), fs);
            if (fEntries != null) {
                for (int i = 0; i < fEntries.size(); i++) {
                    AgentBotFormationStateRuntime.setFollowOffsetX(fEntries.get(i), fs.offsetFor(i, fEntries.size()));
                }
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
        AgentBotTargetedCommandMatch targetedBot = BotCommandParser.resolveTargetedBot(entries, message);
        if (targetedBot.entry() != null) {
            String followTargetToken = AgentChatCommandClassifier.matchFollowTarget(targetedBot.commandText());
            if (followTargetToken != null) {
                applyFollowTargetCommand(owner, List.of(targetedBot.entry()), followTargetToken);
                return;
            }
            AgentBotReplyChannelStateRuntime.setReplyChannel(targetedBot.entry(), channel);
            String cmd = targetedBot.commandText();
            if (server.bots.llm.BotLlmConfig.typoSuggesterEnabled) {
                String typo = server.bots.llm.CommandTypoSuggester.suggest(cmd);
                if (typo != null) {
                    AgentBotManagerReplyRuntime.queueReply(targetedBot.entry(), "did you mean '" + typo + "'?");
                    return;
                }
            }
            BotChatManager.handleChat(targetedBot.entry(), cmd);
            boolean matched = BotChatManager.wasLastChatHandled();
            if (matched && targetedBot.entry().getOwner() != null
                    && owner.getId() == targetedBot.entry().getOwner().getId()) {
                AgentBotActivityStateRuntime.recordLastOwnerCommand(
                        targetedBot.entry(), cmd, System.currentTimeMillis());
            }
            // Fall through to LLM only if no command pattern matched.
            if (server.bots.llm.BotLlmConfig.enabled && !matched) {
                server.bots.llm.BotLlmReplyManager.maybeRespond(targetedBot.entry(), owner, cmd);
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
                BotChatManager.handleChat(responder, message);
            }
            return;
        }

        // No name prefix — typo-suggest once via the first bot, otherwise broadcast.
        if (server.bots.llm.BotLlmConfig.typoSuggesterEnabled) {
            String typo = server.bots.llm.CommandTypoSuggester.suggest(message);
            if (typo != null) {
                BotEntry first = entries.get(0);
                AgentBotReplyChannelStateRuntime.setReplyChannel(first, channel);
                AgentBotManagerReplyRuntime.queueReply(first, "did you mean '" + typo + "'?");
                return;
            }
        }
        for (BotEntry entry : entries) {
            AgentBotReplyChannelStateRuntime.setReplyChannel(entry, channel);
            BotChatManager.handleChat(entry, message);
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
                BotOfferManager.expirePendingOffer(entry);
                if (!isPendingLootOfferTarget(entry, speaker)) {
                    continue;
                }

                matches.add(entry);
            }
        }

        AgentBotTargetedCommandMatch targetedBot = BotCommandParser.resolveTargetedBot(matches, message);
        if (targetedBot.entry() != null) {
            return BotOfferManager.handlePendingOfferResponse(targetedBot.entry(), speaker, targetedBot.commandText());
        }
        if (targetedBot.feedbackMessage() != null) {
            speaker.dropMessage(5, targetedBot.feedbackMessage());
            return true;
        }

        if (matches.size() == 1) {
            return BotOfferManager.handlePendingOfferResponse(matches.get(0), speaker, message);
        }
        if (matches.size() > 1 && looksLikeConfirmation(message)) {
            speaker.dropMessage(5, "More than one bot is waiting on you. Say '<botname> yes' or '<slot> yes'.");
            return true;
        }

        return false;
    }

    private boolean isPendingLootOfferTarget(BotEntry entry, Character speaker) {
        return entry != null
                && BotOfferManager.hasPendingOffer(entry)
                && AgentBotOfferStateRuntime.pendingOfferRecipientIs(entry, speaker)
                && AgentBotRuntimeIdentityRuntime.botMapId(entry) == speaker.getMapId();
    }

    private boolean looksLikeConfirmation(String message) {
        String normalized = message.trim().toLowerCase();
        return normalized.matches(".*\\b(yes|yep|yeah|yea|y|ok|sure|confirm|no|nope|nah|nvm|never\\s*mind|dont|don't|not\\s+now|skip)\\b.*");
    }

    // -------------------------------------------------------------------------
    /**
     * Resolve the follow target by sweeping for a real platform at followBase.x within
     * snapRange pixels of ownerPos.y. If a platform exists there, the bot may stand on
     * a platform different from the owner's (formation spread). If no platform is found
     * within range, or snap is disabled, fall back to the owner's foothold with X clamped
     * so the bot never targets a position that isn't on a real standing surface.
     * If the owner is on a rope, target the rope region instead of searching for ground platforms.
     */
    private static Point resolveFollowTargetPos(Point followBase,
                                                Character owner,
                                                Point ownerPos,
                                                int snapRange,
                                                MapleMap map) {
        if (owner != null && CharacterStance.isClimbing(owner.getStance()) && map != null) {
            return clampedOnOwnerRegion(followBase.x, owner, ownerPos, map);
        }

        if (snapRange > 0 && map != null) {
            Point below = BotPhysicsEngine.findGroundPoint(map, followBase);
            Point above = BotPhysicsEngine.findGroundPoint(map, new Point(followBase.x, ownerPos.y - snapRange));
            boolean belowOk = below != null && Math.abs(below.y - ownerPos.y) <= snapRange;
            boolean aboveOk = above != null && Math.abs(above.y - ownerPos.y) <= snapRange;
            if (belowOk || aboveOk) {
                if (!belowOk) return above;
                if (!aboveOk) return below;
                return Math.abs(below.y - ownerPos.y) <= Math.abs(above.y - ownerPos.y) ? below : above;
            }
        }
        // Swim maps: when owner is themselves swimming (mid-water) and no
        // platform is within snapRange, target the raw owner position so the
        // bot swims up to mid-water. If owner is grounded on a platform but
        // we couldn't snap-match it (e.g. very tall stack), keep the normal
        // clampedOnOwnerRegion behaviour — bot should land on a real floor,
        // not hover in water under a grounded owner.
        if (map != null && map.isSwim()
                && owner != null && CharacterStance.isSwimming(owner.getStance())) {
            return new Point(followBase.x, ownerPos.y);
        }
        return clampedOnOwnerRegion(followBase.x, owner, ownerPos, map);
    }

    /**
     * Clamps targetX to the owner's current walk region and returns a real standing point.
     * Falls back to the owner's foothold segment if the region cannot be resolved.
     * For rope targets, finds the nearest rope to the formation target position.
     */
    private static Point clampedOnOwnerRegion(int targetX, Character owner, Point ownerPos, MapleMap map) {
        if (map != null) {
            BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map);
            if (graph != null) {
                int ownerRegionId = owner != null
                        ? BotNavigationManager.resolveCharacterRegionId(graph, map, owner)
                        : graph.findRegionId(map, ownerPos);
                BotNavigationGraph.Region ownerRegion = graph.getRegion(ownerRegionId);
                if (ownerRegion != null) {
                    if (ownerRegion.isRopeRegion) {
                        // Find nearest rope at the post-formation offset position.
                        // If no rope is nearby, fall back to the owner's own rope so
                        // the bot still climbs up to follow rather than standing
                        // on the platform below.
                        BotNavigationGraph.Region nearestRope = findNearestRopeAtY(graph, targetX, ownerPos.y);
                        if (nearestRope == null) {
                            nearestRope = ownerRegion;
                        }
                        return new Point(nearestRope.minX, ownerPos.y);
                    } else {
                        // Inset by a small margin so the bot doesn't aim at the
                        // exact platform edge — owner's hit-region extends past
                        // the foothold endpoints, but the bot needs a real
                        // standing surface under its feet, and the integrator
                        // routinely overshoots edges by 1-2 px in swim maps.
                        int edgeMargin = PLATFORM_EDGE_INSET_PX;
                        int minX = ownerRegion.minX;
                        int maxX = ownerRegion.maxX;
                        if (maxX - minX > 2 * edgeMargin) {
                            minX += edgeMargin;
                            maxX -= edgeMargin;
                        }
                        int clampedX = Math.max(minX, Math.min(maxX, targetX));
                        return ownerRegion.pointAt(clampedX);
                    }
                }
            }
        }

        Foothold ownerFh = BotPhysicsEngine.findGroundFoothold(map, ownerPos);
        if (ownerFh != null) {
            int x1 = Math.min(ownerFh.getX1(), ownerFh.getX2());
            int x2 = Math.max(ownerFh.getX1(), ownerFh.getX2());
            targetX = Math.max(x1, Math.min(x2, targetX));
        }
        Point fallback = map == null ? null : BotPhysicsEngine.findGroundPoint(map, new Point(targetX, ownerPos.y));
        return fallback != null ? fallback : new Point(targetX, ownerPos.y);
    }

    /**
     * Finds the rope region nearest to the target position (targetX, targetY).
     * Returns null if no rope region is found within reasonable distance.
     */
    private static BotNavigationGraph.Region findNearestRopeAtY(BotNavigationGraph graph, int targetX, int targetY) {
        BotNavigationGraph.Region nearestRope = null;
        int nearestDistance = Integer.MAX_VALUE;
        int maxDistance = 400;

        for (BotNavigationGraph.Region region : graph.regions) {
            if (region.isRopeRegion) {
                if (region.minY > targetY || region.maxY < targetY) {
                    continue;
                }
                int ropeX = region.minX;
                int distance = Math.abs(ropeX - targetX);
                if (distance < nearestDistance && distance <= maxDistance) {
                    nearestDistance = distance;
                    nearestRope = region;
                }
            }
        }

        return nearestRope;
    }

    FormationState formationStateFor(BotEntry entry) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return FormationState.defaultStagger();
        }
        return ownerFormations.getOrDefault(owner.getId(), FormationState.defaultStagger());
    }

    Character resolveFollowAnchor(BotEntry entry, Character owner) {
        if (owner == null) {
            return null;
        }

        int targetId = AgentBotModeStateRuntime.followTargetId(entry);
        if (targetId <= 0 || targetId == owner.getId() || targetId == AgentBotRuntimeIdentityRuntime.botId(entry)) {
            return owner;
        }

        if (owner.getParty() != null) {
            for (Character member : owner.getPartyMembersOnline()) {
                if (member != null && member.getId() == targetId && member.isLoggedinWorld()) {
                    return member;
                }
            }
        }

        for (BotEntry sibling : getBotEntries(owner.getId())) {
            Character siblingBot = AgentBotRuntimeIdentityRuntime.bot(sibling);
            if (siblingBot != null && siblingBot.getId() == targetId && siblingBot.isLoggedinWorld()) {
                return siblingBot;
            }
        }

        return owner;
    }

    void setFormationState(Character owner, FormationType type, int px, int snapRange, List<BotEntry> entries) {
        if (owner == null) {
            return;
        }

        FormationState formation = new FormationState(type, px, snapRange);
        ownerFormations.put(owner.getId(), formation);
        if (entries == null) {
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            AgentBotFormationStateRuntime.setFollowOffsetX(entries.get(i), formation.offsetFor(i, entries.size()));
        }
    }

    TargetSnapshot captureTargetSnapshot(BotEntry entry) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        Character followAnchor = resolveFollowAnchor(entry, owner);
        Point fallbackPos = bot.getPosition();
        Point rawOwnerPos = owner != null ? owner.getPosition() : fallbackPos;
        Point rawFollowAnchorPos = followAnchor != null ? followAnchor.getPosition() : rawOwnerPos;
        String followAnchorName = followAnchor != null ? followAnchor.getName() : "owner";
        FormationState formation = formationStateFor(entry);
        Point followBasePos = new Point(rawFollowAnchorPos.x + AgentBotFormationStateRuntime.followOffsetX(entry), rawFollowAnchorPos.y);
        Point followTargetPos = resolveFollowTargetPos(followBasePos, followAnchor, rawFollowAnchorPos, formation.snapRange(), bot.getMap());
        Point rawShopTargetPos = AgentBotShopStateRuntime.shopVisitPending(entry)
                ? AgentBotShopStateRuntime.activeShopTargetPosition(entry)
                : null;
        Point shopTargetPos = rawShopTargetPos == null ? null : new Point(rawShopTargetPos);
        Point moveTargetPos = AgentBotMoveTargetStateRuntime.moveTarget(entry);
        Point farmAnchorPos = AgentBotFarmAnchorStateRuntime.farmAnchorInMap(entry, bot.getMapId());
        Monster activeGrindTarget = AgentBotGrindTargetStateRuntime.activeTargetInMap(entry, bot.getMap());
        Point grindTargetPos = activeGrindTarget == null ? null : new Point(activeGrindTarget.getPosition());
        Point primaryTargetPos;
        String primaryTargetSource;
        if (shopTargetPos != null) {
            primaryTargetPos = shopTargetPos;
            primaryTargetSource = "shop-target";
        } else if (moveTargetPos != null) {
            primaryTargetPos = moveTargetPos;
            primaryTargetSource = "move-target";
        } else if (farmAnchorPos != null) {
            primaryTargetPos = farmAnchorPos;
            primaryTargetSource = "farm-anchor";
        } else if (grindTargetPos != null) {
            primaryTargetPos = grindTargetPos;
            primaryTargetSource = "grind-target";
        } else if (AgentBotModeStateRuntime.grinding(entry)) {
            primaryTargetPos = fallbackPos;
            primaryTargetSource = "grind-idle";
        } else if (AgentBotModeStateRuntime.following(entry)) {
            primaryTargetPos = followTargetPos;
            primaryTargetSource = "follow-target";
        } else {
            primaryTargetPos = rawOwnerPos;
            primaryTargetSource = "owner-raw";
        }
        return new TargetSnapshot(
                formation,
                new Point(rawOwnerPos),
                new Point(rawFollowAnchorPos),
                followAnchorName,
                new Point(followBasePos),
                new Point(followTargetPos),
                moveTargetPos,
                farmAnchorPos,
                grindTargetPos,
                new Point(primaryTargetPos),
                primaryTargetSource);
    }

    private static final int RETREAT_HOLD_MS = 600;
    private static final int RETREAT_ARRIVAL_TOLERANCE_X = 25; // 50ms tick can't land on an exact pixel

    // AoE reposition commitment: returns the sweet-spot Point to walk to before firing, or null to
    // fire now. Scores once when a commitment starts (BotCombatManager.aoeRepositionTarget); while
    // committed it just returns the stored anchor — no further scoring — until the bot arrives, the
    // bounded-chase deadline expires, or the target dies/clears.
    private static Point resolveAoeReposition(BotEntry entry, Character bot, Monster target,
                                              BotCombatManager.AttackPlan attackPlan, Point botPos) {
        long now = System.currentTimeMillis();
        if (AgentBotAoeRepositionStateRuntime.hasAnchor(entry)) {
            boolean done = AgentBotAoeRepositionStateRuntime.isExpiredOrArrived(
                    entry, botPos, now, BotCombatManager.cfg.AOE_REPOSITION_ARRIVAL_X)
                    || target == null || !target.isAlive();
            if (done) {
                AgentBotAoeRepositionStateRuntime.clear(entry);
                return null;
            }
            return AgentBotAoeRepositionStateRuntime.anchor(entry);
        }
        Point anchor = BotCombatManager.aoeRepositionTarget(entry, bot, target, attackPlan);
        if (anchor != null) {
            AgentBotAoeRepositionStateRuntime.setAnchor(
                    entry, anchor, now + BotCombatManager.cfg.AOE_REPOSITION_MAX_MS);
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
            } else if (dxHold > BotCombatManager.cfg.RANGED_RETREAT_DISTANCE_X * 2) {
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
                    entry, dir, now + BotCombatManager.cfg.BREAKOUT_MAX_MS);
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
        return new Point(botPos.x + dir * BotCombatManager.cfg.RANGED_RETREAT_DISTANCE_X, botPos.y);
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
        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return base;
        }
        int botRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        if (botRegionId < 0) {
            return base;
        }
        int leftBestMobs = Integer.MAX_VALUE;
        int rightBestMobs = Integer.MAX_VALUE;
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(botRegionId)) {
            if (edge.type != BotNavigationGraph.EdgeType.WALK) {
                continue;
            }
            BotNavigationGraph.Region region = graph.getRegion(edge.toRegionId);
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
        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            BotNavigationGraphProvider.warmGraphAsync(map, AgentBotMovementStateRuntime.movementProfile(entry));
            return null;
        }

        int botRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        if (botRegionId < 0) {
            return null;
        }
        int targetRegionId = BotNavigationManager.resolveTargetRegionId(graph, entry, map, combatTargetPos);

        int projectileRange = AgentProjectileHitbox.CLIENT_PROJECTILE_BASE_RANGE
                + AgentProjectileHitbox.passiveProjectileRangeBonus(bot);
        int yReachable = BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_Y * 2;

        Point reachableRetreat = selectReachableProjectileRetreatTarget(
                graph, map, botPos, botRegionId, targetRegionId, combatTargetPos, projectileRange, yReachable);
        if (reachableRetreat != null) {
            return reachableRetreat;
        }

        BotNavigationGraph.Edge bestEdge = null;
        int bestScore = Integer.MIN_VALUE;
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(botRegionId)) {
            if (edge.type != BotNavigationGraph.EdgeType.WALK) {
                continue;
            }
            int toRegionId = edge.toRegionId;
            if (toRegionId == botRegionId || toRegionId == targetRegionId) {
                continue;
            }
            BotNavigationGraph.Region region = graph.getRegion(toRegionId);
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
            if (dx <= BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_X) {
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

    private static Point selectReachableProjectileRetreatTarget(BotNavigationGraph graph,
                                                                MapleMap map,
                                                                Point botPos,
                                                                int botRegionId,
                                                                int targetRegionId,
                                                                Point combatTargetPos,
                                                                int projectileRange,
                                                                int yReachable) {
        Point bestPoint = null;
        int bestScore = Integer.MIN_VALUE;
        for (BotNavigationGraph.Region region : graph.regions) {
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

            List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(
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

    private static boolean pathUsesPortal(List<BotNavigationGraph.Edge> path) {
        for (BotNavigationGraph.Edge edge : path) {
            if (edge.type == BotNavigationGraph.EdgeType.PORTAL) {
                return true;
            }
        }
        return false;
    }

    private static Point selectProjectileRetreatPoint(BotNavigationGraph.Region region,
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

        int minShootDx = BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_X + 20;
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

    private static Point projectileRetreatCandidate(BotNavigationGraph.Region region,
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

    private static int countMobsInRegion(BotNavigationGraph graph,
                                         MapleMap map,
                                         BotNavigationGraph.Region region) {
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

        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            BotNavigationGraphProvider.warmGraphAsync(map, AgentBotMovementStateRuntime.movementProfile(entry));
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
                                              BotCombatManager.AttackPlan currentAttackPlan,
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
                || !BotCombatManager.isTargetInAttackRange(currentAttackPlan, bot, currentTarget)) {
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
                                                Monster searched, BotCombatManager.AttackPlan currentPlan) {
        if (searched == null || searched == current) {
            return false;
        }
        if (current == null || bot == null || currentPlan == null
                || !BotCombatManager.isTargetInAttackRange(currentPlan, bot, current)) {
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

        BotNavigationGraph graph = map != null
                ? BotNavigationGraphProvider.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry))
                : null;
        int regionId = graph != null ? BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos) : -1;
        BotNavigationGraph.Region region = graph != null ? graph.getRegion(regionId) : null;
        if (region != null && !region.isRopeRegion && region.width() > 0) {
            Point wander = AgentBotPatrolStateRuntime.patrolWanderTarget(entry);
            if (wander == null || isNear(botPos, wander, BotMovementManager.cfg.STOP_DIST)) {
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

    static boolean isGrindLootRetrySuppressed(BotEntry entry, MapItem loot, long now) {
        return AgentBotGrindLootStateRuntime.isRetrySuppressed(entry, loot, now);
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
        BotNavigationGraph graph = BotNavigationGraphProvider.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        int patrolRegionId = AgentBotPatrolStateRuntime.patrolRegionId(entry);
        BotNavigationGraph.Region region = graph != null ? graph.getRegion(patrolRegionId) : null;
        if (region == null || region.isRopeRegion || region.width() == 0) {
            return resolveNoGrindTargetPosition(entry, botPos, map);
        }
        // Seek loot before roaming
        Point lootTarget = BotInventoryManager.findNearestPatrolLootTarget(entry, patrolRegionId);
        if (lootTarget != null) {
            AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, lootTarget);
            return lootTarget;
        }
        // Roam within region
        Point wander = AgentBotPatrolStateRuntime.patrolWanderTarget(entry);
        if (wander == null || isNear(botPos, wander, BotMovementManager.cfg.STOP_DIST)) {
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
        if (AgentBotTickStateRuntime.heartbeatDue(entry, nowMs, 600_000L)) {
            AgentBotTickStateRuntime.markHeartbeat(entry, nowMs);
            bot.getClient().updateLastPacket();
            BotMovementManager.broadcastMovement(entry);
        }

        BotOfferManager.expirePendingOffer(entry);
        boolean runAiTick = consumeAiTick(entry);
        AgentBotTickStateRuntime.recordTick(entry, runAiTick, System.currentTimeMillis());

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
        TargetSnapshot targetSnapshot = captureTargetSnapshot(entry);
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
                BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                BotMovementManager.broadcastMovement(entry);
                if (BotPqHooks.requiresGrind(entry, bot)) { issueGrind(entry); }
                else if (BotPqHooks.requiresFollow(entry, bot)) { issueFollowOwner(entry); }
                else { AgentBotPqRuntime.resetKpqStage5Claimed(entry); } // left KPQ — reset for next run
                BotShopManager.onMapChange(entry, bot);
                AgentBotManagerStatusRuntime.checkManagerStatus(entry, bot);
            } else {
                long tMapChange = System.nanoTime();
                try {
                    AgentBotMapStateRuntime.setMapTracking(entry, bot.getMapId(), BotMovementManager.buildFhIndex(bot.getMap()));
                    Point cur = bot.getPosition();
                    Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
                    BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
                    BotMovementManager.resetEntryStateAfterTeleport(entry);
                    BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                    BotMovementManager.broadcastMovement(entry);
                    if (BotPqHooks.requiresGrind(entry, bot)) { issueGrind(entry); }
                    else if (BotPqHooks.requiresFollow(entry, bot)) { issueFollowOwner(entry); }
                    else { AgentBotPqRuntime.resetKpqStage5Claimed(entry); } // left KPQ — reset for next run
                    BotShopManager.onMapChange(entry, bot);
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
                consumed = BotShopManager.tickShopVisit(entry, bot);
            } else {
                long tShop = System.nanoTime();
                consumed = BotShopManager.tickShopVisit(entry, bot);
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
        double seekRangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
        Monster target = AgentBotGrindTargetStateRuntime.targetInSeekRange(entry, bot, botPos, seekRangeSq);
        long now = System.currentTimeMillis();
        BotCombatManager.AttackPlan attackPlan = target == null
                ? null
                : BotCombatManager.planAttack(entry, bot, target);
        // Validate cached loot target
        if (AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            MapItem loot = AgentBotGrindLootStateRuntime.grindLootTarget(entry);
            if (loot.isPickedUp() || bot.getMap().getMapObject(loot.getObjectId()) != loot) {
                AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            }
        }
        if (runAiTick && shouldSearchForGrindTarget(entry, bot, target, attackPlan, now)) {
            Monster searchedTarget = AgentBotPatrolStateRuntime.hasPatrolRegion(entry)
                    ? BotCombatManager.findPatrolTarget(entry, bot)
                    : BotCombatManager.findGrindTarget(entry, bot);
            if (shouldSwitchToSearchedTarget(entry, bot, target, searchedTarget, attackPlan)) {
                target = searchedTarget;
                attackPlan = null;
            }
            AgentBotGrindSearchStateRuntime.scheduleNextSearch(
                    entry, now + BotCombatManager.cfg.GRIND_RETARGET_INTERVAL_MS);
        }
        // Search for a convenient loot drop every AI tick (grind mode only)
        if (runAiTick && !AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, BotInventoryManager.findNearestGrindLootTarget(entry, bot));
        }
        if (target == null) {
            AgentBotGrindTargetStateRuntime.clear(entry);
            if (isSwimMap(entry) && AgentBotMovementStateRuntime.inAir(entry)) {
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
            attackPlan = BotCombatManager.planAttack(entry, bot, target);
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
                && attackGateOpen && BotCombatManager.isTargetInAttackRange(attackPlan, bot, target))
                ? resolveAoeReposition(entry, bot, target, attackPlan, botPos)
                : null;

        boolean attackAttemptedInRange = false;
        if (!AgentBotMovementStateRuntime.climbing(entry)) {
            if (aoeRepositionPos == null
                    && attackGateOpen && BotCombatManager.isTargetInAttackRange(attackPlan, bot, target)
                    && AgentCombatRangePolicy.canUseAttackPlanNow(
                            AgentBotMovementStateRuntime.grounded(entry), grindWeaponType, attackPlan.route)) {
                attackAttemptedInRange = true;
                // In range — attack if grounded, or during ascent of a jump
                int prevCooldown = AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry);
                BotCombatManager.attackMonster(entry, bot, attackPlan);
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
                && BotCombatManager.isTargetInAttackRange(attackPlan, bot, target)) {
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
        int failureCount = AgentBotTickFailureStateRuntime.recordFailure(entry, now, BOT_TICK_FAILURE_WINDOW_MS);

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        String botName = bot != null ? bot.getName() : "?";
        String ownerName = owner != null ? owner.getName() : "?";
        int mapId = bot != null ? bot.getMapId() : -1;

        clearBotVolatileActions(entry);
        if (failureCount >= BOT_TICK_FAILURE_LIMIT) {
            log.error("Disabling bot '{}' after {} tick failures within {} ms (owner={}, map={}, grinding={}, following={})",
                    botName, failureCount, BOT_TICK_FAILURE_WINDOW_MS, ownerName, mapId,
                    AgentBotModeStateRuntime.grinding(entry), AgentBotModeStateRuntime.following(entry), t);
            removeBotByCharId(botCharId);
            return;
        }

        if (failureCount == 2) {
            forceBotIdleAfterTickFailure(entry);
        }

        log.warn("Bot '{}' tick failed {}/{} (owner={}, map={}, grinding={}, following={})",
                botName, failureCount, BOT_TICK_FAILURE_LIMIT, ownerName, mapId,
                AgentBotModeStateRuntime.grinding(entry), AgentBotModeStateRuntime.following(entry), t);
    }

    private static void resetBotTickFailures(BotEntry entry) {
        if (entry == null || !AgentBotTickFailureStateRuntime.hasFailures(entry)) {
            return;
        }
        AgentBotTickFailureStateRuntime.clear(entry);
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
        BotCombatManager.AttackPlan plan = BotCombatManager.planAttack(entry, bot, target);
        return plan != null
                && plan.route == AgentAttackRoute.RANGED
                && BotCombatManager.isTargetInAttackRange(plan, bot, target)
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

        if (isNear(botPos, anchor, 8) && !AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)) {
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

        Monster localTarget = BotCombatManager.findFollowAttackTarget(entry, bot);
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

        BotCombatManager.AttackPlan attackPlan = BotCombatManager.planAttack(entry, bot, localTarget);
        if (attackPlan == null) {
            return new LocalOpportunityAttackResult(false, targetPos);
        }
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            if (AgentCombatRangePolicy.canUseAttackPlanNow(
                    AgentBotMovementStateRuntime.grounded(entry), weaponType, attackPlan.route)
                    && BotCombatManager.isTargetInAttackRange(attackPlan, bot, localTarget)) {
                BotCombatManager.attackMonster(entry, bot, attackPlan);
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
                && BotCombatManager.isTargetInAttackRange(attackPlan, bot, localTarget)) {
            BotCombatManager.attackMonster(entry, bot, attackPlan);
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
                                                             TargetSnapshot targetSnapshot) {
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
        Character owner = AgentBotLeaderStateRuntime.leader(entry);
        if (owner == null || !AgentBotLeaderStateRuntime.matchesLeaderId(entry, ownerCharId) || !owner.isLoggedinWorld()) {
            owner = Server.getInstance()
                    .getWorld(AgentBotRuntimeIdentityRuntime.bot(entry).getWorld())
                    .getPlayerStorage()
                    .getCharacterById(ownerCharId);
            AgentBotLeaderStateRuntime.setLeader(entry, owner);
        }
        return owner;
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
            if (AgentBotActivityStateRuntime.ownerAwaySafeMode(entry)
                    && !AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)) {
                return false;
            }
            if (AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)
                    || AgentBotActivityStateRuntime.ownerReturnedToTown(entry)) {
                boolean justReturnedFromTown = AgentBotActivityStateRuntime.ownerReturnedToTown(entry);
                AgentBotActivityStateRuntime.clearOwnerInactiveState(entry);
                // Cancel any in-flight cluster walk: while owner was offline the
                // only setter of moveTarget was the offline-town path, so clearing
                // here is safe and avoids stale state when owner reconnects.
                AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
                // Race-safe single-representative wb: each bot tries to remove the
                // group's anchor entry; only the winner (first to observe owner
                // active) speaks. Other bots find the anchor already cleared and
                // skip the announcement, so the party sees one wb line, not N.
                Point removedAnchor = townClusterAnchors.remove(ownerCharId);
                if (justReturnedFromTown && removedAnchor != null) {
                    AgentBotManagerStatusRuntime.announceOwnerReturnedFromOffline(entry);
                }
            }
            return false;
        }

        if (AgentBotActivityStateRuntime.ownerReturnedToTown(entry)) {
            if (AgentBotActivityStateRuntime.ownerAwaySafeMode(entry)
                    && !AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)) {
                AgentBotActivityStateRuntime.startOwnerInactiveTimer(entry, nowMs);
            }
            return false;
        }

        if (!AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)) {
            AgentBotActivityStateRuntime.startOwnerInactiveTimer(entry, nowMs);
            return false;
        }

        if (nowMs - AgentBotActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry)
                < cfg.OWNER_INACTIVE_TOWN_RETURN_MS) {
            return false;
        }

        return enterOwnerInactiveSafeMode(entry, bot, ownerCharId, shouldTownWarpForOwnerInactive(entry));
    }

    private boolean shouldTownWarpForOwnerInactive(BotEntry entry) {
        MapleMap currentMap = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return currentMap != null
                && currentMap.getAllMonsters().stream().anyMatch(Monster::isAlive)
                && canReturnToDifferentMap(currentMap);
    }

    private static boolean canReturnToDifferentMap(MapleMap currentMap) {
        if (currentMap == null) {
            return false;
        }
        MapleMap returnMap = currentMap.getReturnMap();
        return returnMap != null && returnMap.getId() != currentMap.getId();
    }

    public boolean shouldOfferTownForAwayCommand(BotEntry entry) {
        return shouldTownWarpForOwnerInactive(entry);
    }

    public boolean isFirstBotEntry(BotEntry entry) {
        return entry != null
                && AgentBotRuntimeIdentityRuntime.owner(entry) != null
                && getFirstBotEntry(AgentBotRuntimeIdentityRuntime.ownerId(entry)) == entry;
    }

    public void issueOwnerAwaySafeModeForOwner(int ownerCharId, boolean town) {
        for (BotEntry entry : getBotEntries(ownerCharId)) {
            if (!AgentBotRuntimeIdentityRuntime.botHasMap(entry)) {
                continue;
            }
            enterOwnerInactiveSafeMode(entry, AgentBotRuntimeIdentityRuntime.bot(entry), ownerCharId,
                    town && shouldTownWarpForOwnerInactive(entry));
        }
    }

    private boolean enterOwnerInactiveSafeMode(BotEntry entry, Character bot, int ownerCharId, boolean town) {
        prepareOwnerInactiveIdle(entry, ownerCharId);
        if (town) {
            return scrollBotToTown(entry, bot, ownerCharId);
        }

        BotPhysicsEngine.idleOnGround(entry, bot);
        BotMovementManager.broadcastMovement(entry);
        AgentBotActivityStateRuntime.setOwnerReturnedToTown(entry, true);
        return false;
    }

    private void prepareOwnerInactiveIdle(BotEntry entry, int ownerCharId) {
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        clearMode(entry);
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotDegenerateAttackStateRuntime.clear(entry);
        AgentBotBuffStateRuntime.disable(entry);
        AgentBotActivityStateRuntime.setOwnerAwaySafeMode(entry, true);
    }

    private boolean scrollBotToTown(BotEntry entry, Character bot, int ownerCharId) {
        MapleMap currentMap = bot.getMap();
        if (currentMap == null) {
            return false;
        }
        MapleMap returnMap = currentMap.getReturnMap();
        if (returnMap == null || returnMap.getId() == currentMap.getId()) {
            // No return map (e.g. some PQ/town maps): mark handled to avoid re-evaluating every tick.
            AgentBotActivityStateRuntime.setOwnerReturnedToTown(entry, true);
            return false;
        }

        BotPhysicsEngine.idleOnGround(entry, bot);

        // Every bot uses the same path: applyTo handles scroll-consume + random
        // portal warp. Falls back to a plain changeMap when the bot has no scroll.
        if (!tryUseReturnScroll(bot)) {
            bot.changeMap(returnMap);
        }
        groundAfterMapChange(entry, bot);

        // Capture the cluster anchor at the first bot's post-warp position, then let
        // every bot walk to the deterministic formation slot around that anchor. This
        // mirrors the owner's current follow formation instead of stacking everyone on
        // the same portal landing point.
        Point post = new Point(bot.getPosition());
        Point anchor = townClusterAnchors.putIfAbsent(ownerCharId, post);
        if (anchor == null) {
            anchor = post;
        }
        Point target = resolveTownClusterTarget(entry, ownerCharId, returnMap, anchor);

        BotMovementManager.resetEntryState(entry);
        startMoveTo(entry, target, true);
        AgentBotActivityStateRuntime.setOwnerReturnedToTown(entry, true);
        return true;
    }

    private Point resolveTownClusterTarget(BotEntry entry, int ownerCharId, MapleMap map, Point anchor) {
        Point base = anchor != null ? new Point(anchor) : new Point(AgentBotRuntimeIdentityRuntime.botPosition(entry));
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || map == null) {
            return base;
        }

        List<BotEntry> entries = getBotEntries(ownerCharId);
        int idx = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) == entry) {
                idx = i;
                break;
            }
        }

        FormationState formation = ownerFormations.getOrDefault(ownerCharId, FormationState.defaultStagger());
        int offsetX = formation.offsetFor(idx, Math.max(1, entries.size()));
        int targetX = base.x + offsetX;

        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph != null) {
            int anchorRegionId = graph.findRegionId(map, base);
            BotNavigationGraph.Region anchorRegion = graph.getRegion(anchorRegionId);
            if (anchorRegion != null && !anchorRegion.isRopeRegion) {
                int edgeMargin = PLATFORM_EDGE_INSET_PX;
                int minX = anchorRegion.minX;
                int maxX = anchorRegion.maxX;
                if (maxX - minX > 2 * edgeMargin) {
                    minX += edgeMargin;
                    maxX -= edgeMargin;
                }
                int clampedX = Math.max(minX, Math.min(maxX, targetX));
                return anchorRegion.pointAt(clampedX);
            }
        }

        Rectangle area = map.getMapArea();
        int minX = area != null ? area.x : targetX;
        int maxX = area != null ? area.x + area.width : targetX;
        if (map.getFootholds() != null && map.getFootholds().getMinDropX() < map.getFootholds().getMaxDropX()) {
            minX = map.getFootholds().getMinDropX();
            maxX = map.getFootholds().getMaxDropX();
        }

        int clampedX = Math.max(minX, Math.min(maxX, targetX));
        Point ground = BotPhysicsEngine.findGroundPoint(map, new Point(clampedX, base.y - 1));
        if (ground != null) {
            return ground;
        }

        Point anchorGround = BotPhysicsEngine.findGroundPoint(map, new Point(base.x, base.y - 1));
        return anchorGround != null ? anchorGround : base;
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
        if (entry == null || dest == null) {
            return;
        }
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        startMoveTo(entry, dest, precise);
    }

    private void startMoveTo(BotEntry entry, Point dest, boolean precise) {
        clearMode(entry);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, dest, precise);
    }

    public void issueFarmHere(BotEntry entry, Point dest) {
        if (entry == null || dest == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        startFarmHere(entry, dest);
    }

    private void startFarmHere(BotEntry entry, Point dest) {
        // Sentry/farm-here is an active combat mode that just anchors to a fixed spot.
        // Route through the shared active-mode reset so it stays in lock-step with
        // grind/patrol (self-buff, pot-share, ammo-low, "low on pots" fallback all
        // gate on Agent mode state — see kb feedback_bot_coding_guidelines).
        enterActiveMode(entry);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, dest, AgentBotRuntimeIdentityRuntime.botMapId(entry));
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, dest);
    }

    public void issuePatrol(BotEntry entry, Point ownerPos) {
        if (entry == null || ownerPos == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        BotNavigationGraph graph = BotNavigationGraphProvider.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        int regionId = graph != null ? graph.findRegionId(map, ownerPos) : -1;
        if (regionId < 0) {
            AgentBotManagerReplyRuntime.replyNow(entry, "can't find a patrol region here");
            return;
        }
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        startPatrol(entry, regionId);
    }

    private void startPatrol(BotEntry entry, int regionId) {
        enterActiveMode(entry);
        AgentBotPatrolStateRuntime.startPatrol(entry, regionId, AgentBotRuntimeIdentityRuntime.botMapId(entry));
    }

    /**
     * Public hook: return the bot to ordinary owner-follow mode. Scripted map
     * automation and chat commands should use this instead of writing mode
     * fields directly.
     */
    public void issueFollowOwner(BotEntry entry) {
        issueFollow(entry, AgentBotRuntimeIdentityRuntime.owner(entry));
    }

    /**
     * Public hook: follow a concrete party/member/bot target. Passing the owner
     * (or null) means regular owner-follow.
     */
    public void issueFollow(BotEntry entry, Character target) {
        if (entry == null) {
            return;
        }
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        startFollow(entry, target);
    }

    private void startFollow(BotEntry entry, Character target) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentBotModeStateRuntime.setFollowTargetId(entry, owner != null && target != null && owner.getId() != target.getId()
                ? target.getId()
                : 0);
        AgentBotModeStateRuntime.setGrinding(entry, false);
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentBotFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentBotModeStateRuntime.setFollowing(entry, true);
    }

    /**
     * Public hook: enter autonomous grind/combat mode using the same setup as
     * player chat commands. This deliberately clears fixed movement and follow
     * targets so scripted "grind" steps do not run in parallel with a stale
     * navigation command.
     */
    public void issueGrind(BotEntry entry) {
        if (entry == null) {
            return;
        }
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        startGrind(entry);
    }

    private void startGrind(BotEntry entry) {
        enterActiveMode(entry);
    }

    /**
     * Shared baseline for all active combat modes (grind / sentry / patrol). Sets
     * {@code grinding = true} and zeros out follow, move, and sub-mode state so
     * each mode starts from the same baseline. Mode-specific fields (anchor /
     * patrol region) are set by the caller after this returns.
     *
     * When adding a new active mode, route it through this helper to avoid the
     * sentry-mode regression where {@code grinding=false} silently disabled the
     * pot-share, self-buff, and ammo-low fallback paths.
     */
    private void enterActiveMode(BotEntry entry) {
        AgentBotModeStateRuntime.stopFollowing(entry);
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentBotFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentBotPatrolStateRuntime.clearPatrol(entry);
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
        AgentBotGrindSearchStateRuntime.clear(entry);
        AgentBotCombatCooldownStateRuntime.clearMoveWindow(entry);
        AgentBotDegenerateAttackStateRuntime.clear(entry);
        AgentBotRetreatHoldStateRuntime.clear(entry);
        AgentBotGrindWanderStateRuntime.clearWanderDirection(entry);
        BotMovementManager.clearNavigationState(entry);
        AgentBotModeStateRuntime.startGrinding(entry);
    }

    /** Public hook: stop all scripted movement/combat mode and idle in place. */
    public void issueStop(BotEntry entry) {
        if (entry == null) {
            return;
        }
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        startStop(entry);
    }

    private void startStop(BotEntry entry) {
        clearMode(entry);
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
    }

    /**
     * Public hook for map scripts: drop up to {@code quantity} from the first
     * stack of {@code itemId}. Use {@code quantity <= 0} to drop the whole stack.
     */
    public boolean issueDropItem(BotEntry entry, InventoryType type, int itemId, short quantity) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null || type == null) {
            return false;
        }
        var inventory = bot.getInventory(type);
        if (inventory == null) {
            return false;
        }
        Item item = inventory.findById(itemId);
        if (item == null || item.getQuantity() <= 0) {
            return false;
        }
        short dropQuantity = quantity <= 0 ? item.getQuantity() : (short) Math.min(quantity, item.getQuantity());
        InventoryManipulator.drop(bot.getClient(), type, item.getPosition(), dropQuantity);
        return true;
    }

    public void clearScriptTasks(BotEntry entry) {
        if (entry == null) {
            return;
        }
        AgentBotScriptTaskStateRuntime.clearTasksAndBumpEpoch(entry);   // signal background batches (Maker craft/disassembly) to self-interrupt
    }

    public void queueTask(BotEntry entry, BotTask task) {
        if (entry == null || task == null) {
            return;
        }
        AgentBotScriptTaskStateRuntime.queueTask(entry, task);
    }

    public void queueMoveTo(BotEntry entry, Point point, boolean precise) {
        queueTask(entry, BotTask.moveTo(point, precise));
    }

    public void queueMoveTo(BotEntry entry, Point point, boolean precise, BotTask.MoveCombatMode moveCombatMode) {
        queueTask(entry, BotTask.moveTo(point, precise, moveCombatMode));
    }

    public void queueMoveThenDropItem(BotEntry entry, Point point, boolean precise, InventoryType type, int itemId, short quantity) {
        queueTask(entry, BotTask.moveTo(point, precise));
        queueTask(entry, BotTask.dropItem(type, itemId, quantity));
    }

    public void queueFollowThenDropItem(BotEntry entry, Character target, int nearPx, InventoryType type, int itemId, short quantity) {
        queueTask(entry, BotTask.followUntilNear(target, nearPx));
        queueTask(entry, BotTask.dropItem(type, itemId, quantity));
    }

    public boolean hasQueuedTasks(BotEntry entry) {
        return AgentBotScriptTaskStateRuntime.hasQueuedTasks(entry);
    }

    public boolean isCheapScriptMoveTarget(BotEntry entry,
                                           Point targetPos,
                                           int maxPathCost,
                                           int fallbackRangeX,
                                           int fallbackRangeY) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry) || targetPos == null) {
            return false;
        }

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Point botPos = bot.getPosition();
        if (botPos == null) {
            return false;
        }
        if (Math.abs(targetPos.x - botPos.x) <= cfg.LOOT_RADIUS
                && Math.abs(targetPos.y - botPos.y) <= cfg.LOOT_RADIUS) {
            return false;
        }

        MapleMap map = bot.getMap();
        if (map == null || map.getFootholds() == null) {
            return false;
        }

        BotNavigationGraph graph = BotNavigationGraphProvider.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return Math.abs(targetPos.x - botPos.x) <= fallbackRangeX
                    && Math.abs(targetPos.y - botPos.y) <= fallbackRangeY;
        }

        int startRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        int targetRegionId = BotNavigationManager.resolvePointTargetRegionId(graph, map, targetPos);
        if (startRegionId < 0 || targetRegionId < 0) {
            return false;
        }
        if (startRegionId == targetRegionId) {
            return true;
        }

        List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(graph, bot, startRegionId, targetRegionId, targetPos);
        if (path.isEmpty()) {
            return false;
        }

        int totalCost = 0;
        for (BotNavigationGraph.Edge edge : path) {
            totalCost += edge.cost;
            if (totalCost > maxPathCost) {
                return false;
            }
        }
        return true;
    }

    private static void clearMode(BotEntry entry) {
        AgentBotModeStateRuntime.stopMovementModes(entry);
        AgentBotFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentBotPatrolStateRuntime.clearPatrol(entry);
        AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
    }

    private static boolean isNear(Point source, Point target, int dist) {
        return source != null && target != null
                && Math.abs(source.x - target.x) <= dist
                && Math.abs(source.y - target.y) <= dist;
    }

    private void tickScriptTasks(BotEntry entry) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }

        while (true) {
            BotTask activeScriptTask = AgentBotScriptTaskStateRuntime.activeTask(entry);
            if (activeScriptTask == null) {
                activeScriptTask = AgentBotScriptTaskStateRuntime.activateNextTask(entry);
                if (activeScriptTask == null) {
                    return;
                }
                startScriptTask(entry, activeScriptTask);
            }

            if (!isScriptTaskComplete(entry, activeScriptTask)) {
                return;
            }
            AgentBotScriptTaskStateRuntime.clearActiveTask(entry);
        }
    }

    private void startScriptTask(BotEntry entry, BotTask task) {
        switch (task.type) {
            case MOVE_TO -> startMoveTo(entry, task.point, task.precise);
            case FOLLOW_OWNER -> startFollow(entry, AgentBotRuntimeIdentityRuntime.owner(entry));
            case FOLLOW_TARGET -> startFollow(entry, resolveFollowCharacterById(entry, task.targetCharacterId));
            case FOLLOW_UNTIL_NEAR -> startFollow(entry, resolveFollowCharacterById(entry, task.targetCharacterId));
            case GRIND -> startGrind(entry);
            case STOP -> startStop(entry);
            case DROP_ITEM -> issueDropItem(entry, task.inventoryType, task.itemId, task.quantity);
        }
    }

    private boolean isScriptTaskComplete(BotEntry entry, BotTask task) {
        return switch (task.type) {
            case MOVE_TO -> !AgentBotMoveTargetStateRuntime.hasMoveTarget(entry) || isNear(AgentBotRuntimeIdentityRuntime.botPosition(entry), task.point,
                    task.precise ? 8 : BotMovementManager.cfg.STOP_DIST);
            case FOLLOW_UNTIL_NEAR -> {
                Character target = resolveFollowCharacterById(entry, task.targetCharacterId);
                yield target != null
                        && AgentBotRuntimeIdentityRuntime.botMapId(entry) == target.getMapId()
                        && isNear(AgentBotRuntimeIdentityRuntime.botPosition(entry), target.getPosition(), task.nearPx);
            }
            case FOLLOW_OWNER, FOLLOW_TARGET, GRIND, STOP, DROP_ITEM -> true;
        };
    }

    private Character resolveFollowCharacterById(BotEntry entry, int targetCharacterId) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (entry == null || targetCharacterId <= 0) {
            return owner;
        }
        if (owner != null && owner.getId() == targetCharacterId) {
            return owner;
        }
        if (owner != null && owner.getParty() != null) {
            for (Character member : owner.getPartyMembersOnline()) {
                if (member != null && member.getId() == targetCharacterId && member.isLoggedinWorld()) {
                    return member;
                }
            }
        }
        if (owner != null) {
            for (BotEntry sibling : getBotEntries(owner.getId())) {
                Character siblingBot = AgentBotRuntimeIdentityRuntime.bot(sibling);
                if (siblingBot != null && siblingBot.getId() == targetCharacterId && siblingBot.isLoggedinWorld()) {
                    return siblingBot;
                }
            }
        }
        return owner;
    }

    /**
     * Apply Return Scroll - Nearest Town (item 2030000) via StatEffect.applyTo.
     * The standard scroll effect handles random-portal warp inside applyTo;
     * we only need to remove the consumable afterwards (mirrors ScrollHandler).
     * Returns false when no 2030000 is in the bot's USE inventory or applyTo failed.
     */
    private boolean tryUseReturnScroll(Character bot) {
        var use = bot.getInventory(InventoryType.USE);
        if (use == null) {
            return false;
        }
        for (Item item : use.list()) {
            if (item == null || item.getQuantity() <= 0) {
                continue;
            }
            if (item.getItemId() != 2030000) {
                continue;
            }
            StatEffect effect;
            try {
                effect = ItemInformationProvider.getInstance().getItemEffect(2030000);
            } catch (Exception e) {
                return false;
            }
            if (effect == null || !effect.applyTo(bot)) {
                return false;
            }
            InventoryManipulator.removeFromSlot(bot.getClient(), InventoryType.USE, item.getPosition(), (short) 1, false);
            return true;
        }
        return false;
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
        if (AgentBotMapStateRuntime.isTrackingMap(entry, bot.getMapId())) {
            return false;
        }

        AgentBotMapStateRuntime.setMapTracking(entry, bot.getMapId(), BotMovementManager.buildFhIndex(bot.getMap()));
        Point cur = bot.getPosition();
        Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
        BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
        BotMovementManager.resetEntryStateAfterTeleport(entry);
        BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        BotMovementManager.broadcastMovement(entry);
        return true;
    }

    private boolean handleDeadTick(BotEntry entry, Character bot, Character owner) {
        if (AgentBotDeathStateRuntime.shouldEnterDeadState(entry, bot.getHp())) {
            BotCombatManager.enterDeadState(entry, bot, false);
        }
        if (!AgentBotDeathStateRuntime.isDead(entry)) {
            return false;
        }
        if (AgentBotDeathStateRuntime.isRespawnDue(entry, System.currentTimeMillis())) {
            respawnBot(entry, bot, owner);
        }
        return true;
    }

    private boolean runCommonTickSystems(BotEntry entry, Character bot, Character owner, boolean runAiTick) {
        // Single source of truth for the subsystem sequence. Perf instrumentation is gated by
        // `perf` so the hot (monitor-disabled) path pays only cheap branch checks and never
        // allocates timing state, while the enabled path keeps every per-subsystem label.
        boolean perf = AgentPerformanceMonitor.enabled();
        long t = perf ? System.nanoTime() : 0L;
        BotCombatManager.tickMobDamage(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-mob-damage", System.nanoTime() - t);
        if (bot.getHp() <= 0) {
            if (!AgentBotDeathStateRuntime.isDead(entry)) {
                BotCombatManager.enterDeadState(entry, bot, false);
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
        BotPotionManager.tickPotionCheck(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-potion-check", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        BotPotionManager.tickPassiveRecovery(entry, bot);
        if (perf) AgentPerformanceMonitor.record("common-passive-recovery", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        BotBuildManager.checkLevelUp(entry, bot);
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
        BotPqHooks.tick(entry, bot, owner);
        if (perf) AgentPerformanceMonitor.record("common-pq-hooks", System.nanoTime() - t);
        if (perf) t = System.nanoTime();
        tickScriptTasks(entry);
        if (perf) AgentPerformanceMonitor.record("common-script-tasks", System.nanoTime() - t);
        if (BotPqHooks.isNpcLocked(entry)) {
            return true;
        }
        if (perf) t = System.nanoTime();
        BotCombatManager.tickActionLock(entry);
        if (perf) AgentPerformanceMonitor.record("common-action-lock", System.nanoTime() - t);
        if (runAiTick) {
            if (perf) t = System.nanoTime();
            BotCombatManager.rebuildSkillCacheIfNeeded(entry, bot);
            if (perf) AgentPerformanceMonitor.record("common-skill-cache", System.nanoTime() - t);
            // Support healing is top priority — runs before buffs so that a bot below the heal
            // threshold casts Heal before a rebuff uses up this tick's action window. If it fires,
            // Agent combat cooldown state is set to the heal animation lock and tickActionLocked()
            // will return true, causing the caller to skip attack logic this tick.
            if (perf) t = System.nanoTime();
            BotCombatManager.tickSupportHealing(entry, bot);
            if (perf) AgentPerformanceMonitor.record("common-support-heal", System.nanoTime() - t);
            if (perf) t = System.nanoTime();
            BotCombatManager.tickBuffs(entry, bot);
            if (perf) AgentPerformanceMonitor.record("common-combat-buffs", System.nanoTime() - t);
            if (perf) t = System.nanoTime();
            BotBuffManager.tick(entry, bot);
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
        if (isSwimMap(entry) && AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)) {
            BotMovementManager.tickSwimming(entry, null);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            BotMovementManager.tickAirborne(entry, null);
        } else if (!AgentBotMovementStateRuntime.climbing(entry)) {
            int expectedIdleStance = BotPhysicsEngine.resolveIdleGroundStance(entry);
            if (BotPhysicsEngine.resolveStance(entry) != expectedIdleStance
                    || bot.getStance() != expectedIdleStance) {
                BotPhysicsEngine.idleOnGround(entry, bot);
                BotMovementManager.broadcastMovement(entry);
            }
        }
    }

    private boolean tickIdleEntry(BotEntry entry, Character bot) {
        if (AgentBotModeStateRuntime.following(entry) || AgentBotModeStateRuntime.grinding(entry) || AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)
                || AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry) || AgentBotShopStateRuntime.shopVisitPending(entry)) {
            return false;
        }
        if (isSwimMap(entry) && AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)) {
            BotMovementManager.tickSwimming(entry, null);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            BotMovementManager.tickAirborne(entry, null);
        } else if (!AgentBotMovementStateRuntime.climbing(entry)) {
            int expectedIdleStance = BotPhysicsEngine.resolveIdleGroundStance(entry);
            if (BotPhysicsEngine.resolveStance(entry) != expectedIdleStance
                    || bot.getStance() != expectedIdleStance) {
                BotPhysicsEngine.idleOnGround(entry, bot);
                BotMovementManager.broadcastMovement(entry);
            }
        }
        return true;
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

        boolean runAiTick = consumeAiTick(entry);
        AgentBotTickStateRuntime.recordTick(entry, runAiTick, tickAtMs);

        TargetSnapshot targetSnapshot = captureTargetSnapshot(entry);
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
            BotShopManager.onMapChange(entry, bot);
            AgentBotManagerStatusRuntime.checkManagerStatus(entry, bot);
            return;
        }

        // Shop visit: navigate to approach point before resuming normal flow.
        if (AgentBotShopStateRuntime.shopVisitPending(entry)) {
            boolean consumed = BotShopManager.tickShopVisit(entry, bot);
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
        if (BotFidgetManager.tryHandleTick(entry, steeringTarget, runAiTick)) {
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
        } else if (isSwimMap(entry) && AgentBotMovementStateRuntime.inAir(entry)) {
            BotMovementManager.tickSwimming(entry, targetPos);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            BotMovementManager.tickAirborne(entry, targetPos);
        } else {
            BotMovementManager.tickGrounded(entry, targetPos);
        }
    }

    private static boolean isSwimMap(BotEntry entry) {
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return map != null && map.isSwim();
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
        if (!AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            return false;
        }
        if (isSwimMap(entry) && AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)) {
            BotMovementManager.tickSwimming(entry, null);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            BotMovementManager.tickAirborne(entry, null);
        } else if (!AgentBotMovementStateRuntime.climbing(entry)) {
            // Ground physics must keep ticking during attack lock so prior walk momentum decays
            // via friction, walk-offs trigger falls, and broadcastMovement updates stance from
            // WALK→STAND. Without this the client extrapolates the last walk packet for the
            // entire animation lock and the bot visibly "walks in place" until cooldown ends.
            // External forces (mob knockback) replace this state via applyAirKnockback /
            // beginKnockback before this tick runs, so passing null target is safe.
            BotMovementManager.tickGrounded(entry, null);
        }
        return true;
    }


    void reloginBot(int charId, int ownerCharId, int world, int channel) {
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
        BotChatManager.handleChat(entry, message);
    }

    private boolean consumeAiTick(BotEntry entry) {
        return AgentBotTickCadenceStateRuntime.consumeAiTick(
                entry, BotMovementManager.cfg.TICK_MS, cfg.AI_TICK_MS);
    }

    // ===== Owned-bot accessors used by the androidequip.cpp BotEquipHandler =====
    /** Number of bots currently spawned (active) under this owner. */
    public int spawnedBotCount(int ownerCharId) {
        List<BotEntry> entries = bots.get(ownerCharId);
        return entries == null ? 0 : entries.size();
    }

    /** The Character objects of every spawned bot owned by the given player (empty if none). */
    public List<Character> getOwnedBotCharacters(int ownerCharId) {
        List<Character> result = new ArrayList<>();
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries != null) {
            for (BotEntry e : entries) {
                Character b = e.getBot();
                if (b != null) {
                    result.add(b);
                }
            }
        }
        return result;
    }

}
