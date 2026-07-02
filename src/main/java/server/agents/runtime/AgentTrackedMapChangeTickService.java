package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;

public final class AgentTrackedMapChangeTickService {
    private AgentTrackedMapChangeTickService() {
    }

    public record Hooks(MapChangeHandler mapChangeHandler) {
    }

    @FunctionalInterface
    public interface MapChangeHandler {
        boolean handle(BotEntry entry, Character agent);
    }

    public static boolean tickTrackedMapChange(BotEntry entry, Character agent, Hooks hooks) {
        return hooks.mapChangeHandler().handle(entry, agent);
    }
}
