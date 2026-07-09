package server.agents.integration.cosmic;

import client.inventory.Equip;
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
}
