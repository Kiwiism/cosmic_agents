package server.agents.capabilities.trade;

import client.inventory.Item;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.AmmoGroup;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.EquipsGroup;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AgentGroupedTradeTransferService {
    private AgentGroupedTradeTransferService() {
    }

    public static void startEquipsGroupTradeTransfer(AgentEquipTradeGroups groups,
                                                     BiConsumer<String, List<Item>> startTradeSequence,
                                                     Function<String, String> categoryMessage,
                                                     BiConsumer<String, String> setCategoryMessage,
                                                     Consumer<String> reply) {
        EquipsGroup group = AgentEquipTradeGroupService.firstAvailableGroup(groups);
        if (group != null) {
            String category = group.categoryString();
            startTradeSequence.accept(category, groups.itemsFor(group));
            String message = categoryMessage.apply(category);
            if (message != null) {
                setCategoryMessage.accept(category, message);
            }
            return;
        }
        reply.accept(AgentInventoryDialogueReporter.noItemsReply("equips"));
    }

    public static void startAmmoGroupTradeTransfer(AmmoTradeGroups groups,
                                                   BiConsumer<String, List<Item>> startTradeSequence,
                                                   Consumer<String> reply) {
        AmmoGroup group = AgentInventoryAmmoPolicy.firstAvailableGroup(groups);
        if (group != null) {
            startTradeSequence.accept(group.categoryString(), groups.itemsFor(group));
            return;
        }
        reply.accept(AgentInventoryDialogueReporter.noItemsReply("ammo"));
    }
}
