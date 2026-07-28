package server.agents.capabilities.shop;

/**
 * Pure purchase sizing and reserve policy. Cosmic shop mutations remain in
 * {@link AgentShopService}.
 */
public final class AgentPotionPurchasePolicy {
    private static final double HP_TRIGGER_RESERVE_BARS = config.AgentTuning.doubleValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.HP_TRIGGER_RESERVE_BARS");
    private static final double MP_TRIGGER_RESERVE_BARS = config.AgentTuning.doubleValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.MP_TRIGGER_RESERVE_BARS");
    private static final double HP_CRITICAL_RESERVE_BARS = config.AgentTuning.doubleValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.HP_CRITICAL_RESERVE_BARS");
    private static final double MP_CRITICAL_RESERVE_BARS = config.AgentTuning.doubleValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.MP_CRITICAL_RESERVE_BARS");
    private static final double HP_TARGET_RESERVE_BARS = config.AgentTuning.doubleValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.HP_TARGET_RESERVE_BARS");
    private static final double MP_TARGET_RESERVE_BARS = config.AgentTuning.doubleValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.MP_TARGET_RESERVE_BARS");
    private static final int MIN_PURCHASE_QUANTITY = config.AgentTuning.intValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.MIN_PURCHASE_QUANTITY");
    private static final int MAX_CARRIED_QUANTITY = config.AgentTuning.intValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.MAX_CARRIED_QUANTITY");
    private static final int MAX_PURCHASE_QUANTITY_PER_VISIT = config.AgentTuning.intValue(
            "server.agents.capabilities.shop.AgentPotionPurchasePolicy.MAX_PURCHASE_QUANTITY_PER_VISIT");

    private AgentPotionPurchasePolicy() {
    }

    public static double triggerReserveBars(boolean forHp) {
        return forHp ? HP_TRIGGER_RESERVE_BARS : MP_TRIGGER_RESERVE_BARS;
    }

    public static double criticalReserveBars(boolean forHp) {
        return forHp ? HP_CRITICAL_RESERVE_BARS : MP_CRITICAL_RESERVE_BARS;
    }

    public static double targetReserveBars(boolean forHp) {
        return forHp ? HP_TARGET_RESERVE_BARS : MP_TARGET_RESERVE_BARS;
    }

    public static boolean belowReserve(long recoveryCapacity, int maxStat, double reserveBars) {
        return recoveryCapacity < targetCapacity(maxStat, reserveBars);
    }

    public static int quantityToTarget(
            long recoveryCapacity,
            int carriedQuantity,
            int recoveryPerPotion,
            int maxStat,
            double reserveBars,
            int alreadyBoughtThisVisit,
            boolean applyMinimumPurchase) {
        if (recoveryPerPotion <= 0 || maxStat <= 0) {
            return 0;
        }
        long shortfall = Math.max(0L, targetCapacity(maxStat, reserveBars) - recoveryCapacity);
        if (shortfall == 0L) {
            return 0;
        }
        long raw = (shortfall + recoveryPerPotion - 1L) / recoveryPerPotion;
        if (applyMinimumPurchase) {
            raw = Math.max(raw, MIN_PURCHASE_QUANTITY);
        }
        int carryRoom = Math.max(0, MAX_CARRIED_QUANTITY - Math.max(0, carriedQuantity));
        int visitRoom = Math.max(
                0,
                MAX_PURCHASE_QUANTITY_PER_VISIT - Math.max(0, alreadyBoughtThisVisit));
        return (int) Math.min(raw, Math.min(carryRoom, visitRoom));
    }

    public static int normalSpendBudget(int spendableMesos, boolean anotherResourceNeedsNormalStock) {
        if (spendableMesos <= 0) {
            return 0;
        }
        return anotherResourceNeedsNormalStock ? Math.max(1, spendableMesos / 2) : spendableMesos;
    }

    private static long targetCapacity(int maxStat, double reserveBars) {
        return Math.round(Math.max(0, maxStat) * Math.max(0.0, reserveBars));
    }
}
