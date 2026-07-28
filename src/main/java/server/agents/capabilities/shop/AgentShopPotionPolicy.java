package server.agents.capabilities.shop;

import server.ShopItem;
import server.StatEffect;
import server.agents.capabilities.supplies.AgentPotionRecoveryPolicy;
import server.agents.capabilities.supplies.AgentPotionRecoveryPolicy.Recovery;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class AgentShopPotionPolicy {
    private static final int IDEAL_MIN_COVERAGE_BPS = config.AgentTuning.intValue(
            "server.agents.capabilities.shop.AgentShopPotionPolicy.IDEAL_MIN_COVERAGE_BPS");
    private static final int IDEAL_MAX_COVERAGE_BPS = config.AgentTuning.intValue(
            "server.agents.capabilities.shop.AgentShopPotionPolicy.IDEAL_MAX_COVERAGE_BPS");

    private AgentShopPotionPolicy() {
    }

    public record PotionShopSlot(short slot, ShopItem shopItem, Recovery recovery) {
    }

    public static PotionShopSlot selectPotionItem(List<ShopItem> items,
                                                  int maxHp,
                                                  int maxMp,
                                                  int targetDeficit,
                                                  boolean forHp,
                                                  IntPredicate recoveryPotion,
                                                  IntFunction<StatEffect> effectLookup) {
        PotionShopSlot best = null;
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
            Recovery recovery =
                    AgentPotionRecoveryPolicy.recovery(effect, maxHp, maxMp, forHp);
            if (recovery == null) {
                continue;
            }
            PotionShopSlot candidate = new PotionShopSlot((short) i, item, recovery);
            if (better(candidate, best, targetDeficit)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean better(
            PotionShopSlot candidate, PotionShopSlot current, int targetDeficit) {
        if (current == null) {
            return true;
        }
        int candidateBand = band(candidate.recovery.primary(), targetDeficit);
        int currentBand = band(current.recovery.primary(), targetDeficit);
        if (candidateBand != currentBand) {
            return candidateBand < currentBand;
        }
        if (candidate.recovery.mixed() != current.recovery.mixed()) {
            return !candidate.recovery.mixed();
        }
        if (candidateBand == 1
                && candidate.recovery.primary() != current.recovery.primary()) {
            return candidate.recovery.primary() > current.recovery.primary();
        }
        if (candidateBand == 2
                && candidate.recovery.primary() != current.recovery.primary()) {
            return candidate.recovery.primary() < current.recovery.primary();
        }
        long candidateCost = (long) candidate.shopItem.getPrice()
                * Math.max(1, current.recovery.weightedRecovery());
        long currentCost = (long) current.shopItem.getPrice()
                * Math.max(1, candidate.recovery.weightedRecovery());
        if (candidateCost != currentCost) {
            return candidateCost < currentCost;
        }
        int candidateDistance = Math.abs(candidate.recovery.primary() - targetDeficit);
        int currentDistance = Math.abs(current.recovery.primary() - targetDeficit);
        if (candidateDistance != currentDistance) {
            return candidateDistance < currentDistance;
        }
        return candidate.shopItem.getItemId() < current.shopItem.getItemId();
    }

    private static int band(int recovery, int targetDeficit) {
        int coverage = AgentPotionRecoveryPolicy.coverageBasisPoints(recovery, targetDeficit);
        if (coverage >= IDEAL_MIN_COVERAGE_BPS && coverage <= IDEAL_MAX_COVERAGE_BPS) {
            return 0;
        }
        return coverage < IDEAL_MIN_COVERAGE_BPS ? 1 : 2;
    }
}
