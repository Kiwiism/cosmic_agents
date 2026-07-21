package server.agents.integration.cosmic;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import client.inventory.Inventory;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import net.server.channel.handlers.AgentUseItemBridge;
import server.ItemInformationProvider;
import server.StatEffect;
import server.agents.integration.InventoryGateway;
import server.agents.events.AgentEventPriority;
import server.agents.resources.events.AgentEquipmentLoadoutChangedEvent;
import server.agents.resources.events.AgentItemQuantityChangedEvent;
import server.agents.resources.events.AgentResourceEventPublisher;

import java.util.LinkedHashMap;
import java.util.Map;

public enum CosmicInventoryGateway implements InventoryGateway {
    INSTANCE;

    @Override
    public String getItemName(int itemId) {
        ItemInformationProvider itemInfo = ItemInformationProvider.getInstance();
        synchronized (itemInfo) {
            return itemInfo.getName(itemId);
        }
    }

    @Override
    public StatEffect getItemEffect(int itemId) {
        return ItemInformationProvider.getInstance().getItemEffect(itemId);
    }

    @Override
    public int getProjectileWeaponAttack(int itemId) {
        return ItemInformationProvider.getInstance().getWatkForProjectile(itemId);
    }

    @Override
    public short getSlotMax(Character agent, int itemId) {
        return ItemInformationProvider.getInstance().getSlotMax(agent.getClient(), itemId);
    }

    @Override
    public boolean isQuestItem(int itemId) {
        return ItemInformationProvider.getInstance().isQuestItem(itemId);
    }

    @Override
    public boolean isCashItem(int itemId) {
        return ItemInformationProvider.getInstance().isCash(itemId);
    }

    @Override
    public Map<String, Integer> getEquipStats(int itemId) {
        return ItemInformationProvider.getInstance().getEquipStats(itemId);
    }

    @Override
    public Equip getEquipById(int itemId) {
        return (Equip) ItemInformationProvider.getInstance().getEquipById(itemId);
    }

    @Override
    public String getEquipmentSlot(int itemId) {
        return ItemInformationProvider.getInstance().getEquipmentSlot(itemId);
    }

    @Override
    public int getEquipLevelRequirement(int itemId) {
        return ItemInformationProvider.getInstance().getEquipLevelReq(itemId);
    }

    @Override
    public WeaponType getWeaponType(int itemId) {
        return ItemInformationProvider.getInstance().getWeaponType(itemId);
    }

    @Override
    public boolean isTwoHandedWeapon(int itemId) {
        return ItemInformationProvider.getInstance().isTwoHanded(itemId);
    }

    @Override
    public int getMakerCrystalFromLeftover(int leftoverId) {
        return ItemInformationProvider.getInstance().getMakerCrystalFromLeftover(leftoverId);
    }

    @Override
    public boolean meetsEquipRequirements(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame) {
        return ItemInformationProvider.getInstance().meetsEquipRequirements(equip, job, level, str, dex, int_, luk, fame);
    }

    @Override
    public boolean canWearEquipment(Character agent, Equip equip, short primarySlot) {
        return ItemInformationProvider.getInstance().canWearEquipment(agent, equip, primarySlot);
    }

    @Override
    public void dropItem(Character agent, InventoryType type, short slot, short quantity) {
        Item item = agent.getInventory(type).getItem(slot);
        int itemId = item == null ? 0 : item.getItemId();
        int before = itemId <= 0 ? 0 : count(agent, type, itemId);
        InventoryManipulator.drop(agent.getClient(), type, slot, quantity);
        publishQuantity(agent, type, itemId, before, "drop");
    }

    @Override
    public void removeFromSlot(Character agent, InventoryType type, short slot, short quantity, boolean fromDrop) {
        Item item = agent.getInventory(type).getItem(slot);
        int itemId = item == null ? 0 : item.getItemId();
        int before = itemId <= 0 ? 0 : count(agent, type, itemId);
        InventoryManipulator.removeFromSlot(agent.getClient(), type, slot, quantity, fromDrop);
        publishQuantity(agent, type, itemId, before, fromDrop ? "drop" : "remove-slot");
    }

    @Override
    public void removeById(Character agent, InventoryType type, int itemId, int quantity, boolean fromDrop,
                           boolean consume) {
        int before = count(agent, type, itemId);
        InventoryManipulator.removeById(agent.getClient(), type, itemId, quantity, fromDrop, consume);
        publishQuantity(agent, type, itemId, before, consume ? "consume" : fromDrop ? "drop" : "remove-id");
    }

    @Override
    public void moveItem(Character agent, InventoryType type, short sourceSlot, short destinationSlot, short quantity) {
        Map<Short, Integer> before = equippedLoadout(agent);
        InventoryManipulator.handleItemMove(agent.getClient(), type, sourceSlot, destinationSlot, quantity);
        publishLoadout(agent, before, equippedLoadout(agent), "inventory-move");
    }

    @Override
    public boolean addItem(Character agent, int itemId, short quantity) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        int before = count(agent, type, itemId);
        boolean added = InventoryManipulator.addById(agent.getClient(), itemId, quantity);
        if (added) {
            publishQuantity(agent, type, itemId, before, "add");
        }
        return added;
    }

    @Override
    public boolean consumeUseItem(Character agent, short slot, int itemId) {
        int before = count(agent, InventoryType.USE, itemId);
        boolean consumed = AgentUseItemBridge.consumeUseItem(agent, slot, itemId);
        if (consumed) {
            publishQuantity(agent, InventoryType.USE, itemId, before, "consume");
        }
        return consumed;
    }

    private static int count(Character agent, InventoryType type, int itemId) {
        Inventory inventory = agent == null || type == null ? null : agent.getInventory(type);
        return inventory == null || itemId <= 0 ? 0 : inventory.countById(itemId);
    }

    private static void publishQuantity(Character agent,
                                        InventoryType type,
                                        int itemId,
                                        int previousQuantity,
                                        String source) {
        int quantity = count(agent, type, itemId);
        if (itemId <= 0 || previousQuantity == quantity) {
            return;
        }
        AgentResourceEventPublisher.publishFor(agent,
                objectiveId -> new AgentItemQuantityChangedEvent(
                        agent.getId(), System.currentTimeMillis(), itemId, previousQuantity, quantity,
                        type.name(), source, objectiveId),
                AgentEventPriority.NORMAL);
    }

    private static Map<Short, Integer> equippedLoadout(Character agent) {
        Inventory equipped = agent == null ? null : agent.getInventory(InventoryType.EQUIPPED);
        if (equipped == null) {
            return Map.of();
        }
        Map<Short, Integer> loadout = new LinkedHashMap<>();
        for (Item item : equipped.list()) {
            if (item != null) {
                loadout.put(item.getPosition(), item.getItemId());
            }
        }
        return Map.copyOf(loadout);
    }

    private static void publishLoadout(Character agent,
                                       Map<Short, Integer> previous,
                                       Map<Short, Integer> current,
                                       String reason) {
        if (previous.equals(current)) {
            return;
        }
        AgentResourceEventPublisher.publishFor(agent,
                objectiveId -> new AgentEquipmentLoadoutChangedEvent(
                        agent.getId(), System.currentTimeMillis(), previous, current, reason, objectiveId),
                AgentEventPriority.IMPORTANT);
    }
}
