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

    public static long ownerOfflineOrDeadSinceMs(BotEntry entry) {
        return entry.ownerOfflineOrDeadSinceMs();
    }

    public static void setOwnerOfflineOrDeadSinceMs(BotEntry entry, long sinceMs) {
        entry.setOwnerOfflineOrDeadSinceMs(sinceMs);
    }

    public static boolean ownerInactiveTimerStarted(BotEntry entry) {
        return ownerOfflineOrDeadSinceMs(entry) != 0L;
    }

    public static void startOwnerInactiveTimer(BotEntry entry, long nowMs) {
        entry.setOwnerOfflineOrDeadSinceMs(nowMs);
    }

    public static void clearOwnerInactiveTimer(BotEntry entry) {
        entry.setOwnerOfflineOrDeadSinceMs(0L);
    }

    public static boolean ownerReturnedToTown(BotEntry entry) {
        return entry.ownerReturnedToTown();
    }

    public static void setOwnerReturnedToTown(BotEntry entry, boolean returnedToTown) {
        entry.setOwnerReturnedToTown(returnedToTown);
    }

    public static boolean ownerAwaySafeMode(BotEntry entry) {
        return entry.ownerAwaySafeMode();
    }

    public static void setOwnerAwaySafeMode(BotEntry entry, boolean safeMode) {
        entry.setOwnerAwaySafeMode(safeMode);
    }

    public static void clearOwnerInactiveState(BotEntry entry) {
        clearOwnerInactiveTimer(entry);
        setOwnerReturnedToTown(entry, false);
        setOwnerAwaySafeMode(entry, false);
    }
}
