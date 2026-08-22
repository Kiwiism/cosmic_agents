package server.agents.runtime.activity.control.binding;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldDirective;

@FunctionalInterface
public interface AgentWorldActivityBindingResolver {
    AgentWorldActivityBinding bind(
            AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId);
}
