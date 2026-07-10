package server.agents.capabilities.follow;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed owner activity/AFK state.
 */
public final class AgentActivityStateRuntime {
    private AgentActivityStateRuntime() {
    }

    public static Point ownerAfkPosition(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().afkPosition();
    }

    public static void setOwnerAfkPosition(AgentRuntimeEntry entry, Point position) {
        entry.leaderActivityState().setAfkPosition(position);
    }

    public static long ownerAfkSinceMs(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().afkSinceMs();
    }

    public static void setOwnerAfkSinceMs(AgentRuntimeEntry entry, long sinceMs) {
        entry.leaderActivityState().setAfkSinceMs(sinceMs);
    }

    public static boolean ownerWasAfk(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().wasAfk();
    }

    public static void setOwnerWasAfk(AgentRuntimeEntry entry, boolean wasAfk) {
        entry.leaderActivityState().setWasAfk(wasAfk);
    }

    public static String lastOwnerCommand(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().lastCommand();
    }

    public static long lastOwnerCommandAtMs(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().lastCommandAtMs();
    }

    public static void recordLastOwnerCommand(AgentRuntimeEntry entry, String command, long commandAtMs) {
        entry.leaderActivityState().recordLastCommand(command, commandAtMs);
    }

    public static long ownerOfflineOrDeadSinceMs(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().offlineOrDeadSinceMs();
    }

    public static void setOwnerOfflineOrDeadSinceMs(AgentRuntimeEntry entry, long sinceMs) {
        entry.leaderActivityState().setOfflineOrDeadSinceMs(sinceMs);
    }

    public static boolean ownerInactiveTimerStarted(AgentRuntimeEntry entry) {
        return ownerOfflineOrDeadSinceMs(entry) != 0L;
    }

    public static void startOwnerInactiveTimer(AgentRuntimeEntry entry, long nowMs) {
        setOwnerOfflineOrDeadSinceMs(entry, nowMs);
    }

    public static void clearOwnerInactiveTimer(AgentRuntimeEntry entry) {
        setOwnerOfflineOrDeadSinceMs(entry, 0L);
    }

    public static boolean ownerReturnedToTown(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().returnedToTown();
    }

    public static void setOwnerReturnedToTown(AgentRuntimeEntry entry, boolean returnedToTown) {
        entry.leaderActivityState().setReturnedToTown(returnedToTown);
    }

    public static boolean ownerAwaySafeMode(AgentRuntimeEntry entry) {
        return entry.leaderActivityState().awaySafeMode();
    }

    public static void setOwnerAwaySafeMode(AgentRuntimeEntry entry, boolean safeMode) {
        entry.leaderActivityState().setAwaySafeMode(safeMode);
    }

    public static void clearOwnerInactiveState(AgentRuntimeEntry entry) {
        clearOwnerInactiveTimer(entry);
        setOwnerReturnedToTown(entry, false);
        setOwnerAwaySafeMode(entry, false);
    }
}
