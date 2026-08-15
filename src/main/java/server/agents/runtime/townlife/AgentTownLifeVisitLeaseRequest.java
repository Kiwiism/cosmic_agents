package server.agents.runtime.townlife;

import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;

/** Externally-authored schedule for one TownLife visit. */
public record AgentTownLifeVisitLeaseRequest(
        AgentTownLifeEntryRequest entryRequest,
        AgentTownLifeAdmissionMode admissionMode,
        long exitAtMs,
        long gracefulTimeoutMs,
        String exitReason) {

    public AgentTownLifeVisitLeaseRequest {
        admissionMode = admissionMode == null
                ? AgentTownLifeAdmissionMode.MANUAL_ONLY : admissionMode;
        exitReason = exitReason == null ? "scheduled TownLife departure" : exitReason.trim();
        if (entryRequest == null || exitAtMs <= 0L || gracefulTimeoutMs <= 0L) {
            throw new IllegalArgumentException("valid scheduled TownLife visit is required");
        }
    }
}
