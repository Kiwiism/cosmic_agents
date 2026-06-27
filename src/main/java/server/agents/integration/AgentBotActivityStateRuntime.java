package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed owner activity/AFK state.
 */
public final class AgentBotActivityStateRuntime {
    private AgentBotActivityStateRuntime() {
    }

    public static Point ownerAfkPosition(BotEntry entry) {
        return entry.ownerAfkPosition();
    }

    public static void setOwnerAfkPosition(BotEntry entry, Point position) {
        entry.setOwnerAfkPosition(position);
    }

    public static long ownerAfkSinceMs(BotEntry entry) {
        return entry.ownerAfkSinceMs();
    }

    public static void setOwnerAfkSinceMs(BotEntry entry, long sinceMs) {
        entry.setOwnerAfkSinceMs(sinceMs);
    }

    public static boolean ownerWasAfk(BotEntry entry) {
        return entry.ownerWasAfk();
    }

    public static void setOwnerWasAfk(BotEntry entry, boolean wasAfk) {
        entry.setOwnerWasAfk(wasAfk);
    }
}
