package server.agents.capabilities.supplies;

import client.Character;
import client.Skill;
import client.inventory.InventoryType;
import constants.inventory.ItemConstants;
import server.StatEffect;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skills that burn an item per cast (Shadow Partner: Summoning Rock, Mystic Door: Magic Rock) are
 * refused by the server without it. This policy turns the Agent's learned skills into a stock
 * target per item so shop visits can top them up like ammo and potions.
 */
public final class AgentSkillConsumablePolicy {
    /** Casts' worth to keep on hand. */
    public static final int STOCK_CASTS = 30;
    /** Below this fraction of the target a shop that sells the item is worth a visit. */
    private static final int RESTOCK_TRIGGER_DIVISOR = config.AgentTuning.intValue("server.agents.capabilities.supplies.AgentSkillConsumablePolicy.RESTOCK_TRIGGER_DIVISOR");

    private AgentSkillConsumablePolicy() {
    }

    /** Item id -> quantity the Agent should carry, from every learned skill with an item cost. */
    public static Map<Integer, Integer> targets(Character agent) {
        Map<Integer, Integer> targets = new LinkedHashMap<>();
        if (agent == null) {
            return targets;
        }
        for (Map.Entry<Skill, Character.SkillEntry> learned : agent.getSkills().entrySet()) {
            int level = learned.getValue().skillevel;
            if (level <= 0) {
                continue;
            }
            StatEffect effect = learned.getKey().getEffect(level);
            if (effect == null || effect.getItemConNo() <= 0 || effect.getItemCon() <= 0) {
                continue;
            }
            targets.merge(effect.getItemCon(), effect.getItemConNo() * STOCK_CASTS, Math::max);
        }
        return targets;
    }

    public static int quantity(Character agent, int itemId) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        return agent.getInventory(type) == null ? 0 : agent.getInventory(type).countById(itemId);
    }

    public static int shortfall(Character agent, int itemId, int target) {
        return Math.max(0, target - quantity(agent, itemId));
    }

    public static boolean needsRestock(Character agent, int itemId, int target) {
        return quantity(agent, itemId) < Math.max(1, target / RESTOCK_TRIGGER_DIVISOR);
    }
}
