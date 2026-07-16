package server.agents.integration.cosmic;

import client.Character;
import client.SkinColor;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.ItemInformationProvider;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterTemplate;

import java.util.function.IntFunction;

/** Reapplies a pooled character's immutable creation identity before a clean run reset. */
public final class CosmicMapleIslandCohortIdentity {
    public static final int DEFAULT_STARTER_SWORD_ID = 1302000;

    private CosmicMapleIslandCohortIdentity() {
    }

    public static void apply(Character character, MapleIslandCohortCharacterTemplate template) {
        SkinColor skinColor = SkinColor.getById(template.skin());
        if (skinColor == null) {
            throw new IllegalArgumentException("Unknown cohort skin " + template.skin());
        }
        character.setGender(template.gender());
        character.setSkinColor(skinColor);
        character.setFace(template.face());
        character.setHair(template.hair());

        Inventory current = character.getInventory(InventoryType.EQUIPPED);
        Inventory equipped = new Inventory(character, InventoryType.EQUIPPED, current.getSlotLimit());
        addEquip(equipped, template.top(), (byte) -5);
        addEquip(equipped, template.bottom(), (byte) -6);
        addEquip(equipped, template.shoes(), (byte) -7);
        addEquip(equipped, template.weapon(), (byte) -11);
        character.setInventory(InventoryType.EQUIPPED, equipped);
        character.recalcLocalStats();
    }

    /** Gives a named showcase Agent the canonical Sword while retaining its beginner clothing. */
    public static void applyDefaultStarterWeapon(Character character) {
        applyDefaultStarterWeapon(character, ItemInformationProvider.getInstance()::getEquipById);
    }

    static void applyDefaultStarterWeapon(Character character, IntFunction<Item> itemFactory) {
        Inventory current = character.getInventory(InventoryType.EQUIPPED);
        Inventory equipped = new Inventory(character, InventoryType.EQUIPPED, current.getSlotLimit());
        for (Item item : current.list()) {
            if (item.getPosition() != -11) {
                equipped.addItemFromDB(item.copy());
            }
        }
        Item weapon = itemFactory.apply(DEFAULT_STARTER_SWORD_ID);
        if (weapon == null) {
            throw new IllegalStateException("Missing showcase equipment " + DEFAULT_STARTER_SWORD_ID);
        }
        weapon.setPosition((byte) -11);
        equipped.addItemFromDB(weapon);
        character.setInventory(InventoryType.EQUIPPED, equipped);
        character.recalcLocalStats();
    }

    private static void addEquip(Inventory equipped, int itemId, byte position) {
        Item item = ItemInformationProvider.getInstance().getEquipById(itemId);
        if (item == null) {
            throw new IllegalStateException("Missing cohort equipment " + itemId);
        }
        item.setPosition(position);
        equipped.addItemFromDB(item);
    }
}
