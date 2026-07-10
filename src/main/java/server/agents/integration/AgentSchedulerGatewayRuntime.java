package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentSchedulerGatewayRuntime {
    private AgentSchedulerGatewayRuntime() {
    }

    public static SchedulerGateway scheduler() {
        return CosmicAgentServerAdapter.INSTANCE.scheduler();
    }
}
