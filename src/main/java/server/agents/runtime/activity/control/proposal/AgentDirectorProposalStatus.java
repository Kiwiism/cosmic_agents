package server.agents.runtime.activity.control.proposal;

public enum AgentDirectorProposalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    STALE,
    EXECUTED;

    public boolean terminal() {
        return this != PENDING;
    }
}
