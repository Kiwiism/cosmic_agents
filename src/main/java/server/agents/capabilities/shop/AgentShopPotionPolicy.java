package server.agents.capabilities.shop;

import server.ShopItem;
import server.StatEffect;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class AgentShopPotionPolicy {
    private AgentShopPotionPolicy() {
    }

    public record PotionShopSlot(short slot, ShopItem shopItem) {
    }

    public static PotionShopSlot selectPotionItem(List<ShopItem> items,
                                                  int maxStat,
                                                  boolean forHp,
                                                  IntPredicate recoveryPotion,
                                                  IntFunction<StatEffect> effectLookup) {
        int minRecover = (int) (maxStat * 0.10);
        int maxRecover = (int) (maxStat * 0.50);

        PotionShopSlot inBand = null;
        PotionShopSlot bestTooLow = null;
        int bestTooLowRecover = -1;
        PotionShopSlot bestTooHigh = null;
        int bestTooHighRecover = Integer.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            ShopItem item = items.get(i);
            if (item.getPrice() <= 0) {
                continue;
            }
            int itemId = item.getItemId();
            if (!recoveryPotion.test(itemId)) {
                continue;
            }

            StatEffect effect = effectLookup.apply(itemId);
            if (effect == null) {
                continue;
            }
            if (forHp && effect.getHpRate() > 0) {
                continue;
            }
            if (!forHp && effect.getMpRate() > 0) {
                continue;
            }

            int recover = forHp ? effect.getHp() : effect.getMp();
            if (recover <= 0) {
                continue;
            }

            PotionShopSlot slot = new PotionShopSlot((short) i, item);
            if (recover < minRecover) {
                if (recover > bestTooLowRecover) {
                    bestTooLowRecover = recover;
                    bestTooLow = slot;
                }
            } else if (recover > maxRecover) {
                if (recover < bestTooHighRecover) {
                    bestTooHighRecover = recover;
                    bestTooHigh = slot;
                }
            } else if (inBand == null || item.getPrice() < inBand.shopItem.getPrice()) {
                inBand = slot;
            }
        }

        if (inBand != null) {
            return inBand;
        }
        return bestTooLow != null ? bestTooLow : bestTooHigh;
    }
}
