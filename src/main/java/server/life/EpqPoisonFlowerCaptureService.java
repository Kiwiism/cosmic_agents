package server.life;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.manipulator.InventoryManipulator;
import constants.id.ItemId;
import constants.id.MobId;
import tools.PacketCreator;

/** Authoritative EPQ Poison Flower catch transaction shared by humans and Agents. */
public final class EpqPoisonFlowerCaptureService {
    private EpqPoisonFlowerCaptureService() { }

    public static boolean ready(Monster monster) {
        return monster != null && monster.isAlive() && monster.getId() == MobId.POISON_FLOWER
                && monster.getHp() < (monster.getMaxHp() / 10L) * 4L;
    }

    public static Result capture(Character character, Monster monster) {
        if (character == null || monster == null || character.getMap() == null
                || character.getMap().getMonsterByOid(monster.getObjectId()) != monster
                || monster.getId() != MobId.POISON_FLOWER) return Result.INVALID_TARGET;
        if (!ready(monster)) return Result.NOT_READY;
        if (character.getInventory(InventoryType.USE)
                .countById(ItemId.EPQ_PURIFICATION_MARBLE) < 1) return Result.NO_MARBLE;
        if (!character.canHold(ItemId.EPQ_MONSTER_MARBLE, 1)) return Result.NO_INVENTORY_SPACE;
        character.getMap().broadcastMessage(PacketCreator.catchMonster(
                monster.getObjectId(), ItemId.EPQ_PURIFICATION_MARBLE, (byte) 1));
        character.getMap().killMonster(monster, null, false, (short) 0);
        InventoryManipulator.removeById(character.getClient(), InventoryType.USE,
                ItemId.EPQ_PURIFICATION_MARBLE, 1, true, true);
        InventoryManipulator.addById(character.getClient(), ItemId.EPQ_MONSTER_MARBLE,
                (short) 1, "", -1);
        return Result.CAPTURED;
    }

    public enum Result { CAPTURED, NOT_READY, NO_MARBLE, NO_INVENTORY_SPACE, INVALID_TARGET }
}
