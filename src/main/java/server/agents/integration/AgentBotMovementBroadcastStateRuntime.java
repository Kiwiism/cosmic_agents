package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed movement broadcast cache state.
 */
public final class AgentBotMovementBroadcastStateRuntime {
    private AgentBotMovementBroadcastStateRuntime() {
    }

    public static void invalidate(BotEntry entry) {
        entry.setMovementBroadcastValid(false);
    }

    public static boolean matches(BotEntry entry,
                                  int x,
                                  int y,
                                  int velocityX,
                                  int velocityY,
                                  int stance,
                                  int footholdId) {
        return entry.movementBroadcastValid()
                && entry.lastBroadcastX() == x
                && entry.lastBroadcastY() == y
                && entry.lastBroadcastVelX() == velocityX
                && entry.lastBroadcastVelY() == velocityY
                && entry.lastBroadcastStance() == stance
                && entry.lastBroadcastFh() == footholdId;
    }

    public static void record(BotEntry entry,
                              int x,
                              int y,
                              int velocityX,
                              int velocityY,
                              int stance,
                              int footholdId) {
        entry.setMovementBroadcastValid(true);
        entry.setLastBroadcastX(x);
        entry.setLastBroadcastY(y);
        entry.setLastBroadcastVelX(velocityX);
        entry.setLastBroadcastVelY(velocityY);
        entry.setLastBroadcastStance(stance);
        entry.setLastBroadcastFh(footholdId);
    }
}
