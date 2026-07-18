package server.agents.plans.amherst;

import server.agents.capabilities.objective.AgentObjectiveProgressWatchdog;

public final class AmherstPlanExecutionState {
    AmherstPlanRuntimeRunner runner;
    AmherstPlanProgressSnapshot progress;
    String assignedObjectiveId;
    int objectiveStartLevel;
    int objectiveStartExp;
    int syncedCapabilityJournalCount;
    final AgentObjectiveProgressWatchdog.State objectiveWatchdog =
            new AgentObjectiveProgressWatchdog.State();
    boolean active;
    boolean loading;
    boolean completed;
    AmherstPlanExecutionMode mode = AmherstPlanExecutionMode.AUTO;
    boolean advanceRequested;
    boolean waitingForAdvance;
    long nextObjectiveAtMs;
    AmherstPlanObserver observer = AmherstPlanObserver.NONE;
    String lastError = "";

    public synchronized boolean active() {
        return active;
    }

    public synchronized boolean completed() {
        return completed;
    }

    public synchronized String assignedObjectiveId() {
        return assignedObjectiveId;
    }

    public synchronized AmherstPlanProgressSnapshot progress() {
        return progress;
    }

    public synchronized String lastError() {
        return lastError;
    }

    public synchronized AmherstPlanExecutionMode mode() {
        return mode;
    }

    public synchronized boolean waitingForAdvance() {
        return waitingForAdvance;
    }

    synchronized void clearRuntime() {
        runner = null;
        progress = null;
        assignedObjectiveId = null;
        objectiveStartLevel = 0;
        objectiveStartExp = 0;
        syncedCapabilityJournalCount = 0;
        objectiveWatchdog.reset();
        active = false;
        loading = false;
        completed = false;
        mode = AmherstPlanExecutionMode.AUTO;
        advanceRequested = false;
        waitingForAdvance = false;
        nextObjectiveAtMs = 0L;
        observer = AmherstPlanObserver.NONE;
        lastError = "";
    }
}
