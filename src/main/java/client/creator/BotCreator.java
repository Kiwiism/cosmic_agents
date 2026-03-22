package client.creator;

import client.Character;
import client.Client;
import client.Job;
import client.SkinColor;
import constants.id.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates bot/companion characters server-side using the same Character.getDefault +
 * insertNewChar pipeline as normal character creation, but without the client-packet
 * validation (MakeCharInfoValidator) which is irrelevant for server-initiated creation.
 */
public class BotCreator extends CharacterFactory {
    private static final Logger log = LoggerFactory.getLogger(BotCreator.class);

    public static int createCharacter(Client c, String name) {
        if (!Character.canCreateChar(name)) {
            log.warn("Bot creation rejected — invalid name '{}'", name);
            return -1;
        }

        Character botChar = Character.getDefault(c);
        botChar.setWorld(c.getWorld());
        botChar.setSkinColor(SkinColor.getById(0));
        botChar.setGender(0);
        botChar.setName(name);
        botChar.setHair(30030);
        botChar.setFace(20100);
        botChar.setJob(Job.BEGINNER);
        botChar.setLevel(1);
        botChar.setMapId(MapId.HENESYS);

        CharacterFactoryRecipe recipe = new CharacterFactoryRecipe(Job.BEGINNER, 1, MapId.HENESYS, 0, 0, 0, 0);

        if (!botChar.insertNewChar(recipe)) {
            log.error("insertNewChar failed for bot '{}'", name);
            return -1;
        }

        log.info("Bot character '{}' created for account id {}", name, c.getAccID());
        return botChar.getId();
    }
}