package server.agents.plans;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Transient attachment between one universal plan step and its TownLife session. */
public final class AgentTownLifeVisitStepState {
    public static final AgentCapabilityStateKey<AgentTownLifeVisitStepState> STATE_KEY =
            new AgentCapabilityStateKey<>("plan.town-life-visit",
                    AgentTownLifeVisitStepState.class, AgentTownLifeVisitStepState::new);

    private String attachmentKey = "";
    private String requestId = "";
    private String sessionId = "";

    synchronized void attach(String nextAttachmentKey, String nextRequestId, String nextSessionId) {
        attachmentKey = normalize(nextAttachmentKey);
        requestId = normalize(nextRequestId);
        sessionId = normalize(nextSessionId);
    }

    synchronized boolean matches(String expectedAttachmentKey) {
        return !attachmentKey.isBlank() && attachmentKey.equals(expectedAttachmentKey);
    }

    synchronized String requestId() {
        return requestId;
    }

    synchronized String sessionId() {
        return sessionId;
    }

    synchronized void clear() {
        attachmentKey = "";
        requestId = "";
        sessionId = "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
