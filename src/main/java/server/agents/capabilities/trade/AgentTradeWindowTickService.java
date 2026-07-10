package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentTradeGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeWindowTickService {
    private AgentTradeWindowTickService() {
    }

    @FunctionalInterface
    public interface PhysicsOnlyTick {
        void tick(AgentRuntimeEntry entry, Character agent);
    }

    public static boolean tickIfTradeWindowOpen(AgentRuntimeEntry entry,
                                                Character agent,
                                                PhysicsOnlyTick physicsOnlyTick) {
        if (AgentTradeGatewayRuntime.trade().currentWindow(agent) == null) {
            return false;
        }

        physicsOnlyTick.tick(entry, agent);
        return true;
    }
}
