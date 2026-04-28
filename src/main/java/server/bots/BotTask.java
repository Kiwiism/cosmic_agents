package server.bots;

import client.Character;
import client.inventory.InventoryType;

import java.awt.*;

/**
 * Small script/task primitive for bot automation. Per-map scripts should build
 * behavior from these tasks instead of writing movement/combat/drop state
 * directly.
 */
public final class BotTask {
    enum Type {
        MOVE_TO,
        FOLLOW_OWNER,
        FOLLOW_TARGET,
        FOLLOW_UNTIL_NEAR,
        GRIND,
        STOP,
        DROP_ITEM
    }

    final Type type;
    final Point point;
    final boolean precise;
    final int targetCharacterId;
    final int nearPx;
    final InventoryType inventoryType;
    final int itemId;
    final short quantity;

    private BotTask(Type type,
                    Point point,
                    boolean precise,
                    int targetCharacterId,
                    int nearPx,
                    InventoryType inventoryType,
                    int itemId,
                    short quantity) {
        this.type = type;
        this.point = point == null ? null : new Point(point);
        this.precise = precise;
        this.targetCharacterId = targetCharacterId;
        this.nearPx = nearPx;
        this.inventoryType = inventoryType;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public static BotTask moveTo(Point point, boolean precise) {
        return new BotTask(Type.MOVE_TO, point, precise, 0, 0, null, 0, (short) 0);
    }

    public static BotTask followOwner() {
        return new BotTask(Type.FOLLOW_OWNER, null, false, 0, 0, null, 0, (short) 0);
    }

    public static BotTask follow(Character target) {
        return new BotTask(Type.FOLLOW_TARGET, null, false, target != null ? target.getId() : 0, 0, null, 0, (short) 0);
    }

    public static BotTask followUntilNear(Character target, int nearPx) {
        return new BotTask(Type.FOLLOW_UNTIL_NEAR, null, false, target != null ? target.getId() : 0, nearPx, null, 0, (short) 0);
    }

    public static BotTask grind() {
        return new BotTask(Type.GRIND, null, false, 0, 0, null, 0, (short) 0);
    }

    public static BotTask stop() {
        return new BotTask(Type.STOP, null, false, 0, 0, null, 0, (short) 0);
    }

    public static BotTask dropItem(InventoryType type, int itemId, short quantity) {
        return new BotTask(Type.DROP_ITEM, null, false, 0, 0, type, itemId, quantity);
    }
}
