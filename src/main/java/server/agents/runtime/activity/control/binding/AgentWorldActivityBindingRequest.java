package server.agents.runtime.activity.control.binding;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldDirective;

import java.util.Map;

/** Complete context supplied to one system-specific binding provider. */
public record AgentWorldActivityBindingRequest(
        AgentWorldDirective directive,
        AgentRuntimeEntry entry,
        Character agent,
        AgentActivityKind sourceActivityKind,
        String sourceSessionId,
        Map<String, Object> attributes) {

    public AgentWorldActivityBindingRequest {
        sourceSessionId = sourceSessionId == null ? "" : sourceSessionId.trim();
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        if (directive == null || entry == null || agent == null
                || directive.targetActivityKind() == null) {
            throw new IllegalArgumentException("a bound activity directive and Agent are required");
        }
        if (agent.getId() != directive.agentId()) {
            throw new IllegalArgumentException("directive Agent does not match the bound Agent");
        }
    }

    public AgentActivityKind targetActivityKind() {
        return directive.targetActivityKind();
    }
}
