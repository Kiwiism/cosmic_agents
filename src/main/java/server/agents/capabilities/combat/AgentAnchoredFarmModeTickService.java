package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentFarmAnchorStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentAnchoredFarmModeTickService {
    private AgentAnchoredFarmModeTickService() {
    }

    public record Hooks(AnchoredFarmTick anchoredFarmTick) {
    }

    @FunctionalInterface
    public interface AnchoredFarmTick {
        void tick(AgentRuntimeEntry entry, Character agent, Point agentPosition, boolean runAiTick);
    }

    public static boolean tickIfAnchoredFarm(AgentRuntimeEntry entry,
                                             Character agent,
                                             Point agentPosition,
                                             boolean runAiTick,
                                             Hooks hooks) {
        if (!AgentFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return false;
        }
        hooks.anchoredFarmTick().tick(entry, agent, agentPosition, runAiTick);
        return true;
    }
}
