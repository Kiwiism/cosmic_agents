package server.agents.runtime.activity.control.facade;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityOutcomePort;
import server.agents.runtime.activity.session.AgentActivityRollbackPort;
import server.agents.runtime.activity.session.AgentActivitySourcePort;

/** Live lifecycle projection for one system, independent of its entry-request compiler. */
public record AgentLiveActivityFacade(
        AgentActivityKind kind,
        AgentActivitySourcePort source,
        AgentActivityOutcomePort outcome,
        AgentActivityRollbackPort rollback,
        boolean rollbackSupported,
        String readinessEvidence) {

    public AgentLiveActivityFacade {
        readinessEvidence = readinessEvidence == null ? "" : readinessEvidence.trim();
        if (kind == null || source == null || outcome == null || rollback == null
                || readinessEvidence.isEmpty()) {
            throw new IllegalArgumentException("complete live activity facade is required");
        }
    }
}
