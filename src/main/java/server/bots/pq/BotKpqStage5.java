package server.bots.pq;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import scripting.event.EventInstanceManager;
import server.ItemInformationProvider;
import server.bots.BotChatManager;
import server.bots.BotEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * KPQ Stage 5 reward automation — detects when the stage is cleared and
 * claims the event reward on behalf of the bot without requiring NPC interaction.
 */
final class BotKpqStage5 {

    static final int KPQ_STAGE5_MAP = 103000804;

    static void tick(BotEntry entry, Character bot) {
        if (bot.getMapId() != KPQ_STAGE5_MAP) return;
        if (entry.kpq.stage5Claimed) return;

        EventInstanceManager eim = bot.getEventInstance();
        if (eim == null || eim.getProperty("5stageclear") == null) return;

        Map<Integer, Integer> before = snapshotInventory(bot);
        boolean success = eim.giveEventReward(bot);
        if (success) {
            entry.kpq.stage5Claimed = true;
            String reward = findNewItem(before, snapshotInventory(bot));
            BotChatManager.queueBotSay(entry, reward != null ? "r, I got " + reward : "r");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Map<Integer, Integer> snapshotInventory(Character bot) {
        Map<Integer, Integer> snap = new HashMap<>();
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.EQUIPPED || type == InventoryType.UNDEFINED) continue;
            Inventory inv = bot.getInventory(type);
            if (inv == null) continue;
            for (Item item : inv.list()) {
                snap.merge(item.getItemId(), (int) item.getQuantity(), Integer::sum);
            }
        }
        return snap;
    }

    private static String findNewItem(Map<Integer, Integer> before, Map<Integer, Integer> after) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (Map.Entry<Integer, Integer> e : after.entrySet()) {
            int delta = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (delta > 0) {
                String name = ii.getName(e.getKey());
                return (delta > 1 ? delta + "x " : "") + (name != null ? name : "item " + e.getKey());
            }
        }
        return null;
    }
}
