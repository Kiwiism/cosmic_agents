package server.agents.runtime;

import client.Character;
import client.inventory.InventoryType;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.plans.AgentTask;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public final class AgentScriptTaskStartService {
    @FunctionalInterface
    public interface DropItemAction {
        boolean drop(InventoryType type, int itemId, short quantity);
    }

    public record StartHooks(BiConsumer<Point, Boolean> startMoveTo,
                             Consumer<Character> startFollow,
                             IntFunction<Character> resolveFollowTarget,
                             Runnable startGrind,
                             Runnable startStop,
                             DropItemAction dropItem) {
    }

    private AgentScriptTaskStartService() {
    }

    public static void start(BotEntry entry, AgentTask task, StartHooks hooks) {
        switch (task.type()) {
            case MOVE_TO -> hooks.startMoveTo().accept(task.point(), task.precise());
            case FOLLOW_OWNER -> hooks.startFollow().accept(AgentBotRuntimeIdentityRuntime.owner(entry));
            case FOLLOW_TARGET -> hooks.startFollow().accept(hooks.resolveFollowTarget().apply(task.targetCharacterId()));
            case FOLLOW_UNTIL_NEAR -> hooks.startFollow().accept(hooks.resolveFollowTarget().apply(task.targetCharacterId()));
            case GRIND -> hooks.startGrind().run();
            case STOP -> hooks.startStop().run();
            case DROP_ITEM -> hooks.dropItem().drop(task.inventoryType(), task.itemId(), task.quantity());
        }
    }
}
