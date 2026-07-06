package server.agents.runtime;

import client.Character;

public final class AgentTrackedMapChangeTickService {
    private AgentTrackedMapChangeTickService() {
    }

    public record Hooks(MapChangeHandler mapChangeHandler) {
    }

    @FunctionalInterface
    public interface MapChangeHandler {
        boolean handle(AgentRuntimeEntry entry, Character agent);
    }

    public static boolean tickTrackedMapChange(AgentRuntimeEntry entry, Character agent, Hooks hooks) {
        return hooks.mapChangeHandler().handle(entry, agent);
    }
}
