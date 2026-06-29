package server.agents.capabilities.supplies;

import client.inventory.Item;
import server.StatEffect;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;

import java.util.Collection;
import java.util.function.Function;

public final class AgentPotionInventoryPolicy {
    private AgentPotionInventoryPolicy() {
    }

    public static int[] countPureRecoveryPotions(Collection<Item> items, Function<Integer, StatEffect> effectLookup) {
        int hp = 0;
        int mp = 0;
        for (Item item : items) {
            if (item.getQuantity() <= 0) {
                continue;
            }
            StatEffect effect = effectLookup.apply(item.getItemId());
            if (!AgentUseItemClassificationPolicy.isRecoveryPotion(effect)) {
                continue;
            }
            int quantity = item.getQuantity();
            if (effect.getHp() > 0 || effect.getHpRate() > 0) {
                hp += quantity;
            }
            if (effect.getMp() > 0 || effect.getMpRate() > 0) {
                mp += quantity;
            }
        }
        return new int[]{hp, mp};
    }
}
