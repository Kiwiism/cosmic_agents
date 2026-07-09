package server.agents.integration;

import client.inventory.Equip;
import client.Character;
import server.StatEffect;

import java.util.Map;

public interface InventoryGateway {
    String getItemName(int itemId);

    StatEffect getItemEffect(int itemId);

    int getProjectileWeaponAttack(int itemId);

    short getSlotMax(Character agent, int itemId);

    boolean isQuestItem(int itemId);

    boolean isCashItem(int itemId);

    Map<String, Integer> getEquipStats(int itemId);

    Equip getEquipById(int itemId);
}

