package server.monitoring;

import server.ThreadManager;
import server.TimerManager;

public final class ServerLoadMonitor {
    private ServerLoadMonitor() {
    }

    public static ServerLoadLevel currentLevel() {
        Runtime runtime = Runtime.getRuntime();
        double heapRatio = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        int timerQueued = TimerManager.getInstance().getQueuedTasks();
        long timerActive = TimerManager.getInstance().getActiveCount();

        if (heapRatio >= 0.92 || timerQueued >= 20_000) {
            return ServerLoadLevel.CRITICAL;
        }
        if (heapRatio >= 0.85 || timerQueued >= 10_000) {
            return ServerLoadLevel.DEGRADED;
        }
        if (heapRatio >= 0.75 || timerQueued >= 5_000 || timerActive >= 8) {
            return ServerLoadLevel.BUSY;
        }
        return ServerLoadLevel.NORMAL;
    }

    public static boolean allowNonCriticalWork() {
        return currentLevel().compareTo(ServerLoadLevel.DEGRADED) < 0;
    }

    public static String diagnostics() {
        return "loadLevel=" + currentLevel()
                + " " + ThreadManager.getInstance().diagnostics()
                + " " + TimerManager.getInstance().diagnostics();
    }
}
