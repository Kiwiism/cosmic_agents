package server.agents.integration;

import server.StatEffect;

public interface InventoryGateway {
    String getItemName(int itemId);

    StatEffect getItemEffect(int itemId);

    int getProjectileWeaponAttack(int itemId);

    boolean isQuestItem(int itemId);

    boolean isCashItem(int itemId);
}

