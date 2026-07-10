package server.agents.capabilities.movement;

import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentMapEnvironmentService {
    private AgentMapEnvironmentService() {
    }

    public static boolean isSwimMap(AgentRuntimeEntry entry) {
        return isSwimMap(entry, AgentMapGatewayRuntime.map());
    }

    public static boolean isSwimMap(AgentRuntimeEntry entry, MapGateway maps) {
        return maps.isSwimMap(AgentRuntimeIdentityRuntime.bot(entry));
    }
}
