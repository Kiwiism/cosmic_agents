package server.agents.runtime.activity.control.binding;

import server.agents.runtime.activity.session.AgentActivityOutcomePort;
import server.agents.runtime.activity.session.AgentActivityPreflightPort;
import server.agents.runtime.activity.session.AgentActivityRollbackPort;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.AgentActivityTargetPort;
import server.agents.runtime.activity.session.AgentActivityTransferPort;

/** Ports needed to execute one handoff without exposing child-system internals. */
public record AgentWorldActivityBinding(
        AgentActivitySourcePort source,
        AgentActivityPreflightPort targetPreflight,
        AgentActivityTransferPort transfer,
        AgentActivityTargetPort target,
        AgentActivityRollbackPort rollback,
        AgentActivityOutcomePort outcome) {

    public AgentWorldActivityBinding {
        if (source == null || targetPreflight == null || transfer == null || target == null
                || rollback == null || outcome == null) {
            throw new IllegalArgumentException("all World Director activity ports are required");
        }
    }
}
