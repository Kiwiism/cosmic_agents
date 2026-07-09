package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.agents.capabilities.inventory.AgentAmmoTradeCallbackService;
import server.agents.capabilities.inventory.AgentAmmoTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeCallbackService;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentEquipTradeSlowLogService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentInventoryTradeRuntimeService {
    private AgentInventoryTradeRuntimeService() {
    }

    public static List<Item> collectItems(String category,
                                          Character agent,
                                          Character owner,
                                          RuntimeCallbacks callbacks) {
        return AgentTradeItemCollectionService.collectItems(
                category,
                agent,
                owner,
                AgentTradeItemCollectionCallbackService.tradeItemCollectionCallbacks(
                        () -> AgentTradeRecommendationService.recommendedItems(
                                owner,
                                agent,
                                callbacks::collectRecommendedItems),
                        () -> classifyEquipTradeGroups(agent, callbacks),
                        () -> classifyAmmoTradeGroups(agent, callbacks)));
    }

    public static AmmoTradeGroups classifyAmmoTradeGroups(Character agent, RuntimeCallbacks callbacks) {
        return AgentAmmoTradeClassificationService.classifyAmmoTradeGroups(
                agent,
                AgentAmmoTradeCallbackService.ammoTradeCallbacks(
                        () -> callbacks.equippedWeaponType(agent),
                        callbacks::projectileWatk,
                        callbacks::isQuestItem,
                        callbacks::untradeableItemsTradeable));
    }

    public static AgentEquipTradeGroups classifyEquipTradeGroups(Character agent, RuntimeCallbacks callbacks) {
        return AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                agent,
                AgentEquipTradeCallbackService.equipTradeCallbacks(
                        callbacks::profileEquips,
                        AgentEquipTradeSlowLogService::slowWarnNs,
                        character -> AgentEquipTradeClassificationService.ClassificationCallbacks.collectEquipBag(
                                character,
                                callbacks::isQuestItem,
                                callbacks.untradeableItemsTradeable()),
                        callbacks::collectPotentialSelfUpgradeItems,
                        callbacks::isReservedForOtherRecipients,
                        callbacks::owner,
                        AgentEquipTradeSlowLogService::warnSlowClassification));
    }

    public interface RuntimeCallbacks {
        List<Item> collectRecommendedItems(Character owner, Character agent);

        WeaponType equippedWeaponType(Character agent);

        int projectileWatk(int itemId);

        boolean isQuestItem(int itemId);

        boolean untradeableItemsTradeable();

        boolean profileEquips();

        Set<Item> collectPotentialSelfUpgradeItems(Character agent);

        boolean isReservedForOtherRecipients(Item item);

        Character owner();

        static RuntimeCallbacks of(BiFunction<Character, Character, List<Item>> collectRecommendedItems,
                                   Function<Character, WeaponType> equippedWeaponType,
                                   IntUnaryOperator projectileWatk,
                                   IntPredicate isQuestItem,
                                   BooleanSupplier untradeableItemsTradeable,
                                   BooleanSupplier profileEquips,
                                   Function<Character, Set<Item>> collectPotentialSelfUpgradeItems,
                                   Predicate<Item> isReservedForOtherRecipients,
                                   Supplier<Character> owner) {
            return new RuntimeCallbacks() {
                @Override
                public List<Item> collectRecommendedItems(Character owner, Character agent) {
                    return collectRecommendedItems.apply(owner, agent);
                }

                @Override
                public WeaponType equippedWeaponType(Character agent) {
                    return equippedWeaponType.apply(agent);
                }

                @Override
                public int projectileWatk(int itemId) {
                    return projectileWatk.applyAsInt(itemId);
                }

                @Override
                public boolean isQuestItem(int itemId) {
                    return isQuestItem.test(itemId);
                }

                @Override
                public boolean untradeableItemsTradeable() {
                    return untradeableItemsTradeable.getAsBoolean();
                }

                @Override
                public boolean profileEquips() {
                    return profileEquips.getAsBoolean();
                }

                @Override
                public Set<Item> collectPotentialSelfUpgradeItems(Character agent) {
                    return collectPotentialSelfUpgradeItems.apply(agent);
                }

                @Override
                public boolean isReservedForOtherRecipients(Item item) {
                    return isReservedForOtherRecipients.test(item);
                }

                @Override
                public Character owner() {
                    return owner.get();
                }
            };
        }
    }
}
