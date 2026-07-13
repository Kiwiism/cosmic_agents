package server.agents.integration;

import client.inventory.Equip;
import client.Character;
import client.inventory.InventoryType;
import client.Job;
import client.inventory.WeaponType;
import server.StatEffect;

import java.util.Map;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Inventory mutations are scoped to the owning Agent and use normal Cosmic manipulators.")
public interface InventoryGateway {
    String getItemName(int itemId);

    StatEffect getItemEffect(int itemId);

    int getProjectileWeaponAttack(int itemId);

    short getSlotMax(Character agent, int itemId);

    boolean isQuestItem(int itemId);

    boolean isCashItem(int itemId);

    Map<String, Integer> getEquipStats(int itemId);

    Equip getEquipById(int itemId);

    String getEquipmentSlot(int itemId);

    int getEquipLevelRequirement(int itemId);

    WeaponType getWeaponType(int itemId);

    boolean isTwoHandedWeapon(int itemId);

    int getMakerCrystalFromLeftover(int leftoverId);

    boolean meetsEquipRequirements(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame);

    boolean canWearEquipment(Character agent, Equip equip, short primarySlot);

    void dropItem(Character agent, InventoryType type, short slot, short quantity);

    void removeFromSlot(Character agent, InventoryType type, short slot, short quantity, boolean fromDrop);

    void removeById(Character agent, InventoryType type, int itemId, int quantity, boolean fromDrop, boolean consume);

    void moveItem(Character agent, InventoryType type, short sourceSlot, short destinationSlot, short quantity);

    boolean addItem(Character agent, int itemId, short quantity);

    boolean consumeUseItem(Character agent, short slot, int itemId);
}

