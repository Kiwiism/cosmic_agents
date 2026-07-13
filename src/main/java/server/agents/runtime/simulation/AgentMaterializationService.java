package server.agents.runtime.simulation;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

@FunctionalInterface
public interface AgentMaterializationService {
    boolean materialize(AgentRuntimeEntry entry);

    static AgentMaterializationService validating() {
        return entry -> AgentRuntimeIdentityRuntime.bot(entry) != null
                && AgentRuntimeIdentityRuntime.botMap(entry) != null
                && AgentRuntimeIdentityRuntime.botPosition(entry) != null;
    }
}
