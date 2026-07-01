package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import client.inventory.InventoryType;
import config.YamlConfig;
import server.ItemInformationProvider;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.equipment.AgentEquipRecommendation;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryDropService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryCollectionService;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentBotInventoryStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotEquipManager;
import server.bots.BotInventoryManager;
import server.bots.BotMovementManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Agent-owned boundary for inventory transfer commands while the deeper trade
 * state machine is still being reconstructed out of BotInventoryManager.
 */
public final class AgentInventoryTransferService {
    private AgentInventoryTransferService() {
    }

    public static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character agent) {
        if (tradeToOwner) {
            startTradeTransfer(category, entry, agent);
            return;
        }
        AgentInventoryDropService.dropCategory(
                category,
                entry,
                agent,
                AgentInventorySellTrashService::collectSellTrashEquips);
        AgentBotInventoryStateRuntime.setLootInhibitMs(
                entry,
                BotMovementManager.delayAfterCurrentTick(20_000));
    }

    public static void startTradeTransfer(String category, BotEntry entry, Character agent) {
        BotInventoryManager.startTradeTransfer(category, entry, agent);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character agent) {
        BotInventoryManager.startTradeTransfer(item, recipient, entry, agent);
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character agent) {
        return AgentInventoryTradeCollectionService.hasTransferableItems(
                category,
                agent,
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(agent, fragment, BotEquipManager::slotsFromName),
                () -> collectItems(category, entry, agent));
    }

    public static int countTransferableItems(String category, BotEntry entry, Character agent) {
        return AgentInventoryTradeCollectionService.countTransferableItems(
                category,
                agent,
                fragment -> AgentInventoryNamedItemService.countNamedItems(agent, fragment),
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(agent, fragment, BotEquipManager::slotsFromName),
                () -> AgentInventoryTradePolicy.itemQuantitySum(collectItems(category, entry, agent)));
    }

    private static List<Item> collectItems(String category, BotEntry entry, Character agent) {
        return AgentInventoryTradeCollectionService.collectItems(
                category,
                agent,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                () -> recommendedItems(entry, agent),
                () -> classifyEquipTradeGroups(entry, agent),
                () -> classifyAmmoTradeGroups(agent));
    }

    private static List<Item> recommendedItems(BotEntry entry, Character agent) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return List.of();
        }
        return new ArrayList<>(BotEquipManager.findRecommendedEquips(owner, agent).stream()
                .map(AgentEquipRecommendation::candidate)
                .toList());
    }

    private static AgentEquipTradeGroups classifyEquipTradeGroups(BotEntry entry, Character agent) {
        List<Item> all = new ArrayList<>();
        all.addAll(AgentInventoryCollectionService.collectFromBag(agent, InventoryType.EQUIP, item -> true));
        Set<Item> selfKeep = AgentEquipmentReservePolicy.collectPotentialSelfUpgradeItems(agent);

        return AgentEquipTradeGroupService.classifyEquipGroups(
                agent,
                all,
                selfKeep,
                item -> AgentOfferService.isReservedForOtherRecipients(entry, agent, item),
                AgentTradeCommandProfiler.profileCategory("equips")).groups();
    }

    private static AmmoTradeGroups classifyAmmoTradeGroups(Character agent) {
        return AgentInventoryAmmoPolicy.classifyTradeGroups(agent,
                AgentAttackExecutionProvider.getEquippedWeaponType(agent),
                ItemInformationProvider.getInstance()::getWatkForProjectile,
                ItemInformationProvider.getInstance()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
    }
}
