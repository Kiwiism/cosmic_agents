package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed movement broadcast cache state.
 */
public final class AgentBotMovementBroadcastStateRuntime {
    private AgentBotMovementBroadcastStateRuntime() {
    }

    public static void invalidate(AgentRuntimeEntry entry) {
        entry.movementBroadcastState().setValid(false);
    }

    public static boolean matches(AgentRuntimeEntry entry,
                                  int x,
                                  int y,
                                  int velocityX,
                                  int velocityY,
                                  int stance,
                                  int footholdId) {
        return entry.movementBroadcastState().matches(x, y, velocityX, velocityY, stance, footholdId);
    }

    public static void record(AgentRuntimeEntry entry,
                              int x,
                              int y,
                              int velocityX,
                              int velocityY,
                              int stance,
                              int footholdId) {
        entry.movementBroadcastState().record(x, y, velocityX, velocityY, stance, footholdId);
    }
}
