package server.agents.capabilities.supplies;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.StatEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class AgentPotionSharePolicy {
    private AgentPotionSharePolicy() {
    }

    public static boolean canShareForSlot(StatEffect effect, boolean forHp) {
        if (effect == null) {
            return false;
        }
        if (forHp) {
            return effect.getHp() != 0 || effect.getHpRate() != 0;
        }
        return effect.getMp() != 0 || effect.getMpRate() != 0;
    }

    public static int recoveryScore(StatEffect effect, boolean forHp) {
        if (effect == null) {
            return Integer.MAX_VALUE;
        }
        if (forHp) {
            if (effect.getHpRate() > 0) {
                return 1_000_000 + (int) (effect.getHpRate() * 1000);
            }
            return effect.getHp();
        }
        if (effect.getMpRate() > 0) {
            return 1_000_000 + (int) (effect.getMpRate() * 1000);
        }
        return effect.getMp();
    }

    public static List<Item> collectShareItems(Character donorAgent,
                                               boolean forHp,
                                               int maxQty,
                                               IntPredicate isRecoveryPotion,
                                               IntFunction<StatEffect> itemEffect) {
        if (maxQty <= 0) return List.of();
        List<Item> candidates = new ArrayList<>();
        Inventory useInv = donorAgent.getInventory(InventoryType.USE);
        for (short slot = 1; slot <= useInv.getSlotLimit(); slot++) {
            Item item = useInv.getItem(slot);
            if (item == null || !isRecoveryPotion.test(item.getItemId())) continue;
            StatEffect effect = itemEffect.apply(item.getItemId());
            if (effect == null) continue;
            if (!canShareForSlot(effect, forHp)) continue;
            candidates.add(item);
        }
        candidates.sort(Comparator.comparingInt(item -> recoveryScore(itemEffect.apply(item.getItemId()), forHp)));
        List<Item> result = new ArrayList<>();
        int totalQty = 0;
        for (Item item : candidates) {
            if (result.size() >= 9 || totalQty >= maxQty) break;
            result.add(item);
            totalQty += item.getQuantity();
        }
        return result;
    }
}
