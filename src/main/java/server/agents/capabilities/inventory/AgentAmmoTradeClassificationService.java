package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

public final class AgentAmmoTradeClassificationService {
    private AgentAmmoTradeClassificationService() {
    }

    public static AmmoTradeGroups classifyAmmoTradeGroups(Character agent, AmmoTradeCallbacks callbacks) {
        callbacks.untradeableItemsTradeable();
        return AgentInventoryAmmoPolicy.classifyTradeGroups(
                agent,
                callbacks.equippedWeaponType(),
                callbacks.projectileWatk(),
                callbacks.isQuestItem(),
                callbacks::allowsUntradeableItem);
    }

    public static String nextAmmoGroup(String category, AmmoTradeGroups groups) {
        return AgentInventoryAmmoPolicy.nextAvailableGroupCategory(category, groups);
    }

    public interface AmmoTradeCallbacks {
        WeaponType equippedWeaponType();
        IntUnaryOperator projectileWatk();
        IntPredicate isQuestItem();
        boolean allowsUntradeableItem(int itemId);

        default boolean untradeableItemsTradeable() {
            return allowsUntradeableItem(0);
        }

        static AmmoTradeCallbacks of(Supplier<WeaponType> equippedWeaponType,
                                    IntUnaryOperator projectileWatk,
                                    IntPredicate isQuestItem,
                                    IntPredicate allowsUntradeableItem) {
            return new AmmoTradeCallbacks() {
                @Override
                public WeaponType equippedWeaponType() {
                    return equippedWeaponType.get();
                }

                @Override
                public IntUnaryOperator projectileWatk() {
                    return projectileWatk;
                }

                @Override
                public IntPredicate isQuestItem() {
                    return isQuestItem;
                }

                @Override
                public boolean allowsUntradeableItem(int itemId) {
                    return allowsUntradeableItem.test(itemId);
                }
            };
        }

        static AmmoTradeCallbacks of(Supplier<WeaponType> equippedWeaponType,
                                    IntUnaryOperator projectileWatk,
                                    IntPredicate isQuestItem,
                                    Supplier<Boolean> untradeableItemsTradeable) {
            return new AmmoTradeCallbacks() {
                @Override
                public WeaponType equippedWeaponType() {
                    return equippedWeaponType.get();
                }

                @Override
                public IntUnaryOperator projectileWatk() {
                    return projectileWatk;
                }

                @Override
                public IntPredicate isQuestItem() {
                    return isQuestItem;
                }

                @Override
                public boolean allowsUntradeableItem(int itemId) {
                    return untradeableItemsTradeable.get();
                }
            };
        }
    }
}
