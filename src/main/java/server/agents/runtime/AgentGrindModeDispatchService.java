package server.agents.runtime;

import client.Character;
import server.agents.runtime.AgentModeStateRuntime;

import java.awt.Point;

public final class AgentGrindModeDispatchService {
    private AgentGrindModeDispatchService() {
    }

    public record Result(boolean consumedTick, Point targetPos) {
    }

    public record Hooks(GrindTick grindTick) {
    }

    @FunctionalInterface
    public interface GrindTick {
        Result tick(AgentRuntimeEntry entry, Character agent, Point agentPosition, Point targetPosition, boolean runAiTick);
    }

    public static Result tickIfGrinding(AgentRuntimeEntry entry,
                                        Character agent,
                                        Point agentPosition,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        Hooks hooks) {
        if (!AgentModeStateRuntime.grinding(entry)) {
            return new Result(false, targetPosition);
        }
        return hooks.grindTick().tick(entry, agent, agentPosition, targetPosition, runAiTick);
    }
}
