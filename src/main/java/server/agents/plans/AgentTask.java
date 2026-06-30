package server.agents.plans;

import client.Character;
import client.inventory.InventoryType;

import java.awt.*;

/**
 * Small script/task primitive for bot automation. Per-map scripts should build
 * behavior from these tasks instead of writing movement/combat/drop state
 * directly.
 */
public final class AgentTask {
    public enum MoveCombatMode {
        NONE,
        LOCAL_OPPORTUNITY
    }

    public enum Type {
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
    final MoveCombatMode moveCombatMode;

    private AgentTask(Type type,
                      Point point,
                      boolean precise,
                      int targetCharacterId,
                      int nearPx,
                      InventoryType inventoryType,
                      int itemId,
                      short quantity,
                      MoveCombatMode moveCombatMode) {
        this.type = type;
        this.point = point == null ? null : new Point(point);
        this.precise = precise;
        this.targetCharacterId = targetCharacterId;
        this.nearPx = nearPx;
        this.inventoryType = inventoryType;
        this.itemId = itemId;
        this.quantity = quantity;
        this.moveCombatMode = moveCombatMode == null ? MoveCombatMode.NONE : moveCombatMode;
    }

    public Type type() {
        return type;
    }

    public Point point() {
        return point == null ? null : new Point(point);
    }

    public MoveCombatMode moveCombatMode() {
        return moveCombatMode;
    }

    public boolean precise() {
        return precise;
    }

    public int targetCharacterId() {
        return targetCharacterId;
    }

    public int nearPx() {
        return nearPx;
    }

    public InventoryType inventoryType() {
        return inventoryType;
    }

    public int itemId() {
        return itemId;
    }

    public short quantity() {
        return quantity;
    }

    public static AgentTask moveTo(Point point, boolean precise) {
        return moveTo(point, precise, MoveCombatMode.NONE);
    }

    public static AgentTask moveTo(Point point, boolean precise, MoveCombatMode moveCombatMode) {
        return new AgentTask(Type.MOVE_TO, point, precise, 0, 0, null, 0, (short) 0, moveCombatMode);
    }

    public static AgentTask followOwner() {
        return new AgentTask(Type.FOLLOW_OWNER, null, false, 0, 0, null, 0, (short) 0, MoveCombatMode.NONE);
    }

    public static AgentTask follow(Character target) {
        return new AgentTask(Type.FOLLOW_TARGET, null, false, target != null ? target.getId() : 0, 0, null, 0, (short) 0, MoveCombatMode.NONE);
    }

    public static AgentTask followUntilNear(Character target, int nearPx) {
        return new AgentTask(Type.FOLLOW_UNTIL_NEAR, null, false, target != null ? target.getId() : 0, nearPx, null, 0, (short) 0, MoveCombatMode.NONE);
    }

    public static AgentTask grind() {
        return new AgentTask(Type.GRIND, null, false, 0, 0, null, 0, (short) 0, MoveCombatMode.NONE);
    }

    public static AgentTask stop() {
        return new AgentTask(Type.STOP, null, false, 0, 0, null, 0, (short) 0, MoveCombatMode.NONE);
    }

    public static AgentTask dropItem(InventoryType type, int itemId, short quantity) {
        return new AgentTask(Type.DROP_ITEM, null, false, 0, 0, type, itemId, quantity, MoveCombatMode.NONE);
    }
}
