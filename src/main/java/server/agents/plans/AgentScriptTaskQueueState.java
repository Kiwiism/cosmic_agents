package server.agents.plans;

import server.agents.monitoring.AgentAsyncQueueMetrics;
import server.agents.runtime.AgentBoundedExecutorFactory;

import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;

/**
 * Mutable queued task state for scripted Agent plans while Agent runtime reconstruction is being finalized.
 */
public final class AgentScriptTaskQueueState {
    private final ArrayDeque<AgentTask> queuedTasks = new ArrayDeque<>();
    private final int capacity;
    private AgentTask activeTask = null;
    private int activityEpoch = 0;

    public AgentScriptTaskQueueState() {
        this(AgentBoundedExecutorFactory.positiveIntegerProperty(
                "agents.async.scriptTasks.queueCapacity", 256));
    }

    AgentScriptTaskQueueState(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized int activityEpoch() {
        return activityEpoch;
    }

    public synchronized int bumpActivityEpoch() {
        return ++activityEpoch;
    }

    public synchronized void addTask(AgentTask task) {
        if (queuedTasks.size() >= capacity) {
            AgentAsyncQueueMetrics.recordRejected("script-tasks", queuedTasks.size());
            throw new RejectedExecutionException("Agent script task queue is full");
        }
        queuedTasks.addLast(task);
        AgentAsyncQueueMetrics.recordSubmitted("script-tasks", queuedTasks.size());
    }

    public synchronized AgentTask activeTask() {
        return activeTask;
    }

    public synchronized void setActiveTask(AgentTask activeTask) {
        this.activeTask = activeTask;
    }

    public synchronized AgentTask pollTask() {
        AgentTask task = queuedTasks.pollFirst();
        AgentAsyncQueueMetrics.recordDepth("script-tasks", queuedTasks.size());
        return task;
    }

    public synchronized boolean hasTasks() {
        return activeTask != null || !queuedTasks.isEmpty();
    }

    public synchronized void clearTasks() {
        queuedTasks.clear();
        activeTask = null;
        AgentAsyncQueueMetrics.recordDepth("script-tasks", 0);
    }

    synchronized int queuedTaskCount() {
        return queuedTasks.size();
    }
}
