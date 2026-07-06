package server.agents.runtime;

import client.Character;
import client.inventory.InventoryType;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.plans.AgentTask;

import java.awt.Point;

public final class AgentScriptTaskQueueService {
    private AgentScriptTaskQueueService() {
    }

    public static void clearTasks(AgentRuntimeEntry entry) {
        if (entry == null) {
            return;
        }
        AgentBotScriptTaskStateRuntime.clearTasksAndBumpEpoch(entry);
    }

    public static void queueTask(AgentRuntimeEntry entry, AgentTask task) {
        if (entry == null || task == null) {
            return;
        }
        AgentBotScriptTaskStateRuntime.queueTask(entry, task);
    }

    public static void queueMoveTo(AgentRuntimeEntry entry, Point point, boolean precise) {
        queueTask(entry, AgentTask.moveTo(point, precise));
    }

    public static void queueMoveTo(AgentRuntimeEntry entry, Point point, boolean precise, AgentTask.MoveCombatMode moveCombatMode) {
        queueTask(entry, AgentTask.moveTo(point, precise, moveCombatMode));
    }

    public static void queueMoveThenDropItem(AgentRuntimeEntry entry,
                                             Point point,
                                             boolean precise,
                                             InventoryType type,
                                             int itemId,
                                             short quantity) {
        queueTask(entry, AgentTask.moveTo(point, precise));
        queueTask(entry, AgentTask.dropItem(type, itemId, quantity));
    }

    public static void queueFollowThenDropItem(AgentRuntimeEntry entry,
                                               Character target,
                                               int nearPx,
                                               InventoryType type,
                                               int itemId,
                                               short quantity) {
        queueTask(entry, AgentTask.followUntilNear(target, nearPx));
        queueTask(entry, AgentTask.dropItem(type, itemId, quantity));
    }

    public static boolean hasQueuedTasks(AgentRuntimeEntry entry) {
        return AgentBotScriptTaskStateRuntime.hasQueuedTasks(entry);
    }
}
