package server.bots;

import client.BotClient;
import client.Character;
import client.Disease;
import client.QuestStatus;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
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
import server.bots.pq.BotPqHooks;
import server.life.Monster;
import server.life.MobSkill;
import server.maps.Foothold;
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
        public int   POT_CHECK_INTERVAL_MS = 2_000;
        public int   MP_RECOVERY_INTERVAL_MS = 10_000;
        public int   BASE_HP_RECOVERY = 10;
        public int   BASE_MP_RECOVERY = 3;
        public float AUTOPOT_HP_THRESH = 0.7f; // use HP pot when HP falls below this ratio
        public float AUTOPOT_MP_THRESH = 0.5f; // use MP pot when MP falls below this ratio

        // Follow stagger: each bot is offset this many px from the owner (index-based, alternating left/right)
        public int FOLLOW_STAGGER = 60;

        // Owner inactivity (offline or dead) before bot scrolls/warps to nearest town and idles.
        public long OWNER_INACTIVE_TOWN_RETURN_MS = 5L * 60_000L;
    }

    /** Singleton config — replace with `cfg = new Config()` after hotswapping to reset. */
    public static Config cfg = new Config();

    public static BotManager getInstance() { return instance; }

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
                          Point grindTargetPos,
                          Point primaryTargetPos,
                          String primaryTargetSource) {
        Point steeringTargetPos(BotEntry entry) {
            return entry.navTargetPos != null ? new Point(entry.navTargetPos) : new Point(primaryTargetPos);
        }

        String steeringTargetSource(BotEntry entry) {
            return entry.navTargetPos != null ? "nav-waypoint" : primaryTargetSource;
        }
    }

    private static final Pattern DISMISS_PATTERN = Pattern.compile(
            "\\b(dismiss|disown|release)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECRUIT_PATTERN = Pattern.compile(
            "\\b(recruit|adopt|hire|claim)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMATION_PATTERN = Pattern.compile(
            "\\b(?:formation|form)\\b(?:\\s+(stagger|split|random|stack|spread|tight|loose|left|right|snap)(?:\\s+(\\d+|tight|loose|on|off))?)?",
            Pattern.CASE_INSENSITIVE);
    // Reserve `give ...` for item requests handled by BotChatManager.
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "\\btransfer\\s+(\\S+)(?:\\s+to)?\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final int MIN_PREFIX_TARGET_LENGTH = 2;
    private static final int MAX_NUMERIC_TARGET_SLOT = 5;
    private static final int PLATFORM_EDGE_INSET_PX = 12;

    record BotTransferCommand(String botName, String targetName) {}

    private record TargetedBotCommand(String targetToken, String commandText) {}

    private record TargetedBotMatch(BotEntry entry, String commandText, String feedbackMessage) {}

    static BotTransferCommand matchBotTransferCommand(String message) {
        Matcher matcher = TRANSFER_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        return new BotTransferCommand(matcher.group(1), matcher.group(2));
    }

    private static TargetedBotCommand parseTargetedBotCommand(String message) {
        if (message == null) {
            return null;
        }

        String trimmed = message.stripLeading();
        if (trimmed.isEmpty()) {
            return null;
        }

        int separatorIndex = 0;
        while (separatorIndex < trimmed.length() && !isTargetSeparator(trimmed.charAt(separatorIndex))) {
            separatorIndex++;
        }
        if (separatorIndex == 0 || separatorIndex >= trimmed.length()) {
            return null;
        }

        String commandText = trimmed.substring(separatorIndex).replaceFirst("^[,!?\\s]+", "").trim();
        if (commandText.isEmpty()) {
            return null;
        }

        return new TargetedBotCommand(trimmed.substring(0, separatorIndex), commandText);
    }

    private static boolean isTargetSeparator(char ch) {
        return java.lang.Character.isWhitespace(ch) || ch == ',' || ch == '!' || ch == '?';
    }

    private static boolean isNumericTarget(String targetToken) {
        return !targetToken.isEmpty() && targetToken.chars().allMatch(java.lang.Character::isDigit);
    }

    private static String buildAmbiguousPrefixMessage(String prefix, List<BotEntry> matches) {
        StringBuilder message = new StringBuilder("Ambiguous bot prefix '")
                .append(prefix)
                .append("': ");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                message.append(", ");
            }
            message.append(i + 1).append(": ").append(matches.get(i).bot.getName());
        }
        message.append(". Use the full name or a slot number.");
        return message.toString();
    }

    private static TargetedBotMatch resolveTargetedBot(List<BotEntry> entries, String message) {
        if (entries == null || entries.isEmpty()) {
            return new TargetedBotMatch(null, null, null);
        }

        TargetedBotCommand targetedCommand = parseTargetedBotCommand(message);
        if (targetedCommand == null) {
            return new TargetedBotMatch(null, null, null);
        }

        String targetToken = targetedCommand.targetToken();
        for (BotEntry entry : entries) {
            if (entry.bot.getName().equalsIgnoreCase(targetToken)) {
                return new TargetedBotMatch(entry, targetedCommand.commandText(), null);
            }
        }

        if (isNumericTarget(targetToken)) {
            int slot = Integer.parseInt(targetToken);
            if (slot < 1 || slot > MAX_NUMERIC_TARGET_SLOT) {
                return new TargetedBotMatch(null, null, null);
            }
            if (slot > entries.size()) {
                return new TargetedBotMatch(null, null, "No bot in slot " + slot + ".");
            }
            return new TargetedBotMatch(entries.get(slot - 1), targetedCommand.commandText(), null);
        }

        if (targetToken.length() < MIN_PREFIX_TARGET_LENGTH) {
            return new TargetedBotMatch(null, null, null);
        }

        List<BotEntry> prefixMatches = new ArrayList<>();
        for (BotEntry entry : entries) {
            if (entry.bot.getName().regionMatches(true, 0, targetToken, 0, targetToken.length())) {
                prefixMatches.add(entry);
            }
        }

        if (prefixMatches.size() == 1) {
            return new TargetedBotMatch(prefixMatches.get(0), targetedCommand.commandText(), null);
        }
        if (prefixMatches.size() > 1) {
            return new TargetedBotMatch(null, null, buildAmbiguousPrefixMessage(targetToken, prefixMatches));
        }

        return new TargetedBotMatch(null, null, null);
    }

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
            Character siblingBot = sibling.bot;
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
            if (entry == null || entry.bot == null || entry.bot.getId() == target.getId()) {
                continue;
            }
            BotChatManager.queueBotSay(entry, randomReply(List.of(
                    "ok",
                    "k",
                    "sure",
                    "omw",
                    "got it",
                    "following " + target.getName(),
                    "ok, following " + target.getName()
            )));
            after(randMs(250, 750), () -> {
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
                issueFollow(entry, target);
            });
        }
        return true;
    }

    static String randomReply(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** Schedule {@code r} to run after {@code ms} milliseconds. */
    static ScheduledFuture<?> after(long ms, Runnable r) {
        return TimerManager.getInstance().schedule(r, ms);
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
        BotOwnershipService.ResolvedCharacter resolved = ownershipService.resolveCharacterByName(botName);
        if (resolved == null) {
            return SpawnResult.fail("No character named '" + botName + "' exists.");
        }
        if (resolved.isOnline() && !resolved.isOnlineAsBot()) {
            return SpawnResult.fail("'" + botName + "' is currently being played by a real player.");
        }
        BotOwnershipService.AuthorizationResult auth = ownershipService.ensureCanControl(owner, resolved);
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
                e.printStackTrace();
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
            if (e.bot.getId() == botCharId) return e;
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
        entry.deadUntil = 0;
        entry.lastMapId = spawnMap != null ? spawnMap.getId() : botChar.getMapId();
        if (spawnMap != null && spawnMap.getFootholds() != null) {
            entry.fhIndex = BotMovementManager.buildFhIndex(spawnMap);
            BotNavigationGraphProvider.warmGraphAsync(spawnMap, entry.movementProfile);
        }
        entry.skipDelayMs = 0;
        entry.aiTickAccumulatorMs = 0;
        entry.moveDir = 0;
        entry.movementBroadcastValid = false;
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
            if (e.bot.getId() == bot.getId()) { e.task.cancel(false); return true; }
            return false;
        });
        int botCharId = bot.getId();
        ScheduledFuture<?> task = TimerManager.getInstance().register(
                () -> tick(ownerCharId, botCharId), BotMovementManager.cfg.TICK_MS);
        BotEntry entry = new BotEntry(bot, owner, task);
        entry.movementProfile = BotMovementProfile.fromCharacter(bot);
        BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), entry.movementProfile);
        entries.add(entry);
        FormationState fs = ownerFormations.getOrDefault(ownerCharId, FormationState.defaultStagger());
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).followOffsetX = fs.offsetFor(i, entries.size());
        }
        if (normalizeSpawnState) {
            normalizeSpawnedBot(entry);
        }
        after(randMs(30_000, 31_000), () -> BotChatManager.checkBotStatus(entry, bot));
        return entry;
    }

    private void normalizeSpawnedBot(BotEntry entry) {
        Character bot = entry.bot;
        Point spawnPos = resolveSpawnPosition(bot.getMap(), bot.getPosition());
        if (bot.getHp() <= 0) {
            bot.updateHp(Math.max(1, bot.getCurrentMaxHp()));
        }

        BotPhysicsEngine.teleportTo(entry, bot, spawnPos != null ? spawnPos : bot.getPosition());
        BotMovementManager.resetEntryStateAfterTeleport(entry);
        entry.deadUntil = 0;
        entry.lastMapId = bot.getMapId();
        entry.fhIndex = BotMovementManager.buildFhIndex(bot.getMap());
        entry.skipDelayMs = 0;
        entry.aiTickAccumulatorMs = 0;
        entry.moveDir = 0;
        entry.movementBroadcastValid = false;
        BotMovementManager.broadcastMovement(entry);
        if (entry.owner != null) {
            joinBotToOwnerParty(entry.owner, bot);
        }
    }

    public void removeBot(int ownerCharId) {
        List<BotEntry> entries = bots.remove(ownerCharId);
        if (entries != null) entries.forEach(e -> e.task.cancel(false));
    }

    /** Cancel and remove a bot by the bot character's own ID (used during shutdown/disconnect). */
    public void removeBotByCharId(int botCharId) {
        for (List<BotEntry> entries : bots.values()) {
            entries.removeIf(e -> {
                if (e.bot.getId() == botCharId) { e.task.cancel(false); return true; }
                return false;
            });
        }
    }

    /** Disown a bot by name — cancels its AI tick and leaves it idle in the map. */
    public boolean dismissBot(int ownerCharId, String botName) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return false;
        BotEntry entry = getBotEntry(ownerCharId, botName);
        if (entry == null) return false;
        entries.remove(entry);
        entry.task.cancel(false);
        issueStop(entry);
        after(randMs(400, 600), () ->
                botSay(entry.bot, randomReply(List.of(
                        "ok", "sure", "alright", "gotcha",
                        "later!", "see ya", "take care", "cya", "peace out"))));
        return true;
    }

    /** Recruit an ownerless bot by name into the owner's group. Returns an error string on failure, null on success. */
    public String recruitBot(int ownerCharId, Character owner, String botName) {
        Character bot = findOwnerlessBot(botName, owner.getWorld());
        if (bot == null) return "No ownerless bot named '" + botName + "' found.";

        BotOwnershipService.AuthorizationResult auth =
                BotOwnershipService.getInstance().ensureCanControl(
                        owner,
                        new BotOwnershipService.ResolvedCharacter(
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

        BotOwnershipService.AuthorizationResult auth =
                BotOwnershipService.getInstance().ensureCanControl(
                        target,
                        new BotOwnershipService.ResolvedCharacter(
                                found.bot.getId(),
                                found.bot.getName(),
                                found.bot.getAccountID(),
                                found.bot));
        if (!auth.allowed()) {
            return auth.failureMessage();
        }

        // Disown from current owner
        Character bot = found.bot;
        entries.remove(found);
        found.task.cancel(false);
        issueStop(found);

        // Register under new owner
        registerBot(target.getId(), target, bot);
        after(randMs(700, 900), () ->
                botSay(bot, randomReply(List.of("ok!", "sure!", "hey " + target.getName() + "!", "hi " + target.getName() + "!"))));
        return null;
    }

    public Character getActiveOwnerByBotCharId(int botCharId) {
        for (List<BotEntry> entries : bots.values()) {
            for (BotEntry entry : entries) {
                if (entry.bot.getId() == botCharId) {
                    return entry.owner;
                }
            }
        }
        return null;
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
        return (entries != null && !entries.isEmpty()) ? entries.get(0).bot : null;
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
        if (ItemConstants.getInventoryType(item.getItemId()) != InventoryType.EQUIP) return;
        for (BotEntry entry : getBotEntries(owner.getId())) {
            BotOfferManager.notifyOwnerGainedEquip(entry, entry.bot, item);
        }
    }

    BotEntry getBotEntry(int ownerCharId, String botName) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null || botName == null) {
            return null;
        }

        for (BotEntry entry : entries) {
            if (entry.bot.getName().equalsIgnoreCase(botName)) {
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

    public void handleChat(Character owner, String message) {
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

        BotTransferCommand transferCommand = matchBotTransferCommand(message);
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
                if (fEntries != null && !fEntries.isEmpty()) BotChatManager.queueBotSay(fEntries.get(0), help);
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
                    if (fEntries != null && !fEntries.isEmpty()) BotChatManager.queueBotSay(fEntries.get(0), "snap: " + status);
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
                    BotChatManager.queueBotSay(fEntries.get(0), "snap: " + status);
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
                for (int i = 0; i < fEntries.size(); i++) fEntries.get(i).followOffsetX = fs.offsetFor(i, fEntries.size());
                if (!fEntries.isEmpty()) {
                    String label = typeStr.toLowerCase() + (px > 0 ? " " + px + "px" : "");
                    BotChatManager.queueBotSay(fEntries.get(0), "formation: " + label);
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
                owner.yellowMessage("Bot '" + name + "' disowned — now idle.");
            } else {
                owner.yellowMessage("No bot named '" + name + "' in your group.");
            }
            return;
        }

        // Name-prefix routing: "Jason pots?" → only Jason responds
        TargetedBotMatch targetedBot = resolveTargetedBot(entries, message);
        if (targetedBot.entry != null) {
            String followTargetToken = BotChatManager.matchFollowTarget(targetedBot.commandText());
            if (followTargetToken != null) {
                applyFollowTargetCommand(owner, List.of(targetedBot.entry), followTargetToken);
                return;
            }
            BotChatManager.handleChat(targetedBot.entry, targetedBot.commandText);
            return;
        }
        if (targetedBot.feedbackMessage != null) {
            owner.yellowMessage(targetedBot.feedbackMessage);
            return;
        }

        String followTargetToken = BotChatManager.matchFollowTarget(message);
        if (followTargetToken != null) {
            applyFollowTargetCommand(owner, entries, followTargetToken);
            return;
        }

        // No name prefix — broadcast to all bots
        for (BotEntry entry : entries) {
            BotChatManager.handleChat(entry, message);
        }
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

        TargetedBotMatch targetedBot = resolveTargetedBot(matches, message);
        if (targetedBot.entry != null) {
            return BotOfferManager.handlePendingOfferResponse(targetedBot.entry, speaker, targetedBot.commandText);
        }
        if (targetedBot.feedbackMessage != null) {
            speaker.dropMessage(5, targetedBot.feedbackMessage);
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
                && entry.pendingLootOfferRecipientId == speaker.getId()
                && entry.bot.getMapId() == speaker.getMapId();
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
        Character owner = entry.owner;
        if (owner == null) {
            return FormationState.defaultStagger();
        }
        return ownerFormations.getOrDefault(owner.getId(), FormationState.defaultStagger());
    }

    Character resolveFollowAnchor(BotEntry entry, Character owner) {
        if (owner == null) {
            return null;
        }

        int targetId = entry.followTargetId;
        if (targetId <= 0 || targetId == owner.getId() || targetId == entry.bot.getId()) {
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
            if (sibling.bot != null && sibling.bot.getId() == targetId && sibling.bot.isLoggedinWorld()) {
                return sibling.bot;
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
            entries.get(i).followOffsetX = formation.offsetFor(i, entries.size());
        }
    }

    TargetSnapshot captureTargetSnapshot(BotEntry entry) {
        Character bot = entry.bot;
        Character owner = entry.owner;
        Character followAnchor = resolveFollowAnchor(entry, owner);
        Point fallbackPos = bot.getPosition();
        Point rawOwnerPos = owner != null ? owner.getPosition() : fallbackPos;
        Point rawFollowAnchorPos = followAnchor != null ? followAnchor.getPosition() : rawOwnerPos;
        String followAnchorName = followAnchor != null ? followAnchor.getName() : "owner";
        FormationState formation = formationStateFor(entry);
        Point followBasePos = new Point(rawFollowAnchorPos.x + entry.followOffsetX, rawFollowAnchorPos.y);
        Point followTargetPos = resolveFollowTargetPos(followBasePos, followAnchor, rawFollowAnchorPos, formation.snapRange(), bot.getMap());
        Point rawShopTargetPos = entry.shopVisitPending
                ? (entry.shopTargetPos != null ? entry.shopTargetPos : entry.shopNpcPos)
                : null;
        Point shopTargetPos = rawShopTargetPos == null ? null : new Point(rawShopTargetPos);
        Point moveTargetPos = entry.moveTarget == null ? null : new Point(entry.moveTarget);
        Monster activeGrindTarget = entry.grindTarget != null
                && entry.grindTarget.isAlive()
                && entry.grindTarget.getMap() == bot.getMap()
                ? entry.grindTarget
                : null;
        Point grindTargetPos = activeGrindTarget == null ? null : new Point(activeGrindTarget.getPosition());
        Point primaryTargetPos;
        String primaryTargetSource;
        if (shopTargetPos != null) {
            primaryTargetPos = shopTargetPos;
            primaryTargetSource = "shop-target";
        } else if (moveTargetPos != null) {
            primaryTargetPos = moveTargetPos;
            primaryTargetSource = "move-target";
        } else if (grindTargetPos != null) {
            primaryTargetPos = grindTargetPos;
            primaryTargetSource = "grind-target";
        } else if (entry.grinding) {
            primaryTargetPos = fallbackPos;
            primaryTargetSource = "grind-idle";
        } else if (entry.following) {
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
                grindTargetPos,
                new Point(primaryTargetPos),
                primaryTargetSource);
    }

    private static final int RETREAT_HOLD_MS = 600;
    private static final int RETREAT_ARRIVAL_TOLERANCE_X = 25; // 50ms tick can't land on an exact pixel

    static Point selectGrindNavigationTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        if (entry == null || botPos == null || combatTargetPos == null) {
            return combatTargetPos;
        }

        Character bot = entry.bot;
        if (bot == null) {
            return combatTargetPos;
        }

        long now = System.currentTimeMillis();
        boolean retreatNeeded = BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(
                BotAttackExecutionProvider.getEquippedWeaponType(bot), botPos, combatTargetPos);

        // Hysteresis: a previously committed retreat keeps its goal until either the
        // hold expires, the bot has effectively arrived, or the bot wandered too far
        // from the hold pos for it to still be the right answer.
        if (entry.retreatHoldPos != null && now < entry.retreatHoldUntilMs) {
            int dxHold = Math.abs(entry.retreatHoldPos.x - botPos.x);
            if (dxHold <= RETREAT_ARRIVAL_TOLERANCE_X) {
                entry.retreatHoldUntilMs = 0L;
                entry.retreatHoldPos = null;
            } else if (dxHold > BotCombatManager.cfg.RANGED_RETREAT_DISTANCE_X * 2) {
                entry.retreatHoldUntilMs = 0L;
                entry.retreatHoldPos = null;
            } else {
                return new Point(entry.retreatHoldPos);
            }
        } else if (entry.retreatHoldPos != null) {
            entry.retreatHoldUntilMs = 0L;
            entry.retreatHoldPos = null;
        }

        if (!retreatNeeded) {
            return combatTargetPos;
        }

        // Prefer landing on a different walkable region than the target — mobs there can't
        // path to the bot without traversing a nav edge, while the bot can still shoot across.
        // Empty/sparse adjacent regions score highest. Cross-region uses the nav-edge
        // pipeline for stickiness, so no separate hysteresis is set here.
        Point crossRegionPos = selectCrossRegionRetreatTarget(entry, botPos, combatTargetPos);
        if (crossRegionPos != null) {
            return crossRegionPos;
        }

        Point retreatPos = BotAttackExecutionProvider.retreatTargetPosition(bot, botPos, combatTargetPos);
        if (shouldUseLocalCombatRetreatTarget(entry, botPos, combatTargetPos, retreatPos)) {
            entry.retreatHoldUntilMs = now + RETREAT_HOLD_MS;
            entry.retreatHoldPos = new Point(retreatPos);
            return retreatPos;
        }
        return combatTargetPos;
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
        if (entry.climbing || entry.inAir || entry.navEdge != null) {
            return null;
        }
        Character bot = entry.bot;
        MapleMap map = bot != null ? bot.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return null;
        }
        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map, entry.movementProfile);
        if (graph == null) {
            BotNavigationGraphProvider.warmGraphAsync(map, entry.movementProfile);
            return null;
        }

        int botRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        if (botRegionId < 0) {
            return null;
        }
        int targetRegionId = BotNavigationManager.resolveTargetRegionId(graph, entry, map, combatTargetPos);

        int projectileRange = BotCombatManager.CLIENT_PROJECTILE_BASE_RANGE
                + BotCombatManager.passiveProjectileRangeBonus(bot);
        int yReachable = BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_Y * 2;

        BotNavigationGraph.Edge bestEdge = null;
        int bestScore = Integer.MIN_VALUE;
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(botRegionId)) {
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
        if (entry.climbing || entry.inAir || entry.navEdge != null) {
            return false;
        }

        Character bot = entry.bot;
        MapleMap map = bot != null ? bot.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return false;
        }

        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map, entry.movementProfile);
        if (graph == null) {
            BotNavigationGraphProvider.warmGraphAsync(map, entry.movementProfile);
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

    // Main tick
    // -------------------------------------------------------------------------

    private void tick(int ownerCharId, int botCharId) {
        long startedAt = BotPerformanceMonitor.enabled() ? System.nanoTime() : 0L;
        try {
            tickCore(ownerCharId, botCharId);
        } finally {
            if (startedAt != 0L) {
                BotPerformanceMonitor.record("tick-total", System.nanoTime() - startedAt);
            }
        }
    }

    private void tickCore(int ownerCharId, int botCharId) {
        BotEntry entry = getBotEntry(ownerCharId, botCharId);
        if (entry == null) return;
        if (entry.airshowActive) return;
        if (entry.skipDelayMs > 0) {
            entry.skipDelayMs = BotMovementManager.tickDown(entry.skipDelayMs);
            return;
        }
        Character bot = entry.bot;

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
        if (nowMs - entry.lastHeartbeatAtMs >= 600_000L) {
            entry.lastHeartbeatAtMs = nowMs;
            bot.getClient().updateLastPacket();
            BotMovementManager.broadcastMovement(entry);
        }

        BotOfferManager.expirePendingOffer(entry);
        boolean runAiTick = consumeAiTick(entry);
        entry.lastTickWasAi = runAiTick;
        entry.lastTickAtMs = System.currentTimeMillis();

        Character owner = resolveTickOwner(entry, ownerCharId);
        if (handleOwnerOfflineOrDead(entry, bot, owner, nowMs, ownerCharId)) {
            return;
        }
        if (owner == null) {
            entry.following = false;
            if (groundAfterMapChange(entry, bot)) {
                return;
            }
            // Owner-offline pass-through: when entry.moveTarget is set (currently by
            // the offline-town cluster, but any caller of issueMoveTo) the bot still
            // walks toward it. Same field and movement core as the player "here"
            // command — only the tick gate differs because the regular pipeline is
            // tightly coupled to `owner` for combat/follow/heal logic that's all
            // skipped here.
            if (entry.moveTarget != null) {
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
        entry.lastOwnerPos = new Point(ownerPos); // raw owner pos before formation offset/snap — used by path logger
        Point targetPos = targetSnapshot.primaryTargetPos();

        // These run in all modes (idle, follow, grind)
        if (runCommonTickSystems(entry, bot, owner, runAiTick)) {
            return;
        }

        if (tickIdleEntry(entry, bot)) {
            return;
        }

        // Map change and teleport checks only apply when following a live anchor.
        // Shop visits are intentional same-map detours and must not be pulled back
        // to the owner while walking to the NPC.
        if (!entry.shopVisitPending && syncFollowMap(entry, bot, followAnchor)) {
            return;
        }
        // Teleport if hopelessly far — applies to both follow and grind (catches falling off map)
        if (recoverTeleportDistance(entry, bot, targetPos)) {
            return;
        }

        // On any map change (e.g. NPC-triggered portal): rebuild footholds, reset physics,
        // and snap to ground so the bot doesn't carry over airborne state from the previous map.
        if (entry.lastMapId != bot.getMapId()) {
            entry.fhIndex  = BotMovementManager.buildFhIndex(bot.getMap());
            entry.lastMapId = bot.getMapId();
            Point cur = bot.getPosition();
            Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
            BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
            BotMovementManager.resetEntryStateAfterTeleport(entry);
            BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), entry.movementProfile);
            BotMovementManager.broadcastMovement(entry);
            if (BotPqHooks.requiresGrind(entry, bot)) { issueGrind(entry); }
            else if (BotPqHooks.requiresFollow(entry, bot)) { issueFollowOwner(entry); }
            else { entry.kpq.stage5Claimed = false; } // left KPQ — reset for next run
            BotShopManager.onMapChange(entry, bot);
            BotChatManager.checkBotStatus(entry, bot);
            return;
        }

        // Shop visit: navigate to approach point before resuming normal flow.
        // Keep this ahead of follow/combat/grind logic so resupply movement is not
        // coupled to owner proximity.
        if (entry.shopVisitPending) {
            boolean consumed = BotShopManager.tickShopVisit(entry, bot);
            targetPos = entry.shopTargetPos != null ? entry.shopTargetPos : entry.shopNpcPos;
            if (!consumed && entry.shopApproachDelayMs > 0) {
                return;
            }
            if (targetPos != null) {
                stepMovementCore(entry, targetPos, runAiTick);
            }
            return;
        }

        // Follow mode: attack monsters already in attack range without chasing
        if (entry.following && !entry.noAmmo && runAiTick && !entry.climbing
                && followAnchor != null
                && bot.getMapId() == followAnchor.getMapId()
                && Math.abs(botPos.x - followAnchor.getPosition().x) <= BotMovementManager.cfg.FOLLOW_DIST * 5) {
            Monster followTarget = BotCombatManager.findFollowAttackTarget(entry, bot);
            if (followTarget != null) {
                Point followTargetPos = followTarget.getPosition();
                WeaponType followWeaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
                boolean followRetreat = entry.degenAttackDone
                        || BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(followWeaponType, botPos, followTargetPos)
                        || BotAttackExecutionProvider.isAnyMobNearerThanTarget(bot, botPos, followTargetPos);
                if (followRetreat) {
                    targetPos = selectGrindNavigationTarget(entry, botPos, followTargetPos);
                    entry.degenAttackDone = false;
                } else if (entry.inAir) {
                    // Mid-air (from a jump): attack bypasses moveWindowMs; attackCooldownMs still gates
                    BotCombatManager.AttackPlan ap = BotCombatManager.planAttack(entry, bot, followTarget);
                    if (BotCombatManager.canUseAttackPlanNow(entry, followWeaponType, ap)
                            && BotCombatManager.isTargetInAttackRange(ap, bot, followTarget)) {
                        BotCombatManager.attackMonster(entry, bot, ap);
                        if (ap.isCloseRangeRoute() && BotCombatManager.isRangedAmmoWeapon(followWeaponType)) {
                            entry.degenAttackDone = true;
                        }
                    }
                } else if (followWeaponType != WeaponType.BOW && followWeaponType != WeaponType.CROSSBOW
                        && followWeaponType != WeaponType.WAND && followWeaponType != WeaponType.STAFF
                        && BotCombatManager.isTargetJumpable(entry.movementProfile, true, botPos, followTargetPos)) {
                    // Target is above but within jump height — jump regardless of moveWindowMs
                    BotMovementManager.initiateJump(entry, bot, followTargetPos.x - botPos.x);
                    return;
                } else if (entry.moveWindowMs <= 0) {
                    BotCombatManager.AttackPlan ap = BotCombatManager.planAttack(entry, bot, followTarget);
                    if (BotCombatManager.isTargetInAttackRange(ap, bot, followTarget)) {
                        BotCombatManager.attackMonster(entry, bot, ap);
                        int followDx = Math.abs(botPos.x - targetSnapshot.followTargetPos().x);
                        entry.moveWindowMs = followDx > BotMovementManager.cfg.FOLLOW_DIST * 3 ? 1000
                                           : followDx > BotMovementManager.cfg.FOLLOW_DIST     ? 200
                                           : 0;
                        if (ap.isCloseRangeRoute() && BotCombatManager.isRangedAmmoWeapon(followWeaponType)) {
                            entry.degenAttackDone = true;
                        }
                        if (!entry.inAir) return;
                    }
                }
            }
        }

        if (tryFollowIdleMovementFastPath(entry, bot, targetPos, nowMs)) {
            return;
        }

        // Grind mode: navigate toward nearest monster, attack when in range
        if (entry.grinding) {
            // PQ nav override: walking to NPC or owner — skip monster seeking entirely.
            // Coupon-seeking (soft hint) is excluded: the bot should still fight mobs
            // opportunistically and only drift toward the coupon when idle.
            if (entry.kpq.navTarget != null && !BotPqHooks.isCouponSeeking(entry)) {
                targetPos = entry.kpq.navTarget;
                BotNavigationManager.NavigationDirective pqNav = BotNavigationManager.resolveTarget(entry, targetPos, runAiTick);
                if (!pqNav.consumedTick) {
                    if (entry.climbing) {
                        BotMovementManager.tickClimbing(entry, pqNav.targetPos, runAiTick);
                    } else if (isSwimMap(entry) && entry.inAir) {
                        BotMovementManager.tickSwimming(entry, pqNav.targetPos);
                    } else if (entry.inAir) {
                        BotMovementManager.tickAirborne(entry, pqNav.targetPos);
                    } else {
                        BotMovementManager.tickGrounded(entry, pqNav.targetPos);
                    }
                }
                return;
            }
            double seekRangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Monster target = entry.grindTarget;
            if (target == null || !target.isAlive()
                    || target.getMap() != bot.getMap()
                    || target.getPosition().distanceSq(botPos) > seekRangeSq) {
                target = null;
            }
            long now = System.currentTimeMillis();
            if (runAiTick && (target == null || now >= entry.nextGrindTargetSearchAtMs)) {
                target = BotCombatManager.findGrindTarget(entry, bot);
                entry.nextGrindTargetSearchAtMs = now + BotCombatManager.cfg.GRIND_RETARGET_INTERVAL_MS;
            }
            if (target == null) {
                entry.grindTarget = null;
                if (BotPqHooks.isCouponSeeking(entry)) {
                    // No mob in seek range — drift toward the nearby coupon drop instead of idling.
                    targetPos = entry.kpq.navTarget;
                    // falls through to stepMovementCore below
                } else if (isSwimMap(entry) && entry.inAir) {
                    BotMovementManager.tickSwimming(entry, targetPos);
                    return;
                } else if (entry.inAir) {
                    BotMovementManager.tickAirborne(entry, targetPos);
                    return;
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                    return;
                }
            }
            entry.grindTarget = target;
            Point tp = target.getPosition();
            // Crowding swap: if a closer mob is breaching the retreat band, attack THAT mob
            // instead of fleeing the original far target. The bot would have retreated either
            // way (the close mob also triggers retreat), but with the right target our shots
            // land on the actual threat instead of pointing at the far one.
            server.life.Monster closerThreat = BotAttackExecutionProvider.findCloserThreatMob(bot, botPos, tp);
            if (closerThreat != null && closerThreat != target) {
                target = closerThreat;
                entry.grindTarget = closerThreat;
                tp = target.getPosition();
            }
            BotCombatManager.AttackPlan attackPlan = BotCombatManager.planAttack(entry, bot, target);
            WeaponType grindWeaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
            boolean shouldRetreatForRangedSpacing = entry.degenAttackDone
                    || BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(grindWeaponType, botPos, tp);
            // Opportunity attack: keep firing during retreat as long as the shot would land
            // as a true ranged hit. Suppress only inside the degenerate band, since firing
            // there would re-trigger degenAttackDone and extend the retreat indefinitely.
            boolean canFireWithoutDegen = grindWeaponType == null
                    || !BotAttackExecutionProvider.shouldDegenerateRangedAttack(grindWeaponType, botPos, tp);
            boolean attackGateOpen = !shouldRetreatForRangedSpacing || canFireWithoutDegen;
            // Sticky cross-region retreat: pre-compute so an opportunity attack doesn't stall
            // the traversal — bot fires AND keeps walking toward the safe vantage in the same tick.
            Point crossRegionRetreatPos = shouldRetreatForRangedSpacing
                    ? selectCrossRegionRetreatTarget(entry, botPos, tp)
                    : null;

            if (!entry.climbing) {
                boolean couponSeeking = BotPqHooks.isCouponSeeking(entry);
                if (attackGateOpen && BotCombatManager.isTargetInAttackRange(attackPlan, bot, target)
                        && BotCombatManager.canUseAttackPlanNow(entry, grindWeaponType, attackPlan)
                        && (!couponSeeking || entry.moveWindowMs <= 0)) {
                    // In range — attack if grounded, or during ascent of a jump
                    int prevCooldown = entry.attackCooldownMs;
                    BotCombatManager.attackMonster(entry, bot, attackPlan);
                    boolean attacked = entry.attackCooldownMs != prevCooldown;
                    // After attacking in coupon-seek mode, add a movement window based on
                    // distance to the coupon so the bot walks toward it between attacks.
                    if (attacked && couponSeeking && entry.kpq.navTarget != null) {
                        int couponDx = Math.abs(botPos.x - entry.kpq.navTarget.x);
                        entry.moveWindowMs = couponDx > BotMovementManager.cfg.FOLLOW_DIST * 3 ? 1000
                                           : couponDx > BotMovementManager.cfg.FOLLOW_DIST     ? 200
                                           : 0;
                    }
                    // If a ranged bot just did a degenerate close-range hit, force retreat next tick
                    if (attacked && attackPlan.isCloseRangeRoute()
                            && BotCombatManager.isRangedAmmoWeapon(grindWeaponType)) {
                        entry.degenAttackDone = true;
                    }
                    // Don't short-circuit when a cross-region retreat is in progress — the
                    // bot must still walk to the edge launch this tick.
                    if (attacked && !entry.inAir && crossRegionRetreatPos == null) return;
                } else if (!entry.inAir
                        && BotCombatManager.isTargetJumpable(entry.movementProfile, attackPlan.isCloseRangeRoute(), botPos, tp)
                        && grindWeaponType != WeaponType.BOW && grindWeaponType != WeaponType.CROSSBOW
                        && grindWeaponType != WeaponType.WAND && grindWeaponType != WeaponType.STAFF) {
                    // Target is above but within jump height — jump toward it
                    BotMovementManager.initiateJump(entry, bot, tp.x - botPos.x);
                    return;
                }
            }
            // Soft kite: pre-emptive step away when target is closing through the comfort
            // band just outside the retreat trigger. Only valid sample if the target hasn't
            // changed since last tick (swap or kill resets the baseline).
            int sampledDx = (target.getObjectId() == entry.lastTargetObjectId) ? entry.lastTargetDx : -1;
            boolean softKite = !shouldRetreatForRangedSpacing && crossRegionRetreatPos == null
                    && !entry.inAir && !entry.climbing
                    && BotAttackExecutionProvider.shouldSoftKite(grindWeaponType, botPos, tp, sampledDx);
            entry.lastTargetDx = Math.abs(tp.x - botPos.x);
            entry.lastTargetObjectId = target.getObjectId();

            // Retreat positioning is a local combat adjustment, not an inter-region path target.
            // Feeding a synthetic same-Y retreat point into nav while the monster is elsewhere
            // can make rope/ladder bots path back onto the nearby foothold instead of toward
            // the monster's actual region.
            targetPos = crossRegionRetreatPos != null
                    ? crossRegionRetreatPos
                    : softKite
                        ? BotAttackExecutionProvider.softKiteTargetPosition(bot, botPos, tp)
                        : selectGrindNavigationTarget(entry, botPos, tp);
            if (entry.degenAttackDone && shouldRetreatForRangedSpacing) {
                entry.degenAttackDone = false;
            }
        }

        stepMovementCore(entry, targetPos, runAiTick);
    }

    private Character resolveTickOwner(BotEntry entry, int ownerCharId) {
        Character owner = entry.owner;
        if (owner == null || owner.getId() != ownerCharId || !owner.isLoggedinWorld()) {
            owner = Server.getInstance()
                    .getWorld(entry.bot.getWorld())
                    .getPlayerStorage()
                    .getCharacterById(ownerCharId);
            entry.owner = owner;
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
            if (entry.ownerAwaySafeMode && entry.ownerOfflineOrDeadSinceMs == 0) {
                return false;
            }
            if (entry.ownerOfflineOrDeadSinceMs != 0 || entry.ownerReturnedToTown) {
                boolean justReturnedFromTown = entry.ownerReturnedToTown;
                entry.ownerOfflineOrDeadSinceMs = 0;
                entry.ownerReturnedToTown = false;
                entry.ownerAwaySafeMode = false;
                // Cancel any in-flight cluster walk: while owner was offline the
                // only setter of moveTarget was the offline-town path, so clearing
                // here is safe and avoids stale state when owner reconnects.
                entry.moveTarget = null;
                entry.moveTargetPrecise = false;
                // Race-safe single-representative wb: each bot tries to remove the
                // group's anchor entry; only the winner (first to observe owner
                // active) speaks. Other bots find the anchor already cleared and
                // skip the announcement, so the party sees one wb line, not N.
                Point removedAnchor = townClusterAnchors.remove(ownerCharId);
                if (justReturnedFromTown && removedAnchor != null) {
                    BotChatManager.announceOwnerReturnedFromOffline(entry);
                }
            }
            return false;
        }

        if (entry.ownerReturnedToTown) {
            if (entry.ownerAwaySafeMode && entry.ownerOfflineOrDeadSinceMs == 0) {
                entry.ownerOfflineOrDeadSinceMs = nowMs;
            }
            return false;
        }

        if (entry.ownerOfflineOrDeadSinceMs == 0) {
            entry.ownerOfflineOrDeadSinceMs = nowMs;
            return false;
        }

        if (nowMs - entry.ownerOfflineOrDeadSinceMs < cfg.OWNER_INACTIVE_TOWN_RETURN_MS) {
            return false;
        }

        return enterOwnerInactiveSafeMode(entry, bot, ownerCharId, shouldTownWarpForOwnerInactive(entry));
    }

    private boolean shouldTownWarpForOwnerInactive(BotEntry entry) {
        MapleMap currentMap = entry != null && entry.bot != null ? entry.bot.getMap() : null;
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
                && entry.owner != null
                && getFirstBotEntry(entry.owner.getId()) == entry;
    }

    public void issueOwnerAwaySafeModeForOwner(int ownerCharId, boolean town) {
        for (BotEntry entry : getBotEntries(ownerCharId)) {
            if (entry.bot == null || entry.bot.getMap() == null) {
                continue;
            }
            enterOwnerInactiveSafeMode(entry, entry.bot, ownerCharId,
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
        entry.ownerReturnedToTown = true;
        return false;
    }

    private void prepareOwnerInactiveIdle(BotEntry entry, int ownerCharId) {
        clearScriptTasks(entry);
        BotShopManager.cancelShopVisit(entry);
        clearMode(entry);
        entry.moveTarget = null;
        entry.moveTargetPrecise = false;
        entry.grindTarget = null;
        entry.degenAttackDone = false;
        entry.buffConsumablesEnabled = false;
        entry.ownerAwaySafeMode = true;
        townClusterAnchors.remove(ownerCharId);
    }

    private boolean scrollBotToTown(BotEntry entry, Character bot, int ownerCharId) {
        MapleMap currentMap = bot.getMap();
        if (currentMap == null) {
            return false;
        }
        MapleMap returnMap = currentMap.getReturnMap();
        if (returnMap == null || returnMap.getId() == currentMap.getId()) {
            // No return map (e.g. some PQ/town maps): mark handled to avoid re-evaluating every tick.
            entry.ownerReturnedToTown = true;
            return false;
        }

        BotPhysicsEngine.idleOnGround(entry, bot);

        // Every bot uses the same path: applyTo handles scroll-consume + random
        // portal warp. Falls back to a plain changeMap when the bot has no scroll.
        if (!tryUseReturnScroll(bot)) {
            bot.changeMap(returnMap);
        }

        // Capture the cluster anchor at the first bot's post-warp position.
        // Subsequent bots get a random ±150px X offset around the anchor as their
        // physical move target via the same machinery as the player "here" command.
        Point post = new Point(bot.getPosition());
        Point prior = townClusterAnchors.putIfAbsent(ownerCharId, post);
        if (prior != null) {
            int offsetX = ThreadLocalRandom.current().nextInt(-150, 151);
            Point candidate = new Point(prior.x + offsetX, prior.y - 1);
            Point ground = BotPhysicsEngine.findGroundPoint(returnMap, candidate);
            issueMoveTo(entry, ground != null ? ground : new Point(prior), true);
        }

        BotMovementManager.resetEntryState(entry);
        issueStop(entry);
        entry.ownerReturnedToTown = true;
        return true;
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
        entry.moveTarget = new Point(dest);
        entry.moveTargetPrecise = precise;
    }

    /**
     * Public hook: return the bot to ordinary owner-follow mode. Scripted map
     * automation and chat commands should use this instead of writing mode
     * fields directly.
     */
    public void issueFollowOwner(BotEntry entry) {
        issueFollow(entry, entry != null ? entry.owner : null);
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
        Character owner = entry.owner;
        entry.followTargetId = owner != null && target != null && owner.getId() != target.getId()
                ? target.getId()
                : 0;
        entry.grinding = false;
        entry.moveTarget = null;
        entry.moveTargetPrecise = false;
        entry.following = true;
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
        entry.followTargetId = 0;
        entry.following = false;
        entry.moveTarget = null;
        entry.moveTargetPrecise = false;
        entry.grinding = true;
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
        entry.moveTarget = null;
        entry.moveTargetPrecise = false;
    }

    /**
     * Public hook for map scripts: drop up to {@code quantity} from the first
     * stack of {@code itemId}. Use {@code quantity <= 0} to drop the whole stack.
     */
    public boolean issueDropItem(BotEntry entry, InventoryType type, int itemId, short quantity) {
        if (entry == null || entry.bot == null || type == null) {
            return false;
        }
        var inventory = entry.bot.getInventory(type);
        if (inventory == null) {
            return false;
        }
        Item item = inventory.findById(itemId);
        if (item == null || item.getQuantity() <= 0) {
            return false;
        }
        short dropQuantity = quantity <= 0 ? item.getQuantity() : (short) Math.min(quantity, item.getQuantity());
        InventoryManipulator.drop(entry.bot.getClient(), type, item.getPosition(), dropQuantity);
        return true;
    }

    public void clearScriptTasks(BotEntry entry) {
        if (entry == null) {
            return;
        }
        entry.scriptTasks.clear();
        entry.activeScriptTask = null;
    }

    public void queueTask(BotEntry entry, BotTask task) {
        if (entry == null || task == null) {
            return;
        }
        entry.scriptTasks.add(task);
    }

    public void queueMoveTo(BotEntry entry, Point point, boolean precise) {
        queueTask(entry, BotTask.moveTo(point, precise));
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
        return entry != null && (entry.activeScriptTask != null || !entry.scriptTasks.isEmpty());
    }

    private static void clearMode(BotEntry entry) {
        entry.followTargetId = 0;
        entry.following = false;
        entry.grinding = false;
    }

    private static boolean isNear(Point source, Point target, int dist) {
        return source != null && target != null
                && Math.abs(source.x - target.x) <= dist
                && Math.abs(source.y - target.y) <= dist;
    }

    private void tickScriptTasks(BotEntry entry) {
        if (entry == null || entry.bot == null) {
            return;
        }

        while (true) {
            if (entry.activeScriptTask == null) {
                entry.activeScriptTask = entry.scriptTasks.poll();
                if (entry.activeScriptTask == null) {
                    return;
                }
                startScriptTask(entry, entry.activeScriptTask);
            }

            if (!isScriptTaskComplete(entry, entry.activeScriptTask)) {
                return;
            }
            entry.activeScriptTask = null;
        }
    }

    private void startScriptTask(BotEntry entry, BotTask task) {
        switch (task.type) {
            case MOVE_TO -> startMoveTo(entry, task.point, task.precise);
            case FOLLOW_OWNER -> startFollow(entry, entry.owner);
            case FOLLOW_TARGET -> startFollow(entry, resolveFollowCharacterById(entry, task.targetCharacterId));
            case FOLLOW_UNTIL_NEAR -> startFollow(entry, resolveFollowCharacterById(entry, task.targetCharacterId));
            case GRIND -> startGrind(entry);
            case STOP -> startStop(entry);
            case DROP_ITEM -> issueDropItem(entry, task.inventoryType, task.itemId, task.quantity);
        }
    }

    private boolean isScriptTaskComplete(BotEntry entry, BotTask task) {
        return switch (task.type) {
            case MOVE_TO -> entry.moveTarget == null || isNear(entry.bot.getPosition(), task.point,
                    task.precise ? 8 : BotMovementManager.cfg.STOP_DIST);
            case FOLLOW_UNTIL_NEAR -> {
                Character target = resolveFollowCharacterById(entry, task.targetCharacterId);
                yield target != null
                        && entry.bot.getMapId() == target.getMapId()
                        && isNear(entry.bot.getPosition(), target.getPosition(), task.nearPx);
            }
            case FOLLOW_OWNER, FOLLOW_TARGET, GRIND, STOP, DROP_ITEM -> true;
        };
    }

    private Character resolveFollowCharacterById(BotEntry entry, int targetCharacterId) {
        if (entry == null || targetCharacterId <= 0) {
            return entry != null ? entry.owner : null;
        }
        if (entry.owner != null && entry.owner.getId() == targetCharacterId) {
            return entry.owner;
        }
        if (entry.owner != null && entry.owner.getParty() != null) {
            for (Character member : entry.owner.getPartyMembersOnline()) {
                if (member != null && member.getId() == targetCharacterId && member.isLoggedinWorld()) {
                    return member;
                }
            }
        }
        if (entry.owner != null) {
            for (BotEntry sibling : getBotEntries(entry.owner.getId())) {
                if (sibling.bot != null && sibling.bot.getId() == targetCharacterId && sibling.bot.isLoggedinWorld()) {
                    return sibling.bot;
                }
            }
        }
        return entry.owner;
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
     * Owner-offline tick path for entry.moveTarget — drives the bot to its
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
        stepMovementCore(entry, entry.moveTarget, runAiTick);
    }

    private boolean groundAfterMapChange(BotEntry entry, Character bot) {
        if (entry.lastMapId == bot.getMapId()) {
            return false;
        }

        entry.fhIndex = BotMovementManager.buildFhIndex(bot.getMap());
        entry.lastMapId = bot.getMapId();
        Point cur = bot.getPosition();
        Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
        BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
        BotMovementManager.resetEntryStateAfterTeleport(entry);
        BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), entry.movementProfile);
        BotMovementManager.broadcastMovement(entry);
        return true;
    }

    private boolean handleDeadTick(BotEntry entry, Character bot, Character owner) {
        if (entry.deadUntil == 0 && bot.getHp() <= 0) {
            BotCombatManager.enterDeadState(entry, bot, false);
        }
        if (entry.deadUntil == 0) {
            return false;
        }
        if (System.currentTimeMillis() >= entry.deadUntil) {
            respawnBot(entry, bot, owner);
        }
        return true;
    }

    private boolean runCommonTickSystems(BotEntry entry, Character bot, Character owner, boolean runAiTick) {
        BotCombatManager.tickMobDamage(entry, bot);
        if (bot.getHp() <= 0) {
            if (entry.deadUntil == 0) {
                BotCombatManager.enterDeadState(entry, bot, false);
            }
            return true;
        }
        tickReleaseMonsterControl(bot);
        BotInventoryManager.tickPassiveLoot(entry, bot);
        BotPotionManager.tickPotionCheck(entry, bot);
        BotPotionManager.tickPassiveRecovery(entry, bot);
        BotBuildManager.checkLevelUp(entry, bot);
        BotChatManager.tickAfkCheck(entry, owner);
        BotInventoryManager.tickTrade(entry, bot);
        BotInventoryManager.tickManualTrade(entry, bot);
        BotPqHooks.tick(entry, bot, owner);
        tickScriptTasks(entry);
        if (BotPqHooks.isNpcLocked(entry)) {
            return true;
        }
        BotCombatManager.tickActionLock(entry);
        if (runAiTick) {
            BotCombatManager.rebuildSkillCacheIfNeeded(entry, bot);
            // Support healing is top priority — runs before buffs so that a bot below the heal
            // threshold casts Heal before a rebuff uses up this tick's action window. If it fires,
            // entry.attackCooldownMs is set to the heal animation lock and tickActionLocked() will
            // return true, causing the caller to skip attack logic this tick.
            BotCombatManager.tickSupportHealing(entry, bot);
            BotCombatManager.tickBuffs(entry, bot);
            BotBuffManager.tick(entry, bot);
        }
        return tickActionLocked(entry);
    }

    private boolean tickIdleEntry(BotEntry entry, Character bot) {
        if (entry.following || entry.grinding || entry.moveTarget != null || entry.shopVisitPending) {
            return false;
        }
        if (isSwimMap(entry) && entry.inAir && !entry.climbing) {
            BotMovementManager.tickSwimming(entry, null);
        } else if (entry.inAir) {
            BotMovementManager.tickAirborne(entry, null);
        } else if (!entry.climbing) {
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
        if (!entry.following || followAnchor == null || bot.getMapId() == followAnchor.getMapId()) {
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
        if (entry == null || entry.bot == null) {
            return false;
        }

        boolean runAiTick = consumeAiTick(entry);
        entry.lastTickWasAi = runAiTick;
        entry.lastTickAtMs = tickAtMs;

        TargetSnapshot targetSnapshot = captureTargetSnapshot(entry);
        Point ownerPos = targetSnapshot.rawOwnerPos();
        updateObservedOwnerMotion(entry, ownerPos);
        entry.lastOwnerPos = new Point(ownerPos);
        stepMovementOnly(entry, targetSnapshot.primaryTargetPos(), ownerPos, runAiTick);
        return runAiTick;
    }

    void stepMovementOnly(BotEntry entry,
                          Point targetPos,
                          Point ownerPos,
                          boolean runAiTick) {
        if (entry == null || entry.bot == null || targetPos == null) {
            return;
        }

        Character bot = entry.bot;
        Character owner = entry.owner;

        if (tickIdleEntry(entry, bot)) {
            return;
        }

        if (owner != null && !entry.shopVisitPending && syncFollowMap(entry, bot, owner)) {
            return;
        }
        if (recoverTeleportDistance(entry, bot, targetPos)) {
            return;
        }

        if (entry.lastMapId != bot.getMapId()) {
            entry.fhIndex  = BotMovementManager.buildFhIndex(bot.getMap());
            entry.lastMapId = bot.getMapId();
            Point cur = bot.getPosition();
            Point ground = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(cur.x, cur.y - 1));
            BotPhysicsEngine.teleportTo(entry, bot, ground != null ? ground : cur);
            BotMovementManager.resetEntryStateAfterTeleport(entry);
            BotMovementManager.broadcastMovement(entry);
            BotShopManager.onMapChange(entry, bot);
            BotChatManager.checkBotStatus(entry, bot);
            return;
        }

        // Shop visit: navigate to approach point before resuming normal flow.
        if (entry.shopVisitPending) {
            boolean consumed = BotShopManager.tickShopVisit(entry, bot);
            targetPos = entry.shopTargetPos != null ? entry.shopTargetPos : entry.shopNpcPos;
            if (!consumed && entry.shopApproachDelayMs > 0) {
                return;
            }
            if (targetPos != null) {
                stepMovementCore(entry, targetPos, runAiTick);
            }
            return;
        }

        if (tryFollowIdleMovementFastPath(entry, bot, targetPos, entry.lastTickAtMs)) {
            return;
        }

        stepMovementCore(entry, targetPos, runAiTick);
    }

    static boolean tryFollowIdleMovementFastPath(BotEntry entry, Character bot, Point targetPos, long nowMs) {
        if (!isFollowIdleMovementFastPathEligible(entry, bot, targetPos)) {
            return false;
        }

        if (entry.nextFollowIdleMovementCheckAtMs == 0L) {
            entry.nextFollowIdleMovementCheckAtMs = nowMs + 1000L;
        } else if (nowMs >= entry.nextFollowIdleMovementCheckAtMs) {
            entry.nextFollowIdleMovementCheckAtMs = nowMs + 1000L;
            return false;
        }

        entry.lastNavDecision = "idle-fast";
        entry.stuckMs = 0;
        entry.stuckCheckX = Integer.MIN_VALUE;
        return true;
    }

    private static boolean isFollowIdleMovementFastPathEligible(BotEntry entry, Character bot, Point targetPos) {
        if (entry == null || bot == null || targetPos == null) {
            return false;
        }
        if (!entry.following || entry.grinding || entry.moveTarget != null) {
            return false;
        }
        if (entry.inAir || entry.climbing || entry.downJumpPending || entry.graphWarmupFallback) {
            return false;
        }
        if (entry.navEdge != null || entry.navPreciseTarget || entry.fidgetMode != BotFidgetMode.NONE) {
            return false;
        }
        if (entry.shopVisitPending || entry.shopSequenceActive || entry.kpq.navTarget != null) {
            return false;
        }
        if (entry.wasMovingX || entry.moveDir != 0 || entry.movementVelX != 0 || entry.movementVelY != 0) {
            return false;
        }
        if (entry.observedOwnerStepX != 0 || entry.observedOwnerStepY != 0) {
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
        if (entry.moveTargetPrecise && entry.navEdge == null) {
            entry.navPreciseTarget = true;
        }
        if (BotFidgetManager.tryHandleTick(entry, steeringTarget, runAiTick)) {
            return;
        }

        tickMovementPhase(entry, steeringTarget, runAiTick);
        if (runAiTick && !entry.inAir && !entry.climbing) {
            BotNavigationManager.tryExecuteCommittedEdgeAfterGroundMovement(entry, targetPos);
        }
        tickStuckDetection(entry);
        clearReachedMoveTarget(entry);
    }

    private void tickMovementPhase(BotEntry entry, Point targetPos, boolean runAiTick) {
        if (entry.climbing) {
            BotMovementManager.tickClimbing(entry, targetPos, runAiTick);
        } else if (isSwimMap(entry) && entry.inAir) {
            BotMovementManager.tickSwimming(entry, targetPos);
        } else if (entry.inAir) {
            BotMovementManager.tickAirborne(entry, targetPos);
        } else {
            BotMovementManager.tickGrounded(entry, targetPos);
        }
    }

    private static boolean isSwimMap(BotEntry entry) {
        return entry.bot != null && entry.bot.getMap() != null && entry.bot.getMap().isSwim();
    }

    private void clearReachedMoveTarget(BotEntry entry) {
        if (entry.moveTarget == null) {
            return;
        }
        Point botPos = entry.bot.getPosition();
        int arrivalDist = entry.moveTargetPrecise ? 8 : BotMovementManager.cfg.STOP_DIST;
        if (Math.abs(botPos.x - entry.moveTarget.x) <= arrivalDist
                && Math.abs(botPos.y - entry.moveTarget.y) <= arrivalDist) {
            entry.moveTarget = null;
            entry.moveTargetPrecise = false;
        }
    }

    private static void updateObservedOwnerMotion(BotEntry entry, Point ownerPos) {
        if (entry == null || ownerPos == null) {
            return;
        }
        entry.observedOwnerStepX = entry.lastOwnerPos == null ? 0 : ownerPos.x - entry.lastOwnerPos.x;
        entry.observedOwnerStepY = entry.lastOwnerPos == null ? 0 : ownerPos.y - entry.lastOwnerPos.y;
    }

    private static void tickStuckDetection(BotEntry entry) {
        entry.unstuckCooldownMs = BotMovementManager.tickDown(entry.unstuckCooldownMs);

        // Only detect/act while actively navigating — idling near owner is not stuck.
        if (entry.inAir || entry.climbing
                || entry.graphWarmupFallback
                || (entry.navEdge == null && entry.moveTarget == null)) {
            entry.stuckMs = 0;
            entry.stuckCheckX = Integer.MIN_VALUE;
            return;
        }

        Point botPos = entry.bot.getPosition();
        if (entry.stuckCheckX == Integer.MIN_VALUE) {
            entry.stuckCheckX = botPos.x;
            entry.stuckCheckY = botPos.y;
            return;
        }

        boolean moved = Math.abs(botPos.x - entry.stuckCheckX) > 8
                || Math.abs(botPos.y - entry.stuckCheckY) > 8;
        if (moved) {
            entry.stuckMs = 0;
            entry.stuckCheckX = botPos.x;
            entry.stuckCheckY = botPos.y;
        } else {
            entry.stuckMs += BotPhysicsEngine.cfg.TICK_MS;
        }

        if (entry.stuckMs >= 500 && entry.unstuckCooldownMs == 0) {
            entry.stuckMs = 0;
            entry.stuckCheckX = Integer.MIN_VALUE;
            BotMovementManager.tickUnstuck(entry);
        }
    }

    private boolean tickActionLocked(BotEntry entry) {
        if (entry.attackCooldownMs <= 0) {
            return false;
        }
        if (isSwimMap(entry) && entry.inAir && !entry.climbing) {
            BotMovementManager.tickSwimming(entry, null);
        } else if (entry.inAir) {
            BotMovementManager.tickAirborne(entry, null);
        } else if (!entry.climbing) {
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
            after(randMs(900, 1100), () -> {
                botSay(botChar, "back!!");
                botChar.changeFaceExpression(Emote.HAPPY.getValue());
            });
        } catch (SQLException e) {
            log.warn("reloginBot: failed to reload charId={}", charId, e);
        }
    }

    private void respawnBot(BotEntry entry, Character bot, Character owner) {
        entry.deadUntil = 0;
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
        bot.changeFaceExpression(Emote.GLARE.getValue());
    }

    // -------------------------------------------------------------------------
    // Monster control hand-off
    // -------------------------------------------------------------------------

    /**
     * Bots can't drive mob AI (BotClient.sendPacket is a no-op), so any monster
     * assigned to a bot as controller would freeze. Hand off immediately to the
     * nearest real player, or release control if no real player is in the map.
     */
    private static void tickReleaseMonsterControl(Character bot) {
        java.util.Collection<Monster> controlled = bot.getControlledMonsters();
        if (controlled.isEmpty()) return;

        Character realPlayer = null;
        for (Character chr : bot.getMap().getAllPlayers()) {
            if (!(chr.getClient() instanceof BotClient)) {
                realPlayer = chr;
                break;
            }
        }

        for (Monster monster : controlled) {
            if (realPlayer != null) {
                monster.aggroSwitchController(realPlayer, false);
            } else {
                monster.aggroRemoveController();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    void botSay(Character bot, String text) {
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), text, false, 0));
    }

    /**
     * Broadcasts via party chat so the owner sees the message even when they're
     * on a different map. Falls back to map chat if the bot has no party.
     */
    void botSayParty(Character bot, String text) {
        Party party = bot.getParty();
        if (party != null && bot.getClient() != null && bot.getClient().getWorldServer() != null) {
            bot.getClient().getWorldServer().partyChat(party, text, bot.getName());
        } else {
            botSay(bot, text);
        }
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
        BotChatManager.handleChat(entry, message);
    }

    private boolean consumeAiTick(BotEntry entry) {
        entry.aiTickAccumulatorMs += BotMovementManager.cfg.TICK_MS;
        if (entry.aiTickAccumulatorMs < cfg.AI_TICK_MS) {
            return false;
        }

        entry.aiTickAccumulatorMs -= cfg.AI_TICK_MS;
        return true;
    }

}
