package server.agents.integration.cosmic;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.WeaponType;
import server.ItemInformationProvider;
import server.StatEffect;
import server.agents.integration.InventoryGateway;

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
}
