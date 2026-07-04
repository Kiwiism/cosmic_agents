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
        return entry.leaderActivityState().afkPosition();
    }

    public static void setOwnerAfkPosition(BotEntry entry, Point position) {
        entry.leaderActivityState().setAfkPosition(position);
    }

    public static long ownerAfkSinceMs(BotEntry entry) {
        return entry.leaderActivityState().afkSinceMs();
    }

    public static void setOwnerAfkSinceMs(BotEntry entry, long sinceMs) {
        entry.leaderActivityState().setAfkSinceMs(sinceMs);
    }

    public static boolean ownerWasAfk(BotEntry entry) {
        return entry.leaderActivityState().wasAfk();
    }

    public static void setOwnerWasAfk(BotEntry entry, boolean wasAfk) {
        entry.leaderActivityState().setWasAfk(wasAfk);
    }

    public static String lastOwnerCommand(BotEntry entry) {
        return entry.leaderActivityState().lastCommand();
    }

    public static long lastOwnerCommandAtMs(BotEntry entry) {
        return entry.leaderActivityState().lastCommandAtMs();
    }

    public static void recordLastOwnerCommand(BotEntry entry, String command, long commandAtMs) {
        entry.leaderActivityState().recordLastCommand(command, commandAtMs);
    }

    public static long ownerOfflineOrDeadSinceMs(BotEntry entry) {
        return entry.leaderActivityState().offlineOrDeadSinceMs();
    }

    public static void setOwnerOfflineOrDeadSinceMs(BotEntry entry, long sinceMs) {
        entry.leaderActivityState().setOfflineOrDeadSinceMs(sinceMs);
    }

    public static boolean ownerInactiveTimerStarted(BotEntry entry) {
        return ownerOfflineOrDeadSinceMs(entry) != 0L;
    }

    public static void startOwnerInactiveTimer(BotEntry entry, long nowMs) {
        setOwnerOfflineOrDeadSinceMs(entry, nowMs);
    }

    public static void clearOwnerInactiveTimer(BotEntry entry) {
        setOwnerOfflineOrDeadSinceMs(entry, 0L);
    }

    public static boolean ownerReturnedToTown(BotEntry entry) {
        return entry.leaderActivityState().returnedToTown();
    }

    public static void setOwnerReturnedToTown(BotEntry entry, boolean returnedToTown) {
        entry.leaderActivityState().setReturnedToTown(returnedToTown);
    }

    public static boolean ownerAwaySafeMode(BotEntry entry) {
        return entry.leaderActivityState().awaySafeMode();
    }

    public static void setOwnerAwaySafeMode(BotEntry entry, boolean safeMode) {
        entry.leaderActivityState().setAwaySafeMode(safeMode);
    }

    public static void clearOwnerInactiveState(BotEntry entry) {
        clearOwnerInactiveTimer(entry);
        setOwnerReturnedToTown(entry, false);
        setOwnerAwaySafeMode(entry, false);
    }
}
