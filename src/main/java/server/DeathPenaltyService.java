package server;

import client.Character;
import client.Job;
import client.inventory.manipulator.InventoryManipulator;
import constants.game.ExpTable;
import constants.id.ItemId;
import constants.id.MapId;
import constants.inventory.ItemConstants;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import java.util.Objects;

/**
 * Authoritative v83 death-penalty rule shared by live characters and logical activity.
 * The caller remains responsible for buffs, event scripts, pose, and respawn handling.
 */
public final class DeathPenaltyService {
    private static final int[] SAFETY_CHARMS = {
            ItemId.SAFETY_CHARM, ItemId.EASTER_BASKET, ItemId.EASTER_CHARM
    };

    private DeathPenaltyService() {
    }

    public static Result apply(Character character, MapleMap map) {
        Objects.requireNonNull(map, "death map");
        return apply(character, new FieldContext(map.getId(), map.isTown(), map.getFieldLimit()));
    }

    public static Result apply(Character character, FieldContext field) {
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(field, "field");

        for (int charmId : SAFETY_CHARMS) {
            if (character.getItemQuantity(charmId, false) <= 0) continue;
            if (MapId.isDojo(field.mapId())) break;
            InventoryManipulator.removeById(character.getClient(), ItemConstants.getInventoryType(charmId),
                    charmId, 1, true, false);
            return new Result(0, charmId, true, Reason.SAFETY_CHARM);
        }
        if (character.getJob() == Job.BEGINNER) return Result.none(Reason.BEGINNER);
        if (FieldLimit.NO_EXP_DECREASE.check(field.fieldLimit())) return Result.none(Reason.FIELD_PROTECTED);

        int loss = ExpTable.getExpNeededForLevel(character.getLevel());
        if (field.town()) loss /= 100;
        else loss /= character.getLuk() < 50 ? 10 : 20;
        loss = Math.min(character.getExp(), loss);
        if (loss > 0) character.loseExp(loss, false, false);
        return new Result(loss, 0, false, loss == 0 ? Reason.NO_EXPERIENCE : Reason.EXPERIENCE_LOST);
    }

    public record FieldContext(int mapId, boolean town, int fieldLimit) {
        public FieldContext {
            if (mapId <= 0) throw new IllegalArgumentException("map id must be positive");
        }
    }

    public record Result(int experienceLost, int consumedCharmItemId, boolean prevented, Reason reason) {
        public Result {
            Objects.requireNonNull(reason, "reason");
            if (experienceLost < 0 || consumedCharmItemId < 0) throw new IllegalArgumentException();
        }

        private static Result none(Reason reason) {
            return new Result(0, 0, false, reason);
        }
    }

    public enum Reason {
        EXPERIENCE_LOST,
        SAFETY_CHARM,
        BEGINNER,
        FIELD_PROTECTED,
        NO_EXPERIENCE
    }
}
