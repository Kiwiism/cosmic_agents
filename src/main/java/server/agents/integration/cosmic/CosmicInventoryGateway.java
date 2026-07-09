package server.agents.integration.cosmic;

import server.ItemInformationProvider;
import server.agents.integration.InventoryGateway;

public enum CosmicInventoryGateway implements InventoryGateway {
    INSTANCE;

    @Override
    public String getItemName(int itemId) {
        return ItemInformationProvider.getInstance().getName(itemId);
    }

    @Override
    public int getProjectileWeaponAttack(int itemId) {
        return ItemInformationProvider.getInstance().getWatkForProjectile(itemId);
    }

    @Override
    public boolean isQuestItem(int itemId) {
        return ItemInformationProvider.getInstance().isQuestItem(itemId);
    }
}
