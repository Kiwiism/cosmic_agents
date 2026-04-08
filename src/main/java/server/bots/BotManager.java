package server.bots;

import client.BotClient;
import client.Character;
import client.QuestStatus;
import client.Skill;
import client.SkillFactory;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.Magician;
import constants.skills.Warrior;
import constants.skills.WhiteKnight;
import net.packet.Packet;
import net.server.Server;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.PartyOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.StatEffect;
import server.TimerManager;
import server.life.Monster;
import server.bots.pq.BotPqHooks;
import server.maps.Foothold;
import server.maps.MapItem;
import server.maps.MapleMap;
import server.quest.Quest;
import tools.PacketCreator;

import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
    // ownerCharId → timestamp when the next pot-share request is allowed (covers HP and MP; 30 s cooldown)
    private final Map<Integer, Long> potShareCooldownUntil = new ConcurrentHashMap<>();

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
            if (slot < 1 || slot > entries.size()) {
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
            botChar.setPosition(pos);
            botChar.broadcastStance();
            botChar.updatePartyMemberHP();
            if (entry != null) entry.following = true;
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
        return botChar;
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
        entries.add(entry);
        FormationState fs = ownerFormations.getOrDefault(ownerCharId, FormationState.defaultStagger());
        entry.followOffsetX = fs.offsetFor(entries.size() - 1, entries.size());
        if (normalizeSpawnState) {
            normalizeSpawnedBot(entry);
        }
        after(randMs(2000, 2100), () -> BotChatManager.checkBotStatus(entry, bot));
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
            BotChatManager.notifyOwnerGainedEquip(entry, entry.bot, item);
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
                BotChatManager.expirePendingLootOffer(entry);
                if (!isPendingLootOfferTarget(entry, speaker)) {
                    continue;
                }

                matches.add(entry);
            }
        }

        TargetedBotMatch targetedBot = resolveTargetedBot(matches, message);
        if (targetedBot.entry != null) {
            return BotChatManager.handlePendingLootOfferResponse(targetedBot.entry, speaker, targetedBot.commandText);
        }
        if (targetedBot.feedbackMessage != null) {
            speaker.dropMessage(5, targetedBot.feedbackMessage);
            return true;
        }

        if (matches.size() == 1) {
            return BotChatManager.handlePendingLootOfferResponse(matches.get(0), speaker, message);
        }
        if (matches.size() > 1 && looksLikeConfirmation(message)) {
            speaker.dropMessage(5, "More than one bot is waiting on you. Say '<botname> yes' or '<slot> yes'.");
            return true;
        }

        return false;
    }

    private boolean isPendingLootOfferTarget(BotEntry entry, Character speaker) {
        return entry != null
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
     */
    private static Point resolveFollowTargetPos(Point followBase,
                                                Character owner,
                                                Point ownerPos,
                                                int snapRange,
                                                MapleMap map) {
        if (snapRange > 0 && map != null) {
            // Two probes: one at ownerY (finds platform at or below), one above by snapRange.
            // Compare distances to ownerPos.y (not followBase.y, which is always ownerPos.y here,
            // but keeping the comparison explicit guards against future changes).
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
        // No platform found within snap range at the offset X — or snap is disabled.
        // Fall back to the owner's current walk region, not just the single foothold segment,
        // so formation still works across merged flat/sloped platforms when snap is disabled.
        return clampedOnOwnerRegion(followBase.x, owner, ownerPos, map);
    }

    /**
     * Clamps targetX to the owner's current walk region and returns a real standing point.
     * Falls back to the owner's foothold segment if the region cannot be resolved.
     */
    private static Point clampedOnOwnerRegion(int targetX, Character owner, Point ownerPos, MapleMap map) {
        if (map != null) {
            BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(map);
            int ownerRegionId = owner != null
                    ? BotNavigationManager.resolveCharacterRegionId(graph, map, owner)
                    : graph.findRegionId(map, ownerPos);
            BotNavigationGraph.Region ownerRegion = graph.getRegion(ownerRegionId);
            if (ownerRegion != null && !ownerRegion.isRopeRegion) {
                int clampedX = Math.max(ownerRegion.minX, Math.min(ownerRegion.maxX, targetX));
                return ownerRegion.pointAt(clampedX);
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
                : entry.grinding ? BotCombatManager.findGrindTarget(bot) : null;
        Point grindTargetPos = activeGrindTarget == null ? null : new Point(activeGrindTarget.getPosition());
        Point primaryTargetPos;
        String primaryTargetSource;
        if (moveTargetPos != null) {
            primaryTargetPos = moveTargetPos;
            primaryTargetSource = "move-target";
        } else if (grindTargetPos != null) {
            primaryTargetPos = grindTargetPos;
            primaryTargetSource = "grind-target";
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

        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(map);
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
        BotChatManager.expirePendingLootOffer(entry);
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

        Point botPos = bot.getPosition();
        TargetSnapshot targetSnapshot = captureTargetSnapshot(entry);
        Point ownerPos = targetSnapshot.rawOwnerPos();
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
            BotMovementManager.broadcastMovement(entry);
            if (BotPqHooks.requiresGrind(entry, bot)) { entry.grinding = true; entry.following = false; }
            else if (BotPqHooks.requiresFollow(entry, bot)) { entry.following = true; entry.grinding = false; }
            else { entry.kpq.stage5Claimed = false; } // left KPQ — reset for next run
            return;
        }

        // Follow mode: attack monsters already in attack range without chasing
        if (entry.following && runAiTick && !entry.climbing
                && Math.abs(botPos.x - owner.getPosition().x) <= BotMovementManager.cfg.FOLLOW_DIST * 5) {
            Monster followTarget = BotCombatManager.findGrindTarget(bot);
            if (followTarget != null) {
                Point followTargetPos = followTarget.getPosition();
                if (BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(
                        BotAttackExecutionProvider.getEquippedWeaponType(bot), botPos, followTargetPos)) {
                    targetPos = selectGrindNavigationTarget(entry, botPos, followTargetPos);
                } else {
                    BotCombatManager.AttackPlan ap = BotCombatManager.planAttack(entry, bot, followTarget);
                    if (BotCombatManager.isTargetInAttackRange(ap, bot, followTarget)) {
                        BotCombatManager.attackMonster(entry, bot, ap);
                        if (!entry.inAir) return;
                    }
                }
            }
        }

        // Grind mode: navigate toward nearest monster, attack when in range
        if (entry.grinding) {
            // PQ nav override: walking to NPC or owner — skip monster seeking entirely
            if (entry.kpq.navTarget != null) {
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
            // Stick to current target while it's alive and in range; only re-pick when needed
            double seekRangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Monster target = entry.grindTarget;
            if (target == null || !target.isAlive()
                    || target.getPosition().distanceSq(botPos) > seekRangeSq) {
                target = runAiTick ? BotCombatManager.findGrindTarget(bot) : null;
            }
            if (target == null) {
                if (entry.inAir) {
                    BotMovementManager.tickAirborne(entry, targetPos);
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
                return;
            }
            entry.grindTarget = target;
            Point tp = target.getPosition();
            BotCombatManager.AttackPlan attackPlan = BotCombatManager.planAttack(entry, bot, target);
            boolean shouldRetreatForRangedSpacing = BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(
                    BotAttackExecutionProvider.getEquippedWeaponType(bot), botPos, tp);

            if (!entry.climbing) {
                if (!shouldRetreatForRangedSpacing && BotCombatManager.isTargetInAttackRange(attackPlan, bot, target)) {
                    // In range — attack if grounded, or during ascent of a jump
                    BotCombatManager.attackMonster(entry, bot, attackPlan);
                    if (!entry.inAir) return;
                } else if (!entry.inAir
                        && BotCombatManager.isTargetJumpable(attackPlan.isCloseRangeRoute(), botPos, tp)
                        && entry.jumpCooldownMs == 0) {
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
        }

        stepMovementCore(entry, targetPos, runAiTick, entry.grinding);
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
        tickPassiveLoot(entry, bot);
        tickPotionCheck(entry, bot);
        tickPassiveMpRecovery(entry, bot);
        BotBuildManager.checkLevelUp(entry, bot);
        BotChatManager.tickAfkCheck(entry, owner);
        BotDropManager.tickTrade(entry, bot);
        BotDropManager.tickManualTrade(entry, bot);
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
        entry.lastOwnerPos = new Point(ownerPos);
        stepMovementOnly(entry, targetSnapshot.primaryTargetPos(), ownerPos, runAiTick, false);
        return runAiTick;
    }

    void stepMovementOnly(BotEntry entry,
                          Point targetPos,
                          Point ownerPos,
                          boolean runAiTick,
                          boolean applyGrindSpread) {
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
            return;
        }

        stepMovementCore(entry, targetPos, runAiTick, applyGrindSpread);
    }

    private void stepMovementCore(BotEntry entry,
                                  Point targetPos,
                                  boolean runAiTick,
                                  boolean applyGrindSpread) {
        BotNavigationManager.NavigationDirective navDirective = BotNavigationManager.resolveTarget(entry, targetPos, runAiTick);
        if (navDirective.consumedTick) {
            return;
        }

        Point steeringTarget = navDirective.targetPos;
        if (applyGrindSpread && entry.navEdge == null) {
            steeringTarget = new Point(steeringTarget.x + entry.followOffsetX, steeringTarget.y);
        }
        if (entry.moveTargetPrecise && entry.navEdge == null) {
            entry.navPreciseTarget = true;
        }

        tickMovementPhase(entry, steeringTarget, runAiTick);
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

    private static void tickStuckDetection(BotEntry entry) {
        entry.unstuckCooldownMs = BotMovementManager.tickDown(entry.unstuckCooldownMs);

        // Only detect/act while actively navigating — idling near owner is not stuck.
        if (entry.inAir || entry.climbing || (entry.navEdge == null && entry.moveTarget == null)) {
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
    // Potion management
    // -------------------------------------------------------------------------

    /**
     * Counts HP and MP potions in the bot's USE inventory.
     * Uses isRecoveryPotion (heals only, no statups) so combo pots don't inflate the count.
     * Items restoring both HP and MP (elixirs) count toward both totals.
     * @return int[2]: [hpCount, mpCount]
     */
    int[] countPotions(Character bot) {
        List<Item> items = bot.getInventory(InventoryType.USE).list().stream()
                .filter(item -> BotDropManager.isRecoveryPotion(item.getItemId()))
                .toList();
        return countPotions(items, BotDropManager::itemEffect);
    }

    static int[] countPotions(List<Item> items, Function<Integer, StatEffect> effectLookup) {
        int hp = 0, mp = 0;
        for (Item item : items) {
            StatEffect eff = effectLookup.apply(item.getItemId());
            if (eff == null) continue;
            int qty = item.getQuantity();
            if (eff.getHp() > 0 || eff.getHpRate() > 0) hp += qty;
            if (eff.getMp() > 0 || eff.getMpRate() > 0) mp += qty;
        }
        return new int[]{hp, mp};
    }

    /**
     * Binds the best HP/MP potions from inventory to autopot keymap slots 91/92.
     * Called on grind start and every POT_CHECK_INTERVAL_MS to handle type depletion.
     */
    void setupAutopotForBot(Character bot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int hpItemId = -1, mpItemId = -1;
        int bestHp = 0, bestMp = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (item.getQuantity() <= 0) continue;
            StatEffect eff;
            try { eff = ii.getItemEffect(item.getItemId()); } catch (Exception e) { continue; }
            if (eff == null) continue;

            int hpGain = resolveEffectiveRecoveryAmount(bot, eff, true);
            if (hpGain > bestHp) {
                bestHp = hpGain;
                hpItemId = item.getItemId();
            }

            int mpGain = resolveEffectiveRecoveryAmount(bot, eff, false);
            if (mpGain > bestMp) {
                bestMp = mpGain;
                mpItemId = item.getItemId();
            }
        }
        if (hpItemId > 0) {
            bot.changeKeybinding(91, new KeyBinding(2, hpItemId));
            bot.setAutopotHpAlert(cfg.AUTOPOT_HP_THRESH);
        } else {
            bot.getKeymap().remove(91);
            bot.setAutopotHpAlert(0f);
        }
        if (mpItemId > 0) {
            bot.changeKeybinding(92, new KeyBinding(2, mpItemId));
            bot.setAutopotMpAlert(cfg.AUTOPOT_MP_THRESH);
        } else {
            bot.getKeymap().remove(92);
            bot.setAutopotMpAlert(0f);
        }
    }

    private static final List<String> GRIND_REPLIES = List.of(
            "ok", "on it", "lets get it", "farming time", "got it",
            "sure", "ok boss", "time to grind",
            "lets farm", "hunting time", "aye, killing stuff",
            "lezgo", "gonna get some kills", "on it boss",
            "time to work", "lets do this");

    private static final List<String> POT_REQUEST_HP_MSGS = List.of(
            "anyone have HP pots? running low",
            "low on HP pots, does anyone have some?",
            "need HP pots!! anyone?",
            "HP pots? who has some",
            "anyone got HP pots to spare?");
    private static final List<String> POT_REQUEST_MP_MSGS = List.of(
            "anyone have MP pots? running low",
            "low on MP pots, does anyone have some?",
            "need MP pots!! anyone?",
            "MP pots? who has some",
            "anyone got MP pots to spare?");
    private static final List<String> POT_OFFER_HP_MSGS = List.of(
            "got some HP pots, inv u",
            "yep i have HP pots, inv u",
            "sure, got spare HP pots",
            "coming, inv",
            "got you");
    private static final List<String> POT_OFFER_MP_MSGS = List.of(
            "got some MP pots, inv u",
            "yep i have MP pots, inv u",
            "sure, got spare MP pots",
            "coming, inv",
            "got you");

    /** Builds grind-start reply with low-potion warning when below POT_LOW_WARN. */
    String grindStartMessage(Character bot) {
        int[] pots = countPotions(bot);
        int hp = pots[0], mp = pots[1];
        String base = randomReply(GRIND_REPLIES);
        if (hp >= cfg.POT_LOW_WARN && mp >= cfg.POT_LOW_WARN) return base;
        StringBuilder msg = new StringBuilder(base).append(", but");
        if (hp < cfg.POT_LOW_WARN) msg.append(" only ").append(hp).append(" HP pots");
        if (hp < cfg.POT_LOW_WARN && mp < cfg.POT_LOW_WARN) msg.append(" and");
        if (mp < cfg.POT_LOW_WARN) msg.append(" only ").append(mp).append(" MP pots");
        return msg.append(" left").toString();
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

    // Passive loot
    // -------------------------------------------------------------------------

    /** Picks up lootable drops within LOOT_RADIUS — runs every tick in all modes. */
    private void tickPassiveLoot(BotEntry entry, Character bot) {
        if (entry.lootInhibitMs > 0) {
            entry.lootInhibitMs = BotMovementManager.tickDown(entry.lootInhibitMs);
            return;
        }
        if (entry.pendingTradeCategory != null) return; // don't loot while trading — keeps inventory state consistent
        entry.invFullWarnCooldownMs = BotMovementManager.tickDown(entry.invFullWarnCooldownMs);
        Point botPos = bot.getPosition();
        for (MapItem drop : bot.getMap().getDroppedItems()) {
            if (drop.isPickedUp() || bot.getMap().getMapObject(drop.getObjectId()) != drop) {
                cleanupBotLootGhostDrop(bot, drop);
                continue;
            }
            if (!drop.canBePickedBy(bot)) continue;
            if (drop.getItemId() == 4001008) continue; // KPQ pass — only the party leader should pick this up
            if (drop.getItemId() == 4001007 && (BotPqHooks.shouldSkipCouponLoot(entry)
                    || (entry.kpq.couponTarget > 0 && bot.getItemQuantity(4001007, false) >= entry.kpq.couponTarget))) continue;
            if (System.currentTimeMillis() - drop.getDropTime() < 3000) continue; // wait 3s after spawn
            Point dp = drop.getPosition();
            if (Math.abs(dp.x - botPos.x) > cfg.LOOT_RADIUS
                    || Math.abs(dp.y - botPos.y) > cfg.LOOT_RADIUS) continue;
            if (drop.getMeso() <= 0 && drop.getItemId() > 0) {
                InventoryType type = ItemConstants.getInventoryType(drop.getItemId());
                Inventory inv = bot.getInventory(type);
                if (inv != null && inv.isFull()) {
                    if (entry.invFullWarnCooldownMs <= 0) {
                        botSay(bot, type.name().toLowerCase() + " inventory is full!");
                        entry.invFullWarnCooldownMs = BotMovementManager.delayAfterCurrentTick(cfg.INV_FULL_WARN_CD_MS);
                    }
                    continue;
                }
            }
            Item pickedItem = drop.getItem();
            int pickedItemId = drop.getItemId();
            if (ItemId.isNxCard(pickedItemId) && entry.owner != null && entry.owner.getMap() == bot.getMap()) {
                entry.owner.pickupItem(drop);
            } else {
                bot.pickupItem(drop);
            }
            cleanupBotLootGhostDrop(bot, drop);
            if (pickedItem != null
                    && pickedItemId > 0
                    && ItemConstants.getInventoryType(pickedItemId) == InventoryType.EQUIP
                    && BotDropManager.hasItem(bot, pickedItem)) {
                BotEquipManager.autoEquip(bot, entry.owner, entry.pendingLootOfferItem);
                if (BotDropManager.hasItem(bot, pickedItem)) {
                    BotChatManager.scheduleLootOfferPrompt(entry, bot, pickedItem, 5_000L);
                }
                // else: item was an upgrade — bot self-equipped it, no offer needed
            }
        }
    }

    private void cleanupBotLootGhostDrop(Character bot, MapItem drop) {
        if (drop == null) {
            return;
        }
        if (!drop.isPickedUp() && bot.getMap().getMapObject(drop.getObjectId()) == drop) {
            return;
        }

        var map = bot.getMap();
        Packet removePacket = PacketCreator.removeItemFromMap(drop.getObjectId(), 1, 0);
        for (Character player : map.getAllPlayers()) {
            if (player.getClient() instanceof BotClient) {
                continue;
            }
            if (!player.isMapObjectVisible(drop)) {
                continue;
            }

            player.removeVisibleMapObject(drop);
            player.sendPacket(removePacket);
        }
    }

    // -------------------------------------------------------------------------
    // Potion check tick
    // -------------------------------------------------------------------------

    /** Periodically rebinds autopot and stops grinding when HP pots are critically low. */
    private void tickPotionCheck(BotEntry entry, Character bot) {
        if (entry.potCheckTimerMs > 0) {
            entry.potCheckTimerMs = BotMovementManager.tickDown(entry.potCheckTimerMs);
            return;
        }
        entry.potCheckTimerMs = BotMovementManager.delayAfterCurrentTick(cfg.POT_CHECK_INTERVAL_MS);

        setupAutopotForBot(bot);

        if (!entry.grinding && !entry.following) return;

        int[] pots = countPotions(bot);

        // Edge-triggered pot-share requests: fire once when count crosses below POT_LOW_WARN,
        // reset the flag when pots are replenished above the threshold.
        // Flag is only set if the request was actually broadcast (not blocked by owner cooldown),
        // so other bots whose requests were suppressed will retry once the cooldown clears.
        if (pots[0] >= cfg.POT_LOW_WARN) {
            entry.potShareRequestedHp = false;
        } else if (!entry.potShareRequestedHp && requestPotShare(entry, bot, true)) {
            entry.potShareRequestedHp = true;
        }
        if (pots[1] >= cfg.POT_LOW_WARN) {
            entry.potShareRequestedMp = false;
        } else if (!entry.potShareRequestedMp && requestPotShare(entry, bot, false)) {
            entry.potShareRequestedMp = true;
        }

        if (!entry.grinding) return;
        if (pots[0] < cfg.POT_STOP && bot.getHp() < bot.getMaxHp() * 0.4f) {
            entry.grinding = false;
            entry.following = true;
            botSay(bot, "low on pots!! walking to you");
            bot.changeFaceExpression(Emote.GLARE.getValue());
        }
    }

    /**
     * Resets pot-share request flags and immediately triggers a request for any category
     * already below POT_LOW_WARN. Call this on grind/follow mode start.
     */
    void checkPotShareOnModeStart(BotEntry entry, Character bot) {
        entry.potShareRequestedHp = false;
        entry.potShareRequestedMp = false;
        int[] pots = countPotions(bot);
        if (pots[0] < cfg.POT_LOW_WARN && requestPotShare(entry, bot, true)) {
            entry.potShareRequestedHp = true;
        }
        if (pots[1] < cfg.POT_LOW_WARN && requestPotShare(entry, bot, false)) {
            entry.potShareRequestedMp = true;
        }
    }

    /**
     * Broadcasts a pot request from the needy bot, then schedules the best-qualified
     * same-owner sibling bot to donate pots via the existing trade system.
     * If no bot has enough to qualify, the highest-count sibling says so after a longer delay.
     */
    /**
     * Broadcasts a pot request and schedules a donation, using a 30 s per-owner cooldown so
     * only one request fires at a time. Returns true if the request was actually broadcast.
     * Callers that receive false should NOT mark their flag — they will retry naturally when
     * the cooldown expires and their tickPotionCheck fires again.
     */
    private boolean requestPotShare(BotEntry entry, Character bot, boolean forHp) {
        Character owner = entry.owner;
        if (owner == null || bot.getTrade() != null || entry.pendingTradeCategory != null) return false;

        long now = System.currentTimeMillis();
        if (now < potShareCooldownUntil.getOrDefault(owner.getId(), 0L)) return false;
        potShareCooldownUntil.put(owner.getId(), now + 30_000L);

        botSay(bot, randomReply(forHp ? POT_REQUEST_HP_MSGS : POT_REQUEST_MP_MSGS));

        // Find the sibling bot with the highest pot count of this type on the same map
        List<BotEntry> siblings = getBotEntries(owner.getId());
        BotEntry bestEntry = null;
        int bestCount = 0;
        for (BotEntry sibling : siblings) {
            if (sibling == entry || sibling.bot == null) continue;
            if (sibling.bot.getMapId() != bot.getMapId()) continue;
            int[] pots = countPotions(sibling.bot);
            int count = forHp ? pots[0] : pots[1];
            if (count > bestCount) {
                bestCount = count;
                bestEntry = sibling;
            }
        }

        if (bestEntry == null) {
            // No sibling bots on the same map — nobody to ask; back off for 10 min
            potShareCooldownUntil.put(owner.getId(), now + 10 * 60_000L);
            return true;
        }

        final BotEntry donorEntry = bestEntry;
        final Character donorBot  = donorEntry.bot;
        final boolean qualifies   = bestCount > cfg.POT_LOW_WARN * 3;
        final int maxQty          = bestCount / 3;
        final Character needyBot  = bot;

        if (qualifies) {
            after(randMs(2000, 3000), () -> {
                if (donorBot.getTrade() != null || donorEntry.pendingTradeCategory != null) return;
                List<Item> items = BotDropManager.collectPotShareItems(donorBot, forHp, maxQty);
                if (items.isEmpty()) return;
                botSay(donorBot, randomReply(forHp ? POT_OFFER_HP_MSGS : POT_OFFER_MP_MSGS));
                after(randMs(900, 1100), () ->
                        BotDropManager.startPotShareTransfer(items, needyBot, donorEntry, donorBot, maxQty));
            });
        } else {
            // Best donor is also low — nobody can help; back off for 10 min
            potShareCooldownUntil.put(owner.getId(), now + 10 * 60_000L);
            String ownerName = owner.getName();
            List<String> noQualMsgs = List.of(
                    "low too, maybe " + ownerName + " has some?",
                    "wish i could help, try " + ownerName + "?",
                    "i'm low too :/ check with " + ownerName,
                    "barely have any myself, ask " + ownerName);
            after(randMs(4000, 6000), () ->
                    botSay(donorBot, randomReply(noQualMsgs)));
        }
        return true;
    }

    private void tickPassiveMpRecovery(BotEntry entry, Character bot) {
        boolean hpFull = bot.getHp() >= bot.getCurrentMaxHp();
        boolean mpFull = bot.getMp() >= bot.getCurrentMaxMp();
        if (hpFull && mpFull) {
            entry.mpRecoveryTimerMs = 0;
            return;
        }
        if (entry.mpRecoveryTimerMs > 0) {
            entry.mpRecoveryTimerMs = BotMovementManager.tickDown(entry.mpRecoveryTimerMs);
            return;
        }

        entry.mpRecoveryTimerMs = BotMovementManager.delayAfterCurrentTick(cfg.MP_RECOVERY_INTERVAL_MS);

        int hpRecovery = hpFull ? 0 : calculatePassiveHpRecovery(entry, bot);
        int mpRecovery = mpFull ? 0 : calculatePassiveMpRecovery(entry, bot);
        if (hpRecovery <= 0 && mpRecovery <= 0) {
            return;
        }

        bot.addMPHP(hpRecovery, mpRecovery);
    }

    private int calculatePassiveHpRecovery(BotEntry entry, Character bot) {
        int recovery = cfg.BASE_HP_RECOVERY;
        if (!isStandingStillForRecovery(entry, bot)) {
            return recovery;
        }

        recovery += getFlatHpRecoveryBonus(bot, Warrior.IMPROVED_HPREC);
        return recovery;
    }

    private int calculatePassiveMpRecovery(BotEntry entry, Character bot) {
        int recovery = cfg.BASE_MP_RECOVERY;
        if (!isStandingStillForRecovery(entry, bot)) {
            return recovery;
        }

        recovery += getFlatMpRecoveryBonus(bot, Crusader.IMPROVING_MPREC);
        recovery += getFlatMpRecoveryBonus(bot, WhiteKnight.IMPROVING_MP_RECOVERY);
        recovery += getFlatMpRecoveryBonus(bot, DawnWarrior.INCREASED_MP_RECOVERY);
        recovery += getMagicianMpRecoveryBonus(bot);
        return recovery;
    }

    private boolean isStandingStillForRecovery(BotEntry entry, Character bot) {
        if (entry.inAir || entry.climbing) {
            return false;
        }

        return entry.lastDesiredDirection == 0
                && BotPhysicsEngine.isStandingStance(BotPhysicsEngine.resolveStance(entry));
    }

    private int getFlatHpRecoveryBonus(Character bot, int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            return 0;
        }

        int level = bot.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }

        return skill.getEffect(level).getHp();
    }

    private int getFlatMpRecoveryBonus(Character bot, int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            return 0;
        }

        int level = bot.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }

        return skill.getEffect(level).getMp();
    }

    private int getMagicianMpRecoveryBonus(Character bot) {
        Skill skill = SkillFactory.getSkill(Magician.IMPROVED_MP_RECOVERY);
        if (skill == null) {
            return 0;
        }

        int level = bot.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }

        return Math.max(0, (bot.getInt() / 10) * level);
    }

    private int resolveEffectiveRecoveryAmount(Character bot, StatEffect effect, boolean hp) {
        if (effect == null) {
            return 0;
        }

        int flat = hp ? effect.getHp() : effect.getMp();
        if (flat > 0) {
            return flat;
        }

        double rate = hp ? effect.getHpRate() : effect.getMpRate();
        if (rate <= 0) {
            return 0;
        }

        int max = hp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp();
        return (int) Math.ceil(max * rate);
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
