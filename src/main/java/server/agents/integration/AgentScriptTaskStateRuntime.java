package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.plans.AgentTask;
import server.agents.plans.AgentScriptRuntimeState;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed scripted task queue state.
 */
public final class AgentScriptTaskStateRuntime {
    private AgentScriptTaskStateRuntime() {
    }

    public static int activityEpoch(AgentRuntimeEntry entry) {
        return entry.scriptTaskQueueState().activityEpoch();
    }

    public static boolean isCurrentActivityEpoch(AgentRuntimeEntry entry, int epoch) {
        return activityEpoch(entry) == epoch;
    }

    public static void clearTasksAndBumpEpoch(AgentRuntimeEntry entry) {
        entry.scriptTaskQueueState().bumpActivityEpoch();
        entry.scriptTaskQueueState().clearTasks();
    }

    public static void queueTask(AgentRuntimeEntry entry, AgentTask task) {
        entry.scriptTaskQueueState().addTask(task);
    }

    public static boolean hasQueuedTasks(AgentRuntimeEntry entry) {
        return entry != null && entry.scriptTaskQueueState().hasTasks();
    }

    public static AgentTask activeTask(AgentRuntimeEntry entry) {
        return entry.scriptTaskQueueState().activeTask();
    }

    public static boolean hasActiveTask(AgentRuntimeEntry entry) {
        return activeTask(entry) != null;
    }

    public static AgentTask activateNextTask(AgentRuntimeEntry entry) {
        AgentTask activeTask = entry.scriptTaskQueueState().activeTask();
        if (activeTask != null) {
            return activeTask;
        }
        activeTask = entry.scriptTaskQueueState().pollTask();
        entry.scriptTaskQueueState().setActiveTask(activeTask);
        return activeTask;
    }

    public static void clearActiveTask(AgentRuntimeEntry entry) {
        entry.scriptTaskQueueState().setActiveTask(null);
    }

    public static boolean isActiveLocalOpportunityMoveTo(AgentRuntimeEntry entry, Point targetPos) {
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
        if (!AgentMoveTargetStateRuntime.moveTargetEquals(entry, activeTask.point())) {
            return false;
        }
        return !AgentModeStateRuntime.following(entry);
    }

    public static String scriptId(AgentRuntimeEntry entry) {
        return scriptState(entry).scriptId();
    }

    public static boolean hasScriptId(AgentRuntimeEntry entry) {
        return scriptId(entry) != null;
    }

    public static void resetScript(AgentRuntimeEntry entry, String scriptId) {
        scriptState(entry).reset(scriptId);
    }

    public static int scriptStepIndex(AgentRuntimeEntry entry) {
        return scriptState(entry).stepIndex();
    }

    public static boolean scriptStepEntered(AgentRuntimeEntry entry) {
        return scriptState(entry).stepEntered();
    }

    public static void markScriptStepEntered(AgentRuntimeEntry entry) {
        scriptState(entry).markStepEntered();
    }

    public static void advanceScriptStep(AgentRuntimeEntry entry) {
        scriptState(entry).advanceStep();
    }

    public static int scriptInt(AgentRuntimeEntry entry, String key) {
        return scriptState(entry).intValue(key);
    }

    public static void setScriptInt(AgentRuntimeEntry entry, String key, int value) {
        scriptState(entry).setIntValue(key, value);
    }

    public static void waitScriptUntil(AgentRuntimeEntry entry, long untilMs) {
        scriptState(entry).waitUntil(untilMs);
    }

    public static boolean scriptWaitDone(AgentRuntimeEntry entry, long nowMs) {
        return scriptState(entry).waitDone(nowMs);
    }

    private static AgentScriptRuntimeState scriptState(AgentRuntimeEntry entry) {
        return entry.scriptRuntimeState();
    }
}
