package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;

public final class AgentIdleModeTickService {
    private AgentIdleModeTickService() {
    }

    public record Hooks(IdleTick idleTick) {
    }

    @FunctionalInterface
    public interface IdleTick {
        boolean tick(BotEntry entry, Character agent);
    }

    public static boolean tickIdleMode(BotEntry entry, Character agent, Hooks hooks) {
        return hooks.idleTick().tick(entry, agent);
    }
}
