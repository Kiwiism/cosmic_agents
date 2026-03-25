package server.bots;

import client.BotClient;
import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import constants.inventory.ItemConstants;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.StatEffect;
import server.TimerManager;
import server.life.Monster;
import server.maps.MapItem;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.awt.*;
import java.sql.SQLException;
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
        // Passive loot
        public int   LOOT_RADIUS      = 100;   // px; pickup items within this box radius
        public int   INV_FULL_WARN_CD = 100;   // ticks between "inventory full" complaints

        // Potion management
        public int   POT_LOW_WARN     = 100;   // warn on grind start below this count
        public int   POT_STOP         = 10;    // stop grinding below this HP pot count
        public int   POT_CHECK_TICKS  = 20;    // ticks between potion recount/rebind
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
    // "give Jason Bob" or "transfer Jason to Bob"
    private static final Pattern GIVE_PATTERN = Pattern.compile(
            "\\b(give|transfer)\\s+(\\S+)(?:\\s+to)?\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);

    static String randomReply(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void registerBot(int ownerCharId, Character owner, Character bot) {
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
        TimerManager.getInstance().schedule(() -> BotChatManager.checkBotStatus(entry, bot), 2000);
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
                botSay(entry.bot, randomReply(List.of("ok", "sure", "alright", "gotcha"))), 500);
        return true;
    }

    /** Recruit an ownerless bot by name into the owner's group. */
    public boolean recruitBot(int ownerCharId, Character owner, String botName) {
        Character bot = findOwnerlessBot(botName, owner.getWorld());
        if (bot == null) return false;
        registerBot(ownerCharId, owner, bot);
        return true;
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

    public void handleChat(Character owner, String message) {
        // Recruit must work even when owner has no bots yet
        Matcher rm = RECRUIT_PATTERN.matcher(message);
        if (rm.find()) {
            String name = rm.group(2);
            if (recruitBot(owner.getId(), owner, name)) {
                owner.yellowMessage("Bot '" + name + "' recruited!");
            } else {
                owner.yellowMessage("No ownerless bot named '" + name + "' found.");
            }
            return;
        }

        Matcher gm = GIVE_PATTERN.matcher(message);
        if (gm.find()) {
            String err = giveBot(owner.getId(), owner, gm.group(2), gm.group(3));
            if (err != null) owner.yellowMessage(err);
            else owner.yellowMessage("Bot '" + gm.group(2) + "' transferred to " + gm.group(3) + ".");
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
        if (entry.skipTicks > 0) { entry.skipTicks--; return; }
        Character bot = entry.bot;

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
            bot.setStance(BotMovementManager.cfg.DEAD_STANCE);
            BotMovementManager.broadcastMovement(bot, 0, 0);
            entry.deadUntil = System.currentTimeMillis() + BotCombatManager.cfg.BOT_DEAD_MS;
            BotMovementManager.resetEntryState(entry);
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
        tickPassiveLoot(entry, bot);
        tickPotionCheck(entry, bot);
        BotBuildManager.checkLevelUp(entry, bot);
        BotChatManager.tickAfkCheck(entry, owner);
        BotDropManager.tickTrade(entry, bot);
        BotCombatManager.rebuildSkillCacheIfNeeded(entry, bot);
        BotCombatManager.tickBuffs(entry, bot);

        if (!entry.following && !entry.grinding) {
            if (entry.inAir) {
                BotMovementManager.tickAirborne(entry, targetPos);
            } else if (!entry.climbing) {
                // On ground — snap to stand stance once so walking/jumping animation clears
                if (bot.getStance() != 5) {
                    bot.setStance(5);
                    BotMovementManager.broadcastMovement(bot, 0, 0);
                }
            }
            // If climbing, bot idles on the rope — no stance change needed (16/17 is already idle)
            return;
        }

        // Map change and teleport checks only apply when following owner
        if (entry.following) {
            if (bot.getMapId() != owner.getMapId()) {
                Point spawn = new Point(owner.getPosition().x, owner.getPosition().y - 10);
                bot.setStance(BotMovementManager.cfg.STAND_STANCE); // ensure spawn packet shows stand stance, not walk
                bot.changeMap(owner.getMap(), spawn);
                BotMovementManager.resetEntryState(entry);
                return;
            }
        }
        // Teleport if hopelessly far — applies to both follow and grind (catches falling off map)
        if (Math.abs(botPos.x - targetPos.x) + Math.abs(botPos.y - targetPos.y) > BotMovementManager.cfg.TELEPORT_DIST) {
            Point spawn = new Point(targetPos.x, targetPos.y - 10);
            bot.setPosition(spawn);
            BotMovementManager.resetEntryState(entry);
            BotMovementManager.broadcastMovement(bot, 0, 0);
            return;
        }

        // Rebuild foothold index on map change
        if (entry.lastMapId != bot.getMapId()) {
            entry.fhIndex  = BotMovementManager.buildFhIndex(bot.getMap());
            entry.lastMapId = bot.getMapId();
        }

        // Grind mode: navigate toward nearest monster, attack when in range
        if (entry.grinding) {
            // Stick to current target while it's alive and in range; only re-pick when needed
            double seekRangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Monster target = entry.grindTarget;
            if (target == null || !target.isAlive()
                    || target.getPosition().distanceSq(botPos) > seekRangeSq) {
                target = BotCombatManager.findGrindTarget(bot);
            }
            if (target == null) {
                if (entry.inAir) BotMovementManager.tickAirborne(entry, targetPos);
                else { bot.setStance(5); BotMovementManager.broadcastMovement(bot, 0, 0); }
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
                        && entry.jumpCooldown == 0) {
                    // Target is above but within jump height — jump toward it
                    BotMovementManager.initiateJump(entry, bot, tp.x - botPos.x);
                    return;
                }
            }
            targetPos = tp;
        }

        // Shift target by bot's personal offset so multiple bots spread out (follow + grind)
        if (entry.following || entry.grinding) {
            targetPos = new Point(targetPos.x + entry.followOffsetX, targetPos.y);
        }

        if (entry.climbing) {
            BotMovementManager.tickClimbing(entry, targetPos);
        } else if (entry.inAir) {
            BotMovementManager.tickAirborne(entry, targetPos);
        } else {
            BotMovementManager.tickGrounded(entry, targetPos);
        }
    }


    void reloginBot(int charId, int ownerCharId, int world, int channel) {
        Character owner = Server.getInstance()
                .getWorld(world)
                .getPlayerStorage()
                .getCharacterById(ownerCharId);
        if (owner == null) return; // owner logged off — skip

        try {
            BotClient botClient = new BotClient(world, channel);
            Character botChar = Character.loadCharFromDB(charId, botClient, true);
            botClient.setPlayer(botChar);
            botClient.setAccID(botChar.getAccountID());

            MapleMap map = owner.getMap();
            Point pos = map.getPointBelow(new Point(owner.getPosition().x, owner.getPosition().y - 1));
            if (pos == null) pos = owner.getPosition();

            botChar.setMapId(map.getId());
            botChar.newClient(botClient);
            botChar.recalcLocalStats();
            botChar.setPosition(pos);

            var channelServer = Server.getInstance().getChannel(world, channel);
            channelServer.addPlayer(botChar);
            channelServer.getWorldServer().addPlayer(botChar);
            botChar.setEnteredChannelWorld();
            map.addPlayer(botChar);
            botChar.broadcastStance();

            registerBot(ownerCharId, owner, botChar);
            TimerManager.getInstance().schedule(() -> botSay(botChar, "back!!"), 1000);
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
        bot.setPosition(spawnPos != null ? spawnPos : ownerPos);
        BotMovementManager.resetEntryState(entry);
        bot.setStance(BotMovementManager.cfg.STAND_STANCE); // clears tombstone for nearby clients
        BotMovementManager.broadcastMovement(bot, 0, 0);
        botSay(bot, "back!");
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
     * Called on grind start and every POT_CHECK_TICKS to handle type depletion.
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
            if (eff.getHp() > bestHp) { bestHp = eff.getHp(); hpItemId = item.getItemId(); }
            if (eff.getMp() > bestMp) { bestMp = eff.getMp(); mpItemId = item.getItemId(); }
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
            "sure", "ok boss", "time to grind");

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
        if (entry.lootInhibitTicks > 0) { entry.lootInhibitTicks--; return; }
        if (entry.pendingTradeCategory != null) return; // don't loot while trading — keeps inventory state consistent
        if (entry.invFullWarnCooldown > 0) entry.invFullWarnCooldown--;
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
                    if (entry.invFullWarnCooldown <= 0) {
                        botSay(bot, type.name().toLowerCase() + " inventory is full!");
                        entry.invFullWarnCooldown = cfg.INV_FULL_WARN_CD;
                    }
                    continue;
                }
            }
            bot.pickupItem(drop);
        }
    }

    // -------------------------------------------------------------------------
    // Potion check tick
    // -------------------------------------------------------------------------

    /** Periodically rebinds autopot and stops grinding when HP pots are critically low. */
    private void tickPotionCheck(BotEntry entry, Character bot) {
        if (entry.potCheckTimer > 0) { entry.potCheckTimer--; return; }
        entry.potCheckTimer = cfg.POT_CHECK_TICKS;

        setupAutopotForBot(bot);

        if (!entry.grinding) return;
        int[] pots = countPotions(bot);
        if (pots[0] < cfg.POT_STOP && bot.getHp() < bot.getMaxHp() * 0.5f) {
            entry.grinding = false;
            entry.following = true;
            botSay(bot, "out of HP pots!! walking to you");
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    void botSay(Character bot, String text) {
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), text, false, 0));
    }
}
