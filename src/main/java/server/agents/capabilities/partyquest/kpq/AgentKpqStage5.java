package server.agents.capabilities.partyquest.kpq;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.partyquest.AgentPqRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * KPQ Stage 5 reward automation — detects when the stage is cleared and
 * claims the event reward on behalf of the bot without requiring NPC interaction.
 */
public final class AgentKpqStage5 {

    static final int KPQ_STAGE5_MAP = 103000804;

    public static void tick(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        if (bot.getMapId() != KPQ_STAGE5_MAP) return;
        if (AgentPqRuntime.kpqStage5Claimed(entry)) return;

        EventInstanceManager eim = bot.getEventInstance();
        if (eim == null || eim.getProperty("5stageclear") == null) return;

        Map<Integer, Integer> before = snapshotInventory(bot);
        boolean success = eim.giveEventReward(bot);
        if (success) {
            AgentPqRuntime.markKpqStage5Claimed(entry);
            String reward = findNewItem(before, snapshotInventory(bot), inventory);
            AgentPqRuntime.queueSay(entry, reward != null ? "r, I got " + reward : "r");
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

    static String findNewItem(Map<Integer, Integer> before, Map<Integer, Integer> after, InventoryGateway inventory) {
        for (Map.Entry<Integer, Integer> e : after.entrySet()) {
            int delta = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (delta > 0) {
                String name = inventory.getItemName(e.getKey());
                return (delta > 1 ? delta + "x " : "") + (name != null ? name : "item " + e.getKey());
            }
        }
        return null;
    }
}
