package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentAnchoredFarmModeTickService {
    private AgentAnchoredFarmModeTickService() {
    }

    public record Hooks(AnchoredFarmTick anchoredFarmTick) {
    }

    @FunctionalInterface
    public interface AnchoredFarmTick {
        void tick(BotEntry entry, Character agent, Point agentPosition, boolean runAiTick);
    }

    public static boolean tickIfAnchoredFarm(BotEntry entry,
                                             Character agent,
                                             Point agentPosition,
                                             boolean runAiTick,
                                             Hooks hooks) {
        if (!AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return false;
        }
        hooks.anchoredFarmTick().tick(entry, agent, agentPosition, runAiTick);
        return true;
    }
}
