package server.agents.runtime.scheduler;

public record AgentAdmissionDecision(
        boolean allowed,
        AgentLoadSheddingReason reason,
        String message) {
    public AgentAdmissionDecision {
        if (allowed && reason != null) {
            throw new IllegalArgumentException("Allowed Agent admission cannot have a rejection reason");
        }
        if (!allowed && (reason == null || message == null || message.isBlank())) {
            throw new IllegalArgumentException("Rejected Agent admission requires a reason and message");
        }
    }

    public static AgentAdmissionDecision allow() {
        return new AgentAdmissionDecision(true, null, "");
    }

    public static AgentAdmissionDecision reject(AgentLoadSheddingReason reason, String message) {
        return new AgentAdmissionDecision(false, reason, message);
    }
}
