package server.agents.capabilities.inventory;

import client.inventory.WeaponType;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

public final class AgentAmmoTradeCallbackService {
    private AgentAmmoTradeCallbackService() {
    }

    public static AgentAmmoTradeClassificationService.AmmoTradeCallbacks ammoTradeCallbacks(
            Supplier<WeaponType> equippedWeaponType,
            IntUnaryOperator projectileWatk,
            IntPredicate isQuestItem,
            Supplier<Boolean> untradeableItemsTradeable) {
        return AgentAmmoTradeClassificationService.AmmoTradeCallbacks.of(
                equippedWeaponType,
                projectileWatk,
                isQuestItem,
                untradeableItemsTradeable);
    }
}
