package server.agents.integration.cosmic;

import server.ItemInformationProvider;
import server.StatEffect;
import server.agents.integration.InventoryGateway;

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
}
