package server.agents.integration;

import server.bots.BotEntry;
import server.agents.plans.AgentTask;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed scripted task queue state.
 */
public final class AgentBotScriptTaskStateRuntime {
    private AgentBotScriptTaskStateRuntime() {
    }

    public static int activityEpoch(BotEntry entry) {
        return entry.scriptTaskQueueState().activityEpoch();
    }

    public static boolean isCurrentActivityEpoch(BotEntry entry, int epoch) {
        return activityEpoch(entry) == epoch;
    }

    public static void clearTasksAndBumpEpoch(BotEntry entry) {
        entry.scriptTaskQueueState().bumpActivityEpoch();
        entry.scriptTaskQueueState().clearTasks();
    }

    public static void queueTask(BotEntry entry, AgentTask task) {
        entry.scriptTaskQueueState().addTask(task);
    }

    public static boolean hasQueuedTasks(BotEntry entry) {
        return entry != null && entry.scriptTaskQueueState().hasTasks();
    }

    public static AgentTask activeTask(BotEntry entry) {
        return entry.scriptTaskQueueState().activeTask();
    }

    public static boolean hasActiveTask(BotEntry entry) {
        return activeTask(entry) != null;
    }

    public static AgentTask activateNextTask(BotEntry entry) {
        AgentTask activeTask = entry.scriptTaskQueueState().activeTask();
        if (activeTask != null) {
            return activeTask;
        }
        activeTask = entry.scriptTaskQueueState().pollTask();
        entry.scriptTaskQueueState().setActiveTask(activeTask);
        return activeTask;
    }

    public static void clearActiveTask(BotEntry entry) {
        entry.scriptTaskQueueState().setActiveTask(null);
    }

    public static boolean isActiveLocalOpportunityMoveTo(BotEntry entry, Point targetPos) {
        if (entry == null || targetPos == null) {
            return false;
        }
        AgentTask activeTask = activeTask(entry);
        if (activeTask == null) {
            return false;
        }
        if (activeTask.type() != AgentTask.Type.MOVE_TO
                || activeTask.moveCombatMode() != AgentTask.MoveCombatMode.LOCAL_OPPORTUNITY) {
            return false;
        }
        if (!AgentBotMoveTargetStateRuntime.moveTargetEquals(entry, activeTask.point())) {
            return false;
        }
        return !AgentBotModeStateRuntime.following(entry);
    }

    public static String scriptId(BotEntry entry) {
        return entry.script.scriptId;
    }

    public static boolean hasScriptId(BotEntry entry) {
        return scriptId(entry) != null;
    }

    public static void resetScript(BotEntry entry, String scriptId) {
        entry.script.reset(scriptId);
    }

    public static int scriptStepIndex(BotEntry entry) {
        return entry.script.stepIndex;
    }

    public static boolean scriptStepEntered(BotEntry entry) {
        return entry.script.stepEntered;
    }

    public static void markScriptStepEntered(BotEntry entry) {
        entry.script.stepEntered = true;
    }

    public static void advanceScriptStep(BotEntry entry) {
        entry.script.stepIndex++;
        entry.script.stepEntered = false;
    }

    public static int scriptInt(BotEntry entry, String key) {
        return entry.script.ints.getOrDefault(key, 0);
    }

    public static void setScriptInt(BotEntry entry, String key, int value) {
        entry.script.ints.put(key, value);
    }

    public static void waitScriptUntil(BotEntry entry, long untilMs) {
        entry.script.waitUntilMs = untilMs;
    }

    public static boolean scriptWaitDone(BotEntry entry, long nowMs) {
        return nowMs >= entry.script.waitUntilMs;
    }
}
