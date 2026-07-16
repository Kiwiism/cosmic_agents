package client.creator;

import client.Character;
import client.Client;
import client.Job;
import client.SkinColor;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;

/**
 * Creates bot/companion characters server-side using the same Character.getDefault +
 * insertNewChar pipeline as normal character creation, but without the client-packet
 * validation (MakeCharInfoValidator) which is irrelevant for server-initiated creation.
 */
public class BotCreator extends CharacterFactory {
    private static final Logger log = LoggerFactory.getLogger(BotCreator.class);

    public static int createCharacter(Client c, String name) {
        return createCharacter(c, name, 20100, 30020, 0, 0,
                1040002, 1060002, 1072001, 1302000);
    }

    public static int createCharacter(Client c,
                                      String name,
                                      int face,
                                      int hair,
                                      int skin,
                                      int gender,
                                      int topId,
                                      int bottomId,
                                      int shoesId,
                                      int weaponId) {
        if (!Character.canCreateChar(name)) {
            log.warn("Bot creation rejected — invalid name '{}'", name);
            return -1;
        }

        Character botChar = Character.getDefault(c);
        botChar.setWorld(c.getWorld());
        botChar.setSkinColor(SkinColor.getById(skin));
        botChar.setGender(gender);
        botChar.setName(name);
        botChar.setHair(hair);
        botChar.setFace(face);
        botChar.setJob(Job.BEGINNER);
        botChar.setLevel(1);
        botChar.setMapId(MapId.HENESYS);

        // Equip standard beginner starting gear (mirrors CharacterFactory.createNewCharacter)
        Inventory equipped = botChar.getInventory(InventoryType.EQUIPPED);
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        Item top = ii.getEquipById(topId);
        top.setPosition((byte) -5);
        equipped.addItemFromDB(top);

        Item bottom = ii.getEquipById(bottomId);
        bottom.setPosition((byte) -6);
        equipped.addItemFromDB(bottom);

        Item shoes = ii.getEquipById(shoesId);
        shoes.setPosition((byte) -7);
        equipped.addItemFromDB(shoes);

        Item weapon = ii.getEquipById(weaponId);
        weapon.setPosition((byte) -11);
        equipped.addItemFromDB(weapon.copy());

        CharacterFactoryRecipe recipe = new CharacterFactoryRecipe(
                Job.BEGINNER, 1, MapId.HENESYS, topId, bottomId, shoesId, weaponId);

        if (!botChar.insertNewChar(recipe)) {
            log.error("insertNewChar failed for bot '{}'", name);
            return -1;
        }

        log.info("Bot character '{}' created for account id {}", name, c.getAccID());
        return botChar.getId();
    }
}
