package server.agents.runtime.activity.control.binding;

import server.agents.runtime.activity.session.AgentActivityKind;

public interface AgentWorldActivityBindingProvider {
    AgentActivityKind targetKind();

    AgentWorldActivityBinding bind(AgentWorldActivityBindingRequest request);
}
