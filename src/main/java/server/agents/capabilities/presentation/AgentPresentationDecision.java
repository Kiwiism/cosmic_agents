package server.agents.capabilities.presentation;

public record AgentPresentationDecision(
        AgentPresentationIntent intent,
        AgentPresentationTrigger trigger,
        long notBeforeMs,
        int durationMs) {
    public AgentPresentationDecision {
        if (intent == null || trigger == null || notBeforeMs < 0 || durationMs < 200) {
            throw new IllegalArgumentException("valid presentation intent, trigger, timing, and duration are required");
        }
    }
}
