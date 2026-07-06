package server.agents.runtime;

import client.Character;

public final class AgentIdleModeTickService {
    private AgentIdleModeTickService() {
    }

    public record Hooks(IdleTick idleTick) {
    }

    @FunctionalInterface
    public interface IdleTick {
        boolean tick(AgentRuntimeEntry entry, Character agent);
    }

    public static boolean tickIdleMode(AgentRuntimeEntry entry, Character agent, Hooks hooks) {
        return hooks.idleTick().tick(entry, agent);
    }
}
