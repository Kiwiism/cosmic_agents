package server.agents.runtime;

import java.awt.Point;

/**
 * Mutable leader activity and safety state for a live Agent session.
 */
public final class AgentLeaderActivityState {
    private Point afkPosition = null;
    private long afkSinceMs = 0L;
    private boolean wasAfk = false;
    private long offlineOrDeadSinceMs = 0L;
    private boolean returnedToTown = false;
    private boolean awaySafeMode = false;
    private volatile String lastCommand = null;
    private volatile long lastCommandAtMs = 0L;

    public Point afkPosition() {
        return afkPosition;
    }

    public void setAfkPosition(Point afkPosition) {
        this.afkPosition = afkPosition;
    }

    public long afkSinceMs() {
        return afkSinceMs;
    }

    public void setAfkSinceMs(long afkSinceMs) {
        this.afkSinceMs = afkSinceMs;
    }

    public boolean wasAfk() {
        return wasAfk;
    }

    public void setWasAfk(boolean wasAfk) {
        this.wasAfk = wasAfk;
    }

    public long offlineOrDeadSinceMs() {
        return offlineOrDeadSinceMs;
    }

    public void setOfflineOrDeadSinceMs(long offlineOrDeadSinceMs) {
        this.offlineOrDeadSinceMs = offlineOrDeadSinceMs;
    }

    public boolean returnedToTown() {
        return returnedToTown;
    }

    public void setReturnedToTown(boolean returnedToTown) {
        this.returnedToTown = returnedToTown;
    }

    public boolean awaySafeMode() {
        return awaySafeMode;
    }

    public void setAwaySafeMode(boolean awaySafeMode) {
        this.awaySafeMode = awaySafeMode;
    }

    public String lastCommand() {
        return lastCommand;
    }

    public long lastCommandAtMs() {
        return lastCommandAtMs;
    }

    public void recordLastCommand(String command, long commandAtMs) {
        this.lastCommand = command;
        this.lastCommandAtMs = commandAtMs;
    }
}
