package server.agents.runtime.activity.control.facade;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

public interface AgentLiveActivityFacadeProvider {
    AgentActivityKind kind();

    AgentLiveActivityFacade bind(AgentRuntimeEntry entry, Character agent);
}
