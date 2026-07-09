package server.agents.integration;

public interface InventoryGateway {
    String getItemName(int itemId);

    int getProjectileWeaponAttack(int itemId);

    boolean isQuestItem(int itemId);

    boolean isCashItem(int itemId);
}

