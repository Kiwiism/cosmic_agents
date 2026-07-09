package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.AmmoGroup;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.EquipsGroup;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.UseTradeGroups;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class AgentInventoryTradeCollectionService {
    private AgentInventoryTradeCollectionService() {
    }

    public record PreparedTradeItems(List<Item> items, String errorMessage) {}

    public static PreparedTradeItems prepareTradeItems(String category,
                                                       Character agent,
                                                       Function<String, PreparedTradeItems> equippedSlotPreparer,
                                                       Function<String, List<Item>> namedItemCollector,
                                                       Supplier<List<Item>> recommendedItems,
                                                       Supplier<AgentEquipTradeGroups> equipGroups,
                                                       Supplier<AmmoTradeGroups> ammoGroups,
                                                       Character owner) {
        if (category != null && category.startsWith("name:")) {
            String fragment = category.substring(5).trim();
            PreparedTradeItems equippedSlotItems = equippedSlotPreparer.apply(fragment);
            if (equippedSlotItems.errorMessage() != null || !equippedSlotItems.items().isEmpty()) {
                return equippedSlotItems;
            }
            return new PreparedTradeItems(namedItemCollector.apply(fragment), null);
        }

        return new PreparedTradeItems(
                collectItems(category, agent, owner, recommendedItems, equipGroups, ammoGroups),
                null);
    }

    public static List<Item> collectItems(String category,
                                          Character agent,
                                          Character owner,
                                          Supplier<List<Item>> recommendedItems,
                                          Supplier<AgentEquipTradeGroups> equipGroups,
                                          Supplier<AmmoTradeGroups> ammoGroups) {
        List<Item> result = new ArrayList<>();
        switch (category) {
            case "recommended" -> {
                if (owner != null) {
                    result.addAll(recommendedItems.get());
                }
            }
            case "scrolls" -> {
                result.addAll(AgentInventoryCollectionService.collectFromBag(agent, InventoryType.USE,
                        item -> ItemConstants.isEquipScroll(item.getItemId()),
                        CosmicAgentServerAdapter.INSTANCE.inventory()));
                result = AgentInventoryTradePolicy.prioritizeScrollTradeItems(result, owner);
            }
            case "pots" -> result.addAll(AgentInventoryCollectionService.collectFromBag(agent, InventoryType.USE,
                    item -> AgentUseItemClassificationPolicy.isRecoveryPotion(item.getItemId()),
                    CosmicAgentServerAdapter.INSTANCE.inventory()));
            case "buff" -> result.addAll(AgentInventoryCollectionService.collectFromBag(agent, InventoryType.USE,
                    item -> AgentUseItemClassificationPolicy.isBuffConsumable(item.getItemId()),
                    CosmicAgentServerAdapter.INSTANCE.inventory()));
            case "use" -> {
                UseTradeGroups groups = classifyUseTradeGroups(agent, owner);
                result.addAll(groups.uncategorized());
                result.addAll(groups.categorized());
            }
            case "ammo" -> {
                AmmoTradeGroups groups = ammoGroups.get();
                result.addAll(groups.nonOwn());
                result.addAll(groups.own());
            }
            case "equips" -> result.addAll(AgentEquipTradeGroupService.allTradeItems(equipGroups.get()));
            case "trash" -> result.addAll(equipGroups.get().itemsFor(EquipsGroup.NORMAL));
            case "etc" -> {
                result.addAll(AgentInventoryCollectionService.collectFromBag(
                        agent,
                        InventoryType.ETC,
                        item -> true,
                        CosmicAgentServerAdapter.INSTANCE.inventory()));
                result = AgentInventoryTradePolicy.prioritizeEtcTradeItems(result, owner);
            }
            default -> {
                if (AgentInventoryTradePolicy.isReservedEquipsCategory(category)) {
                    result.addAll(AgentEquipTradeGroupService.reservedEquipTradePage(category, equipGroups.get()));
                } else {
                    EquipsGroup equipsGroup = AgentInventoryTradePolicy.equipsGroupFromCategory(category);
                    if (equipsGroup != null) {
                        result.addAll(equipGroups.get().itemsFor(equipsGroup));
                    } else {
                        AmmoGroup ammoGroup = AgentInventoryTradePolicy.ammoGroupFromCategory(category);
                        if (ammoGroup != null) {
                            result.addAll(ammoGroups.get().itemsFor(ammoGroup));
                        } else if (category.startsWith("name:")) {
                            result.addAll(AgentInventoryNamedItemService.collectNamedItems(
                                    agent,
                                    category.substring(5),
                                    CosmicAgentServerAdapter.INSTANCE.inventory()));
                        }
                    }
                }
            }
        }
        return result;
    }

    public static boolean hasTransferableItems(String category,
                                               Character agent,
                                               Function<String, Integer> equippedSlotItemCounter,
                                               Supplier<List<Item>> collectedItems) {
        if (AgentInventoryTradePolicy.isMesoCategory(category)) {
            int currentMesos = agent.getMeso();
            if (currentMesos <= 0) {
                return false;
            }

            int requestedMesos = AgentInventoryTradePolicy.requestedTradeMesos(category);
            return requestedMesos <= 0 || currentMesos >= requestedMesos;
        }

        if (category != null && category.startsWith("name:")) {
            String fragment = category.substring(5);
            if (equippedSlotItemCounter.apply(fragment) > 0) {
                return true;
            }
        }

        return !collectedItems.get().isEmpty();
    }

    public static int countTransferableItems(String category,
                                             Character agent,
                                             Function<String, Integer> namedItemCounter,
                                             Function<String, Integer> equippedSlotItemCounter,
                                             IntSupplier collectedItemQuantity) {
        if (AgentInventoryTradePolicy.isMesoCategory(category)) {
            return agent.getMeso();
        }
        if (category != null && category.startsWith("name:")) {
            String fragment = category.substring(5);
            int total = namedItemCounter.apply(fragment);
            total += equippedSlotItemCounter.apply(fragment);
            return total;
        }
        return collectedItemQuantity.getAsInt();
    }

    public static UseTradeGroups classifyUseTradeGroups(Character agent, Character recipient) {
        return AgentInventoryTradePolicy.classifyUseTradeGroups(agent, recipient,
                AgentUseItemClassificationPolicy::isRecoveryPotion,
                AgentInventoryAmmoPolicy::isTradeAmmoItem,
                ItemConstants::isEquipScroll,
                AgentUseItemClassificationPolicy::isBuffConsumable,
                CosmicAgentServerAdapter.INSTANCE.inventory()::isQuestItem,
                config.YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
    }
}
