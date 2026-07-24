package server.agents.plans;

import server.agents.runtime.state.AgentCapabilityStateKey;

public final class AgentPlanAttachmentState {
    public static final AgentCapabilityStateKey<AgentPlanAttachmentState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.plan-attachment", AgentPlanAttachmentState.class,
                    AgentPlanAttachmentState::new);

    private String objectiveId = "";
    private boolean attached;
    private long nextRetryAtMs;

    public synchronized boolean ready(String candidateObjectiveId, long nowMs) {
        return !candidateObjectiveId.equals(objectiveId)
                || (!attached && nowMs >= nextRetryAtMs);
    }

    public synchronized void attached(String candidateObjectiveId) {
        objectiveId = candidateObjectiveId;
        attached = true;
        nextRetryAtMs = 0L;
    }

    public synchronized void failed(String candidateObjectiveId, long retryAtMs) {
        objectiveId = candidateObjectiveId;
        attached = false;
        nextRetryAtMs = retryAtMs;
    }

}
