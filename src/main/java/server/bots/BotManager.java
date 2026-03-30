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
import constants.inventory.ItemConstants;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.Magician;
import constants.skills.Warrior;
import constants.skills.WhiteKnight;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.StatEffect;
import server.TimerManager;
import server.life.Monster;
import server.maps.MapItem;
import server.maps.MapleMap;
import server.quest.Quest;
import tools.PacketCreator;

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
    // TODO: list from most important to least important
    // TODO: Option to respec bot ap/sp
    // TODO: Make bot auto scan/autoequip the "best" equipment they have + can equip in their inventory
    // TODO: some kind of help command/question on available interactions available (can ask to drop, respec, change build, check stats, etc)
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
    }

    /** Singleton config — replace with `cfg = new Config()` after hotswapping to reset. */
    public static Config cfg = new Config();

    public static BotManager getInstance() { return instance; }

    // ownerCharId → list of owned bot entries (1:N)
    private final Map<Integer, List<BotEntry>> bots = new ConcurrentHashMap<>();

    private static final Pattern DISMISS_PATTERN = Pattern.compile(
            "\\b(dismiss|disown|release)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECRUIT_PATTERN = Pattern.compile(
            "\\b(recruit|adopt|hire|claim)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    // Reserve `give ...` for item requests handled by BotChatManager.
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "\\btransfer\\s+(\\S+)(?:\\s+to)?\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);

    record BotTransferCommand(String botName, String targetName) {}

    static BotTransferCommand matchBotTransferCommand(String message) {
        Matcher matcher = TRANSFER_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        return new BotTransferCommand(matcher.group(1), matcher.group(2));
    }

    static String randomReply(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void registerBot(int ownerCharId, Character owner, Character bot) {
        registerBotInternal(ownerCharId, owner, bot, false);
    }

    public void registerSpawnedBot(int ownerCharId, Character owner, Character bot) {
        registerBotInternal(ownerCharId, owner, bot, true);
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

    private void registerBotInternal(int ownerCharId, Character owner, Character bot, boolean normalizeSpawnState) {
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
        if (normalizeSpawnState) {
            normalizeSpawnedBot(entry);
        }
        TimerManager.getInstance().schedule(() -> BotChatManager.checkBotStatus(entry, bot), 2000);
    }

    private void normalizeSpawnedBot(BotEntry entry) {
        Character bot = entry.bot;
        Point spawnPos = resolveSpawnPosition(bot.getMap(), bot.getPosition());
        if (bot.getHp() <= 0) {
            bot.updateHp(Math.max(1, bot.getCurrentMaxHp()));
        }

        BotPhysicsEngine.teleportTo(entry, bot, spawnPos != null ? spawnPos : bot.getPosition());
        BotMovementManager.clearNavigationState(entry);
        entry.grindTarget = null;
        entry.attackCooldownMs = 0;
        entry.deadUntil = 0;
        entry.lastMapId = bot.getMapId();
        entry.fhIndex = BotMovementManager.buildFhIndex(bot.getMap());
        entry.skipDelayMs = 0;
        entry.aiTickAccumulatorMs = 0;
        entry.lastDesiredDirection = 0;
        entry.movementBroadcastValid = false;
        BotMovementManager.broadcastMovement(entry);
        bot.updatePartyMemberHP();
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
        BotEntry found = null;
        for (BotEntry e : entries) {
            if (e.bot.getName().equalsIgnoreCase(botName)) { found = e; break; }
        }
        if (found == null) return false;
        BotEntry entry = found;
        entries.remove(entry);
        entry.task.cancel(false);
        entry.following = false;
        entry.grinding  = false;
        TimerManager.getInstance().schedule(() ->
                botSay(entry.bot, randomReply(List.of(
                        "ok", "sure", "alright", "gotcha",
                        "later!", "see ya", "take care", "cya", "peace out"))), 500);
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
        BotEntry found = null;
        for (BotEntry e : entries) {
            if (e.bot.getName().equalsIgnoreCase(botName)) { found = e; break; }
        }
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
        TimerManager.getInstance().schedule(() ->
                botSay(bot, randomReply(List.of("ok!", "sure!", "hey " + target.getName() + "!", "hi " + target.getName() + "!"))), 800);
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
            // Check not already owned
            boolean owned = false;
            outer:
            for (List<BotEntry> entries : bots.values()) {
                for (BotEntry e : entries) {
                    if (e.bot.getId() == c.getId()) { owned = true; break outer; }
                }
            }
            if (!owned) return c;
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
        String lowerMsg = message.toLowerCase();
        for (BotEntry entry : entries) {
            String name = entry.bot.getName().toLowerCase();
            if (lowerMsg.startsWith(name) && lowerMsg.length() > name.length()) {
                char next = lowerMsg.charAt(name.length());
                if (next == ' ' || next == ',' || next == '!' || next == '?') {
                    String rest = message.substring(name.length()).replaceFirst("^[,!?\\s]+", "").trim();
                    if (!rest.isEmpty()) {
                        BotChatManager.handleChat(entry, rest);
                        return;
                    }
                }
            }
        }

        // No name prefix — broadcast to all bots
        for (BotEntry entry : entries) {
            BotChatManager.handleChat(entry, message);
        }
    }

    private boolean handlePendingLootOfferResponse(Character speaker, String message) {
        List<BotEntry> matches = new ArrayList<>();
        String lowerMessage = message.toLowerCase();

        for (List<BotEntry> entries : bots.values()) {
            for (BotEntry entry : entries) {
                BotChatManager.expirePendingLootOffer(entry);
                if (!isPendingLootOfferTarget(entry, speaker)) {
                    continue;
                }

                String botName = entry.bot.getName().toLowerCase();
                if (lowerMessage.startsWith(botName) && lowerMessage.length() > botName.length()) {
                    char next = lowerMessage.charAt(botName.length());
                    if (next == ' ' || next == ',' || next == '!' || next == '?') {
                        String rest = message.substring(botName.length()).replaceFirst("^[,!?\\s]+", "").trim();
                        return !rest.isEmpty() && BotChatManager.handlePendingLootOfferResponse(entry, speaker, rest);
                    }
                }

                matches.add(entry);
            }
        }

        if (matches.size() == 1) {
            return BotChatManager.handlePendingLootOfferResponse(matches.get(0), speaker, message);
        }
        if (matches.size() > 1 && looksLikeConfirmation(message)) {
            speaker.dropMessage(5, "More than one bot is waiting on you. Say '<botname> yes' or '<botname> no'.");
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
    // Main tick
    // -------------------------------------------------------------------------

    private void tick(int ownerCharId, int botCharId) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return;
        BotEntry entry = null;
        for (BotEntry e : entries) {
            if (e.bot.getId() == botCharId) { entry = e; break; }
        }
        if (entry == null) return;
        if (entry.skipDelayMs > 0) {
            entry.skipDelayMs = BotMovementManager.tickDown(entry.skipDelayMs);
            return;
        }
        Character bot = entry.bot;
        BotChatManager.expirePendingLootOffer(entry);
        boolean runAiTick = consumeAiTick(entry);

        Character owner = entry.owner;
        if (owner == null || owner.getId() != ownerCharId || !owner.isLoggedinWorld()) {
            owner = Server.getInstance()
                    .getWorld(bot.getWorld())
                    .getPlayerStorage()
                    .getCharacterById(ownerCharId);
            entry.owner = owner;
        }
        if (owner == null) {
            entry.following = false;
            return;
        }

        // Dead state: skip AI until respawn timer expires.
        // Also catch stale hp=0 (e.g. deadUntil was lost on save/reconnect) — re-enter dead state.
        if (entry.deadUntil == 0 && bot.getHp() <= 0) {
            BotCombatManager.enterDeadState(entry, bot, false);
        }
        if (entry.deadUntil > 0) {
            if (System.currentTimeMillis() >= entry.deadUntil) {
                respawnBot(entry, bot, owner);
            }
            return;
        }

        Point botPos    = bot.getPosition();
        Point targetPos = owner.getPosition();

        // These run in all modes (idle, follow, grind)
        BotCombatManager.tickMobDamage(entry, bot);
        if (bot.getHp() <= 0) {
            if (entry.deadUntil == 0) {
                BotCombatManager.enterDeadState(entry, bot, false);
            }
            return;
        }
        tickPassiveLoot(entry, bot);
        tickPotionCheck(entry, bot);
        tickPassiveMpRecovery(entry, bot);
        BotBuildManager.checkLevelUp(entry, bot);
        BotChatManager.tickAfkCheck(entry, owner);
        BotDropManager.tickTrade(entry, bot);
        BotDropManager.tickManualTrade(entry, bot);
        BotCombatManager.tickActionLock(entry);
        if (runAiTick) {
            BotCombatManager.rebuildSkillCacheIfNeeded(entry, bot);
            BotCombatManager.tickBuffs(entry, bot);
            BotCombatManager.tickSupportHealing(entry, bot);
        }
        if (tickActionLocked(entry)) {
            return;
        }

        if (!entry.following && !entry.grinding) {
            if (entry.inAir) {
                BotMovementManager.tickAirborne(entry);
            } else if (!entry.climbing) {
                // On ground — snap to stand stance once so walking/jumping animation clears
                int expectedIdleStance = BotPhysicsEngine.resolveIdleGroundStance(entry);
                if (BotPhysicsEngine.resolveStance(entry) != expectedIdleStance
                        || bot.getStance() != expectedIdleStance) {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
            }
            return;
        }

        // Map change and teleport checks only apply when following owner
        if (entry.following) {
            if (bot.getMapId() != owner.getMapId()) {
                Point ownerPos = owner.getPosition();
                Point spawn = BotPhysicsEngine.findGroundPoint(owner.getMap(), new Point(ownerPos.x, ownerPos.y - 1));
                if (spawn == null) {
                    spawn = ownerPos;
                }
                BotPhysicsEngine.idleOnGround(entry, bot);
                bot.changeMap(owner.getMap(), spawn);
                BotMovementManager.resetEntryState(entry);
                return;
            }
        }
        // Teleport if hopelessly far — applies to both follow and grind (catches falling off map)
        if (Math.abs(botPos.x - targetPos.x) + Math.abs(botPos.y - targetPos.y) > BotMovementManager.cfg.TELEPORT_DIST) {
            Point spawn = BotPhysicsEngine.findGroundPoint(bot.getMap(), new Point(targetPos.x, targetPos.y - 1));
            if (spawn == null) {
                spawn = targetPos;
            }
            BotPhysicsEngine.teleportTo(entry, bot, spawn);
            BotMovementManager.resetEntryState(entry);
            BotMovementManager.broadcastMovement(entry);
            return;
        }

        // Rebuild foothold index on map change
        if (entry.lastMapId != bot.getMapId()) {
            entry.fhIndex  = BotMovementManager.buildFhIndex(bot.getMap());
            entry.lastMapId = bot.getMapId();
        }

        // Follow mode: attack monsters already in attack range without chasing
        if (entry.following && runAiTick && !entry.inAir && !entry.climbing
                && Math.abs(botPos.x - owner.getPosition().x) <= BotMovementManager.cfg.FOLLOW_DIST * 5) {
            Monster followTarget = BotCombatManager.findGrindTarget(bot);
            if (followTarget != null) {
                BotCombatManager.AttackPlan ap = BotCombatManager.planAttack(entry, bot, followTarget);
                if (BotCombatManager.isTargetInAttackRange(ap, bot, followTarget)) {
                    BotCombatManager.attackMonster(entry, bot, ap);
                    if (!entry.inAir) return;
                }
            }
        }

        // Grind mode: navigate toward nearest monster, attack when in range
        if (entry.grinding) {
            // Stick to current target while it's alive and in range; only re-pick when needed
            double seekRangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Monster target = entry.grindTarget;
            if (target == null || !target.isAlive()
                    || target.getPosition().distanceSq(botPos) > seekRangeSq) {
                target = runAiTick ? BotCombatManager.findGrindTarget(bot) : null;
            }
            if (target == null) {
                if (entry.inAir) {
                    BotMovementManager.tickAirborne(entry);
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
                return;
            }
            entry.grindTarget = target;
            Point tp = target.getPosition();
            BotCombatManager.AttackPlan attackPlan = BotCombatManager.planAttack(entry, bot, target);

            if (!entry.climbing) {
                if (BotCombatManager.isTargetInAttackRange(attackPlan, bot, target)) {
                    // In range — attack if grounded, or during ascent of a jump
                    if (!entry.inAir || entry.velY < 0) {
                        BotCombatManager.attackMonster(entry, bot, attackPlan);
                        if (!entry.inAir) return;
                        // airborne: fall through so tickAirborne still runs this tick
                    }
                } else if (attackPlan.skillId == 0
                        && !entry.inAir
                        && BotCombatManager.isTargetJumpable(botPos, tp)
                        && entry.jumpCooldownMs == 0) {
                    // Target is above but within jump height — jump toward it
                    BotMovementManager.initiateJump(entry, bot, tp.x - botPos.x);
                    return;
                }
            }
            targetPos = tp;
        }

        // Shift target by bot's personal offset so multiple bots spread out (follow + grind)
//        if (entry.following || entry.grinding) {
//            targetPos = new Point(targetPos.x + entry.followOffsetX, targetPos.y);
//        }

        BotNavigationManager.NavigationDirective navDirective = BotNavigationManager.resolveTarget(entry, targetPos, runAiTick);
        if (navDirective.consumedTick) {
            return;
        }
        targetPos = navDirective.targetPos;

        if (entry.climbing) {
            BotMovementManager.tickClimbing(entry, targetPos, runAiTick);
        } else if (entry.inAir) {
            BotMovementManager.tickAirborne(entry);
        } else {
            BotMovementManager.tickGrounded(entry, targetPos);
        }
    }

    private boolean tickActionLocked(BotEntry entry) {
        if (entry.attackCooldownMs <= 0) {
            return false;
        }
        if (entry.inAir) {
            BotMovementManager.tickAirborne(entry);
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
            TimerManager.getInstance().schedule(() -> {
                botSay(botChar, "back!!");
                botChar.changeFaceExpression(Emote.HAPPY.getValue());
            }, 1000);
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
        BotMovementManager.resetEntryState(entry);
        BotMovementManager.broadcastMovement(entry);
        botSay(bot, "back!");
        bot.changeFaceExpression(Emote.GLARE.getValue());
    }

    // -------------------------------------------------------------------------
    // Potion management
    // -------------------------------------------------------------------------

    /**
     * Counts HP and MP potions in the bot's USE inventory.
     * Items restoring both (elixirs) count toward both totals.
     * @return int[2]: [hpCount, mpCount]
     */
    int[] countPotions(Character bot) {
        int hp = 0, mp = 0;
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            StatEffect eff;
            try { eff = ii.getItemEffect(item.getItemId()); } catch (Exception e) { continue; }
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
            if (!drop.canBePickedBy(bot)) continue;
            if (System.currentTimeMillis() - drop.getDropTime() < 1000) continue; // wait 1s after spawn
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
            bot.pickupItem(drop);
            if (pickedItem != null
                    && pickedItemId > 0
                    && ItemConstants.getInventoryType(pickedItemId) == InventoryType.EQUIP
                    && BotDropManager.hasItem(bot, pickedItem)) {
                BotChatManager.scheduleLootOfferPrompt(entry, bot, pickedItem, 5_000L);
            }
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

        if (!entry.grinding) return;
        int[] pots = countPotions(bot);
        if (pots[0] < cfg.POT_STOP && bot.getHp() < bot.getMaxHp() * 0.4f) {
            entry.grinding = false;
            entry.following = true;
            botSay(bot, "low on pots!! walking to you");
            bot.changeFaceExpression(Emote.GLARE.getValue());
        }
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
