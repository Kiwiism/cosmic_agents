package server.agents.integration.cosmic;

import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.runtime.activity.control.AgentWorldDirectorAgentDirectory;
import server.agents.runtime.activity.control.AgentWorldDirectorApplication;
import server.agents.runtime.activity.control.AgentWorldDirectorExecutive;

/** Cosmic composition root for the transport-neutral Director application. */
public final class CosmicAgentWorldDirectorApplicationFactory {
    private CosmicAgentWorldDirectorApplicationFactory() { }

    public static AgentWorldDirectorApplication create() {
        return new AgentWorldDirectorApplication(
                new AgentWorldDirectorAgentDirectory(
                        AgentPersistenceGatewayRuntime.persistence()),
                new CosmicAgentDirectorSessionService(),
                AgentWorldDirectorExecutive.runtimeDefault());
    }
}
