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
 * Safe list: shop-buyable USE items in the 2000000–2009999 range priced 2–10 000 mesos.
 * Items are grouped by their primary BuffStat. Cheap mode picks the weakest; max picks the best.
 *
 * Default: off. Configured via chat ("potbuff on/off", "potbuff cheap/max").
 */
public final class BotBuffManager {

    private static final int  MIN_PRICE = 2;        // exclude 1-meso GM-shop entries
    private static final int  MAX_PRICE = 10_000;
    private static final long TICK_MS   = 3_000;
    private static final double ACC_HIT_THRESHOLD = 0.60;

    // primary BuffStat -> {cheapItemId, bestItemId}
    private static final Map<BuffStat, int[]>    safeItems = new LinkedHashMap<>();
    private static final Map<Integer, StatEffect> fxCache  = new HashMap<>();
    private static volatile boolean initialized            = false;

    private BotBuffManager() {}

    // ── init ────────────────────────────────────────────────────────────────

    /** Called once (lazily) to build the safe-buff list from WZ/XML data. */
    public static synchronized void ensureInit() {
        if (initialized) return;
        initialized = true;
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Map<BuffStat, List<int[]>> candidates = new LinkedHashMap<>(); // stat -> [[itemId, statValue]]

        // Scan the buff potion range (2000000–2009999)
        for (int itemId : ii.getItemIdsInRange(2000000, 2009999, false)) {
            int price;
            try { price = ii.getPrice(itemId, 1); } catch (Exception e) { continue; }
            if (price < MIN_PRICE || price > MAX_PRICE) continue;

            StatEffect fx;
            try { fx = ii.getItemEffect(itemId); } catch (Exception e) { continue; }
            if (fx == null || fx.getStatups().isEmpty()) continue;

            Pair<BuffStat, Integer> primary = fx.getStatups().get(0);
            candidates.computeIfAbsent(primary.getLeft(), k -> new ArrayList<>())
                      .add(new int[]{itemId, primary.getRight()});
            fxCache.put(itemId, fx);
        }

        for (Map.Entry<BuffStat, List<int[]>> e : candidates.entrySet()) {
            List<int[]> list = e.getValue();
            list.sort(Comparator.comparingInt(a -> a[1]));
            safeItems.put(e.getKey(), new int[]{list.get(0)[0], list.get(list.size() - 1)[0]});
        }

        System.out.println("[BotBuff] Safe buff list built: " + safeItems.size() + " buff type(s).");
    }

    // ── tick ────────────────────────────────────────────────────────────────

    public static void tick(BotEntry entry, Character bot) {
        if (!entry.buffConsumablesEnabled) return;
        long now = System.currentTimeMillis();
        if (now - entry.lastBuffScanMs < TICK_MS) return;
        entry.lastBuffScanMs = now;

        ensureInit();

        Inventory use = bot.getInventory(InventoryType.USE);
        for (Map.Entry<BuffStat, int[]> e : safeItems.entrySet()) {
            BuffStat stat = e.getKey();

            // Don't use if already buffed with this stat
            if (bot.getBuffedValue(stat) != null) continue;

            // ACC potions only when hit rate against current mobs is below threshold
            if (stat == BuffStat.ACC && !needsAccBuff(entry, bot)) continue;

            int itemId = entry.buffCheapMode ? e.getValue()[0] : e.getValue()[1];
            Item item = use.findById(itemId);
            if (item == null || item.getQuantity() <= 0) continue;

            StatEffect fx = fxCache.get(itemId);
            if (fx == null) continue;

            fx.applyTo(bot);
            InventoryManipulator.removeFromSlot(bot.getClient(), InventoryType.USE,
                    item.getPosition(), (short) 1, false, true);
            return; // one buff potion per tick cycle
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns true when the bot's hit rate against the reference mob is below ACC_HIT_THRESHOLD.
     * Uses the grind target if available; falls back to any live monster on the map.
     */
    private static boolean needsAccBuff(BotEntry entry, Character bot) {
        Monster ref = entry.grindTarget;
        if (ref == null) {
            for (Monster m : bot.getMap().getAllMonsters()) {
                if (m.isAlive()) { ref = m; break; }
            }
        }
        if (ref == null) return false;
        double hitRate = BotCombatFormulaProvider.getInstance().calculateMobHitChance(bot, ref);
        return hitRate < ACC_HIT_THRESHOLD;
    }

    // ── debug ─────────────────────────────────────────────────────────────

    /** Returns a printable summary of the safe buff list. */
    public static String getSafeListSummary() {
        ensureInit();
        if (safeItems.isEmpty()) return "No safe buff items loaded.";
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        StringBuilder sb = new StringBuilder("Safe buff list (" + safeItems.size() + " type(s)):\n");
        for (Map.Entry<BuffStat, int[]> e : safeItems.entrySet()) {
            int cheapId = e.getValue()[0];
            int bestId  = e.getValue()[1];
            sb.append("  ").append(e.getKey().name())
              .append(": cheap=").append(ii.getName(cheapId)).append("(").append(cheapId).append(")")
              .append("  best=").append(ii.getName(bestId)).append("(").append(bestId).append(")\n");
        }
        return sb.toString().trim();
    }
}
