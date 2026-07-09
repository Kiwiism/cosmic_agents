package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import config.YamlConfig;
import constants.inventory.ItemConstants;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.FieldLimit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class AgentInventoryDropService {
    private AgentInventoryDropService() {
    }

    public static void dropCategory(String category,
                                    AgentRuntimeEntry entry,
                                    Character agent,
                                    InventoryGateway inventory,
                                    BiFunction<AgentRuntimeEntry, Character, List<Item>> trashEquipCollector) {
        if (!YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE
                && FieldLimit.DROP_LIMIT.check(agent.getMap().getFieldLimit())) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.dropLimitedMapReply());
            return;
        }
        switch (category) {
            case "scrolls" -> dropScrolls(entry, agent, inventory);
            case "pots" -> dropPotions(entry, agent, inventory);
            case "buff" -> dropBuffPots(entry, agent, inventory);
            case "equips" -> dropEquips(entry, agent, inventory);
            case "trash" -> dropTrashEquips(entry, agent, inventory, trashEquipCollector.apply(entry, agent));
            case "etc" -> dropEtc(entry, agent, inventory);
            default -> {
                if (category.startsWith("name:")) {
                    dropByName(entry, agent, inventory, category.substring(5));
                }
            }
        }
    }

    static void dropScrolls(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        int count = dropFromBag(agent, InventoryType.USE,
                inventory,
                item -> ItemConstants.isEquipScroll(item.getItemId()));
        reply(entry, count, "scroll");
    }

    static void dropPotions(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        int count = dropFromBag(agent, InventoryType.USE,
                inventory,
                item -> AgentUseItemClassificationPolicy.isRecoveryPotion(item.getItemId()));
        reply(entry, count, "potion");
    }

    static void dropEquips(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        int count = dropFromBag(agent, InventoryType.EQUIP, inventory, item -> true);
        AgentInventoryRuntime.replyNow(entry,
                count > 0 ? "dropped " + count + " equip" + (count != 1 ? "s" : "") + "!"
                          : "equip bag is already empty");
    }

    static void dropTrashEquips(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory, List<Item> trashEquips) {
        Set<Item> trash = new HashSet<>(trashEquips);
        int count = dropFromBag(agent, InventoryType.EQUIP, inventory, trash::contains);
        AgentInventoryRuntime.replyNow(entry,
                count > 0 ? "dropped " + count + " trash equip" + (count != 1 ? "s" : "") + "!"
                          : "no trash equips to drop");
    }

    static void dropBuffPots(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        int count = dropFromBag(agent, InventoryType.USE,
                inventory,
                item -> AgentUseItemClassificationPolicy.isBuffConsumable(item.getItemId()));
        reply(entry, count, "buff pot");
    }

    static void dropEtc(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        int count = dropFromBag(agent, InventoryType.ETC, inventory, item -> true);
        reply(entry, count, "etc item");
    }

    static void dropByName(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory, String nameFragment) {
        String normalizedFragment = AgentInventoryNamedItemService.normalizeQuery(nameFragment);
        int total = 0;
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            total += dropFromBag(agent, type, inventory,
                    item -> AgentInventoryNamedItemService.itemNameContains(item.getItemId(), normalizedFragment, inventory));
        }
        if (total <= 0) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeNamedItemNotFoundReply(nameFragment));
        }
    }

    static int dropFromBag(Character agent, InventoryType type, InventoryGateway inventoryGateway, Predicate<Item> filter) {
        int count = 0;
        Inventory inventory = agent.getInventory(type);
        for (short slot = 1; slot <= inventory.getSlotLimit(); slot++) {
            Item item = inventory.getItem(slot);
            if (item != null
                    && AgentInventoryItemPolicy.isSafeToDrop(
                            item,
                            inventoryGateway::isQuestItem,
                            YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE)
                    && filter.test(item)) {
                InventoryManipulator.drop(agent.getClient(), type, slot, item.getQuantity());
                count++;
            }
        }
        return count;
    }

    private static void reply(AgentRuntimeEntry entry, int count, String noun) {
        AgentInventoryRuntime.replyNow(entry,
                count > 0 ? "dropped " + count + " " + noun + (count != 1 ? "s" : "") + "!"
                          : "no " + noun + "s to drop");
    }
}
