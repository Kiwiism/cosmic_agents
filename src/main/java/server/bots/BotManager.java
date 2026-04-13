package server.bots;

import client.BotClient;
import client.Character;
import client.Disease;
import client.QuestStatus;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.game.CharacterStance;
import constants.inventory.ItemConstants;
import net.server.Server;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.PartyOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    }

    /** Singleton config — replace with `cfg = new Config()` after hotswapping to reset. */
    public static Config cfg = new Config();

    public static BotManager getInstance() { return instance; }

    // ownerCharId → list of owned bot entries (1:N)
    private final Map<Integer, List<BotEntry>> bots = new ConcurrentHashMap<>();
    // ownerCharId → current formation (in-memory only, defaults to stagger)
    private final Map<Integer, FormationState> ownerFormations = new ConcurrentHashMap<>();
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
                entry.following = true;
            }
            return SpawnResult.ok(botChar, auth.autoRegistered());
        } else {
            try {
                Character botChar = loadOfflineBot(resolved.id(), owner.getClient().getWorld(), owner.getClient().getChannel(), map, pos);
                BotEntry entry = registerSpawnedBot(owner.getId(), owner, botChar);
                entry.following = true;
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
        entry.lastDesiredDirection = 0;
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
        entry.lastDesiredDirection = 0;
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
        entry.following = false;
        entry.grinding  = false;
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
        found.following = false;
        found.grinding  = false;

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
            BotChatManager.handleChat(targetedBot.entry, targetedBot.commandText);
            return;
        }
        if (targetedBot.feedbackMessage != null) {
            owner.yellowMessage(targetedBot.feedbackMessage);
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
                        BotNavigationGraph.Region nearestRope = findNearestRopeAtY(graph, targetX, ownerPos.y);
                        if (nearestRope != null) {
                            return new Point(nearestRope.minX, ownerPos.y);
                        }
                        return new Point(ownerPos.x, ownerPos.y);
                    } else {
                        int clampedX = Math.max(ownerRegion.minX, Math.min(ownerRegion.maxX, targetX));
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
        Point fallbackPos = bot.getPosition();
        Point rawOwnerPos = owner != null ? owner.getPosition() : fallbackPos;
        FormationState formation = formationStateFor(entry);
        Point followBasePos = new Point(rawOwnerPos.x + entry.followOffsetX, rawOwnerPos.y);
        Point followTargetPos = resolveFollowTargetPos(followBasePos, owner, rawOwnerPos, formation.snapRange(), bot.getMap());
        Point moveTargetPos = entry.moveTarget == null ? null : new Point(entry.moveTarget);
        Monster activeGrindTarget = entry.grindTarget != null
                && entry.grindTarget.isAlive()
                && entry.grindTarget.getMap() == bot.getMap()
                ? entry.grindTarget
                : null;
        Point grindTargetPos = activeGrindTarget == null ? null : new Point(activeGrindTarget.getPosition());
        Point primaryTargetPos;
        String primaryTargetSource;
        if (moveTargetPos != null) {
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
                new Point(followBasePos),
                new Point(followTargetPos),
                moveTargetPos,
                grindTargetPos,
                new Point(primaryTargetPos),
                primaryTargetSource);
    }

    static Point selectGrindNavigationTarget(BotEntry entry, Point botPos, Point combatTargetPos) {
        if (entry == null || botPos == null || combatTargetPos == null) {
            return combatTargetPos;
        }

        Character bot = entry.bot;
        if (bot == null) {
            return combatTargetPos;
        }

        if (!BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(
                BotAttackExecutionProvider.getEquippedWeaponType(bot), botPos, combatTargetPos)) {
            return combatTargetPos;
        }

        Point retreatPos = BotAttackExecutionProvider.retreatTargetPosition(botPos, combatTargetPos);
        return shouldUseLocalCombatRetreatTarget(entry, botPos, combatTargetPos, retreatPos)
                ? retreatPos
                : combatTargetPos;
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
        BotEntry entry = getBotEntry(ownerCharId, botCharId);
        if (entry == null) return;
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
        if (owner == null) {
            entry.following = false;
            return;
        }

        // Dead state: skip AI until respawn timer expires.
        // Also catch stale hp=0 (e.g. deadUntil was lost on save/reconnect) — re-enter dead state.
        if (handleDeadTick(entry, bot, owner)) {
            return;
        }

        BotMovementManager.refreshMovementProfile(entry);

        Point botPos = bot.getPosition();
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

        // Map change and teleport checks only apply when following owner
        if (syncFollowMap(entry, bot, owner, ownerPos)) {
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
            if (BotPqHooks.requiresGrind(entry, bot)) { entry.grinding = true; entry.following = false; }
            else if (BotPqHooks.requiresFollow(entry, bot)) { entry.following = true; entry.grinding = false; }
            else { entry.kpq.stage5Claimed = false; } // left KPQ — reset for next run
            BotShopManager.onMapChange(entry, bot);
            BotChatManager.checkBotStatus(entry, bot);
            return;
        }

        // Shop visit: navigate to approach point before resuming normal flow
        if (BotShopManager.tickShopVisit(entry, bot)) {
            targetPos = entry.shopTargetPos != null ? entry.shopTargetPos : entry.shopNpcPos;
        }

        // Follow mode: attack monsters already in attack range without chasing
        if (entry.following && !entry.noAmmo && runAiTick && !entry.climbing
                && Math.abs(botPos.x - owner.getPosition().x) <= BotMovementManager.cfg.FOLLOW_DIST * 5) {
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
                } else if (entry.moveWindowMs <= 0) {
                    BotCombatManager.AttackPlan ap = BotCombatManager.planAttack(entry, bot, followTarget);
                    if (BotCombatManager.isTargetInAttackRange(ap, bot, followTarget)) {
                        BotCombatManager.attackMonster(entry, bot, ap);
                        int followDx = Math.abs(botPos.x - targetSnapshot.followTargetPos().x);
                        entry.moveWindowMs = followDx > BotMovementManager.cfg.FOLLOW_DIST * 3 ? 1000
                                           : followDx > BotMovementManager.cfg.FOLLOW_DIST     ? 200
                                           : 0;
                        if (ap.isCloseRangeRoute()
                                && BotCombatManager.isRangedAmmoWeapon(followWeaponType)) {
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
            BotCombatManager.AttackPlan attackPlan = BotCombatManager.planAttack(entry, bot, target);
            WeaponType grindWeaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
            boolean shouldRetreatForRangedSpacing = entry.degenAttackDone
                    || BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(grindWeaponType, botPos, tp);

            if (!entry.climbing) {
                boolean couponSeeking = BotPqHooks.isCouponSeeking(entry);
                if (!shouldRetreatForRangedSpacing && BotCombatManager.isTargetInAttackRange(attackPlan, bot, target)
                        && (!couponSeeking || entry.moveWindowMs <= 0)) {
                    // In range — attack if grounded, or during ascent of a jump
                    BotCombatManager.attackMonster(entry, bot, attackPlan);
                    // After attacking in coupon-seek mode, add a movement window based on
                    // distance to the coupon so the bot walks toward it between attacks.
                    if (couponSeeking && entry.kpq.navTarget != null) {
                        int couponDx = Math.abs(botPos.x - entry.kpq.navTarget.x);
                        entry.moveWindowMs = couponDx > BotMovementManager.cfg.FOLLOW_DIST * 3 ? 1000
                                           : couponDx > BotMovementManager.cfg.FOLLOW_DIST     ? 200
                                           : 0;
                    }
                    // If a ranged bot just did a degenerate close-range hit, force retreat next tick
                    if (attackPlan.isCloseRangeRoute()
                            && BotCombatManager.isRangedAmmoWeapon(grindWeaponType)) {
                        entry.degenAttackDone = true;
                    }
                    if (!entry.inAir) return;
                } else if (!entry.inAir
                        && BotCombatManager.isTargetJumpable(entry.movementProfile, attackPlan.isCloseRangeRoute(), botPos, tp)) {
                    // Target is above but within jump height — jump toward it
                    BotMovementManager.initiateJump(entry, bot, tp.x - botPos.x);
                    return;
                }
            }
            // Retreat positioning is a local combat adjustment, not an inter-region path target.
            // Feeding a synthetic same-Y retreat point into nav while the monster is elsewhere
            // can make rope/ladder bots path back onto the nearby foothold instead of toward
            // the monster's actual region.
            targetPos = selectGrindNavigationTarget(entry, botPos, tp);
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
        if (BotPqHooks.isNpcLocked(entry)) {
            return true;
        }
        BotCombatManager.tickActionLock(entry);
        if (runAiTick) {
            BotCombatManager.rebuildSkillCacheIfNeeded(entry, bot);
            BotCombatManager.tickBuffs(entry, bot);
            BotCombatManager.tickSupportHealing(entry, bot);
            BotBuffManager.tick(entry, bot);
        }
        return tickActionLocked(entry);
    }

    private boolean tickIdleEntry(BotEntry entry, Character bot) {
        if (entry.following || entry.grinding || entry.moveTarget != null) {
            return false;
        }
        if (entry.inAir) {
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

    private boolean syncFollowMap(BotEntry entry, Character bot, Character owner, Point ownerPos) {
        if (!entry.following || bot.getMapId() == owner.getMapId()) {
            return false;
        }
        Point spawn = BotPhysicsEngine.findGroundPoint(owner.getMap(), new Point(ownerPos.x, ownerPos.y - 1));
        if (spawn == null) {
            spawn = ownerPos;
        }
        BotPhysicsEngine.idleOnGround(entry, bot);
        bot.changeMap(owner.getMap(), spawn);
        BotMovementManager.resetEntryState(entry);
        return true;
    }

    private boolean recoverTeleportDistance(BotEntry entry, Character bot, Point targetPos) {
        Point botPos = bot.getPosition();
        if (Math.abs(botPos.x - targetPos.x) + Math.abs(botPos.y - targetPos.y)
                <= BotMovementManager.cfg.TELEPORT_DIST) {
            return false;
        }
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

        if (owner != null && syncFollowMap(entry, bot, owner, ownerPos)) {
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

        // Shop visit: navigate to approach point before resuming normal flow
        if (BotShopManager.tickShopVisit(entry, bot)) {
            targetPos = entry.shopTargetPos != null ? entry.shopTargetPos : entry.shopNpcPos;
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
        if (entry.wasMovingX || entry.lastDesiredDirection != 0 || entry.movementVelX != 0 || entry.movementVelY != 0) {
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
        } else if (entry.inAir) {
            BotMovementManager.tickAirborne(entry, targetPos);
        } else {
            BotMovementManager.tickGrounded(entry, targetPos);
        }
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
        if (entry.inAir) {
            BotMovementManager.tickAirborne(entry, null);
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

    private boolean consumeAiTick(BotEntry entry) {
        entry.aiTickAccumulatorMs += BotMovementManager.cfg.TICK_MS;
        if (entry.aiTickAccumulatorMs < cfg.AI_TICK_MS) {
            return false;
        }

        entry.aiTickAccumulatorMs -= cfg.AI_TICK_MS;
        return true;
    }

}
