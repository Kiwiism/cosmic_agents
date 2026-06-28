package server.agents.integration;

import server.bots.BotEntry;
import server.bots.BotTask;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed scripted task queue state.
 */
public final class AgentBotScriptTaskStateRuntime {
    private AgentBotScriptTaskStateRuntime() {
    }

    public static int activityEpoch(BotEntry entry) {
        return entry.activityEpoch();
    }

    public static boolean isCurrentActivityEpoch(BotEntry entry, int epoch) {
        return activityEpoch(entry) == epoch;
    }

    public static void clearTasksAndBumpEpoch(BotEntry entry) {
        entry.bumpActivityEpoch();
        entry.clearScriptTasks();
    }

    public static void queueTask(BotEntry entry, BotTask task) {
        entry.addScriptTask(task);
    }

    public static boolean hasQueuedTasks(BotEntry entry) {
        return entry != null && entry.hasScriptTasks();
    }

    public static BotTask activeTask(BotEntry entry) {
        return entry.activeScriptTask();
    }

    public static boolean hasActiveTask(BotEntry entry) {
        return activeTask(entry) != null;
    }

    public static BotTask activateNextTask(BotEntry entry) {
        BotTask activeTask = entry.activeScriptTask();
        if (activeTask != null) {
            return activeTask;
        }
        activeTask = entry.pollScriptTask();
        entry.setActiveScriptTask(activeTask);
        return activeTask;
    }

    public static void clearActiveTask(BotEntry entry) {
        entry.setActiveScriptTask(null);
    }

    public static boolean isActiveLocalOpportunityMoveTo(BotEntry entry, Point targetPos) {
        if (entry == null || targetPos == null) {
            return false;
        }
        BotTask activeTask = activeTask(entry);
        if (activeTask == null) {
            return false;
        }
        if (activeTask.type() != BotTask.Type.MOVE_TO
                || activeTask.moveCombatMode() != BotTask.MoveCombatMode.LOCAL_OPPORTUNITY) {
            return false;
        }
        if (!AgentBotMoveTargetStateRuntime.moveTargetEquals(entry, activeTask.point())) {
            return false;
        }
        return !AgentBotModeStateRuntime.following(entry);
    }
}
