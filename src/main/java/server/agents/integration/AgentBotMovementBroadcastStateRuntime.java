package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed movement broadcast cache state.
 */
public final class AgentBotMovementBroadcastStateRuntime {
    private AgentBotMovementBroadcastStateRuntime() {
    }

    public static void invalidate(BotEntry entry) {
        entry.movementBroadcastState().setValid(false);
    }

    public static boolean matches(BotEntry entry,
                                  int x,
                                  int y,
                                  int velocityX,
                                  int velocityY,
                                  int stance,
                                  int footholdId) {
        return entry.movementBroadcastState().matches(x, y, velocityX, velocityY, stance, footholdId);
    }

    public static void record(BotEntry entry,
                              int x,
                              int y,
                              int velocityX,
                              int velocityY,
                              int stance,
                              int footholdId) {
        entry.movementBroadcastState().record(x, y, velocityX, velocityY, stance, footholdId);
    }
}
