package server.agents.plans;

import java.util.ArrayDeque;

/**
 * Mutable queued task state for scripted Agent plans while Agent runtime reconstruction is being finalized.
 */
public final class AgentScriptTaskQueueState {
    private final ArrayDeque<AgentTask> queuedTasks = new ArrayDeque<>();
    private AgentTask activeTask = null;
    private int activityEpoch = 0;

    public int activityEpoch() {
        return activityEpoch;
    }

    public int bumpActivityEpoch() {
        return ++activityEpoch;
    }

    public void addTask(AgentTask task) {
        queuedTasks.add(task);
    }

    public AgentTask activeTask() {
        return activeTask;
    }

    public void setActiveTask(AgentTask activeTask) {
        this.activeTask = activeTask;
    }

    public AgentTask pollTask() {
        return queuedTasks.poll();
    }

    public boolean hasTasks() {
        return activeTask != null || !queuedTasks.isEmpty();
    }

    public void clearTasks() {
        queuedTasks.clear();
        activeTask = null;
    }
}
