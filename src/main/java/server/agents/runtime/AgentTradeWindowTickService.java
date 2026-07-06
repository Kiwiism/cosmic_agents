package server.agents.runtime;

import client.Character;

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
        if (agent.getTrade() == null) {
            return false;
        }

        physicsOnlyTick.tick(entry, agent);
        return true;
    }
}
