package server.agents.integration;

import client.inventory.Equip;
import client.Character;
import client.Job;
import client.inventory.WeaponType;
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

    String getEquipmentSlot(int itemId);

    int getEquipLevelRequirement(int itemId);

    WeaponType getWeaponType(int itemId);

    boolean isTwoHandedWeapon(int itemId);

    int getMakerCrystalFromLeftover(int leftoverId);

    boolean meetsEquipRequirements(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame);
}

