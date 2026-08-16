package server.agents.plans;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Transient attachment between one universal-plan step and its field session. */
public final class AgentFieldVisitStepState {
    public static final AgentCapabilityStateKey<AgentFieldVisitStepState> STATE_KEY =
            new AgentCapabilityStateKey<>("plan.field-visit",
                    AgentFieldVisitStepState.class, AgentFieldVisitStepState::new);

    private String attachmentKey = "";
    private String requestId = "";
    private String sessionId = "";

    synchronized void attach(String nextAttachmentKey, String nextRequestId, String nextSessionId) {
        attachmentKey = normalize(nextAttachmentKey);
        requestId = normalize(nextRequestId);
        sessionId = normalize(nextSessionId);
    }

    synchronized boolean matches(String expected) {
        return !attachmentKey.isBlank() && attachmentKey.equals(expected);
    }

    synchronized String sessionId() { return sessionId; }

    synchronized void clear() {
        attachmentKey = "";
        requestId = "";
        sessionId = "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
