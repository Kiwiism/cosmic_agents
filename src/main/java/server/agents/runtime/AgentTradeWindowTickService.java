package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;

public final class AgentTradeWindowTickService {
    private AgentTradeWindowTickService() {
    }

    @FunctionalInterface
    public interface PhysicsOnlyTick {
        void tick(BotEntry entry, Character agent);
    }

    public static boolean tickIfTradeWindowOpen(BotEntry entry,
                                                Character agent,
                                                PhysicsOnlyTick physicsOnlyTick) {
        if (agent.getTrade() == null) {
            return false;
        }

        physicsOnlyTick.tick(entry, agent);
        return true;
    }
}
