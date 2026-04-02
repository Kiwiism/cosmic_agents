package server.bots;

import client.BuffStat;
import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.ItemInformationProvider;
import server.StatEffect;
import server.life.Monster;
import tools.Pair;

import java.util.*;

/**
 * Manages automatic use of buff consumable items from the bot's USE inventory.
 *
 * Validity check: BotDropManager.isBuffConsumable (has statups) — same predicate used by
 * inv? and the trade/drop "buff" category, so all commands are consistent.
 * Items are grouped by primary BuffStat from the bot's current inventory.
 * Cheap mode picks the weakest per stat; max picks the strongest.
 *
 * Default: off. Configured via chat ("buff on/off", "buff cheap/max").
 */
public final class BotBuffManager {

    private static final long   TICK_MS           = 3_000;
    private static final double ACC_HIT_THRESHOLD = 0.60;

    // Cache StatEffect lookups to avoid repeated WZ reads
    private static final Map<Integer, StatEffect> fxCache = new HashMap<>();

    private BotBuffManager() {}

    // ── tick ────────────────────────────────────────────────────────────────

    public static void tick(BotEntry entry, Character bot) {
        if (!entry.buffConsumablesEnabled) return;
        long now = System.currentTimeMillis();
        if (now - entry.lastBuffScanMs < TICK_MS) return;
        entry.lastBuffScanMs = now;

        // Build per-stat selection from what the bot actually has in bag
        Map<BuffStat, Item> selected = buildSelection(bot, entry.buffCheapMode);

        for (Map.Entry<BuffStat, Item> e : selected.entrySet()) {
            BuffStat stat = e.getKey();
            if (bot.getBuffedValue(stat) != null) continue;
            if (stat == BuffStat.ACC && !needsAccBuff(entry, bot)) continue;

            Item item = e.getValue();
            StatEffect fx = fxCache.get(item.getItemId());
            if (fx == null) continue;

            fx.applyTo(bot);
            InventoryManipulator.removeFromSlot(bot.getClient(), InventoryType.USE,
                    item.getPosition(), (short) 1, false, true);
            return; // one buff per tick cycle
        }
    }

    // ── chat ────────────────────────────────────────────────────────────────

    /**
     * Returns a chat-line showing buff items the bot currently has in inventory,
     * with quantities, based on cheap/max mode. Example:
     *   "buff pots on (cheap): sniper pill x3, warrior potion x5"
     */
    public static String getChatSummary(boolean enabled, boolean cheapMode, Character bot) {
        Map<BuffStat, Item> selected = buildSelection(bot, cheapMode);
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("buff pots ").append(enabled ? "on" : "off")
          .append(" (").append(cheapMode ? "cheap" : "max").append("): ");
        boolean first = true;
        for (Item item : selected.values()) {
            String name = ii.getName(item.getItemId());
            if (name == null) name = String.valueOf(item.getItemId());
            if (!first) sb.append(", ");
            sb.append(name.toLowerCase()).append(" x").append(item.getQuantity());
            first = false;
        }
        if (first) sb.append("none in bag");
        return sb.toString();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Scans the bot's USE inventory and returns one item per primary BuffStat,
     * choosing the weakest (cheap) or strongest (max) when duplicates exist.
     */
    private static Map<BuffStat, Item> buildSelection(Character bot, boolean cheapMode) {
        Inventory use = bot.getInventory(InventoryType.USE);
        Map<BuffStat, Item>    best    = new LinkedHashMap<>();
        Map<BuffStat, Integer> bestVal = new HashMap<>();

        for (short slot = 1; slot <= use.getSlotLimit(); slot++) {
            Item item = use.getItem(slot);
            if (item == null || item.getQuantity() <= 0) continue;
            int id = item.getItemId();
            if (!BotDropManager.isBuffConsumable(id)) continue;

            StatEffect fx = fxCache.computeIfAbsent(id, BotDropManager::itemEffect);
            if (fx == null || fx.getStatups().isEmpty()) continue;

            Pair<BuffStat, Integer> primary = fx.getStatups().get(0);
            BuffStat stat = primary.getLeft();
            int val       = primary.getRight();

            Integer prev = bestVal.get(stat);
            if (prev == null || (cheapMode ? val < prev : val > prev)) {
                best.put(stat, item);
                bestVal.put(stat, val);
            }
        }
        return best;
    }

    /**
     * Returns true when the bot's hit rate against the reference mob is below ACC_HIT_THRESHOLD.
     */
    private static boolean needsAccBuff(BotEntry entry, Character bot) {
        Monster ref = entry.grindTarget;
        if (ref == null) {
            for (Monster m : bot.getMap().getAllMonsters()) {
                if (m.isAlive()) { ref = m; break; }
            }
        }
        if (ref == null) return false;
        return BotCombatFormulaProvider.getInstance().calculateMobHitChance(bot, ref) < ACC_HIT_THRESHOLD;
    }
}
