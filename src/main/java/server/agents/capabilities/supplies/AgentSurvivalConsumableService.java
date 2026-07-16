package server.agents.capabilities.supplies;

import client.Character;
import client.Disease;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.ItemId;
import net.server.channel.handlers.UseItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Narrow inventory exception for a player-owned Adventurer Partner: it may
 * consume recovery and curative items to stay alive, but it does not acquire,
 * share, sell, or restock them.
 */
public final class AgentSurvivalConsumableService {
    private static final int ANTIDOTE = 2050000;

    private AgentSurvivalConsumableService() {
    }

    public static boolean tryUseDiseaseCure(Character agent) {
        if (agent == null || !agent.isAlive()) {
            return false;
        }

        List<Integer> candidates = cureCandidates(agent);
        if (candidates.isEmpty()) {
            return false;
        }

        Inventory use = agent.getInventory(InventoryType.USE);
        for (int itemId : candidates) {
            Item item = use.findById(itemId);
            if (item != null && item.getQuantity() > 0
                    && UseItemHandler.consumeUseItem(agent, item.getPosition(), itemId)) {
                return true;
            }
        }
        return false;
    }

    static List<Integer> cureCandidates(Character agent) {
        boolean poison = agent.hasDisease(Disease.POISON);
        boolean darkness = agent.hasDisease(Disease.DARKNESS);
        boolean movement = agent.hasDisease(Disease.WEAKEN) || agent.hasDisease(Disease.SLOW);
        boolean magic = agent.hasDisease(Disease.SEAL) || agent.hasDisease(Disease.CURSE);
        int groups = (poison ? 1 : 0) + (darkness ? 1 : 0)
                + (movement ? 1 : 0) + (magic ? 1 : 0);
        if (groups == 0) {
            return List.of();
        }

        List<Integer> candidates = new ArrayList<>(5);
        if (groups > 1) {
            candidates.add(ItemId.ALL_CURE_POTION);
        }
        if (poison) {
            candidates.add(ANTIDOTE);
        }
        if (darkness) {
            candidates.add(ItemId.EYEDROP);
        }
        if (movement) {
            candidates.add(ItemId.TONIC);
        }
        if (magic) {
            candidates.add(ItemId.HOLY_WATER);
        }
        if (groups == 1) {
            candidates.add(ItemId.ALL_CURE_POTION);
        }
        return candidates;
    }
}
