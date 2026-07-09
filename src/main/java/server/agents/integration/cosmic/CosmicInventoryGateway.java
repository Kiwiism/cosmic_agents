package server.agents.integration.cosmic;

import server.ItemInformationProvider;
import server.agents.integration.InventoryGateway;

public enum CosmicInventoryGateway implements InventoryGateway {
    INSTANCE;

    @Override
    public int getProjectileWeaponAttack(int itemId) {
        return ItemInformationProvider.getInstance().getWatkForProjectile(itemId);
    }
}
